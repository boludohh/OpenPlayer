package com.openplayer.music.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Cliente mínimo de la API pública de Deezer (solo búsqueda de
 * artistas) para resolver la imagen de un artista a partir de su
 * nombre local.
 *
 * ## Sin API key ni cuenta de desarrollador
 * El endpoint público `https://api.deezer.com/search/artist` no
 * requiere credenciales para búsquedas GET. Los términos de uso de
 * Deezer para desarrolladores se aceptan como proyecto; el uso es
 * estrictamente NO comercial y el contenido se informa en la app como
 * de uso privado y familiar (ver string `artist_images_attribution`).
 *
 * ## Algoritmo de selección del artista correcto (4 pasos)
 * 1. **Descarte de perfiles vacíos/basura**: se elimina cualquier
 *    resultado con `nb_album == 0` Y `nb_fan == 0` a la vez
 *    (duplicados sin contenido real, típicamente con picture rota).
 * 2. **Prioridad de coincidencia exacta**: se normalizan (minúsculas
 *    + trim) el nombre buscado y el `name` de cada superviviente.
 *    Grupo A = idénticos; Grupo B = variantes ("tributo a X", etc.).
 *    Si Grupo A tiene ≥ 1 resultado, Grupo B se descarta por completo.
 *    Si Grupo A queda vacío, se trabaja con Grupo B.
 * 3. **Relevancia por nb_fan**: del grupo superviviente se toma el
 *    de mayor `nb_fan` (en empate, el primero devuelto por la API).
 * 4. **Sin confiables → null**: si tras el Paso 1 no queda nada, se
 *    devuelve NoResults y la app cachea el negativo y muestra
 *    placeholder, nunca una imagen equivocada.
 *
 * **Mejora futura documentada (Paso 2.5, no implementado):** cruzar
 * los álbumes locales (ID3) contra `api.deezer.com/artist/{id}/albums`
 * de cada candidato del Grupo A; si un álbum coincide, ese candidato
 * gana sin importar nb_fan.
 *
 * ## Imagen
 * Se usa `picture_big` (500×500): sobrada para el círculo de 160dp y
 * ligera para descarga. Si viniera vacía en un candidato confiable,
 * se resuelve la identidad pero con `pictureBig = null` (placeholder).
 *
 * ## Sin dependencias nuevas
 * Usa `HttpURLConnection` y `org.json` (ambos incluidos en Android).
 */
object DeezerClient {

    private const val SEARCH_URL = "https://api.deezer.com/search/artist"
    private const val RESULT_LIMIT = 10
    private const val TIMEOUT_MS = 10_000

    /** Candidato de artista devuelto por la búsqueda. */
    data class DeezerArtist(
        val id: Long,
        val name: String,
        val pictureBig: String?
    )

    /** Resultado de una búsqueda: encontrado, sin confiables, o error de red/HTTP. */
    sealed interface DeezerResult {
        data class Found(val artist: DeezerArtist) : DeezerResult
        data object NoResults : DeezerResult
        data object Error : DeezerResult
    }

    /**
     * Busca el artista [name] en Deezer y aplica el algoritmo de
     * selección de 4 pasos. Distingue "sin resultados confiables"
     * (cacheable como negativo) de "error" (red o HTTP != 200, NO
     * cacheable para reintentar en la próxima visita).
     */
    suspend fun searchArtist(name: String): DeezerResult = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode(name, "UTF-8")
        val url = "$SEARCH_URL?q=$query&limit=$RESULT_LIMIT"
        var connection: HttpURLConnection? = null
        try {
            val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            connection = conn
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext DeezerResult.Error
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            selectArtist(body, name)
        } catch (_: Exception) {
            DeezerResult.Error
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Algoritmo de selección puro (sin red): recibe el JSON completo
     * de la respuesta y el nombre buscado; devuelve el candidato
     * correcto o NoResults. Aislado para ser testeable.
     */
    private fun selectArtist(body: String, queriedName: String): DeezerResult {
        val data = JSONObject(body).optJSONArray("data") ?: return DeezerResult.NoResults

        // Paso 1: descartar perfiles vacíos/basura
        val survivors = mutableListOf<JSONObject>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val nbAlbum = item.optInt("nb_album", 0)
            val nbFan = item.optInt("nb_fan", 0)
            if (nbAlbum == 0 && nbFan == 0) continue
            survivors.add(item)
        }

        // Paso 4 (anticipado): sin supervivientes → sin confiables
        if (survivors.isEmpty()) return DeezerResult.NoResults

        // Paso 2: priorizar coincidencia exacta de nombre normalizado
        val normalizedQuery = normalize(queriedName)
        val groupA = survivors.filter { normalize(it.optString("name")) == normalizedQuery }
        val pool = if (groupA.isNotEmpty()) groupA else survivors

        // Paso 3: elegir por relevancia (nb_fan); empate → primero
        val best = pool.maxByOrNull { it.optInt("nb_fan", 0) }
            ?: return DeezerResult.NoResults
        val id = best.optLong("id", -1L)
        if (id <= 0) return DeezerResult.NoResults

        val pictureBig = best.optString("picture_big").takeIf { it.isNotBlank() }
        return DeezerResult.Found(
            DeezerArtist(
                id = id,
                name = best.optString("name"),
                pictureBig = pictureBig
            )
        )
    }

    /** Normalización: minúsculas + trim (sin tocar espacios internos). */
    private fun normalize(value: String): String = value.trim().lowercase()
}