package com.openplayer.music.ui

import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat

/**
 * Configuración global de EdgeToEdge para OpenPlayer.
 *
 * Centraliza en un único archivo toda la configuración necesaria para que la app
 * dibuje su contenido por debajo de las barras de estado y navegación con ambas
 * barras 100% transparentes, cubriendo desde API 27 hasta API 35+.
 *
 * Lo que aplica esta función:
 * 1. `enableEdgeToEdge()` antes de `setContent` (androidx.activity).
 * 2. Eliminación de flags traslúcidos heredados (`FLAG_TRANSLUCENT_NAVIGATION` y
 *    `FLAG_TRANSLUCENT_STATUS`) que agregan degradados en API 27 y 28.
 * 3. Transparencia explícita a nivel de `Window`, para que capas de personalización
 *    OEM (MIUI/HyperOS, One UI, ColorOS) que pisen el tema XML no recuperen colores
 *    opacos en las barras.
 * 4. Desactivación del contraste forzado por el sistema (`isStatusBarContrastEnforced`
 *    y `isNavigationBarContrastEnforced`, API 29+). Este es el mecanismo que AOSP y las
 *    capas OEM utilizan para dibujar un "filtro"/scrim sobre las barras transparentes.
 * 5. Desactivación del "force dark" de plataforma mediante `View.setForceDarkAllowed`
 *    sobre el `decorView` (API 29+). Force dark es una API de `android.view.View`, no
 *    de `Window`; al desactivarlo en la vista raíz queda cubierto todo el árbol de la
 *    ventana, incluido el contenido Compose. Es el mecanismo utilizado por MIUI/HyperOS
 *    y otras capas para alterar forzosamente los colores.
 * 6. Extensión del contenido al área del recorte de pantalla (notch) en API 28+
 *    (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`).
 *
 * Esta configuración se complementa a nivel XML en:
 * - `res/values/themes.xml`: colores de barra transparentes para todos los APIs.
 * - `res/values-v29/themes.xml`: `enforceStatusBarContrast`,
 *   `enforceNavigationBarContrast` y `forceDarkAllowed` en false, para que el sistema
 *   nunca aplique scrims ni force dark desde el primer frame de la ventana.
 *
 * Nota sobre capas de fabricantes: solo se utilizan APIs públicas documentadas.
 * MIUI/HyperOS, One UI y ColorOS respetan los atributos estándar de contraste y
 * force-dark, por lo que desactivarlos cubre los casos reales documentados. Si el
 * usuario tiene activos modos del sistema como "atenuación adicional" o temas de
 * terceros del OEM, esos son ajustes del sistema y no existe una API pública que
 * deba utilizarse para anularlos.
 *
 * Manejo de insets en Jetpack Compose:
 * - `Scaffold` ya consume `WindowInsets.safeDrawing` a través de `innerPadding`,
 *   protegiendo automáticamente el contenido normal.
 * - Para contenido FLOTANTE que viva fuera del Scaffold (barra de reproducción,
 *   botones inferiores, etc.), aplica los insets de la barra de navegación así:
 *
 * ```
 * // Ejemplo: contenido flotante sobre la barra de navegación
 * Box(
 *     modifier = Modifier
 *         .align(Alignment.BottomCenter)
 *         .windowInsetsPadding(WindowInsets.navigationBars)
 * ) {
 *     // Contenido flotante
 * }
 * ```
 *
 *   Equivalentemente puedes usar `Modifier.navigationBarsPadding()`.
 *   Prefiere `WindowInsets.safeDrawing` cuando también quieras incluir el área del
 *   recorte de pantalla (notch).
 *
 * Apariencia de las barras del sistema:
 * - La apariencia de los iconos de las barras (oscuros para tema claro, claros
 *   para tema oscuro/AMOLED) se gestiona dinámicamente en ThemeSwitcherHost
 *   mediante WindowInsetsControllerCompat, no en esta función.
 * - Esto permite que la apariencia se actualice automáticamente cuando el
 *   usuario cambia de tema sin necesidad de recrear la Activity.
 */
fun ComponentActivity.configureEdgeToEdge() {
    // 1) Edge to edge base: dibuja bajo las barras con transparencia.
    enableEdgeToEdge()

    // Garantiza que el contenido se extienda detrás de las barras del sistema
    // (redundante con enableEdgeToEdge, pero explícito y seguro).
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // 2) Elimina flags traslúcidos heredados que agregan degradados
    //    en API 27 y 28.
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

    // Flag necesario para que apliquen los colores de barra definidos aquí.
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

    // 3) Transparencia explícita a nivel de ventana.
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT

    // 4) y 5) Desactiva scrims de contraste y force dark (API 29+).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        // Force dark es una API de View, no de Window: se aplica sobre el
        // decorView (raíz del árbol de la ventana) para cubrir todo el
        // contenido, incluido el árbol Compose.
        window.decorView.setForceDarkAllowed(false)
    }

    // 6) Extiende el contenido al área del notch (API 28+).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val params = window.attributes
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = params
    }
}