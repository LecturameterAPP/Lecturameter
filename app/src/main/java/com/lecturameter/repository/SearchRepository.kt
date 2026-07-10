// Fase 1.3: SearchRepository — pila de busqueda migrada INTEGRA desde MainActivity.kt
// (supervisorScope, KNOWN_SPANISH_EDITIONS, aliases ES-EN, scoring, isCoverUrlValid,
//  consensusPages, Wikidata SPARQL, MangaDex + AniList + MangaUpdates). Sin cambios de logica.
// Mantiene package com.lecturameter para no romper referencias; el paquete cambiara al final de la Fase 1.
package com.lecturameter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.getValue
import android.widget.Toast
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import java.util.concurrent.TimeUnit
import com.lecturameter.model.*
import com.lecturameter.utils.*

data class OpenLibraryResult(
    val title: String,
    val author: String,
    val pages: Int,
    val coverUrl: String?,
    val isbn: String?,
    val genre: String,
    val publishYear: String,
    val olKey: String = "",
    // v2.6 (búsqueda r1): idioma del resultado ("es","en","ca","fr"…, "" si desconocido)
    val language: String = "",
    // v2.6: todos los autores concatenados — solo para scoring de relevancia (no UI)
    val matchAuthors: String = ""
)

// v2.6: mapea códigos MARC de OpenLibrary (3 letras) a ISO-639-1 de la app
private fun olLangToCode(l: String): String = when (l.lowercase(Locale.ROOT)) {
    "spa" -> "es"; "eng" -> "en"; "cat" -> "ca"
    "fre", "fra" -> "fr"; "ger", "deu" -> "de"; "ita" -> "it"; "por" -> "pt"
    else -> l.take(2).lowercase(Locale.ROOT)
}

// v2.6: código app (2 letras) → código MARC de OpenLibrary
private fun appLangToOl(code: String): String = when (code.lowercase(Locale.ROOT)) {
    "es" -> "spa"; "en" -> "eng"; "ca" -> "cat"; else -> "eng"
}

// v2.6: stopwords ES/EN que no aportan a la relevancia de tokens
private val SEARCH_STOPWORDS = setOf(
    "the", "and", "los", "las", "del", "una", "unos", "unas", "para", "con", "por", "que"
)

// v2.6: tokens significativos de una query (normalizados, sin stopwords, len>=3).
// Tokens 100% numéricos fuera: un ISBN escaneado o un año nunca aparecen en el
// título → filtrar por ellos vaciaría los resultados (query ISBN → tokens vacíos
// → relevancia 1.0 → sin filtro, comportamiento correcto).
private fun searchQueryTokens(query: String): Set<String> =
    normalizedEditionText(query).split(" ")
        .filter { it.length >= 3 && it !in SEARCH_STOPWORDS && !it.all { c -> c.isDigit() } }
        .toSet()

// v2.6: fracción de tokens de la query presentes en título+autores del resultado.
// 1.0 = todos los tokens; 0.0 = ninguno. Query sin tokens útiles → 1.0 (no filtrar).
private fun searchRelevance(tokens: Set<String>, title: String, authors: String): Double {
    if (tokens.isEmpty()) return 1.0
    val hay = normalizedEditionText("$title $authors")
    return tokens.count { hay.contains(it) }.toDouble() / tokens.size
}

data class BookMetadata(
    val coverUrl: String? = null,
    val genres: List<String> = emptyList()
)

// ── Google Books search ───────────────────────────────────────────────────────

private fun fetchGoogleBooksResults(query: String, maxResults: Int = 15, preferredLang: String = "es"): List<OpenLibraryResult> {
    val results = mutableListOf<OpenLibraryResult>()
    val seenTitles = mutableSetOf<String>()

    fun parseGbItems(items: JSONArray?) {
        if (items == null) return
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val info = item.optJSONObject("volumeInfo") ?: continue
            val title = info.optString("title", "").takeIf { it.isNotBlank() } ?: continue
            val norm = title.lowercase().trim()
            if (norm in seenTitles) continue
            seenTitles.add(norm)
            val authorsArr = info.optJSONArray("authors")
            val author = authorsArr?.optString(0, "") ?: ""
            // v2.6: idioma del volumen + lista completa de autores para relevancia
            val gbLanguage = info.optString("language", "").lowercase(Locale.ROOT)
            val allAuthors = buildString {
                if (authorsArr != null) for (j in 0 until authorsArr.length()) {
                    append(authorsArr.optString(j, "")); append(' ')
                }
            }.trim()
            // Feedback 2.7: pageCount implausible (<40) → 0 (desconocido)
            val pages = info.optInt("pageCount", 0).takeIf { it >= MIN_PLAUSIBLE_PAGES } ?: 0
            val publishYear = info.optString("publishedDate", "").take(4)
            val imageLinks = info.optJSONObject("imageLinks")
            val coverUrl = imageLinks?.optString("thumbnail")
                ?.replace("http://", "https://")
                ?.replace("&edge=curl", "")
                ?.let { if (it.isNotBlank()) it else null }
            val identifiers = info.optJSONArray("industryIdentifiers")
            var isbn13: String? = null; var isbn10: String? = null
            if (identifiers != null) {
                for (j in 0 until identifiers.length()) {
                    val id = identifiers.getJSONObject(j)
                    val type = id.optString("type", ""); val identifier = id.optString("identifier", "")
                    if (type == "ISBN_13" && isbn13 == null) isbn13 = identifier
                    if (type == "ISBN_10" && isbn10 == null) isbn10 = identifier
                }
            }
            val isbn = isbn13 ?: isbn10
            val categories = info.optJSONArray("categories")
            var genre = ""
            if (categories != null) {
                val rawCats = mutableListOf<String>()
                for (j in 0 until categories.length()) rawCats.add(categories.optString(j, ""))
                val mapped = bestGenreFromRawCandidates(rawCats)
                genre = if (mapped.isNotEmpty()) mapped.joinToString("; ")
                        else if (categories.length() > 0) categories.optString(0, "") else ""
            }
            results.add(OpenLibraryResult(title, author, pages, coverUrl, isbn, genre, publishYear,
                "gb_${item.optString("id")}", gbLanguage, allAuthors))
        }
    }

    // v19.2: Dos búsquedas. Antes solo buscaba con langRestrict=es, lo que mataba resultados
    // de manga, cómics y cualquier título internacional. Ahora:
    //   1) SIN langRestrict (encuentra todo: manga, inglés, francés, etc.)
    //   2) CON langRestrict=es (boost ediciones españolas, que son las que el usuario
    //      probablemente busca pero que a menudo no son el top result global en GB).
    // Ambos mergeados con dedup por título normalizado.
    val encoded = URLEncoder.encode(query, "UTF-8")
    try {
        val url1 = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=$maxResults&printType=books"
        val conn1 = URL(url1).openConnection() as HttpURLConnection
        conn1.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn1.connectTimeout = 8000; conn1.readTimeout = 8000
        parseGbItems(JSONObject(conn1.inputStream.bufferedReader().readText()).optJSONArray("items"))
    } catch (_: Exception) {}
    // v2.6 (búsqueda r1): langRestrict = idioma de la app, no "es" fijo.
    try {
        val url2 = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=$maxResults&printType=books&langRestrict=$preferredLang"
        val conn2 = URL(url2).openConnection() as HttpURLConnection
        conn2.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn2.connectTimeout = 8000; conn2.readTimeout = 8000
        parseGbItems(JSONObject(conn2.inputStream.bufferedReader().readText()).optJSONArray("items"))
    } catch (_: Exception) {}
    // v2.6: 3) CON langRestrict=ca — ediciones catalanas (Sanderson en català etc.)
    //    nunca salían: ni el top global ni el filtro es las traían. Solo si app en español.
    if (preferredLang == "es") try {
        val url3 = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=10&printType=books&langRestrict=ca"
        val conn3 = URL(url3).openConnection() as HttpURLConnection
        conn3.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn3.connectTimeout = 8000; conn3.readTimeout = 8000
        parseGbItems(JSONObject(conn3.inputStream.bufferedReader().readText()).optJSONArray("items"))
    } catch (_: Exception) {}
    return results
}

// Auditoría APIs r2: User-Agent identificable con contacto. OpenLibrary da prioridad
// BAJA y limita agresivamente a clientes sin UA claro. Aplicado a TODAS las APIs.
internal const val APP_USER_AGENT = "Lecturameter/2.5 (lecturameter.app@gmail.com)"

// Auditoría APIs r3: Mutex corrutinas (idiomático) vs synchronized bloqueante.
internal object ApiThrottle {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val last = HashMap<String, Long>()
    private const val MIN_INTERVAL_MS = 200L
    suspend fun gate(host: String) {
        val wait = mutex.withLock {
            val now = System.currentTimeMillis()
            val slot = maxOf(now, (last[host] ?: 0L) + MIN_INTERVAL_MS)
            last[host] = slot
            slot - now
        }
        if (wait > 0) kotlinx.coroutines.delay(wait)
    }
    suspend fun gate(url: java.net.URL) = gate(url.host ?: "")
}

internal fun cleanCoverUrl(url: String?): String? =
    url?.takeIf { it.isNotBlank() }
        ?.replace("http://", "https://")
        ?.replace("&edge=curl", "")
        // Auditoría APIs: OL sirve -S/-M de baja resolución que Coil acepta sin error →
        // nunca se intentaba -L. Normalizar a -L en origen (solo covers.openlibrary.org).
        ?.let { u ->
            if (u.contains("covers.openlibrary.org"))
                u.replace(Regex("""-[SM]\.jpg""", RegexOption.IGNORE_CASE), "-L.jpg")
            else u
        }

/**
 * Valida que una URL de portada devuelva una imagen real (>8 KB).
 * Los placeholders tipo "COVER TO BE REVEALED" de TOR/Gollancz suelen
 * tener menos de 8 KB. Devuelve true si no se puede determinar el tamaño.
 */
private suspend fun isCoverUrlValid(url: String): Boolean = withContext(Dispatchers.IO) {
    try {
        ApiThrottle.gate(URL(url))   // throttling 200ms/host (auditoría r2)
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 3_000; conn.readTimeout = 3_000
        val code = conn.responseCode
        val len = conn.contentLength
        conn.disconnect()
        // Si el servidor no informa tamaño (len < 0) asumimos válida
        code == 200 && (len < 0 || len > 8_000)
    } catch (_: Exception) { true }
}

/**
 * Intenta obtener una portada válida (>8 KB) para el título+autor en español
 * buscando directamente en OpenLibrary con cover_i.
 * Se usa como fallback cuando la portada obtenida es un placeholder.
 */
private suspend fun fetchSpanishCoverByTitle(title: String, author: String): String? = withContext(Dispatchers.IO) {
    try {
        val q = URLEncoder.encode("$title $author", "UTF-8")
        val conn = URL("https://openlibrary.org/search.json?q=$q&language=spa&limit=5&fields=cover_i,isbn").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 5_000; conn.readTimeout = 5_000
        val docs = conn.inputStream.use { JSONObject(it.bufferedReader().readText()) }.optJSONArray("docs") ?: return@withContext null
        for (i in 0 until docs.length()) {
            val doc = docs.getJSONObject(i)
            val coverId = doc.optLong("cover_i", -1L)
            if (coverId > 0) {
                val candidate = "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                if (isCoverUrlValid(candidate)) return@withContext candidate
            }
        }
        // Fallback: buscar portada por ISBN de la primera entrada con ISBN
        for (i in 0 until docs.length()) {
            val doc = docs.getJSONObject(i)
            val isbnArr = doc.optJSONArray("isbn") ?: continue
            for (j in 0 until isbnArr.length()) {
                val isbn = isbnArr.optString(j, "")
                val candidate = "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false"
                try {
                    val c = URL(candidate).openConnection() as HttpURLConnection
                    c.connectTimeout = 2_000; c.readTimeout = 2_000
                    if (c.responseCode == 200) return@withContext candidate.removeSuffix("?default=false")
                } catch (_: Exception) {}
            }
        }
        null
    } catch (_: Exception) { null }
}
        // Mantenemos zoom=1 (thumbnail garantizado). zoom=0 a veces devuelve 1×1 px
        // transparente en libros sin imagen de alta resolución, lo que causa portadas rotas.
        // fetchGoogleBooksVolumeImage ya intenta extraLarge/large/medium antes del thumbnail.

/** Llama directamente al endpoint de un volumen para obtener la mejor imagen disponible. */
private fun fetchGoogleBooksVolumeImage(volumeId: String): String? {
    return try {
        val url = "https://www.googleapis.com/books/v1/volumes/${URLEncoder.encode(volumeId, "UTF-8")}"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        val info = JSONObject(conn.inputStream.bufferedReader().readText())
            .optJSONObject("volumeInfo") ?: return null
        val links = info.optJSONObject("imageLinks") ?: return null
        cleanCoverUrl(
            links.optString("extraLarge").ifBlank { null }
                ?: links.optString("large").ifBlank { null }
                ?: links.optString("medium").ifBlank { null }
                ?: links.optString("thumbnail").ifBlank { null }
        )
    } catch (_: Exception) { null }
}

// Géneros "de alto nivel" que sólo se eligen si no hay nada más concreto
private val GENERIC_GENRES = setOf("Otro", "Historia", "Drama", "Literatura clásica")

private fun bestGenreFromRawCandidates(candidates: List<String>): List<String> {
    val mapped = candidates
        .flatMap { it.split("/", ";", ",") }
        .map { it.trim() }
        .filter { it.length >= 3 }
        .distinct()
        .flatMap { mapApiGenre(it) }    // mapApiGenre ahora devuelve List<String>
        .filter { it.isNotBlank() }
    if (mapped.isEmpty()) return emptyList()
    // Preferir géneros específicos sobre genéricos
    val specific = mapped.filter { it !in GENERIC_GENRES }
    val pool = if (specific.isNotEmpty()) specific else mapped
    // Top 2 por número de votos; empate desempata por orden canónico en BOOK_GENRES
    val genreOrder = BOOK_GENRES.withIndex().associate { (i, g) -> g to i }
    return pool
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { genreOrder[it.key] ?: Int.MAX_VALUE }
        )
        .take(2)
        .map { it.key }
}

// Feedback 2.6: GET con control explícito del código HTTP y reintentos con backoff.
// Google Books sin API key devuelve 429 con frecuencia; HttpURLConnection en Android
// lo convierte en FileNotFoundException, así que la cadena de escaneo "fallaba" entera
// sin estarlo (ver ISBN SCAN LOG: P1 GB_isbn FAIL FileNotFoundException = HTTP 429).
private suspend fun httpGetTextWithRetry(url: String, tag: String, retries: Int = 2): String? {
    var attempt = 0
    while (true) {
        try {
            ApiThrottle.gate(java.net.URL(url))
            val conn = java.net.URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", APP_USER_AGENT)
            conn.connectTimeout = 6000; conn.readTimeout = 6000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code in 200..299) return conn.inputStream.use { it.bufferedReader().readText() }
            try { conn.errorStream?.close() } catch (_: Exception) {}
            val retryable = code == 429 || code >= 500
            com.lecturameter.utils.AppLogger.log(
                "$tag HTTP $code${if (retryable && attempt < retries) " → retry ${attempt + 1}" else ""}", "IsbnScan")
            if (!retryable || attempt >= retries) return null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.lecturameter.utils.AppLogger.log(
                "$tag ${e.javaClass.simpleName}: ${e.message}${if (attempt < retries) " → retry ${attempt + 1}" else ""}", "IsbnScan")
            if (attempt >= retries) return null
        }
        attempt++
        kotlinx.coroutines.delay(700L * attempt * attempt)   // 700ms, 2.8s
    }
}

// v20.9: Búsqueda completa por ISBN — devuelve título, autor, páginas, géneros y portada.
// Se usa en AddBookScreen al escanear un ISBN para autorrellenar el formulario.
data class IsbnFullMetadata(
    val title: String? = null,
    val author: String? = null,
    val pages: Int? = null,
    val genres: List<String> = emptyList(),
    val coverUrl: String? = null
)

// Feedback 2.6 (auditoría APIs): caché in-memory por ISBN — el mismo código escaneado
// dos veces en la sesión no repite la cadena completa (Google Books rate-limita sin
// API key). Solo se cachean resultados con algún dato útil.
private val isbnMetaCache = java.util.concurrent.ConcurrentHashMap<String, IsbnFullMetadata>()

// Feedback 2.7: umbral de plausibilidad de páginas — por debajo se considera ficha
// rota (sample, tomo suelto, error de catálogo tipo "Mitología japonesa" con 22 págs)
// salvo que TODAS las fuentes coincidan en un valor bajo (libro ilustrado legítimo).
private const val MIN_PLAUSIBLE_PAGES = 40

