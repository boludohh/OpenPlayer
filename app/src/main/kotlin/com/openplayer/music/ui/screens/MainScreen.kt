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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openplayer.music.OpenPlayerApplication
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.ui.components.BottomNavigationPanel
import com.openplayer.music.ui.components.TopActionBar
import com.openplayer.music.ui.screens.albums.AlbumsScreen
import com.openplayer.music.ui.screens.artists.ArtistsScreen
import com.openplayer.music.ui.screens.home.HomeScreen
import com.openplayer.music.ui.screens.playlist.PlaylistScreen
import com.openplayer.music.ui.screens.tracks.TracksScreen

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
 * - Navegación entre 5 pestañas: Home, Tracks, Albums, Artists, Playlists
 * - Transición fade entre pantallas (300ms)
 * - Barra de acción superior con iconos de menú y búsqueda
 * - Panel inferior siempre visible con animación de elevación en icono activo
 * - Padding inferior de 130dp para no ocultar contenido bajo el panel
 * - Padding superior de 43dp: los glifos de la barra de acción ocupan
 *   9–35dp (glifo de 26dp centrado en su área de toque de 44dp); la base
 *   del glifo (35dp) + 8dp de respiro = 43dp, que es la línea de recorte
 *   del contenido scrolleable. Así ningún contenido pasa jamás detrás de
 *   los iconos. Cada pantalla gestiona su propia franja de desvanecido
 *   superior dentro de su viewport (p. ej. TracksScreen con 32dp) y
 *   posiciona su primer elemento por debajo de dicha franja.
 * - StatusBarsPadding aplicado al contenedor raíz para que el contenido no
 *   quede detrás de la barra de estado del sistema.
 */
@Composable
fun MainScreen(audioRepository: AudioRepository) {
    val context = LocalContext.current
    // Estado de navegación compartido con el panel inferior
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }

    // Singleton de PlaylistRepository desde la Application: compartido
    // por toda la app y pasado como parámetro a la pantalla de listas.
    val playlistRepository = remember {
        (context.applicationContext as OpenPlayerApplication).playlistRepository
    }

    // Singleton de PlaybackHistoryRepository desde la Application: compartido
    // por toda la app y pasado como parámetro a la pantalla de inicio.
    val playbackHistoryRepository = remember {
        (context.applicationContext as OpenPlayerApplication).playbackHistoryRepository
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding() // Reserva el espacio de la barra de estado una sola vez
    ) {
        // Contenido principal con transición fade entre pantallas
        // Padding top = 43dp: base de los glifos de la barra superior (35dp)
        //   + 8dp de respiro. El recorte del scroll ocurre aquí, por debajo
        //   de los iconos; cada pantalla dibuja su fade superior interno.
        // Padding bottom = 130dp (altura del panel de navegación inferior)
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            },
            label = "screenTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 43.dp, bottom = 130.dp)
        ) { tab ->
            when (tab) {
                NavTab.HOME -> HomeScreen(
                    audioRepository = audioRepository,
                    playbackHistoryRepository = playbackHistoryRepository
                )
                NavTab.TRACKS -> TracksScreen(audioRepository = audioRepository)
                NavTab.ALBUMS -> AlbumsScreen(audioRepository = audioRepository)
                NavTab.ARTISTS -> ArtistsScreen(audioRepository = audioRepository)
                NavTab.PLAYLISTS -> PlaylistScreen(playlistRepository = playlistRepository)
            }
        }

        // Barra de acción superior con iconos de menú y búsqueda
        TopActionBar(
            modifier = Modifier.align(Alignment.TopStart)
        )

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
    TRACKS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS
}