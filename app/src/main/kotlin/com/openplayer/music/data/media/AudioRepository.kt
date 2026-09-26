package com.openplayer.music.data.media

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.MediaStore
import com.openplayer.music.data.AppPreferences
import com.openplayer.music.data.db.AppDatabase
import com.openplayer.music.data.db.SongEntity
import com.simplecityapps.ktaglib.KTagLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Orquesta el escaneo y sincronización de la biblioteca musical.
 *
 * ## Fuentes de datos
 * - **MediaStore**: descubre archivos de audio del dispositivo
 *   (`IS_MUSIC != 0`). Es la fuente primaria de `id`, `path`,
 *   `duration`, `dateAdded` y valores de respaldo para `title`/`artist`.
 * - **KTagLib**: extracción estricta de metadatos usando TagLib 2.3.2.
 *   Tiene prioridad sobre MediaStore para `title` y `artist`; si no
 *   devuelve valor, se usa el de MediaStore. También actúa como validador
 *   de formato: si KTagLib no puede leer el archivo, se descarta.
 * - **CoverRepository**: extracción y guardado de la portada
 *   embebida en el mismo pipeline, junto con los metadatos.
 * - **Room**: caché reconstruible (ver AppDatabase).
 * - **DataStore (AppPreferences)**: timestamp del último escaneo
 *   (`last_scan_seconds`), usado por el mecanismo incremental.
 *
 * ## Tres mecanismos de sincronización
 *
 * 1. **Escaneo completo inicial** (`syncIfNeeded`): si Room está
 *    vacío (primera apertura o después de reinstalar), se corre
 *    `observeSongBatches()` una sola vez. Al terminar se guarda
 *    el timestamp actual en DataStore.
 *
 * 2. **Re-escaneo incremental al volver a primer plano**
 *    (`incrementalScanIfDue`): lee el timestamp guardado y consulta
 *    MediaStore filtrando por `DATE_ADDED > ts` (nuevos archivos)
 *    **y** `DATE_MODIFIED > ts` (archivos ya indexados cuyas
 *    etiquetas cambiaron). Después de procesar, actualiza el
 *    timestamp al momento actual. Tiene un throttling de
 *    [INCREMENTAL_SCAN_MIN_INTERVAL_MS] (30s) para evitar re-escaneos
 *    frecuentes cuando el usuario cambia rápidamente entre apps.
 *
 * 3. **ContentObserver con debounce de 1.5s**: se registra sobre
 *    `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`. Ante una
 *    ráfaga de avisos (por ejemplo, descarga de varias canciones
 *    seguidas) se cancela y reprograma un re-escaneo incremental,
 *    de modo que se ejecuta **una sola vez** al terminar la
 *    ráfaga. Cubre el caso de música agregada con la app abierta.
 *    **No tiene throttling** porque el ContentObserver ya aplica
 *    su propio debounce y es crítico detectar cambios en caliente.
 *
 * ## Detección de eliminaciones externas
 *
 * Al final de cada sync, se obtiene un snapshot de la tabla y se
 * borran en batch las filas cuyo `path` ya no existe en disco
 * (`File(path).exists() == false`). **Junto con cada fila se
 * elimina también su archivo de portada** en `CoverRepository`,
 * de modo que nunca quedan imágenes huérfanas.
 *
 * ## Pipeline por archivo (orden estricto)
 *
 * 1. Filtro fantasma: path nulo o `File(path)` inexistente.
 * 2. Duración mínima: 30.000 ms (descarta tonos y notificaciones).
 * 3. Extracción KTagLib con FileDescriptor: valida formato y extrae metadatos.
 *    - Si KTagLib devuelve null, el archivo se descarta (formato inválido).
 *    - `title` y `artist` de KTagLib si están presentes, si no,
 *      fallback a los valores obtenidos de MediaStore.
 * 4. Extracción y guardado de portada (idempotente):
 *    `CoverRepository.extractAndSaveCover(path)`. Si el archivo
 *    ya existe en disco, no hace nada.
 * 5. Construcción de [SongEntity].
 * 6. Emisión en lotes de 300 (no acumular toda la biblioteca en
 *    memoria antes de insertar).
 *
 * Todo el trabajo corre con prioridad BACKGROUND y en
 * `Dispatchers.IO`.
 */
class AudioRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val preferences: AppPreferences
) {

    /** Lista reactiva de canciones ordenadas por título (dominio). */
    val songs: Flow<List<com.openplayer.music.data.model.Song>> =
        database.songDao().getAll().map { entities -> entities.map { it.toSong() } }

    private val songDao = database.songDao()
    private val contentResolver: ContentResolver = context.contentResolver
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Scope dedicado al observer y tareas de fondo de larga vida. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Job del debounce del ContentObserver (cancelable). */
    private var debounceJob: Job? = null

    /** Debounce en milisegundos antes de reaccionar a un cambio de MediaStore. */
    private val debounceMillis = 1500L

    /** Duración mínima aceptada para una canción (descarta tonos/notifs). */
    private val minDurationMs = 30_000L

    /** Tamaño de cada lote de inserción en Room. */
    private val batchSize = 300

    /**
     * Intervalo mínimo entre dos llamadas consecutivas de
     * [incrementalScanIfDue] en milisegundos. Evita que cambios
     * rápidos entre apps (ej: volver a la app tras 5s) desencadenen
     * re-escaneos innecesarios. El ContentObserver sigue sin
     * throttling para no perder cambios en caliente.
     */
    private val incrementalScanMinIntervalMs = 30_000L

    /** Timestamp (ms del reloj del sistema) del último incrementalScan ejecutado. */
    private var lastIncrementalScanTimeMs: Long = 0L

    /** Repositorio de portadas: extracción inline y limpieza. */
    private val coverRepository = CoverRepository(context)

    /** Instancia de KTagLib para extracción de metadatos. */
    private val kTagLib = KTagLib()

    // =========================================================================
    // API pública
    // =========================================================================

    /**
     * Búsqueda reactiva de canciones por coincidencia parcial en
     * título, artista o álbum. Usado por la pantalla de búsqueda global.
     *
     * @param query Texto de búsqueda (sin wildcards; se añaden aquí).
     * @return Flow reactivo que se re-emite cuando cambia la query
     *         o cuando la tabla songs cambia (nuevas canciones, etc.).
     */
    fun searchSongs(query: String): Flow<List<com.openplayer.music.data.model.Song>> {
        val pattern = "%${query.trim()}%"
        return songDao.searchSongs(pattern).map { entities -> entities.map { it.toSong() } }
    }

    /**
     * Ejecuta un escaneo completo si Room está vacío. Es la
     * llamada típica desde la Splash (LoadingScreen) en la
     * primera apertura.
     *
     * @return Flow que emite lotes de [SongEntity] mientras se
     *         van insertando; útil para mostrar progreso. Se
     *         completa cuando termina el escaneo.
     */
    fun syncIfNeeded(): Flow<List<SongEntity>> = flow {
        val currentCount = songDao.count()
        if (currentCount == 0) {
            observeSongBatches(isFull = true).collect { batch ->
                songDao.insertBatch(batch)
                emit(batch)
            }
            preferences.setLastScanSeconds(epochSecondsNow())
            cleanDeletedFiles()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Escaneo incremental: nuevos archivos (`DATE_ADDED`) y
     * modificados (`DATE_MODIFIED`) desde el último timestamp.
     * Se llama desde el ContentObserver con debounce de 1.5s para
     * cubrir cambios en caliente. **No aplica throttling** porque
     * el observer ya agrupa ráfagas.
     */
    suspend fun incrementalScan() {
        val lastScan = preferences.lastScanSeconds.first() ?: 0L
        observeSongBatches(isFull = false, sinceSeconds = lastScan).collect { batch ->
            songDao.insertBatch(batch)
        }
        preferences.setLastScanSeconds(epochSecondsNow())
        lastIncrementalScanTimeMs = System.currentTimeMillis()
        cleanDeletedFiles()
    }

    /**
     * Escaneo incremental con throttling de [incrementalScanMinIntervalMs]
     * (30s). Si no ha pasado suficiente tiempo desde el último
     * incrementalScan, retorna inmediatamente sin hacer nada.
     * Diseñado para [MainActivity.onStart], donde la app vuelve
     * a primer plano frecuentemente y no queremos escanear en
     * cada cambio rápido de app.
     *
     * Si el usuario agregó música con la app cerrada, el scan
     * se ejecutará la próxima vez que pase el intervalo. Si agregó
     * música con la app abierta, el ContentObserver la detecta
     * inmediatamente (usa [incrementalScan] sin throttling).
     */
    suspend fun incrementalScanIfDue() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastIncrementalScanTimeMs
        if (lastIncrementalScanTimeMs > 0 && elapsed < incrementalScanMinIntervalMs) {
            // Dentro del intervalo: no hacer nada
            return
        }
        incrementalScan()
    }

    /**
     * Registra el ContentObserver sobre MediaStore. Llamar desde
     * el onCreate de la Activity principal (o donde se desee
     * detectar cambios en tiempo real).
     */
    fun registerContentObserver() {
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaStoreObserver
        )
    }

    /**
     * Desregistra el ContentObserver. Llamar desde onDestroy de
     * la Activity principal.
     */
    fun unregisterContentObserver() {
        debounceJob?.cancel()
        contentResolver.unregisterContentObserver(mediaStoreObserver)
    }

    // =========================================================================
    // Núcleo del escaneo
    // =========================================================================

    /**
     * Flow de lotes de canciones obtenidas de MediaStore y
     * validadas/enriquecidas por KTagLib y extracción de portada.
     *
     * @param isFull true = escaneo completo (sin filtros de tiempo).
     * @param sinceSeconds timestamp epoch en segundos; sólo se
     *        usan si [isFull] es false.
     */
    private fun observeSongBatches(
        isFull: Boolean,
        sinceSeconds: Long = 0L
    ): Flow<List<SongEntity>> = flow {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)

        val (selection, selectionArgs) = if (isFull) {
            "${MediaStore.Audio.Media.IS_MUSIC} != 0" to emptyArray<String>()
        } else {
            ("${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "(${MediaStore.Audio.Media.DATE_ADDED} > ? OR " +
                "${MediaStore.Audio.Media.DATE_MODIFIED} > ?)") to
                arrayOf(sinceSeconds.toString(), sinceSeconds.toString())
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val batch = ArrayList<SongEntity>(batchSize)

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val displayNameIndex =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val path = cursor.getString(dataIndex).orEmpty()
                val mediaStoreTitle = cursor.getString(titleIndex).orEmpty()
                    .ifEmpty { cursor.getString(displayNameIndex).orEmpty() }
                val mediaStoreArtist = cursor.getString(artistIndex).orEmpty()
                val mediaStoreAlbum = cursor.getString(albumIndex).orEmpty()
                val durationMs = cursor.getLong(durationIndex)
                val dateAdded = cursor.getLong(dateAddedIndex)

                // 1. Filtro fantasma
                if (path.isEmpty() || !File(path).exists()) continue

                // 2. Duración mínima
                if (durationMs < minDurationMs) continue

                // 3. Extracción KTagLib con FileDescriptor (valida formato y extrae metadatos)
                val metadata = FileDescriptorHelper.useFd(path) { fd ->
                    kTagLib.getMetadata(fd, File(path).name)
                } ?: continue // Si KTagLib devuelve null, el archivo es inválido

                // Mapeo de propertyMap (KTagLib usa mayúsculas, nosotros minúsculas)
                val propertyMap = metadata.propertyMap
                val title = propertyMap["TITLE"]?.firstOrNull()?.takeIf { it.isNotBlank() } 
                    ?: mediaStoreTitle
                val artist = propertyMap["ARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() } 
                    ?: mediaStoreArtist

                // 4. Extracción y guardado de portada inline (idempotente).
                //    Si el archivo ya existe en disco, extractAndSaveCover
                //    retorna sin hacer I/O pesado. Se ejecuta en el mismo
                //    hilo BACKGROUND del escaneo.
                coverRepository.extractAndSaveCover(path)

                // 5. Construcción de SongEntity
                val entity = SongEntity(
                    id = id,
                    title = title,
                    artist = artist,
                    album = propertyMap["ALBUM"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                        ?: mediaStoreAlbum.takeIf { it.isNotBlank() },
                    albumArtist = propertyMap["ALBUMARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                    genre = propertyMap["GENRE"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                    composer = propertyMap["COMPOSER"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                    lyrics = propertyMap["LYRICS"]?.firstOrNull()?.takeIf { it.isNotBlank() },
                    trackNumber = propertyMap["TRACKNUMBER"]?.firstOrNull()?.parseSlashFirst(),
                    discNumber = propertyMap["DISCNUMBER"]?.firstOrNull()?.parseSlashFirst(),
                    year = propertyMap["DATE"]?.firstOrNull()?.parseYear(),
                    duration = durationMs,
                    path = path,
                    bitrate = metadata.audioProperties?.bitrate,
                    sampleRate = metadata.audioProperties?.sampleRate,
                    channels = metadata.audioProperties?.channels,
                    dateAdded = dateAdded
                )

                batch += entity

                // 6. Emitir lote cuando alcanza el tamaño configurado
                if (batch.size >= batchSize) {
                    emit(ArrayList(batch))
                    batch.clear()
                }
            }

            // Emitir el último lote si quedó algo pendiente
            if (batch.isNotEmpty()) {
                emit(ArrayList(batch))
                batch.clear()
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Borra de Room las filas cuyo path ya no existe en disco y
     * elimina también sus archivos de portada asociados. Detecta
     * canciones eliminadas por otra app o por el usuario desde un
     * explorador de archivos.
     */
    private suspend fun cleanDeletedFiles() {
        val snapshot = songDao.getAll().first()
        val toDelete = snapshot.filter {
            it.path.isEmpty() || !File(it.path).exists()
        }
        if (toDelete.isEmpty()) return

        // Borrar portadas asociadas antes de las filas
        toDelete.forEach { coverRepository.deleteCover(it.path) }

        // Borrar filas en chunks para no exceder límites de SQLite
        toDelete.map { it.id }.chunked(500).forEach { chunk ->
            songDao.deleteByIds(chunk)
        }
    }

    // =========================================================================
    // ContentObserver con debounce
    // =========================================================================

    private val mediaStoreObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            // Cancela cualquier re-escaneo previamente programado y
            // programa uno nuevo dentro de 1.5s. Así una ráfaga de
            // cambios (ej: descarga de varias canciones) se resuelve
            // con un único escaneo incremental al final.
            // **Sin throttling**: el observer es crítico para detectar
            // cambios en caliente mientras la app está abierta.
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(debounceMillis)
                runCatching { incrementalScan() }
            }
        }
    }

    // =========================================================================
    // Utilidades
    // =========================================================================

    private fun epochSecondsNow(): Long = System.currentTimeMillis() / 1000L
}

/**
 * Extrae la parte numérica antes de la "/" en strings tipo "3/12"
 * (TRACKNUMBER o DISCNUMBER). Si no hay "/", parsea el string
 * completo. Retorna null si no es parseable.
 */
private fun String.parseSlashFirst(): Int? =
    substringBefore("/").trim().toIntOrNull()

/**
 * Extrae los primeros 4 dígitos válidos del string como año,
 * restringido al rango 1900-2100.
 */
private fun String.parseYear(): Int? {
    val digits = filter { it.isDigit() }
    if (digits.length < 4) return null
    val year = digits.take(4).toIntOrNull() ?: return null
    return if (year in 1900..2100) year else null
}