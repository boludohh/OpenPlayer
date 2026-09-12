package com.openplayer.music.splash.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Texto con efecto máquina de escribir en dos líneas y cursor
 * parpadeante mientras se escribe.
 *
 * Comportamiento:
 * - Escribe [line1] y luego [line2] carácter a carácter, con un
 *   retraso configurable ([charDelayMillis], 70ms por defecto).
 * - El cursor parpadea (450ms por fase) junto a la línea que se
 *   está escribiendo y desaparece al terminar ambas líneas.
 * - Al completar el texto invoca [onFinished] una sola vez,
 *   permitiendo al padre encadenar acciones (mostrar botones,
 *   transicionar de pantalla, etc.).
 *
 * Los estilos y colores por defecto coinciden con la pantalla de
 * bienvenida (línea 1: headlineMedium itálica onSurfaceVariant con
 * cursor de 28dp; línea 2: displaySmall negrita onSurface con cursor
 * de 32dp), pero pueden sobrescribirse mediante parámetros para
 * reutilizar el componente en otras pantallas (ej. pantalla de carga).
 */
@Composable
fun TypewriterText(
    line1: String,
    line2: String,
    modifier: Modifier = Modifier,
    line1Style: TextStyle? = null,
    line2Style: TextStyle? = null,
    line1Color: Color? = null,
    line2Color: Color? = null,
    line1CursorHeight: Dp = 28.dp,
    line2CursorHeight: Dp = 32.dp,
    charDelayMillis: Long = 70L,
    onFinished: (() -> Unit)? = null
) {
    val resolvedLine1Style = line1Style
        ?: MaterialTheme.typography.headlineMedium.copy(fontStyle = FontStyle.Italic)
    val resolvedLine2Style = line2Style
        ?: MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
    val resolvedLine1Color = line1Color ?: MaterialTheme.colorScheme.onSurfaceVariant
    val resolvedLine2Color = line2Color ?: MaterialTheme.colorScheme.onSurface

    val totalChars = line1.length + line2.length

    var visibleChars by remember(line1, line2) { mutableIntStateOf(0) }
    LaunchedEffect(totalChars) {
        for (i in 1..totalChars) {
            delay(charDelayMillis)
            visibleChars = i
        }
    }
    val done = visibleChars >= totalChars
    val line1Chars = minOf(visibleChars, line1.length)
    val line2Chars = (visibleChars - line1.length).coerceAtLeast(0)
    val typingLine1 = visibleChars < line1.length

    val cursorAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            cursorAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 450)
            )
            cursorAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 450)
            )
        }
    }

    // Notifica al padre una única vez cuando el tipeo termina.
    LaunchedEffect(done) {
        if (done) onFinished?.invoke()
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = line1.take(line1Chars),
                style = resolvedLine1Style,
                color = resolvedLine1Color
            )
            if (!done && typingLine1) {
                TypingCursor(alpha = cursorAlpha.value, height = line1CursorHeight)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = line2.take(line2Chars),
                style = resolvedLine2Style,
                color = resolvedLine2Color
            )
            if (!done && !typingLine1) {
                TypingCursor(alpha = cursorAlpha.value, height = line2CursorHeight)
            }
        }
    }
}

/** Cursor parpadeante del efecto máquina de escribir. */
@Composable
private fun TypingCursor(alpha: Float, height: Dp) {
    Box(
        Modifier
            .padding(start = 4.dp)
            .size(width = 3.dp, height = height)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.onSurface)
    )
}