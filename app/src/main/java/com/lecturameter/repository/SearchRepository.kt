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

// Feedback 17-07: año de publicación robusto. Antes se hacía take(4)/takeLast(4) sobre la
// fecha cruda, que va bien con Google Books ("2020-10-01") pero NO con OpenLibrary, cuyo
// publish_date es texto libre ("Oct 2020", "October 1999"): take(4) daba "Oct " y se
// guardaba el mes como año. Aquí se extrae la PRIMERA secuencia año 1xxx/2xxx de donde sea.
private val YEAR_RE = Regex("(1\\d{3}|2\\d{3})")
private fun yearOf(raw: String): String = YEAR_RE.find(raw)?.value ?: ""

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

// Relevancia SOLO contra el título, y por PALABRA en vez de por subcadena.
//
// Existe por un caso medido en el móvil el 21-07: buscando "La torre", el primer resultado
// era "The Giver of Stars" de Jojo Moyes. No era un fallo del catálogo, que devolvió lo que
// se le pidió: la ficha lleva a su traductor, "Jesús de la Torre Olid", así que el token
// "torre" aparecía de verdad en sus metadatos y searchRelevance le daba 1,00. El segundo
// resultado, "La porte étroite", casaba por la traductora "Blanca Torrents", y ahí ni
// siquiera como palabra: `contains` encuentra "torre" DENTRO de "torrents".
//
// Con todos empatados a 1,00 el filtro de relevancia no filtraba nada y el desempate caía
// siempre en el idioma de la app, que es como un libro sin relación encabezaba la lista.
//
// Esto NO sustituye a searchRelevance: el filtro global sigue usando título+autores, para
// que buscar por autor siga funcionando. Esto solo ORDENA, poniendo delante a quien lleva
// la consulta en el título.
private fun searchTitleRelevance(tokens: Set<String>, title: String): Double {
    if (tokens.isEmpty()) return 1.0
    val palabras = normalizedEditionText(title).split(" ").filter { it.isNotBlank() }
    if (palabras.isEmpty()) return 0.0
    return tokens.count { t ->
        // Palabra exacta, o la del título empieza por el token y solo la alarga en 1 o 2
        // letras (plurales y flexiones: "torre" vale para "torres", no para "torrents").
        palabras.any { p -> p == t || (p.startsWith(t) && p.length - t.length <= 2) }
    }.toDouble() / tokens.size
}

data class BookMetadata(
    val coverUrl: String? = null,
    val genres: List<String> = emptyList()
)

// Fuentes manga (md_ = MangaDex, kt_ = Kitsu): quedan exentas del filtro de relevancia
// global porque ya filtran con su propio matchScore contra TODOS los títulos de la serie,
// y el título que acaban mostrando puede no parecerse a la query (se busca en español y la
// ficha viene en inglés o japonés). (cv_ = Comic Vine retirado 21-07 por licencia.)
internal fun isMangaSourceKey(olKey: String): Boolean =
    olKey.startsWith("md_") || olKey.startsWith("kt_")

// ── Google Books search ───────────────────────────────────────────────────────

private suspend fun fetchGoogleBooksResults(query: String, maxResults: Int = 15, preferredLang: String = "es"): List<OpenLibraryResult> {
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
            val publishYear = yearOf(info.optString("publishedDate", ""))
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
    // RF-M18: las tres llamadas pasan por httpGetTextWithRetry. Antes leían inputStream
    // directo y un HTTP 429 salía como FileNotFoundException tragada en silencio; ahora
    // el código de estado es visible y un 429 activa el cooldown de sesión gbQuotaExhausted.
    fun parseGbBody(body: String?) {
        val items = body?.let { try { JSONObject(it).optJSONArray("items") } catch (_: Exception) { null } }
        parseGbItems(items)
    }
    parseGbBody(httpGetTextWithRetry(
        withGbKey("https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=$maxResults&printType=books"),
        "GB_search", retries = 1))
    // v2.6 (búsqueda r1): langRestrict = idioma de la app, no "es" fijo.
    parseGbBody(httpGetTextWithRetry(
        withGbKey("https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=$maxResults&printType=books&langRestrict=$preferredLang"),
        "GB_search_lang", retries = 1))
    // v2.6: 3) CON langRestrict=ca — ediciones catalanas (Sanderson en català etc.)
    //    nunca salían: ni el top global ni el filtro es las traían. Solo si app en español.
    if (preferredLang == "es") parseGbBody(httpGetTextWithRetry(
        withGbKey("https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=10&printType=books&langRestrict=ca"),
        "GB_search_ca", retries = 1))
    return results
}

// Auditoría APIs r2: User-Agent identificable con contacto. OpenLibrary da prioridad
// BAJA y limita agresivamente a clientes sin UA claro. Aplicado a TODAS las APIs.
// Deriva de versionName (no `const`) para que no se quede obsoleto: antes decia 2.5 con
// la app ya en 3.1. Con esto la version siempre cuadra con la del build.
internal val APP_USER_AGENT = "Lecturameter/${com.lecturameter.BuildConfig.VERSION_NAME} (lecturameter.app@gmail.com)"

// Fase 7: la API key de Google Books viaja de local.properties → BuildConfig → aquí.
// Autentica todas las llamadas a googleapis.com/books (sin key ⇒ cuota anónima y 429
// frecuentes). Si el build no tiene key, la URL queda intacta y todo sigue funcionando.
internal fun withGbKey(url: String): String {
    val key = com.lecturameter.BuildConfig.GOOGLE_BOOKS_API_KEY
    if (key.isBlank()) return url
    return url + (if ('?' in url) "&" else "?") + "key=" + key
}

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
 * ¿Los primeros bytes de la respuesta son la portada fantasma en vez de una imagen?
 *
 * Open Library responde HTTP 200 con un **GIF transparente de 1x1 y 43 bytes**, servido
 * con extensión .jpg, cuando NO tiene portada para ese ISBN. No es un caso raro: medido
 * el 21-07 sobre 22 obras del catálogo sin `cover_id`, **18 (82%)** contestan eso.
 *
 * Se mira por dos vías independientes, porque cada una sola tiene un punto ciego:
 * el tamaño (ninguna portada de verdad cabe en 200 bytes) y la firma GIF (si un día
 * engordan el pixel, sigue sin ser un JPEG). Función pura: ver CoverPhantomTest.
 */
internal fun esPortadaFantasma(bytes: ByteArray, leidos: Int): Boolean {
    if (leidos <= 200) return true
    return leidos >= 4 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() && bytes[3] == '8'.code.toByte()
}

/**
 * Valida que una URL de portada devuelva una imagen real (>8 KB).
 * Los placeholders tipo "COVER TO BE REVEALED" de TOR/Gollancz suelen
 * tener menos de 8 KB.
 *
 * Cuando el servidor NO informa del tamaño hay que bajar a mirar los bytes. Antes se
 * asumía válida, y eso dejaba muerto medio pipeline de portadas: `covers.openlibrary.org`
 * contesta al HEAD con 200 y SIN `Content-Length`, así que TODA portada por ISBN se daba
 * por buena, fantasmas incluidas. Al quedarse con `coverUrl != null`, el bloque que pide
 * la portada a Google Books (filtra por `coverUrl == null`) no se activaba nunca para las
 * 250.000 obras de la Biblioteca Nacional, que son justo las que lo necesitan.
 */
