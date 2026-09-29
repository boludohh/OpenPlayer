package com.openplayer.music.ui.screens.albums.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openplayer.music.R
import com.openplayer.music.data.model.Song
import com.openplayer.music.ui.theme.LocalCurrentTrackBorderColor
import com.openplayer.music.ui.theme.LocalListItemMetaColor
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor

/**
 * Borde izquierdo del número de pista respecto al borde de la fila.
 * Equivalente al CoverLeftEdge de TrackRow (24dp) para mantener
 * coherencia visual entre ambas filas.
 */
private val IndexLeftEdge = 24.dp

/** Ancho del área reservada al número de pista (equivalente al ancho de la carátula). */
private val IndexWidth = 52.dp

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
 * el número de pista (lado izquierdo) o el glifo more vert (lado derecho).
 */
private val CurrentTrackBorderGap = 4.dp

/** Radio exterior de las esquinas del recuadro indicador. */
private val CurrentTrackBorderCorner = 12.dp

/**
 * Fila de pista para el detalle de álbum.
 *
 * Mantiene exactamente la misma geometría y separaciones que
 * [com.openplayer.music.ui.screens.tracks.components.TrackRow] pero
 * reemplaza la carátula por un número de pista centrado en el
 * espacio de 52dp. NO muestra el icono placeholder: solo el número.
 *
 * ## Composición visual
 * - **Número de pista** (izquierda, 52dp de ancho, 64dp de alto):
 *   número 13sp bold, centrado vertical y horizontalmente, color
 *   [LocalListItemSubtitleColor]. Reemplaza el espacio de la carátula.
 * - **Textos** (centro): título (16sp) a 10dp del tope, artista
 *   (13sp) 4dp debajo. Recorte con ellipsis si exceden el límite.
 * - **Duración** (derecha, 13sp): duración real en formato mm:ss
 *   (o h:mm:ss), centrada verticalmente.
 * - **Icono more vert** (extremo derecho): glifo 24dp dentro de
 *   área de toque 44×64dp, a 15dp del borde derecho.
 *
 * ## Indicador de pista actual
 * Idéntico a TrackRow: recuadro bordeado con `LocalCurrentTrackBorderColor`,
 * trazo 2dp, esquinas 12dp, gap 4dp.
 *
 * ## Sin ripple
 * `indication = null` en toda la fila y en more vert.
 *
 * @param song Canción a renderizar.
 * @param trackIndex Número de pista (1-based) para mostrar a la izquierda.
 * @param isCurrentTrack true si es la pista actualmente en reproducción.
 * @param onClick Callback al tocar cualquier parte de la fila.
 * @param onMoreClick Callback al tocar el icono more vert.
 * @param modifier Modificador externo.
 */
@Composable
fun AlbumDetailTrackRow(
    song: Song,
    trackIndex: Int,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val metaColor = LocalListItemMetaColor.current
    val currentTrackBorderColor = LocalCurrentTrackBorderColor.current
    val backgroundColor = MaterialTheme.colorScheme.background

    val rowInteractionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(backgroundColor)
            .drawWithContent {
                drawContent()
                if (isCurrentTrack) {
                    val strokePx = CurrentTrackBorderStroke.toPx()
                    val halfPx = strokePx / 2f

                    val leftCenterPx =
                        (IndexLeftEdge - CurrentTrackBorderGap).toPx() - halfPx

                    val rightCenterPx =
                        size.width - ((MoreGlyphRightEdgeFromEnd - CurrentTrackBorderGap).toPx() - halfPx)

                    drawRoundRect(
                        color = currentTrackBorderColor,
                        topLeft = Offset(leftCenterPx, halfPx),
                        size = androidx.compose.ui.geometry.Size(
                            width = rightCenterPx - leftCenterPx,
                            height = size.height - strokePx
                        ),
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
            .padding(end = 15.dp)
    ) {
        // Número de pista (reemplaza la carátula)
        Box(
            modifier = Modifier
                .padding(start = 24.dp)
                .width(IndexWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = trackIndex.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp
                ),
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = subtitleColor,
                textAlign = TextAlign.Center
            )
        }

        // Bloque de textos (idéntico a TrackRow)
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

        // Duración
        Text(
            text = formatTrackDuration(song.duration),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp
            ),
            color = metaColor,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterVertically)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // More vert
        val moreInteractionSource = remember { MutableInteractionSource() }
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
 * Formatea duración en mm:ss (o h:mm:ss si >= 1 hora).
 */
private fun formatTrackDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$minutes:$ss"
}