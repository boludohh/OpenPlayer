package com.openplayer.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Panel inferior de navegación de OpenPlayer.
 *
 * Actualmente es un contenedor vacío que servirá como base para los iconos
 * de las pestañas de navegación (Home, Songs, Albums, Playlists).
 *
 * Características:
 * - Altura fija de 130dp.
 * - Esquinas superiores redondeadas de 25dp.
 * - Color de fondo adaptativo según el tema (usa surfaceVariant).
 * - Compatible con Edge-to-Edge: el fondo cubre el área de la barra de
 *   navegación del sistema, y reserva el espacio inferior (insets) para
 *   que el futuro contenido no quede tapado por la barra de gestos.
 */
@Composable
fun BottomNavigationPanel(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp),
        shape = RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        // Contenedor interno que respeta los insets de la barra de navegación
        // para que el futuro contenido (iconos) no quede tapado por los gestos.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Vacío por ahora, listo para recibir los iconos de navegación.
        }
    }
}