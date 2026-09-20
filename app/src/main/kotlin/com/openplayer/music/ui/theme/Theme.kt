package com.openplayer.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color custom para los iconos flotantes de la pantalla de bienvenida.
 * Se provee mediante CompositionLocal en OpenPlayerTheme para que
 * cualquier componente descendiente pueda acceder al color correcto
 * según el tema activo, sin necesidad de pasar parámetros.
 *
 * Valores:
 * - Claro: #D9D9D9
 * - Oscuro: #333333
 * - AMOLED: #212121
 */
val LocalFloatingIconColor = compositionLocalOf { Color.Gray }

/**
 * Color para iconos de navegación activos.
 * Se provee mediante CompositionLocal para acceso global según el tema.
 */
val LocalNavIconActiveColor = compositionLocalOf { Color.Black }

/**
 * Color para iconos de navegación inactivos.
 * Se provee mediante CompositionLocal para acceso global según el tema.
 */
val LocalNavIconInactiveColor = compositionLocalOf { Color.Gray }

/**
 * Color para iconos de la barra superior (menú y búsqueda).
 * Se provee mediante CompositionLocal para acceso global según el tema.
 *
 * Valores:
 * - Claro: #1A1A1A
 * - Oscuro: #F5F5F5
 * - AMOLED: #E5E5E5
 */
val LocalTopBarIconColor = compositionLocalOf { Color.Black }

/**
 * Color para el texto de conteo de pistas en la pantalla de Tracks.
 * Se provee mediante CompositionLocal para acceso global según el tema.
 *
 * Valores:
 * - Claro: #525252
 * - Oscuro: #B3B3B3
 * - AMOLED: #A3A3A3
 */
val LocalTracksCountTextColor = compositionLocalOf { Color.Gray }

/**
 * Color para el título de pantalla (Pistas, Álbumes, Artistas, etc.).
 * Se provee mediante CompositionLocal para acceso global según el tema.
 * Reutiliza los mismos colores que los iconos de la barra superior.
 *
 * Valores:
 * - Claro: #1A1A1A
 * - Oscuro: #F5F5F5
 * - AMOLED: #E5E5E5
 */
val LocalScreenTitleColor = compositionLocalOf { Color.Black }

/**
 * Color para títulos de ítem de lista (título de pista, nombre de álbum, etc.).
 * Genérico y reutilizable en Tracks/Albums/Artists/Playlists.
 * Reutiliza los mismos colores que los iconos de la barra superior.
 *
 * Valores:
 * - Claro: #1A1A1A
 * - Oscuro: #F5F5F5
 * - AMOLED: #E5E5E5
 */
val LocalListItemTitleColor = compositionLocalOf { Color.Black }

/**
 * Color para subtítulos de ítem de lista (artista de pista, cantidad de
 * canciones, etc.). Genérico y reutilizable en Tracks/Albums/Artists/Playlists.
 * Reutiliza los mismos colores que el texto de conteo de pistas.
 *
 * Valores:
 * - Claro: #525252
 * - Oscuro: #B3B3B3
 * - AMOLED: #A3A3A3
 */
val LocalListItemSubtitleColor = compositionLocalOf { Color.Gray }

/**
 * Color para metadatos de ítem de lista (texto de duración de pista,
 * icono de más opciones, etc.). Genérico y reutilizable en
 * Tracks/Albums/Artists/Playlists.
 * Reutiliza los mismos colores que los iconos de navegación inactivos.
 *
 * Valores:
 * - Claro: #A3A3A3
 * - Oscuro: #8C8C8C
 * - AMOLED: #737373
 */
val LocalListItemMetaColor = compositionLocalOf { Color.Gray }

/**
 * Color del icono dentro del placeholder de carátula (cuando una canción
 * no tiene portada extraída). Genérico y reutilizable en Tracks/Albums.
 * Reutiliza los mismos colores que los iconos de navegación inactivos.
 *
 * Valores:
 * - Claro: #A3A3A3
 * - Oscuro: #8C8C8C
 * - AMOLED: #737373
 */
val LocalCoverPlaceholderIconColor = compositionLocalOf { Color.Gray }

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    inverseOnSurface = LightInverseOnSurface
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    inverseOnSurface = DarkInverseOnSurface
)

