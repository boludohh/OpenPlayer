package com.openplayer.music.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Gestiona el idioma de la aplicación sin depender de AppCompat.
 *
 * - Idioma por defecto: Español (sin opción de seguir al sistema).
 * - Idiomas soportados en la primera versión:
 *   es, en, pt-BR, pt, ar, fr.
 * - Aplica el idioma mediante createConfigurationContext (API 17+),
 *   compatible con minSdk 27.
 *
 * RTL (escritura de derecha a izquierda):
 * - La inversión visual del layout se gestiona a nivel de Compose
 *   mediante CompositionLocalProvider con LocalLayoutDirection en
 *   ThemeSwitcherHost, usando el helper [isRtl] de este objeto.
 * - Este enfoque es compatible con todas las versiones de Android
 *   soportadas (API 27–37) y evita los crashes de
 *   applyOverrideConfiguration() en versiones antiguas y recientes.
 * - Los strings y el locale de la Activity se siguen aplicando en
 *   attachBaseContext mediante [applyLocale].
 */
object LocaleManager {

    const val LANGUAGE_ES = "es"
    const val LANGUAGE_EN = "en"
    const val LANGUAGE_PT_BR = "pt-BR"
    const val LANGUAGE_PT = "pt"
    const val LANGUAGE_AR = "ar"
    const val LANGUAGE_FR = "fr"

    const val DEFAULT_LANGUAGE = LANGUAGE_ES

    val SUPPORTED_LANGUAGES: List<String> = listOf(
        LANGUAGE_ES,
        LANGUAGE_EN,
        LANGUAGE_PT_BR,
        LANGUAGE_PT,
        LANGUAGE_AR,
        LANGUAGE_FR
    )

    /**
     * Códigos de idioma que utilizan escritura de derecha a izquierda.
     * Extender esta lista si se agregan más idiomas RTL (hebreo, farsi,
     * urdu, etc.).
     */
    private val RTL_LANGUAGES: Set<String> = setOf(LANGUAGE_AR)

    /** Indica si el código de idioma dado usa dirección RTL. */
    fun isRtl(code: String): Boolean = code in RTL_LANGUAGES

    /** Resuelve el código de idioma guardado; si no es válido, usa el defecto. */
    fun resolveLanguage(saved: String?): String =
        if (saved != null && SUPPORTED_LANGUAGES.contains(saved)) saved else DEFAULT_LANGUAGE

    /**
     * Convierte un código de idioma en [Locale] utilizando la API moderna
     * [Locale.Builder] (disponible desde API 21). Los constructores
     * directos de Locale están deprecados desde Java 19 y Android 16.
     */
    fun localeFor(code: String): Locale =
        when (code) {
            LANGUAGE_PT_BR -> Locale.Builder().setLanguage("pt").setRegion("BR").build()
            else -> Locale.Builder().setLanguage(code).build()
        }

    /**
     * Devuelve un contexto con el idioma guardado aplicado.
     * Usa una lectura bloqueante de DataStore: se invoca una única vez
     * por creación de Activity en attachBaseContext.
     *
     * En versiones anteriores a Android 13 (API < 33) también actualiza
     * los recursos del contexto base para que todas las vistas y
     * recursos globales respeten el locale y el layout direction.
     * La inversión visual real del layout se hace a nivel de Compose
     * en ThemeSwitcherHost mediante LocalLayoutDirection.
     */
    fun applyLocale(context: Context): Context {
        val code = AppPreferences(context).getLanguageBlocking()
        val locale = localeFor(code)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        // En API < 33, actualizar los recursos del contexto base garantiza
        // que todas las vistas respeten el locale. En API 33+ el sistema
        // lo propaga de forma global y no hace falta.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(
                configuration,
                context.resources.displayMetrics
            )
        }

        return context.createConfigurationContext(configuration)
    }
}