// Feedback 2.7: consenso de páginas multi-fuente. `votes` = (valor, esFuentePorEdición)
// en orden de fase (P1 → P4). Reglas: 1) descartar implausibles si hay alternativas,
// 2) un valor repetido en ≥2 fuentes gana (modo; empate → orden de fase), 3) si no,
// gana la primera fuente por edición y solo en último término las de nivel work
// (median de OL search, GB por título).
private fun consensusPages(votes: List<Pair<Int, Boolean>>): Int? {
    if (votes.isEmpty()) return null
    val plausible = votes.filter { it.first >= MIN_PLAUSIBLE_PAGES }
    val pool = if (plausible.isNotEmpty()) plausible else votes
    val counts = pool.groupingBy { it.first }.eachCount()
    val maxCount = counts.values.max()
    if (maxCount >= 2) {
        val winners = counts.filterValues { it == maxCount }.keys
        return pool.first { it.first in winners }.first
    }
    return (pool.firstOrNull { it.second } ?: pool.first()).first
}

internal suspend fun fetchIsbnFullMetadata(isbn: String): IsbnFullMetadata {
    if (isbn.isBlank()) return IsbnFullMetadata()
    isbnMetaCache[isbn]?.let {
        com.lecturameter.utils.AppLogger.log("fetchIsbnFullMetadata: isbn=$isbn → caché", "IsbnScan")
        return it
    }
    var title: String? = null
    var author: String? = null
    var pages: Int? = null
    val rawGenres = mutableListOf<String>()
    var coverUrl: String? = null
    val tStart = System.currentTimeMillis()
    com.lecturameter.utils.AppLogger.log("fetchIsbnFullMetadata: isbn=$isbn", "IsbnScan")

    // Feedback 2.7: cada fase VOTA sus páginas en vez de "la primera con valor gana".
    // `pages` pasa a ser provisional (solo valores plausibles) para el gating de fases;
    // el valor final lo decide consensusPages() al terminar la cadena.
    val pageVotes = mutableListOf<Pair<Int, Boolean>>()
    fun votePages(value: Int, editionLevel: Boolean) {
        if (value > 0) {
            pageVotes.add(value to editionLevel)
            if (pages == null && value >= MIN_PLAUSIBLE_PAGES) pages = value
        }
    }

    // Feedback 2.6: variante ISBN-10/13 — GB y OL a veces indexan una edición solo
    // bajo una de las dos formas; probamos ambas en cada fase.
    val isbnAlt: String? = when (isbn.length) {
        13 -> isbn13To10(isbn)
        10 -> isbn10To13(isbn)
        else -> null
    }
    val isbnCandidates = listOfNotNull(isbn, isbnAlt)

    // Absorbe un volumeInfo de Google Books rellenando solo lo que falte
    fun absorbGbVolumeInfo(info: JSONObject) {
        if (title.isNullOrBlank()) title = info.optString("title").ifBlank { null }
        if (author.isNullOrBlank()) {
            val authors = info.optJSONArray("authors")
            if (authors != null && authors.length() > 0) author = authors.optString(0).ifBlank { null }
        }
        votePages(info.optInt("pageCount", 0), editionLevel = true)  // Feedback 2.7: voto, no asignación
        info.optJSONArray("categories")?.let { cats ->
            for (i in 0 until cats.length()) rawGenres.add(cats.optString(i, ""))
        }
        if (coverUrl == null) {
            info.optJSONObject("imageLinks")?.let { links ->
                coverUrl = cleanCoverUrl(
                    links.optString("extraLarge").ifBlank { null }
                        ?: links.optString("large").ifBlank { null }
                        ?: links.optString("medium").ifBlank { null }
                        ?: links.optString("thumbnail").ifBlank { null }
                )
            }
        }
    }

    // 1. Google Books por ISBN (más fiable para título/autor/páginas).
    //    Feedback 2.6: reintentos ante 429/5xx (un rate-limit puntual de GB tumbaba la
    //    fase entera) + variante ISBN-10/13.
    run {
        val t0 = System.currentTimeMillis()
        var items: org.json.JSONArray? = null
        for (candidate in isbnCandidates) {
            val query = URLEncoder.encode("isbn:$candidate", "UTF-8")
            val body = httpGetTextWithRetry(
                "https://www.googleapis.com/books/v1/volumes?q=$query&maxResults=3&printType=books",
                "P1 GB_isbn($candidate)")
            items = body?.let { try { JSONObject(it).optJSONArray("items") } catch (_: Exception) { null } }
            if ((items?.length() ?: 0) > 0) break
        }
        items?.optJSONObject(0)?.optJSONObject("volumeInfo")?.let { absorbGbVolumeInfo(it) }
        com.lecturameter.utils.AppLogger.log(
            "P1 GB_isbn: items=${items?.length() ?: 0} title=${title != null} pages=${pages ?: "-"} cover=${coverUrl != null} ${System.currentTimeMillis() - t0}ms",
            "IsbnScan")
    }

    // 1b. Feedback 2.6: Google Books "Dynamic Links" (books.google.com) — host distinto
    //     que no comparte el rate-limit de googleapis.com. Resuelve el volumeId del ISBN
    //     (verificado con Wind and Truth Tor 9781250387202) y con él pedimos el volumen.
    if (title.isNullOrBlank() || pages == null) {
        val t0 = System.currentTimeMillis()
        var volumeId: String? = null
        for (candidate in isbnCandidates) {
            val body = httpGetTextWithRetry(
                "https://books.google.com/books?bibkeys=ISBN:$candidate&jscmd=viewapi",
                "P1b GB_bibkeys($candidate)")
            volumeId = body?.let { Regex("""[?&]id=([A-Za-z0-9_\-]+)""").find(it)?.groupValues?.get(1) }
            if (volumeId != null) break
        }
        if (volumeId != null) {
            val body = httpGetTextWithRetry(
                "https://www.googleapis.com/books/v1/volumes/${URLEncoder.encode(volumeId, "UTF-8")}",
                "P1b GB_volume")
            val info = body?.let { try { JSONObject(it).optJSONObject("volumeInfo") } catch (_: Exception) { null } }
            if (info != null) absorbGbVolumeInfo(info)
        }
        com.lecturameter.utils.AppLogger.log(
            "P1b GB_bibkeys: volumeId=${volumeId ?: "-"} title=${title != null} pages=${pages ?: "-"} ${System.currentTimeMillis() - t0}ms",
            "IsbnScan")
    } else {
        com.lecturameter.utils.AppLogger.log("P1b GB_bibkeys SKIP (P1 completo)", "IsbnScan")
    }

    // 2. OpenLibrary Books API — complementa lo que falte (Feedback 2.6: variante 10/13)
    try {
        val t0 = System.currentTimeMillis()
        var book: JSONObject? = null
        for (candidate in isbnCandidates) {
            val body = httpGetTextWithRetry(
                "https://openlibrary.org/api/books?bibkeys=ISBN:$candidate&jscmd=data&format=json",
                "P2 OL_bibkeys($candidate)") ?: continue
            book = try { JSONObject(body).optJSONObject("ISBN:$candidate") } catch (_: Exception) { null }
            if (book != null) break
        }
        if (book != null) {
            if (title.isNullOrBlank()) title = book.optString("title").ifBlank { null }
            if (author.isNullOrBlank()) {
                val authors = book.optJSONArray("authors")
                if (authors != null && authors.length() > 0)
                    author = authors.optJSONObject(0)?.optString("name")?.ifBlank { null }
            }
            votePages(book.optInt("number_of_pages", 0), editionLevel = true)  // Feedback 2.7
            if (coverUrl == null) {
                val coverObj = book.optJSONObject("cover")
                coverUrl = coverObj?.let {
                    it.optString("large").ifBlank { null }
                        ?: it.optString("medium").ifBlank { null }
                        ?: it.optString("small").ifBlank { null }
                }
            }
            val subjects = book.optJSONArray("subjects")
            if (subjects != null) for (i in 0 until subjects.length())
                rawGenres.add(subjects.getJSONObject(i).optString("name", ""))
        }
        com.lecturameter.utils.AppLogger.log(
            "P2 OL_bibkeys: found=${book != null} pages=${pages ?: "-"} cover=${coverUrl != null} ${System.currentTimeMillis() - t0}ms",
            "IsbnScan")
    } catch (e: Exception) {
        com.lecturameter.utils.AppLogger.log("P2 OL_bibkeys FAIL: ${e.javaClass.simpleName}: ${e.message}", "IsbnScan")
    }

    // 3. v2.5: OpenLibrary edición directa (/isbn/{isbn}.json) — cubre ISBNs que
    //    bibkeys y Google Books no indexan (p. ej. Wind and Truth Tor/Gollancz,
    //    El Libro Hueco ed. ampliada 9788412596625). Trae number_of_pages fiable.
    if (title.isNullOrBlank() || pages == null || coverUrl == null) {
        try {
            val t0 = System.currentTimeMillis()
            var ed: JSONObject? = null
            for (candidate in isbnCandidates) {
                val body = httpGetTextWithRetry("https://openlibrary.org/isbn/$candidate.json", "P3 OL_isbn($candidate)") ?: continue
                ed = try { JSONObject(body) } catch (_: Exception) { null }
                if (ed != null) break
            }
            if (ed != null) {
                if (title.isNullOrBlank()) title = ed.optString("title").ifBlank { null }
                votePages(ed.optInt("number_of_pages", 0), editionLevel = true)  // Feedback 2.7
                if (coverUrl == null) {
                    val coverId = ed.optJSONArray("covers")?.optLong(0, 0L) ?: 0L
                    if (coverId > 0) coverUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                }
                if (author.isNullOrBlank()) {
                    // authors en la edición vienen como refs /authors/OLxxxA → resolver el primero
                    val ref = ed.optJSONArray("authors")?.optJSONObject(0)?.optString("key", "").orEmpty()
                    if (ref.isNotBlank()) {
                        val body = httpGetTextWithRetry("https://openlibrary.org$ref.json", "P3 OL_author")
                        author = body?.let { try { JSONObject(it).optString("name").ifBlank { null } } catch (_: Exception) { null } }
                    }
                }
            }
            com.lecturameter.utils.AppLogger.log(
                "P3 OL_isbn: pages=${pages ?: "-"} title=${title != null} cover=${coverUrl != null} ${System.currentTimeMillis() - t0}ms",
                "IsbnScan")
        } catch (e: Exception) {
            com.lecturameter.utils.AppLogger.log("P3 OL_isbn FAIL: ${e.javaClass.simpleName}: ${e.message}", "IsbnScan")
        }
    } else {
        com.lecturameter.utils.AppLogger.log("P3 OL_isbn SKIP (todo completo con P1+P2)", "IsbnScan")
    }

    // 3b. Feedback 2.6: red de seguridad — índice de búsqueda de OL por ISBN (cubre
    //     ediciones que /isbn y bibkeys no devuelven).
    if (title.isNullOrBlank() || author.isNullOrBlank() || coverUrl == null) {
        val t0 = System.currentTimeMillis()
        for (candidate in isbnCandidates) {
            val body = httpGetTextWithRetry(
                "https://openlibrary.org/search.json?q=isbn:$candidate&limit=1&fields=title,author_name,cover_i,number_of_pages_median,subject",
                "P3b OL_search($candidate)") ?: continue
            val doc = try { JSONObject(body).optJSONArray("docs")?.optJSONObject(0) } catch (_: Exception) { null } ?: continue
            if (title.isNullOrBlank()) title = doc.optString("title").ifBlank { null }
            if (author.isNullOrBlank()) author = doc.optJSONArray("author_name")?.optString(0, "")?.ifBlank { null }
            // Feedback 2.7: median de OL search = nivel work (puede ser de otra edición/idioma)
            votePages(doc.optInt("number_of_pages_median", 0), editionLevel = false)
            if (coverUrl == null) doc.optLong("cover_i", -1L).takeIf { it > 0 }?.let { coverUrl = "https://covers.openlibrary.org/b/id/$it-L.jpg" }
            doc.optJSONArray("subject")?.let { subj ->
                for (i in 0 until minOf(subj.length(), 10)) rawGenres.add(subj.optString(i, ""))
            }
            break
        }
        com.lecturameter.utils.AppLogger.log(
            "P3b OL_search: title=${title != null} author=${author != null} pages=${pages ?: "-"} ${System.currentTimeMillis() - t0}ms",
            "IsbnScan")
    } else {
        com.lecturameter.utils.AppLogger.log("P3b OL_search SKIP", "IsbnScan")
    }

    // 4. v2.5: páginas aún desconocidas pero título sí → Google Books por título+autor
    //    (fallo reportado: El Nombre del Viento / El Temor de un Hombre Sabio sin páginas)
    if (pages == null && !title.isNullOrBlank()) {
        try {
            val t0 = System.currentTimeMillis()
            val q = URLEncoder.encode("intitle:${title} ${author.orEmpty()}".trim(), "UTF-8")
            val body = httpGetTextWithRetry(
                "https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=5&printType=books",
                "P4 GB_title+author")
            val items = body?.let { try { JSONObject(it).optJSONArray("items") } catch (_: Exception) { null } }
            if (items != null) {
                for (i in 0 until items.length()) {
                    val pg = items.optJSONObject(i)?.optJSONObject("volumeInfo")?.optInt("pageCount", 0) ?: 0
                    // Feedback 2.7: GB por título+autor = nivel work (edición indeterminada)
                    if (pg > 0) { votePages(pg, editionLevel = false); break }
                }
            }
            com.lecturameter.utils.AppLogger.log(
                "P4 GB_title+author: items=${items?.length() ?: 0} pages=${pages ?: "-"} ${System.currentTimeMillis() - t0}ms",
                "IsbnScan")
        } catch (e: Exception) {
            com.lecturameter.utils.AppLogger.log("P4 GB_title+author FAIL: ${e.javaClass.simpleName}: ${e.message}", "IsbnScan")
        }
    } else if (pages != null) {
        com.lecturameter.utils.AppLogger.log("P4 GB_title+author SKIP (páginas ya obtenidas)", "IsbnScan")
    } else {
        com.lecturameter.utils.AppLogger.log("P4 GB_title+author SKIP (sin título para query)", "IsbnScan")
    }

    // Feedback 2.7: resolución final de páginas por consenso entre todas las fuentes
    pages = consensusPages(pageVotes)

    val genres = bestGenreFromRawCandidates(rawGenres)
    com.lecturameter.utils.AppLogger.log(
        "RESULTADO: title=${title != null} author=${author != null} pages=${pages ?: "-"} votos=${pageVotes.map { it.first }} genres=${genres.size} cover=${coverUrl != null} total=${System.currentTimeMillis() - tStart}ms",
        "IsbnScan")
    val result = IsbnFullMetadata(title = title, author = author, pages = pages, genres = genres, coverUrl = coverUrl)
    if (title != null || pages != null || coverUrl != null) isbnMetaCache[isbn] = result
    return result
}

private fun fetchGoogleBooksMetadata(title: String, author: String, isbn: String?): BookMetadata {
    val rawGenres = mutableListOf<String>()
    var coverUrl: String? = null
    val volumeIds = mutableListOf<String>()
    val queries = buildList {
        if (!isbn.isNullOrBlank()) add(URLEncoder.encode("isbn:$isbn", "UTF-8"))
        add(URLEncoder.encode("intitle:$title inauthor:$author", "UTF-8"))
        add(URLEncoder.encode("$title $author", "UTF-8"))
    }.distinct()
    for (query in queries) {
        try {
            val url = "https://www.googleapis.com/books/v1/volumes?q=$query&maxResults=8&printType=books"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", APP_USER_AGENT)
            conn.connectTimeout = 6000; conn.readTimeout = 6000
            val items = conn.inputStream.use { JSONObject(it.bufferedReader().readText()) }.optJSONArray("items") ?: continue
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val info = item.optJSONObject("volumeInfo") ?: continue
                val vid = item.optString("id").takeIf { it.isNotBlank() }
                if (vid != null) volumeIds.add(vid)
                val imageLinks = info.optJSONObject("imageLinks")
                if (coverUrl == null && imageLinks != null) {
                    coverUrl = cleanCoverUrl(
                        imageLinks.optString("extraLarge").ifBlank { null }
                            ?: imageLinks.optString("large").ifBlank { null }
                            ?: imageLinks.optString("medium").ifBlank { null }
                            ?: imageLinks.optString("thumbnail").ifBlank { null }
                    )
                }
                val categories = info.optJSONArray("categories")
                if (categories != null) {
                    for (j in 0 until categories.length()) rawGenres.add(categories.optString(j, ""))
                }
                // La descripción NO se añade: su texto narrativo activa reglas de género
                // por coincidencia de palabras (guerra, historia, aventura…) sin ser el género real.
            }
        } catch (_: Exception) {}
        if (coverUrl != null && bestGenreFromRawCandidates(rawGenres).isNotEmpty()) break
    }
    // Fallback: llamada directa al volume ID para obtener extraLarge/large
    // que la búsqueda general no expone en imageLinks
    if (coverUrl == null) {
        for (vid in volumeIds.take(3)) {
            coverUrl = fetchGoogleBooksVolumeImage(vid)
            if (coverUrl != null) break
        }
    }
    return BookMetadata(coverUrl = coverUrl, genres = bestGenreFromRawCandidates(rawGenres))
}

