package com.openplayer.music.native

/**
 * Puente JNI hacia la librería nativa libopenplayeraudio.so.
 *
 * Proporciona acceso a las funciones de extracción de metadatos y
 * portadas implementadas en C++ con TagLib 2.3.1.
 *
 * La validación de formato se realiza en Kotlin puro mediante
 * [com.openplayer.music.data.media.AudioFormatParser].
 *
 * Uso:
 * ```kotlin
 * val metadata = NativeBridge.extractMetadata("/path/to/file.mp3")
 * val coverBytes = NativeBridge.extractCoverBytes("/path/to/file.mp3")
 * ```
 */
object NativeBridge {

    init {
        System.loadLibrary("openplayeraudio")
    }

    /**
     * Verifica si la librería nativa está disponible y cargada.
     * Siempre retorna true si la carga fue exitosa.
     */
    external fun isNativeAvailable(): Boolean

    /**
     * Retorna la versión de la librería nativa.
     */
    external fun getVersion(): String

    /**
     * Extrae metadatos completos del archivo de audio en [path] usando TagLib.
     *
     * Retorna un mapa con las siguientes claves (si están disponibles):
     * - title, artist, album, albumArtist, genre, composer, lyrics
     * - trackNumber, discNumber, year
     * - duration (milisegundos), bitrate (kb/s), sampleRate (Hz), channels
     *
     * Los valores son strings; los campos numéricos deben parsearse en Kotlin.
     * Si un campo no está disponible, no aparece en el mapa.
     */
    external fun extractMetadata(path: String): Map<String, String>

    /**
     * Extrae la primera portada embebida del archivo de audio en [path].
     *
     * Formatos soportados:
     * - MP3: frames APIC en ID3v2
     * - FLAC: pictureList()
     * - OGG: pictureList() en XiphComment
     * - M4A/MP4: item "covr"
     *
     * @return ByteArray con los bytes de la imagen (JPEG/PNG) o null si no hay portada.
     */
    external fun extractCoverBytes(path: String): ByteArray?
}