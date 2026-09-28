package com.openplayer.music.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de Room para la tabla "artists".
 *
 * Al igual que "songs", es un CACHÉ RECONSTRUIBLE: guarda el resultado
 * del enriquecimiento remoto (ID de Deezer + URL de imagen) para no
 * repetir peticiones de red. No contiene datos creados por el usuario;
 * la migración 5→6 de AppDatabase reconstruye esta tabla sin tocar
 * las playlists del usuario.
 *
 * Semántica de [deezerId]:
 * - `[com.openplayer.music.data.media.ArtistImageRepository.NOT_FOUND_ID]`
 *   (-1) → resultado negativo: Deezer no devolvió candidatos confiables
 *   (no se vuelve a consultar).
 * - > 0  → ID real del artista en Deezer.
 *
 * [imageUrl] es la URL `picture_big` (500×500) del candidato elegido por
 * el algoritmo de 4 pasos de DeezerClient; `null` si el candidato
 * confiable no tenía imagen. Los bytes se descargan a
 * `filesDir/artist_images/` con nombre MD5(name) + extensión detectada
 * por magic bytes.
 */
@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val name: String,
    val deezerId: Long,
    val imageUrl: String?,
    /** Timestamp epoch en segundos de la última resolución remota. */
    val updatedAt: Long
)