private val AmoledColors = darkColorScheme(
    primary = AmoledPrimary,
    onPrimary = AmoledOnPrimary,
    primaryContainer = AmoledPrimaryContainer,
    onPrimaryContainer = AmoledOnPrimaryContainer,
    secondary = AmoledSecondary,
    onSecondary = AmoledOnSecondary,
    secondaryContainer = AmoledSecondaryContainer,
    onSecondaryContainer = AmoledOnSecondaryContainer,
    tertiary = AmoledTertiary,
    onTertiary = AmoledOnTertiary,
    tertiaryContainer = AmoledTertiaryContainer,
    onTertiaryContainer = AmoledOnTertiaryContainer,
    error = AmoledError,
    onError = AmoledOnError,
    errorContainer = AmoledErrorContainer,
    onErrorContainer = AmoledOnErrorContainer,
    background = AmoledBackground,
    onBackground = AmoledOnBackground,
    surface = AmoledSurface,
    onSurface = AmoledOnSurface,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = AmoledOnSurfaceVariant,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineVariant,
    scrim = AmoledScrim,
    inverseOnSurface = AmoledInverseOnSurface
)

/**
 * Tema principal de OpenPlayer.
 *
 * Aplica uno de los tres esquemas de color (Claro, Oscuro o AMOLED)
 * según el [themeMode] indicado. Los colores dinámicos del sistema
 * quedan deliberadamente desactivados: OpenPlayer siempre usa sus
 * propios colores definidos para cada tema.
 *
 * También provee [LocalFloatingIconColor] con el color específico
 * para los iconos flotantes de la pantalla de bienvenida, adaptado
 * al tema activo.
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

    val floatingIconColor = when (themeMode) {
        ThemeMode.LIGHT -> LightFloatingIcon
        ThemeMode.DARK -> DarkFloatingIcon
        ThemeMode.AMOLED -> AmoledFloatingIcon
    }

    val navIconActiveColor = when (themeMode) {
        ThemeMode.LIGHT -> LightNavIconActive
        ThemeMode.DARK -> DarkNavIconActive
        ThemeMode.AMOLED -> AmoledNavIconActive
    }

    val navIconInactiveColor = when (themeMode) {
        ThemeMode.LIGHT -> LightNavIconInactive
        ThemeMode.DARK -> DarkNavIconInactive
        ThemeMode.AMOLED -> AmoledNavIconInactive
    }

    val topBarIconColor = when (themeMode) {
        ThemeMode.LIGHT -> LightTopBarIcon
        ThemeMode.DARK -> DarkTopBarIcon
        ThemeMode.AMOLED -> AmoledTopBarIcon
    }

    val tracksCountTextColor = when (themeMode) {
        ThemeMode.LIGHT -> LightTracksCountText
        ThemeMode.DARK -> DarkTracksCountText
        ThemeMode.AMOLED -> AmoledTracksCountText
    }

    val screenTitleColor = when (themeMode) {
        ThemeMode.LIGHT -> LightTopBarIcon
        ThemeMode.DARK -> DarkTopBarIcon
        ThemeMode.AMOLED -> AmoledTopBarIcon
    }

    val listItemTitleColor = when (themeMode) {
        ThemeMode.LIGHT -> LightTopBarIcon
        ThemeMode.DARK -> DarkTopBarIcon
        ThemeMode.AMOLED -> AmoledTopBarIcon
    }

    val listItemSubtitleColor = when (themeMode) {
        ThemeMode.LIGHT -> LightTracksCountText
        ThemeMode.DARK -> DarkTracksCountText
        ThemeMode.AMOLED -> AmoledTracksCountText
    }

    val listItemMetaColor = when (themeMode) {
        ThemeMode.LIGHT -> LightNavIconInactive
        ThemeMode.DARK -> DarkNavIconInactive
        ThemeMode.AMOLED -> AmoledNavIconInactive
    }

    val coverPlaceholderIconColor = when (themeMode) {
        ThemeMode.LIGHT -> LightNavIconInactive
        ThemeMode.DARK -> DarkNavIconInactive
        ThemeMode.AMOLED -> AmoledNavIconInactive
    }

    CompositionLocalProvider(
        LocalFloatingIconColor provides floatingIconColor,
        LocalNavIconActiveColor provides navIconActiveColor,
        LocalNavIconInactiveColor provides navIconInactiveColor,
        LocalTopBarIconColor provides topBarIconColor,
        LocalTracksCountTextColor provides tracksCountTextColor,
        LocalScreenTitleColor provides screenTitleColor,
        LocalListItemTitleColor provides listItemTitleColor,
        LocalListItemSubtitleColor provides listItemSubtitleColor,
        LocalListItemMetaColor provides listItemMetaColor,
        LocalCoverPlaceholderIconColor provides coverPlaceholderIconColor
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}