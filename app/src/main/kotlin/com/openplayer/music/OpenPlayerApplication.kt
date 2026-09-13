package com.openplayer.music

import android.app.Application
import com.openplayer.music.data.AppPreferences
import com.openplayer.music.data.db.AppDatabase
import com.openplayer.music.data.media.AudioRepository

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
}