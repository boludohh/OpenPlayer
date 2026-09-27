package com.openplayer.music.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.openplayer.music.OpenPlayerApplication
import com.openplayer.music.R
import com.openplayer.music.data.db.TotalStats
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.data.media.PlaybackHistoryRepository
import com.openplayer.music.data.model.Song
import com.openplayer.music.ui.screens.home.components.HomeAlbumCard
import com.openplayer.music.ui.screens.home.components.HomeArtistCard
import com.openplayer.music.ui.screens.home.components.HomeSectionHeader
import com.openplayer.music.ui.screens.home.components.HomeTrackCard
import com.openplayer.music.ui.screens.home.components.PlaylistCard
import com.openplayer.music.ui.screens.home.components.ResumeCard
import com.openplayer.music.ui.screens.home.components.ShuffleAllButton
import com.openplayer.music.ui.screens.home.components.StatsPanel
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import com.openplayer.music.ui.theme.LocalTracksCountTextColor
import java.util.Calendar

/**
 * Pantalla de inicio de OpenPlayer (rediseño basado en mockup HTML).
 *
 * Muestra un resumen vivo de la actividad musical del usuario con
 * secciones jerárquicas:
 *
 * 1. **Saludo personalizado** según la hora del día
 * 2. **Seguir escuchando**: card con la última canción reproducida
 * 3. **Tu actividad**: panel de estadísticas agregadas
 * 4. **Reproducir todo aleatoriamente**: botón grande con contador
 * 5. **Escuchado recientemente**: fila horizontal de canciones
 * 6. **Tus artistas**: fila horizontal de artistas con avatar circular
 * 7. **Agregado recientemente**: fila horizontal de canciones recientes
 * 8. **Playlists**: grid 2 columnas de playlists con mosaicos
 * 9. **Álbumes**: fila horizontal de álbumes recientes
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
    val artistImageRepository = remember {
        (context.applicationContext as OpenPlayerApplication).artistImageRepository
    }

    // Flows reactivos del repositorio de historial
    val totalStats by playbackHistoryRepository.totalStats.collectAsState(
        initial = TotalStats(0, 0, 0L)
    )
    val recentlyPlayed by playbackHistoryRepository.getRecentlyPlayed(5)
        .collectAsState(initial = emptyList())

    // Flow de canciones
    val songs by audioRepository.songs.collectAsState(initial = emptyList())

    // Saludo según hora del día
    val greeting = rememberGreeting()

    // Canción para "Seguir escuchando": la más reciente reproducida
    val resumeSong = recentlyPlayed.firstOrNull()

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
                val coverPath = groupSongs.firstOrNull { it.path.isNotBlank() }?.path
                val dateAdded = groupSongs.maxOf { it.dateAdded }
                HomeAlbumData(title, artist, coverPath, dateAdded)
            }
            .sortedByDescending { it.dateAdded }
            .take(10)
    }

    // Canciones agregadas recientemente
    val recentlyAdded = remember(songs) {
        songs.sortedByDescending { it.dateAdded }.take(10)
    }

    // Artistas únicos (para la sección "Tus artistas")
    val uniqueArtists = remember(songs) {
        songs
            .mapNotNull { it.artist.takeIf { name -> name.isNotBlank() } }
            .distinct()
            .take(10)
    }

    // Tiempo formateado para el panel de estadísticas
    val formattedTime = formatPlayedTime(totalStats.totalPlayedMs)

    // Strings localizados
    val statsTitle = stringResource(R.string.home_total_plays)
    val completedLabel = stringResource(R.string.home_completed_plays)
    val timeLabel = stringResource(R.string.home_hours_listened)
    val shuffleTitle = stringResource(R.string.home_shuffle_all)
    val shuffleCount = stringResource(R.string.home_shuffle_count, songs.size)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            // ── Saludo ──
            item(key = "greeting") {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    color = titleColor,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            // ── Seguir escuchando ──
            if (resumeSong != null) {
                item(key = "resume_section") {
                    ResumeSection(
                        song = resumeSong,
                        coverRepository = coverRepository
                    )
                }
            }

            // ── Tu actividad ──
            if (totalStats.totalPlays > 0) {
                item(key = "stats_section") {
                    StatsSection(
                        stats = totalStats,
                        formattedTime = formattedTime,
                        statsTitle = statsTitle,
                        completedLabel = completedLabel,
                        timeLabel = timeLabel
                    )
                }
            }

            // ── Shuffle all ──
            if (songs.isNotEmpty()) {
                item(key = "shuffle") {
                    ShuffleAllButton(
                        titleText = shuffleTitle,
                        countText = shuffleCount,
                        onClick = { /* TODO: shuffle all */ }
                    )
                }
            }

            // ── Escuchado recientemente ──
            if (recentlyPlayed.isNotEmpty()) {
                item(key = "recent_section") {
                    RecentlyPlayedSection(
                        songs = recentlyPlayed,
                        coverRepository = coverRepository
                    )
                }
            }

            // ── Tus artistas ──
            if (uniqueArtists.isNotEmpty()) {
                item(key = "artists_section") {
                    ArtistsSection(
                        artists = uniqueArtists,
                        artistImageRepository = artistImageRepository
                    )
                }
            }

            // ── Agregado recientemente ──
            if (recentlyAdded.isNotEmpty()) {
                item(key = "added_section") {
                    RecentlyAddedSection(
                        songs = recentlyAdded,
                        coverRepository = coverRepository
                    )
                }
            }

            // ── Playlists ──
            item(key = "playlists_section") {
                PlaylistsSection()
            }

            // ── Álbumes ──
            if (recentAlbums.isNotEmpty()) {
                item(key = "albums_section") {
                    AlbumsSection(
                        albums = recentAlbums,
                        coverRepository = coverRepository
                    )
                }
            }

            // ── Estado vacío ──
            if (songs.isEmpty() && totalStats.totalPlays == 0) {
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

        // Atribución de Deezer (términos de uso)
        Text(
            text = stringResource(R.string.artist_images_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = attributionColor,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp, top = 8.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECCIONES HELPER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResumeSection(
    song: Song,
    coverRepository: com.openplayer.music.data.media.CoverRepository
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.home_resume_section),
            showSeeAll = false
        )
        ResumeCard(
            title = song.title,
            artist = song.artist,
            context = stringResource(R.string.home_resume_playing_from),
            coverFile = coverRepository.coverFile(song.path),
            onPlayClick = { /* TODO: reproducir */ },
            onCardClick = { /* TODO: navegar */ }
        )
    }
}

