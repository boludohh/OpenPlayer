package com.openplayer.music.playback.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.openplayer.music.native.BassNative
import com.un4seen.bass.BASS
import com.un4seen.bass.BASSmix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Adapter que expone BASS Audio Library como un `Player` de Media3.
 *
 * Extiende [SimpleBasePlayer] para reutilizar la implementación base
 * de la interfaz [Player] y solo tener que sobrescribir los handlers
 * que traducen cada comando Media3 a llamadas equivalentes de BASS
 * ([BassNative] para init/plugins) y de los wrappers oficiales
 * [BASS] / [BASSmix] para streams y mixer.
 *
 * ## Arquitectura de reproducción con BASSmix
 *
 * 1. **Mixer stream**: se crea al inicializar y es el único stream con
 *    salida de audio al dispositivo. Si el init falla (device de audio
 *    no listo en instalaciones frescas), [ensureBassReady] lo reintenta
 *    en cada play y re-vincula los streams existentes.
 * 2. **Streams decodificadores**: cada canción se crea con
 *    `BASS_STREAM_DECODE` (sin PRESCAN, apertura instantánea) y se añade
 *    al mixer, no se reproduce directamente.
 * 3. **Gapless puro**: el polling detecta cuánto falta para el final y,
 *    [GAPLESS_SCHEDULE_MS] antes, añade la siguiente pista con
 *    `BASS_Mixer_StreamAddChannelEx` usando un start RELATIVO en bytes
 *    del mixer → arranca en el byte exacto donde termina la actual,
 *    sin silencio y sin solapamiento.
 * 4. **Baja latencia**: buffers de salida de BASS reducidos antes de
 *    `BASS_Init`, y flush del buffer de reproducción del mixer
 *    (`BASS_POS_MIXER_RESET`) en cada cambio de pista y seek, para que
 *    no suene la cola de la pista anterior.
 * 5. **playWhenReady**: lo controla exclusivamente Media3 vía
 *    [handleSetPlayWhenReady]; el polling nunca lo sobrescribe.
 *
 * ## Fuera de su responsabilidad
 *
 * - **Audio focus**: lo maneja el `PlaybackService` de forma unificada.
 * - **Selección de motor**: la gestiona `PlaybackEngineManager` (Fase 5).
 * - **Capa de datos Song**: no conoce `AudioRepository` ni `Song`.
 */
