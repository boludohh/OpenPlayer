package com.openplayer.music.ui.screens.tracks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.openplayer.music.R
import com.openplayer.music.data.model.Song
import com.openplayer.music.ui.theme.LocalCoverPlaceholderIconColor
import com.openplayer.music.ui.theme.LocalCurrentTrackColor
import com.openplayer.music.ui.theme.LocalListItemMetaColor
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
 * - **Textos** (centro): título (16sp) a 10dp del tope del
 *   contenedor, artista (13sp) 4dp debajo del título.
 *   Ambos con recorte visual con "..." si superan el límite de
 *   15dp antes del texto de duración (solo visual, nunca se
 *   modifica el dato real de la canción).
 * - **Duración** (derecha, 13sp): duración real de la pista en
 *   formato mm:ss (o h:mm:ss), centrada verticalmente.
 * - **Icono more vert** (extremo derecho): glifo de 24dp dentro de
 *   un área de toque de 44×64dp situada a 15dp del borde derecho
 *   (el mismo margen que el icono de búsqueda en TopActionBar).
 *   Centrado verticalmente por construcción: 20dp de padding
 *   óptico superior e inferior (20 + 24 + 20 = 64dp). Con callback
 *   [onMoreClick] independiente del tap de reproducción de la fila.
 *
 * ## Indicador de pista actual
 * Cuando [isCurrentTrack] es true, el fondo del Row se colorea con
 * [LocalCurrentTrackColor] (#E8E8E8 claro, #202020 oscuro, #101010
 * AMOLED), indicando visualmente cuál es la pista que está sonando.
 * El color se aplica SOLO al fondo del contenedor, detrás de la
 * carátula, los textos y los iconos (no se superpone a ellos).
 * El indicador se actualiza reactivamente según el estado del
 * reproductor (no según taps del usuario), por lo que tocar
 * repetidamente la misma pista no causa parpadeo.
 *
 * ## Sin ripple de Material
 * El efecto de onda (ripple) al tocar está deshabilitado en toda
 * la fila y en el botón more vert, usando `indication = null` con
 * un InteractionSource propio. El click sigue siendo funcional pero
 * sin feedback visual de toque.
 *
 * ## Separaciones ópticas
 * - Carátula al borde izquierdo: 24dp (igual que el margen del
 *   título de pestaña y conteo).
 * - Carátula a los textos: 16dp.
 * - Textos a la duración: 15dp (límite de recorte con ellipsis).
 * - Duración al glifo more vert: 16dp (6dp de separador + 10dp de
 *   centrado del glifo de 24dp dentro de su área de toque de 44dp).
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
 * @param isCurrentTrack true si esta canción es la que está sonando
 *                       actualmente en el reproductor. Aplica fondo
 *                       de color al contenedor.
 * @param onClick Callback invocado al tocar cualquier parte de la fila.
 *                La lógica de reproducción vive en la pantalla padre.
 * @param onMoreClick Callback invocado al tocar el icono more vert.
 *                    Sin funcionalidad asociada todavía (futuro menú
 *                    de opciones de la pista).
 */
@Composable
fun TrackRow(
    song: Song,
    coverFile: File?,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val metaColor = LocalListItemMetaColor.current
    val placeholderBg = MaterialTheme.colorScheme.surfaceVariant
    val placeholderIconColor = LocalCoverPlaceholderIconColor.current
    val currentTrackBg = LocalCurrentTrackColor.current

    // InteractionSource propio para deshabilitar el ripple de Material
    val rowInteractionSource = remember { MutableInteractionSource() }

    // Fondo del Row: color de pista actual si corresponde, transparente si no
    val rowBackground = if (isCurrentTrack) currentTrackBg else MaterialTheme.colorScheme.background

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(rowBackground)
            .clickable(
                interactionSource = rowInteractionSource,
                indication = null,
                onClick = onClick
            )
            .padding(end = 15.dp) // Margen derecho del área de toque del more vert
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

        // Bloque de textos: título a 10dp del tope, artista 4dp debajo.
        // weight(1f) limita el ancho disponible: si el título o el
        // artista superan el límite de 15dp antes de la duración, se
        // recortan visualmente con "..." (TextOverflow.Ellipsis).
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 16.dp, end = 15.dp, top = 10.dp, bottom = 10.dp)
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

        // Duración real de la pista, centrada verticalmente
        Text(
            text = formatDuration(song.duration),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp
            ),
            color = metaColor,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterVertically)
        )

        // Separador: 6dp + 10dp de centrado del glifo en su área de
        // toque = 16dp ópticos entre la duración y el glifo more vert.
        Spacer(modifier = Modifier.width(6.dp))

        // InteractionSource propio para el more vert (sin ripple)
        val moreInteractionSource = remember { MutableInteractionSource() }

        // Icono more vert: área de toque de 44×64dp (toda la altura
        // del contenedor), con padding vertical óptico de 20dp arriba
        // y abajo para centrar el glifo de 24dp por construcción
        // (20 + 24 + 20 = 64dp). El centro horizontal del glifo queda
        // a 37dp del borde derecho del contenedor, alineado con el
        // icono de búsqueda del TopActionBar. El clickable consume el
        // evento antes de que llegue al Row, por lo que el tap en el
        // more vert no dispara la reproducción de la fila.
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .clickable(
                    interactionSource = moreInteractionSource,
                    indication = null,
                    onClick = onMoreClick
                )
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.cd_more_options),
                tint = metaColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Formatea una duración en milisegundos como texto visual mm:ss
 * (o h:mm:ss si supera la hora). Es únicamente para presentación:
 * no modifica el dato real de la canción.
 */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$minutes:$ss"
}