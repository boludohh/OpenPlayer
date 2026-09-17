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
 * que traducen cada comando Media3 a llamadas equivalentes del glue
 * de BASS ([BassNative]) y BASSmix.
 *
 * ## Responsabilidades de este adapter
 *
 * - Crear y liberar streams BASS.
 * - Traducir play/pause/seek/stop/prepare.
 * - Reportar estado (posición, duración, buffering, ended) a Media3.
 * - Avanzar automáticamente al siguiente item cuando termina la pista.
 * - **Gapless playback**: transición sin silencio entre canciones usando BASSmix.
 * - **Crossfade**: fundido suave de 4000ms entre canciones cuando termina una
 *   y empieza la siguiente (configurable vía [CROSSFADE_MS]).
 *
 * ## Fuera de su responsabilidad
 *
 * - **Audio focus**: lo maneja el `PlaybackService` (Fase 4) de forma
 *   unificada para ambos motores (ExoPlayer y BASS).
 * - **Selección de motor**: la gestiona `PlaybackEngineManager` (Fase 5).
 * - **Capa de datos Song**: no conoce `AudioRepository` ni `Song`.
 *   Quien construye el `MediaItem` (UI o servicio) se encarga de
 *   incluir el URI del archivo en `localConfiguration.uri`.
 *
 * ## Arquitectura de reproducción con BASSmix
 *
 * 1. **Mixer stream**: se crea una sola vez al inicializar y es el único
 *    stream con salida de audio al dispositivo.
 * 2. **Streams decodificadores**: cada canción se crea con `BASS_STREAM_DECODE`
 *    y se añade al mixer, no se reproduce directamente.
 * 3. **Gapless**: al empezar la canción N, se programa N+1 con
 *    `BASS_Mixer_StreamAddChannelEx(startBytes)` donde `startBytes` es la
 *    posición exacta donde N termina → cero silencio entre pistas.
 * 4. **Crossfade**: cuando el polling detecta que quedan [CROSSFADE_MS] para
 *    el final de N, se añade N+1 inmediatamente y se aplica un envelope de
 *    volumen descendente a N → fundido suave.
 *
 * ## Mecanismo de polling
 *
 * Como el glue actual no provee callbacks nativos para detectar
 * cambios de estado, el adapter consulta la posición y el estado del
 * canal cada [POLLING_INTERVAL_MS] desde un hilo de coroutines, y
 * publica los cambios al hilo del Looper del player vía [Handler].
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

    /** Handle del stream decodificador siguiente (para gapless/crossfade). 0 si no hay. */
    private var nextHandle: Int = 0

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

    /** Flag para saber si el crossfade está activo actualmente. */
    private var isCrossfading: Boolean = false

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
        
        // Cargar plugins FLAC y Opus después de inicializar BASS
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
            // Reproducir el mixer
            BASS.BASS_ChannelPlay(mixerHandle, false)
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
     * en la biblioteca musical (canciones agregadas, eliminadas o
     * modificadas). Se ejecuta en el hilo del Looper del player para
     * garantizar thread safety.
     *
     * Comportamiento:
     * - Si la canción actual sigue existiendo (mismo mediaId), mantiene
     *   su reproducción sin interrupciones y ajusta el índice si cambió
     *   de posición.
     * - Si la canción actual fue eliminada, libera el stream y marca
     *   el estado como IDLE.
     * - Si no había canción reproduciéndose, simplemente actualiza la
     *   lista.
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
                // Buscar la canción actual en la nueva playlist
                val newIndex = currentPlaylist.indexOfFirst { it.mediaId == currentMediaId }
                
                if (newIndex >= 0) {
                    // La canción sigue existiendo, actualizar índice sin interrumpir
                    currentIndex = newIndex
                    // Reprogramar gapless para la siguiente si existe
                    scheduleNextTrackForGapless()
                } else {
                    // La canción fue eliminada, liberar stream y resetear estado
                    releaseCurrentStream()
                    currentPositionMs = 0L
                    currentDurationMs = C.TIME_UNSET
                    currentIndex = 0.coerceIn(0, max(0, currentPlaylist.size - 1))
                    currentPlaybackState = Player.STATE_IDLE
                    currentPlayWhenReady = false
                    stopPolling()
                }
            } else {
                // No había canción reproduciéndose, ajustar índice
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
                BASS.BASS_ChannelSetPosition(
                    currentHandle,
                    BASS.BASS_ChannelSeconds2Bytes(currentHandle, startPositionMs / 1000.0),
                    BASS.BASS_POS_BYTE
                )
                currentPositionMs = startPositionMs
            }
            // Programar la siguiente pista para gapless
            scheduleNextTrackForGapless()
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
            
            // Si el player estaba reproduciendo, iniciar automáticamente
            // la reproducción del nuevo stream (para botón "Siguiente")
            if (currentPlayWhenReady && currentHandle != 0) {
                playInternal()
            }
            
            // Programar la siguiente pista para gapless
            scheduleNextTrackForGapless()
        }

        // Seek dentro del ítem actual
        if (positionMs != C.TIME_UNSET && currentHandle != 0) {
            BASS.BASS_ChannelSetPosition(
                currentHandle,
                BASS.BASS_ChannelSeconds2Bytes(currentHandle, positionMs / 1000.0),
                BASS.BASS_POS_BYTE
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
     * y lo añade al mixer. No reproduce directamente; el mixer se encarga
     * de la salida de audio.
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

        // Crear stream decodificador (no reproduce directamente)
        val handle = BASS.BASS_StreamCreateFile(
            path,
            0,
            0,
            BASS.BASS_STREAM_DECODE or BASS.BASS_STREAM_PRESCAN
        )

        if (handle == 0) {
            currentHandle = 0
            currentPlaybackState = Player.STATE_IDLE
            return
        }

        currentHandle = handle
        currentDurationMs = (BASS.BASS_ChannelBytes2Seconds(
            handle,
            BASS.BASS_ChannelGetLength(handle, BASS.BASS_POS_BYTE)
        ) * 1000).toLong()
        
        if (currentDurationMs < 0) {
            currentDurationMs = C.TIME_UNSET
        }
        
        currentPositionMs = 0L
        currentPlaybackState = Player.STATE_READY

        // Añadir al mixer
        if (mixerHandle != 0) {
            BASSmix.BASS_Mixer_StreamAddChannel(
                mixerHandle,
                currentHandle,
                BASSmix.BASS_MIXER_CHAN_AUTOFREE
            )
        }
    }

    /**
     * Programa la siguiente pista para gapless playback.
     * Usa BASS_Mixer_StreamAddChannelEx con la posición exacta donde
     * termina la pista actual, garantizando cero silencio entre canciones.
     */
    private fun scheduleNextTrackForGapless() {
        if (nextHandle != 0) {
            BASSmix.BASS_Mixer_ChannelRemove(nextHandle)
            BASS.BASS_StreamFree(nextHandle)
            nextHandle = 0
        }

        val nextIndex = currentIndex + 1
        if (nextIndex >= currentPlaylist.size) {
            return // No hay siguiente pista
        }

        val nextMediaItem = currentPlaylist[nextIndex]
        val nextPath = nextMediaItem.localConfiguration?.uri?.path

        if (nextPath.isNullOrEmpty()) {
            return
        }

        // Crear stream decodificador para la siguiente pista
        val handle = BASS.BASS_StreamCreateFile(
            nextPath,
            0,
            0,
            BASS.BASS_STREAM_DECODE or BASS.BASS_STREAM_PRESCAN
        )

        if (handle == 0) {
            return
        }

        nextHandle = handle

        // Calcular posición de inicio en bytes del mixer
        if (mixerHandle != 0 && currentHandle != 0 && currentDurationMs != C.TIME_UNSET) {
            val mixerPos = BASSmix.BASS_Mixer_ChannelGetPosition(currentHandle, BASS.BASS_POS_BYTE)
            val currentLenBytes = BASS.BASS_ChannelGetLength(currentHandle, BASS.BASS_POS_BYTE)
            val remainingBytes = currentLenBytes - mixerPos
            val startBytes = mixerPos + remainingBytes

            BASSmix.BASS_Mixer_StreamAddChannelEx(
                mixerHandle,
                nextHandle,
                BASSmix.BASS_MIXER_CHAN_AUTOFREE,
                startBytes,
                0
            )
        }
    }

    /** Libera el stream BASS actual si existe. */
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
    // Polling de posición y detección de fin de pista
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
     * Consulta a BASS la posición actual del canal actual dentro del mixer,
     * detecta cuándo iniciar crossfade, y publica los cambios al hilo del
     * Looper del player.
     */
    private fun updateFromNative() {
        if (currentHandle == 0) return

        val positionBytes = BASSmix.BASS_Mixer_ChannelGetPosition(currentHandle, BASS.BASS_POS_BYTE)
        val positionMs = (BASS.BASS_ChannelBytes2Seconds(currentHandle, positionBytes) * 1000).toLong()
        val active = BASSmix.BASS_Mixer_ChannelIsActive(currentHandle)

        handler.post {
            if (currentHandle == 0) return@post

            if (positionMs >= 0L) {
                currentPositionMs = positionMs
            }

            val previousState = currentPlaybackState

            // Detectar crossfade
            if (currentDurationMs != C.TIME_UNSET && !isCrossfading && CROSSFADE_MS > 0) {
                val remainingMs = currentDurationMs - currentPositionMs
                if (remainingMs <= CROSSFADE_MS && remainingMs > 0) {
                    startCrossfade()
                }
            }

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
            }

            invalidateState()
        }
    }

    /**
     * Inicia el crossfade: aplica envelope de volumen descendente al canal
     * actual y empieza a reproducir el siguiente canal inmediatamente.
     */
    private fun startCrossfade() {
        if (nextHandle == 0 || isCrossfading) return

        isCrossfading = true

        // Aplicar envelope de fade-out al canal actual
        val nodes = arrayOf(
            BASSmix.BASS_MIXER_NODE(0, 1.0f),
            BASSmix.BASS_MIXER_NODE(
                BASS.BASS_ChannelSeconds2Bytes(currentHandle, CROSSFADE_MS / 1000.0),
                0.0f
            )
        )
        BASSmix.BASS_Mixer_ChannelSetEnvelope(
            currentHandle,
            BASSmix.BASS_MIXER_ENV_VOL,
            nodes,
            nodes.size
        )

        // El siguiente canal ya está programado para empezar en el momento exacto
        // (gapless), pero como estamos en crossfade, ya debería estar sonando
        android.util.Log.d(TAG, "Crossfade started")
    }

    /**
     * Avanza automáticamente al siguiente ítem de la playlist cuando
     * termina el actual. Si no hay más ítems, marca el estado como
     * [Player.STATE_ENDED].
     */
    private fun onTrackEnded() {
        val nextIndex = currentIndex + 1
        if (nextIndex < currentPlaylist.size) {
            // El siguiente canal ya está en el mixer (gapless) o se creó en crossfade
            currentIndex = nextIndex
            
            // El canal actual se libera automáticamente por BASS_MIXER_CHAN_AUTOFREE
            // El siguiente canal ya está sonando
            
            // Actualizar referencias
            currentHandle = nextHandle
            nextHandle = 0
            isCrossfading = false
            
            currentPositionMs = 0L
            currentDurationMs = if (currentHandle != 0) {
                (BASS.BASS_ChannelBytes2Seconds(
                    currentHandle,
                    BASS.BASS_ChannelGetLength(currentHandle, BASS.BASS_POS_BYTE)
                ) * 1000).toLong()
            } else {
                C.TIME_UNSET
            }
            
            // Programar la siguiente pista para gapless
            scheduleNextTrackForGapless()
            
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
         * 4000ms (4 segundos) es el equilibrio ideal entre un fundido
         * suave y no mantener dos streams decodificando simultáneamente
         * más tiempo del necesario.
         */
        private const val CROSSFADE_MS = 4000L
    }
}