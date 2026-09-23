package com.openplayer.music.ui.screens.artists.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalCoverPlaceholderIconColor
import java.io.File

/** Diámetro del círculo del artista (carátula vía MusicBrainz/Fanart.tv). */
private val ArtistCircleSize = 160.dp

/** Tamaño del icono placeholder centrado dentro del círculo. */
private val ArtistPlaceholderIconSize = 100.dp

/** Separación vertical entre el borde inferior del círculo y el nombre. */
private val CircleToNameSpacing = 6.dp

/**
 * Componente visual de un artista individual en el grid de la pestaña
 * de Artistas.
 *
 * Renderiza la carátula real del artista cuando [imageFile] existe
 * (resuelta por el pipeline MusicBrainz → Fanart.tv → Room → disco y
 * cargada con Coil), o un placeholder circular mientras tanto,
 * manteniendo exactamente las mismas dimensiones en ambos casos.
 *
 * ## Geometría óptica
 * - Círculo: 160dp × 160dp con [CircleShape].
 * - Imagen real: recorte [ContentScale.Crop] dentro del círculo, con
 *   downsampling de Coil a 160dp en píxeles (mismo patrón de memoria
 *   que TrackRow: evita decodificar la imagen completa).
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
 * El placeholder reutiliza los mismos colores que los placeholders de
 * carátulas de pistas: fondo `surfaceVariant` e icono con
 * [LocalCoverPlaceholderIconColor], manteniendo consistencia visual
 * en los 3 temas (Claro / Oscuro / AMOLED).
 *
 * @param artistName Nombre del artista a mostrar debajo del círculo.
 * @param imageFile Archivo de imagen en disco resuelto por
 *                  [com.openplayer.music.data.media.ArtistImageRepository];
 *                  null si el artista aún no tiene imagen (placeholder).
 * @param modifier Modificador externo que aplica el padre (grid).
 */
@Composable
fun ArtistCircle(
    artistName: String,
    imageFile: File? = null,
    modifier: Modifier = Modifier
) {
    val placeholderBg = MaterialTheme.colorScheme.surfaceVariant
    val placeholderIconColor = LocalCoverPlaceholderIconColor.current
    val density = LocalDensity.current

    // Tamaño del thumbnail en píxeles para Coil: downsampling durante
    // el decode (inSampleSize) en vez de decodificar la imagen completa,
    // reduciendo el pico de memoria en grids largos.
    val thumbnailPx = remember(density) {
        (ArtistCircleSize.value * density.density).toInt()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        // Círculo de 160dp: imagen real recortada o placeholder con icono
        Box(
            modifier = Modifier
                .size(ArtistCircleSize)
                .clip(CircleShape)
                .background(placeholderBg),
            contentAlignment = Alignment.Center
        ) {
            // Icono placeholder (siempre dibujado debajo)
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_artists_filled),
                contentDescription = null,
                tint = placeholderIconColor,
                modifier = Modifier.size(ArtistPlaceholderIconSize)
            )

            // Carátula real encima del placeholder (solo si existe en disco)
            if (imageFile != null) {
                val request = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(imageFile)
                    .size(Size(thumbnailPx, thumbnailPx))
                    .build()
                AsyncImage(
                    model = request,
                    contentDescription = artistName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
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