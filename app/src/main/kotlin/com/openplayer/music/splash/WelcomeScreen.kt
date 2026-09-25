package com.openplayer.music.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openplayer.music.R
import com.openplayer.music.splash.components.CapsuleButton
import com.openplayer.music.splash.components.FloatingIconsBackground
import kotlinx.coroutines.delay

/**
 * Duraciones y delays de las animaciones de entrada escalonada.
 * Cada elemento aparece con un delay progresivo para crear un efecto
 * de revelación secuencial sin typewriter.
 */
private const val FADE_IN_DURATION_MS = 400
private const val DELAY_LOGO_MS = 0L
private const val DELAY_TITLE_MS = 200L
private const val DELAY_TAGLINE_MS = 400L
private const val DELAY_DESCRIPTION_MS = 600L
private const val DELAY_FEATURES_MS = 800L
private const val DELAY_BUTTON_MS = 1000L

/**
 * Especificación de una feature (icono + etiqueta) de la fila inferior.
 */
private data class FeatureSpec(
    val iconRes: Int,
    val labelRes: Int
)

/**
 * Pantalla de bienvenida de la Splash (solo primera instalación).
 *
 * Diseño rediseñado con layout centrado vertical:
 * - Fondo de iconos flotantes (5 iconos gigantes únicos distribuidos
 *   con lógica de no-colisiones).
 * - Logo de la app (ic_app_logo con sus colores originales preservados).
 * - Título bicolor "OpenPlayer" (Open en highContrast, Player en
 *   secondaryOnBg).
 * - Tagline en negrita (ej. "Tu música, a tu manera.").
 * - Descripción de 2 líneas con ancho acotado para salto elegante.
 * - Fila de 4 features con separadores verticales (música local,
 *   sin anuncios, código abierto, personalizable).
 * - Botón "Comenzar" centrado horizontalmente.
 *
 * Entrada estática con fade-in escalonado (sin typewriter):
 * - Logo aparece primero (delay 0ms)
 * - Título + tagline (delay 200ms / 400ms)
 * - Descripción (delay 600ms)
 * - Features (delay 800ms)
 * - Botón Comenzar (delay 1000ms)
 *
 * Z-order (de atrás hacia adelante):
 * 1. Fondo sólido (background)
 * 2. Iconos flotantes (detrás del contenido)
 * 3. Contenido principal centrado (logo, textos, features, botón)
 *
 * La cápsula "Comenzar" se adapta automáticamente al tema mediante
 * los slots semánticos del colorScheme.
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val highContrastColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
    val separatorColor = MaterialTheme.colorScheme.outline
    val appName = stringResource(R.string.app_name)
    val scrollState = rememberScrollState()

    // Control de aparición escalonada de cada sección
    var showLogo by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var showFeatures by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }

    // Animación escalonada: cada sección aparece con un delay progresivo
    LaunchedEffect(Unit) {
        showLogo = true
        delay(DELAY_TITLE_MS)
        showTitle = true
        delay(DELAY_TAGLINE_MS - DELAY_TITLE_MS)
        showTagline = true
        delay(DELAY_DESCRIPTION_MS - DELAY_TAGLINE_MS)
        showDescription = true
        delay(DELAY_FEATURES_MS - DELAY_DESCRIPTION_MS)
        showFeatures = true
        delay(DELAY_BUTTON_MS - DELAY_FEATURES_MS)
        showButton = true
    }

    // Lista de features con sus iconos y labels localizados
    val features = remember {
        listOf(
            FeatureSpec(R.drawable.ic_feature_music_local, R.string.welcome_feature_local),
            FeatureSpec(R.drawable.ic_feature_no_ads, R.string.welcome_feature_no_ads),
            FeatureSpec(R.drawable.ic_feature_open_source, R.string.welcome_feature_open_source),
            FeatureSpec(R.drawable.ic_feature_customizable, R.string.welcome_feature_customizable)
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // z-order 1: iconos flotantes gigantes distribuidos por la pantalla
        FloatingIconsBackground()

        // z-order 2: contenido principal centrado con scroll de seguridad
        // para pantallas bajas (nunca se corta contenido)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo de la app (preserva sus colores originales con tint = Unspecified)
            AnimatedVisibility(
                visible = showLogo,
                enter = fadeIn(animationSpec = tween(FADE_IN_DURATION_MS))
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            // Título bicolor "OpenPlayer"
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(animationSpec = tween(FADE_IN_DURATION_MS))
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Open",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = highContrastColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Player",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = secondaryColor
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tagline en negrita
            AnimatedVisibility(
                visible = showTagline,
                enter = fadeIn(animationSpec = tween(FADE_IN_DURATION_MS))
            ) {
                Text(
                    text = stringResource(R.string.welcome_tagline),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = highContrastColor,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))

            // Descripción de 2 líneas con ancho acotado
            AnimatedVisibility(
                visible = showDescription,
                enter = fadeIn(animationSpec = tween(FADE_IN_DURATION_MS))
            ) {
                Text(
                    text = stringResource(R.string.welcome_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp)
                )
            }

            Spacer(Modifier.height(40.dp))

            // Fila de 4 features con separadores verticales
            AnimatedVisibility(
                visible = showFeatures,
                enter = fadeIn(animationSpec = tween(FADE_IN_DURATION_MS))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    features.forEachIndexed { index, feature ->
                        FeatureItem(
                            iconRes = feature.iconRes,
                            labelRes = feature.labelRes,
                            iconColor = secondaryColor,
                            labelColor = highContrastColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (index < features.lastIndex) {
                            // Separador vertical entre features
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(36.dp)
                                    .background(separatorColor)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            // Botón "Comenzar" centrado
            AnimatedVisibility(
                visible = showButton,
                enter = fadeIn(animationSpec = tween(FADE_IN_DURATION_MS))
            ) {
                CapsuleButton(
                    text = stringResource(R.string.start_button),
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .widthIn(max = 320.dp)
                )
            }
        }
    }
}

/**
 * Item individual de feature: icono de 22dp centrado + etiqueta de
 * 2 líneas debajo. Se usa dentro de la fila de 4 features.
 */
@Composable
private fun FeatureItem(
    iconRes: Int,
    labelRes: Int,
    iconColor: androidx.compose.ui.graphics.Color,
    labelColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 14.sp
        )
    }
}