package com.openplayer.music.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.openplayer.music.R
import com.openplayer.music.data.LocaleManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Permite a cualquier componente solicitar un cambio de tema
 * indicando el punto de origen (coordenadas de ventana) desde
 * el que se expandirá la animación circular.
 */
val LocalThemeSwitch = compositionLocalOf<(Offset) -> Unit> { {} }

/**
 * Estado del cambio de tema expuesto a los componentes hijos.
 * - [isAnimating]: true mientras la animación de cambio está en curso
 *   (los toques en el botón de tema se ignoran durante este tiempo).
 * - [displayedTheme]: el tema que se está mostrando visualmente (puede
 *   ser el tema destino durante la animación, antes de confirmarse).
 */
data class ThemeSwitchState(
    val isAnimating: Boolean,
    val displayedTheme: ThemeMode
)

val LocalThemeSwitchState = compositionLocalOf {
    ThemeSwitchState(isAnimating = false, displayedTheme = ThemeMode.LIGHT)
}

/** Forma circular usada para revelar el nuevo tema. */
private class CircleRevealShape(
    private val center: Offset,
    private val radius: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            addOval(
                Rect(
                    left = center.x - radius,
                    top = center.y - radius,
                    right = center.x + radius,
                    bottom = center.y + radius
                )
            )
        }
        return Outline.Generic(path)
    }
}

/** Distancia máxima desde un punto hasta las esquinas del contenedor. */
private fun maxDistance(point: Offset, size: Size): Float {
    if (size == Size.Zero) return 3000f
    val dx = max(point.x, size.width - point.x)
    val dy = max(point.y, size.height - point.y)
    return sqrt(dx * dx + dy * dy)
}

/**
 * Host de cambio de tema con animación circular estilo Telegram.
 *
 * Comportamiento:
 * - Cada pulsación inicia una animación circular que revela el siguiente
 *   tema del ciclo desde el punto de origen.
 * - Durante la animación, los toques en el botón de tema se ignoran
 *   (el usuario debe esperar a que la animación termine).
 * - Si una pulsación nueva interrumpe la animación antes de cubrir toda la
 *   pantalla, el cambio NO se confirma (el tema no cambia).
 * - El tema nuevo solo se confirma (commit) cuando la animación logra
 *   cubrir toda la pantalla.
 * - El fondo del tema se aplica tanto al contenido principal como a la
 *   capa de revelación, garantizando que el cambio visual sea completo.
 *
 * RTL:
 * - [currentLanguage] se utiliza para determinar si el idioma actual
 *   requiere escritura de derecha a izquierda.
 * - Se aplica LocalLayoutDirection = Rtl sobre todo el contenido cuando
 *   corresponde (mediante LocaleManager.isRtl), forzando la inversión
 *   visual del layout en Compose. Compatible con API 27–37 sin depender
 *   de applyOverrideConfiguration().
 *
 * Tipografía por idioma:
 * - [currentLanguage] se utiliza para determinar la familia de fuentes:
 *   IBM Plex Sans Arabic si el idioma es "ar", IBM Plex Sans por defecto.
 * - Se provee LocalAppFontFamily para que todos los componentes de
 *   tipografía usen la familia correcta automáticamente.
 *
 * Barras del sistema:
 * - Actualiza dinámicamente la apariencia de los iconos de las barras
 *   de estado y navegación (isAppearanceLightStatusBars e
 *   isAppearanceLightNavigationBars) según el tema activo:
 *   - Tema claro: iconos oscuros (gris fuerte) sobre fondo transparente.
 *   - Tema oscuro/AMOLED: iconos claros (blancos) sobre fondo transparente.
 * - Esto garantiza que los iconos del sistema siempre sean visibles
 *   independientemente del tema seleccionado.
 *
 * @param currentTheme tema activo (Claro, Oscuro o AMOLED).
 * @param currentLanguage código de idioma guardado por el usuario.
 * @param systemIsDark indica si el sistema está en modo oscuro.
 * @param onThemeCommitted callback que se invoca cuando la animación
 *   de cambio de tema cubre toda la pantalla y se confirma el cambio.
 * @param content árbol de Compose de la aplicación.
 */
