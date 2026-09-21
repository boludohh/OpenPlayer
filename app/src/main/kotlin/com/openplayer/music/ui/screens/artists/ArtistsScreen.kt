package com.openplayer.music.ui.screens.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalScreenTitleColor
import com.openplayer.music.ui.theme.screenTitle

/**
 * Pantalla de artistas de OpenPlayer.
 *
 * Actualmente muestra el título de pestaña "Artistas" con la misma
 * geometría óptica que TracksScreen (padding start=24dp, top=8dp,
 * end=15dp; estilo screenTitle con IBM Plex Sans Bold según idioma;
 * color LocalScreenTitleColor).
 *
 * En futuras iteraciones contendrá:
 * - Lista de artistas agrupados por nombre
 * - Carátulas de artista (MusicBrainz → Fanart.tv → Room → Coil)
 * - Cantidad de canciones y álbumes por artista
 * - Navegación a detalle del artista
 *
 * El título de pestaña se posiciona bajo la barra de acción superior
 * (TopActionBar) con los mismos paddings ópticos que TracksScreen,
 * garantizando alineación visual consistente entre pestañas.
 */
@Composable
fun ArtistsScreen(modifier: Modifier = Modifier) {
    val screenTitleColor = LocalScreenTitleColor.current
    val backgroundColor = MaterialTheme.colorScheme.background

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Título de pestaña con geometría óptica idéntica a TracksScreen.
        // Geometría óptica (bajo la barra de estado):
        // - Base de iconos: 35dp
        // - Título: 35 + 20 = 55dp (compensando ~4dp de leading de fuente 36sp)
        // - Viewport del scroll: 43dp (definido en MainScreen)
        // - Padding top del título: 55 - 43 - 4 = 8dp (compensación óptica)
        // - start = 24dp: alineado con el glifo del icono de menú
        //   (15dp del Row + 9dp de centrado del glifo de 26dp en área de 44dp)
        // - end = 15dp: margen derecho simétrico con TopActionBar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 8.dp, end = 15.dp)
        ) {
            // Título de pestaña
            androidx.compose.material3.Text(
                text = stringResource(R.string.artists_screen_placeholder),
                style = screenTitle(),
                color = screenTitleColor,
                textAlign = TextAlign.Start
            )
        }
    }
}