package com.openplayer.music

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.openplayer.music.data.AppPreferences
import com.openplayer.music.data.LocaleManager
import com.openplayer.music.data.db.AppDatabase
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.ui.configureEdgeToEdge
import com.openplayer.music.ui.screens.MainScreen
import com.openplayer.music.ui.theme.ThemeMode
import com.openplayer.music.ui.theme.ThemeSwitcherHost
import kotlinx.coroutines.launch

/**
 * Activity principal de OpenPlayer.
 *
 * Punto de entrada y conexión entre Android y la interfaz Compose.
 * La lógica de cada funcionalidad vive en archivos independientes.
 *
 * Ciclo de vida del escaneo:
 * - onStart(): registra ContentObserver para detectar cambios en tiempo real
 *   y lanza un escaneo incremental **con throttling** (30s) para evitar
 *   re-escaneos frecuentes cuando el usuario cambia rápidamente entre apps.
 * - onStop(): desregistra el ContentObserver.
 *
 * RTL: el idioma actual se pasa a ThemeSwitcherHost, que invierte el
 * layout direction de Compose mediante LocalLayoutDirection cuando
 * corresponde (ej. árabe). Compatible con API 27–37 sin crashes.
 */
class MainActivity : ComponentActivity() {

    private lateinit var audioRepository: AudioRepository

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge()

        val preferences = AppPreferences(applicationContext)
        val database = AppDatabase.getInstance(applicationContext)
        audioRepository = AudioRepository(applicationContext, database, preferences)

        val systemIsDark = isSystemDark()
        val initialTheme = preferences.getThemeModeBlocking()
            ?: if (systemIsDark) ThemeMode.DARK else ThemeMode.LIGHT
        val currentLanguage = preferences.getLanguageBlocking()

        setContent {
            var theme by remember { mutableStateOf(initialTheme) }
            val scope = rememberCoroutineScope()

            ThemeSwitcherHost(
                currentTheme = theme,
                currentLanguage = currentLanguage,
                systemIsDark = systemIsDark,
                onThemeCommitted = { newTheme ->
                    theme = newTheme
                    scope.launch { preferences.setThemeMode(newTheme) }
                }
            ) {
                MainScreen(audioRepository = audioRepository)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Registrar observer para detectar cambios en tiempo real
        // (sin throttling, el observer aplica su propio debounce de 1.5s)
        audioRepository.registerContentObserver()

        // Escaneo incremental con throttling de 30s al volver a primer plano.
        // Evita re-escaneos frecuentes cuando el usuario cambia rápidamente
        // entre apps. Si el usuario agregó música con la app abierta, el
        // ContentObserver la detecta inmediatamente (usa incrementalScan
        // sin throttling).
        lifecycleScope.launch {
            audioRepository.incrementalScanIfDue()
        }
    }

    override fun onStop() {
        super.onStop()
        // Desregistrar observer cuando la app pasa a segundo plano
        audioRepository.unregisterContentObserver()
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}