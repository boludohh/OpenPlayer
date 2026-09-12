package com.openplayer.music.ui.screens

import android.content.ComponentName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.openplayer.music.R
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.data.media.CoverRepository
import com.openplayer.music.data.model.Song
import com.openplayer.music.playback.service.PlaybackService
import com.openplayer.music.playback.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pantalla principal de OpenPlayer (cascarón global TEMPORAL).
 *
 * Por ahora muestra:
 * - La cantidad de canciones escaneadas y una lista temporal de
 *   canciones; tocar una inicia la reproducción vía MediaController
 *   conectado al PlaybackService.
 *
 * **Esta pantalla no realiza ninguna extracción de portadas.**
 * La extracción ya se hizo durante el escaneo en [AudioRepository],
 * y la resolución del `artworkUri` de cada `MediaItem` se delega a
 * [CoverRepository.coverFile], que es un stat sincrónico.
 *
 * En pasos futuros aquí se integrará la navegación entre pestañas
 * (Home, Songs, Albums, Playlists) y los componentes globales,
 * y se eliminará la lista temporal.
 */
@Composable
fun MainScreen(audioRepository: AudioRepository) {
    val context = LocalContext.current
    val songs by audioRepository.songs.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    // Instancia para consulta de archivos existentes; NO extrae nada.
    val coverRepository = remember { CoverRepository(context) }

    // ===== MediaController TEMPORAL conectado al PlaybackService =====
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            if (future.isDone && !future.isCancelled) {
                controller = runCatching { future.get() }.getOrNull()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            controller?.release()
            controller = null
        }
    }

    // ===== Lista de MediaItems construida UNA sola vez =====
    var mediaItems by remember { mutableStateOf<List<androidx.media3.common.MediaItem>>(emptyList()) }

    // Reconstruir MediaItems cuando cambie la lista de canciones
    LaunchedEffect(songs) {
        if (songs.isNotEmpty()) {
            mediaItems = withContext(Dispatchers.IO) {
                songs.map { it.toMediaItem(coverRepository) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.main_songs_count, songs.size),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            // Lista TEMPORAL de canciones para probar reproducción
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(songs) { index, song ->
                    TempSongRow(song = song) {
                        val current = controller ?: return@TempSongRow
                        if (mediaItems.isEmpty()) return@TempSongRow

                        coroutineScope.launch {
                            // Sin ensureCover: toda la extracción ya se hizo
                            // durante el escaneo. El tap es solo seekTo/play.
                            if (current.mediaItemCount == 0 || current.currentMediaItem == null) {
                                current.setMediaItems(mediaItems, index, 0L)
                                current.prepare()
                            } else {
                                current.seekTo(index, 0L)
                            }
                            current.play()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fila TEMPORAL de canción para la lista de prueba.
 * Muestra título y artista; al tocarla inicia la reproducción.
 */
@Composable
private fun TempSongRow(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}