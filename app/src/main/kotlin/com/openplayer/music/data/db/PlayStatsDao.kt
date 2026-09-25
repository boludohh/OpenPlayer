package com.openplayer.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO de la tabla "play_stats".
 *
 * Proporciona operaciones de actualización atómica (INSERT OR UPDATE
 * con incrementos) y queries de agregación para el repositorio de
 * historial de reproducción.
 */
@Dao
interface PlayStatsDao {

    /** Inserta o reemplaza una fila completa de estadísticas. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: PlayStatsEntity)

    /**
     * Incrementa playCount en 1 y actualiza lastPlayedAt.
     * Si la fila no existe, la crea con playCount=1 y el resto en 0.
     */
    @Query(
        """
        INSERT INTO play_stats (songId, playCount, completedCount, playedMs, lastPlayedAt)
        VALUES (:songId, 1, 0, 0, :timestamp)
        ON CONFLICT(songId) DO UPDATE SET
            playCount = playCount + 1,
            lastPlayedAt = :timestamp
        """
    )
    suspend fun incrementPlayCount(songId: Long, timestamp: Long)

    /**
     * Incrementa completedCount en 1.
     * Si la fila no existe, la crea con completedCount=1 y el resto en 0.
     */
    @Query(
        """
        INSERT INTO play_stats (songId, playCount, completedCount, playedMs, lastPlayedAt)
        VALUES (:songId, 0, 1, 0, 0)
        ON CONFLICT(songId) DO UPDATE SET
            completedCount = completedCount + 1
        """
    )
    suspend fun incrementCompletedCount(songId: Long)

    /**
     * Añade deltaMs a playedMs.
     * Si la fila no existe, la crea con playedMs=deltaMs y el resto en 0.
     */
    @Query(
        """
        INSERT INTO play_stats (songId, playCount, completedCount, playedMs, lastPlayedAt)
        VALUES (:songId, 0, 0, :deltaMs, 0)
        ON CONFLICT(songId) DO UPDATE SET
            playedMs = playedMs + :deltaMs
        """
    )
    suspend fun addPlayedMs(songId: Long, deltaMs: Long)

    /**
     * Estadísticas agregadas totales: suma de playCount, completedCount
     * y playedMs de todas las canciones.
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(playCount), 0) AS totalPlays,
            COALESCE(SUM(completedCount), 0) AS totalCompleted,
            COALESCE(SUM(playedMs), 0) AS totalPlayedMs
        FROM play_stats
        """
    )
    fun getTotalStats(): Flow<TotalStats>

    /** Canción más reproducida (por playCount). */
    @Query(
        """
        SELECT songId FROM play_stats
        ORDER BY playCount DESC
        LIMIT 1
        """
    )
    fun getTopTrackId(): Flow<Long?>

    /**
     * Artista más reproducido: JOIN con songs, GROUP BY artist,
     * ORDER BY SUM(playCount) DESC.
     */
    @Query(
        """
        SELECT s.artist
        FROM play_stats ps
        INNER JOIN songs s ON ps.songId = s.id
        GROUP BY s.artist
        ORDER BY SUM(ps.playCount) DESC
        LIMIT 1
        """
    )
    fun getTopArtist(): Flow<String?>

    /**
     * Últimas N canciones reproducidas, ordenadas por lastPlayedAt
     * descendente. Devuelve solo los songIds; el repositorio resuelve
     * cada id a Song usando SongDao.getByIds.
     */
    @Query(
        """
        SELECT songId FROM play_stats
        WHERE lastPlayedAt > 0
        ORDER BY lastPlayedAt DESC
        LIMIT :limit
        """
    )
    fun getRecentlyPlayedIds(limit: Int): Flow<List<Long>>
}

/**
 * DTO de Room para estadísticas agregadas totales (resultado de
 * [PlayStatsDao.getTotalStats]).
 */
data class TotalStats(
    val totalPlays: Int,
    val totalCompleted: Int,
    val totalPlayedMs: Long
)