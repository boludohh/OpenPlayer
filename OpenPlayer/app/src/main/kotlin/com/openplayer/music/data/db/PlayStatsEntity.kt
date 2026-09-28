package com.openplayer.music.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad Room para la tabla "play_stats".
 *
 * Almacena estadísticas de reproducción por canción: conteo de
 * reproducciones iniciadas, conteo de reproducciones completadas,
 * tiempo total escuchado en milisegundos, y timestamp de la última
 * reproducción.
 *
 * ## Datos de actividad del usuario
 * A diferencia de "songs" y "artists" (cachés reconstruibles), esta
 * tabla contiene DATOS DEL USUARIO (su historial de actividad). La
 * migración 6→7 es REAL (no destructiva): las estadísticas se
 * preservan entre versiones de la app.
 *
 * ## Semántica de campos
 * - [songId]: FK hacia "songs" con CASCADE on delete. Si una canción
 *   se elimina de la biblioteca, sus estadísticas se borran
 *   automáticamente.
 * - [playCount]: cantidad de veces que la canción se empezó a
 *   reproducir (cada inicio de reproducción = +1).
 * - [completedCount]: cantidad de veces que la canción se reprodujo
 *   completamente (solo STATE_ENDED = +1).
 * - [playedMs]: milisegundos acumulados escuchados (completas o no).
 * - [lastPlayedAt]: timestamp epoch en milisegundos de la última
 *   reproducción; usado para ordenar recientes.
 */
@Entity(
    tableName = "play_stats",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["lastPlayedAt"])]
)
data class PlayStatsEntity(
    @PrimaryKey val songId: Long,
    val playCount: Int,
    val completedCount: Int,
    val playedMs: Long,
    val lastPlayedAt: Long
)