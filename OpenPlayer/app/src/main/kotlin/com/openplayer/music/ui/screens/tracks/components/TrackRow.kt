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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.openplayer.music.R
import com.openplayer.music.data.model.Song
import com.openplayer.music.ui.theme.LocalCoverPlaceholderIconColor
import com.openplayer.music.ui.theme.LocalCurrentTrackBorderColor
import com.openplayer.music.ui.theme.LocalListItemMetaColor
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import java.io.File

/**
 * Borde izquierdo de la carátula respecto al borde de la fila.
 * Replica el padding start de 24dp aplicado al Box de la carátula;
 * se declara como constante para poder calcular la geometría del
 * recuadro indicador sin alterar el layout existente.
 */
private val CoverLeftEdge = 24.dp

/**
 * Borde derecho del glifo more vert respecto al borde derecho de la
 * fila: 15dp de padding end del Row + 10dp de centrado del glifo de
 * 24dp dentro de su área de toque de 44dp (15 + 10 = 25dp).
 */
private val MoreGlyphRightEdgeFromEnd = 25.dp

/** Grosor del trazo del recuadro indicador de pista actual. */
private val CurrentTrackBorderStroke = 2.dp

/**
 * Separación óptica entre el borde interno del trazo del recuadro y
 * la carátula (lado izquierdo) o el glifo more vert (lado derecho).
 */
private val CurrentTrackBorderGap = 4.dp

/**
 * Radio exterior de las esquinas del recuadro indicador.
 * Valor óptico proporcional al contenedor de 64dp.
 */
private val CurrentTrackBorderCorner = 12.dp

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
 * Cuando [isCurrentTrack] es true, se dibuja un recuadro bordeado
 * como overlay puramente visual (trazo de 2dp, esquinas de 12dp):
 * - Línea vertical izquierda a 4dp ópticos de la carátula.
 * - Línea vertical derecha a 4dp ópticos del glifo more vert.
 * - Líneas superior e inferior en el límite del contenedor (64dp),
 *   sin sobrepasarlo ni invadir las filas contiguas.
 * El color proviene de [LocalCurrentTrackBorderColor] (#1A1A1A
 * claro, #F5F5F5 oscuro, #E5E5E5 AMOLED). El contenedor real NO se
 * modifica: sigue siendo 64dp de alto y ancho completo, y su fondo
 * permanece con el color de fondo del tema (el antiguo fondo de
 * color del indicador se eliminó por ser un indicador genérico).
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
 * ## Optimización de memoria de carátulas
 * La carátula se carga vía [ImageRequest] con tamaño fijo de 52dp
 * (convertido a píxeles según densidad de pantalla). Coil hace
 * downsampling durante el decode (inSampleSize) en vez de cargar
 * la imagen completa y escalarla, reduciendo drásticamente el pico
 * de memoria al scrollear listas largas. El resultado visual es
 * idéntico (la carátula ya se renderiza a 52×52dp).
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
 *                       actualmente en el reproductor. Dibuja el
 *                       recuadro bordeado indicador sobre la fila.
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
    val currentTrackBorderColor = LocalCurrentTrackBorderColor.current
    val density = LocalDensity.current

    // InteractionSource propio para deshabilitar el ripple de Material
    val rowInteractionSource = remember { MutableInteractionSource() }

    // Fondo del Row: siempre el color de fondo del tema. El indicador
    // de pista actual ahora es el recuadro bordeado que se dibuja en
    // el drawWithContent más abajo (overlay puramente visual).
    val rowBackground = MaterialTheme.colorScheme.background

    // Tamaño del thumbnail de la carátula en píxeles para Coil.
    // Coil hace downsampling durante el decode (inSampleSize) en
    // lugar de cargar la imagen completa, reduciendo el pico de
    // memoria al scrollear. El resultado visual es idéntico.
    // density.density es el float de densidad de pantalla (ej. 2.0 para xxhdpi).
    val thumbnailPx = (52 * density.density).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(rowBackground)
            // Recuadro indicador de pista actual (overlay visual).
            // Se dibuja DESPUÉS del contenido para garantizar su
            // visibilidad; no interseca carátula, textos ni iconos
            // porque vive en los márgenes ópticos de la fila.
            .drawWithContent {
                drawContent()
                if (isCurrentTrack) {
                    val strokePx = CurrentTrackBorderStroke.toPx()
                    val halfPx = strokePx / 2f

                    // Centro del trazo vertical izquierdo: borde de la
                    // carátula (24dp) - separación óptica (4dp) - medio
                    // trazo (1dp) = 19dp. El borde interno del trazo
                    // queda exactamente a 4dp de la carátula.
                    val leftCenterPx =
                        (CoverLeftEdge - CurrentTrackBorderGap).toPx() - halfPx

                    // Centro del trazo vertical derecho: borde del glifo
                    // more vert (25dp desde el final) - separación óptica
                    // (4dp) - medio trazo (1dp) = 20dp desde el borde
                    // derecho de la fila. El borde interno del trazo
                    // queda exactamente a 4dp del glifo.
                    val rightCenterPx =
                        size.width - ((MoreGlyphRightEdgeFromEnd - CurrentTrackBorderGap).toPx() - halfPx)

                    // Trazo superior e inferior centrados a medio trazo
                    // del límite del contenedor: ocupan [0, 2dp] y
                    // [62dp, 64dp] sin invadir las filas contiguas.
                    drawRoundRect(
                        color = currentTrackBorderColor,
                        topLeft = Offset(leftCenterPx, halfPx),
                        // Nombre completo para evitar la colisión con
                        // coil3.size.Size (usado por el ImageRequest de
                        // la carátula). Sin este calificativo, Kotlin
                        // no sabría cuál de las dos clases "Size" usar.
                        size = androidx.compose.ui.geometry.Size(
                            width = rightCenterPx - leftCenterPx,
                            height = size.height - strokePx
                        ),
                        // Radio de línea centro = radio exterior (12dp)
                        // - medio trazo, para que el borde exterior del
                        // trazo tenga exactamente 12dp ópticos.
                        cornerRadius = CornerRadius(
                            CurrentTrackBorderCorner.toPx() - halfPx
                        ),
                        style = Stroke(strokePx)
                    )
                }
            }
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

            // Carátula encima del placeholder (solo si existe).
            // ImageRequest con tamaño fijo para que Coil haga
            // downsampling durante el decode, reduciendo memoria.
            if (coverFile != null) {
                val request = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(coverFile)
                    .size(Size(thumbnailPx, thumbnailPx))
                    .build()
                AsyncImage(
                    model = request,
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