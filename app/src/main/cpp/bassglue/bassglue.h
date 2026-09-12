#ifndef BASSGLUE_H
#define BASSGLUE_H

#include <jni.h>
#include <stdint.h>

/*
 * Header público del glue de BASS Audio Library.
 *
 * Este archivo define ÚNICAMENTE la API que exponemos a Kotlin vía JNI.
 * No contiene código, tipos ni constantes propietarias de BASS.
 *
 * La implementación real (bassglue.c) declara extern las funciones BASS
 * que necesita y las llama; el linker las resuelve contra el libbass.so
 * IMPORTED configurado en CMakeLists.txt.
 *
 * El usuario/fork debe proveer su propio libbass.so (no redistribuido
 * en este repo por razones de licencia) para que la app funcione.
 */

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Inicializa BASS con el sample rate indicado.
 * Retorna true si la inicialización fue exitosa.
 */
JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_init(
    JNIEnv *env, jclass clazz, jint sampleRate);

/*
 * Libera los recursos de BASS.
 */
JNIEXPORT void JNICALL
Java_com_openplayer_music_native_BassNative_free(
    JNIEnv *env, jclass clazz);

/*
 * Carga los plugins de BASS (FLAC y Opus) desde el directorio de
 * librerías nativas de Android.
 * Retorna true si ambos plugins se cargaron correctamente.
 */
JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_loadPlugins(
    JNIEnv *env, jclass clazz, jstring nativeLibDir);

/*
 * Crea un stream desde el path dado.
 * Retorna el handle del stream (> 0) o 0 si falla.
 * El stream tiene salida de audio directa al dispositivo.
 */
JNIEXPORT jint JNICALL
Java_com_openplayer_music_native_BassNative_streamCreateFile(
    JNIEnv *env, jclass clazz, jstring path);

/*
 * Libera el handle de un stream.
 * Retorna true si fue exitoso.
 */
JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_channelFree(
    JNIEnv *env, jclass clazz, jint handle);

/*
 * Reproduce el canal (reset = false: continúa desde pausa).
 * Retorna true si fue exitoso.
 */
JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_channelPlay(
    JNIEnv *env, jclass clazz, jint handle, jboolean restart);

/*
 * Pausa el canal.
 * Retorna true si fue exitoso.
 */
JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_channelPause(
    JNIEnv *env, jclass clazz, jint handle);

/*
 * Detiene el canal y libera el stream.
 * Retorna true si fue exitoso.
 */
JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_channelStop(
    JNIEnv *env, jclass clazz, jint handle);

/*
 * Hace seek a una posición en milisegundos.
 * Retorna true si fue exitoso.
 */
JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_channelSetPositionMs(
    JNIEnv *env, jclass clazz, jint handle, jlong positionMs);

/*
 * Retorna la posición actual de reproducción en milisegundos,
 * o -1 si hay error.
 */
JNIEXPORT jlong JNICALL
Java_com_openplayer_music_native_BassNative_channelGetPositionMs(
    JNIEnv *env, jclass clazz, jint handle);

/*
 * Retorna la duración total del stream en milisegundos,
 * o -1 si hay error.
 */
JNIEXPORT jlong JNICALL
Java_com_openplayer_music_native_BassNative_channelGetLengthMs(
    JNIEnv *env, jclass clazz, jint handle);

/*
 * Retorna el estado activo del canal:
 *   0 = stopped
 *   1 = playing
 *   2 = stalled (buffering)
 *   3 = paused
 *   4 = stopped (handle inválido)
 */
JNIEXPORT jint JNICALL
Java_com_openplayer_music_native_BassNative_channelIsActive(
    JNIEnv *env, jclass clazz, jint handle);

/*
 * Retorna el código del último error BASS, o 0 si no hubo error.
 */
JNIEXPORT jint JNICALL
Java_com_openplayer_music_native_BassNative_errorGetCode(
    JNIEnv *env, jclass clazz);

#ifdef __cplusplus
}
#endif

#endif /* BASSGLUE_H */