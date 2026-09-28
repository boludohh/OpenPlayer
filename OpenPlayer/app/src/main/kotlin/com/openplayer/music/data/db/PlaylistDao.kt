package com.openplayer.music.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO de las tablas "playlists" y "playlist_songs".
 *
 * Trabaja con [PlaylistEntity] y [PlaylistSongEntity]; el mapeo al
 * modelo de dominio [com.openplayer.music.data.model.Playlist] lo
 * realiza el repositorio (capa superior), manteniendo el DAO limitado
 * al acceso a datos.
 *
 * ## Datos de usuario
 * Todas las operaciones aquí actúan sobre datos creados por el usuario.
 * El repositorio se encarga de las validaciones (nombres únicos,
 * canciones no duplicadas) antes de llamar a estos métodos.
 */
@Dao
interface PlaylistDao {

    // ===== Playlists =====

    /** Inserta una playlist nueva y devuelve su id autoincrement. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    /** Actualiza una playlist existente (nombre, updatedAt). */
    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    /** Elimina una playlist; las filas de playlist_songs se borran por CASCADE. */
    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    /** Todas las playlists ordenadas por fecha de creación descendente (reactivo). */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    /** Una playlist por su id; null si no existe. */
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    /**
     * Búsqueda de playlists por coincidencia parcial (LIKE) en el nombre.
     * Case-insensitive por defecto en SQLite. Usado por la pantalla
     * de búsqueda global.
     *
     * @param query Patrón de búsqueda con wildcards (%query%).
     */
    @Query(
        """
        SELECT * FROM playlists 
        WHERE name LIKE :query
        ORDER BY createdAt DESC
        LIMIT 20
        """
    )
    fun searchPlaylists(query: String): Flow<List<PlaylistEntity>>

    /** Verifica si existe una playlist con el nombre dado. */
    @Query("SELECT EXISTS(SELECT 1 FROM playlists WHERE name = :name)")
    suspend fun existsByName(name: String): Boolean

    /** Verifica si existe una playlist con el nombre dado, excluyendo un id específico (para renombrar). */
    @Query("SELECT EXISTS(SELECT 1 FROM playlists WHERE name = :name AND id != :excludeId)")
    suspend fun existsByNameExcluding(name: String, excludeId: Long): Boolean

    /** Cuenta canciones dentro de una playlist (para el contador de la UI). */
    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun countSongsInPlaylist(playlistId: Long): Int

    /**
     * Mapa reactivo playlistId → contador de canciones. Se re-evalúa
     * cada vez que playlist_songs cambia (agregar, quitar, reordenar).
     */
    @Query("SELECT playlistId, COUNT(*) AS count FROM playlist_songs GROUP BY playlistId")
    fun getSongCounts(): Flow<List<PlaylistSongCount>>

    // ===== Canciones dentro de playlists =====

    /** Inserta una canción en una playlist en la posición dada. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSongInPlaylist(row: PlaylistSongEntity)

    /** Quita una canción específica de una playlist. */
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    /** Quita todas las canciones de una playlist (no borra la playlist). */
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    /** Reemplaza la posición de una relación playlist↔canción (para reordenar). */
    @Update
    suspend fun updateSongPosition(row: PlaylistSongEntity)

    /**
     * IDs de canciones de una playlist ordenados por posición.
     * El repositorio resuelve cada id a [com.openplayer.music.data.model.Song]
     * usando [SongDao.getByIds].
     */
    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getSongIdsInPlaylist(playlistId: Long): List<Long>

    /** Verifica si una canción ya está en una playlist dada. */
    @Query("SELECT EXISTS(SELECT 1 FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId)")
    suspend fun containsSong(playlistId: Long, songId: Long): Boolean
}

/**
 * DTO de Room para el conteo de canciones por playlist (resultado
 * de [PlaylistDao.getSongCounts]).
 */
data class PlaylistSongCount(
    val playlistId: Long,
    val count: Int
)