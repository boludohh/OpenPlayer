package com.openplayer.music.ui.screens.artists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.openplayer.music.R

/**
 * Pantalla de artistas de OpenPlayer.
 *
 * Actualmente muestra un placeholder. En futuras iteraciones contendrá:
 * - Lista de artistas agrupados por nombre
 * - Cantidad de canciones y álbumes por artista
 * - Navegación a detalle del artista
 */
@Composable
fun ArtistsScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.artists_screen_placeholder),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}