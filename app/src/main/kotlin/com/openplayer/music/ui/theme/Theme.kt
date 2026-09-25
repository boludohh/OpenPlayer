package com.openplayer.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// =========================================================================
// CompositionLocals de colores personalizados
// =========================================================================
//
// Estos CompositionLocals permiten acceder a colores semánticos según
// el tema activo sin necesidad de pasar parámetros a cada componente.
//
// Los nombres de los Locals se mantienen por compatibilidad con el código
// existente; internamente ahora apuntan a los tokens base del nuevo
// sistema (sin duplicados de valores hex):
// - LocalFloatingIconColor → secondaryOnBg
// - LocalNavIconActiveColor → highContrast
// - LocalNavIconInactiveColor → inactiveOnBg
// - LocalTopBarIconColor → highContrast
// - LocalTracksCountTextColor → secondaryOnBg
// - LocalScreenTitleColor → highContrast
// - LocalListItemTitleColor → highContrast
// - LocalListItemSubtitleColor → secondaryOnBg
// - LocalListItemMetaColor → inactiveOnBg
// - LocalCoverPlaceholderIconColor → inactiveOnBg
// - LocalCurrentTrackColor → cardL1
// - LocalCurrentTrackBorderColor → highContrast
// - LocalCardL2Color → cardL2 (NUEVO: libera scrim de uso atípico)
// - LocalActiveBorderColor → highContrast (NUEVO: borde de selección activa)

/**
 * Color custom para los iconos flotantes de la pantalla de bienvenida.
 * Apunta a secondaryOnBg (texto/iconos decorativos de contraste medio).
 */
val LocalFloatingIconColor = compositionLocalOf { Color.Gray }

/** Color para iconos de navegación activos. Apunta a highContrast. */
val LocalNavIconActiveColor = compositionLocalOf { Color.Black }

/** Color para iconos de navegación inactivos. Apunta a inactiveOnBg. */
val LocalNavIconInactiveColor = compositionLocalOf { Color.Gray }

/**
 * Color para iconos de la barra superior (menú y búsqueda).
 * Apunta a highContrast (iconos funcionales de máximo contraste).
 */
val LocalTopBarIconColor = compositionLocalOf { Color.Black }

/**
 * Color para el texto de conteo de pistas en la pantalla de Tracks.
 * Apunta a secondaryOnBg (texto secundario).
 */
val LocalTracksCountTextColor = compositionLocalOf { Color.Gray }

/**
 * Color para el título de pantalla (Pistas, Álbumes, Artistas, etc.).
 * Apunta a highContrast (texto principal).
 */
val LocalScreenTitleColor = compositionLocalOf { Color.Black }

/**
 * Color para títulos de ítem de lista (título de pista, nombre de
 * álbum, etc.). Apunta a highContrast (texto principal).
 */
val LocalListItemTitleColor = compositionLocalOf { Color.Black }

/**
 * Color para subtítulos de ítem de lista (artista de pista, cantidad
 * de canciones, etc.). Apunta a secondaryOnBg (texto secundario).
 */
val LocalListItemSubtitleColor = compositionLocalOf { Color.Gray }

/**
 * Color para metadatos de ítem de lista (texto de duración de pista,
 * icono de más opciones, etc.). Apunta a inactiveOnBg.
 */
val LocalListItemMetaColor = compositionLocalOf { Color.Gray }

/**
 * Color del icono dentro del placeholder de carátula (cuando una
 * canción no tiene portada extraída). Apunta a inactiveOnBg.
 */
val LocalCoverPlaceholderIconColor = compositionLocalOf { Color.Gray }

/**
 * Color de fondo del contenedor de la pista actualmente en reproducción.
 * Apunta a cardL1 (superficie elevada nivel 1).
 */
val LocalCurrentTrackColor = compositionLocalOf { Color.Gray }

/**
 * Color del recuadro bordeado que rodea la fila de la pista actualmente
 * en reproducción. Apunta a highContrast (borde activo funcional).
 */
val LocalCurrentTrackBorderColor = compositionLocalOf { Color.Black }

