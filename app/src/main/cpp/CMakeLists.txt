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
# BASS Audio Library y add-ons (solo empaquetado, no enlace)
# Los .so se empaquetan automáticamente por AGP al estar en jniLibs
# o en el directorio de CMake. No se enlazan porque BASS ahora se usa
# desde los wrappers oficiales de Java (BASS.java, BASSmix.java).
# ==========================================================================

# Nuestra librería nativa (solo TagLib, sin bassglue)
add_library(openplayeraudio SHARED
    openplayeraudio.cpp
)

# ==========================================================================
# Headers de TagLib
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
)

# Buscar zlib del NDK (requerido por TagLib en runtime)
find_library(ZLIB_LIB z)

# Buscar liblog de Android (para __android_log_print)
find_library(LOG_LIB log)

# Enlazar solo TagLib precompilado, zlib y log (sin bass)
target_link_libraries(openplayeraudio PRIVATE
    taglib
    ${ZLIB_LIB}
    ${LOG_LIB}
)

# Flags de compilación
target_compile_options(openplayeraudio PRIVATE
    -Wall
    -Wextra
    -fvisibility=hidden
)