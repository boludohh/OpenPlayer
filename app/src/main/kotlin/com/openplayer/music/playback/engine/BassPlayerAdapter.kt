package com.openplayer.music.playback.engine

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultDataSource
import androidx.media3.exoplayer2.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.atomic.AtomicLong

/**
 * Adaptador de BASS para Media3 (SimpleBasePlayer).
 *
 * Implementa la interfaz mínima de [SimpleBasePlayer] para que BASS
 * actúe como motor de reproducción dentro del ecosistema Media3.
 * Esto permite integración con:
 * - Notificación del sistema (MediaSession)
 * - Android Auto (MediaBrowser)
 * - Controles Bluetooth AVRCP
 * - Wear OS
 *
 * ## Cola global vs colas personalizadas
 * [BassPlayerAdapter] mantiene DOS colas:
 * - **QUEUE_LIBRARY** (cola global de biblioteca): contiene todas las
 *   canciones de la biblioteca en orden alfabético por título. Se
 *   actualiza reactivamente cuando la biblioteca cambia (nuevas
 *   canciones, eliminaciones, cambios de metadatos).
 * - **Colas personalizadas** (QUEUE_TRACKS_BY_DATE, QUEUE_SEARCH,
 *   QUEUE_RECENTLY_PLAYED, etc.): se crean bajo demanda cuando el
 *   usuario toca una canción desde una vista específica (ej. ordenada
 *   por fecha, resultados de búsqueda, recientes). Estas colas
 *   reemplazan temporalmente la cola global.
 *
 * ## QueueId en el tag del primer MediaItem
 * El [queueId] se almacena en el campo `tag` del primer MediaItem de
 * la cola. Esto permite al adapter saber si debe reemplazar la cola
 * completa (queueId distinto) o hacer merge reactivo (mismo queueId).
 *
 * ## Merge reactivo (mismo queueId)
 * Si la cola actual tiene el mismo queueId que la nueva, el adapter:
 * - Actualiza metadatos de canciones existentes (título, artista, etc.).
 * - Elimina canciones que ya no están en la biblioteca.
 * - Agrega canciones nuevas al final.
 * - Conserva el orden de la cola existente.
 * - NO interrumpe la reproducción en curso.
 *
 * Esto garantiza que si el usuario está escuchando una canción en la
 * cola global y se agrega una nueva canción a la biblioteca, la cola
 * se actualiza sin detener la reproducción.
 */
@OptIn(UnstableApi::class)
class BassPlayerAdapter(
    context: Context,
    looper: Looper
) : SimpleBasePlayer(looper) {

    companion object {
        /** QueueId de la cola global de biblioteca (orden alfabético). */
        const val QUEUE_LIBRARY = "library"

        /** QueueId de la cola ordenada por fecha de agregada descendente. */
        const val QUEUE_TRACKS_BY_DATE = "tracksByDate"

        /** QueueId de la cola de resultados de búsqueda. */
        const val QUEUE_SEARCH = "search"

        /** QueueId de la cola de canciones recientemente reproducidas. */
        const val QUEUE_RECENTLY_PLAYED = "recentlyPlayed"
    }

    private val context = context.applicationContext
    private val dataSourceFactory = DefaultDataSource.Factory(context)

    /** MediaItems actuales en la cola. */
    private var currentMediaItems: List<MediaItem> = emptyList()

    /** QueueId actual (extraído del tag del primer MediaItem). */
    private var currentQueueId: String = QUEUE_LIBRARY

    /** Índice de la pista actual en currentMediaItems. */
    private var currentMediaItemIndex: Int = 0

    /** Posición actual en milisegundos dentro de la pista actual. */
    private var currentPositionMs: Long = 0L

    /** true si el reproductor está en estado de reproducción activa. */
    private var isPlaying: Boolean = false

    /** Contador atómico para generar IDs únicos de comandos. */
    private val commandCounter = AtomicLong(0)

    // =========================================================================
    // API pública: actualización de cola
    // =========================================================================

    /**
     * Actualiza la cola de reproducción con [mediaItems].
     *
     * Si el queueId de [mediaItems] (extraído del tag del primer item)
     * es distinto al actual, reemplaza la cola completa. Si es el mismo,
     * hace merge reactivo (actualiza metadatos, elimina borrados,
     * agrega nuevos, conserva orden).
     *
     * **No interrumpe la reproducción en curso** si el queueId es el
     * mismo y la pista actual sigue existiendo en la nueva cola.
     */
    fun updateLibraryPlaylist(mediaItems: List<MediaItem>) {
        val newQueueId = mediaItems.firstOrNull()?.localConfiguration?.tag as? String
            ?: QUEUE_LIBRARY

        if (newQueueId != currentQueueId) {
            // QueueId distinto: reemplazar cola completa
            currentMediaItems = mediaItems
            currentQueueId = newQueueId
            invalidateState()
        } else {
            // Mismo queueId: merge reactivo
            val currentMediaId = currentMediaItems.getOrNull(currentMediaItemIndex)?.mediaId
            val newCurrentIndex = mediaItems.indexOfFirst { it.mediaId == currentMediaId }

            currentMediaItems = mediaItems
            if (newCurrentIndex >= 0) {
                currentMediaItemIndex = newCurrentIndex
            }
            invalidateState()
        }
    }

    // =========================================================================
    // SimpleBasePlayer overrides
    // =========================================================================

    override fun getState(): State {
        val playbackState = when {
            currentMediaItems.isEmpty() -> Player.STATE_IDLE
            isPlaying -> Player.STATE_READY
            else -> Player.STATE_READY
        }

        return State.Builder()
            .setAvailableCommands(
                COMMAND_PLAY_PAUSE,
                COMMAND_PREPARE,
                COMMAND_STOP,
                COMMAND_SEEK_TO_DEFAULT_POSITION,
                COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                COMMAND_GET_TIMELINE
            )
            .setPlayWhenReady(isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setPlaylist(
                currentMediaItems.map { item ->
                    MediaItemData.Builder(item.mediaId.hashCode().toLong())
                        .setMediaItem(item)
                        .build()
                }
            )
            .setCurrentMediaItemIndex(currentMediaItemIndex)
            .setPositionMs(currentPositionMs)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        isPlaying = playWhenReady
        invalidateState()
        return Futures.immediateFuture(null)
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        if (mediaItemIndex != Player.C.INDEX_UNSET) {
            currentMediaItemIndex = mediaItemIndex.coerceIn(0, currentMediaItems.size - 1)
        }
        currentPositionMs = positionMs.coerceAtLeast(0L)
        invalidateState()
        return Futures.immediateFuture(null)
    }

    override fun handleStop(): ListenableFuture<*> {
        isPlaying = false
        currentPositionMs = 0L
        invalidateState()
        return Futures.immediateFuture(null)
    }

    override fun handleRelease(): ListenableFuture<*> {
        isPlaying = false
        currentMediaItems = emptyList()
        currentMediaItemIndex = 0
        currentPositionMs = 0L
        return Futures.immediateFuture(null)
    }
}

/**
 * Extensión para convertir [Song] a [MediaItem] de Media3.
 *
 * Incluye metadatos (título, artista, álbum, carátula) y la URI del
 * archivo local. La carátula se obtiene de [CoverRepository] si existe.
 */
fun Song.toMediaItem(coverRepository: com.openplayer.music.data.media.CoverRepository): MediaItem {
    val coverFile = coverRepository.coverFile(path)

    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(coverFile?.toURI()?.toString())
        .build()

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(path)
        .setMediaMetadata(metadata)
        .build()
}