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
 *   y playedMs.
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
            } else {
                abandonBassFocus()
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
            // Al cambiar de canción: flush sesión anterior + inicio de nueva
            player?.let { p ->
                val previousPosition = p.currentPosition
                val previousMediaId = mediaItem?.mediaId?.toLongOrNull()
                
                // Flush sesión anterior (si había una)
                if (previousMediaId != null) {
                    serviceScope.launch {
                        playbackHistoryRepository.flushSession(previousPosition)
                    }
                }

                // Inicio de nueva sesión (si hay nueva canción)
                val newMediaId = mediaItem?.mediaId?.toLongOrNull()
                if (newMediaId != null) {
                    serviceScope.launch {
                        playbackHistoryRepository.recordPlayStart(newMediaId, 0L)
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // STATE_ENDED: canción terminó completamente
            if (playbackState == Player.STATE_ENDED) {
                player?.let { p ->
                    val currentPosition = p.currentPosition
                    val mediaId = p.currentMediaItem?.mediaId?.toLongOrNull()
                    if (mediaId != null) {
                        serviceScope.launch {
                            // Flush final + completedCount +1
                            playbackHistoryRepository.flushSession(currentPosition)
                            playbackHistoryRepository.recordCompleted(mediaId)
                        }
                    }
                }
            }
        }
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
        // Cancelar suscripción reactiva
        librarySubscriptionJob?.cancel()
        librarySubscriptionJob = null
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
}