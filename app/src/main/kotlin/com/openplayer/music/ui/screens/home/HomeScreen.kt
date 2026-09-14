package com.openplayer.music.ui.screens.home

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
 * Pantalla de inicio de OpenPlayer.
 *
 * Actualmente muestra un placeholder. En futuras iteraciones contendrá:
 * - Saludo personalizado
 * - Reproducción reciente
 * - Álbumes destacados
 * - Listas sugeridas
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.home_screen_placeholder),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}