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
import com.openplayer.music.playback.engine.BassPlayerAdapter
import com.openplayer.music.playback.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
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
 * - **Sincronización reactiva global de playlist**: observa el queueId
 *   del adapter y se suscribe al Flow de canciones correspondiente
 *   (biblioteca completa, filtrada por álbum, por artista, etc.),
 *   actualizando automáticamente la cola cuando cambian los datos.
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
    // Listener del player (audio focus)
    // =========================================================================

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) requestBassFocus() else abandonBassFocus()
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
    // Sincronización reactiva global de playlist
    // =========================================================================

    /**
     * Observa el queueId del adapter y se suscribe dinámicamente al
     * Flow de canciones correspondiente. Cuando el queueId cambia
     * (ej: usuario toca una canción en una pantalla diferente), cancela
     * la suscripción anterior y crea una nueva según el nuevo contexto.
     *
     * Tipos de queueId soportados:
     * - "library": biblioteca completa ordenada por título
     * - "tracksByDate": biblioteca completa ordenada por fecha descendente
     * - "album:{nombre}": canciones de un álbum específico, ordenadas por trackNumber
     * - "artist:{nombre}": canciones de un artista específico, ordenadas por título
     * - "playlist:{id}": (futuro) playlist personalizada
     *
     * Usa [collectLatest] para cancelar automáticamente la suscripción
     * anterior cuando cambia el queueId, evitando fugas de memoria.
     * Nota: StateFlow ya garantiza no emitir valores consecutivos iguales,
     * por lo que no se necesita distinctUntilChanged() (sería redundante).
     *
     * **Optimización**: el mapeo de canciones a MediaItems (que incluye
     * stats de disco para verificar carátulas) se ejecuta en
     * `Dispatchers.IO` para no bloquear el hilo principal. Solo vuelve
     * a Main para entregar la lista al adapter.
     */
    private fun subscribeToLibraryChanges() {
        val audioRepository = (application as OpenPlayerApplication).audioRepository
        val bassPlayer = player ?: return

        serviceScope.launch {
            bassPlayer.queueIdFlow.collectLatest { queueId ->
                // Seleccionar el Flow correcto según el queueId
                val songsFlow = when {
                    queueId == BassPlayerAdapter.QUEUE_LIBRARY -> {
                        // Biblioteca completa ordenada por título (orden del DAO)
                        audioRepository.songs
                    }
                    queueId == BassPlayerAdapter.QUEUE_TRACKS_BY_DATE -> {
                        // Biblioteca completa ordenada por fecha descendente
                        audioRepository.songs.map { songs ->
                            songs.sortedByDescending { it.dateAdded }
                        }
                    }
                    queueId.startsWith(BassPlayerAdapter.QUEUE_ALBUM_PREFIX) -> {
                        // Canciones de un álbum específico, ordenadas por trackNumber
                        val albumName = queueId.removePrefix(BassPlayerAdapter.QUEUE_ALBUM_PREFIX)
                        audioRepository.songs.map { songs ->
                            songs.filter { it.album == albumName }
                                .sortedBy { it.trackNumber ?: Int.MAX_VALUE }
                        }
                    }
                    queueId.startsWith(BassPlayerAdapter.QUEUE_ARTIST_PREFIX) -> {
                        // Canciones de un artista específico, ordenadas por título
                        val artistName = queueId.removePrefix(BassPlayerAdapter.QUEUE_ARTIST_PREFIX)
                        audioRepository.songs.map { songs ->
                            songs.filter { it.artist == artistName }
                                .sortedBy { it.title }
                        }
                    }
                    queueId.startsWith(BassPlayerAdapter.QUEUE_PLAYLIST_PREFIX) -> {
                        // Futuro: playlist personalizada desde PlaylistRepository
                        // Por ahora, biblioteca completa como fallback
                        audioRepository.songs
                    }
                    else -> {
                        // QueueId desconocido: usar biblioteca completa
                        audioRepository.songs
                    }
                }

                // Suscribirse al Flow y enviar MediaItems al adapter.
                // El mapeo (que incluye stats de disco) corre en IO
                // para no bloquear el hilo principal.
                songsFlow.collectLatest { songs ->
                    val mediaItems = withContext(Dispatchers.IO) {
                        songs.map { it.toMediaItem(coverRepository) }
                    }
                    bassPlayer.updateLibraryPlaylist(mediaItems)
                }
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