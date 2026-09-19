package com.openplayer.music.data.model

/**
 * Modelo de dominio de una canción de la biblioteca.
 *
 * Los campos provienen de dos fuentes durante el escaneo:
 * - MediaStore: id, título/artista de respaldo, duración, path y dateAdded.
 * - TagLib (propertyMap + audioProperties): metadatos descriptivos,
 *   de numeración y técnicos.
 *
 * Todos los metadatos opcionales son nullable: si el archivo no
 * contiene el tag, el campo simplemente queda en null.
 */
data class Song(
    /** PrimaryKey, proviene de MediaStore.Audio.Media._ID. */
    val id: Long,
    val title: String,
    val artist: String,
    // ===== Descriptivos =====
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val composer: String?,
    val lyrics: String?,
    // ===== Numeración =====
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    // ===== Técnicos / sistema =====
    /** Duración en milisegundos. */
    val duration: Long,
    val path: String,
    /** Bitrate en kb/s. */
    val bitrate: Int?,
    /** Sample rate en Hz. */
    val sampleRate: Int?,
    /** Número de canales (1 = mono, 2 = stereo). */
    val channels: Int?,
    /** Timestamp epoch en segundos de cuando se agregó el archivo. */
    val dateAdded: Long
)