package com.openplayer.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openplayer.music.OpenPlayerApplication
import com.openplayer.music.R
import com.openplayer.music.playback.engine.BassPlayerAdapter
import com.openplayer.music.ui.screens.playlist.PlaylistRow
import com.openplayer.music.ui.screens.tracks.components.TrackRow
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import com.openplayer.music.ui.theme.LocalTracksCountTextColor
import kotlinx.coroutines.delay

/**
 * Delay del debounce en milisegundos antes de ejecutar la búsqueda
 * tras el último cambio en el campo de texto. Evita consultas
 * excesivas a Room mientras el usuario escribe.
 */
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Pantalla de búsqueda global de OpenPlayer.
 *
 * Permite buscar canciones (por título, artista o álbum) y playlists
 * (por nombre) en tiempo real con debounce de 300ms.
 *
 * ## Comportamiento
 * - Campo de texto con debounce de 300ms: la búsqueda se ejecuta
 *   300ms después de que el usuario deja de escribir.
 * - Dos secciones de resultados:
 *   - **Canciones**: reutiliza [TrackRow] (mismo componente que
 *     TracksScreen). Al tocar una canción, se reproduce en la cola
 *     QUEUE_SEARCH (resultados de búsqueda en orden alfabético).
 *   - **Playlists**: reutiliza [PlaylistRow] (mismo componente que
 *     PlaylistScreen). Callbacks de navegación vacíos por ahora.
 * - Estado vacío cuando no hay resultados o cuando el campo está
 *   vacío (mensaje diferente en cada caso).
 * - Los resultados son reactivos: si la biblioteca cambia mientras
 *   el usuario está en esta pantalla, los resultados se actualizan
 *   automáticamente.
 *
 * ## Indicador de pista actual
 * Cada [TrackRow] recibe `isCurrentTrack` conectado al Flow
 * `currentMediaId` de [PlaybackController], por lo que el indicador
 * se actualiza en tiempo real incluso si la reproducción se inició
 * desde otra pantalla.
 *
 * @param onClose Callback invocado cuando el usuario cierra la
 *                pantalla de búsqueda.
 * @param modifier Modificador de Compose opcional.
 */
@Composable
fun SearchScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalTracksCountTextColor.current
    val backgroundColor = MaterialTheme.colorScheme.background

    // Singletons de los repositorios desde la Application
    val audioRepository = remember {
        (context.applicationContext as OpenPlayerApplication).audioRepository
    }
    val playlistRepository = remember {
        (context.applicationContext as OpenPlayerApplication).playlistRepository
    }
    val coverRepository = remember {
        (context.applicationContext as OpenPlayerApplication).coverRepository
    }
    // Singleton de PlaybackController desde la Application
    val playbackController = remember {
        (context.applicationContext as OpenPlayerApplication).playbackController
    }

    // Flow reactivo del controller: mediaId de la pista actualmente en reproducción
    val currentPlayingMediaId by playbackController.currentMediaId.collectAsState()

    // Estado del campo de búsqueda
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }

    // Debounce: actualiza debouncedQuery 300ms después del último cambio
    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        debouncedQuery = query
    }

    // Resultados reactivos: se re-emiten cuando cambia debouncedQuery
    // o cuando las tablas subyacentes cambian
    val songs by audioRepository.searchSongs(debouncedQuery)
        .collectAsState(initial = emptyList())
    val playlists by playlistRepository.searchPlaylists(debouncedQuery)
        .collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        // Campo de búsqueda
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Resultados o estado vacío
        if (debouncedQuery.isBlank()) {
            // Campo vacío: mensaje de instrucción
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_empty_query),
                    style = MaterialTheme.typography.bodyLarge,
                    color = subtitleColor,
                    textAlign = TextAlign.Center
                )
            }
        } else if (songs.isEmpty() && playlists.isEmpty()) {
            // Búsqueda sin resultados
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = subtitleColor,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Hay resultados: lista scrolleable
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                // Sección de canciones
                if (songs.isNotEmpty()) {
                    item(key = "songs_header") {
                        Text(
                            text = stringResource(R.string.search_section_songs),
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(
                        items = songs,
                        key = { "song-${it.id}" }
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
                                    queueId = BassPlayerAdapter.QUEUE_SEARCH,
                                    queueSongs = songs
                                )
                            },
                            onMoreClick = { /* TODO: menú contextual */ }
                        )
                    }

                    item(key = "songs-spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Sección de playlists
                if (playlists.isNotEmpty()) {
                    item(key = "playlists-header") {
                        Text(
                            text = stringResource(R.string.search_section_playlists),
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(
                        items = playlists,
                        key = { "playlist-${it.id}" }
                    ) { playlist ->
                        PlaylistRow(playlist = playlist)
                    }
                }
            }
        }
    }
}