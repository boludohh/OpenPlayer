package com.openplayer.music.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.splash.components.FloatingIconsBackground
import com.openplayer.music.splash.components.TypewriterText

/**
 * Pantalla de carga de la Splash (aperturas siguientes).
 *
 * Diseño:
 * - Fondo de iconos flotantes (mismo componente y colores que la
 *   pantalla de bienvenida) detrás de todo el contenido.
 * - Logo oficial de OpenPlayer (ic_app_logo) de 160dp centrado
 *   horizontalmente y ubicado justo arriba del texto. Se renderiza
 *   SIN tint para preservar sus colores originales (fondo rojo
 *   #C1121F y símbolo blanco).
 * - Debajo del logo (separado por 10dp), texto con efecto máquina
 *   de escribir: línea 1 "OpenPlayer" (displaySmall negrita) y
 *   línea 2 con el eslogan localizado (titleMedium itálica).
 * - El conjunto (logo + texto) se centra en la pantalla mediante
 *   Alignment.Center, quedando el logo arriba del centro y el texto
 *   debajo del centro, con el logo inmediatamente encima del texto.
 * - Tres puntos de carga centrados horizontalmente justo encima de
 *   la barra de navegación (sin dibujarse sobre ella), con la misma
 *   animación y colores de siempre (onBackground en todos los temas).
 *
 * Transición:
 * - Lógica de doble condición: se llama a [onReady] únicamente cuando
 *   el escaneo de MediaStore terminó Y el texto terminó de escribirse.
 *   - Si la librería termina antes que el texto: espera al texto.
 *   - Si el texto termina antes que la librería: espera a la librería.
 * - SplashActivity decide después si transiciona a MainActivity
 *   (flag temporal ENABLE_MAIN_ACTIVITY_TRANSITION).
 */
@Composable
fun LoadingScreen(
    audioRepository: AudioRepository,
    onReady: () -> Unit
) {
    var libraryLoaded by remember { mutableStateOf(false) }
    var textFinished by remember { mutableStateOf(false) }

    // Escaneo de la biblioteca musical en segundo plano.
    // syncIfNeeded() ejecuta el escaneo completo solo si Room está vacío.
    LaunchedEffect(Unit) {
        audioRepository.syncIfNeeded().collect {
            // El flow emite lotes mientras se insertan; al completarse
            // (sin más emisiones), marcamos libraryLoaded.
        }
        libraryLoaded = true
    }

    // Solo transiciona cuando ambas condiciones se cumplen.
    LaunchedEffect(libraryLoaded, textFinished) {
        if (libraryLoaded && textFinished) {
            onReady()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // z-order 1: iconos flotantes distribuidos por toda la pantalla
        FloatingIconsBackground()

        // z-order 2: logo + texto tipeado, centrados como conjunto
        // (logo justo arriba del texto, separado por 10dp)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = null,
                // Sin tint: se preservan los colores originales del
                // logo (fondo rojo #C1121F y símbolo blanco).
                tint = Color.Unspecified,
                modifier = Modifier.size(160.dp)
            )

            Spacer(Modifier.height(10.dp))

            TypewriterText(
                line1 = stringResource(R.string.app_name),
                line2 = stringResource(R.string.loading_tagline),
                line1Style = MaterialTheme.typography.displaySmall
                    .copy(fontWeight = FontWeight.Bold),
                line2Style = MaterialTheme.typography.titleMedium
                    .copy(fontStyle = FontStyle.Italic),
                line1Color = MaterialTheme.colorScheme.onSurface,
                line2Color = MaterialTheme.colorScheme.onSurfaceVariant,
                line1CursorHeight = 32.dp,
                line2CursorHeight = 20.dp,
                onFinished = { textFinished = true }
            )
        }

        // z-order 3: puntos de carga abajo, centrados y justo encima
        // de la barra de navegación (safeDrawing ya excluye esa área).
        LoadingDots(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * Tres puntos con barrido luminoso en bucle, dependiente del tema.
 * El color se mantiene idéntico en los 3 temas (onBackground), tal
 * como estaba definido originalmente.
 */
@Composable
private fun LoadingDots(modifier: Modifier = Modifier) {
    val phase = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            phase.animateTo(
                targetValue = 3f,
                animationSpec = tween(durationMillis = 900, easing = LinearEasing)
            )
            phase.snapTo(0f)
        }
    }

    val activeIndex = phase.value.toInt().coerceIn(0, 2)
    val baseColor = MaterialTheme.colorScheme.onBackground

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (index in 0..2) {
            val color = if (index == activeIndex) {
                baseColor
            } else {
                baseColor.copy(alpha = 0.3f)
            }
            Box(
                Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
        }
    }
}