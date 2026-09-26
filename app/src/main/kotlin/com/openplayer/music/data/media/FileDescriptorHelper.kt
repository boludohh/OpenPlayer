package com.openplayer.music.data.media

import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import java.io.File

/**
 * Helper para abrir FileDescriptors desde rutas de archivo.
 * 
 * KTagLib requiere un FileDescriptor (Int) en lugar de una ruta (String).
 * Este helper actúa como adaptador entre el pipeline actual (que trabaja
 * con paths) y la API de KTagLib.
 * 
 * ## Uso
 * ```kotlin
 * val metadata = FileDescriptorHelper.useFd(path) { fd ->
 *     KTagLib().getMetadata(fd, File(path).name)
 * }
 * ```
 * 
 * ## Responsabilidades
 * - Abrir un ParcelFileDescriptor desde el path
 * - Proveer el FileDescriptor como Int dentro de un bloque de uso
 * - Cerrar automáticamente el descriptor crudo al finalizar el bloque
 * - Manejar excepciones y retornar null si el archivo no se puede abrir
 */
object FileDescriptorHelper {

    private const val LOG_TAG = "FileDescriptorHelper"

    /**
     * Abre un FileDescriptor, ejecuta el bloque de código proporcionado,
     * y cierra el descriptor crudo automáticamente al finalizar.
     * 
     * @param path Ruta absoluta del archivo
     * @param block Función que recibe el FileDescriptor como Int
     * @return El resultado del bloque, o null si el archivo no se puede abrir
     */
    fun <T> useFd(path: String, block: (Int) -> T): T? {
        val file = File(path)
        if (!file.exists()) {
            Log.w(LOG_TAG, "Archivo no existe: $path")
            return null
        }

        var pfd: ParcelFileDescriptor? = null
        var fd: Int = -1
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fd = pfd.detachFd() // Obtenemos el Int y transferimos propiedad
            block(fd)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error procesando $path: ${e.message}")
            null
        } finally {
            // Cerramos el descriptor crudo manualmente para evitar fugas
            if (fd >= 0) {
                try {
                    // Crear un FileDescriptor usando reflection para poder cerrarlo con Os.close()
                    // Esto es necesario porque Os.close() espera un FileDescriptor, no un Int
                    val fileDescriptor = java.io.FileDescriptor()
                    val field = java.io.FileDescriptor::class.java.getDeclaredField("fd")
                    field.isAccessible = true
                    field.setInt(fileDescriptor, fd)
                    Os.close(fileDescriptor)
                } catch (e: Exception) {
                    Log.w(LOG_TAG, "Error cerrando fd para $path: ${e.message}")
                }
            }
            // Cerramos el ParcelFileDescriptor por si falló antes de detachFd
            try {
                pfd?.close()
            } catch (_: Exception) {}
        }
    }
}