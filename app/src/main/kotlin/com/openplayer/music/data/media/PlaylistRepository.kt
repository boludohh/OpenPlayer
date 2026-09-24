package com.openplayer.music.data.media

import com.openplayer.music.data.db.AppDatabase
import com.openplayer.music.data.db.PlaylistDao
import com.openplayer.music.data.db.PlaylistEntity
import com.openplayer.music.data.db.PlaylistSongEntity
import com.openplayer.music.data.db.SongDao
import com.openplayer.music.data.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repositorio de playlists del usuario: orquesta el acceso a datos de
 * [PlaylistDao] y [SongDao], aplica validaciones de integridad y
 * expone un Flow reactivo de [Playlist] listo para consumir desde UI.
 *
 * ## Datos de usuario
 * Las playlists son DATOS DEL USUARIO (no caché reconstruible). El
 * repositorio es la capa responsable de:
 * - Validar nombres únicos antes de insertar o renombrar.
 * - Validar que una canción no esté dos veces en la misma playlist.
 * - Mantener el orden de las canciones dentro de cada playlist
 *   mediante el campo `position` de [PlaylistSongEntity].
 * - Actualizar el timestamp `updatedAt` en cada modificación.
 *
 * ## Flow reactivo de playlists
 * [playlists] combina:
 * - `PlaylistDao.getAllPlaylists()` — lista de playlists.
 * - `PlaylistDao.getSongCounts()` — conteos por playlistId.
 *
 * Cualquier cambio en playlists o en sus canciones dispara una nueva
 * emisión del Flow, por lo que la UI no necesita refrescar manualmente.
 *
 * ## Operaciones
 * - [createPlaylist]: inserta una playlist nueva (valida nombre único).
 * - [renamePlaylist]: cambia el nombre (valida nombre único excluyendo
 *   el id actual).
 * - [deletePlaylist]: elimina la playlist (CASCADE borra playlist_songs).
 * - [addSong]: añade una canción al final (valida duplicados).
 * - [removeSong]: quita una canción y renumera posiciones.
 * - [reorder]: reorganiza la lista completa de canciones según el
 *   orden de ids dado (útil para drag & drop).
 *
 * Todas las operaciones son suspend y lanzan [IllegalArgumentException]
 * si la validación falla (nombre duplicado, canción ya presente, etc.);
 * la UI debe capturarlas y mostrar el mensaje correspondiente.
 */
