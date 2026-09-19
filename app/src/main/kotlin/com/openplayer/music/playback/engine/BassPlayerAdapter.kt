package com.openplayer.music.playback.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.openplayer.music.data.media.AudioFormatParser
import com.un4seen.bass.BASS
import com.un4seen.bass.BASSFLAC
import com.un4seen.bass.BASSOPUS
import com.un4seen.bass.BASS_AAC
import com.un4seen.bass.BASSmix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Adapter que expone BASS Audio Library como un `Player` de Media3.
 * Extiende [SimpleBasePlayer] para reutilizar la implementación base
 * de la interfaz [Player] y solo tener que sobrescribir los handlers
 * que traducen cada comando Media3 a llamadas equivalentes de BASS
 * usando los wrappers oficiales [BASS], [BASSmix], [BASSOPUS],
 * [BASSFLAC] y [BASS_AAC].
 *
 * Arquitectura de reproducción con BASSmix
 *
 * Mixer stream: se crea al inicializar y es el único stream con
 * salida de audio al dispositivo. Si el init falla (device de audio
 * no listo en instalaciones frescas), [ensureBassReady] lo reintenta
 * en cada play con una cadena de fallback (default → OpenSL ES → AudioTrack)
 * y re-vincula los streams existentes.
 *
 * Streams decodificadores: cada canción se crea con
 * `BASS_STREAM_DECODE` (sin PRESCAN, apertura instantánea) y se añade
 * al mixer, no se reproduce directamente. Para archivos Opus se usa
 * [BASSOPUS], para FLAC se usa [BASSFLAC], para AAC se usa [BASS_AAC],
 * y para el resto (MP3, OGG Vorbis) se usa [BASS] genérico.
 *
 * Gapless puro: el polling detecta cuánto falta para el final y,
 * [GAPLESS_SCHEDULE_MS] antes, añade la siguiente pista con
 * `BASS_Mixer_StreamAddChannelEx` usando un start RELATIVO en bytes
 * del mixer → arranca en el byte exacto donde termina la actual,
 * sin silencio y sin solapamiento.
 *
 * Transición de metadatos: cuando el canal de la pista actual
 * termina, BASSmix lo libera (AUTOFREE) y sus lecturas fallan. El
 * polling interpreta ese fallo como fin de pista y promueve la
 * siguiente pista a actual (índice, handles y duración), de modo que
 * notificación y metadatos Media3 coinciden con lo que suena.
 *
 * Baja latencia: buffers de salida de BASS reducidos antes de
 * `BASS_Init`, y flush del buffer de reproducción del mixer
 * (`BASS_POS_MIXER_RESET`) en cada cambio de pista y seek.
 *
 * playWhenReady: lo controla exclusivamente Media3 vía
 * [handleSetPlayWhenReady]; el polling nunca lo sobrescribe.
 *
 * Sistema de queueId global: el adapter mantiene un identificador
 * de cola ([currentQueueId]) expuesto como [queueIdFlow] que permite
 * al PlaybackService distinguir entre diferentes tipos de cola
 * (biblioteca global, orden por fecha, álbum, artista, playlist)
 * y sincronizar reactivamente la cola correcta según el contexto.
 *
 * Fuera de su responsabilidad
 *
 * Audio focus: lo maneja el `PlaybackService` de forma unificada.
 *
 * Selección de motor: la gestiona `PlaybackEngineManager` (Fase 5).
 *
 * Capa de datos Song: no conoce `AudioRepository` ni `Song`.
 */