@Composable
fun ThemeSwitcherHost(
    currentTheme: ThemeMode,
    currentLanguage: String,
    systemIsDark: Boolean,
    onThemeCommitted: (ThemeMode) -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    var containerSize by remember { mutableStateOf(Size.Zero) }
    var pendingTarget by remember { mutableStateOf<ThemeMode?>(null) }
    var center by remember { mutableStateOf(Offset.Zero) }
    var isAnimating by remember { mutableStateOf(false) }
    val radius = remember { Animatable(0f) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // El tema que se está mostrando visualmente (puede ser el destino
    // durante la animación, antes de confirmarse).
    val displayedTheme = pendingTarget ?: currentTheme

    // Lambda para cambiar el tema: ignora toques si ya hay una animación
    // en curso, evitando el uso de return@Unit que causa problemas de
    // compilación en algunas versiones de Kotlin/AGP.
    val switchTheme: (Offset) -> Unit = { tap ->
        if (!isAnimating) {
            animationJob?.cancel()
            val target = ThemeMode.next(currentTheme, systemIsDark)
            pendingTarget = target
            center = tap
            isAnimating = true
            val targetRadius = maxDistance(tap, containerSize)
            animationJob = scope.launch {
                radius.snapTo(0f)
                radius.animateTo(
                    targetValue = targetRadius,
                    animationSpec = tween(durationMillis = 500)
                )
                // Solo se confirma si la animación cubrió toda la pantalla.
                pendingTarget?.let(onThemeCommitted)
                pendingTarget = null
                isAnimating = false
            }
        }
    }

    // Dirección del layout según el idioma actual: RTL para árabe y
    // otros idiomas de derecha a izquierda, LTR para el resto.
    val layoutDirection = if (LocaleManager.isRtl(currentLanguage)) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    // Familia de fuentes según el idioma actual: IBM Plex Sans Arabic
    // si el idioma es "ar", IBM Plex Sans por defecto.
    val fontFamily = if (currentLanguage == LocaleManager.LANGUAGE_AR) {
        IBMPlexSansArabicFamily
    } else {
        IBMPlexSansFamily
    }

    // Actualiza dinámicamente la apariencia de las barras del sistema
    // (iconos oscuros en tema claro, iconos claros en tema oscuro/AMOLED)
    // cada vez que cambia el tema activo.
    LaunchedEffect(currentTheme) {
        activity?.let { act ->
            val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            val isLightTheme = currentTheme == ThemeMode.LIGHT
            insetsController.isAppearanceLightStatusBars = isLightTheme
            insetsController.isAppearanceLightNavigationBars = isLightTheme
        }
    }

    val switchState = ThemeSwitchState(
        isAnimating = isAnimating,
        displayedTheme = displayedTheme
    )

    CompositionLocalProvider(
        LocalThemeSwitch provides switchTheme,
        LocalLayoutDirection provides layoutDirection,
        LocalThemeSwitchState provides switchState,
        LocalAppFontFamily provides fontFamily
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    containerSize = Size(size.width.toFloat(), size.height.toFloat())
                }
        ) {
            OpenPlayerTheme(themeMode = currentTheme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    content()
                }
            }

            val target = pendingTarget
            if (target != null) {
                val currentRadius = radius.value
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleRevealShape(center, currentRadius))
                ) {
                    OpenPlayerTheme(themeMode = target) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Botón circular que cambia el tema con la animación circular.
 * El círculo se origina en el centro del propio botón.
 *
 * Comportamiento:
 * - El icono rota 360° con easing de desaceleración durante la
 *   animación de cambio de tema (arranca rápido, frena progresivamente).
 * - Durante la animación, el icono cambia suavemente (crossfade) al
 *   icono del tema destino.
 * - Los toques se ignoran mientras la animación está en curso (el
 *   usuario debe esperar a que termine antes de volver a tocar).
 *
 * Cada icono tiene un color específico según el tema:
 * - Sol (tema claro): #1A1A1A
 * - Luna (tema oscuro): #F5F5F5
 * - Luna AMOLED (tema AMOLED): #E5E5E5
 *
 * - [enabled]: controla si el botón responde a clicks. Útil cuando el
 *   botón está desvaneciéndose con el scroll y no debe ser interactuable
 *   hasta estar completamente visible. Por defecto true.
 */
@Composable
fun ThemeToggleButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val switchTheme = LocalThemeSwitch.current
    val switchState = LocalThemeSwitchState.current
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    // Animación de rotación del icono
    val rotationAnimatable = remember { Animatable(0f) }

    // Cuando el tema cambia (o se inicia la animación), girar el icono
    LaunchedEffect(switchState.displayedTheme) {
        if (switchState.isAnimating) {
            rotationAnimatable.snapTo(0f)
            rotationAnimatable.animateTo(
                targetValue = 360f,
                animationSpec = tween(
                    durationMillis = 500,
                    easing = FastOutSlowInEasing
                )
            )
            rotationAnimatable.snapTo(0f)
        }
    }

    IconButton(
        onClick = { switchTheme(buttonCenter) },
        enabled = enabled && !switchState.isAnimating,
        modifier = modifier.onGloballyPositioned { coordinates ->
            buttonCenter = coordinates.localToWindow(
                Offset(
                    coordinates.size.width / 2f,
                    coordinates.size.height / 2f
                )
            )
        }
    ) {
        // Crossfade suave entre iconos según el tema mostrado, cada uno
        // con su color específico.
        Crossfade(
            targetState = switchState.displayedTheme,
            animationSpec = tween(durationMillis = 200)
        ) { theme ->
            val (iconRes, iconColor) = when (theme) {
                ThemeMode.LIGHT -> R.drawable.ic_sun to LightSunIconColor
                ThemeMode.DARK -> R.drawable.ic_moon to DarkMoonIconColor
                ThemeMode.AMOLED -> R.drawable.ic_moon_amoled to AmoledMoonIconColor
            }

            Icon(
                painter = painterResource(iconRes),
                contentDescription = stringResource(R.string.cd_change_theme),
                tint = iconColor,
                modifier = Modifier.graphicsLayer {
                    rotationZ = rotationAnimatable.value
                }
            )
        }
    }
}