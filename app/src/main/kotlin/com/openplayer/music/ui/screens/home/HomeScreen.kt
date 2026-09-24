package com.openplayer.music.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalTracksCountTextColor

/**
 * Pantalla de inicio de OpenPlayer.
 *
 * Actualmente muestra un placeholder centrado y la atribución de
 * Deezer exigida por sus términos de uso para desarrolladores
 * (uso no comercial e información al usuario de que el contenido
 * es de uso estrictamente privado y del ámbito familiar).
 *
 * ## Geometría de la atribución
 * - Esquina superior IZQUIERDA óptica del contenido de pestaña: mismos
 *   paddings que los títulos de pestaña (start 24dp, top 8dp), por lo
 *   que queda siempre DEBAJO de la barra de estado y de los iconos de
 *   la barra superior (el viewport de MainScreen ya arranca bajo ellos;
 *   ningún contenido se dibuja encima).
 * - Texto pequeño de 10sp con color secundario
 *   ([LocalTracksCountTextColor]) para no competir visualmente.
 * - Respeta RTL automáticamente mediante Alignment.TopStart.
 *
 * En futuras iteraciones contendrá:
 * - Saludo personalizado
 * - Reproducción reciente
 * - Álbumes destacados
 * - Listas sugeridas
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val attributionColor = LocalTracksCountTextColor.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.home_screen_placeholder),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Atribución de Deezer (términos de uso para desarrolladores):
        // informa la fuente de las imágenes de artista y el ámbito de
        // uso privado y familiar del contenido.
        // Esquina superior izquierda óptica, bajo la barra de estado.
        Text(
            text = stringResource(R.string.artist_images_attribution),
            fontSize = 10.sp,
            color = attributionColor,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 8.dp)
        )
    }
}