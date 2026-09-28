package com.openplayer.music

import android.app.Application
import com.openplayer.music.data.AppPreferences
import com.openplayer.music.data.db.AppDatabase
import com.openplayer.music.data.media.ArtistImageRepository
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.data.media.CoverRepository
import com.openplayer.music.data.media.PlaybackHistoryRepository
import com.openplayer.music.data.media.PlaylistRepository
import com.openplayer.music.playback.PlaybackController

/**
 * Application class personalizada de OpenPlayer.
 *
 * Mantiene instancias singleton de los repositorios principales
 * para que puedan ser accedidas desde cualquier componente de la
 * aplicación (Activities, Services, etc.) sin necesidad de pasarlas
 * manualmente.
 *
 * Esto es especialmente importante para PlaybackService, que necesita
 * acceso a AudioRepository para mantener la playlist sincronizada con
 * la biblioteca musical, y a PlaybackHistoryRepository para registrar
 * eventos de reproducción.
 *
 * ## Optimización: CoverRepository singleton
 * Expone [coverRepository] como singleton para que la UI (TracksScreen)
 * y el servicio (PlaybackService) compartan el mismo caché de memoria
 * de bitmaps y el mismo caché de `coverFile()`. Evita duplicados de
 * memoria y stats de disco repetidos.
 *
 * ## ArtistImageRepository singleton
 * Expone [artistImageRepository] como singleton para que la pestaña
 * de Artistas y cualquier futuro consumidor compartan el mismo caché
 * de disco/memoria y la misma cola de enriquecido remoto
 * (Deezer → disco → Room).
 *
 * ## PlaylistRepository singleton
 * Expone [playlistRepository] como singleton para que cualquier
 * pantalla (PlaylistScreen, diálogos de añadir a playlist, etc.)
 * comparta el mismo acceso a las playlists del usuario y al mismo
 * Flow reactivo.
 *
 * ## PlaybackHistoryRepository singleton
 * Expone [playbackHistoryRepository] como singleton para que
 * PlaybackService (registro de eventos) y HomeScreen (lectura de
 * estadísticas) compartan el mismo acceso a play_stats y al mismo
 * Flow reactivo.
 *
 * ## PlaybackController singleton
 * Expone [playbackController] como singleton para que todas las
 * pantallas (TracksScreen, SearchScreen, HomeScreen, futuras)
 * compartan el mismo MediaController conectado a PlaybackService.
 * Evita duplicación de conexiones y garantiza que los Flows reactivos
 * (currentMediaId, isPlaying, etc.) sean consistentes en toda la app.
 */
class OpenPlayerApplication : Application() {

    val appPreferences: AppPreferences by lazy {
        AppPreferences(applicationContext)
    }

    val appDatabase: AppDatabase by lazy {
        AppDatabase.getInstance(applicationContext)
    }

    val audioRepository: AudioRepository by lazy {
        AudioRepository(applicationContext, appDatabase, appPreferences)
    }

    /**
     * Singleton de [CoverRepository]: caché de bitmaps en memoria,
     * caché de `coverFile()` y directorio de disco compartido por
     * toda la app (UI + servicio + escaneos).
     */
    val coverRepository: CoverRepository by lazy {
        CoverRepository(applicationContext)
    }

    /**
     * Singleton de [ArtistImageRepository]: pipeline de imágenes de
     * artista (Deezer → disco → Room) compartido por toda la app, con
     * búsquedas secuenciales espaciadas, descargas paralelizadas y
     * cachés unificados.
     */
    val artistImageRepository: ArtistImageRepository by lazy {
        ArtistImageRepository(applicationContext, appDatabase)
    }

    /**
     * Singleton de [PlaylistRepository]: acceso unificado a las
     * playlists del usuario (crear, renombrar, eliminar, añadir/quitar
     * canciones, reordenar) y Flow reactivo de playlists con contadores.
     */
    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(appDatabase)
    }

    /**
     * Singleton de [PlaybackHistoryRepository]: registro de eventos
     * de reproducción (playCount, completedCount, playedMs) y Flow
     * reactivo de estadísticas agregadas, top artista/pista y
     * canciones recientes.
     */
    val playbackHistoryRepository: PlaybackHistoryRepository by lazy {
        PlaybackHistoryRepository(appDatabase)
    }

    /**
     * Singleton de [PlaybackController]: controller centralizado de
     * reproducción compartido por todas las pantallas. Gestiona la
     * conexión única a PlaybackService y expone Flows reactivos para
     * el indicador de pista actual y futuros mini players.
     */
    val playbackController: PlaybackController by lazy {
        PlaybackController(applicationContext, coverRepository)
    }

    override fun onTerminate() {
        super.onTerminate()
        playbackController.release()
    }
}