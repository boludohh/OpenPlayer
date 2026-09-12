package com.openplayer.music.native

/**
 * Puente JNI hacia las funciones del glue de BASS Audio Library
 * implementado en `bassglue.c`.
 *
 * El glue declara extern las funciones BASS que usa y las llama;
 * el linker las resuelve contra el `libbass.so` precompilado que
 * el desarrollador (o el fork) provee localmente en
 * `app/src/main/cpp/bass/<abi>/libbass.so`. Este `.so` NO se
 * redistribuye en el repo por razones de licencia.
 *
 * Uso básico:
 * ```kotlin
 * if (BassNative.init(44100)) {
 *     BassNative.loadPlugins(nativeLibDir)
 *     val handle = BassNative.streamCreateFile("/ruta/al/archivo.mp3")
 *     if (handle != 0) {
 *         BassNative.channelPlay(handle, false)
 *     }
 * }
 * ```
 *
 * Manejo de handles:
 * - `streamCreateFile` retorna un entero > 0 si fue exitoso, 0 si falló.
 * - Todas las funciones de canal reciben el handle.
 * - `channelFree` debe llamarse cuando se termina de usar el stream.
 */
object BassNative {

    init {
        System.loadLibrary("openplayeraudio")
    }

    /**
     * Inicializa BASS con el sample rate indicado.
     * Debe llamarse una sola vez antes de crear streams.
     *
     * @param sampleRate Sample rate de salida (ej. 44100).
     * @return true si la inicialización fue exitosa.
     */
    external fun init(sampleRate: Int): Boolean

    /**
     * Libera los recursos de BASS.
     * Debe llamarse cuando la app ya no va a reproducir audio.
     */
    external fun free()

    /**
     * Carga los plugins de BASS (FLAC y Opus) desde el directorio
     * de librerías nativas de Android.
     *
     * @param nativeLibDir Ruta al directorio donde están instaladas
     *                     las librerías nativas (obtenida con
     *                     context.applicationInfo.nativeLibraryDir).
     * @return true si ambos plugins se cargaron correctamente.
     */
    external fun loadPlugins(nativeLibDir: String): Boolean

    /**
     * Crea un stream desde el path dado.
     *
     * @param path Ruta absoluta al archivo de audio.
     * @return Handle del stream (> 0) o 0 si falló.
     */
    external fun streamCreateFile(path: String): Int

    /**
     * Libera el handle de un stream.
     *
     * @param handle Handle retornado por [streamCreateFile].
     * @return true si fue liberado correctamente.
     */
    external fun channelFree(handle: Int): Boolean

    /**
     * Reproduce el stream.
     *
     * @param handle Handle del stream.
     * @param restart true para empezar desde el inicio, false para continuar.
     */
    external fun channelPlay(handle: Int, restart: Boolean): Boolean

    /**
     * Pausa el stream.
     */
    external fun channelPause(handle: Int): Boolean

    /**
     * Detiene el stream.
     */
    external fun channelStop(handle: Int): Boolean

    /**
     * Hace seek a la posición indicada en milisegundos.
     */
    external fun channelSetPositionMs(handle: Int, positionMs: Long): Boolean

    /**
     * @return Posición actual de reproducción en milisegundos, o -1 si hay error.
     */
    external fun channelGetPositionMs(handle: Int): Long

    /**
     * @return Duración total del stream en milisegundos, o -1 si hay error.
     */
    external fun channelGetLengthMs(handle: Int): Long

    /**
     * Retorna el estado activo del canal:
     * - 0 = stopped
     * - 1 = playing
     * - 2 = stalled (buffering)
     * - 3 = paused
     * - 4 = handle inválido
     */
    external fun channelIsActive(handle: Int): Int

    /**
     * @return Código del último error BASS, o 0 si no hubo error.
     */
    external fun errorGetCode(): Int

    /** Constantes de estado retornadas por [channelIsActive]. */
    object State {
        const val STOPPED = 0
        const val PLAYING = 1
        const val STALLED = 2
        const val PAUSED = 3
    }
}