private suspend fun isCoverUrlValid(url: String): Boolean = withContext(Dispatchers.IO) {
    // covers.openlibrary.org NUNCA manda Content-Length (verificado con HEAD el 21-07: 200 OK
    // con seis cabeceras y ninguna de tamaño). Hacerle un HEAD es gastar una petición para no
    // enterarse de nada y además pagar dos veces el throttling. Se va directo a los bytes.
    if (url.contains("covers.openlibrary.org")) return@withContext !esPortadaFantasmaEnRed(url)
    try {
        ApiThrottle.gate(URL(url))   // throttling 200ms/host (auditoría r2)
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 3_000; conn.readTimeout = 3_000
        val code = conn.responseCode
        val len = conn.contentLength
        conn.disconnect()
        when {
            code != 200 -> false
            len >= 0 -> len > 8_000
            else -> !esPortadaFantasmaEnRed(url)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        // RF-M16: al cancelar hay que propagar, no devolver true (marcaría la
        // portada como válida sin haberla comprobado)
        throw e
    } catch (_: Exception) { true }
}

/**
 * Pide solo la cabecera del fichero (1 KB) para distinguir una portada de verdad del GIF
 * de relleno. Se lee poco a propósito: basta para el tamaño y la firma, y no se descarga
 * una imagen que a lo mejor no se va a enseñar. Ante la duda devuelve false (la trata como
 * válida): equivocarse aquí hacia el "sí" solo deja una portada de más, y BookCover todavía
 * la caza al pintarla comprobando el tamaño real del drawable.
 */
private suspend fun esPortadaFantasmaEnRed(url: String): Boolean = withContext(Dispatchers.IO) {
    try {
        ApiThrottle.gate(URL(url))
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.setRequestProperty("Range", "bytes=0-1023")
        // 10 s de lectura, no 5: medido el 21-07 sobre 22 portadas por ISBN, la respuesta
        // tarda de 1,0 a 7,4 s (media 3,9). Con 5 s expiraban las lentas, y al expirar se
        // daba la portada por buena, que es justo el fallo que este código viene a arreglar.
        conn.connectTimeout = 3_000; conn.readTimeout = 10_000
        // 206 si respeta el Range, 200 si lo ignora y manda el fichero entero: en ese caso
        // leemos 1 KB y cerramos, que corta la descarga igual.
        if (conn.responseCode !in listOf(200, 206)) { conn.disconnect(); return@withContext false }
        val buf = ByteArray(1024)
        var leidos = 0
        conn.inputStream.use { input ->
            while (leidos < buf.size) {
                val n = input.read(buf, leidos, buf.size - leidos)
                if (n <= 0) break
                leidos += n
            }
        }
        conn.disconnect()
        esPortadaFantasma(buf, leidos)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) { false }
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
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
    catch (_: Exception) { null }
}
        // Mantenemos zoom=1 (thumbnail garantizado). zoom=0 a veces devuelve 1×1 px
        // transparente en libros sin imagen de alta resolución, lo que causa portadas rotas.
        // fetchGoogleBooksVolumeImage ya intenta extraLarge/large/medium antes del thumbnail.

/** Llama directamente al endpoint de un volumen para obtener la mejor imagen disponible. */
private fun fetchGoogleBooksVolumeImage(volumeId: String): String? {
    if (gbQuotaExhausted) return null   // RF-M18: cuota GB agotada esta sesión → skip instantáneo
    return try {
        val url = withGbKey("https://www.googleapis.com/books/v1/volumes/${URLEncoder.encode(volumeId, "UTF-8")}")
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

// RF-M18: cooldown de sesión para Google Books. La cuota agotada (diaria, por IP)
// devuelve 429 en el 100% de las llamadas (confirmado en la auditoría de APIs del
// 20-07): en cuanto se detecta un 429 de GB, el resto de fases GB de ESTA sesión de
// app se saltan al instante en vez de quemar tiempo en llamadas que van a fallar.
@Volatile private var gbQuotaExhausted = false

private fun isGoogleBooksUrl(url: String): Boolean =
    url.contains("googleapis.com/books") || url.contains("books.google.com")

// Auditoría APIs 20-07: MangaDex bloqueado a nivel de ISP (España) rechaza la conexión
// al instante (HTTP 000). Se trata igual que un 404: al primer fallo de conexión en
// frío se marca el host como caído para la sesión y las fases MangaDex siguientes
// saltan directas al fallback (Kitsu/AniList) sin gastar red ni tiempo.
@Volatile private var mangaDexUnreachable = false

// Fallo de conexión en frío: rechazo TCP, reset o DNS. NO incluye timeouts de lectura
// (SocketTimeoutException extiende InterruptedIOException, no SocketException).
private fun isColdConnectionFailure(e: Exception): Boolean =
    e is java.net.ConnectException || e is java.net.SocketException || e is java.net.UnknownHostException

// Feedback 2.6: GET con control explícito del código HTTP y reintentos con backoff.
// Google Books sin API key devuelve 429 con frecuencia; HttpURLConnection en Android
// lo convierte en FileNotFoundException, así que la cadena de escaneo "fallaba" entera
// sin estarlo (ver ISBN SCAN LOG: P1 GB_isbn FAIL FileNotFoundException = HTTP 429).
private suspend fun httpGetTextWithRetry(url: String, tag: String, retries: Int = 2): String? {
    var attempt = 0
    while (true) {
        // RF-M18: con la cuota GB agotada esta sesión, ni siquiera se intenta la llamada
        if (gbQuotaExhausted && isGoogleBooksUrl(url)) {
            com.lecturameter.utils.AppLogger.log("$tag SKIP (cuota GB agotada esta sesión)", "IsbnScan")
            return null
        }
        try {
            ApiThrottle.gate(java.net.URL(url))
            val conn = java.net.URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", APP_USER_AGENT)
            conn.connectTimeout = 6000; conn.readTimeout = 6000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code in 200..299) return conn.inputStream.use { it.bufferedReader().readText() }
            try { conn.errorStream?.close() } catch (_: Exception) {}
            // RF-M18: un 429 de Google Books = cuota agotada. Cooldown de sesión y fuera:
            // reintentar contra una cuota diaria agotada solo quema tiempo del presupuesto.
            if (code == 429 && isGoogleBooksUrl(url)) {
                gbQuotaExhausted = true
                com.lecturameter.utils.AppLogger.log("$tag HTTP 429 GB: cuota agotada, cooldown de sesión activado", "IsbnScan")
                return null
            }
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

// B1 (4B): editoriales de manga en Espana. Senal para reactivar el enriquecimiento por serie
// cuando un tomo llega SOLO con genero "comic"/"novela grafica" y SIN numero de tomo en el
// titulo (el caso que la exclusion de "comic" como senal manga dejaba sin portada/genero).
// Se incluyen mixtas (Norma, Planeta Comic) porque el falso positivo del comic occidental de
// esas mismas editoriales lo frena la validacion por titulo de las tres APIs de manga
// (matchScore/pertenencia >= 0.4-0.5): un comic europeo no casa fuerte con una serie japonesa.
private val SPAIN_MANGA_PUBLISHERS = listOf(
    "norma editorial", "planeta comic", "planeta cómic", "ivrea", "milky way",
    "distrito manga", "panini manga", "ediciones babylon", "kitsune manga",
    "arechi manga", "fandogamia", "tomodomo", "ponent mon", "editorial hidra", "odaiba"
)
private fun hasSpainMangaPublisher(publisher: String?): Boolean {
    val p = publisher?.lowercase() ?: return false
    return SPAIN_MANGA_PUBLISHERS.any { p.contains(it) }
}

// Feedback 23-07: un titulo de una edicion espanola no deberia traer kana/kanji. Si los trae
// (p. ej. OL devuelve el titulo japones de un tomo de manga), el registro esta flojo y conviene
// recomponerlo por la cadena por ISBN. Rangos: hiragana/katakana + CJK unificado (+ ext. A).
private fun hasCjk(s: String): Boolean = s.any {
    it.code in 0x3040..0x30FF || it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF
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
    var publisher: String? = null   // B1 (4B): senal de editorial para reactivar el enriquecimiento manga

    // Catálogo local: lookup instantáneo por ISBN, sin red. NO corta la cadena online a
    // propósito: las fases P1-P4 aportan consenso de páginas y géneros que el catálogo no
    // tiene. Se guarda aquí y al final rellena solo los huecos que la red haya dejado, que
    // es lo que hace que escanear funcione sin conexión.
    val catalogHit = try {
        CatalogRepository.lookupIsbn(isbn)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e   // RF-M16
    } catch (e: Exception) {
        com.lecturameter.utils.AppLogger.log("P0 catálogo local FAIL: ${e.javaClass.simpleName}", "IsbnScan")
        null
    }
    if (catalogHit != null) {
        com.lecturameter.utils.AppLogger.log(
            "P0 catálogo local: HIT título=${catalogHit.title} pages=${catalogHit.pages}", "IsbnScan")
    }
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
        // B1 (4B): la editorial es la senal que reactiva el enriquecimiento manga para tomos
        // etiquetados solo como "comic". GB es donde el manga espanol (Norma/Planeta) se indexa.
        if (publisher.isNullOrBlank()) publisher = info.optString("publisher").ifBlank { null }
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
                withGbKey("https://www.googleapis.com/books/v1/volumes?q=$query&maxResults=3&printType=books"),
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
                withGbKey("https://www.googleapis.com/books/v1/volumes/${URLEncoder.encode(volumeId, "UTF-8")}"),
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
            if (publisher.isNullOrBlank()) {   // B1 (4B): fuente secundaria de editorial si GB no la trajo
                val pubs = book.optJSONArray("publishers")
                if (pubs != null && pubs.length() > 0)
                    publisher = pubs.optJSONObject(0)?.optString("name")?.ifBlank { null }
            }
        }
        com.lecturameter.utils.AppLogger.log(
            "P2 OL_bibkeys: found=${book != null} pages=${pages ?: "-"} cover=${coverUrl != null} ${System.currentTimeMillis() - t0}ms",
            "IsbnScan")
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
    catch (e: Exception) {
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
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
        catch (e: Exception) {
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
                withGbKey("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=5&printType=books"),
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
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
        catch (e: Exception) {
            com.lecturameter.utils.AppLogger.log("P4 GB_title+author FAIL: ${e.javaClass.simpleName}: ${e.message}", "IsbnScan")
        }
    } else if (pages != null) {
        com.lecturameter.utils.AppLogger.log("P4 GB_title+author SKIP (páginas ya obtenidas)", "IsbnScan")
    } else {
        com.lecturameter.utils.AppLogger.log("P4 GB_title+author SKIP (sin título para query)", "IsbnScan")
    }

    // Feedback 2.7: resolución final de páginas por consenso entre todas las fuentes
    pages = consensusPages(pageVotes)

    // El catálogo local rellena SOLO lo que la red no haya conseguido. Va al final, no al
    // principio, para no contaminar el consenso de páginas: si las fases online han
    // respondido, mandan ellas. Si no hay conexión, todo esto viene del catálogo y el
    // escaneo funciona igualmente.
    if (catalogHit != null) {
        if (title.isNullOrBlank()) title = catalogHit.title
        if (author.isNullOrBlank()) author = catalogHit.author.ifBlank { null }
        if (pages == null && catalogHit.pages > 0) pages = catalogHit.pages
        if (coverUrl == null) coverUrl = catalogHit.coverUrl
        if (rawGenres.isEmpty() && catalogHit.genre.isNotBlank()) rawGenres.add(catalogHit.genre)
    }

    // Feedback 22-07 (MangaResolver, paso 2): si es un TOMO DE MANGA y le falta portada o
    // genero real, enriquecer por SERIE con las APIs de manga (van por serie, NO por ISBN: el
    // ISBN del escaneo puede ser una edicion extranjera). Orden D2: Kitsu -> AniList -> MangaDex
    // (bloqueado por ISP en ES, ultimo; aporta la portada del TOMO exacto via volumeNum). NUNCA
    // se tocan paginas ni ISBN: la identidad ya la fijaron el catalogo/GB por ISBN.
    run {
        val t = title
        fun needCover() = coverUrl == null
        fun needGenre() = bestGenreFromRawCandidates(rawGenres).isEmpty()
        // Feedback 22-07 (comics P1): NO disparar el enriquecimiento manga solo por genero
        // "comic"/"cómic". El manga espanol (Norma/Planeta via Google Books) se etiqueta
        // "Comics & Graphic Novels" SIN "manga", asi que excluir por ese genero lo degradaria;
        // y a la vez un comic occidental sin patron de tomo colaba a las APIs de manga y se
        // llevaba portada/genero japones. Se dispara solo con senal manga explicita
        // (manga/manhwa/manhua/webtoon) o patron de tomo en el titulo (Kitsu/AniList/MangaDex
        // ya filtran ademas por matchScore >= 0.4, asi que un tomo occidental no casa fuerte).
        val mangaSignal = rawGenres.any { g ->
            g.contains("manga", true) || g.contains("manhwa", true) ||
            g.contains("manhua", true) || g.contains("webtoon", true)
        }
        // B1 (4B): recuperar el manga espanol etiquetado SOLO como "comic"/"novela grafica" y sin
        // numero de tomo. Se dispara cuando hay genero de comic Y la editorial es de manga en
        // Espana. La pertenencia por titulo de las APIs evita el falso positivo del comic occidental.
        val comicGenre = rawGenres.any { g ->
            g.contains("comic", true) || g.contains("cómic", true) ||
            g.contains("graphic novel", true) ||
            g.contains("novela gráfica", true) || g.contains("novela grafica", true)
        }
        val publisherMangaSignal = comicGenre && hasSpainMangaPublisher(publisher)
        val looksManga = t != null && (mangaSignal || publisherMangaSignal || extractVolumeNumber(t) != null)
        if (t != null && looksManga && (needCover() || needGenre())) {
            val (serie, tomo) = extractSeriesAndVolume(t)
            val q = serie.ifBlank { t }
            if (needCover() || needGenre()) {
                val kitsu = try { fetchKitsuMangaResults(q, "es") }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { emptyList() }
                kitsu.firstOrNull()?.let { r ->
                    if (needCover()) coverUrl = r.coverUrl
                    if (needGenre() && r.genre.isNotBlank()) rawGenres.add(r.genre)
                }
            }
            if (needCover() || needGenre()) {
                val an = fetchAniListMetadata(q, author ?: "")
                if (needCover()) coverUrl = an.coverUrl
                if (needGenre()) rawGenres.addAll(an.genres)
            }
            if ((needCover() || needGenre()) && !mangaDexUnreachable) {
                val md = fetchMangaDexMetadata(q, tomo)
                if (needCover()) coverUrl = md.coverUrl
                if (needGenre()) rawGenres.addAll(md.genres)
            }
            com.lecturameter.utils.AppLogger.log(
                "MangaResolver escaneo: serie=$serie tomo=${tomo ?: "-"} pubSignal=$publisherMangaSignal pub=${publisher ?: "-"} cover=${coverUrl != null} genres=${rawGenres.size}", "IsbnScan")
        }
    }

    val genres = bestGenreFromRawCandidates(rawGenres)
    com.lecturameter.utils.AppLogger.log(
        "RESULTADO: title=${title != null} author=${author != null} pages=${pages ?: "-"} votos=${pageVotes.map { it.first }} genres=${genres.size} cover=${coverUrl != null} total=${System.currentTimeMillis() - tStart}ms",
        "IsbnScan")
    val result = IsbnFullMetadata(title = title, author = author, pages = pages, genres = genres, coverUrl = coverUrl)
    if (title != null || pages != null || coverUrl != null) isbnMetaCache[isbn] = result
    return result
}

private fun fetchGoogleBooksMetadata(title: String, author: String, isbn: String?): BookMetadata {
    if (gbQuotaExhausted) return BookMetadata()   // RF-M18: cuota GB agotada esta sesión → skip instantáneo
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
            val url = withGbKey("https://www.googleapis.com/books/v1/volumes?q=$query&maxResults=8&printType=books")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", APP_USER_AGENT)
            conn.connectTimeout = 6000; conn.readTimeout = 6000
            val code = conn.responseCode
            if (code == 429) {
                gbQuotaExhausted = true
                try { conn.errorStream?.close() } catch (_: Exception) {}
                return BookMetadata()
            }
            if (code !in 200..299) { try { conn.errorStream?.close() } catch (_: Exception) {}; continue }
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
            val url = "https://openlibrary.org/search.json?$query&limit=5&fields=key,title,cover_i,isbn,subject"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", APP_USER_AGENT)
            conn.connectTimeout = 6000; conn.readTimeout = 6000
            val docs = conn.inputStream.use { JSONObject(it.bufferedReader().readText()) }.optJSONArray("docs") ?: continue
            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                // Guardia anti-colisión de ISBN (auditoría 20-07): en el lookup por ISBN,
                // un doc cuyo título no se parece en nada al del libro es un registro
                // sucio de OL (ISBNs de otra obra) → no tomar su portada ni sus subjects.
                if (query.startsWith("isbn:") && !titlesLooselyMatch(doc.optString("title", ""), title)) {
                    com.lecturameter.utils.AppLogger.log(
                        "OL search $query descartado por colisión: \"${doc.optString("title", "")}\" no se parece a \"$title\"", "Search")
                    continue
                }
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
private fun fetchOpenLibraryBooksApi(isbn: String?, refTitle: String = ""): BookMetadata {
    if (isbn.isNullOrBlank()) return BookMetadata()
    return try {
        val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&jscmd=data&format=json"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val root = JSONObject(conn.inputStream.bufferedReader().readText())
        val book = root.optJSONObject("ISBN:$isbn") ?: return BookMetadata()
        // Guardia anti-colisión de ISBN (auditoría 20-07): si el registro de OL para
        // este ISBN es de OTRO libro, no heredar su portada ni sus géneros.
        val olTitle = book.optString("title", "")
        if (!titlesLooselyMatch(olTitle, refTitle)) {
            com.lecturameter.utils.AppLogger.log(
                "OL /api/books ISBN:$isbn descartado por colisión: \"$olTitle\" no se parece a \"$refTitle\"", "Search")
            return BookMetadata()
        }
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
        val searchTitle = extractSeriesAndVolume(title).first
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
// Regex UNICA del marcador de tomo/volumen (grupo 1 = numero). Consolidada: antes estaba
// copiada en fetchMangaUpdatesMetadata, fetchMangaDexMetadata y las fases 3a/3b de
// searchOpenLibrary. Si se amplian los marcadores (parte, libro, romanos) se toca aqui.
// El "#" NO lleva \b delante: "Berserk #3" (# tras espacio) no casaria, y Goodreads escribe
// justo asi ("Serie, #9"). Los marcadores de palabra (tomo/vol/volumen) SI llevan \b cada uno.
internal val VOLUME_MARKER_REGEX = Regex("""(?i)(?:\btomo|\bvol\.?|\bvolumen|#)\s*0*(\d{1,3})\b""")

private fun extractVolumeNumber(title: String): Int? =
    VOLUME_MARKER_REGEX.find(title)?.groupValues?.get(1)?.toIntOrNull()

// (serie sin el marcador de tomo, numero de tomo si lo hay). Puro y testeable (MangaVolumeTest,
// caso CP-4). La serie puede quedar en blanco si el titulo era solo el marcador; el llamante
// decide el fallback con .ifBlank { ... }. Base del MangaResolver (serie+tomo, no por ISBN).
internal fun extractSeriesAndVolume(title: String): Pair<String, Int?> =
    VOLUME_MARKER_REGEX.replace(title, "").trim() to extractVolumeNumber(title)

// ── MangaDex REST API ─────────────────────────────────────────────────────────
// Sin key. A diferencia de AniList (datos a nivel de SERIE), MangaDex expone
// portadas POR TOMO vía su endpoint /cover con campo "volume" — esto es justo lo
// que faltaba: información real por tomo, no solo de la serie en conjunto.
private fun fetchMangaDexMetadata(title: String, volumeNum: Int?): BookMetadata {
    // Auditoría 20-07: host marcado como caído esta sesión → fallback (Kitsu/AniList) directo
    if (mangaDexUnreachable) return BookMetadata()
    return try {
        // Quitar el marcador de tomo/volumen para buscar la SERIE (MangaDex indexa por serie)
        val seriesTitle = extractSeriesAndVolume(title).first
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
    } catch (e: Exception) {
        // Auditoría 20-07: bloqueo ISP (HTTP 000, rechazo al instante) → tratar como 404:
        // marcar la sesión para que las fases MangaDex restantes salten al fallback rápido.
        if (isColdConnectionFailure(e)) {
            mangaDexUnreachable = true
            com.lecturameter.utils.AppLogger.log(
                "MangaDex inaccesible (${e.javaClass.simpleName}) → fallback Kitsu/AniList el resto de la sesión", "Search")
        }
        BookMetadata()
    }
}

// ── Kitsu JSON:API ────────────────────────────────────────────────────────────
// P-029: cuarta fuente manga (pública, sin key, JSON:API). Nivel de SERIE, como
// AniList y MangaUpdates: NO tiene datos por tomo (verificado contra la API real:
// /manga/<id>/installments devuelve count=0 y los capítulos no traen thumbnail),
// así que las portadas por tomo siguen siendo cosa de MangaDex.
//
// Por qué entra igualmente:
//   1) MangaDex está bloqueado en algunas redes (la de Víctor entre ellas): cuando
//      cae, la ruta manga se queda sin fuente propia. Kitsu la cubre.
//   2) Aporta GÉNEROS reales vía `categories` (Action, Horror, Shounen…). MangaDex
//      aquí mete un "Manga" fijo y nada más, y el género es justo el punto flaco
//      de los metadatos de manga.
//
// Un único GET lo trae todo (verificado): filter[text] + include=categories,staff.person.
// El dominio canónico hoy es kitsu.app (kitsu.io sigue vivo como espejo y las
// imágenes ya se sirven desde media.kitsu.app). Solo HTTPS: la app no permite cleartext.
private const val KITSU_API_BASE = "https://kitsu.app/api/edge"

// Subtipos de Kitsu que NO son manga: son novela (ligera). No deben etiquetarse
// como "Manga" — filter[text]=naruto, por ejemplo, devuelve "Naruto Ninden Series",
// que es subtype=novel.
private val KITSU_NOVEL_SUBTYPES = setOf("novel")

// Con include=categories,staff.person cada resultado engorda la respuesta: 10 series
// son ~82 KB (medido). 5 bastan (Kitsu ya ordena por relevancia) y bajan a ~40 KB,
// que en datos móviles importa.
private const val KITSU_PAGE_LIMIT = 5

// Tope de resultados NUEVOS que Kitsu puede añadir a la lista. Enriquecer los que ya
// están no tiene tope (eso solo suma). Buscar "Berserk" devuelve media docena de
// series legítimamente llamadas "Berserk algo": son reales, pero no pueden sepultar
// a OpenLibrary y Google Books, que son los que traen ediciones con ISBN y páginas.
private const val KITSU_MAX_NEW = 3

/**
 * P-029: parseo puro de la respuesta de Kitsu → resultados normalizados.
 * Separado de la red a propósito: los tests corren sin red (JUnitCore + android.jar,
 * ver run_tests.ps1), así que se prueba dándole un JSON de ejemplo.
 *
 * Filtra por parecido de títulos igual que MangaDex (matchScore >= 0.4). Es
 * imprescindible: la búsqueda de Kitsu NO devuelve vacío cuando no conoce el
 * título, devuelve basura con aplomo ("El ataque de los titanes" → "Mato Seihei
 * no Slave"). Sin este filtro, contaminaría la lista.
 *
 * `genreMapper` es un parámetro solo para poder testear: por defecto es el mapeo
 * normal del proyecto, pero ese vive en MainActivity.kt y arrastra Compose, que no
 * está en el classpath de los tests. Inyectándolo, el test prueba ESTE parseo sin
 * cargar media app. En producción nadie pasa este parámetro.
 */
internal fun parseKitsuMangaResults(
    json: String,
    query: String,
    preferredLang: String = "es",
    genreMapper: (List<String>) -> List<String> = ::bestGenreFromRawCandidates
): List<OpenLibraryResult> {
    val out = mutableListOf<OpenLibraryResult>()
    val root = try { JSONObject(json) } catch (_: Exception) { return emptyList() }
    val data = root.optJSONArray("data") ?: return emptyList()

    // JSON:API sirve las relaciones aparte, en `included`. Indexamos por id para
    // resolver categorías y autores sin más llamadas.
    val categoryById = HashMap<String, String>()
    val personById = HashMap<String, String>()
    val staffById = HashMap<String, Pair<String, String>>()   // id → (personId, rol)
    root.optJSONArray("included")?.let { inc ->
        for (i in 0 until inc.length()) {
            val obj = inc.optJSONObject(i) ?: continue
            val id = obj.optString("id", "")
            val attrs = obj.optJSONObject("attributes")
            when (obj.optString("type", "")) {
                "categories" -> attrs?.optString("title", "")?.takeIf { it.isNotBlank() }
                    ?.let { categoryById[id] = it }
                "people" -> attrs?.optString("name", "")?.takeIf { it.isNotBlank() }
                    ?.let { personById[id] = it }
                "mediaStaff" -> {
                    val personId = obj.optJSONObject("relationships")
                        ?.optJSONObject("person")?.optJSONObject("data")?.optString("id", "") ?: ""
                    if (personId.isNotBlank()) staffById[id] = personId to (attrs?.optString("role", "") ?: "")
                }
            }
        }
    }

    val qNorm = normalizedEditionText(query)
    val qTokens = qNorm.split(" ").filter { it.length >= 3 }.toSet()

    for (i in 0 until data.length()) {
        val item = data.optJSONObject(i) ?: continue
        val id = item.optString("id", "").takeIf { it.isNotBlank() } ?: continue
        val attrs = item.optJSONObject("attributes") ?: continue

        // Todos los títulos conocidos (canónico + localizados + abreviados) para el
        // matching. Kitsu SÍ indexa los localizados: "Ataque a los Titanes" encuentra
        // Attack on Titan (verificado), pero solo si la query calca el título que guarda.
        val titlesObj = attrs.optJSONObject("titles")
        val allTitles = mutableListOf<String>()
        val canonical = attrs.optString("canonicalTitle", "")
        if (canonical.isNotBlank()) allTitles.add(canonical)
        titlesObj?.keys()?.forEach { k -> titlesObj.optString(k, "").takeIf { it.isNotBlank() }?.let { allTitles.add(it) } }
        attrs.optJSONArray("abbreviatedTitles")?.let { arr ->
            for (j in 0 until arr.length()) arr.optString(j, "").takeIf { it.isNotBlank() }?.let { allTitles.add(it) }
        }
        if (allTitles.isEmpty()) continue

        fun scoreOf(t: String): Double {
            val tNorm = normalizedEditionText(t)
            return when {
                tNorm == qNorm -> 1.0
                qTokens.isEmpty() -> 0.0
                else -> qTokens.count { tNorm.contains(it) }.toDouble() / qTokens.size
            }
        }

        // Título mostrado: el español si Kitsu lo tiene y la app está en español
        // (mismo criterio que las ediciones de OpenLibrary). Cobertura ES real:
        // baja, solo 1 de cada 8 series la trae, pero cuando está es la buena.
        val localized = if (preferredLang == "es") titlesObj?.optString("es_es", "")?.ifBlank { null } else null
        val displayTitle = localized ?: canonical.ifBlank { allTitles.first() }

        // El match se mide contra el título que se va a MOSTRAR (y el canónico), no
        // contra la lista entera de alias. Motivo, verificado contra la API real:
        // buscar "Berserk" casa con el alias "Berserk Peerless Battle Spirit" de una
        // serie que se muestra como "Peerless Battle Spirit", y buscar "El Imperio
        // Final" (que no es manga) casa con el alias "El eunuco del Imperio" de
        // "Mekkoku no Kangan". Puntuar los alias y enseñar el canónico mete ruido que
        // el usuario no puede relacionar con lo que ha buscado.
        // Excepción: un alias que coincide EXACTO con la query sí vale — es el caso de
        // buscar por el título original ("Shingeki no Kyojin" → Attack on Titan).
        val exactAlias = allTitles.any { normalizedEditionText(it) == qNorm }
        val matchScore = maxOf(scoreOf(displayTitle), scoreOf(canonical))
        if (!exactAlias && matchScore < 0.4) continue

        // Autor vía staff → person. Kitsu solo lo tiene en ~2 de cada 3 series
        // (Chainsaw Man, por ejemplo, no lo trae), así que puede quedar vacío.
        // Se prefiere quien firma la historia sobre el resto del equipo.
        val staffIds = item.optJSONObject("relationships")?.optJSONObject("staff")?.optJSONArray("data")
        var author = ""
        if (staffIds != null) {
            val credits = mutableListOf<Pair<String, String>>()   // (nombre, rol)
            for (j in 0 until staffIds.length()) {
                val sid = staffIds.optJSONObject(j)?.optString("id", "") ?: continue
                val (personId, role) = staffById[sid] ?: continue
                personById[personId]?.let { credits.add(it to role) }
            }
            author = (credits.firstOrNull { it.second.contains("Story", ignoreCase = true) }
                ?: credits.firstOrNull())?.first ?: ""
        }

        // Portada: original > large > medium. Kitsu las sirve por HTTPS desde
        // media.kitsu.app, así que valen tal cual.
        val poster = attrs.optJSONObject("posterImage")
        val coverUrl = cleanCoverUrl(
            poster?.optString("original")?.ifBlank { null }
                ?: poster?.optString("large")?.ifBlank { null }
                ?: poster?.optString("medium")?.ifBlank { null }
        )

        // Géneros: "Manga" (salvo novela ligera) + lo que digan las categorías.
        // Máximo 2, como el resto de fuentes.
        val subtype = attrs.optString("subtype", "").lowercase(Locale.ROOT)
        val rawCats = mutableListOf<String>()
        item.optJSONObject("relationships")?.optJSONObject("categories")?.optJSONArray("data")?.let { arr ->
            for (j in 0 until arr.length()) {
                val cid = arr.optJSONObject(j)?.optString("id", "") ?: continue
                categoryById[cid]?.let { rawCats.add(it) }
            }
        }
        val mapped = genreMapper(rawCats)
        val genres = mutableListOf<String>()
        if (subtype !in KITSU_NOVEL_SUBTYPES) genres.add("Manga")
        for (g in mapped) if (g !in genres) genres.add(g)
        val genre = genres.take(2).joinToString("; ")

        val year = attrs.optString("startDate", "").take(4)

        // Páginas 0 (dato de serie, no de tomo) e ISBN null: Kitsu no los tiene.
        // language se deja en "" a propósito aunque el título mostrado sea el
        // español: es una ficha de SERIE sin ISBN ni páginas, y marcarla "es" la
        // colaría por encima de ediciones reales en español del comparator.
        out.add(OpenLibraryResult(
            title = displayTitle,
            author = author,
            pages = 0,
            coverUrl = coverUrl,
            isbn = null,
            genre = genre,
            publishYear = year,
            olKey = "kt_$id",
            language = "",
            // Blob de scoring: todos los títulos conocidos + autor, para que la
            // relevancia global no lo mate por buscarse en español.
            matchAuthors = (allTitles + author).joinToString(" ").trim()
        ))
    }
    return out
}

/** P-029: GET a Kitsu. Devuelve lista vacía ante cualquier fallo: la búsqueda nunca depende de esto. */
private suspend fun fetchKitsuMangaResults(query: String, preferredLang: String): List<OpenLibraryResult> {
    return try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$KITSU_API_BASE/manga?filter%5Btext%5D=$encoded&page%5Blimit%5D=$KITSU_PAGE_LIMIT" +
            "&include=categories,staff.person"
        ApiThrottle.gate("kitsu.app")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        // Accept propio de JSON:API. Hoy Kitsu responde igual sin él (comprobado),
        // pero es lo que pide el estándar y nos cubre si algún día lo exigen.
        conn.setRequestProperty("Accept", "application/vnd.api+json")
        conn.connectTimeout = 7000; conn.readTimeout = 7000
        val code = conn.responseCode
        if (code !in 200..299) {
            try { conn.errorStream?.close() } catch (_: Exception) {}
            return emptyList()
        }
        val body = conn.inputStream.use { it.bufferedReader().readText() }
        parseKitsuMangaResults(body, query, preferredLang)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
    catch (_: Exception) { emptyList() }
}

/**
 * Busca SOLO la portada de un libro que el catálogo local ya ha resuelto.
 *
 * No trae metadatos: el catálogo ya sabe título, autor, páginas y año, y son mejores que
 * los que devolvería una búsqueda genérica. Aquí lo único que falta es la imagen.
 *
 * Dos fuentes en cascada para que Google Books sea PRESCINDIBLE (por su licencia de zona
 * gris comercial y por el riesgo de que revoquen la key): primero GB por ISBN y por título
 * (mejor cobertura de ediciones españolas recientes y sin rate limit), y si GB no da nada
 * —o el día que Google corte la key— Open Library rescata lo que puede, gratis y sin key.
 * Antes esto era GB-only: era el único rescate de portada para las ~250.000 obras de la
 * Biblioteca Nacional (cover_id nulo), o sea que sin GB la fuente PRINCIPAL de la app se
 * quedaba con portada generada.
 *
 * Devuelve null si ninguna fuente tiene portada. Quien llama debe cachear también ese
 * null, o el mismo libro se preguntará a la red en cada búsqueda para siempre.
 */
private suspend fun fetchCoverUrlOnline(isbn13: String?, title: String, author: String): String? {
    suspend fun pedirGb(url: String): String? = try {
        ApiThrottle.gate(URL(url))
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        if (conn.responseCode !in 200..299) {
            try { conn.errorStream?.close() } catch (_: Exception) {}
            null
        } else {
            val root = JSONObject(conn.inputStream.use { it.bufferedReader().readText() })
            val item = root.optJSONArray("items")?.optJSONObject(0)
            val links = item?.optJSONObject("volumeInfo")?.optJSONObject("imageLinks")
            // Google Books sirve estas URLs por http; la app no permite tráfico en claro,
            // así que hay que forzar https o la imagen no carga nunca.
            (links?.optString("thumbnail", "")?.takeIf { it.isNotBlank() }
                ?: links?.optString("smallThumbnail", "")?.takeIf { it.isNotBlank() })
                ?.replace("http://", "https://")
        }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
    catch (_: Exception) { null }

    // 1. Google Books por ISBN
    if (!isbn13.isNullOrBlank()) {
        pedirGb(withGbKey(
            "https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn13&maxResults=1&printType=books"
        ))?.let { return it }
    }
    // 2. Google Books por título+autor
    if (title.isNotBlank()) {
        val q = URLEncoder.encode(if (author.isBlank()) title else "$title $author", "UTF-8")
        pedirGb(withGbKey(
            "https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=1&printType=books"
        ))?.let { return it }
    }

    // 3. Respaldo Open Library — hace a GB prescindible. default=false es clave: sin él,
    //    covers.openlibrary.org devuelve el GIF fantasma de 43 bytes con 200 cuando no tiene
    //    portada; con él responde 404 y se distingue limpio.
    if (!isbn13.isNullOrBlank()) {
        val candidate = "https://covers.openlibrary.org/b/isbn/$isbn13-L.jpg?default=false"
        try {
            ApiThrottle.gate(URL(candidate))
            val c = URL(candidate).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", APP_USER_AGENT)
            c.connectTimeout = 4_000; c.readTimeout = 4_000
            val ok = c.responseCode == 200
            c.disconnect()
            if (ok) return candidate.removeSuffix("?default=false")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
        catch (_: Exception) {}
    }
    // 4. Open Library por título+autor (reutiliza la búsqueda que ya valida la portada)
    if (title.isNotBlank()) return fetchSpanishCoverByTitle(title, author)
    return null
}

// chooseBetterGenre eliminado en v8.0; reemplazado por votación en fetchBookMetadata

suspend fun fetchBookMetadata(title: String, author: String, isbn: String?): BookMetadata = withContext(Dispatchers.IO) {
    coroutineScope {
        // Fuentes base — SIEMPRE se consultan, para cualquier libro
        val googleDeferred = async { fetchGoogleBooksMetadata(title, author, isbn) }
        val olDeferred     = async { fetchOpenLibraryMetadata(title, author, isbn) }
        val olBooksDeferred = async { fetchOpenLibraryBooksApi(isbn, title) }

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
        // Feedback 22-07 (comics P1): "Cómics y novela gráfica" ya no dispara solo las APIs de
        // manga. El manga espanol se etiqueta asi (sin "Manga") pero casi siempre trae tomo en
        // el titulo, asi que se sigue cubriendo por volumeNum; un comic occidental sin tomo deja
        // de llamar a AniList/MangaDex/MangaUpdates (y de arriesgar genero/portada japones).
        val isManga = baseGenres.any { it == "Manga" } || volumeNum != null

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
    val publishYear: String,
    // B-023: autor que declara la API para este ISBN. Se usa solo para detectar que
    // un ISBN escaneado pertenece a OTRA obra (caso real: escanear "Hábito y Mortaja"
    // dentro de "El dragón renacido" lo añadía como edición suya). Por defecto vacío:
    // las fuentes que no lo aportan no disparan el aviso.
    val author: String = ""
)

private val LANGUAGE_META = mapOf(
    "spa" to Triple("es",       "Español",    "🇪🇸"),
    "eng" to Triple("original", "English",    "🌐"),
    "fre" to Triple("fr",       "Français",   "🇫🇷"),
    "ger" to Triple("de",       "Deutsch",    "🇩🇪"),
    "ita" to Triple("it",       "Italiano",   "🇮🇹"),
    "por" to Triple("pt",       "Português",  "🇵🇹"),
    "cat" to Triple("ca",       "Català",     "🇪🇸 (CAT)"),
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

// Guardia anti-colisión de ISBN (auditoría APIs 20-07, caso 9788498720976): OL tiene
// registros que listan ISBNs de OTRO libro ("El Dragón Renacido" resolvía a "Medicina
// en la Cocina" de Txumari Alfaro). Antes de aceptar metadatos o portada resueltos POR
// ISBN teniendo un título de referencia, se comprueba un parecido básico: normalizados
// (minúsculas, sin tildes ni signos), vale un contains mutuo o compartir algún token
// significativo. Laxa a propósito: "El dragón renacido" vs "The Dragon Reborn" pasa
// (comparten "dragon"); "Medicina en la Cocina" no comparte nada y se descarta.
// Sin datos suficientes devuelve true: nunca bloquea por falta de información.
internal fun titlesLooselyMatch(candidate: String, reference: String): Boolean {
    if (candidate.isBlank() || reference.isBlank()) return true
    val c = normalizedEditionText(candidate)
    val r = normalizedEditionText(reference)
    if (c.isBlank() || r.isBlank()) return true
    if (c.contains(r) || r.contains(c)) return true
    val refTokens = r.split(" ").filter { it.length >= 4 && it !in SEARCH_STOPWORDS }.toSet()
    if (refTokens.isEmpty()) return true
    return refTokens.any { c.contains(it) }
}

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
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
    catch (_: Exception) { emptyList() }
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

// TAREA 3 (lanzamiento, P-028): indica si isbnToLanguageMeta pudo detectar el idioma
// a partir de un prefijo ISBN reconocido, o si cayó en el valor por defecto ("original")
// simplemente por no reconocer el prefijo. Se usa para avisar al usuario cuando el
// idioma asignado a una edición escaneada es solo una suposición, no una detección real.
internal fun isbnLanguageIsConfident(isbn: String): Boolean {
    val n = isbn.replace(Regex("[^\\dXx]"), "")
    val prefix = n.take(7)
    return when {
        prefix.startsWith("97884") || prefix.startsWith("97849") ||
        prefix.startsWith("97913") ||
        prefix.startsWith("97985") || prefix.startsWith("97995") -> true
        prefix.startsWith("9780") || prefix.startsWith("9781") -> true
        prefix.startsWith("9782") -> true
        prefix.startsWith("9783") -> true
        prefix.startsWith("97888") -> true
        prefix.startsWith("97885") || prefix.startsWith("97897") ||
        prefix.startsWith("97898") -> true
        prefix.startsWith("97890") || prefix.startsWith("97894") -> true
        prefix.startsWith("97886") -> true
        prefix.startsWith("97887") -> true
        prefix.startsWith("97891") -> true
        prefix.startsWith("97892") -> true
        n.length == 10 && n.startsWith("84") -> true
        n.length == 10 && (n.startsWith("0") || n.startsWith("1")) -> true
        else -> false
    }
}

// Busca metadatos de UN ISBN concreto en Google Books y devuelve un EditionResult.
// Usado por xISBN para hidratar cada ISBN alternativo encontrado.
internal suspend fun fetchEditionByIsbn(isbn: String): EditionResult? = withContext(Dispatchers.IO) {
    return@withContext try {
        val clean = cleanIsbn(isbn) ?: return@withContext null
        // RF-M18: helper con control de código HTTP (429 visible + cooldown GB de sesión)
        val body = httpGetTextWithRetry(
            withGbKey("https://www.googleapis.com/books/v1/volumes?q=isbn:$clean&maxResults=1&printType=books"),
            "GB_edicion_isbn", retries = 1) ?: return@withContext null
        val root = JSONObject(body)
        val items = root.optJSONArray("items")
        if (items == null || items.length() == 0) return@withContext null
        val info = items.getJSONObject(0).optJSONObject("volumeInfo") ?: return@withContext null

        val title = info.optString("title", "").takeIf { it.isNotBlank() } ?: return@withContext null
        // Feedback 2.7: pageCount implausible (<40) → 0 (desconocido); fichas rotas de GB
        // asignaban páginas absurdas a la edición
        val pages = info.optInt("pageCount", 0).takeIf { it >= MIN_PLAUSIBLE_PAGES } ?: 0
        val publishYear = yearOf(info.optString("publishedDate", ""))
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

        // B-023: autor declarado por GB, para detectar ISBNs de otra obra
        val author = info.optJSONArray("authors")?.optString(0, "").orEmpty()

        EditionResult(langId, langLabel, flag, title, pages, coverUrl, clean, publisher, publishYear, author)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
    catch (_: Exception) { null }
}

// ── B-023: ¿el ISBN escaneado es de otra obra? ────────────────────────────────
// Comparamos por AUTOR, no por título: las ediciones de un mismo libro cambian de
// título entre idiomas ("El dragón renacido" / "The Dragon Reborn"), pero el autor
// se mantiene. Si la API no da autor, no avisamos (mejor callar que dar un falso
// positivo). La comparación es por tokens del nombre para tolerar "J.R.R. Tolkien"
// vs "Tolkien, J. R. R.".
private fun authorTokens(name: String): Set<String> =
    java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length > 2 }
        .toSet()

/** true solo si hay autor en AMBOS lados y no comparten ningún token relevante. */
fun editionAuthorMismatch(bookAuthor: String, editionAuthor: String): Boolean {
    val a = authorTokens(bookAuthor)
    val b = authorTokens(editionAuthor)
    if (a.isEmpty() || b.isEmpty()) return false
    return a.intersect(b).isEmpty()
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
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
    catch (_: Exception) { "" }

    if (!workKey.startsWith("/works/")) {
        workKey = try {
            val conn = URL("https://openlibrary.org/isbn/$clean.json").openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", APP_USER_AGENT)
            conn.connectTimeout = 6000; conn.readTimeout = 6000
            JSONObject(conn.inputStream.bufferedReader().readText())
                .optJSONArray("works")
                ?.optJSONObject(0)
                ?.optString("key", "") ?: ""
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
        catch (_: Exception) { "" }
    }

    if (!workKey.startsWith("/works/")) return@withContext emptyList()

    val entries = try {
        val safeWorkKey = if (workKey.matches(Regex("/works/OL\\d+W"))) workKey else return@withContext emptyList()
        val conn = URL("https://openlibrary.org$safeWorkKey/editions.json?limit=500").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", APP_USER_AGENT)
        conn.connectTimeout = 7000; conn.readTimeout = 7000
        JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("entries")
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
    catch (_: Exception) { null } ?: return@withContext emptyList()

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
        val publishDate = yearOf(entry.optString("publish_date", ""))
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
    // RF-M18: helper con control de código HTTP (antes un 429 salía como
    // FileNotFoundException tragada) + cooldown GB de sesión.
    val body = httpGetTextWithRetry(
        withGbKey("https://www.googleapis.com/books/v1/volumes?q=isbn:$clean&maxResults=1&printType=books"),
        "GB_titulo_es", retries = 1) ?: return fallbackTitle
    return try {
        val info = JSONObject(body)
            .optJSONArray("items")?.getJSONObject(0)?.optJSONObject("volumeInfo")
            ?: return fallbackTitle
        val gbLang = info.optString("language", "")
        val gbTitle = info.optString("title", "").trim()
        // Solo usar el título de GB si el idioma es español y el título es distinto al fallback
        if (gbLang == "es" && gbTitle.isNotBlank() && gbTitle != fallbackTitle) gbTitle else fallbackTitle
    } catch (_: Exception) { fallbackTitle }   // parse puro, sin suspensión: no puede tragar cancelación
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
            val year = yearOf(book.optString("publish_date", ""))
            val coverObj = book.optJSONObject("cover")
            val coverUrl = coverObj?.let {
                it.optString("large").ifBlank { null }
                    ?: it.optString("medium").ifBlank { null }
            } ?: "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"
            EditionResult("es", "Español", "🇪🇸", title, pages, coverUrl, isbn, publisher, year)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
        catch (_: Exception) { null }
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
        val year = yearOf(j.optString("publish_date", ""))
        val coverId = j.optJSONArray("covers")?.optLong(0, -1L) ?: -1L
        val coverUrl = when {
            coverId > 0 -> "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
            isbn != null -> "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"
            else -> null
        }
        EditionResult("es", "Español", "🇪🇸", title, pages, coverUrl, isbn, publisher, year)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
    catch (_: Exception) { null }
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
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
        catch (_: Exception) {}
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
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
    catch (_: Exception) { null }
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
        val olEditions = try { fetchEditionsViaOpenLibraryByIsbn(isbn) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
            catch (_: Exception) { emptyList() }
        // Guardia anti-colisión de ISBN (auditoría 20-07, caso 9788498720976): OL puede
        // resolver el ISBN a un Work de OTRO libro (registros con ISBNs sucios). Si
        // NINGUNA edición devuelta se parece al título buscado (ni al alias), el Work
        // entero es ajeno: descartar la fase y dejar que el resto de fuentes resuelvan.
        if (olEditions.isNotEmpty() && olEditions.none {
                titlesLooselyMatch(it.title, originalTitle) || titlesLooselyMatch(it.title, searchTitle)
            }) {
            com.lecturameter.utils.AppLogger.log(
                "PhaseA_OL_ISBN: colisión de ISBN, Work de OL (\"${olEditions.first().title}\") sin parecido con \"$originalTitle\" → descartado", "EditionSearch")
            return@runPhase r
        }
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
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
                catch (_: Exception) { null }
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
                    val publishDate = yearOf(e.optString("publish_date", ""))
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
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
                        catch (_: Exception) {}
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
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
                catch (_: Exception) {}
            }
            // Búsqueda con título español primero si hay alias (p.ej. "El Hombre Iluminado" antes de "The Sunlit Man")
            if (hasAlias) olLangSearch("$originalTitle $author", "spa", "es", "Español", "🇪🇸")
            olLangSearch("$searchTitle $author", "spa", "es", "Español", "🇪🇸")
            // v2.6: ediciones catalanas (OL usa código MARC "cat")
            olLangSearch("$searchTitle $author", "cat", "ca", "Català", "🇪🇸 (CAT)")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
        catch (_: Exception) {}
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
            val publishYear = yearOf(info.optString("publishedDate", ""))
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
                "ca" -> Triple("ca", "Català", "🇪🇸 (CAT)")
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
                val langParam = if (langRestrict.isNotBlank()) "&langRestrict=$langRestrict" else ""
                // RF-M18: helper con control de código HTTP (429 visible + cooldown GB de
                // sesión; el throttle por host ya lo aplica el propio helper)
                val body = httpGetTextWithRetry(
                    withGbKey("https://www.googleapis.com/books/v1/volumes?q=${URLEncoder.encode(query, "UTF-8")}&maxResults=20&printType=books$langParam"),
                    "PhaseC_GB", retries = 1) ?: return
                val items = JSONObject(body).optJSONArray("items") ?: return
                for (i in 0 until items.length()) {
                    val info = items.getJSONObject(i).optJSONObject("volumeInfo") ?: continue
                    addVolume(info)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
            catch (_: Exception) {}
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
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
        catch (_: Exception) {}
        r
    } }

    // ── Phase E: Wikidata SPARQL ──────────────────────────────────────────────
    // Requiere ISBN. Corre en paralelo con A, B, C.
    val phaseE = async(Dispatchers.IO) { runPhase("PhaseE_Wikidata", 10_000L) {
        if (isbn.isNullOrBlank()) return@runPhase emptyList<EditionResult>()
        try { fetchSpanishEditionsFromWikidata(isbn) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
        catch (_: Exception) { emptyList() }
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
                try { onPartial!!.invoke(ordered) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
                catch (_: Exception) {}
            }
        }
    }

    // A, B, C y E en paralelo (Phase H — Hardcover — eliminada en v2.7 doc / código actual).
    for (phase in listOf(phaseA, phaseB, phaseC, phaseE)) {
        try { for (ed in phase.await()) merged.addEditionIfNew(ed, mergedSeen) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
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
        val known = try { fetchOlEditionById(olid) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
            catch (_: Exception) { null }
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
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
        catch (_: Exception) {}
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
    // Claves de las obras que ha aportado el catálogo local (fase 0). Se rellena abajo y
    // el comparator la lee, por eso se declara aquí arriba.
    val delCatalogo = HashSet<String>()

    // Relevancia en TRAMOS GRUESOS de 0,25 en vez del valor continuo. Es lo que permite
    // ponerla por encima del idioma sin cargarse la preferencia de idioma: dos resultados
    // igual de relevantes caen en el mismo tramo y entonces decide el idioma, como antes.
    fun relevanceTier(r: OpenLibraryResult): Int = (relevance(r) * 4).toInt()

    // Relevancia del título, cacheada aparte. Ver searchTitleRelevance: es la que impide
    // que el apellido de un traductor pese lo mismo que el título del libro.
    val titleRelOf = HashMap<String, Double>()
    fun titleTier(r: OpenLibraryResult): Int = titleRelOf.getOrPut(r.olKey.ifBlank { r.title }) {
        val directo = searchTitleRelevance(qTokens, r.title)
        val viaAlias = if (aliasTokens.isNotEmpty()) searchTitleRelevance(aliasTokens, r.title) else 0.0
        maxOf(directo, viaAlias)
    }.let { (it * 4).toInt() }

    val comparator =
        // La relevancia manda. Antes mandaba el idioma, y el resultado medido en el movil
        // el 21-07 fue que buscando "La torre" salia primero "The Giver of Stars" de Jojo
        // Moyes: un libro sin ninguna relacion con la consulta ganaba a una coincidencia
        // exacta de titulo solo por estar en el idioma de la app. Afecta a TODAS las
        // busquedas, con catalogo o sin el.
        // El TÍTULO manda. Sin este primer criterio, un libro cuyo traductor se apellida
        // "de la Torre" empataba a 1,00 con "La torre nera" y decidía el idioma.
        compareByDescending<OpenLibraryResult> { titleTier(it) }
            .thenByDescending { relevanceTier(it) }                               // luego título+autores
            .thenByDescending { it.language == preferredLang }                    // a igual relevancia, tu idioma
            .thenByDescending { relevance(it) }                                  // desempate fino dentro del tramo
            // El catálogo local manda: es la fuente principal, no un respaldo para cuando
            // no hay red. Va por ENCIMA de "tiene portada" a propósito, y ese orden importa:
            // las 250.000 obras que aporta la Biblioteca Nacional entran sin portada (no se
            // pueden empaquetar cubiertas, es copyright de las editoriales), así que con el
            // orden anterior CUALQUIER resultado de API con imagen las hundía. El catálogo
            // se consultaba primero y luego el orden lo tiraba abajo.
            .thenByDescending { it.olKey.isNotBlank() && it.olKey in delCatalogo }
            .thenByDescending { it.coverUrl != null }                            // con portada antes
            .thenByDescending { it.pages > 1 }                                   // páginas funcionales
            .thenByDescending { it.pages }

    // Fusión por ISBN (fleco Miles Morales, 22-07): el dedup del resto de la función es por
    // TÍTULO normalizado, así que dos fuentes que devuelven el MISMO libro con títulos distintos
    // ("Miles Morales: Origen" -> "Origen" en una, "Miles Morales" en otra) NO se unen y salen
    // duplicados, sobre todo al buscar por ISBN. Colapsa los que comparten ISBN-13 limpio:
    // conserva el primero (la lista viene ordenada, así que es el mejor rankeado) y le rellena los
    // huecos con los duplicados descartados. Los que no tienen ISBN se dejan intactos. Se aplica
    // tanto en los snapshots parciales como en el final, para que el duplicado NO llegue a verse.
    fun collapseByIsbn(list: List<OpenLibraryResult>): List<OpenLibraryResult> {
        val idxByIsbn = HashMap<String, Int>()
        val collapsed = ArrayList<OpenLibraryResult>(list.size)
        for (r in list) {
            // canonicalIsbn (no cleanIsbn): normaliza ISBN-10 -> ISBN-13, para que dos fuentes que
            // devuelven la MISMA edición con distinto formato (8427... vs 9788427...) se fusionen.
            val key = canonicalIsbn(r.isbn)
            val at = if (key != null) idxByIsbn[key] else null
            if (at == null) {
                if (key != null) idxByIsbn[key] = collapsed.size
                collapsed.add(r)
            } else {
                val prev = collapsed[at]
                collapsed[at] = prev.copy(
                    author      = prev.author.ifBlank { r.author },
                    pages       = if (prev.pages > 1) prev.pages else r.pages,
                    coverUrl    = prev.coverUrl ?: r.coverUrl,
                    genre       = prev.genre.ifBlank { r.genre },
                    publishYear = prev.publishYear.ifBlank { r.publishYear },
                    language    = prev.language.ifBlank { r.language }
                )
            }
        }
        return collapsed
    }

    // Feedback 2.6: snapshot filtrado y ordenado tras cada fase → la lista va creciendo
    // en pantalla en vez de aparecer entera al final. Se colapsa por ISBN para que los
    // duplicados de la misma edición no aparezcan ni siquiera de forma transitoria.
    fun emitPartial() {
        val cb = onPartial ?: return
        // Feedback 23-07: el autor de manga suele llegar en kanji (GB/OL con la edicion espanola).
        // Se romaniza en el ultimo paso (mapa en assets), sin afectar al dedup/orden anteriores.
        cb(collapseByIsbn(results.filter { r -> isMangaSourceKey(r.olKey) || relevance(r) >= 0.34 }
            .sortedWith(comparator))
            .map { it.copy(author = CatalogRepository.romanizeAuthor(it.author)) })
    }

    // ── Fase 0: catálogo local ────────────────────────────────────────────────
    // Va ANTES de todo lo demás y FUERA del timeout global a propósito: es una consulta
    // SQLite en disco, no consume presupuesto de red, y sus resultados aparecen al
    // instante. Las fases online siguen ejecutándose después: el catálogo adelanta lo
    // que ya sabemos, no sustituye a la búsqueda.
    //
    // Las claves se registran en `seen` con el mismo formato que usa OL ("/works/OL…W"),
    // así que si la API devuelve la misma obra no se duplica.
    if (CatalogRepository.isAvailable) {
        try {
            val locales = CatalogRepository.search(query, preferredLang, limit = 20)
            for (r in locales) {
                val k = if (r.olKey.isNotBlank()) "/works/${r.olKey}" else r.title
                if (seen.add(k)) {
                    results.add(r)
                    // Marcar el origen para que el comparator pueda priorizarlo. No vale
                    // mirar el olKey: una obra de Open Library traída por la API tiene una
                    // clave con la misma forma ("OL…W") que la del catálogo.
                    if (r.olKey.isNotBlank()) delCatalogo.add(r.olKey)
                }
            }
            if (locales.isNotEmpty()) {
                com.lecturameter.utils.AppLogger.log(
                    "searchOpenLibrary: catálogo local aporta ${locales.size} resultados", "Search")
                emitPartial()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e   // RF-M16: la cancelación no se traga
        } catch (e: Exception) {
            // El catálogo nunca debe impedir una búsqueda: si falla, se sigue online.
            com.lecturameter.utils.AppLogger.log("searchOpenLibrary: catálogo local falló: $e", "Search")
        }
    }

    // Timeout GLOBAL de la búsqueda (revisión final 20-07, ver RF-M19): antes solo había
    // timeouts por conexión, así que si OL respondía lento fase a fase el conjunto podía
    // alargarse sin cortar nunca. 30s: alineado con el tope de spinner (~30s) y muy por
    // encima de la latencia real medida por fase en la auditoría (<2s). Mismo patrón
    // withTimeoutOrNull que el presupuesto global de fetchEditionsForBook. Si vence, se
    // devuelven los parciales acumulados (el filtro y orden finales van tras el bloque).
    // El contenido conserva su sangría original a propósito (mismo criterio que el
    // bloque withTimeoutOrNull de fetchEditionsForBook).
    val completedInTime = kotlinx.coroutines.withTimeoutOrNull(30_000L) {

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
        // Feedback 23-07: para un ISBN escaneado OL suele venir pobre (manga con titulo en
        // japones, sin genero ni paginas). Si no hay resultado o el mejor esta flojo, recomponer
        // por la cadena completa por ISBN (GB + catalogo + enriquecimiento manga por serie: la
        // que trae titulo de la edicion espanola, paginas, genero y portada del tomo).
        val top = results.firstOrNull()
        val weak = top == null || top.genre.isBlank() || top.pages <= 1 ||
            top.coverUrl == null || hasCjk(top.title)
        if (weak) {
            val meta = fetchIsbnFullMetadata(isbnQuery)
            if (!meta.title.isNullOrBlank()) {
                val (lId, _, _) = isbnToLanguageMeta(isbnQuery)
                val lang = lId.takeIf { it.length == 2 } ?: top?.language ?: ""
                // Preferir el titulo de la cadena por ISBN si el de OL trae CJK; en el resto,
                // completar solo los huecos (paginas/genero/portada) sin pisar lo bueno de OL.
                val preferMetaTitle = top == null || hasCjk(top.title)
                val enriched = OpenLibraryResult(
                    if (preferMetaTitle) meta.title!! else top!!.title,
                    meta.author?.ifBlank { null } ?: top?.author ?: "",
                    meta.pages?.takeIf { it > 1 } ?: top?.pages ?: 0,
                    meta.coverUrl ?: top?.coverUrl,
                    canonicalIsbn(isbnQuery) ?: isbnQuery,
                    meta.genres.joinToString("; ").ifBlank { top?.genre ?: "" }, "",
                    "isbn_$isbnQuery", language = lang)
                if (top != null) results[0] = enriched else results.add(enriched)
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
        val known = try { fetchOlEditionById(olid) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
            catch (_: Exception) { null }
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
                // Feedback 23-07: el autor de un manga suele venir en kanji (龍幸伸) del catalogo/OL.
                // Si GB trae el mismo libro con autor en alfabeto latino (Yukinobu Tatsu), adoptarlo.
                val betterAuthor = hasCjk(existing.author) && gb.author.isNotBlank() && !hasCjk(gb.author)
                if (betterCover || betterGenre || betterLang || betterAuthor) {
                    results[dupIdx] = existing.copy(
                        coverUrl = if (betterCover) gb.coverUrl else existing.coverUrl,
                        genre    = if (betterGenre) gb.genre    else existing.genre,
                        pages    = if (betterPages) existing.pages else if (gb.pages > existing.pages) gb.pages else existing.pages,
                        language = if (betterLang)  gb.language else existing.language,
                        author   = if (betterAuthor) gb.author else existing.author
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
    // Auditoría 20-07: con MangaDex marcado como caído (bloqueo ISP) la fase se salta
    // entera al instante; Kitsu (3b) queda como fuente manga de la sesión.
    if (queryLooksManga && !mangaDexUnreachable) {
        try {
            // Buscar serie en MangaDex
            val seriesQuery = extractSeriesAndVolume(query).first
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
                    // D2 (22-07): MangaDex ya no aporta ficha nueva (solo enriquece portada), asi
                    // que no hace falta leer el autor de las relationships.
                    val normMain = mainTitle.lowercase().trim()
                    val dupIdx = results.indexOfFirst { it.title.lowercase().trim() == normMain }
                    if (dupIdx >= 0) {
                        // D2 (feedback 22-07): MangaDex solo ENRIQUECE la portada de resultados
                        // ya existentes; ya NO crea fichas nuevas. Kitsu es la fuente manga
                        // primaria (MangaDex esta bloqueado por ISP en Espana). Ver fase Kitsu.
                        val ex = results[dupIdx]
                        if (ex.coverUrl == null && coverUrl != null) results[dupIdx] = ex.copy(coverUrl = coverUrl)
                    }
                    // else (sin duplicado): D2 -> MangaDex no anade ficha nueva; ya lo hara Kitsu.
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
        catch (e: Exception) {
            // Auditoría 20-07: rechazo de conexión en frío (HTTP 000, bloqueo ISP) → igual
            // que un 404: marcar la sesión y saltar directo al fallback Kitsu (fase 3b).
            if (isColdConnectionFailure(e)) {
                mangaDexUnreachable = true
                com.lecturameter.utils.AppLogger.log(
                    "MangaDex inaccesible (${e.javaClass.simpleName}) → fallback Kitsu el resto de la sesión", "Search")
            }
        }
        emitPartial()
    }

    // Kitsu (P-029): FUENTE MANGA PRIMARIA (D2, feedback 22-07). Anade las fichas nuevas de
    // serie (hasta KITSU_MAX_NEW) y enriquece portada/genero de las existentes. MangaDex quedo
    // arriba como mero enriquecedor de portada (ya no anade fichas): esta bloqueado por ISP en
    // Espana, asi que en la practica Kitsu es la unica fuente manga que responde aqui.
    if (queryLooksManga) {
        val seriesQuery = extractSeriesAndVolume(query).first
        val kitsuResults = fetchKitsuMangaResults(seriesQuery.ifBlank { query }, preferredLang)
        var added = 0
        for (kt in kitsuResults) {
            val normKt = kt.title.lowercase().trim()
            val dupIdx = results.indexOfFirst { it.title.lowercase().trim() == normKt }
            if (dupIdx >= 0) {
                val ex = results[dupIdx]
                // Enriquecer sin pisar: portada solo si falta, autor solo si falta.
                // El género se mejora también cuando el existente es el "Manga" pelado
                // que pone MangaDex — Kitsu sabe si además es Terror, Romance…
                val betterGenre = ex.genre.isBlank() || ex.genre.equals("Manga", ignoreCase = true)
                results[dupIdx] = ex.copy(
                    coverUrl = ex.coverUrl ?: kt.coverUrl,
                    author   = ex.author.ifBlank { kt.author },
                    genre    = if (betterGenre && kt.genre.isNotBlank()) kt.genre else ex.genre
                )
            } else if (added < KITSU_MAX_NEW) {
                results.add(kt)
                added++
            }
        }
        emitPartial()
    }

    // Comic Vine RETIRADO (21-07-2026). Su licencia es de uso NO comercial y revoca la key
    // en uso comercial; Lecturameter tiene nivel Pro de pago. El valor medido era marginal
    // (búsqueda por serie, 0-1 resultado nuevo entre 20, sin páginas ni autor). No compensa
    // el riesgo. El cómic/manga español lo cubren el catálogo local (BNE), MangaDex y Kitsu.

    // ── v2.6 (búsqueda r1): relevancia + idioma del usuario + portadas válidas ──
    // Sustituye la heurística de tildes (fallaba con "El hombre Iluminado", "el nombre",
    // "El Imperio Final": ninguna lleva tilde → no se detectaban como español).
    // (Relevancia y comparator definidos arriba, antes de las fases — Feedback 2.6.)

    // Filtro: fuera resultados sin relación con la query ("A War to Be Won" para
    // "El Imperio Final", "Tiaztlán"…). MangaDex y Kitsu exentos: ya filtran con
    // matchScore propio y sus títulos principales pueden diferir del alias buscado.
    results.removeAll { r -> !isMangaSourceKey(r.olKey) && relevance(r) < 0.34 }

    results.sortWith(comparator)

    // Validación de portadas del top 8 (HEAD <8KB = placeholder tipo "cover to be
    // revealed"; sin Content-Length se miran los bytes). Solo top N: coste de red acotado.
    // Si alguna cae, reordenar.
    //
    // Son 8 y no 6 para que cuadre con el bloque de rescate de portadas de más abajo, que
    // trabaja sobre el top 8: con 6 los puestos 7 y 8 no se validaban aquí, así que
    // llegaban allí con su portada fantasma intacta y tampoco se rescataban. Caían entre
    // las dos sillas.
    coroutineScope {
        val checks = results.take(8).filter { it.coverUrl != null }.map { r ->
            async { r to isCoverUrlValid(r.coverUrl!!) }
        }
        var changed = false
        var descartadas = 0
        for (chk in checks) {
            val (r, valid) = try { chk.await() }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
                catch (_: Exception) { continue }
            if (!valid) {
                val i = results.indexOf(r)
                if (i >= 0) { results[i] = r.copy(coverUrl = null); changed = true; descartadas++ }
            }
        }
        // Se registra porque sin esto no hay forma de saber si el descarte funciona: una
        // portada fantasma y una portada que simplemente no existe se ven IGUAL en pantalla
        // (las dos acaban en portada generada). Anoche costó tres hipótesis fallidas razonar
        // sobre esto sin mirar el dato.
        if (checks.isNotEmpty()) com.lecturameter.utils.AppLogger.log(
            "portadas comprobadas: ${checks.size}, descartadas por no ser imagen: $descartadas", "Search")
        if (changed) results.sortWith(comparator)
    }

    // ── Portadas de los resultados del catálogo ──────────────────────────────
    // El catálogo decide QUÉ libro es; la red solo lo viste. Las obras de la Biblioteca
    // Nacional entran con `cover_id` nulo, así que `coverUrlFor()` cae en la vía por ISBN
    // de Open Library, limitada a 100 peticiones cada 5 minutos (luego 403) y sin imagen
    // para muchos ISBN españoles. Aquí se pregunta a Google Books SOLO por la imagen.
    //
    // Se cachea una semana, incluido el "no hay portada": sin cachear el negativo, un
    // libro sin cubierta se vuelve a preguntar a la red en cada búsqueda para siempre.
    // Solo el top 8: el coste de red queda acotado y nadie mira más abajo sin desplazarse.
    run {
        val sinPortada = results.take(8).filter {
            it.coverUrl == null && it.olKey.isNotBlank() && it.olKey in delCatalogo
        }
        if (sinPortada.isNotEmpty()) {
            var cambio = false
            coroutineScope {
                val trabajos = sinPortada.map { r ->
                    async {
                        val clave = com.lecturameter.utils.SearchCoverCache
                            .keyFor(r.isbn, r.title, r.author)
                        val cacheado = com.lecturameter.utils.SearchCoverCache.get(clave)
                        val url = when {
                            cacheado == null ->
                                fetchCoverUrlOnline(r.isbn, r.title, r.author)
                                    .also { com.lecturameter.utils.SearchCoverCache.put(clave, it) }
                            cacheado.isBlank() -> null    // comprobado: no tiene portada
                            else -> cacheado
                        }
                        r to url
                    }
                }
                for (t in trabajos) {
                    val (r, url) = try { t.await() }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
                        catch (_: Exception) { continue }
                    if (url != null) {
                        val i = results.indexOf(r)
                        if (i >= 0) { results[i] = r.copy(coverUrl = url); cambio = true }
                    }
                }
            }
            if (cambio) {
                results.sortWith(comparator)
                com.lecturameter.utils.AppLogger.log(
                    "portadas del catálogo completadas online (${sinPortada.size} pedidas)", "Search")
                emitPartial()
            }
        }
    }

    true
    }   // fin del withTimeoutOrNull global de búsqueda

    if (completedInTime == null) {
        com.lecturameter.utils.AppLogger.log(
            "searchOpenLibrary: TIMEOUT global (>30s), devolviendo ${results.size} parciales", "Search")
        // Garantizar filtro de relevancia y orden también en la salida por timeout
        results.removeAll { r -> !isMangaSourceKey(r.olKey) && relevance(r) < 0.34 }
        results.sortWith(comparator)
    }

    // Fusión por ISBN (fleco Miles Morales, 22-07): último paso, para que el resultado final
    // tampoco traiga duplicados de la misma edición. Ver collapseByIsbn más arriba.
    val mergedFinal = collapseByIsbn(results)
    results.clear(); results.addAll(mergedFinal)

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
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
    catch (_: Exception) {}

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
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }   // RF-M16
    catch (_: Exception) {}

    // 3. Fallback: Google Books (búsqueda + volume ID directo)
    fetchGoogleBooksMetadata(title, author, isbn).coverUrl
}

