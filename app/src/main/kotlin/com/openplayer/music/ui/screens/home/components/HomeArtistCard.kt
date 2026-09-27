package com.openplayer.music.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalCardL2Color
import com.openplayer.music.ui.theme.LocalCoverPlaceholderIconColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import java.io.File

/**
 * Card circular de artista para LazyRow horizontal en HomeScreen.
 * Muestra un avatar circular de 72dp con el nombre debajo.
 *
 * @param artistName Nombre del artista.
 * @param imageFile Archivo de imagen del artista, null para placeholder.
 * @param onClick Callback al tocar la card.
 */
@Composable
fun HomeArtistCard(
    artistName: String,
    imageFile: File?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val surfaceL2 = LocalCardL2Color.current
    val titleColor = LocalListItemTitleColor.current
    val placeholderIconColor = LocalCoverPlaceholderIconColor.current

    Column(
        modifier = modifier
            .width(76.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar circular 72dp
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(surfaceL2),
            contentAlignment = Alignment.Center
        ) {
            // Placeholder icon
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_artists_filled),
                contentDescription = null,
                tint = placeholderIconColor,
                modifier = Modifier.size(28.dp)
            )

            // Imagen real encima
            if (imageFile != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageFile)
                        .size(Size(72 * 3, 72 * 3))
                        .build(),
                    contentDescription = artistName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        // Nombre del artista
        Text(
            text = artistName,
            style = MaterialTheme.typography.bodySmall,
            color = titleColor,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}