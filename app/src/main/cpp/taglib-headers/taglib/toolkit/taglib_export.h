/* taglib_export.h
 * Generado manualmente para NokliPlayer.
 * Define los macros necesarios para consumir libtaglib.so precompilado.
 */

#ifndef TAGLIB_EXPORT_H
#define TAGLIB_EXPORT_H

/* Al consumir la librería precompilada (no al buildarla),
 * TAGLIB_EXPORT no necesita atributos de visibilidad. */
#define TAGLIB_EXPORT

/* Macro usado por TagLib para suprimir warnings de MSVC (Visual Studio)
 * sobre clases exportadas con miembros std::unique_ptr/std::shared_ptr.
 * En Android/Clang no aplica, se define como vacío. */
#define TAGLIB_MSVC_SUPPRESS_WARNING_NEEDS_TO_HAVE_DLL_INTERFACE

#endif /* TAGLIB_EXPORT_H */