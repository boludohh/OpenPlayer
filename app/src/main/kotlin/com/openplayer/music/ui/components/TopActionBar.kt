package com.openplayer.music.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalTopBarIconColor

/**
 * Barra de acción superior de OpenPlayer.
 *
 * Contiene dos iconos de acción (menú y búsqueda) posicionados en la
 * esquina superior izquierda de la pantalla principal. Los iconos son
 * actualmente solo visuales, sin funcionalidad asociada.
 *
 * Especificaciones:
 * - Dos iconos de 28dp de tamaño con área de toque de 44dp (padding 8dp)
 * - Primer icono (menú): 15dp del margen izquierdo
 * - Segundo icono (búsqueda): 15dp del primer icono
 * - Color adaptativo según el tema usando [LocalTopBarIconColor]
 * - Sin padding superior propio: el contenedor padre aplica
 *   statusBarsPadding, por lo que la barra queda pegada justo debajo
 *   de la barra de estado. El área de toque de 44dp centra el glifo
 *   de 28dp, dejando 8dp de margen óptico superior (glifo en 8–36dp
 *   relativos al inicio del contenido bajo la barra de estado).
 *
 * @param modifier Modificador externo que aplica el posicionamiento
 *                 desde el contenedor padre.
 */
@Composable
fun TopActionBar(modifier: Modifier = Modifier) {
    val iconColor = LocalTopBarIconColor.current

    Row(
        modifier = modifier.padding(start = 15.dp)
    ) {
        // Icono de menú (tres líneas)
        IconButton(
            onClick = { /* TODO: Implementar funcionalidad */ },
            modifier = Modifier.size(44.dp) // Área de toque recomendada por Material Design
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_menu),
                contentDescription = "Menú",
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
        }

        // Icono de búsqueda (lupa) - 15dp de separación del anterior
        IconButton(
            onClick = { /* TODO: Implementar funcionalidad */ },
            modifier = Modifier
                .size(44.dp)
                .padding(start = 15.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_search),
                contentDescription = "Buscar",
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}