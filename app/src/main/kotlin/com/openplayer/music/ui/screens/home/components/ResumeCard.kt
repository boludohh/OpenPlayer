package com.openplayer.music.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.openplayer.music.ui.theme.LocalTopBarIconColor
import java.io.File

/**
 * Card "Seguir escuchando" — muestra la última canción reproducida
 * con carátula, título, artista, contexto y botón de play circular.
 *
 * @param title Título de la canción.
 * @param artist Nombre del artista.
 * @param context Texto de contexto (ej. "Reproduciendo desde Álbum").
 * @param coverFile Archivo de la carátula en disco, null si no tiene.
 * @param onPlayClick Callback al tocar el botón play.
 * @param onCardClick Callback al tocar la card (navegar al contexto).
 */
@Composable
fun ResumeCard(
    title: String,
    artist: String,
    context: String,
    coverFile: File?,
    modifier: Modifier = Modifier,
    onPlayClick: () -> Unit = {},
    onCardClick: () -> Unit = {}
) {
    val surfaceL1 = MaterialTheme.colorScheme.surfaceVariant
    val borderL1 = MaterialTheme.colorScheme.outline
    val surfaceL2 = LocalCardL2Color.current
    val borderL2 = MaterialTheme.colorScheme.outlineVariant
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val placeholderIconColor = LocalCoverPlaceholderIconColor.current
    val functionalIconColor = LocalTopBarIconColor.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(surfaceL1)
            .border(1.dp, borderL1, RoundedCornerShape(18.dp))
            .clickable(onClick = onCardClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Carátula 54x54dp
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceL2),
            contentAlignment = Alignment.Center
        ) {
            // Placeholder icon
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_tracks_filled),
                contentDescription = null,
                tint = placeholderIconColor,
                modifier = Modifier.size(22.dp)
            )

            // Carátula real encima
            if (coverFile != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverFile)
                        .size(Size(54 * 3, 54 * 3))
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = context,
                style = MaterialTheme.typography.labelSmall,
                color = subtitleColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Botón play circular
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(surfaceL2)
                .border(1.dp, borderL2, CircleShape)
                .clickable(onClick = onPlayClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_home_play),
                contentDescription = null,
                tint = functionalIconColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}