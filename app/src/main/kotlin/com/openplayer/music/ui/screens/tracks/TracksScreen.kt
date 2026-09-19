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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * Altura del desvanecido inferior en dp.
 * El fade ocupa esta distancia desde el borde inferior del LazyColumn
 * hacia arriba, dibujando un gradiente vertical de transparente al
 * color de fondo principal. Visible pero no exagerado.
 */
private val BottomFadeHeight = 80.dp

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
 * **Efecto fade inferior**: se dibuja un gradiente vertical en el borde
 * inferior del área de scroll (de transparente al color de fondo), de
 * modo que las pistas que están a punto de salir de pantalla se
 * desvanecen suavemente contra el fondo principal.
 *
 * **Texto de conteo dentro del scroll**: el texto "X pistas" forma
 * parte del LazyColumn (primer item), por lo que sube junto con las
 * canciones al hacer scroll. Los iconos de la barra superior
 * (TopActionBar) permanecen fijos en MainScreen.
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
    val backgroundColor = MaterialTheme.colorScheme.background

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
            .background(backgroundColor)
    ) {
        // LazyColumn con efecto fade en el borde inferior.
        // El fade se dibuja DESPUÉS del contenido (drawContent() primero),
        // por lo que cubre las pistas que están a punto de salir de
        // pantalla con un gradiente vertical de transparente al color
        // de fondo principal.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // Gradiente vertical en el borde inferior
                    val fadeHeightPx = BottomFadeHeight.toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, backgroundColor),
                            startY = size.height - fadeHeightPx,
                            endY = size.height
                        ),
                        topLeft = Offset(0f, size.height - fadeHeightPx),
                        size = Size(size.width, fadeHeightPx)
                    )
                }
        ) {
            // Texto de conteo de pistas como primer item del scroll.
            // Sube junto con las canciones al hacer scroll.
            // start = 23dp: alineado con el glifo del primer icono de la
            //   barra superior (15dp de padding del Row + 8dp de centrado
            //   del icono de 26dp dentro de su área de toque de 44dp).
            item(key = "tracks_count_header") {
                Text(
                    text = stringResource(R.string.main_songs_count, sortedSongs.size),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = tracksCountTextColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 23.dp, top = 10.dp, end = 15.dp, bottom = 8.dp)
                )
            }

            // Lista de pistas
            itemsIndexed(
                items = sortedSongs,
                key = { _, song -> song.id }
            ) { _, song ->
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