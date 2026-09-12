package com.openplayer.music.data.media

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.openplayer.music.native.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Repositorio de portadas de álbumes con caché de dos niveles:
 * - **Memoria**: [LruCache] de [Bitmap] escalados (~1/8 de la memoria
 *   disponible de la app).
 * - **Disco**: archivos con los **bytes originales** embebidos en
 *   `filesDir/covers/` (sin compresión ni re-escalado). El nombre
 *   del archivo es el MD5 del path de la canción + extensión real
 *   detectada por magic bytes (jpg / png / webp), con `.cover`
 *   como fallback para formatos desconocidos.
 *
 * ## Ciclo de vida
 * La extracción y guardado se realiza **en el mismo pipeline de
 * escaneo** ([AudioRepository.observeSongBatches]), junto con la
 * extracción de metadatos de TagLib. Es idempotente: si el archivo
 * ya existe en disco, no se hace nada.
 *
 * La lectura (para UI/notificación) sigue el orden:
 * **memoria → disco**. Ya no existe extracción bajo demanda; si la
 * portada no está en disco, la canción simplemente no tiene portada.
 *
 * Cuando una canción se elimina de Room, [deleteCover] borra también
 * el archivo de disco correspondiente.
 *
 * ## Formatos de imagen soportados
 * Se detectan por magic bytes (sin confiar en metadatos de TagLib,
 * que pueden venir mal):
 * - JPEG (`FF D8 FF`) → `.jpg`
 * - PNG (`89 50 4E 47`) → `.png`
 * - WebP (`RIFF....WEBP`) → `.webp`
 * - Cualquier otro formato decodable → `.cover` (fallback neutro)
 *
 * Si [BitmapFactory] no puede decodificar los bytes, la canción se
 * trata como "sin portada" y no se guarda ningún archivo, evitando
 * apuntar la notificación a un recurso que el dispositivo no puede
 * mostrar.
 */
class CoverRepository(private val context: Context) {

    /** Formatos de imagen reconocidos por sus magic bytes. */
    private enum class CoverFormat(val extension: String) {
        JPEG(".jpg"),
        PNG(".png"),
        WEBP(".webp"),
        UNKNOWN(".cover")
    }

