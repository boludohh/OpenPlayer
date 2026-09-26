package com.openplayer.music.splash

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.data.AppPreferences
import com.openplayer.music.data.LocaleManager
import com.openplayer.music.permissions.PermissionHandler
import com.openplayer.music.splash.components.CapsuleButton
import com.openplayer.music.splash.components.PermissionState
import com.openplayer.music.splash.components.SplashCard
import com.openplayer.music.ui.theme.ThemeToggleButton
import kotlinx.coroutines.launch

/** Idiomas disponibles con su recurso de nombre localizado. */
private val languageEntries: List<Pair<String, Int>> = listOf(
    LocaleManager.LANGUAGE_ES to R.string.language_es,
    LocaleManager.LANGUAGE_EN to R.string.language_en,
    LocaleManager.LANGUAGE_AR to R.string.language_ar
)

/** Especificación de una tarjeta de permiso dentro del panel. */
private class PermissionCardSpec(
    @param:DrawableRes val icon: Int,
    val title: String,
    val body: String,
    val permissionState: PermissionState,
    val onClick: () -> Unit
)

/** Separación entre tarjetas de un mismo panel. */
private val panelCardSpacing = 3.dp

/**
 * Distancia de scroll (en dp) sobre la cual los iconos superiores
 * (flecha y luna) se desvanecen completamente al subir el contenido.
 */
private val iconFadeDistance = 80.dp

/**
 * Altura (en dp) del gradiente de difuminado superior que evita que el
 * contenido se corte de golpe contra la barra de estado.
 */
private val topFadeHeight = 50.dp

/**
 * Separación (en dp) entre la última tarjeta de idioma y el botón
 * "Finalizar", ahora integrado al final del scroll en lugar de flotar.
 */
private val finishButtonSpacing = 7.dp

/**
 * Duración (en ms) de la animación de entrada del botón "Finalizar".
 */
private const val finishButtonAnimDuration = 300

/**
 * Pantalla de configuración inicial de la Splash.
 *
 * - Tarjetas de permisos según nivel de API (ver [PermissionHandler]),
 *   agrupadas en un panel unido: la primera redondea arriba, la última
 *   redondea abajo y las intermedias van rectas, separadas 3dp.
 *   Cada tarjeta muestra un icono de estado: X roja (DENIED) o check
 *   verde (GRANTED) con transición suave (Crossfade).
 * - Aviso Android 13+ solo en API 33+, como tarjeta independiente.
 * - Arriba-izquierda: flecha de regreso a la bienvenida.
 * - Arriba-derecha: botón de cambio de tema. Ambos iconos comparten
 *   el mismo padding superior para quedar alineados en la misma
 *   línea horizontal y se desvanecen suavemente al subir el contenido.
 *   Solo responden a clicks cuando están completamente visibles
 *   (alpha = 1); mientras se desvanecen quedan deshabilitados.
 * - Gradiente de difuminado superior que evita que el contenido se
 *   corte de golpe al llegar a la barra de estado.
 * - Sección de idiomas debajo de los permisos, también en panel unido;
 *   cada tarjeta muestra el icono de globo terráqueo con los colores
 *   del tema activo. Al elegir uno se guarda y se recrea la Activity
 *   para aplicarlo.
 * - "Finalizar" aparece al final del scroll, 7dp debajo de la última
 *   tarjeta de idioma, solo cuando los permisos obligatorios están
 *   concedidos, con animación fade + slide vertical.
 * - La posición del scroll se preserva al cambiar de idioma (recreate)
 *   mediante rememberSaveable con ScrollState.Saver, evitando que el
 *   contenido suba al primer título tras el cambio.
 * - El fondo usa el color semántico del tema para que cambie al
 *   alternar entre claro/oscuro/AMOLED.
 *
 * Los títulos de sección y el icono del escudo usan inverseOnSurface
 * (#5A1A1A claro, #F5F5F5 oscuro, #E5E5E5 AMOLED). Los subtítulos y
 * cuerpos de las tarjetas usan onSurfaceVariant (#525252 / #B3B3B3 /
 * #A3A3A3). La cápsula "Finalizar" se adapta automáticamente al tema
 * mediante los slots semánticos del colorScheme (surfaceVariant, scrim,
 * inverseOnSurface), por lo que esta pantalla no necesita recibir el
 * tema activo como parámetro.
 */
