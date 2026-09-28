package com.openplayer.music.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad Room para la tabla "playlists".
 *
 * A diferencia de "songs" y "artists", esta tabla contiene DATOS DEL
 * USUARIO creados explícitamente; no es un caché reconstruible. Por
 * eso la migración 4→5 en [AppDatabase] es REAL (no destructiva) y
 * futuras migraciones sobre esta tabla deberán preservar los datos
 * del usuario.
 *
 * ## Semántica de campos
 * - [id]: autoincrement, PK estable de Room.
 * - [name]: nombre visible de la playlist; índice UNIQUE garantiza
 *   que no existan dos playlists con el mismo nombre (validación a
 *   nivel de esquema, reforzada también en [com.openplayer.music.data.media.PlaylistRepository]).
 * - [createdAt]: timestamp epoch en segundos de creación.
 * - [updatedAt]: timestamp epoch en segundos de la última modificación
 *   (renombrado, reordenado, adición/eliminación de canciones).
 *
 * El orden de las canciones dentro de la playlist NO vive aquí: se
 * mantiene en [PlaylistSongEntity.position].
 */
@Entity(
    tableName = "playlists",
    indices = [Index(value = ["name"], unique = true)]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)