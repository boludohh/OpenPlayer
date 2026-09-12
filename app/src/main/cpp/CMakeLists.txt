cmake_minimum_required(VERSION 3.22.1)
project(openplayeraudio VERSION 0.1.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# ==========================================================================
# TagLib (librería precompilada - libtaglib.so)
# ==========================================================================
set(TAGLIB_SO_PATH "${CMAKE_CURRENT_SOURCE_DIR}/taglib/${ANDROID_ABI}/libtaglib.so")

if (NOT EXISTS "${TAGLIB_SO_PATH}")
    message(FATAL_ERROR
        "No se encontró libtaglib.so para ${ANDROID_ABI} en: ${TAGLIB_SO_PATH}\n"
        "Compilá el proyecto lib y copiá los .so a app/src/main/cpp/taglib/${ANDROID_ABI}/"
    )
endif()

add_library(taglib SHARED IMPORTED)
set_target_properties(taglib PROPERTIES
    IMPORTED_LOCATION "${TAGLIB_SO_PATH}"
)

# ==========================================================================
# BASS Audio Library (IMPORTED)
# ==========================================================================
set(BASS_SO_PATH "${CMAKE_CURRENT_SOURCE_DIR}/bass/${ANDROID_ABI}/libbass.so")

if (NOT EXISTS "${BASS_SO_PATH}")
    message(FATAL_ERROR
        "No se encontró libbass.so para ${ANDROID_ABI} en: ${BASS_SO_PATH}\n"
        "Descargá BASS 2.4.18.3 desde https://www.un4seen.com/bass.html y "
        "colocá el libbass.so correspondiente en app/src/main/cpp/bass/${ANDROID_ABI}/"
    )
endif()

add_library(bass SHARED IMPORTED)
set_target_properties(bass PROPERTIES
    IMPORTED_LOCATION "${BASS_SO_PATH}"
)

# Nuestra librería nativa
add_library(openplayeraudio SHARED
    openplayeraudio.cpp
    bassglue/bassglue.c
)

# ==========================================================================
# Headers de TagLib
# ==========================================================================
# Estructura: taglib-headers/taglib/...
# - taglib-headers/ permite includes como <taglib/fileref.h>
# - taglib-headers/taglib/ y subdirectorios permiten includes internos
# ==========================================================================
target_include_directories(openplayeraudio PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib-headers
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib-headers/taglib
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib-headers/taglib/toolkit
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib-headers/taglib/mpeg
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib-headers/taglib/mpeg/id3v1
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib-headers/taglib/mpeg/id3v2
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib-headers/taglib/mpeg/id3v2/frames
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib-headers/taglib/flac
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib-headers/taglib/ogg
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib-headers/taglib/ogg/vorbis
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib-headers/taglib/ogg/opus
    ${CMAKE_CURRENT_SOURCE_DIR}/taglib-headers/taglib/mp4
    ${CMAKE_CURRENT_SOURCE_DIR}/bassglue
)

# Buscar zlib del NDK (requerido por TagLib en runtime)
find_library(ZLIB_LIB z)

# Buscar liblog de Android (para __android_log_print en bassglue.c)
find_library(LOG_LIB log)

# Enlazar TagLib precompilado, zlib, log y BASS
target_link_libraries(openplayeraudio PRIVATE
    taglib
    ${ZLIB_LIB}
    ${LOG_LIB}
    bass
)

# Flags de compilación
target_compile_options(openplayeraudio PRIVATE
    -Wall
    -Wextra
    -fvisibility=hidden
)