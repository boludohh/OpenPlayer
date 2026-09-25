package com.openplayer.music.data.media

import com.openplayer.music.data.db.AppDatabase
import com.openplayer.music.data.db.PlayStatsDao
import com.openplayer.music.data.db.SongDao
import com.openplayer.music.data.db.TotalStats
import com.openplayer.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repositorio de historial de reproducción: orquesta el registro de
 * eventos de reproducción y expone Flows reactivos de estadísticas
 * agregadas, top artista/pista y canciones recientes.
 *
 * ## Datos de actividad del usuario
 * Las estadísticas de reproducción son DATOS DEL USUARIO (no caché
 * reconstruible). La migración 6→7 de AppDatabase preserva esta
 * tabla entre versiones de la app.
 *
 * ## Lógica de sesión
 * El repositorio mantiene una sesión en memoria (songId + posición
 * inicial + timestamp de inicio) para calcular el delta de tiempo
 * escuchado. Al pausar, seek, cambiar de canción o terminar la
 * reproducción, se calcula el delta y se acumula en playedMs.
 *
 * ## Flows reactivos
 * - [totalStats]: suma de playCount, completedCount y playedMs.
 * - [topTrack]: canción con más playCount.
 * - [topArtist]: artista con más playCount acumulado.
 * - [recentlyPlayed]: últimas N canciones reproducidas.
 *
 * Todos los Flows se re-emiten automáticamente cuando play_stats
 * cambia, por lo que la UI no necesita refrescar manualmente.
 */
class PlaybackHistoryRepository(
    private val appDatabase: AppDatabase
) {
    private val playStatsDao: PlayStatsDao = appDatabase.playStatsDao()
    private val songDao: SongDao = appDatabase.songDao()

    // =========================================================================
    // Sesión en memoria
    // =========================================================================

    /** Sesión activa: songId, posición al iniciar, timestamp de inicio. */
    private var currentSession: PlaybackSession? = null

    private data class PlaybackSession(
        val songId: Long,
        val startPositionMs: Long,
        val startTimeMs: Long
    )

    // =========================================================================
    // API pública: registro de eventos
    // =========================================================================

    /**
     * Registra el inicio de reproducción de una canción.
     * Incrementa playCount en 1 y actualiza lastPlayedAt.
     * Inicia una nueva sesión para tracking de tiempo.
     *
     * @param songId ID de la canción (MediaStore._ID).
     * @param startPositionMs Posición de inicio en milisegundos
     *        (normalmente 0, pero puede ser > 0 si se reanuda).
     */
    suspend fun recordPlayStart(songId: Long, startPositionMs: Long = 0L) {
        val now = System.currentTimeMillis()
        playStatsDao.incrementPlayCount(songId, now)
        currentSession = PlaybackSession(songId, startPositionMs, now)
    }

    /**
     * Registra que la canción se reprodujo completamente (STATE_ENDED).
     * Incrementa completedCount en 1.
     *
     * @param songId ID de la canción.
     */
    suspend fun recordCompleted(songId: Long) {
        playStatsDao.incrementCompletedCount(songId)
    }

    /**
     * Finaliza la sesión actual y acumula el delta de tiempo escuchado
     * en playedMs. Se llama al pausar, seek, cambiar de canción o
     * terminar la reproducción.
     *
     * @param currentPositionMs Posición actual de reproducción en
     *        milisegundos (desde el inicio de la canción).
     */
    suspend fun flushSession(currentPositionMs: Long) {
        val session = currentSession ?: return
        val deltaMs = currentPositionMs - session.startPositionMs
        if (deltaMs > 0) {
            playStatsDao.addPlayedMs(session.songId, deltaMs)
        }
        currentSession = null
    }

    // =========================================================================
    // API pública: Flows reactivos
    // =========================================================================

    /**
     * Estadísticas agregadas totales: suma de playCount, completedCount
     * y playedMs de todas las canciones. Se re-emite cuando play_stats
     * cambia.
     */
    val totalStats: Flow<TotalStats> = playStatsDao.getTotalStats()
        .flowOn(Dispatchers.IO)

    /**
     * Canción más reproducida (por playCount), resuelta a [Song].
     * Si no hay estadísticas, emite null.
     */
    val topTrack: Flow<Song?> = playStatsDao.getTopTrackId()
        .map { songId ->
            songId?.let { songDao.getById(it)?.toSong() }
        }
        .flowOn(Dispatchers.IO)

    /**
     * Artista más reproducido (por suma de playCount de sus canciones).
     * Si no hay estadísticas, emite null.
     */
    val topArtist: Flow<String?> = playStatsDao.getTopArtist()
        .flowOn(Dispatchers.IO)

    /**
     * Últimas [limit] canciones reproducidas, ordenadas por
     * lastPlayedAt descendente. Si no hay estadísticas, emite lista
     * vacía.
     */
    fun getRecentlyPlayed(limit: Int = 5): Flow<List<Song>> =
        playStatsDao.getRecentlyPlayedIds(limit)
            .map { ids ->
                if (ids.isEmpty()) return@map emptyList()
                val entities = songDao.getByIds(ids)
                val byId = entities.associateBy { it.id }
                ids.mapNotNull { byId[it]?.toSong() }
            }
            .flowOn(Dispatchers.IO)
}