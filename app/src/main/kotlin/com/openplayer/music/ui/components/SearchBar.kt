package com.openplayer.music.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openplayer.music.R

/**
 * Barra de búsqueda de OpenPlayer.
 *
 * Componente visual de la barra de búsqueda global, visible en las 5 pestañas
 * principales (Inicio, Música, Álbumes, Artistas, Playlists).
 *
 * Especificaciones:
 * - Altura fija de 35dp.
 * - Forma de píldora (esquinas totalmente redondeadas, radio = 50% de la altura).
 * - Fondo adaptativo según el tema usando `surfaceVariant` del colorScheme.
 * - Texto placeholder ("Buscar") con color `onSurfaceVariant` del tema activo.
 * - Padding interno horizontal de 16dp.
 * - Comportamiento: solo visual por ahora (al tocar no pasa nada).
 *
 * Los márgenes externos (start = 20dp, end = 10dp) y el tope contra la barra
 * de estado se aplican desde el contenedor padre (MainScreen), no desde aquí,
 * para mantener el componente reutilizable y desacoplado del layout global.
 *
 * @param modifier Modificador externo que aplica los márgenes y el posicionamiento
 *                 desde el contenedor padre.
 */
@Composable
fun SearchBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(35.dp),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = stringResource(R.string.search_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}