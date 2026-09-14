package com.openplayer.music.ui.screens.playlist

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
 * Pantalla de listas de reproducción de OpenPlayer.
 *
 * Actualmente muestra un placeholder. En futuras iteraciones contendrá:
 * - Listas de reproducción creadas por el usuario
 * - Listas inteligentes (favoritos, más escuchadas, etc.)
 * - Creación y edición de listas personalizadas
 */
@Composable
fun PlaylistScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.playlist_screen_placeholder),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}