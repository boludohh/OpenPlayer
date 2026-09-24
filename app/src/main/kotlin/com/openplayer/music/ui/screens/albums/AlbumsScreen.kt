package com.openplayer.music.ui.screens.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openplayer.music.OpenPlayerApplication
import com.openplayer.music.R
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.data.model.Album
import com.openplayer.music.data.model.Song
import com.openplayer.music.ui.screens.albums.components.AlbumCard
import com.openplayer.music.ui.theme.LocalScreenTitleColor
import com.openplayer.music.ui.theme.LocalTracksCountTextColor
import com.openplayer.music.ui.theme.screenTitle

/**
 * Altura del desvanecido superior en dp.
 * El fade ocupa esta distancia desde el tope del LazyVerticalGrid hacia abajo,
 * dibujando un gradiente vertical del color de fondo principal (opaco en
 * el tope) a transparente. El extremo opaco oculta la línea de recorte
 * dura del viewport, y las tarjetas que suben hacia el panel fijo se
 * desvanecen suavemente dentro de esta franja.
 */
private val TopFadeHeight = 64.dp

/**
 * Distancia de scroll (en dp) necesaria para que el fade alcance su
 * alfa máximo (1.0). El fade se enciende progresivamente desde 0 hasta
 * este umbral, evitando un encendido brusco de un frame a otro.
 */
private val ScrollFadeThreshold = 48.dp

/**
 * Pantalla de álbumes de OpenPlayer.
 *
 * Muestra un grid de 2 columnas con tarjetas de álbumes agrupados por
 * `(album, albumArtist)` para desambiguar álbumes homónimos de
 * distintos artistas. Cada tarjeta muestra la carátula del álbum,
 * título, artista y metadata (año + conteo de pistas). El header
 * (título + conteo) forma parte del scroll junto con las tarjetas.
 *
 * **Agrupación en memoria**: no requiere cambios en Room/DAO; se
 * deriva del Flow de canciones de [AudioRepository] mediante
 * `groupBy` con clave `"${album}|${albumArtist}"`. Para canciones
 * sin campo `album`, se agrupa por `artist` como fallback (canciones
 * sueltas aparecen como "álbum" con el nombre del artista).
 *
 * **Carátula del álbum**: para cada grupo, se busca la primera
 * canción que tenga carátula en disco (vía [com.openplayer.music.data.media.CoverRepository.coverFile]).
 * Si ninguna canción del grupo tiene carátula, la tarjeta muestra un
 * placeholder.
 *
 * **Efecto fade superior condicional**: idéntico al de TracksScreen,
 * ArtistsScreen y PlaylistScreen; solo se dibuja cuando hay scroll
 * activo.
 *
 * **Geometría óptica idéntica a las otras pestañas**: los paddings
 * del header (start=24dp, top=8dp, end=15dp) y las separaciones
 * (8dp título/conteo, 24dp conteo/primer álbum) coinciden con las
 * otras pestañas, garantizando alineación visual consistente.
 *
 * @param audioRepository Repositorio de audio para obtener la lista de canciones.
 * @param modifier Modificador de Compose opcional.
 */
@Composable
fun AlbumsScreen(
    audioRepository: AudioRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val songs by audioRepository.songs.collectAsState(initial = emptyList())
    val screenTitleColor = LocalScreenTitleColor.current
    val tracksCountTextColor = LocalTracksCountTextColor.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current

    // Singleton de CoverRepository desde la Application
    val coverRepository = remember {
        (context.applicationContext as OpenPlayerApplication).coverRepository
    }

    val thresholdPx = with(density) { ScrollFadeThreshold.toPx() }

    val fadeAlpha by remember {
        derivedStateOf {
            val firstVisibleItemIndex = gridState.firstVisibleItemIndex
            val firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset

            if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) {
                0f
            } else {
                val scrollDistance = if (firstVisibleItemIndex > 0) {
                    thresholdPx
                } else {
                    firstVisibleItemScrollOffset.toFloat()
                }
                (scrollDistance / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }

    // Agrupar canciones por álbum y ordenar alfabéticamente
    val albums = remember(songs) {
        groupSongsByAlbum(songs)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    if (fadeAlpha > 0f) {
                        val fadeHeightPx = TopFadeHeight.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    backgroundColor.copy(alpha = fadeAlpha),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = fadeHeightPx
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width, fadeHeightPx)
                        )
                    }
                },
            contentPadding = PaddingValues(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(
                key = "albums_header",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp, end = 3.dp, bottom = 24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.albums_screen_placeholder),
                        style = screenTitle(),
                        color = screenTitleColor,
                        textAlign = TextAlign.Start
                    )

                    Text(
                        text = stringResource(R.string.main_albums_count, albums.size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tracksCountTextColor,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            items(
                items = albums,
                key = { it.key }
            ) { album ->
                val coverFile = album.coverPath?.let { coverRepository.coverFile(it) }
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

/**
 * Agrupa canciones por álbum y devuelve una lista de [Album] ordenada
 * alfabéticamente por título.
 *
 * ## Clave de agrupación
 * La clave es `"${album}|${albumArtist}"` para desambiguar álbumes
 * homónimos de distintos artistas. Para canciones sin campo `album`,
 * se usa el `artist` como fallback (canciones sueltas aparecen
 * agrupadas por artista).
 *
 * ## Carátula del álbum
 * Para cada grupo, se busca la primera canción que tenga carátula en
 * disco (verificando que el campo `path` no esté vacío). Si ninguna
 * canción del grupo tiene carátula, [Album.coverPath] es `null`.
 */
private fun groupSongsByAlbum(songs: List<Song>): List<Album> {
    if (songs.isEmpty()) return emptyList()

    // Agrupar por clave (album, albumArtist)
    val grouped = songs.groupBy { song ->
        val albumName = song.album?.takeIf { it.isNotBlank() }
        val artistName = song.albumArtist?.takeIf { it.isNotBlank() }
            ?: song.artist.takeIf { it.isNotBlank() }
            ?: "Unknown Artist"
        
        if (albumName != null) {
            "$albumName|$artistName"
        } else {
            // Canciones sin álbum: agrupar por artista
            "|$artistName"
        }
    }

    // Convertir cada grupo a Album
    return grouped.map { (key, groupSongs) ->
        val firstSong = groupSongs.first()
        
        // Título del álbum (o nombre del artista si no hay álbum)
        val title = firstSong.album?.takeIf { it.isNotBlank() }
            ?: firstSong.artist
        
        // Artista del álbum (albumArtist preferido, fallback a artist)
        val artist = firstSong.albumArtist?.takeIf { it.isNotBlank() }
            ?: firstSong.artist
        
        // Año (primer valor no-null encontrado en el grupo)
        val year = groupSongs.firstNotNullOfOrNull { it.year }
        
        // Carátula del álbum (path de la primera canción con carátula)
        val coverPath = groupSongs
            .filter { it.path.isNotBlank() }
            .firstOrNull()?.path
        
        Album(
            key = key,
            title = title,
            artist = artist,
            year = year,
            trackCount = groupSongs.size,
            coverPath = coverPath
        )
    }.sortedBy { it.title.lowercase() }
}