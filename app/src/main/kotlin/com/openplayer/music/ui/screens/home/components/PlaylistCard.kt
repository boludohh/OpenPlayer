package com.openplayer.music.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalCardL2Color
import com.openplayer.music.ui.theme.LocalCoverPlaceholderIconColor
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import java.io.File

/**
 * Card de playlist con mosaico 2x2 de carátulas + título + contador.
 *
 * @param title Nombre de la playlist.
 * @param songCount Cantidad de canciones en la playlist.
 * @param countText Texto formateado (ej. "32 canciones").
 * @param coverFiles Lista de hasta 4 archivos de carátula para el mosaico.
 * @param onClick Callback al tocar la card.
 */
@Composable
fun PlaylistCard(
    title: String,
    countText: String,
    coverFiles: List<File?>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val surfaceL1 = MaterialTheme.colorScheme.surfaceVariant
    val surfaceL2 = LocalCardL2Color.current
    val borderL1 = MaterialTheme.colorScheme.outline
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val placeholderIconColor = LocalCoverPlaceholderIconColor.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceL1)
            .border(1.dp, borderL1, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mosaico 2x2
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceL2)
        ) {
            // Verificar si hay al menos una carátula
            val hasAnyCover = coverFiles.any { it != null }

            if (!hasAnyCover) {
                // Placeholder centrado
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_playlists_filled),
                        contentDescription = null,
                        tint = placeholderIconColor,
                        modifier = Modifier.fillMaxSize(0.4f)
                    )
                }
            } else {
                // Grid 2x2 de carátulas
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        MosaicCell(
                            coverFile = coverFiles.getOrNull(0),
                            modifier = Modifier.weight(1f)
                        )
                        MosaicCell(
                            coverFile = coverFiles.getOrNull(1),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        MosaicCell(
                            coverFile = coverFiles.getOrNull(2),
                            modifier = Modifier.weight(1f)
                        )
                        MosaicCell(
                            coverFile = coverFiles.getOrNull(3),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Título
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = titleColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Contador
        Text(
            text = countText,
            style = MaterialTheme.typography.labelSmall,
            color = subtitleColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Celda individual del mosaico 2x2 de una playlist.
 * Muestra la carátula o un fondo L2 si no hay carátula.
 */
@Composable
private fun MosaicCell(
    coverFile: File?,
    modifier: Modifier = Modifier
) {
    val surfaceL2 = LocalCardL2Color.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(surfaceL2)
    ) {
        if (coverFile != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverFile)
                    .size(Size(100, 100))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}