private fun fetchOpenLibraryMetadata(title: String, author: String, isbn: String?): BookMetadata {
    val rawGenres = mutableListOf<String>()
    var coverUrl: String? = null
    val queries = buildList {
        if (!isbn.isNullOrBlank()) add("isbn:${URLEncoder.encode(isbn, "UTF-8")}")
        add("q=${URLEncoder.encode("$title $author", "UTF-8")}")
        add("title=${URLEncoder.encode(title, "UTF-8")}&author=${URLEncoder.encode(author, "UTF-8")}")
    }.distinct()
    for (query in queries) {
        try {
            val url = "https://openlibrary.org/search.json?$query&limit=5&fields=key,cover_i,isbn,subject"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", APP_USER_AGENT)
            conn.connectTimeout = 6000; conn.readTimeout = 6000
            val docs = conn.inputStream.use { JSONObject(it.bufferedReader().readText()) }.optJSONArray("docs") ?: continue
            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                val coverId = doc.optLong("cover_i", -1L)
                if (coverUrl == null && coverId > 0) coverUrl = "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                val isbns = doc.optJSONArray("isbn")
                if (coverUrl == null && isbns != null) {
                    for (j in 0 until isbns.length()) {
                        val value = isbns.optString(j, "").trim()
                        if (value.length >= 10) {
                            coverUrl = "https://covers.openlibrary.org/b/isbn/$value-L.jpg"
                            break
                        }
                    }
                }
                val subjects = doc.optJSONArray("subject")
                if (subjects != null) {
                    for (j in 0 until subjects.length()) rawGenres.add(subjects.optString(j, ""))
                }
                val key = doc.optString("key", "")
                if (key.startsWith("/works/")) {
                    try {
                        val workConn = URL("https://openlibrary.org$key.json").openConnection() as HttpURLConnection
                        workConn.setRequestProperty("User-Agent", APP_USER_AGENT)
                        workConn.connectTimeout = 5000; workConn.readTimeout = 5000
                        val work = JSONObject(workConn.inputStream.bufferedReader().readText())
                        val workSubjects = work.optJSONArray("subjects")
                        if (workSubjects != null) {
                            for (j in 0 until workSubjects.length()) rawGenres.add(workSubjects.optString(j, ""))
                        }
                        // La descripción del work NO se añade a rawGenres (misma razón que en GB).
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        if (coverUrl != null && bestGenreFromRawCandidates(rawGenres).isNotEmpty()) break
    }
    return BookMetadata(coverUrl = coverUrl, genres = bestGenreFromRawCandidates(rawGenres))
}


// ── Open Library /api/books ───────────────────────────────────────────────────
// Endpoint distinto a search.json: cover URLs directas + subjects estructurados
private fun fetchOpenLibraryBooksApi(isbn: String?): BookMetadata {
    if (isbn.isNullOrBlank()) return BookMetadata()
    return try {
        val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&jscmd=data&format=json"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val root = JSONObject(conn.inputStream.bufferedReader().readText())
        val book = root.optJSONObject("ISBN:$isbn") ?: return BookMetadata()
        // Portada: el endpoint expone large, medium, small directamente
        val coverObj = book.optJSONObject("cover")
        val coverUrl = coverObj?.let {
            it.optString("large").ifBlank { null }
                ?: it.optString("medium").ifBlank { null }
                ?: it.optString("small").ifBlank { null }
        }
        // Géneros: subjects es array de {name, url}
        val rawGenres = mutableListOf<String>()
        val subjects = book.optJSONArray("subjects")
        if (subjects != null) {
            for (i in 0 until subjects.length()) {
                rawGenres.add(subjects.getJSONObject(i).optString("name", ""))
            }
        }
        BookMetadata(coverUrl = coverUrl, genres = bestGenreFromRawCandidates(rawGenres))
    } catch (_: Exception) { BookMetadata() }
}

// ── AniList GraphQL ───────────────────────────────────────────────────────────
// API gratuita sin key. Cubre manga, manhwa, light novels y novelas gráficas a
// nivel de SERIE (no de tomo individual — para eso usamos MangaDex, ver abajo).
// v19.0: solo se llama cuando isManga()==true (ver fetchBookMetadata) y el resultado
// se valida contra el título buscado antes de usarlo — antes se llamaba para
// CUALQUIER libro y se aceptaba ciegamente el primer resultado de AniList aunque
// no tuviera relación real con el título (la búsqueda de AniList es muy sensible:
// acentos, subtítulos y romanización rompen el match).
private fun fetchAniListMetadata(title: String, _author: String): BookMetadata {
    return try {
        // Construir el payload con JSONObject para evitar problemas de escape
        val variables = org.json.JSONObject().put("search", title)
        val payload = org.json.JSONObject()
            .put("query", "query(${"$"}search:String){Media(search:${"$"}search,type:MANGA,sort:SEARCH_MATCH){title{romaji english}coverImage{extraLarge large}genres}}")
            .put("variables", variables)
            .toString()
        val conn = URL("https://graphql.anilist.co").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        conn.doOutput = true
        conn.outputStream.use { it.write(payload.toByteArray()) }
        val media = JSONObject(conn.inputStream.bufferedReader().readText())
            .optJSONObject("data")
            ?.optJSONObject("Media") ?: return BookMetadata()

        // Validación de pertenencia: el título devuelto por AniList debe parecerse al buscado.
        val mediaTitle = media.optJSONObject("title")
        val candidateTitles = listOfNotNull(mediaTitle?.optString("romaji"), mediaTitle?.optString("english"))
        val qNorm = normalizedEditionText(title)
        val qTokens = qNorm.split(" ").filter { it.length >= 3 }.toSet()
        val belongsToQuery = qTokens.isEmpty() || candidateTitles.any { c ->
            val cNorm = normalizedEditionText(c)
            cNorm.isNotBlank() && (cNorm == qNorm || qTokens.count { t -> cNorm.contains(t) }.toDouble() / qTokens.size >= 0.5)
        }
        if (!belongsToQuery) return BookMetadata()

        val coverImg = media.optJSONObject("coverImage")
        val coverUrl = coverImg?.let {
            it.optString("extraLarge").ifBlank { null }
                ?: it.optString("large").ifBlank { null }
        }
        val rawGenres = mutableListOf<String>()
        val genres = media.optJSONArray("genres")
        if (genres != null) for (i in 0 until genres.length()) rawGenres.add(genres.optString(i, ""))
        // "manga" forzado: ya hemos confirmado pertenencia, así que el género Manga es seguro.
        BookMetadata(coverUrl = coverUrl, genres = bestGenreFromRawCandidates(rawGenres + listOf("manga")))
    } catch (_: Exception) { BookMetadata() }
}

// ── MangaUpdates REST API ─────────────────────────────────────────────────────
// v2.6: Tercera fuente manga (gratuita, sin key). Nivel de SERIE, como AniList.
// Aporta: cobertura de series que AniList no indexa bien (manhwa antiguos,
// títulos con romanización distinta) + portada y géneros extra para la votación.
// POST https://api.mangaupdates.com/v1/series/search  {"search": "<título>"}
private fun fetchMangaUpdatesMetadata(title: String): BookMetadata {
    return try {
        val searchTitle = title.replace(Regex("""(?i)\b(?:tomo|vol\.?|volumen|#)\s*0*\d{1,3}\b"""), "").trim()
        val payload = org.json.JSONObject().put("search", searchTitle).put("perpage", 3).toString()
        val conn = URL("https://api.mangaupdates.com/v1/series/search").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        conn.doOutput = true
        conn.outputStream.use { it.write(payload.toByteArray()) }
        val results = JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("results")
            ?: return BookMetadata()

        // Validación de pertenencia (mismo criterio que AniList): >=50% de tokens del query
        val qNorm = normalizedEditionText(searchTitle)
        val qTokens = qNorm.split(" ").filter { it.length >= 3 }.toSet()
        for (i in 0 until results.length()) {
            val rec = results.getJSONObject(i).optJSONObject("record") ?: continue
            val recTitle = rec.optString("title", "")
            val cNorm = normalizedEditionText(recTitle)
            val belongs = qTokens.isEmpty() || cNorm == qNorm ||
                (cNorm.isNotBlank() && qTokens.count { t -> cNorm.contains(t) }.toDouble() / qTokens.size >= 0.5)
            if (!belongs) continue
            val coverUrl = rec.optJSONObject("image")?.optJSONObject("url")
                ?.optString("original")?.ifBlank { null }
            val rawGenres = mutableListOf<String>()
            val genres = rec.optJSONArray("genres")
            if (genres != null) for (j in 0 until genres.length())
                genres.optJSONObject(j)?.optString("genre", "")?.takeIf { it.isNotBlank() }?.let { rawGenres.add(it) }
            return BookMetadata(coverUrl = coverUrl, genres = bestGenreFromRawCandidates(rawGenres + listOf("manga")))
        }
        BookMetadata()
    } catch (_: Exception) { BookMetadata() }
}

// ── Detección de número de tomo/volumen ──────────────────────────────────────
// Usado para: (a) heurística de respaldo de isManga cuando GB/OL no traen género,
// y (b) pedir a MangaDex la portada del TOMO exacto, no la de la serie completa.
private fun extractVolumeNumber(title: String): Int? =
    Regex("""(?i)\b(?:tomo|vol\.?|volumen|#)\s*0*(\d{1,3})\b""").find(title)?.groupValues?.get(1)?.toIntOrNull()

// ── MangaDex REST API ─────────────────────────────────────────────────────────
// Sin key. A diferencia de AniList (datos a nivel de SERIE), MangaDex expone
// portadas POR TOMO vía su endpoint /cover con campo "volume" — esto es justo lo
// que faltaba: información real por tomo, no solo de la serie en conjunto.
private fun fetchMangaDexMetadata(title: String, volumeNum: Int?): BookMetadata {
    return try {
        // Quitar el marcador de tomo/volumen para buscar la SERIE (MangaDex indexa por serie)
        val seriesTitle = title.replace(Regex("""(?i)\b(?:tomo|vol\.?|volumen|#)\s*0*\d{1,3}\b"""), "").trim()
        val q = seriesTitle.ifBlank { title }
        val searchUrl = "https://api.mangadex.org/manga?title=${URLEncoder.encode(q, "UTF-8")}&limit=5"
        val conn = URL(searchUrl).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 7000; conn.readTimeout = 7000
        val root = JSONObject(conn.inputStream.bufferedReader().readText())
        val data = root.optJSONArray("data") ?: return BookMetadata()

        // Elegir el mejor match por similitud de título normalizado — evita falsos positivos
        // (mismo problema que AniList: sin esto, cualquier serie parecida contamina los datos).
        val qNorm = normalizedEditionText(q)
        val qTokens = qNorm.split(" ").filter { it.length >= 3 }.toSet()
        var bestIdx = -1; var bestScore = 0.0
        for (i in 0 until data.length()) {
            val attrs = data.getJSONObject(i).optJSONObject("attributes") ?: continue
            val candidateTitles = mutableListOf<String>()
            attrs.optJSONObject("title")?.let { t -> t.keys().forEach { k -> candidateTitles.add(t.optString(k, "")) } }
            attrs.optJSONArray("altTitles")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val obj = arr.getJSONObject(j)
                    obj.keys().forEach { k -> candidateTitles.add(obj.optString(k, "")) }
                }
            }
            for (t in candidateTitles) {
                if (t.isBlank()) continue
                val tNorm = normalizedEditionText(t)
                val score = when {
                    tNorm == qNorm -> 1.0
                    qTokens.isEmpty() -> 0.0
                    else -> qTokens.count { tNorm.contains(it) }.toDouble() / qTokens.size
                }
                if (score > bestScore) { bestScore = score; bestIdx = i }
            }
        }
        if (bestIdx == -1 || bestScore < 0.5) return BookMetadata()  // sin match fiable

        val manga = data.getJSONObject(bestIdx)
        val mangaId = manga.optString("id", "")
        val rawGenres = mutableListOf<String>()
        manga.optJSONObject("attributes")?.optJSONArray("tags")?.let { tags ->
            for (i in 0 until tags.length()) {
                tags.getJSONObject(i).optJSONObject("attributes")?.optJSONObject("name")
                    ?.optString("en", "")?.let { if (it.isNotBlank()) rawGenres.add(it) }
            }
        }

        // Portada del tomo exacto si lo conocemos; si no, la primera portada disponible.
        var coverUrl: String? = null
        if (mangaId.isNotBlank()) {
            try {
                val coverReq = "https://api.mangadex.org/cover?manga[]=$mangaId&limit=100"
                val cConn = URL(coverReq).openConnection() as HttpURLConnection
                cConn.setRequestProperty("User-Agent", APP_USER_AGENT)
                cConn.connectTimeout = 6000; cConn.readTimeout = 6000
                val covers = JSONObject(cConn.inputStream.bufferedReader().readText()).optJSONArray("data")
                if (covers != null) {
                    var fallbackFile: String? = null
                    for (i in 0 until covers.length()) {
                        val cAttrs = covers.getJSONObject(i).optJSONObject("attributes") ?: continue
                        val fileName = cAttrs.optString("fileName", "")
                        if (fileName.isBlank()) continue
                        if (fallbackFile == null) fallbackFile = fileName
                        if (volumeNum != null && cAttrs.optString("volume", "").toIntOrNull() == volumeNum) {
                            coverUrl = "https://uploads.mangadex.org/covers/$mangaId/$fileName"
                            break
                        }
                    }
                    if (coverUrl == null) fallbackFile?.let { coverUrl = "https://uploads.mangadex.org/covers/$mangaId/$it" }
                }
            } catch (_: Exception) {}
        }

        BookMetadata(coverUrl = coverUrl, genres = bestGenreFromRawCandidates(rawGenres + listOf("manga")))
    } catch (_: Exception) { BookMetadata() }
}

// chooseBetterGenre eliminado en v8.0; reemplazado por votación en fetchBookMetadata

suspend fun fetchBookMetadata(title: String, author: String, isbn: String?): BookMetadata = withContext(Dispatchers.IO) {
    coroutineScope {
        // Fuentes base — SIEMPRE se consultan, para cualquier libro
        val googleDeferred = async { fetchGoogleBooksMetadata(title, author, isbn) }
        val olDeferred     = async { fetchOpenLibraryMetadata(title, author, isbn) }
        val olBooksDeferred = async { fetchOpenLibraryBooksApi(isbn) }

        val googleBooks = googleDeferred.await()
        val openLibrary = olDeferred.await()
        val olBooks     = olBooksDeferred.await()

        // ═════════════════════════════════════════════════════════════════════
        // v19.0 — Gating de manga: AniList y MangaDex SOLO se llaman si el libro
        // es (probablemente) manga. Antes AniList se llamaba SIEMPRE, para
        // cualquier novela o ensayo, desperdiciando la llamada y arriesgando
        // contaminar la votación de género con falsos positivos.
        //   Señal 1: GB/OL/OLBooks ya devolvieron género Manga o Cómics.
        //   Señal 2 (respaldo): el título tiene marcador de tomo ("Tomo 3",
        //   "Vol. 12") — cubre el caso más común de fallo, que es tomos de
        //   manga mal catalogados en GB/OL sin género asignado.
        // ═════════════════════════════════════════════════════════════════════
        val baseGenres = openLibrary.genres + googleBooks.genres + olBooks.genres
        val volumeNum = extractVolumeNumber(title)
        val isManga = baseGenres.any { it == "Manga" || it == "Cómics y novela gráfica" } || volumeNum != null

        val aniList: BookMetadata
        val mangaDex: BookMetadata
        val mangaUpdates: BookMetadata
        if (isManga) {
            val aniListDeferred = async { fetchAniListMetadata(title, author) }
            val mangaDexDeferred = async { fetchMangaDexMetadata(title, volumeNum) }
            val mangaUpdatesDeferred = async { fetchMangaUpdatesMetadata(title) }  // v2.6
            aniList = aniListDeferred.await()
            mangaDex = mangaDexDeferred.await()
            mangaUpdates = mangaUpdatesDeferred.await()
        } else {
            aniList = BookMetadata()
            mangaDex = BookMetadata()
            mangaUpdates = BookMetadata()
        }

        // Portada: Google Books > MangaDex (portada del TOMO exacto si es manga) >
        // OL /api/books > OL search > AniList (solo serie completa, último recurso)
        val coverUrl = googleBooks.coverUrl
            ?: mangaDex.coverUrl
            ?: olBooks.coverUrl
            ?: openLibrary.coverUrl
            ?: aniList.coverUrl
            ?: mangaUpdates.coverUrl

        // Género: votar entre todas las fuentes y devolver top 2
        val allGenres = baseGenres + aniList.genres + mangaDex.genres + mangaUpdates.genres
        val genres = if (allGenres.isEmpty()) emptyList()
        else {
            val genreOrder = BOOK_GENRES.withIndex().associate { (i, g) -> g to i }
            val specific = allGenres.filter { it !in GENERIC_GENRES }
            val pool = if (specific.isNotEmpty()) specific else allGenres
            pool.groupingBy { it }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { genreOrder[it.key] ?: Int.MAX_VALUE })
                .take(2).map { it.key }
        }

        BookMetadata(coverUrl = coverUrl, genres = genres)
    }
}

// ── Búsqueda de ediciones de un libro ────────────────────────────────────────
// Devuelve una lista de BookEdition para el selector de "Cambiar edición".
// Estrategia: OpenLibrary Works API primero (mejor para múltiples ediciones),
// Google Books con langRestrict como complemento y fallback.