@OptIn(UnstableApi::class)
class BassPlayerAdapter(
    context: Context,
    looper: Looper
) : SimpleBasePlayer(looper) {

    // ====== Estado interno del reproductor ======

    /** Handle del mixer BASS. 0 significa que no hay mixer creado. */
    private var mixerHandle: Int = 0

    /** Handle del stream decodificador actual. 0 si no hay stream. */
    private var currentHandle: Int = 0

    /** Handle del stream decodificador siguiente (gapless). 0 si no hay. */
    private var nextHandle: Int = 0

    /**
     * True si la transición de la pista actual ya fue programada,
     * o si ya se decidió que no hay siguiente.
     */
    private var nextScheduled: Boolean = false

    /** Posición de reproducción actual en milisegundos. */
    private var currentPositionMs: Long = 0L

    /**
     * Duración del stream actual en milisegundos, o
     * [C.TIME_UNSET] si aún no se conoce.
     */
    private var currentDurationMs: Long = C.TIME_UNSET

    /**
     * Estado de reproducción según las constantes [Player.STATE_IDLE],
     * [Player.STATE_BUFFERING], [Player.STATE_READY] o
     * [Player.STATE_ENDED].
     */
    private var currentPlaybackState: Int = Player.STATE_IDLE

    /**
     * Flag `playWhenReady` de Media3. Cuando es true y el estado es
     * READY, el adapter debe estar reproduciendo. SOLO lo modifica
     * [handleSetPlayWhenReady]; el polling nunca lo sobrescribe.
     */
    private var currentPlayWhenReady: Boolean = false

    /** Playlist actual (lista de MediaItem). */
    private var currentPlaylist: List<MediaItem> = emptyList()

    /** Índice del ítem actual dentro de [currentPlaylist]. */
    private var currentIndex: Int = 0

    /**
     * Identificador de la cola actual. Este valor determina qué tipo
     * de cola está activa y cómo debe sincronizarse con los cambios
     * en la biblioteca. El [PlaybackService] observa [queueIdFlow] para
     * decidir qué Flow de canciones usar (biblioteca completa, filtrada
     * por álbum, por artista, etc.).
     */
    private var currentQueueId: String = QUEUE_LIBRARY

    // ====== Hilos y ciclos ======

    /** Handler en el Looper del player para publicar cambios de estado. */
    private val handler = Handler(looper)

    /** Scope dedicado al polling (independiente del ciclo de vida del player). */
    private val pollingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Job activo del polling. */
    private var pollingJob: Job? = null

    /** Flag interno para saber si BASS ya fue inicializado en este adapter. */
    private var bassInitialized: Boolean = false

    // ====== StateFlow para queueId reactivo ======

    /**
     * StateFlow que expone el queueId actual. El PlaybackService se
     * suscribe a este flujo para detectar cambios de cola y reaccionar
     * cambiando su suscripción al Flow de canciones correspondiente.
     */
    private val _queueIdFlow = MutableStateFlow(QUEUE_LIBRARY)
    val queueIdFlow: StateFlow<String> = _queueIdFlow.asStateFlow()

    init {
        // Buffers de salida reducidos ANTES de BASS_Init: menos latencia
        configureLowLatency()

        // Inicializa BASS con cadena de fallback de device de audio
        bassInitialized = initBassWithFallback()

        // Todos los formatos soportados (Opus, FLAC, AAC) se usan vía sus
        // wrappers oficiales (BASSOPUS, BASSFLAC, BASS_AAC), que cargan sus
        // respectivas librerías nativas automáticamente vía System.loadLibrary.
        // Por eso no hay carga de plugins aquí.
        if (bassInitialized) {
            createMixer()
        }
    }

    /**
     * Reduce los buffers de salida de BASS antes de [BASS.BASS_Init].
     * Debe llamarse antes de inicializar el engine para que aplique.
     */
    private fun configureLowLatency() {
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_BUFFER, OUTPUT_BUFFER_MS)
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_UPDATEPERIOD, UPDATE_PERIOD_MS)
    }

    /**
     * Inicializa BASS con una cadena de fallback de dispositivos de audio.
     * Si el dispositivo por defecto falla (típico en instalaciones frescas
     * o cuando el HAL de audio no está listo), intenta con OpenSL ES y
     * luego con AudioTrack.
     *
     * @return true si BASS se inicializó correctamente con algún dispositivo.
     */
    private fun initBassWithFallback(): Boolean {
        // Intento 1: dispositivo por defecto (AAudio en Android moderno)
        if (BASS.BASS_Init(-1, DEFAULT_SAMPLE_RATE, 0)) {
            Log.i(TAG, "BASS_Init OK with default device")
            return true
        }
        Log.w(TAG, "BASS_Init failed with default device, error: ${BASS.BASS_ErrorGetCode()}")
        // Liberar estado parcial antes del siguiente intento
        BASS.BASS_Free()

        // Intento 2: OpenSL ES
        if (BASS.BASS_Init(-1, DEFAULT_SAMPLE_RATE, BASS.BASS_DEVICE_OPENSLES)) {
            Log.i(TAG, "BASS_Init OK with OpenSL ES")
            return true
        }
        Log.w(TAG, "BASS_Init failed with OpenSL ES, error: ${BASS.BASS_ErrorGetCode()}")
        BASS.BASS_Free()

        // Intento 3: AudioTrack (fallback más básico)
        if (BASS.BASS_Init(-1, DEFAULT_SAMPLE_RATE, BASS.BASS_DEVICE_AUDIOTRACK)) {
            Log.i(TAG, "BASS_Init OK with AudioTrack")
            return true
        }
        Log.e(TAG, "BASS_Init failed with AudioTrack, error: ${BASS.BASS_ErrorGetCode()}")
        return false
    }

    /**
     * Crea el mixer stream que será la salida de audio única.
     * El mixer vive toda la sesión del adapter.
     */
    private fun createMixer() {
        mixerHandle = BASSmix.BASS_Mixer_StreamCreate(
            DEFAULT_SAMPLE_RATE,
            2, // stereo
            BASSmix.BASS_MIXER_NONSTOP // no se detiene cuando no hay fuentes
        )
        if (mixerHandle == 0) {
            val err = BASS.BASS_ErrorGetCode()
            Log.e(TAG, "Failed to create mixer, error: $err")
        } else {
            Log.i(TAG, "Mixer created successfully")
        }
    }
    
    // =========================================================================
    // Estado reportado a Media3
    // =========================================================================

    /**
     * Construye el objeto [State] que SimpleBasePlayer consulta para
     * reportar a Media3 la situación actual del reproductor.
     */
    override fun getState(): State {
        val commands = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_RELEASE)
            .add(Player.COMMAND_SET_MEDIA_ITEM)
            .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_METADATA)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            .build()

        val mediaItemDatas = currentPlaylist.mapIndexed { index, mediaItem ->
            buildMediaItemData(mediaItem, isCurrent = index == currentIndex)
        }

        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaybackState(currentPlaybackState)
            .setPlayWhenReady(
                currentPlayWhenReady,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            )
            .setPlaylist(mediaItemDatas)
            .setCurrentMediaItemIndex(currentIndex)
            .setContentPositionMs(currentPositionMs)
            .build()
    }

    /**
     * Construye un [MediaItemData] a partir de un [MediaItem] de la
     * playlist. La duración solo se reporta si es el ítem actual y
     * ya fue leída desde BASS; de lo contrario queda [C.TIME_UNSET]
     * para que Media3 la trate como desconocida.
     */
    private fun buildMediaItemData(
        mediaItem: MediaItem,
        isCurrent: Boolean
    ): MediaItemData {
        val durationUs = if (isCurrent && currentDurationMs != C.TIME_UNSET) {
            currentDurationMs * 1000L
        } else {
            C.TIME_UNSET
        }

        return MediaItemData.Builder(mediaItem.mediaId.hashCode().toLong())
            .setMediaItem(mediaItem)
            .setDurationUs(durationUs)
            .setIsSeekable(true)
            .setIsPlaceholder(false)
            .setIsDynamic(false)
            .build()
    }

    // =========================================================================
    // API pública para actualización de playlist
    // =========================================================================

    /**
     * Actualiza la playlist del adapter con la lista correcta según
     * el queueId activo. Este método es llamado por PlaybackService
     * cuando detecta cambios en la biblioteca musical. El servicio
     * ya envía la lista filtrada/ordenada correctamente según el
     * queueId (biblioteca completa, álbum específico, artista específico, etc.).
     *
     * El adapter reemplaza la playlist completa pero preserva la
     * canción actual si sigue existiendo en la nueva lista (por mediaId).
     * Si la canción actual fue eliminada, marca el estado como IDLE.
     *
     * Se ejecuta en el hilo del Looper del player para garantizar thread safety.
     *
     * @param newPlaylist Nueva lista de MediaItem ya filtrada y ordenada
     *                    según el queueId activo.
     */
    fun updateLibraryPlaylist(newPlaylist: List<MediaItem>) {
        Log.d(DEBUG_TAG, "updateLibraryPlaylist called | currentQueueId=$currentQueueId | newSize=${newPlaylist.size}")
        handler.post {
            val currentMediaId = if (currentIndex in currentPlaylist.indices) {
                currentPlaylist[currentIndex].mediaId
            } else null

            currentPlaylist = newPlaylist

            if (currentMediaId != null) {
                val newIndex = currentPlaylist.indexOfFirst { it.mediaId == currentMediaId }

                if (newIndex >= 0) {
                    currentIndex = newIndex
                    cancelPendingNext()
                } else {
                    // La canción actual fue eliminada
                    releaseCurrentStream()
                    currentPositionMs = 0L
                    currentDurationMs = C.TIME_UNSET
                    currentIndex = 0.coerceIn(0, max(0, currentPlaylist.size - 1))
                    currentPlaybackState = Player.STATE_IDLE
                    currentPlayWhenReady = false
                    stopPolling()
                }
            } else {
                currentIndex = 0.coerceIn(0, max(0, currentPlaylist.size - 1))
            }

            invalidateState()
        }
    }

    // =========================================================================
    // Handlers de comandos Media3
    // =========================================================================

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        // Leer queueId del tag del primer MediaItem (si existe)
        val queueId = mediaItems.firstOrNull()?.localConfiguration?.tag as? String
            ?: QUEUE_LIBRARY

        Log.d(DEBUG_TAG, "handleSetMediaItems called | queueId=$queueId | itemsCount=${mediaItems.size} | startIndex=$startIndex")

        currentQueueId = queueId
        _queueIdFlow.value = queueId // Notificar al PlaybackService del cambio
        currentPlaylist = mediaItems.toList()
        currentIndex = startIndex.coerceIn(0, max(0, mediaItems.size - 1))

        releaseCurrentStream()

        if (currentPlaylist.isNotEmpty()) {
            createStreamForCurrentItem()

            if (currentHandle != 0) {
                val start = startPositionMs.coerceAtLeast(0L)
                val bytes = BASS.BASS_ChannelSeconds2Bytes(currentHandle, start / 1000.0)
                BASSmix.BASS_Mixer_ChannelSetPosition(
                    currentHandle,
                    bytes,
                    BASS.BASS_POS_BYTE or BASSmix.BASS_POS_MIXER_RESET
                )
                currentPositionMs = start
            }
        }

        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<*> {
        val newList = currentPlaylist.toMutableList()
        newList.addAll(index.coerceIn(0, newList.size), mediaItems)
        currentPlaylist = newList
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(
        fromIndex: Int,
        toIndex: Int
    ): ListenableFuture<*> {
        val newList = currentPlaylist.toMutableList()
        if (fromIndex in 0 until newList.size && toIndex in 0..newList.size && fromIndex < toIndex) {
            newList.subList(fromIndex, toIndex).clear()
            currentPlaylist = newList

            currentIndex = when {
                currentIndex >= toIndex -> currentIndex - (toIndex - fromIndex)
                currentIndex >= fromIndex -> fromIndex.coerceIn(0, max(0, currentPlaylist.size - 1))
                else -> currentIndex
            }
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleMoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int
    ): ListenableFuture<*> {
        if (fromIndex !in 0 until currentPlaylist.size) {
            return Futures.immediateVoidFuture()
        }

        val safeToIndex = toIndex.coerceIn(fromIndex, currentPlaylist.size)
        val newList = currentPlaylist.toMutableList()
        val moved = newList.subList(fromIndex, safeToIndex).toList()

        newList.subList(fromIndex, safeToIndex).clear()
        newList.addAll(newIndex.coerceIn(0, newList.size), moved)

        currentPlaylist = newList
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (currentPlaylist.isEmpty()) {
            currentPlaybackState = Player.STATE_IDLE
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        if (currentHandle == 0) {
            createStreamForCurrentItem()
        }

        currentPlaybackState = if (currentHandle != 0) {
            Player.STATE_READY
        } else {
            Player.STATE_IDLE
        }

        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        currentPlayWhenReady = playWhenReady
        if (playWhenReady) {
            playInternal()
        } else {
            pauseInternal()
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex != currentIndex) {
            currentIndex = mediaItemIndex.coerceIn(0, max(0, currentPlaylist.size - 1))
            releaseCurrentStream()
            createStreamForCurrentItem()

            if (currentHandle != 0) {
                BASSmix.BASS_Mixer_ChannelSetPosition(
                    currentHandle,
                    0,
                    BASS.BASS_POS_BYTE or BASSmix.BASS_POS_MIXER_RESET
                )
            }

            if (currentPlayWhenReady && currentHandle != 0) {
                playInternal()
            }
        } else if (positionMs != C.TIME_UNSET && currentHandle != 0) {
            cancelPendingNext()
            val bytes = BASS.BASS_ChannelSeconds2Bytes(currentHandle, positionMs / 1000.0)
            BASSmix.BASS_Mixer_ChannelSetPosition(
                currentHandle,
                bytes,
                BASS.BASS_POS_BYTE or BASSmix.BASS_POS_MIXER_RESET
            )
            currentPositionMs = positionMs
        }

        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        if (mixerHandle != 0) {
            BASS.BASS_ChannelStop(mixerHandle)
        }
        currentPlayWhenReady = false
        currentPlaybackState = Player.STATE_IDLE
        currentPositionMs = 0L
        currentQueueId = QUEUE_LIBRARY
        _queueIdFlow.value = QUEUE_LIBRARY // Resetear queueId al detener
        Log.d(DEBUG_TAG, "handleStop: queueId reset to LIBRARY")
        stopPolling()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        stopPolling()
        releaseCurrentStream()

        if (mixerHandle != 0) {
            BASS.BASS_StreamFree(mixerHandle)
            mixerHandle = 0
        }

        handler.removeCallbacksAndMessages(null)
        pollingScope.cancel()

        currentQueueId = QUEUE_LIBRARY
        _queueIdFlow.value = QUEUE_LIBRARY // Resetear queueId al liberar
        Log.d(DEBUG_TAG, "handleRelease: queueId reset to LIBRARY")

        invalidateState()
        return Futures.immediateVoidFuture()
    }
    
    // =========================================================================
    // Lógica interna de BASS con BASSmix
    // =========================================================================

    /**
     * Garantiza que BASS y el mixer estén operativos. Si el init original
     * falló (device de audio no listo, típico en instalaciones frescas),
     * reintenta la inicialización con la cadena de fallback y re-vincula
     * el stream actual al mixer nuevo. Se llama en cada play para que el
     * adapter se auto-repare.
     *
     * @return true si el mixer quedó operativo.
     */
    private fun ensureBassReady(): Boolean {
        if (mixerHandle != 0) return true

        if (!bassInitialized) {
            configureLowLatency()
            bassInitialized = initBassWithFallback()
        }

        if (bassInitialized && mixerHandle == 0) {
            createMixer()
            if (mixerHandle != 0 && currentHandle != 0) {
                BASSmix.BASS_Mixer_StreamAddChannel(
                    mixerHandle,
                    currentHandle,
                    BASSmix.BASS_MIXER_CHAN_AUTOFREE
                )
            }
        }

        return mixerHandle != 0
    }

    /**
     * Crea un stream decodificador BASS para el ítem actual de la playlist
     * y lo añade al mixer. Sin PRESCAN para que el inicio sea instantáneo.
     * Para archivos Opus usa BASSOPUS, para FLAC usa BASSFLAC, para AAC
     * usa BASS_AAC, y para el resto (MP3, OGG Vorbis) usa BASS genérico.
     */
    private fun createStreamForCurrentItem() {
        if (currentPlaylist.isEmpty() || currentIndex !in currentPlaylist.indices) {
            currentHandle = 0
            return
        }

        val mediaItem = currentPlaylist[currentIndex]
        val uri = mediaItem.localConfiguration?.uri
        val path = uri?.path

        if (path.isNullOrEmpty()) {
            currentHandle = 0
            currentPlaybackState = Player.STATE_IDLE
            return
        }

        val handle = createDecoderStream(path)
        if (handle == 0) {
            currentHandle = 0
            currentPlaybackState = Player.STATE_IDLE
            return
        }

        currentHandle = handle
        currentDurationMs = lengthMsOf(handle)
        currentPositionMs = 0L
        currentPlaybackState = Player.STATE_READY
        nextScheduled = false

        if (mixerHandle != 0) {
            BASSmix.BASS_Mixer_StreamAddChannel(
                mixerHandle,
                currentHandle,
                BASSmix.BASS_MIXER_CHAN_AUTOFREE
            )
        }
    }

    /**
     * Crea un stream decodificador para el archivo dado, eligiendo el
     * wrapper apropiado según el formato detectado:
     * Opus → BASSOPUS.BASS_OPUS_StreamCreateFile()
     * FLAC u OGG FLAC → BASSFLAC.BASS_FLAC_StreamCreateFile()
     * AAC/M4A → BASS_AAC.BASS_AAC_StreamCreateFile()
     * Otros (MP3, OGG Vorbis, etc.) → BASS.BASS_StreamCreateFile()
     *
     * @param path Ruta absoluta al archivo de audio.
     * @return Handle del stream ( > 0) o 0 si falló.
     */
    private fun createDecoderStream(path: String): Int {
        val format = try {
            if (AudioFormatParser.isValid(path)) {
                when {
                    path.endsWith(".opus", ignoreCase = true) -> "opus"
                    path.endsWith(".flac", ignoreCase = true) -> "flac"
                    path.endsWith(".ogg", ignoreCase = true) -> {
                        // Detectar si es OGG FLAC o OGG Vorbis/Opus
                        when (detectOggFormat(path)) {
                            "flac" -> "flac"
                            "opus" -> "opus"
                            else -> "vorbis"
                        }
                    }
                    path.endsWith(".m4a", ignoreCase = true) || path.endsWith(".aac", ignoreCase = true) -> "aac"
                    else -> "other"
                }
            } else {
                "other"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error detecting format for $path", e)
            "other"
        }

        return when (format) {
            "opus" -> BASSOPUS.BASS_OPUS_StreamCreateFile(path, 0, 0, BASS.BASS_STREAM_DECODE)
            "flac" -> BASSFLAC.BASS_FLAC_StreamCreateFile(path, 0, 0, BASS.BASS_STREAM_DECODE)
            "aac" -> BASS_AAC.BASS_AAC_StreamCreateFile(path, 0, 0, BASS.BASS_STREAM_DECODE)
            else -> BASS.BASS_StreamCreateFile(path, 0, 0, BASS.BASS_STREAM_DECODE)
        }
    }

    /**
     * Detecta el formato de un archivo OGG leyendo el header.
     *
     * @param path Ruta absoluta al archivo OGG.
     * @return "flac", "opus", "vorbis" o "unknown".
     */
    private fun detectOggFormat(path: String): String {
        return try {
            java.io.RandomAccessFile(path, "r").use { file ->
                val header = ByteArray(64)
                val read = file.read(header)
                if (read < 27) return "unknown"

                // Verificar magic "OggS"
                if (header[0] != 'O'.code.toByte() || header[1] != 'g'.code.toByte() ||
                    header[2] != 'g'.code.toByte() || header[3] != 'S'.code.toByte()) {
                    return "unknown"
                }

                // Buscar en el primer paquete
                if (read >= 8 && header[0] == 0x01.toByte() &&
                    header[1] == 'v'.code.toByte() && header[2] == 'o'.code.toByte() &&
                    header[3] == 'r'.code.toByte() && header[4] == 'b'.code.toByte() &&
                    header[5] == 'i'.code.toByte() && header[6] == 's'.code.toByte()) {
                    return "vorbis"
                }

                if (read >= 8 && header[0] == 'O'.code.toByte() &&
                    header[1] == 'p'.code.toByte() && header[2] == 'u'.code.toByte() &&
                    header[3] == 's'.code.toByte() && header[4] == 'H'.code.toByte() &&
                    header[5] == 'e'.code.toByte() && header[6] == 'a'.code.toByte() &&
                    header[7] == 'd'.code.toByte()) {
                    return "opus"
                }

                if (read >= 5 && header[0] == 0x7F.toByte() &&
                    header[1] == 'F'.code.toByte() && header[2] == 'L'.code.toByte() &&
                    header[3] == 'A'.code.toByte() && header[4] == 'C'.code.toByte()) {
                    return "flac"
                }

                "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Duración en milisegundos de un stream decodificador,
     * o [C.TIME_UNSET] si no se puede determinar.
     */
    private fun lengthMsOf(handle: Int): Long {
        val bytes = BASS.BASS_ChannelGetLength(handle, BASS.BASS_POS_BYTE)
        if (bytes < 0L) return C.TIME_UNSET

        val ms = (BASS.BASS_ChannelBytes2Seconds(handle, bytes) * 1000.0).toLong()
        return if (ms < 0L) C.TIME_UNSET else ms
    }

    /**
     * Programa la siguiente pista para gapless puro, justo a tiempo.
     */
    private fun scheduleNextGapless(remainingMs: Long) {
        nextScheduled = true
        val nextIndex = currentIndex + 1

        if (nextIndex >= currentPlaylist.size) {
            return
        }

        val nextPath = currentPlaylist[nextIndex].localConfiguration?.uri?.path
        if (nextPath.isNullOrEmpty()) {
            return
        }

        val handle = createDecoderStream(nextPath)
        if (handle == 0) {
            return
        }

        nextHandle = handle

        if (mixerHandle == 0) return

        val startBytes = BASS.BASS_ChannelSeconds2Bytes(mixerHandle, remainingMs / 1000.0)
        BASSmix.BASS_Mixer_StreamAddChannelEx(
            mixerHandle,
            nextHandle,
            BASSmix.BASS_MIXER_CHAN_AUTOFREE,
            startBytes,
            0
        )
    }

    /**
     * Cancela cualquier transición pendiente.
     */
    private fun cancelPendingNext() {
        if (nextHandle != 0) {
            BASSmix.BASS_Mixer_ChannelRemove(nextHandle)
            BASS.BASS_StreamFree(nextHandle)
            nextHandle = 0
        }
        nextScheduled = false
    }

    /** Libera los streams BASS actual y siguiente si existen. */
    private fun releaseCurrentStream() {
        if (currentHandle != 0) {
            BASSmix.BASS_Mixer_ChannelRemove(currentHandle)
            BASS.BASS_StreamFree(currentHandle)
            currentHandle = 0
        }
        if (nextHandle != 0) {
            BASSmix.BASS_Mixer_ChannelRemove(nextHandle)
            BASS.BASS_StreamFree(nextHandle)
            nextHandle = 0
        }
        nextScheduled = false
    }

    /** Reproduce el mixer (auto-reparando el init si hace falta) y arranca el polling. */
    private fun playInternal() {
        if (!ensureBassReady()) return
        BASS.BASS_ChannelPlay(mixerHandle, false)
        currentPlaybackState = Player.STATE_READY
        startPolling()
    }

    /** Pausa el mixer (sin detener el polling todavía). */
    private fun pauseInternal() {
        if (mixerHandle == 0) return
        BASS.BASS_ChannelPause(mixerHandle)
        currentPlaybackState = Player.STATE_READY
    }

    // =========================================================================
    // Polling de posición, transiciones y detección de fin de pista
    // =========================================================================

    /** Arranca el ciclo de polling si no estaba activo. */
    private fun startPolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = pollingScope.launch {
            while (isActive) {
                updateFromNative()
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    /** Detiene el ciclo de polling. */
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Consulta al mixer la posición y el estado del canal actual,
     * programa justo a tiempo la transición gapless a la siguiente
     * pista y publica los cambios al hilo del Looper del player.
     */
    private fun updateFromNative() {
        val handle = currentHandle
        if (handle == 0) return

        val positionBytes = BASSmix.BASS_Mixer_ChannelGetPosition(handle, BASS.BASS_POS_BYTE)
        val positionMs = if (positionBytes >= 0L) {
            (BASS.BASS_ChannelBytes2Seconds(handle, positionBytes) * 1000.0).toLong()
        } else {
            -1L
        }

        val active = BASSmix.BASS_Mixer_ChannelIsActive(handle)

        handler.post {
            if (currentHandle == 0 || currentHandle != handle) return@post

            if (positionMs >= 0L) {
                currentPositionMs = positionMs
            }

            val previousState = currentPlaybackState

            val channelEnded = positionMs < 0L ||
                active == CHANNEL_ERROR ||
                active == BASS.BASS_ACTIVE_STOPPED

            if (!nextScheduled &&
                !channelEnded &&
                currentPlayWhenReady &&
                currentDurationMs != C.TIME_UNSET
            ) {
                val remaining = currentDurationMs - currentPositionMs
                if (remaining in 1..GAPLESS_SCHEDULE_MS) {
                    scheduleNextGapless(remaining)
                }
            }

            if (channelEnded &&
                previousState != Player.STATE_IDLE &&
                previousState != Player.STATE_ENDED
            ) {
                onTrackEnded()
            } else if (!channelEnded) {
                when (active) {
                    BASS.BASS_ACTIVE_PLAYING, BASS.BASS_ACTIVE_PAUSED -> {
                        currentPlaybackState = Player.STATE_READY
                    }
                    BASS.BASS_ACTIVE_STALLED -> {
                        currentPlaybackState = Player.STATE_BUFFERING
                    }
                    BASSmix.BASS_ACTIVE_WAITING, BASSmix.BASS_ACTIVE_QUEUED -> {
                        // La siguiente pista espera su turno en el mixer
                    }
                    else -> { /* Estados desconocidos: ignorar */ }
                }
            }

            invalidateState()
        }
    }

    /**
     * Avanza automáticamente al siguiente ítem de la playlist cuando
     * termina el actual.
     */
    private fun onTrackEnded() {
        val nextIndex = currentIndex + 1

        Log.d(DEBUG_TAG, "onTrackEnded: currentIndex=$currentIndex | nextIndex=$nextIndex | playlistSize=${currentPlaylist.size} | queueId=$currentQueueId")

        if (nextIndex < currentPlaylist.size) {
            currentIndex = nextIndex

            if (nextHandle != 0) {
                currentHandle = nextHandle
                nextHandle = 0
            } else {
                createStreamForCurrentItem()
            }

            nextScheduled = false
            currentPositionMs = 0L
            currentDurationMs = if (currentHandle != 0) {
                lengthMsOf(currentHandle)
            } else {
                C.TIME_UNSET
            }

            if (currentPlayWhenReady && mixerHandle != 0) {
                playInternal()
            }
        } else {
            currentPlaybackState = Player.STATE_ENDED
            currentPlayWhenReady = false
            stopPolling()
        }
    }

    // =========================================================================
    // Constantes
    // =========================================================================

    companion object {
        private const val TAG = "BassPlayerAdapter"
        private const val DEBUG_TAG = "QueueDebug"

        /** Identificador de la cola global de biblioteca (ordenada por título). */
        const val QUEUE_LIBRARY = "library"

        /** Identificador de la cola de TracksScreen ordenada por fecha de agregada descendente. */
        const val QUEUE_TRACKS_BY_DATE = "tracksByDate"

        /** Prefijo para colas de álbum específico. Formato: "album:{nombreÁlbum}" */
        const val QUEUE_ALBUM_PREFIX = "album:"

        /** Prefijo para colas de artista específico. Formato: "artist:{nombreArtista}" */
        const val QUEUE_ARTIST_PREFIX = "artist:"

        /** Prefijo para playlists personalizadas. Formato: "playlist:{playlistId}" */
        const val QUEUE_PLAYLIST_PREFIX = "playlist:"

        /** Intervalo del polling de posición en milisegundos. */
        private const val POLLING_INTERVAL_MS = 500L

        /** Sample rate por defecto para inicializar BASS. */
        private const val DEFAULT_SAMPLE_RATE = 44100

        /**
         * Antelación (ms) con la que se programa la siguiente pista para
         * garantizar gapless sin pre-cargar archivos al iniciar la reproducción.
         */
        private const val GAPLESS_SCHEDULE_MS = 2000L

        /**
         * Buffer de salida de BASS en milisegundos (se aplica antes de BASS_Init).
         */
        private const val OUTPUT_BUFFER_MS = 100

        /**
         * Periodo de update de BASS en milisegundos (se aplica antes de BASS_Init).
         */
        private const val UPDATE_PERIOD_MS = 20

        /**
         * Valor que devuelven las funciones de estado de BASSmix cuando
         * el handle ya fue liberado (canal terminado con AUTOFREE).
         */
        private const val CHANNEL_ERROR = -1
    }
}