/**
 * NUEVO: Color de superficie elevada nivel 2 (elementos anidados dentro
 * de una Card Nivel 1: círculos de iconos, chips, sub-paneles).
 *
 * Se introduce para liberar a `colorScheme.scrim` de su uso atípico
 * como "fondo de círculo de icono" en SplashCard y CapsuleButton. El
 * scrim real ahora queda disponible exclusivamente para overlays de
 * modales/bottom sheets según el documento de colores (#000000 @32%).
 */
val LocalCardL2Color = compositionLocalOf { Color.Gray }

/**
 * NUEVO: Color de borde de estado activo (selección, pista actual,
 * opción marcada). Apunta a highContrast (máximo contraste funcional).
 *
 * Reservado idealmente para el color "primary" una vez definido;
 * mientras tanto usa el valor neutro de máximo contraste del tema.
 */
val LocalActiveBorderColor = compositionLocalOf { Color.Black }

/**
 * Familia de fuentes activa según el idioma actual.
 * IBM Plex Sans para idiomas no árabes; IBM Plex Sans Arabic para árabe.
 */
val LocalAppFontFamily = compositionLocalOf { IBMPlexSansFamily }

// =========================================================================
// Esquemas de color Material3
// =========================================================================
//
// Mapeo de tokens base a slots de Material3 para que los consumidores
// de `MaterialTheme.colorScheme.*` obtengan los colores correctos sin
// cambios de código.
//
// Decisiones clave:
// - `primary` = highContrast (sin acento propio, coherencia neutra)
// - `tertiary` = success (el verde semántico para checks GRANTED)
// - `surfaceVariant` = cardL1 (superficie principal de componentes)
// - `inverseOnSurface` = highContrast (texto sobre fondo)

private val LightColors = lightColorScheme(
    primary = LightHighContrast,
    onPrimary = LightBackground,
    primaryContainer = LightCardL2,
    onPrimaryContainer = LightHighContrast,
    secondary = LightSecondaryOnBg,
    onSecondary = LightBackground,
    secondaryContainer = LightCardL2,
    onSecondaryContainer = LightHighContrast,
    tertiary = LightSuccess,
    onTertiary = LightOnSuccess,
    tertiaryContainer = LightSuccessContainer,
    onTertiaryContainer = LightOnSuccessContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightHighContrast,
    surface = LightBackground,
    onSurface = LightHighContrast,
    surfaceVariant = LightCardL1,
    onSurfaceVariant = LightSecondaryOnBg,
    outline = LightBorderL1,
    outlineVariant = LightBorderL2,
    scrim = LightScrim,
    inverseOnSurface = LightHighContrast,
    inverseSurface = LightHighContrast,
    inversePrimary = LightBackground
)

private val DarkColors = darkColorScheme(
    primary = DarkHighContrast,
    onPrimary = DarkBackground,
    primaryContainer = DarkCardL2,
    onPrimaryContainer = DarkHighContrast,
    secondary = DarkSecondaryOnBg,
    onSecondary = DarkBackground,
    secondaryContainer = DarkCardL2,
    onSecondaryContainer = DarkHighContrast,
    tertiary = DarkSuccess,
    onTertiary = DarkOnSuccess,
    tertiaryContainer = DarkSuccessContainer,
    onTertiaryContainer = DarkOnSuccessContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkHighContrast,
    surface = DarkBackground,
    onSurface = DarkHighContrast,
    surfaceVariant = DarkCardL1,
    onSurfaceVariant = DarkSecondaryOnBg,
    outline = DarkBorderL1,
    outlineVariant = DarkBorderL2,
    scrim = DarkScrim,
    inverseOnSurface = DarkHighContrast,
    inverseSurface = DarkHighContrast,
    inversePrimary = DarkBackground
)

