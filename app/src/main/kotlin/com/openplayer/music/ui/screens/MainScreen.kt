package com.openplayer.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.ui.components.BottomNavigationPanel
import com.openplayer.music.ui.screens.albums.AlbumsScreen
import com.openplayer.music.ui.screens.artists.ArtistsScreen
import com.openplayer.music.ui.screens.home.HomeScreen
import com.openplayer.music.ui.screens.playlist.PlaylistScreen
import com.openplayer.music.ui.screens.songs.SongsScreen

/**
 * Pantalla principal de OpenPlayer.
 *
 * Actúa como contenedor de navegación que muestra una de las 5 pantallas
 * según la pestaña seleccionada en el panel inferior.
 *
 * El estado de navegación (pestaña activa) se mantiene aquí y se comparte
 * con [BottomNavigationPanel] mediante parámetros, permitiendo que el panel
 * controle la navegación y MainScreen renderice la pantalla correspondiente.
 *
 * Características:
 * - Navegación entre 5 pestañas: Home, Songs, Albums, Artists, Playlists
 * - Transición fade entre pantallas (300ms)
 * - Panel inferior siempre visible con animación de elevación en icono activo
 * - Padding inferior de 130dp para no ocultar contenido bajo el panel
 */
@Composable
fun MainScreen(audioRepository: AudioRepository) {
    // Estado de navegación compartido con el panel inferior
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Contenido principal con transición fade entre pantallas
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            },
            label = "screenTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 130.dp)
        ) { tab ->
            when (tab) {
                NavTab.HOME -> HomeScreen()
                NavTab.SONGS -> SongsScreen(audioRepository = audioRepository)
                NavTab.ALBUMS -> AlbumsScreen()
                NavTab.ARTISTS -> ArtistsScreen()
                NavTab.PLAYLISTS -> PlaylistScreen()
            }
        }

        // Panel de navegación inferior con estado compartido
        BottomNavigationPanel(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Pestañas de navegación disponibles.
 * El orden de declaración define el orden visual de izquierda a derecha.
 * Este enum debe coincidir con el de BottomNavigationPanel.
 */
enum class NavTab {
    HOME,
    SONGS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS
}