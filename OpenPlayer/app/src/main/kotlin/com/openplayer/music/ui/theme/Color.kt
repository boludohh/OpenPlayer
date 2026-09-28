package com.openplayer.music.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// Tokens base del sistema de colores de OpenPlayer
// =========================================================================
//
// Diseño basado en jerarquía de superficies + bordes + contenido por
// contexto, con cero duplicados de valores hex entre temas.
//
// Tokens principales:
// - background: fondo principal de toda la pantalla
// - cardL1: superficie elevada nivel 1 (filas, tarjetas principales)
// - cardL2: superficie elevada nivel 2 (elementos anidados en cardL1)
// - borderL1: borde decorativo nivel 1 (separa cards del fondo)
// - borderL2: borde decorativo nivel 2 (separa elementos anidados)
// - highContrast: texto principal, iconos funcionales/activos, bordes
//   activos (absorbe todos los antiguos topBarIcon, navActive, sun/moon,
//   listItemTitle, screenTitle, currentTrackBorder, etc.)
// - secondaryOnBg: texto secundario, conteos, iconos decorativos
//   (absorbe tracksCountText, listItemSubtitle, floatingIcon)
// - inactiveOnBg: estados apagados (tabs inactivas, toggles off, meta,
//   placeholder icons; absorbe navIconInactive, listItemMeta,
//   coverPlaceholderIcon)
// - error/success/warning familias: colores semánticos con sus 4
//   variantes (color, on-color, container, on-container)
// - scrim: overlay oscuro para modales (#000000 @32% en los 3 temas)
//
// Mapeo a Material3 slots (ver Theme.kt):
// - primary / onPrimary → highContrast / background (sin acento propio,
//   coherencia total con el tema neutro)
// - tertiary → success (el verde semántico para checks GRANTED)
// - surfaceVariant → cardL1
// - outline / outlineVariant → borderL1 / borderL2

// =========================================================================
// Tema Claro
// =========================================================================

val LightBackground = Color(0xFFFFFFFF)
val LightCardL1 = Color(0xFFF5F5F5)
val LightCardL2 = Color(0xFFEBEBEB)
val LightBorderL1 = Color(0xFFE0E0E0)
val LightBorderL2 = Color(0xFFD0D0D0)
val LightHighContrast = Color(0xFF1C1B1F)
val LightSecondaryOnBg = Color(0xFF6E6E73)
val LightInactiveOnBg = Color(0xFF8E8E93)
val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
val LightSuccess = Color(0xFF2E6E3E)
val LightOnSuccess = Color(0xFFFFFFFF)
val LightSuccessContainer = Color(0xFFC3F2CB)
val LightOnSuccessContainer = Color(0xFF002106)
val LightWarning = Color(0xFF7A5900)
val LightOnWarning = Color(0xFFFFFFFF)
val LightWarningContainer = Color(0xFFFFDF9B)
val LightOnWarningContainer = Color(0xFF271900)
val LightScrim = Color(0x52000000)

// =========================================================================
// Tema Oscuro
// =========================================================================

val DarkBackground = Color(0xFF1C1B1F)
val DarkCardL1 = Color(0xFF242328)
val DarkCardL2 = Color(0xFF2C2B30)
val DarkBorderL1 = Color(0xFF3A383E)
val DarkBorderL2 = Color(0xFF454348)
val DarkHighContrast = Color(0xFFE6E1E5)
val DarkSecondaryOnBg = Color(0xFF9A979E)
val DarkInactiveOnBg = Color(0xFF948F96)
val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
val DarkSuccess = Color(0xFF8FDA9C)
val DarkOnSuccess = Color(0xFF00390F)
val DarkSuccessContainer = Color(0xFF14531F)
val DarkOnSuccessContainer = Color(0xFFC3F2CB)
val DarkWarning = Color(0xFFF6BE3F)
val DarkOnWarning = Color(0xFF3F2E00)
val DarkWarningContainer = Color(0xFF5C4300)
val DarkOnWarningContainer = Color(0xFFFFDF9B)
val DarkScrim = Color(0x52000000)

// =========================================================================
// Tema AMOLED (negro puro)
// =========================================================================

val AmoledBackground = Color(0xFF000000)
val AmoledCardL1 = Color(0xFF121212)
val AmoledCardL2 = Color(0xFF1C1C1C)
val AmoledBorderL1 = Color(0xFF2A2A2A)
val AmoledBorderL2 = Color(0xFF333333)
val AmoledHighContrast = Color(0xFFE6E1E5)
val AmoledSecondaryOnBg = Color(0xFF9A979E)
val AmoledInactiveOnBg = Color(0xFF948F96)
val AmoledError = Color(0xFFFFB4AB)
val AmoledOnError = Color(0xFF690005)
val AmoledErrorContainer = Color(0xFF7A0006)
val AmoledOnErrorContainer = Color(0xFFFFDAD6)
val AmoledSuccess = Color(0xFF8FDA9C)
val AmoledOnSuccess = Color(0xFF00390F)
val AmoledSuccessContainer = Color(0xFF0E4517)
val AmoledOnSuccessContainer = Color(0xFFC3F2CB)
val AmoledWarning = Color(0xFFF6BE3F)
val AmoledOnWarning = Color(0xFF3F2E00)
val AmoledWarningContainer = Color(0xFF4C3800)
val AmoledOnWarningContainer = Color(0xFFFFDF9B)
val AmoledScrim = Color(0x52000000)