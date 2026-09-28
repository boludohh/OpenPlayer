package com.openplayer.music.playback.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.openplayer.music.MainActivity
import com.openplayer.music.OpenPlayerApplication
import com.openplayer.music.data.media.CoverRepository
import com.openplayer.music.data.media.PlaybackHistoryRepository
import com.openplayer.music.playback.engine.BassPlayerAdapter
import com.openplayer.music.playback.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio de reproducción en segundo plano de OpenPlayer.
 *
 * Extiende [MediaLibraryService] de Media3, lo que proporciona sin
 * trabajo adicional:
 * - Notificación del sistema con controles de reproducción.
 * - Servicio en primer plano mientras suena música (la reproducción
 *   NO muere al cerrar la app por completo).
 * - Integración con Android Auto, Bluetooth, Wear OS y controladores
 *   externos vía MediaSession.
 *
 * Responsabilidades propias:
 * - Crear el [MediaLibrarySession] con [BassPlayerAdapter] como player.
 * - Gestionar audio focus manualmente (SimpleBasePlayer no lo gestiona).
 * - Pausar la reproducción al desconectarse la salida de audio
 *   (ACTION_AUDIO_BECOMING_NOISY).
 * - Decidir si el servicio sobrevive en onTaskRemoved: si el usuario
 *   cierra la app mientras suena música, la reproducción continúa;
 *   si está pausada, el servicio se detiene.
 * - **Sincronización reactiva de playlist**: se suscribe al Flow de
 *   canciones de AudioRepository y actualiza automáticamente la cola
 *   cuando cambian los datos, sin interrumpir la reproducción en curso.
 * - **Registro de historial de reproducción**: engancha eventos del
 *   player (inicio, pausa, seek, cambio de canción, fin natural) a
 *   [PlaybackHistoryRepository] para trackear playCount, completedCount
 *   y playedMs en tiempo real.
 * - **Tracking en tiempo real**: polling cada 1 segundo mientras
 *   suena música para acumular playedMs sin esperar al flush de sesión.
 * - **Detección de reproducción completa**: usa [Player.Listener.onMediaItemTransition]
 *   con el parámetro `reason` para distinguir entre fin natural
 *   (reason = AUTO/REPEAT) y seeks/cambios manuales. Solo cuenta como
 *   completada si se escuchó al menos el 90% de la canción.
 *
 * ## Optimizaciones de rendimiento
 * - El mapeo de canciones a MediaItems se ejecuta en `Dispatchers.IO`
 *   para no bloquear el hilo principal durante syncs de biblioteca.
 * - Usa el `CoverRepository` singleton de la Application, compartiendo
 *   el caché de memoria con el resto de la app (evita duplicados).
 *
 * Nota de API (Media3 1.11.0): [MediaLibrarySession] es una clase
 * anidada dentro de [MediaLibraryService], por eso se importa como
 * `MediaLibraryService.MediaLibrarySession`.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private var player: BassPlayerAdapter? = null

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /** Request de focus activo mientras BASS reproduce; null si no. */
    private var bassFocusRequest: AudioFocusRequest? = null

    /** Scope dedicado a la suscripción reactiva de la biblioteca. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Job de la suscripción al Flow de canciones. */
    private var librarySubscriptionJob: Job? = null

    /** Job del polling de tracking en tiempo real. */
    private var trackingJob: Job? = null

    /**
     * Última posición leída en el polling de tracking. Usado para
     * calcular el delta de milisegundos entre lecturas consecutivas.
     */
    private var lastTrackedPositionMs: Long = 0L

    /**
     * Máxima posición alcanzada en la canción actual. Usado para
     * detectar si el usuario hizo seek al final (no cuenta como
     * reproducción completada).
     */
    private var maxPositionReachedMs: Long = 0L

    /**
     * ID de la canción actualmente en tracking. Si cambia, se reinicia
     * lastTrackedPositionMs y maxPositionReachedMs.
     */
    private var currentTrackedSongId: Long? = null

    /**
     * ID de la canción que estaba sonando antes del último cambio.
     * Se usa en [Player.Listener.onMediaItemTransition] para verificar
     * si la canción anterior debe contar como completada.
     */
    private var previousMediaId: Long? = null

    /**
     * Duración de la canción anterior (la que terminó justo antes del
     * último transition). Se usa junto con [maxPositionReachedMs] para
     * validar el umbral del 90% de completado.
     */
    private var previousDurationMs: Long = 0L

    /**
     * Repositorio de portadas para construir MediaItems.
     * Reutiliza el singleton de la Application para compartir el caché
     * de memoria con la UI y evitar duplicados.
     */
    private val coverRepository: CoverRepository
        get() = (application as OpenPlayerApplication).coverRepository

    /**
     * Repositorio de historial de reproducción.
     * Reutiliza el singleton de la Application para compartir el acceso
     * a play_stats con HomeScreen y otros consumidores.
     */
    private val playbackHistoryRepository: PlaybackHistoryRepository
        get() = (application as OpenPlayerApplication).playbackHistoryRepository

    // =========================================================================
    // Audio focus para BASS
    // =========================================================================

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.pause()
        }
    }

    private fun requestBassFocus() {
        if (bassFocusRequest != null) return
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        audioManager.requestAudioFocus(request)
        bassFocusRequest = request
    }

    private fun abandonBassFocus() {
        bassFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        bassFocusRequest = null
    }

    // =========================================================================
    // Becoming noisy (auriculares desconectados)
    // =========================================================================

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                player?.pause()
            }
        }
    }

    private var noisyReceiverRegistered = false

    // =========================================================================
    // Listener del player (audio focus + historial de reproducción)
    // =========================================================================

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                requestBassFocus()
                startTracking()
            } else {
                abandonBassFocus()
                stopTracking()
                // Al pausar, flush de la sesión actual (acumular playedMs)
                player?.let { p ->
                    val currentPosition = p.currentPosition
                    val mediaId = p.currentMediaItem?.mediaId?.toLongOrNull()
                    if (mediaId != null) {
                        serviceScope.launch {
                            playbackHistoryRepository.flushSession(currentPosition)
                        }
                    }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            player?.let { p ->
                // ── Verificar si la canción ANTERIOR debe contar como completada ──
                // Solo cuenta si el transition fue por fin natural (AUTO/REPEAT),
                // NO si fue por seek manual o cambio de playlist.
                // BassPlayerAdapter no dispara STATE_ENDED entre canciones
                // (solo al final de la cola), por eso usamos este callback.
                if (previousMediaId != null &&
                    (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                     reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
                ) {
                    val completionThreshold = if (previousDurationMs > 0) {
                        (previousDurationMs * COMPLETION_THRESHOLD_RATIO).toLong()
                    } else {
                        0L
                    }
                    // maxPositionReachedMs tiene la posición más alta de la canción
                    // que acaba de terminar (se acumuló durante el tracking)
                    if (maxPositionReachedMs >= completionThreshold && previousDurationMs > 0) {
                        val songIdToComplete = previousMediaId!!
                        serviceScope.launch {
                            playbackHistoryRepository.recordCompleted(songIdToComplete)
                        }
                    }
                }

                // ── Flush de sesión anterior (playedMs restante) ──
                // Usamos previousMediaId porque p.currentMediaItem YA es la nueva canción
                if (previousMediaId != null) {
                    val finalPosition = maxPositionReachedMs
                    serviceScope.launch {
                        playbackHistoryRepository.flushSession(finalPosition)
                    }
                }

                // ── Preparar estado para la nueva canción ──
                // Guardar info de la nueva canción (será la "anterior" cuando cambie)
                previousMediaId = mediaItem?.mediaId?.toLongOrNull()
                previousDurationMs = p.duration.takeIf { it > 0 } ?: 0L

                // Reiniciar tracking state para la nueva canción
                lastTrackedPositionMs = 0L
                maxPositionReachedMs = 0L
                currentTrackedSongId = null

                // ── Inicio de nueva sesión ──
                val newMediaId = mediaItem?.mediaId?.toLongOrNull()
                if (newMediaId != null) {
                    serviceScope.launch {
                        playbackHistoryRepository.recordPlayStart(newMediaId, 0L)
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // STATE_ENDED: solo se dispara cuando termina la ÚLTIMA canción
            // de la cola (BassPlayerAdapter no lo pone entre canciones por gapless).
            // Sirve como respaldo para ese caso edge.
            if (playbackState == Player.STATE_ENDED) {
                player?.let { p ->
                    val currentPosition = p.currentPosition
                    val duration = p.duration.takeIf { it > 0 } ?: previousDurationMs
                    val mediaId = p.currentMediaItem?.mediaId?.toLongOrNull()
                        ?: previousMediaId
                    if (mediaId != null) {
                        serviceScope.launch {
                            // Flush final de playedMs
                            playbackHistoryRepository.flushSession(currentPosition)

                            // Verificar umbral de completado (90% de la canción)
                            val completionThreshold = if (duration > 0) {
                                (duration * COMPLETION_THRESHOLD_RATIO).toLong()
                            } else {
                                0L
                            }
                            if (maxPositionReachedMs >= completionThreshold && duration > 0) {
                                playbackHistoryRepository.recordCompleted(mediaId)
                            }
                        }
                    }

                    // Reiniciar tracking state
                    lastTrackedPositionMs = 0L
                    maxPositionReachedMs = 0L
                    currentTrackedSongId = null
                    previousMediaId = null
                    previousDurationMs = 0L
                }
            }
        }
    }

    // =========================================================================
    // Tracking en tiempo real
    // =========================================================================

    /**
     * Inicia el polling de tracking que cada 1 segundo acumula el
     * tiempo escuchado en playedMs. Solo corre mientras suena música.
     * Es idempotente: no crea un segundo job si ya hay uno activo.
     */
    private fun startTracking() {
        if (trackingJob?.isActive == true) return
        trackingJob = serviceScope.launch {
            while (isActive) {
                tickTracking()
                delay(TRACKING_INTERVAL_MS)
            }
        }
    }

    /** Detiene el polling de tracking. */
    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    /**
     * Un tick del polling: lee la posición actual del player, calcula
     * el delta desde la última lectura y acumula playedMs en la BD.
     *
     * Lógica de delta:
     * - Si el delta es <= 0 (seek hacia atrás o pausa), no se acumula.
     * - Si el delta es > 5 segundos (seek hacia adelante), se descarta
     *   porque no representa tiempo realmente escuchado.
     * - Si el delta está en rango razonable (1ms a 5000ms), se acumula.
     *
     * También actualiza maxPositionReachedMs para la detección de
     * reproducción completa cuando la canción termine.
     */
    private suspend fun tickTracking() {
        val p = player ?: return
        if (!p.isPlaying) return

        val currentPosition = p.currentPosition
        val mediaId = p.currentMediaItem?.mediaId?.toLongOrNull() ?: return

        // Si cambió la canción, reiniciar estado
        if (mediaId != currentTrackedSongId) {
            lastTrackedPositionMs = currentPosition
            currentTrackedSongId = mediaId
            maxPositionReachedMs = currentPosition
            return
        }

        // Actualizar máxima posición alcanzada (para detectar seeks al final)
        if (currentPosition > maxPositionReachedMs) {
            maxPositionReachedMs = currentPosition
        }

        // También actualizar previousDurationMs si el player ahora la conoce
        // (a veces BASS tarda unos ms en reportar la duración correcta)
        val knownDuration = p.duration
        if (knownDuration > 0 && previousDurationMs <= 0) {
            previousDurationMs = knownDuration
        }

        // Calcular delta y acumular si es razonable
        val delta = currentPosition - lastTrackedPositionMs
        if (delta in 1..MAX_REASONABLE_DELTA_MS) {
            playbackHistoryRepository.accumulatePlayedMs(mediaId, delta)
        }

        lastTrackedPositionMs = currentPosition
    }

    // =========================================================================
    // Ciclo de vida del servicio
    // =========================================================================

    override fun onCreate() {
        super.onCreate()

        val bassPlayer = BassPlayerAdapter(this, Looper.getMainLooper())
        bassPlayer.addListener(playerListener)
        player = bassPlayer

        // Tocar la notificación abre MainActivity
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(this, bassPlayer, LibraryCallback())
            .setSessionActivity(sessionActivity)
            .build()

        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyReceiverRegistered = true

        // Suscribirse a cambios en la biblioteca para mantener la playlist sincronizada
        subscribeToLibraryChanges()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si el usuario cierra la app con música sonando, la reproducción
        // continúa (servicio en primer plano). Si está pausada o vacía,
        // el servicio se detiene para no quedar vivo innecesariamente.
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Cancelar suscripción reactiva y tracking
        librarySubscriptionJob?.cancel()
        librarySubscriptionJob = null
        stopTracking()
        serviceScope.cancel()

        if (noisyReceiverRegistered) {
            unregisterReceiver(noisyReceiver)
            noisyReceiverRegistered = false
        }
        abandonBassFocus()
        player?.removeListener(playerListener)
        player?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    // =========================================================================
    // Sincronización reactiva de playlist
    // =========================================================================

    /**
     * Se suscribe al Flow de canciones de AudioRepository y actualiza
     * la playlist del adapter cada vez que la biblioteca cambia.
     *
     * El adapter ([BassPlayerAdapter]) se encarga internamente de:
     - Reemplazar completamente la playlist si el queueId actual es
     *   QUEUE_LIBRARY (cola global de biblioteca).
     - Hacer merge reactivo si el queueId es personalizado (ej.
     *   QUEUE_TRACKS_BY_DATE): actualiza metadatos, elimina borrados,
     *   conserva el orden, sin interrumpir la reproducción actual.
     *
     * **Optimización**: el mapeo de canciones a MediaItems (que incluye
     * stats de disco para verificar carátulas) se ejecuta en
     * `Dispatchers.IO` para no bloquear el hilo principal. Solo vuelve
     * a Main para entregar la lista al adapter.
     *
     * Usa [collectLatest] para cancelar automáticamente la emisión
     * anterior si llega una nueva antes de terminar el mapeo, evitando
     * trabajo redundante.
     */
    private fun subscribeToLibraryChanges() {
        val audioRepository = (application as OpenPlayerApplication).audioRepository
        val bassPlayer = player ?: return

        librarySubscriptionJob = serviceScope.launch {
            audioRepository.songs.collectLatest { songs ->
                // Mapeo en IO: cada toMediaItem hace stats de disco para
                // verificar si la carátula existe. Con bibliotecas grandes
                // esto puede ser costoso, por eso se mueve fuera de Main.
                val mediaItems = withContext(Dispatchers.IO) {
                    songs.map { it.toMediaItem(coverRepository) }
                }
                bassPlayer.updateLibraryPlaylist(mediaItems)
            }
        }
    }

    /**
     * Callback de la sesión. Por ahora se deja el comportamiento por
     * defecto de Media3: la navegación de biblioteca para Android Auto
     * se implementará en una fase futura; la reproducción, notificación
     * y controles externos ya funcionan con la sesión actual.
     */
    private class LibraryCallback : MediaLibrarySession.Callback

    // =========================================================================
    // Constantes
    // =========================================================================

    private companion object {
        /** Intervalo del polling de tracking en milisegundos. */
        const val TRACKING_INTERVAL_MS = 1000L

        /**
         * Delta máximo razonable entre dos lecturas consecutivas del
         * polling. Si el delta es mayor, se considera un seek hacia
         * adelante y no se acumula como tiempo escuchado.
         *
         * 5 segundos de margen: cubre el caso de que el polling se
         * retrasara temporalmente (GC pause, etc.) sin falsos positivos.
         */
        const val MAX_REASONABLE_DELTA_MS = 5000L

        /**
         * Porcentaje mínimo de la canción que debe haberse escuchado
         * para contar como reproducción completada (0.90 = 90%).
         *
         * Con esto, si un usuario hace seek al último segundo de una
         * canción de 3 minutos, maxPositionReachedMs será bajo y no
         * contará como completada. Si la escucha completa, sí cuenta.
         */
        const val COMPLETION_THRESHOLD_RATIO = 0.90
    }
}