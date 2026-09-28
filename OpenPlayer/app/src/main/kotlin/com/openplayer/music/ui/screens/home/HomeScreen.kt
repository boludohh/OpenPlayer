package com.openplayer.music.ui.screens.home

import android.text.format.DateFormat
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openplayer.music.OpenPlayerApplication
import com.openplayer.music.R
import com.openplayer.music.data.db.TotalStats
import com.openplayer.music.data.model.Song
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.data.media.PlaybackHistoryRepository
import com.openplayer.music.playback.engine.BassPlayerAdapter
import com.openplayer.music.ui.screens.albums.components.AlbumCard
import com.openplayer.music.ui.screens.tracks.components.TrackRow
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import com.openplayer.music.ui.theme.LocalTracksCountTextColor
import java.util.Calendar

/**
 * Pantalla de inicio de OpenPlayer.
 *
 * Muestra un resumen vivo de la actividad musical del usuario:
 * - **Saludo personalizado** según la hora del día (mañana/tarde/noche).
 * - **Estadísticas agregadas**: reproducciones totales, completadas,
 *   tiempo total escuchado.
 * - **Top artista y pista** más reproducidos.
 * - **Reproducciones recientes**: últimas 5 canciones. Al tocar una
 *   canción, se reproduce en la cola QUEUE_RECENTLY_PLAYED (orden
 *   de más reciente a más antigua).
 * - **Álbumes recientes**: últimos 6 álbumes por fecha de agregado.
 *
 * ## Saludo según hora del día
 * La lógica del saludo se deriva de la hora interna del sistema
 * (`HOUR_OF_DAY`, 0-23), que es idéntica y correcta tanto para usuarios
 * con formato 12h como 24h. Rangos:
 * - Madrugada/noche (20:00-04:59): "Buenas noches"
 * - Mañana (05:00-11:59): "Buenos días"
 * - Tarde (12:00-19:59): "Buenas tardes"
 *
 * ## Formato horario 12h/24h
 * Si en el futuro se muestran horas visibles al usuario, se usará
 * `DateFormat.is24HourFormat(context)` para respetar la preferencia
 * del sistema. La lógica del saludo NO depende del formato de hora.
 *
 * ## Indicador de pista actual
 * Cada [TrackRow] en la sección de recientes recibe `isCurrentTrack`
 * conectado al Flow `currentMediaId` de [PlaybackController], por lo
 * que el indicador se actualiza en tiempo real incluso si la reproducción
 * se inició desde otra pantalla.
 *
 * ## Atribución de Deezer
 * La atribución exigida por los términos de uso de Deezer se mantiene
 * en la esquina superior izquierda óptica (mismos paddings que los
 * títulos de pestaña: start 24dp, top 8dp).
 *
 * @param audioRepository Repositorio de audio para obtener canciones y álbumes.
 * @param playbackHistoryRepository Repositorio de historial de reproducción.
 * @param modifier Modificador de Compose opcional.
 */
