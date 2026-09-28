package com.openplayer.music.data.media

import android.content.Context
import com.openplayer.music.data.db.AppDatabase
import com.openplayer.music.data.db.ArtistEntity
import com.openplayer.music.data.remote.DeezerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Repositorio de imágenes de artista: orquesta el pipeline completo
 * **Deezer (búsqueda de artista + picture_big) → descarga a disco →
 * Room (caché) → Coil (UI)**, cumpliendo los términos de uso de Deezer
 * (uso no comercial; contenido informado en la app como de uso
 * privado y familiar).
 *
 * ## Política de caché y peticiones mínimas
 * - **Cache-first**: un artista con fila en Room (positiva o negativa)
 *   NUNCA se vuelve a consultar en Deezer.
 * - Resultado negativo (`deezerId = [NOT_FOUND_ID]`) se cachea para no
 *   reintentar nombres inexistentes en cada visita.
 * - Error de red en Deezer NO se cachea: se reintenta en la próxima
 *   visita a la pestaña.
 * - Si hay `imageUrl` pero falta el archivo en disco (borrado externo),
 *   se re-descarga DIRECTO desde la URL sin tocar la API.
 * - **Búsquedas secuenciales con ~300ms entre llamadas** (Deezer no
 *   impone el 1 req/s de MusicBrainz; el espaciado es conservador para
 *   respetar el monitoreo de uso de sus términos).
 * - **Descargas de imagen paralelizadas** con [DOWNLOAD_WORKERS]
 *   workers mediante [Semaphore]: la búsqueda sigue siendo secuencial,
 *   solo la transferencia de bytes se solapa.
 * - Un [Mutex] garantiza una única corrida de enriquecido a la vez.
 *
 * ## Caché de disco
 * Directorio `filesDir/artist_images/`, nombre MD5(name) + extensión
 * detectada por magic bytes (jpg/png/webp, fallback `.img`), mismo
 * criterio que [CoverRepository]. Un [ConcurrentHashMap] evita stats
 * de disco repetidos entre recomposiciones.
 */
