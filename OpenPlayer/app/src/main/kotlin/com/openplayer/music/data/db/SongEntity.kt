package com.openplayer.music.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.openplayer.music.data.model.Song

/**
 * Entidad de Room para la tabla "songs".
 *
 * Es un CACHÉ RECONSTRUIBLE del escaneo de MediaStore + TagLib,
 * no datos creados por el usuario; por eso la base de datos usa
 * migración destructiva y esta entidad no requiere migraciones
 * reales (ver AppDatabase).
 *
 * Índice en [title] porque la biblioteca se ordena por título.
 */
@Entity(
    tableName = "songs",
    indices = [Index(value = ["title"])]
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val composer: String?,
    val lyrics: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val duration: Long,
    val path: String,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channels: Int?,
    /** Timestamp epoch en segundos de cuando se agregó el archivo (MediaStore DATE_ADDED). */
    val dateAdded: Long
) {

    /** Mapea la entidad de base de datos al modelo de dominio. */
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        genre = genre,
        composer = composer,
        lyrics = lyrics,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        duration = duration,
        path = path,
        bitrate = bitrate,
        sampleRate = sampleRate,
        channels = channels,
        dateAdded = dateAdded
    )
}