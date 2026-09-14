package com.openplayer.music.ui.screens.songs

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * Pantalla de canciones de OpenPlayer.
 *
 * Muestra la lista completa de canciones escaneadas ordenadas por título.
 * Al tocar una canción inicia la reproducción vía MediaController conectado
 * al PlaybackService, buscando por mediaId para garantizar sincronización
 * correcta con cualquier orden de lista.
 *
 * **Esta pantalla no realiza extracción de portadas.**
 * La extracción ya se hizo durante el escaneo en [AudioRepository].
 */
@Composable
fun SongsScreen(
    audioRepository: AudioRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val songs by audioRepository.songs.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val coverRepository = remember { CoverRepository(context) }

    // MediaController conectado al PlaybackService
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

    // Lista de MediaItems construida como fallback para primera reproducción
    var mediaItems by remember { mutableStateOf<List<androidx.media3.common.MediaItem>>(emptyList()) }

    LaunchedEffect(songs) {
        if (songs.isNotEmpty()) {
            mediaItems = withContext(Dispatchers.IO) {
                songs.map { it.toMediaItem(coverRepository) }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = stringResource(R.string.main_songs_count, songs.size),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(songs) { _, song ->
                SongRow(song = song) {
                    val current = controller ?: return@SongRow
                    val targetMediaId = song.id.toString()

                    coroutineScope.launch {
                        val index = (0 until current.mediaItemCount).indexOfFirst {
                            current.getMediaItemAt(it).mediaId == targetMediaId
                        }

                        if (index >= 0) {
                            current.seekTo(index, 0L)
                            current.play()
                        } else if (mediaItems.isNotEmpty()) {
                            val startIndex = mediaItems.indexOfFirst { it.mediaId == targetMediaId }
                                .coerceAtLeast(0)
                            current.setMediaItems(mediaItems, startIndex, 0L)
                            current.prepare()
                            current.play()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fila de canción que muestra título y artista.
 * Al tocarla inicia la reproducción de esa canción.
 */
@Composable
private fun SongRow(song: Song, onClick: () -> Unit) {
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