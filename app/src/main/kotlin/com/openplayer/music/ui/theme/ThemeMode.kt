package com.openplayer.music.ui.theme

/**
 * Modos de tema disponibles en OpenPlayer.
 *
 * OpenPlayer soporta 2 temas:
 * - LIGHT (Claro): fondo blanco (#FFFFFF), texto oscuro.
 * - DARK (Oscuro): fondo negro puro (#000000), texto claro.
 *
 * El ciclo alterna entre ambos temas:
 * - LIGHT -> DARK -> LIGHT...
 * - DARK -> LIGHT -> DARK...
 *
 * El ciclo se ancla al tema del sistema al arrancar para que el primer
 * cambio vaya en la dirección natural (si el sistema está claro, el
 * primer toque cambia a oscuro, y viceversa).
 */
enum class ThemeMode {
    LIGHT,
    DARK;

    companion object {
        /** Orden del ciclo anclado al tema del sistema. */
        fun cycleOrder(systemIsDark: Boolean): List<ThemeMode> =
            if (systemIsDark) listOf(DARK, LIGHT)
            else listOf(LIGHT, DARK)

        /** Siguiente tema del ciclo para el tema actual. */
        fun next(current: ThemeMode, systemIsDark: Boolean): ThemeMode {
            val order = cycleOrder(systemIsDark)
            val index = order.indexOf(current)
            return order[(index + 1) % order.size]
        }
    }
}