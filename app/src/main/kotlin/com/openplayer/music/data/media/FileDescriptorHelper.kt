package com.openplayer.music.data.media

import android.util.Log
import java.io.File
import java.io.FileInputStream

/**
 * Helper para abrir FileDescriptors desde rutas de archivo.
 * 
 * KTagLib requiere un FileDescriptor (Int) en lugar de una ruta (String).
 * Este helper actúa como adaptador entre el pipeline actual (que trabaja
 * con paths) y la API de KTagLib.
 * 
 * ## Uso con bloque use (recomendado)
 * ```kotlin
 * FileDescriptorHelper.useFd(path) { fd ->
 *     val metadata = KTagLib().getMetadata(fd, File(path).name)
 *     // ... usar metadata
 * } // El FileInputStream se cierra automáticamente aquí
 * ```
 * 
 * ## Responsabilidades
 * - Abrir un FileInputStream desde el path
 * - Proveer el FileDescriptor como Int dentro de un bloque de uso
 * - Cerrar automáticamente el stream al finalizar el bloque
 * - Manejar excepciones y retornar null si el archivo no se puede abrir
 */
object FileDescriptorHelper {

    private const val LOG_TAG = "FileDescriptorHelper"

    /**
     * Abre un FileDescriptor, ejecuta el bloque de código proporcionado,
     * y cierra el stream automáticamente al finalizar.
     * 
     * @param path Ruta absoluta del archivo
     * @param block Función que recibe el FileDescriptor como Int
     * @return El resultado del bloque, o null si el archivo no se puede abrir
     */
    inline fun <T> useFd(path: String, block: (Int) -> T): T? {
        val file = File(path)
        if (!file.exists()) {
            Log.w(LOG_TAG, "Archivo no existe: $path")
            return null
        }

        return try {
            FileInputStream(file).use { fis ->
                val fd = fis.fd
                if (!fd.valid()) {
                    Log.e(LOG_TAG, "FileDescriptor inválido para: $path")
                    return null
                }
                block(fd.`$`())
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error procesando $path: ${e.message}")
            null
        }
    }
}