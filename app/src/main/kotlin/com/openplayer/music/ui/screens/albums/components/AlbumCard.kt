package com.openplayer.music.ui.screens.albums.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalCardL2Color
import com.openplayer.music.ui.theme.LocalCoverPlaceholderIconColor
import com.openplayer.music.ui.theme.LocalListItemMetaColor
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import java.io.File

/** Esquinas de la tarjeta completa. */
private val CardCornerSize = 20.dp

/** Esquinas de la portada. */
private val CoverCornerSize = 14.dp

/** Esquinas del badge. */
private val BadgeCornerSize = 999.dp

/** Padding interno de la tarjeta. */
private val CardPadding = 10.dp

/** Separación entre portada e info. */
private val CoverToInfoSpacing = 12.dp

/**
 * Fracción del ancho de la portada que ocupa el icono placeholder.
 * Valor original 0.35f aumentado un 50% para mejor presencia visual
 * (0.35 * 1.5 = 0.525).
 */
private val PlaceholderIconFillFraction = 0.525f

/**
 * Tarjeta individual de un álbum en el grid de la pestaña de Álbumes.
 * Diseño basado en el mockup oficial de OpenPlayer.
 *
 * ## Composición visual
 * - **Contenedor**: fondo `surfaceVariant` (cardL1), borde de 1dp con
 *   `outline` (borderL1), esquinas de 20dp, padding interno de 10dp.
 * - **Portada** (arriba, aspect-ratio 1:1, esquinas 14dp): cargada vía
 *   Coil desde el archivo de portada. Si no hay carátula, se muestra
 *   un placeholder con fondo `cardL2` y el icono `ic_nav_albums_filled`.
 * - **Badge "Nuevo"** (esquina superior izquierda de la portada):
 *   píldora con fondo `cardL2`, borde `borderL2`, texto secondary.
 *   Solo se muestra si [isNew] es true.
 * - **Título del álbum**: 15sp, bold, color highContrast, 1 línea con ellipsis.
 * - **Artista**: 13sp, medium, color secondaryOnBg, 1 línea con ellipsis.
 * - **Meta**: 12sp, año • conteo pistas, color secondaryOnBg.
 *
 * ## Sin ripple de Material
 * El efecto de onda (ripple) al tocar está deshabilitado usando
 * `indication = null` con un InteractionSource propio. El click sigue
 * siendo funcional pero sin feedback visual de toque.
 *
 * ## Optimización de memoria de carátulas
 * La carátula se carga vía [ImageRequest] con tamaño fijo calculado
 * según densidad. Coil hace downsampling durante el decode (inSampleSize)
 * en vez de cargar la imagen completa, reduciendo el pico de memoria
 * al scrollear grids largos.
 *
 * @param title Título del álbum.
 * @param artist Artista del álbum.
 * @param year Año de lanzamiento (null si no está en metadatos).
 * @param trackCount Cantidad de pistas en el álbum.
 * @param isNew Si el álbum fue agregado en los últimos 30 días.
 * @param coverFile Archivo de portada en disco (null si el álbum no
 *                  tiene carátula disponible).
 * @param onClick Callback invocado al tocar la tarjeta.
 * @param modifier Modificador externo que aplica el padre (grid).
 */
@Composable
fun AlbumCard(
    title: String,
    artist: String,
    year: Int?,
    trackCount: Int,
    isNew: Boolean,
    coverFile: File?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val metaColor = LocalListItemMetaColor.current
    val placeholderBg = LocalCardL2Color.current
    val placeholderIconColor = LocalCoverPlaceholderIconColor.current
    val surfaceL1 = MaterialTheme.colorScheme.surfaceVariant
    val borderL1 = MaterialTheme.colorScheme.outline
    val borderL2 = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current

    // InteractionSource propio para deshabilitar el ripple de Material
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCornerSize))
            .background(surfaceL1)
            .border(1.dp, borderL1, RoundedCornerShape(CardCornerSize))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(CardPadding)
    ) {
        // Portada del álbum con aspect-ratio 1:1
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(CoverCornerSize))
                .background(placeholderBg),
            contentAlignment = Alignment.Center
        ) {
            // Icono placeholder (siempre dibujado debajo)
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_albums_filled),
                contentDescription = null,
                tint = placeholderIconColor,
                modifier = Modifier.fillMaxWidth(PlaceholderIconFillFraction)
            )

            // Carátula encima del placeholder (solo si existe)
            if (coverFile != null) {
                // Tamaño en píxeles para downsampling de Coil
                // Calculamos el tamaño aproximado basado en densidad
                // (ancho de pantalla típico ~360dp / 2 columnas - gaps = ~170dp)
                val thumbnailPx = (170 * density.density).toInt()
                val request = ImageRequest.Builder(LocalContext.current)
                    .data(coverFile)
                    .size(Size(thumbnailPx, thumbnailPx))
                    .build()
                AsyncImage(
                    model = request,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Badge "Nuevo" (esquina superior izquierda)
            if (isNew) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(BadgeCornerSize))
                        .background(placeholderBg.copy(alpha = 0.85f))
                        .border(1.dp, borderL2, RoundedCornerShape(BadgeCornerSize))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = stringResource(R.string.album_badge_new),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            letterSpacing = 0.08.sp
                        ),
                        color = subtitleColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(CoverToInfoSpacing))

        // Información del álbum
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    letterSpacing = (-0.02).sp
                ),
                color = titleColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp
                ),
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (year != null) {
                    stringResource(R.string.album_year_tracks, year, trackCount)
                } else {
                    stringResource(R.string.album_tracks_count, trackCount)
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    letterSpacing = 0.01.sp
                ),
                color = metaColor,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}