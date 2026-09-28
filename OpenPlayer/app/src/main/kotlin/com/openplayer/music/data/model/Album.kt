package com.openplayer.music.data.model

/**
 * Modelo de dominio de un álbum, derivado de agrupar canciones por
 * `(album, albumArtist)` en [com.openplayer.music.ui.screens.albums.AlbumsScreen].
 *
 * ## Clave de agrupación
 * La [key] es una cadena compuesta `"${album}|${albumArtist}"` que
 * garantiza unicidad incluso cuando dos álbumes de distintos artistas
 * comparten el mismo nombre. Para canciones sin campo `album`, la
 * clave se construye con el `artist` como fallback (canciones sueltas
 * aparecen agrupadas por artista).
 *
 * ## Carátula del álbum
 * El [coverPath] apunta al `path` de la primera canción del grupo
 * que tenga carátula en disco (verificada vía
 * [com.openplayer.music.data.media.CoverRepository.coverFile]). Si
 * ninguna canción del álbum tiene carátula, [coverPath] es `null` y
 * la UI muestra un placeholder.
 *
 * ## Campos
 * - [key]: identificador único del álbum (para `key` en LazyGrid).
 * - [title]: nombre del álbum (o nombre del artista si no hay campo
 *   `album` en las canciones).
 * - [artist]: artista del álbum (`albumArtist` si existe en al menos
 *   una canción, fallback al `artist` de la primera canción).
 * - [year]: año de lanzamiento (del primer valor no-null encontrado
 *   en las canciones del grupo).
 * - [trackCount]: cantidad de pistas en el álbum.
 * - [coverPath]: path de la canción representativa para obtener la
 *   carátula; `null` si el álbum no tiene carátula disponible.
 */
data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val year: Int?,
    val trackCount: Int,
    val coverPath: String?
)