class PlaylistRepository(
    private val appDatabase: AppDatabase
) {
    private val playlistDao: PlaylistDao = appDatabase.playlistDao()
    private val songDao: SongDao = appDatabase.songDao()

    /**
     * Flow reactivo de todas las playlists con su contador de
     * canciones, ordenadas por fecha de creación descendente.
     *
     * Se re-emite automáticamente cuando:
     * - Se crea, renombra o elimina una playlist.
     * - Se añade, quita o reordena una canción dentro de cualquier
     *   playlist.
     */
    val playlists: Flow<List<Playlist>> = combine(
        playlistDao.getAllPlaylists(),
        playlistDao.getSongCounts()
    ) { entities, counts ->
        val countMap = counts.associate { it.playlistId to it.count }
        entities.map { entity ->
            Playlist.fromEntity(entity, countMap[entity.id] ?: 0)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Búsqueda reactiva de playlists por coincidencia parcial en el
     * nombre. Usado por la pantalla de búsqueda global.
     *
     * @param query Texto de búsqueda (sin wildcards; se añaden aquí).
     * @return Flow reactivo que se re-emite cuando cambia la query
     *         o cuando la tabla playlists cambia.
     */
    fun searchPlaylists(query: String): Flow<List<Playlist>> {
        val pattern = "%${query.trim()}%"
        return combine(
            playlistDao.searchPlaylists(pattern),
            playlistDao.getSongCounts()
        ) { entities, counts ->
            val countMap = counts.associate { it.playlistId to it.count }
            entities.map { entity ->
                Playlist.fromEntity(entity, countMap[entity.id] ?: 0)
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Crea una playlist nueva con el nombre dado.
     *
     * @throws IllegalArgumentException si ya existe una playlist con
     *         el mismo nombre (validación reforzada por el UNIQUE de
     *         la tabla "playlists").
     */
    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "El nombre de la playlist no puede estar vacío" }
        require(!playlistDao.existsByName(trimmed)) {
            "Ya existe una playlist con el nombre '$trimmed'"
        }
        val now = System.currentTimeMillis() / 1000
        val entity = PlaylistEntity(
            name = trimmed,
            createdAt = now,
            updatedAt = now
        )
        playlistDao.insertPlaylist(entity)
    }

    /**
     * Renombra una playlist existente.
     *
     * @throws IllegalArgumentException si el nuevo nombre ya existe
     *         en otra playlist.
     */
    suspend fun renamePlaylist(id: Long, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "El nombre de la playlist no puede estar vacío" }
        require(!playlistDao.existsByNameExcluding(trimmed, id)) {
            "Ya existe una playlist con el nombre '$trimmed'"
        }
        val current = playlistDao.getPlaylistById(id)
            ?: throw IllegalArgumentException("La playlist con id=$id no existe")
        val now = System.currentTimeMillis() / 1000
        playlistDao.updatePlaylist(
            current.copy(name = trimmed, updatedAt = now)
        )
    }

    /** Elimina una playlist; sus canciones se borran por CASCADE. */
    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        val current = playlistDao.getPlaylistById(id) ?: return@withContext
        playlistDao.deletePlaylist(current)
    }

    /**
     * Añade una canción al final de la playlist.
     *
     * @throws IllegalArgumentException si la canción ya está en la
     *         playlist (validación reforzada por la PK compuesta).
     */
    suspend fun addSong(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        require(!playlistDao.containsSong(playlistId, songId)) {
            "La canción ya está en esta playlist"
        }
        val currentIds = playlistDao.getSongIdsInPlaylist(playlistId)
        val nextPosition = currentIds.size
        playlistDao.insertSongInPlaylist(
            PlaylistSongEntity(playlistId, songId, nextPosition)
        )
        // Actualizar updatedAt de la playlist
        val current = playlistDao.getPlaylistById(playlistId)
        if (current != null) {
            val now = System.currentTimeMillis() / 1000
            playlistDao.updatePlaylist(current.copy(updatedAt = now))
        }
    }

    /**
     * Quita una canción de la playlist y renumera las posiciones
     * siguientes para mantener continuidad (sin huecos).
     */
    suspend fun removeSong(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        // Obtener la posición actual antes de borrar para renumerar
        val currentIds = playlistDao.getSongIdsInPlaylist(playlistId)
        val index = currentIds.indexOf(songId)
        if (index < 0) return@withContext
        playlistDao.removeSongFromPlaylist(playlistId, songId)
        // Renumerar posiciones posteriores
        for (i in (index + 1) until currentIds.size) {
            val sid = currentIds[i]
            playlistDao.updateSongPosition(
                PlaylistSongEntity(playlistId, sid, i - 1)
            )
        }
        // Actualizar updatedAt
        val current = playlistDao.getPlaylistById(playlistId)
        if (current != null) {
            val now = System.currentTimeMillis() / 1000
            playlistDao.updatePlaylist(current.copy(updatedAt = now))
        }
    }

    /**
     * Reordena las canciones de la playlist según el orden de ids
     * dado. Útil para drag & drop desde la UI: la UI envía la lista
     * de ids en el nuevo orden y el repositorio actualiza `position`
     * de cada fila.
     *
     * @param orderedSongIds Lista de ids de canciones en el orden
     *                       deseado. Debe contener exactamente las
     *                       canciones actualmente en la playlist.
     * @throws IllegalArgumentException si la lista no coincide con
     *         las canciones actuales de la playlist.
     */
    suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>) =
        withContext(Dispatchers.IO) {
            val currentIds = playlistDao.getSongIdsInPlaylist(playlistId).toSet()
            require(orderedSongIds.toSet() == currentIds) {
                "La lista de ids no coincide con las canciones actuales de la playlist"
            }
            orderedSongIds.forEachIndexed { newIndex, songId ->
                playlistDao.updateSongPosition(
                    PlaylistSongEntity(playlistId, songId, newIndex)
                )
            }
            // Actualizar updatedAt
            val current = playlistDao.getPlaylistById(playlistId)
            if (current != null) {
                val now = System.currentTimeMillis() / 1000
                playlistDao.updatePlaylist(current.copy(updatedAt = now))
            }
        }

    /**
     * Devuelve las canciones de una playlist ordenadas por posición,
     * ya resueltas a [com.openplayer.music.data.model.Song]. Si una
     * canción fue eliminada de la biblioteca (FK CASCADE la borró de
     * playlist_songs), simplemente no aparece.
     */
    suspend fun getSongsInPlaylist(playlistId: Long): List<com.openplayer.music.data.model.Song> =
        withContext(Dispatchers.IO) {
            val songIds = playlistDao.getSongIdsInPlaylist(playlistId)
            if (songIds.isEmpty()) return@withContext emptyList()
            val entities = songDao.getByIds(songIds)
            // Preservar el orden de la playlist (getByIds no lo garantiza)
            val byId = entities.associateBy { it.id }
            songIds.mapNotNull { id -> byId[id]?.toSong() }
        }
}