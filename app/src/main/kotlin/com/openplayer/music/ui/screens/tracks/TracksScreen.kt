package com.openplayer.music.ui.screens.tracks

import android.content.ComponentName
import android.util.Log
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.openplayer.music.R
import com.openplayer.music.data.media.AudioRepository
import com.openplayer.music.data.media.CoverRepository
import com.openplayer.music.data.model.Song
import com.openplayer.music.playback.engine.BassPlayerAdapter
import com.openplayer.music.playback.service.PlaybackService
import com.openplayer.music.playback.toMediaItem
import com.openplayer.music.ui.theme.LocalTracksCountTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEBUG_TAG = "QueueDebug"

/**
 * Pantalla de pistas de OpenPlayer.
 *
 * Muestra la lista completa de canciones escaneadas ordenadas por fecha
 * de agregada descendente (las más recientes primero). Al tocar una
 * canción inicia la reproducción vía MediaController conectado al
 * PlaybackService, buscando por mediaId para garantizar sincronización
 * correcta con cualquier orden de lista.
 *
 * La primera vez que se reproduce desde esta pantalla, se carga la cola
 * con queueId "tracksByDate" para que el orden de reproducción respete
 * el orden visual (por fecha descendente).
 *
 * **Esta pantalla no realiza extracción de portadas.**
 * La extracción ya se hizo durante el escaneo en [AudioRepository].
 */
@Composable
fun TracksScreen(
    audioRepository: AudioRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val songs by audioRepository.songs.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val coverRepository = remember { CoverRepository(context) }
    val tracksCountTextColor = LocalTracksCountTextColor.current

    // Ordenar por fecha de agregada descendente (más recientes primero)
    val sortedSongs = remember(songs) {
        songs.sortedByDescending { it.dateAdded }
    }

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

    LaunchedEffect(sortedSongs) {
        if (sortedSongs.isNotEmpty()) {
            mediaItems = withContext(Dispatchers.IO) {
                sortedSongs.map { it.toMediaItem(coverRepository) }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Texto de conteo de pistas: 15sp de tamaño.
        // start = 23dp: alineado con el glifo del primer icono de la barra
        //   superior (15dp de padding del Row + 8dp de centrado del icono
        //   de 28dp dentro de su área de toque de 44dp).
        // top = 10dp: la caja del texto queda a 50dp; compensando el leading
        //   interno de la fuente (~6dp sobre el glifo en línea de 24sp), el
        //   texto queda ópticamente a 8dp debajo de la base del icono (48dp).
        Text(
            text = stringResource(R.string.main_songs_count, sortedSongs.size),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = tracksCountTextColor,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 23.dp, top = 10.dp, end = 15.dp)
        )

        // Lista de pistas con 8dp de separación respecto al texto de conteo
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            itemsIndexed(sortedSongs) { _, song ->
                SongRow(song = song) {
                    val current = controller ?: return@SongRow
                    val targetMediaId = song.id.toString()

                    Log.d(DEBUG_TAG, "TracksScreen: user tapped song | mediaId=$targetMediaId | title=${song.title}")

                    coroutineScope.launch {
                        if (mediaItems.isNotEmpty()) {
                            // Encontrar el índice de la canción en la lista ordenada por fecha
                            val startIndex = mediaItems.indexOfFirst { it.mediaId == targetMediaId }
                                .coerceAtLeast(0)
                            
                            Log.d(DEBUG_TAG, "TracksScreen: loading queue with queueId=${BassPlayerAdapter.QUEUE_TRACKS_BY_DATE} | startIndex=$startIndex | totalItems=${mediaItems.size}")
                            
                            // Agregar queueId al tag del primer MediaItem
                            val taggedMediaItems = mediaItems.mapIndexed { i, item ->
                                if (i == 0) {
                                    androidx.media3.common.MediaItem.Builder()
                                        .setMediaId(item.mediaId)
                                        .setUri(item.localConfiguration?.uri)
                                        .setMediaMetadata(item.mediaMetadata)
                                        .setTag(BassPlayerAdapter.QUEUE_TRACKS_BY_DATE)
                                        .build()
                                } else {
                                    item
                                }
                            }
                            
                            // Reemplazar la cola del controller con la cola ordenada por fecha
                            current.setMediaItems(taggedMediaItems, startIndex, 0L)
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