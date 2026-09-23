package com.openplayer.music.data.remote

import com.openplayer.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Cliente mínimo de la API v3 de Fanart.tv (solo imágenes de artista
 * musical) para obtener la mejor URL de `artistthumb` a partir del
 * MBID de MusicBrainz.
 *
 * ## Claves y términos del servicio
 * - Envía siempre la **clave de proyecto** (`BuildConfig.FANART_API_KEY`,
 *   inyectada desde `local.properties`, nunca commiteada). Si está
 *   vacía (CI o build sin clave), retorna `null` y la funcionalidad
 *   degrada elegantemente a placeholders.
 * - Envía además la **clave personal del usuario** como `client_key`
 *   cuando existe (término general 2 de fanart.tv: la app debe
 *   permitir la clave propia del usuario además de la del proyecto).
 * - Usa únicamente el método API documentado (término general 6):
 *   `GET https://webservice.fanart.tv/v3/music/{MBID}`.
 * - El campo documentado para miniaturas de artista es `artistthumb`
 *   (objetos con id/url/likes); se elige la de más `likes`. El nombre
 *   "bigpreview" no existe en la documentación oficial v3 de music,
 *   por lo que no se utiliza.
 *
 * ## Sin dependencias nuevas
 * Usa `HttpURLConnection` y `org.json` (ambos incluidos en Android).
 */
object FanartClient {

    private const val MUSIC_ARTIST_URL = "https://webservice.fanart.tv/v3/music/"
    private const val TIMEOUT_MS = 10_000

    /**
     * Devuelve la mejor URL de `artistthumb` para el [mbid], o `null`
     * si no hay clave de proyecto, el artista no tiene imágenes o
     * falló la petición.
     */
    suspend fun fetchBestThumbUrl(mbid: String, userKey: String?): String? =
        withContext(Dispatchers.IO) {
            val projectKey = BuildConfig.FANART_API_KEY
            if (projectKey.isBlank()) return@withContext null

            val urlBuilder = StringBuilder(MUSIC_ARTIST_URL)
                .append(mbid)
                .append("?api_key=")
                .append(URLEncoder.encode(projectKey, "UTF-8"))
            if (!userKey.isNullOrBlank()) {
                urlBuilder.append("&client_key=").append(URLEncoder.encode(userKey, "UTF-8"))
            }

            var connection: HttpURLConnection? = null
            try {
                val conn = (URI(urlBuilder.toString()).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                }
                connection = conn
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseBestThumbUrl(body)
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }

    /** Elige la artistthumb con más likes; null si no hay ninguna. */
    private fun parseBestThumbUrl(body: String): String? {
        val thumbs = JSONObject(body).optJSONArray("artistthumb") ?: return null
        var bestUrl: String? = null
        var bestLikes = Int.MIN_VALUE
        for (i in 0 until thumbs.length()) {
            val item = thumbs.optJSONObject(i) ?: continue
            val url = item.optString("url")
            if (url.isBlank()) continue
            val likes = item.optInt("likes", 0)
            if (likes > bestLikes) {
                bestLikes = likes
                bestUrl = url
            }
        }
        return bestUrl
    }
}