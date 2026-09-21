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
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Recursos de iconos disponibles para el fondo flotante.
 * 12 iconos únicos relacionados con música/sonido.
 */
private val floatingIconResources = listOf(
    R.drawable.ic_floating_1,  // avión de papel
    R.drawable.ic_floating_2,  // notas musicales dobles
    R.drawable.ic_floating_3,  // auriculares
    R.drawable.ic_floating_4,  // nota musical con círculo
    R.drawable.ic_floating_5,  // clave de sol
    R.drawable.ic_floating_6,  // señal de radio/ondas
    R.drawable.ic_floating_7,  // reproductor de música
    R.drawable.ic_floating_8,  // nota musical simple
    R.drawable.ic_floating_9,  // corchea con banderola
    R.drawable.ic_floating_10, // nota con flag larga
    R.drawable.ic_floating_11, // nota doble corchea beamed
    R.drawable.ic_floating_12  // barras de ecualizador
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
    val durationMillis: Int,
    val phaseOffsetMillis: Int,
    val phaseOffsetX: Float,
    val phaseOffsetY: Float,
    val frequencyMultiplierX: Float,
    val frequencyMultiplierY: Float
)

private const val TARGET_ICON_COUNT = 85
private const val MIN_ICON_SIZE_DP = 30f
private const val MAX_ICON_SIZE_DP = 80f
private const val MIN_ALPHA = 0.15f
private const val MAX_ALPHA = 0.45f
private const val MIN_ANIM_DURATION_MS = 3000
private const val MAX_ANIM_DURATION_MS = 6000
private const val FLOAT_AMPLITUDE_DP = 5f
private const val MIN_GAP_DP = 8f

/**
 * Rejilla de celdas para el muestreo estratificado.
 * 8 columnas × 16 filas = 128 celdas, suficiente margen para
 * colocar 85 iconos sin que queden regiones vacías.
 * El aspect ratio ~1:2 se aproxima al de pantallas móviles modernas.
 */
private const val GRID_COLS = 8
private const val GRID_ROWS = 16

/**
 * Porcentaje de margen dentro de cada celda donde NO se colocan
 * iconos. Un valor de 0.2f mantiene el icono dentro del 60% central
 * de cada celda, evitando que queden pegados al borde de la celda
 * y dando un aspecto orgánico y no rejilla-perfecta.
 */
private const val CELL_JITTER = 0.2f

/**
 * Máximo de intentos de jitter dentro de una celda antes de
 * descartarla (si colisiona en todos los intentos, se omite).
 */
private const val MAX_JITTER_ATTEMPTS_PER_CELL = 20

/**
 * Semilla fija para el generador aleatorio.
 * Garantiza que las posiciones sean consistentes cada vez
 * que se entra a la pantalla de bienvenida.
 */
private const val RANDOM_SEED = 42L

/**
 * Fondo con iconos flotantes para la pantalla de bienvenida.
 *
 * Comportamiento:
 * - Coloca ~85 iconos distribuidos uniformemente por toda la
 *   pantalla (centro, laterales, bordes superior/inferior, las 4
 *   esquinas) mediante muestreo estratificado por celdas.
 * - Cada icono tiene tamaño (30-80dp), rotación, opacidad y fase de
 *   animación aleatorios dentro de rangos definidos.
 * - Animación sutil e infinita de flotación (vaivén de ±5dp).
 * - Todos los iconos usan el color custom floatingIcon del tema
 *   activo (LocalFloatingIconColor), adaptándose automáticamente
 *   a claro (#D9D9D9) / oscuro (#333333) / AMOLED (#212121).
 * - Las posiciones se generan una sola vez con remember para
 *   evitar recálculos durante la animación.
 * - Se posicionan usando el tamaño real del contenedor padre
 *   (medido con onSizeChanged) para una distribución correcta
 *   independientemente del tamaño de pantalla.
 *
 * **Optimización: un solo `infiniteTransition` compartido**.
 * Anteriormente cada uno de los ~85 iconos tenía 2 animaciones
 * infinitas independientes (~170 animaciones corriendo en paralelo).
 * Ahora un único `rememberInfiniteTransition` a nivel del
 * composable principal genera una fase global (valor 0..1 que
 * crece continuamente), y cada icono deriva su desplazamiento
 * aplicando su propia fase/frecuencia/multiplicador. El resultado
 * visual es idéntico (movimientos no sincronizados, orgánicos),
 * pero solo hay 1 animación en el reloj de Compose en vez de 170.
 */
