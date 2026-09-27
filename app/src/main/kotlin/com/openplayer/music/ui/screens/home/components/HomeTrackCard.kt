package com.openplayer.music.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
 * Card de canción para LazyRow horizontal en HomeScreen.
 * Muestra una carátula 108x108dp, título y artista.
 *
 * @param title Título de la canción/álbum.
 * @param subtitle Subtítulo (artista).
 * @param coverFile Archivo de la carátula en disco, null si no tiene.
 * @param onClick Callback al tocar la card.
 */
@Composable
fun HomeTrackCard(
    title: String,
    subtitle: String,
    coverFile: File?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val surfaceL2 = LocalCardL2Color.current
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val placeholderIconColor = LocalCoverPlaceholderIconColor.current

    Column(
        modifier = modifier
            .width(108.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.Start
    ) {
        // Carátula 108x108dp
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceL2),
            contentAlignment = Alignment.Center
        ) {
            // Placeholder icon
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_music_filled),
                contentDescription = null,
                tint = placeholderIconColor,
                modifier = Modifier.size(26.dp)
            )

            // Carátula real encima
            if (coverFile != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverFile)
                        .size(Size(108 * 3, 108 * 3))
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Título
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = titleColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Subtítulo (artista)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = subtitleColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}