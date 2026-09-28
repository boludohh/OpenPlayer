package com.openplayer.music.ui.screens.albums.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalCoverPlaceholderIconColor
import com.openplayer.music.ui.theme.LocalListItemMetaColor
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import java.io.File

/** Ancho y alto de la carátula del álbum en dp. */
private val AlbumCoverSize = 160.dp

/** Altura del bloque de metadata debajo de la carátula. */
private val MetadataHeight = 44.dp

/** Esquinas de la carátula del álbum. */
private val CoverCornerSize = 8.dp

/**
 * Tarjeta individual de un álbum en el grid de la pestaña de Álbumes.
 *
 * Renderiza una tarjeta cuadrada con la carátula del álbum arriba y
 * metadata (título, artista, año + conteo de pistas) abajo. La
 * geometría es similar a [com.openplayer.music.ui.screens.artists.components.ArtistCircle]
 * pero rectangular en lugar de circular, manteniendo coherencia visual
 * entre pestañas.
 *
 * ## Composición visual
 * - **Carátula** (arriba, 160×160dp, esquinas 8dp): cargada vía Coil
 *   desde el archivo de portada de la canción representativa del
 *   álbum. Si no hay carátula, se muestra un placeholder con fondo
 *   `surfaceVariant` y el icono [R.drawable.ic_nav_music_filled]
 *   centrado (60dp).
 * - **Título del álbum** (debajo de la carátula): [MaterialTheme.typography.bodyMedium],
 *   color [LocalListItemTitleColor], máximo 1 línea con ellipsis.
 * - **Artista**: [MaterialTheme.typography.bodySmall], color
 *   [LocalListItemSubtitleColor], máximo 1 línea con ellipsis.
 * - **Año + conteo de pistas**: [MaterialTheme.typography.labelSmall],
 *   color [LocalListItemMetaColor], formato "2023 · 12 pistas".
 *
 * ## Sin ripple de Material
 * El efecto de onda (ripple) al tocar está deshabilitado usando
 * `indication = null` con un InteractionSource propio. El click sigue
 * siendo funcional pero sin feedback visual de toque.
 *
 * ## Optimización de memoria de carátulas
 * La carátula se carga vía [ImageRequest] con tamaño fijo de 160dp
 * (convertido a píxeles según densidad de pantalla). Coil hace
 * downsampling durante el decode (inSampleSize) en vez de cargar la
 * imagen completa y escalarla, reduciendo drásticamente el pico de
 * memoria al scrollear grids largos.
 *
 * @param title Título del álbum.
 * @param artist Artista del álbum.
 * @param year Año de lanzamiento (null si no está en metadatos).
 * @param trackCount Cantidad de pistas en el álbum.
 * @param coverFile Archivo de portada en disco (null si el álbum no
 *                  tiene carátula disponible).
 * @param onClick Callback invocado al tocar la tarjeta. Preparado
 *                para navegación futura al detalle del álbum.
 * @param modifier Modificador externo que aplica el padre (grid).
 */
@Composable
fun AlbumCard(
    title: String,
    artist: String,
    year: Int?,
    trackCount: Int,
    coverFile: File?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val metaColor = LocalListItemMetaColor.current
    val placeholderBg = MaterialTheme.colorScheme.surfaceVariant
    val placeholderIconColor = LocalCoverPlaceholderIconColor.current
    val density = LocalDensity.current

    // InteractionSource propio para deshabilitar el ripple de Material
    val interactionSource = remember { MutableInteractionSource() }

    // Tamaño del thumbnail de la carátula en píxeles para Coil
    val thumbnailPx = (AlbumCoverSize.value * density.density).toInt()

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Carátula del álbum (o placeholder si no hay portada)
        Box(
            modifier = Modifier
                .size(AlbumCoverSize)
                .clip(RoundedCornerShape(CoverCornerSize))
                .background(placeholderBg),
            contentAlignment = Alignment.Center
        ) {
            // Icono placeholder (siempre dibujado debajo)
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_music_filled),
                contentDescription = null,
                tint = placeholderIconColor,
                modifier = Modifier.size(60.dp)
            )

            // Carátula encima del placeholder (solo si existe)
            if (coverFile != null) {
                val request = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(coverFile)
                    .size(Size(thumbnailPx, thumbnailPx))
                    .build()
                AsyncImage(
                    model = request,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(AlbumCoverSize)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Metadata: título, artista, año + conteo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(MetadataHeight)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = if (year != null) {
                    stringResource(R.string.album_year_tracks, year, trackCount)
                } else {
                    stringResource(R.string.album_tracks_count, trackCount)
                },
                style = MaterialTheme.typography.labelSmall,
                color = metaColor,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}