@Composable
fun HomeScreen(
    audioRepository: AudioRepository,
    playbackHistoryRepository: PlaybackHistoryRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val attributionColor = LocalTracksCountTextColor.current
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val backgroundColor = MaterialTheme.colorScheme.background

    val coverRepository = remember {
        (context.applicationContext as OpenPlayerApplication).coverRepository
    }
    // Singleton de PlaybackController desde la Application
    val playbackController = remember {
        (context.applicationContext as OpenPlayerApplication).playbackController
    }

    // Flow reactivo del controller: mediaId de la pista actualmente en reproducción
    val currentPlayingMediaId by playbackController.currentMediaId.collectAsState()

    // Flows reactivos del repositorio de historial
    val totalStats by playbackHistoryRepository.totalStats.collectAsState(
        initial = TotalStats(0, 0, 0L)
    )
    val topTrack by playbackHistoryRepository.topTrack.collectAsState(initial = null)
    val topArtist by playbackHistoryRepository.topArtist.collectAsState(initial = null)
    val recentlyPlayed by playbackHistoryRepository.getRecentlyPlayed(5)
        .collectAsState(initial = emptyList())

    // Flow de canciones para álbumes recientes
    val songs by audioRepository.songs.collectAsState(initial = emptyList())

    // Saludo según hora del día
    val greeting = rememberGreeting()

    // Álbumes recientes: agrupar por álbum y ordenar por dateAdded descendente
    val recentAlbums = remember(songs) {
        songs
            .groupBy { song ->
                val albumName = song.album?.takeIf { it.isNotBlank() }
                val artistName = song.albumArtist?.takeIf { it.isNotBlank() }
                    ?: song.artist.takeIf { it.isNotBlank() }
                    ?: "Unknown Artist"
                if (albumName != null) "$albumName|$artistName" else "|$artistName"
            }
            .map { (key, groupSongs) ->
                val firstSong = groupSongs.first()
                val title = firstSong.album?.takeIf { it.isNotBlank() } ?: firstSong.artist
                val artist = firstSong.albumArtist?.takeIf { it.isNotBlank() } ?: firstSong.artist
                val year = groupSongs.firstNotNullOfOrNull { it.year }
                val coverPath = groupSongs.firstOrNull { it.path.isNotBlank() }?.path
                val dateAdded = groupSongs.maxOf { it.dateAdded }
                RecentAlbum(title, artist, year, groupSongs.size, coverPath, dateAdded)
            }
            .sortedByDescending { it.dateAdded }
            .take(6)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = 24.dp
            )
        ) {
            // Saludo
            item(key = "greeting") {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    color = titleColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Estadísticas agregadas
            item(key = "stats") {
                StatsCard(totalStats = totalStats)
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Top artista y pista
            if (topArtist != null || topTrack != null) {
                item(key = "top_header") {
                    Text(
                        text = stringResource(R.string.home_top_section),
                        style = MaterialTheme.typography.titleLarge,
                        color = titleColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                item(key = "top_artist") {
                    if (topArtist != null) {
                        TopItemRow(
                            label = stringResource(R.string.home_top_artist),
                            value = topArtist!!
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                item(key = "top_track") {
                    if (topTrack != null) {
                        TopItemRow(
                            label = stringResource(R.string.home_top_track),
                            value = "${topTrack!!.title} · ${topTrack!!.artist}"
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            // Reproducciones recientes
            if (recentlyPlayed.isNotEmpty()) {
                item(key = "recent_header") {
                    Text(
                        text = stringResource(R.string.home_recently_played),
                        style = MaterialTheme.typography.titleLarge,
                        color = titleColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                items(
                    items = recentlyPlayed,
                    key = { "recent-${it.id}" }
                ) { song ->
                    val coverFile = coverRepository.coverFile(song.path)
                    // ¿Esta canción es la que está sonando ahora?
                    val isCurrentTrack = song.id.toString() == currentPlayingMediaId

                    TrackRow(
                        song = song,
                        coverFile = coverFile,
                        isCurrentTrack = isCurrentTrack,
                        onClick = {
                            playbackController.playSong(
                                song = song,
                                queueId = BassPlayerAdapter.QUEUE_RECENTLY_PLAYED,
                                queueSongs = recentlyPlayed
                            )
                        },
                        onMoreClick = { /* TODO: menú contextual */ }
                    )
                }

                item(key = "recent_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Álbumes recientes
            if (recentAlbums.isNotEmpty()) {
                item(key = "albums_header") {
                    Text(
                        text = stringResource(R.string.home_recent_albums),
                        style = MaterialTheme.typography.titleLarge,
                        color = titleColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                item(key = "albums_row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = recentAlbums,
                            key = { it.key }
                        ) { album ->
                            val coverFile = album.coverPath?.let { coverRepository.coverFile(it) }
                            Box(modifier = Modifier.width(160.dp)) {
                                AlbumCard(
                                    title = album.title,
                                    artist = album.artist,
                                    year = album.year,
                                    trackCount = album.trackCount,
                                    coverFile = coverFile,
                                    onClick = { /* TODO: navegar a detalle del álbum */ }
                                )
                            }
                        }
                    }
                }
            }

            // Estado vacío: sin estadísticas ni álbumes
            if (totalStats.totalPlays == 0 && recentAlbums.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.home_no_stats),
                            style = MaterialTheme.typography.bodyLarge,
                            color = subtitleColor
                        )
                    }
                }
            }
        }

        // Atribución de Deezer (términos de uso para desarrolladores):
        // informa la fuente de las imágenes de artista y el ámbito de
        // uso privado y familiar del contenido.
        // Esquina superior izquierda óptica, bajo la barra de estado.
        Text(
            text = stringResource(R.string.artist_images_attribution),
            fontSize = 10.sp,
            color = attributionColor,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 8.dp)
        )
    }
}

/**
 * Genera el saludo según la hora del día (HOUR_OF_DAY, 0-23).
 * - Madrugada/noche (20:00-04:59): "Buenas noches"
 * - Mañana (05:00-11:59): "Buenos días"
 * - Tarde (12:00-19:59): "Buenas tardes"
 */
@Composable
private fun rememberGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> stringResource(R.string.home_greeting_morning)
        in 12..19 -> stringResource(R.string.home_greeting_afternoon)
        else -> stringResource(R.string.home_greeting_night)
    }
}

/**
 * Tarjeta de estadísticas agregadas: reproducciones totales,
 * completadas y tiempo total escuchado.
 */
@Composable
private fun StatsCard(totalStats: TotalStats) {
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.home_stats_title),
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = stringResource(R.string.home_total_plays),
                    value = totalStats.totalPlays.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.home_completed_plays),
                    value = totalStats.totalCompleted.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.home_hours_listened),
                    value = formatPlayedTime(totalStats.totalPlayedMs),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Elemento individual de estadística (label + value).
 */
@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = titleColor,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = subtitleColor
        )
    }
}

/**
 * Fila de top item (artista o pista más reproducidos).
 */
@Composable
private fun TopItemRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Formatea milisegundos reproducidos a formato legible:
 * - Si >= 1 hora: "X h Y min"
 * - Si < 1 hora: "Y min"
 */
@Composable
private fun formatPlayedTime(playedMs: Long): String {
    val totalMinutes = playedMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.home_hours_minutes_format, hours, minutes)
    } else {
        stringResource(R.string.home_only_minutes_format, minutes)
    }
}

/**
 * DTO para álbum reciente con metadata necesaria para AlbumCard.
 */
private data class RecentAlbum(
    val title: String,
    val artist: String,
    val year: Int?,
    val trackCount: Int,
    val coverPath: String?,
    val dateAdded: Long
) {
    val key: String = "$title|$artist"
}