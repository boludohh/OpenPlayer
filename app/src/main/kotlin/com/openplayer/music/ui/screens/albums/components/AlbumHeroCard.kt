package com.openplayer.music.ui.screens.albums.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalCardL2Color
import com.openplayer.music.ui.theme.LocalCoverPlaceholderIconColor
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import java.io.File

/** Tamaño del thumbnail del hero. */
private val HeroCoverSize = 116.dp

/** Esquinas de la card hero completa. */
private val HeroCardCorner = 24.dp

/** Esquinas del thumbnail del hero. */
private val HeroCoverCorner = 18.dp

/** Padding interno de la card hero. */
private val HeroCardPadding = 14.dp

/**
 * Card hero del detalle de álbum. Muestra la portada grande a la
 * izquierda, información del álbum a la derecha, y chips de metadatos
 * debajo (año, cantidad de pistas, duración total).
 *
 * ## Composición (Grid 2 columnas)
 * - **Columna 1**: portada 116×116dp, esquinas 18dp, con badge "Nuevo"
 *   opcional en esquina superior izquierda.
 * - **Columna 2**: título (22sp, ExtraBold) y artista (14sp, semi-bold).
 * - **Fila inferior (span completo)**: chips con año, pistas y duración.
 *
 * ## Estilo
 * - Fondo `surfaceVariant` (cardL1).
 * - Borde 1dp con `outline` (borderL1).
 * - Esquinas 24dp.
 * - Padding 14dp.
 *
 * @param title Título del álbum.
 * @param artist Artista del álbum.
 * @param year Año de lanzamiento (null si desconocido).
 * @param trackCount Cantidad de pistas.
 * @param totalDurationMs Duración total del álbum en milisegundos.
 * @param isNew Si muestra el badge "Nuevo".
 * @param coverFile Archivo de la portada (null si no existe).
 * @param modifier Modificador externo.
 */
@Composable
fun AlbumHeroCard(
    title: String,
    artist: String,
    year: Int?,
    trackCount: Int,
    totalDurationMs: Long,
    isNew: Boolean,
    coverFile: File?,
    modifier: Modifier = Modifier
) {
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val surfaceL1 = MaterialTheme.colorScheme.surfaceVariant
    val surfaceL2 = LocalCardL2Color.current
    val borderL1 = MaterialTheme.colorScheme.outline
    val borderL2 = MaterialTheme.colorScheme.outlineVariant
    val placeholderIconColor = LocalCoverPlaceholderIconColor.current
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HeroCardCorner))
            .background(surfaceL1)
            .border(1.dp, borderL1, RoundedCornerShape(HeroCardCorner))
            .padding(HeroCardPadding)
    ) {
        // Fila superior: portada + info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Portada
            Box(
                modifier = Modifier
                    .size(HeroCoverSize)
                    .clip(RoundedCornerShape(HeroCoverCorner))
                    .background(surfaceL2)
                    .border(1.dp, borderL2, RoundedCornerShape(HeroCoverCorner)),
                contentAlignment = Alignment.Center
            ) {
                // Icono placeholder
                Icon(
                    painter = painterResource(id = R.drawable.ic_nav_albums_filled),
                    contentDescription = null,
                    tint = placeholderIconColor,
                    modifier = Modifier.size(44.dp)
                )

                // Carátula encima
                if (coverFile != null) {
                    val thumbnailPx = (HeroCoverSize.value * density.density).toInt()
                    val request = ImageRequest.Builder(LocalContext.current)
                        .data(coverFile)
                        .size(Size(thumbnailPx, thumbnailPx))
                        .build()
                    AsyncImage(
                        model = request,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(HeroCoverSize)
                    )
                }

                // Badge "Nuevo"
                if (isNew) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(surfaceL2.copy(alpha = 0.85f))
                            .border(1.dp, borderL2, RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.album_badge_new),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                letterSpacing = 0.08.sp
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            color = titleColor
                        )
                    }
                }
            }

            // Info (título + artista)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 22.sp,
                        letterSpacing = (-0.035).sp
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Chips de especificaciones (debajo, span completo)
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Año
            if (year != null) {
                SpecChip(text = year.toString())
            }

            // Cantidad de pistas
            SpecChip(
                text = stringResource(R.string.album_detail_tracks_count_format, trackCount)
            )

            // Duración total
            SpecChip(text = formatAlbumDuration(totalDurationMs))
        }
    }
}

/**
 * Chip individual de especificación (año, pistas, duración).
 * Fondo L2, borde L2, texto secondary.
 */
@Composable
private fun SpecChip(text: String) {
    val surfaceL2 = LocalCardL2Color.current
    val borderL2 = MaterialTheme.colorScheme.outlineVariant
    val textColor = LocalListItemSubtitleColor.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(surfaceL2)
            .border(1.dp, borderL2, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                letterSpacing = 0.04.sp
            ),
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/**
 * Formatea la duración total del álbum a formato legible:
 * - Si >= 1 hora: "X h Y min"
 * - Si < 1 hora: "Y min"
 */
@Composable
private fun formatAlbumDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.album_detail_total_duration_hours_minutes, hours.toInt(), minutes.toInt())
    } else {
        stringResource(R.string.album_detail_total_duration_minutes, minutes.toInt())
    }
}