    /** Caché de memoria: Bitmaps escalados a [maxMemorySize]. */
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        calculateMemoryCacheSize()
    ) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount
    }

    /** Directorio persistente en filesDir (no cacheDir). */
    private val diskCacheDir: File = File(context.filesDir, "covers").apply {
        if (!exists()) mkdirs()
    }

    /** Tamaño máximo del lado más largo para bitmaps en memoria. */
    private val maxMemorySize = 512

    // =========================================================================
    // API pública — escritura (llamada durante el escaneo)
    // =========================================================================

    /**
     * Extrae y guarda la portada de la canción en [path] durante
     * el escaneo. **Idempotente**: si ya existe cualquier variante
     * en disco, retorna inmediatamente sin hacer I/O pesado.
     *
     * @return `true` si la portada quedó guardada (o ya existía),
     *         `false` si la canción no tiene portada o si el
     *         dispositivo no puede decodificarla.
     */
    fun extractAndSaveCover(path: String): Boolean {
        val cacheKey = md5(path)

        // Idempotencia: si ya existe cualquier variante, salir
        if (findExistingCover(cacheKey) != null) return true

        // Extraer bytes crudos desde TagLib
        val bytes = NativeBridge.extractCoverBytes(path) ?: return false
        if (bytes.isEmpty()) return false

        // Validar decodibilidad: si el dispositivo no puede abrirlos,
        // no guardamos nada para que el artworkUri nunca apunte a un
        // archivo inútil.
        val probe = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return false
        probe.recycle()

        // Detectar formato real y construir nombre con extensión correcta
        val format = detectFormat(bytes)
        val diskFile = File(diskCacheDir, "$cacheKey${format.extension}")
        val tempFile = File(diskCacheDir, "$cacheKey.tmp")

        return try {
            // Guardar bytes ORIGINALES (sin compresión)
            FileOutputStream(tempFile).use { it.write(bytes) }
            if (!tempFile.renameTo(diskFile)) {
                tempFile.delete()
                false
            } else {
                true
            }
        } catch (_: Exception) {
            tempFile.delete()
            false
        }
    }

    /**
     * Devuelve el [File] de la portada en disco si existe, o `null`
     * si la canción no tiene portada guardada.
     *
     * Operación sincrónica rápida (stat de hasta 4 archivos). Se usa
     * desde `SongMediaItems.toMediaItem` para fijar `artworkUri` sin
     * hacer extracción bajo demanda.
     */
    fun coverFile(path: String): File? = findExistingCover(md5(path))

    /**
     * Borra el archivo de portada asociado a [path] y lo remueve
     * del caché de memoria. Se llama cuando una canción se elimina
     * de la biblioteca.
     */
    fun deleteCover(path: String) {
        val cacheKey = md5(path)
        memoryCache.remove(cacheKey)
        for (fmt in CoverFormat.entries) {
            File(diskCacheDir, "$cacheKey${fmt.extension}").delete()
        }
    }

    /**
     * Limpia ambos niveles de caché.
     */
    fun clearCache() {
        memoryCache.evictAll()
        diskCacheDir.listFiles()?.forEach { it.delete() }
    }

    // =========================================================================
    // API pública — lectura (para UI / notificación)
    // =========================================================================

    /**
     * Obtiene la portada de la canción en [path] para visualización.
     *
     * Flujo: **memoria → disco**. No realiza extracción bajo demanda
     * (toda la extracción se hizo en el escaneo). Si la portada no
     * está en disco, retorna `null`.
     *
     * El bitmap devuelto está escalado a [maxMemorySize] para el
     * caché de memoria. El archivo en disco conserva los bytes
     * originales completos.
     */
    suspend fun getCover(path: String): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = md5(path)

        // 1. Memoria
        memoryCache.get(cacheKey)?.let { return@withContext it }

        // 2. Disco
        val diskFile = findExistingCover(cacheKey) ?: return@withContext null
        val bitmap = FileInputStream(diskFile).use {
            BitmapFactory.decodeStream(it)
        } ?: return@withContext null

        val scaled = scaleBitmap(bitmap, maxMemorySize)
        memoryCache.put(cacheKey, scaled)
        if (scaled !== bitmap) bitmap.recycle()

        scaled
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /**
     * Busca cualquier archivo de portada existente para el [cacheKey].
     */
    private fun findExistingCover(cacheKey: String): File? {
        for (fmt in CoverFormat.entries) {
            val candidate = File(diskCacheDir, "$cacheKey${fmt.extension}")
            if (candidate.exists()) return candidate
        }
        return null
    }

    /**
     * Detecta el formato de la imagen leyendo los magic bytes.
     */
    private fun detectFormat(bytes: ByteArray): CoverFormat {
        if (bytes.size < 4) return CoverFormat.UNKNOWN
        val b0 = bytes[0]; val b1 = bytes[1]; val b2 = bytes[2]; val b3 = bytes[3]

        // JPEG: FF D8 FF
        if (b0 == 0xFF.toByte() && b1 == 0xD8.toByte() && b2 == 0xFF.toByte()) {
            return CoverFormat.JPEG
        }
        // PNG: 89 50 4E 47
        if (b0 == 0x89.toByte() && b1 == 0x50.toByte() &&
            b2 == 0x4E.toByte() && b3 == 0x47.toByte()
        ) {
            return CoverFormat.PNG
        }
        // WebP: RIFF....WEBP (offsets 0-3 y 8-11)
        if (bytes.size >= 12 &&
            b0 == 0x52.toByte() && b1 == 0x49.toByte() &&
            b2 == 0x46.toByte() && b3 == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        ) {
            return CoverFormat.WEBP
        }
        return CoverFormat.UNKNOWN
    }

    /** Escala manteniendo aspect ratio. */
    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxSize && h <= maxSize) return bitmap
        val ratio = if (w > h) maxSize.toFloat() / w else maxSize.toFloat() / h
        return Bitmap.createScaledBitmap(
            bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true
        )
    }

    /** MD5 del string (nombre del archivo). */
    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** 1/8 de la memoria disponible de la app. */
    private fun calculateMemoryCacheSize(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return (am.memoryClass * 1024 * 1024) / 8
    }
}