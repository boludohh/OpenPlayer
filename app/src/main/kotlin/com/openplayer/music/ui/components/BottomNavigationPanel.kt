package com.openplayer.music.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.ui.screens.NavTab
import com.openplayer.music.ui.theme.LocalNavIconActiveColor
import com.openplayer.music.ui.theme.LocalNavIconInactiveColor

/**
 * Panel inferior de navegación de OpenPlayer.
 *
 * Contiene los iconos de las pestañas de navegación con:
 * - Transición crossfade entre estados outline (inactivo) y filled (activo)
 * - Animación de elevación: el icono activo sube 10dp suavemente
 * - El crossfade y la elevación ocurren simultáneamente (300ms)
 *
 * Pestañas disponibles (en orden):
 * 1. Home (Inicio)
 * 2. Songs (Música)
 * 3. Albums (Álbumes)
 * 4. Artists (Artistas)
 * 5. Playlists (Listas de reproducción)
 *
 * Características:
 * - Altura fija de 130dp.
 * - Esquinas superiores redondeadas de 25dp.
 * - Color de fondo adaptativo según el tema (usa surfaceVariant).
 * - Compatible con Edge-to-Edge.
 * - Efecto ripple desactivado para un aspecto más limpio al hacer clic.
 */
@Composable
fun BottomNavigationPanel(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
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
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Icono de navegación individual con:
 * - Transición crossfade entre outline y filled
 * - Animación de elevación vertical (0dp inactivo, -10dp activo)
 * - Ambas animaciones sincronizadas en 300ms
 *
 * El efecto visual de ripple está desactivado intencionalmente.
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
    
    // InteractionSource para desactivar el ripple effect
    val interactionSource = remember { MutableInteractionSource() }

    // Animación de elevación: sube 10dp cuando está activo
    val offsetAnimation by animateDpAsState(
        targetValue = if (isSelected) (-10).dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "iconElevation"
    )

    val (outlineRes, filledRes) = when (tab) {
        NavTab.HOME -> R.drawable.ic_nav_home_outline to R.drawable.ic_nav_home_filled
        NavTab.SONGS -> R.drawable.ic_nav_music_outline to R.drawable.ic_nav_music_filled
        NavTab.ALBUMS -> R.drawable.ic_nav_albums_outline to R.drawable.ic_nav_albums_filled
        NavTab.ARTISTS -> R.drawable.ic_nav_artists_outline to R.drawable.ic_nav_artists_filled
        NavTab.PLAYLISTS -> R.drawable.ic_nav_playlist_outline to R.drawable.ic_nav_playlist_filled
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .size(48.dp)
            .offset(y = offsetAnimation) // Aplicar elevación animada
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = isSelected,
            animationSpec = tween(durationMillis = 300),
            label = "navIconCrossfade"
        ) { selected ->
            val painter = if (selected) painterResource(filledRes) else painterResource(outlineRes)
            val tint = if (selected) activeColor else inactiveColor

            Icon(
                painter = painter,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}