package com.openplayer.music.splash.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalFloatingIconColor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Recursos de iconos disponibles para el fondo flotante.
 * 5 iconos únicos relacionados con música/sonido, cada uno aparece
 * una sola vez en la pantalla (no se repiten).
 */
private val floatingIconResources = listOf(
    R.drawable.ic_float_disc,        // disco de vinilo
    R.drawable.ic_float_note,        // doble nota musical
    R.drawable.ic_float_headphones,  // audífonos
    R.drawable.ic_float_equalizer,   // barras de ecualizador
    R.drawable.ic_float_headset      // headset/diadema
)

/**
 * Especificación de un icono flotante individual.
 *
 * [xFraction] y [yFraction] son valores normalizados 0..1 que
 * representan la posición relativa dentro del contenedor padre.
 * [phaseOffsetX] y [phaseOffsetY] son desfasajes de fase para
 * que cada icono tenga su propio ritmo dentro de la animación
 * global compartida. [frequencyMultiplierX] y [frequencyMultiplierY]
 * varían la velocidad para evitar sincronización visual.
 */
private data class FloatingIconSpec(
    val iconRes: Int,
    val xFraction: Float,
    val yFraction: Float,
    val sizeDp: Float,
    val rotationDegrees: Float,
    val alpha: Float,
    val phaseOffsetX: Float,
    val phaseOffsetY: Float,
    val frequencyMultiplierX: Float,
    val frequencyMultiplierY: Float
)

/** Tamaño de los iconos gigantes (80-120dp). */
private const val MIN_ICON_SIZE_DP = 80f
private const val MAX_ICON_SIZE_DP = 120f
private const val MIN_ALPHA = 0.12f
private const val MAX_ALPHA = 0.28f
private const val MIN_ANIM_DURATION_MS = 4000
private const val MAX_ANIM_DURATION_MS = 7000
private const val FLOAT_AMPLITUDE_DP = 8f
private const val MIN_GAP_DP = 40f

/**
 * Posiciones predefinidas para los 5 iconos gigantes.
 * Distribuidas estratégicamente por la pantalla para evitar el centro
 * (donde va el contenido principal) y cubrir esquinas y laterales.
 */
private val predefinedPositions = listOf(
    0.15f to 0.18f,  // superior-izquierda
    0.85f to 0.25f,  // superior-derecha
    0.20f to 0.75f,  // inferior-izquierda
    0.80f to 0.82f,  // inferior-derecha
    0.50f to 0.50f   // centro (detrás del contenido)
)

/**
 * Semilla fija para el generador aleatorio.
 * Garantiza que las rotaciones, tamaños y fases sean consistentes
 * cada vez que se entra a la pantalla de bienvenida.
 */
private const val RANDOM_SEED = 42L

/**
 * Fondo con iconos flotantes para la pantalla de bienvenida.
 *
 * Comportamiento:
 * - Coloca exactamente 5 iconos gigantes (80-120dp) distribuidos por
 *   la pantalla en posiciones predefinidas (esquinas y centro).
 * - Cada icono aparece una sola vez (no se repiten).
 * - Animación sutil e infinita de flotación (vaivén de ±8dp).
 * - Todos los iconos usan el color custom floatingIcon del tema
 *   activo (LocalFloatingIconColor), adaptándose automáticamente
 *   a los 3 temas.
 * - Las posiciones son fijas pero la rotación, tamaño y fase de
 *   animación son aleatorios dentro de rangos definidos.
 *
 * **Optimización: un solo `infiniteTransition` compartido**.
 * Un único `rememberInfiniteTransition` genera una fase global, y
 * cada icono deriva su desplazamiento aplicando su propia fase y
 * frecuencia. Solo hay 1 animación en el reloj de Compose en vez
 * de 10 animaciones independientes.
 */
@Composable
fun FloatingIconsBackground(modifier: Modifier = Modifier) {
    val floatingIconColor = LocalFloatingIconColor.current
    val density = LocalDensity.current

    val specs = remember {
        generateFixedIcons(random = Random(RANDOM_SEED))
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // ÚNICA animación infinita para todo el fondo
    val infiniteTransition = rememberInfiniteTransition(label = "floatingGlobal")
    val globalPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = MAX_ANIM_DURATION_MS,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "globalPhase"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        if (containerSize != IntSize.Zero) {
            specs.forEach { spec ->
                StaticFloatingIcon(
                    spec = spec,
                    globalPhase = globalPhase,
                    floatingIconColor = floatingIconColor,
                    containerWidthPx = containerSize.width,
                    containerHeightPx = containerSize.height,
                    density = density.density
                )
            }
        }
    }
}

/**
 * Icono flotante individual con desplazamiento derivado de la fase
 * global compartida. Cada icono aplica su propia fase y frecuencia
 * para tener un movimiento independiente.
 */
@Composable
private fun StaticFloatingIcon(
    spec: FloatingIconSpec,
    globalPhase: Float,
    floatingIconColor: androidx.compose.ui.graphics.Color,
    containerWidthPx: Int,
    containerHeightPx: Int,
    density: Float
) {
    val phaseX = (globalPhase * spec.frequencyMultiplierX + spec.phaseOffsetX) * 2f * Math.PI.toFloat()
    val phaseY = (globalPhase * spec.frequencyMultiplierY + spec.phaseOffsetY) * 2f * Math.PI.toFloat()

    val offsetX = sin(phaseX) * FLOAT_AMPLITUDE_DP
    val offsetY = sin(phaseY) * FLOAT_AMPLITUDE_DP

    val baseXDp = (spec.xFraction * containerWidthPx / density)
    val baseYDp = (spec.yFraction * containerHeightPx / density)

    Icon(
        painter = painterResource(spec.iconRes),
        contentDescription = null,
        tint = floatingIconColor.copy(alpha = spec.alpha),
        modifier = Modifier
            .offset(x = baseXDp.dp, y = baseYDp.dp)
            .graphicsLayer {
                translationX = offsetX * density
                translationY = offsetY * density
                rotationZ = spec.rotationDegrees
            }
            .size(spec.sizeDp.dp)
    )
}

/**
 * Genera los 5 iconos flotantes con posiciones predefinidas pero
 * rotaciones, tamaños y fases aleatorios.
 */
private fun generateFixedIcons(random: Random): List<FloatingIconSpec> {
    val specs = mutableListOf<FloatingIconSpec>()

    floatingIconResources.forEachIndexed { index, iconRes ->
        val (xFraction, yFraction) = predefinedPositions[index]

        val sizeDp = random.nextFloat() * (MAX_ICON_SIZE_DP - MIN_ICON_SIZE_DP) + MIN_ICON_SIZE_DP
        val freqX = 0.6f + random.nextFloat() * 0.8f
        val freqY = 0.6f + random.nextFloat() * 0.8f
        val phaseX = random.nextFloat()
        val phaseY = random.nextFloat()

        specs.add(
            FloatingIconSpec(
                iconRes = iconRes,
                xFraction = xFraction,
                yFraction = yFraction,
                sizeDp = sizeDp,
                rotationDegrees = random.nextFloat() * 360f,
                alpha = random.nextFloat() * (MAX_ALPHA - MIN_ALPHA) + MIN_ALPHA,
                phaseOffsetX = phaseX,
                phaseOffsetY = phaseY,
                frequencyMultiplierX = freqX,
                frequencyMultiplierY = freqY
            )
        )
    }

    return specs
}