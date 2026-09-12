package com.openplayer.music.splash.components

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
 */
private data class FloatingIconSpec(
    val iconRes: Int,
    val xFraction: Float,
    val yFraction: Float,
    val sizeDp: Float,
    val rotationDegrees: Float,
    val alpha: Float,
    val durationMillis: Int,
    val phaseOffsetMillis: Int
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
 * - Animación sutil e infinita de flotación (vaivén de ±5dp)
 *   con duraciones variadas por icono para evitar sincronización.
 * - Todos los iconos usan el color custom floatingIcon del tema
 *   activo (LocalFloatingIconColor), adaptándose automáticamente
 *   a claro (#D9D9D9) / oscuro (#333333) / AMOLED (#212121).
 * - Las posiciones se generan una sola vez con remember para
 *   evitar recálculos durante la animación.
 * - Se posicionan usando el tamaño real del contenedor padre
 *   (medido con onSizeChanged) para una distribución correcta
 *   independientemente del tamaño de pantalla.
 *
 * El algoritmo de colocación usa una rejilla de 8×16 celdas,
 * baraja las celdas y coloca un icono en cada una de las primeras
 * 85, con jitter aleatorio dentro de cada celda para evitar el
 * aspecto de rejilla perfecta. Esto garantiza cobertura uniforme
 * de toda la pantalla incluyendo esquinas y bordes.
 */
@Composable
fun FloatingIconsBackground(modifier: Modifier = Modifier) {
    val floatingIconColor = LocalFloatingIconColor.current
    val density = LocalDensity.current

    val specs = remember {
        generateStratifiedIcons(random = Random(RANDOM_SEED))
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        if (containerSize != IntSize.Zero) {
            specs.forEach { spec ->
                AnimatedFloatingIcon(
                    spec = spec,
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
 * Icono flotante individual con animación infinita de vaivén.
 *
 * Se posiciona usando offset() con coordenadas absolutas
 * calculadas a partir del tamaño real del contenedor padre,
 * más una animación de vaivén superpuesta vía graphicsLayer.
 */
@Composable
private fun AnimatedFloatingIcon(
    spec: FloatingIconSpec,
    floatingIconColor: androidx.compose.ui.graphics.Color,
    containerWidthPx: Int,
    containerHeightPx: Int,
    density: Float
) {
    val infiniteTransition = rememberInfiniteTransition(
        label = "float_${spec.phaseOffsetMillis}"
    )

    val offsetX by infiniteTransition.animateFloat(
        initialValue = -FLOAT_AMPLITUDE_DP,
        targetValue = FLOAT_AMPLITUDE_DP,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = spec.durationMillis,
                delayMillis = spec.phaseOffsetMillis
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX"
    )

    val offsetY by infiniteTransition.animateFloat(
        initialValue = -FLOAT_AMPLITUDE_DP,
        targetValue = FLOAT_AMPLITUDE_DP,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (spec.durationMillis * 1.3f).toInt(),
                delayMillis = spec.phaseOffsetMillis + spec.durationMillis / 3
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

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
                specs.add(
                    FloatingIconSpec(
                        iconRes = floatingIconResources[random.nextInt(floatingIconResources.size)],
                        xFraction = xFraction,
                        yFraction = yFraction,
                        sizeDp = sizeDp,
                        rotationDegrees = random.nextFloat() * 360f,
                        alpha = random.nextFloat() * (MAX_ALPHA - MIN_ALPHA) + MIN_ALPHA,
                        durationMillis = random.nextInt(MIN_ANIM_DURATION_MS, MAX_ANIM_DURATION_MS + 1),
                        phaseOffsetMillis = random.nextInt(0, MAX_ANIM_DURATION_MS)
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