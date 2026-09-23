package com.openplayer.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO de la tabla "artists" (caché reconstruible del enriquecimiento
 * remoto MusicBrainz → Fanart.tv). Limitado al acceso a datos; la
 * orquestación vive en
 * [com.openplayer.music.data.media.ArtistImageRepository].
 */
@Dao
interface ArtistDao {

    /** Inserta o actualiza artistas por nombre (REPLACE). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(artists: List<ArtistEntity>)

    /** Un artista por nombre; null si nunca fue enriquecido. */
    @Query("SELECT * FROM artists WHERE name = :name")
    suspend fun getByName(name: String): ArtistEntity?

    /** Todos los artistas enriquecidos (no reactivo), para el arranque del enriquecido. */
    @Query("SELECT * FROM artists")
    suspend fun getAllOnce(): List<ArtistEntity>

    /** Todos los artistas enriquecidos (reactivo), para UI y mapeo de disco. */
    @Query("SELECT * FROM artists")
    fun getAll(): Flow<List<ArtistEntity>>
}