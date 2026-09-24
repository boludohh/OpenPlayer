package com.openplayer.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO de la tabla "songs".
 *
 * Trabaja con [SongEntity]; el mapeo al modelo de dominio [com.openplayer.music.data.model.Song]
 * lo realiza el repositorio (capa superior), manteniendo el DAO
 * limitado al acceso a datos.
 */
@Dao
interface SongDao {

    /**
     * Inserta un lote de canciones. Con REPLACE, un re-escaneo
     * actualiza las filas existentes por id sin duplicarlas.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(songs: List<SongEntity>)

    /** Todas las canciones ordenadas por título (reactivo). */
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAll(): Flow<List<SongEntity>>

    /** Todas las canciones ordenadas por fecha de agregada descendente (reactivo). */
    @Query("SELECT * FROM songs ORDER BY dateAdded DESC")
    fun getAllByDateAdded(): Flow<List<SongEntity>>

    /** Una canción por su id de MediaStore; null si no existe. */
    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    /**
     * Varias canciones por sus ids de MediaStore. Usado por
     * [com.openplayer.music.data.media.PlaylistRepository] para
     * resolver las canciones de una playlist. El orden de la
     * respuesta NO está garantizado; el ordenamiento por posición
     * lo hace el repositorio.
     */
    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongEntity>

    /** Cantidad de canciones; sirve para detectar "Room vacío". */
    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int

    /** Vacía la tabla (reconstrucción completa del caché). */
    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    /**
     * Borra canciones por sus ids. Usado por el repositorio para
     * eliminar del caché las filas cuyo archivo ya no existe en
     * disco (detección de eliminaciones externas).
     */
    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}