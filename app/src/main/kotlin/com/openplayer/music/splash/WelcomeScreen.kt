package com.openplayer.music.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.splash.components.CapsuleButton
import com.openplayer.music.splash.components.FloatingIconsBackground
import com.openplayer.music.splash.components.TypewriterText

/**
 * Duración (en ms) de la animación de entrada del botón "Comenzar".
 */
private const val startButtonAnimDuration = 300

/**
 * Pantalla de bienvenida de la Splash (solo primera instalación).
 *
 * - Efecto máquina de escribir sobre "Bienvenido a" + "OpenPlayer",
 *   con cursor parpadeante mientras se escribe (delegado al componente
 *   reutilizable [TypewriterText]).
 * - La cápsula "Comenzar" aparece en la esquina inferior derecha
 *   únicamente al terminar la animación de tipeo, creciendo desde
 *   escala 0 hasta su tamaño real en su posición final, combinado
 *   con un fade in para que la aparición sea suave y progresiva.
 * - Fondo de iconos flotantes (música, auriculares, notas, etc.)
 *   distribuidos aleatoriamente por toda la pantalla (incluyendo
 *   esquinas, laterales y zonas superior/inferior), con movimiento
 *   sutil y usando el color custom floatingIcon del tema activo.
 *   Los iconos no se superponen entre sí gracias a un algoritmo de
 *   no-colisiones.
 * - El fondo usa el color semántico del tema para que cambie al
 *   alternar entre claro/oscuro/AMOLED.
 *
 * Z-order (de atrás hacia adelante):
 * 1. Fondo sólido (background)
 * 2. Iconos flotantes (detrás del texto y botón)
 * 3. Texto de bienvenida (centrado)
 * 4. Botón "Comenzar" (esquina inferior derecha)
 *
 * La cápsula "Comenzar" se adapta automáticamente al tema mediante
 * los slots semánticos del colorScheme (surfaceVariant, scrim,
 * inverseOnSurface), por lo que esta pantalla no necesita recibir
 * el tema activo como parámetro.
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val welcomeTitle = stringResource(R.string.welcome_title)
    val appName = stringResource(R.string.app_name)

    var typingDone by remember { mutableStateOf(false) }

    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // z-order 1: iconos flotantes distribuidos por toda la pantalla
        FloatingIconsBackground()

        // z-order 2: texto de bienvenida centrado (encima de los iconos)
        TypewriterText(
            line1 = welcomeTitle,
            line2 = appName,
            modifier = Modifier.align(Alignment.Center),
            onFinished = { typingDone = true }
        )

        // z-order 3: botón "Comenzar" en la esquina (encima de todo)
        AnimatedVisibility(
            visible = typingDone,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            enter = fadeIn(
                animationSpec = tween(durationMillis = startButtonAnimDuration)
            ) + scaleIn(
                initialScale = 0f,
                animationSpec = tween(durationMillis = startButtonAnimDuration)
            )
        ) {
            CapsuleButton(
                text = stringResource(R.string.start_button),
                onClick = onStart
            )
        }
    }
}