class ArtistImageRepository(
    context: Context,
    appDatabase: AppDatabase
) {

    private val artistDao = appDatabase.artistDao()

    /** Directorio persistente en filesDir (no cacheDir). */
    private val diskCacheDir: File = File(context.filesDir, "artist_images").apply {
        if (!exists()) mkdirs()
    }

    /** Caché en memoria de `artistImageFile()` (thread-safe, solo no-null). */
    private val fileCache = ConcurrentHashMap<String, File>()

    /** Una única corrida de enriquecido a la vez. */
    private val enrichMutex = Mutex()

    /** Límite de descargas de imagen simultáneas. */
    private val downloadSemaphore = Semaphore(DOWNLOAD_WORKERS)

    /**
     * Mapa reactivo `nombre → File de imagen` para la UI. Solo incluye
     * artistas con imagen presente en disco. El mapeo hace stats de
     * disco en IO (fuera del hilo principal).
     */
    val artistImages: Flow<Map<String, File>> = artistDao.getAll()
        .map { entities ->
            buildMap {
                for (entity in entities) {
                    val file = artistImageFile(entity.name)
                    if (file != null) put(entity.name, file)
                }
            }
        }
        .flowOn(Dispatchers.IO)

    /**
     * Devuelve el [File] de la imagen del artista en disco si existe,
     * o `null` si aún no tiene imagen. Usa [fileCache] para evitar
     * stats repetidos.
     */
    fun artistImageFile(name: String): File? {
        fileCache[name]?.let { cached ->
            return if (cached.exists()) cached else null
        }
        val file = findOnDisk(name)
        if (file != null) fileCache[name] = file
        return file
    }

    /**
     * Enriquecido secuencial cache-first de [names] (nombres de
     * artista distintos de la biblioteca). Seguro para llamarse desde
     * la UI al entrar a la pestaña: el [Mutex] descarta corridas
     * solapadas y cada artista resuelto no genera peticiones.
     */
    suspend fun enrichArtists(names: List<String>) {
        if (names.isEmpty()) return
        enrichMutex.withLock {
            val cached = artistDao.getAllOnce().associateBy { it.name }
            val now = System.currentTimeMillis() / 1000
            val pendingDownloads = mutableListOf<Pair<String, String>>()

            for (name in names) {
                val entity = cached[name]

                // Ya resuelto en Deezer (positivo o negativo): no se
                // vuelve a consultar. Solo se re-descarga la imagen si
                // hay URL y falta el archivo en disco.
                if (entity != null) {
                    val url = entity.imageUrl
                    if (url != null && artistImageFile(name) == null) {
                        pendingDownloads += name to url
                    }
                    continue
                }

                // Búsqueda en Deezer (1 request) con algoritmo de 4 pasos
                when (val result = DeezerClient.searchArtist(name)) {
                    DeezerClient.DeezerResult.Error -> {
                        // Red caída o HTTP != 200: sin caché, reintento
                        // en la próxima visita.
                        delay(SEARCH_SPACING_MS)
                        continue
                    }
                    DeezerClient.DeezerResult.NoResults -> {
                        // Sin candidatos confiables: negativo cacheable
                        artistDao.upsertAll(
                            listOf(
                                ArtistEntity(
                                    name = name,
                                    deezerId = NOT_FOUND_ID,
                                    imageUrl = null,
                                    updatedAt = now
                                )
                            )
                        )
                        delay(SEARCH_SPACING_MS)
                        continue
                    }
                    is DeezerClient.DeezerResult.Found -> {
                        delay(SEARCH_SPACING_MS)

                        // Persistir resolución en Room (identidad + URL)
                        artistDao.upsertAll(
                            listOf(
                                ArtistEntity(
                                    name = name,
                                    deezerId = result.artist.id,
                                    imageUrl = result.artist.pictureBig,
                                    updatedAt = now
                                )
                            )
                        )

                        // Encolar descarga de imagen (si hay URL)
                        result.artist.pictureBig?.let { url ->
                            pendingDownloads += name to url
                        }
                    }
                }
            }

            // Descargas paralelizadas (máximo DOWNLOAD_WORKERS a la vez)
            if (pendingDownloads.isNotEmpty()) {
                coroutineScope {
                    pendingDownloads.forEach { (name, url) ->
                        launch { downloadAndSave(name, url) }
                    }
                }
            }
        }
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /**
     * Descarga [url] y la guarda en disco como MD5(name)+extensión.
     * Idempotente y limitada por [downloadSemaphore].
     */
    private suspend fun downloadAndSave(name: String, url: String): Boolean =
        withContext(Dispatchers.IO) {
            downloadSemaphore.withPermit {
                if (findOnDisk(name) != null) return@withContext true
                var connection: HttpURLConnection? = null
                try {
                    val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = DOWNLOAD_TIMEOUT_MS
                        readTimeout = DOWNLOAD_TIMEOUT_MS
                    }
                    connection = conn
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext false
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.isEmpty()) return@withContext false

                    val cacheKey = md5(name)
                    val target = File(diskCacheDir, "$cacheKey${detectExtension(bytes)}")
                    val temp = File(diskCacheDir, "$cacheKey.tmp")
                    FileOutputStream(temp).use { it.write(bytes) }
                    if (!temp.renameTo(target)) {
                        temp.delete()
                        false
                    } else {
                        fileCache[name] = target
                        true
                    }
                } catch (_: Exception) {
                    false
                } finally {
                    connection?.disconnect()
                }
            }
        }

    /** Busca cualquier variante de extensión existente para el nombre. */
    private fun findOnDisk(name: String): File? {
        val cacheKey = md5(name)
        for (ext in EXTENSIONS) {
            val candidate = File(diskCacheDir, "$cacheKey$ext")
            if (candidate.exists()) return candidate
        }
        return null
    }

    /** Detecta el formato de imagen por magic bytes (sin confiar en la URL). */
    private fun detectExtension(bytes: ByteArray): String {
        if (bytes.size < 4) return ".img"
        val b0 = bytes[0]; val b1 = bytes[1]; val b2 = bytes[2]; val b3 = bytes[3]
        // JPEG: FF D8 FF
        if (b0 == 0xFF.toByte() && b1 == 0xD8.toByte() && b2 == 0xFF.toByte()) return ".jpg"
        // PNG: 89 50 4E 47
        if (b0 == 0x89.toByte() && b1 == 0x50.toByte() &&
            b2 == 0x4E.toByte() && b3 == 0x47.toByte()
        ) return ".png"
        // WebP: RIFF....WEBP
        if (bytes.size >= 12 &&
            b0 == 0x52.toByte() && b1 == 0x49.toByte() &&
            b2 == 0x46.toByte() && b3 == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        ) return ".webp"
        return ".img"
    }

    /** MD5 del string (nombre del archivo). */
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** ~300ms entre búsquedas (uso conservador, términos Deezer). */
        private const val SEARCH_SPACING_MS = 300L
        private const val DOWNLOAD_TIMEOUT_MS = 15_000

        /** Workers simultáneos de descarga de imagen. */
        private const val DOWNLOAD_WORKERS = 4

        /** deezerId sentinel: artista buscado y NO encontrado (negativo cacheable). */
        const val NOT_FOUND_ID = -1L

        private val EXTENSIONS = listOf(".jpg", ".png", ".webp", ".img")
    }
}