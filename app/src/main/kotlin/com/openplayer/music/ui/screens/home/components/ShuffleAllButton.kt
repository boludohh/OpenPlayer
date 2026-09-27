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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalCardL2Color
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import com.openplayer.music.ui.theme.LocalTopBarIconColor

/**
 * Botón grande "Reproducir todo aleatoriamente" con icono shuffle
 * circular y contador de canciones de la biblioteca.
 *
 * @param songCount Cantidad total de canciones en la biblioteca.
 * @param countText Texto formateado del conteo (ej. "1.248 canciones").
 * @param onClick Callback al tocar el botón.
 */
@Composable
fun ShuffleAllButton(
    countText: String,
    titleText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val surfaceL1 = MaterialTheme.colorScheme.surfaceVariant
    val borderL1 = MaterialTheme.colorScheme.outline
    val surfaceL2 = LocalCardL2Color.current
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val functionalIconColor = LocalTopBarIconColor.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceL1)
            .border(1.dp, borderL1, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icono shuffle circular
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(surfaceL2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_home_shuffle),
                contentDescription = null,
                tint = functionalIconColor,
                modifier = Modifier.size(17.dp)
            )
        }

        // Texto
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = countText,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}