package com.openplayer.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.openplayer.music.data.media.CoverRepository
import com.openplayer.music.data.model.Song
import com.openplayer.music.playback.engine.BassPlayerAdapter
import com.openplayer.music.playback.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controller centralizado de reproducción para toda la app.
 *
 * Gestiona la conexión/desconexión de [MediaController] con
 * [PlaybackService] y expone una API simple para controlar la
 * reproducción desde cualquier pantalla.
 *
 * ## Ventajas sobre MediaController local en cada pantalla
 * - **Una sola conexión**: evita que cada pantalla cree su propio
 *   MediaController (ahorra recursos y simplifica el ciclo de vida).
 * - **Flows reactivos compartidos**: `currentMediaId`, `isPlaying`,
 *   `currentPositionMs` y `durationMs` se actualizan en tiempo real
 *   y son consumidos por todas las pantallas (indicadores de pista
 *   actual, mini player futuro, etc.).
 * - **API simple**: `playSong(song, queueId, queueSongs)` construye
 *   la cola, busca el índice y llama a `setMediaItems` + `play`.
 * - **Thread-safe**: todos los Flows son StateFlow con emisión en Main.
 *
 * ## Uso
 * ```kotlin
 * val controller = (context.applicationContext as OpenPlayerApplication)
 *     .playbackController
 *
 * // Reproducir canción en cola de búsqueda
 * controller.playSong(song, BassPlayerAdapter.QUEUE_SEARCH, allSearchSongs)
 *
 * // Observar pista actual
 * controller.currentMediaId.collect { mediaId ->
 *     val isCurrentTrack = song.id.toString() == mediaId
 * }
 * ```
 *
 * ## Conexión automática
 * El controller se conecta a PlaybackService al primer uso (lazy
 * connection). Si la conexión falla, los métodos de control no hacen
 * nada (degradación elegante).
 */
class PlaybackController(
    private val context: Context,
    private val coverRepository: CoverRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** MediaController conectado a PlaybackService; null si aún no conectado. */
    private var mediaController: MediaController? = null

    // =========================================================================
    // Flows reactivos (consumidos por UI)
    // =========================================================================

    /** mediaId de la pista actualmente en reproducción (null si no hay nada sonando). */
    private val _currentMediaId = MutableStateFlow<String?>(null)
    val currentMediaId: StateFlow<String?> = _currentMediaId.asStateFlow()

    /** true si el reproductor está en estado de reproducción activa. */
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** Posición actual de reproducción en milisegundos. */
    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    /** Duración total de la pista actual en milisegundos. */
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    // =========================================================================
    // Listener del player (actualiza Flows)
    // =========================================================================

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaId.value = mediaItem?.mediaId
            _currentPositionMs.value = 0L
            mediaItem?.let { item ->
                _durationMs.value = mediaController?.duration ?: item.mediaMetadata.extras?.getLong("duration", 0L) ?: 0L
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE) {
                _currentMediaId.value = null
                _isPlaying.value = false
            }
        }
    }

    // =========================================================================
    // Conexión a PlaybackService
    // =========================================================================

    /**
     * Conecta el MediaController a PlaybackService si aún no está
     * conectado. Seguro para llamar múltiples veces (idempotente).
     */
    private fun ensureConnected() {
        if (mediaController != null) return

        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            if (future.isDone && !future.isCancelled) {
                mediaController = runCatching { future.get() }.getOrNull()
                mediaController?.let { ctrl ->
                    ctrl.addListener(playerListener)
                    // Inicializar estado con la pista actual
                    _currentMediaId.value = ctrl.currentMediaItem?.mediaId
                    _isPlaying.value = ctrl.isPlaying
                    _currentPositionMs.value = ctrl.currentPosition
                    _durationMs.value = ctrl.duration
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // =========================================================================
    // API pública: control de reproducción
    // =========================================================================

    /**
     * Reproduce [song] en el contexto de [queueId], construyendo la
     * cola completa con [queueSongs] y saltando directamente a la
     * canción objetivo.
     *
     * @param song Canción a reproducir.
     * @param queueId Identificador de la cola (ej. QUEUE_TRACKS_BY_DATE,
     *                QUEUE_SEARCH, QUEUE_RECENTLY_PLAYED).
     * @param queueSongs Lista completa de canciones de la cola en el
     *                   orden deseado. [song] debe estar en esta lista.
     */
    fun playSong(song: Song, queueId: String, queueSongs: List<Song>) {
        ensureConnected()
        val ctrl = mediaController ?: return

        scope.launch {
            val targetMediaId = song.id.toString()
            val startIndex = queueSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

            // Construir MediaItems con carátulas
            val mediaItems = withContext(Dispatchers.IO) {
                queueSongs.mapIndexed { i, s ->
                    val item = s.toMediaItem(coverRepository)
                    // Agregar queueId al tag del primer MediaItem
                    if (i == 0) {
                        MediaItem.Builder()
                            .setMediaId(item.mediaId)
                            .setUri(item.localConfiguration?.uri)
                            .setMediaMetadata(item.mediaMetadata)
                            .setTag(queueId)
                            .build()
                    } else {
                        item
                    }
                }
            }

            ctrl.setMediaItems(mediaItems, startIndex, 0L)
            ctrl.prepare()
            ctrl.play()
        }
    }

    /** Pausa la reproducción actual. */
    fun pause() {
        mediaController?.pause()
    }

    /** Reanuda la reproducción actual. */
    fun resume() {
        mediaController?.play()
    }

    /** Alterna entre pausa y reproducción. */
    fun togglePlayPause() {
        val ctrl = mediaController ?: return
        if (ctrl.isPlaying) pause() else resume()
    }

    /**
     * Salta a una posición específica en la pista actual.
     * @param positionMs Posición en milisegundos desde el inicio.
     */
    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    /** Libera el MediaController. Llamar desde onDestroy de la Application. */
    fun release() {
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        mediaController = null
    }
}