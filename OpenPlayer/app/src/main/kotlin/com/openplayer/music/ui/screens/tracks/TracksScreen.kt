package com.openplayer.music.ui.screens.tracks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.openplayer.music.playback.engine.BassPlayerAdapter
import com.openplayer.music.ui.screens.tracks.components.TrackRow
import com.openplayer.music.ui.theme.LocalScreenTitleColor
import com.openplayer.music.ui.theme.LocalTracksCountTextColor
import com.openplayer.music.ui.theme.screenTitle

/**
 * Altura del desvanecido superior en dp.
 * El fade ocupa esta distancia desde el tope del LazyColumn hacia abajo,
 * dibujando un gradiente vertical del color de fondo principal (opaco en
 * el tope) a transparente. El extremo opaco oculta la línea de recorte
 * dura del viewport, y las pistas que suben hacia el panel fijo se
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
 * Pantalla de pistas de OpenPlayer.
 *
 * Muestra la lista completa de canciones escaneadas ordenadas por fecha
 * de agregada descendente (las más recientes primero). Al tocar una
 * canción inicia la reproducción vía [PlaybackController] conectado al
 * PlaybackService, construyendo la cola con queueId QUEUE_TRACKS_BY_DATE
 * para que el orden de reproducción respete el orden visual.
 *
 * **Indicador de pista actual**: cada [TrackRow] recibe un booleano
 * [TrackRow.isCurrentTrack] que indica si esa canción es la que está
 * sonando ahora en el reproductor. El indicador se actualiza en tiempo
 * real mediante el Flow `currentMediaId` de [PlaybackController],
 * respondiendo a cambios de pista desde cualquier fuente (taps en otras
 * filas, transiciones automáticas, controles externos).
 *
 * **Efecto fade superior condicional**: se dibuja un gradiente vertical
 * en el tope del área de scroll (de color de fondo opaco a transparente
 * hacia abajo), pero SOLO cuando el usuario hace scroll. En reposo
 * (lista arriba, sin scroll) el fade está apagado (alfa 0), por lo que
 * no afecta al texto de conteo ni a la primera pista. Apenas se detecta
 * scroll, el fade se enciende progresivamente (0 → 1) hasta alcanzar
 * su intensidad máxima tras 48dp de recorrido. Permanece activo mientras
 * el usuario esté dentro de la lista, y se apaga automáticamente al
 * volver al inicio (primera pista visible con offset 0). El viewport
 * del scroll comienza en 43dp bajo la barra de estado (base de glifos
 * 35dp + 8dp de respiro, definido en MainScreen), por lo que ningún
 * contenido pasa detrás de los iconos.
 *
 * **Título, conteo y filas de pistas dentro del scroll**: los tres
 * forman parte del LazyColumn, por lo que suben juntos con las
 * canciones al hacer scroll. Los iconos de la barra superior
 * (TopActionBar) permanecen fijos en MainScreen.
 *
 * **Carátulas vía Coil**: cada fila ([TrackRow]) muestra la carátula
 * que TagLib extrajo durante el escaneo y que [com.openplayer.music.data.media.CoverRepository]
 * guardó en disco. Si una canción no tiene carátula, se muestra un
 * placeholder con icono de nota musical. Las carátulas nunca se cruzan
 * entre canciones gracias al key estable del LazyColumn y al cacheo de
 * Coil por ruta de archivo.
 *
 * **Optimización: CoverRepository singleton**. Usa el singleton de
 * [OpenPlayerApplication] en lugar de crear una instancia propia,
 * compartiendo el caché de memoria con el servicio y evitando duplicados.
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
    // Singleton de CoverRepository desde la Application: comparte caché
    // de memoria con PlaybackService y evita duplicados.
    val coverRepository = remember {
        (context.applicationContext as OpenPlayerApplication).coverRepository
    }
    // Singleton de PlaybackController desde la Application
    val playbackController = remember {
        (context.applicationContext as OpenPlayerApplication).playbackController
    }
    val screenTitleColor = LocalScreenTitleColor.current
    val tracksCountTextColor = LocalTracksCountTextColor.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Convertir el umbral de dp a píxeles usando la densidad de la pantalla
    val thresholdPx = with(density) { ScrollFadeThreshold.toPx() }

    // Calcular el alfa del fade basado en la posición de scroll.
    // - 0 cuando la lista está en reposo (primera pista visible, offset 0).
    // - 1 cuando el scroll supera ScrollFadeThreshold (48dp de recorrido).
    // - Valores intermedios para una transición suave.
    val fadeAlpha by remember {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            val firstVisibleOffset = listState.firstVisibleItemScrollOffset

            if (firstVisibleIndex == 0 && firstVisibleOffset == 0) {
                0f
            } else {
                val scrollDistance = if (firstVisibleIndex > 0) {
                    // Ya pasamos el primer item, fade al máximo
                    thresholdPx
                } else {
                    // Estamos en el primer item pero con offset, calcular proporción
                    firstVisibleOffset.toFloat()
                }
                (scrollDistance / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }

    // Ordenar por fecha de agregada descendente (más recientes primero)
    val sortedSongs = remember(songs) {
        songs.sortedByDescending { it.dateAdded }
    }

    // Flow reactivo del controller: mediaId de la pista actualmente en reproducción
    val currentPlayingMediaId by playbackController.currentMediaId.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // LazyColumn con efecto fade condicional en el borde superior.
        // El fade se dibuja DESPUÉS del contenido (drawContent() primero),
        // y solo cuando fadeAlpha > 0 (es decir, cuando hay scroll).
        // El gradiente va de color de fondo (opaco) a transparente,
        // multiplicado por fadeAlpha para que en reposo no se dibuje nada.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // Solo dibujar el fade si hay scroll activo
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
                }
        ) {
            // Header con título de pestaña y conteo de pistas como primer item del scroll.
            // Suben juntos con las canciones al hacer scroll.
            // Geometría óptica (bajo la barra de estado):
            // - Base de iconos: 35dp
            // - Título: 35 + 20 = 55dp (compensando ~4dp de leading de fuente 36sp)
            // - Viewport del scroll: 43dp (definido en MainScreen)
            // - Padding top del título: 55 - 43 - 4 = 8dp (compensación óptica)
            // - Conteo: 8dp debajo del título
            // - Lista: 24dp debajo del conteo
            // - start = 24dp: alineado con el glifo del icono de menú
            //   (15dp del Row + 9dp de centrado del glifo de 26dp en área de 44dp)
            item(key = "tracks_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 8.dp, end = 15.dp)
                ) {
                    // Título de pestaña
                    Text(
                        text = stringResource(R.string.tracks_screen_placeholder),
                        style = screenTitle(),
                        color = screenTitleColor,
                        textAlign = TextAlign.Start
                    )

                    // Conteo de pistas (8dp debajo del título)
                    Text(
                        text = stringResource(R.string.main_songs_count, sortedSongs.size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tracksCountTextColor,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )
                }
            }

            // Lista de pistas con contenedores individuales y carátulas vía Coil.
            // key = song.id garantiza identidad estable de cada fila, evitando
            // que Coil mezcle carátulas al reciclar filas durante el scroll.
            itemsIndexed(
                items = sortedSongs,
                key = { _, song -> song.id }
            ) { _, song ->
                // Archivo de portada en disco (síncrono, sin extracción bajo demanda).
                // null si la canción no tiene portada extraída por TagLib.
                // Ahora usa el caché de coverFile() de CoverRepository para evitar
                // stats de disco repetidos al recomponer filas visibles.
                val coverFile = remember(song.path) {
                    coverRepository.coverFile(song.path)
                }

                // ¿Esta canción es la que está sonando ahora?
                // Comparación por mediaId (estable, no depende del orden visual).
                val isCurrentTrack = song.id.toString() == currentPlayingMediaId

                TrackRow(
                    song = song,
                    coverFile = coverFile,
                    isCurrentTrack = isCurrentTrack,
                    onClick = {
                        playbackController.playSong(
                            song = song,
                            queueId = BassPlayerAdapter.QUEUE_TRACKS_BY_DATE,
                            queueSongs = sortedSongs
                        )
                    },
                    onMoreClick = {
                        // TODO: futuro menú de opciones de la pista
                        // (añadir a playlist, compartir, detalles, etc.).
                        // Por ahora el icono es solo visual.
                    }
                )
            }
        }
    }
}