data class EditionResult(
    val language: String,
    val languageLabel: String,
    val flag: String,
    val title: String,
    val pages: Int,
    val coverUrl: String?,
    val isbn: String?,
    val publisher: String,
    val publishYear: String
)

private val LANGUAGE_META = mapOf(
    "spa" to Triple("es",       "Español",    "🇪🇸"),
    "eng" to Triple("original", "English",    "🌐"),
    "fre" to Triple("fr",       "Français",   "🇫🇷"),
    "ger" to Triple("de",       "Deutsch",    "🇩🇪"),
    "ita" to Triple("it",       "Italiano",   "🇮🇹"),
    "por" to Triple("pt",       "Português",  "🇵🇹"),
    "cat" to Triple("ca",       "Català",     "🏴󠁥󠁳󠁣󠁴󠁿"),
)

private fun languageMeta(langCode: String?): Triple<String, String, String> =
    LANGUAGE_META[langCode?.lowercase()?.take(3)] ?: Triple("original", langCode ?: "Original", "🌐")

/**
 * v18.4: heurística US/UK para ediciones inglesas.
 *
 * Mira primero el publisher (señal más fuerte), luego rangos de ISBN-13 que sí
 * distinguen país de registro de forma fiable. Devuelve:
 *   "🇺🇸" si la edición es muy probablemente estadounidense
 *   "🇬🇧" si es muy probablemente británica
 *   null  si no hay evidencia suficiente → conservar 🌐
 *
 * No usa ISBN-13 genéricos (978-0/978-1) porque cubren todo el mundo angloparlante
 * y no separan US de UK. Solo se usan sub-rangos muy específicos.
 */
internal fun inferAnglophoneFlag(isbn: String?, publisher: String?): String? {
    val pub = (publisher ?: "").lowercase().trim()

    // ── Editoriales UK (señal fuerte) ──────────────────────────────────────
    val ukPublishers = listOf(
        "gollancz", "orbit books", "orbit uk", "bloomsbury", "faber",
        "vintage books", "vintage uk", "vintage classics",
        "hodder", "stoughton", "pan macmillan", "pan books", "panmacmillan",
        "hamish hamilton", "penguin uk", "penguin books ltd", "penguin classics uk",
        "harpercollins uk", "harper collins uk", "harper voyager uk",
        "random house uk", "jonathan cape", "secker", "warburg",
        "viking uk", "fourth estate", "tinder press", "headline",
        "transworld", "doubleday uk", "phoenix", "weidenfeld",
        "atlantic books", "canongate", "tor uk", "gollancz uk",
        "harperfiction uk", "michael joseph", "century uk",
        "vintage international uk", "del rey uk", "ebury press"
    )
    if (ukPublishers.any { it in pub }) return "🇬🇧"

    // ── Editoriales US (señal fuerte) ──────────────────────────────────────
    val usPublishers = listOf(
        "tor books", "tor.com", "tor publishing", "forge books",
        "dragonsteel", "del rey", "ace books", "daw books", "bantam books",
        "berkley", "scholastic", "scribner", "knopf", "doubleday us",
        "alfred a. knopf", "harper voyager", "harper voyager us",
        "harpercollins us", "harpercollins publishers", "harper perennial",
        "simon & schuster", "simon and schuster", "atria books",
        "macmillan us", "st. martin", "st martin", "vintage international",
        "anchor books", "ballantine", "william morrow", "ecco",
        "little brown", "little, brown", "grand central",
        "penguin random house us", "viking penguin", "penguin press",
        "putnam", "dutton", "riverhead books", "viking us",
        "tor teen", "orbit us", "hachette books", "twelve books"
    )
    if (usPublishers.any { it in pub }) return "🇺🇸"

    // ── Rangos ISBN-13 muy específicos (sin ambigüedad) ────────────────────
    // Ver: https://www.isbn-international.org/range_file_generation
    val isb = (isbn ?: "").replace(Regex("[-\\s]"), "")
    if (isb.length == 13 && isb.startsWith("978")) {
        // Grupo 978-1-84 a 978-1-86: registrado para UK exclusivamente
        // Grupo 978-0-09: Random House UK (Hutchinson, Cornerstone, etc.)
        // No usamos 978-0-7 ni 978-1-78 — son demasiado mixtos
        when {
            isb.startsWith("9780099") -> return "🇬🇧"
            isb.startsWith("978184")  -> return "🇬🇧"
            isb.startsWith("978185")  -> return "🇬🇧"
            isb.startsWith("978186")  -> return "🇬🇧"
            // 978-1-250 a 978-1-254 → St. Martin's Press (US, asignación reciente)
            isb.startsWith("9781250") -> return "🇺🇸"
            isb.startsWith("9781251") -> return "🇺🇸"
        }
    }

    return null  // sin certeza → 🌐
}

// Fase 1.1: cleanIsbn / isbn13To10 / isbn10To13 / canonicalIsbn viven en utils/CoreUtils.kt

private fun normalizedEditionText(value: String): String =
    java.text.Normalizer.normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

private fun EditionResult.dedupeKey(): String =
    cleanIsbn(isbn) ?: "${language}|${normalizedEditionText(title)}|${pages}|${publishYear}"

private fun MutableList<EditionResult>.addEditionIfNew(
    edition: EditionResult,
    seenKeys: MutableSet<String>
) {
    if (edition.title.isBlank()) return
    val key = edition.dedupeKey()
    if (key in seenKeys) return
    seenKeys.add(key)
    add(edition)
}

private fun editionLanguageMeta(langCode: String?, isbn: String?, publisher: String? = null, publishPlace: String? = null): Triple<String, String, String> {
    if (!langCode.isNullOrBlank()) {
        val base = languageMeta(langCode)
        // v18.4: si la edición es inglesa, intentar refinar 🌐 → 🇺🇸/🇬🇧 según publisher/ISBN
        if (base.first == "original") {
            val refined = inferAnglophoneFlag(isbn, publisher)
            if (refined != null) return Triple(base.first, base.second, refined)
        }
        return base
    }
    val fromIsbn = isbnToLanguageMeta(isbn.orEmpty())
    // Editores explícitamente ingleses — preceden a cualquier heurística de publisher/ciudad
    if (!publisher.isNullOrBlank()) {
        val pub = publisher.lowercase()
        val enPublishers = listOf(
            "tor books", "tor fantasy", "tor teen", "gollancz", "orion", "orbit",
            "del rey", "ace books", "daw books", "baen books", "berkley",
            "random house", "penguin books", "harper collins", "harpercollins",
            "simon & schuster", "macmillan", "pan macmillan", "little brown",
            "bantam", "doubleday", "knopf", "scholastic", "hyperion",
            "dragonsteel", "doherty associates", "tom doherty"
        )
        if (enPublishers.any { pub.contains(it) }) {
            // v18.4: refinar US/UK también aquí
            val refined = inferAnglophoneFlag(isbn, publisher) ?: "🌐"
            return Triple("original", "English", refined)
        }
    }
    if (fromIsbn.first != "original") return fromIsbn
    if (!publisher.isNullOrBlank()) {
        val pub = publisher.lowercase()
        val esPublishers = listOf(
            "minotauro", "nova", "ediciones b", "planeta", "alfaguara",
            "sm libros", "destino", "anaya", "booket", "debolsillo",
            "bibliotecca", "biblioteka", "penguin random house españa",
            "montena", "muchnick", "noguer", "timun mas", "timun más",
            "la factoría de ideas", "factoria de ideas", "bibliopolis",
            "la cúpula", "la cupula", "norma editorial", "dolmen",
            "fantascy", "umbriel", "edaf", "ediciones martinez roca",
            "b de bolsillo", "b de books", "zeta bolsillo"
        )
        if (esPublishers.any { pub.contains(it) }) return Triple("es", "Español", "🇪🇸")
    }
    if (!publishPlace.isNullOrBlank()) {
        val place = publishPlace.lowercase()
        val esPlaces = listOf(
            "barcelona", "madrid", "buenos aires", "méxico", "mexico",
            "bogotá", "bogota", "santiago", "lima", "caracas", "montevideo",
            "ciudad de mexico", "ciudad de méxico", "guadalajara", "sevilla",
            "valencia", "bilbao", "zaragoza", "salamanca", "palma"
        )
        if (esPlaces.any { place.contains(it) }) return Triple("es", "Español", "🇪🇸")
    }
    // v18.4: si fromIsbn es "original" (inglés inferido por ISBN), refinar US/UK
    if (fromIsbn.first == "original") {
        val refined = inferAnglophoneFlag(isbn, publisher)
        if (refined != null) return Triple(fromIsbn.first, fromIsbn.second, refined)
    }
    return fromIsbn
}

// ── ThingISBN (LibraryThing) ──────────────────────────────────────────────────
// Dado un ISBN, devuelve los ISBNs de ediciones del mismo trabajo catalogadas en
// ── Wikidata SPARQL ───────────────────────────────────────────────────────────
// Dado el ISBN de cualquier edición, busca el Work en Wikidata y devuelve todos
// los ISBNs de ediciones en español enlazados a ese Work. Muy preciso para libros
// populares (Cosmere, bestsellers) donde Wikidata tiene datos completos.
private suspend fun fetchSpanishEditionsFromWikidata(isbn: String): List<EditionResult> = withContext(Dispatchers.IO) {
    val clean = cleanIsbn(isbn) ?: return@withContext emptyList()
    // Normalizar: Wikidata suele tener ISBN-13 sin guiones
    val isbn13 = if (clean.length == 13) clean else null
    val isbn10 = if (clean.length == 10) clean else null

    val isbnFilter = buildString {
        append("  { ?srcEdition wdt:P212 \"$clean\" } ")
        if (isbn13 != null) append("UNION { ?srcEdition wdt:P212 \"$isbn13\" } ")
        if (isbn10 != null) append("UNION { ?srcEdition wdt:P957 \"$isbn10\" } ")
    }

    val sparql = """
        SELECT DISTINCT ?title ?isbn13 ?isbn10 WHERE {
          $isbnFilter
          ?srcEdition wdt:P629 ?work .
          ?tgtEdition wdt:P629 ?work ;
                      wdt:P407 wd:Q1321 .
          OPTIONAL { ?tgtEdition wdt:P212 ?isbn13 }
          OPTIONAL { ?tgtEdition wdt:P957 ?isbn10 }
          OPTIONAL { ?tgtEdition rdfs:label ?title . FILTER(LANG(?title) = "es") }
        }
        LIMIT 20
    """.trimIndent()

    return@withContext try {
        val encoded = URLEncoder.encode(sparql, "UTF-8")
        val url = "https://query.wikidata.org/sparql?query=$encoded&format=json"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.setRequestProperty("Accept", "application/sparql-results+json")
        conn.connectTimeout = 8000; conn.readTimeout = 8000

        val root = JSONObject(conn.inputStream.bufferedReader().readText())
        val bindings = root.optJSONObject("results")?.optJSONArray("bindings") ?: return@withContext emptyList()

        val results = mutableListOf<EditionResult>()
        val seen = mutableSetOf<String>()

        for (i in 0 until bindings.length()) {
            val b = bindings.getJSONObject(i)
            val wdIsbn13 = b.optJSONObject("isbn13")?.optString("value", null)
            val wdIsbn10 = b.optJSONObject("isbn10")?.optString("value", null)
            val wdTitle  = b.optJSONObject("title")?.optString("value", null)

            val resolvedIsbn = cleanIsbn(wdIsbn13) ?: cleanIsbn(wdIsbn10)

            // Si tenemos ISBN, buscar metadatos en Google Books para portada y páginas
            if (resolvedIsbn != null && resolvedIsbn !in seen) {
                seen.add(resolvedIsbn)
                val ed = fetchEditionByIsbn(resolvedIsbn)
                if (ed != null) {
                    results.add(ed.copy(language = "es", languageLabel = "Español", flag = "🇪🇸"))
                } else if (!wdTitle.isNullOrBlank()) {
                    // No encontrado en GB pero tenemos título de Wikidata: devolver sin portada
                    results.add(EditionResult("es", "Español", "🇪🇸", wdTitle, 0, null, resolvedIsbn, "", ""))
                }
            } else if (resolvedIsbn == null && !wdTitle.isNullOrBlank()) {
                val dedupeKey = "es|${normalizedEditionText(wdTitle)}"
                if (dedupeKey !in seen) {
                    seen.add(dedupeKey)
                    results.add(EditionResult("es", "Español", "🇪🇸", wdTitle, 0, null, null, "", ""))
                }
            }
        }
        results
    } catch (_: Exception) { emptyList() }
}
// Detecta el idioma probable de una edición a partir del prefijo del ISBN-13.
// La detección es aproximada (no todos los publishers siguen el estándar al pie de la letra).
internal fun isbnToLanguageMeta(isbn: String): Triple<String, String, String> {
    val n = isbn.replace(Regex("[^\\dXx]"), "")
    // ISBN-13: comparar prefijos de grupo de registro
    val prefix = n.take(7)
    return when {
        prefix.startsWith("97884") || prefix.startsWith("97849") ||
        prefix.startsWith("97913") ||   // nuevo bloque nacional español 979-13 (desde 2025)
        prefix.startsWith("97985") || prefix.startsWith("97995") -> Triple("es", "Español", "🇪🇸")
        prefix.startsWith("9780") || prefix.startsWith("9781") -> Triple("original", "English", "🌐")
        prefix.startsWith("9782") -> Triple("fr", "Français", "🇫🇷")
        prefix.startsWith("9783") -> Triple("de", "Deutsch", "🇩🇪")
        prefix.startsWith("97888") -> Triple("it", "Italiano", "🇮🇹")
        prefix.startsWith("97885") || prefix.startsWith("97897") ||
        prefix.startsWith("97898") -> Triple("pt", "Português", "🇵🇹")
        prefix.startsWith("97890") || prefix.startsWith("97894") -> Triple("nl", "Nederlands", "🇳🇱")
        prefix.startsWith("97886") -> Triple("sr", "Srpski", "🇷🇸")
        prefix.startsWith("97887") -> Triple("da", "Dansk", "🇩🇰")
        prefix.startsWith("97891") -> Triple("sv", "Svenska", "🇸🇪")
        prefix.startsWith("97892") -> Triple("no", "Norsk", "🇳🇴")
        // ISBN-10: primer dígito del grupo
        n.length == 10 && n.startsWith("84") -> Triple("es", "Español", "🇪🇸")
        n.length == 10 && (n.startsWith("0") || n.startsWith("1")) -> Triple("original", "English", "🌐")
        else -> Triple("original", "Original", "🌐")
    }
}

// Busca metadatos de UN ISBN concreto en Google Books y devuelve un EditionResult.
// Usado por xISBN para hidratar cada ISBN alternativo encontrado.
internal suspend fun fetchEditionByIsbn(isbn: String): EditionResult? = withContext(Dispatchers.IO) {
    return@withContext try {
        val clean = cleanIsbn(isbn) ?: return@withContext null
        val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$clean&maxResults=1&printType=books"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val root = JSONObject(conn.inputStream.bufferedReader().readText())
        val items = root.optJSONArray("items")
        if (items == null || items.length() == 0) return@withContext null
        val info = items.getJSONObject(0).optJSONObject("volumeInfo") ?: return@withContext null

        val title = info.optString("title", "").takeIf { it.isNotBlank() } ?: return@withContext null
        // Feedback 2.7: pageCount implausible (<40) → 0 (desconocido); fichas rotas de GB
        // asignaban páginas absurdas a la edición
        val pages = info.optInt("pageCount", 0).takeIf { it >= MIN_PLAUSIBLE_PAGES } ?: 0
        val publishYear = info.optString("publishedDate", "").take(4)
        val publisher = info.optString("publisher", "")
        val imageLinks = info.optJSONObject("imageLinks")
        val rawCoverUrl = imageLinks?.let {
            cleanCoverUrl(it.optString("extraLarge").ifBlank { null }
                ?: it.optString("large").ifBlank { null }
                ?: it.optString("thumbnail").ifBlank { null })
        }
        // Validar portada: los placeholders ("COVER TO BE REVEALED") suelen ser <8 KB
        val coverUrl = if (rawCoverUrl != null && !isCoverUrlValid(rawCoverUrl)) null else rawCoverUrl
        // Idioma: preferir lo que diga GB, caer en detección por prefijo ISBN
        val gbLang = info.optString("language", "")
        val (langId, langLabel, flag) = if (gbLang.isNotBlank()) {
            languageMeta(gbLang)
        } else {
            isbnToLanguageMeta(clean)
        }

        EditionResult(langId, langLabel, flag, title, pages, coverUrl, clean, publisher, publishYear)
    } catch (_: Exception) { null }
}

