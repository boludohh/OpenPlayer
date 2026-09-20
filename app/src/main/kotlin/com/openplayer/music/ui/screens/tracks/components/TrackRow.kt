package com.openplayer.music.ui.screens.tracks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.openplayer.music.R
import com.openplayer.music.data.model.Song
import com.openplayer.music.ui.theme.LocalCoverPlaceholderIconColor
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import java.io.File

/**
 * Contenedor de pista individual para listas de reproducción.
 *
 * Renderiza una fila de 64dp de alto y ancho completo (de borde a
 * borde), con área táctil en toda la superficie para iniciar la
 * reproducción al tocar cualquier parte de la fila.
 *
 * ## Composición visual
 * - **Carátula** (izquierda, 52×52dp, esquinas 8dp): cargada vía
 *   Coil desde el archivo de portada extraído por TagLib durante
 *   el escaneo. Si la canción no tiene portada, se muestra un
 *   placeholder con fondo `surfaceVariant` y el icono
 *   [R.drawable.ic_nav_music_filled] centrado (26dp).
 * - **Textos** (derecha): título (16sp Linotte) a 10dp del tope del
 *   contenedor, artista (13sp Linotte) 4dp debajo del título.
 *
 * ## Separaciones
 * - Carátula al borde izquierdo: 24dp (igual que el margen del
 *   título de pestaña y conteo).
 * - Carátula a los textos: 16dp.
 * - Título al tope del contenedor: 10dp.
 * - Separación vertical título-artista: 4dp.
 * - Separación vertical entre filas: 0dp (las filas se tocan).
 *
 * ## Garantía de integridad de carátulas
 * El archivo de portada proviene de [com.openplayer.music.data.media.CoverRepository],
 * que guarda cada portada con nombre = MD5(path de la canción) + extensión.
 * Coil cachea por ruta de archivo, y el LazyColumn que contiene estas
 * filas usa `key = song.id`, por lo que las carátulas nunca se cruzan
 * entre canciones distintas aunque el usuario haga scroll rápido.
 *
 * @param song Canción a renderizar (modelo de dominio).
 * @param coverFile Archivo de portada en disco (null si la canción
 *                  no tiene portada extraída).
 * @param onClick Callback invocado al tocar cualquier parte de la fila.
 *                La lógica de reproducción vive en la pantalla padre.
 */
@Composable
fun TrackRow(
    song: Song,
    coverFile: File?,
    onClick: () -> Unit
) {
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val placeholderBg = MaterialTheme.colorScheme.surfaceVariant
    val placeholderIconColor = LocalCoverPlaceholderIconColor.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
    ) {
        // Carátula (o placeholder si no hay portada).
        // La carátula se centra verticalmente dentro del contenedor:
        // (64dp - 52dp) / 2 = 6dp de padding top/bottom.
        Box(
            modifier = Modifier
                .padding(start = 24.dp, top = 6.dp, bottom = 6.dp)
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(placeholderBg),
            contentAlignment = Alignment.Center
        ) {
            // Icono placeholder (siempre dibujado debajo)
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_music_filled),
                contentDescription = null,
                tint = placeholderIconColor,
                modifier = Modifier.size(26.dp)
            )

            // Carátula encima del placeholder (solo si existe)
            if (coverFile != null) {
                AsyncImage(
                    model = coverFile,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight()
                )
            }
        }

        // Bloque de textos: título a 10dp del tope, artista 4dp debajo
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp
                ),
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}