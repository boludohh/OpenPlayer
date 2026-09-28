package com.openplayer.music.splash.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalCardL2Color

/**
 * Cápsula reutilizable de la Splash (Comenzar / Finalizar).
 *
 * - [filled] = true: cápsula sólida con fondo surfaceVariant (cardL1
 *   en los 3 temas), texto y flecha en inverseOnSurface (highContrast),
 *   y un círculo interno con LocalCardL2Color (cardL2, superficie
 *   elevada nivel 2 según el documento de colores). Incluye un ícono
 *   circular con flecha al final del texto.
 * - [filled] = false: cápsula con borde, para acciones secundarias.
 *
 * Todos los colores provienen de slots semánticos del tema o de los
 * nuevos CompositionLocals (LocalCardL2Color), por lo que el botón se
 * adapta automáticamente a los tres temas sin necesidad de lógica
 * condicional por ThemeMode.
 */
@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true
) {
    val capsuleColor = if (filled) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }
    val contentColor = if (filled) {
        MaterialTheme.colorScheme.inverseOnSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val circleBgColor = LocalCardL2Color.current

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = capsuleColor,
        contentColor = contentColor,
        border = if (filled) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(start = 28.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )

            if (filled) {
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(circleBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_forward),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}