// ── OpenLibrary Work resolver ──────────────────────────────────────────────────
// Estrategia más fiable que buscar por título cuando hay ISBN: resuelve el Work ID
// directamente y descarga todas las ediciones de esa obra intelectual.
private suspend fun fetchEditionsViaOpenLibraryByIsbn(isbn: String): List<EditionResult> = withContext(Dispatchers.IO) {
    val clean = cleanIsbn(isbn) ?: return@withContext emptyList()

    var workKey = try {
        val bibUrl = "https://openlibrary.org/api/books?bibkeys=ISBN:$clean&format=json&jscmd=data"
        val conn = URL(bibUrl).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        JSONObject(conn.inputStream.bufferedReader().readText())
            .optJSONObject("ISBN:$clean")
            ?.optJSONArray("works")            // "works" es array, no "work" objeto
            ?.optJSONObject(0)
            ?.optString("key", "") ?: ""
    } catch (_: Exception) { "" }

    if (!workKey.startsWith("/works/")) {
        workKey = try {
            val conn = URL("https://openlibrary.org/isbn/$clean.json").openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", APP_USER_AGENT)
            conn.connectTimeout = 6000; conn.readTimeout = 6000
            JSONObject(conn.inputStream.bufferedReader().readText())
                .optJSONArray("works")
                ?.optJSONObject(0)
                ?.optString("key", "") ?: ""
        } catch (_: Exception) { "" }
    }

    if (!workKey.startsWith("/works/")) return@withContext emptyList()

    val entries = try {
        val safeWorkKey = if (workKey.matches(Regex("/works/OL\\d+W"))) workKey else return@withContext emptyList()
        val conn = URL("https://openlibrary.org$safeWorkKey/editions.json?limit=500").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 7000; conn.readTimeout = 7000
        JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("entries")
    } catch (_: Exception) { null } ?: return@withContext emptyList()

    val results = mutableListOf<EditionResult>()
    var coverChecks = 0
    // Acotar: en obras muy catalogadas (ej. La Rueda del Tiempo) hay cientos de ediciones.
    // Procesar como máximo 150 y limitar las comprobaciones de portada por red.
    for (i in 0 until minOf(entries.length(), 150)) {
        val entry = entries.getJSONObject(i)
        val langCode = entry.optJSONArray("languages")?.optJSONObject(0)
            ?.optString("key", "")?.removePrefix("/languages/") ?: ""
        val edTitle = cleanCompositeTitle(
            entry.optString("title", "").ifBlank { entry.optString("full_title", "") }
        )
        if (edTitle.isBlank()) continue
        // isbn_13 e isbn_10 son arrays en OL (no strings)
        val edIsbn = cleanIsbn(
            entry.optJSONArray("isbn_13")?.optString(0, null)
                ?: entry.optJSONArray("isbn_10")?.optString(0, null)
        )
        // Feedback 2.7: páginas implausibles (<40) → 0 (desconocido)
        val pages = entry.optInt("number_of_pages", 0).takeIf { it >= MIN_PLAUSIBLE_PAGES } ?: 0
        val publishDate = entry.optString("publish_date", "").take(4)
        val publisher = entry.optJSONArray("publishers")?.optString(0, "") ?: ""
        val publishPlace = entry.optJSONArray("publish_places")?.optString(0, "") ?: ""
        val (langId, langLabel, flag) = editionLanguageMeta(langCode, edIsbn, publisher, publishPlace)
        // covers es array de IDs enteros
        val coverId = entry.optJSONArray("covers")?.optLong(0, -1L) ?: -1L
        var coverUrl: String? = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else null
        // Solo gastar una petición de portada por red en español o si aún no superamos el cupo
        if (coverUrl == null && !edIsbn.isNullOrBlank() && (langId == "es" || coverChecks < 12)) {
            coverChecks++
            val testUrl = "https://covers.openlibrary.org/b/isbn/$edIsbn-L.jpg?default=false"
            try {
                val c = URL(testUrl).openConnection() as HttpURLConnection
                c.connectTimeout = 3000; c.readTimeout = 3000
                if (c.responseCode == 200) coverUrl = testUrl.removeSuffix("?default=false")
            } catch (_: Exception) {}
        }
        results.add(EditionResult(langId, langLabel, flag, edTitle, pages, coverUrl, edIsbn, publisher, publishDate))
    }
    results
}


// Limpia títulos compuestos del tipo "Juramentada/Oathbringer" que aparecen en OL.
// Si el título contiene " / " o "/" con partes largas, toma solo la primera parte.
// Evita truncar títulos legítimos como "Sense and Sensibility / Pride and Prejudice".
private fun cleanCompositeTitle(title: String): String {
    val sep = when {
        title.contains(" / ") -> " / "
        title.contains("/") && !title.startsWith("http") -> "/"
        else -> return title
    }
    val parts = title.split(sep).map { it.trim() }.filter { it.isNotBlank() }
    if (parts.size < 2) return title
    // Solo limpiar si la segunda parte parece ser el título original en otro idioma
    // (heurística: ambas partes tienen >3 palabras o son claramente distintas en idioma)
    val first = parts[0]
    val second = parts[1]
    // Si la primera parte es más corta pero ambas son largas, conservar el original
    if (first.length < 4 || second.length < 4) return title
    return first
}

// Dado un ISBN y el título que OL nos dio, intenta obtener el título real de Google Books.
// Útil cuando OL detecta la edición como española (vía publisher) pero guarda el título en inglés.
private suspend fun resolveSpanishTitleByIsbn(isbn: String, fallbackTitle: String): String {
    val clean = cleanIsbn(isbn) ?: return fallbackTitle
    return try {
        val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$clean&maxResults=1&printType=books"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 4000; conn.readTimeout = 4000
        val info = JSONObject(conn.inputStream.bufferedReader().readText())
            .optJSONArray("items")?.getJSONObject(0)?.optJSONObject("volumeInfo")
            ?: return fallbackTitle
        val gbLang = info.optString("language", "")
        val gbTitle = info.optString("title", "").trim()
        // Solo usar el título de GB si el idioma es español y el título es distinto al fallback
        if (gbLang == "es" && gbTitle.isNotBlank() && gbTitle != fallbackTitle) gbTitle else fallbackTitle
    } catch (_: Exception) { fallbackTitle }
}

// Elimina sufijos de serie del tipo "(The Stormlight Archive, #3)" o "[Cosmere]"
// que rompen todas las APIs cuando se usan en queries de búsqueda.
private fun cleanTitleForSearch(title: String): String =
    title
        .replace(Regex("""\s*[\(\[][^\)\]]*[\)\]]\s*$"""), "")
        // Auditoría APIs: índice de saga estilo Goodreads al final (", #3" / ", 3").
        // Conservador: NO toca "Vol. N"/"Tomo N" (tomos de manga se preservan).
        .replace(Regex("""\s*,\s*#?\d+\s*$"""), "")
        .trim()
        .ifBlank { title }

/** Mapeo de títulos en español/alias a su título de búsqueda más efectivo en APIs.
 *  IMPORTANTE: NO incluir el autor — se añade automáticamente en las queries. */
// ── Ediciones españolas verificadas para libros problemáticos ─────────────────
// OL cataloga estos títulos con Works ES/EN separados y metadatos pobres; los IDs
// de edición fueron verificados manualmente. Garantiza título/páginas/portada
// correctos tanto en "Buscar libros" como en "Cambiar edición".
private val KNOWN_SPANISH_EDITIONS: List<Triple<List<String>, String, String>> = listOf(
    // (patrones en el título, OLID de la edición española, autor)
    Triple(listOf("hombre iluminado", "sunlit man"), "OL52959679M", "Brandon Sanderson"),
    Triple(listOf("ascuaoscura", "ascuaosura", "emberdark"), "OL62085744M", "Brandon Sanderson"),
    // v20.9: "Realidades a medida" solo existe en OL en inglés (Tailored Realities); inyectar edición ES vía ISBN
    Triple(listOf("realidades a medida", "tailored realities"), "OL62207700M", "Brandon Sanderson")
)

private fun knownSpanishEdition(title: String): Pair<String, String>? {
    val l = title.lowercase()
    return KNOWN_SPANISH_EDITIONS.firstOrNull { (patterns, _, _) -> patterns.any { l.contains(it) } }
        ?.let { it.second to it.third }
}

/** Descarga una edición concreta de OpenLibrary por su OLID (ej. OL52959679M). */
private suspend fun fetchOlEditionById(olid: String): EditionResult? = withContext(Dispatchers.IO) {
    // v20.9: si la clave es "ISBN:XXXXXXXX" recuperar directamente vía OpenLibrary Books API
    if (olid.startsWith("ISBN:")) {
        val isbn = olid.removePrefix("ISBN:")
        return@withContext try {
            val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&jscmd=data&format=json"
            val conn = java.net.URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", APP_USER_AGENT)
            conn.connectTimeout = 6000; conn.readTimeout = 6000
            val root = JSONObject(conn.inputStream.bufferedReader().readText())
            // Si OL no tiene el libro aún, no inyectamos resultado vacío (title="", pages=0)
            val book = root.optJSONObject("ISBN:$isbn") ?: return@withContext null
            val title = cleanCompositeTitle(book.optString("title", "").ifBlank { "" })
            // Feedback 2.7: páginas implausibles (<40) → 0 (desconocido)
            val pages = book.optInt("number_of_pages", 0).takeIf { it >= MIN_PLAUSIBLE_PAGES } ?: 0
            val publisher = book.optJSONArray("publishers")?.optJSONObject(0)?.optString("name", "") ?: ""
            val year = book.optString("publish_date", "").takeLast(4)
            val coverObj = book.optJSONObject("cover")
            val coverUrl = coverObj?.let {
                it.optString("large").ifBlank { null }
                    ?: it.optString("medium").ifBlank { null }
            } ?: "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"
            EditionResult("es", "Español", "🇪🇸", title, pages, coverUrl, isbn, publisher, year)
        } catch (_: Exception) { null }
    }
    try {
        val conn = URL("https://openlibrary.org/books/$olid.json").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val j = JSONObject(conn.inputStream.bufferedReader().readText())
        val title = cleanCompositeTitle(j.optString("title", "").ifBlank { return@withContext null })
        // Feedback 2.7: páginas implausibles (<40) → 0 (desconocido)
        val pages = j.optInt("number_of_pages", 0).takeIf { it >= MIN_PLAUSIBLE_PAGES } ?: 0
        val isbn = cleanIsbn(j.optJSONArray("isbn_13")?.optString(0, null)
            ?: j.optJSONArray("isbn_10")?.optString(0, null))
        val publisher = j.optJSONArray("publishers")?.optString(0, "") ?: ""
        val year = j.optString("publish_date", "").takeLast(4)
        val coverId = j.optJSONArray("covers")?.optLong(0, -1L) ?: -1L
        val coverUrl = when {
            coverId > 0 -> "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
            isbn != null -> "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"
            else -> null
        }
        EditionResult("es", "Español", "🇪🇸", title, pages, coverUrl, isbn, publisher, year)
    } catch (_: Exception) { null }
}

private fun resolveSearchTitle(title: String): String {
    val clean = cleanTitleForSearch(title)
    val lower = clean.lowercase().trim()
    return when {
        lower.contains("ascuaoscura") || lower.contains("ascuaosura") ||
        lower.contains("emberdark") -> "Isles of the Emberdark"
        // v20.9: alias ES→EN para Realidades a medida
        lower.contains("realidades a medida") -> "Tailored Realities"
        lower.contains("hombre iluminado") || lower.contains("sunlit man") -> "The Sunlit Man"
        lower.contains("ojo del mundo") -> "The Eye of the World"
        lower.contains("gran cacería") || lower.contains("gran caceria") -> "The Great Hunt"
        lower.contains("dragón renacido") || lower.contains("dragon renacido") -> "The Dragon Reborn"
        lower.contains("sombra ascendente") -> "The Shadow Rising"
        lower.contains("fuegos del cielo") -> "The Fires of Heaven"
        lower.contains("señor del caos") || lower.contains("senor del caos") -> "Lord of Chaos"
        lower.contains("corona de espadas") -> "A Crown of Swords"
        lower.contains("camino de dagas") -> "The Path of Daggers"
        lower.contains("corazón de invierno") || lower.contains("corazon de invierno") -> "Winter's Heart"
        lower.contains("encrucijada del crepúsculo") || lower.contains("encrucijada del crepusculo") -> "Crossroads of Twilight"
        lower.contains("cuchillo de sueños") || lower.contains("cuchillo de suenos") -> "Knife of Dreams"
        lower.contains("tormenta de la luz") || lower.contains("reúne las tormentas") || lower.contains("reune las tormentas") -> "The Gathering Storm"
        lower.contains("torres de medianoche") -> "Towers of Midnight"
        lower.contains("memoria de luz") -> "A Memory of Light"
        else -> clean
    }
}

/**
 * v19.0→19.2 — Resolución DINÁMICA de título original vía Wikidata.
 *
 * Problema: resolveSearchTitle() solo conoce ~16 títulos hardcodeados.
 * Para cualquier libro traducido fuera de esa lista, las fases B y C de
 * fetchEditionsForBook buscan con título ESPAÑOL contra catálogos que indexan
 * por título original → no encuentran ediciones en otros idiomas ("Global" vacío).
 *
 * Estrategia (dos intentos, cada uno con su propio catch — si uno falla, el otro
 * sigue vivo):
 *   1. Por ISBN (SPARQL): localizar Edition → Work → rdfs:label@en.
 *      Rápido y preciso, pero falla si el ISBN no está en Wikidata.
 *   2. Por título español (REST API wbsearchentities): buscar el título como texto
 *      libre en Wikidata con language=es → filtrar solo ítems cuya descripción
 *      sugiera "libro/novela/literary work" → pedir etiqueta@en del primero.
 *      Más lento pero cubre el caso de "Trenza del Mar Esmeralda" sin ISBN mapeado.
 *
 * Devuelve null si ninguno resuelve — sin regresión (se sigue usando el título tal cual).
 */
private suspend fun resolveOriginalTitleFromWikidata(isbn: String?, spanishTitle: String? = null): String? = withContext(Dispatchers.IO) {
    // ── Intento 1: por ISBN (SPARQL) ─────────────────────────────────────────
    val clean = cleanIsbn(isbn)
    if (clean != null) {
        try {
            val sparql = """
                SELECT ?label WHERE {
                  { ?ed wdt:P212 "$clean" } UNION { ?ed wdt:P957 "$clean" }
                  ?ed wdt:P629 ?work .
                  ?work rdfs:label ?label .
                  FILTER(LANG(?label) = "en")
                }
                LIMIT 1
            """.trimIndent()
            val encoded = URLEncoder.encode(sparql, "UTF-8")
            val url = "https://query.wikidata.org/sparql?query=$encoded&format=json"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", APP_USER_AGENT)
            conn.setRequestProperty("Accept", "application/sparql-results+json")
            conn.connectTimeout = 7000; conn.readTimeout = 7000
            val root = JSONObject(conn.inputStream.bufferedReader().readText())
            val bindings = root.optJSONObject("results")?.optJSONArray("bindings")
            if (bindings != null && bindings.length() > 0) {
                val label = bindings.getJSONObject(0).optJSONObject("label")?.optString("value", "")?.ifBlank { null }
                if (label != null) return@withContext label
            }
        } catch (_: Exception) {}
    }

    // ── Intento 2: por título español (REST API) ─────────────────────────────
    val titleQuery = spanishTitle?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext null
    try {
        // Paso 2a: buscar entidades con el título español
        val searchUrl = "https://www.wikidata.org/w/api.php?action=wbsearchentities" +
            "&search=${URLEncoder.encode(titleQuery, "UTF-8")}" +
            "&language=es&uselang=es&type=item&limit=5&format=json"
        val sConn = URL(searchUrl).openConnection() as HttpURLConnection
        sConn.setRequestProperty("User-Agent", APP_USER_AGENT)
        sConn.connectTimeout = 6000; sConn.readTimeout = 6000
        val searchResults = JSONObject(sConn.inputStream.bufferedReader().readText())
            .optJSONArray("search") ?: return@withContext null

        // Paso 2b: filtrar por descripción que sugiera libro/novela/obra literaria
        val bookKeywords = listOf("novel", "book", "novela", "libro", "literary work", "obra literaria", "short story", "relato", "light novel")
        var candidateQid: String? = null
        for (i in 0 until searchResults.length()) {
            val item = searchResults.getJSONObject(i)
            val desc = (item.optString("description", "") + " " + item.optString("label", "")).lowercase()
            if (bookKeywords.any { desc.contains(it) }) {
                candidateQid = item.optString("id", "").ifBlank { null }
                break
            }
        }
        // Si ninguno matchea por descripción, tomar el primero (suele ser correcto para títulos únicos)
        if (candidateQid == null && searchResults.length() > 0) {
            candidateQid = searchResults.getJSONObject(0).optString("id", "").ifBlank { null }
        }
        if (candidateQid == null) return@withContext null

        // Paso 2c: pedir la etiqueta en inglés de ese ítem
        val entityUrl = "https://www.wikidata.org/w/api.php?action=wbgetentities" +
            "&ids=$candidateQid&props=labels&languages=en&format=json"
        val eConn = URL(entityUrl).openConnection() as HttpURLConnection
        eConn.setRequestProperty("User-Agent", APP_USER_AGENT)
        eConn.connectTimeout = 5000; eConn.readTimeout = 5000
        val entities = JSONObject(eConn.inputStream.bufferedReader().readText())
            .optJSONObject("entities") ?: return@withContext null
        val entity = entities.optJSONObject(candidateQid) ?: return@withContext null
        val enLabel = entity.optJSONObject("labels")?.optJSONObject("en")
            ?.optString("value", "")?.ifBlank { null }
        // Validación: si la etiqueta inglesa es idéntica al título español, no aporta nada
        if (enLabel != null && enLabel.equals(titleQuery, ignoreCase = true)) return@withContext null
        enLabel
    } catch (_: Exception) { null }
}