@Composable
fun SetupScreen(
    resumeTick: Int,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var refresh by remember { mutableIntStateOf(0) }

    val audioGranted = remember(refresh, resumeTick) {
        PermissionHandler.hasAudioPermission(context)
    }
    val notificationPermission = PermissionHandler.notificationPermission()
    val notificationGranted = remember(refresh, resumeTick) {
        notificationPermission == null ||
            PermissionHandler.hasPermission(context, notificationPermission)
    }
    val bluetoothPermission = PermissionHandler.bluetoothPermission()
    val bluetoothGranted = remember(refresh, resumeTick) {
        bluetoothPermission == null ||
            PermissionHandler.hasPermission(context, bluetoothPermission)
    }
    val batteryIgnored = remember(refresh, resumeTick) {
        PermissionHandler.isBatteryOptimizationIgnored(context)
    }
    val requiredGranted = audioGranted && notificationGranted

    val currentLanguage = remember { preferences.getLanguageBlocking() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    // Panel de permisos: solo incluye las tarjetas que existen según el SDK.
    // Cada tarjeta tiene un permissionState: GRANTED o DENIED.
    val permissionCards = buildList {
        add(
            PermissionCardSpec(
                icon = R.drawable.ic_music_note,
                title = stringResource(R.string.permission_library_title),
                body = stringResource(R.string.permission_library_body),
                permissionState = if (audioGranted) PermissionState.GRANTED else PermissionState.DENIED,
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(PermissionHandler.audioPermission())
                    )
                }
            )
        )
        if (bluetoothPermission != null) {
            add(
                PermissionCardSpec(
                    icon = R.drawable.ic_bluetooth,
                    title = stringResource(R.string.permission_bluetooth_title),
                    body = stringResource(R.string.permission_bluetooth_body),
                    permissionState = if (bluetoothGranted) PermissionState.GRANTED else PermissionState.DENIED,
                    onClick = {
                        permissionLauncher.launch(arrayOf(bluetoothPermission))
                    }
                )
            )
        }
        if (notificationPermission != null) {
            add(
                PermissionCardSpec(
                    icon = R.drawable.ic_notifications,
                    title = stringResource(R.string.permission_notifications_title),
                    body = stringResource(R.string.permission_notifications_body),
                    permissionState = if (notificationGranted) PermissionState.GRANTED else PermissionState.DENIED,
                    onClick = {
                        permissionLauncher.launch(arrayOf(notificationPermission))
                    }
                )
            )
        }
        add(
            PermissionCardSpec(
                icon = R.drawable.ic_battery,
                title = stringResource(R.string.permission_battery_title),
                body = stringResource(R.string.permission_battery_body),
                permissionState = if (batteryIgnored) PermissionState.GRANTED else PermissionState.DENIED,
                onClick = {
                    batteryLauncher.launch(
                        PermissionHandler.batteryOptimizationIntent(context)
                    )
                }
            )
        )
    }

    BackHandler(onBack = onBack)

    // rememberSaveable con ScrollState.Saver preserva la posición del
    // scroll a través del recreate() de la Activity al cambiar el idioma.
    val scrollState = rememberSaveable(saver = ScrollState.Saver) {
        ScrollState(0)
    }
    val fadeDistancePx = with(density) { iconFadeDistance.toPx() }
    val iconAlpha = if (fadeDistancePx <= 0f) {
        1f
    } else {
        (1f - (scrollState.value / fadeDistancePx)).coerceIn(0f, 1f)
    }
    val iconsEnabled = iconAlpha >= 1f

    val backgroundColor = MaterialTheme.colorScheme.background
    val topFadeBrush = remember(backgroundColor) {
        Brush.verticalGradient(
            colors = listOf(backgroundColor, Color.Transparent),
            startY = 0f
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Capa 1: contenido scrollable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(top = 72.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_shield),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.size(48.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.permissions_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            if (PermissionHandler.showsAndroid13Notice()) {
                SplashCard(
                    icon = R.drawable.ic_info,
                    title = stringResource(R.string.android13_notice_title),
                    body = stringResource(R.string.android13_notice_body)
                )
                Spacer(Modifier.height(16.dp))
            }

            permissionCards.forEachIndexed { index, spec ->
                SplashCard(
                    icon = spec.icon,
                    title = spec.title,
                    body = spec.body,
                    permissionState = spec.permissionState,
                    roundTop = index == 0,
                    roundBottom = index == permissionCards.lastIndex,
                    onClick = spec.onClick
                )
                if (index < permissionCards.lastIndex) {
                    Spacer(Modifier.height(panelCardSpacing))
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.choose_language_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            Spacer(Modifier.height(16.dp))

            // Tarjetas de idioma con icono de globo terráqueo
            languageEntries.forEachIndexed { index, (code, nameRes) ->
                SplashCard(
                    icon = R.drawable.ic_language,
                    title = stringResource(nameRes),
                    selected = code == currentLanguage,
                    roundTop = index == 0,
                    roundBottom = index == languageEntries.lastIndex,
                    onClick = {
                        if (code != currentLanguage) {
                            scope.launch {
                                preferences.setLanguage(code)
                                (context as? ComponentActivity)?.recreate()
                            }
                        }
                    }
                )
                if (index < languageEntries.lastIndex) {
                    Spacer(Modifier.height(panelCardSpacing))
                }
            }

            // Botón "Finalizar" integrado al final del scroll, 7dp debajo
            // de la última tarjeta de idioma. Aparece con animación fade
            // + slide vertical cuando los permisos obligatorios están
            // concedidos, evitando una aparición brusca.
            AnimatedVisibility(
                visible = requiredGranted,
                enter = fadeIn(
                    animationSpec = tween(durationMillis = finishButtonAnimDuration)
                ) + slideInVertically(
                    animationSpec = tween(durationMillis = finishButtonAnimDuration),
                    initialOffsetY = { it / 2 }
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Spacer(Modifier.height(finishButtonSpacing))
                    CapsuleButton(
                        text = stringResource(R.string.finish_button),
                        onClick = onFinish
                    )
                }
            }
        }

        // Capa 2: difuminado superior (encima del contenido, debajo de los iconos)
        Box(
            Modifier
                .fillMaxWidth()
                .height(topFadeHeight)
                .background(topFadeBrush)
        )

        // Capa 3: iconos fijos con fade dinámico, solo habilitados cuando alpha = 1
        IconButton(
            onClick = onBack,
            enabled = iconsEnabled,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 8.dp, start = 8.dp)
                .alpha(iconAlpha)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back_button),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ThemeToggleButton(
            enabled = iconsEnabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .alpha(iconAlpha)
        )
    }
}