package com.openplayer.music.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.openplayer.music.data.media.CoverRepository
import com.openplayer.music.data.model.Song
import java.io.File

/**
 * Conversión de modelos de dominio [Song] a [MediaItem] de Media3.
 *
 * El MediaItem lleva:
 * - `mediaId`: el id de MediaStore de la canción (clave estable).
 * - `uri`: el path local del archivo de audio.
 * - `mediaMetadata`: título, artista, álbum y `artworkUri`.
 *
 * El `artworkUri` se fija **sólo si la portada ya existe en disco**
 * (la extracción se hizo durante el escaneo en [AudioRepository]).
 * Esta función es síncrona y rápida: no realiza I/O pesado ni
 * extracción bajo demanda. Si la canción no tiene portada, la
 * notificación simplemente no muestra imagen, evitando los warnings
 * de `FileNotFoundException` del NotificationProvider.
 */
fun Song.toMediaItem(
    coverRepository: CoverRepository
): MediaItem {
    // coverFile() es un stat sincrónico de hasta 4 archivos candidatos;
    // no extrae ni decodifica nada.
    val artworkUri = coverRepository.coverFile(path)?.let { Uri.fromFile(it) }

    val metadataBuilder = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)

    artworkUri?.let { metadataBuilder.setArtworkUri(it) }

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.fromFile(File(path)))
        .setMediaMetadata(metadataBuilder.build())
        .build()
}