suspend fun fetchEditionsForBook(
    title: String,
    author: String,
    isbn: String?,
    originalPages: Int = 0,
    // Auditoría APIs r3: feedback progresivo. Se invoca cada vez que una fase
    // termina, con la lista mergeada+ordenada (mínimamente: es primero, portada
    // primero) hasta ese momento. NO reemplaza el return final (que va con
    // reclasificación + SpanishScore completos). Null → sin feedback progresivo.
    onPartial: (suspend (List<EditionResult>) -> Unit)? = null
): List<EditionResult> = kotlinx.coroutines.withTimeoutOrNull(90_000) { supervisorScope {   // auditoría r2: presupuesto global 60s → 90s

    // Título limpio para búsquedas — con resolución de aliases (ej. "El dragón renacido" → "The Dragon Reborn")
    val staticAlias = resolveSearchTitle(title)
    // Título original en español (antes de resolver el alias) — para búsquedas con langRestrict=spa/es
    val originalTitle = cleanTitleForSearch(title)
    // v19.0: si NO hay alias estático (libro fuera de la lista hardcodeada de Sanderson/WoT) y
    // tenemos ISBN, intentar resolver el título original dinámicamente vía Wikidata. Esto es lo
    // que permite que la pestaña "Global" encuentre ediciones en otros idiomas para CUALQUIER
    // libro, no solo para los 16 títulos hardcodeados.
    val dynamicAlias = if (staticAlias == originalTitle) resolveOriginalTitleFromWikidata(isbn, originalTitle) else null
    val searchTitle = dynamicAlias ?: staticAlias
    val hasAlias = searchTitle != originalTitle

    // Auditoría APIs r2: timeout INDIVIDUAL por fase — OL 15s, GB 10s, resto 5s —
    // con presupuesto global de 90s. Logging de latencia/resultados por fase
    // (AppLogger, tag "EditionSearch") para diagnóstico en producción.
    val tStart = System.currentTimeMillis()
    com.lecturameter.utils.AppLogger.log(
        "fetchEditionsForBook: title=\"$searchTitle\" isbn=${isbn ?: "-"} alias=$hasAlias", "EditionSearch")
    // Auditoría APIs r3: feedback progresivo — canal de resultados por fase.
    // Cada fase envía al terminar (con o sin timeout). Consumidor separado
    // mergea + reordena mínimo + notifica al callback. Sin canal si no hay callback.
    val partialChan = if (onPartial != null)
        kotlinx.coroutines.channels.Channel<List<EditionResult>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    else null

    suspend fun runPhase(name: String, timeoutMs: Long, block: suspend () -> List<EditionResult>): List<EditionResult> {
        val t0 = System.currentTimeMillis()
        val res = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
        }
        val ms = System.currentTimeMillis() - t0
        if (res == null) com.lecturameter.utils.AppLogger.log("$name: TIMEOUT (>${timeoutMs / 1000}s)", "EditionSearch")
        else com.lecturameter.utils.AppLogger.log("$name: ${res.size} ediciones en ${ms}ms", "EditionSearch")
        val out = res ?: emptyList()
        partialChan?.trySend(out)
        return out
    }

    // ── Phase A: OL Work resolver por ISBN ────────────────────────────────────
    // Si el libro tiene ISBN, resuelve el Work en OL y descarga todas sus ediciones.
    // Es la fase más completa para libros bien catalogados.
    val phaseA = async(Dispatchers.IO) { runPhase("PhaseA_OL_ISBN", 15_000L) {
        val r = mutableListOf<EditionResult>()
        if (isbn.isNullOrBlank()) return@runPhase r
        val seen = mutableSetOf<String>()
        cleanIsbn(isbn)?.let { seen.add(it) }

        // Intento principal: resolver Work por ISBN y listar sus ediciones
        val olEditions = try { fetchEditionsViaOpenLibraryByIsbn(isbn) } catch (_: Exception) { emptyList() }
        for (ed in olEditions) r.addEditionIfNew(ed, seen)
        r
    } }

    // ── Phase B: OpenLibrary por título limpio ────────────────────────────────
    // Busca el Work en OL usando el título sin sufijos de serie, lista todas sus
    // ediciones, y también lanza una búsqueda explícita con filtro language=spa.
    val phaseB = async(Dispatchers.IO) { runPhase("PhaseB_OL_titulo", 15_000L) {
        val r = mutableListOf<EditionResult>()
        val seen = mutableSetOf<String>()
        cleanIsbn(isbn)?.let { seen.add(it) }
        try {
            // 1. Encontrar la clave del Work en OL — con el título resuelto (inglés si hay alias)
            val q = URLEncoder.encode("$searchTitle $author", "UTF-8")
            ApiThrottle.gate("openlibrary.org")
            val docsConn = URL("https://openlibrary.org/search.json?q=$q&limit=3&fields=key,title,author_name")
                .openConnection() as HttpURLConnection
            docsConn.setRequestProperty("User-Agent", APP_USER_AGENT)
            docsConn.connectTimeout = 5000; docsConn.readTimeout = 5000
            val docs = JSONObject(docsConn.inputStream.bufferedReader().readText()).optJSONArray("docs")

            // Si hay alias, también buscar el Work con el título en español
            val docsSpanish: org.json.JSONArray? = if (hasAlias) {
                try {
                    val qEs = URLEncoder.encode("$originalTitle $author", "UTF-8")
                    ApiThrottle.gate("openlibrary.org")
                    val esConn = URL("https://openlibrary.org/search.json?q=$qEs&limit=3&fields=key,title,author_name")
                        .openConnection() as HttpURLConnection
                    esConn.setRequestProperty("User-Agent", APP_USER_AGENT)
                    esConn.connectTimeout = 5000; esConn.readTimeout = 5000
                    JSONObject(esConn.inputStream.bufferedReader().readText()).optJSONArray("docs")
                } catch (_: Exception) { null }
            } else null

            val normAuthor = normalizedEditionText(author)
            fun pickWorkKey(arr: org.json.JSONArray?): String {
                if (arr == null) return ""
                for (i in 0 until arr.length()) {
                    val doc = arr.getJSONObject(i)
                    val docAuthors = doc.optJSONArray("author_name")
                    val authorOk = normAuthor.isBlank() || (docAuthors != null &&
                        (0 until docAuthors.length()).any {
                            normalizedEditionText(docAuthors.optString(it, "")).contains(normAuthor)
                        })
                    if (authorOk) return doc.optString("key", "")
                }
                return if (arr.length() > 0) arr.getJSONObject(0).optString("key", "") else ""
            }
            // Works del título inglés Y del español — pueden ser DISTINTOS en OL
            // (ej. "Islas de la Ascuaoscura" tiene su propio Work separado de "Isles of the Emberdark").
            // Descargamos las ediciones de AMBOS, el español primero.
            val workKeys = listOfNotNull(
                pickWorkKey(docsSpanish).takeIf { it.startsWith("/works/") },
                pickWorkKey(docs).takeIf { it.startsWith("/works/") }
            ).distinct()

            // 2. Descargar ediciones de cada Work encontrado
            var coverOps = 0   // presupuesto de peticiones de portada por red (evita cuelgues en obras enormes)
            for (workKey in workKeys) {
                ApiThrottle.gate("openlibrary.org")
                val edConn = URL("https://openlibrary.org$workKey/editions.json?limit=300")
                    .openConnection() as HttpURLConnection
                edConn.setRequestProperty("User-Agent", APP_USER_AGENT)
                edConn.connectTimeout = 5000; edConn.readTimeout = 5000
                val entries = JSONObject(edConn.inputStream.bufferedReader().readText())
                    .optJSONArray("entries") ?: JSONArray()

                // Pre-pase sin red: detectar idioma para priorizar las ediciones españolas.
                // En obras muy catalogadas evita gastar el presupuesto en cientos de ediciones inglesas.
                val esIdx = HashSet<Int>()
                val coverIdx = HashSet<Int>()
                for (i in 0 until entries.length()) {
                    val e = entries.getJSONObject(i)
                    val lc = e.optJSONArray("languages")?.optJSONObject(0)
                        ?.optString("key", "")?.removePrefix("/languages/") ?: ""
                    val ib = cleanIsbn(e.optJSONArray("isbn_13")?.optString(0, null)
                        ?: e.optJSONArray("isbn_10")?.optString(0, null))
                    val pub = e.optJSONArray("publishers")?.optString(0, "") ?: ""
                    val pl = e.optJSONArray("publish_places")?.optString(0, "") ?: ""
                    if (editionLanguageMeta(lc, ib, pub, pl).first == "es") esIdx.add(i)
                    if ((e.optJSONArray("covers")?.optLong(0, -1L) ?: -1L) > 0) coverIdx.add(i)
                }
                // Español primero, y dentro de cada grupo las que ya traen portada
                val order = (0 until entries.length()).sortedWith(
                    compareByDescending<Int> { it in esIdx }.thenByDescending { it in coverIdx }
                )

                // Procesar como máximo 70 ediciones por Work
                for (idx in order.take(70)) {
                    val e = entries.getJSONObject(idx)
                    val rawTitle = e.optString("title", "").ifBlank { e.optString("full_title", searchTitle) }
                    if (rawTitle.isBlank()) continue
                    val langCode = e.optJSONArray("languages")?.optJSONObject(0)
                        ?.optString("key", "")?.removePrefix("/languages/") ?: ""
                    val edIsbn = cleanIsbn(e.optJSONArray("isbn_13")?.optString(0, null)
                        ?: e.optJSONArray("isbn_10")?.optString(0, null))
                    val publisher = e.optJSONArray("publishers")?.optString(0, "") ?: ""
                    val publishPlace = e.optJSONArray("publish_places")?.optString(0, "") ?: ""
                    val (langId, langLabel, flag) = editionLanguageMeta(langCode, edIsbn, publisher, publishPlace)
                    // Si el idioma fue detectado como español pero el langCode estaba vacío
                    // (detección por publisher/publishPlace), el título de OL puede estar en inglés.
                    // En ese caso intentamos obtener el título real desde Google Books por ISBN.
                    val edTitle = if (langId == "es" && langCode.isBlank() && !edIsbn.isNullOrBlank()) {
                        val gb = resolveSpanishTitleByIsbn(edIsbn, rawTitle)
                        cleanCompositeTitle(gb)
                    } else {
                        cleanCompositeTitle(rawTitle)
                    }
                    // Feedback 2.7: páginas implausibles (<40) → 0 (desconocido)
                    val pages = e.optInt("number_of_pages", 0).takeIf { it >= MIN_PLAUSIBLE_PAGES } ?: 0
                    val publishDate = e.optString("publish_date", "").take(4)
                    val coverId = e.optJSONArray("covers")?.optLong(0, -1L) ?: -1L
                    var coverUrl: String? = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else null
                    // Validar que el cover_i devuelve imagen real (>8 KB) — solo en español o con presupuesto
                    if (coverUrl != null && (langId == "es" || coverOps < 8)) {
                        if (langId != "es") coverOps++
                        if (!isCoverUrlValid(coverUrl)) coverUrl = null
                    }
                    if (coverUrl == null && !edIsbn.isNullOrBlank() && (langId == "es" || coverOps < 8)) {
                        if (langId != "es") coverOps++
                        val testUrl = "https://covers.openlibrary.org/b/isbn/$edIsbn-L.jpg?default=false"
                        try {
                            ApiThrottle.gate("covers.openlibrary.org")
                            val c = URL(testUrl).openConnection() as HttpURLConnection
                            c.connectTimeout = 2000; c.readTimeout = 2000
                            if (c.responseCode == 200) coverUrl = testUrl.removeSuffix("?default=false")
                        } catch (_: Exception) {}
                    }
                    // Si la edición es española y sigue sin portada, intentar OL con título español
                    if (coverUrl == null && langId == "es" && hasAlias) {
                        coverUrl = fetchSpanishCoverByTitle(originalTitle, author)
                    }
                    r.addEditionIfNew(EditionResult(langId, langLabel, flag, edTitle, pages, coverUrl, edIsbn, publisher, publishDate), seen)
                }
            }

            // 3. Búsqueda OL con filtro de idioma (v2.6: generalizada spa → spa+cat).
            //    Si hay alias, usar primero el título español original (mayor precisión).
            suspend fun olLangSearch(query: String, olLang: String, langId: String, langLabel: String, langFlag: String) {
                try {
                    val qEnc = URLEncoder.encode(query, "UTF-8")
                    ApiThrottle.gate("openlibrary.org")
                    val esConn = URL("https://openlibrary.org/search.json?q=$qEnc&language=$olLang&limit=5&fields=title,isbn,number_of_pages,cover_i,publisher")
                        .openConnection() as HttpURLConnection
                    esConn.setRequestProperty("User-Agent", APP_USER_AGENT)
                    esConn.connectTimeout = 5000; esConn.readTimeout = 5000
                    val esDocs = JSONObject(esConn.inputStream.bufferedReader().readText()).optJSONArray("docs")
                    if (esDocs != null) for (i in 0 until esDocs.length()) {
                        val doc = esDocs.getJSONObject(i)
                        val esTitle = cleanCompositeTitle(doc.optString("title", ""))
                        if (esTitle.isBlank()) continue
                        val esIsbn = cleanIsbn(doc.optJSONArray("isbn")?.optString(0, null))
                        // Feedback 2.7: páginas implausibles (<40) → 0 (desconocido)
                        val pages = doc.optInt("number_of_pages", 0).takeIf { it >= MIN_PLAUSIBLE_PAGES } ?: 0
                        val coverId = doc.optLong("cover_i", -1L)
                        val coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else null
                        val publisher = doc.optJSONArray("publisher")?.optString(0, "") ?: ""
                        r.addEditionIfNew(EditionResult(langId, langLabel, langFlag, esTitle, pages, coverUrl, esIsbn, publisher, ""), seen)
                    }
                } catch (_: Exception) {}
            }
            // Búsqueda con título español primero si hay alias (p.ej. "El Hombre Iluminado" antes de "The Sunlit Man")
            if (hasAlias) olLangSearch("$originalTitle $author", "spa", "es", "Español", "🇪🇸")
            olLangSearch("$searchTitle $author", "spa", "es", "Español", "🇪🇸")
            // v2.6: ediciones catalanas (OL usa código MARC "cat")
            olLangSearch("$searchTitle $author", "cat", "ca", "Català", "🏴󠁥󠁳󠁣󠁴󠁿")
        } catch (_: Exception) {}
        r
    } }

    // ── Phase C: Google Books con título limpio ───────────────────────────────
    // Queries con el título limpio (sin sufijos de serie) y langRestrict=es primero.
    val phaseC = async(Dispatchers.IO) { runPhase("PhaseC_GoogleBooks", 10_000L) {
        val r = mutableListOf<EditionResult>()
        val seen = mutableSetOf<String>()
        cleanIsbn(isbn)?.let { seen.add(it) }

        suspend fun addVolume(info: JSONObject) {
            val gbTitle = info.optString("title", "").takeIf { it.isNotBlank() } ?: return
            // Feedback 2.7: pageCount implausible (<40) → 0 (desconocido) — fichas rotas
            // de GB asignaban páginas absurdas a ediciones (ej. 22 págs)
            val pages = info.optInt("pageCount", 0).takeIf { it >= MIN_PLAUSIBLE_PAGES } ?: 0
            val publishYear = info.optString("publishedDate", "").take(4)
            val identifiers = info.optJSONArray("industryIdentifiers")
            var gbIsbn: String? = null
            if (identifiers != null) {
                for (j in 0 until identifiers.length()) {
                    val id = identifiers.getJSONObject(j)
                    if (id.optString("type") == "ISBN_13") { gbIsbn = cleanIsbn(id.optString("identifier")); break }
                }
                if (gbIsbn == null) for (j in 0 until identifiers.length()) {
                    val id = identifiers.getJSONObject(j)
                    if (id.optString("type") == "ISBN_10") { gbIsbn = cleanIsbn(id.optString("identifier")); break }
                }
            }
            val gbLang = info.optString("language", "")
            val (langId, langLabel, flag) = when (gbLang) {
                "es" -> Triple("es", "Español", "🇪🇸")
                "ca" -> Triple("ca", "Català", "🏴󠁥󠁳󠁣󠁴󠁿")
                "en" -> Triple("original", "English", "🌐")
                "fr" -> Triple("fr", "Français", "🇫🇷")
                "de" -> Triple("de", "Deutsch", "🇩🇪")
                "it" -> Triple("it", "Italiano", "🇮🇹")
                "pt" -> Triple("pt", "Português", "🇵🇹")
                else -> editionLanguageMeta(gbLang, gbIsbn)
            }
            val imageLinks = info.optJSONObject("imageLinks")
            val rawCover = imageLinks?.let {
                cleanCoverUrl(it.optString("extraLarge").ifBlank { null }
                    ?: it.optString("large").ifBlank { null }
                    ?: it.optString("thumbnail").ifBlank { null })
            }
            // Validar que no sea un placeholder (<8 KB)
            var coverUrl = if (rawCover != null && !isCoverUrlValid(rawCover)) null else rawCover
            // Si la edición es española y la portada falló, buscar en OL con título español
            if (coverUrl == null && langId == "es" && hasAlias) {
                coverUrl = fetchSpanishCoverByTitle(originalTitle, author)
            }
            r.addEditionIfNew(EditionResult(langId, langLabel, flag, gbTitle, pages, coverUrl, gbIsbn, info.optString("publisher", ""), publishYear), seen)
        }

        suspend fun gbSearch(query: String, langRestrict: String = "") {
            if (r.size >= 12) return
            try {
                ApiThrottle.gate("www.googleapis.com")
                val langParam = if (langRestrict.isNotBlank()) "&langRestrict=$langRestrict" else ""
                val url = "https://www.googleapis.com/books/v1/volumes?q=${URLEncoder.encode(query, "UTF-8")}&maxResults=20&printType=books$langParam"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", APP_USER_AGENT)
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                val items = JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("items") ?: return
                for (i in 0 until items.length()) {
                    val info = items.getJSONObject(i).optJSONObject("volumeInfo") ?: continue
                    addVolume(info)
                }
            } catch (_: Exception) {}
        }

        try {
            // Si hay alias (título español ≠ inglés), buscar con el título español PRIMERO
            // para que la edición española aparezca antes de que r alcance 12 entradas.
            if (hasAlias) {
                gbSearch("intitle:\"$originalTitle\" inauthor:\"$author\"", "es")
                gbSearch("$originalTitle $author", "es")
            }
            // Después, búsqueda con título inglés resuelto + langRestrict=es
            gbSearch("intitle:\"$searchTitle\" inauthor:\"$author\"", "es")
            gbSearch("$searchTitle $author", "es")
            // v2.6: ediciones catalanas
            if (hasAlias) gbSearch("$originalTitle $author", "ca")
            gbSearch("$searchTitle $author", "ca")
            // Sin restricción de idioma
            if (hasAlias) {
                gbSearch("intitle:\"$originalTitle\" inauthor:\"$author\"")
                gbSearch("$originalTitle $author")
            }
            gbSearch("intitle:\"$searchTitle\" inauthor:\"$author\"")
            gbSearch("$searchTitle $author")
            if (r.size < 4) gbSearch(searchTitle, "es")
            if (r.size < 8) gbSearch(searchTitle)
        } catch (_: Exception) {}
        r
    } }

    // ── Phase E: Wikidata SPARQL ──────────────────────────────────────────────
    // Requiere ISBN. Corre en paralelo con A, B, C.
    val phaseE = async(Dispatchers.IO) { runPhase("PhaseE_Wikidata", 10_000L) {
        if (isbn.isNullOrBlank()) return@runPhase emptyList<EditionResult>()
        try { fetchSpanishEditionsFromWikidata(isbn) } catch (_: Exception) { emptyList() }
    } }


    // ── Merge ─────────────────────────────────────────────────────────────────
    val mergedSeen = mutableSetOf<String>()
    cleanIsbn(isbn)?.let { mergedSeen.add(it) }
    val merged = mutableListOf<EditionResult>()

    // Auditoría APIs r3: consumidor del canal parcial. Corre en paralelo con las
    // fases; cada vez que UNA fase termina, mergea + ordena mínimo + notifica.
    // Se lanza SOLO si hay callback (partialChan != null).
    val partialConsumer = partialChan?.let { chan ->
        async {
            val accumSeen = mutableSetOf<String>()
            cleanIsbn(isbn)?.let { accumSeen.add(it) }
            val accum = mutableListOf<EditionResult>()
            for (phaseRes in chan) {
                for (ed in phaseRes) accum.addEditionIfNew(ed, accumSeen)
                // Orden mínimo: es primero, con portada primero. La ordenación
                // completa (SpanishScore + reclasificación) va en el resultado final.
                val ordered = accum.sortedWith(
                    compareByDescending<EditionResult> { it.language == "es" }
                        .thenByDescending { it.coverUrl != null }
                )
                try { onPartial!!.invoke(ordered) } catch (_: Exception) {}
            }
        }
    }

    // A, B, C y E en paralelo (Phase H — Hardcover — eliminada en v2.7 doc / código actual).
    for (phase in listOf(phaseA, phaseB, phaseC, phaseE)) {
        try { for (ed in phase.await()) merged.addEditionIfNew(ed, mergedSeen) }
        catch (_: Exception) {}
    }
    // Todas las fases terminadas → cerrar canal para que el consumidor salga del for
    partialChan?.close()
    partialConsumer?.await()
    com.lecturameter.utils.AppLogger.log(
        "Merge: ${merged.size} ediciones únicas en ${System.currentTimeMillis() - tStart}ms totales", "EditionSearch")

    // Inyección de edición española VERIFICADA (libros con Works ES/EN separados en OL).
    // Sustituye a la variante regional pobre (ej. mexicana sin "El" y sin páginas) si comparte ISBN.
    knownSpanishEdition(originalTitle)?.let { (olid, _) ->
        val known = try { fetchOlEditionById(olid) } catch (_: Exception) { null }
        if (known != null) {
            merged.removeAll { cleanIsbn(it.isbn) != null && cleanIsbn(it.isbn) == cleanIsbn(known.isbn) }
            merged.add(known)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // REWORK v17.7 — Sistema de decisión post-merge
    // ═════════════════════════════════════════════════════════════════════════
    // Tres pasos claros y sin parches superpuestos:
    //   1. Validación de pertenencia (mixto): la edición debe pertenecer al
    //      libro buscado. Pasa si tiene ISBN del Work (lo trajo Phase A/D vía
    //      el ISBN original) O si su título supera 40% de similitud con
    //      originalTitle/searchTitle. Defensa contra Hamlet/Macbeth/Pinocho.
    //   2. Reclasificación de idioma: para entrar en la COLUMNA ESPAÑOL una
    //      edición debe ser de ESPAÑA estricto (ISBN 978-84/979-13 o editorial
    //      española). Las "es" sin señal España se degradan a su idioma real
    //      basado en ISBN, y caen en Global.
    //   3. SpanishScore reforzado: ISBN España domina (+100), páginas>1 suben
    //      (+35), título exacto (+30), idioma es (+40), editorial (+20),
    //      portada (+15). El ISBN España vale más que la suma de muchos otros.
    // ═════════════════════════════════════════════════════════════════════════

    // ── Paso 0: helpers ────────────────────────────────────────────────────────
    fun isSpainIsbn(isbnStr: String?): Boolean {
        val c = isbnStr?.filter { it.isDigit() } ?: return false
        return c.startsWith("97884") || c.startsWith("97913") || (c.length == 10 && c.startsWith("84"))
    }
    val spainPublishers = listOf(
        "minotauro", "nova", "ediciones b", "planeta", "alfaguara", "destino", "anaya",
        "booket", "debolsillo", "montena", "noguer", "timun mas", "timun más", "fantascy",
        "umbriel", "edaf", "martinez roca", "martínez roca", "norma editorial", "dolmen",
        "la factoría de ideas", "factoria de ideas", "penguin random house españa", "roca editorial",
        "ediciones b", "salamandra", "siruela", "valdemar", "gigamesh", "alianza editorial"
    )
    fun hasSpainPublisher(publisher: String): Boolean {
        if (publisher.isBlank()) return false
        val p = publisher.lowercase()
        return spainPublishers.any { p.contains(it) }
    }
    fun isSpainStrict(ed: EditionResult): Boolean = isSpainIsbn(ed.isbn) || hasSpainPublisher(ed.publisher)

    // ── Paso 1: validación de pertenencia al libro buscado ─────────────────────
    // Tokens significativos (≥4 chars) de ambos títulos. Una edición pertenece si
    // (a) su ISBN coincide con el del libro original, o (b) supera 40% de coincidencia
    // con originalTitle o con searchTitle (lo que sea más alto).
    val originalIsbn = cleanIsbn(isbn)
    fun normalizedTokens(s: String): Set<String> = normalizedEditionText(s).split(" ").filter { it.length >= 4 }.toSet()
    val refTokensOriginal = normalizedTokens(originalTitle)
    val refTokensSearch   = normalizedTokens(searchTitle)
    fun similarityRatio(candidate: String, refs: Set<String>): Double {
        if (refs.isEmpty()) return 1.0
        val c = normalizedEditionText(candidate)
        val hits = refs.count { c.contains(it) }
        return hits.toDouble() / refs.size
    }
    fun belongsToBook(ed: EditionResult): Boolean {
        if (originalIsbn != null && cleanIsbn(ed.isbn) == originalIsbn) return true
        val ratio = maxOf(similarityRatio(ed.title, refTokensOriginal), similarityRatio(ed.title, refTokensSearch))
        return ratio >= 0.4
    }
    val validated = merged.filter { belongsToBook(it) }.toMutableList()

    // ── Paso 2: reclasificación de idioma (España estricto en columna ES) ─────
    val reclassified = validated.map { ed ->
        if (ed.language == "es" && !isSpainStrict(ed)) {
            // Edición etiquetada como español pero sin señal España: sacar de columna ES.
            // Reasignar idioma según ISBN real (puede ser inglés, mexicano, etc.).
            val realLang = isbnToLanguageMeta(ed.isbn.orEmpty())
            // Si el ISBN sigue diciendo "es" (caso ISBN sudamericano), forzar "original" para mantenerla fuera de columna ES.
            if (realLang.first == "es") ed.copy(language = "original", languageLabel = "Original", flag = "🌐")
            else ed.copy(language = realLang.first, languageLabel = realLang.second, flag = realLang.third)
        } else ed
    }.toMutableList()

    // ── Paso 3: SpanishScore reforzado ─────────────────────────────────────────
    fun spanishScore(ed: EditionResult): Int {
        var s = 0
        if (isSpainIsbn(ed.isbn)) s += 200                                 // ISBN España domina: vale más que la suma del resto (40+35+30+20+15=140)
        if (ed.language == "es") s += 40                                    // idioma español
        if (ed.pages > 1) s += 35                                           // páginas funcionales (libro real, no stub)
        val cand = normalizedEditionText(ed.title)
        if (cand == normalizedEditionText(originalTitle) || cand == normalizedEditionText(searchTitle)) s += 30  // título exacto
        if (hasSpainPublisher(ed.publisher)) s += 20                         // editorial española
        if (ed.coverUrl != null) s += 15                                    // portada disponible
        return s
    }

    // titleSimilarity: para Global (ordenar por relevancia de título cuando no hay señales españolas)
    fun titleSimilarity(candidate: String): Int {
        val refTokens = normalizedEditionText(title)
            .split(" ").filter { it.length >= 3 }.toSet()
        if (refTokens.isEmpty()) return 0
        val candNorm = normalizedEditionText(candidate)
        return refTokens.count { candNorm.contains(it) }
    }

    reclassified.sortWith(
        compareByDescending<EditionResult> { it.language == "es" }       // columna español primero
            .thenByDescending { isSpainIsbn(it.isbn) }                   // ISBN España aparece SIEMPRE antes que el resto
            .thenByDescending { spanishScore(it) }                       // entre españolas, SpanishScore (ISBN España manda)
            .thenByDescending { it.pages > 1 }                           // luego, páginas funcionales (también para Global)
            .thenByDescending { titleSimilarity(it.title) }              // relevancia de título
            .thenByDescending { it.language == "original" }              // original antes que otros idiomas
            .thenByDescending { it.coverUrl != null }                    // con portada antes que sin
    )
    reclassified.distinctBy { it.isbn ?: "${it.language}|${it.title}|${it.publishYear}" }
} } ?: emptyList()

// ── Búsqueda de ediciones de un libro ────────────────────────────────────────

suspend fun searchOpenLibrary(
    query: String,
    preferredLang: String = "es",
    // Feedback 2.6: callback opcional con snapshots parciales — los resultados van
    // apareciendo por fases en vez de todos a la vez al final.
    onPartial: ((List<OpenLibraryResult>) -> Unit)? = null
): List<OpenLibraryResult> = withContext(Dispatchers.IO) {
    val results = mutableListOf<OpenLibraryResult>()
    val seen = mutableSetOf<String>()
    val prefOl = appLangToOl(preferredLang)   // v2.6: "es"→"spa" etc.

    fun fetchAndParseOL(urlStr: String) {
        try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", APP_USER_AGENT)
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val json = conn.inputStream.bufferedReader().readText()
            val root = JSONObject(json)
            val docs = root.optJSONArray("docs") ?: return
            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                val title = doc.optString("title", "").takeIf { it.isNotBlank() } ?: continue
                val key = doc.optString("key", title)
                if (seen.contains(key)) continue
                seen.add(key)
                // v2.6: array completo de autores para relevancia; primero para UI
                val authorsArr = doc.optJSONArray("author_name")
                val author = authorsArr?.optString(0, "") ?: ""
                val allAuthors = buildString {
                    if (authorsArr != null) for (j in 0 until authorsArr.length()) {
                        append(authorsArr.optString(j, "")); append(' ')
                    }
                }.trim()
                // v2.6: idioma del work. OL da lista de códigos MARC de todas sus ediciones;
                // si incluye el idioma preferido → ese work TIENE edición en idioma del usuario.
                var language = ""
                doc.optJSONArray("language")?.let { langs ->
                    var first = ""
                    for (j in 0 until langs.length()) {
                        val l = langs.optString(j, "")
                        if (first.isBlank() && l.isNotBlank()) first = l
                        if (l.equals(prefOl, ignoreCase = true)) { language = preferredLang; break }
                    }
                    if (language.isBlank() && first.isNotBlank()) language = olLangToCode(first)
                }
                // Feedback 2.6 (búsqueda ES): el campo "editions" de OL trae la edición que
                // mejor casa con la query y el idioma (work "The Name of the Wind" → edición
                // "El nombre del viento", con portada e ISBN propios). Buscar en español
                // fallaba porque la relevancia se medía contra el título EN del work.
                var edDoc: JSONObject? = null
                val edsArr = doc.optJSONObject("editions")?.optJSONArray("docs")
                if (edsArr != null && edsArr.length() > 0) {
                    outer@ for (j in 0 until edsArr.length()) {
                        val e = edsArr.getJSONObject(j)
                        val eLangs = e.optJSONArray("language") ?: continue
                        for (k in 0 until eLangs.length()) {
                            if (eLangs.optString(k, "").equals(prefOl, ignoreCase = true)) {
                                edDoc = e; language = preferredLang; break@outer
                            }
                        }
                    }
                    if (edDoc == null) edDoc = edsArr.getJSONObject(0)
                }
                val edTitle = edDoc?.optString("title")?.ifBlank { null }
                // Feedback 2.7: páginas implausibles (<40) → 0 → cae al median del work
                val edPages = (edDoc?.optInt("number_of_pages", 0) ?: 0).takeIf { it >= MIN_PLAUSIBLE_PAGES } ?: 0
                val edCoverId = edDoc?.optLong("cover_i", -1L) ?: -1L
                val edIsbn = edDoc?.optJSONArray("isbn")?.let { arr ->
                    var f13: String? = null; var f10: String? = null
                    for (j in 0 until arr.length()) {
                        val v = arr.optString(j, "").trim()
                        if (v.length == 13) { f13 = v; break }
                        if (v.length == 10 && f10 == null) f10 = v
                    }
                    f13 ?: f10
                }

                val pages = if (edPages > 0) edPages
                    else doc.optInt("number_of_pages_median", 0).takeIf { it >= MIN_PLAUSIBLE_PAGES } ?: 0  // Feedback 2.7
                val coverId = if (edCoverId > 0) edCoverId else doc.optLong("cover_i", -1L)
                val isbn = edIsbn ?: doc.optJSONArray("isbn")?.let { arr ->
                    var f13: String? = null; var f10: String? = null
                    for (j in 0 until arr.length()) {
                        val v = arr.optString(j, "").trim()
                        if (v.length == 13) { f13 = v; break }
                        if (v.length == 10 && f10 == null) f10 = v
                    }
                    f13 ?: f10
                }
                // v2.6: -L en origen (antes -M de baja calidad; Coil escala hacia abajo bien)
                val coverUrl = when {
                    coverId > 0 -> "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
                    isbn != null -> "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"
                    else -> null
                }
                val subjects = doc.optJSONArray("subject")
                var genre = ""
                if (subjects != null) {
                    val rawSubjs = mutableListOf<String>()
                    for (si in 0 until subjects.length()) rawSubjs.add(subjects.optString(si, ""))
                    val mapped = bestGenreFromRawCandidates(rawSubjs)
                    genre = if (mapped.isNotEmpty()) mapped.joinToString("; ")
                            else if (subjects.length() > 0) subjects.optString(0, "") else ""
                }
                val year = doc.optInt("first_publish_year", 0).takeIf { it > 0 }?.toString() ?: ""
                // Feedback 2.6: título mostrado = el de la edición en el idioma del usuario
                // si existe. El blob de scoring lleva autores + título del work para que la
                // relevancia funcione con la query en ES y con el título original en EN.
                val displayTitle = edTitle ?: title
                val matchBlob = "$allAuthors $title".trim()
                results.add(OpenLibraryResult(displayTitle, author, pages, coverUrl, isbn, genre, year, key, language, matchBlob))
            }
        } catch (_: Exception) {}
    }

    // 1. Buscar en Open Library.
    // v2.6 (búsqueda r1): campo language en fields + lang=<pref> (boost de ranking OL
    // hacia el idioma del usuario) + query adicional filtrada language:<pref> que
    // garantiza works con edición en el idioma del usuario aunque el ranking global
    // no los traiga (caso "El Imperio Final" → cero Sanderson ES).
    val encoded = URLEncoder.encode(query, "UTF-8")
    // Feedback 2.6: + campos "editions" — OL devuelve la edición que mejor casa con la
    // query y el idioma del usuario (título/portada/ISBN/páginas propios).
    val olFields = "key,title,author_name,number_of_pages_median,cover_i,isbn,subject,first_publish_year,language," +
        "editions,editions.title,editions.language,editions.isbn,editions.number_of_pages,editions.cover_i"

    // Relevancia (Feedback 2.6: movida antes de las fases para emitir parciales ya
    // ordenados): tokens de la query (y de su alias ES→EN) contra título+autores.
    // max() con el alias evita matar resultados legítimos del título original
    // (ej. "El hombre Iluminado" → "The Sunlit Man").
    val aliasTitle = resolveSearchTitle(query)
    val qTokens = searchQueryTokens(query)
    val aliasTokens = if (!aliasTitle.equals(query.trim(), ignoreCase = true))
        searchQueryTokens(aliasTitle) else emptySet()
    val relevanceOf = HashMap<String, Double>()
    fun relevance(r: OpenLibraryResult): Double = relevanceOf.getOrPut(r.olKey.ifBlank { r.title }) {
        val direct = searchRelevance(qTokens, r.title, r.matchAuthors.ifBlank { r.author })
        val viaAlias = if (aliasTokens.isNotEmpty())
            searchRelevance(aliasTokens, r.title, r.matchAuthors.ifBlank { r.author }) else 0.0
        maxOf(direct, viaAlias)
    }
    val comparator =
        compareByDescending<OpenLibraryResult> { it.language == preferredLang }  // idioma del usuario primero
            .thenByDescending { relevance(it) }                                  // relevancia real con la query
            .thenByDescending { it.coverUrl != null }                            // con portada antes
            .thenByDescending { it.pages > 1 }                                   // páginas funcionales
            .thenByDescending { it.pages }

    // Feedback 2.6: snapshot filtrado y ordenado tras cada fase → la lista va creciendo
    // en pantalla en vez de aparecer entera al final.
    fun emitPartial() {
        val cb = onPartial ?: return
        cb(results.filter { r -> r.olKey.startsWith("md_") || relevance(r) >= 0.34 }
            .sortedWith(comparator))
    }

    // Feedback 2.7: query tipo ISBN (escaneada o pegada). Antes se enviaba cruda como
    // q=<isbn> y con ISBN-10 OL no resolvía nada ("escanear un ISBN-10 no hace nada").
    // Ahora: detectar el ISBN, buscar con el cualificador isbn: probando forma 13 y 10,
    // y si OL no lo conoce, caer en la cadena completa por ISBN (GB con reintentos +
    // OL edición directa), la misma que usa el alta por escaneo.
    val strippedQ = query.trim().replace(Regex("[\\s-]"), "")
    val isbnQuery = if (Regex("^\\d{13}$|^\\d{9}[\\dXx]$").matches(strippedQ)) strippedQ.uppercase() else null

    // Auditoría APIs r2: throttle 200ms/host también en la búsqueda principal
    if (isbnQuery != null) {
        val isbnAlt = if (isbnQuery.length == 10) isbn10To13(isbnQuery) else isbn13To10(isbnQuery)
        for (candidate in listOfNotNull(isbnQuery, isbnAlt)) {
            ApiThrottle.gate("openlibrary.org")
            fetchAndParseOL("https://openlibrary.org/search.json?q=isbn:$candidate&limit=10&fields=$olFields")
            if (results.isNotEmpty()) break
        }
        emitPartial()
        if (results.isEmpty()) {
            val meta = fetchIsbnFullMetadata(isbnQuery)
            if (!meta.title.isNullOrBlank()) {
                val (lId, _, _) = isbnToLanguageMeta(isbnQuery)
                results.add(OpenLibraryResult(
                    meta.title, meta.author ?: "", meta.pages ?: 0, meta.coverUrl,
                    canonicalIsbn(isbnQuery) ?: isbnQuery, meta.genres.joinToString("; "), "",
                    "isbn_$isbnQuery", language = lId.takeIf { it.length == 2 } ?: ""))
                emitPartial()
            }
        }
    } else {
        ApiThrottle.gate("openlibrary.org")
        fetchAndParseOL("https://openlibrary.org/search.json?q=$encoded&lang=$preferredLang&limit=20&fields=$olFields")
        emitPartial()
        ApiThrottle.gate("openlibrary.org")
        fetchAndParseOL("https://openlibrary.org/search.json?q=$encoded&language=$prefOl&limit=10&fields=$olFields")
        emitPartial()
        if (results.size < 5) {
            ApiThrottle.gate("openlibrary.org")
            fetchAndParseOL("https://openlibrary.org/search.json?title=$encoded&limit=10&fields=$olFields")
            emitPartial()
        }
    }

    // 1b. Alias ES→EN: mismo diccionario que usa "Cambiar edición" (unifica ambos flujos).
    // "El hombre Iluminado" también busca "The Sunlit Man"; cubre typos conocidos (ascuaosura).
    if (!aliasTitle.equals(query.trim(), ignoreCase = true) && results.size < 8) {
        val encodedAlias = URLEncoder.encode(aliasTitle, "UTF-8")
        ApiThrottle.gate("openlibrary.org")
        fetchAndParseOL("https://openlibrary.org/search.json?q=$encodedAlias&limit=10&fields=$olFields")
        emitPartial()
    }

    // 1c. Edición española VERIFICADA para libros problemáticos: se inyecta SIEMPRE
    // la primera, con título/páginas/portada correctos (mismo origen que "Cambiar edición").
    knownSpanishEdition(query)?.let { (olid, knownAuthor) ->
        val known = try { fetchOlEditionById(olid) } catch (_: Exception) { null }
        if (known != null) {
            val knownIsbnClean = cleanIsbn(known.isbn)
            results.removeAll { r ->
                (knownIsbnClean != null && cleanIsbn(r.isbn) == knownIsbnClean) ||
                r.title.trim().equals(known.title.trim(), ignoreCase = true)
            }
            results.add(0, OpenLibraryResult(known.title, knownAuthor, known.pages, known.coverUrl, known.isbn, "", known.publishYear, olid, language = "es"))
        }
    }

    val withCover = results.count { it.coverUrl != null }
    // v2.6: también dispara GB si hay pocos resultados en el idioma del usuario
    val prefLangCount = results.count { it.language == preferredLang }
    val needsGoogleBooks = results.size < 5 || withCover < 3 || prefLangCount < 3
    val needsGenreEnrich = results.any { it.genre.isBlank() }

    // 2. Completar/reemplazar con Google Books si hay pocos resultados, pocas portadas, o géneros vacíos
    if (needsGoogleBooks || needsGenreEnrich) {
        // Feedback 2.7: para queries ISBN, GB con cualificador isbn: (forma canónica 13)
        val gbQuery = if (isbnQuery != null) "isbn:${canonicalIsbn(isbnQuery) ?: isbnQuery}" else query
        val gbResults = fetchGoogleBooksResults(gbQuery, maxResults = 15, preferredLang = preferredLang)
        val olTitlesNorm = results.map { it.title.lowercase().trim() }.toSet()
        for (gb in gbResults) {
            val norm = gb.title.lowercase().trim()
            val dupIdx = results.indexOfFirst { it.title.lowercase().trim() == norm }
            if (dupIdx >= 0) {
                val existing = results[dupIdx]
                val betterCover = existing.coverUrl == null && gb.coverUrl != null
                val betterGenre = existing.genre.isBlank() && gb.genre.isNotBlank()
                val betterPages = gb.pages <= 1 && existing.pages > 1
                val betterLang  = existing.language.isBlank() && gb.language.isNotBlank()   // v2.6
                if (betterCover || betterGenre || betterLang) {
                    results[dupIdx] = existing.copy(
                        coverUrl = if (betterCover) gb.coverUrl else existing.coverUrl,
                        genre    = if (betterGenre) gb.genre    else existing.genre,
                        pages    = if (betterPages) existing.pages else if (gb.pages > existing.pages) gb.pages else existing.pages,
                        language = if (betterLang)  gb.language else existing.language
                    )
                }
            } else if (!olTitlesNorm.contains(norm)) {
                results.add(gb)
            }
        }
        emitPartial()
    }

    // 3. MangaDex — solo si parece manga (keyword en query o resultados ya detectados).
    // Objetivo: portadas por tomo (OL y GB no tienen portadas de tomos individuales) y
    // resultados adicionales cuando OL/GB son pobres para búsquedas de manga/manhwa.
    val lowerQuery = query.lowercase()
    val queryLooksManga = lowerQuery.contains("manga") || lowerQuery.contains("manhwa") ||
        lowerQuery.contains("manhua") || lowerQuery.contains("vol.") ||
        lowerQuery.contains("tomo") || lowerQuery.contains(" vol ") ||
        results.any { it.genre.contains("manga", ignoreCase = true) ||
                      it.genre.contains("cómic", ignoreCase = true) ||
                      it.genre.contains("comic", ignoreCase = true) }
    if (queryLooksManga) {
        try {
            // Buscar serie en MangaDex
            val seriesQuery = query.replace(Regex("""(?i)\b(?:tomo|vol\.?|volumen|#)\s*0*\d{1,3}\b"""), "").trim()
            val mdEncoded = URLEncoder.encode(seriesQuery.ifBlank { query }, "UTF-8")
            val mdConn = URL("https://api.mangadex.org/manga?title=$mdEncoded&limit=10").openConnection() as HttpURLConnection
            mdConn.setRequestProperty("User-Agent", APP_USER_AGENT)
            mdConn.connectTimeout = 7000; mdConn.readTimeout = 7000
            val mdRoot = JSONObject(mdConn.inputStream.bufferedReader().readText())
            val mdData = mdRoot.optJSONArray("data")
            if (mdData != null) {
                val qNorm = normalizedEditionText(seriesQuery.ifBlank { query })
                val qTokens = qNorm.split(" ").filter { it.length >= 3 }.toSet()
                for (i in 0 until mdData.length()) {
                    val manga = mdData.getJSONObject(i)
                    val mangaId = manga.optString("id", "")
                    val attrs = manga.optJSONObject("attributes") ?: continue
                    // Recoger todos los títulos disponibles para matching
                    val titles = mutableListOf<String>()
                    attrs.optJSONObject("title")?.let { t -> t.keys().forEach { k -> titles.add(t.optString(k, "")) } }
                    attrs.optJSONArray("altTitles")?.let { arr ->
                        for (j in 0 until arr.length()) {
                            val obj = arr.getJSONObject(j); obj.keys().forEach { k -> titles.add(obj.optString(k, "")) }
                        }
                    }
                    // Título principal en inglés o el primero disponible
                    val mainTitle = attrs.optJSONObject("title")?.let {
                        it.optString("en", "").ifBlank { null } ?: it.keys().asSequence().firstOrNull()?.let { k -> it.optString(k, "") }
                    } ?: titles.firstOrNull() ?: continue
                    if (mainTitle.isBlank()) continue
                    // Filtrar por similitud — evita falsos positivos
                    val matchScore = titles.maxOfOrNull { t ->
                        val tNorm = normalizedEditionText(t)
                        when {
                            tNorm == qNorm -> 1.0
                            qTokens.isEmpty() -> 0.0
                            else -> qTokens.count { tNorm.contains(it) }.toDouble() / qTokens.size
                        }
                    } ?: 0.0
                    if (matchScore < 0.4) continue
                    // Portada de la serie (primer cover disponible)
                    var coverUrl: String? = null
                    if (mangaId.isNotBlank()) {
                        try {
                            val cConn = URL("https://api.mangadex.org/cover?manga[]=$mangaId&limit=1").openConnection() as HttpURLConnection
                            cConn.setRequestProperty("User-Agent", APP_USER_AGENT)
                            cConn.connectTimeout = 5000; cConn.readTimeout = 5000
                            val covers = JSONObject(cConn.inputStream.bufferedReader().readText()).optJSONArray("data")
                            val fileName = covers?.getJSONObject(0)?.optJSONObject("attributes")?.optString("fileName", "") ?: ""
                            if (fileName.isNotBlank()) coverUrl = "https://uploads.mangadex.org/covers/$mangaId/$fileName"
                        } catch (_: Exception) {}
                    }
                    // Autor desde relationships
                    var mdAuthor = ""
                    manga.optJSONArray("relationships")?.let { rels ->
                        for (j in 0 until rels.length()) {
                            val rel = rels.getJSONObject(j)
                            if (rel.optString("type") == "author") {
                                mdAuthor = rel.optJSONObject("attributes")?.optString("name", "") ?: ""
                                break
                            }
                        }
                    }
                    val normMain = mainTitle.lowercase().trim()
                    val dupIdx = results.indexOfFirst { it.title.lowercase().trim() == normMain }
                    if (dupIdx >= 0) {
                        // Enriquecer con portada si el resultado existente no la tiene
                        val ex = results[dupIdx]
                        if (ex.coverUrl == null && coverUrl != null) results[dupIdx] = ex.copy(coverUrl = coverUrl)
                    } else {
                        results.add(OpenLibraryResult(mainTitle, mdAuthor, 0, coverUrl, null, "Manga", "", "md_$mangaId"))
                    }
                }
            }
        } catch (_: Exception) {}
        emitPartial()
    }

    // ── v2.6 (búsqueda r1): relevancia + idioma del usuario + portadas válidas ──
    // Sustituye la heurística de tildes (fallaba con "El hombre Iluminado", "el nombre",
    // "El Imperio Final": ninguna lleva tilde → no se detectaban como español).
    // (Relevancia y comparator definidos arriba, antes de las fases — Feedback 2.6.)

    // Filtro: fuera resultados sin relación con la query ("A War to Be Won" para
    // "El Imperio Final", "Tiaztlán"…). MangaDex exento: ya filtra con matchScore
    // propio y sus títulos principales pueden diferir del alias buscado.
    results.removeAll { r -> !r.olKey.startsWith("md_") && relevance(r) < 0.34 }

    results.sortWith(comparator)

    // Validación de portadas del top 6 (HEAD <8KB = placeholder tipo "cover to be
    // revealed"). Solo top N: coste de red acotado. Si alguna cae, reordenar.
    coroutineScope {
        val checks = results.take(6).filter { it.coverUrl != null }.map { r ->
            async { r to isCoverUrlValid(r.coverUrl!!) }
        }
        var changed = false
        for (chk in checks) {
            val (r, valid) = try { chk.await() } catch (_: Exception) { continue }
            if (!valid) {
                val i = results.indexOf(r)
                if (i >= 0) { results[i] = r.copy(coverUrl = null); changed = true }
            }
        }
        if (changed) results.sortWith(comparator)
    }

    results.take(20)
}