@OptIn(UnstableApi::class)
class BassPlayerAdapter(
    context: Context,
    looper: Looper
) : SimpleBasePlayer(looper) {

    // ====== Estado interno del reproductor ======

    /** Directorio de librerías nativas para cargar plugins BASS. */
    private val nativeLibDir: String = context.applicationInfo.nativeLibraryDir

    /** Handle del mixer BASS. 0 significa que no hay mixer creado. */
    private var mixerHandle: Int = 0

    /** Handle del stream decodificador actual. 0 si no hay stream. */
    private var currentHandle: Int = 0

    /** Handle del stream decodificador siguiente (gapless). 0 si no hay. */
    private var nextHandle: Int = 0

    /**
     * True si la transición de la pista actual ya fue programada,
     * o si ya se decidió que no hay siguiente. Evita reprogramar
     * en cada ciclo de polling.
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

    // ====== Hilos y ciclos ======

    /** Handler en el Looper del player para publicar cambios de estado. */
    private val handler = Handler(looper)

    /** Scope dedicado al polling (independiente del ciclo de vida del player). */
    private val pollingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Job activo del polling. */
    private var pollingJob: Job? = null

    /** Flag interno para saber si BASS ya fue inicializado en este adapter. */
    private var bassInitialized: Boolean = false

    init {
        // Buffers de salida reducidos ANTES de BASS_Init: menos latencia
        // en cada arranque de reproducción.
        configureLowLatency()

        // Inicializa BASS con sample rate estándar. BassNative usa
        // System.loadLibrary que es idempotente; la inicialización
        // del engine también lo es internamente.
        bassInitialized = BassNative.init(DEFAULT_SAMPLE_RATE)

        // Cargar plugins FLAC, Opus y AAC después de inicializar BASS
        if (bassInitialized) {
            BassNative.loadPlugins(nativeLibDir)
            createMixer()
        }
    }

    /**
     * Reduce los buffers de salida de BASS antes de [BassNative.init].
     * Debe llamarse antes de inicializar el engine para que aplique.
     */
    private fun configureLowLatency() {
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_BUFFER, OUTPUT_BUFFER_MS)
        BASS.BASS_SetConfig(BASS.BASS_CONFIG_UPDATEPERIOD, UPDATE_PERIOD_MS)
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
            android.util.Log.e(TAG, "Failed to create mixer, error: $err")
        } else {
            android.util.Log.i(TAG, "Mixer created successfully")
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
     * Actualiza la playlist interna del adapter sin interrumpir la
     * reproducción actual si la canción sigue existiendo.
     *
     * Este método es llamado por PlaybackService cuando detecta cambios
     * en la biblioteca musical. Se ejecuta en el hilo del Looper del
     * player para garantizar thread safety.
     *
     * Si la canción actual sigue existiendo se mantiene la reproducción
     * y se cancela cualquier transición pendiente (el polling la
     * reprograma con la playlist nueva). Si fue eliminada, se libera el
     * stream y se resetea el estado.
     *
     * @param newPlaylist Nueva lista de MediaItem que reemplaza la actual.
     */
    fun updatePlaylist(newPlaylist: List<MediaItem>) {
        handler.post {
            // Identificar la canción actual por su mediaId
            val currentMediaId = if (currentIndex in currentPlaylist.indices) {
                currentPlaylist[currentIndex].mediaId
            } else null

            // Actualizar la playlist
            currentPlaylist = newPlaylist

            if (currentMediaId != null) {
                val newIndex = currentPlaylist.indexOfFirst { it.mediaId == currentMediaId }

                if (newIndex >= 0) {
                    currentIndex = newIndex
                    // Cancelar transición pendiente: la siguiente canción
                    // pudo haber cambiado; el polling la reprograma.
                    cancelPendingNext()
                } else {
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
        currentPlaylist = mediaItems.toList()
        currentIndex = startIndex.coerceIn(0, max(0, mediaItems.size - 1))

        releaseCurrentStream()

        if (currentPlaylist.isNotEmpty()) {
            createStreamForCurrentItem()
            if (currentHandle != 0) {
                // Coloca la posición inicial y hace flush del buffer del
                // mixer para que no suene nada de la pista anterior.
                val start = startPositionMs.coerceAtLeast(0L)
                val bytes = BASS.BASS_ChannelSeconds2Bytes(currentHandle, start / 1000.0)
                BASSmix.BASS_Mixer_ChannelSetPosition(
                    currentHandle,
                    bytes,
                    BASS.BASS_POS_BYTE or BASSmix.BASS_POS_MIXER_RESET
                )
                currentPositionMs = start
            }
            // La transición gapless la programa el polling justo a tiempo.
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
        // Cambio de ítem si es necesario
        if (mediaItemIndex != C.INDEX_UNSET && mediaItemIndex != currentIndex) {
            currentIndex = mediaItemIndex.coerceIn(0, max(0, currentPlaylist.size - 1))
            releaseCurrentStream()
            createStreamForCurrentItem()

            if (currentHandle != 0) {
                // Flush del buffer del mixer: sin cola de la pista anterior
                BASSmix.BASS_Mixer_ChannelSetPosition(
                    currentHandle,
                    0,
                    BASS.BASS_POS_BYTE or BASSmix.BASS_POS_MIXER_RESET
                )
            }

            // Si el player estaba reproduciendo, asegurar mixer activo y polling
            if (currentPlayWhenReady && currentHandle != 0) {
                playInternal()
            }
        } else if (positionMs != C.TIME_UNSET && currentHandle != 0) {
            // Seek dentro del ítem actual: cancelar transición pendiente
            // (su posición de inicio quedó invalidada); el polling la
            // reprogramará justo a tiempo.
            cancelPendingNext()

            // Seek correcto dentro del mixer: flush del buffer incluido
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
        // No liberamos BASS global aquí: el PlaybackService lo hará
        // cuando destruya la sesión, para permitir cambio de motor
        // sin reinicializar la biblioteca.
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    // =========================================================================
    // Lógica interna de BASS con BASSmix
    // =========================================================================

    /**
     * Garantiza que BASS y el mixer estén operativos. Si el init original
     * falló (device de audio no listo, típico en instalaciones frescas),
     * reintenta la inicialización y re-vincula el stream actual al mixer
     * nuevo. Se llama en cada play para que el adapter se auto-repare.
     *
     * @return true si el mixer quedó operativo.
     */
    private fun ensureBassReady(): Boolean {
        if (mixerHandle != 0) return true

        if (!bassInitialized) {
            bassInitialized = BassNative.init(DEFAULT_SAMPLE_RATE)
            if (bassInitialized) {
                BassNative.loadPlugins(nativeLibDir)
            }
        }

        if (bassInitialized && mixerHandle == 0) {
            createMixer()
            // Re-vincular streams que quedaron sin mixer por el fallo inicial
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
     * No reproduce directamente; el mixer se encarga de la salida de audio.
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

        // Stream decodificador sin PRESCAN: apertura instantánea
        val handle = BASS.BASS_StreamCreateFile(
            path,
            0,
            0,
            BASS.BASS_STREAM_DECODE
        )

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

        // Añadir al mixer para salida de audio
        if (mixerHandle != 0) {
            BASSmix.BASS_Mixer_StreamAddChannel(
                mixerHandle,
                currentHandle,
                BASSmix.BASS_MIXER_CHAN_AUTOFREE
            )
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
     * Añade el stream decodificador de la siguiente canción al mixer con
     * un start RELATIVO en bytes del mixer equivalente al tiempo restante,
     * de modo que arranca exactamente cuando termina la actual.
     *
     * @param remainingMs Milisegundos restantes de la pista actual.
     */
    private fun scheduleNextGapless(remainingMs: Long) {
        nextScheduled = true

        val nextIndex = currentIndex + 1
        if (nextIndex >= currentPlaylist.size) {
            return // No hay siguiente pista
        }

        val nextPath = currentPlaylist[nextIndex].localConfiguration?.uri?.path
        if (nextPath.isNullOrEmpty()) {
            return
        }

        // Stream decodificador sin PRESCAN para la siguiente pista
        val handle = BASS.BASS_StreamCreateFile(
            nextPath,
            0,
            0,
            BASS.BASS_STREAM_DECODE
        )
        if (handle == 0) {
            return
        }
        nextHandle = handle

        if (mixerHandle == 0) return

        // Start RELATIVO en bytes del mixer: arranca al terminar la actual
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
     * Cancela cualquier transición pendiente: retira la siguiente pista
     * del mixer y libera su stream. El polling la reprogramará cuando
     * corresponda (después de seeks o cambios de playlist).
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
     *
     * IMPORTANTE: este polling NUNCA modifica [currentPlayWhenReady];
     * ese flag lo controla exclusivamente Media3. Así, al pausar, el
     * botón de la notificación se queda en play y la barra de progreso
     * se congela correctamente.
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
            // Si el handle cambió entre la lectura y este post, ignorar
            if (currentHandle == 0 || currentHandle != handle) return@post

            if (positionMs >= 0L) {
                currentPositionMs = positionMs
            }

            // Programación justo a tiempo de la transición gapless
            if (!nextScheduled &&
                currentPlayWhenReady &&
                currentDurationMs != C.TIME_UNSET
            ) {
                val remaining = currentDurationMs - currentPositionMs
                if (remaining in 1..GAPLESS_SCHEDULE_MS) {
                    scheduleNextGapless(remaining)
                }
            }

            val previousState = currentPlaybackState

            when (active) {
                BASS.BASS_ACTIVE_PLAYING, BASS.BASS_ACTIVE_PAUSED -> {
                    // Solo estado; playWhenReady lo decide Media3
                    currentPlaybackState = Player.STATE_READY
                }
                BASS.BASS_ACTIVE_STALLED -> {
                    currentPlaybackState = Player.STATE_BUFFERING
                }
                BASSmix.BASS_ACTIVE_WAITING, BASSmix.BASS_ACTIVE_QUEUED -> {
                    // La siguiente pista espera su turno en el mixer:
                    // no alterar el estado reportado.
                }
                BASS.BASS_ACTIVE_STOPPED -> {
                    // Distingue fin natural de pista vs stop manual
                    if (currentDurationMs != C.TIME_UNSET &&
                        currentPositionMs >= currentDurationMs - END_THRESHOLD_MS &&
                        previousState == Player.STATE_READY
                    ) {
                        onTrackEnded()
                    } else if (previousState != Player.STATE_IDLE &&
                        previousState != Player.STATE_ENDED
                    ) {
                        currentPlaybackState = Player.STATE_IDLE
                    }
                }
                else -> { /* Estados desconocidos: ignorar */ }
            }

            invalidateState()
        }
    }

    /**
     * Avanza automáticamente al siguiente ítem de la playlist cuando
     * termina el actual. La siguiente pista ya está arrancando en el
     * punto exacto gracias a la programación gapless justo a tiempo.
     * Si no hay más ítems, marca [Player.STATE_ENDED].
     */
    private fun onTrackEnded() {
        val nextIndex = currentIndex + 1
        if (nextIndex < currentPlaylist.size) {
            currentIndex = nextIndex

            if (nextHandle != 0) {
                // La siguiente ya estaba en el mixer: promoverla a actual
                currentHandle = nextHandle
                nextHandle = 0
            } else {
                // Fallback (transición no llegó a programarse): crear y añadir ya
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

        /** Intervalo del polling de posición en milisegundos. */
        private const val POLLING_INTERVAL_MS = 500L

        /**
         * Umbral para considerar que una pista terminó: si la
         * posición está a menos de este valor del final y el estado
         * pasa a STOPPED, se considera fin natural.
         */
        private const val END_THRESHOLD_MS = 500L

        /** Sample rate por defecto para inicializar BASS. */
        private const val DEFAULT_SAMPLE_RATE = 44100

        /**
         * Antelación (ms) con la que se programa la siguiente pista para
         * garantizar gapless sin pre-cargar archivos al iniciar la
         * reproducción. 2000ms da margen de sobra para abrir el stream
         * decodificador incluso en archivos pesados.
         */
        private const val GAPLESS_SCHEDULE_MS = 2000L

        /**
         * Buffer de salida de BASS en milisegundos (se aplica antes de
         * BASS_Init). Valores bajos reducen la latencia de arranque y de
         * cada cambio de pista; 100ms es seguro en dispositivos modernos.
         */
        private const val OUTPUT_BUFFER_MS = 100

        /**
         * Periodo de update de BASS en milisegundos (se aplica antes de
         * BASS_Init). Junto con [OUTPUT_BUFFER_MS] define la latencia
         * total de salida.
         */
        private const val UPDATE_PERIOD_MS = 20
    }
}