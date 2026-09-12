package com.openplayer.music.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Lógica de permisos de OpenPlayer por nivel de API.
 *
 * Visibilidad de tarjetas en la Splash de configuración:
 * - Biblioteca musical: siempre (READ_EXTERNAL_STORAGE en 27-32,
 *   READ_MEDIA_AUDIO en 33+). OBLIGATORIO.
 * - Notificaciones: solo 33+ (POST_NOTIFICATIONS); en 27-32 el sistema
 *   las concede automáticamente. OBLIGATORIO cuando existe.
 * - Bluetooth: solo 31+ (BLUETOOTH_CONNECT); no existe en 27-30. OPCIONAL.
 * - Optimización de batería: siempre, mediante intent de sistema. OPCIONAL.
 */
object PermissionHandler {

    /** Permiso de acceso a audios según el nivel de API. */
    fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /** Permiso de notificaciones; null en API 27-32 (auto-concedido). */
    fun notificationPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    /** Permiso de Bluetooth; null en API 27-30 (no existe). */
    fun bluetoothPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            null
        }

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    fun hasAudioPermission(context: Context): Boolean =
        hasPermission(context, audioPermission())

    /**
     * Permisos obligatorios para mostrar "Finalizar":
     * audio siempre + notificaciones solo cuando existen (33+).
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        val notification = notificationPermission()
        return hasAudioPermission(context) &&
            (notification == null || hasPermission(context, notification))
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * Aviso informativo sobre notificaciones, mostrado en todos los
     * dispositivos (API 27-37) para informar al usuario sobre la
     * importancia de las notificaciones en OpenPlayer, independientemente
     * del nivel de API.
     */
    fun showsAndroid13Notice(): Boolean = true
}