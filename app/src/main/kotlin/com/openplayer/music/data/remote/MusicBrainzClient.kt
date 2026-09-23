package com.openplayer.music.data.remote

import com.openplayer.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Cliente mínimo de la API de MusicBrainz (ws/2, solo búsqueda de
 * artistas) para resolver el MBID a partir del nombre local.
 *
 * ## Reglas respetadas (política oficial de MusicBrainz)
 * - **Sin API key**: solo lecturas GET, no requiere autenticación.
 * - **User-Agent significativo** en cada request: nombre de la app,
 *   versión (BuildConfig.VERSION_NAME) y correo de contacto.
 * - **JSON** mediante `fmt=json` (evita parsear XML).
 * - **Rate limit 1 req/s**: este cliente NO temporiza; la cadencia la
 *   impone [com.openplayer.music.data.media.ArtistImageRepository]
 *   (~1.1s entre llamadas remotas).
 * - **Ambigüedad de nombres**: se elige el candidato con mayor
 *   `score`; en empate se conserva el primero devuelto por la API
 *   (ya ordenado por relevancia). `disambiguation` se conserva como
 *   dato auxiliar.
 *
 * ## Sin dependencias nuevas
 * Usa `HttpURLConnection` y `org.json` (ambos incluidos en Android).
 */
object MusicBrainzClient {

    private const val SEARCH_URL = "https://musicbrainz.org/ws/2/artist/"
    private const val CONTACT_EMAIL = "lautaroacosta99@protonmail.com"
    private const val RESULT_LIMIT = 5
    private const val TIMEOUT_MS = 10_000

    /** Candidato de artista devuelto por la búsqueda. */
    data class ArtistMatch(
        val mbid: String,
        val score: Int,
        val disambiguation: String?
    )

    /** Resultado de una búsqueda: encontrado, sin candidatos, o error de red/HTTP. */
    sealed interface MusicBrainzResult {
        data class Found(val match: ArtistMatch) : MusicBrainzResult
        data object NoResults : MusicBrainzResult
        data object Error : MusicBrainzResult
    }

    /**
     * Busca el MBID de [name]. Distingue "sin candidatos" (HTTP 200
     * con lista vacía, cacheable como negativo) de "error" (red o
     * HTTP != 200, NO cacheable para reintentar en la próxima visita).
     */
    suspend fun searchArtist(name: String): MusicBrainzResult = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode(name, "UTF-8")
        val url = "$SEARCH_URL?query=$query&fmt=json&limit=$RESULT_LIMIT"
        var connection: HttpURLConnection? = null
        try {
            val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", userAgent())
                setRequestProperty("Accept", "application/json")
            }
            connection = conn
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext MusicBrainzResult.Error
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseBestMatch(body)
        } catch (_: Exception) {
            MusicBrainzResult.Error
        } finally {
            connection?.disconnect()
        }
    }

    /** Elige el candidato con mayor score; en empate, el primero. */
    private fun parseBestMatch(body: String): MusicBrainzResult {
        val artists = JSONObject(body).optJSONArray("artists")
            ?: return MusicBrainzResult.NoResults
        var best: ArtistMatch? = null
        for (i in 0 until artists.length()) {
            val item = artists.optJSONObject(i) ?: continue
            val mbid = item.optString("id")
            if (mbid.isBlank()) continue
            val score = item.optInt("score", 0)
            if (best == null || score > best.score) {
                val disambiguation = item.optString("disambiguation").takeIf { it.isNotBlank() }
                best = ArtistMatch(mbid, score, disambiguation)
            }
        }
        return if (best != null) MusicBrainzResult.Found(best) else MusicBrainzResult.NoResults
    }

    /** User-Agent identificable exigido por la política de MusicBrainz. */
    private fun userAgent(): String =
        "OpenPlayer/${BuildConfig.VERSION_NAME} ($CONTACT_EMAIL)"
}