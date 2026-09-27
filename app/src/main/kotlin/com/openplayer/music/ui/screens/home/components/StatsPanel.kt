package com.openplayer.music.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.openplayer.music.data.db.TotalStats
import com.openplayer.music.ui.theme.LocalCardL2Color
import com.openplayer.music.ui.theme.LocalCoverPlaceholderIconColor
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor

/**
 * Panel de estadísticas con 3 columnas separadas por divisores verticales.
 * Muestra: reproducciones totales, completadas y tiempo de escucha.
 *
 * @param stats Objeto [TotalStats] con los datos agregados.
 * @param formattedTime Texto ya formateado del tiempo (ej. "342 h" o "45 min").
 */
@Composable
fun StatsPanel(
    stats: TotalStats,
    formattedTime: String,
    totalPlaysLabel: String,
    completedLabel: String,
    timeLabel: String,
    modifier: Modifier = Modifier
) {
    val surfaceL1 = MaterialTheme.colorScheme.surfaceVariant
    val borderL1 = MaterialTheme.colorScheme.outline
    val surfaceL2 = LocalCardL2Color.current
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val iconColor = LocalCoverPlaceholderIconColor.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceL1)
            .border(1.dp, borderL1, RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stat 1: Reproducciones totales
        StatColumn(
            iconRes = R.drawable.ic_play_arrow,
            value = formatStatNumber(stats.totalPlays),
            label = totalPlaysLabel,
            surfaceL2 = surfaceL2,
            titleColor = titleColor,
            subtitleColor = subtitleColor,
            iconColor = iconColor,
            modifier = Modifier.weight(1f)
        )

        // Divisor
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.7f)
                .background(borderL1)
        )

        // Stat 2: Completadas
        StatColumn(
            iconRes = R.drawable.ic_check,
            value = formatStatNumber(stats.totalCompleted),
            label = completedLabel,
            surfaceL2 = surfaceL2,
            titleColor = titleColor,
            subtitleColor = subtitleColor,
            iconColor = iconColor,
            modifier = Modifier.weight(1f)
        )

        // Divisor
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.7f)
                .background(borderL1)
        )

        // Stat 3: Tiempo
        StatColumn(
            iconRes = R.drawable.ic_schedule,
            value = formattedTime,
            label = timeLabel,
            surfaceL2 = surfaceL2,
            titleColor = titleColor,
            subtitleColor = subtitleColor,
            iconColor = iconColor,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Columna individual de estadística dentro del panel.
 */
@Composable
private fun StatColumn(
    iconRes: Int,
    value: String,
    label: String,
    surfaceL2: androidx.compose.ui.graphics.Color,
    titleColor: androidx.compose.ui.graphics.Color,
    subtitleColor: androidx.compose.ui.graphics.Color,
    iconColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        // Icono circular
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(surfaceL2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(14.dp)
            )
        }

        // Valor
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            fontWeight = FontWeight.ExtraBold
        )

        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = subtitleColor,
            fontWeight = FontWeight.SemiBold,
            lineHeight = MaterialTheme.typography.labelSmall.lineHeight
        )
    }
}

/**
 * Formatea un número de reproducciones con separador de miles (punto).
 * Ej: 12480 → "12.480"
 */
private fun formatStatNumber(value: Int): String {
    return if (value >= 1000) {
        val str = value.toString()
        val result = StringBuilder()
        for (i in str.indices) {
            if (i > 0 && (str.length - i) % 3 == 0) {
                result.append('.')
            }
            result.append(str[i])
        }
        result.toString()
    } else {
        value.toString()
    }
}