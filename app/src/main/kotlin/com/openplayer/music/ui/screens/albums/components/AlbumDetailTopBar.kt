package com.openplayer.music.ui.screens.albums.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalFloatingIconColor
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor

/**
 * Barra superior de la pantalla de detalle de álbum.
 *
 * ## Composición
 * - Botón "Volver" a la izquierda con icono `ic_arrow_back` (42×42dp,
 *   ícono 22dp). Usa `LocalFloatingIconColor` para máximo contraste
 *   funcional.
 * - Breadcrumb "ÁLBUM" centrado horizontalmente (12sp, uppercase,
 *   weight 800, letter-spacing 0.16em). Usa `LocalListItemSubtitleColor`.
 * - Espacio derecho simétrico al botón (42dp) para centrar el texto.
 *
 * ## Sin fondo ni ripple
 * El botón es plano (solo icono sobre fondo transparente). Feedback
 * de opacidad al presionar vía `IconButton` estándar.
 *
 * @param onBack Callback invocado al tocar el botón de volver.
 * @param modifier Modificador externo.
 */
@Composable
fun AlbumDetailTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = LocalFloatingIconColor.current
    val breadcrumbColor = LocalListItemSubtitleColor.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Botón volver (izquierda)
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(42.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.album_detail_cd_back),
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        // Breadcrumb centrado
        Text(
            text = stringResource(R.string.album_detail_breadcrumb),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                letterSpacing = 0.16.sp
            ),
            fontWeight = FontWeight.ExtraBold,
            color = breadcrumbColor
        )

        // Espacio derecho simétrico (para centrar el breadcrumb)
        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.size(42.dp)
        )
    }
}