package com.openplayer.music.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.data.media.PlaylistRepository
import com.openplayer.music.data.model.Playlist
import com.openplayer.music.ui.theme.LocalListItemSubtitleColor
import com.openplayer.music.ui.theme.LocalListItemTitleColor
import com.openplayer.music.ui.theme.LocalScreenTitleColor
import com.openplayer.music.ui.theme.LocalTracksCountTextColor
import com.openplayer.music.ui.theme.screenTitle

/**
 * Altura del desvanecido superior en dp.
 * El fade ocupa esta distancia desde el tope del LazyColumn hacia abajo,
 * dibujando un gradiente vertical del color de fondo principal (opaco en
 * el tope) a transparente. El extremo opaco oculta la línea de recorte
 * dura del viewport, y las filas que suben hacia el panel fijo se
 * desvanecen suavemente dentro de esta franja.
 */
private val TopFadeHeight = 64.dp

/**
 * Distancia de scroll (en dp) necesaria para que el fade alcance su
 * alfa máximo (1.0). El fade se enciende progresivamente desde 0 hasta
 * este umbral, evitando un encendido brusco de un frame a otro.
 */
private val ScrollFadeThreshold = 48.dp

/** Altura de cada fila de playlist en dp. */
private val PlaylistRowHeight = 64.dp

/** Tamaño del icono de playlist en dp. */
private val PlaylistIconSize = 40.dp

/**
 * Pantalla de listas de reproducción de OpenPlayer.
 *
 * Muestra las playlists creadas por el usuario ordenadas por fecha de
 * creación descendente (más recientes primero), con el nombre y el
 * contador de canciones en cada fila. El header (título + conteo)
 * forma parte del scroll junto con las filas.
 *
 * **Cascarón conectado**: esta pantalla ya consume el Flow reactivo
 * de [PlaylistRepository.playlists] mediante [collectAsState]. El
 * pulido visual (iconos personalizados, botón "Crear playlist",
 * diálogos de creación/edición, pantalla de detalle de playlist con
 * canciones) se implementará en la fase de UI posterior.
 *
 * **Efecto fade superior condicional**: idéntico al de TracksScreen y
 * ArtistsScreen; solo se dibuja cuando hay scroll activo.
 *
 * **Estado vacío**: si el usuario no ha creado ninguna playlist, se
 * muestra el string [R.string.playlist_empty_state] centrado.
 *
 * **Geometría óptica idéntica a TracksScreen y ArtistsScreen**: los
 * paddings del header (start=24dp, top=8dp, end=15dp) y las separaciones
 * (8dp título/conteo, 24dp conteo/primera fila) coinciden con las
 * otras pestañas, garantizando alineación visual consistente.
 *
 * @param playlistRepository Repositorio de playlists del usuario.
 * @param modifier Modificador de Compose opcional.
 */
@Composable
fun PlaylistScreen(
    playlistRepository: PlaylistRepository,
    modifier: Modifier = Modifier
) {
    val playlists by playlistRepository.playlists.collectAsState(initial = emptyList())
    val screenTitleColor = LocalScreenTitleColor.current
    val tracksCountTextColor = LocalTracksCountTextColor.current
    val backgroundColor = MaterialTheme.colorScheme.background
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val thresholdPx = with(density) { ScrollFadeThreshold.toPx() }

    val fadeAlpha by remember {
        derivedStateOf {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (playlists.isEmpty()) {
            // Estado vacío: título + mensaje centrado
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Header fijo (no scrolleable porque no hay contenido)
                Text(
                    text = stringResource(R.string.playlist_screen_placeholder),
                    style = screenTitle(),
                    color = screenTitleColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = stringResource(R.string.main_playlists_count, 0),
                    style = MaterialTheme.typography.bodyLarge,
                    color = tracksCountTextColor,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 8.dp)
                )
                // Mensaje de estado vacío centrado en el espacio restante
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.playlist_empty_state),
                        style = MaterialTheme.typography.bodyLarge,
                        color = tracksCountTextColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Hay playlists: LazyColumn con fade condicional
            LazyColumn(
                state = listState,
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
                    }
            ) {
                item(key = "playlists_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 8.dp, end = 15.dp, bottom = 24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.playlist_screen_placeholder),
                            style = screenTitle(),
                            color = screenTitleColor,
                            textAlign = TextAlign.Start
                        )
                        Text(
                            text = stringResource(R.string.main_playlists_count, playlists.size),
                            style = MaterialTheme.typography.bodyLarge,
                            color = tracksCountTextColor,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                items(
                    items = playlists,
                    key = { it.id }
                ) { playlist ->
                    PlaylistRow(playlist = playlist)
                }
            }
        }
    }
}

/**
 * Fila de una playlist individual (cascarón).
 *
 * Muestra el nombre de la playlist y su contador de canciones. La
 * geometría es idéntica a TrackRow (64dp de alto, paddings
 * consistentes) para mantener coherencia visual entre pestañas.
 *
 * **Visibilidad internal**: permite reutilización desde SearchScreen
 * para mostrar resultados de búsqueda de playlists sin duplicar código.
 *
 * ## Próximamente (fase de UI pulida)
 * - Icono personalizado (mosaico de carátulas de las primeras
 *   canciones, o icono temático).
 * - Botón de acción more_vert para editar/eliminar.
 * - Navegación a la pantalla de detalle de la playlist.
 */
@Composable
internal fun PlaylistRow(
    playlist: Playlist,
    modifier: Modifier = Modifier
) {
    val titleColor = LocalListItemTitleColor.current
    val subtitleColor = LocalListItemSubtitleColor.current
    val placeholderBg = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PlaylistRowHeight)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Placeholder del icono de playlist (cascarón; en fase de UI
        // será un mosaico de carátulas o un icono temático)
        Box(
            modifier = Modifier
                .size(PlaylistIconSize)
                .background(placeholderBg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_playlist_filled),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.main_songs_count, playlist.songCount),
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}