@Composable
private fun StatsSection(
    stats: TotalStats,
    formattedTime: String,
    statsTitle: String,
    completedLabel: String,
    timeLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.home_your_activity),
            showSeeAll = true,
            onSeeAllClick = { /* TODO: navegar a stats */ }
        )
        StatsPanel(
            stats = stats,
            formattedTime = formattedTime,
            totalPlaysLabel = statsTitle,
            completedLabel = completedLabel,
            timeLabel = timeLabel
        )
    }
}

@Composable
private fun RecentlyPlayedSection(
    songs: List<Song>,
    coverRepository: com.openplayer.music.data.media.CoverRepository
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.home_recently_played),
            showSeeAll = true,
            onSeeAllClick = { /* TODO: navegar a historial */ }
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(
                items = songs,
                key = { "recent-${it.id}" }
            ) { song ->
                HomeTrackCard(
                    title = song.title,
                    subtitle = song.artist,
                    coverFile = coverRepository.coverFile(song.path),
                    onClick = { /* TODO: reproducir */ }
                )
            }
        }
    }
}

@Composable
private fun ArtistsSection(
    artists: List<String>,
    artistImageRepository: com.openplayer.music.data.media.ArtistImageRepository
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.home_your_artists),
            showSeeAll = true,
            onSeeAllClick = { /* TODO: navegar a artistas */ }
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(
                items = artists,
                key = { "artist-$it" }
            ) { artistName ->
                val imageFile = artistImageRepository.artistImageFile(artistName)
                HomeArtistCard(
                    artistName = artistName,
                    imageFile = imageFile,
                    onClick = { /* TODO: navegar a detalle artista */ }
                )
            }
        }
    }
}

@Composable
private fun RecentlyAddedSection(
    songs: List<Song>,
    coverRepository: com.openplayer.music.data.media.CoverRepository
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.home_recently_added),
            showSeeAll = true,
            onSeeAllClick = { /* TODO: navegar */ }
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(
                items = songs,
                key = { "added-${it.id}" }
            ) { song ->
                HomeTrackCard(
                    title = song.title,
                    subtitle = song.artist,
                    coverFile = coverRepository.coverFile(song.path),
                    onClick = { /* TODO: reproducir */ }
                )
            }
        }
    }
}

@Composable
private fun PlaylistsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.home_playlists),
            showSeeAll = true,
            onSeeAllClick = { /* TODO: navegar a playlists */ }
        )
        // Grid manual 2x2 (evita LazyVerticalGrid anidado en LazyColumn)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Fila 1
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlaylistCard(
                    title = "Playlist 1",
                    countText = stringResource(R.string.home_songs_count, 0),
                    coverFiles = emptyList(),
                    modifier = Modifier.weight(1f),
                    onClick = { /* TODO: navegar */ }
                )
                PlaylistCard(
                    title = "Playlist 2",
                    countText = stringResource(R.string.home_songs_count, 0),
                    coverFiles = emptyList(),
                    modifier = Modifier.weight(1f),
                    onClick = { /* TODO: navegar */ }
                )
            }
            // Fila 2
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlaylistCard(
                    title = "Playlist 3",
                    countText = stringResource(R.string.home_songs_count, 0),
                    coverFiles = emptyList(),
                    modifier = Modifier.weight(1f),
                    onClick = { /* TODO: navegar */ }
                )
                PlaylistCard(
                    title = "Playlist 4",
                    countText = stringResource(R.string.home_songs_count, 0),
                    coverFiles = emptyList(),
                    modifier = Modifier.weight(1f),
                    onClick = { /* TODO: navegar */ }
                )
            }
        }
    }
}

@Composable
private fun AlbumsSection(
    albums: List<HomeAlbumData>,
    coverRepository: com.openplayer.music.data.media.CoverRepository
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionHeader(
            title = stringResource(R.string.home_albums),
            showSeeAll = true,
            onSeeAllClick = { /* TODO: navegar a álbumes */ }
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(
                items = albums,
                key = { "album-${it.title}|${it.artist}" }
            ) { album ->
                HomeAlbumCard(
                    title = album.title,
                    artist = album.artist,
                    coverFile = album.coverPath?.let { coverRepository.coverFile(it) },
                    onClick = { /* TODO: navegar a detalle álbum */ }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FUNCIONES UTILITARIAS
// ─────────────────────────────────────────────────────────────────────────────

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
        stringResource(R.string.home_hours_minutes_format, hours.toInt(), minutes.toInt())
    } else {
        stringResource(R.string.home_only_minutes_format, minutes.toInt())
    }
}

/**
 * DTO para álbum reciente en la sección de álbumes de Home.
 */
private data class HomeAlbumData(
    val title: String,
    val artist: String,
    val coverPath: String?,
    val dateAdded: Long
)