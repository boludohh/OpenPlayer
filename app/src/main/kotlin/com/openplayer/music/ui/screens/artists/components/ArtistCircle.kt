package com.openplayer.music.ui.screens.artists.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalCoverPlaceholderIconColor

/** Diámetro del círculo del artista (futura carátula vía MusicBrainz/Fanart.tv). */
private val ArtistCircleSize = 160.dp

/** Tamaño del icono placeholder centrado dentro del círculo. */
private val ArtistPlaceholderIconSize = 100.dp

/** Separación vertical entre el borde inferior del círculo y el nombre. */
private val CircleToNameSpacing = 6.dp

/**
 * Componente visual de un artista individual en el grid de la pestaña
 * de Artistas.
 *
 * Por ahora renderiza un placeholder circular; en futuras iteraciones
 * el círculo mostrará la carátula real del artista obtenida mediante
 * el pipeline MusicBrainz (MBID) → Fanart.tv (artistthumb/bigpreview)
 * → Room (caché) → Coil (carga), manteniendo exactamente las mismas
 * dimensiones definidas aquí.
 *
 * ## Geometría óptica
 * - Círculo: 160dp × 160dp con [CircleShape].
 * - Icono placeholder: 100dp centrado vertical y horizontalmente.
 * - Separación círculo → nombre: 6dp.
 * - Nombre: centrado, [MaterialTheme.typography.bodyMedium], color
 *   `onBackground`, máximo 2 líneas, padding horizontal de 8dp para
 *   que no toque los bordes de su columna.
 * - La separación vertical entre filas (12dp) NO vive aquí: la decide
 *   el grid padre mediante `verticalArrangement`, porque es una
 *   propiedad de la rejilla, no del componente.
 *
 * ## Colores
 * Reutiliza los mismos colores que los placeholders de carátulas de
 * pistas: fondo `surfaceVariant` e icono con
 * [LocalCoverPlaceholderIconColor], manteniendo consistencia visual
 * en los 3 temas (Claro / Oscuro / AMOLED).
 *
 * @param artistName Nombre del artista a mostrar debajo del círculo.
 * @param modifier Modificador externo que aplica el padre (grid).
 */
@Composable
fun ArtistCircle(
    artistName: String,
    modifier: Modifier = Modifier
) {
    val placeholderBg = MaterialTheme.colorScheme.surfaceVariant
    val placeholderIconColor = LocalCoverPlaceholderIconColor.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Círculo placeholder de 160dp con icono de artista centrado
        Box(
            modifier = Modifier
                .size(ArtistCircleSize)
                .clip(CircleShape)
                .background(placeholderBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_artists_filled),
                contentDescription = null,
                tint = placeholderIconColor,
                modifier = Modifier.size(ArtistPlaceholderIconSize)
            )
        }

        // Separación de 6dp entre círculo y nombre
        Spacer(modifier = Modifier.size(CircleToNameSpacing))

        // Nombre del artista centrado debajo del círculo
        Text(
            text = artistName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}