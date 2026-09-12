#include "bassglue.h"
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <stdio.h>

/*
 * Glue de BASS Audio Library para OpenPlayer.
 *
 * Este archivo implementa el puente JNI hacia BASS sin incluir el
 * header oficial bass.h. Declaramos extern solo las funciones y
 * constantes mínimas que usamos; el linker las resuelve contra el
 * libbass.so IMPORTED de CMakeLists.txt.
 *
 * Esto mantiene el repo libre de código propietario de BASS,
 * coherente con la licencia GPL-3.0 de OpenPlayer.
 */

#define BASSGLUE_LOG_TAG "BassGlue"
#define BASSGLUE_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, BASSGLUE_LOG_TAG, __VA_ARGS__)
#define BASSGLUE_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  BASSGLUE_LOG_TAG, __VA_ARGS__)

/* ==========================================================================
 * Tipos y constantes mínimos de BASS que usamos.
 * Solo los necesarios para nuestro glue; no copiamos bass.h.
 * ========================================================================== */

/* Tipo BOOL de BASS (int) - DEBE estar antes de las declaraciones extern */
#ifndef BOOL
#define BOOL int
#endif
#ifndef TRUE
#define TRUE 1
#endif
#ifndef FALSE
#define FALSE 0
#endif

typedef unsigned int DWORD;
typedef void *HSTREAM; /* En BASS, HSTREAM es DWORD; lo usamos así por claridad */

/* Flags de BASS_Init (device: -1 = default) */
#define BASS_DEVICE_DEFAULT 0

/* Estados retornados por BASS_ChannelIsActive */
#define BASS_ACTIVE_STOPPED 0
#define BASS_ACTIVE_PLAYING 1
#define BASS_ACTIVE_STALLED 2
#define BASS_ACTIVE_PAUSED  3

/* ==========================================================================
 * Declaraciones extern de las funciones BASS que llamamos.
 * El linker las resuelve contra libbass.so IMPORTED.
 * ========================================================================== */

extern BOOL BASS_Init(int device, DWORD freq, DWORD flags, void *win, void *dsguid);
extern BOOL BASS_Free(void);
extern DWORD BASS_StreamCreateFile(BOOL mem, const void *file, unsigned long long offset,
                                   unsigned long long length, DWORD flags);
extern BOOL BASS_ChannelFree(DWORD handle);
extern BOOL BASS_ChannelPlay(DWORD handle, BOOL restart);
extern BOOL BASS_ChannelPause(DWORD handle);
extern BOOL BASS_ChannelStop(DWORD handle);
extern BOOL BASS_ChannelSetPosition(DWORD handle, unsigned long long pos, DWORD mode);
extern unsigned long long BASS_ChannelGetPosition(DWORD handle, DWORD mode);
extern unsigned long long BASS_ChannelGetLength(DWORD handle, DWORD mode);
extern double BASS_ChannelBytes2Seconds(DWORD handle, unsigned long long pos);
extern unsigned long long BASS_ChannelSeconds2Bytes(DWORD handle, double pos);
extern DWORD BASS_ChannelIsActive(DWORD handle);
extern int BASS_ErrorGetCode(void);
extern DWORD BASS_PluginLoad(const char *file, DWORD flags);

/* Constantes de modo de posición */
#define BASS_POS_BYTE 0

/* ==========================================================================
 * Implementación de las funciones JNI
 * ========================================================================== */

JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_init(
    JNIEnv *env, jclass clazz, jint sampleRate) {
    (void)env; (void)clazz;
    BOOL ok = BASS_Init(-1, (DWORD)sampleRate, BASS_DEVICE_DEFAULT, NULL, NULL);
    if (!ok) {
        int err = BASS_ErrorGetCode();
        BASSGLUE_LOGE("BASS_Init failed, error code: %d", err);
    } else {
        BASSGLUE_LOGI("BASS_Init OK, sampleRate=%d", sampleRate);
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_openplayer_music_native_BassNative_free(
    JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    BASS_Free();
    BASSGLUE_LOGI("BASS_Free called");
}

JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_loadPlugins(
    JNIEnv *env, jclass clazz, jstring nativeLibDir) {
    (void)clazz;
    const char *libDir = (*env)->GetStringUTFChars(env, nativeLibDir, NULL);
    if (libDir == NULL) {
        return JNI_FALSE;
    }

    jboolean success = JNI_TRUE;
    char pluginPath[512];

    /* Cargar plugin FLAC */
    snprintf(pluginPath, sizeof(pluginPath), "%s/libbassflac.so", libDir);
    DWORD flacHandle = BASS_PluginLoad(pluginPath, 0);
    if (flacHandle == 0) {
        int err = BASS_ErrorGetCode();
        BASSGLUE_LOGE("Failed to load libbassflac.so, error code: %d", err);
        success = JNI_FALSE;
    } else {
        BASSGLUE_LOGI("libbassflac.so loaded successfully");
    }

    /* Cargar plugin Opus */
    snprintf(pluginPath, sizeof(pluginPath), "%s/libbassopus.so", libDir);
    DWORD opusHandle = BASS_PluginLoad(pluginPath, 0);
    if (opusHandle == 0) {
        int err = BASS_ErrorGetCode();
        BASSGLUE_LOGE("Failed to load libbassopus.so, error code: %d", err);
        success = JNI_FALSE;
    } else {
        BASSGLUE_LOGI("libbassopus.so loaded successfully");
    }

    /* Cargar plugin AAC */
    snprintf(pluginPath, sizeof(pluginPath), "%s/libbass_aac.so", libDir);
    DWORD aacHandle = BASS_PluginLoad(pluginPath, 0);
    if (aacHandle == 0) {
        int err = BASS_ErrorGetCode();
        BASSGLUE_LOGE("Failed to load libbass_aac.so, error code: %d", err);
        success = JNI_FALSE;
    } else {
        BASSGLUE_LOGI("libbass_aac.so loaded successfully");
    }

    (*env)->ReleaseStringUTFChars(env, nativeLibDir, libDir);
    return success;
}

JNIEXPORT jint JNICALL
Java_com_openplayer_music_native_BassNative_streamCreateFile(
    JNIEnv *env, jclass clazz, jstring path) {
    (void)clazz;
    const char *pathStr = (*env)->GetStringUTFChars(env, path, NULL);
    if (pathStr == NULL) {
        return 0;
    }

    /* Sin BASS_STREAM_DECODE: creamos un stream con salida de audio
     * directa al dispositivo. Esto permite que BASS envíe el audio
     * al altavoz automáticamente. */
    DWORD handle = BASS_StreamCreateFile(
        FALSE,          /* mem: false, es un archivo */
        pathStr,        /* file */
        0,              /* offset */
        0,              /* length (0 = todo el archivo) */
        0               /* flags: 0 = stream normal con salida de audio */
    );

    (*env)->ReleaseStringUTFChars(env, path, pathStr);

    if (handle == 0) {
        int err = BASS_ErrorGetCode();
        BASSGLUE_LOGE("BASS_StreamCreateFile failed, error code: %d", err);
    }

    return (jint)handle;
}

JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_channelFree(
    JNIEnv *env, jclass clazz, jint handle) {
    (void)env; (void)clazz;
    return BASS_ChannelFree((DWORD)handle) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_channelPlay(
    JNIEnv *env, jclass clazz, jint handle, jboolean restart) {
    (void)env; (void)clazz;
    return BASS_ChannelPlay((DWORD)handle, restart ? TRUE : FALSE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_channelPause(
    JNIEnv *env, jclass clazz, jint handle) {
    (void)env; (void)clazz;
    return BASS_ChannelPause((DWORD)handle) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_channelStop(
    JNIEnv *env, jclass clazz, jint handle) {
    (void)env; (void)clazz;
    return BASS_ChannelStop((DWORD)handle) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_openplayer_music_native_BassNative_channelSetPositionMs(
    JNIEnv *env, jclass clazz, jint handle, jlong positionMs) {
    (void)env; (void)clazz;
    double seconds = (double)positionMs / 1000.0;
    unsigned long long bytes = BASS_ChannelSeconds2Bytes((DWORD)handle, seconds);
    return BASS_ChannelSetPosition((DWORD)handle, bytes, BASS_POS_BYTE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_openplayer_music_native_BassNative_channelGetPositionMs(
    JNIEnv *env, jclass clazz, jint handle) {
    (void)env; (void)clazz;
    unsigned long long bytes = BASS_ChannelGetPosition((DWORD)handle, BASS_POS_BYTE);
    if (bytes == (unsigned long long)-1) {
        return -1LL;
    }
    double seconds = BASS_ChannelBytes2Seconds((DWORD)handle, bytes);
    return (jlong)(seconds * 1000.0);
}

JNIEXPORT jlong JNICALL
Java_com_openplayer_music_native_BassNative_channelGetLengthMs(
    JNIEnv *env, jclass clazz, jint handle) {
    (void)env; (void)clazz;
    unsigned long long bytes = BASS_ChannelGetLength((DWORD)handle, BASS_POS_BYTE);
    if (bytes == (unsigned long long)-1) {
        return -1LL;
    }
    double seconds = BASS_ChannelBytes2Seconds((DWORD)handle, bytes);
    return (jlong)(seconds * 1000.0);
}

JNIEXPORT jint JNICALL
Java_com_openplayer_music_native_BassNative_channelIsActive(
    JNIEnv *env, jclass clazz, jint handle) {
    (void)env; (void)clazz;
    return (jint)BASS_ChannelIsActive((DWORD)handle);
}

JNIEXPORT jint JNICALL
Java_com_openplayer_music_native_BassNative_errorGetCode(
    JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return (jint)BASS_ErrorGetCode();
}