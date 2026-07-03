package com.lecturameter
import com.lecturameter.bookquest.BookQuestScreen

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
// v21.42: Icons.Outlined.Star eliminado — estrellas usan ★/☆ Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
// v21.41: pointerInput + detectTapGestures removidos (simulate wrapped eliminado)

// ── Modelo ────────────────────────────────────────────────────────────────────

enum class BookStatus { PENDING, READING, FINISHED, DROPPED, REREADING }

// v19.8: eventos de fecha para soportar múltiples ciclos de abandono/retomado/relectura.
// type: "start" | "end" | "drop" | "resume" | "reread" | "reread_end"
// occurrence: 1 = primer evento de ese tipo, 2 = segundo, etc. (para drop/resume/reread/reread_end)
//             start/end siempre tienen occurrence = 1
data class DateEvent(
    val type: String,
    val date: String,           // yyyy-MM-dd
    val occurrence: Int = 1
)

// ── Edición de un libro ────────────────────────────────────────────────────────
// Representa una edición concreta (española, original, etc.) del mismo libro.
// Un Book puede tener 1 o más ediciones. Las sesiones siguen perteneciendo al
// Book (por bookId) y se distribuyen entre ediciones mediante editionId.
// Si editionId es null en una ReadingSession, pertenece a la edición activa.
data class BookEdition(
    val id: Long = System.currentTimeMillis(),
    val language: String = "unknown",          // "es" | "original" | código ISO
    val languageLabel: String = "Edición principal",// etiqueta visible: "Español", "English", etc.
    val flag: String = "🌐",             // emoji de bandera o 🌐
    val title: String = "",               // título en este idioma (puede diferir)
    val pages: Int = 0,
    val coverUrl: String? = null,
    val isbn: String? = null,
    val publisher: String = "",
    val publishYear: String = "",
    val noCoverFound: Boolean = false,
    val isActive: Boolean = true,          // edición cuya portada se muestra en la biblioteca
    val comment: String = ""              // comentario específico de esta edición/idioma
)

data class Book(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val author: String,
    val pages: Int,                       // páginas de la edición activa (cache para la UI de lista)
    val startDate: String?,
    val endDate: String?,
    val status: BookStatus,
    val rating: Int = 0,
    val coverUrl: String? = null,         // portada de la edición activa (cache para la UI de lista)
    val isbn: String? = null,             // ISBN de la edición activa (cache)
    val comment: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val genre: String = "",          // mantenido para migración JSON → leer via sanitizeBook
    val genres: List<String> = emptyList(), // campo activo; sustituye a genre
    val importedFromGoodreads: Boolean = false,
    val isRereading: Boolean = false,
    val noCoverFound: Boolean = false,
    val editions: List<BookEdition> = emptyList(), // vacío = edición única implícita (migración hacia atrás)
    val firstFunctionalPage: Int? = null,  // primera página real del contenido (v13)
    val lastFunctionalPage: Int? = null,   // última página real del contenido (v13)
    val dropDate: String? = null,          // fecha de abandono (DROPPED), automática al cambiar de estado (v17.10) — legacy, ahora cache del primer drop
    val resumedDate: String? = null,       // fecha de retomado (DROPPED→READING), automática (v17.10) — legacy, ahora cache del primer resume
    val dateEvents: List<DateEvent> = emptyList()  // v19.8: lista completa de eventos cronológicos (start/end/drop×N/resume×N/reread×N/reread_end×N)
)

data class ReadingSession(
    val id: Long = System.currentTimeMillis(),
    val bookId: Long,
    val date: String,          // yyyy-MM-dd
    val pages: Int,
    val minutes: Int? = null,
    val note: String = "",
    val editionId: Long? = null,  // null = edición activa en el momento de la sesión
    val startPage: Int? = null,   // página de inicio de la sesión (v13)
    val endPage: Int? = null,     // página final de la sesión (v13)
    val readingIndex: Int? = 0    // v19.8: 0 = lectura original, 1 = primera relectura, etc. Nullable para compatibilidad Gson con JSONs viejos
)

private var editionIdCounter = System.currentTimeMillis()
fun newEditionId(): Long {
    // Garantiza unicidad incluso si se crean múltiples ediciones en el mismo milisegundo
    val t = System.currentTimeMillis()
    editionIdCounter = if (t > editionIdCounter) t else editionIdCounter + 1
    return editionIdCounter
}

// ── DateEvent helpers (v19.8) ──────────────────────────────────────────────────

/** Devuelve la siguiente occurrence para un tipo de evento dado. */
fun nextOccurrence(events: List<DateEvent>, type: String): Int =
    (events.filter { it.type == type }.maxOfOrNull { it.occurrence } ?: 0) + 1

/**
 * v20.0 (G6 fix): renumera las `occurrence` de los tipos drop/resume/reread/reread_end
 * para garantizar que sean 1..N consecutivas según orden cronológico real.
 * Cualquier hueco o duplicado por ediciones manuales se corrige aquí.
 */
fun renumberOccurrences(events: List<DateEvent>): List<DateEvent> {
    val typesWithOccurrence = setOf("drop", "resume", "reread", "reread_end")
    val sorted = events.sortedWith(compareBy({ it.date }, { typeOrder(it.type) }, { it.occurrence }))
    val counters = mutableMapOf<String, Int>()
    return sorted.map { e ->
        if (e.type in typesWithOccurrence) {
            val n = (counters[e.type] ?: 0) + 1
            counters[e.type] = n
            e.copy(occurrence = n)
        } else e
    }
}

/** Cuenta cuántas relecturas completadas hay (reread_end). */
fun completedRereads(events: List<DateEvent>): Int =
    events.count { it.type == "reread_end" }

/** Cuenta cuántas relecturas iniciadas (incluyendo en curso). */
fun startedRereads(events: List<DateEvent>): Int =
    events.count { it.type == "reread" }

/** Genera dateEvents a partir de los campos legacy (startDate/endDate/dropDate/resumedDate).
 *  Solo se usa si el libro no tiene dateEvents (migración perezosa).
 *  v20.0 (G6): renumera occurrences para evitar huecos en libros guardados con bugs previos. */
fun migrateLegacyToEvents(book: Book): List<DateEvent> {
    if (book.dateEvents.isNotEmpty()) return renumberOccurrences(book.dateEvents)
    val out = mutableListOf<DateEvent>()
    if (book.startDate != null) out.add(DateEvent("start", book.startDate))
    if (book.dropDate != null) out.add(DateEvent("drop", book.dropDate, 1))
    if (book.resumedDate != null) out.add(DateEvent("resume", book.resumedDate, 1))
    if (book.endDate != null && (book.status == BookStatus.FINISHED || book.status == BookStatus.REREADING || book.status == BookStatus.DROPPED)) {
        out.add(DateEvent("end", book.endDate))
    }
    return renumberOccurrences(out)
}

/** Etiqueta localizada para mostrar un DateEvent. */
@Composable
fun dateEventLabel(e: DateEvent): String = when (e.type) {
    "start"      -> stringResource(R.string.date_event_start)
    "end"        -> stringResource(R.string.date_event_end)
    "drop"       -> if (e.occurrence == 1) stringResource(R.string.date_event_drop)
                    else stringResource(R.string.date_event_drop_n, e.occurrence)
    "resume"     -> if (e.occurrence == 1) stringResource(R.string.date_event_resume)
                    else stringResource(R.string.date_event_resume_n, e.occurrence)
    "reread"     -> if (e.occurrence == 1) stringResource(R.string.date_event_reread)
                    else stringResource(R.string.date_event_reread_n, e.occurrence)
    "reread_end" -> if (e.occurrence == 1) stringResource(R.string.date_event_reread_end)
                    else stringResource(R.string.date_event_reread_end_n, e.occurrence)
    else         -> e.type
}

/** Versión no-Composable de dateEventLabel para lambdas onClick. */
fun dateEventLabelCtx(e: DateEvent, ctx: android.content.Context): String = when (e.type) {
    "start"      -> ctx.getString(R.string.date_event_start)
    "end"        -> ctx.getString(R.string.date_event_end)
    "drop"       -> if (e.occurrence == 1) ctx.getString(R.string.date_event_drop)
                    else ctx.getString(R.string.date_event_drop_n, e.occurrence)
    "resume"     -> if (e.occurrence == 1) ctx.getString(R.string.date_event_resume)
                    else ctx.getString(R.string.date_event_resume_n, e.occurrence)
    "reread"     -> if (e.occurrence == 1) ctx.getString(R.string.date_event_reread)
                    else ctx.getString(R.string.date_event_reread_n, e.occurrence)
    "reread_end" -> if (e.occurrence == 1) ctx.getString(R.string.date_event_reread_end)
                    else ctx.getString(R.string.date_event_reread_end_n, e.occurrence)
    else         -> e.type
}

/** Orden lógico de tipos cuando coinciden fechas: start < drop < resume < end < reread < reread_end. */
private fun typeOrder(t: String): Int = when (t) {
    "start" -> 0; "drop" -> 1; "resume" -> 2; "end" -> 3; "reread" -> 4; "reread_end" -> 5; else -> 9
}

/** Ordena eventos por fecha, y dentro del mismo día por tipo + occurrence. */
fun sortedDateEvents(events: List<DateEvent>): List<DateEvent> =
    events.sortedWith(compareBy({ it.date }, { typeOrder(it.type) }, { it.occurrence }))

/** Calcula el readingIndex de una sesión a partir de su fecha y los dateEvents.
 *  0 = lectura original; 1 = primera relectura; etc.
 *  Una sesión pertenece a la relectura N si su fecha es >= reread_N. */
fun computeReadingIndex(sessionDate: String, events: List<DateEvent>): Int {
    val rereads = events.filter { it.type == "reread" }.sortedBy { it.occurrence }
    var idx = 0
    for (r in rereads) {
        if (sessionDate >= r.date) idx = r.occurrence else break
    }
    return idx
}

// ── Modelo Wrapped ─────────────────────────────────────────────────────────────

data class YearWrapped(
    val year: Int,
    val totalBooks: Int,
    val totalPages: Int,
    val avgPagesPerDay: Double,
    val avgDaysPerBook: Double,
    val favoriteAuthor: String,
    val favoriteAuthorBooks: Int,
    val favoriteGenre: String,
    val favoriteGenreBooks: Int,
    val fastestBookTitle: String,
    val fastestBookPpd: Double,
    val fastestBookPages: Int,
    val bestRatedTitle: String,
    val bestRatedScore: Int,
    val longestStreakDays: Int,
    val longestStreakStart: String,
    val longestStreakEnd: String,
    // ── v18.3 additions ────────────────────────────────────────────────────
    val totalSessions: Int = 0,            // nº de sesiones registradas en el año
    val totalMinutes: Int = 0,             // minutos totales de lectura (sesiones del año)
    val maxSessionPages: Int = 0,          // página récord de una sola sesión
    val maxSessionDate: String = "",       // fecha de esa sesión
    val mostReadDay: String = "",          // día con más páginas leídas (yyyy-MM-dd)
    val mostReadDayPages: Int = 0,         // páginas leídas ese día
    val droppedBooks: Int = 0,             // libros abandonados en el año
    val topAuthorsTop3: List<Pair<String, Int>> = emptyList(),  // top 3 autores con su nº libros únicos
    val topAuthorsTop3Editions: List<Int> = emptyList(),        // v18.6: nº de ediciones totales por autor, alineado con topAuthorsTop3
    val topGenresTop3: List<Pair<String, Int>> = emptyList(),   // top 3 géneros con su nº libros
    // ── v18.4: gráfico mensual + comparativa año anterior ──────────────────
    val pagesPerMonth: List<Int> = List(12) { 0 },              // páginas leídas por mes (índice 0=ene, 11=dic)
    val booksPerMonth: List<Int> = List(12) { 0 },              // libros terminados por mes
    val previousYearBooks: Int = 0,                             // libros del año anterior (para Δ)
    val previousYearPages: Int = 0,                             // páginas del año anterior
    // ── v18.5: donut género del año ────────────────────────────────────────
    val genreCountsTop6: List<Pair<String, Int>> = emptyList(), // top 6 géneros (género principal) para donut
    // ── v19.3: libro que más tiempo te robó ────────────────────────────────
    val longestBooksTop3: List<Pair<String, Int>> = emptyList(), // (título, minutos totales)
    val rereadBooks: Int = 0,       // v19.9: relecturas completadas en el año (reread_end events)
    // v21.41: top 3 libros por nota (desempate por endDate desc = más recientes primero)
    val bestRatedTop3: List<Triple<String, Int, String>> = emptyList(), // (título, rating, endDate)
    val savedAt: Long = System.currentTimeMillis()
)

// Extended sort: added RATING_DESC / RATING_ASC
enum class SortOrder {
    DATE_DESC, DATE_ASC,
    ALPHA_AZ, ALPHA_ZA,
    RATING_DESC, RATING_ASC,
    LENGTH_DESC, LENGTH_ASC
}

data class BookStats(val days: Int, val pagesPerDay: Double?)

data class OpenLibraryResult(
    val title: String,
    val author: String,
    val pages: Int,
    val coverUrl: String?,
    val isbn: String?,
    val genre: String,
    val publishYear: String,
    val olKey: String = ""
)

data class BookMetadata(
    val coverUrl: String? = null,
    val genres: List<String> = emptyList()
)

// ── Google Books search ───────────────────────────────────────────────────────

private fun fetchGoogleBooksResults(query: String, maxResults: Int = 15): List<OpenLibraryResult> {
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
            val pages = info.optInt("pageCount", 0)
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
            results.add(OpenLibraryResult(title, author, pages, coverUrl, isbn, genre, publishYear, "gb_${item.optString("id")}"))
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
        conn1.setRequestProperty("User-Agent", "Lecturameter/19.0 (Android)")
        conn1.connectTimeout = 8000; conn1.readTimeout = 8000
        parseGbItems(JSONObject(conn1.inputStream.bufferedReader().readText()).optJSONArray("items"))
    } catch (_: Exception) {}
    try {
        val url2 = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=$maxResults&printType=books&langRestrict=es"
        val conn2 = URL(url2).openConnection() as HttpURLConnection
        conn2.setRequestProperty("User-Agent", "Lecturameter/19.0 (Android)")
        conn2.connectTimeout = 8000; conn2.readTimeout = 8000
        parseGbItems(JSONObject(conn2.inputStream.bufferedReader().readText()).optJSONArray("items"))
    } catch (_: Exception) {}
    return results
}

private fun cleanCoverUrl(url: String?): String? =
    url?.takeIf { it.isNotBlank() }
        ?.replace("http://", "https://")
        ?.replace("&edge=curl", "")

/**
 * Valida que una URL de portada devuelva una imagen real (>8 KB).
 * Los placeholders tipo "COVER TO BE REVEALED" de TOR/Gollancz suelen
 * tener menos de 8 KB. Devuelve true si no se puede determinar el tamaño.
 */
private suspend fun isCoverUrlValid(url: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.setRequestProperty("User-Agent", "Lecturameter/19.3")
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
        conn.setRequestProperty("User-Agent", "Lecturameter/10.0")
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
        conn.setRequestProperty("User-Agent", "Lecturameter/19.3")
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

// v20.9: Búsqueda completa por ISBN — devuelve título, autor, páginas, géneros y portada.
// Se usa en AddBookScreen al escanear un ISBN para autorrellenar el formulario.
data class IsbnFullMetadata(
    val title: String? = null,
    val author: String? = null,
    val pages: Int? = null,
    val genres: List<String> = emptyList(),
    val coverUrl: String? = null
)

private fun fetchIsbnFullMetadata(isbn: String): IsbnFullMetadata {
    if (isbn.isBlank()) return IsbnFullMetadata()
    var title: String? = null
    var author: String? = null
    var pages: Int? = null
    val rawGenres = mutableListOf<String>()
    var coverUrl: String? = null

    // 1. Google Books por ISBN (más fiable para título/autor/páginas)
    try {
        val query = URLEncoder.encode("isbn:$isbn", "UTF-8")
        val url = "https://www.googleapis.com/books/v1/volumes?q=$query&maxResults=3&printType=books"
        val conn = java.net.URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Lecturameter/6.0")
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val items = conn.inputStream.use { JSONObject(it.bufferedReader().readText()) }.optJSONArray("items")
        val info = items?.optJSONObject(0)?.optJSONObject("volumeInfo")
        if (info != null) {
            title = info.optString("title").ifBlank { null }
            val authors = info.optJSONArray("authors")
            if (authors != null && authors.length() > 0) author = authors.optString(0).ifBlank { null }
            val pg = info.optInt("pageCount", 0)
            if (pg > 0) pages = pg
            val cats = info.optJSONArray("categories")
            if (cats != null) for (i in 0 until cats.length()) rawGenres.add(cats.optString(i, ""))
            val imageLinks = info.optJSONObject("imageLinks")
            if (imageLinks != null) {
                coverUrl = cleanCoverUrl(
                    imageLinks.optString("extraLarge").ifBlank { null }
                        ?: imageLinks.optString("large").ifBlank { null }
                        ?: imageLinks.optString("medium").ifBlank { null }
                        ?: imageLinks.optString("thumbnail").ifBlank { null }
                )
            }
        }
    } catch (_: Exception) {}

    // 2. OpenLibrary Books API — complementa lo que falte
    try {
        val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$isbn&jscmd=data&format=json"
        val conn = java.net.URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Lecturameter/6.0")
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val root = JSONObject(conn.inputStream.bufferedReader().readText())
        val book = root.optJSONObject("ISBN:$isbn")
        if (book != null) {
            if (title.isNullOrBlank()) title = book.optString("title").ifBlank { null }
            if (author.isNullOrBlank()) {
                val authors = book.optJSONArray("authors")
                if (authors != null && authors.length() > 0)
                    author = authors.optJSONObject(0)?.optString("name")?.ifBlank { null }
            }
            if (pages == null) {
                val pg = book.optInt("number_of_pages", 0)
                if (pg > 0) pages = pg
            }
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
    } catch (_: Exception) {}

    val genres = bestGenreFromRawCandidates(rawGenres)
    return IsbnFullMetadata(title = title, author = author, pages = pages, genres = genres, coverUrl = coverUrl)
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
            conn.setRequestProperty("User-Agent", "Lecturameter/6.0")
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
            conn.setRequestProperty("User-Agent", "Lecturameter/6.0")
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
                        workConn.setRequestProperty("User-Agent", "Lecturameter/6.0")
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
        conn.setRequestProperty("User-Agent", "Lecturameter/6.0")
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
        conn.setRequestProperty("User-Agent", "Lecturameter/19.0")
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
        conn.setRequestProperty("User-Agent", "Lecturameter/19.0")
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
                cConn.setRequestProperty("User-Agent", "Lecturameter/19.0")
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
        if (isManga) {
            val aniListDeferred = async { fetchAniListMetadata(title, author) }
            val mangaDexDeferred = async { fetchMangaDexMetadata(title, volumeNum) }
            aniList = aniListDeferred.await()
            mangaDex = mangaDexDeferred.await()
        } else {
            aniList = BookMetadata()
            mangaDex = BookMetadata()
        }

        // Portada: Google Books > MangaDex (portada del TOMO exacto si es manga) >
        // OL /api/books > OL search > AniList (solo serie completa, último recurso)
        val coverUrl = googleBooks.coverUrl
            ?: mangaDex.coverUrl
            ?: olBooks.coverUrl
            ?: openLibrary.coverUrl
            ?: aniList.coverUrl

        // Género: votar entre todas las fuentes y devolver top 2
        val allGenres = baseGenres + aniList.genres + mangaDex.genres
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
private fun inferAnglophoneFlag(isbn: String?, publisher: String?): String? {
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

internal fun cleanIsbn(value: String?): String? {
    val clean = value?.replace(Regex("[^\\dXx]"), "")?.uppercase().orEmpty()
    return clean.takeIf { it.length in 10..13 }
}

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
        conn.setRequestProperty("User-Agent", "Lecturameter/10.0 (Android; contact: lecturameter@example.com)")
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
private fun isbnToLanguageMeta(isbn: String): Triple<String, String, String> {
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
private suspend fun fetchEditionByIsbn(isbn: String): EditionResult? = withContext(Dispatchers.IO) {
    return@withContext try {
        val clean = cleanIsbn(isbn) ?: return@withContext null
        val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$clean&maxResults=1&printType=books"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Lecturameter/19.3")
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val root = JSONObject(conn.inputStream.bufferedReader().readText())
        val items = root.optJSONArray("items")
        if (items == null || items.length() == 0) return@withContext null
        val info = items.getJSONObject(0).optJSONObject("volumeInfo") ?: return@withContext null

        val title = info.optString("title", "").takeIf { it.isNotBlank() } ?: return@withContext null
        val pages = info.optInt("pageCount", 0)
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
        conn.setRequestProperty("User-Agent", "Lecturameter/8.0")
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
            conn.setRequestProperty("User-Agent", "Lecturameter/8.6")
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
        conn.setRequestProperty("User-Agent", "Lecturameter/8.0")
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
        val pages = entry.optInt("number_of_pages", 0)
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
        conn.setRequestProperty("User-Agent", "Lecturameter/10.0")
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
            conn.setRequestProperty("User-Agent", "Lecturameter/17.0")
            conn.connectTimeout = 6000; conn.readTimeout = 6000
            val root = JSONObject(conn.inputStream.bufferedReader().readText())
            // Si OL no tiene el libro aún, no inyectamos resultado vacío (title="", pages=0)
            val book = root.optJSONObject("ISBN:$isbn") ?: return@withContext null
            val title = cleanCompositeTitle(book.optString("title", "").ifBlank { "" })
            val pages = book.optInt("number_of_pages", 0)
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
        conn.setRequestProperty("User-Agent", "Lecturameter/17.0")
        conn.connectTimeout = 6000; conn.readTimeout = 6000
        val j = JSONObject(conn.inputStream.bufferedReader().readText())
        val title = cleanCompositeTitle(j.optString("title", "").ifBlank { return@withContext null })
        val pages = j.optInt("number_of_pages", 0)
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
            conn.setRequestProperty("User-Agent", "Lecturameter/19.0 (Android; contact: lecturameter@example.com)")
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
        sConn.setRequestProperty("User-Agent", "Lecturameter/19.0 (Android; contact: lecturameter@example.com)")
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
        eConn.setRequestProperty("User-Agent", "Lecturameter/19.0 (Android; contact: lecturameter@example.com)")
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
    originalPages: Int = 0
): List<EditionResult> = kotlinx.coroutines.withTimeoutOrNull(60_000) { supervisorScope {

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

    // ── Phase A: OL Work resolver por ISBN ────────────────────────────────────
    // Si el libro tiene ISBN, resuelve el Work en OL y descarga todas sus ediciones.
    // Es la fase más completa para libros bien catalogados.
    val phaseA = async(Dispatchers.IO) {
        val r = mutableListOf<EditionResult>()
        if (isbn.isNullOrBlank()) return@async r
        val seen = mutableSetOf<String>()
        cleanIsbn(isbn)?.let { seen.add(it) }

        // Intento principal: resolver Work por ISBN y listar sus ediciones
        val olEditions = try { fetchEditionsViaOpenLibraryByIsbn(isbn) } catch (_: Exception) { emptyList() }
        for (ed in olEditions) r.addEditionIfNew(ed, seen)
        r
    }

    // ── Phase B: OpenLibrary por título limpio ────────────────────────────────
    // Busca el Work en OL usando el título sin sufijos de serie, lista todas sus
    // ediciones, y también lanza una búsqueda explícita con filtro language=spa.
    val phaseB = async(Dispatchers.IO) {
        val r = mutableListOf<EditionResult>()
        val seen = mutableSetOf<String>()
        cleanIsbn(isbn)?.let { seen.add(it) }
        try {
            // 1. Encontrar la clave del Work en OL — con el título resuelto (inglés si hay alias)
            val q = URLEncoder.encode("$searchTitle $author", "UTF-8")
            val docsConn = URL("https://openlibrary.org/search.json?q=$q&limit=3&fields=key,title,author_name")
                .openConnection() as HttpURLConnection
            docsConn.setRequestProperty("User-Agent", "Lecturameter/10.0")
            docsConn.connectTimeout = 5000; docsConn.readTimeout = 5000
            val docs = JSONObject(docsConn.inputStream.bufferedReader().readText()).optJSONArray("docs")

            // Si hay alias, también buscar el Work con el título en español
            val docsSpanish: org.json.JSONArray? = if (hasAlias) {
                try {
                    val qEs = URLEncoder.encode("$originalTitle $author", "UTF-8")
                    val esConn = URL("https://openlibrary.org/search.json?q=$qEs&limit=3&fields=key,title,author_name")
                        .openConnection() as HttpURLConnection
                    esConn.setRequestProperty("User-Agent", "Lecturameter/10.0")
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
                val edConn = URL("https://openlibrary.org$workKey/editions.json?limit=300")
                    .openConnection() as HttpURLConnection
                edConn.setRequestProperty("User-Agent", "Lecturameter/10.0")
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
                    val pages = e.optInt("number_of_pages", 0)
                    val publishDate = e.optString("publish_date", "").take(4)
                    val coverId = e.optJSONArray("covers")?.optLong(0, -1L) ?: -1L
                    var coverUrl: String? = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else null
                    // Validar que el cover_i devuelve imagen real (>8 KB) — solo en español o con presupuesto
                    if (coverUrl != null && (langId == "es" || coverOps < 15)) {
                        if (langId != "es") coverOps++
                        if (!isCoverUrlValid(coverUrl)) coverUrl = null
                    }
                    if (coverUrl == null && !edIsbn.isNullOrBlank() && (langId == "es" || coverOps < 15)) {
                        if (langId != "es") coverOps++
                        val testUrl = "https://covers.openlibrary.org/b/isbn/$edIsbn-L.jpg?default=false"
                        try {
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

            // 3. Búsqueda OL con filtro language=spa.
            //    Si hay alias, usar primero el título español original (mayor precisión).
            suspend fun olSpanishSearch(query: String) {
                try {
                    val qEnc = URLEncoder.encode(query, "UTF-8")
                    val esConn = URL("https://openlibrary.org/search.json?q=$qEnc&language=spa&limit=5&fields=title,isbn,number_of_pages,cover_i,publisher")
                        .openConnection() as HttpURLConnection
                    esConn.setRequestProperty("User-Agent", "Lecturameter/10.0")
                    esConn.connectTimeout = 5000; esConn.readTimeout = 5000
                    val esDocs = JSONObject(esConn.inputStream.bufferedReader().readText()).optJSONArray("docs")
                    if (esDocs != null) for (i in 0 until esDocs.length()) {
                        val doc = esDocs.getJSONObject(i)
                        val esTitle = cleanCompositeTitle(doc.optString("title", ""))
                        if (esTitle.isBlank()) continue
                        val esIsbn = cleanIsbn(doc.optJSONArray("isbn")?.optString(0, null))
                        val pages = doc.optInt("number_of_pages", 0)
                        val coverId = doc.optLong("cover_i", -1L)
                        val coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else null
                        val publisher = doc.optJSONArray("publisher")?.optString(0, "") ?: ""
                        r.addEditionIfNew(EditionResult("es", "Español", "🇪🇸", esTitle, pages, coverUrl, esIsbn, publisher, ""), seen)
                    }
                } catch (_: Exception) {}
            }
            // Búsqueda con título español primero si hay alias (p.ej. "El Hombre Iluminado" antes de "The Sunlit Man")
            if (hasAlias) olSpanishSearch("$originalTitle $author")
            olSpanishSearch("$searchTitle $author")
        } catch (_: Exception) {}
        r
    }

    // ── Phase C: Google Books con título limpio ───────────────────────────────
    // Queries con el título limpio (sin sufijos de serie) y langRestrict=es primero.
    val phaseC = async(Dispatchers.IO) {
        val r = mutableListOf<EditionResult>()
        val seen = mutableSetOf<String>()
        cleanIsbn(isbn)?.let { seen.add(it) }

        suspend fun addVolume(info: JSONObject) {
            val gbTitle = info.optString("title", "").takeIf { it.isNotBlank() } ?: return
            val pages = info.optInt("pageCount", 0)
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
                val url = "https://www.googleapis.com/books/v1/volumes?q=${URLEncoder.encode(query, "UTF-8")}&maxResults=20&printType=books$langParam"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Lecturameter/10.0")
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
    }

    // ── Phase E: Wikidata SPARQL ──────────────────────────────────────────────
    // Requiere ISBN. Corre en paralelo con A, B, C.
    val phaseE = async(Dispatchers.IO) {
        if (isbn.isNullOrBlank()) return@async emptyList<EditionResult>()
        try { fetchSpanishEditionsFromWikidata(isbn) } catch (_: Exception) { emptyList() }
    }

    // Fase H: Hardcover — complemento a Google Books (cubre ediciones modernas y de nicho)
    val phaseH = async(Dispatchers.IO) {
        try {
            kotlinx.coroutines.withTimeout(10_000L) {
                HardcoverClient.searchEditions(searchTitle, isbn).map { ed ->
                    // Bug fix v21.15: HardcoverClient ya normaliza inglés a language="original",
                    // pero su flag 🌐 genérico no pasa por el refinamiento US/UK (inferAnglophoneFlag
                    // es private en este archivo). Lo aplicamos aquí igual que con Google Books.
                    if (ed.language == "original") {
                        val (_, _, refinedFlag) = editionLanguageMeta(null, ed.isbn, ed.publisher)
                        if (refinedFlag != "🌐") ed.copy(flag = refinedFlag) else ed
                    } else ed
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    // ── Merge ─────────────────────────────────────────────────────────────────
    val mergedSeen = mutableSetOf<String>()
    cleanIsbn(isbn)?.let { mergedSeen.add(it) }
    val merged = mutableListOf<EditionResult>()

    // A, B, C, E y H en paralelo.
    for (phase in listOf(phaseA, phaseB, phaseC, phaseE, phaseH)) {
        try { for (ed in phase.await()) merged.addEditionIfNew(ed, mergedSeen) }
        catch (_: Exception) {}
    }

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

suspend fun searchOpenLibrary(query: String): List<OpenLibraryResult> = withContext(Dispatchers.IO) {
    val results = mutableListOf<OpenLibraryResult>()
    val seen = mutableSetOf<String>()

    fun fetchAndParseOL(urlStr: String) {
        try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Lecturameter/6.0 (Android)")
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
                val author = doc.optJSONArray("author_name")?.optString(0, "") ?: ""
                val pages = doc.optInt("number_of_pages_median", 0)
                val coverId = doc.optLong("cover_i", -1L)
                val isbn = doc.optJSONArray("isbn")?.let { arr ->
                    var f13: String? = null; var f10: String? = null
                    for (j in 0 until arr.length()) {
                        val v = arr.optString(j, "").trim()
                        if (v.length == 13) { f13 = v; break }
                        if (v.length == 10 && f10 == null) f10 = v
                    }
                    f13 ?: f10
                }
                val coverUrl = when {
                    coverId > 0 -> "https://covers.openlibrary.org/b/id/$coverId-M.jpg"
                    isbn != null -> "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg"
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
                results.add(OpenLibraryResult(title, author, pages, coverUrl, isbn, genre, year, key))
            }
        } catch (_: Exception) {}
    }

    // 1. Buscar en Open Library
    val encoded = URLEncoder.encode(query, "UTF-8")
    fetchAndParseOL("https://openlibrary.org/search.json?q=$encoded&limit=20&fields=key,title,author_name,number_of_pages_median,cover_i,isbn,subject,first_publish_year")
    if (results.size < 5)
        fetchAndParseOL("https://openlibrary.org/search.json?title=$encoded&limit=10&fields=key,title,author_name,number_of_pages_median,cover_i,isbn,subject,first_publish_year")

    // 1b. Alias ES→EN: mismo diccionario que usa "Cambiar edición" (unifica ambos flujos).
    // "El hombre Iluminado" también busca "The Sunlit Man"; cubre typos conocidos (ascuaosura).
    val aliasTitle = resolveSearchTitle(query)
    if (!aliasTitle.equals(query.trim(), ignoreCase = true) && results.size < 8) {
        val encodedAlias = URLEncoder.encode(aliasTitle, "UTF-8")
        fetchAndParseOL("https://openlibrary.org/search.json?q=$encodedAlias&limit=10&fields=key,title,author_name,number_of_pages_median,cover_i,isbn,subject,first_publish_year")
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
            results.add(0, OpenLibraryResult(known.title, knownAuthor, known.pages, known.coverUrl, known.isbn, "", known.publishYear, olid))
        }
    }

    val withCover = results.count { it.coverUrl != null }
    val needsGoogleBooks = results.size < 5 || withCover < 3
    val needsGenreEnrich = results.any { it.genre.isBlank() }

    // 2. Completar/reemplazar con Google Books si hay pocos resultados, pocas portadas, o géneros vacíos
    if (needsGoogleBooks || needsGenreEnrich) {
        val gbResults = fetchGoogleBooksResults(query, maxResults = 15)
        val olTitlesNorm = results.map { it.title.lowercase().trim() }.toSet()
        for (gb in gbResults) {
            val norm = gb.title.lowercase().trim()
            val dupIdx = results.indexOfFirst { it.title.lowercase().trim() == norm }
            if (dupIdx >= 0) {
                val existing = results[dupIdx]
                val betterCover = existing.coverUrl == null && gb.coverUrl != null
                val betterGenre = existing.genre.isBlank() && gb.genre.isNotBlank()
                val betterPages = gb.pages <= 1 && existing.pages > 1
                if (betterCover || betterGenre) {
                    results[dupIdx] = existing.copy(
                        coverUrl = if (betterCover) gb.coverUrl else existing.coverUrl,
                        genre    = if (betterGenre) gb.genre    else existing.genre,
                        pages    = if (betterPages) existing.pages else if (gb.pages > existing.pages) gb.pages else existing.pages
                    )
                }
            } else if (!olTitlesNorm.contains(norm)) {
                results.add(gb)
            }
        }
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
            mdConn.setRequestProperty("User-Agent", "Lecturameter/19.0")
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
                            cConn.setRequestProperty("User-Agent", "Lecturameter/19.0")
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
    }

    val spanishQueryRegex = Regex("[áéíóúüñ¿¡]", RegexOption.IGNORE_CASE)
    val queryIsSpanish = spanishQueryRegex.containsMatchIn(query)
    results.sortWith(
        compareByDescending<OpenLibraryResult> { if (queryIsSpanish) spanishQueryRegex.containsMatchIn(it.title) else false }
            .thenByDescending { it.pages > 1 }        // primero los que tienen páginas reales
            .thenByDescending { it.coverUrl != null }
            .thenByDescending { it.pages }
    )
    results.take(20)
}

suspend fun fetchCoverForBook(title: String, author: String, isbn: String?): String? = withContext(Dispatchers.IO) {
    // 1. Intentar Open Library por ISBN
    try {
        if (!isbn.isNullOrBlank()) {
            val url = "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg?default=false"
            val c = URL(url).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "Lecturameter/6.0"); c.connectTimeout = 5000; c.readTimeout = 5000
            if (c.responseCode == 200) return@withContext url.removeSuffix("?default=false")
        }
    } catch (_: Exception) {}

    // 2. Buscar en Open Library por título+autor
    try {
        val q = URLEncoder.encode("$title $author", "UTF-8")
        val conn = URL("https://openlibrary.org/search.json?q=$q&limit=5&fields=cover_i,isbn").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Lecturameter/6.0"); conn.connectTimeout = 6000; conn.readTimeout = 6000
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
                        c2.setRequestProperty("User-Agent", "Lecturameter/6.0"); c2.connectTimeout = 3000; c2.readTimeout = 3000
                        if (c2.responseCode == 200) return@withContext u.removeSuffix("?default=false")
                    }
                }
            }
        }
    } catch (_: Exception) {}

    // 3. Fallback: Google Books (búsqueda + volume ID directo)
    fetchGoogleBooksMetadata(title, author, isbn).coverUrl
}

// ── Guardar portada local en almacenamiento interno ───────────────────────────
// Copia el contenido del URI elegido por el usuario a filesDir/covers/<bookId>[_<editionId>].jpg
// y devuelve la ruta absoluta del archivo copiado, o null si falla.
// editionId != null → fichero por edición (evita que ediciones distintas compartan portada).

fun copyUriToInternalStorage(context: Context, uri: Uri, bookId: Long, editionId: Long? = null): String? {
    return try {
        val coversDir = java.io.File(context.filesDir, "covers")
        if (!coversDir.exists()) coversDir.mkdirs()
        val fileName = if (editionId != null) "${bookId}_${editionId}.jpg" else "$bookId.jpg"
        val destFile = java.io.File(coversDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        destFile.absolutePath
    } catch (_: Exception) { null }
}

// ── EditionCache ──────────────────────────────────────────────────────────────

object EditionCache {
    private const val PREFS_NAME = "lecturameter_editions_cache"
    private val CACHE_DURATION_MS = java.util.concurrent.TimeUnit.HOURS.toMillis(24)

    private var prefs: android.content.SharedPreferences? = null
    private val gson = Gson()

    fun init(context: android.content.Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    suspend fun getEditions(
        bookId: Long,
        isbn: String?,
        title: String,
        forceRefresh: Boolean = false,
        fetchBlock: suspend () -> List<EditionResult>
    ): List<EditionResult> {
        val p = prefs ?: return fetchBlock()
        val cacheKey = buildCacheKey(bookId, isbn, title)
        if (!forceRefresh) {
            val cached = readCache(p, cacheKey)
            if (cached != null && !isCacheExpired(p, cacheKey)) return cached
        }
        val result = fetchBlock()
        if (result.isNotEmpty()) writeCache(p, cacheKey, result)
        return result
    }

    private fun buildCacheKey(bookId: Long, isbn: String?, title: String): String {
        val key = if (!isbn.isNullOrBlank()) "isbn_$isbn" else "title_${title.trim().take(50)}"
        return "editions_${bookId}_$key"
    }

    private fun readCache(p: android.content.SharedPreferences, key: String): List<EditionResult>? {
        val json = p.getString(key, null) ?: return null
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<EditionResult>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) { null }
    }

    private fun writeCache(p: android.content.SharedPreferences, key: String, editions: List<EditionResult>) {
        p.edit()
            .putString(key, gson.toJson(editions))
            .putLong("${key}_timestamp", System.currentTimeMillis())
            .apply()
    }

    private fun isCacheExpired(p: android.content.SharedPreferences, key: String): Boolean {
        val ts = p.getLong("${key}_timestamp", 0L)
        return ts == 0L || (System.currentTimeMillis() - ts) > CACHE_DURATION_MS
    }

    fun clearCache() { prefs?.edit()?.clear()?.apply() }

    fun clearCacheForBook(bookId: Long) {
        val p = prefs ?: return
        p.all.keys.filter { it.startsWith("editions_${bookId}_") }
            .forEach { p.edit().remove(it).apply() }
    }
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class BooksViewModel : ViewModel() {
    private val gson = Gson()
    var books by mutableStateOf<List<Book>>(emptyList())
    var sessions by mutableStateOf<List<ReadingSession>>(emptyList())
    var themeMode by mutableStateOf(ThemeMode.DARK)
        private set
    var tutorialCompleted by mutableStateOf(false)
        private set
    fun completeTutorial(prefs: android.content.SharedPreferences) {
        tutorialCompleted = true
        prefs.edit().putBoolean("tutorial_completed", true).apply()
    }
    fun resetTutorial(prefs: android.content.SharedPreferences) {
        tutorialCompleted = false
        prefs.edit().putBoolean("tutorial_completed", false).apply()
    }
    fun loadTutorialStatus(prefs: android.content.SharedPreferences) {
        tutorialCompleted = prefs.getBoolean("tutorial_completed", false)
    }
    var currentLanguage by mutableStateOf("es")
        private set
    var languageChosen by mutableStateOf(false)
        private set
    fun setLanguage(lang: String, prefs: android.content.SharedPreferences) {
        currentLanguage = lang
        prefs.edit().putString("app_language", lang).commit()
    }
    fun setLanguageChosen(prefs: android.content.SharedPreferences) {
        languageChosen = true
        prefs.edit().putBoolean("language_chosen", true).apply()
    }
    fun loadLanguageStatus(prefs: android.content.SharedPreferences) {
        currentLanguage = prefs.getString("app_language", "es") ?: "es"
        languageChosen  = prefs.getBoolean("language_chosen", false)
    }
    // Persisted list state
    var savedSortOrder by mutableStateOf(SortOrder.DATE_DESC)
    var savedSearchQuery by mutableStateOf("")
    var savedSessionNewestFirst by mutableStateOf(true)
    private var refreshingCoverIds by mutableStateOf<Set<Long>>(emptySet())
    // Job para cargar ediciones — cancelable para evitar cargas concurrentes y crashes
    private var loadEditionsJob: kotlinx.coroutines.Job? = null

    /** Se inicializa una sola vez desde MainActivity.onCreate para poder lanzar backups sin pasar Context a cada función. */
    private var appContext: android.content.Context? = null
    fun initContext(context: android.content.Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun load(prefs: android.content.SharedPreferences) {
        // v1.0: cargar idioma/tutorial SIEMPRE, antes del early return por libros vacíos
        loadLanguageStatus(prefs)
        loadTutorialStatus(prefs)
        val json = prefs.getString("books", null) ?: return
        val type = object : TypeToken<List<Book>>() {}.type
        // distinctBy { id }: si un proceso previo (restauración con IDs colisionantes) dejó
        // libros fantasma con el mismo ID, se conserva solo el primero. Evita duplicados ocultos.
        books = (gson.fromJson(json, type) ?: emptyList<Book>())
            .map { sanitizeBook(it) }
            .distinctBy { it.id }
        themeMode = when (prefs.getString("theme_mode", "dark")) {
            "light"  -> ThemeMode.LIGHT
            "aurora" -> ThemeMode.AURORA
            "amoled" -> ThemeMode.AMOLED
            else     -> ThemeMode.DARK
        }
        val sessJson = prefs.getString("sessions", null)
        if (sessJson != null) {
            val sessType = object : TypeToken<List<ReadingSession>>() {}.type
            sessions = (gson.fromJson(sessJson, sessType) ?: emptyList<ReadingSession>())
                .distinctBy { it.id }
        }
        loadWrapped(prefs)
        autoRepairFinishedBooks(prefs)
        repairLegacyFlags(prefs)
        repairLegacyEditionIconsV8900(prefs)
        genreLogicRepairV8601(prefs)
        repairSpanishFlagsV1470(prefs)
        migrateAnglophoneFlagsV1840(prefs)
        migrateStuckGlobalFlagsV1880(prefs)
        repairBlindAnglophoneDefaultV1900(prefs)
        savedSessionNewestFirst = prefs.getBoolean("session_newest_first", true)
        savedSortOrder = SortOrder.entries.firstOrNull { it.name == prefs.getString("sort_order", null) } ?: SortOrder.DATE_DESC
        // loadTutorialStatus y loadLanguageStatus ya llamados al inicio de load()
    }
    /**
     * Migración única v7.35: recarga portada y género de todos los libros LEÍDOS.
     * Corrige las portadas rotas por el bug zoom=0 y los géneros erróneos causados
     * por incluir la sinopsis en el pool de candidatos.
     * Se ejecuta una sola vez en background tras la primera carga post-actualización.
     * Velocidad: 1 libro cada 2 s para no saturar las APIs externas.
     */
    fun autoRepairFinishedBooks(prefs: android.content.SharedPreferences) {
        val flagKey = "cover_genre_repair_v800"
        if (prefs.getBoolean(flagKey, false)) return   // ya ejecutado
        // v8.0: repara portadas de todos los libros (no solo FINISHED) y género de cualquiera
        // que esté vacío, sea "Otro" o tenga sólo un género genérico
        val targets = books.filter { book ->
            book.noCoverFound || book.coverUrl.isNullOrBlank() ||
            book.genres.isEmpty() || book.genres == listOf("Otro") ||
            book.genres == listOf("Historia") || book.genres == listOf("Drama")
        }
        if (targets.isEmpty()) { prefs.edit().putBoolean(flagKey, true).apply(); return }
        viewModelScope.launch {
            targets.forEach { book ->
                // Evitar solapamiento con refreshes manuales en curso
                if (book.id in refreshingCoverIds) {
                    kotlinx.coroutines.delay(2_000)
                    return@forEach
                }
                refreshingCoverIds = refreshingCoverIds + book.id
                try {
                    val activeEdition = editionsForBook(book.id).firstOrNull { it.isActive }
                    val searchTitle = activeEdition?.title?.ifBlank { book.title } ?: book.title
                    val searchIsbn  = activeEdition?.isbn ?: book.isbn
                    val meta = try {
                        fetchBookMetadata(searchTitle, book.author, searchIsbn)
                    } catch (_: Exception) { BookMetadata() }

                    // Portada
                    if (meta.coverUrl != null) {
                        if (activeEdition != null) {
                            upsertEdition(book.id, activeEdition.copy(coverUrl = meta.coverUrl, noCoverFound = false), prefs)
                        }
                        updateCover(book.id, meta.coverUrl, prefs)
                    } else if (book.coverUrl?.contains("zoom=0") == true) {
                        // URL de zoom=0 rota y no encontramos sustituta → limpiar
                        markCoverBroken(book.id, prefs)
                    }

                    // Género (sólo actualizar si encontramos algo mejor o el actual está vacío)
                    if (meta.genres.isNotEmpty() && (book.genres.isEmpty() || book.genres == listOf("Otro"))) {
                        updateGenres(book.id, meta.genres, prefs)
                    }
                } finally {
                    refreshingCoverIds = refreshingCoverIds - book.id
                }
                kotlinx.coroutines.delay(2_000)
            }
            prefs.edit().putBoolean(flagKey, true).apply()
        }
    }

    /** Migración v8.6.1 — Bug 2: corrige ediciones legacy con 🇪🇸 implícito sintético. */
    fun repairLegacyFlags(prefs: android.content.SharedPreferences) {
        val flagKey = "flag_repair_v8601"
        if (prefs.getBoolean(flagKey, false)) return

        // Fecha de lanzamiento de la versión 6 (1 de enero de 2024 UTC)
        val V6_RELEASE_TIMESTAMP = 1704067200000L

        books = books.map { book ->
            val editions = book.editions
            if (editions.size == 1 && editions[0].flag == "🇪🇸" && editions[0].language == "es"
                && editions[0].id == book.id
                && book.addedAt < V6_RELEASE_TIMESTAMP) {
                book.copy(editions = listOf(
                    editions[0].copy(
                        language = "unknown",
                        languageLabel = "Edición principal",
                        flag = "🌐"
                    )
                ))
            } else book
        }
        save(prefs)
        prefs.edit().putBoolean(flagKey, true).apply()
    }

    /** Migración v8.9: corrige de nuevo iconos de ediciones sintéticas antiguas marcadas como español. */
    fun repairLegacyEditionIconsV8900(prefs: android.content.SharedPreferences) {
        val flagKey = "legacy_edition_icon_repair_v8900"
        if (prefs.getBoolean(flagKey, false)) return

        val v6ReleaseTimestamp = 1704067200000L
        var changed = false

        books = books.map { book ->
            val editions = book.editions
            if (editions.size != 1) return@map book

            val ed = editions[0]
            val sameCachedEdition = ed.id == book.id

            val oldBookSignal =
                book.addedAt <= 0L ||
                book.addedAt < v6ReleaseTimestamp ||
                book.importedFromGoodreads ||
                ed.publisher.isBlank() ||
                ed.publishYear.isBlank()

            val syntheticSpanishIcon =
                ed.language == "es" &&
                ed.flag == "🇪🇸" &&
                (ed.languageLabel.equals("Español", ignoreCase = true) ||
                 ed.languageLabel.equals("Spanish", ignoreCase = true))

            // No resetear libros que el usuario ha empezado a leer — su bandera fue corregida
            val userHasReadBook = !book.startDate.isNullOrBlank()
            if (sameCachedEdition && oldBookSignal && syntheticSpanishIcon && !userHasReadBook) {
                changed = true
                book.copy(editions = listOf(
                    ed.copy(
                        language = "unknown",
                        languageLabel = "Edición principal",
                        flag = "🌐"
                    )
                ))
            } else book
        }

        if (changed) save(prefs)
        prefs.edit().putBoolean(flagKey, true).apply()
    }

    /** Migración v8.6.1 — Bug 3: rehidrata géneros en libros que se saltaron la reparación v8.0. */
    fun genreLogicRepairV8601(prefs: android.content.SharedPreferences) {
        val flagKey = "genre_logic_repair_v8601"
        if (prefs.getBoolean(flagKey, false)) return

        val targets = books.filter { book ->
            book.genres.isEmpty() ||
            book.genres == listOf("Otro") ||
            book.genres == listOf("Historia") ||
            book.genres == listOf("Drama") ||
            book.genres.size == 1   // candidato a tener 2 géneros con la nueva lógica
        }

        if (targets.isEmpty()) {
            prefs.edit().putBoolean(flagKey, true).apply()
            return
        }

        viewModelScope.launch {
            targets.forEach { book ->
                if (book.id in refreshingCoverIds) {
                    kotlinx.coroutines.delay(1_500)
                    return@forEach
                }
                refreshingCoverIds = refreshingCoverIds + book.id
                try {
                    val activeEdition = editionsForBook(book.id).firstOrNull { it.isActive }
                    val searchTitle   = activeEdition?.title?.ifBlank { book.title } ?: book.title
                    val searchIsbn    = activeEdition?.isbn ?: book.isbn
                    val meta = try {
                        fetchBookMetadata(searchTitle, book.author, searchIsbn)
                    } catch (_: Exception) { BookMetadata() }

                    if (meta.genres.isNotEmpty()) {
                        updateGenres(book.id, meta.genres, prefs)
                    }
                } finally {
                    refreshingCoverIds = refreshingCoverIds - book.id
                }
                kotlinx.coroutines.delay(1_500)  // throttle: 1 libro cada 1.5s
            }
            prefs.edit().putBoolean(flagKey, true).apply()
        }
    }

    /**
     * Migración v14.7: asigna bandera 🇪🇸 a ediciones con language="unknown" y flag="🌐"
     * cuyo título contiene indicios claros de estar en español (artículos, tildes, ñ, signos).
     * Solo actualiza la edición activa. No toca ediciones ya con bandera explícita.
     */
    fun repairSpanishFlagsV1470(prefs: android.content.SharedPreferences) {
        val flagKey = "spanish_flag_repair_v1470"
        if (prefs.getBoolean(flagKey, false)) return

        val spanishStartWords = setOf(
            "el", "la", "los", "las", "un", "una", "unos", "unas",
            "en", "por", "al", "del", "de", "lo", "hay",
            "hábito", "habito", "crónica", "cronica", "historia",
            "mundo", "ciudad", "tierra", "sangre", "sombra", "fuego",
            "hijo", "hija", "rey", "reina", "señor", "señora"
        )
        val spanishCharsRegex = Regex("[áéíóúüñ¿¡]", RegexOption.IGNORE_CASE)

        var changed = false
        books = books.map { book ->
            val activeEd = book.editions.firstOrNull { it.isActive } ?: return@map book
            if (activeEd.language != "unknown" || activeEd.flag != "🌐") return@map book

            val titleLower = activeEd.title.ifBlank { book.title }.lowercase()
            val firstWord  = titleLower.trim().split(Regex("\\s+")).firstOrNull() ?: ""

            val isSpanish = firstWord in spanishStartWords ||
                            spanishCharsRegex.containsMatchIn(activeEd.title.ifBlank { book.title })

            if (!isSpanish) return@map book

            changed = true
            val updatedEditions = book.editions.map { ed ->
                if (ed.id == activeEd.id) ed.copy(language = "es", languageLabel = "Español", flag = "🇪🇸")
                else ed
            }
            book.copy(editions = updatedEditions)
        }

        if (changed) save(prefs)
        prefs.edit().putBoolean(flagKey, true).apply()
    }

    /**
     * Migración v18.4 — clasifica las ediciones inglesas existentes con 🌐 en 🇺🇸/🇬🇧
     * cuando publisher o ISBN permite inferirlo con certeza. Solo afecta a ediciones
     * con flag 🌐 y language en ("original", "en", "eng"). El resto se conservan.
     *
     * Corregido en v18.5: usa ed.publisher (BookEdition SÍ guarda publisher).
     */
    fun migrateAnglophoneFlagsV1840(prefs: android.content.SharedPreferences) {
        val flagKey = "anglophone_flags_migration_v1840_v2"
        if (prefs.getBoolean(flagKey, false)) return

        var changed = false
        books = books.map { book ->
            val updated = book.editions.map { ed ->
                val isEnglish = ed.language in setOf("original", "en", "eng")
                if (!isEnglish || ed.flag != "🌐") return@map ed
                val refined = inferAnglophoneFlag(ed.isbn, ed.publisher)
                if (refined != null) {
                    changed = true
                    ed.copy(flag = refined)
                } else ed
            }
            if (updated !== book.editions) book.copy(editions = updated) else book
        }

        if (changed) save(prefs)
        prefs.edit().putBoolean(flagKey, true).apply()
    }

    /**
     * Migración v18.9 (CORREGIDA en v19.0): refina banderas 🌐 a 🇺🇸/🇬🇧 SOLO cuando
     * inferAnglophoneFlag(isbn, publisher) da señal real. Si no hay señal, la edición
     * se queda en 🌐 — NO se defaultea a 🇺🇸. El default ciego original rompía libros
     * legítimamente internacionales/sin idioma claro (manga, cómics, ediciones sin
     * publisher/ISBN reconocible, ej. "Invencible Compendium 2").
     */
    fun migrateStuckGlobalFlagsV1880(prefs: android.content.SharedPreferences) {
        val flagKey = "stuck_global_flags_migration_v1881"
        if (prefs.getBoolean(flagKey, false)) return

        var changed = false
        books = books.map { book ->
            val updated = book.editions.map { ed ->
                if (ed.flag != "🌐") return@map ed
                val refined = inferAnglophoneFlag(ed.isbn, ed.publisher) ?: return@map ed
                val newLabel = when (refined) {
                    "🇺🇸" -> "Inglés (EE.UU.)"
                    "🇬🇧" -> "Inglés (Reino Unido)"
                    else   -> ed.languageLabel
                }
                changed = true
                ed.copy(flag = refined, language = "original", languageLabel = newLabel)
            }
            if (updated !== book.editions) book.copy(editions = updated) else book
        }

        if (changed) save(prefs)
        prefs.edit().putBoolean(flagKey, true).apply()
    }

    /**
     * Migración v19.0 — Bug: corrige el daño hecho por la migración v18.9 original, que
     * defaulteaba CUALQUIER bandera 🌐 sin señal a 🇺🇸 (sin filtrar idioma/tipo de libro).
     * Esto afectó a manga, cómics y libros internacionales legítimos (ej. "Invencible
     * Compendium 2"), que perdieron su 🌐 correcto.
     * Detecta el daño así: edición con language="original", flag en (🇺🇸,🇬🇧) Y
     * inferAnglophoneFlag(isbn, publisher) devuelve null HOY — es decir, nunca hubo
     * señal real, por lo que esa bandera solo puede venir del default ciego. Se revierte a 🌐.
     */
    fun repairBlindAnglophoneDefaultV1900(prefs: android.content.SharedPreferences) {
        val flagKey = "blind_anglophone_default_repair_v1900"
        if (prefs.getBoolean(flagKey, false)) return

        var changed = false
        books = books.map { book ->
            val updated = book.editions.map { ed ->
                if (ed.language != "original" || (ed.flag != "🇺🇸" && ed.flag != "🇬🇧")) return@map ed
                val realSignal = inferAnglophoneFlag(ed.isbn, ed.publisher)
                if (realSignal != null) return@map ed   // bandera con señal real, no toca
                changed = true
                ed.copy(flag = "🌐", languageLabel = "English")
            }
            if (updated !== book.editions) book.copy(editions = updated) else book
        }

        if (changed) save(prefs)
        prefs.edit().putBoolean(flagKey, true).apply()
    }

    fun save(prefs: android.content.SharedPreferences, triggerBackup: Boolean = true) {
        // v21.41: apply() en lugar de commit() — asíncrono, no bloquea hilo principal
        prefs.edit().putString("books", gson.toJson(books)).apply()
        if (triggerBackup) triggerDriveBackup(prefs)
    }
    fun saveSessions(prefs: android.content.SharedPreferences, triggerBackup: Boolean = true) {
        prefs.edit().putString("sessions", gson.toJson(sessions)).apply()
        if (triggerBackup) triggerDriveBackup(prefs)
    }

    /** Lanza un backup inmediato a Drive en background (no bloquea la UI). Solo actua si hay cuenta conectada. */
    fun triggerDriveBackup(_prefs: android.content.SharedPreferences) {
        val ctx = appContext ?: return
        if (DriveBackupManager.getSignedInAccount(ctx) == null) return
        // Usar OneTimeWorkRequest garantiza que el backup se ejecuta incluso si
        // la app pasa a segundo plano o el ViewModel es destruido antes de que
        // termine la coroutine. REPLACE descarta backups pendientes solapados.
        val request = androidx.work.OneTimeWorkRequestBuilder<DriveBackupWorker>().build()
        androidx.work.WorkManager.getInstance(ctx).enqueueUniqueWork(
            "lecturameter_drive_immediate_backup",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }
    /** Sobrecarga con context por compatibilidad con llamadas explicitas existentes. */
    fun triggerDriveBackup(context: android.content.Context, prefs: android.content.SharedPreferences) {
        if (appContext == null) appContext = context.applicationContext
        triggerDriveBackup(prefs)
    }

    fun setThemeMode(mode: ThemeMode, prefs: android.content.SharedPreferences, context: android.content.Context? = null) {
        themeMode = mode
        prefs.edit().putString("theme_mode", mode.value).apply()
        // Bug fix v21.14: el widget no seguía el tema de la app — refrescarlo aquí también
        if (context != null) {
            viewModelScope.launch { com.lecturameter.widget.updateBookWidgets(context) }
        }
    }
    // Compatibilidad: toggle rápido claro/oscuro desde la topbar
    fun toggleTheme(prefs: android.content.SharedPreferences, context: android.content.Context? = null) {
        val next = if (themeMode == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
        setThemeMode(next, prefs, context)
    }
    val isDarkMode get() = themeMode != ThemeMode.LIGHT
    fun addBook(book: Book, prefs: android.content.SharedPreferences) { books = listOf(book) + books; save(prefs) }
    fun deleteBook(id: Long, prefs: android.content.SharedPreferences) { books = books.filter { it.id != id }; sessions = sessions.filter { it.bookId != id }; save(prefs, triggerBackup = false); saveSessions(prefs, triggerBackup = false) }
    fun updateRating(id: Long, rating: Int, prefs: android.content.SharedPreferences) {
        books = books.map { if (it.id == id) it.copy(rating = rating) else it }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { save(prefs) }
    }
    fun updateGenres(id: Long, genres: List<String>, prefs: android.content.SharedPreferences) { books = books.map { if (it.id == id) it.copy(genres = genres) else it }; save(prefs) }
    @Deprecated("Use updateGenres") fun updateGenre(id: Long, genre: String, prefs: android.content.SharedPreferences) = updateGenres(id, if (genre.isBlank()) emptyList() else listOf(genre), prefs)
    fun updateCover(id: Long, coverUrl: String, prefs: android.content.SharedPreferences) {
        books = books.map { book ->
            if (book.id != id) return@map book
            val updatedEditions = if (book.editions.isNotEmpty()) {
                book.editions.map { ed -> if (ed.isActive) ed.copy(coverUrl = coverUrl, noCoverFound = false) else ed }
            } else listOf(
                BookEdition(
                    id = book.id,
                    language = "mul",
                    languageLabel = "Edición principal",
                    flag = "🌐",
                    title = book.title,
                    pages = book.pages,
                    coverUrl = coverUrl,
                    isbn = book.isbn,
                    noCoverFound = false,
                    isActive = true
                )
            )
            book.copy(coverUrl = coverUrl, noCoverFound = false, editions = updatedEditions)
        }
        save(prefs)
    }
    fun updateNoCoverFound(id: Long, noCoverFound: Boolean, prefs: android.content.SharedPreferences) {
        books = books.map { book ->
            if (book.id != id) return@map book
            // Actualizar también la edición activa para que upsertEdition no revierta el flag
            val updatedEditions = if (book.editions.isNotEmpty()) {
                book.editions.map { ed -> if (ed.isActive) ed.copy(noCoverFound = noCoverFound) else ed }
            } else {
                // Libro pre-v6 sin ediciones: materializar la edición sintética para persistir el flag
                listOf(BookEdition(
                    id = book.id, language = "mul", languageLabel = "Edición principal", flag = "🌐",
                    title = book.title, pages = book.pages,
                    coverUrl = book.coverUrl, isbn = book.isbn,
                    noCoverFound = noCoverFound, isActive = true
                ))
            }
            book.copy(noCoverFound = noCoverFound, editions = updatedEditions)
        }
        save(prefs)
    }
    /** Marca una portada como rota (fallo de carga en Coil): limpia la URL y activa el badge ⚠️.
     *  Afecta al libro y a la edición activa para que el siguiente refresh busque de cero. */
    fun markCoverBroken(id: Long, prefs: android.content.SharedPreferences) {
        books = books.map { book ->
            if (book.id != id) return@map book
            val editions = editionsForBook(id)
            val updatedEditions = editions.map { ed ->
                if (ed.isActive) ed.copy(coverUrl = null, noCoverFound = true) else ed
            }
            book.copy(coverUrl = null, noCoverFound = true,
                editions = if (book.editions.isNotEmpty()) updatedEditions else book.editions)
        }
        save(prefs)
    }
    fun isCoverRefreshing(id: Long): Boolean = id in refreshingCoverIds
    fun refreshCover(
        id: Long,
        prefs: android.content.SharedPreferences,
        onFinished: ((coverFound: Boolean, genreFound: Boolean) -> Unit)? = null
    ) {
        if (id in refreshingCoverIds) return

        val book = books.find { it.id == id } ?: run {
            onFinished?.invoke(false, false)
            return
        }

        refreshingCoverIds = refreshingCoverIds + id

        viewModelScope.launch {
            // Si hay edición activa, usar su título e ISBN (más específicos)
            // para encontrar la portada correcta (p.ej. edición española con título diferente)
            val activeEdition = editionsForBook(id).firstOrNull { it.isActive }
            val searchTitle = activeEdition?.title?.ifBlank { book.title } ?: book.title
            val searchIsbn  = activeEdition?.isbn ?: book.isbn

            val metadata = try {
                fetchBookMetadata(searchTitle, book.author, searchIsbn)
            } catch (_: Exception) {
                BookMetadata()
            }

            // ── PORTADA ─────────────────────────────────────────────
            val finalCoverFound = when {
                metadata.coverUrl != null -> {
                    // Actualizar también la coverUrl de la edición activa si existe
                    if (activeEdition != null) {
                        val updatedEd = activeEdition.copy(coverUrl = metadata.coverUrl, noCoverFound = false)
                        upsertEdition(id, updatedEd, prefs)
                    }
                    updateCover(id, metadata.coverUrl, prefs)
                    true
                }
                // La edición activa tiene portada válida (no marcada como rota) → mantenerla
                activeEdition != null && !activeEdition.coverUrl.isNullOrBlank() && !activeEdition.noCoverFound -> true
                // Sin edición explícita, el libro tiene portada y no está marcada como rota
                activeEdition == null && !book.coverUrl.isNullOrBlank() && !book.noCoverFound -> true
                else -> {
                    // No hay portada para esta edición: marcar en la edición Y en el libro
                    if (activeEdition != null) {
                        upsertEdition(id, activeEdition.copy(noCoverFound = true), prefs)
                    }
                    updateNoCoverFound(id, true, prefs)
                    false
                }
            }

            // ── GÉNERO ──────────────────────────────────────────────
            val finalGenreFound = when {
                metadata.genres.isNotEmpty() -> {
                    updateGenres(id, metadata.genres, prefs)
                    true
                }
                book.genres.isNotEmpty() && book.genres != listOf("Otro") -> true
                else -> false
            }

            refreshingCoverIds = refreshingCoverIds - id

            onFinished?.invoke(finalCoverFound, finalGenreFound)
        }
    }
    fun refreshGenre(id: Long, prefs: android.content.SharedPreferences, onFinished: ((Boolean) -> Unit)? = null) {
        val book = books.find { it.id == id } ?: run {
            onFinished?.invoke(false)
            return
        }
        viewModelScope.launch {
            val activeEdition = editionsForBook(id).firstOrNull { it.isActive }
            val searchTitle = activeEdition?.title?.ifBlank { book.title } ?: book.title
            val genres = try {
                fetchBookMetadata(searchTitle, book.author, activeEdition?.isbn ?: book.isbn).genres
            } catch (_: Exception) {
                emptyList()
            }
            if (genres.isNotEmpty()) updateGenres(id, genres, prefs)
            onFinished?.invoke(genres.isNotEmpty())
        }
    }

    fun bulkRefreshGenres(prefs: android.content.SharedPreferences, bookIds: List<Long>?, onProgress: (Int, Int) -> Unit = { _, _ -> }, onDone: (ok: Int, errors: Int) -> Unit = { _, _ -> }) {
        val targets = if (bookIds != null) books.filter { it.id in bookIds } else books.toList()
        if (targets.isEmpty()) { onDone(0, 0); return }
        viewModelScope.launch {
            var okCount = 0
            targets.forEachIndexed { idx, book ->
                if (book.id !in refreshingCoverIds) {
                    refreshingCoverIds = refreshingCoverIds + book.id
                    try {
                        val activeEdition = editionsForBook(book.id).firstOrNull { it.isActive }
                        val searchTitle = activeEdition?.title?.ifBlank { book.title } ?: book.title
                        val meta = try { fetchBookMetadata(searchTitle, book.author, activeEdition?.isbn ?: book.isbn) } catch (_: Exception) { BookMetadata() }
                        if (meta.genres.isNotEmpty()) { updateGenres(book.id, meta.genres, prefs); okCount++ }
                    } finally { refreshingCoverIds = refreshingCoverIds - book.id }
                }
                onProgress(idx + 1, targets.size)
                kotlinx.coroutines.delay(1_500)
            }
            onDone(okCount, targets.size - okCount)
        }
    }

    fun bulkRefreshCovers(prefs: android.content.SharedPreferences, bookIds: List<Long>?, onProgress: (Int, Int) -> Unit = { _, _ -> }, onDone: (ok: Int, errors: Int) -> Unit = { _, _ -> }) {
        val targets = if (bookIds != null) books.filter { it.id in bookIds } else books.toList()
        if (targets.isEmpty()) { onDone(0, 0); return }
        viewModelScope.launch {
            var okCount = 0
            targets.forEachIndexed { idx, book ->
                if (book.id !in refreshingCoverIds) {
                    refreshingCoverIds = refreshingCoverIds + book.id
                    try {
                        val activeEdition = editionsForBook(book.id).firstOrNull { it.isActive }
                        val searchTitle = activeEdition?.title?.ifBlank { book.title } ?: book.title
                        val meta = try { fetchBookMetadata(searchTitle, book.author, activeEdition?.isbn ?: book.isbn) } catch (_: Exception) { BookMetadata() }
                        if (meta.coverUrl != null) {
                            if (activeEdition != null) upsertEdition(book.id, activeEdition.copy(coverUrl = meta.coverUrl, noCoverFound = false), prefs)
                            updateCover(book.id, meta.coverUrl, prefs)
                            okCount++
                        }
                    } finally { refreshingCoverIds = refreshingCoverIds - book.id }
                }
                onProgress(idx + 1, targets.size)
                kotlinx.coroutines.delay(1_500)
            }
            onDone(okCount, targets.size - okCount)
        }
    }
    fun updateComment(id: Long, comment: String, prefs: android.content.SharedPreferences) { books = books.map { if (it.id == id) it.copy(comment = comment) else it }; save(prefs) }

    fun updateEditionComment(bookId: Long, editionId: Long, comment: String, prefs: android.content.SharedPreferences) {
        books = books.map { book ->
            if (book.id != bookId) return@map book
            book.copy(editions = book.editions.map { ed ->
                if (ed.id == editionId) ed.copy(comment = comment) else ed
            })
        }
        save(prefs)
    }

    fun activeLanguage(bookId: Long): String =
        editionsForBook(bookId).firstOrNull { it.isActive }?.language ?: "original"

    fun sessionsForBookAndLanguage(bookId: Long, language: String): List<ReadingSession> {
        val bookEditions = editionsForBook(bookId)
        val editionIdToLanguage = bookEditions.associate { it.id to it.language }
        val singleEdition = bookEditions.size <= 1
        return sessions.filter { s ->
            if (s.bookId != bookId) return@filter false
            if (singleEdition) return@filter true
            val sessionLang = s.editionId?.let { editionIdToLanguage[it] } ?: "original"
            sessionLang == language
        }.sortedByDescending { it.date }
    }
    fun updatePages(id: Long, pages: Int, prefs: android.content.SharedPreferences) {
        books = books.map { book ->
            if (book.id != id) return@map book
            // Actualizar tanto book.pages como la edición activa (si existe)
            val updatedEditions = book.editions.map { ed ->
                if (ed.isActive) ed.copy(pages = pages) else ed
            }
            book.copy(pages = pages, editions = updatedEditions)
        }
        save(prefs)
    }

    // ── Gestión de ediciones ──────────────────────────────────────────────────

    /** Devuelve las ediciones de un libro. Si no tiene ninguna definida, sintetiza
     *  una edición implícita a partir de los datos actuales del libro (migración). */
    fun editionsForBook(id: Long): List<BookEdition> {
        val book = books.find { it.id == id } ?: return emptyList()
        if (book.editions.isNotEmpty()) return book.editions
        // Migración: libro sin ediciones → crear edición implícita
        return listOf(BookEdition(
            id          = book.id,
            language    = "mul",
            languageLabel = "Edición principal",
            flag        = "🌐",
            title       = book.title,
            pages       = book.pages,
            coverUrl    = book.coverUrl,
            isbn        = book.isbn,
            isActive    = true
        ))
    }

    /** Añade o reemplaza una edición en un libro. Si la edición tiene isActive=true,
     *  desactiva el resto y sincroniza los campos cache del libro (coverUrl, pages, isbn). */
    fun upsertEdition(bookId: Long, edition: BookEdition, prefs: android.content.SharedPreferences) {
        books = books.map { book ->
            if (book.id != bookId) return@map book
            val currentEditions = editionsForBook(bookId).toMutableList()
            val existingIdx = currentEditions.indexOfFirst { it.id == edition.id }
            if (existingIdx >= 0) currentEditions[existingIdx] = edition
            else currentEditions.add(edition)
            // Si la nueva es activa, desactivar el resto
            val finalEditions = if (edition.isActive)
                currentEditions.map { it.copy(isActive = it.id == edition.id) }
            else currentEditions
            // Sincronizar cache del Book con la edición activa
            val active = finalEditions.firstOrNull { it.isActive } ?: finalEditions.first()
            book.copy(
                editions     = finalEditions,
                title        = active.title.ifBlank { book.title },
                coverUrl     = active.coverUrl,
                pages        = if (active.pages > 0) active.pages else book.pages,
                isbn         = active.isbn ?: book.isbn,
                noCoverFound = active.noCoverFound
            )
        }
        save(prefs)
    }

    /** Elimina una edición. No permite eliminar si es la única. */
    fun removeEdition(bookId: Long, editionId: Long, prefs: android.content.SharedPreferences): Boolean {
        val book = books.find { it.id == bookId } ?: return false
        val editions = editionsForBook(bookId)
        if (editions.size <= 1) return false
        val updated = editions.filter { it.id != editionId }
        // Si la eliminada era la activa, activar la primera restante
        val needsNewActive = editions.firstOrNull { it.id == editionId }?.isActive == true
        val finalEditions = if (needsNewActive)
            updated.mapIndexed { i, e -> if (i == 0) e.copy(isActive = true) else e }
        else updated
        val active = finalEditions.firstOrNull { it.isActive } ?: finalEditions.first()
        books = books.map { b ->
            if (b.id != bookId) b
            else b.copy(
                editions     = finalEditions,
                title        = active.title.ifBlank { b.title },
                coverUrl     = active.coverUrl,
                pages        = if (active.pages > 0) active.pages else b.pages,
                isbn         = active.isbn ?: b.isbn,
                noCoverFound = active.noCoverFound
            )
        }
        save(prefs, triggerBackup = false)
        return true
    }
    fun setActiveEdition(bookId: Long, editionId: Long, prefs: android.content.SharedPreferences) {
        books = books.map { book ->
            if (book.id != bookId) return@map book
            val editions = editionsForBook(bookId).map { it.copy(isActive = it.id == editionId) }
            val active = editions.firstOrNull { it.isActive } ?: return@map book
            book.copy(
                editions     = editions,
                title        = active.title.ifBlank { book.title },
                coverUrl     = active.coverUrl,
                pages        = if (active.pages > 0) active.pages else book.pages,
                isbn         = active.isbn ?: book.isbn,
                noCoverFound = active.noCoverFound
            )
        }
        save(prefs)
    }

    /** Recarga la portada para una edición concreta */
    fun refreshEditionCover(
        bookId: Long,
        editionId: Long,
        prefs: android.content.SharedPreferences,
        onFinished: ((Boolean) -> Unit)? = null
    ) {
        val book = books.find { it.id == bookId } ?: return
        val edition = editionsForBook(bookId).firstOrNull { it.id == editionId } ?: return
        viewModelScope.launch {
            // Para búsqueda en español, incluir el título en español si difiere
            val searchTitle = edition.title.ifBlank { book.title }
            val coverUrl = try {
                fetchCoverForBook(searchTitle, book.author, edition.isbn)
            } catch (_: Exception) { null }
            val updated = edition.copy(
                coverUrl     = coverUrl ?: edition.coverUrl,
                noCoverFound = coverUrl == null
            )
            upsertEdition(bookId, updated, prefs)
            // Si esta edición es la activa, también actualizar el noCoverFound del libro
            if (edition.isActive) {
                books = books.map { b ->
                    if (b.id == bookId) b.copy(noCoverFound = coverUrl == null) else b
                }
                save(prefs)
            }
            onFinished?.invoke(coverUrl != null)
        }
    }

    /** Busca ediciones disponibles en OpenLibrary + Google Books para el selector "Cambiar edición" */
    fun loadAvailableEditions(
        bookId: Long,
        forceRefresh: Boolean = false,
        onResult: (List<EditionResult>) -> Unit
    ) {
        val book = books.find { it.id == bookId } ?: return
        if (forceRefresh) EditionCache.clearCacheForBook(bookId)
        // Cancel any previous ongoing load to avoid concurrent network storms and crashes
        loadEditionsJob?.cancel()
        loadEditionsJob = viewModelScope.launch {
            val activeEdition = editionsForBook(bookId).firstOrNull { it.isActive }
            // Usar el ISBN del libro (original) preferentemente para que belongsToBook lo reconozca.
            // Si no hay, caemos al ISBN de la edición activa.
            val baseIsbn = book.isbn ?: activeEdition?.isbn
            val activeTitle = activeEdition?.title?.ifBlank { book.title } ?: book.title
            val pages = if (activeEdition != null && activeEdition.pages > 0) activeEdition.pages else book.pages
            val editions = try {
                EditionCache.getEditions(
                    bookId = bookId,
                    isbn = baseIsbn,
                    title = activeTitle,
                    forceRefresh = forceRefresh
                ) {
                kotlinx.coroutines.withTimeout(65_000L) {
                    kotlinx.coroutines.coroutineScope {
                        val primaryDeferred = async { fetchEditionsForBook(activeTitle, book.author, baseIsbn, pages) }
                        val secondaryDeferred = if (activeTitle.trim().equals(book.title.trim(), ignoreCase = true)) null
                            else async { fetchEditionsForBook(book.title, book.author, baseIsbn, pages) }
                        val primary = primaryDeferred.await()
                        val secondary = secondaryDeferred?.await() ?: emptyList()
                        val combined = (primary + secondary)
                            .distinctBy { it.isbn ?: "${it.language}|${it.title}|${it.publishYear}" }
                        val activeIsbnClean = activeEdition?.isbn?.let { cleanIsbn(it) }
                        val alreadyHasActive = activeIsbnClean != null && combined.any { cleanIsbn(it.isbn) == activeIsbnClean }
                        if (activeEdition != null && !alreadyHasActive) {
                            val syntheticEs = activeEdition.languageLabel.equals("Español", ignoreCase = true) ||
                                              activeEdition.languageLabel.equals("Spanish", ignoreCase = true)
                            if (syntheticEs) {
                                combined + EditionResult(
                                    language = "es", languageLabel = "Español", flag = "🇪🇸",
                                    title = activeEdition.title.ifBlank { book.title },
                                    pages = activeEdition.pages,
                                    coverUrl = activeEdition.coverUrl,
                                    isbn = activeEdition.isbn,
                                    publisher = "", publishYear = ""
                                )
                            } else combined
                        } else combined
                    }
                }
                } // cierre lambda EditionCache.getEditions
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // BUG v18.x: este timeout es NUESTRO (withTimeout de arriba), no una cancelación
                // externa del job. Antes se relanzaba como CancellationException y onResult()
                // nunca se llamaba → editionsLoading se quedaba en true para siempre (spinner
                // infinito en "Añadir edición"). Ahora se trata como fallo normal: lista vacía.
                emptyList()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelación externa real (ej. loadEditionsJob?.cancel() por una búsqueda nueva).
                // Aquí sí hay que relanzar para que la corrutina se cierre correctamente.
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            onResult(editions)
        }
    }
    fun updateDates(id: Long, startDate: String?, endDate: String?, prefs: android.content.SharedPreferences) {
        books = books.map {
            if (it.id == id) {
                // Los libros en estado READING nunca deben tener fecha de fin
                val cleanEnd = if (it.status == BookStatus.READING) null
                               else endDate?.takeIf { s -> s.isNotBlank() }
                it.copy(
                    startDate = startDate?.takeIf { s -> s.isNotBlank() },
                    endDate = cleanEnd
                )
            } else it
        }; save(prefs)
    }

    /** v19.8: actualiza la lista completa de dateEvents y sincroniza los campos legacy. */
    fun updateDateEvents(id: Long, newEvents: List<DateEvent>, prefs: android.content.SharedPreferences) {
        books = books.map { b ->
            if (b.id == id) {
                // v20.0 (G6): renumerar occurrences para evitar huecos/duplicados que rompen los labels
                val renumbered = renumberOccurrences(newEvents)
                val sorted = sortedDateEvents(renumbered)
                val firstStart = sorted.firstOrNull { it.type == "start" }?.date
                val firstEnd = sorted.firstOrNull { it.type == "end" }?.date
                val firstDrop = sorted.firstOrNull { it.type == "drop" }?.date
                val firstResume = sorted.firstOrNull { it.type == "resume" }?.date
                b.copy(
                    dateEvents = sorted,
                    startDate = firstStart ?: b.startDate,
                    endDate = firstEnd ?: b.endDate,
                    dropDate = firstDrop,
                    resumedDate = firstResume
                )
            } else b
        }
        // Recalcular readingIndex de todas las sesiones afectadas
        val book = books.firstOrNull { it.id == id }
        if (book != null) {
            val events = book.dateEvents
            sessions = sessions.map { s ->
                if (s.bookId == id) s.copy(readingIndex = computeReadingIndex(s.date, events)) else s
            }
            saveSessions(prefs, triggerBackup = false)
        }
        save(prefs)
    }
    fun updateStatus(id: Long, status: BookStatus, prefs: android.content.SharedPreferences) {
        books = books.map {
            if (it.id == id) {
                val sd = when {
                    status == BookStatus.PENDING -> null
                    it.startDate == null -> today()
                    else -> it.startDate
                }
                val ed = when {
                    status == BookStatus.FINISHED && it.endDate == null -> today()
                    status == BookStatus.REREADING -> it.endDate  // preserve original finish date
                    status == BookStatus.READING   -> null         // leyendo = sin fecha de fin
                    status == BookStatus.PENDING   -> null
                    else -> it.endDate
                }
                // Al cambiar a un estado distinto de FINISHED, limpiar el flag isRereading
                val newLastFunctional = if (status == BookStatus.FINISHED && it.lastFunctionalPage == null) {
                    sessions.filter { s -> s.bookId == id }
                        .maxByOrNull { s -> s.date }?.endPage
                } else null
                // Fecha de abandono: automática al pasar a DROPPED (no se sobreescribe si ya existe)
                val newDropDate = when {
                    status == BookStatus.DROPPED && it.dropDate == null -> today()
                    else -> it.dropDate
                }
                // Fecha de retomado: automática al pasar de DROPPED a READING/REREADING
                val newResumedDate = when {
                    (status == BookStatus.READING || status == BookStatus.REREADING) &&
                        it.status == BookStatus.DROPPED && it.resumedDate == null -> today()
                    else -> it.resumedDate
                }

                // v19.8: registrar eventos automáticos en dateEvents
                val baseEvents = renumberOccurrences(migrateLegacyToEvents(it)).toMutableList()
                val prevStatus = it.status
                val todayStr = today()
                // start inicial si no existe y se pasa a un estado activo
                if (status != BookStatus.PENDING && baseEvents.none { e -> e.type == "start" } && sd != null) {
                    baseEvents.add(DateEvent("start", sd))
                }
                // → DROPPED: añadir drop si el último evento no es drop sin resume posterior
                if (status == BookStatus.DROPPED && prevStatus != BookStatus.DROPPED) {
                    val occ = nextOccurrence(baseEvents, "drop")
                    baseEvents.add(DateEvent("drop", todayStr, occ))
                }
                // DROPPED → READING/REREADING: añadir resume
                if ((status == BookStatus.READING || status == BookStatus.REREADING) && prevStatus == BookStatus.DROPPED) {
                    val occ = nextOccurrence(baseEvents, "resume")
                    baseEvents.add(DateEvent("resume", todayStr, occ))
                }
                // → FINISHED por primera vez: añadir "end" si no hay aún
                if (status == BookStatus.FINISHED && prevStatus != BookStatus.FINISHED && prevStatus != BookStatus.REREADING) {
                    if (baseEvents.none { e -> e.type == "end" }) {
                        baseEvents.add(DateEvent("end", ed ?: todayStr))
                    }
                }
                // → REREADING desde cualquier otro estado: añadir "reread"
                // Bug fix v21.19: antes solo se registraba si prevStatus == FINISHED, que es
                // el único camino que usa el botón dedicado "Releer" (toggleRereading, siempre
                // gateado a FINISHED). Pero el desplegable genérico de estado permite pasar a
                // REREADING desde cualquier estado (Leyendo, Pendiente, Abandonado...) sin pasar
                // por ese botón — en esos casos nunca se registraba el evento y la relectura no
                // aparecía como iniciada en el historial de fechas.
                if (status == BookStatus.REREADING && prevStatus != BookStatus.REREADING) {
                    val occ = nextOccurrence(baseEvents, "reread")
                    baseEvents.add(DateEvent("reread", todayStr, occ))
                }
                // REREADING → FINISHED: añadir "reread_end"
                if (status == BookStatus.FINISHED && prevStatus == BookStatus.REREADING) {
                    val occ = nextOccurrence(baseEvents, "reread_end")
                    baseEvents.add(DateEvent("reread_end", todayStr, occ))
                }

                it.copy(status = status, startDate = sd, endDate = ed,
                    isRereading = if (status != BookStatus.FINISHED) false else it.isRereading,
                    lastFunctionalPage = newLastFunctional ?: it.lastFunctionalPage,
                    dropDate = newDropDate,
                    resumedDate = newResumedDate,
                    // v20.0 (G6): renumerar occurrences para garantizar 1..N consecutivas
                    dateEvents = renumberOccurrences(baseEvents.toList()))
            } else it
        }
        // Recalcular readingIndex de sesiones del libro tras cambio de status
        val updatedBook = books.firstOrNull { it.id == id }
        if (updatedBook != null) {
            val events = updatedBook.dateEvents
            // v20.4 (B5): al pasar a REREADING, NO mover sesiones de Lectura a Relectura.
            // Solo recalcular sesiones que tengan readingIndex == null (nunca asignado).
            // Las sesiones con readingIndex ya asignado (0 = Lectura, N = RelecturaN) se preservan.
            sessions = sessions.map { s ->
                if (s.bookId == id) {
                    if (s.readingIndex == null) {
                        s.copy(readingIndex = computeReadingIndex(s.date, events))
                    } else s
                } else s
            }
            saveSessions(prefs, triggerBackup = false)
        }
        // Si el libro ya no está en lectura activa, quitarlo del widget
        if (status != BookStatus.READING && status != BookStatus.REREADING) {
            val ctx = appContext
            if (ctx != null && com.lecturameter.widget.loadWidgetBook(ctx) == id) {
                com.lecturameter.widget.saveWidgetBook(ctx, -1L)
                com.lecturameter.widget.requestBookWidgetUpdate(ctx)
            }
        }
        save(prefs)
    }

    fun toggleRereading(id: Long, prefs: android.content.SharedPreferences) {
        val book = books.firstOrNull { it.id == id } ?: return
        // v19.8: si está releyendo (REREADING o flag isRereading) → terminar relectura
        // si no, empezar relectura (debe estar FINISHED)
        val isCurrentlyRereading = book.status == BookStatus.REREADING || book.isRereading
        if (isCurrentlyRereading) {
            // Terminar relectura: REREADING → FINISHED registra reread_end
            updateStatus(id, BookStatus.FINISHED, prefs)
        } else if (book.status == BookStatus.FINISHED) {
            // Empezar relectura: FINISHED → REREADING registra reread
            updateStatus(id, BookStatus.REREADING, prefs)
        }
    }

    // ── Sessions ───────────────────────────────────────────────────────────────
    fun addSession(session: ReadingSession, prefs: android.content.SharedPreferences) {
        // v19.8: calcular readingIndex automáticamente según fecha y eventos del libro
        val book = books.firstOrNull { it.id == session.bookId }
        val finalSession = if (book != null) {
            val events = migrateLegacyToEvents(book)
            session.copy(readingIndex = computeReadingIndex(session.date, events))
        } else session
        sessions = listOf(finalSession) + sessions
        saveSessions(prefs)
    }
    fun deleteSession(sessionId: Long, prefs: android.content.SharedPreferences) {
        sessions = sessions.filter { it.id != sessionId }
        saveSessions(prefs, triggerBackup = false)
    }
    /** v20.0: borra varias sesiones a la vez (botón "Eliminar todas" del historial). */
    fun deleteSessions(sessionIds: Collection<Long>, prefs: android.content.SharedPreferences) {
        if (sessionIds.isEmpty()) return
        val toRemove = sessionIds.toHashSet()
        sessions = sessions.filter { it.id !in toRemove }
        saveSessions(prefs, triggerBackup = false)
    }
    fun updateSession(updated: ReadingSession, prefs: android.content.SharedPreferences) {
        sessions = sessions.map { if (it.id == updated.id) updated else it }
        saveSessions(prefs)
    }
    fun updateFunctionalPages(bookId: Long, firstPage: Int?, lastPage: Int?, prefs: android.content.SharedPreferences) {
        books = books.map {
            if (it.id == bookId) it.copy(firstFunctionalPage = firstPage, lastFunctionalPage = lastPage) else it
        }; save(prefs)
    }

    fun sessionsForBook(bookId: Long): List<ReadingSession> =
        sessions.filter { it.bookId == bookId }.sortedByDescending { it.date }

    /**
     * Aplana la biblioteca a un libro virtual por edición (v18.3).
     *
     * Para libros con 2+ ediciones, genera una copia de Book por cada edición con sus
     * datos específicos (título, páginas, portada, ISBN, lengua). El `book.id` se
     * conserva para que las sesiones se sigan asociando al libro padre. Cada entrada
     * mantiene su única edición marcada como `isActive=true` para que los donuts de
     * idioma y similares lean el idioma correcto.
     *
     * Libros con 0 o 1 edición devuelven la lista normal (sin duplicar).
     */
    fun booksByEdition(): List<Book> = books.flatMap { book ->
        val eds = book.editions
        if (eds.size <= 1) listOf(book)
        else eds.map { ed ->
            book.copy(
                title    = ed.title.ifBlank { book.title },
                pages    = ed.pages.takeIf { it > 0 } ?: book.pages,
                coverUrl = ed.coverUrl,
                isbn     = ed.isbn,
                noCoverFound = ed.noCoverFound,
                editions = listOf(ed.copy(isActive = true))
            )
        }
    }

    // ── Year Wrapped ───────────────────────────────────────────────────────────
    var wrappedHistory by mutableStateOf<List<YearWrapped>>(emptyList())

    private fun loadWrapped(prefs: android.content.SharedPreferences) {
        val json = prefs.getString("wrapped_history", null) ?: return
        val type = object : TypeToken<List<YearWrapped>>() {}.type
        wrappedHistory = try { Gson().fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }
    private fun saveWrapped(prefs: android.content.SharedPreferences) {
        prefs.edit().putString("wrapped_history", Gson().toJson(wrappedHistory)).apply()
        triggerDriveBackup(prefs)
    }
    fun saveWrappedForYear(wrapped: YearWrapped, prefs: android.content.SharedPreferences) {
        // Reemplaza si ya existe ese año, si no añade
        wrappedHistory = listOf(wrapped) + wrappedHistory.filter { it.year != wrapped.year }
        saveWrapped(prefs)
    }
    fun wrappedForYear(year: Int): YearWrapped? = wrappedHistory.find { it.year == year }

    fun computeWrapped(year: Int): YearWrapped? {
        val finished = books.filter {
            it.status == BookStatus.FINISHED &&
            it.endDate != null && it.startDate != null &&
            it.endDate.startsWith(year.toString()) &&
            !it.importedFromGoodreads
        }
        // v19.8: relecturas completadas en el año (reread_end events del año)
        val rereadsThisYear = books.filter { !it.importedFromGoodreads }.flatMap { b ->
            b.dateEvents.filter { it.type == "reread_end" && it.date.startsWith(year.toString()) }
                .map { b to it }
        }
        if (finished.isEmpty() && rereadsThisYear.isEmpty()) return null

        val rereadCount = rereadsThisYear.size
        val totalBooks = finished.size + rereadCount
        // v19.8: páginas de las relecturas también se suman
        val totalPages = finished.sumOf { it.pages } + rereadsThisYear.sumOf { (b, _) -> b.pages }
        // Exclude same-day books (startDate == endDate) from speed-based stats
        val multiDayBooks = finished.filter { it.startDate != it.endDate && daysBetween(it.startDate!!, it.endDate!!) >= 2 }
        val speeds = multiDayBooks.map { b ->
            val d = daysBetween(b.startDate!!, b.endDate!!)
            Pair(b, b.pages.toDouble() / d)
        }
        val avgPpd = if (speeds.isNotEmpty()) speeds.map { it.second }.average() else 0.0
        val avgDays = if (multiDayBooks.isNotEmpty()) multiDayBooks.map { b -> daysBetween(b.startDate!!, b.endDate!!).toDouble() }.average() else 0.0

        val favAuthorEntry = finished.filter { it.author.isNotBlank() }
            .groupBy { it.author }.maxByOrNull { it.value.size }
        val favGenreEntry = finished.flatMap { b -> b.genres.map { g -> g to b } }
            .groupBy({ it.first }, { it.second }).maxByOrNull { it.value.size }

        val fastest = if (speeds.isNotEmpty()) speeds.maxByOrNull { it.second } else null
        val bestRated = finished.filter { it.rating > 0 }.maxByOrNull { it.rating }
        // v21.41: top 3 libros por nota, desempate por endDate desc (los más recientes primero)
        val bestRatedTop3List = finished
            .filter { it.rating > 0 }
            .sortedWith(compareByDescending<Book> { it.rating }.thenByDescending { it.endDate ?: "" })
            .take(3)
            .map { Triple(it.title, it.rating, it.endDate ?: "") }

        // Racha más larga: días consecutivos con al menos 1 libro terminado
        // v1.4 fix crash: si el año solo tiene relecturas, finished está vacío →
        // endDates.first() lanzaba NoSuchElementException. Guard + valores neutros.
        val endDates = finished.map { it.endDate!! }.toSortedSet()
        var maxStreak = if (endDates.isEmpty()) 0 else 1
        var streakStart = endDates.firstOrNull() ?: ""
        var streakEnd = endDates.firstOrNull() ?: ""
        if (endDates.isNotEmpty()) {
            var streak = 1
            var curStart = endDates.first()
            val dateList = endDates.toList()
            for (i in 1 until dateList.size) {
                val prev = dateList[i - 1]; val curr = dateList[i]
                if (daysBetween(prev, curr) == 1) {
                    streak++
                    if (streak > maxStreak) { maxStreak = streak; streakStart = curStart; streakEnd = curr }
                } else { streak = 1; curStart = curr }
            }
        }

        // ── v18.3: nuevos cálculos ──────────────────────────────────────────
        // Sesiones del año (basadas en su fecha, no en el libro)
        val sessionsInYear = sessions.filter { it.date.startsWith(year.toString()) }
        val totalSessions = sessionsInYear.size
        val totalMinutes = sessionsInYear.mapNotNull { it.minutes }.sum()
        val maxSession = sessionsInYear.maxByOrNull { it.pages }
        val maxSessionPages = maxSession?.pages ?: 0
        val maxSessionDate = maxSession?.date ?: ""
        // Día con más páginas leídas
        val pagesByDay = sessionsInYear.groupBy { it.date }.mapValues { (_, lst) -> lst.sumOf { it.pages } }
        val mostReadEntry = pagesByDay.maxByOrNull { it.value }
        val mostReadDay = mostReadEntry?.key ?: ""
        val mostReadDayPages = mostReadEntry?.value ?: 0
        // Libros abandonados ese año (dropDate dentro del año)
        val droppedBooks = books.count { b ->
            b.status == BookStatus.DROPPED &&
            (b.dropDate?.startsWith(year.toString()) == true) &&
            !b.importedFromGoodreads
        }
        // Top 3 autores y géneros
        // v18.6: cada autor lleva (nº libros únicos, nº ediciones totales). Los autores
        // se ordenan por libros únicos (no por ediciones). Si Sanderson tiene 1 libro en
        // 2 ediciones, aparece como "1 libro (2 ediciones distintas)" en Wrapped.
        val topAuthorsGrouped = finished.filter { it.author.isNotBlank() }
            .groupBy { it.author }
            .entries.sortedByDescending { it.value.size }
            .take(3)
        val topAuthorsTop3 = topAuthorsGrouped.map { it.key to it.value.size }
        val topAuthorsTop3Editions = topAuthorsGrouped.map { entry ->
            entry.value.sumOf { b -> b.editions.size.coerceAtLeast(1) }
        }
        val topGenresTop3 = finished.flatMap { b -> b.genres.map { g -> g to b } }
            .groupBy({ it.first }, { it.second })
            .entries.sortedByDescending { it.value.size }
            .take(3)
            .map { it.key to it.value.size }

        // v18.4: páginas y libros por mes
        val pagesPerMonth = IntArray(12)
        val booksPerMonth = IntArray(12)
        sessionsInYear.forEach { s ->
            val m = s.date.substring(5, 7).toIntOrNull()?.minus(1)
            if (m != null && m in 0..11) pagesPerMonth[m] += s.pages
        }
        finished.forEach { b ->
            val m = b.endDate!!.substring(5, 7).toIntOrNull()?.minus(1)
            if (m != null && m in 0..11) booksPerMonth[m] += 1
        }
        // Comparativa año anterior
        val prevYearFinished = books.filter { b ->
            b.status == BookStatus.FINISHED && b.endDate != null &&
            b.endDate.startsWith((year - 1).toString()) &&
            !b.importedFromGoodreads
        }
        val previousYearBooks = prevYearFinished.size
        val previousYearPages = prevYearFinished.sumOf { it.pages }

        // v18.5: distribución por género (género principal de cada libro, top 6 para donut)
        val genreCountsTop6 = finished
            .mapNotNull { b -> (b.genres.firstOrNull()?.ifBlank { null }) ?: b.genre.ifBlank { null } }
            .groupBy { it }
            .entries.sortedByDescending { it.value.size }
            .take(6)
            .map { it.key to it.value.size }

        // v19.3: top 3 libros por minutos totales de sesión en el año
        val longestBooksTop3 = sessions
            .filter { it.date.startsWith(year.toString()) && (it.minutes ?: 0) > 0 }
            .groupBy { it.bookId }
            .mapNotNull { (bookId, sess) ->
                val book = books.find { it.id == bookId } ?: return@mapNotNull null
                val totalMins = sess.sumOf { it.minutes ?: 0 }
                if (totalMins > 0) Pair(book.title, totalMins) else null
            }
            .sortedByDescending { it.second }
            .take(3)

        return YearWrapped(
            year = year,
            totalBooks = totalBooks,
            totalPages = totalPages,
            avgPagesPerDay = avgPpd,
            avgDaysPerBook = avgDays,
            favoriteAuthor = favAuthorEntry?.key ?: "",
            favoriteAuthorBooks = favAuthorEntry?.value?.size ?: 0,
            favoriteGenre = favGenreEntry?.key ?: "",
            favoriteGenreBooks = favGenreEntry?.value?.size ?: 0,
            fastestBookTitle = fastest?.first?.title ?: "",
            fastestBookPpd = fastest?.second ?: 0.0,
            fastestBookPages = fastest?.first?.pages ?: 0,
            bestRatedTitle = bestRated?.title ?: "",
            bestRatedScore = bestRated?.rating ?: 0,
            longestStreakDays = maxStreak,
            longestStreakStart = streakStart,
            longestStreakEnd = streakEnd,
            totalSessions = totalSessions,
            totalMinutes = totalMinutes,
            maxSessionPages = maxSessionPages,
            maxSessionDate = maxSessionDate,
            mostReadDay = mostReadDay,
            mostReadDayPages = mostReadDayPages,
            droppedBooks = droppedBooks,
            topAuthorsTop3 = topAuthorsTop3,
            topAuthorsTop3Editions = topAuthorsTop3Editions,
            topGenresTop3 = topGenresTop3,
            pagesPerMonth = pagesPerMonth.toList(),
            booksPerMonth = booksPerMonth.toList(),
            previousYearBooks = previousYearBooks,
            previousYearPages = previousYearPages,
            genreCountsTop6 = genreCountsTop6,
            longestBooksTop3 = longestBooksTop3,
            rereadBooks = rereadCount,
            bestRatedTop3 = bestRatedTop3List
        )
    }

    fun importFromGoodreads(context: Context, uri: Uri, prefs: android.content.SharedPreferences): Int {
        var imported = 0
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return 0
            val reader = BufferedReader(InputStreamReader(stream))
            val header = reader.readLine() ?: return 0
            val cols = header.split(",").map { it.trim().trim('"') }
            val idxTitle = cols.indexOfFirst { it.contains("Title", true) }
            val idxAuthor = cols.indexOfFirst { it.contains("Author", true) }
            val idxPages = cols.indexOfFirst { it.contains("Pages", true) }
            val idxStatus = cols.indexOfFirst { it.contains("Shelf", true) || it.contains("Status", true) }
            val idxRating = cols.indexOfFirst { it.contains("My Rating", true) }
            val idxDateRead = cols.indexOfFirst { it.contains("Date Read", true) }
            val idxISBN = cols.indexOfFirst { it.contains("ISBN", true) }
            val idxBookshelves = cols.indexOfFirst { it.equals("Bookshelves", true) }
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = parseCsvLine(line)
                val title = parts.getOrNull(idxTitle)?.trim()?.trim('"') ?: return@forEachLine
                if (title.isBlank()) return@forEachLine
                val author = parts.getOrNull(idxAuthor)?.trim()?.trim('"') ?: ""
                val pages = parts.getOrNull(idxPages)?.trim()?.trim('"')?.toIntOrNull() ?: 0
                val shelfRaw = parts.getOrNull(idxStatus)?.trim()?.trim('"')?.lowercase() ?: ""
                val ratingRaw = parts.getOrNull(idxRating)?.trim()?.trim('"')?.toIntOrNull() ?: 0
                val dateRead = parts.getOrNull(idxDateRead)?.trim()?.trim('"')?.let { dr ->
                    if (dr.matches(Regex("\\d{4}/\\d{2}/\\d{2}"))) dr.replace("/", "-") else null
                }
                val isbn = parts.getOrNull(idxISBN)?.trim()?.trim('"')?.replace("=", "")?.replace("\"", "")?.trimStart('0')
                    ?.let { raw -> if (raw.length >= 10 && raw.matches(Regex("^[0-9X]+$"))) raw else null }
                val rating10 = if (ratingRaw in 1..5) ratingRaw * 2 else 0
                val status = when {
                    shelfRaw.contains("read") && !shelfRaw.contains("to") && !shelfRaw.contains("currently") -> BookStatus.FINISHED
                    shelfRaw.contains("currently") || shelfRaw.contains("reading") -> BookStatus.READING
                    else -> BookStatus.PENDING
                }
                val coverUrl = if (!isbn.isNullOrBlank()) "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg" else null
                // Auto-assign genre: try Bookshelves column first, then infer from title
                val bookshelvesRaw = if (idxBookshelves >= 0) parts.getOrNull(idxBookshelves)?.trim()?.trim('"') ?: "" else ""
                // Solo intentar inferir género desde la columna Bookshelves (etiquetas reales de Goodreads).
                // No intentar inferir desde el título — produce demasiados falsos positivos.
                val autoGenres = if (bookshelvesRaw.isNotBlank()) mapApiGenre(bookshelvesRaw) else emptyList()
                books = listOf(Book(id = System.currentTimeMillis() + imported, title = title, author = author, pages = if (pages > 0) pages else 1, startDate = if (status != BookStatus.PENDING) (dateRead ?: today()) else null, endDate = if (status == BookStatus.FINISHED) dateRead else null, status = status, rating = rating10, coverUrl = coverUrl, isbn = isbn, genres = autoGenres, importedFromGoodreads = true)) + books
                imported++
            }
            if (imported > 0) save(prefs)
        } catch (e: Exception) { e.printStackTrace() }
        return imported
    }
}

fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>(); var inQuotes = false; val cur = StringBuilder()
    for (c in line) { when { c == '"' -> inQuotes = !inQuotes; c == ',' && !inQuotes -> { result.add(cur.toString()); cur.clear() }; else -> cur.append(c) } }
    result.add(cur.toString()); return result
}

// ── Helpers ───────────────────────────────────────────────────────────────────

// v1.4: SimpleDateFormat NO es thread-safe. Se usa desde main y Dispatchers.IO
// (backups, workers). ThreadLocal da una instancia por hilo — sin locks, sin corrupción.
private val sdfTL = java.lang.ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
private val sdfDisplayTL = java.lang.ThreadLocal.withInitial { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }
private val sdfInputTL = java.lang.ThreadLocal.withInitial { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()) }
private val sdf: SimpleDateFormat get() = sdfTL.get()!!
private val sdfDisplay: SimpleDateFormat get() = sdfDisplayTL.get()!!
private val sdfInput: SimpleDateFormat get() = sdfInputTL.get()!!

fun today(): String = sdf.format(Date())
fun todayDisplay(): String = sdfInput.format(Date())

/** Parsea "d-M-yyyy" o "dd-MM-yyyy" — acepta 1 o 2 dígitos en día y mes.
 *  Rechaza fechas imposibles (39/01, 29/02 en año no bisiesto, etc.).
 *  Devuelve "yyyy-MM-dd" para almacenamiento, o null si la fecha es inválida. */
fun parseFlexibleDate(input: String): String? {
    val parts = input.trim().split("-")
    if (parts.size != 3) return null
    if (parts[0].length !in 1..2 || parts[1].length !in 1..2 || parts[2].length != 4) return null
    val day   = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val year  = parts[2].toIntOrNull() ?: return null
    if (month < 1 || month > 12) return null
    if (day < 1) return null
    val cal = java.util.Calendar.getInstance()
    cal.isLenient = false
    return try {
        cal.set(year, month - 1, day)
        cal.time
        "%04d-%02d-%02d".format(year, month, day)
    } catch (_: Exception) { null }
}

fun storedToDisplay(date: String): String =
    try { sdfInput.format(sdf.parse(date)!!) } catch (_: Exception) { date }

/** Convierte "dd-MM-yyyy" (input del usuario) → "yyyy-MM-dd" (almacenamiento). */
fun displayToStored(date: String): String =
    try { sdf.format(sdfInput.parse(date)!!) } catch (_: Exception) { date }

fun daysBetween(start: String, end: String): Int {
    return try {
        val s = sdf.parse(start) ?: return 1
        val e = sdf.parse(end) ?: return 1
        val d = ceil((e.time - s.time) / 86400000.0).toInt()
        if (d < 0) 0 else d
    } catch (_: Exception) { 1 }
}

// Returns true if a finished book has a real multi-day reading span (not same-day)
fun hasValidReadingSpan(book: Book): Boolean =
    book.startDate != null && book.endDate != null && book.startDate != book.endDate && daysBetween(book.startDate, book.endDate) >= 1

fun fmtDays(days: Int): String = if (days == 1) "1 día" else "$days días"

/** Versión Composable de fmtDays usando strings localizados. */
@Composable
fun fmtDaysLocalized(days: Int): String {
    val dayWord = stringResource(R.string.word_days)
    val dayWordSingular = stringResource(R.string.word_day)
    return if (days == 1) "1 $dayWordSingular" else "$days $dayWord"
}

fun fmtDate(date: String): String = try { sdfDisplay.format(sdf.parse(date)!!) } catch (_: Exception) { date }

// Only FINISHED books get pagesPerDay — no estimates for READING
// Books with same startDate and endDate are excluded from pagesPerDay (same-day = unreliable speed)
fun getStats(book: Book): BookStats? = when {
    book.status == BookStatus.FINISHED && book.startDate != null && book.endDate != null -> {
        val d = daysBetween(book.startDate, book.endDate).coerceAtLeast(1)
        val ppd = if (book.startDate != book.endDate) {
            val funcPages = if (book.firstFunctionalPage != null && book.lastFunctionalPage != null)
                (book.lastFunctionalPage - book.firstFunctionalPage + 1).toDouble()
            else book.pages.toDouble()
            if (funcPages > 0) funcPages / d else null
        } else null
        BookStats(d, ppd)
    }
    (book.status == BookStatus.READING || book.status == BookStatus.REREADING) && book.startDate != null ->
        BookStats(daysBetween(book.startDate, today()).coerceAtLeast(1), null)
    else -> null
}

// v19.8: stats por ciclo (lectura original + relecturas)
data class CycleStats(
    val readingIndex: Int,       // 0 = lectura original, 1+ = relecturas
    val label: String,           // "Lectura", "Relectura", "Relectura 2", ...
    val startDate: String?,      // fecha inicio del ciclo
    val endDate: String?,        // fecha fin del ciclo (null si en curso)
    val days: Int?,              // días duración (null si no hay start)
    val pagesPerDay: Double?,    // sólo si terminado y >=2 días
    val sessions: List<ReadingSession>,
    val isOngoing: Boolean       // true si el ciclo no ha terminado
)

/** Calcula stats separados por cada ciclo de lectura del libro. */
fun computeCycleStats(book: Book, allSessions: List<ReadingSession>): List<CycleStats> {
    val events = sortedDateEvents(migrateLegacyToEvents(book))
    val bookSessions = allSessions.filter { it.bookId == book.id }
    val result = mutableListOf<CycleStats>()

    val funcPages: Double = if (book.firstFunctionalPage != null && book.lastFunctionalPage != null)
        (book.lastFunctionalPage - book.firstFunctionalPage + 1).toDouble()
    else book.pages.toDouble()

    // Ciclo 0 — lectura original: start → end
    val startEv = events.firstOrNull { it.type == "start" }?.date
    val endEv = events.firstOrNull { it.type == "end" }?.date
    val originalOngoing = endEv == null && book.status != BookStatus.PENDING
    if (startEv != null || bookSessions.any { (it.readingIndex ?: 0) == 0 }) {
        val days = if (startEv != null) {
            val toDate = endEv ?: today()
            daysBetween(startEv, toDate).coerceAtLeast(1)
        } else null
        val ppd = if (!originalOngoing && startEv != null && endEv != null && startEv != endEv && days != null && funcPages > 0)
            funcPages / days else null
        result.add(CycleStats(
            readingIndex = 0,
            label = "Lectura",
            startDate = startEv,
            endDate = endEv,
            days = days,
            pagesPerDay = ppd,
            sessions = bookSessions.filter { (it.readingIndex ?: 0) == 0 },
            isOngoing = originalOngoing
        ))
    }

    // Ciclos de relectura: cada reread N → reread_end N (o null si abierto)
    val rereads = events.filter { it.type == "reread" }.sortedBy { it.occurrence }
    // v20.2: si hay >1 relectura, numeramos todas (Relectura 1, Relectura 2, …)
    val multipleRereads = rereads.size > 1
    for (r in rereads) {
        val end = events.firstOrNull { it.type == "reread_end" && it.occurrence == r.occurrence }?.date
        // v20.0: si el libro está DROPPED durante la relectura, el ciclo NO está "en curso"
        // (drop/resume son eventos internos del ciclo de relectura, pero el "(En curso)" debe limpiarse).
        val ongoing = end == null && book.status != BookStatus.DROPPED
        val toDate = end ?: today()
        val days = daysBetween(r.date, toDate).coerceAtLeast(1)
        val ppd = if (!ongoing && r.date != end && days >= 2 && funcPages > 0) funcPages / days else null
        result.add(CycleStats(
            readingIndex = r.occurrence,
            label = if (multipleRereads) "Relectura ${r.occurrence}" else "Relectura",
            startDate = r.date,
            endDate = end,
            days = days,
            pagesPerDay = ppd,
            sessions = bookSessions.filter { (it.readingIndex ?: 0) == r.occurrence },
            isOngoing = ongoing
        ))
    }
    return result
}

fun List<Book>.applySort(order: SortOrder): List<Book> = when (order) {
    SortOrder.DATE_DESC   -> sortedByDescending { it.addedAt }
    SortOrder.DATE_ASC    -> sortedBy { it.addedAt }
    SortOrder.ALPHA_AZ    -> sortedBy { it.title.lowercase() }
    SortOrder.ALPHA_ZA    -> sortedByDescending { it.title.lowercase() }
    SortOrder.RATING_DESC -> sortedWith(compareByDescending { if (it.rating > 0) it.rating else Int.MIN_VALUE })
    SortOrder.RATING_ASC  -> sortedWith(compareBy { if (it.rating > 0) it.rating else Int.MAX_VALUE })
    SortOrder.LENGTH_DESC -> sortedByDescending { it.pages }
    SortOrder.LENGTH_ASC  -> sortedBy { it.pages }
}

@Composable
fun sortLabel(order: SortOrder): String = when (order) {
    SortOrder.DATE_DESC   -> stringResource(R.string.sort_date_desc)
    SortOrder.DATE_ASC    -> stringResource(R.string.sort_date_asc)
    SortOrder.ALPHA_AZ    -> "A → Z"
    SortOrder.ALPHA_ZA    -> "Z → A"
    SortOrder.RATING_DESC -> stringResource(R.string.sort_rating_desc)
    SortOrder.RATING_ASC  -> stringResource(R.string.sort_rating_asc)
    SortOrder.LENGTH_DESC -> stringResource(R.string.sort_length_desc)
    SortOrder.LENGTH_ASC  -> stringResource(R.string.sort_length_asc)
}

// Ventana Wrapped: 26 dic del año anterior → 26 ene del año en curso
fun wrappedWindowYear(): Int {
    val cal = Calendar.getInstance()
    val month = cal.get(Calendar.MONTH) + 1  // 1-based
    val day   = cal.get(Calendar.DAY_OF_MONTH)
    val year  = cal.get(Calendar.YEAR)
    // Si estamos en enero (mes 1) hasta el día 26 → el wrapped es del año anterior
    return if (month == 1 && day <= 26) year - 1
    // Si estamos en diciembre a partir del día 26 → el wrapped es del año en curso
    else if (month == 12 && day >= 26) year
    else -1  // fuera de ventana
}

fun isInWrappedWindow(): Boolean = wrappedWindowYear() != -1

@Composable
fun nextWrappedSubtitle(): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.MONTH, Calendar.DECEMBER)
        set(Calendar.DAY_OF_MONTH, 26)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (!after(now)) add(Calendar.YEAR, 1)
    }
    val days = kotlin.math.ceil((target.timeInMillis - now.timeInMillis) / 86_400_000.0).toInt().coerceAtLeast(0)
    return stringResource(R.string.wrapped_countdown, days)
}

// ── Colores ───────────────────────────────────────────────────────────────────

val Accent  = Color(0xFF6366F1); val Accent2 = Color(0xFF8B5CF6)
// v21.35: Accent adaptado por tema — turquesa en Aurora (legibilidad), índigo oscuro en Claro (contraste)
val AccentAurora = Color(0xFF22D3EE)
val AccentLight  = Color(0xFF4338CA)
fun accentForTheme(theme: Theme): Color = when {
    !theme.isDark && theme.bgDark == Color(0xFFDDE3EC) -> AccentLight   // Claro D
    theme.bgDark == Color(0xFF150B33)                  -> AccentAurora  // Aurora
    else -> Accent
}
val Green   = Color(0xFF10B981); val Red     = Color(0xFFF87171)
val Amber   = Color(0xFFF59E0B); val Gold    = Color(0xFFFFBB33)
val Sky     = Color(0xFF0EA5E9)

val BgDarkD = Color(0xFF0F172A); val BgMidD = Color(0xFF1E1B4B)
val SurfaceD = Color(0x0DFFFFFF); val BorderD = Color(0x14FFFFFF)
val TextMainD = Color(0xFFF1F5F9); val TextMutedD = Color(0xFF94A3B8); val TextDimD = Color(0xFF64748B)

val BgDarkL = Color(0xFFDDE3EC); val BgMidL = Color(0xFFD0D9E5)
val SurfaceL = Color(0xFFEEF2F8); val BorderL = Color(0xFFBCC8D8)
val TextMainL = Color(0xFF1A2030); val TextMutedL = Color(0xFF485868); val TextDimL = Color(0xFF7A90A4)

// Aurora: azul-morado profundo con toques verde azulado (teal) — texto sin tocar, legibilidad
val BgDarkA = Color(0xFF150B33); val BgMidA = Color(0xFF2A1158)
val SurfaceA = Color(0x268B5CF6); val BorderA = Color(0x6622E5D0)
val TextMainA = Color(0xFFEDE9FF); val TextMutedA = Color(0xFFC4B5FD); val TextDimA = Color(0xFFB0A3D4)

// AMOLED: negro puro para pantallas OLED
val BgDarkAm = Color(0xFF000000); val BgMidAm = Color(0xFF0A0A0A)
val SurfaceAm = Color(0x10FFFFFF); val BorderAm = Color(0x18FFFFFF)
val TextMainAm = Color(0xFFF1F5F9); val TextMutedAm = Color(0xFF94A3B8); val TextDimAm = Color(0xFF64748B)

enum class ThemeMode(val value: String) {
    LIGHT("light"), DARK("dark"), AURORA("aurora"), AMOLED("amoled")
}

data class Theme(val bgDark: Color, val bgMid: Color, val surface: Color, val border: Color, val textMain: Color, val textMuted: Color, val textDim: Color, val isDark: Boolean, val bgSurf: Color = Color.Transparent, val bgSurf2: Color = Color.Transparent)

fun buildTheme(mode: ThemeMode) = when (mode) {
    ThemeMode.LIGHT  -> Theme(BgDarkL,  BgMidL,  SurfaceL,  BorderL,  TextMainL,  TextMutedL,  TextDimL,  false, bgSurf = Color(0xFFEEF2F8), bgSurf2 = Color(0xFFDDE3EC))
    ThemeMode.DARK   -> Theme(BgDarkD,  BgMidD,  SurfaceD,  BorderD,  TextMainD,  TextMutedD,  TextDimD,  true,  bgSurf = Color(0x1AFFFFFF), bgSurf2 = Color(0x0DFFFFFF))
    ThemeMode.AURORA -> Theme(BgDarkA,  BgMidA,  SurfaceA,  BorderA,  TextMainA,  TextMutedA,  TextDimA,  true,  bgSurf = Color(0x12FFFFFF), bgSurf2 = Color(0x08FFFFFF))
    ThemeMode.AMOLED -> Theme(BgDarkAm, BgMidAm, SurfaceAm, BorderAm, TextMainAm, TextMutedAm, TextDimAm, true,  bgSurf = Color(0x0FFFFFFF), bgSurf2 = Color(0x06FFFFFF))
}

fun statusColor(s: BookStatus) = when (s) {
    BookStatus.READING   -> Amber
    BookStatus.REREADING -> Color(0xFF06B6D4)
    BookStatus.FINISHED  -> Green
    BookStatus.PENDING   -> Color(0xFF8B5CF6)
    BookStatus.DROPPED   -> Color(0xFFF87171)
}
@Composable
fun statusLabel(s: BookStatus) = when (s) {
    BookStatus.READING   -> stringResource(R.string.status_reading)
    BookStatus.REREADING -> stringResource(R.string.status_rereading)
    BookStatus.FINISHED  -> stringResource(R.string.status_finished)
    BookStatus.PENDING   -> stringResource(R.string.status_pending)
    BookStatus.DROPPED   -> stringResource(R.string.status_dropped)
}
fun statusEmoji(s: BookStatus) = when (s) {
    BookStatus.READING   -> "📖"
    BookStatus.REREADING -> "🔁"
    BookStatus.FINISHED  -> "✅"
    BookStatus.PENDING   -> "🔖"
    BookStatus.DROPPED   -> "❌"
}

// ── Géneros (OpenLibrary + Google Books, orden alfabético) ────────────────────

val BOOK_GENRES = listOf(
    "Arte",
    "Autoayuda",
    "Aventura",
    "Biografía",
    "Ciencia",
    "Ciencia ficción",
    "Cómics y novela gráfica",
    "Costumbrismo",
    "Crimen",
    "Deportes",
    "Desarrollo personal",
    "Drama",
    "Economía",
    "Educación",
    "Ensayo",
    "Fantasía",
    "Filosofía",
    "Grimdark",
    "Historia",
    "Humor",
    "Infantil",
    "Juvenil",
    "Lingüística",
    "Literatura clásica",
    "Manga",
    "Misterio",
    "Mitología",
    "Música",
    "Naturaleza",
    "Negocios",
    "Novela gráfica",
    "Novela histórica",
    "Novela negra",
    "Periodismo",
    "Poesía",
    "Política",
    "Psicología",
    "Religión y espiritualidad",
    "Romance",
    "Salud y bienestar",
    "Sociología",
    "Suspense",
    "Teatro",
    "Tecnología",
    "Terror",
    "Thriller",
    "Viajes",
    "Otro"
)

// Mapa de género canónico (ES, valor guardado/usado en matching) → string resource para mostrar.
// El valor guardado en book.genres NUNCA cambia con el idioma; solo cambia lo que se pinta.
val GENRE_DISPLAY_KEY: Map<String, Int> = mapOf(
    "Arte" to R.string.genre_arte,
    "Autoayuda" to R.string.genre_autoayuda,
    "Aventura" to R.string.genre_aventura,
    "Biografía" to R.string.genre_biografia,
    "Ciencia" to R.string.genre_ciencia,
    "Ciencia ficción" to R.string.genre_ciencia_ficcion,
    "Cómics y novela gráfica" to R.string.genre_comics_y_novela_grafica,
    "Costumbrismo" to R.string.genre_costumbrismo,
    "Crimen" to R.string.genre_crimen,
    "Deportes" to R.string.genre_deportes,
    "Desarrollo personal" to R.string.genre_desarrollo_personal,
    "Drama" to R.string.genre_drama,
    "Economía" to R.string.genre_economia,
    "Educación" to R.string.genre_educacion,
    "Ensayo" to R.string.genre_ensayo,
    "Fantasía" to R.string.genre_fantasia,
    "Filosofía" to R.string.genre_filosofia,
    "Grimdark" to R.string.genre_grimdark,
    "Historia" to R.string.genre_historia,
    "Humor" to R.string.genre_humor,
    "Infantil" to R.string.genre_infantil,
    "Juvenil" to R.string.genre_juvenil,
    "Lingüística" to R.string.genre_linguistica,
    "Literatura clásica" to R.string.genre_literatura_clasica,
    "Manga" to R.string.genre_manga,
    "Misterio" to R.string.genre_misterio,
    "Mitología" to R.string.genre_mitologia,
    "Música" to R.string.genre_musica,
    "Naturaleza" to R.string.genre_naturaleza,
    "Negocios" to R.string.genre_negocios,
    "Novela gráfica" to R.string.genre_novela_grafica,
    "Novela histórica" to R.string.genre_novela_historica,
    "Novela negra" to R.string.genre_novela_negra,
    "Periodismo" to R.string.genre_periodismo,
    "Poesía" to R.string.genre_poesia,
    "Política" to R.string.genre_politica,
    "Psicología" to R.string.genre_psicologia,
    "Religión y espiritualidad" to R.string.genre_religion_y_espiritualidad,
    "Romance" to R.string.genre_romance,
    "Salud y bienestar" to R.string.genre_salud_y_bienestar,
    "Sociología" to R.string.genre_sociologia,
    "Suspense" to R.string.genre_suspense,
    "Teatro" to R.string.genre_teatro,
    "Tecnología" to R.string.genre_tecnologia,
    "Terror" to R.string.genre_terror,
    "Thriller" to R.string.genre_thriller,
    "Viajes" to R.string.genre_viajes,
    "Otro" to R.string.genre_otro,
)

// Traduce un género canónico para mostrarlo al usuario. Si no está en el mapa
// (dato legacy o no reconocido), se muestra tal cual venía guardado.
@Composable
fun displayGenre(raw: String): String {
    val resId = GENRE_DISPLAY_KEY[raw]
    return if (resId != null) stringResource(resId) else raw
}

// Mapea géneros crudos de Google Books / OpenLibrary a géneros canónicos.
// Devuelve 0, 1 o 2 géneros de BOOK_GENRES.
// Correcciones clave vs versión anterior:
//   - "animal" eliminado de Naturaleza (falso positivo con "Animals, Mythical -- Fiction")
//   - "occult fiction" → Fantasía (no Terror; es una categoría de biblioteca para magia)
//   - "juvenile" solo no activa Juvenil ("Juvenile Fiction" es categoría genérica de GB/OL)
//   - "historical" sin "fiction" → Historia; con "fiction" → Novela histórica
//   - Multi-género: devuelve hasta 2 coincidencias ordenadas por prioridad
fun mapApiGenre(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val r = raw.lowercase().trim()

    // Coincidencia exacta con un género canónico
    val exact = BOOK_GENRES.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }
    if (exact != null && exact != "Otro") return listOf(exact)

    val found = mutableListOf<String>()

    fun add(genre: String) { if (!found.contains(genre)) found.add(genre) }

    // ── Ciencia ficción ────────────────────────────────────────────────────
    if ((r.contains("science") && r.contains("fiction")) ||
        r.contains("sci-fi") || r.contains("sci fi") ||
        r.contains("ciencia ficción") || r.contains("ciencia ficcion") ||
        r.contains("cyberpunk") || r.contains("dystopi") || r.contains("distopi") ||
        r.contains("space opera") || r.contains("post-apocalyp") || r.contains("postapocalyp") ||
        r.contains("steampunk") || r.contains("biopunk")) add("Ciencia ficción")

    // ── Grimdark (fantasía oscura) — antes de Fantasía y Terror para que tenga prioridad ─
    if (r.contains("grimdark") || r.contains("grim dark") || r.contains("grim-dark") ||
        r.contains("dark fantasy") || r.contains("fantasía oscura") || r.contains("fantasia oscura")) add("Grimdark")

    // ── Fantasía ───────────────────────────────────────────────────────────
    if (r.contains("fantasy") || r.contains("fantasia") || r.contains("fantasía") ||
        r.contains("magic") || r.contains("magia") || r.contains("dragon") ||
        r.contains("supernatural") || r.contains("sobrenatural") ||
        r.contains("wizards") || r.contains("witch") || r.contains("bruja") ||
        r.contains("epic fantasy") || r.contains("sword and sorcery") ||
        r.contains("urban fantasy") || r.contains("dark fantasy") ||
        r.contains("light novel") ||
        // "occult fiction" es categoría de biblioteca para magia/sobrenatural → Fantasía, NO Terror
        (r.contains("occult") && r.contains("fiction")) ||
        // paranormal sin horror explícito → Fantasía
        (r.contains("paranormal") && !r.contains("horror"))) add("Fantasía")

    // ── Terror / Horror ────────────────────────────────────────────────────
    if (r.contains("horror") || r.contains("terror") ||
        r.contains("gothic") || r.contains("gótico") ||
        r.contains("ghost") || r.contains("dark fiction") ||
        // occult solo (sin fiction) puede ser Terror
        (r.contains("occult") && !r.contains("fiction")) ||
        // paranormal horror → Terror (puede coexistir con Fantasía)
        (r.contains("paranormal") && r.contains("horror"))) add("Terror")

    // ── Romance ────────────────────────────────────────────────────────────
    if (r.contains("romance") || r.contains("love story") || r.contains("historia de amor") ||
        r.contains("romantic")) add("Romance")

    // ── Thriller / Suspense ────────────────────────────────────────────────
    if (r.contains("thriller") || r.contains("suspense") ||
        r.contains("spy") || r.contains("espionaje") ||
        (Regex("\baction\b").containsMatchIn(r) && !r.contains("adventure") && !r.contains("aventura"))) add("Thriller")

    // ── Misterio ───────────────────────────────────────────────────────────
    if (r.contains("mystery") || r.contains("misterio") ||
        r.contains("detective") || r.contains("whodunit") || r.contains("cozy")) add("Misterio")

    // ── Novela negra ───────────────────────────────────────────────────────
    if (r.contains("crime") || r.contains("crimen") || r.contains("noir") ||
        r.contains("murder") || Regex("\bpolic").containsMatchIn(r) ||
        r.contains("true crime") || r.contains("crimen real") || r.contains("criminal")) add("Novela negra")

    // ── Aventura ───────────────────────────────────────────────────────────
    if (r.contains("adventure") || r.contains("aventura") ||
        r.contains("western") || r.contains("action-adventure")) add("Aventura")

    // ── Novela histórica ───────────────────────────────────────────────────
    if (r.contains("historical fiction") || r.contains("ficción histórica") ||
        r.contains("ficcion historica") || r.contains("novela histórica") ||
        r.contains("novela historica")) add("Novela histórica")

    // ── Historia ───────────────────────────────────────────────────────────
    if ((r.contains("history") || (r.contains("historia") && !r.contains("fiction") && !r.contains("novel"))) ||
        (r.contains("historical") && !r.contains("fiction")) ||
        (r.contains("históric") && !r.contains("fiction")) ||
        Regex("\bwar\b").containsMatchIn(r) || Regex("\bguerra\b").containsMatchIn(r) ||
        ((r.contains("military") || r.contains("militar") || r.contains("bélico") || r.contains("belico")) &&
         !r.contains("fantasy") && !r.contains("fiction"))) add("Historia")

    // ── Biografía ──────────────────────────────────────────────────────────
    if (r.contains("biography") || r.contains("autobio") ||
        r.contains("biografía") || r.contains("biografia") ||
        r.contains("memoir") || r.contains("memorias")) add("Biografía")

    // ── Autoayuda ──────────────────────────────────────────────────────────
    if (r.contains("self-help") || r.contains("self help") || r.contains("autoayuda")) add("Autoayuda")

    // ── Desarrollo personal ────────────────────────────────────────────────
    if (r.contains("personal development") || r.contains("desarrollo personal") ||
        r.contains("personal growth") || r.contains("crecimiento personal")) add("Desarrollo personal")

    // ── Psicología ─────────────────────────────────────────────────────────
    if (r.contains("psychology") || r.contains("psicolog") || r.contains("cognitive")) add("Psicología")

    // ── Filosofía ──────────────────────────────────────────────────────────
    if (r.contains("philosophy") || r.contains("filosof")) add("Filosofía")

    // ── Ciencia ────────────────────────────────────────────────────────────
    if (r.contains("popular science") || r.contains("divulgación") || r.contains("divulgacion") ||
        r.contains("physics") || r.contains("física") || r.contains("chemistry") ||
        r.contains("biology") || r.contains("biología") || r.contains("astronomy") ||
        r == "science" || r == "ciencia" || r.contains("natural science")) add("Ciencia")

    // ── Tecnología ─────────────────────────────────────────────────────────
    if (r.contains("technology") || r.contains("tecnolog") ||
        r.contains("computing") || r.contains("computer") || r.contains("informática") ||
        r.contains("programm") || r.contains("software") || r.contains("internet")) add("Tecnología")

    // ── Negocios ───────────────────────────────────────────────────────────
    if (r.contains("business") || r.contains("negocio") ||
        r.contains("management") || r.contains("marketing") ||
        r.contains("entrepreneurship") || r.contains("emprendimiento") ||
        r.contains("leadership") || r.contains("liderazgo")) add("Negocios")

    // ── Economía ───────────────────────────────────────────────────────────
    if (r.contains("econom") || r.contains("finance") || r.contains("finanzas") ||
        r.contains("investing") || r.contains("inversión")) add("Economía")

    // ── Política ───────────────────────────────────────────────────────────
    if (r.contains("politics") || r.contains("política") || r.contains("politica") ||
        r.contains("political")) add("Política")

    // ── Sociología ─────────────────────────────────────────────────────────
    if (r.contains("sociology") || r.contains("sociolog") ||
        r.contains("social science") || r.contains("ciencias sociales") ||
        r.contains("anthropology") || r.contains("antropolog")) add("Sociología")

    // ── Mitología ──────────────────────────────────────────────────────────
    if (r.contains("mytholog") || r.contains("mitolog") ||
        r.contains("folklore") || r.contains("folclore") ||
        Regex("\bmyth\b").containsMatchIn(r) || Regex("\bmito\b").containsMatchIn(r)) add("Mitología")

    // ── Deportes ───────────────────────────────────────────────────────────
    if (r.contains("sport") || r.contains("deporte") || r.contains("sports") ||
        r.contains("fútbol") || r.contains("futbol") || r.contains("soccer") ||
        r.contains("basketball") || r.contains("athletics") || r.contains("fitness")) add("Deportes")

    // ── Salud y bienestar ──────────────────────────────────────────────────
    if (r.contains("health") || r.contains("wellness") || r.contains("salud") ||
        r.contains("nutrition") || r.contains("nutrición") || r.contains("medicina") ||
        r.contains("medicine")) add("Salud y bienestar")

    // ── Naturaleza ─────────────────────────────────────────────────────────
    // "animal" eliminado: falso positivo con "Animals, Mythical -- Fiction" (fantasía)
    if (r.contains("naturaleza") && !r.contains("fiction") ||
        r.contains("ecology") || r.contains("ecología") ||
        r.contains("environment") && !r.contains("fiction") ||
        r.contains("wildlife") ||
        r == "nature" || r == "natural history") add("Naturaleza")

    // ── Educación ──────────────────────────────────────────────────────────
    if (r.contains("education") || r.contains("educación") || r.contains("educacion") ||
        r.contains("teaching") || r.contains("pedagog")) add("Educación")

    // ── Periodismo ─────────────────────────────────────────────────────────
    if (r.contains("journalism") || r.contains("periodism") || r.contains("periodico") ||
        r.contains("reportage") || r.contains("reportaje") ||
        r.contains("nonfiction") || r.contains("non-fiction") ||
        r.contains("narrative nonfiction")) add("Periodismo")

    // ── Lingüística ────────────────────────────────────────────────────────
    if (r.contains("linguistic") || r.contains("lingüístic") || r.contains("linguistico") ||
        (r.contains("language") && !r.contains("fiction")) || r.contains("idiom")) add("Lingüística")

    // ── Viajes ─────────────────────────────────────────────────────────────
    if (r.contains("travel") || r.contains("viaje") || r.contains("travelogue")) add("Viajes")

    // ── Literatura clásica ─────────────────────────────────────────────────
    if (r.contains("classic") || r.contains("clásic") || r.contains("clasica") ||
        r.contains("clasico") || r.contains("world literature") ||
        r.contains("literatura universal")) add("Literatura clásica")

    // ── Poesía ─────────────────────────────────────────────────────────────
    if (r.contains("poetry") || r.contains("poesía") || r.contains("poesia") ||
        r.contains("poems")) add("Poesía")

    // ── Cómics ─────────────────────────────────────────────────────────────
    // Manga separado: japonés + manhwa/manhua (coreano/chino) → Manga
    // Cómics occidentales → Cómics y novela gráfica
    if (r.contains("manga") || r.contains("manhwa") || r.contains("manhua") ||
        r.contains("webtoon")) add("Manga")
    if ((r.contains("comic") && !r.contains("comicbook")) || r.contains("graphic novel") ||
        r.contains("novela gráfica") || r.contains("novela grafica") ||
        r.contains("bande dessinée") || r.contains("bd")) add("Cómics y novela gráfica")

    // ── Costumbrismo ───────────────────────────────────────────────────────
    if (r.contains("costumbris") || r.contains("realismo social") ||
        r.contains("slice of life") || r.contains("daily life")) add("Costumbrismo")

    // ── Infantil ───────────────────────────────────────────────────────────
    if (r.contains("children") || r.contains("infantil") ||
        r.contains("picture book") || r.contains("middle grade")) add("Infantil")

    // ── Juvenil ────────────────────────────────────────────────────────────
    // NOTA: "Juvenile Fiction" es una categoría genérica de GB/OL que NO indica género juvenil.
    // Solo señales explícitas de YA cuentan.
    if (r.contains("young adult") || r.contains("ya fiction") || r.contains("ya novel") ||
        r.contains("new adult") || r.contains("coming of age") ||
        r == "juvenile" || r == "juvenil" || r.contains("teen fiction")) add("Juvenil")

    // ── Religión y espiritualidad ──────────────────────────────────────────
    if (r.contains("religion") || r.contains("spiritual") ||
        r.contains("religión") || r.contains("religioso") ||
        r.contains("christian") || r.contains("cristiano") ||
        r.contains("islam") || r.contains("buddhism") || r.contains("budismo") ||
        r.contains("espiritualidad")) add("Religión y espiritualidad")

    // ── Arte ───────────────────────────────────────────────────────────────
    if (r.split(" ", ",", "/", "&").any {
        it.trim() in listOf("art", "arte", "arts", "fine art", "visual art",
                            "photography", "fotografía", "arquitectura",
                            "architecture", "diseño", "design") }) add("Arte")

    // ── Música ─────────────────────────────────────────────────────────────
    if (r.contains("music") || r.contains("música") || r.contains("musica")) add("Música")

    // Géneros de ficción genérica sin más contexto → no asignar
    if (found.isEmpty()) {
        if (r.contains("fiction") || r.contains("ficción") || r.contains("ficcion") ||
            r.contains("novela") || r.contains("novel")) return emptyList()
    }

    return found.distinct().take(2)
}



// Puente entre MainActivity.onNewIntent y el Composable LecturaMeterApp.
// Cuando llega un nuevo intent (p.ej. tap al widget), MainActivity dispara este callback
// y el Composable lee el nuevo intent.data para navegar.
object WidgetIntentBridge {
    @Volatile var onNewIntent: (() -> Unit)? = null
}

private fun refreshWidgetForBookIfSelected(context: Context, bookId: Long, clearCoverCache: Boolean = false) {
    val appContext = context.applicationContext
    if (com.lecturameter.widget.loadWidgetBook(appContext) != bookId) return
    if (clearCoverCache) com.lecturameter.widget.clearWidgetCoverCache(appContext)
    com.lecturameter.widget.requestBookWidgetUpdate(appContext)
}

private fun showBookInWidget(context: Context, bookId: Long) {
    val appContext = context.applicationContext
    com.lecturameter.widget.saveWidgetBook(appContext, bookId)
    com.lecturameter.widget.clearWidgetCoverCache(appContext)
    com.lecturameter.widget.requestBookWidgetUpdate(appContext)
}

private fun clearWidgetBookIfSelected(context: Context, bookId: Long) {
    val appContext = context.applicationContext
    if (com.lecturameter.widget.loadWidgetBook(appContext) != bookId) return
    com.lecturameter.widget.saveWidgetBook(appContext, -1L)
    com.lecturameter.widget.clearWidgetCoverCache(appContext)
    com.lecturameter.widget.requestBookWidgetUpdate(appContext)
}

class MainActivity : ComponentActivity() {
    private val vm: BooksViewModel by viewModels()

    // ISBN escaneado — se pasa a LecturaMeterApp como estado observable
    internal var pendingScannedIsbn = androidx.compose.runtime.mutableStateOf<String?>(null)
    // ISBN escaneado desde BookSearch — activa dialog directamente en BookSearchScreen
    internal var isbnFromScannerForBookSearch = androidx.compose.runtime.mutableStateOf<String?>(null)
    // Diálogo educativo de permiso de cámara
    internal var showCameraPermDialog = androidx.compose.runtime.mutableStateOf(false)
    // Callback a ejecutar si el permiso es concedido (qué scanner lanzar)
    internal var pendingCameraAction: (() -> Unit)? = null

    // Launcher de permiso de cámara con diálogo educativo previo
    val cameraPermLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) pendingCameraAction?.invoke()
        else android.widget.Toast.makeText(this, getString(R.string.msg_no_camera_permission_scan), android.widget.Toast.LENGTH_SHORT).show()
        pendingCameraAction = null
    }

    // Launcher para ScannerActivity — se registra antes de onCreate
    val scanIsbnLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val isbn = result.data?.getStringExtra("isbn") ?: return@registerForActivityResult
            if (isbnFromScannerForBookSearch.value == "__scanning__") {
                // Escaneo iniciado desde BookSearch → dialog directo en BookSearch
                isbnFromScannerForBookSearch.value = isbn
            } else {
                pendingScannedIsbn.value = isbn
            }
        } else {
            // Cancelado: limpiar marca de escaneo desde BookSearch
            if (isbnFromScannerForBookSearch.value == "__scanning__") {
                isbnFromScannerForBookSearch.value = null
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setAppLocale(lang: String) {
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplicar idioma ANTES de inflar cualquier UI
        val prefsEarly = getSharedPreferences("lecturameter", MODE_PRIVATE)
        setAppLocale(prefsEarly.getString("app_language", "es") ?: "es")

        super.onCreate(savedInstanceState)
        // Si la app arranca en frío desde el botón "Detener" de la notificación,
        // parar el servicio y mostrar encima de la lockscreen solo en ese caso.
        if (intent?.action == com.lecturameter.TimerService.ACTION_OPEN_SESSION_DIALOG &&
            intent.getBooleanExtra("stop_session", false)) {
            com.lecturameter.TimerService.stop(this, showEndNotification = false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
        }
        val prefs = getSharedPreferences("lecturameter", MODE_PRIVATE)
        vm.initContext(this)
        vm.load(prefs)
        // El permiso POST_NOTIFICATIONS se pide con diálogo educativo la primera vez
        // que el usuario intenta iniciar el cronómetro (en DetailScreen.startTimerWithPermCheck).
        setContent {
            Crossfade(
                targetState = vm.themeMode,
                animationSpec = tween(durationMillis = 350),
                label = "theme_crossfade"
            ) { currentThemeMode ->
            val theme = buildTheme(currentThemeMode)
            LecturaMeterTheme(theme) {
                // Primera apertura: selección de idioma
                if (!vm.languageChosen) {
                    LanguageSelectionScreen(theme = theme) { lang ->
                        // v1.0: commit() garantiza escritura en disco ANTES de recreate()
                        // apply() es async — recreate podría ejecutarse antes de que se guarde
                        vm.setLanguage(lang, prefs)
                        prefs.edit().putBoolean("language_chosen", true).commit()
                        recreate()
                    }
                } else {
                    LecturaMeterApp(vm, prefs, theme)
                    // Diálogo educativo de permiso de cámara
                    if (showCameraPermDialog.value) {
                        AlertDialog(
                            onDismissRequest = { showCameraPermDialog.value = false },
                            containerColor = theme.bgMid,
                            title = { Text(stringResource(R.string.txt_135a16f2), color = theme.textMain, fontWeight = FontWeight.Bold) },
                            text = { Text(stringResource(R.string.txt_79f555a5), color = theme.textMuted, fontSize = 13.sp) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showCameraPermDialog.value = false
                                    cameraPermLauncher.launch(android.Manifest.permission.CAMERA)
                                }) { Text(stringResource(R.string.txt_64b46771), color = Accent, fontWeight = FontWeight.Bold) }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showCameraPermDialog.value = false
                                    pendingCameraAction = null
                                }) { Text(stringResource(R.string.txt_847607d7), color = Red) }
                            }
                        )
                    }
                }
            }
            } // end Crossfade
        }

        // Backup automático en Drive según el intervalo elegido en Ajustes (default 2h).
        // También ejecuta el backup local en Descargas en la misma pasada.
        // Bug fix v21.15: NO ponemos NetworkType.CONNECTED como restricción — eso bloqueaba
        // TAMBIÉN el backup local (que no necesita red) cuando no había conexión. El backup
        // local debe ejecutarse siempre; solo la subida a Drive falla sin red y reintenta
        // sola (Result.retry()) cuando vuelva la conexión.
        val backupIntervalH = prefs.getInt("backup_interval_hours", 2).toLong()
        val driveWorkRequest = PeriodicWorkRequestBuilder<DriveBackupWorker>(backupIntervalH, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "lecturameter_drive_backup",
            ExistingPeriodicWorkPolicy.KEEP,  // KEEP: no resetea el timer en cada apertura
            driveWorkRequest
        )

        // Refresco periódico del widget según el intervalo de Ajustes (default 30 min)
        val widgetMins = prefs.getInt("widget_refresh_minutes", 30)
        val widgetWorkRequest = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(widgetMins.toLong(), TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "lecturameter_widget_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            widgetWorkRequest
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == com.lecturameter.TimerService.ACTION_OPEN_SESSION_DIALOG &&
            intent.getBooleanExtra("stop_session", false)) {
            // Parar el servicio para que desaparezca la notificación de Pausar/Detener
            com.lecturameter.TimerService.stop(this, showEndNotification = false)
            // Mostrar la app encima de la lockscreen solo para este intent concreto
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
        }
        // Notificar al Composable para que relea el intent y navegue al detalle
        WidgetIntentBridge.onNewIntent?.invoke()
    }

    override fun onResume() {
        super.onResume()
        // Refresh all Lecturameter home screen widgets when the app is opened.
        com.lecturameter.widget.requestBookWidgetUpdate(applicationContext)
        // Si el móvil estaba bloqueado al terminar la sesión, al desbloquear
        // onResume dispara el bridge para que LecturaMeterApp navegue al detail.
        WidgetIntentBridge.onNewIntent?.invoke()
    }

    override fun onDestroy() {
        com.lecturameter.utils.AppLogger.log("App destruida")
        super.onDestroy()
    }
}

@Composable
fun LecturaMeterTheme(theme: Theme, content: @Composable () -> Unit) {
    val cs = if (theme.isDark)
        darkColorScheme(background = theme.bgDark, surface = theme.bgMid, primary = Accent, onPrimary = Color.White, onBackground = theme.textMain, onSurface = theme.textMain)
    else
        lightColorScheme(background = theme.bgDark, surface = theme.bgMid, primary = Accent, onPrimary = Color.White, onBackground = theme.textMain, onSurface = theme.textMain)
    MaterialTheme(colorScheme = cs, content = content)
}

// ── Navigation ────────────────────────────────────────────────────────────────

sealed class Screen {
    object List : Screen(); object Add : Screen(); object BookSearch : Screen(); object Stats : Screen()
    object ImportExport : Screen(); object WrappedHistory : Screen(); object SessionHistory : Screen()
    object BookQuest : Screen(); object Settings : Screen()
    data class Detail(val id: Long, val highlightDate: String? = null) : Screen()
    data class AuthorBooks(val author: String) : Screen()
    data class Wrapped(val year: Int) : Screen()
    data class BulkReload(val type: String) : Screen()
    data class DailySessions(val date: String) : Screen()
}

private fun Screen.routeKey(): String = when (this) {
    is Screen.List -> "list"
    is Screen.Add -> "add"
    is Screen.BookSearch -> "book_search"
    is Screen.Stats -> "stats"
    is Screen.ImportExport -> "import_export"
    is Screen.WrappedHistory -> "wrapped_history"
    is Screen.SessionHistory -> "session_history"
    is Screen.BookQuest -> "bookquest"
    is Screen.Settings -> "settings"
    is Screen.Detail -> if (highlightDate != null) "detail:$id:$highlightDate" else "detail:$id"
    is Screen.AuthorBooks -> "author:${Uri.encode(author)}"
    is Screen.Wrapped -> "wrapped:$year"
    is Screen.BulkReload -> "bulk_reload:$type"
    is Screen.DailySessions -> "daily_sessions:$date"
}

private fun screenFromRoute(route: String): Screen? = when {
    route == "list" -> Screen.List
    route == "add" -> Screen.Add
    route == "book_search" -> Screen.BookSearch
    route == "stats" -> Screen.Stats
    route == "import_export" -> Screen.ImportExport
    route == "wrapped_history" -> Screen.WrappedHistory
    route == "session_history" -> Screen.SessionHistory
    route == "bookquest" -> Screen.BookQuest
    route == "settings" -> Screen.Settings
    route.startsWith("detail:") -> {
        val parts = route.substringAfter(':').split(":")
        val bookId = parts.getOrNull(0)?.toLongOrNull()
        val date = parts.getOrNull(1)
        bookId?.let { Screen.Detail(it, date) }
    }
    route.startsWith("author:") -> Screen.AuthorBooks(Uri.decode(route.substringAfter(':')))
    route.startsWith("wrapped:") -> route.substringAfter(':').toIntOrNull()?.let { Screen.Wrapped(it) }
    route.startsWith("bulk_reload:") -> Screen.BulkReload(route.substringAfter(':'))
    route.startsWith("daily_sessions:") -> Screen.DailySessions(route.substringAfter(':'))
    else -> null
}

private fun restoreBackStack(routes: List<String>): List<Screen> {
    val screens = routes.mapNotNull { screenFromRoute(it) }
    return when {
        screens.isEmpty() -> listOf(Screen.List)
        screens.first() is Screen.List -> screens
        else -> listOf(Screen.List) + screens
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturaMeterApp(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity

    // Lee el bookId del intent: o bien del extra "widget_book_id", o bien de la URI
    // "lecturameter://book/{id}" (Glance preserva la URI pero NO los extras).
    fun readBookIdFromIntent(): Long {
        val i = activity?.intent ?: return -1L
        // Primero la URI
        val uri = i.data
        if (uri != null && uri.scheme == "lecturameter" && uri.host == "book") {
            val seg = uri.lastPathSegment
            val id = seg?.toLongOrNull() ?: -1L
            if (id > 0L) return id
        }
        // Fallback al extra
        return i.getLongExtra("widget_book_id", -1L)
    }

    // Limpia el intent para que un cambio de configuración (rotación, tema oscuro...)
    // no vuelva a navegar al detalle. Sin esto, cada rotación reabriría el detail.
    fun consumeIntentBookId() {
        activity?.intent?.let { i ->
            i.data = null
            i.removeExtra("widget_book_id")
        }
    }

    val initialScreen: Screen = remember {
        val i = activity?.intent
        val timerPrefs = context.getSharedPreferences(com.lecturameter.TimerService.TIMER_PREFS, android.content.Context.MODE_PRIVATE)
        // Prioridad 1: intent del timer (sesión terminada desde notificación, arranque en frío)
        if (i?.action == com.lecturameter.TimerService.ACTION_OPEN_SESSION_DIALOG) {
            com.lecturameter.TimerService.cancelSessionEndNotification(context)
            val bookId = i.getLongExtra(com.lecturameter.TimerService.EXTRA_BOOK_ID, -1L)
            i.action = null  // consumir
            // Restaurar estado desde prefs si el proceso había muerto
            if (TimerStateHolder.seconds == 0L) {
                TimerStateHolder.seconds = timerPrefs.getLong("seconds", 0L)
            }
            TimerStateHolder.shouldOpenDialog = true
            if (TimerStateHolder.activeBookId <= 0L) TimerStateHolder.activeBookId = bookId
            if (bookId > 0L) Screen.Detail(bookId) else Screen.List
        // Prioridad 2: shouldOpenDialog en memoria (proceso vivo)
        } else if (com.lecturameter.TimerStateHolder.shouldOpenDialog &&
                   com.lecturameter.TimerStateHolder.activeBookId > 0L) {
            com.lecturameter.TimerService.cancelSessionEndNotification(context)
            Screen.Detail(com.lecturameter.TimerStateHolder.activeBookId)
        // Prioridad 2b: sesión pendiente en prefs (proceso muerto, sin intent)
        } else if (timerPrefs.getBoolean("pending", false) &&
                   timerPrefs.getLong("book_id", -1L) > 0L) {
            com.lecturameter.TimerService.cancelSessionEndNotification(context)
            val bookId = timerPrefs.getLong("book_id", -1L)
            // Restaurar a memoria
            TimerStateHolder.shouldOpenDialog = true
            TimerStateHolder.activeBookId = bookId
            TimerStateHolder.seconds = timerPrefs.getLong("seconds", 0L)
            Screen.Detail(bookId)
        // Prioridad 3: deep link del widget
        } else {
            val bookId = readBookIdFromIntent()
            if (bookId > 0L) {
                consumeIntentBookId()
                Screen.Detail(bookId)
            } else {
                Screen.List
            }
        }
    }

    val initialBackStack = remember(initialScreen) {
        if (initialScreen is Screen.List) listOf(Screen.List) else listOf(Screen.List, initialScreen)
    }
    var backStackRoutes by rememberSaveable {
        mutableStateOf(initialBackStack.map { it.routeKey() })
    }
    var backPressedOnce by remember { mutableStateOf(false) }
    val backStack = restoreBackStack(backStackRoutes)
    val screen = backStack.last()
    val saveableStateHolder = rememberSaveableStateHolder()

    fun navigateTo(destination: Screen) {
        backPressedOnce = false
        val destinationRoute = destination.routeKey()
        backStackRoutes = when {
            destination is Screen.List -> listOf(Screen.List.routeKey())
            backStackRoutes.lastOrNull() == destinationRoute -> backStackRoutes
            else -> backStackRoutes + destinationRoute
        }
    }

    fun goBack() {
        backPressedOnce = false
        if (backStackRoutes.size > 1) {
            backStackRoutes = backStackRoutes.dropLast(1)
        } else {
            (context as? android.app.Activity)?.finish()
        }
    }

    // Para detectar nuevos intents (tap al widget mientras la app está abierta) usamos
    // un ticker que se actualiza desde onNewIntent. La key del LaunchedEffect debe
    // cambiar cada vez que llega un intent nuevo, no en cada recomposición.
    val intentTrigger = remember { mutableStateOf(0) }
    DisposableEffect(activity) {
        activity ?: return@DisposableEffect onDispose { }
        // Compartimos el trigger con MainActivity vía un static holder simple
        com.lecturameter.WidgetIntentBridge.onNewIntent = {
            intentTrigger.value += 1
        }
        onDispose { com.lecturameter.WidgetIntentBridge.onNewIntent = null }
    }

    LaunchedEffect(intentTrigger.value) {
        val i = activity?.intent
        val timerPrefs = context.getSharedPreferences(com.lecturameter.TimerService.TIMER_PREFS, android.content.Context.MODE_PRIVATE)
        // Intent del timer recibido vía onNewIntent (app ya estaba en marcha)
        if (i?.action == com.lecturameter.TimerService.ACTION_OPEN_SESSION_DIALOG) {
            com.lecturameter.TimerService.cancelSessionEndNotification(context)
            val bookId = i.getLongExtra(com.lecturameter.TimerService.EXTRA_BOOK_ID, -1L)
            if (i.getBooleanExtra("stop_session", false)) {
                TimerStateHolder.running = false
                TimerStateHolder.paused = false
            }
            if (TimerStateHolder.seconds == 0L) TimerStateHolder.seconds = timerPrefs.getLong("seconds", 0L)
            TimerStateHolder.shouldOpenDialog = true
            if (TimerStateHolder.activeBookId <= 0L) TimerStateHolder.activeBookId = bookId
            if (bookId > 0L) navigateTo(Screen.Detail(bookId))
            i.action = null
            return@LaunchedEffect
        }
        // shouldOpenDialog en memoria
        if (com.lecturameter.TimerStateHolder.shouldOpenDialog &&
            com.lecturameter.TimerStateHolder.activeBookId > 0L) {
            com.lecturameter.TimerService.cancelSessionEndNotification(context)
            navigateTo(Screen.Detail(com.lecturameter.TimerStateHolder.activeBookId))
            return@LaunchedEffect
        }
        // Sesión pendiente en prefs (proceso muerto, onResume sin intent nuevo)
        if (timerPrefs.getBoolean("pending", false) &&
            timerPrefs.getLong("book_id", -1L) > 0L) {
            com.lecturameter.TimerService.cancelSessionEndNotification(context)
            val bookId = timerPrefs.getLong("book_id", -1L)
            TimerStateHolder.shouldOpenDialog = true
            TimerStateHolder.activeBookId = bookId
            TimerStateHolder.seconds = timerPrefs.getLong("seconds", 0L)
            navigateTo(Screen.Detail(bookId))
            return@LaunchedEffect
        }
        // Deep link del widget (solo si es un intent real, no la composición inicial)
        if (intentTrigger.value == 0) return@LaunchedEffect
        val bookId = readBookIdFromIntent()
        if (bookId > 0L) {
            consumeIntentBookId()
            navigateTo(Screen.Detail(bookId))
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    // ── Migración v13: rellenar startPage/endPage en sesiones antiguas ────────
    // Usamos derivedStateOf sobre vm.sessions para que se recalcule si cambian las sesiones
    // (p. ej. tras restaurar un backup). migrationDone se lee en cada recomposición.
    val sessionsNeedingMigration by remember {
        derivedStateOf {
            if (prefs.getBoolean("session_pages_migrated", false)) emptyList()
            else vm.sessions.filter { it.startPage == null || it.endPage == null }
                .sortedWith(compareBy({ it.bookId }, { it.date }))
        }
    }
    var migrationIndex by remember { mutableStateOf(0) }
    var prevConfirmedEndByBook by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var showMigrationDialog by remember { mutableStateOf(false) }
    // Sincronizar showMigrationDialog con sessionsNeedingMigration reactivamente
    LaunchedEffect(sessionsNeedingMigration.size) {
        if (sessionsNeedingMigration.isNotEmpty() && !prefs.getBoolean("session_pages_migrated", false)) {
            migrationIndex = 0
            prevConfirmedEndByBook = emptyMap()
            showMigrationDialog = true
        }
    }

    if (showMigrationDialog && migrationIndex < sessionsNeedingMigration.size) {
        val session = sessionsNeedingMigration[migrationIndex]
        val book = remember(session.bookId) { vm.books.find { it.id == session.bookId } }
        val sessionNumber = migrationIndex + 1
        val totalSessions = sessionsNeedingMigration.size

        // Inferencia acumulativa: sumar páginas de sesiones anteriores del mismo libro
        val previousSessions = sessionsNeedingMigration.take(migrationIndex)
            .filter { it.bookId == session.bookId }
        val inferredStart = previousSessions.sumOf { it.pages } + 1
        val inferredEnd = inferredStart + session.pages - 1

        var startText by remember(migrationIndex) { mutableStateOf(inferredStart.toString()) }
        var endText   by remember(migrationIndex) { mutableStateOf(inferredEnd.toString()) }
        var migError  by remember(migrationIndex) { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { /* no se puede cerrar sin completar o omitir */ },
            containerColor = (if (vm.isDarkMode) Color(0xFF1E2235) else Color(0xFFF5F7FF)),
            title = {
                Column {
                    Text(stringResource(R.string.txt_613a3a29), color = if (vm.isDarkMode) Color.White else Color(0xFF1E2235), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.migration_session_counter, sessionNumber, totalSessions), color = Color(0xFF7B8EC8), fontSize = 12.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (book != null) {
                        Text(book.title, color = if (vm.isDarkMode) Color(0xFFBBCCFF) else Color(0xFF3344AA), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(stringResource(R.string.session_header_timer, sessionNumber, fmtDate(session.date)), color = Color(0xFF7B8EC8), fontSize = 12.sp)
                    Text(stringResource(R.string.timed_session_pages, session.pages), color = Color(0xFF7B8EC8), fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.txt_17724589), color = Color(0xFF7B8EC8), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                            OutlinedTextField(
                                value = startText,
                                onValueChange = { startText = it.filter { c -> c.isDigit() }; migError = "" },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = if (vm.isDarkMode) Color.White else Color(0xFF1E2235),
                                    unfocusedTextColor = if (vm.isDarkMode) Color.White else Color(0xFF1E2235),
                                    focusedBorderColor = Color(0xFF6366F1),
                                    unfocusedBorderColor = Color(0xFF3D4166)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.txt_d532077e), color = Color(0xFF7B8EC8), fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                            OutlinedTextField(
                                value = endText,
                                onValueChange = { endText = it.filter { c -> c.isDigit() }; migError = "" },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = if (vm.isDarkMode) Color.White else Color(0xFF1E2235),
                                    unfocusedTextColor = if (vm.isDarkMode) Color.White else Color(0xFF1E2235),
                                    focusedBorderColor = Color(0xFF6366F1),
                                    unfocusedBorderColor = Color(0xFF3D4166)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    if (migError.isNotBlank()) {
                        Text(migError, color = Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val s = startText.toIntOrNull()
                        val e = endText.toIntOrNull()
                        val lastFuncMig = book?.lastFunctionalPage
                        when {
                            s == null || s < 1 -> { migError = context.getString(R.string.err_page_start_min1); return@Button }
                            e == null || (book != null && e > book.pages) -> { migError = context.getString(R.string.err_page_end_over_total); return@Button }
                            lastFuncMig != null && e > lastFuncMig -> { migError = context.getString(R.string.err_page_end_over_func, lastFuncMig); return@Button }
                            e < s  -> { migError = context.getString(R.string.err_page_end_lt_start); return@Button }
                        }
                        // Recalcular pages a partir del rango confirmado.
                        // prevConfirmedEndByBook rastrea el último endPage confirmado por libro.
                        val prevConfirmedEnd = prevConfirmedEndByBook[session.bookId]
                        val recalcPages = if (prevConfirmedEnd != null && s == prevConfirmedEnd) e!! - s!!
                                          else e!! - s!! + 1
                        vm.updateSession(session.copy(startPage = s, endPage = e, pages = recalcPages), prefs)
                        prevConfirmedEndByBook = prevConfirmedEndByBook + (session.bookId to e!!)
                        if (migrationIndex + 1 >= totalSessions) {
                            prefs.edit().putBoolean("session_pages_migrated", true).apply()
                            showMigrationDialog = false
                        } else {
                            migrationIndex++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(10.dp)
                ) { Text(stringResource(R.string.txt_8487931b)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("session_pages_migrated", true).apply()
                    showMigrationDialog = false
                }) { Text(stringResource(R.string.txt_babead9b), color = Red) }
            }
        )
    }

    BackHandler(enabled = drawerState.isOpen) {
        drawerScope.launch { drawerState.close() }
    }

    // Doble pulsación para salir desde la pantalla principal
    BackHandler(enabled = drawerState.isClosed && screen is Screen.List) {
        if (backPressedOnce) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, context.getString(R.string.msg_back_again_exit), Toast.LENGTH_SHORT).show()
            // Resetear tras 2 segundos
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.postDelayed({ backPressedOnce = false }, 2000)
        }
    }

    // Volver atrás desde cualquier otra pantalla
    BackHandler(enabled = drawerState.isClosed && screen !is Screen.List) {
        goBack()
    }

    // Bug 2 fix: BookQuest se renderiza FUERA del ModalNavigationDrawer
    // para que el drawer no coexista nunca con el juego
    if (screen is Screen.BookQuest) {
        BookQuestScreen(vm = vm, onExit = { goBack() })
    } else {

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = vm.tutorialCompleted,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = theme.bgDark,
                drawerContentColor = theme.textMain,
                modifier = Modifier.fillMaxWidth(0.82f)
            ) {
                SessionHistoryScreen(
                    vm = vm,
                    theme = theme,
                    onClose = { drawerScope.launch { drawerState.close() } },
                    onDetail = { id -> drawerScope.launch { drawerState.close() }; navigateTo(Screen.Detail(id)) }
                )
            }
        }
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.bgDark, theme.bgMid, theme.bgDark)))) {
            when (val s = screen) {
                is Screen.Detail      -> DetailScreen(vm, prefs, theme, s.id, highlightDate = s.highlightDate, onBack = { goBack() }, onAuthorClick = { navigateTo(Screen.AuthorBooks(it)) })
                else -> saveableStateHolder.SaveableStateProvider(screen.routeKey()) {
                    when (val s2 = screen) {
                        is Screen.List          -> {
                            val mainActivity = context as? MainActivity
                            ListScreen(vm, prefs, theme,
                                onAdd = { navigateTo(Screen.Add) },
                                onSearch = { navigateTo(Screen.BookSearch) },
                                onStats = { navigateTo(Screen.Stats) },
                                onWrappedHistory = { navigateTo(Screen.WrappedHistory) },
                                onWrapped = { y -> navigateTo(Screen.Wrapped(y)) },
                                onImportExport = { navigateTo(Screen.ImportExport) },
                                onDetail = { navigateTo(Screen.Detail(it)) },
                                onOpenHistory = { drawerScope.launch { drawerState.open() } },
                                onSettings = { navigateTo(Screen.Settings) },
                                onScanIsbnSearch = {
                                    if (mainActivity != null) {
                                        val camPerm = android.Manifest.permission.CAMERA
                                        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                            mainActivity, camPerm
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            mainActivity.pendingScannedIsbn.value = null
                                            mainActivity.scanIsbnLauncher.launch(
                                                android.content.Intent(mainActivity, ScannerActivity::class.java)
                                            )
                                            // Cuando el resultado llegue con isbn != null, navegamos a BookSearch
                                        } else {
                                            mainActivity.pendingCameraAction = {
                                                mainActivity.pendingScannedIsbn.value = null
                                                mainActivity.scanIsbnLauncher.launch(android.content.Intent(mainActivity, ScannerActivity::class.java))
                                            }
                                            mainActivity.showCameraPermDialog.value = true
                                        }
                                    }
                                },
                                onNavigateToBookSearch = { navigateTo(Screen.BookSearch) },
                                onAddWithIsbn = { isbn ->
                                    // Navegar a AddScreen con ISBN pre-cargado
                                    if (mainActivity != null) {
                                        mainActivity.pendingScannedIsbn.value = isbn
                                    }
                                    navigateTo(Screen.Add)
                                },
                                onEasterEgg = {
                                    drawerScope.launch {
                                        drawerState.close()
                                        navigateTo(Screen.BookQuest)
                                    }
                                }
                            )
                        }
                        is Screen.Add           -> {
                            val mainActivity = context as? MainActivity
                            val scannedIsbn = mainActivity?.pendingScannedIsbn?.value
                            AddScreen(
                                vm, prefs, theme,
                                onBack = { mainActivity?.pendingScannedIsbn?.value = null; goBack() },
                                externalIsbn = scannedIsbn,
                                onClearExternalIsbn = { mainActivity?.pendingScannedIsbn?.value = null },
                                onScanIsbn = {
                                    if (mainActivity != null) {
                                        val camPerm = android.Manifest.permission.CAMERA
                                        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                            mainActivity, camPerm
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            mainActivity.scanIsbnLauncher.launch(
                                                android.content.Intent(mainActivity, ScannerActivity::class.java)
                                            )
                                        } else {
                                            mainActivity.pendingCameraAction = {
                                                mainActivity.scanIsbnLauncher.launch(android.content.Intent(mainActivity, ScannerActivity::class.java))
                                            }
                                            mainActivity.showCameraPermDialog.value = true
                                        }
                                    }
                                }
                            )
                        }
                        is Screen.BookSearch    -> {
                            val bsMain = context as? MainActivity
                            val isbnForSearch = bsMain?.pendingScannedIsbn?.value ?: ""
                            val isbnFromScanner = bsMain?.isbnFromScannerForBookSearch?.value
                            BookSearchScreen(
                                vm, prefs, theme,
                                onBack = { bsMain?.pendingScannedIsbn?.value = null; bsMain?.isbnFromScannerForBookSearch?.value = null; goBack() },
                                initialQuery = isbnForSearch,
                                isbnFromScanner = isbnFromScanner,
                                onClearIsbnFromScanner = { bsMain?.isbnFromScannerForBookSearch?.value = null },
                                onAddWithIsbn = { isbn ->
                                    bsMain?.pendingScannedIsbn?.value = isbn
                                    navigateTo(Screen.Add)
                                },
                                onScanIsbn = {
                                    if (bsMain != null) {
                                        val camPerm = android.Manifest.permission.CAMERA
                                        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                            bsMain, camPerm
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (granted) {
                                            // Marca que el próximo resultado viene de BookSearch
                                            bsMain.isbnFromScannerForBookSearch.value = "__scanning__"
                                            bsMain.scanIsbnLauncher.launch(
                                                android.content.Intent(bsMain, ScannerActivity::class.java)
                                            )
                                        } else {
                                            bsMain.pendingCameraAction = {
                                                bsMain.isbnFromScannerForBookSearch.value = "__scanning__"
                                                bsMain.scanIsbnLauncher.launch(android.content.Intent(bsMain, ScannerActivity::class.java))
                                            }
                                            bsMain.showCameraPermDialog.value = true
                                        }
                                    }
                                }
                            )
                        }
                        is Screen.Stats         -> StatsScreen(vm, prefs, theme, onBack = { goBack() }, onWrapped = { y -> navigateTo(Screen.Wrapped(y)) }, onWrappedHistory = { navigateTo(Screen.WrappedHistory) }, onDetail = { navigateTo(Screen.Detail(it)) }, onDetailWithDate = { bookId, date -> navigateTo(Screen.Detail(bookId, date)) }, onDailySessions = { date -> navigateTo(Screen.DailySessions(date)) })
                        is Screen.ImportExport  -> ImportExportScreen(vm, prefs, theme, onBack = { goBack() })
                        is Screen.Wrapped       -> WrappedScreen(vm, prefs, theme, s2.year, onBack = { goBack() })
                        is Screen.WrappedHistory -> WrappedHistoryScreen(vm, theme, onBack = { goBack() }, onOpen = { y -> navigateTo(Screen.Wrapped(y)) })
                        is Screen.AuthorBooks   -> AuthorBooksScreen(vm, prefs, theme, s2.author, onBack = { goBack() }, onDetail = { navigateTo(Screen.Detail(it)) })
                        is Screen.Settings      -> SettingsScreen(vm, prefs, theme, onBack = { goBack() }, onBulkReload = { type -> navigateTo(Screen.BulkReload(type)) }, onResetTutorial = { navigateTo(Screen.List) })
                        is Screen.SessionHistory -> { /* handled by drawer */ }
                        is Screen.Detail        -> { /* handled above */ }
                        is Screen.BookQuest   -> { /* handled above, outside drawer */ }
                        is Screen.BulkReload  -> BulkReloadScreen(vm, prefs, theme, s2.type, onBack = { goBack() })
                        is Screen.DailySessions -> DailySessionsScreen(
                            date = s2.date,
                            sessions = vm.sessions.filter { it.date == s2.date },
                            books = vm.books,
                            theme = theme,
                            onNavigateToDetail = { bookId, date -> navigateTo(Screen.Detail(bookId, date)) },
                            onBack = { goBack() }
                        )
                    }
                }
            }
        }
    }
} // else de BookQuest
}

// ── ListScreen ────────────────────────────────────────────────────────────────
//
// Estilo Goodreads: pestañas por estante + búsqueda + ordenación.
// Cada pestaña muestra sólo los libros de ese estado → no hay listas infinitas.

// Orden canónico de estantes (igual al que muestra la barra de pestañas)
private val SHELF_ORDER = listOf(
    BookStatus.READING,
    BookStatus.FINISHED,
    BookStatus.REREADING,
    BookStatus.PENDING,
    BookStatus.DROPPED
)

fun normalizeSearchText(text: String): String {
    val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
    return normalized.replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
        .replace(" ", "").lowercase()
}

fun fuzzyMatch(query: String, target: String): Boolean {
    val nq = normalizeSearchText(query)
    val nt = normalizeSearchText(target)
    return nt.contains(nq)
}

// ── Selección de idioma inicial ───────────────────────────────────────────────

@Composable
fun LanguageSelectionScreen(theme: Theme, onLanguageSelected: (String) -> Unit) {
    Box(
        Modifier.fillMaxSize().background(theme.bgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("📚", fontSize = 64.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.txt_4d8b0a6f),
                color = theme.textMain,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.txt_18fb3478),
                color = theme.textMuted,
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { onLanguageSelected("es") },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text(stringResource(R.string.txt_95b01315), fontSize = 15.sp, color = androidx.compose.ui.graphics.Color.White) }
                Button(
                    onClick = { onLanguageSelected("en") },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = theme.bgMid),
                    border = BorderStroke(1.dp, theme.border)
                ) { Text(stringResource(R.string.txt_f759fe35), fontSize = 15.sp, color = theme.textMain) }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.txt_82056f1f),
                color = theme.textDim,
                fontSize = 12.sp
            )
        }
    }
}

// ── Tutorial ──────────────────────────────────────────────────────────────────

// ── Mockups visuales para el tutorial (réplicas SIN lógica real) ──────────────
// Reusan los mismos tokens de color/forma que los componentes reales (BookCover,
// DataChip, DrawerStatChipH, StatBox) para que se vean "como son realmente",
// pero no están conectados a ViewModel ni tienen onClick funcionales.

@Composable
fun TutorialBookCardVisual(theme: Theme) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.86f),
        shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)
    ) {
        Row(Modifier.padding(12.dp)) {
            Box(modifier = Modifier.size(70.dp, (70 * 1.42f).dp)) {
                BookCover(null, "TEST", size = 70)
                Box(
                    Modifier.size(22.dp).offset(x = (-5).dp, y = 5.dp).clip(CircleShape)
                        .background(Sky).border(2.dp, theme.surface, CircleShape).align(Alignment.BottomStart),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                Box(
                    Modifier.size(22.dp).offset(x = 5.dp, y = 5.dp).clip(CircleShape)
                        .background(Accent).border(2.dp, theme.surface, CircleShape).align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("TEST", color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(Red.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Delete, null, tint = Red.copy(alpha = 0.7f), modifier = Modifier.size(11.dp)) }
                }
                Text("Lecturameter", color = theme.textMuted, fontSize = 12.sp)
                Text(stringResource(R.string.tutorial_mock_genres), color = Accent.copy(alpha = 0.8f), fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.tutorial_mock_pages), color = theme.textMuted, fontSize = 12.sp)
                    Text(stringResource(R.string.tutorial_mock_days), color = theme.textMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text("💬 ??", color = theme.textDim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun TutorialStatsPillsVisual(theme: Theme) {
    Row(Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatBox(stringResource(R.string.tutorial_mock_days_pill), stringResource(R.string.pill_dias_leyendo), Modifier.weight(1f), theme)
        StatBox("3h 30m", stringResource(R.string.stat_total_time), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
        StatBox("136p", stringResource(R.string.pill_pags_leidas), Modifier.weight(1f), theme, highlight = true, highlightColor = Color(0xFF34D399))
        StatBox("20%", stringResource(R.string.pill_porcentaje_leido), Modifier.weight(1f), theme)
        StatBox("17.0", stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, highlight = true, highlightColor = Green)
    }
}

@Composable
fun TutorialHistoryRowVisual(theme: Theme) {
    Column(Modifier.fillMaxWidth(0.88f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Fila de libro colapsable
        Surface(
            shape = RoundedCornerShape(14.dp), color = theme.surface, border = BorderStroke(1.dp, Accent.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Accent, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                    Text("🇪🇸", fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp))
                    Column(Modifier.weight(1f)) {
                        Text("TEST", color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Lecturameter", color = theme.textDim, fontSize = 11.sp)
                    }
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(Red.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Delete, null, tint = Red.copy(alpha = 0.7f), modifier = Modifier.size(11.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DrawerStatChipH(stringResource(R.string.tutorial_mock_ses_chip), Accent, Modifier.weight(1f))
                    DrawerStatChipH("⏱ 28m", Sky, Modifier.weight(1f))
                    DrawerStatChipH(stringResource(R.string.tutorial_mock_pages_chip), Green, Modifier.weight(1f))
                }
            }
        }
        // Sesión expandida dentro del libro
        Surface(shape = RoundedCornerShape(10.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#1", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tutorial_mock_date), color = theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Edit, null, tint = Accent.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Default.Delete, null, tint = Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DataChip("📄 25p", Accent.copy(alpha = 0.15f), Accent, Modifier.weight(1f))
                    DataChip("⏱ 28m", Sky.copy(alpha = 0.15f), Sky, Modifier.weight(1f))
                    DataChip("⚡ 0.9 p/m", Green.copy(alpha = 0.15f), Green, Modifier.weight(1f))
                    DataChip("📖 12-36", Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun TutorialWidgetVisual(theme: Theme) {
    // v1.0: captura real del widget según idioma de la app
    val ctx = LocalContext.current
    val lang = ctx.getSharedPreferences("lecturameter", android.content.Context.MODE_PRIVATE)
        .getString("app_language", "es") ?: "es"
    val resId = if (lang == "en") R.drawable.tutorial_widget_en else R.drawable.tutorial_widget_es
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = resId),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .clip(RoundedCornerShape(16.dp))
    )
}

@Composable
fun TutorialDonationsVisual(theme: Theme) {
    Column(Modifier.fillMaxWidth(0.78f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = Gold.copy(alpha = 0.10f), border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⭐", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text("Play Store", color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF29ABE0).copy(alpha = 0.10f), border = BorderStroke(1.dp, Color(0xFF29ABE0).copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("☕", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text("Ko-fi", color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF0070BA).copy(alpha = 0.10f), border = BorderStroke(1.dp, Color(0xFF0070BA).copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💙", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text("PayPal", color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

data class TutorialPage(val icon: String, val title: String, val description: String, val visual: (@Composable () -> Unit)? = null)

@Composable
fun TutorialPageContent(page: TutorialPage, theme: Theme) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        if (page.visual != null) {
            page.visual.invoke()
        } else {
            Text(page.icon, fontSize = 64.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text(page.title, color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(page.description, color = theme.textMuted, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TutorialSlideshow(theme: Theme, onComplete: () -> Unit, onSkip: () -> Unit) {
    val pages = listOf(
        TutorialPage("📚", stringResource(R.string.tut_p1_title), stringResource(R.string.tut_p1_desc)),
        TutorialPage("➕", stringResource(R.string.tut_p2_title), stringResource(R.string.tut_p2_desc)),
        TutorialPage("📤", stringResource(R.string.tut_p2b_title), stringResource(R.string.tut_p2b_desc)),
        TutorialPage("🔍", stringResource(R.string.txt_0b059290), stringResource(R.string.tut_search_desc)),
        TutorialPage("🃏", stringResource(R.string.tut_p3_title), stringResource(R.string.tut_card_desc), visual = { TutorialBookCardVisual(theme) }),
        TutorialPage("⏱️", stringResource(R.string.tut_p4_title), stringResource(R.string.tut_p4_desc)),
        TutorialPage("📊", stringResource(R.string.tut_p5_title), stringResource(R.string.tut_p5_desc)),
        TutorialPage("🗓️", stringResource(R.string.tut_p6_title), stringResource(R.string.tut_p6_desc)),
        TutorialPage("🏷️", stringResource(R.string.tut_stat_pills_title), stringResource(R.string.tut_pills_desc), visual = { TutorialStatsPillsVisual(theme) }),
        TutorialPage("📅", stringResource(R.string.tut_p7_title), stringResource(R.string.tut_p7_desc)),
        TutorialPage("🌐", stringResource(R.string.tut_p8_title), stringResource(R.string.tut_p8_desc)),
        TutorialPage("🎨", stringResource(R.string.tut_p9_title), stringResource(R.string.tut_p9_desc)),
        TutorialPage("📜", stringResource(R.string.tut_history_row_title), stringResource(R.string.tut_history_row_desc), visual = { TutorialHistoryRowVisual(theme) }),
        TutorialPage("🛠️", stringResource(R.string.tut_p10_title), stringResource(R.string.tut_p10_desc)),
        TutorialPage("📨", stringResource(R.string.tut_p11_title), stringResource(R.string.tut_p11_desc)),
        TutorialPage("🧩", stringResource(R.string.tut_widget_title), stringResource(R.string.tut_widget_desc), visual = { TutorialWidgetVisual(theme) }),
        TutorialPage("🔋", stringResource(R.string.tut_p12_title), stringResource(R.string.tut_p12_desc)),
        TutorialPage("💾", stringResource(R.string.tut_backup_title), stringResource(R.string.tut_backup_desc)),
        TutorialPage("", stringResource(R.string.tut_donations_title), stringResource(R.string.tut_donations_desc), visual = { TutorialDonationsVisual(theme) })
    )
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    var showSkipDialog by remember { mutableStateOf(false) }

    if (showSkipDialog) {
        AlertDialog(
            onDismissRequest = { showSkipDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_145bed95), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_96b976d8), color = theme.textMuted) },
            confirmButton = { TextButton(onClick = { onSkip(); showSkipDialog = false }) { Text(stringResource(R.string.txt_a6e39241), color = Red) } },
            dismissButton = { TextButton(onClick = { showSkipDialog = false }) { Text(stringResource(R.string.txt_bd23eb60), color = Accent) } }
        )
    }

    Box(Modifier.fillMaxSize().background(theme.bgDark)) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page -> TutorialPageContent(pages[page], theme) }

            // Indicador de página: número + fila de puntos (estilo dot-pagination)
            Text(
                "${pagerState.currentPage + 1} / ${pages.size}",
                color = theme.textDim, fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier
                            .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == pagerState.currentPage) Accent else theme.border)
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // Back button — ancho fijo para que Skip no se desplace al aparecer
                Box(Modifier.width(96.dp)) {
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.5.dp, theme.border),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = theme.textMuted),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) { Text(stringResource(R.string.txt_c673411e), fontSize = 14.sp, maxLines = 1) }
                    }
                }

                // Skip — pill outline con color Accent
                OutlinedButton(
                    onClick = { showSkipDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.5.dp, Accent.copy(alpha = 0.6f)),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(stringResource(R.string.txt_a6e39241), fontSize = 14.sp)
                }

                // Next / Begin — filled pill, color Accent
                if (pagerState.currentPage < pages.size - 1) {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        shape = RoundedCornerShape(999.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Accent),
                        modifier = Modifier.height(44.dp)
                    ) { Text(stringResource(R.string.txt_eccc5922), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                } else {
                    Button(
                        onClick = onComplete,
                        shape = RoundedCornerShape(999.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Accent),
                        modifier = Modifier.height(44.dp)
                    ) { Text(stringResource(R.string.txt_d4d1809c), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun ListScreen(
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    theme: Theme,
    onAdd: () -> Unit,
    onSearch: () -> Unit,
    onStats: () -> Unit,
    onWrappedHistory: () -> Unit,
    onWrapped: (Int) -> Unit = {},
    onImportExport: () -> Unit,
    onDetail: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onSettings: () -> Unit = {},
    onScanIsbnSearch: () -> Unit = {},
    onNavigateToBookSearch: () -> Unit = {},
    onAddWithIsbn: (String) -> Unit = {},
    onEasterEgg: () -> Unit = {}
) {
    var searchQuery by rememberSaveable { mutableStateOf(vm.savedSearchQuery) }
    var sortOrderName by rememberSaveable { mutableStateOf(vm.savedSortOrder.name) }
    val sortOrder = SortOrder.entries.firstOrNull { it.name == sortOrderName } ?: SortOrder.DATE_DESC
    var showSortMenu by remember { mutableStateOf(false) }
    var showIsbnScanDialog by remember { mutableStateOf(false) }
    var scannedIsbnForDialog by remember { mutableStateOf("") }

    // Detectar ISBN escaneado y mostrar dialog de elección
    val listMainRef = androidx.compose.ui.platform.LocalContext.current as? MainActivity
    val listScannedIsbn = listMainRef?.pendingScannedIsbn?.value
    LaunchedEffect(listScannedIsbn) {
        if (!listScannedIsbn.isNullOrBlank()) {
            scannedIsbnForDialog = listScannedIsbn
            showIsbnScanDialog = true
        }
    }
    // Which tab is active: index into SHELF_ORDER
    var activeTab by rememberSaveable { mutableStateOf(0) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val listScope = rememberCoroutineScope()
    LaunchedEffect(searchQuery) { vm.savedSearchQuery = searchQuery }
    LaunchedEffect(sortOrder) {
        vm.savedSortOrder = sortOrder
        prefs.edit().putString("sort_order", sortOrder.name).apply()
        listScope.launch { listState.animateScrollToItem(0) }
    }
    LaunchedEffect(activeTab) { listScope.launch { listState.animateScrollToItem(0) } }

    // Compute per-shelf book lists (filtered + sorted) — with fuzzy/accent-insensitive search
    val allFiltered = if (searchQuery.isBlank()) vm.books
        else vm.books.filter {
            fuzzyMatch(searchQuery, it.title) || fuzzyMatch(searchQuery, it.author)
        }
    val shelves: Map<BookStatus, List<Book>> = run {
        val widgetBookId = com.lecturameter.widget.loadWidgetBook(LocalContext.current)
        SHELF_ORDER.associateWith { status ->
            val books = when (status) {
                BookStatus.REREADING -> allFiltered.filter { it.status == BookStatus.REREADING || it.isRereading }
                BookStatus.FINISHED  -> allFiltered.filter { it.status == BookStatus.FINISHED }
                else -> allFiltered.filter { it.status == status }
            }
            val sorted = books.applySort(sortOrder)
            // El libro del widget va siempre al inicio de su lista
            if (widgetBookId > 0L) {
                val widgetBook = sorted.firstOrNull { it.id == widgetBookId }
                if (widgetBook != null) listOf(widgetBook) + sorted.filter { it.id != widgetBookId }
                else sorted
            } else sorted
        }
    }

    // Conteo sin doble conteo: FINISHED cuenta los FINISHED puros, REREADING los que relean
    val shelfCounts: Map<BookStatus, Int> = SHELF_ORDER.associateWith { status ->
        when (status) {
            BookStatus.FINISHED  -> allFiltered.count { it.status == BookStatus.FINISHED }
            BookStatus.REREADING -> allFiltered.count { it.status == BookStatus.REREADING || it.isRereading }
            else -> allFiltered.count { it.status == status }
        }
    }

    // Tutorial overlay — se muestra encima de todo mientras no esté completado
    if (!vm.tutorialCompleted) {
        TutorialSlideshow(
            theme = theme,
            onComplete = { vm.completeTutorial(prefs) },
            onSkip    = { vm.completeTutorial(prefs) }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {

        // ── Header ──────────────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(theme.bgDark, theme.bgDark))
                )
                .padding(horizontal = 16.dp)
        ) {
            // ── App title ────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 28.dp, bottom = 2.dp)
            ) {
                IconButton(onClick = onOpenHistory, modifier = Modifier.size(36.dp).offset(x = (-7).dp)) {
                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.txt_beea2815), tint = Accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(6.dp))
                // Easter egg: 4 toques → BookQuest
                val ldContext  = LocalContext.current
                var ldTapCount by remember { mutableStateOf(0) }
                var ldLastTap  by remember { mutableStateOf(0L) }
                var ldPrevTap  by remember { mutableStateOf(0L) }
                var ldToast: Toast? by remember { mutableStateOf(null) }
                // Bug 1 fix: cancelar el toast si el composable se desmonta
                DisposableEffect(Unit) { onDispose { ldToast?.cancel() } }
                Text(
                    stringResource(R.string.txt_4d8b0a6f),
                    color = theme.textMain,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        val now = System.currentTimeMillis()
                        val interval = now - ldLastTap
                        // Si el tap es demasiado rápido (< 150ms), cancelar y avisar
                        if (ldLastTap > 0L && interval < 150L) {
                            ldTapCount = 0
                            ldPrevTap = 0L
                            ldLastTap = now
                            ldToast?.cancel()
                            val msg = if ((ldContext.resources.configuration.locales[0].language) == "en")
                                "Slower, please!" else "¡Más despacio, por favor!"
                            ldToast = Toast.makeText(ldContext, msg, Toast.LENGTH_SHORT)
                            ldToast?.show()
                            return@clickable
                        }
                        if (now - ldLastTap > 5_000L) ldTapCount = 0
                        ldPrevTap = ldLastTap
                        ldTapCount++
                        ldLastTap = now
                        when {
                            ldTapCount < 4 -> {
                                ldToast?.cancel()
                                ldToast = Toast.makeText(
                                    ldContext,
                                    ldContext.getString(R.string.msg_easter_egg_tap_more, 4 - ldTapCount),
                                    Toast.LENGTH_SHORT
                                )
                                ldToast?.show()
                            }
                            else -> {
                                // Bug 1 fix: cancelar el último toast antes de navegar
                                ldToast?.cancel()
                                ldToast = null
                                ldTapCount = 0
                                onEasterEgg()
                            }
                        }
                    }
                )
            }
            // ── Icons row ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📖", fontSize = 16.sp)
                Text(
                    stringResource(R.string.label_books_total, vm.books.size),
                    color = theme.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var showThemeMenu by remember { mutableStateOf(false) }
                    val context = LocalContext.current
                    Box {
                        IconButton(onClick = { showThemeMenu = true }, modifier = Modifier.size(38.dp)) {
                            Text(
                                when (vm.themeMode) {
                                    ThemeMode.LIGHT  -> "☀️"
                                    ThemeMode.DARK   -> "🌙"
                                    ThemeMode.AURORA -> "🌌"
                                    ThemeMode.AMOLED -> "⬛"
                                },
                                fontSize = 16.sp
                            )
                        }
                        DropdownMenu(expanded = showThemeMenu, onDismissRequest = { showThemeMenu = false }) {
                            listOf(
                                ThemeMode.LIGHT  to stringResource(R.string.theme_light),
                                ThemeMode.DARK   to stringResource(R.string.theme_dark),
                                ThemeMode.AURORA to stringResource(R.string.theme_aurora),
                                ThemeMode.AMOLED to stringResource(R.string.theme_oled)
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (vm.themeMode == mode) Accent else theme.textMain, fontWeight = if (vm.themeMode == mode) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { vm.setThemeMode(mode, prefs, context); showThemeMenu = false }
                                )
                            }
                        }
                    }
                    // v21.41: eliminado longPress simulate wrapped (pendiente rediseño desde MD)
                    IconButton(onClick = { onWrappedHistory() }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = "Wrapped", tint = Accent, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onStats, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.BarChart, contentDescription = "Statistics", tint = Accent, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onImportExport, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.SwapVert, contentDescription = "Import/Export", tint = Accent, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onSearch, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.txt_113f7428), tint = Accent, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onSettings, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Accent, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.weight(1.2f))
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("+", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            }
            // ── Subtitle ─────────────────────────────────────────────────────
            Text(
                stringResource(R.string.txt_e860710c),
                color = theme.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.txt_84f524c0), color = theme.textDim, fontSize = 13.sp) },
                leadingIcon  = { Icon(Icons.Default.Search, null, tint = theme.textDim, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = theme.textDim, modifier = Modifier.size(18.dp))
                            }
                        }
                        // Sort button inside search row
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Filled.Sort, null, tint = Accent, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(sortLabel(order), color = if (sortOrder == order) Accent else theme.textMain) },
                                        onClick = { sortOrderName = order.name; showSortMenu = false }
                                    )
                                }
                            }
                        }
                        // (scan ISBN movido a BookSearchScreen)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 0.dp),
                colors = fieldColors(theme),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // ── Dialog elección post-scan ISBN ────────────────────────
            if (showIsbnScanDialog && scannedIsbnForDialog.isNotBlank()) {
                AlertDialog(
                    onDismissRequest = {
                        showIsbnScanDialog = false
                        listMainRef?.pendingScannedIsbn?.value = null
                    },
                    containerColor = theme.bgMid,
                    title = { Text(stringResource(R.string.txt_016a31c7), color = theme.textMain, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(8.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
                                Text(scannedIsbnForDialog, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                            Text(stringResource(R.string.txt_55800f4c), color = theme.textMuted, fontSize = 13.sp)
                        }
                    },
                    confirmButton = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Button(
                                onClick = {
                                    showIsbnScanDialog = false
                                    // Buscar automáticamente → BookSearch con ISBN como query
                                    // pendingScannedIsbn queda seteado para que BookSearchScreen lo use
                                    onNavigateToBookSearch()  // navega a BookSearch (el isbn ya está en pending)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.txt_69e20782), fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = {
                                    showIsbnScanDialog = false
                                    listMainRef?.pendingScannedIsbn?.value = null
                                    onAddWithIsbn(scannedIsbnForDialog)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Accent)
                            ) {
                                Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.txt_97225860), color = Accent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    },
                    dismissButton = {}
                )
            }

            // ── Shelf tabs (fixed, all visible) ─────────────────
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color.Transparent,
                contentColor = Accent,
                indicator = { tabPositions ->
                    if (activeTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = statusColor(SHELF_ORDER[activeTab]),
                            height = 2.dp
                        )
                    }
                },
                divider = { HorizontalDivider(color = theme.border, thickness = 0.5.dp) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                SHELF_ORDER.forEachIndexed { index, status ->
                    val count = shelfCounts[status] ?: 0
                    val color = statusColor(status)
                    val selected = activeTab == index
                    Tab(
                        selected = selected,
                        onClick  = { activeTab = index },
                        modifier = Modifier.padding(bottom = 0.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)
                        ) {
                            Text(
                                statusEmoji(status),
                                fontSize = 13.sp
                            )
                            Text(
                                statusLabel(status),
                                fontSize = 8.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) color else theme.textDim,
                                maxLines = 1
                            )
                            if (count > 0) {
                                Box(
                                    Modifier
                                        .padding(top = 2.dp)
                                        .background(
                                            if (selected) color else theme.border,
                                            RoundedCornerShape(50)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        "$count",
                                        fontSize = 8.sp,
                                        color = if (selected) Color.White else theme.textDim,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Content: books for active shelf ─────────────────────────────────
        val activeStatus = SHELF_ORDER[activeTab]
        val activeBooks  = shelves[activeStatus] ?: emptyList()

        if (vm.books.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📚", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.txt_66308bc7), color = theme.textMain, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.txt_eb8e218c), color = theme.textDim, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp)
            ) {
                if (activeBooks.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(statusEmoji(activeStatus), fontSize = 44.sp)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    emptyShelfHint(activeStatus),
                                    color = theme.textDim,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(activeBooks, key = { it.id }) { book ->
                        val context = LocalContext.current
                        val widgetBookId = com.lecturameter.widget.loadWidgetBook(context)
                        val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
                        AnimatedVisibility(
                            visibleState = visibleState,
                            enter = slideInVertically(
                                initialOffsetY = { it / 5 },
                                animationSpec = tween(durationMillis = 280)
                            ) + fadeIn(animationSpec = tween(durationMillis = 320))
                        ) {
                        BookCard(
                            book, theme,
                            onClick = { onDetail(book.id) },
                            onDelete = { vm.deleteBook(book.id, prefs) },
                            isWidgetBook = widgetBookId == book.id,
                            isRefreshingCover = vm.isCoverRefreshing(book.id),
                            onRefreshCover = {
                                vm.refreshCover(book.id, prefs) { coverFound, genreFound ->
                                    if (coverFound || genreFound) {
                                        refreshWidgetForBookIfSelected(context, book.id, clearCoverCache = true)
                                    }
                                    if (!coverFound || !genreFound) {
                                        val missing = listOfNotNull(
                                            if (!coverFound) context.getString(R.string.word_cover) else null,
                                            if (!genreFound) context.getString(R.string.word_genre) else null
                                        )
                                        val msg = context.getString(R.string.msg_not_found_prefix, missing.joinToString(context.getString(R.string.word_join_nor)))
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onApplyCoverUrl = { newUrl ->
                                vm.updateCover(book.id, newUrl, prefs)
                                refreshWidgetForBookIfSelected(context, book.id, clearCoverCache = true)
                            },
                            onBroken = { vm.markCoverBroken(book.id, prefs) },
                            sessionPages = vm.sessionsForBook(book.id).sumOf { it.pages },
                            sessionDays = vm.sessionsForBook(book.id).map { it.date }.toSet().size
                        )
                        } // AnimatedVisibility
                        Spacer(Modifier.height(10.dp))
                    }

                }
            }
        }
    }
}

@Composable
fun emptyShelfHint(status: BookStatus) = when (status) {
    BookStatus.READING   -> stringResource(R.string.empty_shelf_reading)
    BookStatus.REREADING -> stringResource(R.string.empty_shelf_rereading)
    BookStatus.FINISHED  -> stringResource(R.string.empty_shelf_finished)
    BookStatus.PENDING   -> stringResource(R.string.empty_shelf_pending)
    BookStatus.DROPPED   -> stringResource(R.string.empty_shelf_dropped)
}

// ── JSON Backup / Restore ─────────────────────────────────────────────────────

data class FullBackup(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val books: List<Book>? = null,
    val sessions: List<ReadingSession>? = null,
    val wrappedHistory: List<YearWrapped>? = null
)

/** Convierte portadas locales (rutas absolutas a filesDir) en base64 data URIs
 *  para que sobrevivan al backup/restore. Las URLs https se dejan tal cual. */
internal fun embedLocalCoverUrl(url: String?): String? {
    if (url == null || url.startsWith("http") || url.startsWith("data:")) return url
    return try {
        val file = java.io.File(url)
        if (!file.exists()) return url
        // Seguridad: solo leer si el archivo está bajo una ruta esperada de portadas locales.
        // Esto evita que un coverUrl manipulado (vía backup malicioso restaurado, por ejemplo)
        // exfiltre archivos privados de la app en el siguiente backup.
        val canonical = file.canonicalPath
        if (!canonical.contains("/files/covers/") && !canonical.contains("/cache/")) return url
        if (file.length() > 5_000_000L) return url
        val bytes = file.readBytes()
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    } catch (_: Exception) { url }
}

private fun embedLocalCoversForExport(context: Context, books: List<Book>): List<Book> =
    books.map { book ->
        val embeddedCover = embedLocalCoverUrl(book.coverUrl)
        val embeddedEditions = book.editions.map { ed ->
            ed.copy(coverUrl = embedLocalCoverUrl(ed.coverUrl))
        }
        book.copy(coverUrl = embeddedCover, editions = embeddedEditions)
    }

/** Al restaurar, extrae base64 data URIs y las guarda de nuevo en filesDir. */
private fun restoreLocalCoversFromBackup(context: Context, books: List<Book>): List<Book> =
    books.map { book ->
        val url = book.coverUrl ?: return@map book
        if (!url.startsWith("data:image")) return@map book
        try {
            // Seguridad: validar tipo MIME explícito
            val mimeType = url.substringAfter("data:").substringBefore(";")
            if (mimeType !in listOf("image/jpeg", "image/png", "image/webp")) return@map book

            val b64 = url.substringAfter("base64,")

            // Seguridad: rechazar payloads > 5 MB (Base64 de 5 MB ≈ 6.67 M chars)
            if (b64.length > 6_700_000) return@map book

            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)

            // Seguridad: validar magic bytes — JPEG (FF D8 FF) o PNG (89 50 4E 47)
            val isValidImage = when {
                bytes.size >= 3 &&
                    bytes[0] == 0xFF.toByte() &&
                    bytes[1] == 0xD8.toByte() &&
                    bytes[2] == 0xFF.toByte() -> true  // JPEG
                bytes.size >= 4 &&
                    bytes[0] == 0x89.toByte() &&
                    bytes[1] == 0x50.toByte() &&
                    bytes[2] == 0x4E.toByte() &&
                    bytes[3] == 0x47.toByte() -> true  // PNG
                else -> false
            }
            if (!isValidImage) return@map book

            val coversDir = java.io.File(context.filesDir, "covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val dest = java.io.File(coversDir, "${book.id}.jpg")
            dest.writeBytes(bytes)
            val restoredEditions = book.editions.map { ed ->
                val edUrl = ed.coverUrl ?: return@map ed
                if (!edUrl.startsWith("data:image")) return@map ed
                try {
                    val edMime = edUrl.substringAfter("data:").substringBefore(";")
                    if (edMime !in listOf("image/jpeg", "image/png", "image/webp")) return@map ed
                    val edB64 = edUrl.substringAfter("base64,")
                    if (edB64.length > 6_700_000) return@map ed
                    val edBytes = android.util.Base64.decode(edB64, android.util.Base64.DEFAULT)
                    val coversDir2 = java.io.File(context.filesDir, "covers")
                    if (!coversDir2.exists()) coversDir2.mkdirs()
                    val edDest = java.io.File(coversDir2, "${book.id}_${ed.id}.jpg")
                    edDest.writeBytes(edBytes)
                    ed.copy(coverUrl = edDest.absolutePath)
                } catch (_: Exception) { ed }
            }
            book.copy(coverUrl = dest.absolutePath, editions = restoredEditions)
        } catch (_: Exception) { book }
    }

fun formatLastLocalBackup(context: Context, prefs: android.content.SharedPreferences): String {
    val ts = prefs.getLong("last_local_backup_ms", 0L)
    if (ts == 0L) return context.getString(R.string.backup_time_never)
    val diff = kotlin.math.abs(System.currentTimeMillis() - ts)
    val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        mins < 2    -> context.getString(R.string.backup_time_moment)
        mins < 60   -> context.getString(R.string.backup_time_mins, mins)
        mins < 1440 -> context.getString(R.string.backup_time_hours, java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diff))
        else        -> context.getString(R.string.backup_time_days, java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff))
    }
}

fun exportFullBackup(context: Context, vm: BooksViewModel): Uri? {
    return try {
        val backup = FullBackup(
            books = embedLocalCoversForExport(context, vm.books),
            sessions = vm.sessions,
            wrappedHistory = vm.wrappedHistory
        )
        val gson = Gson()
        val json = gson.toJson(backup)
        val sdf = SimpleDateFormat("ddMMyy", Locale.getDefault())
        val fileName = "Backup_Lecturameter_${sdf.format(Date())}.json"
        val file = File(context.cacheDir, fileName)
        FileWriter(file).use { it.write(json) }
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (_: Exception) { null }
}

// Sanitiza campos String que Gson puede dejar como null al deserializar datos de versiones anteriores
// ── Heurística de bandera US/UK (v18.4) ───────────────────────────────────
// Para ediciones inglesas (language="original"/"unknown" y flag="🌐"), intenta
// clasificarlas como 🇺🇸 o 🇬🇧 según el publisher (señal más fiable) o el ISBN
// (señal secundaria). Si no hay señal clara, devuelve null y se conserva 🌐.
//
// Retorna Triple(flag, languageLabel, language) o null si no hay clasificación.
fun inferEnglishFlag(isbn: String?, publisher: String?): Triple<String, String, String>? {
    val pub = (publisher ?: "").lowercase()
    val isb = (isbn ?: "").replace(Regex("[-\\s]"), "")

    // Publishers conocidos: prioridad sobre ISBN
    val usPublishers = listOf(
        "tor books", "tor.com", "dragonsteel", "random house", "scribner", "knopf",
        "harpercollins us", "bantam", "anchor", "henry holt", "farrar", "fsg",
        "crown", "doubleday", "riverhead", "norton", "simon & schuster", "simon and schuster",
        "del rey", "vintage books", "ace books", "daw books", "subterranean press"
    )
    val ukPublishers = listOf(
        "gollancz", "orbit uk", "bloomsbury", "penguin uk", "penguin books ltd",
        "hodder", "pan macmillan", "faber", "headline", "tor uk", "voyager",
        "harpercollins uk", "vintage uk", "picador uk", "jonathan cape", "harvill secker"
    )
    val isUs = usPublishers.any { pub.contains(it) }
    val isUk = ukPublishers.any { pub.contains(it) }
    if (isUs && !isUk) return Triple("🇺🇸", "Inglés (EE.UU.)", "original")
    if (isUk && !isUs) return Triple("🇬🇧", "Inglés (Reino Unido)", "original")

    // Caso especial: "tor" como prefijo sin sufijo claro → tratamos como US (más probable)
    if (pub.matches(Regex("^tor[\\s,].*")) || pub == "tor") return Triple("🇺🇸", "Inglés (EE.UU.)", "original")

    // ISBN-13: 978-0-XX y 978-1-XX. Reglas aproximadas por rango de prefijo de grupo
    // (registration agency). Es imprecisa pero útil cuando el publisher falla.
    if (isb.length >= 13 && (isb.startsWith("9780") || isb.startsWith("9781"))) {
        // Tomar 2 dígitos siguientes al "978X" para decidir
        val grp = isb.substring(4, 6).toIntOrNull() ?: return null
        if (isb.startsWith("9780")) {
            return when {
                grp in 0..19 -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")  // mayoría US (Penguin, Scribner, etc.)
                grp in 20..29 -> Triple("🇬🇧", "Inglés (Reino Unido)", "original")  // UK Allen & Unwin, Faber...
                grp in 30..49 -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")
                grp in 50..69 -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")
                grp in 70..79 -> Triple("🇬🇧", "Inglés (Reino Unido)", "original")  // Hodder, Pan...
                grp in 80..87 -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")
                else -> null
            }
        } else { // 9781
            return when {
                grp in 0..6   -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")
                grp in 39..54 -> Triple("🇬🇧", "Inglés (Reino Unido)", "original")  // 1-40 a 1-54 UK
                grp in 55..86 -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")
                grp in 90..99 -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")
                else -> null
            }
        }
    }
    return null
}

fun sanitizeBook(b: Book): Book {
    // Gson puede deserializar null en campos con valor por defecto en Kotlin
    // cuando el JSON proviene de una version anterior que no tenia ese campo.
    // Todos los campos de tipo lista deben ser null-safe aqui.
    val safeGenres   = b.genres   ?: emptyList()
    val safeEditions = b.editions ?: emptyList()

    // Migracion: si genres esta vacio pero genre (campo legacy) tiene valor, convertir
    val legacyGenre = b.genre.orEmpty()
    val baseGenres = when {
        safeGenres.isNotEmpty() -> safeGenres
        legacyGenre.isNotBlank() -> listOf(legacyGenre)
        else -> emptyList()
    }
    // v20.9: "Horror" eliminado de BOOK_GENRES → normalizar a "Terror"
    val migratedGenres = baseGenres.map { if (it == "Horror") "Terror" else it }
    val safeFirst = b.firstFunctionalPage?.takeIf { it >= 1 }
    val safeLast  = b.lastFunctionalPage?.takeIf { it >= 1 && (safeFirst == null || it >= safeFirst) }
    // Migración v17.11: dropDate/resumedDate de versiones previas pudieron guardarse en dd-MM-yyyy.
    // El formato interno de fechas es yyyy-MM-dd; reconvertir si es necesario.
    fun fixInternalDate(d: String?): String? {
        if (d == null) return null
        if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(d)) return d
        return parseFlexibleDate(d)  // acepta dd-MM-yyyy y variantes; null si no se entiende
    }
    return b.copy(
        title    = b.title    ?: "",
        author   = b.author   ?: "",
        comment  = b.comment  ?: "",
        genre    = "",
        genres   = migratedGenres,
        firstFunctionalPage = safeFirst,
        lastFunctionalPage  = safeLast,
        // v20.9: si está DROPPED y no tiene startDate/dropDate, asignamos hoy
        startDate = fixInternalDate(b.startDate) ?: if (b.status == BookStatus.DROPPED) today() else null,
        endDate = fixInternalDate(b.endDate) ?: run {
            // v21.37: FINISHED sin endDate → usar startDate (el usuario lo editará si es incorrecto)
            val fixedStart = fixInternalDate(b.startDate)
            if (b.status == BookStatus.FINISHED && fixedStart != null) fixedStart else null
        },
        dropDate = fixInternalDate(b.dropDate) ?: if (b.status == BookStatus.DROPPED) today() else null,
        resumedDate = fixInternalDate(b.resumedDate),
        dateEvents = b.dateEvents ?: emptyList(),   // v19.9 fix: Gson pone null si el campo no existe en el JSON
        editions = safeEditions.map { e ->
            val sanitized = e.copy(
                language      = e.language      ?: "unknown",
                languageLabel = e.languageLabel ?: "Edición principal",
                flag          = e.flag          ?: "🌐",
                title         = e.title         ?: "",
                publisher     = e.publisher     ?: "",
                publishYear   = e.publishYear   ?: ""
            )
            // v18.4: auto-clasificación US/UK para ediciones inglesas con flag=🌐.
            // Solo se aplica si el usuario no ha elegido manualmente otra bandera.
            val needsInfer = sanitized.flag == "🌐" &&
                (sanitized.language == "original" || sanitized.language == "unknown")
            if (needsInfer) {
                val inferred = inferEnglishFlag(sanitized.isbn, sanitized.publisher)
                if (inferred != null) {
                    val (newFlag, newLabel, newLang) = inferred
                    sanitized.copy(flag = newFlag, languageLabel = newLabel, language = newLang)
                } else sanitized
            } else sanitized
        }
    )
}

fun importFullBackup(
    context: Context,
    uri: Uri,
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences
): Pair<Boolean, String> {
    return try {
        val json = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.readText() ?: return Pair(false, context.getString(R.string.err_backup_read_failed))
        val gson = Gson()
        val type = object : TypeToken<FullBackup>() {}.type
        val backup: FullBackup = gson.fromJson(json, type)
            ?: return Pair(false, context.getString(R.string.err_backup_format_invalid))

        val backupBooks = backup.books ?: emptyList()
        val backupSessions = backup.sessions ?: emptyList()
        val backupWrapped = backup.wrappedHistory ?: emptyList()

        // Merge: importamos libros que no existan (por ISBN o título+autor)
        val existingIsbns = vm.books.mapNotNull { it.isbn }.toSet()
        val existingKeys = vm.books.map { "${it.title.trim().lowercase()}|${it.author.trim().lowercase()}" }.toSet()

        val existingBookIds = vm.books.map { it.id }.toSet()
        val newBooks = backupBooks.filter { b ->
            val key = "${b.title.trim().lowercase()}|${b.author.trim().lowercase()}"
            b.id !in existingBookIds && (b.isbn == null || b.isbn !in existingIsbns) && key !in existingKeys
        }
        val existingSessionIds = vm.sessions.map { it.id }.toSet()
        // Bug fix v21.15: una sesión "nueva a restaurar" hay que reasignarla al id LOCAL del
        // libro, no al id que traía en el backup. Si el libro ya existía localmente (mismo id,
        // mismo ISBN o mismo título+autor — caso típico al restaurar un backup de OTRA instalación,
        // p.ej. dev -> public, donde los ids de libro son System.currentTimeMillis() y nunca coinciden)
        // las sesiones deben apuntar al id local existente, no quedar huérfanas.
        val bookIdRemap: Map<Long, Long> = backupBooks.associate { b ->
            val key = "${b.title.trim().lowercase()}|${b.author.trim().lowercase()}"
            val matched = vm.books.firstOrNull { it.id == b.id }
                ?: vm.books.firstOrNull { b.isbn != null && it.isbn == b.isbn }
                ?: vm.books.firstOrNull { "${it.title.trim().lowercase()}|${it.author.trim().lowercase()}" == key }
            b.id to (matched?.id ?: b.id)
        }
        val newSessions = backupSessions
            .filter { it.id !in existingSessionIds }
            .mapNotNull { s -> bookIdRemap[s.bookId]?.let { resolvedId -> s.copy(bookId = resolvedId) } }

        // Actualizar libros existentes con campos nuevos del backup
        val backupBookById = backupBooks.associateBy { it.id }
        vm.books = vm.books.map { existing ->
            val fromBackup = backupBookById[existing.id]
            if (fromBackup != null) {
                val restoredEditions = if (fromBackup.editions.isNotEmpty())
                    restoreLocalCoversFromBackup(context, listOf(
                        existing.copy(editions = fromBackup.editions)
                    )).first().editions
                else existing.editions
                val restoredCover = if (fromBackup.coverUrl?.startsWith("data:image") == true)
                    restoreLocalCoversFromBackup(context, listOf(fromBackup)).first().coverUrl
                else fromBackup.coverUrl ?: existing.coverUrl
                existing.copy(
                    firstFunctionalPage = fromBackup.firstFunctionalPage,
                    lastFunctionalPage  = fromBackup.lastFunctionalPage,
                    coverUrl  = restoredCover,
                    editions  = restoredEditions
                )
            } else existing
        } + newBooks.map { sanitizeBook(restoreLocalCoversFromBackup(context, listOf(it)).first()) }

        // Actualizar sesiones existentes con campos nuevos del backup (startPage, endPage, pages, etc.)
        val backupSessionById = backupSessions.associateBy { it.id }
        vm.sessions = vm.sessions.map { existing ->
            val fromBackup = backupSessionById[existing.id]
            if (fromBackup != null) existing.copy(
                startPage = fromBackup.startPage ?: existing.startPage,
                endPage   = fromBackup.endPage   ?: existing.endPage,
                pages     = fromBackup.pages
            ) else existing
        } + newSessions
        // Merge wrapped history (backup wins on same year)
        val existingYears = vm.wrappedHistory.map { it.year }.toSet()
        val newWrapped = backupWrapped.filter { it.year !in existingYears }
        vm.wrappedHistory = vm.wrappedHistory + newWrapped

        prefs.edit()
            .putString("books", gson.toJson(vm.books))
            .putString("sessions", gson.toJson(vm.sessions))
            .putString("wrapped_history", gson.toJson(vm.wrappedHistory))
            .apply()

        // Solo contar libros que YA existían y fueron actualizados (no los nuevos)
        val updatedBooks = vm.books.count { b -> backupBookById[b.id] != null && b.id in existingBookIds }
        val updatedSessions = vm.sessions.count { s -> backupSessionById[s.id] != null && s.id in existingSessionIds }
        val msg = buildString {
            if (newBooks.isNotEmpty()) append(context.getString(R.string.import_new_books, newBooks.size))
            if (newSessions.isNotEmpty()) {
                if (isNotEmpty()) append(", ")
                append(context.getString(R.string.import_new_sessions, newSessions.size))
            }
            if (updatedBooks > 0) {
                if (isNotEmpty()) append(", ")
                append(context.getString(R.string.import_updated_books, updatedBooks))
            }
            if (updatedSessions > 0) {
                if (isNotEmpty()) append(", ")
                append(context.getString(R.string.import_updated_sessions, updatedSessions))
            }
            if (isEmpty()) append(context.getString(R.string.import_all_up_to_date))
        }
        Pair(true, msg)
    } catch (e: Exception) {
        Pair(false, "Error: ${e.message}")
    }
}

// ── CSV Export ────────────────────────────────────────────────────────────────

fun exportBooksToCSV(context: Context, books: List<Book>): Uri? {
    return try {
        val header = "Title,Author,ISBN,My Rating,Average Rating,Publisher,Binding,Number of Pages,Year Published,Original Publication Year,Date Read,Date Added,Bookshelves,Exclusive Shelf,My Review,Spoiler,Private Notes,Read Count,Recommended For,Recommended By,Owned Copies,Original Purchase Date,Purchase Location,Condition,Condition Description,BCID"
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val rows = books.map { b ->
            val shelf = when (b.status) {
                BookStatus.FINISHED  -> "read"
                BookStatus.READING   -> "currently-reading"
                BookStatus.REREADING -> "currently-reading"
                BookStatus.PENDING   -> "to-read"
                BookStatus.DROPPED   -> "did-not-finish"
            }
            val dateRead = if (b.status == BookStatus.FINISHED && b.endDate != null)
                try { sdf.format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(b.endDate)!!) } catch (_: Exception) { "" }
            else ""
            val dateAdded = try { sdf.format(Date(b.addedAt)) } catch (_: Exception) { "" }
            // v21.41: mismo mapping que MiniRating — cada 2 puntos = 1 estrella, con redondeo hacia arriba
            val rating = if (b.rating > 0) ((b.rating + 1) / 2).coerceIn(1, 5).toString() else "0"
            fun esc(s: String) = "\"${s.replace("\"", "\"\"")}\""
            listOf(
                esc(b.title), esc(b.author), b.isbn ?: "", rating, "",
                "", "", b.pages.toString(), "", "",
                dateRead, dateAdded, shelf, shelf,
                esc(b.comment), "", "", "1", "", "", "0", "", "", "", "", ""
            ).joinToString(",")
        }
        val csv = (listOf(header) + rows).joinToString("\n")
        val file = File(context.cacheDir, "lecturameter_export.csv")
        FileWriter(file).use { it.write(csv) }
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (_: Exception) { null }
}

// ── importFullBackupFromJson (helper compartido con DriveBackupManager) ───────

fun importFullBackupFromJson(
    json: String,
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    context: android.content.Context
): Pair<Boolean, String> {
    return try {
        val gson = Gson()
        val type = object : TypeToken<FullBackup>() {}.type
        val backup: FullBackup = gson.fromJson(json, type)
            ?: return Pair(false, context.getString(R.string.err_backup_format_invalid))

        val backupBooks2 = backup.books ?: emptyList()
        val backupSessions2 = backup.sessions ?: emptyList()
        val backupWrapped2 = backup.wrappedHistory ?: emptyList()

        val existingIsbns = vm.books.mapNotNull { it.isbn }.toSet()
        val existingKeys = vm.books.map { "${it.title.trim().lowercase()}|${it.author.trim().lowercase()}" }.toSet()
        val existingBookIds = vm.books.map { it.id }.toSet()
        val newBooks = backupBooks2.filter { b ->
            val key = "${b.title.trim().lowercase()}|${b.author.trim().lowercase()}"
            b.id !in existingBookIds && (b.isbn == null || b.isbn !in existingIsbns) && key !in existingKeys
        }
        val existingSessionIds = vm.sessions.map { it.id }.toSet()
        // Bug fix v21.15: una sesión "nueva a restaurar" hay que reasignarla al id LOCAL del
        // libro, no al id que traía en el backup. Si el libro ya existía localmente (mismo id,
        // mismo ISBN o mismo título+autor — caso típico al restaurar un backup de OTRA instalación,
        // p.ej. dev -> public, donde los ids de libro son System.currentTimeMillis() y nunca coinciden)
        // las sesiones deben apuntar al id local existente, no quedar huérfanas.
        val bookIdRemap: Map<Long, Long> = backupBooks2.associate { b ->
            val key = "${b.title.trim().lowercase()}|${b.author.trim().lowercase()}"
            val matched = vm.books.firstOrNull { it.id == b.id }
                ?: vm.books.firstOrNull { b.isbn != null && it.isbn == b.isbn }
                ?: vm.books.firstOrNull { "${it.title.trim().lowercase()}|${it.author.trim().lowercase()}" == key }
            b.id to (matched?.id ?: b.id)
        }
        val newSessions = backupSessions2
            .filter { it.id !in existingSessionIds }
            .mapNotNull { s -> bookIdRemap[s.bookId]?.let { resolvedId -> s.copy(bookId = resolvedId) } }
        val backupBookById2 = backupBooks2.associateBy { it.id }
        vm.books = vm.books.map { existing ->
            val fromBackup = backupBookById2[existing.id]
            if (fromBackup != null) {
                val restoredEditions2 = if (fromBackup.editions.isNotEmpty())
                    restoreLocalCoversFromBackup(context, listOf(
                        existing.copy(editions = fromBackup.editions)
                    )).first().editions
                else existing.editions
                val restoredCover2 = if (fromBackup.coverUrl?.startsWith("data:image") == true)
                    restoreLocalCoversFromBackup(context, listOf(fromBackup)).first().coverUrl
                else fromBackup.coverUrl ?: existing.coverUrl
                existing.copy(
                    firstFunctionalPage = fromBackup.firstFunctionalPage,
                    lastFunctionalPage  = fromBackup.lastFunctionalPage,
                    coverUrl  = restoredCover2,
                    editions  = restoredEditions2
                )
            } else existing
        } + newBooks.map { sanitizeBook(restoreLocalCoversFromBackup(context, listOf(it)).first()) }
        val backupSessionById2 = backupSessions2.associateBy { it.id }
        vm.sessions = vm.sessions.map { existing ->
            val fromBackup = backupSessionById2[existing.id]
            if (fromBackup != null) existing.copy(
                startPage = fromBackup.startPage ?: existing.startPage,
                endPage   = fromBackup.endPage   ?: existing.endPage,
                pages     = fromBackup.pages
            ) else existing
        } + newSessions
        val existingYears = vm.wrappedHistory.map { it.year }.toSet()
        val newWrapped = backupWrapped2.filter { it.year !in existingYears }
        vm.wrappedHistory = vm.wrappedHistory + newWrapped
        prefs.edit()
            .putString("books", gson.toJson(vm.books))
            .putString("sessions", gson.toJson(vm.sessions))
            .putString("wrapped_history", gson.toJson(vm.wrappedHistory))
            .apply()
        val msg = buildString {
            if (newBooks.isNotEmpty()) append(context.getString(R.string.import_restored_books, newBooks.size))
            if (newSessions.isNotEmpty()) { if (isNotEmpty()) append(", "); append(context.getString(R.string.import_restored_sessions, newSessions.size)) }
            if (isEmpty()) append(context.getString(R.string.import_all_up_to_date))
        }
        Pair(true, msg)
    } catch (e: OutOfMemoryError) {
        throw e // No silenciar — el sistema necesita reaccionar
    } catch (e: Exception) {
        Pair(false, "Error: ${e.message}")
    }
}

// ── ImportExportScreen ────────────────────────────────────────────────────────

@Composable
fun ImportExportScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit) {
    val context = LocalContext.current
    var importMsg by remember { mutableStateOf<String?>(null) }
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var backupMsg by remember { mutableStateOf<String?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isLocalAutoBackingUp by remember { mutableStateOf(false) }
    var lastLocalBackupText by remember { mutableStateOf(formatLastLocalBackup(context, prefs)) }
    // Flags de backup desde Ajustes — los botones manuales quedan grises si están desactivados
    var localBackupOn by remember { mutableStateOf(prefs.getBoolean("local_backup_enabled", true)) }
    var driveBackupOn by remember { mutableStateOf(prefs.getBoolean("drive_backup_enabled", true)) }
    var showActivateLocalBk by remember { mutableStateOf(false) }
    var showActivateDriveBk by remember { mutableStateOf(false) }
    // Permiso WRITE_EXTERNAL_STORAGE (solo Android 8-9)
    var showStoragePermDialog by remember { mutableStateOf(false) }
    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) pendingStorageAction?.invoke()
        else android.widget.Toast.makeText(context, context.getString(R.string.msg_no_storage_permission_backup), android.widget.Toast.LENGTH_SHORT).show()
        pendingStorageAction = null
    }
    if (showStoragePermDialog) {
        AlertDialog(
            onDismissRequest = { showStoragePermDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_5f9fd925), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_b7a882b4), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showStoragePermDialog = false
                    storagePermLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }) { Text(stringResource(R.string.txt_5fcafeb2), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showStoragePermDialog = false; pendingStorageAction = null }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }
    fun runWithStoragePerm(action: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT in 26..28 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingStorageAction = action
            showStoragePermDialog = true
        } else {
            action()
        }
    }
    val scope = rememberCoroutineScope()
    val driveSignInClient = remember {
        GoogleSignIn.getClient(context, DriveBackupManager.buildSignInOptions())
    }
    var driveAccount by remember { mutableStateOf(DriveBackupManager.getSignedInAccount(context)) }
    var driveMsg by remember { mutableStateOf<String?>(null) }
    var isDriveLoading by remember { mutableStateOf(false) }
    var lastBackupText by remember { mutableStateOf(DriveBackupManager.formatLastBackup(context, prefs)) }

    if (showActivateLocalBk) {
        AlertDialog(
            onDismissRequest = { showActivateLocalBk = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_2d639552), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_632543a9), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    localBackupOn = true
                    prefs.edit().putBoolean("local_backup_enabled", true).apply()
                    showActivateLocalBk = false
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showActivateLocalBk = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }
    if (showActivateDriveBk) {
        AlertDialog(
            onDismissRequest = { showActivateDriveBk = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_dcd9cd29), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_eec2e836), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    driveBackupOn = true
                    prefs.edit().putBoolean("drive_backup_enabled", true).apply()
                    showActivateDriveBk = false
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showActivateDriveBk = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val count = vm.importFromGoodreads(context, uri, prefs)
            importMsg = if (count > 0) context.getString(R.string.msg_import_goodreads_ok, count) else context.getString(R.string.msg_import_goodreads_empty)
            exportMsg = null
        }
    }

    val jsonRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val (ok, msg) = importFullBackup(context, uri, vm, prefs)
            backupMsg = if (ok) "✅ $msg" else "❌ $msg"
            importMsg = null; exportMsg = null
        }
    }

    val driveSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (GoogleSignIn.hasPermissions(account, DriveBackupManager.REQUIRED_SCOPE)) {
                driveAccount = account
                driveMsg = context.getString(R.string.msg_drive_connected, account.email ?: "")
            } else {
                driveMsg = context.getString(R.string.msg_drive_no_perms)
            }
        } catch (e: ApiException) {
            driveMsg = context.getString(R.string.msg_drive_connect_error, e.message ?: "")
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 24.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.txt_7ddf5345), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (vm.books.size == 1) stringResource(R.string.library_books_count, vm.books.size) else stringResource(R.string.library_books_count_plural, vm.books.size), color = theme.textMuted, fontSize = 13.sp)
            }
        }

        // ── IMPORTAR ─────────────────────────────────────────────────────────
        Text(stringResource(R.string.txt_e346618a), color = theme.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 10.dp))

        Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1A6366F1)), contentAlignment = Alignment.Center) {
                        Text("📚", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_62da2239), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.txt_10932fd3), color = theme.textMuted, fontSize = 12.sp)
                    }
                }
                Text(
                    stringResource(R.string.txt_16f666bb),
                    color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp)
                )
                Button(
                    onClick = { csvLauncher.launch("text/*"); importMsg = null; exportMsg = null },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.txt_e6b9af9f), fontWeight = FontWeight.Bold)
                }
                importMsg?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = if (msg.startsWith("✅")) Color(0x1A10B981) else Color(0x1AF59E0B), border = BorderStroke(1.dp, if (msg.startsWith("✅")) Color(0x4D10B981) else Color(0x4DF59E0B))) {
                        Text(msg, color = if (msg.startsWith("✅")) Green else Amber, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── EXPORTAR ─────────────────────────────────────────────────────────
        Text("📥 " + stringResource(R.string.txt_53d6215e), color = theme.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 10.dp))

        Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1A10B981)), contentAlignment = Alignment.Center) {
                        Text("📊", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_f1fb0dc3), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.txt_30fddd15), color = theme.textMuted, fontSize = 12.sp)
                    }
                }

                // Resumen de lo que se exportará
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0x0D10B981), border = BorderStroke(1.dp, Color(0x1A10B981)), modifier = Modifier.padding(bottom = 14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val reading  = vm.books.count { it.status == BookStatus.READING || it.status == BookStatus.REREADING }
                        val finished = vm.books.count { it.status == BookStatus.FINISHED }
                        val pending  = vm.books.count { it.status == BookStatus.PENDING }
                        ExportStatCell("$finished", stringResource(R.string.export_stat_finished), Green)
                        ExportStatCell("$reading",  stringResource(R.string.export_stat_reading),  Amber)
                        ExportStatCell("$pending",  stringResource(R.string.export_stat_pending),  theme.textDim)
                        ExportStatCell("${vm.books.size}", stringResource(R.string.export_stat_total), theme.textMain)
                    }
                }

                Text(
                    stringResource(R.string.txt_8ccc576b),
                    color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp)
                )

                Button(
                    onClick = {
                        if (vm.books.isEmpty()) { exportMsg = context.getString(R.string.msg_no_books_export); return@Button }
                        isExporting = true; exportMsg = null; importMsg = null
                        scope.launch {
                            val uri = exportBooksToCSV(context, vm.books)
                            isExporting = false
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Mi biblioteca Lecturameter")
                                    putExtra(Intent.EXTRA_TEXT, "Aquí tienes mi biblioteca exportada desde Lecturameter (${vm.books.size} libros).")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Compartir biblioteca"))
                                exportMsg = context.getString(R.string.msg_export_ready)
                            } else {
                                exportMsg = context.getString(R.string.msg_export_error_gen)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green),
                    enabled = !isExporting && vm.books.isNotEmpty()
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_cad19441), fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_583569fd), fontWeight = FontWeight.Bold)
                    }
                }
                exportMsg?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = if (msg.startsWith("✅")) Color(0x1A10B981) else if (msg.startsWith("⚠️")) Color(0x1AF59E0B) else Color(0x1AF87171), border = BorderStroke(1.dp, if (msg.startsWith("✅")) Color(0x4D10B981) else if (msg.startsWith("⚠️")) Color(0x4DF59E0B) else Color(0x4DF87171)), modifier = Modifier.fillMaxWidth()) {
                        Text(msg, color = if (msg.startsWith("✅")) Green else if (msg.startsWith("⚠️")) Amber else Red, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── BACKUP COMPLETO ───────────────────────────────────────────────────
        Text(stringResource(R.string.txt_78932aed), color = theme.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 10.dp))

        Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1AF59E0B)), contentAlignment = Alignment.Center) {
                        Text("🔒", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_c5ed541f), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.txt_28d57875), color = theme.textMuted, fontSize = 11.sp)
                    }
                }
                Text(
                    stringResource(R.string.txt_f7464b62),
                    color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp)
                )
                // Exportar backup JSON
                Button(
                    onClick = {
                        if (vm.books.isEmpty()) { backupMsg = context.getString(R.string.msg_no_data_export); return@Button }
                        isBackingUp = true; backupMsg = null
                        scope.launch {
                            val uri = withContext(kotlinx.coroutines.Dispatchers.IO) { exportFullBackup(context, vm) }
                            isBackingUp = false
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Backup Lecturameter")
                                    putExtra(Intent.EXTRA_TEXT, "Copia de seguridad completa de Lecturameter (${vm.books.size} libros, ${vm.sessions.size} sesiones).")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Guardar backup"))
                                backupMsg = context.getString(R.string.msg_backup_ready_share)
                            } else {
                                backupMsg = context.getString(R.string.msg_backup_error_gen)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber),
                    enabled = !isBackingUp && vm.books.isNotEmpty()
                ) {
                    if (isBackingUp) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_cad19441), fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_ea8ca034), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Restaurar backup JSON
                OutlinedButton(
                    onClick = {
                        jsonRestoreLauncher.launch(arrayOf("application/json", "text/plain", "text/*", "*/*"))
                        backupMsg = null
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Amber),
                ) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp), tint = Amber)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.txt_37af485a), fontWeight = FontWeight.Bold, color = Amber)
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = theme.border)
                Spacer(Modifier.height(12.dp))
                // Guardar backup local en Descargas
                Text(stringResource(R.string.local_backup_last, lastLocalBackupText), color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                OutlinedButton(
                    onClick = {
                        if (!localBackupOn) { showActivateLocalBk = true; return@OutlinedButton }
                        if (vm.books.isEmpty()) { backupMsg = context.getString(R.string.msg_no_data_export); return@OutlinedButton }
                        runWithStoragePerm {
                        isLocalAutoBackingUp = true; backupMsg = null
                        val req = OneTimeWorkRequestBuilder<JsonBackupWorker>().build()
                        val wm = WorkManager.getInstance(context)
                        wm.enqueueUniqueWork(
                            "lecturameter_json_backup_manual",
                            ExistingWorkPolicy.REPLACE,
                            req
                        )
                        scope.launch {
                            // Bug fix v21.15: antes se asumía éxito tras esperar 3s fijos sin
                            // comprobar si el Worker había terminado de verdad (race condition —
                            // con muchas portadas embebidas en base64 puede tardar más). Ahora se
                            // sigue el estado real del WorkInfo hasta que termina, con timeout.
                            val finalState = kotlinx.coroutines.withTimeoutOrNull(20_000) {
                                wm.getWorkInfosForUniqueWorkFlow("lecturameter_json_backup_manual")
                                    .mapNotNull { infos -> infos.firstOrNull { it.id == req.id } }
                                    .first { it.state.isFinished }
                            }
                            isLocalAutoBackingUp = false
                            lastLocalBackupText = formatLastLocalBackup(context, prefs)
                            val customFolder = prefs.getString("local_backup_folder_uri", null)
                            backupMsg = when {
                                finalState == null ->
                                    context.getString(R.string.msg_backup_pending)
                                finalState.state == androidx.work.WorkInfo.State.SUCCEEDED ->
                                    if (customFolder != null) context.getString(R.string.msg_backup_saved_custom, readableFolderName(customFolder))
                                    else context.getString(R.string.msg_backup_saved_default)
                                else -> context.getString(R.string.msg_backup_error)
                            }
                        }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (localBackupOn) Green else theme.border),
                    enabled = !isLocalAutoBackingUp && vm.books.isNotEmpty()
                ) {
                    if (isLocalAutoBackingUp) {
                        CircularProgressIndicator(color = Green, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_9b9c0afb), fontWeight = FontWeight.Bold, color = Green)
                    } else {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp), tint = if (localBackupOn) Green else theme.textDim)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_56446f3b), fontWeight = FontWeight.Bold, color = if (localBackupOn) Green else theme.textDim)
                    }
                }
                backupMsg?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = if (msg.startsWith("✅")) Color(0x1A10B981) else if (msg.startsWith("⚠️")) Color(0x1AF59E0B) else Color(0x1AF87171), border = BorderStroke(1.dp, if (msg.startsWith("✅")) Color(0x4D10B981) else if (msg.startsWith("⚠️")) Color(0x4DF59E0B) else Color(0x4DF87171)), modifier = Modifier.fillMaxWidth()) {
                        Text(msg, color = if (msg.startsWith("✅")) Green else if (msg.startsWith("⚠️")) Amber else Red, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── GOOGLE DRIVE ──────────────────────────────────────────────────────
        Text(stringResource(R.string.txt_f40582e2), color = theme.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 10.dp))

        Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1A4285F4)), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.txt_cdd0bfa9), fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_afe10477), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        val driveIntervalHours = prefs.getInt("backup_interval_hours", 2)
                        Text(stringResource(R.string.drive_backup_auto_hint, driveIntervalHours), color = theme.textMuted, fontSize = 12.sp)
                    }
                }

                if (driveAccount == null) {
                    Text(
                        stringResource(R.string.txt_ed177a89),
                        color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp)
                    )
                    Button(
                        onClick = { driveSignInLauncher.launch(driveSignInClient.signInIntent) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                    ) {
                        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_737672f6), fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(driveAccount!!.email ?: "", color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.drive_backup_last, lastBackupText), color = theme.textDim, fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            // Cambiar de cuenta sin perder la configuración: cerrar sesión y volver a elegir cuenta
                            driveSignInClient.signOut().addOnCompleteListener {
                                driveMsg = null
                                driveSignInLauncher.launch(driveSignInClient.signInIntent)
                            }
                        }) {
                            Text(stringResource(R.string.txt_d1bdc329), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = {
                            driveSignInClient.signOut()
                            driveAccount = null
                            driveMsg = null
                        }) {
                            Text(stringResource(R.string.txt_f306dfe2), color = Red, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = {
                            if (!driveBackupOn) { showActivateDriveBk = true; return@Button }
                            isDriveLoading = true; driveMsg = null
                            scope.launch {
                                val result = DriveBackupManager.backup(context, prefs)
                                isDriveLoading = false
                                lastBackupText = DriveBackupManager.formatLastBackup(context, prefs)
                                driveMsg = result.fold(
                                    onSuccess = { context.getString(R.string.msg_drive_saved) },
                                    onFailure = { context.getString(R.string.msg_drive_error, it.message ?: "") }
                                )
                            }
                        },
                        enabled = !isDriveLoading,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (driveBackupOn) Color(0xFF4285F4) else theme.border)
                    ) {
                        if (isDriveLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.txt_9b9c0afb), fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.txt_3f98c4c5), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            isDriveLoading = true; driveMsg = null
                            scope.launch {
                                val result = DriveBackupManager.restore(context, vm, prefs)
                                isDriveLoading = false
                                driveMsg = result.fold(
                                    onSuccess = { "✅ $it" },
                                    onFailure = { context.getString(R.string.msg_drive_error, it.message ?: "") }
                                )
                            }
                        },
                        enabled = !isDriveLoading,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF4285F4))
                    ) {
                        Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp), tint = Color(0xFF4285F4))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_c8a3ca02), fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                    }
                }

                driveMsg?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    val isOk = msg.startsWith("✅")
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isOk) Color(0x1A10B981) else Color(0x1AF87171),
                        border = BorderStroke(1.dp, if (isOk) Color(0x4D10B981) else Color(0x4DF87171)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(msg, color = if (isOk) Green else Red, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun ExportStatCell(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = color.copy(alpha = 0.7f), fontSize = 10.sp)
    }
}

// ── WrappedScreen ─────────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WrappedScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, year: Int, onBack: () -> Unit) {
    val wrapped = remember(year, vm.books) { vm.computeWrapped(year) }
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { if (wrapped != null) 6 else 1 }

    // Auto-guardar al abrir durante la ventana
    LaunchedEffect(wrapped) {
        if (wrapped != null && isInWrappedWindow() && wrappedWindowYear() == year) {
            vm.saveWrappedForYear(wrapped, prefs)
        }
    }

    fun shareCurrentSlide() {
        if (sharing) return
        sharing = true
        scope.launch {
            try {
                kotlinx.coroutines.delay(80)
                val w = view.width; val h = view.height
                val full = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(full)
                view.draw(canvas)
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
                val statusH = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
                val navH    = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                val cropH = (h - statusH - navH).coerceAtLeast(1)
                val cropped = android.graphics.Bitmap.createBitmap(full, 0, statusH, w, cropH)
                full.recycle()
                val file = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val f = java.io.File(context.cacheDir, "wrapped_${year}_s${pagerState.currentPage}.png")
                    java.io.FileOutputStream(f).use { out -> cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
                    cropped.recycle(); f
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.wrapped_share_text, year))
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Compartir Wrapped"))
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, context.getString(R.string.msg_share_error, e.message), android.widget.Toast.LENGTH_SHORT).show()
            } finally { sharing = false }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // ── TOP BAR ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.wrapped_year_header, year), color = theme.textMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (wrapped != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(6) { i ->
                            Box(Modifier.size(if (i == pagerState.currentPage) 8.dp else 5.dp)
                                .clip(CircleShape)
                                .background(if (i == pagerState.currentPage) Accent else theme.border))
                        }
                    }
                }
            }
            if (wrapped != null) {
                IconButton(onClick = { shareCurrentSlide() }, enabled = !sharing) {
                    Icon(if (sharing) Icons.Default.Refresh else Icons.Default.Share, null, tint = if (sharing) theme.textDim else Accent)
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        if (wrapped == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📚", fontSize = 48.sp); Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.wrapped_no_finished, year.toString()), color = theme.textMain, fontSize = 17.sp)
                    Text(stringResource(R.string.wrapped_empty_hint, year), color = theme.textDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp))
                }
            }
            return@Box
        }

        // ── PAGER ──────────────────────────────────────────────────────────
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(top = 60.dp)
        ) { page ->
            val slideModifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)
            when (page) {

                // ── SLIDE 0: PORTADA / RESUMEN GENERAL ────────────────────
                0 -> Column(slideModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    Text("🎉", fontSize = 52.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.txt_c7363de2), color = theme.textDim, fontSize = 12.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(24.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0x1A6366F1), border = BorderStroke(1.dp, Color(0x336366F1)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp)) {
                            Text(stringResource(R.string.txt_c822bd3d), color = Color(0xFFA5B4FC), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth()) {
                                if (wrapped.rereadBooks > 0) {
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${wrapped.totalBooks}", color = Accent, fontSize = 36.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
                                        Text(stringResource(R.string.txt_76aee4f9), color = Accent.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${wrapped.totalBooks - wrapped.rereadBooks} nuevos + ${wrapped.rereadBooks} relect.", color = Accent.copy(alpha = 0.5f), fontSize = 9.sp, textAlign = TextAlign.Center)
                                    }
                                } else {
                                    WrappedBigStat("${wrapped.totalBooks}", stringResource(R.string.txt_76aee4f9), Modifier.weight(1f), Accent)
                                }
                                WrappedBigStat(wrapped.totalPages.toLocaleString(), stringResource(R.string.txt_47bcdf9a), Modifier.weight(1f), Accent2)
                                WrappedBigStat(String.format("%.1f", wrapped.avgPagesPerDay), stringResource(R.string.wrapped_pags_dia_lc), Modifier.weight(1f), Green)
                            }
                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0x1A6366F1))
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth()) {
                                WrappedBigStat(String.format("%.0f", wrapped.avgDaysPerBook), stringResource(R.string.wrapped_dias_libro), Modifier.weight(1f), Sky)
                                // v21.36: horas reales del cronómetro; si 0 → mensaje
                                if (wrapped.totalMinutes > 0) {
                                    val hWrapped = wrapped.totalMinutes / 60
                                    val mWrapped = wrapped.totalMinutes % 60
                                    WrappedBigStat(if (hWrapped > 0) "${hWrapped}h ${mWrapped}m" else "${mWrapped}m", stringResource(R.string.wrapped_horas), Modifier.weight(1f), Amber)
                                } else {
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("—", color = theme.textDim, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                        Text(stringResource(R.string.wrapped_no_timer), color = theme.textDim, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Racha más larga
                    if (wrapped.longestStreakDays > 0) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0x1AF87171), border = BorderStroke(1.dp, Color(0x33F87171)), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🔥", fontSize = 28.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.txt_362b636c), color = Red.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                    Text("${wrapped.longestStreakDays} ${stringResource(R.string.word_day)}${if (wrapped.longestStreakDays != 1) "s" else ""} ${stringResource(R.string.wrapped_streak_suffix)}", color = theme.textMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    if (wrapped.longestStreakDays > 1) Text("${fmtDate(wrapped.longestStreakStart)} → ${fmtDate(wrapped.longestStreakEnd)}", color = theme.textDim, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(48.dp))
                }

                // ── SLIDE 1: TIEMPO Y SESIONES ────────────────────────────
                1 -> Column(slideModifier) {
                    Text("⏱️ ${stringResource(R.string.txt_46f22310)}", color = Sky, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    if (wrapped.totalSessions > 0) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0x1A60A5FA), border = BorderStroke(1.dp, Color(0x3360A5FA)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    WrappedBigStat("${wrapped.totalSessions}", stringResource(R.string.wrapped_sessions_label), Modifier.weight(1f), Sky)
                                    val hS = wrapped.totalMinutes / 60; val mS = wrapped.totalMinutes % 60
                                    WrappedBigStat(if (hS > 0) "${hS}h ${mS}m" else "${mS}m", stringResource(R.string.wrapped_total_label), Modifier.weight(1f), Accent)
                                    if (wrapped.maxSessionPages > 0)
                                        WrappedBigStat("${wrapped.maxSessionPages}", stringResource(R.string.wrapped_record_session), Modifier.weight(1f), Amber)
                                }
                                if (wrapped.maxSessionPages > 0 && wrapped.maxSessionDate.isNotBlank()) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(stringResource(R.string.wrapped_longest_session, wrapped.maxSessionPages, fmtDate(wrapped.maxSessionDate)), color = theme.textDim, fontSize = 11.sp)
                                }
                                if (wrapped.mostReadDay.isNotBlank() && wrapped.mostReadDayPages > 0) {
                                    Text(stringResource(R.string.wrapped_most_read_day, fmtDate(wrapped.mostReadDay), wrapped.mostReadDayPages), color = theme.textDim, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    } else {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.wrapped_no_timer), color = theme.textDim, fontSize = 15.sp, textAlign = TextAlign.Center)
                        }
                    }
                    // Books that stole you the most time
                    if (wrapped.longestBooksTop3.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0x1A34D399), border = BorderStroke(1.dp, Color(0x3334D399)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Text(stringResource(R.string.txt_1db69449), color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                Spacer(Modifier.height(10.dp))
                                wrapped.longestBooksTop3.forEachIndexed { idx, (title, mins) ->
                                    val hB = mins / 60; val mB = mins % 60
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${idx + 1}.", color = Color(0xFF34D399), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(title, color = theme.textMain, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(if (hB > 0) "${hB}h ${mB}m" else "${mB}m", color = theme.textDim, fontSize = 10.sp)
                                        }
                                    }
                                    if (idx < wrapped.longestBooksTop3.size - 1) HorizontalDivider(color = Color(0x1A34D399), modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(48.dp))
                }

                // ── SLIDE 2: AUTOR Y GÉNERO FAVORITO ─────────────────────
                2 -> Column(slideModifier) {
                    Text("🏆 ${stringResource(R.string.txt_a6f46d56)}", color = Accent2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (wrapped.favoriteAuthor.isNotBlank()) {
                            WrappedHighlightCard(
                                emoji = stringResource(R.string.txt_2fc6405a), label = stringResource(R.string.wrapped_autor_favorito),
                                value = wrapped.favoriteAuthor,
                                sub = "${wrapped.favoriteAuthorBooks} ${stringResource(R.string.word_book)}${if (wrapped.favoriteAuthorBooks != 1) "s" else ""}",
                                color = Accent2, modifier = Modifier.weight(1f)
                            )
                        }
                        if (wrapped.favoriteGenre.isNotBlank()) {
                            WrappedHighlightCard(
                                emoji = "📚", label = stringResource(R.string.wrapped_genero_favorito),
                                value = displayGenre(wrapped.favoriteGenre).take(22),
                                sub = "${wrapped.favoriteGenreBooks} ${stringResource(R.string.word_book)}${if (wrapped.favoriteGenreBooks != 1) "s" else ""}",
                                color = Accent, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // Top 3 autores y géneros
                    if (wrapped.topAuthorsTop3.isNotEmpty() || wrapped.topGenresTop3.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (wrapped.topAuthorsTop3.isNotEmpty()) {
                                Surface(shape = RoundedCornerShape(16.dp), color = Accent2.copy(alpha = 0.1f), border = BorderStroke(1.dp, Accent2.copy(alpha = 0.3f)), modifier = Modifier.weight(1f)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(stringResource(R.string.txt_2fc6405a), fontSize = 22.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(stringResource(R.string.txt_a6f46d56), color = Accent2.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                        Spacer(Modifier.height(6.dp))
                                        wrapped.topAuthorsTop3.forEachIndexed { i, (name, n) ->
                                            Text("${i + 1}. $name", color = Accent2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val editions = wrapped.topAuthorsTop3Editions.getOrNull(i) ?: n
                                            val detail = if (editions > n) "   $n libro${if (n != 1) "s" else ""} ($editions ediciones)"
                                                         else "   $n libro${if (n != 1) "s" else ""}"
                                            Text(detail, color = Accent2.copy(alpha = 0.65f), fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                            if (wrapped.topGenresTop3.isNotEmpty()) {
                                Surface(shape = RoundedCornerShape(16.dp), color = Accent.copy(alpha = 0.1f), border = BorderStroke(1.dp, Accent.copy(alpha = 0.3f)), modifier = Modifier.weight(1f)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text("📚", fontSize = 22.sp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(stringResource(R.string.txt_5d69d53a), color = Accent.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                        Spacer(Modifier.height(6.dp))
                                        wrapped.topGenresTop3.forEachIndexed { i, (name, n) ->
                                            Text("${i + 1}. ${displayGenre(name).take(20)}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("   $n ${stringResource(R.string.word_book)}${if (n != 1) "s" else ""}", color = Accent.copy(alpha = 0.65f), fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(48.dp))
                }

                // ── SLIDE 3: MEJOR Y MÁS RÁPIDO ──────────────────────────
                3 -> Column(slideModifier) {
                    Text("⭐ ${stringResource(R.string.wrapped_mejor_puntuado)}", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    // v21.41: top 3 mejor puntuados (retrocompat con backups antiguos: si vacío, cae al single)
                    val top3Rated = wrapped.bestRatedTop3.ifEmpty {
                        if (wrapped.bestRatedTitle.isNotBlank())
                            listOf(Triple(wrapped.bestRatedTitle, wrapped.bestRatedScore, ""))
                        else emptyList()
                    }
                    top3Rated.forEachIndexed { i, (title, score, _) ->
                        val emoji = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "⭐" }
                        WrappedBookCard(emoji = emoji, label = stringResource(R.string.wrapped_mejor_puntuado), title = title,
                            detail = "$score/10 · ${ratingLabelLocalized(score)}", color = Gold, theme = theme)
                        Spacer(Modifier.height(10.dp))
                    }
                    if (top3Rated.isNotEmpty()) Spacer(Modifier.height(4.dp))
                    if (wrapped.fastestBookTitle.isNotBlank()) {
                        WrappedBookCard(emoji = "🚀", label = stringResource(R.string.wrapped_libro_mas_rapido), title = wrapped.fastestBookTitle,
                            detail = stringResource(R.string.wrapped_fastest_detail, String.format("%.1f", wrapped.fastestBookPpd), wrapped.fastestBookPages),
                            color = Green, theme = theme)
                        Spacer(Modifier.height(14.dp))
                    }
                    if (wrapped.droppedBooks > 0) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0x1AF87171), border = BorderStroke(1.dp, Color(0x33F87171)), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("❌", fontSize = 24.sp); Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(R.string.txt_e796083f), color = Red.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                    Text(stringResource(R.string.wrapped_dropped_text, wrapped.droppedBooks, if (wrapped.droppedBooks != 1) "s" else ""), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.txt_04ed38f0), color = theme.textDim, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(48.dp))
                }

                // ── SLIDE 4: GRÁFICAS ─────────────────────────────────────
                4 -> Column(slideModifier) {
                    Text("📊 ${stringResource(R.string.txt_bd81d36d)}", color = Accent2, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    // Barras mensuales
                    if (wrapped.pagesPerMonth.sum() > 0) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0x1A8B5CF6), border = BorderStroke(1.dp, Color(0x338B5CF6)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Text(stringResource(R.string.txt_bd81d36d), color = Accent2, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                Spacer(Modifier.height(14.dp))
                                val maxP = wrapped.pagesPerMonth.max().coerceAtLeast(1)
                                val months = listOf("E","F","M","A","M","J","J","A","S","O","N","D")
                                Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    wrapped.pagesPerMonth.forEachIndexed { i, p ->
                                        val ratio = (p.toFloat() / maxP).coerceIn(0f, 1f)
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                            if (p > 0) { Text(if (p >= 1000) "${p/1000}k" else "$p", color = Accent2, fontSize = 7.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(2.dp)) }
                                            Box(Modifier.fillMaxWidth().height((120.dp * ratio).coerceAtLeast(if (p > 0) 4.dp else 0.dp))
                                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                                .background(Brush.verticalGradient(listOf(Accent2, Accent2.copy(alpha = 0.5f)))))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    months.forEach { m -> Text(m, color = theme.textDim, fontSize = 9.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
                                }
                                val bestMonthIdx = wrapped.pagesPerMonth.indexOf(wrapped.pagesPerMonth.max())
                                if (bestMonthIdx >= 0 && wrapped.pagesPerMonth[bestMonthIdx] > 0) {
                                    // v21.41: nombres de mes según idioma de la app
                                    val wrappedMonthNames = LocalContext.current.resources.getStringArray(R.array.month_names_full).toList()
                                    Spacer(Modifier.height(8.dp))
                                    Text(stringResource(R.string.wrapped_best_month, wrappedMonthNames[bestMonthIdx], wrapped.pagesPerMonth[bestMonthIdx]), color = theme.textDim, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    // Donut género
                    if (wrapped.genreCountsTop6.isNotEmpty()) {
                        val gColors = listOf(Accent, Green, Sky, Amber, Red, Color(0xFF8B5CF6))
                        val totalG = wrapped.genreCountsTop6.sumOf { it.second }
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0x1A6366F1), border = BorderStroke(1.dp, Color(0x336366F1)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Text(stringResource(R.string.txt_59119316), color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                Spacer(Modifier.height(14.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    DonutChart(wrapped.genreCountsTop6.map { it.second }, gColors, Modifier.size(110.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        wrapped.genreCountsTop6.forEachIndexed { i, (g, n) ->
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(gColors[i % gColors.size]))
                                                Text("${displayGenre(g).take(18)}: $n (${if (totalG > 0) n * 100 / totalG else 0}%)", color = theme.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(48.dp))
                }

                // ── SLIDE 5: AÑO ANTERIOR + CIERRE ────────────────────────
                5 -> Column(slideModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (wrapped.previousYearBooks > 0 || wrapped.previousYearPages > 0) {
                        val dBooks = wrapped.totalBooks - wrapped.previousYearBooks
                        val dPages = wrapped.totalPages - wrapped.previousYearPages
                        val bookSign = if (dBooks > 0) "+" else if (dBooks < 0) "" else "±"
                        val pageSign = if (dPages > 0) "+" else if (dPages < 0) "" else "±"
                        Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Text("📊  VS ${year - 1}", color = theme.textDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${bookSign}$dBooks", color = if (dBooks >= 0) Green else Red, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                        Text(stringResource(R.string.wrapped_books_vs, wrapped.previousYearBooks), color = theme.textDim, fontSize = 10.sp, textAlign = TextAlign.Center)
                                    }
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${pageSign}${dPages.toLocaleString()}", color = if (dPages >= 0) Green else Red, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                        Text(stringResource(R.string.wrapped_pags_vs, wrapped.previousYearPages.toLocaleString()), color = theme.textDim, fontSize = 10.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                    // Cierre festivo
                    Text("🎉", fontSize = 52.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.wrapped_year_header, year), color = theme.textMain, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.txt_b7e522e3), color = theme.textDim, fontSize = 12.sp, textAlign = TextAlign.Center)
                            val footerParts = stringResource(R.string.txt_f51e272b).split("🎁")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                if (footerParts.size >= 2) {
                                    Text(footerParts[0].trimEnd() + " ", color = theme.textDim, fontSize = 11.sp)
                                    Icon(Icons.Default.CardGiftcard, null, tint = Accent, modifier = Modifier.size(14.dp))
                                    Text(" " + footerParts[1].trimStart(), color = theme.textDim, fontSize = 11.sp)
                                } else {
                                    Text(stringResource(R.string.txt_f51e272b), color = theme.textDim, fontSize = 11.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(48.dp))
                }
            }
        }

        // Flechas de navegación
        if (wrapped != null) {
            val corr = rememberCoroutineScope()
            if (pagerState.currentPage > 0) {
                IconButton(onClick = { corr.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
                    Icon(Icons.Default.KeyboardArrowLeft, null, tint = theme.textDim.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                }
            }
            if (pagerState.currentPage < 5) {
                IconButton(onClick = { corr.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                    Icon(Icons.Default.KeyboardArrowRight, null, tint = theme.textDim.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun WrappedBigStat(value: String, label: String, modifier: Modifier, color: Color) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = color.copy(alpha = 0.7f), fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun WrappedHighlightCard(emoji: String, label: String, value: String, sub: String, color: Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.1f), border = BorderStroke(1.dp, color.copy(alpha = 0.3f)), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.height(6.dp))
            Text(label, color = color.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, color = color.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

@Composable
fun WrappedBookCard(emoji: String, label: String, title: String, detail: String, color: Color, theme: Theme) {
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.08f), border = BorderStroke(1.dp, color.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = color.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(3.dp))
                Text(title, color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── WrappedHistoryScreen ──────────────────────────────────────────────────────

@Composable
fun WrappedHistoryScreen(vm: BooksViewModel, theme: Theme, onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val history = vm.wrappedHistory.sortedByDescending { it.year }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 24.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.txt_2d903c83), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (history.size == 1) stringResource(R.string.wrapped_history_count_one, history.size) else stringResource(R.string.wrapped_history_count_other, history.size), color = theme.textMuted, fontSize = 13.sp)
                Text(nextWrappedSubtitle(), color = Gold, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎁", fontSize = 48.sp); Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.txt_5718979a), color = theme.textMain, fontSize = 16.sp)
                    Text(stringResource(R.string.txt_80b7fc7c), color = theme.textDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history) { w ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = theme.surface,
                        border = BorderStroke(1.dp, theme.border),
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(w.year) }
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Year badge
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(Accent, Accent2))),
                                contentAlignment = Alignment.Center
                            ) { Text("${w.year}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.wrapped_history_year_label, w.year), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                val booksLabel = if (w.rereadBooks > 0)
                                    stringResource(R.string.wrapped_history_summary_reread, w.totalBooks, w.rereadBooks, w.totalPages.toLocaleString())
                                else
                                    stringResource(R.string.wrapped_history_summary, w.totalBooks, w.totalPages.toLocaleString())
                                Text(booksLabel, color = theme.textMuted, fontSize = 13.sp)
                                if (w.favoriteAuthor.isNotBlank())
                                    Text("✍️ ${w.favoriteAuthor}", color = theme.textDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(String.format("%.1f p/d", w.avgPagesPerDay), color = Green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (w.bestRatedTitle.isNotBlank())
                                    Text("⭐ ${w.bestRatedScore}/10", color = Gold, fontSize = 11.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.ChevronRight, null, tint = theme.textDim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

// ── StatsScreen ───────────────────────────────────────────────────────────────

@Composable
fun StatsScreen(vm: BooksViewModel, _prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit, onWrapped: (Int) -> Unit, onWrappedHistory: () -> Unit, onDetail: (Long) -> Unit = {}, onDetailWithDate: (Long, String) -> Unit = { _, _ -> }, onDailySessions: (String) -> Unit = {}) {
    // Solo FINISHED con fechas distintas (mismo día distorsiona velocidad)
    val finished = vm.books.filter {
        it.status == BookStatus.FINISHED &&
        it.startDate != null && it.endDate != null &&
        it.startDate != it.endDate &&
        !it.importedFromGoodreads
    }

    val wrappedYear  = wrappedWindowYear()
    val inWindow     = wrappedYear != -1

    // Filter state
    // null = never interacted (show placeholder); "" = "Todos" selected; non-blank = specific filter
    var filterGenre by remember { mutableStateOf<String?>(null) }
    var filterAuthor by remember { mutableStateOf<String?>(null) }
    var genreUserSelected by remember { mutableStateOf(false) }
    var authorUserSelected by remember { mutableStateOf(false) }
    var showGenreMenu by remember { mutableStateOf(false) }
    var showAuthorMenu by remember { mutableStateOf(false) }
    var statsView by remember { mutableStateOf(_prefs.getString("stats_view_mode", "charts") ?: "charts") }
    fun setStatsView(v: String) { statsView = v; _prefs.edit().putString("stats_view_mode", v).apply() }
    val showCharts = statsView == "charts"

    // Normalise stored genres: if a book has a raw API value that isn't in BOOK_GENRES,
    // map it on-the-fly so the filter only shows clean genre names.
    // Los dropdowns incluyen TODOS los libros (también importados de Goodreads — Kirkman, Fujimoto, Tatsu…)
    val allGenres = vm.books.flatMap { b -> b.genres }.distinct().sorted()
    val allAuthors = vm.books.map { it.author }.filter { it.isNotBlank() }.distinct().sorted()

    val filtered = finished
        .let { list -> if (filterGenre != null) list.filter { it.genres.contains(filterGenre) } else list }
        .let { list -> if (filterAuthor != null) list.filter { it.author == filterAuthor } else list }

    // Derive stats
    data class SpeedEntry(val book: Book, val ppd: Double, val days: Int)

    val speedList = filtered
        .filter { it.startDate != it.endDate && daysBetween(it.startDate!!, it.endDate!!) >= 2 }
        .map { b ->
            val sessPages = vm.sessions.filter { s -> s.bookId == b.id }.sumOf { it.pages }
            val d = daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1)
            val funcPages = if (b.firstFunctionalPage != null && b.lastFunctionalPage != null)
                (b.lastFunctionalPage - b.firstFunctionalPage + 1) else null
            val pages = when {
                sessPages > 0 -> sessPages
                funcPages != null && funcPages > 0 -> funcPages
                else -> b.pages
            }
            SpeedEntry(b, pages.toDouble() / d, d)
        }.sortedByDescending { it.ppd }

    val totalPages = filtered.sumOf { b ->
        vm.sessions.filter { s -> s.bookId == b.id }.sumOf { it.pages }.takeIf { it > 0 } ?: b.pages
    }
    val speedFiltered = filtered.filter { it.startDate != it.endDate && daysBetween(it.startDate!!, it.endDate!!) >= 2 }
    val avgPpd = if (speedFiltered.isNotEmpty()) speedFiltered.map { b ->
        val sp = vm.sessions.filter { s -> s.bookId == b.id }.sumOf { it.pages }
        val fp = if (b.firstFunctionalPage != null && b.lastFunctionalPage != null) (b.lastFunctionalPage - b.firstFunctionalPage + 1) else null
        val pages = when { sp > 0 -> sp; fp != null && fp > 0 -> fp; else -> b.pages }
        val d = daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1)
        pages.toDouble() / d
    }.average() else 0.0
    val avgDays = if (speedFiltered.isNotEmpty()) speedFiltered.map { b ->
        daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1)
    }.average() else 0.0
    val avgRating = filtered.filter { it.rating > 0 }.let { r -> if (r.isNotEmpty()) r.map { it.rating }.average() else null }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.txt_a6db8091), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.stats_finished_books, filtered.size), color = theme.textMuted, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("charts" to "📈", "table" to "📋", "heatmap" to "🔥").forEach { (mode, icon) ->
                    val active = statsView == mode
                    Surface(
                        onClick = { setStatsView(mode) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) Accent else theme.surface,
                        border = BorderStroke(1.dp, if (active) Accent else theme.border)
                    ) {
                        Text(icon, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                    }
                }
            }
            // Botón historial Wrapped (siempre visible si hay historial)



            // Botón historial Wrapped (siempre visible si hay historial)
            if (vm.wrappedHistory.isNotEmpty()) {
                IconButton(onClick = onWrappedHistory) {
                    Text("🎁", fontSize = 20.sp)
                }
            }
        }

        // Banner Wrapped (solo en ventana)
        if (inWindow) {
            val alreadySaved = vm.wrappedForYear(wrappedYear) != null
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x1AF59E0B),
                border = BorderStroke(1.dp, Color(0x4DF59E0B)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable { onWrapped(wrappedYear) }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎉", fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.wrapped_ready, wrappedYear), color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(if (alreadySaved) stringResource(R.string.wrapped_banner_saved) else stringResource(R.string.wrapped_banner_available), color = Gold.copy(alpha = 0.75f), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Gold)
                }
            }
        }

        // (Los filtros de Género y Autor se muestran ahora dentro de la tarjeta "Filtros" de las gráficas)

        if (statsView == "heatmap") {
            HeatmapView(vm = vm, theme = theme, onNavigateToSession = { bookId, date -> onDetailWithDate(bookId, date) }, onNavigateToDailySessions = onDailySessions)
        } else if (showCharts) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                item { StatsChartsView(vm, theme, filterGenre, { filterGenre = it; genreUserSelected = true }, allGenres, filterAuthor, { filterAuthor = it; authorUserSelected = true }, allAuthors) }
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📈", fontSize = 48.sp); Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.txt_13f65f8c), color = theme.textMain, fontSize = 16.sp)
                    Text(stringResource(R.string.txt_30fc9acb), color = theme.textDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Global summary card
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0x1A6366F1), border = BorderStroke(1.dp, Color(0x336366F1))) {
                        Column(Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.txt_316406f4), color = Color(0xFFA5B4FC), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth()) {
                                SummaryCell("${filtered.size}", stringResource(R.string.pill_leidos), Modifier.weight(1f), theme)
                                SummaryCell(totalPages.toLocaleString(), stringResource(R.string.txt_939f09a3), Modifier.weight(1f), theme)
                                SummaryCell(if (speedFiltered.isNotEmpty()) String.format("%.1f", avgPpd) else "—", stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, Green)
                                SummaryCell(if (speedFiltered.isNotEmpty()) String.format("%.0f d", avgDays) else "—", stringResource(R.string.pill_dias_libro), Modifier.weight(1f), theme)
                            }
                            if (avgRating != null) {
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = theme.border)
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.txt_a70ecc0a), color = theme.textMuted, fontSize = 13.sp)
                                    Spacer(Modifier.width(8.dp))
                                    val stars = kotlin.math.ceil(avgRating / 2.0).toInt().coerceIn(0, 5)
                                    Text(
                                        "★".repeat(stars) + "☆".repeat(5 - stars),
                                        color = Gold, fontSize = 15.sp, letterSpacing = 1.sp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(String.format("%.1f/10", avgRating), color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Fastest readers ranking — solo si hay libros con span >= 2 días
                if (speedList.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.txt_fca0bbb8), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        Text(stringResource(R.string.txt_012f4789), color = theme.textDim, fontSize = 11.sp)
                    }
                    items(speedList.take(20)) { entry ->
                        SpeedBookRow(entry.book, entry.ppd, entry.days, speedList.first().ppd, theme, onClick = { onDetail(entry.book.id) })
                    }
                }

                // By genre breakdown — siempre visible, mensaje si no hay géneros asignados
                if (filterGenre == null && filterAuthor == null) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.txt_667166ae), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    val genreGroups = finished.filter { it.genres.isNotEmpty() }
                        .flatMap { book -> book.genres.map { g -> g to book } }
                        .groupBy({ it.first }, { it.second })
                        .filter { it.key.isNotBlank() }
                        .entries.sortedByDescending { it.value.size }
                    if (genreGroups.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.txt_896b3fde),
                                color = theme.textDim,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }
                    } else {
                        items(genreGroups.take(10)) { (genre, books) ->
                            val avgSpeed = books
                                .map { b -> b.pages.toDouble() / daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1) }
                                .average()
                            GenreRow(genre, books.size, avgSpeed, theme)
                        }
                    }
                }

                // By author breakdown
                if (allAuthors.size > 1 && filterGenre == null && filterAuthor == null) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.txt_9303ba12), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    val authorGroups = finished.filter { it.author.isNotBlank() }
                        .groupBy { it.author }
                        .entries.sortedByDescending { it.value.size }
                    items(authorGroups.take(10)) { (author, books) ->
                        val avgSpeed = books.filter { it.startDate != null && it.endDate != null && it.startDate != it.endDate && daysBetween(it.startDate, it.endDate) >= 2 }
                            .map { b -> b.pages.toDouble() / daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1) }
                            .let { if (it.isNotEmpty()) it.average() else null }
                        AuthorStatRow(author, books.size, avgSpeed, theme)
                    }
                }

                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
fun SummaryCell(value: String, label: String, modifier: Modifier, theme: Theme, valueColor: Color? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor ?: theme.textMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = theme.textDim, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun SpeedBookRow(book: Book, ppd: Double, days: Int, maxPpd: Double, theme: Theme, onClick: () -> Unit = {}) {
    val fraction = if (maxPpd > 0) (ppd / maxPpd).toFloat().coerceIn(0f, 1f) else 0f
    Surface(shape = RoundedCornerShape(12.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(book.coverUrl, book.title, size = 44)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (book.author.isNotBlank()) Text(book.author, color = theme.textMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(String.format("%.1f p/d", ppd), color = Green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(fmtDaysLocalized(days), color = theme.textDim, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            // Speed bar — muestra la velocidad de este libro respecto al más rápido leído
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(theme.border)) {
                    Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(Accent, Green))))
                }
                Text(stringResource(R.string.txt_1f750a24), color = theme.textDim, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun GenreRow(genre: String, count: Int, avgPpd: Double?, theme: Theme) {
    // Pick a color per genre deterministically
    val genreColors = listOf(Accent, Accent2, Green, Sky, Gold, Color(0xFFEC4899), Color(0xFFF97316), Color(0xFF8B5CF6))
    val colorIndex = (genre.hashCode() and 0x7FFFFFFF) % genreColors.size
    val dotColor = genreColors[colorIndex]

    Surface(shape = RoundedCornerShape(12.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(displayGenre(genre), color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$count ${stringResource(R.string.word_book)}${if (count != 1) "s" else ""}", color = theme.textMuted, fontSize = 11.sp)
            }
            if (avgPpd != null) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(String.format("%.1f", avgPpd), color = Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.txt_2686dd75), color = theme.textDim, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun AuthorStatRow(author: String, count: Int, avgPpd: Double?, theme: Theme) {
    Surface(shape = RoundedCornerShape(10.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Accent2))
            Spacer(Modifier.width(10.dp))
            Text(author, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (count == 1) stringResource(R.string.shelf_books_count, count) else stringResource(R.string.shelf_books_count_plural, count), color = theme.textMuted, fontSize = 12.sp)
            if (avgPpd != null) {
                Spacer(Modifier.width(10.dp))
                Text(String.format("%.1f p/d", avgPpd), color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
fun SectionHeader(label: String, count: Int, color: Color, expanded: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label.uppercase(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
        Text("$count", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun EmptySectionHint(text: String, theme: Theme) { Text(text, color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(start = 18.dp, bottom = 10.dp)) }

@Composable
fun BookCard(
    book: Book,
    theme: Theme,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    isWidgetBook: Boolean = false,
    isRefreshingCover: Boolean = false,
    onRefreshCover: (() -> Unit)? = null,
    onApplyCoverUrl: ((String) -> Unit)? = null,
    onBroken: (() -> Unit)? = null,
    sessionPages: Int = -1,  // total de páginas leídas; -1 = desconocido
    sessionDays: Int = -1    // días únicos con sesión; -1 = desconocido
) {
    val stats = getStats(book)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCoverDialog by remember { mutableStateOf(false) }
    var coverUrlInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val coverImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && onApplyCoverUrl != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* algunos URIs no admiten permisos persistentes */ }
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val activeEdId = book.editions.firstOrNull { it.isActive }?.id
                val localPath = copyUriToInternalStorage(context, uri, book.id, activeEdId)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onApplyCoverUrl(localPath ?: uri.toString())
                }
            }
        }
    }

    if (showCoverDialog) {
        AlertDialog(
            onDismissRequest = { showCoverDialog = false },
            title = { Text(stringResource(R.string.txt_adbe3283), color = theme.textMain) },
            text = {
                Column {
                    Text(stringResource(R.string.txt_aa02a2da), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(value = coverUrlInput, onValueChange = { coverUrlInput = it }, placeholder = { Text(stringResource(R.string.txt_14f2b208), color = theme.textDim) }, colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { coverImagePicker.launch("image/*"); showCoverDialog = false }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Accent.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)) { Text(stringResource(R.string.txt_5cd0defc)) }
                }
            },
            dismissButton = { TextButton(onClick = { showCoverDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } },
            confirmButton = {
                TextButton(onClick = {
                    if (coverUrlInput.isNotBlank()) onApplyCoverUrl?.invoke(coverUrlInput.trim())
                    showCoverDialog = false
                }) { Text(stringResource(R.string.txt_f0ed2dc3), color = Accent) }
            },
            containerColor = theme.bgMid
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_b375487f), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_4259b7dc, book.title), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete?.invoke() }) {
                    Text(stringResource(R.string.txt_5b5c9f9d), color = Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.txt_847607d7), color = Accent)
                }
            }
        )
    }

    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
        Row(Modifier.padding(12.dp)) {
            // ── Cover con badges ─────────────────────────────────────────────
            Box(modifier = Modifier.size(70.dp, (70 * 1.42f).dp)) {
                BookCover(book.coverUrl, book.title, size = 70, onBroken = onBroken, isbnFallback = book.isbn)

                // Widget badge — arriba izquierda
                if (isWidgetBook) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .offset(x = (-5).dp, y = (-5).dp)
                            .clip(CircleShape)
                            .background(Accent)
                            .border(2.dp, theme.surface, CircleShape)
                            .align(Alignment.TopStart),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📺", fontSize = 10.sp, lineHeight = 10.sp)
                    }
                }

                // Refresh button — abajo izquierda
                if (onRefreshCover != null) {
                    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "refresh")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.LinearEasing)
                        ), label = "rot"
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .offset(x = (-5).dp, y = 5.dp)
                            .clip(CircleShape)
                            .background(Sky)
                            .border(2.dp, theme.surface, CircleShape)
                            .align(Alignment.BottomStart)
                            .clickable(enabled = !isRefreshingCover) { onRefreshCover() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh cover",
                            tint = Color.White,
                            modifier = Modifier
                                .size(13.dp)
                                .then(if (isRefreshingCover) Modifier.rotate(rotation) else Modifier)
                        )
                    }
                }

                // Warning badge — arriba derecha (sin portada encontrada)
                if (book.noCoverFound) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .offset(x = 5.dp, y = (-5).dp)
                            .clip(CircleShape)
                            .background(Amber)
                            .border(2.dp, theme.surface, CircleShape)
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚠", fontSize = 10.sp, lineHeight = 10.sp)
                    }
                }

                // Change cover button — abajo derecha (mismo estilo que refresh)
                if (onApplyCoverUrl != null) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .offset(x = 5.dp, y = 5.dp)
                            .clip(CircleShape)
                            .background(Accent)
                            .border(2.dp, theme.surface, CircleShape)
                            .align(Alignment.BottomEnd)
                            .clickable {
                                coverUrlInput = book.coverUrl ?: ""
                                showCoverDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.txt_adbe3283),
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(book.title, color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (onDelete != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Red.copy(alpha = 0.12f))
                                .clickable { showDeleteConfirm = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.txt_b375487f),
                                tint = Red.copy(alpha = 0.7f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        color = theme.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (book.genres.isNotEmpty()) Text(book.genres.map { displayGenre(it) }.joinToString(" · "), color = accentForTheme(theme).copy(alpha = 0.85f), fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val funcLabel = if (book.firstFunctionalPage != null && book.lastFunctionalPage != null)
                        " (${book.lastFunctionalPage - book.firstFunctionalPage + 1}p)"
                    else if (book.lastFunctionalPage != null)
                        stringResource(R.string.func_pages_upto, book.lastFunctionalPage)
                    else ""
                    Text("📄 ${book.pages} ${stringResource(R.string.word_pages_abbr)}$funcLabel", color = theme.textMuted, fontSize = 12.sp)
                    if (stats != null && (book.status == BookStatus.FINISHED || book.status == BookStatus.REREADING) && stats.pagesPerDay != null) {
                        // Con sesiones: media real por días con sesión. Sin sesiones (fechas
                        // añadidas a posteriori): stats.pagesPerDay (funcionales o totales / días).
                        val ppdDisplay = when {
                            sessionPages > 0 && sessionDays >= 1 -> sessionPages.toDouble() / sessionDays
                            sessionPages > 0 && stats.days > 0   -> sessionPages.toDouble() / stats.days
                            else -> stats.pagesPerDay
                        }
                        Text("⚡ ${String.format("%.1f", ppdDisplay)} p/d", color = theme.textMuted, fontSize = 12.sp)
                    }
                    if (stats != null && book.status == BookStatus.READING)
                        Text("⏳ ${fmtDaysLocalized(stats.days)}", color = theme.textMuted, fontSize = 12.sp)
                    if (book.status == BookStatus.REREADING)
                        Text(stringResource(R.string.txt_1ee4f1c7), color = Color(0xFF06B6D4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                if (book.comment.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("💬 ${book.comment}", color = theme.textDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (book.rating > 0) { Spacer(Modifier.height(4.dp)); MiniRating(book.rating) }
            }
        }
    }
}

@Composable
fun BookCover(url: String?, _title: String, size: Int, onBroken: (() -> Unit)? = null, isbnFallback: String? = null) {
    // Estado local para fallback: si la imagen principal falla y tenemos ISBN,
    // reintentamos con la URL OL por ISBN antes de marcar como rota
    var fallbackTriggered by remember(url) { mutableStateOf(false) }
    val effectiveUrl: String? = when {
        url.isNullOrBlank() -> null
        fallbackTriggered && !isbnFallback.isNullOrBlank() -> {
            val clean = isbnFallback.replace(Regex("[-\\s]"), "")
            if (clean.matches(Regex("[0-9Xx]{10,13}"))) "https://covers.openlibrary.org/b/isbn/$clean-L.jpg"
            else null
        }
        else -> url
    }
    if (!effectiveUrl.isNullOrBlank()) {
        // Si empieza por '/' es una ruta local al almacenamiento interno
        val isLocal = effectiveUrl.startsWith("/")
        val localFile = if (isLocal) java.io.File(effectiveUrl) else null
        val lastMod = localFile?.lastModified() ?: 0L
        // Cache-buster para portadas locales: si el archivo cambia (sobreescritura tras cambio
        // de portada) la key cambia y Coil recarga inmediatamente sin cerrar pantalla.
        val cacheKey = if (isLocal) "$effectiveUrl-$lastMod" else effectiveUrl
        val model = localFile ?: effectiveUrl
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onError = {
                // Si ya hemos probado el fallback ISBN o no hay ISBN → marcar rota
                if (fallbackTriggered || isbnFallback.isNullOrBlank()) {
                    onBroken?.invoke()
                } else {
                    fallbackTriggered = true
                }
            },
            modifier = Modifier.size(size.dp, (size * 1.42f).dp).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(Modifier.size(size.dp, (size * 1.42f).dp).clip(RoundedCornerShape(8.dp)).background(Brush.verticalGradient(listOf(Accent, Accent2))), contentAlignment = Alignment.Center) { Text("📖", fontSize = (size / 3.2f).sp) }
    }
}

@Composable
fun MiniRating(rating: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val filledStars = ((rating + 1) / 2).coerceIn(0, 5)
        Text(
            "★".repeat(filledStars) + "☆".repeat(5 - filledStars),
            color = Gold,
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.width(4.dp))
        Text("$rating/10", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusBadge(status: BookStatus) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.background(statusColor(status), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(statusLabel(status), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FooterStats(finished: List<Book>, _theme: Theme) {
    val total = finished.sumOf { it.pages }
    // Exclude same-day books (startDate == endDate) from speed average
    val speedBooks = finished.filter { b -> b.startDate != null && b.endDate != null && b.startDate != b.endDate && daysBetween(b.startDate, b.endDate) >= 2 }
    val avg = if (speedBooks.isNotEmpty()) speedBooks.map { b -> b.pages.toDouble() / daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1) }.average() else null
    val rated = finished.filter { it.rating > 0 }
    val avgRating = if (rated.isNotEmpty()) rated.map { it.rating }.average() else null
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0x1A6366F1), border = BorderStroke(1.dp, Color(0x336366F1))) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.txt_9096e7e0), color = Color(0xFFA5B4FC), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                FooterStat("${finished.size}", stringResource(R.string.pill_leidos), Modifier.weight(1f))
                FooterStat(total.toLocaleString(), stringResource(R.string.txt_939f09a3), Modifier.weight(1f))
                FooterStat(if (avg != null) String.format("%.1f", avg) else "—", stringResource(R.string.pill_pags_dia), Modifier.weight(1f))
                if (avgRating != null) FooterStat(String.format("%.1f", avgRating) + "/10", "Punt.", Modifier.weight(1f))
            }
        }
    }
}

fun Int.toLocaleString() = String.format("%,d", this).replace(',', '.')

@Composable
fun FooterStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextMainD, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextDimD, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

// ── AuthorBooksScreen ─────────────────────────────────────────────────────────

@Composable
fun AuthorBooksScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, author: String, onBack: () -> Unit, onDetail: (Long) -> Unit) {
    val authorBooks = vm.books.filter { it.author.equals(author, ignoreCase = true) }
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    val byStatus = linkedMapOf(
        BookStatus.READING   to authorBooks.filter { it.status == BookStatus.READING }.applySort(sortOrder),
        BookStatus.REREADING to authorBooks.filter { it.status == BookStatus.REREADING }.applySort(sortOrder),
        BookStatus.FINISHED  to authorBooks.filter { it.status == BookStatus.FINISHED }.applySort(sortOrder),
        BookStatus.PENDING   to authorBooks.filter { it.status == BookStatus.PENDING }.applySort(sortOrder),
        BookStatus.DROPPED   to authorBooks.filter { it.status == BookStatus.DROPPED }.applySort(sortOrder)
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(author, color = theme.textMain, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (authorBooks.size == 1) stringResource(R.string.shelf_books_count, authorBooks.size) else stringResource(R.string.shelf_books_count_plural, authorBooks.size), color = theme.textMuted, fontSize = 13.sp)
            }
            Box {
                IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Filled.Sort, null, tint = theme.textMuted) }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(text = { Text(sortLabel(order), color = if (sortOrder == order) Accent else theme.textMain) }, onClick = { sortOrder = order; showSortMenu = false })
                    }
                }
            }
        }
        LazyColumn {
            byStatus.forEach { (status, list) ->
                if (list.isNotEmpty()) {
                    item { SectionHeader(statusLabel(status), list.size, statusColor(status), true) {} }
                    items(list) { book ->
                        val context = LocalContext.current
                        val widgetBookId = com.lecturameter.widget.loadWidgetBook(context)
                        BookCard(
                            book, theme,
                            onClick = { onDetail(book.id) },
                            onDelete = { vm.deleteBook(book.id, prefs) },
                            isWidgetBook = widgetBookId == book.id,
                            isRefreshingCover = vm.isCoverRefreshing(book.id),
                            onRefreshCover = {
                                vm.refreshCover(book.id, prefs) { coverFound, genreFound ->
                                    if (coverFound || genreFound) {
                                        refreshWidgetForBookIfSelected(context, book.id, clearCoverCache = true)
                                    }
                                    if (!coverFound || !genreFound) {
                                        val missing = listOfNotNull(
                                            if (!coverFound) context.getString(R.string.word_cover) else null,
                                            if (!genreFound) context.getString(R.string.word_genre) else null
                                        )
                                        val msg = context.getString(R.string.msg_not_found_prefix, missing.joinToString(context.getString(R.string.word_join_nor)))
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onApplyCoverUrl = { newUrl ->
                                vm.updateCover(book.id, newUrl, prefs)
                                refreshWidgetForBookIfSelected(context, book.id, clearCoverCache = true)
                            },
                            sessionPages = vm.sessionsForBook(book.id).sumOf { it.pages },
                            sessionDays = vm.sessionsForBook(book.id).map { it.date }.toSet().size
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── BookSearchScreen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSearchScreen(
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    theme: Theme,
    onBack: () -> Unit,
    initialQuery: String = "",
    isbnFromScanner: String? = null,
    onClearIsbnFromScanner: () -> Unit = {},
    onAddWithIsbn: (String) -> Unit = {},
    onScanIsbn: () -> Unit = {}
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<OpenLibraryResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var networkError by remember { mutableStateOf(false) }
    var selectedResult by remember { mutableStateOf<OpenLibraryResult?>(null) }
    val scope = rememberCoroutineScope()
    // Dialog de ISBN escaneado — se activa cuando llega un ISBN real desde la cámara
    var showScanDialog by remember { mutableStateOf(false) }
    var dialogIsbn by remember { mutableStateOf("") }
    val mainActivity = context as? MainActivity

    // Cuando llega un ISBN escaneado desde ScannerActivity (vía isbnFromScanner)
    LaunchedEffect(isbnFromScanner) {
        if (!isbnFromScanner.isNullOrBlank() && isbnFromScanner != "__scanning__") {
            dialogIsbn = isbnFromScanner
            showScanDialog = true
        }
    }

    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) { true } // si falla la comprobación, intentar de todos modos
    }

    fun doSearch() {
        if (query.isBlank()) return
        searched = true; errorMsg = ""; networkError = false; results = emptyList()
        if (!isOnline()) {
            networkError = true
            errorMsg = context.getString(R.string.err_no_internet_search)
            return
        }
        isLoading = true
        scope.launch {
            val found = try { searchOpenLibrary(query) } catch (_: Exception) { emptyList() }
            results = found
            isLoading = false
            if (found.isEmpty()) {
                // Re-verificar conectividad: si seguimos offline tras la búsqueda fallida
                if (!isOnline()) {
                    networkError = true
                    errorMsg = context.getString(R.string.err_no_internet_search)
                } else {
                    errorMsg = context.getString(R.string.txt_57be44d3, query)
                }
            }
        }
    }

    // Si viene query inicial del scanner, sanitizar y lanzar búsqueda automáticamente
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            // Solo ISBN: solo dígitos/X. Si no es ISBN puro, usar tal cual (búsqueda manual)
            val isIsbnLike = initialQuery.replace(Regex("[^\\dXx]"), "").length in 10..13
            if (isIsbnLike) {
                query = initialQuery.replace(Regex("[^\\dXx]"), "").uppercase()
            }
            doSearch()
        }
    }

    // Dialog ISBN escaneado desde BookSearchScreen
    if (showScanDialog && dialogIsbn.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false; onClearIsbnFromScanner() },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_016a31c7), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = theme.surface) {
                        Text(dialogIsbn, color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    Text(stringResource(R.string.txt_55800f4c), color = theme.textDim, fontSize = 14.sp)
                    Button(
                        onClick = {
                            showScanDialog = false
                            query = dialogIsbn
                            onClearIsbnFromScanner()
                            doSearch()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.txt_69e20782), fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = {
                            showScanDialog = false
                            onClearIsbnFromScanner()
                            onAddWithIsbn(dialogIsbn)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Accent),
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.txt_97225860), color = Accent) }
                }
            },
            confirmButton = {}
        )
    }

    selectedResult?.let { r ->
        var status by remember { mutableStateOf(BookStatus.PENDING) }
        var statusExpanded by remember { mutableStateOf(false) }
        var searchFirstFunc by remember { mutableStateOf("") }
        var searchLastFunc  by remember { mutableStateOf("") }
        var searchStartDate by remember { mutableStateOf("") }
        var searchEndDate   by remember { mutableStateOf("") }
        var searchRating    by remember { mutableStateOf(0) }
        val needsDates  = status in listOf(BookStatus.READING, BookStatus.FINISHED, BookStatus.REREADING, BookStatus.DROPPED)
        val needsEndDate = status in listOf(BookStatus.FINISHED, BookStatus.REREADING, BookStatus.DROPPED)
        AlertDialog(onDismissRequest = { selectedResult = null }, containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_281db514), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BookCover(r.coverUrl, r.title, size = 60)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (r.author.isNotBlank()) Text(r.author, color = theme.textMuted, fontSize = 13.sp)
                            if (r.pages > 1) Text(stringResource(R.string.search_pages_count, r.pages), color = theme.textDim, fontSize = 12.sp)
                            else Text(stringResource(R.string.txt_0c78d77a), color = Amber, fontSize = 12.sp)
                            if (r.publishYear.isNotBlank()) Text(r.publishYear, color = theme.textDim, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.txt_24610ea2), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = it }) {
                        OutlinedTextField(value = statusLabel(status), onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = fieldColors(theme))
                        ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                            listOf(BookStatus.READING, BookStatus.FINISHED, BookStatus.REREADING, BookStatus.PENDING, BookStatus.DROPPED).forEach { s -> DropdownMenuItem(text = { Text(statusLabel(s), color = theme.textMain) }, onClick = { status = s; statusExpanded = false }) }
                        }
                    }
                    // Fechas — siempre visibles, activadas según estado
                    if (status != BookStatus.PENDING) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.txt_1f34cadf), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                                OutlinedTextField(
                                    value = searchStartDate,
                                    onValueChange = { if (it.length <= 10) searchStartDate = it },
                                    placeholder = { Text(stringResource(R.string.txt_d047c2a8), color = theme.textDim, fontSize = 11.sp) },
                                    singleLine = true, colors = fieldColors(theme),
                                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (needsEndDate) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.txt_e706900d), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    OutlinedTextField(
                                        value = searchEndDate,
                                        onValueChange = { if (it.length <= 10) searchEndDate = it },
                                        placeholder = { Text(stringResource(R.string.txt_d047c2a8), color = theme.textDim, fontSize = 11.sp) },
                                        singleLine = true, colors = fieldColors(theme),
                                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    // Puntuación
                    if (status == BookStatus.FINISHED) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.txt_7a9622a0), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..10).forEach { n ->
                                Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(if (n <= searchRating) Amber.copy(alpha = 0.85f) else theme.surface).border(1.dp, if (n <= searchRating) Amber else theme.border, RoundedCornerShape(6.dp)).clickable { searchRating = if (searchRating == n) 0 else n }, contentAlignment = Alignment.Center) {
                                    Text("$n", color = if (n <= searchRating) Color.White else theme.textDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.txt_066bbf84), color = theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchFirstFunc, onValueChange = { searchFirstFunc = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.txt_3c75ceda), color = theme.textDim, fontSize = 11.sp) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = searchLastFunc, onValueChange = { searchLastFunc = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.txt_27de21f4), color = theme.textDim, fontSize = 11.sp) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val storedStart = searchStartDate.trim().let { d -> if (d.length == 10) displayToStored(d) else if (needsDates) today() else null }
                    val storedEnd   = searchEndDate.trim().let { d -> if (d.length == 10) displayToStored(d) else if (needsEndDate) today() else null }
                    val newBook = Book(title = r.title, author = r.author, pages = if (r.pages > 0) r.pages else 1, status = status, startDate = storedStart, endDate = storedEnd, rating = if (status == BookStatus.FINISHED) searchRating else 0, coverUrl = r.coverUrl, isbn = r.isbn, genres = mapApiGenre(r.genre).ifEmpty { if (r.genre.isNotBlank()) listOf("Otro") else emptyList() }, firstFunctionalPage = searchFirstFunc.toIntOrNull(), lastFunctionalPage = searchLastFunc.toIntOrNull())
                    val firstEdition = BookEdition(id = newBook.id, language = "mul", languageLabel = "Edición principal", flag = "🌐", title = newBook.title, pages = newBook.pages, coverUrl = newBook.coverUrl, isbn = newBook.isbn, isActive = true)
                    vm.addBook(newBook.copy(editions = listOf(firstEdition)), prefs)
                    selectedResult = null
                }, colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.txt_d20f652b)) }
            },
            dismissButton = { TextButton(onClick = { selectedResult = null }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.txt_0b059290), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = query, onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.txt_a4fddd8f), color = theme.textDim, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = theme.textDim, modifier = Modifier.size(20.dp)) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null, tint = theme.textDim, modifier = Modifier.size(18.dp)) } },
                modifier = Modifier.weight(1f), colors = fieldColors(theme), shape = RoundedCornerShape(12.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { doSearch() })
            )
            Button(onClick = { doSearch() }, colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), enabled = query.isNotBlank() && !isLoading) { Text(stringResource(R.string.txt_113f7428), fontWeight = FontWeight.Bold) }
        }
        // Botón escanear ISBN — fila separada debajo de la barra de búsqueda
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.surface)
                .clickable(onClick = onScanIsbn)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_barcode), contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.txt_e82171d9), color = theme.textDim, fontSize = 13.sp)
        }
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Accent); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.txt_65dc881f), color = theme.textMuted, fontSize = 14.sp) } }
            errorMsg.isNotBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (networkError) "📡" else "🔍", fontSize = 40.sp); Spacer(Modifier.height(12.dp)); Text(errorMsg, color = if (networkError) Red else theme.textMuted, fontSize = 14.sp, fontWeight = if (networkError) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center); Spacer(Modifier.height(8.dp)); Text(if (networkError) stringResource(R.string.err_check_wifi_retry) else stringResource(R.string.err_try_other_language), color = if (networkError) Red.copy(alpha = 0.7f) else theme.textDim, fontSize = 12.sp, textAlign = TextAlign.Center) } }
            !searched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🔎", fontSize = 48.sp); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.txt_af80d2f5), color = theme.textMain, fontSize = 16.sp) } }
            else -> {
                Text(stringResource(R.string.search_results_summary, results.size), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(results) { result -> SearchResultCard(result, theme) { selectedResult = result } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(result: OpenLibraryResult, theme: Theme, onAdd: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(result.coverUrl, result.title, size = 60)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(result.title, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (result.author.isNotBlank()) Text(result.author, color = theme.textMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                    if (result.pages > 0) Text("📄 ${result.pages} págs", color = theme.textDim, fontSize = 11.sp)
                    if (result.publishYear.isNotBlank()) Text("📅 ${result.publishYear}", color = theme.textDim, fontSize = 11.sp)
                }
                if (result.genre.isNotBlank()) Text(mapApiGenre(result.genre).joinToString(" · ").ifBlank { result.genre }.take(50), color = Accent.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onAdd, modifier = Modifier.size(36.dp).clip(CircleShape).background(Accent)) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
    }
}

// ── LanguageSelector ──────────────────────────────────────────────────────────
@Composable
fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (code: String, label: String, flag: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val languages = listOf(
        Triple("mul", "Edición principal", "🌐"),
        Triple("es",  "Español",           "🇪🇸"),
        Triple("en-us","English (US)",     "🇺🇸"),
        Triple("en-uk","English (UK)",     "🇬🇧"),
        Triple("en",  "English",           "🌐"),
        Triple("fr",  "Français",          "🇫🇷"),
        Triple("de",  "Deutsch",           "🇩🇪"),
        Triple("it",  "Italiano",          "🇮🇹"),
        Triple("pt",  "Português",         "🇵🇹"),
        Triple("ca",  "Català",            "🏴󠁥󠁳󠁣󠁴󠁿"),
        Triple("ja",  "日本語",              "🇯🇵"),
        Triple("zh",  "中文",               "🇨🇳"),
        Triple("ko",  "한국어",              "🇰🇷")
    )
    var expanded by remember { mutableStateOf(false) }
    val selected = languages.firstOrNull { it.first == selectedLanguage } ?: languages.first()
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (expanded) Accent else Color.Gray.copy(alpha = 0.3f))
        ) {
            Text("${selected.third} ${selected.second}", fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { (code, label, flag) ->
                DropdownMenuItem(
                    text = { Text("$flag $label", color = if (code == selectedLanguage) Accent else Color.Unspecified, fontWeight = if (code == selectedLanguage) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onLanguageSelected(code, label, flag); expanded = false }
                )
            }
        }
    }
}

// ── AddScreen ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    theme: Theme,
    onBack: () -> Unit,
    externalIsbn: String? = null,
    onClearExternalIsbn: () -> Unit = {},
    onScanIsbn: () -> Unit = {}
) {
    var title by remember { mutableStateOf("") }; var author by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf("") }; var isbn by remember { mutableStateOf("") }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }; var status by remember { mutableStateOf(BookStatus.READING) }
    var startDate by remember { mutableStateOf(todayDisplay()) }; var endDate by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0) }; var comment by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }; var statusExpanded by remember { mutableStateOf(false) }; var genreExpanded by remember { mutableStateOf(false) }
    var showOnePage by remember { mutableStateOf(false) }
    var editionLanguage by remember { mutableStateOf("es") }; var editionLanguageLabel by remember { mutableStateOf("Español") }; var editionFlag by remember { mutableStateOf("🇪🇸") }
    var firstFuncPage by remember { mutableStateOf("") }; var lastFuncPage by remember { mutableStateOf("") }
    var manualCoverUrl by remember { mutableStateOf<String?>(null) }
    var showAddCoverDialog by remember { mutableStateOf(false) }
    var addCoverUrlInput by remember { mutableStateOf("") }
    val addCoverPreviewUrl: String? = when {
        manualCoverUrl != null -> manualCoverUrl
        isbn.trim().length >= 10 -> "https://covers.openlibrary.org/b/isbn/${isbn.trim()}-M.jpg"
        else -> null
    }

    // Rellenar ISBN si viene de scanner externo (solo dígitos/X, 10-13 chars)
    // v20.9: también autorellena título, autor, páginas y géneros vía API
    var isbnSearching by remember { mutableStateOf(false) }
    var isbnAutoError by remember { mutableStateOf("") }
    LaunchedEffect(externalIsbn) {
        if (!externalIsbn.isNullOrBlank()) {
            val safeIsbn = externalIsbn.replace(Regex("[^\\dXx]"), "").uppercase()
            if (safeIsbn.length in 10..13) {
                isbn = safeIsbn
                isbnSearching = true
                isbnAutoError = ""
                // withContext en vez de launch anidado: si LaunchedEffect se cancela,
                // la búsqueda también se cancela (sin race condition).
                val meta = withContext(Dispatchers.IO) { fetchIsbnFullMetadata(safeIsbn) }
                isbnSearching = false
                if (meta.title != null && title.isBlank()) title = meta.title
                if (meta.author != null && author.isBlank()) author = meta.author
                if (meta.pages != null && pages.isBlank()) pages = meta.pages.toString()
                if (meta.genres.isNotEmpty() && genres.isEmpty()) genres = meta.genres
                if (meta.coverUrl != null && manualCoverUrl == null) manualCoverUrl = meta.coverUrl
                if (meta.title == null && meta.author == null && meta.pages == null) {
                    isbnAutoError = "No se han encontrado datos para este ISBN"
                }
            }
            onClearExternalIsbn()
        }
    }

    val addContext = androidx.compose.ui.platform.LocalContext.current
    val addImagePickerReal = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val coversDir = java.io.File(addContext.filesDir, "covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val tmpId = System.currentTimeMillis()
            val dest = java.io.File(coversDir, "add_tmp_$tmpId.jpg")
            addContext.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            manualCoverUrl = dest.absolutePath
        } catch (_: Exception) {}
    }

    if (showAddCoverDialog) {
        AlertDialog(
            onDismissRequest = { showAddCoverDialog = false },
            title = { Text(stringResource(R.string.txt_41118960), color = theme.textMain) },
            text = {
                Column {
                    Text(stringResource(R.string.txt_aa02a2da), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(value = addCoverUrlInput, onValueChange = { addCoverUrlInput = it }, placeholder = { Text(stringResource(R.string.txt_14f2b208), color = theme.textDim) }, colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { addImagePickerReal.launch("image/*"); showAddCoverDialog = false }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Accent.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)) { Text(stringResource(R.string.txt_a8830f9a)) }
                }
            },
            confirmButton = { TextButton(onClick = { if (addCoverUrlInput.isNotBlank()) { manualCoverUrl = addCoverUrlInput.trim() }; showAddCoverDialog = false }) { Text(stringResource(R.string.txt_f0ed2dc3), color = Accent) } },
            dismissButton = { TextButton(onClick = { showAddCoverDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } },
            containerColor = theme.bgMid
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 28.dp, bottom = 20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.txt_29b6d9fc), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(20.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
            Column(Modifier.padding(24.dp)) {
                // Cover preview + button
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (addCoverPreviewUrl != null) {
                            BookCover(addCoverPreviewUrl, title, size = 90)
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedButton(
                            onClick = { addCoverUrlInput = manualCoverUrl ?: ""; showAddCoverDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Accent.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                        ) {
                            Text(if (manualCoverUrl != null) "✏️ Cambiar portada" else "📷 Añadir portada", fontSize = 12.sp)
                        }
                        if (manualCoverUrl != null) {
                            TextButton(onClick = { manualCoverUrl = null; addCoverUrlInput = "" }) {
                                Text(stringResource(R.string.txt_ba94296a), color = Red, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FormField("Title *", title, "The book's name", theme) { title = it }
                FormField(stringResource(R.string.txt_c481b00a), author, "Author name", theme) { author = it }
                FormField("Pages *", pages, "350", theme, KeyboardType.Number) { pages = it }
                // Páginas funcionales (opcionales) — entre páginas y género
                Text(stringResource(R.string.txt_066bbf84), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                Text(stringResource(R.string.txt_3d8b847e), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_8803fa48), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = firstFuncPage, onValueChange = { firstFuncPage = it.filter { c -> c.isDigit() } },
                            placeholder = { Text(stringResource(R.string.txt_a7931e50), color = theme.textDim) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_11938ee1), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = lastFuncPage, onValueChange = { lastFuncPage = it.filter { c -> c.isDigit() } },
                            placeholder = { Text(stringResource(R.string.txt_5dedc405), color = theme.textDim) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // Género — multi-select hasta 2
                Text(stringResource(R.string.txt_57d644ad), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                ExposedDropdownMenuBox(expanded = genreExpanded, onExpandedChange = { genreExpanded = it }) {
                    OutlinedTextField(
                        value = if (genres.isEmpty()) "" else genres.map { displayGenre(it) }.joinToString(" · "),
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text(stringResource(R.string.txt_84a8f3ea), color = theme.textDim, fontSize = 13.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genreExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor().padding(bottom = 16.dp),
                        colors = fieldColors(theme),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(expanded = genreExpanded, onDismissRequest = { genreExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.txt_bddf53d0), color = theme.textDim, fontSize = 13.sp) }, onClick = { genres = emptyList(); genreExpanded = false })
                        BOOK_GENRES.filter { it != "Otro" }.forEach { g ->
                            val selected = g in genres
                            DropdownMenuItem(
                                text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (selected) Text("✓", color = Accent, fontWeight = FontWeight.Bold)
                                    Text(displayGenre(g), color = if (selected) Accent else theme.textMain, fontSize = 13.sp)
                                }},
                                onClick = {
                                    genres = if (selected) genres - g
                                    else if (genres.size < 2) genres + g
                                    else genres // ya hay 2, ignorar
                                    if (genres.size == 2) genreExpanded = false
                                }
                            )
                        }
                    }
                }
                Text(stringResource(R.string.txt_4239bda5), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                LanguageSelector(selectedLanguage = editionLanguage, onLanguageSelected = { code, label, flag -> editionLanguage = code; editionLanguageLabel = label; editionFlag = flag }, modifier = Modifier.padding(bottom = 16.dp))
                // ISBN con botón de escaneo
                Text(stringResource(R.string.txt_fb84daae), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = if (isbnSearching || isbnAutoError.isNotEmpty()) 4.dp else 16.dp)) {
                    OutlinedTextField(
                        value = isbn,
                        onValueChange = { isbn = it },
                        placeholder = { Text(stringResource(R.string.txt_eb9316fa), color = theme.textDim, fontSize = 13.sp) },
                        colors = fieldColors(theme),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onScanIsbn,
                        modifier = Modifier.size(48.dp).background(Accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_barcode), contentDescription = "Scan ISBN", tint = Accent, modifier = Modifier.size(22.dp))
                    }
                }
                // v20.9: estado del autorelleno por ISBN
                if (isbnSearching) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Accent)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_f01d3d6b), color = theme.textDim, fontSize = 12.sp)
                    }
                } else if (isbnAutoError.isNotEmpty()) {
                    Text(isbnAutoError, color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                }
                Text(stringResource(R.string.txt_a3b8e497), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                OutlinedTextField(value = comment, onValueChange = { comment = it }, placeholder = { Text(stringResource(R.string.txt_f52cebe0), color = theme.textDim, fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).heightIn(min = 80.dp), colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), maxLines = 4)
                Text(stringResource(R.string.txt_3397e69c), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = it }) {
                    OutlinedTextField(value = statusLabel(status), onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = fieldColors(theme))
                    ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                        listOf(BookStatus.READING, BookStatus.FINISHED, BookStatus.REREADING, BookStatus.PENDING, BookStatus.DROPPED).forEach { s -> DropdownMenuItem(text = { Text(statusLabel(s), color = theme.textMain) }, onClick = { status = s; statusExpanded = false; if (s == BookStatus.PENDING) { startDate = ""; endDate = "" } else if (startDate.isEmpty()) startDate = todayDisplay() }) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (status == BookStatus.READING || status == BookStatus.FINISHED || status == BookStatus.REREADING || status == BookStatus.DROPPED) FormField(stringResource(R.string.form_start_date), startDate, todayDisplay(), theme, KeyboardType.Ascii) { startDate = it }
                if (status == BookStatus.FINISHED || status == BookStatus.REREADING) {
                    FormField(stringResource(R.string.form_end_date), endDate, todayDisplay(), theme, KeyboardType.Ascii) { endDate = it }
                    Text(stringResource(R.string.txt_950a32e2), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
                    RatingSelector(rating) { rating = it }; Spacer(Modifier.height(16.dp))
                }
                if (error.isNotEmpty()) Text(error, color = Red, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                if (showOnePage) {
                    AlertDialog(
                        onDismissRequest = { showOnePage = false },
                        containerColor = theme.bgMid,
                        title = { Text(stringResource(R.string.txt_fd31153a), color = theme.textMain, fontWeight = FontWeight.Bold) },
                        text = { Text(stringResource(R.string.txt_5f6c3709), color = theme.textMuted, fontSize = 13.sp) },
                        confirmButton = {
                            TextButton(onClick = {
                                showOnePage = false
                                val cover = manualCoverUrl ?: if (isbn.trim().length >= 10) "https://covers.openlibrary.org/b/isbn/${isbn.trim()}-M.jpg" else null
                                val hasEnd = status == BookStatus.FINISHED || status == BookStatus.REREADING
                                val newBook = Book(title = title.trim(), author = author.trim(), pages = 1, status = status, startDate = startDate.takeIf { it.isNotEmpty() }?.let { displayToStored(it) }, endDate = endDate.takeIf { it.isNotEmpty() && hasEnd }?.let { displayToStored(it) }, dropDate = if (status == BookStatus.DROPPED) (startDate.takeIf { it.isNotEmpty() }?.let { displayToStored(it) } ?: today()) else null, rating = if (hasEnd) rating else 0, coverUrl = cover, isbn = isbn.trim().takeIf { it.isNotEmpty() }, comment = comment.trim(), genres = genres, firstFunctionalPage = firstFuncPage.toIntOrNull(), lastFunctionalPage = lastFuncPage.toIntOrNull())
                                val firstEdition = BookEdition(id = newBook.id, language = editionLanguage, languageLabel = editionLanguageLabel, flag = editionFlag, title = newBook.title, pages = newBook.pages, coverUrl = newBook.coverUrl, isbn = newBook.isbn, isActive = true)
                                vm.addBook(newBook.copy(editions = listOf(firstEdition)), prefs)
                                onBack()
                            }) { Text(stringResource(R.string.txt_d1cdc7bc), color = Accent, fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = { TextButton(onClick = { showOnePage = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
                    )
                }
                Button(onClick = {
                    error = validate(addContext, title, pages, status, displayToStored(startDate), displayToStored(endDate))
                    if (error.isEmpty() && pages.trim() == "1") { showOnePage = true; return@Button }
                    if (error.isEmpty()) {
                        val cover = manualCoverUrl ?: if (isbn.trim().length >= 10) "https://covers.openlibrary.org/b/isbn/${isbn.trim()}-M.jpg" else null
                        val hasEnd = status == BookStatus.FINISHED || status == BookStatus.REREADING
                        val newBook = Book(title = title.trim(), author = author.trim(), pages = pages.toInt(), status = status, startDate = startDate.takeIf { it.isNotEmpty() }?.let { displayToStored(it) }, endDate = endDate.takeIf { it.isNotEmpty() && hasEnd }?.let { displayToStored(it) }, dropDate = if (status == BookStatus.DROPPED) (startDate.takeIf { it.isNotEmpty() }?.let { displayToStored(it) } ?: today()) else null, rating = if (hasEnd) rating else 0, coverUrl = cover, isbn = isbn.trim().takeIf { it.isNotEmpty() }, comment = comment.trim(), genres = genres, firstFunctionalPage = firstFuncPage.toIntOrNull(), lastFunctionalPage = lastFuncPage.toIntOrNull())
                        val firstEdition = BookEdition(
                            id            = newBook.id,
                            language      = editionLanguage,
                            languageLabel = editionLanguageLabel,
                            flag          = editionFlag,
                            title         = newBook.title,
                            pages         = newBook.pages,
                            coverUrl      = newBook.coverUrl,
                            isbn          = newBook.isbn,
                            isActive      = true
                        )
                        vm.addBook(newBook.copy(editions = listOf(firstEdition)), prefs)
                        onBack()
                    }
                }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text(stringResource(R.string.txt_e3ea89d7), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
        }
    }
}

@Composable
fun RatingSelector(current: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { (1..5).forEach { n -> RatingChip(n, current == n) { onSelect(n) } } }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { (6..10).forEach { n -> RatingChip(n, current == n) { onSelect(n) } } }
        if (current > 0) { Spacer(Modifier.height(6.dp)); Text(ratingLabelLocalized(current), color = Gold, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally)) }
    }
}

@Composable
fun RatingChip(value: Int, selected: Boolean, onClick: () -> Unit) {
    // v21.35: eliminado debounce de 300ms que causaba retraso perceptible
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (selected) Gold else Color(0x22FFBB33))
            .clickable(onClick = onClick)
    ) {
        Text("$value", color = if (selected) Color.White else Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

fun ratingLabel(r: Int) = when (r) { 1, 2 -> "Muy malo 😞"; 3, 4 -> "Regular 😐"; 5, 6 -> "Bien 🙂"; 7, 8 -> "Muy bueno 😊"; 9 -> "¡Excelente! 🌟"; 10 -> "Obra maestra ✨"; else -> "" }

@Composable
fun ratingLabelLocalized(r: Int) = when (r) {
    1, 2 -> stringResource(R.string.rating_muy_malo)
    3, 4 -> stringResource(R.string.rating_regular)
    5, 6 -> stringResource(R.string.rating_bien)
    7, 8 -> stringResource(R.string.rating_muy_bueno)
    9    -> stringResource(R.string.rating_excelente)
    10   -> stringResource(R.string.rating_obra_maestra)
    else -> ""
}

fun validate(context: android.content.Context, title: String, pages: String, status: BookStatus, start: String, end: String): String {
    if (title.isBlank()) return context.getString(R.string.err_title_required)
    if (pages.toIntOrNull()?.let { it < 1 } != false) return context.getString(R.string.err_pages_invalid)
    if (status != BookStatus.PENDING && start.isEmpty()) return context.getString(R.string.err_start_date_required)
    if ((status == BookStatus.FINISHED || status == BookStatus.REREADING) && end.isEmpty()) return context.getString(R.string.err_end_date_required)
    if ((status == BookStatus.FINISHED || status == BookStatus.REREADING) && end < start) return context.getString(R.string.err_end_before_start)
    return ""
}

@Composable
fun FormField(label: String, value: String, placeholder: String, theme: Theme, keyboardType: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    Text(label, color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
    OutlinedTextField(value = value, onValueChange = onChange, placeholder = { Text(placeholder, color = theme.textDim) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), keyboardOptions = KeyboardOptions(keyboardType = keyboardType), colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun fieldColors(theme: Theme) = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, focusedBorderColor = Accent, unfocusedBorderColor = theme.border, cursorColor = Accent, focusedContainerColor = theme.surface, unfocusedContainerColor = theme.surface)

// ── DetailScreen ──────────────────────────────────────────────────────────────

enum class TimerState { IDLE, RUNNING, PAUSED }

@OptIn(ExperimentalMaterial3Api::class)
// ── EditionsSection composable ────────────────────────────────────────────────
@Composable
fun EditionsSection(
    book: Book,
    editions: List<BookEdition>,
    theme: Theme,
    onChangeEdition: (Long) -> Unit,
    onAddEdition: () -> Unit,
    onSetActive: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onUpdatePages: (editionId: Long, pages: Int) -> Unit = { _, _ -> }
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = theme.bgSurf,
        border = BorderStroke(1.dp, theme.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.txt_e38ba5d3),
                    color = theme.textDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.weight(1f)
                )
                if (editions.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0x1A7B6EF6)
                    ) {
                        Text(
                            "${editions.size}",
                            color = Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Divider(color = theme.border, thickness = 1.dp)

            // Edition cards
            editions.forEachIndexed { idx, ed ->
                val isReading = book.status == BookStatus.READING || book.isRereading || book.status == BookStatus.REREADING
                val isActiveReading = ed.isActive && isReading
                // Use the shelf color for the active edition highlight (matches the shelve color)
                val shelfColor = when (book.status) {
                    BookStatus.READING   -> Color(0xFFF59E0B) // Amber
                    BookStatus.REREADING -> Color(0xFF06B6D4) // Cyan
                    BookStatus.FINISHED  -> Color(0xFF10B981) // Green
                    BookStatus.PENDING   -> Color(0xFF8B5CF6) // Purple
                    BookStatus.DROPPED   -> Color(0xFFF87171) // Red
                }
                val cardBg = if (ed.isActive) shelfColor.copy(alpha = 0.06f) else Color.Transparent
                val borderStart = if (ed.isActive) shelfColor else Color.Transparent

                Column(
                    Modifier
                        .background(cardBg)
                        .drawBehind {
                            if (ed.isActive) drawRect(color = borderStart, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Bandera (solo display, no interactiva)
                        Text(
                            ed.flag,
                            fontSize = 22.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(ed.title.ifBlank { book.title }, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            val meta = listOfNotNull(
                                ed.languageLabel,
                                ed.publisher.ifBlank { null },
                                ed.publishYear.ifBlank { null }
                            ).joinToString(" · ")
                            Text(meta, color = theme.textDim, fontSize = 11.sp)
                            if (!ed.isbn.isNullOrBlank()) {
                                Text(
                                    "ISBN: ${ed.isbn}",
                                    color = theme.textMuted,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                        // Status pill — v21.41: distingue Reading de Rereading
                        if (isActiveReading) {
                            val pillText = if (book.status == BookStatus.REREADING)
                                stringResource(R.string.pill_rereading)
                            else
                                stringResource(R.string.txt_125ed9b0)
                            Surface(shape = RoundedCornerShape(20.dp), color = shelfColor.copy(alpha = 0.13f)) {
                                Text(pillText, color = shelfColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                            }
                        } else if (ed.isActive) {
                            Surface(shape = RoundedCornerShape(20.dp), color = shelfColor.copy(alpha = 0.13f)) {
                                Text(statusLabel(book.status), color = shelfColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                            }
                        }
                    }
                    if (ed.pages > 0 || ed.isActive) {
                        Spacer(Modifier.height(7.dp))
                        var editingEditionPages by remember(ed.id) { mutableStateOf(false) }
                        var editionPagesInput by remember(ed.id) { mutableStateOf(ed.pages.toString().takeIf { ed.pages > 0 } ?: "") }
                        Surface(shape = RoundedCornerShape(9.dp), color = theme.bgSurf2, border = BorderStroke(1.dp, if (ed.isActive) shelfColor.copy(alpha = 0.27f) else theme.border), modifier = Modifier.fillMaxWidth()) {
                            if (editingEditionPages) {
                                Row(Modifier.padding(horizontal = 11.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    androidx.compose.material3.OutlinedTextField(
                                        value = editionPagesInput,
                                        onValueChange = { editionPagesInput = it.filter { c -> c.isDigit() } },
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        textStyle = androidx.compose.ui.text.TextStyle(color = theme.textMain, fontSize = 14.sp)
                                    )
                                    TextButton(onClick = { editingEditionPages = false }) { Text("✕", color = Red) }
                                    TextButton(onClick = {
                                        val p = editionPagesInput.toIntOrNull()
                                        if (p != null && p > 0) {
                                            onUpdatePages(ed.id, p)
                                        }
                                        editingEditionPages = false
                                    }) { Text("✓", color = Accent) }
                                }
                            } else {
                                Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp).clickable { editingEditionPages = true; editionPagesInput = ed.pages.toString().takeIf { ed.pages > 0 } ?: "" }, verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.txt_939f09a3), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Text(if (ed.pages > 0) "${ed.pages}" else "—", color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    Spacer(Modifier.width(6.dp))
                                    Text("✎", color = if (ed.isActive) shelfColor else theme.textDim, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (!ed.isActive) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = Color(0x1A7B6EF6),
                                border = BorderStroke(1.dp, Accent.copy(alpha = 0.45f)),
                                modifier = Modifier.clickable { onSetActive(ed.id) }
                            ) {
                                Text(stringResource(R.string.txt_59d90b1a), color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        // Change edition button
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = theme.bgSurf2,
                            border = BorderStroke(1.dp, theme.border),
                            modifier = Modifier.weight(1f).clickable { onChangeEdition(ed.id) }
                        ) {
                            Text(stringResource(R.string.txt_d6578c6a), color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        // Remove if more than one
                        if (editions.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, Color(0x44EF4444)),
                                modifier = Modifier.clickable { onRemove(ed.id) }
                            ) {
                                Text("✕", color = Color(0xFFEF4444), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
                if (idx < editions.lastIndex) Divider(color = theme.border, thickness = 1.dp)
            }

            // Add edition button — only if < 3 editions
            if (editions.size < 3) {
                Divider(color = theme.border, thickness = 1.dp)
                Box(
                    Modifier.fillMaxWidth().clickable { onAddEdition() }.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.txt_b7369062), color = theme.textDim, fontSize = 13.sp)
                }
            }

            // Multi-edition note
            if (editions.size > 1) {
                Divider(color = theme.border, thickness = 1.dp)
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📊", fontSize = 12.sp)
                    Text(
                        "${editions.size} ediciones · cada una cuenta como un libro independiente en estadísticas · ${editions.sumOf { it.pages }} págs en total",
                        color = theme.textDim,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DetailScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, id: Long, highlightDate: String? = null, onBack: () -> Unit, onAuthorClick: (String) -> Unit) {
    val book = vm.books.find { it.id == id } ?: run { onBack(); return }
    val stats = getStats(book)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) { true }
    }
    val activeEdition = vm.editionsForBook(id).firstOrNull { it.isActive }
    val activeLang = activeEdition?.language ?: "original"
    val bookSessions = vm.sessionsForBookAndLanguage(id, activeLang)
    val detailSessionNumberMap = remember(bookSessions) {
        // v20.1 (G3): numerar por ciclo — cada ciclo (readingIndex) empieza en #1
        val map = mutableMapOf<Long, Int>()
        val byCycle = bookSessions.groupBy { it.readingIndex ?: 0 }
        for ((_, cycleSessions) in byCycle) {
            cycleSessions
                .sortedBy { it.date + it.id.toString().padStart(20, '0') }
                .forEachIndexed { index, session -> map[session.id] = index + 1 }
        }
        map
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    // v20.0 (G5): confirmación abandono
    var showAbandonDialog by remember { mutableStateOf(false) }
    var showCoverDialog by remember { mutableStateOf(false) }
    var showSessionDialog by remember { mutableStateOf(false) }
    var showConflictSessionDialog by remember { mutableStateOf(false) }
    var showSessionSummary by remember(id) { mutableStateOf(highlightDate != null) }
    // Diálogo educativo permiso POST_NOTIFICATIONS (Android 13+)
    var showNotifPermDialog by remember { mutableStateOf(false) }
    var showNotifPermDeniedDialog by remember { mutableStateOf(false) }
    var pendingTimerAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) showNotifPermDeniedDialog = true
        // Lanzar igualmente — el servicio funciona, solo no habrá notificación visible
        pendingTimerAction?.invoke()
        pendingTimerAction = null
    }
    if (showNotifPermDialog) {
        AlertDialog(
            onDismissRequest = { showNotifPermDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_5c0e66ea), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_33fe5747), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showNotifPermDialog = false
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else
                        pendingTimerAction?.invoke().also { pendingTimerAction = null }
                }) { Text(stringResource(R.string.txt_5fcafeb2), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNotifPermDialog = false
                    showNotifPermDeniedDialog = true
                    // Iniciar igualmente sin notificación
                    pendingTimerAction?.invoke()
                    pendingTimerAction = null
                }) { Text(stringResource(R.string.txt_ca8f9bb3), color = Red) }
            }
        )
    }
    // Aviso tras rechazar: explica cómo activarlo más tarde desde permisos de la app
    if (showNotifPermDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showNotifPermDeniedDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_1fa1de14), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_9731be9d), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showNotifPermDeniedDialog = false }) { Text(stringResource(R.string.txt_3f346645), color = Accent, fontWeight = FontWeight.Bold) }
            }
        )
    }
    fun startTimerWithPermCheck(action: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("notif_perm_asked", false)) {
            prefs.edit().putBoolean("notif_perm_asked", true).apply()
            pendingTimerAction = action
            showNotifPermDialog = true
        } else {
            action()
        }
    }
    // v20.0 (G2): map de secciones de historial expandidas (key = readingIndex).
    // Si highlightDate viene del heatmap se abrirá la sección correspondiente automáticamente.
    val sectionExpanded = remember(id) { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
    var activeHighlightDate by remember(id) { mutableStateOf(highlightDate) }

    // Advertencia al cambiar edición activa (sin timer): informa que cambian sesiones/comentarios visibles
    var showActiveEditionWarning by remember { mutableStateOf(false) }
    var pendingActiveEditionId by remember { mutableStateOf<Long?>(null) }

    // Advertencia cuando hay sesión en curso y se intenta cambiar edición: pausa + advierte
    var showTimerEditionWarning by remember { mutableStateOf(false) }
    var pendingChangeEditionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Si el aviso viene de onSetActive (no de onChangeEdition), aquí guardamos el edId destino
    // para aplicarlo DESPUÉS de que el usuario guarde la sesión
    var pendingSetActiveEditionAfterSession by remember { mutableStateOf<Long?>(null) }

    // ── Estado de ediciones ───────────────────────────────────────────────────
    var showChangeEditionSheet by remember { mutableStateOf(false) }
    var showAddEditionSheet    by remember { mutableStateOf(false) }
    var editionSheetTarget     by remember { mutableStateOf<Long?>(null) }  // editionId a cambiar
    var availableEditions      by remember { mutableStateOf<List<EditionResult>>(emptyList()) }
    var editionsLoading        by remember { mutableStateOf(false) }
    var editionsNetworkError   by remember { mutableStateOf(false) }
    var selectedEditionResult  by remember { mutableStateOf<EditionResult?>(null) }
    val bookEditions = vm.editionsForBook(id)
    // v20.0 (G2): states obsoletos eliminados (search/order ahora son locales por sección).
    var selectedRating by remember(book.rating) { mutableStateOf(book.rating) }    // Sincronizar solo si el valor guardado cambia externamente (ej. otro dispositivo)
    // v21.41: selectedRating usa remember(book.rating) — reacciona directo sin LaunchedEffect
    // El comentario se lee de la edición activa (si la tiene) o del libro como fallback legacy
    val editionComment = activeEdition?.comment ?: ""
    val effectiveComment = editionComment.ifBlank { book.comment }
    var commentText by remember(effectiveComment) { mutableStateOf(effectiveComment) }
    var editingComment by remember { mutableStateOf(false) }
    var editingPages by remember { mutableStateOf(false) }
    var pagesInput by remember(book.pages) { mutableStateOf(book.pages.toString()) }
    // Valor capturado al abrir edición — permite que Cancelar restaure el dato original
    var pagesOriginal by remember { mutableStateOf(book.pages) }
    var coverUrlInput by remember(book.coverUrl) { mutableStateOf(book.coverUrl ?: "") }
    val isRefreshingCover = vm.isCoverRefreshing(id)
    val scrollState = rememberScrollState()
    // Contador que se incrementa para disparar el scroll al fondo tras guardar sesion
    var scrollToBottomTrigger by remember { mutableStateOf(0) }
    val capturedMaxValueBeforeExpand = remember { mutableStateOf(0) }

    // Cuando se activa el trigger, captura el maxValue actual (con formularios cerrados),
    // luego espera a que maxValue CAMBIE (layout expandido con formularios abiertos) y scrollea.
    LaunchedEffect(scrollToBottomTrigger) {
        if (scrollToBottomTrigger == 0) return@LaunchedEffect
        capturedMaxValueBeforeExpand.value = scrollState.maxValue
        // Esperar hasta que el layout se expanda (maxValue aumenta) o timeout de 1s
        val deadline = System.currentTimeMillis() + 1000L
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(50)
            if (scrollState.maxValue > capturedMaxValueBeforeExpand.value) break
        }
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Si viene del heatmap con highlightDate: abrir sesiones y hacer scroll al fondo
    // v21.41: delays reducidos — sensación de respuesta instantánea
    LaunchedEffect(highlightDate) {
        if (highlightDate != null) {
            showSessionSummary = true
            // v20.0 (G2): abrir también la sección correspondiente al ciclo de la sesión matcheada
            val matchingSession = bookSessions.firstOrNull { it.date == highlightDate }
            val idx = matchingSession?.readingIndex ?: 0
            sectionExpanded[idx] = true
            // Delay inicial mínimo para que el layout empiece a expandirse
            kotlinx.coroutines.delay(30)
            val before = scrollState.maxValue
            val deadline = System.currentTimeMillis() + 500L
            while (System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(20)
                if (scrollState.maxValue > before) break
            }
            // Scroll al fondo para mostrar las sesiones expandidas
            scrollState.animateScrollTo(scrollState.maxValue)
            // Segundo intento por si el layout terminó de medirse después del scroll
            kotlinx.coroutines.delay(80)
            if (scrollState.value < scrollState.maxValue) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }

    var refreshMsg by remember { mutableStateOf("") }

    // ── Temporizador automático (con servicio foreground para pantalla bloqueada) ─
    // El estado vive en TimerStateHolder. Aquí mantenemos copias locales que se
    // sincronizan periódicamente para que la UI se recomponga.
    var timerState by remember { mutableStateOf(
        when {
            !TimerStateHolder.running || TimerStateHolder.activeBookId != id -> TimerState.IDLE
            TimerStateHolder.paused   -> TimerState.PAUSED
            else                      -> TimerState.RUNNING
        }
    ) }
    var timerSeconds by remember { mutableStateOf(TimerStateHolder.seconds) }
    var autoSessionMinutes by remember { mutableStateOf<Int?>(null) }

    fun closeSessionDialog() {
        showSessionDialog = false
        autoSessionMinutes = null
        TimerStateHolder.shouldOpenDialog = false
        com.lecturameter.TimerService.cancelSessionEndNotification(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            (context as? android.app.Activity)?.setShowWhenLocked(false)
        }
        // Si el aviso venía de onSetActive, aplicar el cambio de edición activa ahora
        // que la sesión ya se ha guardado (o cancelado) con la edición correcta
        val switchTo = pendingSetActiveEditionAfterSession
        if (switchTo != null) {
            pendingSetActiveEditionAfterSession = null
            vm.setActiveEdition(id, switchTo, prefs)
            refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
        }
    }

    // Polling cada 500ms para sincronizar el estado con el servicio.
    // Es ligero y permite que cambios desde la notificación (Pause/Resume) se reflejen
    // en la UI sin necesitar Flow ni LiveData.
    LaunchedEffect(Unit) {
        val timerPrefs = context.getSharedPreferences(com.lecturameter.TimerService.TIMER_PREFS, android.content.Context.MODE_PRIVATE)
        // Comprobación inmediata: en memoria O en prefs (proceso muerto y restaurado)
        val inMemory = TimerStateHolder.shouldOpenDialog && TimerStateHolder.activeBookId == id
        val inPrefs  = timerPrefs.getBoolean("pending", false) && timerPrefs.getLong("book_id", -1L) == id
        if (inMemory || inPrefs) {
            val secs = if (TimerStateHolder.seconds > 0L) TimerStateHolder.seconds
                       else timerPrefs.getLong("seconds", 60L)
            timerPrefs.edit().clear().apply()
            TimerStateHolder.shouldOpenDialog = false
            com.lecturameter.TimerService.cancelSessionEndNotification(context)
            autoSessionMinutes = ((secs + 30) / 60).toInt().coerceAtLeast(1)
            TimerStateHolder.reset()
            showSessionDialog = true
        }
        while (true) {
            kotlinx.coroutines.delay(500L)
            timerSeconds = TimerStateHolder.seconds
            val newState = when {
                !TimerStateHolder.running || TimerStateHolder.activeBookId != id -> TimerState.IDLE
                TimerStateHolder.paused   -> TimerState.PAUSED
                else                      -> TimerState.RUNNING
            }
            if (newState != timerState) timerState = newState

            // Si el usuario pulsó Finalizar desde la notificación, abrir el diálogo
            if (TimerStateHolder.shouldOpenDialog) {
                val secs = TimerStateHolder.seconds
                TimerStateHolder.shouldOpenDialog = false
                com.lecturameter.TimerService.cancelSessionEndNotification(context)
                context.getSharedPreferences(com.lecturameter.TimerService.TIMER_PREFS, android.content.Context.MODE_PRIVATE).edit().clear().apply()
                autoSessionMinutes = ((secs + 30) / 60).toInt().coerceAtLeast(1)
                TimerStateHolder.reset()
                showSessionDialog = true
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // Tomar permiso persistente si está disponible (content URIs de galería)
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* algunos URIs no admiten permisos persistentes */ }

            // Copiar siempre al almacenamiento interno para garantizar persistencia
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val activeEdId = book.editions.firstOrNull { it.isActive }?.id
                val localPath = copyUriToInternalStorage(context, uri, id, activeEdId)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    vm.updateCover(id, localPath ?: uri.toString(), prefs)
                    refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
                }
            }
        }
    }

    // ── Diálogo: Sesión en curso en otro libro ────────────────────────────────
    if (showConflictSessionDialog) {
        AlertDialog(
            onDismissRequest = { showConflictSessionDialog = false },
            title = { Text(stringResource(R.string.txt_8bac5764), color = theme.textMain) },
            text = { Text(stringResource(R.string.txt_fb71a7c6), color = theme.textMuted) },
            confirmButton = {
                TextButton(onClick = {
                    showConflictSessionDialog = false
                    // Si estaba pausado, reanudar; si estaba corriendo, dejarlo correr
                    if (TimerStateHolder.paused) {
                        com.lecturameter.TimerService.resume(context)
                    }
                }) { Text(stringResource(R.string.txt_bafd7322), color = theme.textMuted) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConflictSessionDialog = false
                    val secs = TimerStateHolder.seconds
                    val mins = ((secs + 30) / 60).toInt().coerceAtLeast(1)
                    com.lecturameter.TimerService.stop(context, showEndNotification = false)
                    TimerStateHolder.shouldOpenDialog = false
                    TimerStateHolder.reset()
                    autoSessionMinutes = mins
                    timerSeconds = 0
                    showSessionDialog = true
                }) { Text(stringResource(R.string.txt_bdd207ee), color = Green) }
            },
            containerColor = theme.bgMid
        )
    }

    // ── Sheet: Cambiar/Añadir edición ─────────────────────────────────────────
    if (showChangeEditionSheet || showAddEditionSheet) {
        val isAdding = showAddEditionSheet
        AlertDialog(
            onDismissRequest = { showChangeEditionSheet = false; showAddEditionSheet = false },
            containerColor = theme.bgMid,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isAdding) "Añadir edición" else "Cambiar edición",
                        color = theme.textMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (!editionsLoading) {
                                availableEditions = emptyList()
                                editionsLoading = true
                                vm.loadAvailableEditions(id, forceRefresh = true) { results ->
                                    availableEditions = results
                                    editionsLoading = false
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh editions", tint = if (editionsLoading) theme.textDim else Accent, modifier = Modifier.size(18.dp))
                    }
                }
            },
            text = {
                Column {
                    Text(
                        if (isAdding)
                            stringResource(R.string.add_edition_info)
                        else
                            stringResource(R.string.txt_c1404abf),
                        color = theme.textMuted, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    when {
                        editionsLoading -> {
                            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp))
                            }
                        }
                        editionsNetworkError -> {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📡", fontSize = 32.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.txt_75fe6e58), color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.txt_af20f31b), color = Red.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        }
                        availableEditions.isEmpty() -> {
                            Text(stringResource(R.string.txt_fd67b9a7), color = theme.textDim, fontSize = 12.sp)
                        }
                        else -> {
                            val esEditions = availableEditions.filter { it.language == "es" }
                            val enEditions = availableEditions.filter { it.language != "es" }
                            // showingEs: true = Español, false = Global/English
                            var showingEs by remember { mutableStateOf(esEditions.isNotEmpty()) }
                            val activeEditions = if (showingEs) esEditions else enEditions

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // ── Toggle Español / Global ───────────────────
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.bgSurf)
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    listOf(true to stringResource(R.string.txt_95b01315), false to "🌐 Global").forEach { (isEs, label) ->
                                        val active = showingEs == isEs
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (active) Accent else Color.Transparent)
                                                .clickable {
                                                    showingEs = isEs
                                                    // Limpiar selección si cambia de idioma
                                                    if (selectedEditionResult != null) {
                                                        val selLang = selectedEditionResult!!.language
                                                        if ((isEs && selLang != "es") || (!isEs && selLang == "es"))
                                                            selectedEditionResult = null
                                                    }
                                                }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                label,
                                                color = if (active) Color.White else theme.textDim,
                                                fontSize = 12.sp,
                                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                                // ── Lista de ediciones del idioma activo (scrollable) ─
                                if (activeEditions.isEmpty()) {
                                    Text(
                                        if (showingEs) stringResource(R.string.msg_editions_not_found_es)
                                        else stringResource(R.string.msg_editions_not_found_other),
                                        color = theme.textDim, fontSize = 12.sp
                                    )
                                } else {
                                    val listScrollState = androidx.compose.foundation.rememberScrollState()
                                    Column(
                                        modifier = Modifier
                                            .heightIn(max = 320.dp)
                                            .verticalScroll(listScrollState),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        activeEditions.forEach { ed ->
                                            val isSelected = selectedEditionResult == ed
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) Color(0x207B6EF6) else theme.bgSurf,
                                                border = BorderStroke(1.dp, if (isSelected) Accent else theme.border),
                                                modifier = Modifier.fillMaxWidth().clickable { selectedEditionResult = ed }
                                            ) {
                                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Text(ed.flag, fontSize = 22.sp)
                                                    Column(Modifier.weight(1f)) {
                                                        Text(ed.title.ifBlank { book.title }, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                        Text("${ed.languageLabel} · ${ed.publisher.ifBlank { "—" }} · ${ed.publishYear.ifBlank { "—" }}", color = theme.textDim, fontSize = 11.sp)
                                                        if (!ed.isbn.isNullOrBlank()) Text("ISBN: ${ed.isbn}", color = theme.textMuted, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                                        if (ed.pages > 0) Text(stringResource(R.string.search_pages_count, ed.pages), color = theme.textMuted, fontSize = 11.sp)
                                                    }
                                                    if (isSelected) Text("✓", color = Accent, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val sel = selectedEditionResult
                        if (sel != null) {
                            val newEdition = BookEdition(
                                id            = if (isAdding) newEditionId() else (editionSheetTarget ?: newEditionId()),
                                language      = sel.language,
                                languageLabel = sel.languageLabel,
                                flag          = sel.flag,
                                title         = sel.title.ifBlank { book.title },
                                pages         = sel.pages,
                                coverUrl      = sel.coverUrl,
                                isbn          = sel.isbn,
                                publisher     = sel.publisher,
                                publishYear   = sel.publishYear,
                                isActive      = !isAdding  // si cambiamos, la nueva es activa; si añadimos, no
                            )
                            vm.upsertEdition(id, newEdition, prefs)
                            refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
                        }
                        showChangeEditionSheet = false; showAddEditionSheet = false
                    },
                    enabled = selectedEditionResult != null
                ) { Text(if (isAdding) stringResource(R.string.txt_d20f652b) else stringResource(R.string.txt_d1bdc329), color = if (selectedEditionResult != null) Accent else theme.textDim) }
            },
            confirmButton = {
                TextButton(onClick = { showChangeEditionSheet = false; showAddEditionSheet = false }) {
                    Text(stringResource(R.string.txt_847607d7), color = Red)
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(onDismissRequest = { showDeleteDialog = false }, title = { Text(stringResource(R.string.txt_b375487f), color = theme.textMain) }, text = { Text(stringResource(R.string.txt_2750cc8c, book.title), color = theme.textMuted) },
            confirmButton = { TextButton(onClick = { vm.deleteBook(id, prefs); clearWidgetBookIfSelected(context, id); onBack() }) { Text(stringResource(R.string.txt_5b5c9f9d), color = Red) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Accent) } }, containerColor = theme.bgMid)
    }

    // v20.0 (G5): confirmación abandono
    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { showAbandonDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_13d1d160), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_c13603f4, book.title), color = theme.textMuted) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateStatus(id, BookStatus.DROPPED, prefs)
                    refreshWidgetForBookIfSelected(context, id)
                    showAbandonDialog = false
                }) { Text(stringResource(R.string.txt_39e254c6), color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAbandonDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Accent) } }
        )
    }

    // ── Diálogo: cambio de edición activa (sin timer) ─────────────────────────
    if (showActiveEditionWarning) {
        AlertDialog(
            onDismissRequest = { showActiveEditionWarning = false; pendingActiveEditionId = null },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_ca1da6c8), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.txt_c1404abf),
                    color = theme.textMuted, fontSize = 13.sp
                )
            },
            dismissButton = {
                TextButton(onClick = { showActiveEditionWarning = false; pendingActiveEditionId = null }) {
                    Text(stringResource(R.string.txt_847607d7), color = Red)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val edId = pendingActiveEditionId
                    if (edId != null) {
                        vm.setActiveEdition(id, edId, prefs)
                        refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
                    }
                    showActiveEditionWarning = false; pendingActiveEditionId = null
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = Accent) }
            }
        )
    }

    // ── Diálogo: cambio de edición con timer activo ───────────────────────────
    if (showTimerEditionWarning) {
        val isActiveSwitch = pendingSetActiveEditionAfterSession != null
        AlertDialog(
            onDismissRequest = {
                showTimerEditionWarning = false
                pendingChangeEditionAction = null
                pendingSetActiveEditionAfterSession = null
                TimerService.resume(context)
            },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_8bac5764), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isActiveSwitch)
                        "Tienes una sesión en curso. Si cambias de edición ahora, la sesión se guardará automáticamente con la edición actual antes de hacer el cambio."
                    else
                        "Tienes una sesión de lectura en curso. Si cambias de edición ahora, la sesión se finalizará.",
                    color = theme.textMuted, fontSize = 13.sp, textAlign = TextAlign.Justify
                )
            },
            dismissButton = {
                TextButton(onClick = {
                    showTimerEditionWarning = false
                    pendingChangeEditionAction = null
                    pendingSetActiveEditionAfterSession = null
                    TimerService.resume(context)
                }) { Text(stringResource(R.string.txt_847607d7), color = Red) }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTimerEditionWarning = false
                    val action = pendingChangeEditionAction
                    pendingChangeEditionAction = null
                    // Capture seconds BEFORE stopping
                    val secs = TimerStateHolder.seconds
                    val mins = ((secs + 30) / 60).toInt().coerceAtLeast(1)
                    TimerService.stop(context, showEndNotification = false)
                    TimerStateHolder.shouldOpenDialog = false
                    TimerStateHolder.reset()
                    autoSessionMinutes = mins
                    timerSeconds = 0
                    showSessionDialog = true
                    if (action != null) pendingChangeEditionAction = action
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = Accent) }
            }
        )
    }

    // ── Diálogo: Registrar sesión ─────────────────────────────────────────────
    if (showSessionDialog) {
        // v20.0 (G1): autofill considera el ciclo actual.
        //   - Si REREADING: solo sesiones del ciclo de relectura abierto.
        //     · Sin sesiones aún en el ciclo → firstFunctionalPage (o vacío).
        //     · Con sesiones del ciclo → última endPage + 1.
        //   - Si READING/FINISHED: comportamiento clásico (última endPage global del libro).
        // remember(bookSessions, book.status, book.dateEvents) recalcula al cambiar la lista o el estado.
        val autoStart = remember(bookSessions, book.status, book.dateEvents, book.firstFunctionalPage) {
            val isRereadingNow = book.status == BookStatus.REREADING
            if (isRereadingNow) {
                val evs = migrateLegacyToEvents(book)
                val openReread = evs.filter { it.type == "reread" }
                    .lastOrNull { ev ->
                        evs.none { it.type == "reread_end" && it.occurrence == ev.occurrence }
                    }
                if (openReread != null) {
                    val cycleSessions = bookSessions.filter { (it.readingIndex ?: 0) == openReread.occurrence }
                    val lastEndPage = cycleSessions.firstOrNull()?.endPage
                    when {
                        lastEndPage != null -> {
                            val lastFunc = book.lastFunctionalPage
                            val next = lastEndPage + 1
                            if (lastFunc != null && next > lastFunc) book.firstFunctionalPage?.toString() ?: ""
                            else next.toString()
                        }
                        book.firstFunctionalPage != null -> book.firstFunctionalPage.toString()
                        else -> ""
                    }
                } else {
                    // No hay ciclo abierto → fallback firstFunctional
                    book.firstFunctionalPage?.toString() ?: ""
                }
            } else {
                val lastEndPage = bookSessions.firstOrNull()?.endPage
                when {
                    lastEndPage != null -> (lastEndPage + 1).toString()
                    book.firstFunctionalPage != null -> book.firstFunctionalPage.toString()
                    else -> ""
                }
            }
        }

        var pageStart   by remember(autoStart) { mutableStateOf(autoStart) }
        var pageEnd     by remember { mutableStateOf("") }
        var sessionMinutes by remember { mutableStateOf(autoSessionMinutes?.toString() ?: "") }
        var sessionNote by remember { mutableStateOf("") }
        var sessionError by remember { mutableStateOf("") }
        val fromTimer = autoSessionMinutes != null
        // v20.0: fecha editable (cronometrada → today; manual → today, el usuario puede editar)
        var sessionDate by remember { mutableStateOf(todayDisplay()) }

        val totalPages = if (pageEnd.toIntOrNull() != null && pageStart.toIntOrNull() != null) (pageEnd.toIntOrNull()!! - pageStart.toIntOrNull()!! + 1).coerceAtLeast(0) else 0
        val mins = if (fromTimer) autoSessionMinutes else sessionMinutes.toIntOrNull()
        val ppm = if (totalPages > 0 && mins != null && mins > 0)
            String.format("%.1f", totalPages.toDouble() / mins) else null

        AlertDialog(
            onDismissRequest = { closeSessionDialog() },
            containerColor = theme.bgMid,
            title = { Text(if (fromTimer) "⏱️ ${stringResource(R.string.timed_session_title)}" else stringResource(R.string.txt_e810b914), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    // v20.0: fecha editable. Si cronometrada se prefija hoy (no se permite editar para mantener integridad del temporizador).
                    if (fromTimer) {
                        Text("${stringResource(R.string.txt_5d69fc39).removePrefix("📅 ")}: ${fmtDate(today())}", color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                    } else {
                        Text(stringResource(R.string.txt_78359316), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = sessionDate,
                            onValueChange = { sessionDate = it; sessionError = "" },
                            placeholder = { Text(todayDisplay(), color = theme.textDim) },
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        )
                    }
                    if (fromTimer) {
                        Surface(shape = RoundedCornerShape(10.dp), color = Green.copy(alpha = 0.12f), border = BorderStroke(1.dp, Green.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Text("⏱", fontSize = 16.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.timer_recorded_time, fmtMinutes(autoSessionMinutes!!)), color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    // Página inicial y final
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.txt_900cd8a8), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                            OutlinedTextField(
                                value = pageStart, onValueChange = { pageStart = it.filter { c -> c.isDigit() }; sessionError = "" },
                                placeholder = { Text(stringResource(R.string.txt_3015bbac), color = theme.textDim) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.txt_c8d92673), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                            OutlinedTextField(
                                value = pageEnd, onValueChange = { pageEnd = it.filter { c -> c.isDigit() }; sessionError = "" },
                                placeholder = { Text(stringResource(R.string.txt_78cf172d), color = theme.textDim) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    // Resumen calculado
                    if (totalPages > 0) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Accent.copy(alpha = 0.1f), border = BorderStroke(1.dp, Accent.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$totalPages", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.txt_47bcdf9a), color = theme.textDim, fontSize = 10.sp)
                                }
                                if (ppm != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(ppm, color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text(stringResource(R.string.txt_ef31b7ae), color = theme.textDim, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                    // Tiempo (solo si no viene del temporizador)
                    if (!fromTimer) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.txt_f7772d77), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = sessionMinutes, onValueChange = { sessionMinutes = it.filter { c -> c.isDigit() } },
                            placeholder = { Text(stringResource(R.string.txt_e4bf49dd), color = theme.textDim) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                            singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                    }
                    // Nota (opcional)
                    Text(stringResource(R.string.txt_7cd6ad03), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(
                        value = sessionNote, onValueChange = { sessionNote = it },
                        placeholder = { Text(stringResource(R.string.txt_2136819a), color = theme.textDim) },
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                        maxLines = 3, modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp)
                    )
                    if (sessionError.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(sessionError, color = Red, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val start = pageStart.toIntOrNull()
                        val end   = pageEnd.toIntOrNull()
                        val lastFunc = book.lastFunctionalPage
                        // v20.0: validar fecha manual
                        val effectiveDate: String = if (fromTimer) today() else {
                            val raw = sessionDate.trim()
                            if (raw.isBlank()) {
                                sessionError = context.getString(R.string.err_date_required); return@Button
                            }
                            val parsed = parseFlexibleDate(raw)
                            if (parsed == null) {
                                sessionError = context.getString(R.string.err_date_invalid_format); return@Button
                            }
                            val todayStored = today()
                            if (parsed > todayStored) {
                                sessionError = context.getString(R.string.err_date_invalid_generic); return@Button
                            }
                            val bookStart = book.startDate
                            if (bookStart != null && parsed < bookStart) {
                                sessionError = context.getString(R.string.err_date_invalid_generic); return@Button
                            }
                            displayToStored(raw)
                        }
                        when {
                            start == null || start < 1 ->
                                { sessionError = context.getString(R.string.err_page_start_min1); return@Button }
                            end == null || end > book.pages ->
                                { sessionError = context.getString(R.string.err_page_end_over_total_n, book.pages); return@Button }
                            lastFunc != null && end > lastFunc ->
                                { sessionError = context.getString(R.string.err_page_end_over_func, lastFunc); return@Button }
                            end < start ->
                                { sessionError = context.getString(R.string.err_page_end_lt_start); return@Button }
                        }
                        // Si startPage coincide con el endPage de la sesión anterior,
                        // esa página ya fue contada → no sumar +1
                        val prevEndPage = bookSessions.firstOrNull()?.endPage
                        val pages = if (prevEndPage != null && start == prevEndPage) end!! - start!!
                                    else end!! - start!! + 1
                        val m = if (fromTimer) autoSessionMinutes else sessionMinutes.toIntOrNull()
                        val activeEditionId = book.editions.firstOrNull { it.isActive }?.id
                        vm.addSession(ReadingSession(
                            bookId = id, date = effectiveDate, pages = pages, minutes = m,
                            note = sessionNote.trim(), editionId = activeEditionId,
                            startPage = start, endPage = end
                        ), prefs)
                        refreshWidgetForBookIfSelected(context, id)
                        timerSeconds = 0
                        closeSessionDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(10.dp)
                ) { Text(stringResource(R.string.txt_d3270bdb)) }
            },
            dismissButton = { TextButton(onClick = { closeSessionDialog() }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }

    if (showCoverDialog) {
        AlertDialog(onDismissRequest = { showCoverDialog = false }, title = { Text(stringResource(R.string.txt_adbe3283), color = theme.textMain) },
            text = {
                Column {
                    Text(stringResource(R.string.txt_aa02a2da), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(value = coverUrlInput, onValueChange = { coverUrlInput = it }, placeholder = { Text(stringResource(R.string.txt_14f2b208), color = theme.textDim) }, colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { imagePicker.launch("image/*"); showCoverDialog = false }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Accent.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)) { Text(stringResource(R.string.txt_5cd0defc)) }
                }
            },
            confirmButton = { TextButton(onClick = { if (coverUrlInput.isNotBlank()) { vm.updateCover(id, coverUrlInput.trim(), prefs); refreshWidgetForBookIfSelected(context, id, clearCoverCache = true) }; showCoverDialog = false }) { Text(stringResource(R.string.txt_f0ed2dc3), color = Accent) } },
            dismissButton = { TextButton(onClick = { showCoverDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }, containerColor = theme.bgMid)
    }

    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 28.dp, bottom = 20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
            Column(Modifier.padding(24.dp)) {

                // Cover + Edit + Refresh
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BookCover(book.coverUrl, book.title, size = 120, onBroken = { vm.markCoverBroken(id, prefs) }, isbnFallback = book.isbn)
                    Box(Modifier.align(Alignment.BottomEnd).offset(x = (-32).dp).size(32.dp).clip(CircleShape).background(Accent).clickable { coverUrlInput = book.coverUrl ?: ""; showCoverDialog = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Box(Modifier.align(Alignment.BottomStart).offset(x = 32.dp).size(32.dp).clip(CircleShape).background(if (isRefreshingCover) Color(0xFF64748B) else Sky).clickable(enabled = !isRefreshingCover) {
                        refreshMsg = ""
                        vm.refreshCover(id, prefs) { coverFound, genreFound ->
                            if (coverFound || genreFound) {
                                refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
                                val parts = listOfNotNull(
                                    if (coverFound) context.getString(R.string.word_cover) else null,
                                    if (genreFound) context.getString(R.string.word_genre) else null
                                )
                                refreshMsg = context.getString(R.string.msg_refresh_updated, parts.joinToString(context.getString(R.string.word_and)))
                            }
                            if (!coverFound || !genreFound) {
                                val missing = listOfNotNull(
                                    if (!coverFound) context.getString(R.string.word_cover) else null,
                                    if (!genreFound) context.getString(R.string.word_genre) else null
                                )
                                val errorMsg = context.getString(R.string.msg_not_found_prefix, missing.joinToString(context.getString(R.string.word_join_nor)))
                                if (refreshMsg.isEmpty()) refreshMsg = errorMsg
                                else refreshMsg = "$refreshMsg · $errorMsg"
                            }
                        }
                    }, contentAlignment = Alignment.Center) {
                        if (isRefreshingCover) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    if (book.noCoverFound) {
                        Box(Modifier.align(Alignment.BottomEnd).offset(x = (-72).dp).size(32.dp).clip(CircleShape).background(Amber), contentAlignment = Alignment.Center) {
                            Text("⚠", fontSize = 14.sp, lineHeight = 14.sp)
                        }
                    }
                }
                if (refreshMsg.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(refreshMsg, color = if (refreshMsg.startsWith("✅")) Green else Amber, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally)) }

                // ── Ediciones ────────────────────────────────────────────────
                Spacer(Modifier.height(14.dp))
                EditionsSection(
                    book         = book,
                    editions     = bookEditions,
                    theme        = theme,
                    onChangeEdition = { edId ->
                        val action = {
                            editionSheetTarget = edId
                            selectedEditionResult = null
                            availableEditions = emptyList()
                            editionsNetworkError = false
                            if (!isOnline()) {
                                editionsLoading = false
                                editionsNetworkError = true
                                showChangeEditionSheet = true
                            } else {
                                editionsLoading = true
                                showChangeEditionSheet = true
                                vm.loadAvailableEditions(id) { results ->
                                    availableEditions = results
                                    editionsLoading = false
                                    // Si tras la carga seguimos offline y no hay resultados → error de red
                                    if (results.isEmpty() && !isOnline()) editionsNetworkError = true
                                }
                            }
                        }
                        if (TimerStateHolder.running && TimerStateHolder.activeBookId == id) {
                            TimerService.pause(context)
                            pendingChangeEditionAction = action
                            showTimerEditionWarning = true
                        } else {
                            action()
                        }
                    },
                    onAddEdition = {
                        val action = {
                            selectedEditionResult = null
                            availableEditions = emptyList()
                            editionsNetworkError = false
                            if (!isOnline()) {
                                editionsLoading = false
                                editionsNetworkError = true
                                showAddEditionSheet = true
                            } else {
                                editionsLoading = true
                                showAddEditionSheet = true
                                vm.loadAvailableEditions(id) { results ->
                                    availableEditions = results
                                    editionsLoading = false
                                    if (results.isEmpty() && !isOnline()) editionsNetworkError = true
                                }
                            }
                        }
                        if (TimerStateHolder.running && TimerStateHolder.activeBookId == id) {
                            TimerService.pause(context)
                            pendingChangeEditionAction = action
                            showTimerEditionWarning = true
                        } else {
                            action()
                        }
                    },
                    onSetActive  = { edId ->
                        if (TimerStateHolder.running && TimerStateHolder.activeBookId == id) {
                            // Sesión en curso: pausar y pedir confirmación antes de cambiar
                            TimerService.pause(context)
                            pendingSetActiveEditionAfterSession = edId
                            pendingChangeEditionAction = null
                            showTimerEditionWarning = true
                        } else {
                            val currentLang = vm.editionsForBook(id).firstOrNull { it.isActive }?.language ?: "original"
                            val newLang = vm.editionsForBook(id).firstOrNull { it.id == edId }?.language ?: "original"
                            if (currentLang != newLang && vm.editionsForBook(id).size > 1) {
                                pendingActiveEditionId = edId
                                showActiveEditionWarning = true
                            } else {
                                vm.setActiveEdition(id, edId, prefs)
                                refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
                            }
                        }
                    },
                    onRemove     = { edId ->
                        if (vm.removeEdition(id, edId, prefs)) {
                            refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
                        }
                    },
                    onUpdatePages = { edId, pages ->
                        vm.upsertEdition(id, bookEditions.first { it.id == edId }.copy(pages = pages), prefs)
                        if (bookEditions.firstOrNull { it.id == edId }?.isActive == true) vm.updatePages(id, pages, prefs)
                    }
                )

                // ── Temporizador de lectura ────────────────────────────────────
                if (book.status == BookStatus.READING || book.isRereading || book.status == BookStatus.REREADING) {
                    Spacer(Modifier.height(12.dp))
                    val timerHours = timerSeconds / 3600
                    val timerMins = (timerSeconds % 3600) / 60
                    val timerSecs = timerSeconds % 60
                    val timerLabel = if (timerHours > 0) String.format("%d:%02d:%02d", timerHours, timerMins, timerSecs)
                                     else String.format("%02d:%02d", timerMins, timerSecs)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when (timerState) {
                            TimerState.RUNNING -> Color(0x1510B981)
                            TimerState.PAUSED  -> Color(0x15F59E0B)
                            else               -> Color(0x0D6366F1)
                        },
                        border = BorderStroke(1.dp, when (timerState) {
                            TimerState.RUNNING -> Color(0x4010B981)
                            TimerState.PAUSED  -> Color(0x40F59E0B)
                            else               -> Color(0x206366F1)
                        }),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // Rebote al iniciar/reanudar — declarado fuera del if para estabilidad del remember
                            val timerScale = remember { androidx.compose.animation.core.Animatable(1f) }
                            LaunchedEffect(timerState) {
                                if (timerState == TimerState.RUNNING) {
                                    timerScale.animateTo(1.12f, animationSpec = tween(100))
                                    timerScale.animateTo(1f, animationSpec = tween(120))
                                }
                            }
                            if (timerState != TimerState.IDLE) {
                                Text(
                                    timerLabel,
                                    color = when (timerState) {
                                        TimerState.RUNNING -> Green
                                        TimerState.PAUSED  -> Amber
                                        else               -> theme.textMuted
                                    },
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.scale(timerScale.value)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    when (timerState) { TimerState.RUNNING -> stringResource(R.string.timer_reading); TimerState.PAUSED -> stringResource(R.string.timer_paused); else -> "" },
                                    color = theme.textDim, fontSize = 11.sp
                                )
                                Spacer(Modifier.height(10.dp))
                            } else {
                                Text(stringResource(R.string.txt_d8125eae), color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Play / Pause — llaman al servicio para que la notificación
                                // de pantalla de bloqueo funcione mientras hay sesión activa.
                                if (timerState != TimerState.RUNNING) {
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape)
                                            .background(Green)
                                            .clickable {
                                                // Si hay una sesión activa para OTRO libro, preguntar
                                                if (TimerStateHolder.running && TimerStateHolder.activeBookId != id) {
                                                    showConflictSessionDialog = true
                                                } else if (timerState == TimerState.IDLE) {
                                                    startTimerWithPermCheck {
                                                        com.lecturameter.TimerService.start(context, id, book.title)
                                                    }
                                                } else {
                                                    com.lecturameter.TimerService.resume(context)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                } else {
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape)
                                            .background(Amber)
                                            .clickable { com.lecturameter.TimerService.pause(context) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                }
                                // Stop (solo si hay tiempo acumulado) — finaliza el servicio
                                // y abre el diálogo aquí mismo (la app está abierta).
                                if (timerState != TimerState.IDLE) {
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                            .background(Red.copy(alpha = 0.85f))
                                            .clickable {
                                                val secs = TimerStateHolder.seconds
                                                val mins = ((secs + 30) / 60).toInt().coerceAtLeast(1)
                                                com.lecturameter.TimerService.stop(context, showEndNotification = false)
                                                // Reset del flag (el Service lo iba a poner pero
                                                // como ya estamos abiertos abrimos directo)
                                                TimerStateHolder.shouldOpenDialog = false
                                                TimerStateHolder.reset()
                                                autoSessionMinutes = mins
                                                timerSeconds = 0
                                                showSessionDialog = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.cd_end_session), tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Title / author / status
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(book.title, color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (book.author.isNotBlank()) Text(stringResource(R.string.by_author, book.author), color = Accent, fontSize = 14.sp, modifier = Modifier.clickable { onAuthorClick(book.author) })
                        // Género — toca para cambiar; botón swap si hay 2
                        var showGenreMenu by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Box {
                            Text(
                                text = if (book.genres.isNotEmpty()) book.genres.map { displayGenre(it) }.joinToString(" · ") else stringResource(R.string.genre_add_button),
                                color = if (book.genres.isNotEmpty()) theme.textDim else Accent.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp).clickable { showGenreMenu = true }
                            )
                            DropdownMenu(expanded = showGenreMenu, onDismissRequest = { showGenreMenu = false }) {
                                Text(stringResource(R.string.txt_ce89c06f), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                DropdownMenuItem(text = { Text(stringResource(R.string.txt_bddf53d0), color = theme.textDim, fontSize = 13.sp) }, onClick = { vm.updateGenres(id, emptyList(), prefs); showGenreMenu = false })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.txt_6fc03171), color = Accent, fontSize = 13.sp) },
                                    onClick = {
                                        showGenreMenu = false
                                        vm.refreshGenre(id, prefs) { found ->
                                            refreshMsg = if (found) context.getString(R.string.msg_genre_updated) else context.getString(R.string.msg_genre_not_found)
                                        }
                                    }
                                )
                                BOOK_GENRES.filter { it != "Otro" }.forEach { g ->
                                    val selected = g in book.genres
                                    DropdownMenuItem(
                                        text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (selected) Text("✓", color = Accent, fontWeight = FontWeight.Bold)
                                            Text(displayGenre(g), color = if (selected) Accent else theme.textMain, fontSize = 13.sp)
                                        }},
                                        onClick = {
                                            val newGenres = if (selected) book.genres - g
                                                else if (book.genres.size < 2) book.genres + g
                                                else book.genres
                                            vm.updateGenres(id, newGenres, prefs)
                                            if (book.noCoverFound) vm.updateNoCoverFound(id, false, prefs)
                                            if (newGenres.size == 2) showGenreMenu = false
                                        }
                                    )
                                }
                            }
                          }
                          // Botón intercambiar orden — solo si hay 2 géneros
                          if (book.genres.size == 2) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        vm.updateGenres(id, listOf(book.genres[1], book.genres[0]), prefs)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.SwapHoriz,
                                    contentDescription = stringResource(R.string.cd_swap_genres),
                                    tint = Accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                          }
                        }
                    }
                    Box {
                        // v20.7: xN = relecturas iniciadas (incluyendo la en curso)
                        val completedRR = startedRereads(book.dateEvents)
                        // Ambos badges: FINISHED+isRereading, o REREADING+endDate (retrocompat), o solo el estado
                        when {
                            book.status == BookStatus.FINISHED && book.isRereading -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StatusBadge(BookStatus.FINISHED)
                                    StatusBadge(BookStatus.REREADING)
                                    if (completedRR > 0) Text("×$completedRR", color = Color(0xFF06B6D4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            book.status == BookStatus.REREADING -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StatusBadge(BookStatus.FINISHED)
                                    StatusBadge(BookStatus.REREADING)
                                    if (completedRR > 0) Text("×$completedRR", color = Color(0xFF06B6D4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            book.status == BookStatus.FINISHED && completedRR > 0 -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StatusBadge(book.status)
                                    Text("×$completedRR", color = Color(0xFF06B6D4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> StatusBadge(book.status)
                        }
                        Box(Modifier.matchParentSize().clickable { showStatusMenu = true })
                        DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                            Text(stringResource(R.string.txt_c4b4e77a), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            SHELF_ORDER.forEach { s -> DropdownMenuItem(text = { Text(statusLabel(s), color = if (book.status == s) statusColor(s) else theme.textMain) }, onClick = { vm.updateStatus(id, s, prefs); refreshWidgetForBookIfSelected(context, id); showStatusMenu = false }) }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                // v20.0 (G3): título "Resumen global" sobre las pills agregadas del libro,
                // SOLO si hay relecturas (si no, las pills son auto-explicativas).
                val hasRereads = book.dateEvents.any { it.type == "reread" }
                // "Resumen global" eliminado (v21.37)

                // Stats row — solo si NO hay relecturas (con relecturas las pills van por ciclo)
                // v21.36: cuando hasRereads=true este bloque se oculta; los datos van a las pills de ciclo
                if (!hasRereads) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (stats != null && book.status == BookStatus.FINISHED) {
                        val finishedPages = bookSessions.sumOf { it.pages }
                        val effectiveTotalFinished = when {
                            book.firstFunctionalPage != null && book.lastFunctionalPage != null ->
                                (book.lastFunctionalPage - book.firstFunctionalPage + 1).coerceAtLeast(1)
                            book.lastFunctionalPage != null -> book.lastFunctionalPage
                            else -> book.pages
                        }
                        StatBox(fmtDaysLocalized(stats.days), stringResource(R.string.stat_days), Modifier.weight(1f), theme)
                        run {
                            val totalMinsF = bookSessions.mapNotNull { it.minutes }.sum()
                            if (totalMinsF > 0) StatBox(fmtMinutes(totalMinsF), stringResource(R.string.stat_total_time), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
                        }
                        run {
                            val ppdFinished: Double? = if (finishedPages > 0) {
                                val sessionDaysFinished = bookSessions.map { it.date }.toSet().size.coerceAtLeast(1)
                                finishedPages.toDouble() / sessionDaysFinished
                            } else stats.pagesPerDay
                            if (ppdFinished != null) {
                                StatBox(String.format("%.1f", ppdFinished), stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, highlight = true)
                            }
                        }
                        if (finishedPages > 0 && effectiveTotalFinished > 0) {
                            val pctFinished = (finishedPages * 100 / effectiveTotalFinished).coerceIn(0, 100)
                            StatBox("$pctFinished%", stringResource(R.string.pill_porcentaje_leido), Modifier.weight(1f), theme)
                        }
                    }
                    if (stats != null && (book.status == BookStatus.READING || book.status == BookStatus.REREADING || book.isRereading)) {
                        val pagesRead = bookSessions.sumOf { it.pages }
                        StatBox(fmtDaysLocalized(stats.days), stringResource(R.string.pill_dias_leyendo), Modifier.weight(1f), theme)
                        run {
                            val totalMinsR = bookSessions.mapNotNull { it.minutes }.sum()
                            if (totalMinsR > 0) StatBox(fmtMinutes(totalMinsR), stringResource(R.string.stat_total_time), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
                        }
                        if (pagesRead > 0) StatBox("${pagesRead}p", stringResource(R.string.pill_pags_leidas), Modifier.weight(1f), theme, highlight = true, highlightColor = Color(0xFF34D399))
                        val effectiveTotal = when {
                            book.firstFunctionalPage != null && book.lastFunctionalPage != null ->
                                (book.lastFunctionalPage - book.firstFunctionalPage + 1).coerceAtLeast(1)
                            book.lastFunctionalPage != null -> book.lastFunctionalPage
                            else -> book.pages
                        }
                        val pct = if (effectiveTotal > 0) (pagesRead * 100 / effectiveTotal).coerceIn(0, 100) else 0
                        if (pagesRead > 0) StatBox("$pct%", stringResource(R.string.pill_porcentaje_leido), Modifier.weight(1f), theme)
                    }
                    // Págs/día de sesiones — solo para libros no FINISHED
                    if (book.status != BookStatus.FINISHED) {
                        val totalSessionPages = bookSessions.sumOf { it.pages }
                        val sessionDays = bookSessions.map { it.date }.toSet().size.coerceAtLeast(1)
                        if (totalSessionPages > 0) {
                            val avgPerSessionDay = totalSessionPages.toDouble() / sessionDays
                            StatBox(String.format("%.1f", avgPerSessionDay), stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, highlight = true)
                        }
                    }
                }
                } // end !hasRereads
                Spacer(Modifier.height(16.dp))

                // Botones de acción (para libros en lectura o releyendo)
                if (book.status == BookStatus.READING || book.status == BookStatus.REREADING || book.isRereading) {
                    // Botón registrar sesión
                    Button(
                        onClick = { autoSessionMinutes = null; showSessionDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text(stringResource(R.string.txt_e810b914), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(10.dp))

                    // Botón mostrar en widget
                    run {
                        var widgetBookId by remember(id) {
                            mutableStateOf(com.lecturameter.widget.loadWidgetBook(context))
                        }
                        val isCurrentWidget = widgetBookId == book.id
                        var showRemoveWidgetDialog by remember { mutableStateOf(false) }

                        if (showRemoveWidgetDialog) {
                            AlertDialog(
                                onDismissRequest = { showRemoveWidgetDialog = false },
                                containerColor = theme.bgMid,
                                title = { Text(stringResource(R.string.txt_d4b36426), color = theme.textMain, fontWeight = FontWeight.Bold) },
                                text = { Text(stringResource(R.string.txt_40a68828), color = theme.textMuted) },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            com.lecturameter.widget.saveWidgetBook(context, -1L)
                                            com.lecturameter.widget.requestBookWidgetUpdate(context)
                                            widgetBookId = -1L
                                            showRemoveWidgetDialog = false
                                            android.widget.Toast.makeText(context, context.getString(R.string.msg_book_no_longer_widget, book.title), android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Red),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text(stringResource(R.string.txt_f46b3218)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRemoveWidgetDialog = false }) {
                                        Text(stringResource(R.string.txt_847607d7), color = Red)
                                    }
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (isCurrentWidget) {
                                    showRemoveWidgetDialog = true
                                } else {
                                    showBookInWidget(context, book.id)
                                    widgetBookId = book.id
                                    android.widget.Toast.makeText(context, context.getString(R.string.msg_now_displayed_widget), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isCurrentWidget) Color(0xFF10B981) else Accent.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isCurrentWidget) Color(0xFF10B981) else Accent
                            )
                        ) {
                            Text(
                                if (isCurrentWidget) stringResource(R.string.widget_currently_showing) else stringResource(R.string.widget_show_button),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                // ── Páginas totales (editable) ─────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(stringResource(R.string.txt_ebd28578), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (!editingPages) {
                        Text("${book.pages}", color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        val pencilColor = if (book.status == BookStatus.READING || book.status == BookStatus.REREADING) Color(0xFFF59E0B) else Accent
                        IconButton(onClick = { pagesOriginal = book.pages; editingPages = true; pagesInput = book.pages.toString() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, null, tint = pencilColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (editingPages) {
                    OutlinedTextField(
                        value = pagesInput,
                        onValueChange = { v ->
                            pagesInput = v.filter { c -> c.isDigit() }
                            // Actualización en tiempo real mientras se escribe
                            val p = pagesInput.toIntOrNull()
                            if (p != null && p > 0) { vm.updatePages(id, p, prefs); refreshWidgetForBookIfSelected(context, id) }
                        },
                        placeholder = { Text(stringResource(R.string.txt_5a411185), color = theme.textDim) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { val p = pagesInput.toIntOrNull(); if (p != null && p > 0) { vm.updatePages(id, p, prefs); refreshWidgetForBookIfSelected(context, id) }; editingPages = false }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.txt_d3270bdb)) }
                        OutlinedButton(onClick = { val orig = pagesOriginal; pagesInput = orig.toString(); if (orig > 0) { vm.updatePages(id, orig, prefs); refreshWidgetForBookIfSelected(context, id) }; editingPages = false }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.txt_847607d7)) }
                    }
                }
                Spacer(Modifier.height(20.dp))

                // ── Páginas funcionales (editable a posteriori) ─────────────────
                run {
                    var editingFuncPages by remember { mutableStateOf(false) }
                    var funcFirstInput by remember(book.firstFunctionalPage) { mutableStateOf(book.firstFunctionalPage?.toString() ?: "") }
                    var funcLastInput  by remember(book.lastFunctionalPage)  { mutableStateOf(book.lastFunctionalPage?.toString()  ?: "") }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(stringResource(R.string.txt_b9015f44), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (!editingFuncPages) {
                            val label = when {
                                book.firstFunctionalPage != null && book.lastFunctionalPage != null ->
                                    "${book.firstFunctionalPage}–${book.lastFunctionalPage}  ›  ${book.lastFunctionalPage - book.firstFunctionalPage + 1}p"
                                book.firstFunctionalPage != null -> "desde ${book.firstFunctionalPage}"
                                book.lastFunctionalPage != null  -> "hasta ${book.lastFunctionalPage}"
                                else -> "—"
                            }
                            Text(label, color = theme.textMain, fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = { editingFuncPages = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, null, tint = Accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (editingFuncPages) {
                        Text(stringResource(R.string.txt_6f50e674), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = funcFirstInput,
                                onValueChange = { funcFirstInput = it.filter { c -> c.isDigit() } },
                                label = { Text(stringResource(R.string.txt_3c75ceda), color = theme.textDim, fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = funcLastInput,
                                onValueChange = { funcLastInput = it.filter { c -> c.isDigit() } },
                                label = { Text(stringResource(R.string.txt_27de21f4), color = theme.textDim, fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.updateFunctionalPages(id, funcFirstInput.toIntOrNull(), funcLastInput.toIntOrNull(), prefs)
                                    refreshWidgetForBookIfSelected(context, id)
                                    editingFuncPages = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.txt_d3270bdb)) }
                            OutlinedButton(
                                onClick = {
                                    funcFirstInput = book.firstFunctionalPage?.toString() ?: ""
                                    funcLastInput  = book.lastFunctionalPage?.toString()  ?: ""
                                    editingFuncPages = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.txt_847607d7)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))

                // Rating
                Text(stringResource(R.string.txt_ec0bec6b), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                RatingSelector(selectedRating) { r -> selectedRating = r; vm.updateRating(id, r, prefs) }
                Spacer(Modifier.height(20.dp))

                // Comment
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(stringResource(R.string.txt_41f5582a), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (!editingComment) IconButton(onClick = { editingComment = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, tint = Accent, modifier = Modifier.size(16.dp)) }
                }
                if (editingComment) {
                    OutlinedTextField(value = commentText, onValueChange = { commentText = it }, placeholder = { Text(stringResource(R.string.txt_ea889da9), color = theme.textDim) }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), maxLines = 6)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val trimmed = commentText.trim()
                            if (activeEdition != null) {
                                vm.updateEditionComment(id, activeEdition.id, trimmed, prefs)
                            } else {
                                vm.updateComment(id, trimmed, prefs)
                            }
                            editingComment = false
                        }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.txt_d3270bdb)) }
                        OutlinedButton(onClick = { commentText = effectiveComment; editingComment = false }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.txt_847607d7)) }
                    }
                } else {
                    if (effectiveComment.isNotBlank()) Surface(shape = RoundedCornerShape(10.dp), color = Color(0x0D6366F1), border = BorderStroke(1.dp, Color(0x1A6366F1))) { Text(effectiveComment, color = theme.textMain, fontSize = 13.sp, modifier = Modifier.padding(12.dp)) }
                    else Text(stringResource(R.string.txt_e33e3006), color = theme.textDim, fontSize = 12.sp)
                }
                Spacer(Modifier.height(20.dp))

                // Editable dates (v19.8: soporta múltiples ciclos vía dateEvents)
                run {
                    var editingDates by remember { mutableStateOf(false) }
                    var dateError by remember { mutableStateOf("") }
                    // v20.0: índice del evento que el usuario quiere eliminar (con confirm)
                    var dateToDelete by remember { mutableStateOf<Int?>(null) }

                    // Lista de eventos efectiva (migra legacy si dateEvents vacío)
                    val effectiveEvents = remember(book.dateEvents, book.startDate, book.endDate, book.dropDate, book.resumedDate) {
                        sortedDateEvents(migrateLegacyToEvents(book))
                    }

                    // Estado de edición: pares (evento, fecha-en-display) para todos los eventos
                    val editInputs = remember(editingDates, effectiveEvents) {
                        mutableStateListOf<Pair<DateEvent, androidx.compose.runtime.MutableState<String>>>().apply {
                            effectiveEvents.forEach { ev ->
                                add(ev to mutableStateOf(storedToDisplay(ev.date)))
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(stringResource(R.string.txt_5d69fc39), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (!editingDates) IconButton(onClick = { editingDates = true; dateError = "" }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, tint = Accent, modifier = Modifier.size(16.dp)) }
                    }

                    if (editingDates) {
                        // Render de campos editables agrupados por ciclo
                        var lastSection = ""
                        val editRereads = editInputs.map { it.first }.filter { it.type == "reread" }.sortedBy { it.occurrence }
                        val editRereadEnds = editInputs.map { it.first }.filter { it.type == "reread_end" }.sortedBy { it.occurrence }
                        val multipleEditRereads = editRereads.size > 1
                        // v20.4 (B3): índice posicional en lista ordenada para asignación drop/resume
                        val editEvList = editInputs.map { it.first }
                        val editRereadIdxMap = editRereads.associate { r ->
                            r.occurrence to editEvList.indexOfFirst { it.type == "reread" && it.occurrence == r.occurrence }
                        }
                        val editRereadEndIdxMap = editRereadEnds.associate { r ->
                            r.occurrence to editEvList.indexOfFirst { it.type == "reread_end" && it.occurrence == r.occurrence }
                        }
                        fun editEvKey(date: String, type: String) = date + typeOrder(type).toString().padStart(2, '0')
                        editInputs.forEachIndexed { idx, (ev, state) ->
                            val section = when (ev.type) {
                                "reread", "reread_end" -> "reread_${ev.occurrence}"
                                "start", "end" -> "main"
                                "drop", "resume" -> {
                                    var cycleSection = "main"
                                    for (r in editRereads) {
                                        val rStartIdx = editRereadIdxMap[r.occurrence] ?: continue
                                        val rEndIdx = editRereadEndIdxMap[r.occurrence] ?: Int.MAX_VALUE
                                        if (idx > rStartIdx && idx < rEndIdx) {
                                            cycleSection = "reread_${r.occurrence}"; break
                                        }
                                    }
                                    cycleSection
                                }
                                else -> "other"
                            }
                            if (section != lastSection) {
                                val label = when {
                                    section == "main" -> stringResource(R.string.dates_section_reading)
                                    section.startsWith("reread_") -> {
                                        val n = section.removePrefix("reread_").toIntOrNull() ?: 1
                                        stringResource(R.string.dates_section_rereading_n, n)
                                    }
                                    else -> ""
                                }
                            }
                            Text("${dateEventLabel(ev)} (dd-mm-yyyy)", color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                OutlinedTextField(
                                    value = state.value,
                                    onValueChange = { state.value = it },
                                    placeholder = { Text(todayDisplay(), color = theme.textDim) },
                                    colors = fieldColors(theme),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                                )
                                IconButton(onClick = { dateToDelete = idx }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.txt_00b343f8), tint = Red, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (dateError.isNotBlank()) Text(dateError, color = Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))

                        // v20.0: confirmación eliminar fecha
                        val delIdx = dateToDelete
                        if (delIdx != null && delIdx in editInputs.indices) {
                            val ev = editInputs[delIdx].first
                            AlertDialog(
                                onDismissRequest = { dateToDelete = null },
                                containerColor = theme.bgMid,
                                title = { Text(stringResource(R.string.txt_00b343f8), color = theme.textMain, fontWeight = FontWeight.Bold) },
                                text = { Text(stringResource(R.string.delete_date_confirm, dateEventLabel(ev)), color = theme.textMuted) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        // Persistir: quitar el evento de la lista y guardar
                                        val remaining = mutableListOf<DateEvent>()
                                        editInputs.forEachIndexed { i, pair ->
                                            if (i == delIdx) return@forEachIndexed
                                            val s = pair.second.value.trim()
                                            if (s.isBlank()) return@forEachIndexed
                                            if (parseFlexibleDate(s) != null) {
                                                remaining.add(pair.first.copy(date = displayToStored(s)))
                                            } else {
                                                remaining.add(pair.first)
                                            }
                                        }
                                        vm.updateDateEvents(id, remaining, prefs)
                                        refreshWidgetForBookIfSelected(context, id)
                                        editInputs.removeAt(delIdx)
                                        dateToDelete = null
                                    }) { Text(stringResource(R.string.txt_5b5c9f9d), color = Red, fontWeight = FontWeight.Bold) }
                                },
                                dismissButton = { TextButton(onClick = { dateToDelete = null }) { Text(stringResource(R.string.txt_847607d7), color = Accent) } }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            OutlinedButton(
                                onClick = { editingDates = false; dateError = "" },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.txt_847607d7)) }
                            Button(onClick = {
                                // Validar todas las fechas
                                val newEvents = mutableListOf<DateEvent>()
                                for ((ev, state) in editInputs) {
                                    val s = state.value.trim()
                                    if (s.isBlank()) continue  // se omite el evento si vacío
                                    if (parseFlexibleDate(s) == null) {
                                        dateError = context.getString(R.string.err_date_invalid_label, dateEventLabelCtx(ev, context))
                                        return@Button
                                    }
                                    newEvents.add(ev.copy(date = displayToStored(s)))
                                }
                                vm.updateDateEvents(id, newEvents, prefs)
                                refreshWidgetForBookIfSelected(context, id)
                                editingDates = false
                                dateError = ""
                            }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.txt_d3270bdb)) }
                        }
                    } else {
                        // Vista de lectura: agrupar por sección (lectura original / relectura N)
                        if (effectiveEvents.isNotEmpty()) {
                            Surface(shape = RoundedCornerShape(10.dp), color = Color(0x0D6366F1), border = BorderStroke(1.dp, Color(0x1A6366F1))) {
                                Column(Modifier.padding(12.dp)) {
                                    val dispRereads = effectiveEvents.filter { it.type == "reread" }.sortedBy { it.occurrence }
                                    val dispRereadEnds = effectiveEvents.filter { it.type == "reread_end" }.sortedBy { it.occurrence }
                                    // v20.4 (B3): índices posicionales para asignación drop/resume
                                    val rereadIndexMap = dispRereads.associate { r ->
                                        r.occurrence to effectiveEvents.indexOfFirst { it.type == "reread" && it.occurrence == r.occurrence }
                                    }
                                    val rereadEndIndexMap = dispRereadEnds.associate { r ->
                                        r.occurrence to effectiveEvents.indexOfFirst { it.type == "reread_end" && it.occurrence == r.occurrence }
                                    }
                                    // v20.5: asignar cada evento a su sección
                                    fun assignSection(ev: DateEvent, idx: Int): String = when (ev.type) {
                                        "reread", "reread_end" -> "reread_${ev.occurrence}"
                                        "start", "end" -> "main"
                                        "drop", "resume" -> {
                                            var cycleSection = "main"
                                            for (r in dispRereads) {
                                                val rStartIdx = rereadIndexMap[r.occurrence] ?: continue
                                                val rEndIdx = rereadEndIndexMap[r.occurrence] ?: Int.MAX_VALUE
                                                if (idx > rStartIdx && idx < rEndIdx) {
                                                    cycleSection = "reread_${r.occurrence}"; break
                                                }
                                            }
                                            cycleSection
                                        }
                                        else -> "other"
                                    }
                                    // v20.5: agrupar eventos por sección y mostrarlos en orden de ciclo
                                    val eventsWithSection = effectiveEvents.mapIndexed { idx, ev ->
                                        Pair(assignSection(ev, idx), ev)
                                    }
                                    val sectionOrder = buildList {
                                        add("main")
                                        dispRereads.forEach { add("reread_${it.occurrence}") }
                                    }
                                    val eventsBySection = eventsWithSection.groupBy { it.first }
                                    sectionOrder.forEachIndexed { sIdx, section ->
                                        val eventsInSection = eventsBySection[section] ?: return@forEachIndexed
                                        if (sIdx > 0) Spacer(Modifier.height(8.dp))
                                        val label = when {
                                            section == "main" -> stringResource(R.string.dates_section_reading)
                                            section.startsWith("reread_") -> {
                                                val n = section.removePrefix("reread_").toIntOrNull() ?: 1
                                                stringResource(R.string.dates_section_rereading_n, n)
                                            }
                                            else -> ""
                                        }
                                        val color = if (section == "main") Accent else Color(0xFF06B6D4)
                                        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                                        eventsInSection.forEachIndexed { eIdx, (_, ev) ->
                                            if (eIdx > 0) Spacer(Modifier.height(3.dp))
                                            Row {
                                                Text("${dateEventLabel(ev)}: ", color = theme.textDim, fontSize = 13.sp)
                                                Text(fmtDate(ev.date), color = theme.textMuted, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        } else Text(stringResource(R.string.txt_11a95b58), color = theme.textDim, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Estadísticas por ciclo (v20.0 G3: pills por ciclo, solo con relecturas) ─
                run {
                    val cycles = computeCycleStats(book, vm.sessions)
                    if (cycles.size > 1) {
                        // Nivel 1: título principal
                        Text(stringResource(R.string.txt_aa1b8e40), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                        cycles.forEachIndexed { cIdx, c ->
                            val isOriginal = c.readingIndex == 0
                            val color = if (isOriginal) Accent else Color(0xFF06B6D4)
                            // Niveles 2/3/...: cada ciclo tiene su título + pills propios
                            if (cIdx > 0) Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                                Spacer(Modifier.width(8.dp))
                                val ongoingSuffix = if (c.isOngoing) " (${stringResource(R.string.status_reading).lowercase()})" else ""
                                val cycleDisplayLabel = when {
                                    c.readingIndex == 0 -> stringResource(R.string.cycle_label_reading)
                                    c.sessions.isNotEmpty() && c.readingIndex > 1 -> stringResource(R.string.cycle_label_rereading_n, c.readingIndex)
                                    else -> stringResource(R.string.cycle_label_rereading)
                                }
                                Text("📖 $cycleDisplayLabel$ongoingSuffix", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            // Pills del ciclo (v21.33: Row; v21.36: añadido % + sesiones siempre visibles)
                            val sessPages = c.sessions.sumOf { it.pages }
                            val sessMins = c.sessions.mapNotNull { it.minutes }.sum()
                            val daysLabel = if (isOriginal) stringResource(R.string.pill_dias_leyendo) else stringResource(R.string.pill_dias_releyendo)
                            val daysTxt = c.days?.let { "$it ${if (it == 1) stringResource(R.string.word_day) else stringResource(R.string.word_days)}" } ?: "—"
                            // v21.36: % del ciclo = páginas del ciclo / páginas funcionales del libro
                            val effectiveTotalCycle = when {
                                book.firstFunctionalPage != null && book.lastFunctionalPage != null ->
                                    (book.lastFunctionalPage - book.firstFunctionalPage + 1).coerceAtLeast(1)
                                book.lastFunctionalPage != null -> book.lastFunctionalPage
                                else -> book.pages
                            }
                            val cyclePct = if (sessPages > 0 && effectiveTotalCycle > 0)
                                (sessPages * 100 / effectiveTotalCycle).coerceIn(0, 100) else null
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                StatBox(daysTxt, daysLabel, Modifier.weight(1f), theme)
                                if (sessMins > 0) StatBox(fmtMinutes(sessMins), stringResource(R.string.stat_total_time), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
                                if (sessPages > 0) StatBox("${sessPages}p", stringResource(R.string.pill_pags_leidas), Modifier.weight(1f), theme, highlight = true, highlightColor = Color(0xFF34D399))
                                if (cyclePct != null) StatBox("$cyclePct%", stringResource(R.string.pill_porcentaje_leido), Modifier.weight(1f), theme)
                                if (c.pagesPerDay != null) {
                                    StatBox(String.format("%.1f", c.pagesPerDay), stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, highlight = true, highlightColor = color)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }

                // ── Historial de sesiones (v20.0 G2: una sección por ciclo) ──
                // v20.3: precalculamos cycles para poder usar pagesPerDay en las pills de sesiones
                val sessionCycles = computeCycleStats(book, vm.sessions)
                if (bookSessions.isNotEmpty()) {
                    // Agrupar sesiones por readingIndex, ordenado ascendente
                    val cycleSessionsByIndex = bookSessions.groupBy { it.readingIndex ?: 0 }
                        .toSortedMap()
                    // v20.2: si hay >1 relectura numeramos todas
                    val multipleRereadSections = cycleSessionsByIndex.keys.count { it > 0 } > 1

                    cycleSessionsByIndex.entries.forEachIndexed { sectionIdx, (readingIdx, cycleSessions) ->
                        val isOriginal = readingIdx == 0
                        val sectionLabel = when {
                            isOriginal -> stringResource(R.string.sessions_reading)
                            multipleRereadSections -> stringResource(R.string.sessions_rereading_n, readingIdx)
                            else -> stringResource(R.string.sessions_rereading)
                        }
                        val sectionAccent = if (isOriginal) Accent else Color(0xFF06B6D4)
                        // Estado expand por sección. La sección 0 hereda el flag legacy `showSessionSummary` para
                        // mantener compat con el LaunchedEffect del heatmap si no hay match explícito.
                        val expanded = sectionExpanded[readingIdx] ?: (isOriginal && showSessionSummary)

                        // States locales por sección — clave (id, readingIdx) para aislar entre libros y ciclos
                        var localSearchQuery by rememberSaveable(id, readingIdx) { mutableStateOf("") }
                        var localChronological by rememberSaveable(id, readingIdx) { mutableStateOf(true) }
                        var showDeleteAll by remember(id, readingIdx) { mutableStateOf(false) }

                        if (sectionIdx > 0) Spacer(Modifier.height(10.dp))

                        Surface(
                            onClick = {
                                val newVal = !expanded
                                sectionExpanded[readingIdx] = newVal
                                if (isOriginal) showSessionSummary = newVal
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = theme.surface,
                            border = BorderStroke(1.dp, if (isOriginal) theme.border else Color(0x3306B6D4)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sectionLabel, color = sectionAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("${cycleSessions.size}", color = sectionAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = sectionAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (expanded) {
                            Spacer(Modifier.height(10.dp))

                            // Filtrar sesiones de ESTE ciclo
                            val filteredLocal = if (localSearchQuery.isBlank()) cycleSessions
                            else if (localSearchQuery.trim().startsWith("#")) {
                                val num = localSearchQuery.trim().removePrefix("#").toIntOrNull()
                                if (num != null) cycleSessions.filter { detailSessionNumberMap[it.id] == num }
                                else cycleSessions
                            } else cycleSessions.filter { it.note.contains(localSearchQuery.trim(), ignoreCase = true) }

                            val sortedLocal = if (localChronological)
                                filteredLocal.sortedBy { it.date + it.id.toString().padStart(20, '0') }
                            else
                                filteredLocal.sortedByDescending { it.date + it.id.toString().padStart(20, '0') }

                            // Orden + botón Eliminar todas (v20.0 P4)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.txt_26afc116), color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                Surface(
                                    onClick = { localChronological = !localChronological },
                                    shape = RoundedCornerShape(10.dp),
                                    color = theme.surface,
                                    border = BorderStroke(1.dp, theme.border)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            if (localChronological) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            null, tint = sectionAccent, modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            if (localChronological) stringResource(R.string.sort_oldest_first) else stringResource(R.string.sort_newest_first),
                                            color = sectionAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { showDeleteAll = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete all", tint = Red, modifier = Modifier.size(18.dp))
                                }
                            }

                            // Barra de búsqueda
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = theme.surface,
                                border = BorderStroke(1.dp, if (localSearchQuery.isNotBlank()) sectionAccent else theme.border),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🔍", fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                    BasicTextField(
                                        value = localSearchQuery,
                                        onValueChange = { localSearchQuery = it },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(color = theme.textMain, fontSize = 12.sp),
                                        cursorBrush = SolidColor(sectionAccent),
                                        modifier = Modifier.weight(1f).padding(vertical = 9.dp),
                                        decorationBox = { inner ->
                                            if (localSearchQuery.isEmpty()) Text(stringResource(R.string.txt_a27470a5), color = theme.textDim, fontSize = 12.sp)
                                            inner()
                                        }
                                    )
                                    if (localSearchQuery.isNotBlank()) {
                                        IconButton(onClick = { localSearchQuery = "" }, modifier = Modifier.size(26.dp)) {
                                            Icon(Icons.Default.Close, null, tint = theme.textDim, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }

                            // Gráfico de páginas por día (del ciclo)
                            SessionChart(filteredLocal.ifEmpty { cycleSessions }, theme, reversed = !localChronological)
                            Spacer(Modifier.height(14.dp))

                            // Estadísticas rápidas — v20.3: orden Días / Tiempo / Págs / Págs-día / Págs-min
                            val totalSessPages = filteredLocal.sumOf { it.pages }
                            val totalSessMins = filteredLocal.mapNotNull { it.minutes }.sum()
                            val avgSessPagesPerMin = if (totalSessMins > 0) totalSessPages.toDouble() / totalSessMins else null
                            val cycleForSection = sessionCycles.firstOrNull { it.readingIndex == readingIdx }
                            val cycledays = cycleForSection?.days
                            val daysLabel = if (isOriginal) stringResource(R.string.pill_dias_leyendo) else stringResource(R.string.pill_dias_releyendo)
                            val daysTxt = cycledays?.let { "$it ${if (it == 1) stringResource(R.string.word_day) else stringResource(R.string.word_days)}" } ?: "—"
                            // v20.4: si CycleStats no da pagesPerDay (en curso o sin funcPages), calcularlo desde sesiones/días
                            val pagPerDay: Double? = cycleForSection?.pagesPerDay
                                ?: if (cycledays != null && cycledays >= 1 && totalSessPages > 0)
                                    totalSessPages.toDouble() / cycledays else null
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                                StatBox(daysTxt, daysLabel, Modifier.weight(1f), theme)
                                if (totalSessMins > 0) StatBox(fmtMinutes(totalSessMins), stringResource(R.string.stat_total_time), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
                                if (totalSessPages > 0) StatBox("${totalSessPages}p", stringResource(R.string.pill_pags_leidas), Modifier.weight(1f), theme, highlight = true, highlightColor = Color(0xFF34D399))
                                if (pagPerDay != null) StatBox(String.format("%.1f", pagPerDay), stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, highlight = true, highlightColor = sectionAccent)
                                if (avgSessPagesPerMin != null) StatBox(String.format("%.1f", avgSessPagesPerMin), stringResource(R.string.pill_pags_min), Modifier.weight(1f), theme, highlight = true, highlightColor = sectionAccent)
                            }

                            // Lista de sesiones del ciclo
                            sortedLocal.forEach { session ->
                                val isHighlighted = activeHighlightDate != null && session.date == activeHighlightDate
                                if (isHighlighted) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Transparent,
                                        border = BorderStroke(2.dp, Color(0xFFF59E0B)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        SessionRow(session, detailSessionNumberMap[session.id] ?: 0, theme,
                                            onDelete = { vm.deleteSession(session.id, prefs); refreshWidgetForBookIfSelected(context, session.bookId) },
                                            onEdit   = { updated -> vm.updateSession(updated, prefs); refreshWidgetForBookIfSelected(context, updated.bookId) }
                                        )
                                    }
                                } else {
                                    SessionRow(session, detailSessionNumberMap[session.id] ?: 0, theme,
                                        onDelete = { vm.deleteSession(session.id, prefs); refreshWidgetForBookIfSelected(context, session.bookId) },
                                        onEdit   = { updated -> vm.updateSession(updated, prefs); refreshWidgetForBookIfSelected(context, updated.bookId) }
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            Spacer(Modifier.height(12.dp))

                            // Confirm dialog eliminar todas las sesiones del ciclo
                            if (showDeleteAll) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteAll = false },
                                    containerColor = theme.bgMid,
                                    title = { Text(stringResource(R.string.txt_995fe186), color = theme.textMain, fontWeight = FontWeight.Bold) },
                                    text = { Text(stringResource(R.string.txt_632bfb20), color = theme.textMuted) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            vm.deleteSessions(cycleSessions.map { it.id }, prefs)
                                            refreshWidgetForBookIfSelected(context, id)
                                            showDeleteAll = false
                                        }) { Text(stringResource(R.string.txt_5b5c9f9d), color = Red, fontWeight = FontWeight.Bold) }
                                    },
                                    dismissButton = { TextButton(onClick = { showDeleteAll = false }) { Text(stringResource(R.string.txt_847607d7), color = Accent) } }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Toggle "Releyendo" — para libros terminados o en relectura (v19.8)
                if (book.status == BookStatus.FINISHED || book.status == BookStatus.REREADING) {
                    val rereading = book.status == BookStatus.REREADING || book.isRereading
                    OutlinedButton(
                        onClick = { vm.toggleRereading(id, prefs); refreshWidgetForBookIfSelected(context, id) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (rereading) Color(0xFF06B6D4).copy(alpha = 0.6f) else theme.border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (rereading) Color(0xFF06B6D4) else theme.textMuted,
                            containerColor = if (rereading) Color(0xFF06B6D4).copy(alpha = 0.1f) else Color.Transparent
                        )
                    ) {
                        Text(if (rereading) "📖 ${stringResource(R.string.date_event_reread_end)}" else "📖 ${stringResource(R.string.date_event_reread)}", fontWeight = FontWeight.SemiBold)
                    }
                }

                // v20.0 (G5): botón Abandonar — solo si Leyendo/Releyendo
                if (book.status == BookStatus.READING || book.status == BookStatus.REREADING) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showAbandonDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFF87171),
                            containerColor = Color(0xFFF87171).copy(alpha = 0.08f)
                        )
                    ) {
                        Text(stringResource(R.string.txt_7603b36e), fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0x33F87171)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red, containerColor = Color(0x1AF87171))) {
                    Icon(Icons.Default.Delete, null, tint = Red); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.txt_b375487f), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
} // v21.34: cierre de DetailScreen faltante

// ── SessionHistoryScreen (drawer lateral) ─────────────────────────────────────

/** Clave compuesta para agrupar sesiones por libro + idioma de edición (v12). */
private data class BookLangKey(val bookId: Long, val language: String)

@Composable
fun SessionHistoryScreen(vm: BooksViewModel, theme: Theme, onClose: () -> Unit, onDetail: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("lecturameter", android.content.Context.MODE_PRIVATE)

    var newestFirst by remember { mutableStateOf(vm.savedSessionNewestFirst) }
    var searchQuery by remember { mutableStateOf("") }

    val booksWithSessions = remember(vm.sessions, vm.books) {
        // Agrupar por (bookId, language) — misma lengua = mismo "libro", distinta lengua = libro separado
        val bookEditionMap = vm.books.associate { book ->
            book.id to book.editions.associate { it.id to it.language }
        }
        val keys = vm.sessions.mapNotNull { s ->
            val book = vm.books.find { it.id == s.bookId } ?: return@mapNotNull null
            val edMap = bookEditionMap[s.bookId] ?: emptyMap()
            val lang = s.editionId?.let { edMap[it] } ?: run {
                // Legacy: sin editionId → asignar al idioma de la edición activa
                book.editions.firstOrNull { it.isActive }?.language ?: "original"
            }
            BookLangKey(s.bookId, lang)
        }.toSet()
        keys.mapNotNull { key ->
            val book = vm.books.find { it.id == key.bookId } ?: return@mapNotNull null
            book to key.language
        }.sortedBy { (book, _) -> book.title }
    }

    // Numeración FIJA por (bookId, lang): #1 = sesión más antigua de esa combinación
    val fixedNumberMap = remember(vm.sessions, vm.books) {
        val map = mutableMapOf<Long, Int>()
        val bookEditionMap = vm.books.associate { book ->
            book.id to book.editions.associate { it.id to it.language }
        }
        // Agrupar por (bookId, language)
        val byBookLang = vm.sessions.groupBy { s ->
            val edMap = bookEditionMap[s.bookId] ?: emptyMap()
            val lang = s.editionId?.let { edMap[it] }
                ?: vm.books.find { it.id == s.bookId }?.editions?.firstOrNull { it.isActive }?.language
                ?: "original"
            "${s.bookId}_$lang"
        }
        for ((_, sessions) in byBookLang) {
            sessions.sortedBy { it.date + it.id.toString().padStart(20, '0') }
                .forEachIndexed { idx, s -> map[s.id] = idx + 1 }
        }
        map
    }

    // Filtrar sesiones según búsqueda: "#3" → número fijo, "título" → libro exacto, texto libre → nota
    val filteredSessions = remember(vm.sessions, searchQuery, fixedNumberMap) {
        val q = searchQuery.trim()
        if (q.isBlank()) vm.sessions
        else if (q.startsWith("#")) {
            val num = q.removePrefix("#").toIntOrNull()
            if (num != null) vm.sessions.filter { fixedNumberMap[it.id] == num }
            else vm.sessions
        } else if (q.length > 2 && q.startsWith("\"") && q.endsWith("\"")) {
            val exactTitle = q.removeSurrounding("\"").trim()
            val matchingBookIds = vm.books.filter { it.title.equals(exactTitle, ignoreCase = true) }.map { it.id }.toSet()
            vm.sessions.filter { it.bookId in matchingBookIds }
        } else {
            vm.sessions.filter { it.note.contains(q, ignoreCase = true) }
        }
    }

    // (bookId, lang) que tienen sesiones tras el filtro
    val bookEditionMapForFilter = vm.books.associate { book ->
        book.id to book.editions.associate { it.id to it.language }
    }
    val filteredBooksWithSessions = remember(filteredSessions, booksWithSessions) {
        val filteredKeys = filteredSessions.map { s ->
            val edMap = bookEditionMapForFilter[s.bookId] ?: emptyMap()
            val lang = s.editionId?.let { edMap[it] }
                ?: vm.books.find { it.id == s.bookId }?.editions?.firstOrNull { it.isActive }?.language
                ?: "original"
            "${s.bookId}_$lang"
        }.toSet()
        booksWithSessions.filter { (book, lang) -> "${book.id}_$lang" in filteredKeys }
    }

    // Clave de expansión: "bookId_language"
    val expanded = remember(booksWithSessions) {
        mutableStateMapOf<String, Boolean>().also { map ->
            booksWithSessions.forEach { (book, lang) -> map["${book.id}_$lang"] = false }
        }
    }
    // v20.5: expansión de sub-ciclos en historial: "bookId_language_readingIdx"
    val cycleExpanded = remember { mutableStateMapOf<String, Boolean>() }

    // Auto-expandir cuando hay búsqueda activa
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            filteredBooksWithSessions.forEach { (book, lang) -> expanded["${book.id}_$lang"] = true }
        }
    }

    val totalPages = vm.sessions.sumOf { it.pages }
    val totalMins  = vm.sessions.mapNotNull { it.minutes }.sum()

    Column(Modifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(theme.bgDark, theme.bgMid)))
                .padding(top = 40.dp, bottom = 16.dp, start = 20.dp, end = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📖", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.txt_beea2815),
                        color = theme.textMain, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )

                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                    HistoryStat("${vm.sessions.size}", stringResource(R.string.history_stat_sessions), Modifier.weight(1f), theme, valueColor = Accent)
                    HistoryStat(if (totalMins > 0) fmtMinutes(totalMins) else "—", stringResource(R.string.history_stat_total_time), Modifier.weight(1f), theme, valueColor = Sky)
                    HistoryStat("$totalPages", stringResource(R.string.pill_paginas_totales), Modifier.weight(1f), theme, valueColor = Green)
                    HistoryStat(if (totalMins > 0) String.format("%.1f", totalPages.toDouble() / totalMins) else "—", stringResource(R.string.pill_pags_min), Modifier.weight(1f), theme, valueColor = Green)
                }
            }
        }

        HorizontalDivider(color = theme.border, thickness = 0.5.dp)

        // ── Orden + Búsqueda ──────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .background(theme.bgMid)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Fila de orden
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.txt_26afc116), color = theme.textDim, fontSize = 12.sp)
                Surface(
                    onClick = {
                        val newVal = !newestFirst
                        newestFirst = newVal
                        vm.savedSessionNewestFirst = newVal
                        prefs.edit().putBoolean("session_newest_first", newVal).apply()
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (newestFirst) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            null, tint = Accent, modifier = Modifier.size(15.dp)
                        )
                        Text(
                            if (newestFirst) stringResource(R.string.sort_newest_first) else stringResource(R.string.sort_oldest_first),
                            color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            // Barra de búsqueda
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, if (searchQuery.isNotBlank()) Accent else theme.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔍", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = theme.textMain,
                            fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default
                        ),
                        cursorBrush = SolidColor(Accent),
                        modifier = Modifier.weight(1f).padding(vertical = 9.dp),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text(
                                stringResource(R.string.txt_a27470a5),
                                color = theme.textDim, fontSize = 13.sp
                            )
                            inner()
                        }
                    )
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, tint = theme.textDim, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            // Contador de resultados cuando hay búsqueda activa
            if (searchQuery.isNotBlank()) {
                val searchResultText = if (filteredSessions.size == 1)
                    stringResource(R.string.history_search_one, filteredSessions.size)
                else
                    stringResource(R.string.history_search_other, filteredSessions.size)
                Text(
                    searchResultText,
                    color = if (filteredSessions.isEmpty()) Color(0xFFEF4444) else Accent,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        HorizontalDivider(color = theme.border, thickness = 0.5.dp)

        if (vm.sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.txt_0fd1abda),
                        color = theme.textDim, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else if (filteredSessions.isEmpty() && searchQuery.isNotBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 36.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.txt_57be44d3, searchQuery),
                        color = theme.textDim, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                filteredBooksWithSessions.forEach { (book, lang) ->
                    val expandKey = "${book.id}_$lang"
                    val bookEdMap = vm.books.find { it.id == book.id }?.editions?.associate { it.id to it.language } ?: emptyMap()
                    val bookSessions = filteredSessions.filter { s ->
                        if (s.bookId != book.id) return@filter false
                        val sLang = s.editionId?.let { bookEdMap[it] }
                            ?: book.editions.firstOrNull { it.isActive }?.language
                            ?: "original"
                        sLang == lang
                    }
                    val bookTotalPages = bookSessions.sumOf { it.pages }
                    val bookTotalMins = bookSessions.mapNotNull { it.minutes }.sum()
                    val isExpanded = expanded[expandKey] == true
                    // Obtener la edición del idioma correspondiente para mostrar el flag
                    val editionForLang = book.editions.firstOrNull { it.language == lang }
                    val langFlag = editionForLang?.flag ?: if (lang == "es") "🇪🇸" else "🌐"

                    // ── Fila desplegable del libro ─────────────────────────────
                    item(key = "header_$expandKey") {
                        var showDeleteAllHistory by remember { mutableStateOf(false) }
                        if (showDeleteAllHistory) {
                            AlertDialog(
                                onDismissRequest = { showDeleteAllHistory = false },
                                containerColor = theme.bgMid,
                                title = { Text(stringResource(R.string.txt_995fe186), color = theme.textMain, fontWeight = FontWeight.Bold) },
                                text = { Text(stringResource(R.string.txt_632bfb20), color = theme.textMuted) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        vm.deleteSessions(bookSessions.map { it.id }, prefs)
                                        showDeleteAllHistory = false
                                    }) { Text(stringResource(R.string.txt_5b5c9f9d), color = Red, fontWeight = FontWeight.Bold) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteAllHistory = false }) {
                                        Text(stringResource(R.string.txt_847607d7), color = Accent)
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            onClick = { expanded[expandKey] = !isExpanded },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isExpanded) theme.surface else theme.bgMid,
                            border = BorderStroke(1.dp, if (isExpanded) Accent.copy(alpha = 0.5f) else theme.border),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Accent,
                                        modifier = Modifier.size(18.dp).rotate(if (isExpanded) 0f else -90f).padding(top = 2.dp)
                                    )
                                    Text(langFlag, fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            book.title,
                                            color = theme.textMain,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            softWrap = true,
                                            lineHeight = 17.sp,
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null
                                            ) { onDetail(book.id) }
                                        )
                                        if (book.author.isNotBlank()) {
                                            Text(
                                                book.author,
                                                color = theme.textDim,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    // v20.4 (C1): misma posición/estilo que BookCard shelf
                                    Box(
                                        Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Red.copy(alpha = 0.12f))
                                            .clickable { showDeleteAllHistory = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.txt_995fe186),
                                            tint = Red.copy(alpha = 0.7f),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    DrawerStatChipH("📚 ${bookSessions.size} Ses", Accent, Modifier.weight(1f))
                                    DrawerStatChipH(if (bookTotalMins > 0) "⏱ ${fmtMinutes(bookTotalMins)}" else "⏱ —", Sky, Modifier.weight(1f))
                                    DrawerStatChipH("📄 $bookTotalPages Págs", Green, Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }

                    // ── Sesiones del libro/idioma ──────────────────────────────
                    if (isExpanded) {
                        val sortedSessions = if (newestFirst)
                            bookSessions.sortedByDescending { it.date + it.id.toString().padStart(20, '0') }
                        else
                            bookSessions.sortedBy { it.date + it.id.toString().padStart(20, '0') }

                        // v20.5: si el libro tiene sesiones de varios ciclos, mostrar sub-secciones desplegables
                        val cycleGroups = sortedSessions.groupBy { it.readingIndex ?: 0 }
                        val hasMixedCycles = cycleGroups.keys.any { it > 0 } && cycleGroups.keys.any { it == 0 }

                        if (hasMixedCycles) {
                            // ── Sub-secciones por ciclo ────────────────────────
                            val sortedCycleKeys = cycleGroups.keys.sorted()
                            val multipleCycles = sortedCycleKeys.count { it > 0 } > 1

                            for (cycleIdx in sortedCycleKeys) {
                                val cycleSess = cycleGroups[cycleIdx] ?: continue
                                val cycleKey = "${expandKey}_$cycleIdx"
                                val isCycleOriginal = cycleIdx == 0
                                val cycleAccent = if (isCycleOriginal) Accent else Color(0xFF06B6D4)
                                val isCycleExpanded = cycleExpanded[cycleKey] ?: false

                                item(key = "cycle_header_$cycleKey") {
                                    val cycleLabelStr = when {
                                        isCycleOriginal -> stringResource(R.string.sessions_reading)
                                        multipleCycles -> stringResource(R.string.sessions_rereading_n, cycleIdx)
                                        else -> stringResource(R.string.sessions_rereading)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        onClick = { cycleExpanded[cycleKey] = !isCycleExpanded },
                                        shape = RoundedCornerShape(10.dp),
                                        color = theme.bgMid,
                                        border = BorderStroke(1.dp, if (isCycleOriginal) theme.border else Color(0x3306B6D4)),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(cycleLabelStr, color = cycleAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Text("${cycleSess.size}", color = cycleAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(5.dp))
                                            Icon(
                                                if (isCycleExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = cycleAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (isCycleExpanded) {
                                    itemsIndexed(cycleSess) { _, session ->
                                        val fixedNum = fixedNumberMap[session.id] ?: 0
                                        Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                            HistorySessionCard(
                                                session = session,
                                                sessionNumber = fixedNum,
                                                book = null,
                                                theme = theme,
                                                onDelete = { vm.deleteSession(session.id, prefs); refreshWidgetForBookIfSelected(context, session.bookId) },
                                                onEdit = { updated -> vm.updateSession(updated, prefs); refreshWidgetForBookIfSelected(context, updated.bookId) },
                                                chipWidth = 60.dp
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // ── Lista plana (solo un ciclo) ────────────────────
                            itemsIndexed(sortedSessions) { _, session ->
                                val fixedNum = fixedNumberMap[session.id] ?: 0
                                Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                    HistorySessionCard(
                                        session = session,
                                        sessionNumber = fixedNum,
                                        book = null,
                                        theme = theme,
                                        onDelete = { vm.deleteSession(session.id, prefs); refreshWidgetForBookIfSelected(context, session.bookId) },
                                        onEdit = { updated -> vm.updateSession(updated, prefs); refreshWidgetForBookIfSelected(context, updated.bookId) },
                                        chipWidth = 60.dp
                                    )
                                }
                            }
                        }

                        item(key = "spacer_$expandKey") {
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = theme.border, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

fun fmtMinutes(totalMins: Int): String {
    val h = totalMins / 60
    val m = totalMins % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
fun HistoryStat(value: String, label: String, modifier: Modifier, theme: Theme, valueColor: Color? = null) {
    val color = valueColor ?: theme.textMain
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(10.dp),
        color = valueColor?.copy(alpha = 0.12f) ?: theme.surface,
        border = BorderStroke(1.dp, valueColor?.copy(alpha = 0.35f) ?: theme.border)
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AutoSizeText(value, color = color, maxFontSize = 15.sp, minFontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(label, color = theme.textDim, fontSize = 7.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 9.sp, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DrawerStatChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        AutoSizeText(
            text = text,
            color = color,
            maxFontSize = 10.sp,
            minFontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DrawerStatChipH(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f)),
        modifier = modifier
    ) {
        AutoSizeText(
            text = text,
            color = color,
            maxFontSize = 10.sp,
            minFontSize = 6.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun HistoryPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun HistorySessionCard(
    session: ReadingSession,
    sessionNumber: Int = 0,
    book: Book?,
    theme: Theme,
    onDelete: (() -> Unit)? = null,
    onEdit: ((ReadingSession) -> Unit)? = null,
    chipWidth: androidx.compose.ui.unit.Dp = 76.dp
) {
    val context = LocalContext.current
    val pagesPerMin = if (session.minutes != null && session.minutes > 0)
        String.format("%.1f", session.pages.toDouble() / session.minutes)
    else null

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog    by remember { mutableStateOf(false) }

    // ── Diálogo de confirmación de eliminación ────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.txt_03f6aff4), color = theme.textMain) },
            text  = { Text(stringResource(R.string.dialog_delete_session_date, fmtDate(session.date)), color = theme.textMuted) },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteConfirm = false }) {
                    Text(stringResource(R.string.txt_5b5c9f9d), color = Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.txt_847607d7), color = Accent)
                }
            },
            containerColor = theme.bgMid
        )
    }

    // ── Diálogo de edición ────────────────────────────────────────────────────
    if (showEditDialog) {
        var startPageText by remember { mutableStateOf(session.startPage?.toString() ?: "") }
        var endPageText   by remember { mutableStateOf(session.endPage?.toString() ?: "") }
        var minsText   by remember { mutableStateOf(session.minutes?.toString() ?: "") }
        var noteText   by remember { mutableStateOf(session.note) }
        var dateText   by remember { mutableStateOf(storedToDisplay(session.date)) }
        var dateError  by remember { mutableStateOf("") }
        var pageError  by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_8e892422), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it; dateError = "" },
                        label = { Text(stringResource(R.string.txt_39464d70), color = theme.textDim, fontSize = 12.sp) },
                        isError = dateError.isNotEmpty(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.textMain,
                            unfocusedTextColor = theme.textMain,
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = theme.border
                        )
                    )
                    if (dateError.isNotEmpty()) {
                        Text(dateError, color = Red, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = startPageText,
                            onValueChange = { startPageText = it.filter { c -> c.isDigit() }; pageError = "" },
                            label = { Text(stringResource(R.string.txt_739eacc1), color = theme.textDim, fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = pageError.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = theme.textMain,
                                unfocusedTextColor = theme.textMain,
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = theme.border
                            )
                        )
                        OutlinedTextField(
                            value = endPageText,
                            onValueChange = { endPageText = it.filter { c -> c.isDigit() }; pageError = "" },
                            label = { Text(stringResource(R.string.txt_28be9b81), color = theme.textDim, fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = pageError.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = theme.textMain,
                                unfocusedTextColor = theme.textMain,
                                focusedBorderColor = Accent,
                                unfocusedBorderColor = theme.border
                            )
                        )
                    }
                    if (pageError.isNotEmpty()) {
                        Text(pageError, color = Red, fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = minsText,
                        onValueChange = { minsText = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.txt_0a43aca4), color = theme.textDim, fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.textMain,
                            unfocusedTextColor = theme.textMain,
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = theme.border
                        )
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(stringResource(R.string.txt_7cd6ad03), color = theme.textDim, fontSize = 12.sp) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.textMain,
                            unfocusedTextColor = theme.textMain,
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = theme.border
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val stored = parseFlexibleDate(dateText.trim())
                    if (stored == null) { dateError = context.getString(R.string.err_date_invalid_correct); return@TextButton }
                    // v20.2 (B4+B5): validar páginas
                    val newStart = startPageText.toIntOrNull()
                    val newEnd   = endPageText.toIntOrNull()
                    if (startPageText.isBlank() || endPageText.isBlank()) {
                        pageError = context.getString(R.string.err_pages_start_end_required); return@TextButton
                    }
                    if (newStart != null && newEnd != null) {
                        if (newStart >= newEnd) {
                            pageError = if (newStart >= newEnd)
                                context.getString(R.string.err_page_start_lt_end)
                            else
                                context.getString(R.string.err_page_end_gt_start)
                            return@TextButton
                        }
                    }
                    val pages = if (newStart != null && newEnd != null) newEnd - newStart + 1 else session.pages
                    val mins  = minsText.toIntOrNull()
                    onEdit?.invoke(session.copy(pages = pages, minutes = mins, note = noteText.trim(), date = stored, startPage = newStart, endPage = newEnd))
                    showEditDialog = false
                }) { Text(stringResource(R.string.txt_d3270bdb), color = Accent, fontWeight = FontWeight.Bold) }
            },
            confirmButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(R.string.txt_847607d7), color = Red)
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, theme.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            // ── Cabecera: número + fecha + botones ────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sessionNumber > 0) {
                    Text(
                        "#$sessionNumber",
                        color = Accent.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Text(
                    fmtDate(session.date),
                    color = theme.textDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (onEdit != null) {
                    IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, null, tint = Accent.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Red.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            // Título y autor — solo si el book se pasa (en historial global).
            // Cuando book == null el libro ya aparece en la cabecera del grupo desplegable.
            if (book != null) {
                Text(
                    book.title,
                    color = theme.textMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    softWrap = true
                )
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        color = Accent,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            // Chips de datos — ancho fijo idéntico para todos, texto se reduce si no cabe
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DataChipSm("📄 ${session.pages}p", Accent.copy(alpha = 0.15f), Accent, Modifier.width(chipWidth))
                if (session.minutes != null) {
                    DataChipSm("⏱ ${fmtMinutes(session.minutes)}", Sky.copy(alpha = 0.15f), Sky, Modifier.width(chipWidth))
                }
                if (pagesPerMin != null) {
                    DataChipSm("⚡ $pagesPerMin p/m", Green.copy(alpha = 0.15f), Green, Modifier.width(chipWidth))
                }
                if (session.startPage != null && session.endPage != null) {
                    val amber = Color(0xFFF59E0B)
                    DataChipSm("📖 ${session.startPage}-${session.endPage}", amber.copy(alpha = 0.15f), amber, Modifier.width(chipWidth))
                }
            }
            // Nota — expandible si es larga
            if (session.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                var noteExpanded by remember { mutableStateOf(false) }
                val needsExpand = session.note.length > 90 || session.note.contains('\n')
                Column(
                    modifier = if (needsExpand) Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { noteExpanded = !noteExpanded }
                        .padding(vertical = 2.dp)
                    else Modifier
                ) {
                    Text(
                        "💬 ${session.note}",
                        color = theme.textDim,
                        fontSize = 11.sp,
                        maxLines = if (noteExpanded || !needsExpand) Int.MAX_VALUE else 2,
                        overflow = if (noteExpanded || !needsExpand) TextOverflow.Visible else TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (needsExpand) {
                        Icon(
                            if (noteExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (noteExpanded) stringResource(R.string.cd_collapse_note) else stringResource(R.string.cd_expand_note),
                            tint = Accent.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp).align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DataChip(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AutoSizeText(text, color = fg, maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun DataChipSm(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        AutoSizeText(text, color = fg, maxFontSize = 11.sp, minFontSize = 7.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SessionChart(sessions: List<ReadingSession>, theme: Theme, reversed: Boolean = false) {
    // Agrupa por fecha y suma páginas
    val sorted = sessions
        .groupBy { it.date }
        .mapValues { (_, list) -> list.sumOf { it.pages } }
        .entries
        .let { if (reversed) it.sortedByDescending { e -> e.key } else it.sortedBy { e -> e.key } }
        .takeLast(14) // últimos/primeros 14 días con sesiones

    if (sorted.isEmpty()) return

    val maxPages = sorted.maxOf { it.value }.toFloat().coerceAtLeast(1f)
    val barColor = Brush.verticalGradient(listOf(Accent, Accent2))

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0x0D6366F1),
        border = BorderStroke(1.dp, Color(0x1A6366F1)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (reversed) stringResource(R.string.chart_pages_per_day_recent) else stringResource(R.string.chart_pages_per_day),
                color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                sorted.forEach { (date, pages) ->
                    val fraction = (pages.toFloat() / maxPages).coerceIn(0.05f, 1f)
                                        Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Text("$pages", color = theme.textDim, fontSize = 8.sp, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor)
                        )
                    }
                }
            }
            // Etiquetas de fecha debajo
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                sorted.forEach { (date, _) ->
                    val dayLabel = date.takeLast(2)
                    Text(dayLabel, color = theme.textDim, fontSize = 8.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SessionRow(session: ReadingSession, sessionNumber: Int = 0, theme: Theme, onDelete: () -> Unit, onEdit: (ReadingSession) -> Unit) {
    val context = LocalContext.current
    var showConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(true) }
    val pagesPerMin = if (session.minutes != null && session.minutes > 0)
        String.format("%.1f", session.pages.toDouble() / session.minutes)
    else null
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.txt_03f6aff4), color = theme.textMain) },
            text = { Text(stringResource(R.string.dialog_delete_session_date, fmtDate(session.date)), color = theme.textMuted) },
            confirmButton = { TextButton(onClick = { onDelete(); showConfirm = false }) { Text(stringResource(R.string.txt_5b5c9f9d), color = Red) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.txt_847607d7), color = Accent) } },
            containerColor = theme.bgMid
        )
    }
    if (showEditDialog) {
        var startPageText by remember { mutableStateOf(session.startPage?.toString() ?: "") }
        var endPageText   by remember { mutableStateOf(session.endPage?.toString() ?: "") }
        var minsText  by remember { mutableStateOf(session.minutes?.toString() ?: "") }
        var noteText  by remember { mutableStateOf(session.note) }
        var dateText  by remember { mutableStateOf(storedToDisplay(session.date)) }
        var dateError by remember { mutableStateOf("") }
        var pageError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_8e892422), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dateText, onValueChange = { dateText = it; dateError = "" },
                        label = { Text(stringResource(R.string.txt_39464d70), color = theme.textDim, fontSize = 12.sp) },
                        isError = dateError.isNotEmpty(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, focusedBorderColor = Accent, unfocusedBorderColor = theme.border)
                    )
                    if (dateError.isNotEmpty()) Text(dateError, color = Red, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = startPageText, onValueChange = { startPageText = it.filter { c -> c.isDigit() }; pageError = "" },
                            label = { Text(stringResource(R.string.txt_739eacc1), color = theme.textDim, fontSize = 12.sp) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = pageError.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, focusedBorderColor = Accent, unfocusedBorderColor = theme.border)
                        )
                        OutlinedTextField(
                            value = endPageText, onValueChange = { endPageText = it.filter { c -> c.isDigit() }; pageError = "" },
                            label = { Text(stringResource(R.string.txt_28be9b81), color = theme.textDim, fontSize = 12.sp) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = pageError.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, focusedBorderColor = Accent, unfocusedBorderColor = theme.border)
                        )
                    }
                    if (pageError.isNotEmpty()) Text(pageError, color = Red, fontSize = 12.sp)
                    OutlinedTextField(
                        value = minsText, onValueChange = { minsText = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.txt_0a43aca4), color = theme.textDim, fontSize = 12.sp) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, focusedBorderColor = Accent, unfocusedBorderColor = theme.border)
                    )
                    OutlinedTextField(
                        value = noteText, onValueChange = { noteText = it },
                        label = { Text(stringResource(R.string.txt_7cd6ad03), color = theme.textDim, fontSize = 12.sp) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, focusedBorderColor = Accent, unfocusedBorderColor = theme.border)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val stored = parseFlexibleDate(dateText.trim())
                    if (stored == null) { dateError = context.getString(R.string.err_date_invalid_correct); return@TextButton }
                    // v20.2 (B4+B5): validar páginas
                    val newStart = startPageText.toIntOrNull()
                    val newEnd   = endPageText.toIntOrNull()
                    if (startPageText.isBlank() || endPageText.isBlank()) {
                        pageError = context.getString(R.string.err_pages_start_end_required); return@TextButton
                    }
                    if (newStart != null && newEnd != null) {
                        if (newStart >= newEnd) {
                            pageError = if (startPageText.toInt() >= endPageText.toInt())
                                context.getString(R.string.err_page_start_lt_end)
                            else
                                context.getString(R.string.err_page_end_gt_start)
                            return@TextButton
                        }
                    }
                    val pages = if (newStart != null && newEnd != null) newEnd - newStart + 1 else session.pages
                    val mins  = minsText.toIntOrNull()
                    onEdit(session.copy(pages = pages, minutes = mins, note = noteText.trim(), date = stored, startPage = newStart, endPage = newEnd))
                    showEditDialog = false
                }) { Text(stringResource(R.string.txt_d3270bdb), color = Accent, fontWeight = FontWeight.Bold) }
            },
            confirmButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) }
            }
        )
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, theme.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).clickable { expanded = !expanded }
                ) {
                    if (sessionNumber > 0) {
                        Text("#$sessionNumber", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(fmtDate(session.date), color = theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, null, tint = Accent.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
            }
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DataChip("📄 ${session.pages} págs", Accent.copy(alpha = 0.15f), Accent, Modifier.weight(1f))
                    if (session.minutes != null) {
                        DataChip("⏱ ${session.minutes} min", Sky.copy(alpha = 0.15f), Sky, Modifier.weight(1f))
                    }
                    if (pagesPerMin != null) {
                        DataChip("⚡ $pagesPerMin p/min", Green.copy(alpha = 0.15f), Green, Modifier.weight(1f))
                    }
                    if (session.startPage != null && session.endPage != null) {
                        val amber = Color(0xFFF59E0B)
                        DataChip("📖 ${session.startPage}-${session.endPage}", amber.copy(alpha = 0.15f), amber, Modifier.weight(1f))
                    }
                }
                if (session.note.isNotBlank()) {
                    var noteExpanded by remember { mutableStateOf(false) }
                    var noteOverflows by remember { mutableStateOf(false) }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "💬 ${session.note}",
                        color = theme.textDim,
                        fontSize = 11.sp,
                        maxLines = if (noteExpanded) Int.MAX_VALUE else 2,
                        overflow = if (noteExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                        onTextLayout = { result -> noteOverflows = result.hasVisualOverflow }
                    )
                    if (noteOverflows || noteExpanded) {
                        Icon(
                            if (noteExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(18.dp).align(Alignment.CenterHorizontally).clickable { noteExpanded = !noteExpanded }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxFontSize: TextUnit = 14.sp,
    minFontSize: TextUnit = 8.sp,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    var readyToDraw by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.drawWithContent { if (readyToDraw) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth) {
                val next = fontSize * 0.9f
                if (next >= minFontSize) fontSize = next
                else { fontSize = minFontSize; readyToDraw = true }
            } else {
                readyToDraw = true
            }
        }
    )
}

@Composable
fun StatBox(value: String, label: String, modifier: Modifier, theme: Theme, highlight: Boolean = false, highlightColor: Color = Green) {
    val bgColor   = if (highlight) highlightColor.copy(alpha = 0.07f) else theme.surface
    val brdColor  = if (highlight) highlightColor.copy(alpha = 0.3f)  else theme.border
    val txtColor  = if (highlight) highlightColor else theme.textMain
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = bgColor, border = BorderStroke(1.dp, brdColor)) {
        Column(
            Modifier.height(66.dp).padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AutoSizeText(value, color = txtColor, maxFontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(2.dp))
            Text(label, color = theme.textDim, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2, softWrap = true, lineHeight = 11.sp, overflow = TextOverflow.Visible, modifier = Modifier.fillMaxWidth())
        }
    }
}


// === BOOKQUEST INTEGRATION PATCH ===
// Easter egg should now open:
// com.lecturameter.bookquest.BookQuestScreen()
//
// BookQuest primary flow enabled.
// ===================================


// ── BulkReloadScreen ──────────────────────────────────────────────────────────

@Composable
fun BulkReloadScreen(
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    theme: Theme,
    type: String,     // "genres" | "covers"
    onBack: () -> Unit
) {
    val screenTitle = if (type == "genres") stringResource(R.string.bulk_refresh_title_genres)
        else stringResource(R.string.bulk_refresh_title_covers)
    val allBooks = vm.books

    // Búsqueda y filtros
    var searchQuery by remember { mutableStateOf("") }
    var filterAuthor by remember { mutableStateOf<String?>(null) }
    var filterGenre by remember { mutableStateOf<String?>(null) }
    var filterShelf by remember { mutableStateOf<BookStatus?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    // Selección — se mantiene con filtros activos
    val selectedIds = remember { mutableStateMapOf<Long, Boolean>() }

    // Inicializar todos seleccionados
    LaunchedEffect(Unit) { allBooks.forEach { selectedIds[it.id] = true } }

    // Lista filtrada
    val filtered = remember(searchQuery, filterAuthor, filterGenre, filterShelf, allBooks.size) {
        allBooks.filter { book ->
            val q = searchQuery.trim()
            val matchSearch = q.isEmpty() || fuzzyMatch(q, book.title) || fuzzyMatch(q, book.author)
            val matchAuthor = filterAuthor == null || book.author == filterAuthor
            val matchGenre = filterGenre == null || book.genres.contains(filterGenre)
            val matchShelf = filterShelf == null || book.status == filterShelf
            matchSearch && matchAuthor && matchGenre && matchShelf
        }
    }

    val selectedCount = selectedIds.count { it.value }

    // Estado de recarga
    var reloading by remember { mutableStateOf(false) }
    var reloadProgress by remember { mutableStateOf("") }
    var reloadDone by remember { mutableStateOf(false) }
    var processedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var updatedCount by remember { mutableStateOf(0) }
    var errorCount by remember { mutableStateOf(0) }
    var elapsedSecs by remember { mutableStateOf(0L) }
    var showFinalConfirm by remember { mutableStateOf(false) }

    // Diálogo de confirmación final
    if (showFinalConfirm) {
        val selIds = selectedIds.filter { it.value }.keys.toList()
        AlertDialog(
            onDismissRequest = { showFinalConfirm = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_4466d686), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (type == "genres") stringResource(R.string.bulk_confirm_text_genres)
                    else stringResource(R.string.bulk_confirm_text_covers),
                    color = theme.textMuted, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showFinalConfirm = false
                    reloading = true
                    val startMs = System.currentTimeMillis()
                    if (type == "genres") {
                        vm.bulkRefreshGenres(prefs, selIds,
                            onProgress = { done, total ->
                                reloadProgress = "$done / $total"
                                processedCount = done; totalCount = total
                                elapsedSecs = (System.currentTimeMillis() - startMs) / 1000
                            },
                            onDone = { ok, errors ->
                                reloading = false
                                reloadDone = true
                                updatedCount = ok; errorCount = errors
                                elapsedSecs = (System.currentTimeMillis() - startMs) / 1000
                            }
                        )
                    } else {
                        vm.bulkRefreshCovers(prefs, selIds,
                            onProgress = { done, total ->
                                reloadProgress = "$done / $total"
                                processedCount = done; totalCount = total
                                elapsedSecs = (System.currentTimeMillis() - startMs) / 1000
                            },
                            onDone = { ok, errors ->
                                reloading = false
                                reloadDone = true
                                updatedCount = ok; errorCount = errors
                                elapsedSecs = (System.currentTimeMillis() - startMs) / 1000
                            }
                        )
                    }
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showFinalConfirm = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, start = 4.dp, end = 16.dp, bottom = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
                Spacer(Modifier.width(4.dp))
                Text(screenTitle, color = theme.textMain, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            // Progreso / resultado
            if (reloading || reloadDone) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (reloadDone) Color(0x1A10B981) else Accent.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, if (reloadDone) Color(0x4D10B981) else Accent.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (reloading) {
                            val pct = if (totalCount > 0) processedCount * 100 / totalCount else 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = Accent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.reload_progress_label, reloadProgress, pct), color = Accent, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (totalCount > 0) processedCount.toFloat() / totalCount else 0f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = Accent,
                                trackColor = theme.border
                            )
                        } else {
                            Text(stringResource(R.string.reload_done_label, elapsedSecs), color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.reload_updated_label, updatedCount, errorCount), color = theme.textMuted, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Barra de búsqueda + filtros
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = theme.textDim, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = LocalTextStyle.current.copy(color = theme.textMain, fontSize = 13.sp),
                            cursorBrush = SolidColor(Accent),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) Text(stringResource(R.string.txt_38fe9f72), color = theme.textDim, fontSize = 13.sp)
                                inner()
                            }
                        )
                        if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, null, tint = theme.textDim, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Box {
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(Icons.Default.FilterList, null, tint = if (filterAuthor != null || filterGenre != null || filterShelf != null) Accent else theme.textDim)
                    }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                        Text(stringResource(R.string.txt_3397e69c), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        DropdownMenuItem(text = { Text(stringResource(R.string.txt_b73ecdab), color = if (filterShelf == null) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { filterShelf = null; showFilterMenu = false })
                        SHELF_ORDER.forEach { s ->
                            DropdownMenuItem(text = { Text(statusLabel(s), color = if (filterShelf == s) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { filterShelf = s; showFilterMenu = false })
                        }
                        HorizontalDivider(color = theme.border)
                        Text(stringResource(R.string.txt_c481b00a), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        DropdownMenuItem(text = { Text(stringResource(R.string.txt_426f7ea7), color = if (filterAuthor == null) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { filterAuthor = null; showFilterMenu = false })
                        allBooks.map { it.author }.filter { it.isNotBlank() }.distinct().sorted().forEach { a ->
                            DropdownMenuItem(text = { Text(a, color = if (filterAuthor == a) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { filterAuthor = a; showFilterMenu = false })
                        }
                        HorizontalDivider(color = theme.border)
                        Text(stringResource(R.string.txt_98f7ba16), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        DropdownMenuItem(text = { Text(stringResource(R.string.txt_8afc8680), color = if (filterGenre == null) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { filterGenre = null; showFilterMenu = false })
                        allBooks.flatMap { it.genres }.distinct().sorted().forEach { g ->
                            DropdownMenuItem(text = { Text(displayGenre(g), color = if (filterGenre == g) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { filterGenre = g; showFilterMenu = false })
                        }
                        HorizontalDivider(color = theme.border)
                        DropdownMenuItem(text = { Text(stringResource(R.string.txt_af85d57c), color = Red, fontSize = 13.sp) }, onClick = { filterAuthor = null; filterGenre = null; filterShelf = null; showFilterMenu = false })
                    }
                }
            }

            // Seleccionar todos / deseleccionar todos
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { allBooks.forEach { selectedIds[it.id] = true } }) {
                    Text(stringResource(R.string.txt_bb6b0333), color = Accent, fontSize = 12.sp)
                }
                TextButton(onClick = { allBooks.forEach { selectedIds[it.id] = false } }) {
                    Text(stringResource(R.string.txt_a60bc74b), color = theme.textMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("${filtered.size} libros", color = theme.textDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }

            // Lista de libros
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filtered, key = { it.id }) { book ->
                    val isSelected = selectedIds[book.id] == true
                    Surface(
                        onClick = { selectedIds[book.id] = !isSelected },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Accent.copy(alpha = 0.07f) else theme.surface,
                        border = BorderStroke(1.dp, if (isSelected) Accent.copy(alpha = 0.4f) else theme.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { selectedIds[book.id] = it },
                                colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = theme.border)
                            )
                            BookCover(book.coverUrl, book.title, size = 44, onBroken = {})
                            Column(Modifier.weight(1f)) {
                                Text(book.title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (book.author.isNotBlank()) Text(book.author, color = Accent, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (book.genres.isNotEmpty()) Text(book.genres.take(2).map { displayGenre(it) }.joinToString(" · "), color = theme.textDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = statusColor(book.status).copy(alpha = 0.15f)) {
                                Text(statusLabel(book.status), color = statusColor(book.status), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        // Sticky bottom bar con el botón de recarga
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = theme.bgMid,
            shadowElevation = 8.dp
        ) {
            Button(
                onClick = { if (selectedCount > 0) showFinalConfirm = true },
                enabled = selectedCount > 0 && !reloading,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, disabledContainerColor = theme.border)
            ) {
                if (reloading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.txt_8b2335bb), fontWeight = FontWeight.Bold)
                } else {
                    val countLabel = if (selectedCount == allBooks.size) stringResource(R.string.txt_32630ca9) else "$selectedCount"
                    Text(stringResource(R.string.bulk_refresh_button, countLabel), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ── SettingsScreen ─────────────────────────────────────────────────────────────

private fun scheduleBackup(context: android.content.Context, intervalHours: Int) {
    // Bug fix v21.15: ver comentario equivalente en onCreate — sin NetworkType.CONNECTED,
    // el backup local sigue funcionando offline; Drive reintenta solo cuando hay red.
    val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(intervalHours.toLong(), TimeUnit.HOURS)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "lecturameter_drive_backup",
        ExistingPeriodicWorkPolicy.REPLACE,
        request
    )
}

/** Convierte una URI de árbol SAF en un nombre de carpeta legible.
 *  Ej: ".../tree/primary%3ADownload%2FMisBackups" → "Download/MisBackups". */
private fun readableFolderName(treeUriStr: String): String = try {
    val uri = android.net.Uri.parse(treeUriStr)
    val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
    // docId típico: "primary:Download/MisBackups"
    val path = docId.substringAfter(":", docId)
    if (path.isBlank()) docId else path
} catch (_: Exception) {
    treeUriStr
}

private fun scheduleWidgetRefresh(context: android.content.Context, intervalMinutes: Int, replace: Boolean = true) {
    val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "lecturameter_widget_refresh",
        if (replace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

// ── FeedbackDialog ────────────────────────────────────────────────────────────

@Composable
fun FeedbackDialog(theme: Theme, onDismiss: () -> Unit, onSent: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feedbackType by remember { mutableStateOf("bug") }
    var description by remember { mutableStateOf("") }
    var includeLogs by remember { mutableStateOf(true) }
    var selectedImages by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var isSending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var sendSuccess by remember { mutableStateOf(false) }

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)
    ) { uris -> if (uris.isNotEmpty()) selectedImages = uris.take(3) }

    val deviceInfo = remember {
        buildString {
            append("App: Lecturameter ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            append("Marca: ${android.os.Build.BRAND}\n")
            append("Modelo: ${android.os.Build.MODEL}\n")
            append("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
            val skin = when {
                android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ->
                    if (android.os.Build.VERSION.SDK_INT >= 34) "HyperOS" else "MIUI"
                android.os.Build.MANUFACTURER.equals("Oppo", ignoreCase = true) ||
                android.os.Build.MANUFACTURER.equals("OnePlus", ignoreCase = true) -> "ColorOS"
                android.os.Build.MANUFACTURER.equals("Samsung", ignoreCase = true) -> "One UI"
                android.os.Build.MANUFACTURER.equals("Google", ignoreCase = true) -> "Stock Android"
                else -> android.os.Build.MANUFACTURER
            }
            append("Capa: $skin\n")
            append("Fecha: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        containerColor = theme.bgMid,
        title = { Text(stringResource(R.string.txt_76760a79), color = theme.textMain, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.txt_a5389e2a), color = theme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("bug" to "🐛 Bug", "suggestion" to "💡 Suggestion", "other" to "📝 Other")
                        .forEach { (type, label) ->
                            FilterChip(
                                selected = feedbackType == type,
                                onClick = { feedbackType = type },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.txt_d80248ef), color = theme.textMuted) },
                    placeholder = { Text(stringResource(R.string.txt_4204835c), color = theme.textDim, fontSize = 12.sp) },
                    minLines = 4, maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain,
                        focusedBorderColor = Accent, unfocusedBorderColor = theme.border
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
                    )
                )
                Spacer(Modifier.height(10.dp))
                // Checkbox: logs adjuntos
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeLogs, onCheckedChange = { includeLogs = it })
                    Column {
                        Text(stringResource(R.string.txt_e4270dac), color = theme.textMuted, fontSize = 13.sp)
                        Text(stringResource(R.string.txt_1f8cb5ce), color = theme.textDim, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Imágenes propias del usuario (máx 3)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_2d283ff6), color = theme.textMuted, fontSize = 13.sp)
                        Text(stringResource(R.string.txt_fcfc243e), color = theme.textDim, fontSize = 11.sp)
                    }
                    TextButton(
                        enabled = selectedImages.size < 3,
                        onClick = { imagePickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    ) { Text(stringResource(R.string.txt_227430b0), color = if (selectedImages.size < 3) Accent else theme.textDim, fontWeight = FontWeight.Bold) }
                }
                if (selectedImages.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedImages.forEach { uri ->
                            Box(Modifier.size(64.dp)) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(uri),
                                    contentDescription = stringResource(R.string.cd_attached_image),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                )
                                IconButton(
                                    onClick = { selectedImages = selectedImages.filter { it != uri } },
                                    modifier = Modifier.size(20.dp).align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                            }
                        }
                    }
                }
                // Info dispositivo (siempre visible)
                Spacer(Modifier.height(8.dp))
                Surface(color = theme.surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, theme.border)) {
                    Text(deviceInfo, modifier = Modifier.padding(10.dp), color = theme.textDim,
                        fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                if (sendError != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(color = Red.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Red.copy(alpha = 0.3f))) {
                        Text(stringResource(R.string.err_feedback_send_failed, sendError ?: ""), modifier = Modifier.padding(10.dp), color = Red, fontSize = 12.sp)
                    }
                }
                if (sendSuccess) {
                    Spacer(Modifier.height(8.dp))
                    Surface(color = Green.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Green.copy(alpha = 0.3f))) {
                        Text(stringResource(R.string.txt_0bf4acb4), modifier = Modifier.padding(10.dp), color = Green, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSending && !sendSuccess,
                onClick = {
                    if (description.isBlank()) {
                        android.widget.Toast.makeText(context, context.getString(R.string.msg_describe_feedback), android.widget.Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    isSending = true
                    sendError = null
                    scope.launch {
                        val logsText = if (includeLogs) com.lecturameter.utils.AppLogger.getLogs().takeLast(4000) else null
                        val imagesB64 = selectedImages.mapNotNull { uri ->
                            try {
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    val bytes = stream.readBytes()
                                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                }
                            } catch (_: Exception) { null }
                        }
                        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            FeedbackSender.send(feedbackType, description, deviceInfo, logsText, imagesB64)
                        }
                        isSending = false
                        if (result) {
                            sendSuccess = true
                            onSent()
                            onDismiss()
                        } else {
                            sendError = context.getString(R.string.err_feedback_send_retry)
                        }
                    }
                }
            ) {
                if (isSending) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                else Text(stringResource(R.string.txt_30cc00ae), color = Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSending, onClick = onDismiss) {
                Text(stringResource(R.string.txt_847607d7), color = Red)
            }
        }
    )
}

// ── Envío de feedback al backend (Cloud Function) ──────────────────────────
object FeedbackSender {
    // URL de la Cloud Function desplegada — ver Lecturameter_documentacion.md, sección "Backend de feedback"
    private const val ENDPOINT_URL = "https://lectuameter-feedback-1045574439348.europe-west1.run.app"

    fun send(type: String, description: String, deviceInfo: String, logs: String?, imagesB64: List<String>): Boolean {
        return try {
            val json = org.json.JSONObject().apply {
                put("type", type)
                put("description", description)
                put("deviceInfo", deviceInfo)
                if (logs != null) put("logs", logs)
                put("images", org.json.JSONArray(imagesB64))
            }
            val url = java.net.URL(ENDPOINT_URL)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) { false }
    }
}

@Composable
fun SettingsScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit, onBulkReload: (String) -> Unit = {}, onResetTutorial: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var backupIntervalHours by remember { mutableStateOf(prefs.getInt("backup_interval_hours", 2)) }
    var localBackupEnabled by remember { mutableStateOf(prefs.getBoolean("local_backup_enabled", true)) }
    var driveBackupEnabled by remember { mutableStateOf(prefs.getBoolean("drive_backup_enabled", true)) }
    var widgetRefreshMinutes by remember { mutableStateOf(prefs.getInt("widget_refresh_minutes", 30)) }

    var showActivateLocalDialog by remember { mutableStateOf(false) }
    var showActivateDriveDialog by remember { mutableStateOf(false) }
    var bulkGenresRunning by remember { mutableStateOf(false) }
    var bulkCoversRunning by remember { mutableStateOf(false) }
    var bulkProgress by remember { mutableStateOf("") }

    // Secciones colapsadas por defecto; estado persistente en prefs
    var sectBackup by remember { mutableStateOf(prefs.getBoolean("sect_backup_expanded", true)) }
    var sectWidget by remember { mutableStateOf(prefs.getBoolean("sect_widget_expanded", true)) }
    var sectTools  by remember { mutableStateOf(prefs.getBoolean("sect_tools_expanded",  true)) }
    var sectHelp   by remember { mutableStateOf(prefs.getBoolean("sect_help_expanded",   true)) }
    var sectTutorial by remember { mutableStateOf(prefs.getBoolean("sect_tutorial_expanded", true)) }
    var showFeedback by remember { mutableStateOf(false) }
    if (showFeedback) FeedbackDialog(
        theme = theme,
        onDismiss = { showFeedback = false },
        onSent = {
            android.widget.Toast.makeText(context, context.getString(R.string.msg_thanks_feedback), android.widget.Toast.LENGTH_LONG).show()
        }
    )

    // Carpeta local de backup (SAF). Estado reactivo para refrescar la UI al elegir.
    var localFolderUri by remember { mutableStateOf(prefs.getString("local_backup_folder_uri", null)) }

    // Picker de carpeta para backup local (SAF)
    val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            prefs.edit().putString("local_backup_folder_uri", uri.toString()).apply()
            localFolderUri = uri.toString()
        }
    }

    val driveSignInClient = remember {
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, DriveBackupManager.buildSignInOptions())
    }
    var driveAccount by remember { mutableStateOf(DriveBackupManager.getSignedInAccount(context)) }
    val driveSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (com.google.android.gms.auth.api.signin.GoogleSignIn.hasPermissions(account, DriveBackupManager.REQUIRED_SCOPE)) {
                driveAccount = account
            }
        } catch (_: Exception) {}
    }

    // Activar local dialog
    if (showActivateLocalDialog) {
        AlertDialog(
            onDismissRequest = { showActivateLocalDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_2d639552), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_632543a9), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    localBackupEnabled = true
                    prefs.edit().putBoolean("local_backup_enabled", true).apply()
                    showActivateLocalDialog = false
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showActivateLocalDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }
    if (showActivateDriveDialog) {
        AlertDialog(
            onDismissRequest = { showActivateDriveDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_dcd9cd29), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_eec2e836), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    driveBackupEnabled = true
                    prefs.edit().putBoolean("drive_backup_enabled", true).apply()
                    showActivateDriveDialog = false
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showActivateDriveDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }
    // Estado para los diálogos de recarga masiva — nueva versión con opciones
    var bulkReloadTargetType by remember { mutableStateOf("") }  // "genres" | "covers"
    var showBulkOptionDialog by remember { mutableStateOf(false) }
    var bulkOptionSelected by remember { mutableStateOf("all") }  // "all" | "select"
    var showBulkAllConfirmDialog by remember { mutableStateOf(false) }

    // Strings for bulk progress (needed outside Composable lambdas)
    val strBulkStarting         = stringResource(R.string.bulk_starting)
    val strBulkProgressGenres   = stringResource(R.string.bulk_progress_genres_partial)
    val strBulkProgressCovers   = stringResource(R.string.bulk_progress_covers_partial)
    val strBulkDoneGenres       = stringResource(R.string.bulk_done_genres)
    val strBulkDoneCovers       = stringResource(R.string.bulk_done_covers)
    val strBulkRefreshAll       = stringResource(R.string.bulk_refresh_all)
    val strBulkRefreshSelect    = stringResource(R.string.bulk_refresh_select)

    // Diálogo con opciones: Refresh all / Refresh selecting
    if (showBulkOptionDialog) {
        val dialogTitle = if (bulkReloadTargetType == "genres") stringResource(R.string.bulk_refresh_title_genres)
                          else stringResource(R.string.bulk_refresh_title_covers)
        AlertDialog(
            onDismissRequest = { showBulkOptionDialog = false },
            containerColor = theme.bgMid,
            title = { Text(dialogTitle, color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.txt_8e00796a), color = theme.textMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    listOf("all" to strBulkRefreshAll, "select" to strBulkRefreshSelect).forEach { (key, text) ->
                        Surface(
                            onClick = { bulkOptionSelected = key },
                            shape = RoundedCornerShape(10.dp),
                            color = if (bulkOptionSelected == key) Accent.copy(alpha = 0.12f) else theme.surface,
                            border = BorderStroke(1.dp, if (bulkOptionSelected == key) Accent else theme.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text, color = if (bulkOptionSelected == key) Accent else theme.textMain, fontSize = 13.sp, fontWeight = if (bulkOptionSelected == key) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showBulkOptionDialog = false
                    if (bulkOptionSelected == "all") showBulkAllConfirmDialog = true
                    else onBulkReload(bulkReloadTargetType)
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showBulkOptionDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }

    // Diálogo de confirmación para "Refresh all"
    if (showBulkAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBulkAllConfirmDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_4466d686), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (bulkReloadTargetType == "genres") stringResource(R.string.bulk_confirm_text_genres)
                    else stringResource(R.string.bulk_confirm_text_covers),
                    color = theme.textMuted, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBulkAllConfirmDialog = false
                    bulkProgress = strBulkStarting
                    if (bulkReloadTargetType == "genres") {
                        bulkGenresRunning = true
                        vm.bulkRefreshGenres(prefs, null,
                            onProgress = { done, total -> bulkProgress = String.format(strBulkProgressGenres, done, total) },
                            onDone = { ok, errors -> bulkGenresRunning = false; bulkProgress = String.format(strBulkDoneGenres, ok, errors) }
                        )
                    } else {
                        bulkCoversRunning = true
                        vm.bulkRefreshCovers(prefs, null,
                            onProgress = { done, total -> bulkProgress = String.format(strBulkProgressCovers, done, total) },
                            onDone = { ok, errors -> bulkCoversRunning = false; bulkProgress = String.format(strBulkDoneCovers, ok, errors) }
                        )
                    }
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showBulkAllConfirmDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, start = 4.dp, end = 16.dp, bottom = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.txt_f5d52eba), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        // ── TEMA ─────────────────────────────────────────────────────────────
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.txt_057acd78), color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    ThemeMode.LIGHT  to stringResource(R.string.theme_light),
                    ThemeMode.DARK   to stringResource(R.string.theme_dark),
                    ThemeMode.AURORA to stringResource(R.string.theme_aurora),
                    ThemeMode.AMOLED to stringResource(R.string.theme_oled)
                ).forEach { (mode, label) ->
                    val selected = vm.themeMode == mode
                    Surface(
                        onClick = { vm.setThemeMode(mode, prefs, context) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) Accent.copy(alpha = 0.15f) else theme.surface,
                        border = BorderStroke(1.dp, if (selected) Accent else theme.border),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            label, color = if (selected) Accent else theme.textMuted,
                            fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                        )
                    }
                }
            }
        }

        // ── IDIOMA ────────────────────────────────────────────────────────────
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.txt_36f1a4d2), color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("es" to stringResource(R.string.txt_95b01315), "en" to stringResource(R.string.txt_f759fe35)).forEach { (lang, label) ->
                    val selected = vm.currentLanguage == lang
                    Surface(
                        onClick = {
                            if (!selected) {
                                vm.setLanguage(lang, prefs)
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) Accent.copy(alpha = 0.15f) else theme.surface,
                        border = BorderStroke(1.dp, if (selected) Accent else theme.border),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            label, color = if (selected) Accent else theme.textMuted,
                            fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        // ── BACKUPS ───────────────────────────────────────────────────────────
        SettingsSection(
            title = "BACKUPS",
            subtitle = stringResource(R.string.settings_backup_subtitle),
            expanded = sectBackup,
            onToggle = { val newVal = !sectBackup; sectBackup = newVal; prefs.edit().putBoolean("sect_backup_expanded", newVal).apply() },
            theme = theme
        ) {
            // Frecuencia
            Column(Modifier.padding(bottom = 16.dp)) {
                Text(stringResource(R.string.txt_60520d3a), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.txt_ae333e6f), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 4, 6, 8, 10).forEach { h ->
                        val selected = backupIntervalHours == h
                        Surface(
                            onClick = {
                                backupIntervalHours = h
                                prefs.edit().putInt("backup_interval_hours", h).apply()
                                scheduleBackup(context, h)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) Accent else theme.surface,
                            border = BorderStroke(1.dp, if (selected) Accent else theme.border),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${h}h", color = if (selected) Color.White else theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
            HorizontalDivider(color = theme.border, modifier = Modifier.padding(bottom = 12.dp))
            // Backup Local
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1A6366F1)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PhoneAndroid, null, tint = Accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.txt_bfd55cba), color = if (localBackupEnabled) theme.textMain else theme.textDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.txt_f68be150), color = theme.textMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = localBackupEnabled,
                    onCheckedChange = { checked ->
                        localBackupEnabled = checked
                        prefs.edit().putBoolean("local_backup_enabled", checked).apply()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedTrackColor = theme.border)
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (localBackupEnabled) theme.surface else theme.bgMid,
                border = BorderStroke(1.dp, theme.border),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                onClick = {
                    if (localBackupEnabled) folderPickerLauncher.launch(null)
                    else showActivateLocalDialog = true
                }
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_db955caf), color = if (localBackupEnabled) theme.textMain else theme.textDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            localFolderUri?.let { readableFolderName(it) } ?: stringResource(R.string.label_default_backup_folder),
                            color = theme.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Si hay carpeta personalizada, botón para volver a la de por defecto
                    if (localFolderUri != null && localBackupEnabled) {
                        IconButton(onClick = {
                            prefs.edit().remove("local_backup_folder_uri").apply()
                            localFolderUri = null
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.cd_use_default_folder), tint = theme.textDim, modifier = Modifier.size(16.dp))
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = if (localBackupEnabled) theme.textMuted else theme.textDim, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = theme.border, modifier = Modifier.padding(bottom = 12.dp))
            // Backup Drive
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1A4285F4)), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.txt_cdd0bfa9), fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.txt_2ac042cb), color = if (driveBackupEnabled) theme.textMain else theme.textDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.txt_02d47d1a), color = theme.textMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = driveBackupEnabled,
                    onCheckedChange = { checked ->
                        driveBackupEnabled = checked
                        prefs.edit().putBoolean("drive_backup_enabled", checked).apply()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent, uncheckedTrackColor = theme.border)
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (driveBackupEnabled) theme.surface else theme.bgMid,
                border = BorderStroke(1.dp, theme.border),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                onClick = {
                    when {
                        !driveBackupEnabled -> showActivateDriveDialog = true
                        driveAccount != null -> {
                            // Cambiar de cuenta sin perder configuración
                            driveSignInClient.signOut().addOnCompleteListener {
                                driveSignInLauncher.launch(driveSignInClient.signInIntent)
                            }
                        }
                        else -> driveSignInLauncher.launch(driveSignInClient.signInIntent)
                    }
                }
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_c37472ea), color = if (driveBackupEnabled) theme.textMain else theme.textDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            driveAccount?.email?.let { stringResource(R.string.drive_account_connected, it) } ?: stringResource(R.string.drive_account_disconnected),
                            color = theme.textMuted, fontSize = 11.sp
                        )
                    }
                }
            }
            // Info note
            Surface(shape = RoundedCornerShape(10.dp), color = Accent.copy(alpha = 0.06f), border = BorderStroke(1.dp, Accent.copy(alpha = 0.25f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = Accent, modifier = Modifier.size(15.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.txt_2509a87a), color = Accent.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── WIDGET ────────────────────────────────────────────────────────────
        SettingsSection(
            title = "WIDGET",
            subtitle = stringResource(R.string.settings_widget_subtitle),
            expanded = sectWidget,
            onToggle = { val newVal = !sectWidget; sectWidget = newVal; prefs.edit().putBoolean("sect_widget_expanded", newVal).apply() },
            theme = theme
        ) {
            Text(stringResource(R.string.txt_5ccb97b8), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.txt_bd3d357e), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30 to "30 mins", 60 to "1h", 90 to "1h 30min", 120 to "2h").forEach { (mins, label) ->
                    val selected = widgetRefreshMinutes == mins
                    Surface(
                        onClick = {
                            widgetRefreshMinutes = mins
                            prefs.edit().putInt("widget_refresh_minutes", mins).apply()
                            scheduleWidgetRefresh(context, mins)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) Accent else theme.surface,
                        border = BorderStroke(1.dp, if (selected) Accent else theme.border),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, color = if (selected) Color.White else theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── HERRAMIENTAS ──────────────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_tools_title),
            subtitle = stringResource(R.string.settings_tools_subtitle),
            expanded = sectTools,
            onToggle = { val newVal = !sectTools; sectTools = newVal; prefs.edit().putBoolean("sect_tools_expanded", newVal).apply() },
            theme = theme
        ) {
            if (bulkProgress.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(10.dp), color = if (bulkProgress.startsWith("✅")) Color(0x1A10B981) else Accent.copy(alpha = 0.06f), border = BorderStroke(1.dp, if (bulkProgress.startsWith("✅")) Color(0x4D10B981) else Accent.copy(alpha = 0.25f)), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(bulkProgress, color = if (bulkProgress.startsWith("✅")) Green else Accent, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                }
            }
            SettingsToolRow(
                icon = "🔄",
                title = stringResource(R.string.bulk_refresh_title_genres),
                subtitle = stringResource(R.string.bulk_refresh_subtitle_genres),
                running = bulkGenresRunning,
                theme = theme,
                onClick = { bulkReloadTargetType = "genres"; bulkOptionSelected = "all"; showBulkOptionDialog = true }
            )
            Spacer(Modifier.height(8.dp))
            SettingsToolRow(
                icon = "🖼️",
                title = stringResource(R.string.bulk_refresh_title_covers),
                subtitle = stringResource(R.string.bulk_refresh_subtitle_covers),
                running = bulkCoversRunning,
                theme = theme,
                onClick = { bulkReloadTargetType = "covers"; bulkOptionSelected = "all"; showBulkOptionDialog = true }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── TUTORIAL ─────────────────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_tutorial_title),
            subtitle = stringResource(R.string.settings_tutorial_subtitle),
            expanded = sectTutorial,
            onToggle = { val newVal = !sectTutorial; sectTutorial = newVal; prefs.edit().putBoolean("sect_tutorial_expanded", newVal).apply() },
            theme = theme
        ) {
            SettingsToolRow(
                icon = "📖",
                title = stringResource(R.string.settings_tutorial_row_title),
                subtitle = stringResource(R.string.settings_tutorial_row_subtitle),
                running = false,
                theme = theme,
                onClick = { vm.resetTutorial(prefs); onResetTutorial() }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── AYUDA Y FEEDBACK ─────────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_help_title),
            subtitle = stringResource(R.string.settings_help_subtitle),
            expanded = sectHelp,
            onToggle = { val newVal = !sectHelp; sectHelp = newVal; prefs.edit().putBoolean("sect_help_expanded", newVal).apply() },
            theme = theme
        ) {
            SettingsToolRow(
                icon = "📨",
                title = stringResource(R.string.settings_feedback_title),
                subtitle = stringResource(R.string.settings_feedback_subtitle),
                running = false,
                theme = theme,
                onClick = { showFeedback = true }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── APOYA EL PROYECTO ─────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.border),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.txt_76306812), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text(stringResource(R.string.txt_e30acdbb), color = theme.textMuted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                // Ko-fi
                Surface(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://ko-fi.com/lecturameter"))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF29ABE0).copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color(0xFF29ABE0).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("☕", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.txt_eedfdf2b), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.txt_bb61c189), color = theme.textMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = theme.textDim, modifier = Modifier.size(16.dp))
                    }
                }
                // PayPal
                Surface(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://paypal.me/Lecturameter"))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0070BA).copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color(0xFF0070BA).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("💙", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.txt_ad69e733), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.txt_f72dc94e), color = theme.textMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = theme.textDim, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // v21.35: separación igual al resto de cards entre Support y About
        Spacer(Modifier.height(12.dp))

        // ── ACERCA DE ─────────────────────────────────────────────────────────
        // v21.35: eliminada card exterior (título + subtítulo). Solo el recuadro interior, centrado.
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Accent.copy(alpha = 0.06f),
            border = BorderStroke(1.5.dp, Accent.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Accent.copy(alpha = 0.15f)).border(2.dp, Accent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📚", fontSize = 28.sp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.txt_4d8b0a6f), color = theme.textMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.app_version_label, com.lecturameter.BuildConfig.VERSION_NAME), color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.txt_572a06a1), color = theme.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun SettingsSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    theme: Theme,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, theme.border),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle
                )
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(subtitle, color = theme.textMuted, fontSize = 11.sp)
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    null,
                    tint = Accent,
                    modifier = Modifier.size(20.dp).rotate(if (expanded) 0f else -90f)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
fun SettingsToolRow(icon: String, title: String, subtitle: String, running: Boolean, theme: Theme, onClick: () -> Unit) {
    Surface(
        onClick = { if (!running) onClick() },
        shape = RoundedCornerShape(12.dp),
        color = theme.bgMid,
        border = BorderStroke(1.dp, theme.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                if (running) CircularProgressIndicator(color = Accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(icon, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = theme.textMuted, fontSize = 11.sp, softWrap = true, overflow = TextOverflow.Visible)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}

// ── DailySessionsScreen ───────────────────────────────────────────────────────

@Composable
fun DailySessionsScreen(
    date: String,
    sessions: List<ReadingSession>,
    books: List<Book>,
    theme: Theme,
    onNavigateToDetail: (Long, String) -> Unit,
    onBack: () -> Unit
) {
    // Agrupar sesiones por libro, descartar libros eliminados
    val bookEntries = sessions
        .groupBy { it.bookId }
        .mapNotNull { (bookId, bookSessions) ->
            val book = books.find { it.id == bookId } ?: return@mapNotNull null
            val lastDate = bookSessions.maxByOrNull { it.date }?.date ?: ""
            Triple(book, bookSessions, lastDate)
        }
        .sortedByDescending { it.third } // más reciente primero

    val formattedDate = remember(date) {
        try {
            val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val parsed = inFmt.parse(date) ?: return@remember date
            // Usar Locale del sistema (respeta idioma de la app configurado via AppCompatDelegate)
            val outFmt = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
            outFmt.format(parsed)
        } catch (_: Exception) { date }
    }

    fun formatMinutes(min: Int): String = when {
        min >= 60 -> { val h = min / 60; val m = min % 60; if (m > 0) "${h}h ${m}m" else "${h}h" }
        else -> "${min}m"
    }

    Column(Modifier.fillMaxSize().background(theme.bgDark)) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(theme.bgMid)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Text("←", color = theme.textMain, fontSize = 20.sp)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(stringResource(R.string.txt_0c2c2f10), color = theme.textMuted, fontSize = 11.sp)
                Text(formattedDate, color = theme.textMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (bookEntries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.txt_8d5e2184), color = theme.textMuted, fontSize = 14.sp)
            }
            return@Column
        }

        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            items(bookEntries, key = { it.first.id }) { (book, bookSessions, _) ->
                val totalPages = bookSessions.sumOf { it.pages }
                val totalMinutes = bookSessions.sumOf { it.minutes ?: 0 }
                val sessionCount = bookSessions.size

                Surface(
                    onClick = { onNavigateToDetail(book.id, date) },
                    shape = RoundedCornerShape(14.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Portada
                        BookCover(
                            url = book.coverUrl,
                            _title = book.title,
                            size = 64,
                            isbnFallback = book.isbn
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(book.title, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(book.author, color = theme.textMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("📄 $totalPages págs", color = theme.textDim, fontSize = 11.sp)
                                if (totalMinutes > 0) Text("⏱ ${formatMinutes(totalMinutes)}", color = theme.textDim, fontSize = 11.sp)
                                Text(if (sessionCount == 1) "1 sesión" else "$sessionCount sesiones", color = theme.textDim, fontSize = 11.sp)
                            }
                        }
                        Icon(Icons.Default.KeyboardArrowRight, null, tint = theme.textDim, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ── HeatmapView ─────────────────────────────────────────────────────────────
@Composable
fun HeatmapView(vm: BooksViewModel, theme: Theme, onNavigateToSession: (Long, String) -> Unit, onNavigateToDailySessions: (String) -> Unit = {}) {
    // v21.41: los meses siguen el idioma de la app (string-array), no el locale del sistema
    val ctx = LocalContext.current
    val monthNames = ctx.resources.getStringArray(R.array.month_names_short).toList()
    val monthNamesFull = ctx.resources.getStringArray(R.array.month_names_full).toList()
    val dayNames = run {
        // Lunes a domingo — DateFormatSymbols devuelve D,L,M,X,J,V,S (índice 0=Domingo)
        // Reordenamos: lunes=índice 2 ... domingo=índice 1
        val raw = java.text.DateFormatSymbols(java.util.Locale.getDefault()).shortWeekdays
        listOf(raw[2], raw[3], raw[4], raw[5], raw[6], raw[7], raw[1])
            .map { it.take(1).uppercase() }
    }

    val availableYears = vm.sessions
        .mapNotNull { it.date.takeIf { d -> d.length >= 4 }?.take(4) }
        .filter { it.matches(Regex("\\d{4}")) }
        .toSortedSet().toList().reversed()
        .ifEmpty { listOf(java.time.LocalDate.now().year.toString()) }

    var selYear by remember { mutableStateOf(availableYears.first()) }
    var selMonth by remember { mutableStateOf<Int?>(null) }
    var showYearMenu by remember { mutableStateOf(false) }
    var showMonthMenu by remember { mutableStateOf(false) }

    val pagesByDate: Map<String, Int> = remember(vm.sessions, selYear) {
        vm.sessions
            .filter { it.date.startsWith(selYear) }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.sumOf { it.pages } }
    }

    // Pre-calcular libros por fecha — evita O(n*31) en la composición del heatmap
    val booksByDate: Map<String, List<Long>> = remember(vm.sessions, selYear) {
        vm.sessions
            .filter { it.date.startsWith(selYear) }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.map { it.bookId }.distinct() }
    }

    // Colores cálidos: vacío → amarillo suave → naranja → rojo intenso
    fun heatColor(value: Int, max: Int): Color {
        if (max == 0 || value == 0) return Color(0xFF1E293B)
        val ratio = (value.toFloat() / max).coerceIn(0f, 1f)
        return when {
            ratio < 0.20f -> Color(0xFF78350F) // marrón oscuro
            ratio < 0.40f -> Color(0xFFB45309) // ámbar oscuro
            ratio < 0.60f -> Color(0xFFF59E0B) // ámbar
            ratio < 0.80f -> Color(0xFFEA580C) // naranja
            else           -> Color(0xFFDC2626) // rojo
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Controles: año + mes dropdown ─────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selector año
            Box {
                Surface(
                    onClick = { showYearMenu = true },
                    shape = RoundedCornerShape(8.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.border)
                ) {
                    Text("$selYear \u25be", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false },
                    modifier = Modifier.background(Color(0xFF1E293B))) {
                    availableYears.forEach { y ->
                        DropdownMenuItem(
                            text = { Text(y, color = if (y == selYear) Accent else Color(0xFFF1F5F9), fontSize = 13.sp) },
                            onClick = { selYear = y; showYearMenu = false }
                        )
                    }
                }
            }

            // Selector mes (dropdown)
            Box {
                Surface(
                    onClick = { showMonthMenu = true },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selMonth != null) Accent else theme.surface,
                    border = BorderStroke(1.dp, if (selMonth != null) Accent else theme.border)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            selMonth?.let { monthNames[it - 1] } ?: stringResource(R.string.filter_month_placeholder),
                            color = if (selMonth != null) Color.White else theme.textMuted,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.txt_f3baf2ca), color = if (selMonth != null) Color.White else theme.textMuted, fontSize = 10.sp)
                    }
                }
                DropdownMenu(expanded = showMonthMenu, onDismissRequest = { showMonthMenu = false },
                    modifier = Modifier.background(Color(0xFF1E293B))) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.txt_e8b7bada), color = if (selMonth == null) Accent else Color(0xFFF1F5F9), fontSize = 13.sp) },
                        onClick = { selMonth = null; showMonthMenu = false }
                    )
                    (1..12).forEach { m ->
                        val mm = m.toString().padStart(2, '0')
                        val hasDat = pagesByDate.keys.any { it.length >= 7 && it.substring(5, 7) == mm }
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(monthNamesFull[m - 1],
                                        color = if (selMonth == m) Accent else if (hasDat) Color(0xFFF1F5F9) else Color(0xFF64748B),
                                        fontSize = 13.sp)
                                    if (hasDat) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(Modifier.size(6.dp).background(Color(0xFFF59E0B), androidx.compose.foundation.shape.CircleShape))
                                    }
                                }
                            },
                            onClick = { selMonth = m; showMonthMenu = false }
                        )
                    }
                }
            }

            // Leyenda compacta
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.txt_ae0dca8d), color = theme.textDim, fontSize = 9.sp)
                listOf(Color(0xFF78350F), Color(0xFFB45309), Color(0xFFF59E0B), Color(0xFFEA580C), Color(0xFFDC2626)).forEach { c ->
                    Box(Modifier.size(10.dp).background(c, RoundedCornerShape(2.dp)))
                }
                Text(stringResource(R.string.txt_f1caef3b), color = theme.textDim, fontSize = 9.sp)
            }
        }

        val currentMonth = selMonth
        if (currentMonth == null) {
            // ── Vista anual: 12 celdas ──────────────────────────────────────
            val monthTotals = (1..12).map { m ->
                val mm = m.toString().padStart(2, '0')
                pagesByDate.entries.filter { it.key.length >= 7 && it.key.substring(5, 7) == mm }.sumOf { it.value }
            }
            val maxMonthly = monthTotals.maxOrNull()?.coerceAtLeast(1) ?: 1

            Text("$selYear · Tap a month to see it in detail", color = theme.textDim, fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(12) { idx ->
                    val pages = monthTotals[idx]
                    Surface(
                        onClick = { selMonth = idx + 1 },
                        shape = RoundedCornerShape(12.dp),
                        color = heatColor(pages, maxMonthly),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF))
                    ) {
                        Column(
                            Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(monthNames[idx], color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(if (pages > 0) "$pages" else "\u2014",
                                color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (pages > 0) Text(stringResource(R.string.txt_df94c6d3), color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
                        }
                    }
                }
            }
        } else {
            // ── Vista mensual: grid 7 columnas ──────────────────────────────
            val mm = currentMonth.toString().padStart(2, '0')
            val year = selYear.toIntOrNull() ?: java.time.LocalDate.now().year
            val daysInMonth = java.time.YearMonth.of(year, currentMonth).lengthOfMonth()
            val firstDow = java.time.LocalDate.of(year, currentMonth, 1).dayOfWeek.value // 1=Mon..7=Sun

            val dayTotals = (1..daysInMonth).map { d ->
                val key = "$selYear-$mm-${d.toString().padStart(2, '0')}"
                pagesByDate[key] ?: 0
            }
            val maxDaily = dayTotals.maxOrNull()?.coerceAtLeast(1) ?: 1

            Text(stringResource(R.string.heatmap_month_label, "${monthNamesFull[currentMonth - 1]} $selYear"),
                color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))

            // Cabecera días semana
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                dayNames.forEach { d ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(d, color = theme.textDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(firstDow - 1) {
                    Box(Modifier.aspectRatio(1f))
                }
                items(daysInMonth) { idx ->
                    val day = idx + 1
                    val pages = dayTotals[idx]
                    val dateKey = "$selYear-$mm-${day.toString().padStart(2, '0')}"
                    val booksForDay = booksByDate[dateKey] ?: emptyList()
                    Surface(
                        onClick = {
                            if (pages > 0 && booksForDay.isNotEmpty()) {
                                if (booksForDay.size == 1) {
                                    onNavigateToSession(booksForDay.first(), dateKey)
                                } else {
                                    onNavigateToDailySessions(dateKey)
                                }
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = heatColor(pages, maxDaily),
                        border = BorderStroke(0.5.dp, Color(0x22FFFFFF)),
                        modifier = Modifier.aspectRatio(1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$day", color = Color.White.copy(alpha = if (pages > 0) 0.95f else 0.4f),
                                    fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                if (pages > 0) Text("$pages", color = Color.White.copy(alpha = 0.75f), fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}


// Gráficas adicionales dentro de StatsScreen
// Se accede mediante toggle "Vista" en la barra de filtros.

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsChartsView(
    vm: BooksViewModel,
    theme: Theme,
    filterGenre: String?,
    onGenreChange: (String?) -> Unit,
    allGenres: List<String>,
    filterAuthor: String?,
    onAuthorChange: (String?) -> Unit,
    allAuthors: List<String>
) {
    val context = LocalContext.current
    // Base: TODOS los libros aplanados por edición (v18.3) — cada edición cuenta como una entrada.
    // Para libros con varias ediciones, cada una aparece con su título, páginas, idioma y portada
    // propios. Libros sin ediciones múltiples se devuelven como antes.
    val filtered = vm.booksByEdition()
        .let { list -> if (filterGenre != null) list.filter { it.genres.contains(filterGenre) } else list }
        .let { list -> if (filterAuthor != null) list.filter { it.author == filterAuthor } else list }

    // Filtros locales
    var ascending by remember { mutableStateOf(false) }
    var selYear by remember { mutableStateOf<String?>(null) }   // "2026" o null = todos los años
    var selMonth by remember { mutableStateOf<Int?>(null) }     // 1..12 o null = todos los meses
    val statusFilters = remember { mutableStateListOf<BookStatus>() }  // multiselección, máx 2
    var hideFunctionalless by remember { mutableStateOf(false) }

    val booksForCharts = filtered
        .let { list -> if (statusFilters.isNotEmpty()) list.filter { it.status in statusFilters } else list }
        .let { list -> if (hideFunctionalless) list.filter { it.firstFunctionalPage != null || it.lastFunctionalPage != null } else list }

    val sessionsByBook = vm.sessions.groupBy { it.bookId }

    // ¿Una fecha "yyyy-MM-dd" cae en el período seleccionado (año y/o mes)?
    fun dateInPeriod(d: String?): Boolean {
        if (d == null || d.length < 4) return false
        selYear?.let { if (!d.startsWith(it)) return false }
        selMonth?.let { m -> val mm = m.toString().padStart(2, '0'); if (d.length < 7 || d.substring(5, 7) != mm) return false }
        return true
    }
    // Libros que pertenecen al período: por fecha de inicio/fin o por tener alguna sesión en él.
    // Los donuts (estado, género, idioma, autor) usan esto para reaccionar a año/mes.
    val booksInPeriod = if (selYear == null && selMonth == null) booksForCharts
        else booksForCharts.filter { b ->
            dateInPeriod(b.endDate) || dateInPeriod(b.startDate) ||
            (sessionsByBook[b.id]?.any { dateInPeriod(it.date) } == true)
        }

    // Sesiones de los libros filtrados + filtro de período (año y mes combinables)
    val filteredBookIds = booksForCharts.map { it.id }.toSet()
    val filteredSessions = vm.sessions
        .filter { it.bookId in filteredBookIds }
        .let { list ->
            var l = list
            selYear?.let { y -> l = l.filter { it.date.startsWith(y) } }
            selMonth?.let { m ->
                val mm = m.toString().padStart(2, '0')
                l = l.filter { it.date.length >= 7 && it.date.substring(5, 7) == mm }
            }
            l
        }

    // Años disponibles: derivados de TODOS los libros (filtro de género/autor), nunca del
    // filtro de estado ni del período — así el dropdown no se queda solo con 2026.
    val availableYears = run {
        val ys = mutableSetOf<String>()
        filtered.forEach { b ->
            b.startDate?.takeIf { it.length >= 4 }?.let { ys.add(it.take(4)) }
            b.endDate?.takeIf { it.length >= 4 }?.let { ys.add(it.take(4)) }
            sessionsByBook[b.id]?.forEach { if (it.date.length >= 4) ys.add(it.date.take(4)) }
        }
        ys.filter { it.matches(Regex("\\d{4}")) }.toSortedSet().toList().reversed()
    }
    // v21.41: meses según idioma de la app
    val statsCtx = LocalContext.current
    val monthNames = statsCtx.resources.getStringArray(R.array.month_names_short).toList()


    val streakDays = run {
        val sessionDates = filteredSessions.map { it.date }.toSortedSet().toList()
        var maxStreak = 0; var curStreak = 0; var prevDate: java.util.Calendar? = null
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        for (dateStr in sessionDates) {
            val cal = java.util.Calendar.getInstance().apply { time = sdf.parse(dateStr) ?: return@apply }
            if (prevDate == null) { curStreak = 1 } else {
                val prev = prevDate!!
                prev.add(java.util.Calendar.DAY_OF_YEAR, 1)
                curStreak = if (cal.get(java.util.Calendar.DAY_OF_YEAR) == prev.get(java.util.Calendar.DAY_OF_YEAR) && cal.get(java.util.Calendar.YEAR) == prev.get(java.util.Calendar.YEAR)) curStreak + 1 else 1
            }
            if (curStreak > maxStreak) maxStreak = curStreak
            prevDate = java.util.Calendar.getInstance().apply { time = sdf.parse(dateStr) ?: return@apply }
        }
        maxStreak
    }

    // Ratio de terminados — usa booksInPeriod para respetar filtros año/mes
    val showRatioFinished = statusFilters.isEmpty() || BookStatus.FINISHED in statusFilters
    val ratioFinished = run {
        val base = booksInPeriod
        val started = base.count { it.status != BookStatus.PENDING }
        val finished = base.count { it.status == BookStatus.FINISHED }
        if (started > 0) finished.toFloat() / started else 0f
    }

    val avgSessionPages = if (filteredSessions.isNotEmpty()) filteredSessions.map { it.pages }.average() else 0.0
    val avgSessionMins = filteredSessions.mapNotNull { it.minutes }.let { if (it.isNotEmpty()) it.average() else 0.0 }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ── Filtros ────────────────────────────────────────────────────────────
        Text(stringResource(R.string.txt_9042f197), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.txt_26afc116), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                    listOf(false to "↓ Mayor", true to "↑ Menor").forEach { (asc, lbl) ->
                        val sel = ascending == asc
                        Surface(onClick = { ascending = asc }, shape = RoundedCornerShape(8.dp), color = if (sel) Accent else theme.bgMid, border = BorderStroke(1.dp, if (sel) Accent else theme.border)) {
                            Text(lbl, color = if (sel) Color.White else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
                // Período: selector de Año y de Mes, combinables
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.txt_7d6c2883), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                    // Dropdown de Año
                    var showYearMenu by remember { mutableStateOf(false) }
                    Box {
                        Surface(onClick = { showYearMenu = true }, shape = RoundedCornerShape(8.dp), color = if (selYear != null) Accent.copy(alpha = 0.15f) else theme.bgMid, border = BorderStroke(1.dp, if (selYear != null) Accent else theme.border)) {
                            Text("${selYear ?: stringResource(R.string.filter_year_placeholder)} ▾", color = if (selYear != null) Accent else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.txt_32630ca9), color = if (selYear == null) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { selYear = null; showYearMenu = false })
                            availableYears.forEach { y ->
                                DropdownMenuItem(text = { Text(y, color = if (selYear == y) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { selYear = y; showYearMenu = false })
                            }
                        }
                    }
                    // Dropdown de Mes
                    var showMonthMenu by remember { mutableStateOf(false) }
                    Box {
                        Surface(onClick = { showMonthMenu = true }, shape = RoundedCornerShape(8.dp), color = if (selMonth != null) Accent.copy(alpha = 0.15f) else theme.bgMid, border = BorderStroke(1.dp, if (selMonth != null) Accent else theme.border)) {
                            Text("${selMonth?.let { monthNames[it - 1] } ?: stringResource(R.string.filter_month_placeholder)} ▾", color = if (selMonth != null) Accent else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        DropdownMenu(expanded = showMonthMenu, onDismissRequest = { showMonthMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.txt_32630ca9), color = if (selMonth == null) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { selMonth = null; showMonthMenu = false })
                            (1..12).forEach { m ->
                                DropdownMenuItem(text = { Text(monthNames[m - 1], color = if (selMonth == m) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { selMonth = m; showMonthMenu = false })
                            }
                        }
                    }
                }
                // Estados — multiselección (máx 2), en varias líneas con FlowRow
                Row(verticalAlignment = Alignment.Top) {
                    Text(stringResource(R.string.txt_e7396239), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.width(60.dp).padding(top = 5.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(onClick = { statusFilters.clear() }, shape = RoundedCornerShape(8.dp), color = if (statusFilters.isEmpty()) Accent else theme.bgMid, border = BorderStroke(1.dp, if (statusFilters.isEmpty()) Accent else theme.border)) {
                            Text(stringResource(R.string.txt_32630ca9), color = if (statusFilters.isEmpty()) Color.White else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        SHELF_ORDER.forEach { s ->
                            val sel = s in statusFilters
                            Surface(
                                onClick = {
                                    if (sel) statusFilters.remove(s)
                                    else {
                                        if (statusFilters.size >= 2) statusFilters.removeAt(0)
                                        statusFilters.add(s)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) statusColor(s).copy(alpha = 0.9f) else theme.bgMid,
                                border = BorderStroke(1.dp, if (sel) statusColor(s) else theme.border)
                            ) {
                                Text(statusLabel(s), color = if (sel) Color.White else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hideFunctionalless, onCheckedChange = { hideFunctionalless = it }, colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = theme.border), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.txt_73fa4a4d), color = theme.textMuted, fontSize = 12.sp)
                }
                // Género y Autor (mismo bloque de filtros, dropdowns como en la lista)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.txt_d086767f), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                    var showGenreMenu by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        Surface(onClick = { showGenreMenu = true }, shape = RoundedCornerShape(8.dp), color = if (filterGenre != null) Accent.copy(alpha = 0.15f) else theme.bgMid, border = BorderStroke(1.dp, if (filterGenre != null) Accent else theme.border), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Category, null, modifier = Modifier.size(13.dp), tint = if (filterGenre != null) Accent else theme.textMuted)
                                Spacer(Modifier.width(4.dp))
                                Text("${filterGenre?.let { displayGenre(it) } ?: stringResource(R.string.txt_98f7ba16)} ▾", color = if (filterGenre != null) Accent else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        DropdownMenu(expanded = showGenreMenu, onDismissRequest = { showGenreMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.txt_8afc8680), color = if (filterGenre == null) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { onGenreChange(null); showGenreMenu = false })
                            allGenres.forEach { g ->
                                DropdownMenuItem(text = { Text(displayGenre(g), color = if (filterGenre == g) Accent else theme.textMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onGenreChange(g); showGenreMenu = false })
                            }
                        }
                    }
                    var showAuthorMenu by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        Surface(onClick = { showAuthorMenu = true }, shape = RoundedCornerShape(8.dp), color = if (filterAuthor != null) Accent2.copy(alpha = 0.15f) else theme.bgMid, border = BorderStroke(1.dp, if (filterAuthor != null) Accent2 else theme.border), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(13.dp), tint = if (filterAuthor != null) Accent2 else theme.textMuted)
                                Spacer(Modifier.width(4.dp))
                                Text("${filterAuthor ?: stringResource(R.string.txt_c481b00a)} ▾", color = if (filterAuthor != null) Accent2 else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        DropdownMenu(expanded = showAuthorMenu, onDismissRequest = { showAuthorMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.txt_426f7ea7), color = if (filterAuthor == null) Accent2 else theme.textMain, fontSize = 13.sp) }, onClick = { onAuthorChange(null); showAuthorMenu = false })
                            allAuthors.forEach { a ->
                                DropdownMenuItem(text = { Text(a, color = if (filterAuthor == a) Accent2 else theme.textMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onAuthorChange(a); showAuthorMenu = false })
                            }
                        }
                    }
                }
            }
        }

        // ── Empty state cuando hay filtro de período pero ningún libro ────────
        if ((selYear != null || selMonth != null) && booksInPeriod.isEmpty()) {
            val periodLbl = buildString {
                if (selMonth != null) { append(monthNames[selMonth!! - 1]); if (selYear != null) append(" ") }
                if (selYear != null) append(selYear)
            }
            Surface(shape = RoundedCornerShape(14.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📊", fontSize = 38.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.stats_no_data, periodLbl), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.txt_158a98d7), color = theme.textDim, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            return@Column
        }

        // ── Indicadores clave ──────────────────────────────────────────────────
        Text(stringResource(R.string.stats_key_indicators, booksForCharts.size), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("$streakDays ${stringResource(R.string.word_days)}", stringResource(R.string.pill_racha_maxima), Modifier.weight(1f), theme, highlight = true, highlightColor = Amber)
            if (showRatioFinished) StatBox("${String.format("%.0f%%", ratioFinished * 100)}", stringResource(R.string.pill_libros_terminados), Modifier.weight(1f), theme, highlight = true, highlightColor = Green)
            StatBox(if (filteredSessions.isNotEmpty()) String.format("%.1f p", avgSessionPages) else "—", stringResource(R.string.pill_media_pags_ses), Modifier.weight(1f), theme, highlight = true, highlightColor = Accent)
            StatBox(if (avgSessionMins > 0) String.format("%.0f min", avgSessionMins) else "—", stringResource(R.string.pill_media_mins_ses), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
        }

        // ── (El gráfico de barras "Páginas por período" se muestra al FINAL de la sección) ──

        // ── Géneros (donut + barras) — SIEMPRE primero. Cada libro cuenta UNA vez por su género principal ──
        val genreList = booksInPeriod
            .mapNotNull { b -> (b.genres.firstOrNull()?.ifBlank { null }) ?: b.genre.ifBlank { null } }
            .groupBy { it }.mapValues { it.value.size }
            .entries.let { if (ascending) it.sortedBy { e -> e.value } else it.sortedByDescending { e -> e.value } }.take(6)
        if (genreList.isNotEmpty()) {
            val gColors = listOf(Accent, Green, Sky, Amber, Red, Color(0xFF8B5CF6))
            val total = genreList.sumOf { it.value }
            ChartCard(title = stringResource(R.string.chart_genre_title, total), theme = theme) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(genreList.map { it.value }, gColors, Modifier.size(100.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        genreList.forEachIndexed { i, entry ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(gColors[i % gColors.size]))
                                Text("${displayGenre(entry.key)}: ${entry.value} (${if (total > 0) entry.value * 100 / total else 0}%)", color = theme.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (genreList.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalBarChart(genreList.map { Triple(displayGenre(it.key), it.value, Accent) }, theme, max = genreList.maxOf { it.value }.coerceAtLeast(1))
                }
            }
        }

        // ── Estado (donut) — refleja período; oculto en años anteriores a 2026 ──
        // (puntos 1 y 4: antes de 2026 solo hay libros "Leído", la distribución no aporta)
        val showStatusChart = (selYear == null || selYear!! >= "2026")
        if (showStatusChart && booksInPeriod.isNotEmpty()) {
            val counts = mapOf(
                stringResource(R.string.chart_status_leidos) to booksInPeriod.count { it.status == BookStatus.FINISHED },
                stringResource(R.string.chart_status_leyendo) to booksInPeriod.count { it.status == BookStatus.READING },
                stringResource(R.string.chart_status_releyendo) to booksInPeriod.count { it.status == BookStatus.REREADING },
                stringResource(R.string.chart_status_pendiente) to booksInPeriod.count { it.status == BookStatus.PENDING },
                stringResource(R.string.chart_status_abandonado) to booksInPeriod.count { it.status == BookStatus.DROPPED }
            ).filter { it.value > 0 }
            if (counts.isNotEmpty()) {
                val stateColors = listOf(Green, Sky, Color(0xFF06B6D4), Amber, Red)
                val total = counts.values.sum()
                ChartCard(title = stringResource(R.string.chart_distribution_title, total), theme = theme) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(counts.values.toList(), stateColors, Modifier.size(100.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            counts.entries.forEachIndexed { i, (lbl, value) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(stateColors[i % stateColors.size]))
                                    Text("$lbl: $value (${if (total > 0) value * 100 / total else 0}%)", color = theme.textMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Idioma (donut) — "Global"/"Edición principal" NO es idioma: se excluye ──
        val langList = booksInPeriod.mapNotNull { book ->
            val lbl = book.editions.firstOrNull { it.isActive }?.languageLabel
            when {
                lbl == null -> null
                lbl.equals("Edición principal", ignoreCase = true) -> null  // Global no es un idioma
                lbl.equals("Global", ignoreCase = true) -> null
                else -> lbl
            }
        }.groupBy { it }.mapValues { it.value.size }
            .entries.let { if (ascending) it.sortedBy { e -> e.value } else it.sortedByDescending { e -> e.value } }
        if (langList.isNotEmpty()) {
            val lColors = listOf(Color(0xFF3B82F6), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF8B5CF6), Color(0xFFEF4444))
            val total = langList.sumOf { it.value }
            ChartCard(title = stringResource(R.string.stats_by_language, total), theme = theme) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(langList.map { it.value }, lColors, Modifier.size(100.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        langList.forEachIndexed { i, entry ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(lColors[i % lColors.size]))
                                Text("${entry.key}: ${entry.value} (${if (total > 0) entry.value * 100 / total else 0}%)", color = theme.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // ── Autores (donut + barras) ────────────────────────────────────────────
        val authorList = booksInPeriod.filter { it.author.isNotBlank() }
            .groupBy { it.author }.mapValues { it.value.size }
            .entries.let { if (ascending) it.sortedBy { e -> e.value } else it.sortedByDescending { e -> e.value } }.take(6)
        if (authorList.isNotEmpty()) {
            val aColors = listOf(Color(0xFFEC4899), Accent, Sky, Green, Amber, Color(0xFF8B5CF6))
            val total = authorList.sumOf { it.value }
            ChartCard(title = stringResource(R.string.stats_top_authors, total), theme = theme) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(authorList.map { it.value }, aColors, Modifier.size(100.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        authorList.forEachIndexed { i, entry ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(aColors[i % aColors.size]))
                                Text("${entry.key.split(" ").last()}: ${entry.value} (${if (total > 0) entry.value * 100 / total else 0}%)", color = theme.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (authorList.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalBarChart(authorList.map { Triple(it.key.split(" ").last(), it.value, Color(0xFFEC4899)) }, theme)
                }
            }
        }

        // ── Páginas por período (gráfico de barras AL FINAL de la sección) ───────
        // Granularidad adaptativa: solo mes → por día; solo año → por mes; nada → últimos 12 meses.
        val groupKey: (String) -> String = { date ->
            when {
                selMonth != null -> date            // mes concreto → barras por día (fecha completa)
                selYear != null -> date.take(7)     // año concreto sin mes → barras por mes
                else -> date.take(7)                // sin filtro → por mes (últimos 12)
            }
        }
        val rawPeriodData = filteredSessions.groupBy { groupKey(it.date) }
            .mapValues { it.value.sumOf { s -> s.pages } }
            .entries.sortedBy { it.key }
        val periodData = if (ascending) rawPeriodData.sortedBy { it.value } else rawPeriodData.sortedByDescending { it.value }
        val displayPeriod = when {
            ascending -> periodData.take(12)
            selYear == null && selMonth == null -> rawPeriodData.takeLast(12)
            else -> rawPeriodData
        }
        if (displayPeriod.isNotEmpty()) {
            val chartTitleStr = if (selMonth != null) stringResource(R.string.chart_pages_per_day) else stringResource(R.string.chart_pages_per_month)
            // Período mostrado (mm-aa) a la derecha del título cuando hay año y/o mes seleccionados
            val periodLabel = when {
                selYear != null && selMonth != null -> "${selMonth!!.toString().padStart(2, '0')}-${selYear!!.takeLast(2)}"
                selYear != null -> selYear!!
                selMonth != null -> monthNames[selMonth!! - 1]
                else -> ""
            }
            val totalPagesCount = displayPeriod.sumOf { it.value }
            val totalPagesWord = stringResource(R.string.txt_47bcdf9a)
            val fullTitle = (if (periodLabel.isNotBlank()) "$chartTitleStr $periodLabel" else chartTitleStr) +
                " ($totalPagesCount $totalPagesWord)"
            ChartCard(title = fullTitle, theme = theme) {
                val maxP = displayPeriod.maxOf { it.value }.coerceAtLeast(1)
                val items = displayPeriod.map { entry ->
                    val key = entry.key
                    val label = when (key.length) {
                        10 -> key.takeLast(2)                                           // "yyyy-MM-dd" → "dd"
                        7  -> { val p = key.split("-"); "${p[1]}-${p[0].takeLast(2)}" }  // "yyyy-MM" → "MM-AA"
                        else -> key
                    }
                    label to entry.value
                }
                VerticalBarChartWithValues(items, maxP, theme)
            }
        }

        // ── Exportar estadísticas ─────────────────────────────────────────────
        val scope = rememberCoroutineScope()
        var exportError by remember { mutableStateOf<String?>(null) }
        fun exportStats(asCsv: Boolean) {
            scope.launch {
                try {
                    val (fileName, mime, content) = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        if (asCsv) Triple("estadisticas_lecturameter.csv", "text/csv", buildStatsCSV(vm))
                        else Triple("estadisticas_lecturameter.json", "application/json", buildStatsJSON(vm))
                    }
                    val file = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        java.io.File(context.cacheDir, fileName).apply { writeText(content) }
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    // startActivity siempre en el hilo principal — en IO crashea en MIUI
                    context.startActivity(android.content.Intent.createChooser(intent, "Exportar estadísticas"))
                    exportError = null
                } catch (e: Exception) {
                    exportError = "❌ Error al exportar: ${e.message}"
                }
            }
        }
        Surface(shape = RoundedCornerShape(14.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.txt_def36a08), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportStats(asCsv = true) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Green), colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)
                    ) { Text(stringResource(R.string.txt_cc8d68c5), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { exportStats(asCsv = false) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Amber), colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber)
                    ) { Text(stringResource(R.string.txt_0ecd11c1), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                exportError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Red, fontSize = 12.sp)
                }
            }
        }
    }
}


@Composable
fun ChartCard(title: String, theme: Theme, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
fun HorizontalBarChart(items: List<Triple<String, Int, Color>>, theme: Theme, max: Int = items.maxOfOrNull { it.second } ?: 1) {
    val maxVal = max.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, value, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(label, color = theme.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(90.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(theme.bgMid)) {
                    val fraction = value.toFloat() / maxVal
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.8f)))
                }
                Spacer(Modifier.width(8.dp))
                Text("$value", color = theme.textMuted, fontSize = 11.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
fun VerticalBarChart(items: List<Pair<String, Int>>, max: Int, theme: Theme) {
    val maxVal = max.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        items.forEach { (label, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val fraction = value.toFloat() / maxVal
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(Accent.copy(alpha = 0.75f)))
                }
                Spacer(Modifier.height(2.dp))
                Text(label, color = theme.textDim, fontSize = 7.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun VerticalBarChartWithValues(items: List<Pair<String, Int>>, max: Int, theme: Theme) {
    val maxVal = max.coerceAtLeast(1)
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        items.forEachIndexed { idx, (label, value) ->
            val isSelected = selectedIdx == idx
            Column(
                Modifier.weight(1f).clickable { selectedIdx = if (isSelected) null else idx },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text("$value", color = if (isSelected) Accent else theme.textMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(1.dp))
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val fraction = value.toFloat() / maxVal
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fraction.coerceAtLeast(0.02f)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (isSelected) Accent else Accent.copy(alpha = 0.65f)))
                }
                Spacer(Modifier.height(2.dp))
                Text(label, color = if (isSelected) Accent else theme.textDim, fontSize = 7.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    if (selectedIdx != null && selectedIdx!! < items.size) {
        val (lbl, v) = items[selectedIdx!!]
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.chart_bar_label, lbl, v), color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DonutChart(values: List<Int>, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = values.sum().toFloat().coerceAtLeast(1f)
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.18f
        val radius = (size.minDimension - stroke) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        var startAngle = -90f
        values.forEachIndexed { i, v ->
            val sweep = v / total * 360f
            drawArc(
                color = colors[i % colors.size],
                startAngle = startAngle,
                sweepAngle = (sweep - 1f).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(cx - radius, cy - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

private fun buildStatsCSV(vm: BooksViewModel): String {
    val sb = StringBuilder()
    sb.appendLine("Título,Autor,Páginas,Género,Puntuación,Inicio,Fin,Días,Págs/día")
    vm.books.filter { it.status == BookStatus.FINISHED }.forEach { b ->
        val days = if (b.startDate != null && b.endDate != null) daysBetween(b.startDate, b.endDate) else 0
        val ppd = if (days > 0) String.format("%.1f", b.pages.toDouble() / days) else ""
        sb.appendLine("\"${b.title}\",\"${b.author}\",${b.pages},\"${b.genres.joinToString("; ")}\",${b.rating},${b.startDate ?: ""},${b.endDate ?: ""},$days,$ppd")
    }
    return sb.toString()
}

private fun buildStatsJSON(vm: BooksViewModel): String {
    val arr = org.json.JSONArray()
    vm.books.filter { it.status == BookStatus.FINISHED }.forEach { b ->
        arr.put(org.json.JSONObject().apply {
            put("title", b.title)
            put("author", b.author)
            put("pages", b.pages)
            put("genres", org.json.JSONArray(b.genres))
            put("rating", b.rating)
            put("startDate", b.startDate ?: "")
            put("endDate", b.endDate ?: "")
        })
    }
    return arr.toString(2)
}

