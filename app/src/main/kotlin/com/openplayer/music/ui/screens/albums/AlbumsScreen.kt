package com.openplayer.music.ui.screens.albums

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
 * Pantalla de álbumes de OpenPlayer.
 *
 * Actualmente muestra un placeholder. En futuras iteraciones contendrá:
 * - Grid de álbumes agrupados por nombre de álbum
 * - Portada, nombre del álbum y artista
 * - Navegación a detalle del álbum
 */
@Composable
fun AlbumsScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.albums_screen_placeholder),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}