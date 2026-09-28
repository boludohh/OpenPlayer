package com.openplayer.music.splash.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.openplayer.music.R
import com.openplayer.music.ui.theme.LocalActiveBorderColor
import com.openplayer.music.ui.theme.LocalCardL2Color

/**
 * Estado de un permiso en una tarjeta de la Splash.
 * - [GRANTED]: permiso concedido, muestra icono check verde.
 * - [DENIED]: permiso no concedido, muestra icono X rojo.
 * - `null`: sin icono (usado en tarjetas de idioma).
 */
enum class PermissionState {
    GRANTED,
    DENIED
}

/**
 * Tarjeta reutilizable de la Splash, con el estilo de la referencia:
 * icono circular a la izquierda, título y descripción.
 *
 * Se usa para las tarjetas de permisos y para las de idioma.
 * - [selected]: resalta el borde con LocalActiveBorderColor (highContrast
 *   en los 3 temas, máximo contraste) para el idioma activo.
 * - [permissionState]: muestra icono de estado del permiso con
 *   transición suave (Crossfade) entre X roja (DENIED) y check verde
 *   (GRANTED). `null` para tarjetas sin icono de estado (idiomas).
 * - [iconAsFlag]: cuando es true, el icono ocupa los 44dp completos
 *   del círculo con clip circular y sin tint (preserva los colores
 *   originales de las banderas). Cuando es false, el icono es 22dp
 *   con tint inverseOnSurface (comportamiento estándar).
 * - [roundTop] / [roundBottom]: controlan el redondeo de esquinas para
 *   componer grupos tipo "panel unido": la primera tarjeta del grupo
 *   redondea arriba, la última redondea abajo y las intermedias van
 *   rectas y alineadas.
 *
 * El círculo que contiene el icono usa LocalCardL2Color (superficie
 * elevada nivel 2 según el documento de colores). El icono dentro del
 * círculo y el título de la tarjeta usan inverseOnSurface (highContrast
 * en los 3 temas), excepto cuando iconAsFlag es true (las banderas
 * mantienen sus colores originales).
 */
@Composable
fun SplashCard(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    selected: Boolean = false,
    permissionState: PermissionState? = null,
    iconAsFlag: Boolean = false,
    roundTop: Boolean = true,
    roundBottom: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val border = if (selected) {
        BorderStroke(2.dp, LocalActiveBorderColor.current)
    } else {
        null
    }

    val shape = RoundedCornerShape(
        topStart = if (roundTop) 20.dp else 0.dp,
        topEnd = if (roundTop) 20.dp else 0.dp,
        bottomStart = if (roundBottom) 20.dp else 0.dp,
        bottomEnd = if (roundBottom) 20.dp else 0.dp
    )

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        LocalCardL2Color.current,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (iconAsFlag) {
                    // Modo bandera: icono ocupa los 44dp completos del círculo,
                    // con clip circular y sin tint (preserva colores originales).
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                    )
                } else {
                    // Modo estándar: icono de 22dp con tint inverseOnSurface.
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
                if (body != null) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Icono de estado del permiso con transición suave (Crossfade)
            // entre X roja (DENIED) y check verde (GRANTED).
            if (permissionState != null) {
                Spacer(Modifier.width(8.dp))
                Crossfade(
                    targetState = permissionState,
                    animationSpec = tween(durationMillis = 200)
                ) { state ->
                    val (iconRes, tint) = when (state) {
                        PermissionState.GRANTED -> R.drawable.ic_check to MaterialTheme.colorScheme.tertiary
                        PermissionState.DENIED -> R.drawable.ic_close to MaterialTheme.colorScheme.error
                    }
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            content = content
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = border,
            content = content
        )
    }
}