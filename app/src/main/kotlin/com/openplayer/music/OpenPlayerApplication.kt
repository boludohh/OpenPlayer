package com.openplayer.music

import android.app.Application
import com.openplayer.music.data.AppPreferences
import com.openplayer.music.data.db.AppDatabase
import com.openplayer.music.data.media.ArtistImageRepository
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.data.media.CoverRepository

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
 * la biblioteca musical.
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
 * (MusicBrainz → Fanart.tv → Room).
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
     * artista (MusicBrainz → Fanart.tv → disco → Room) compartido por
     * toda la app, con cola secuencial y cachés unificados.
     */
    val artistImageRepository: ArtistImageRepository by lazy {
        ArtistImageRepository(applicationContext, appDatabase, appPreferences)
    }
}