@Composable
fun FloatingIconsBackground(modifier: Modifier = Modifier) {
    val floatingIconColor = LocalFloatingIconColor.current
    val density = LocalDensity.current

    val specs = remember {
        generateStratifiedIcons(random = Random(RANDOM_SEED))
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // ÚNICA animación infinita para todo el fondo: una fase global
    // que crece de 0 a 1 con la duración máxima del rango. Cada
    // icono aplicará su propia fase/frecuencia para derivar su
    // vaivén independiente, manteniendo el aspecto orgánico.
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
 * para tener un movimiento independiente (no sincronizado con los
 * demás), logrando el mismo look orgánico que con animaciones
 * separadas pero con un solo reloj de animación activo.
 *
 * Usa `sin()` sobre la fase global para generar vaivén suave,
 * y [FloatingIconSpec.frequencyMultiplierX] / [FloatingIconSpec.frequencyMultiplierY]
 * para variar la velocidad por icono.
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
    // Derivar desplazamientos X e Y desde la fase global con
    // senos desfasados y de frecuencia distinta, para que cada
    // icono tenga su propio movimiento orgánico.
    // phaseX/Y ∈ [0, 2π) y frequencyMultiplierX/Y ∈ [0.6, 1.4]
    val phaseX = (globalPhase * spec.frequencyMultiplierX + spec.phaseOffsetX) * 2f * Math.PI.toFloat()
    val phaseY = (globalPhase * spec.frequencyMultiplierY + spec.phaseOffsetY) * 2f * Math.PI.toFloat()

    val offsetX = (sin(phaseX) * FLOAT_AMPLITUDE_DP).toFloat()
    val offsetY = (sin(phaseY) * FLOAT_AMPLITUDE_DP).toFloat()

    // Posición base en dp, a partir de las fracciones y el tamaño
    // real del contenedor padre.
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
 * Genera iconos flotantes mediante muestreo estratificado por celdas.
 *
 * Algoritmo:
 * 1. Crear la lista de todas las celdas de la rejilla (GRID_COLS × GRID_ROWS).
 * 2. Barajar la lista de celdas con la semilla fija.
 * 3. Para cada una de las primeras TARGET_ICON_COUNT celdas barajadas:
 *    - Generar un tamaño aleatorio para el icono.
 *    - Probar posiciones aleatorias dentro de la celda (jitter),
 *      hasta encontrar una que no colisione con iconos ya colocados.
 *    - Si después de MAX_JITTER_ATTEMPTS_PER_CELL no se encuentra
 *      una posición libre, descartar la celda y continuar con la
 *      siguiente (puede resultar en menos de TARGET_ICON_COUNT iconos
 *      en pantallas muy pequeñas, pero normalmente se colocan todos).
 *
 * Ventajas sobre el muestreo aleatorio puro:
 * - Garantiza cobertura uniforme de toda la pantalla.
 * - Elimina el problema de regiones vacías por azar (huecos).
 * - Mantiene el aspecto orgánico mediante jitter, rotación y
 *   tamaños/opacidades aleatorios.
 *
 * Cada icono ahora también recibe [FloatingIconSpec.phaseOffsetX],
 * [FloatingIconSpec.phaseOffsetY], [FloatingIconSpec.frequencyMultiplierX]
 * y [FloatingIconSpec.frequencyMultiplierY] para que su movimiento
 * derivado de la fase global tenga un ritmo independiente.
 */
private fun generateStratifiedIcons(random: Random): List<FloatingIconSpec> {
    val specs = mutableListOf<FloatingIconSpec>()

    // Paso 1: crear la lista de todas las celdas
    val allCells = mutableListOf<Pair<Int, Int>>()
    for (col in 0 until GRID_COLS) {
        for (row in 0 until GRID_ROWS) {
            allCells.add(col to row)
        }
    }

    // Paso 2: barajar las celdas con la semilla fija
    allCells.shuffle(random)

    // Paso 3: colocar iconos en las primeras TARGET_ICON_COUNT celdas
    val cellWidth = 1f / GRID_COLS
    val cellHeight = 1f / GRID_ROWS
    val jitterRangeX = cellWidth * (1f - 2f * CELL_JITTER)
    val jitterRangeY = cellHeight * (1f - 2f * CELL_JITTER)

    for (i in 0 until minOf(TARGET_ICON_COUNT, allCells.size)) {
        val (col, row) = allCells[i]
        val cellBaseX = col * cellWidth
        val cellBaseY = row * cellHeight

        val sizeDp = random.nextFloat() * (MAX_ICON_SIZE_DP - MIN_ICON_SIZE_DP) + MIN_ICON_SIZE_DP

        // Intentar encontrar una posición dentro de la celda que no colisione
        var placed = false
        for (attempt in 0 until MAX_JITTER_ATTEMPTS_PER_CELL) {
            // Posición con jitter dentro del rango central de la celda
            val xFraction = cellBaseX + cellWidth * CELL_JITTER + random.nextFloat() * jitterRangeX
            val yFraction = cellBaseY + cellHeight * CELL_JITTER + random.nextFloat() * jitterRangeY

            // Verificar colisión con iconos ya colocados
            val hasCollision = specs.any { existing ->
                val dx = (xFraction - existing.xFraction) * 1000f
                val dy = (yFraction - existing.yFraction) * 1000f
                val distance = sqrt(dx * dx + dy * dy)
                val minDistance = (sizeDp + existing.sizeDp) / 2f + MIN_GAP_DP
                distance < minDistance
            }

            if (!hasCollision) {
                // Frecuencias en [0.6, 1.4] para variar velocidad por icono
                val freqX = 0.6f + random.nextFloat() * 0.8f
                val freqY = 0.6f + random.nextFloat() * 0.8f
                // Fases en [0, 1) para desfasar cada icono
                val phaseX = random.nextFloat()
                val phaseY = random.nextFloat()

                specs.add(
                    FloatingIconSpec(
                        iconRes = floatingIconResources[random.nextInt(floatingIconResources.size)],
                        xFraction = xFraction,
                        yFraction = yFraction,
                        sizeDp = sizeDp,
                        rotationDegrees = random.nextFloat() * 360f,
                        alpha = random.nextFloat() * (MAX_ALPHA - MIN_ALPHA) + MIN_ALPHA,
                        durationMillis = random.nextInt(MIN_ANIM_DURATION_MS, MAX_ANIM_DURATION_MS + 1),
                        phaseOffsetMillis = random.nextInt(0, MAX_ANIM_DURATION_MS),
                        phaseOffsetX = phaseX,
                        phaseOffsetY = phaseY,
                        frequencyMultiplierX = freqX,
                        frequencyMultiplierY = freqY
                    )
                )
                placed = true
                break
            }
        }
        // Si no se colocó después de todos los intentos, se descarta
        // la celda (caso muy raro en pantallas normales)
    }

    return specs
}