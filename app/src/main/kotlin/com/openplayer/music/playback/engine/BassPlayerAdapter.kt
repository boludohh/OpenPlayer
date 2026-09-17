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
 * [BASS] / [BASSmix] para streams, mixer, crossfade y gapless.
 *
 * ## Arquitectura de reproducción con BASSmix
 *
 * 1. **Mixer stream**: se crea una sola vez al inicializar y es el único
 *    stream con salida de audio al dispositivo.
 * 2. **Streams decodificadores**: cada canción se crea con
 *    `BASS_STREAM_DECODE` (sin PRESCAN, para inicio instantáneo) y se
 *    añade al mixer, no se reproduce directamente.
 * 3. **Programación justo a tiempo**: el polling detecta cuánto falta
 *    para el final de la pista y, en ese momento, prepara la siguiente:
 *    - Con crossfade (> 0 ms): la añade YA al mixer con envelope de
 *      volumen 0→1, y aplica envelope 1→0 a la actual (fundido solapado).
 *    - Sin crossfade: la añade con `BASS_Mixer_StreamAddChannelEx` usando
 *      un `start` RELATIVO en bytes del mixer equivalente al tiempo
 *      restante → arranca exactamente cuando termina la actual (gapless).
 * 4. **Seeks**: se realizan con `BASS_Mixer_ChannelSetPosition` (flush del
 *    buffer del mixer incluido) y cancelan cualquier transición pendiente,
 *    que el polling reprograma automáticamente después.
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

    /** Handle del mixer BASS. 0 significa que no hay mixer creado. */
    private var mixerHandle: Int = 0

    /** Handle del stream decodificador actual. 0 si no hay stream. */
    private var currentHandle: Int = 0

    /** Handle del stream decodificador siguiente (transición). 0 si no hay. */
    private var nextHandle: Int = 0

    /**
     * True si la transición de la pista actual ya fue programada
     * (crossfade o gapless), o si ya se decidió que no hay siguiente.
     * Evita reprogramar en cada ciclo de polling.
     */
    private var nextScheduled: Boolean = false

    /** Flag para saber si hay un crossfade activo actualmente. */
    private var isCrossfading: Boolean = false

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
     * READY, el adapter debe estar reproduciendo.
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
        // Inicializa BASS con sample rate estándar. BassNative usa
        // System.loadLibrary que es idempotente; la inicialización
        // del engine también lo es internamente.
        bassInitialized = BassNative.init(DEFAULT_SAMPLE_RATE)

        // Cargar plugins FLAC, Opus y AAC después de inicializar BASS
        if (bassInitialized) {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            BassNative.loadPlugins(nativeLibDir)
        }

        // Crear el mixer stream una sola vez
        if (bassInitialized) {
            createMixer()
        }
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
            if (startPositionMs > 0L && currentHandle != 0) {
                val bytes = BASS.BASS_ChannelSeconds2Bytes(
                    currentHandle,
                    startPositionMs / 1000.0
                )
                BASSmix.BASS_Mixer_ChannelSetPosition(
                    currentHandle,
                    bytes,
                    BASS.BASS_POS_BYTE or BASSmix.BASS_POS_MIXER_RESET
                )
                currentPositionMs = startPositionMs
            }
            // La transición a la siguiente pista la programa el polling
            // justo a tiempo; aquí no se pre-programa nada.
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

            // Si el player estaba reproduciendo, asegurar mixer activo y polling
            if (currentPlayWhenReady && currentHandle != 0) {
                playInternal()
            }
        } else if (positionMs != C.TIME_UNSET && currentHandle != 0) {
            // Seek dentro del ítem actual: cancelar transición pendiente
            // (su posición de inicio quedó invalidada) y restaurar volumen
            // si había un crossfade en curso.
            val wasCrossfading = isCrossfading
            cancelPendingNext()
            if (wasCrossfading) {
                restoreCurrentVolume()
            }

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
        isCrossfading = false

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
     * Programa la transición a la siguiente pista justo a tiempo.
     *
     * Con crossfade activo ([CROSSFADE_MS] > 0): añade la siguiente YA al
     * mixer con envelope de volumen 0→1 y aplica envelope 1→0 a la actual.
     * Sin crossfade: añade la siguiente con start RELATIVO en bytes del
     * mixer equivalente al tiempo restante → gapless exacto.
     *
     * @param remainingMs Milisegundos restantes de la pista actual.
     */
    private fun scheduleNextForTransition(remainingMs: Long) {
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

        if (CROSSFADE_MS > 0) {
            // Crossfade: la siguiente entra YA con fade-in, la actual sale con fade-out
            BASSmix.BASS_Mixer_StreamAddChannel(
                mixerHandle,
                nextHandle,
                BASSmix.BASS_MIXER_CHAN_AUTOFREE
            )

            val fadeInBytes = BASS.BASS_ChannelSeconds2Bytes(nextHandle, CROSSFADE_MS / 1000.0)
            BASSmix.BASS_Mixer_ChannelSetEnvelope(
                nextHandle,
                BASSmix.BASS_MIXER_ENV_VOL,
                arrayOf(
                    BASSmix.BASS_MIXER_NODE(0, 0.0f),
                    BASSmix.BASS_MIXER_NODE(fadeInBytes, 1.0f)
                ),
                2
            )

            if (currentHandle != 0) {
                val fadeOutBytes = BASS.BASS_ChannelSeconds2Bytes(currentHandle, CROSSFADE_MS / 1000.0)
                BASSmix.BASS_Mixer_ChannelSetEnvelope(
                    currentHandle,
                    BASSmix.BASS_MIXER_ENV_VOL,
                    arrayOf(
                        BASSmix.BASS_MIXER_NODE(0, 1.0f),
                        BASSmix.BASS_MIXER_NODE(fadeOutBytes, 0.0f)
                    ),
                    2
                )
            }
            isCrossfading = true
            android.util.Log.d(TAG, "Crossfade started")
        } else {
            // Gapless puro: start RELATIVO en bytes del mixer
            val startBytes = BASS.BASS_ChannelSeconds2Bytes(mixerHandle, remainingMs / 1000.0)
            BASSmix.BASS_Mixer_StreamAddChannelEx(
                mixerHandle,
                nextHandle,
                BASSmix.BASS_MIXER_CHAN_AUTOFREE,
                startBytes,
                0
            )
        }
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
        isCrossfading = false
    }

    /**
     * Restaura el volumen completo de la pista actual mediante un
     * envelope de un solo nodo. Se usa al cancelar un crossfade en
     * curso (por ejemplo, durante un seek).
     */
    private fun restoreCurrentVolume() {
        if (currentHandle != 0) {
            BASSmix.BASS_Mixer_ChannelSetEnvelope(
                currentHandle,
                BASSmix.BASS_MIXER_ENV_VOL,
                arrayOf(BASSmix.BASS_MIXER_NODE(0, 1.0f)),
                1
            )
        }
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
        isCrossfading = false
    }

    /** Reproduce el mixer y arranca el polling de posición. */
    private fun playInternal() {
        if (mixerHandle == 0) return
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
     * programa justo a tiempo la transición a la siguiente pista
     * (crossfade o gapless) y publica los cambios al hilo del Looper
     * del player.
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

            // Programación justo a tiempo de la transición
            if (!nextScheduled &&
                currentPlayWhenReady &&
                currentDurationMs != C.TIME_UNSET
            ) {
                val threshold = if (CROSSFADE_MS > 0) CROSSFADE_MS else GAPLESS_SCHEDULE_MS
                val remaining = currentDurationMs - currentPositionMs
                if (remaining in 1..threshold) {
                    scheduleNextForTransition(remaining)
                }
            }

            val previousState = currentPlaybackState

            when (active) {
                BASS.BASS_ACTIVE_PLAYING -> {
                    currentPlaybackState = Player.STATE_READY
                    currentPlayWhenReady = true
                }
                BASS.BASS_ACTIVE_PAUSED -> {
                    currentPlaybackState = Player.STATE_READY
                    currentPlayWhenReady = false
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
     * termina el actual. La siguiente pista ya está sonando (crossfade)
     * o arrancando en el punto exacto (gapless) gracias a la programación
     * justo a tiempo. Si no hay más ítems, marca [Player.STATE_ENDED].
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
            isCrossfading = false
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
         * Duración del crossfade en milisegundos.
         * 4000ms es el equilibrio ideal entre un fundido suave y no
         * mantener dos streams decodificando simultáneamente más tiempo
         * del necesario. Con 0 se desactiva y queda gapless puro.
         */
        private const val CROSSFADE_MS = 4000L

        /**
         * Antelación (ms) con la que se programa la siguiente pista cuando
         * el crossfade está desactivado, para garantizar gapless sin
         * pre-cargar archivos al iniciar la reproducción.
         */
        private const val GAPLESS_SCHEDULE_MS = 1500L
    }
}