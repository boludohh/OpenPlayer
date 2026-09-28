package com.openplayer.music.splash

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.openplayer.music.MainActivity
import com.openplayer.music.data.AppPreferences
import com.openplayer.music.data.LocaleManager
import com.openplayer.music.data.db.AppDatabase
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.permissions.PermissionHandler
import com.openplayer.music.ui.configureEdgeToEdge
import com.openplayer.music.ui.theme.ThemeMode
import com.openplayer.music.ui.theme.ThemeSwitcherHost
import kotlinx.coroutines.launch

/**
 * Activity launcher de OpenPlayer.
 *
 * Fases:
 * - WELCOME: bienvenida con typewriter (solo si el setup no fue completado).
 * - SETUP: permisos + idioma + tema. Se muestra si el setup no fue completado
 *   (tras tocar Comenzar) o si, estando completado, falta algún permiso
 *   obligatorio revocado.
 * - LOADING: escaneo de MediaStore sin tiempo fijo; al terminar entra a
 *   MainActivity.
 *
 * La fase se restaura tras recreate() (cambio de idioma) mediante
 * savedInstanceState.
 *
 * Transiciones entre fases (Welcome <-> Setup):
 * - Bienvenida -> Permisos: slide in desde la derecha (End), contenido
 *   saliente sale por la izquierda (Start), con fade simultáneo.
 * - Permisos -> Bienvenida: slide in desde la izquierda (Start),
 *   contenido saliente sale por la derecha (End), con fade simultáneo.
 * - Otros cambios (ej. Permisos -> Carga): fade simple.
 *
 * RTL: el idioma actual se pasa a ThemeSwitcherHost, que invierte el
 * layout direction de Compose mediante LocalLayoutDirection cuando
 * corresponde (ej. árabe). Compatible con API 27–37 sin crashes.
 */
class SplashActivity : ComponentActivity() {

    private enum class Phase { WELCOME, SETUP, LOADING }

    private val preferences by lazy { AppPreferences(applicationContext) }
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val audioRepository by lazy { 
        AudioRepository(applicationContext, database, preferences) 
    }
    
    private var phase by mutableStateOf(Phase.WELCOME)
    private var resumeTick by mutableIntStateOf(0)
    private var systemIsDark = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge()

        systemIsDark = isSystemDark()
        val initialTheme = preferences.getThemeModeBlocking()
            ?: if (systemIsDark) ThemeMode.DARK else ThemeMode.LIGHT
        val currentLanguage = preferences.getLanguageBlocking()

        phase = savedInstanceState?.getString(KEY_PHASE)
            ?.let { name -> runCatching { Phase.valueOf(name) }.getOrNull() }
            ?: defaultPhase()

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
                SplashContent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresca el estado de permisos al volver de diálogos del sistema
        // o de ajustes externos.
        resumeTick++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_PHASE, phase.name)
        super.onSaveInstanceState(outState)
    }

    private fun defaultPhase(): Phase = when {
        !preferences.isSetupCompletedBlocking() -> Phase.WELCOME
        !PermissionHandler.hasRequiredPermissions(this) -> Phase.SETUP
        else -> Phase.LOADING
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    @Composable
    private fun SplashContent() {
        val scope = rememberCoroutineScope()

        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                val fromIndex = targetState.ordinal
                val toIndex = initialState.ordinal
                val durationMillis = 320
                when {
                    // Bienvenida -> Permisos: slide in from End
                    initialState == Phase.WELCOME && targetState == Phase.SETUP ->
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(durationMillis)
                        ) + fadeIn(animationSpec = tween(durationMillis)))
                            .togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> -fullWidth },
                                    animationSpec = tween(durationMillis)
                                ) + fadeOut(animationSpec = tween(durationMillis))
                            )
                    // Permisos -> Bienvenida: slide in from Start
                    initialState == Phase.SETUP && targetState == Phase.WELCOME ->
                        (slideInHorizontally(
                            initialOffsetX = { fullWidth -> -fullWidth },
                            animationSpec = tween(durationMillis)
                        ) + fadeIn(animationSpec = tween(durationMillis)))
                            .togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> fullWidth },
                                    animationSpec = tween(durationMillis)
                                ) + fadeOut(animationSpec = tween(durationMillis))
                            )
                    // Otros cambios (ej. Permisos -> Carga): fade simple
                    fromIndex > toIndex ->
                        fadeIn(animationSpec = tween(durationMillis))
                            .togetherWith(fadeOut(animationSpec = tween(durationMillis)))
                    else ->
                        fadeIn(animationSpec = tween(durationMillis))
                            .togetherWith(fadeOut(animationSpec = tween(durationMillis)))
                }
            },
            label = "splashPhaseTransition"
        ) { currentPhase ->
            when (currentPhase) {
                Phase.WELCOME -> WelcomeScreen(
                    onStart = { phase = Phase.SETUP }
                )

                Phase.SETUP -> SetupScreen(
                    resumeTick = resumeTick,
                    onBack = {
                        if (preferences.isSetupCompletedBlocking()) {
                            finish()
                        } else {
                            phase = Phase.WELCOME
                        }
                    },
                    onFinish = {
                        scope.launch { preferences.setSetupCompleted(true) }
                        phase = Phase.LOADING
                    }
                )

                Phase.LOADING -> LoadingScreen(
                    audioRepository = audioRepository,
                    onReady = {
                        // Transición activa: cuando la librería termina de
                        // cargarse y el texto termina de escribirse, se lanza
                        // MainActivity y se cierra SplashActivity.
                        if (ENABLE_MAIN_ACTIVITY_TRANSITION) {
                            startActivity(
                                Intent(this@SplashActivity, MainActivity::class.java)
                            )
                            finish()
                        }
                    }
                )
            }
        }
    }

    companion object {
        private const val KEY_PHASE = "splash_phase"

        /**
         * Controla si SplashActivity transiciona a MainActivity cuando la
         * fase LOADING termina (librería cargada + texto terminado).
         * Poner en `true` para el flujo normal de la aplicación.
         * Poner en `false` durante el desarrollo del diseño de la pantalla
         * de carga para poder inspeccionarla sin que la app salte a
         * MainActivity.
         */
        private const val ENABLE_MAIN_ACTIVITY_TRANSITION = true
    }
}