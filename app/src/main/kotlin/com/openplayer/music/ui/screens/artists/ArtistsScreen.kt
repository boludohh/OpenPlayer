package com.openplayer.music.ui.screens.artists

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
import androidx.compose.runtime.LaunchedEffect
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
import com.openplayer.music.ui.screens.artists.components.ArtistCircle
import com.openplayer.music.ui.theme.LocalScreenTitleColor
import com.openplayer.music.ui.theme.LocalTracksCountTextColor
import com.openplayer.music.ui.theme.screenTitle

/**
 * Altura del desvanecido superior en dp.
 * El fade ocupa esta distancia desde el tope del LazyVerticalGrid hacia abajo,
 * dibujando un gradiente vertical del color de fondo principal (opaco en
 * el tope) a transparente. El extremo opaco oculta la línea de recorte
 * dura del viewport, y los círculos que suben hacia el panel fijo se
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
 * Pantalla de artistas de OpenPlayer.
 *
 * Muestra un grid de 2 columnas con círculos de 160dp (con la carátula
 * real del artista cuando el pipeline de enriquecido la resolvió, o
 * placeholder mientras tanto) y el nombre del artista debajo. El
 * header (título + conteo) forma parte del scroll junto con los círculos.
 *
 * **Enriquecido remoto (MusicBrainz → Fanart.tv → Room → disco → Coil)**:
 * al entrar a la pestaña, un [LaunchedEffect] dispara
 * [com.openplayer.music.data.media.ArtistImageRepository.enrichArtists]
 * con los nombres distintos de artista. El repositorio es cache-first
 * (no repite peticiones ya resueltas) y secuencial con ~1.1s entre
 * llamadas (rate limit de MusicBrainz). Las imágenes resueltas llegan
 * a las celdas reactivamente vía [com.openplayer.music.data.media.ArtistImageRepository.artistImages].
 *
 * **Componente separado**: cada celda del grid se renderiza con
 * [ArtistCircle] (archivo independiente en `artists/components/`),
 * que concentra la geometría del círculo y recibe el [java.io.File]
 * de imagen cuando existe. Esta pantalla se reserva para la
 * orquestación: datos, enriquecido, header, fade y grid.
 *
 * **Efecto fade superior condicional**: se dibuja un gradiente vertical
 * en el tope del área de scroll (de color de fondo opaco a transparente
 * hacia abajo), pero SOLO cuando el usuario hace scroll. En reposo
 * (lista arriba, sin scroll) el fade está apagado (alfa 0), por lo que
 * no afecta al texto de conteo ni al primer círculo. Apenas se detecta
 * scroll, el fade se enciende progresivamente (0 → 1) hasta alcanzar
 * su intensidad máxima tras 48dp de recorrido.
 *
 * **Geometría óptica idéntica a TracksScreen**: el título y el conteo
 * usan los mismos paddings (start=24dp, top=8dp, end=15dp) y separaciones
 * (8dp entre título y conteo, 24dp entre conteo y primer círculo) que
 * en la pantalla de pistas, garantizando alineación visual consistente
 * entre pestañas.
 *
 * @param audioRepository Repositorio de audio para obtener la lista de canciones.
 * @param modifier Modificador de Compose opcional.
 */
@Composable
fun ArtistsScreen(
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

    // Singleton del repositorio de imágenes de artista desde la
    // Application: comparte cachés de disco/memoria y la cola de
    // enriquecido con cualquier otro consumidor futuro.
    val artistImageRepository = remember {
        (context.applicationContext as OpenPlayerApplication).artistImageRepository
    }

    // Mapa reactivo nombre → File de imagen en disco (solo resueltas).
    val artistImages by artistImageRepository.artistImages.collectAsState(initial = emptyMap())

    // Convertir el umbral de dp a píxeles usando la densidad de la pantalla
    val thresholdPx = with(density) { ScrollFadeThreshold.toPx() }

    // Calcular el alfa del fade basado en la posición de scroll.
    // - 0 cuando la lista está en reposo (primer item visible, offset 0).
    // - 1 cuando el scroll supera ScrollFadeThreshold (48dp de recorrido).
    // - Valores intermedios para una transición suave.
    val fadeAlpha by remember {
        derivedStateOf {
            val firstVisibleItemIndex = gridState.firstVisibleItemIndex
            val firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset

            if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) {
                0f
            } else {
                val scrollDistance = if (firstVisibleItemIndex > 0) {
                    // Ya pasamos el primer item, fade al máximo
                    thresholdPx
                } else {
                    // Estamos en el primer item pero con offset, calcular proporción
                    firstVisibleItemScrollOffset.toFloat()
                }
                (scrollDistance / thresholdPx).coerceIn(0f, 1f)
            }
        }
    }

    // Agrupar canciones por artista y ordenar alfabéticamente
    val artists = remember(songs) {
        songs.map { it.artist }
            .distinct()
            .sorted()
    }

    // Enriquecido remoto cache-first al entrar a la pestaña (o al
    // cambiar la biblioteca). El Mutex interno del repositorio descarta
    // corridas solapadas; los artistas ya resueltos no generan peticiones.
    LaunchedEffect(artists) {
        artistImageRepository.enrichArtists(artists)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // LazyVerticalGrid con efecto fade condicional en el borde superior.
        // El fade se dibuja DESPUÉS del contenido (drawContent() primero),
        // y solo cuando fadeAlpha > 0 (es decir, cuando hay scroll).
        // El gradiente va de color de fondo (opaco) a transparente,
        // multiplicado por fadeAlpha para que en reposo no se dibuje nada.
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
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
                },
            contentPadding = PaddingValues(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header con título de pestaña y conteo de artistas como primer item del scroll.
            // Suben juntos con los círculos al hacer scroll.
            // Geometría óptica (bajo la barra de estado):
            // - Base de iconos: 35dp
            // - Título: 35 + 20 = 55dp (compensando ~4dp de leading de fuente 36sp)
            // - Viewport del scroll: 43dp (definido en MainScreen)
            // - Padding top del título: 55 - 43 - 4 = 8dp (compensación óptica)
            // - Conteo: 8dp debajo del título
            // - Lista: 24dp debajo del conteo
            // - start = 24dp: alineado con el glifo del icono de menú
            //   (12dp del contentPadding del grid + 12dp propios = 24dp)
            item(
                key = "artists_header",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp, end = 3.dp, bottom = 24.dp)
                ) {
                    // Título de pestaña
                    Text(
                        text = stringResource(R.string.artists_screen_placeholder),
                        style = screenTitle(),
                        color = screenTitleColor,
                        textAlign = TextAlign.Start
                    )

                    // Conteo de artistas (8dp debajo del título)
                    Text(
                        text = stringResource(R.string.main_artists_count, artists.size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tracksCountTextColor,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Grid de artistas: cada celda delega su visual en ArtistCircle
            items(
                items = artists,
                key = { artistName -> artistName }
            ) { artistName ->
                ArtistCircle(
                    artistName = artistName,
                    imageFile = artistImages[artistName]
                )
            }
        }
    }
}