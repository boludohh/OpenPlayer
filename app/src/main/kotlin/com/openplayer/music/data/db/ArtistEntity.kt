package com.openplayer.music.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de Room para la tabla "artists".
 *
 * Al igual que "songs", es un CACHÉ RECONSTRUIBLE: guarda el resultado
 * del enriquecimiento remoto (MBID de MusicBrainz + URL de imagen de
 * Fanart.tv) para no repetir peticiones de red. No contiene datos
 * creados por el usuario, por lo que queda cubierta por la migración
 * destructiva de la base de datos (ver AppDatabase).
 *
 * Semántica de [mbid]:
 * - `null`  → artista aún no enriquecido (nunca se consultó MusicBrainz).
 * - `""`    → resultado negativo: MusicBrainz no devolvió candidatos
 *              (no se vuelve a consultar, por rate limiting).
 * - otro    → MBID real resuelto.
 *
 * [thumbUrl] es la mejor imagen elegida de Fanart.tv (artistthumb con
 * más likes); `null` si el artista no tiene imágenes en el servicio.
 * Los bytes de la imagen se descargan a `filesDir/artist_images/`
 * con nombre MD5(name) + extensión detectada por magic bytes.
 */
@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val name: String,
    val mbid: String?,
    val disambiguation: String?,
    val thumbUrl: String?,
    /** Timestamp epoch en segundos de la última resolución remota. */
    val updatedAt: Long
)