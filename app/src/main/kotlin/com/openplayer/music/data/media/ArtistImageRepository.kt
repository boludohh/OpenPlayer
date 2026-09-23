package com.openplayer.music.data.media

import android.content.Context
import com.openplayer.music.data.AppPreferences
import com.openplayer.music.data.db.AppDatabase
import com.openplayer.music.data.db.ArtistEntity
import com.openplayer.music.data.remote.FanartClient
import com.openplayer.music.data.remote.MusicBrainzClient
import com.openplayer.music.data.remote.MusicBrainzClient.MusicBrainzResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Repositorio de imágenes de artista: orquesta el pipeline completo
 * **MusicBrainz (MBID) → Fanart.tv (artistthumb) → descarga a disco →
 * Room (caché) → Coil (UI)**, cumpliendo los términos de ambos
 * servicios y la política de rate limiting.
 *
 * ## Política de caché y peticiones mínimas (término general 5 de
 * fanart.tv y buenas prácticas de MusicBrainz)
 * - **Cache-first**: un artista con [ArtistEntity.mbid] no-null
 *   (positivo o negativo) NUNCA se vuelve a consultar en MusicBrainz.
 * - Resultado negativo de MusicBrainz (`mbid = ""`) se cachea para no
 *   reintentar nombres inexistentes en cada visita.
 * - Error de red en MusicBrainz NO se cachea: se reintenta en la
 *   próxima visita a la pestaña.
 * - Si el MBID existe y hay `thumbUrl` pero falta el archivo en disco
 *   (borrado externo), se re-descarga DIRECTO desde la URL sin tocar
 *   ninguna API.
 * - **Cola secuencial con ~1.1s entre llamadas remotas** (límite de
 *   MusicBrainz de 1 req/s). Nunca peticiones en paralelo: un [Mutex]
 *   garantiza una única corrida de enriquecido a la vez.
 *
 * ## Caché de disco
 * Directorio `filesDir/artist_images/`, nombre MD5(name) + extensión
 * detectada por magic bytes (jpg/png/webp, fallback `.img`), mismo
 * criterio que [CoverRepository]. Un [ConcurrentHashMap] evita stats
 * de disco repetidos entre recomposiciones.
 *
 * ## Clave personal del usuario
 * Lee [AppPreferences.fanartUserKey] (preparado técnicamente, sin UI
 * todavía) y la envía como `client_key` junto a la clave de proyecto.
 */
class ArtistImageRepository(
    context: Context,
    appDatabase: AppDatabase,
    private val appPreferences: AppPreferences
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
            val userKey = appPreferences.fanartUserKey.first()
            val now = System.currentTimeMillis() / 1000

            for (name in names) {
                val entity = cached[name]

                // Ya resuelto en MusicBrainz (positivo o negativo): no
                // se vuelve a consultar. Solo se re-descarga la imagen
                // si hay URL y falta el archivo en disco.
                if (entity != null && entity.mbid != null) {
                    val url = entity.thumbUrl
                    if (url != null && artistImageFile(name) == null) {
                        downloadAndSave(name, url)
                    }
                    continue
                }

                // 1) MusicBrainz: resolver MBID (1 request)
                when (val result = MusicBrainzClient.searchArtist(name)) {
                    MusicBrainzResult.Error -> {
                        // Red caída o HTTP != 200: sin caché, reintento
                        // en la próxima visita.
                        delay(REMOTE_CALL_SPACING_MS)
                        continue
                    }
                    MusicBrainzResult.NoResults -> {
                        // Sin candidatos: negativo cacheable (mbid = "")
                        artistDao.upsertAll(
                            listOf(
                                ArtistEntity(
                                    name = name,
                                    mbid = "",
                                    disambiguation = null,
                                    thumbUrl = null,
                                    updatedAt = now
                                )
                            )
                        )
                        delay(REMOTE_CALL_SPACING_MS)
                        continue
                    }
                    is MusicBrainzResult.Found -> {
                        delay(REMOTE_CALL_SPACING_MS)

                        // 2) Fanart.tv: mejor artistthumb (1 request)
                        val thumbUrl = FanartClient.fetchBestThumbUrl(result.match.mbid, userKey)
                        delay(REMOTE_CALL_SPACING_MS)

                        // 3) Descarga de imagen a disco (solo si hay URL)
                        if (thumbUrl != null) {
                            downloadAndSave(name, thumbUrl)
                        }

                        // 4) Persistir resolución en Room
                        artistDao.upsertAll(
                            listOf(
                                ArtistEntity(
                                    name = name,
                                    mbid = result.match.mbid,
                                    disambiguation = result.match.disambiguation,
                                    thumbUrl = thumbUrl,
                                    updatedAt = now
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /** Descarga [url] y la guarda en disco como MD5(name)+extensión. Idempotente. */
    private suspend fun downloadAndSave(name: String, url: String): Boolean =
        withContext(Dispatchers.IO) {
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
        /** ~1.1s entre llamadas remotas (rate limit MusicBrainz 1 req/s). */
        private const val REMOTE_CALL_SPACING_MS = 1100L
        private const val DOWNLOAD_TIMEOUT_MS = 15_000
        private val EXTENSIONS = listOf(".jpg", ".png", ".webp", ".img")
    }
}