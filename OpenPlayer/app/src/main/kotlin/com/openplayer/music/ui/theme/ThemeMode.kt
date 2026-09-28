package com.openplayer.music.ui.theme

/**
 * Modos de tema disponibles en OpenPlayer.
 *
 * El ciclo de cambio de tema queda anclado al tema del sistema al arrancar,
 * y AMOLED siempre es el último elemento del ciclo:
 * - Sistema claro: Claro -> Oscuro -> AMOLED -> Claro...
 * - Sistema oscuro: Oscuro -> Claro -> AMOLED -> Oscuro...
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    AMOLED;

    companion object {
        /** Orden del ciclo anclado al tema del sistema. */
        fun cycleOrder(systemIsDark: Boolean): List<ThemeMode> =
            if (systemIsDark) listOf(DARK, LIGHT, AMOLED)
            else listOf(LIGHT, DARK, AMOLED)

        /** Siguiente tema del ciclo para el tema actual. */
        fun next(current: ThemeMode, systemIsDark: Boolean): ThemeMode {
            val order = cycleOrder(systemIsDark)
            val index = order.indexOf(current)
            return order[(index + 1) % order.size]
        }
    }
}