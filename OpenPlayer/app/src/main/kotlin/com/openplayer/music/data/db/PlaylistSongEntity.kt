package com.openplayer.music.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Entidad Room para la tabla de relación "playlist_songs".
 *
 * Mapea la relación N:M entre playlists y canciones con un campo
 * [position] que mantiene el orden de inserción (o el reordenado
 * manual del usuario).
 *
 * ## Diseño de claves e índices
 * - **PK compuesta** `(playlistId, songId)`: garantiza que la misma
 *   canción no esté dos veces en la misma playlist (validación a
 *   nivel de esquema, reforzada en [com.openplayer.music.data.media.PlaylistRepository]).
 * - **FK hacia "playlists"** con CASCADE on delete: eliminar una
 *   playlist borra automáticamente sus filas de relación.
 * - **FK hacia "songs" con CASCADE on delete**: si una canción se
 *   elimina de la biblioteca (archivo borrado del disco), se borra
 *   también de todas las playlists que la contenían (consistencia
 *   sin playlists con referencias rotas).
 * - **Índice sobre (playlistId, position)**: permite ordenar las
 *   canciones de una playlist por posición en una sola consulta.
 *
 * ## Datos de usuario
 * Al igual que [PlaylistEntity], esta tabla contiene datos creados
 * por el usuario. Las migraciones futuras deben preservar su contenido.
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId", "position"]),
        Index(value = ["songId"])
    ]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int
)