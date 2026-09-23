package com.openplayer.music.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.openplayer.music.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * DataStore de preferencias de OpenPlayer (singleton por proceso).
 */
private val Context.openPlayerDataStore by preferencesDataStore(name = "openplayer_prefs")

/**
 * Persistencia de preferencias de OpenPlayer basada en Preferences DataStore.
 *
 * Guarda:
 * - Si la configuración inicial (Splash) fue completada.
 * - El tema confirmado por el usuario.
 * - El idioma elegido por el usuario.
 * - El timestamp (segundos epoch) del último escaneo de MediaStore,
 *   usado por el mecanismo de escaneo incremental.
 * - La clave API personal de Fanart.tv del usuario (opcional),
 *   preparada técnicamente para enviarse como `client_key`; su UI de
 *   entrada llegará más adelante en Ajustes.
 *
 * Expone Flows reactivos para uso en Compose y setters suspend.
 * Incluye lecturas bloqueantes únicamente para el arranque de las
 * Activities (attachBaseContext / onCreate), donde se necesita el
 * valor antes de dibujar el primer frame.
 */
class AppPreferences(private val context: Context) {

    /** true si el usuario completó la configuración inicial de la Splash. */
    val setupCompleted: Flow<Boolean> =
        context.openPlayerDataStore.data.map { prefs ->
            prefs[KEY_SETUP_COMPLETED] ?: false
        }

    /** Tema confirmado por el usuario; null si aún no eligió. */
    val themeMode: Flow<ThemeMode?> =
        context.openPlayerDataStore.data.map { prefs ->
            prefs[KEY_THEME_MODE]?.let { name ->
                runCatching { ThemeMode.valueOf(name) }.getOrNull()
            }
        }

    /** Código de idioma resuelto (por defecto Español). */
    val language: Flow<String> =
        context.openPlayerDataStore.data.map { prefs ->
            LocaleManager.resolveLanguage(prefs[KEY_LANGUAGE])
        }

    /**
     * Timestamp (segundos epoch) del último escaneo de MediaStore;
     * null si todavía no se realizó ningún escaneo completo.
     * Lo consume el escaneo incremental (DATE_ADDED > timestamp).
     */
    val lastScanSeconds: Flow<Long?> =
        context.openPlayerDataStore.data.map { prefs ->
            prefs[KEY_LAST_SCAN_SECONDS]
        }

    /**
     * Clave API personal de Fanart.tv del usuario; null si no la ha
     * configurado. Se envía como `client_key` junto a la clave de
     * proyecto (término general 2 de fanart.tv). Sin UI por ahora:
     * queda preparada técnicamente para conectarse luego en Ajustes.
     */
    val fanartUserKey: Flow<String?> =
        context.openPlayerDataStore.data.map { prefs ->
            prefs[KEY_FANART_USER_KEY]?.takeIf { it.isNotBlank() }
        }

    suspend fun setSetupCompleted(completed: Boolean) {
        context.openPlayerDataStore.edit { prefs ->
            prefs[KEY_SETUP_COMPLETED] = completed
        }
    }

    suspend fun setThemeMode(mode: ThemeMode?) {
        context.openPlayerDataStore.edit { prefs ->
            if (mode == null) {
                prefs.remove(KEY_THEME_MODE)
            } else {
                prefs[KEY_THEME_MODE] = mode.name
            }
        }
    }

    suspend fun setLanguage(code: String) {
        context.openPlayerDataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = code
        }
    }

    /**
     * Guarda el momento del último escaneo. Se llama al finalizar
     * un escaneo completo y después de cada escaneo incremental.
     */
    suspend fun setLastScanSeconds(seconds: Long) {
        context.openPlayerDataStore.edit { prefs ->
            prefs[KEY_LAST_SCAN_SECONDS] = seconds
        }
    }

    /**
     * Guarda (o limpia con null/blank) la clave personal de Fanart.tv
     * del usuario. Preparado para la futura UI de Ajustes.
     */
    suspend fun setFanartUserKey(key: String?) {
        context.openPlayerDataStore.edit { prefs ->
            if (key.isNullOrBlank()) {
                prefs.remove(KEY_FANART_USER_KEY)
            } else {
                prefs[KEY_FANART_USER_KEY] = key.trim()
            }
        }
    }

    // ===== Lecturas bloqueantes, SOLO para el arranque de Activities =====

    fun isSetupCompletedBlocking(): Boolean =
        runBlocking { setupCompleted.first() }

    fun getThemeModeBlocking(): ThemeMode? =
        runBlocking { themeMode.first() }

    fun getLanguageBlocking(): String =
        runBlocking { language.first() }

    companion object {
        private val KEY_SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_LAST_SCAN_SECONDS = longPreferencesKey("last_scan_seconds")
        private val KEY_FANART_USER_KEY = stringPreferencesKey("fanart_user_key")
    }
}