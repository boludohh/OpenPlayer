package com.openplayer.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalNavIconActiveColor
import com.openplayer.music.ui.theme.LocalNavIconInactiveColor

/**
 * Panel inferior de navegación de OpenPlayer.
 *
 * Contiene los iconos de las pestañas de navegación (Home, Songs, Albums, Playlists)
 * con animaciones de transición entre estados activo/inactivo.
 *
 * Características:
 * - Altura fija de 130dp.
 * - Esquinas superiores redondeadas de 25dp.
 * - Color de fondo adaptativo según el tema (usa surfaceVariant).
 * - Compatible con Edge-to-Edge: el fondo cubre el área de la barra de
 *   navegación del sistema, y reserva el espacio inferior (insets) para
 *   que el contenido no quede tapado por la barra de gestos.
 * - Animaciones:
 *   - Home, Songs, Albums: relleno progresivo de abajo hacia arriba (outline → filled)
 *   - Playlists: relleno de nota musical + movimiento horizontal de líneas
 */
@Composable
fun BottomNavigationPanel(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp),
        shape = RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavTab.entries.forEach { tab ->
                    NavIcon(
                        tab = tab,
                        isSelected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Pestañas de navegación disponibles.
 */
private enum class NavTab {
    HOME, SONGS, ALBUMS, PLAYLISTS
}

/**
 * Icono de navegación individual con animaciones de estado.
 *
 * @param tab Pestaña que representa este icono
 * @param isSelected Si está actualmente seleccionado
 * @param onClick Callback al hacer clic
 * @param modifier Modificador del componente
 */
@Composable
private fun NavIcon(
    tab: NavTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = LocalNavIconActiveColor.current
    val inactiveColor = LocalNavIconInactiveColor.current

    // Animación del progreso de relleno (0f = outline, 1f = filled)
    val fillProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "fillProgress"
    )

    // Animación específica para Playlist: offset horizontal de las líneas
    val playlistLinesOffset by animateFloatAsState(
        targetValue = if (isSelected) 8f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "playlistLinesOffset"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        when (tab) {
            NavTab.HOME -> {
                AnimatedIcon(
                    painter = painterResource(R.drawable.ic_nav_home),
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    fillProgress = fillProgress,
                    onClick = onClick
                )
            }
            NavTab.SONGS -> {
                AnimatedIcon(
                    painter = painterResource(R.drawable.ic_nav_music),
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    fillProgress = fillProgress,
                    onClick = onClick
                )
            }
            NavTab.ALBUMS -> {
                AnimatedIcon(
                    painter = painterResource(R.drawable.ic_nav_albums),
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    fillProgress = fillProgress,
                    onClick = onClick
                )
            }
            NavTab.PLAYLISTS -> {
                AnimatedPlaylistIcon(
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    fillProgress = fillProgress,
                    linesOffset = playlistLinesOffset,
                    onClick = onClick
                )
            }
        }
    }
}

/**
 * Icono con animación de relleno de abajo hacia arriba.
 *
 * Superpone dos versiones del icono (outline y filled) y usa un clip
 * rectangular animado que crece desde la base para revelar gradualmente
 * la versión filled.
 */
@Composable
private fun AnimatedIcon(
    painter: Painter,
    activeColor: androidx.compose.ui.graphics.Color,
    inactiveColor: androidx.compose.ui.graphics.Color,
    fillProgress: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Versión outline (siempre visible en el fondo)
        Icon(
            painter = painter,
            contentDescription = null,
            tint = inactiveColor,
            modifier = Modifier.size(32.dp)
        )

        // Versión filled con clip animado (se revela de abajo hacia arriba)
        if (fillProgress > 0f) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(BottomToTopClipShape(fillProgress))
            ) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * Icono específico de Playlist con animación de líneas.
 *
 * Separa la nota musical (con relleno animado) de las líneas horizontales
 * (con offset animado horizontal).
 */
@Composable
private fun AnimatedPlaylistIcon(
    activeColor: androidx.compose.ui.graphics.Color,
    inactiveColor: androidx.compose.ui.graphics.Color,
    fillProgress: Float,
    linesOffset: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Nota musical con relleno animado
        Box(modifier = Modifier.size(32.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_nav_playlist),
                contentDescription = null,
                tint = inactiveColor,
                modifier = Modifier.size(32.dp)
            )
            if (fillProgress > 0f) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(BottomToTopClipShape(fillProgress))
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_playlist),
                        contentDescription = null,
                        tint = activeColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Shape personalizada que recorta un área rectangular desde la parte inferior
 * hacia arriba, según un progreso (0f = nada visible, 1f = completamente visible).
 *
 * Se usa para la animación de relleno de abajo hacia arriba.
 */
private class BottomToTopClipShape(private val progress: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val clipHeight = size.height * progress
        val rect = Rect(
            left = 0f,
            top = size.height - clipHeight,
            right = size.width,
            bottom = size.height
        )
        return Outline.Rectangle(rect)
    }
}