package com.openplayer.music.ui.screens.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalFloatingIconColor
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor

/**
 * Header de sección para la pantalla Home.
 * Muestra un título a la izquierda y un botón "Ver todo" con chevron a la derecha.
 *
 * @param title Título de la sección (ej. "Escuchado recientemente").
 * @param showSeeAll Si debe mostrarse el botón "Ver todo". Por defecto true.
 * @param onSeeAllClick Callback cuando se toca "Ver todo".
 */
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    showSeeAll: Boolean = true,
    onSeeAllClick: (() -> Unit)? = null
) {
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val iconColor = LocalFloatingIconColor.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            fontWeight = FontWeight.Bold
        )

        if (showSeeAll && onSeeAllClick != null) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onSeeAllClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_see_all),
                    style = MaterialTheme.typography.labelMedium,
                    color = subtitleColor,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_home_chevron_right),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}