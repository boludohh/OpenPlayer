package com.openplayer.music.ui.screens.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openplayer.music.OpenPlayerApplication
import com.openplayer.music.R
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.data.model.Album
import com.openplayer.music.data.model.Song
import com.openplayer.music.playback.PlaybackController
import com.openplayer.music.playback.engine.BassPlayerAdapter
import com.openplayer.music.ui.screens.albums.components.AlbumDetailTopBar
import com.openplayer.music.ui.screens.albums.components.AlbumDetailTrackRow
import com.openplayer.music.ui.screens.albums.components.AlbumHeroCard
import com.openplayer.music.ui.theme.LocalCardL2Color
import com.openplayer.music.ui.theme.LocalFloatingIconColor
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import com.openplayer.music.ui.theme.LocalTopBarIconColor

/**
 * Queue ID usado por el detalle de álbum para identificar su cola
 * en el reproductor. Permite al adapter distinguir entre la cola
 * global de biblioteca y la cola específica de un álbum.
 */
private const val QUEUE_ALBUM = "album"

/**
 * Pantalla de detalle de un álbum específico.
 *
 * Muestra el álbum seleccionado con toda su información: portada,
 * título, artista, metadatos (año, pistas, duración total), botones
 * de acción (Reproducir, Shuffle) y la lista completa de canciones
 * ordenadas por número de pista (o alfabéticamente si no tienen).
 *
 * ## Composición (de arriba hacia abajo)
 * 1. **TopBar** con botón back + breadcrumb "ÁLBUM"
 * 2. **Hero card** (AlbumHeroCard) con portada, info y chips
 * 3. **Action row** con botones Reproducir y Shuffle
 * 4. **Header "Canciones"** (20sp, bold)
 * 5. **Lista de canciones** usando AlbumDetailTrackRow (con número
 *    de pista en lugar de carátula)
 *
 * ## Navegación
 * Recibe [onBack] como callback para volver a la lista de álbumes.
 * La pantalla padre (AlbumsScreen) maneja el estado de navegación.
 *
 * ## Reproducción
 * - Botón Reproducir: llama a `playbackController.playSong()` con
 *   la primera canción del álbum como objetivo, y la lista completa
 *   como cola (queueId = QUEUE_ALBUM).
 * - Botón Shuffle: placeholder TODO para implementar en fase futura.
 *
 * ## Indicador de pista actual
 * Cada AlbumDetailTrackRow observa el `currentMediaId` del
 * PlaybackController y dibuja el recuadro bordeado si coincide con
 * su song.id.
 *
 * @param album Álbum a mostrar (modelo de dominio).
 * @param audioRepository Repositorio para obtener todas las canciones
 *        del álbum (filtrando por `album + albumArtist`).
 * @param onBack Callback para volver a la lista de álbumes.
 * @param modifier Modificador externo.
 */
@Composable
fun AlbumDetailScreen(
    album: Album,
    audioRepository: AudioRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allSongs by audioRepository.songs.collectAsState(initial = emptyList())
    val playbackController = remember {
        (context.applicationContext as OpenPlayerApplication).playbackController
    }
    val coverRepository = remember {
        (context.applicationContext as OpenPlayerApplication).coverRepository
    }
    val currentMediaId by playbackController.currentMediaId.collectAsState()

    val titleColor = LocalListItemTitleColor.current
    val backgroundColor = MaterialTheme.colorScheme.background

    // Filtrar canciones del álbum: mismas reglas que groupSongsByAlbum
    val albumSongs = remember(allSongs, album.key) {
        allSongs
            .filter { song ->
                val albumName = song.album?.takeIf { it.isNotBlank() }
                val artistName = song.albumArtist?.takeIf { it.isNotBlank() }
                    ?: song.artist.takeIf { it.isNotBlank() }
                    ?: "Unknown Artist"
                val songKey = if (albumName != null) {
                    "$albumName|$artistName"
                } else {
                    "|$artistName"
                }
                songKey == album.key
            }
            .sortedWith(
                compareBy<Song> { it.discNumber ?: 0 }
                    .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
    }

    // Duración total del álbum
    val totalDurationMs = remember(albumSongs) {
        albumSongs.sumOf { it.duration }
    }

    val coverFile = album.coverPath?.let { coverRepository.coverFile(it) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 8.dp,
                bottom = 24.dp
            )
        ) {
            // TopBar
            item(key = "topbar") {
                AlbumDetailTopBar(onBack = onBack)
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Hero card
            item(key = "hero") {
                AlbumHeroCard(
                    title = album.title,
                    artist = album.artist,
                    year = album.year,
                    trackCount = album.trackCount,
                    totalDurationMs = totalDurationMs,
                    isNew = album.isNew,
                    coverFile = coverFile
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Action row (Reproducir + Shuffle)
            item(key = "actions") {
                AlbumActionsRow(
                    onPlayClick = {
                        val firstSong = albumSongs.firstOrNull()
                        if (firstSong != null) {
                            playbackController.playSong(
                                song = firstSong,
                                queueId = QUEUE_ALBUM,
                                queueSongs = albumSongs
                            )
                        }
                    },
                    onShuffleClick = { /* TODO: implementar shuffle */ }
                )
                Spacer(modifier = Modifier.height(28.dp))
            }

            // Header "Canciones"
            item(key = "tracks_header") {
                Text(
                    text = stringResource(R.string.album_detail_tracks_header),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        letterSpacing = (-0.02).sp
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    color = titleColor,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // Lista de canciones
            itemsIndexed(
                items = albumSongs,
                key = { _, song -> song.id }
            ) { index, song ->
                val songCoverFile = coverRepository.coverFile(song.path)
                AlbumDetailTrackRow(
                    song = song,
                    trackIndex = index + 1,
                    isCurrentTrack = currentMediaId == song.id.toString(),
                    onClick = {
                        playbackController.playSong(
                            song = song,
                            queueId = QUEUE_ALBUM,
                            queueSongs = albumSongs
                        )
                    },
                    onMoreClick = { /* TODO: menú de opciones */ }
                )
            }
        }
    }
}

/**
 * Fila de botones de acción: "Reproducir" (filled, alto contraste) y
 * Shuffle (icon button, superficie L1).
 *
 * @param onPlayClick Callback al tocar "Reproducir".
 * @param onShuffleClick Callback al tocar el botón shuffle.
 * @param modifier Modificador externo.
 */
@Composable
private fun AlbumActionsRow(
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaceL1 = MaterialTheme.colorScheme.surfaceVariant
    val borderL1 = MaterialTheme.colorScheme.outline
    val highContrastColor = LocalListItemTitleColor.current
    val inverseColor = MaterialTheme.colorScheme.background
    val iconColor = LocalTopBarIconColor.current
    val playInteractionSource = remember { MutableInteractionSource() }
    val shuffleInteractionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón Reproducir (filled, alto contraste)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(highContrastColor)
                .clickable(
                    interactionSource = playInteractionSource,
                    indication = null,
                    onClick = onPlayClick
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_play_arrow),
                contentDescription = null,
                tint = inverseColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.album_detail_play),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp
                ),
                fontWeight = FontWeight.Bold,
                color = inverseColor
            )
        }

        // Botón Shuffle (icon button)
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceL1)
                .border(1.dp, borderL1, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = shuffleInteractionSource,
                    indication = null,
                    onClick = onShuffleClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_shuffle),
                contentDescription = stringResource(R.string.album_detail_cd_shuffle),
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}