suspend fun fetchCoverForBook(title: String, author: String, isbn: String?): String? = withContext(Dispatchers.IO) {
    // 1. Intentar Open Library por ISBN
    try {
        if (!isbn.isNullOrBlank()) {
            val url = "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg?default=false"
            val c = URL(url).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", APP_USER_AGENT); c.connectTimeout = 5000; c.readTimeout = 5000
            if (c.responseCode == 200) return@withContext url.removeSuffix("?default=false")
        }
    } catch (_: Exception) {}

    // 2. Buscar en Open Library por título+autor
    try {
        val q = URLEncoder.encode("$title $author", "UTF-8")
        val conn = URL("https://openlibrary.org/search.json?q=$q&limit=5&fields=cover_i,isbn").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT); conn.connectTimeout = 6000; conn.readTimeout = 6000
        val docs = JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("docs")
        if (docs != null) {
            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                val cid = doc.optLong("cover_i", -1L)
                if (cid > 0) return@withContext "https://covers.openlibrary.org/b/id/$cid-M.jpg"
                val isbnArr = doc.optJSONArray("isbn")
                if (isbnArr != null) for (j in 0 until isbnArr.length()) {
                    val v = isbnArr.optString(j, "").trim()
                    if (v.length >= 10) {
                        val u = "https://covers.openlibrary.org/b/isbn/$v-M.jpg?default=false"
                        val c2 = URL(u).openConnection() as HttpURLConnection
                        c2.setRequestProperty("User-Agent", APP_USER_AGENT); c2.connectTimeout = 3000; c2.readTimeout = 3000
                        if (c2.responseCode == 200) return@withContext u.removeSuffix("?default=false")
                    }
                }
            }
        }
    } catch (_: Exception) {}

    // 3. Fallback: Google Books (búsqueda + volume ID directo)
    fetchGoogleBooksMetadata(title, author, isbn).coverUrl
}