private val AmoledColors = darkColorScheme(
    primary = AmoledHighContrast,
    onPrimary = AmoledBackground,
    primaryContainer = AmoledCardL2,
    onPrimaryContainer = AmoledHighContrast,
    secondary = AmoledSecondaryOnBg,
    onSecondary = AmoledBackground,
    secondaryContainer = AmoledCardL2,
    onSecondaryContainer = AmoledHighContrast,
    tertiary = AmoledSuccess,
    onTertiary = AmoledOnSuccess,
    tertiaryContainer = AmoledSuccessContainer,
    onTertiaryContainer = AmoledOnSuccessContainer,
    error = AmoledError,
    onError = AmoledOnError,
    errorContainer = AmoledErrorContainer,
    onErrorContainer = AmoledOnErrorContainer,
    background = AmoledBackground,
    onBackground = AmoledHighContrast,
    surface = AmoledBackground,
    onSurface = AmoledHighContrast,
    surfaceVariant = AmoledCardL1,
    onSurfaceVariant = AmoledSecondaryOnBg,
    outline = AmoledBorderL1,
    outlineVariant = AmoledBorderL2,
    scrim = AmoledScrim,
    inverseOnSurface = AmoledHighContrast,
    inverseSurface = AmoledHighContrast,
    inversePrimary = AmoledBackground
)

// =========================================================================
// Tema principal de OpenPlayer
// =========================================================================

/**
 * Tema principal de OpenPlayer.
 *
 * Aplica uno de los tres esquemas de color (Claro, Oscuro o AMOLED)
 * según el [themeMode] indicado. Los colores dinámicos del sistema
 * quedan deliberadamente desactivados: OpenPlayer siempre usa sus
 * propios colores definidos para cada tema.
 *
 * La tipografía se construye usando la familia provista por
 * [LocalAppFontFamily], que es provista por ThemeSwitcherHost según
 * el idioma activo (IBM Plex Sans o IBM Plex Sans Arabic).
 *
 * Provee CompositionLocals de colores semánticos (compatibles con
 * nombres existentes + nuevos LocalCardL2Color y LocalActiveBorderColor)
 * para que los componentes accedan a colores según el tema sin pasar
 * parámetros.
 */
@Composable
fun OpenPlayerTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.AMOLED -> AmoledColors
    }

    // Tokens base por tema (única fuente de verdad)
    val highContrast = when (themeMode) {
        ThemeMode.LIGHT -> LightHighContrast
        ThemeMode.DARK -> DarkHighContrast
        ThemeMode.AMOLED -> AmoledHighContrast
    }
    val secondaryOnBg = when (themeMode) {
        ThemeMode.LIGHT -> LightSecondaryOnBg
        ThemeMode.DARK -> DarkSecondaryOnBg
        ThemeMode.AMOLED -> AmoledSecondaryOnBg
    }
    val inactiveOnBg = when (themeMode) {
        ThemeMode.LIGHT -> LightInactiveOnBg
        ThemeMode.DARK -> DarkInactiveOnBg
        ThemeMode.AMOLED -> AmoledInactiveOnBg
    }
    val cardL1 = when (themeMode) {
        ThemeMode.LIGHT -> LightCardL1
        ThemeMode.DARK -> DarkCardL1
        ThemeMode.AMOLED -> AmoledCardL1
    }
    val cardL2 = when (themeMode) {
        ThemeMode.LIGHT -> LightCardL2
        ThemeMode.DARK -> DarkCardL2
        ThemeMode.AMOLED -> AmoledCardL2
    }

    // Construir tipografía usando la familia provista por LocalAppFontFamily
    val typography = buildTypography(LocalAppFontFamily.current)

    CompositionLocalProvider(
        // Locals existentes (apuntan a tokens nuevos sin romper consumidores)
        LocalFloatingIconColor provides secondaryOnBg,
        LocalNavIconActiveColor provides highContrast,
        LocalNavIconInactiveColor provides inactiveOnBg,
        LocalTopBarIconColor provides highContrast,
        LocalTracksCountTextColor provides secondaryOnBg,
        LocalScreenTitleColor provides highContrast,
        LocalListItemTitleColor provides highContrast,
        LocalListItemSubtitleColor provides secondaryOnBg,
        LocalListItemMetaColor provides inactiveOnBg,
        LocalCoverPlaceholderIconColor provides inactiveOnBg,
        LocalCurrentTrackColor provides cardL1,
        LocalCurrentTrackBorderColor provides highContrast,
        // Locales nuevos
        LocalCardL2Color provides cardL2,
        LocalActiveBorderColor provides highContrast
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}