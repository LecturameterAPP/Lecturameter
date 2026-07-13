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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedVisibility
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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
// v21.41: pointerInput + detectTapGestures removidos (simulate wrapped eliminado)

// ── Modelo ────────────────────────────────────────────────────────────────────
// Fase 1.1: modelos y helpers puros extraídos a model/Models.kt.
// Aquí solo quedan los helpers de presentación (usan R/stringResource).

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

// Fase 1.3: la pila de busqueda (OpenLibraryResult, EditionResult, fetchers, searchEditions,
// ApiThrottle, APP_USER_AGENT, isCoverUrlValid, consensusPages...) vive en repository/SearchRepository.kt
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
    // Auditoría APIs: TTL 24h → 7 días. Los catálogos (OL/GB) cambian poco; evita
    // repetir 4 fases de red al reabrir "Cambiar edición" del mismo libro.
    private val CACHE_DURATION_MS = java.util.concurrent.TimeUnit.DAYS.toMillis(7)

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

// Fase 1.2: BooksViewModel vive en BooksViewModel.kt

// ── Helpers ───────────────────────────────────────────────────────────────────
// Fase 1.1: parseCsvLine, fechas (sdf/today/parseFlexibleDate/…), getStats e
// ISBN viven en utils/CoreUtils.kt. Aquí solo quedan los helpers Composable.

/** Versión Composable de fmtDays usando strings localizados. */
@Composable
fun fmtDaysLocalized(days: Int): String {
    val dayWord = stringResource(R.string.word_days)
    val dayWordSingular = stringResource(R.string.word_day)
    return if (days == 1) "1 $dayWordSingular" else "$days $dayWord"
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
// v21.35: Accent adaptado por tema — índigo oscuro en Claro (contraste).
// Fase 3 (Aurora C): el fondo pasa a teal→púrpura y el acento de Aurora pasa a MORADO
// (#B794F6, mockup aprobado) — el turquesa deja de ser necesario porque ya es el fondo.
val AccentAurora = Color(0xFFB794F6)
val AccentLight  = Color(0xFF4338CA)
fun accentForTheme(theme: Theme): Color = when {
    theme.accent != null                               -> theme.accent  // Dinámico (Material You)
    !theme.isDark && theme.bgDark == Color(0xFFDDE3EC) -> AccentLight   // Claro D
    theme.bgDark == BgDarkA                            -> AccentAurora  // Aurora
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

// Aurora C "Aurora boreal" (Fase 3, mockup aprobado 11-07): degradado teal → púrpura.
// bgDark→bgMid→bgDeep son los 3 stops del fondo; acento morado; bordes teal al 25%.
val BgDarkA = Color(0xFF03343A); val BgMidA = Color(0xFF0E2E4E); val BgDeepA = Color(0xFF221452)
val SurfaceA = Color(0x0EC8FFF8); val BorderA = Color(0x405EEAD4)
val TextMainA = Color(0xFFF0FDFB); val TextMutedA = Color(0xFF9CCFC8); val TextDimA = Color(0xFF77A7A0)

// AMOLED: negro puro para pantallas OLED
// Fase 3: bgMid también #000000 — el 0x0A0A0A del degradado impedía el negro real (píxel apagado)
val BgDarkAm = Color(0xFF000000); val BgMidAm = Color(0xFF000000)
val SurfaceAm = Color(0x10FFFFFF); val BorderAm = Color(0x18FFFFFF)
val TextMainAm = Color(0xFFF1F5F9); val TextMutedAm = Color(0xFF94A3B8); val TextDimAm = Color(0xFF64748B)

// QA r2 12-07: el tema Dinámico (Material You) se ELIMINA a petición de Víctor —
// los 4 temas quedan fijos: Claro, Oscuro, Aurora y AMOLED.
enum class ThemeMode(val value: String) {
    LIGHT("light"), DARK("dark"), AURORA("aurora"), AMOLED("amoled")
}

// Fase 3: bgDeep = tercer stop del degradado de fondo (solo Aurora lo usa; Transparent = 2 stops clásicos)
// accent = acento propio del tema (solo Dinámico lo define; null = accentForTheme decide por bgDark)
data class Theme(val bgDark: Color, val bgMid: Color, val surface: Color, val border: Color, val textMain: Color, val textMuted: Color, val textDim: Color, val isDark: Boolean, val bgSurf: Color = Color.Transparent, val bgSurf2: Color = Color.Transparent, val bgDeep: Color = Color.Transparent, val accent: Color? = null)

fun buildTheme(mode: ThemeMode) = when (mode) {
    ThemeMode.LIGHT  -> Theme(BgDarkL,  BgMidL,  SurfaceL,  BorderL,  TextMainL,  TextMutedL,  TextDimL,  false, bgSurf = Color(0xFFEEF2F8), bgSurf2 = Color(0xFFDDE3EC))
    ThemeMode.DARK   -> Theme(BgDarkD,  BgMidD,  SurfaceD,  BorderD,  TextMainD,  TextMutedD,  TextDimD,  true,  bgSurf = Color(0x1AFFFFFF), bgSurf2 = Color(0x0DFFFFFF))
    ThemeMode.AURORA -> Theme(BgDarkA,  BgMidA,  SurfaceA,  BorderA,  TextMainA,  TextMutedA,  TextDimA,  true,  bgSurf = Color(0x12FFFFFF), bgSurf2 = Color(0x08FFFFFF), bgDeep = BgDeepA)
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
    "Distopía",
    "Divulgación científica",
    "Drama",
    "Economía",
    "Educación",
    "Ensayo",
    "Erótica",
    "Fantasía",
    "Filosofía",
    "Gastronomía",
    "Grimdark",
    "Historia",
    "Humor",
    "Infantil",
    "Juvenil",
    "Lingüística",
    "Literatura clásica",
    "Manga",
    "Memorias",
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
    "True crime",
    "Viajes",
    "Western",
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
    "Distopía" to R.string.genre_distopia,
    "Divulgación científica" to R.string.genre_divulgacion_cientifica,
    "Drama" to R.string.genre_drama,
    "Economía" to R.string.genre_economia,
    "Educación" to R.string.genre_educacion,
    "Ensayo" to R.string.genre_ensayo,
    "Erótica" to R.string.genre_erotica,
    "Fantasía" to R.string.genre_fantasia,
    "Filosofía" to R.string.genre_filosofia,
    "Gastronomía" to R.string.genre_gastronomia,
    "Grimdark" to R.string.genre_grimdark,
    "Historia" to R.string.genre_historia,
    "Humor" to R.string.genre_humor,
    "Infantil" to R.string.genre_infantil,
    "Juvenil" to R.string.genre_juvenil,
    "Lingüística" to R.string.genre_linguistica,
    "Literatura clásica" to R.string.genre_literatura_clasica,
    "Manga" to R.string.genre_manga,
    "Memorias" to R.string.genre_memorias,
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
    "True crime" to R.string.genre_true_crime,
    "Viajes" to R.string.genre_viajes,
    "Western" to R.string.genre_western,
    "Otro" to R.string.genre_otro,
)

// ── Grupos del selector de géneros (P-012) — solo presentación, no afectan al dato ──
val GENRE_GROUPS: List<Pair<Int, List<String>>> = listOf(
    R.string.genre_group_fiction to listOf(
        "Aventura", "Ciencia ficción", "Costumbrismo", "Crimen", "Distopía", "Drama",
        "Erótica", "Fantasía", "Grimdark", "Humor", "Infantil", "Juvenil",
        "Literatura clásica", "Misterio", "Mitología", "Novela histórica", "Novela negra",
        "Poesía", "Romance", "Suspense", "Teatro", "Terror", "Thriller", "Western"
    ),
    R.string.genre_group_nonfiction to listOf(
        "Arte", "Autoayuda", "Biografía", "Ciencia", "Deportes", "Desarrollo personal",
        "Divulgación científica", "Economía", "Educación", "Ensayo", "Filosofía",
        "Gastronomía", "Historia", "Lingüística", "Memorias", "Música", "Naturaleza",
        "Negocios", "Periodismo", "Política", "Psicología", "Religión y espiritualidad",
        "Salud y bienestar", "Sociología", "Tecnología", "True crime", "Viajes"
    ),
    R.string.genre_group_format to listOf(
        "Cómics y novela gráfica", "Novela gráfica", "Manga"
    )
)

// ── Selector de géneros (P-012, mockup ronda 1 aprobado 11-07-2026) ───────────
// Bottom sheet con buscador, sugeridos de la API (borde ámbar) y chips agrupados
// (Ficción / No ficción / Formato). Máximo 2 géneros, contador visible.
// Sustituye al ExposedDropdownMenu plano en los 3 puntos de selección.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenreSelectorSheet(
    initial: List<String>,
    suggested: List<String>,
    theme: Theme,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    onRefreshFromApi: (() -> Unit)? = null   // DetailScreen: "recargar género de la API"
) {
    var selection by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sugg = remember(suggested) { suggested.filter { it in BOOK_GENRES && it != "Otro" } }
    // Etiquetas localizadas precalculadas (displayGenre es @Composable; el filtro las usa)
    val labels = BOOK_GENRES.associateWith { displayGenre(it) }

    fun toggle(g: String) {
        selection = when {
            g in selection -> selection - g
            selection.size < 2 -> selection + g
            else -> selection
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (theme.isDark) Color(0xFF151B31) else theme.bgMid,
        contentColor = theme.textMain
    ) {
        // Feedback 11-07: la lista usa weight() dentro del alto acotado del sheet
        // (nada de alturas fijas) para que el pie de botones nunca se salga de pantalla.
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.genre_sheet_title), color = theme.textMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.genre_sheet_counter, selection.size),
                    color = if (selection.size == 2) Green else theme.textMuted, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.genre_sheet_search), color = theme.textDim, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = theme.textDim, modifier = Modifier.size(18.dp)) },
                singleLine = true, colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))

            @Composable
            fun GenreChip(g: String, isSuggested: Boolean) {
                val sel = g in selection
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (sel) Accent else theme.surface,
                    border = BorderStroke(1.dp, if (sel) Accent else if (isSuggested) Amber.copy(alpha = 0.6f) else theme.border),
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable { toggle(g) }
                ) {
                    Text(
                        labels[g] ?: g,
                        color = if (sel) Color.White else theme.textMain.copy(alpha = 0.85f),
                        fontSize = 12.5.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            val q = normalizeSearchText(query.trim())
            fun visible(g: String) = q.isBlank() ||
                normalizeSearchText(labels[g] ?: g).contains(q) || normalizeSearchText(g).contains(q)

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (sugg.isNotEmpty() && q.isBlank()) {
                    Text(
                        stringResource(R.string.genre_sheet_suggested).uppercase(),
                        color = Amber.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        sugg.forEach { GenreChip(it, true) }
                    }
                }
                GENRE_GROUPS.forEach { (titleRes, items) ->
                    val shown = items.filter { visible(it) }
                    if (shown.isNotEmpty()) {
                        Text(
                            stringResource(titleRes).uppercase(),
                            color = theme.textDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            shown.forEach { GenreChip(it, it in sugg) }
                        }
                    }
                }
            }
            if (onRefreshFromApi != null) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onRefreshFromApi, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.txt_6fc03171), color = Accent, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { selection = emptyList() },
                    shape = RoundedCornerShape(11.dp), modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, theme.border)
                ) { Text(stringResource(R.string.genre_sheet_clear), color = theme.textMuted) }
                Button(
                    onClick = { onConfirm(selection); onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(11.dp), modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.genre_sheet_accept), fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

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

    // ── Distopía (P-013) — antes de Ciencia ficción para que tenga prioridad ─
    if (r.contains("dystopi") || r.contains("distopí") || r.contains("distopi")) add("Distopía")

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

    // ── Géneros nuevos (P-013) ─────────────────────────────────────────────
    if (r.contains("erotic") || r.contains("erótic") || r.contains("erotismo")) add("Erótica")
    // "western" excluyendo usos no literarios ("western philosophy/civilization/medicine")
    if (Regex("""\bwesterns?\b""").containsMatchIn(r) &&
        !r.contains("philosoph") && !r.contains("civiliza") && !r.contains("medicine") &&
        !r.contains("filosof")) add("Western")
    // True crime — antes que la regla de Crimen para que tenga prioridad
    if (r.contains("true crime") || r.contains("crimen real")) add("True crime")
    if (r.contains("memoir") || r.contains("memorias")) add("Memorias")
    if (r.contains("cooking") || r.contains("cookery") || r.contains("cookbook") ||
        r.contains("gastronom") || r.contains("cocina") || r.contains("culinar") ||
        r.contains("food and drink") || r.contains("food & drink")) add("Gastronomía")
    if (r.contains("popular science") || r.contains("divulgación científica") ||
        r.contains("divulgacion cientifica")) add("Divulgación científica")

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

// D-002/T1 (Fase 4): puente del inicio rápido de sesión — el selector ⏱️ del home fija
// el libro y DetailScreen arranca el cronómetro al abrirse (mismo flujo de permisos
// que el botón ▶ del detalle).
object TimerQuickStart {
    @Volatile var pendingBookId: Long = -1L
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

    // v2.5 fix idioma: attachBaseContext + createConfigurationContext.
    // El mecanismo anterior (resources.updateConfiguration, deprecated) se REVERTÍA
    // solo cuando el sistema refrescaba la Configuration → mezcla de idiomas en
    // pantalla (p. ej. diálogo de retos con botones en inglés y resto en español)
    // y los problemas de cambio de idioma reportados por usuarios. El contexto
    // creado aquí es inmutable y lo heredan también las ventanas de los diálogos.
    override fun attachBaseContext(newBase: android.content.Context) {
        val lang = newBase.getSharedPreferences("lecturameter", MODE_PRIVATE)
            .getString("app_language", "es") ?: "es"
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
            // QA 12-07 r2: el Crossfade sobre themeMode recreaba la app ENTERA en cada
            // cambio de tema (Crossfade compone contenido nuevo por estado) — el
            // NavController y todo el estado guardado se perdían y volvías a la pantalla
            // principal. Ahora el árbol de composición es único y lo que se anima son los
            // COLORES del tema (mismo efecto de fundido, sin perder la navegación).
            // Feedback 13-07 (4): CROSSFADE REAL entre temas — animar los ~10 colores del
            // tema recomponía el árbol entero en cada frame (stutter = sensación "tosca").
            // Feedback 13-07 (7): el snapshot por software (v.draw en Canvas) clavaba un
            // frame largo que se comía media animación → el fundido no se notaba. Ahora la
            // captura es con PixelCopy (copia por GPU del frame ya presentado, coste ~0 en
            // el hilo principal, resolución completa) y el TEMA NUEVO no se aplica hasta
            // que la copia termina: cero carrera, cero jank. Si PixelCopy falla, el cambio
            // es instantáneo sin fade (mejor seco que tosco).
            var displayedThemeMode by remember { mutableStateOf(vm.themeMode) }
            var themeSnapshot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            if (vm.themeMode != displayedThemeMode) {
                val targetMode = vm.themeMode
                LaunchedEffect(targetMode) {
                    // Feedback 13-07 (8): capturar SOLO el área de contenido (android.R.id.content),
                    // no el decorView entero — el bitmap incluía las barras del sistema y al
                    // estirarse sobre el contenido todo se veía "encogido" durante el fundido
                    // (efecto de la app haciéndose pequeña y volviendo a crecer).
                    val content = window.decorView.findViewById<android.view.View>(android.R.id.content)
                    if (content != null && content.width > 0 && content.height > 0) {
                        val loc = IntArray(2).also { content.getLocationInWindow(it) }
                        val bmp = android.graphics.Bitmap.createBitmap(
                            content.width, content.height, android.graphics.Bitmap.Config.ARGB_8888
                        )
                        try {
                            android.view.PixelCopy.request(
                                window,
                                android.graphics.Rect(loc[0], loc[1], loc[0] + content.width, loc[1] + content.height),
                                bmp, { result ->
                                    themeSnapshot = if (result == android.view.PixelCopy.SUCCESS) bmp.asImageBitmap() else null
                                    displayedThemeMode = targetMode
                                }, android.os.Handler(android.os.Looper.getMainLooper())
                            )
                        } catch (_: Throwable) {
                            themeSnapshot = null
                            displayedThemeMode = targetMode
                        }
                    } else {
                        themeSnapshot = null
                        displayedThemeMode = targetMode
                    }
                }
            }
            val theme = remember(displayedThemeMode) { normalizeThemeDeep(buildTheme(displayedThemeMode)) }
            Box {
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
            // Overlay del crossfade: el frame del tema anterior fundiéndose
            themeSnapshot?.let { snap ->
                val fadeAlpha = remember(snap) { Animatable(1f) }
                LaunchedEffect(snap) {
                    // Feedback 13-07 (7): 300 ms LINEAL — con easing LinearOutSlowIn el 80%
                    // del cambio ocurría en los primeros ~100 ms y parecía un corte seco.
                    // Un fundido constante se percibe entero de principio a fin.
                    fadeAlpha.animateTo(0f, tween(durationMillis = 300, easing = androidx.compose.animation.core.LinearEasing))
                    themeSnapshot = null
                }
                androidx.compose.foundation.Image(
                    bitmap = snap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize().alpha(fadeAlpha.value)
                )
            }
            } // Box crossfade
        }

        // Backup automático en Drive según el intervalo elegido en Ajustes (default 2h).
        // También ejecuta el backup local en Descargas en la misma pasada.
        // Bug fix v21.15: NO ponemos NetworkType.CONNECTED como restricción — eso bloqueaba
        // TAMBIÉN el backup local (que no necesita red) cuando no había conexión.
        // v2.4 rework fix: ExistingPeriodicWorkPolicy.UPDATE en vez de KEEP — KEEP conservaba
        // para siempre requests periódicos VIEJOS (p. ej. los que aún tenían la constraint de
        // red pre-v21.15), y el auto-backup dejaba de dispararse. UPDATE aplica la spec nueva
        // sin resetear el timer del periodo. Backoff LINEAL 15 min: el retry exponencial de
        // Drive sin red retrasaba el siguiente backup horas.
        val backupIntervalH = prefs.getInt("backup_interval_hours", 2).toLong()
        val driveWorkRequest = PeriodicWorkRequestBuilder<DriveBackupWorker>(backupIntervalH, TimeUnit.HOURS)
            .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "lecturameter_drive_backup",
            ExistingPeriodicWorkPolicy.UPDATE,
            driveWorkRequest
        )

        // Refresco periódico del widget según el intervalo de Ajustes (default 30 min)
        val widgetMins = prefs.getInt("widget_refresh_minutes", 30)
        val widgetWorkRequest = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(widgetMins.toLong(), TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "lecturameter_widget_refresh",
            ExistingPeriodicWorkPolicy.UPDATE,
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

// Feedback 13-07 (4): bgDeep nunca es Transparent en runtime — si el tema no define
// tercer stop (solo Aurora lo hace), se usa su propio bgDark. Así el degradado del fondo
// es SIEMPRE de 3 stops y el crossfade entre temas no pasa por negro.
fun normalizeThemeDeep(t: Theme): Theme =
    if (t.bgDeep == Color.Transparent) t.copy(bgDeep = t.bgDark) else t

// QA 12-07 r2 (Aurora): tema actual accesible desde componentes hoja (chips, pills)
// sin pasar `theme` por todos los call sites — se usa para remapear el acento.
val LocalAppTheme = androidx.compose.runtime.compositionLocalOf<Theme?> { null }

/** Si [color] es el acento índigo global (con o sin alpha), devuelve el acento del TEMA
 *  actual (morado en Aurora, primario Material You en Dinámico) conservando el alpha. */
@Composable
fun themedAccentOr(color: Color): Color {
    val t = LocalAppTheme.current ?: return color
    return if (color.copy(alpha = 1f) == Accent) accentForTheme(t).copy(alpha = color.alpha) else color
}

@Composable
fun LecturaMeterTheme(theme: Theme, content: @Composable () -> Unit) {
    val cs = if (theme.isDark)
        darkColorScheme(background = theme.bgDark, surface = theme.bgMid, primary = Accent, onPrimary = Color.White, onBackground = theme.textMain, onSurface = theme.textMain)
    else
        lightColorScheme(background = theme.bgDark, surface = theme.bgMid, primary = Accent, onPrimary = Color.White, onBackground = theme.textMain, onSurface = theme.textMain)
    androidx.compose.runtime.CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(colorScheme = cs, content = content)
    }
}

// ── Navigation ────────────────────────────────────────────────────────────────

// v2.4: centrado de contenido en pantallas anchas (mockup aprobado).
// ≤600dp deja el contenido tal cual; por encima lo acota a maxWidth y lo centra.
@Composable
fun WideScreenCenter(enabled: Boolean = true, maxContentWidth: Dp = 640.dp, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth <= 600.dp) {
            content()
        } else {
            Box(
                Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
            ) { content() }
        }
    }
}

sealed class Screen {
    // P-020: Screen.SessionHistory eliminado — el historial es un drawer, no un destino
    // (la ruta "session_history" no tenía composable y navegar a ella habría crasheado)
    object List : Screen(); object Add : Screen(); object BookSearch : Screen(); object Stats : Screen()
    object ImportExport : Screen(); object WrappedHistory : Screen()
    object Bingo : Screen(); object Settings : Screen(); object Challenges : Screen()
    data class Detail(val id: Long, val highlightDate: String? = null) : Screen()
    data class AuthorBooks(val author: String) : Screen()
    data class Wrapped(val year: Int) : Screen()
    data class BulkReload(val type: String) : Screen()
    data class DailySessions(val date: String) : Screen()
}

// Fase 1.4: navegación migrada a Compose Navigation (NavController).
// Screen sigue siendo el vocabulario de destinos; route() lo serializa a la ruta string.
// El NavController persiste el backstack en SavedState (rotación y muerte de proceso),
// sustituyendo al backstack manual en rememberSaveable + saveableStateHolder de 2.7.
private fun Screen.route(): String = when (this) {
    is Screen.List -> "list"
    is Screen.Add -> "add"
    is Screen.BookSearch -> "book_search"
    is Screen.Stats -> "stats"
    is Screen.ImportExport -> "import_export"
    is Screen.WrappedHistory -> "wrapped_history"
    is Screen.Bingo -> "bingo"
    is Screen.Settings -> "settings"
    is Screen.Challenges -> "challenges"
    is Screen.Detail -> if (highlightDate != null) "detail/$id?highlightDate=${Uri.encode(highlightDate)}" else "detail/$id"
    is Screen.AuthorBooks -> "author/${Uri.encode(author)}"
    is Screen.Wrapped -> "wrapped/$year"
    is Screen.BulkReload -> "bulk_reload/$type"
    is Screen.DailySessions -> "daily_sessions/$date"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturaMeterApp(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
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

    val navController = androidx.navigation.compose.rememberNavController()
    var backPressedOnce by remember { mutableStateOf(false) }
    // Ruta actual (para gestos del drawer y doble-atrás en la principal)
    val currentEntry by navController.currentBackStackEntryAsState()
    val isOnList = (currentEntry?.destination?.route ?: "list") == "list"

    fun navigateTo(destination: Screen) {
        backPressedOnce = false
        if (destination is Screen.List) {
            // Semántica 2.7: navegar a List resetea el backstack (volvemos a la entrada raíz existente)
            navController.popBackStack("list", false)
        } else {
            navController.navigate(destination.route()) { launchSingleTop = true }
        }
    }

    fun goBack() {
        backPressedOnce = false
        if (!navController.popBackStack()) {
            (context as? android.app.Activity)?.finish()
        }
    }

    // Navegación inicial (timer/deep link) — una sola vez; el NavController restaura
    // el backstack por sí mismo en recreaciones y muerte de proceso.
    var initialNavDone by rememberSaveable { mutableStateOf(false) }
    // QA 12-07 (B-012): en frío con deep link, la biblioteca vacía se veía un instante
    // antes del detalle. Hasta que la navegación inicial se asienta, un velo con el color
    // de fondo cubre el NavHost (solo cuando el arranque NO va a la lista).
    var initialNavSettled by remember { mutableStateOf(initialScreen is Screen.List) }
    LaunchedEffect(Unit) {
        if (!initialNavDone) {
            initialNavDone = true
            if (initialScreen !is Screen.List) navController.navigate(initialScreen.route())
        }
        // QA 12-07 r2 (B-012): en frío el primer frame se dibuja ANTES de que lleguen los
        // insets del sistema → el contenido aparecía pegado arriba-izquierda y "saltaba" a
        // su sitio. Mantener el velo un par de frames más cubre ese reacomodo.
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        initialNavSettled = true
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


    // ── Migración v13: rellenar startPage/endPage en sesiones antiguas ────────
    // Usamos derivedStateOf sobre sessions para que se recalcule si cambian las sesiones
    // (p. ej. tras restaurar un backup). migrationDone se lee en cada recomposición.
    val sessionsNeedingMigration by remember {
        derivedStateOf {
            if (prefs.getBoolean("session_pages_migrated", false)) emptyList()
            else sessions.filter { it.startPage == null || it.endPage == null }
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
        val book = remember(session.bookId) { books.find { it.id == session.bookId } }
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

    // Doble pulsación para salir desde la pantalla principal.
    // (El atrás en el resto de pantallas lo gestiona el propio NavController.)
    BackHandler(enabled = isOnList) {
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

    // Feedback 13-07 (D-002): el historial deja de ser un ModalNavigationDrawer — ahora es
    // un panel PROPIO del home (dentro de ListScreen), de modo que el rail queda visible
    // Y FUNCIONAL con el panel abierto (los destinos navegan, 📜 cierra, 📚 vuelve arriba).
    run {
        // Feedback WhatsApp 10-07: imePadding para que el teclado no tape el campo con foco.
        // Con targetSdk 35 (Android 15+) adjustResize se ignora (edge-to-edge forzado); en
        // versiones anteriores la ventana se redimensiona y el inset IME queda a 0 (no duplica).
        // Fase 3 (Aurora C) + feedback 13-07: el degradado es SIEMPRE de 3 stops —
        // animateThemeColors garantiza que bgDeep nunca es Transparent (si el tema no
        // define deep, es su propio bgDark), así el cambio de tema fluye sin saltos.
        val bgBrush = Brush.verticalGradient(listOf(theme.bgDark, theme.bgMid, theme.bgDeep))
        // QA 12-07 r2 (B-012): el velo vive FUERA del padding de insets — si cubriera solo
        // la zona con padding, el reacomodo de los insets en el arranque en frío seguiría
        // siendo visible en los bordes.
        Box(Modifier.fillMaxSize().background(bgBrush)) {
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            // Fase 1.4: NavHost sin transiciones (paridad visual con 2.7; animaciones = Fase 4).
            // Feedback 11-07: EnterTransition.None NO es instantáneo — el reloj de la
            // transición sigue corriendo y la pantalla saliente queda visible (fade feo
            // con solape). snap() hace el cambio en un frame.
            // v2.4: pantallas secundarias centradas en anchos ≥600dp via WideScreenCenter.
            NavHost(
                navController = navController,
                startDestination = "list",
                enterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.snap()) },
                exitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.snap()) },
                popEnterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.snap()) },
                popExitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.snap()) }
            ) {
                composable("list") {
                    WideScreenCenter(enabled = false) {
                            val mainActivity = context as? MainActivity
                            ListScreen(vm, prefs, theme,
                                onAdd = { navigateTo(Screen.Add) },
                                onSearch = { navigateTo(Screen.BookSearch) },
                                onStats = { navigateTo(Screen.Stats) },
                                onWrappedHistory = { navigateTo(Screen.WrappedHistory) },
                                onWrapped = { y -> navigateTo(Screen.Wrapped(y)) },
                                onChallenges = { navigateTo(Screen.Challenges) },
                                onDetail = { navigateTo(Screen.Detail(it)) },
                                onSettings = { navigateTo(Screen.Settings) },
                                onImportExport = { navigateTo(Screen.ImportExport) },
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
                                    navigateTo(Screen.Bingo)
                                }
                            )
                    }
                }
                composable("add") {
                    WideScreenCenter {
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
                }
                composable("book_search") {
                    WideScreenCenter {
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
                }
                composable("stats") { WideScreenCenter { StatsScreen(vm, prefs, theme, onBack = { goBack() }, onWrapped = { y -> navigateTo(Screen.Wrapped(y)) }, onWrappedHistory = { navigateTo(Screen.WrappedHistory) }, onDetail = { navigateTo(Screen.Detail(it)) }, onDetailWithDate = { bookId, date -> navigateTo(Screen.Detail(bookId, date)) }, onDailySessions = { date -> navigateTo(Screen.DailySessions(date)) }) } }
                composable("import_export") { WideScreenCenter { ImportExportScreen(vm, prefs, theme, onBack = { goBack() }) } }
                composable("wrapped_history") { WideScreenCenter { WrappedHistoryScreen(vm, theme, onBack = { goBack() }, onOpen = { y -> navigateTo(Screen.Wrapped(y)) }) } }
                composable("settings") { WideScreenCenter { SettingsScreen(vm, prefs, theme, onBack = { goBack() }, onBulkReload = { type -> navigateTo(Screen.BulkReload(type)) }, onResetTutorial = { navigateTo(Screen.List) }, onImportExport = { navigateTo(Screen.ImportExport) }) } }
                composable("challenges") { WideScreenCenter { ChallengesScreen(vm, prefs, theme, onBack = { goBack() }) } }
                composable("bingo") { WideScreenCenter { BingoPlaceholderScreen(theme, onBack = { goBack() }) } }
                composable(
                    "detail/{id}?highlightDate={highlightDate}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("highlightDate") { type = NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { entry ->
                    val bookId = entry.arguments?.getLong("id") ?: return@composable
                    WideScreenCenter { DetailScreen(vm, prefs, theme, bookId, highlightDate = entry.arguments?.getString("highlightDate"), onBack = { goBack() }, onAuthorClick = { navigateTo(Screen.AuthorBooks(it)) }) }
                }
                composable(
                    "author/{author}",
                    arguments = listOf(navArgument("author") { type = NavType.StringType })
                ) { entry ->
                    WideScreenCenter { AuthorBooksScreen(vm, prefs, theme, entry.arguments?.getString("author") ?: "", onBack = { goBack() }, onDetail = { navigateTo(Screen.Detail(it)) }) }
                }
                composable(
                    "wrapped/{year}",
                    arguments = listOf(navArgument("year") { type = NavType.IntType })
                ) { entry ->
                    WideScreenCenter { WrappedScreen(vm, prefs, theme, entry.arguments?.getInt("year") ?: 0, onBack = { goBack() }) }
                }
                composable(
                    "bulk_reload/{type}",
                    arguments = listOf(navArgument("type") { type = NavType.StringType })
                ) { entry ->
                    WideScreenCenter { BulkReloadScreen(vm, prefs, theme, entry.arguments?.getString("type") ?: "", onBack = { goBack() }) }
                }
                composable(
                    "daily_sessions/{date}",
                    arguments = listOf(navArgument("date") { type = NavType.StringType })
                ) { entry ->
                    val date = entry.arguments?.getString("date") ?: ""
                    WideScreenCenter {
                        DailySessionsScreen(
                            date = date,
                            sessions = sessions.filter { it.date == date },
                            books = books,
                            theme = theme,
                            onNavigateToDetail = { bookId, d -> navigateTo(Screen.Detail(bookId, d)) },
                            onBack = { goBack() }
                        )
                    }
                }
            }
        }
            // B-012: velo de arranque — tapa el frame de biblioteca vacía cuando el
            // arranque en frío entra por deep link (widget/timer) hacia otra pantalla,
            // y ahora también el reacomodo de insets del primer frame (r2).
            if (!initialNavSettled) {
                Box(Modifier.fillMaxSize().background(theme.bgDark)) {}
            }
        }
    }
}

@Composable
fun BingoPlaceholderScreen(theme: Theme, onBack: () -> Unit) {
    BackHandler { onBack() }
    // Feedback 11-07: flecha en cabecera estándar (arriba a la izquierda, como el
    // resto de pantallas) — antes iba dentro de la Column centrada y flotaba rara.
    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize().background(theme.bgDark).systemBarsPadding()) {
        IconButton(
            onClick = onBack,
            modifier = androidx.compose.ui.Modifier.align(Alignment.TopStart).padding(top = 28.dp, start = 16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = theme.textMain)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = androidx.compose.ui.Modifier.align(Alignment.Center)
        ) {
            Text("Bingo", color = theme.textMain, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(androidx.compose.ui.Modifier.height(12.dp))
            Text("Disponible en Fase 5", color = theme.textMuted, fontSize = 14.sp)
        }
    }
}

// v2.5: aviso de libro duplicado (Cancelar rojo / Añadir igualmente Accent)
@Composable
fun DuplicateBookDialog(candidate: Book, existing: Book, theme: Theme, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.bgMid,
        title = { Text(stringResource(R.string.dup_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.dup_text, existing.title), color = theme.textMuted, fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.dup_add_anyway), color = Accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
    )
}

// ── ListScreen ────────────────────────────────────────────────────────────────
//
// Estilo Goodreads: pestañas por estante + búsqueda + ordenación.
// Cada pestaña muestra sólo los libros de ese estado → no hay listas infinitas.

// ── D-002 (Fase 4): mini-rail del home ────────────────────────────────────────
// El rail vive SOLO en la biblioteca: 📜 historial (fijo) + 📚 home (fijo) + destinos
// reordenables. Destinos = push a pantalla completa (semántica 2.7); el historial se
// despliega como panel encajado contra el rail. Iconos = emojis del sistema (D-002b).
// Long-press en un destino → modo edición con arrastre vertical; ✓ guarda en prefs.
// Feedback 13-07: la lupa sale del rail (la búsqueda online vive en la barra de búsqueda)
private val RAIL_DEFAULT_ORDER = listOf("challenges", "stats", "bingo", "wrapped")

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RailItem(
    emoji: String?,
    theme: Theme,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    // Feedback 13-07: los destinos vuelven a los iconos Material azules; solo
    // historial (📜) y biblioteca (📚) conservan emoji (más el icono del tema en la barra)
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    Box(
        Modifier
            .padding(vertical = 3.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) Accent.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (enabled) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(19.dp))
        else Text(emoji ?: "", fontSize = 17.sp)
    }
}

@Composable
fun HomeRail(
    theme: Theme,
    prefs: android.content.SharedPreferences,
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onStats: () -> Unit,
    onChallenges: () -> Unit,
    onBingo: () -> Unit,
    onWrapped: () -> Unit,
    // Feedback 13-07 (8): swipe horizontal SOBRE EL RAIL abre/cierra el historial —
    // la franja de 22dp junto al rail era invisible y nadie acertaba a deslizar ahí;
    // lo natural es arrastrar desde el propio rail (de donde sale el panel)
    onHistorySwipe: (Boolean) -> Unit = {}
) {
    var order by remember {
        mutableStateOf(
            prefs.getString("rail_order", null)
                ?.split(",")?.filter { it in RAIL_DEFAULT_ORDER }
                // Órdenes guardados con claves antiguas o incompletas: completar con el default
                ?.let { saved -> saved + RAIL_DEFAULT_ORDER.filter { it !in saved } }
                ?: RAIL_DEFAULT_ORDER
        )
    }
    var editMode by remember { mutableStateOf(false) }
    val slotPx = with(androidx.compose.ui.platform.LocalDensity.current) { 46.dp.toPx() }

    // Feedback 13-07: destinos con los iconos Material azules de la fila antigua
    fun railIcon(dest: String) = when (dest) {
        "challenges" -> Icons.Default.EmojiEvents
        "stats"      -> Icons.Default.BarChart
        "bingo"      -> Icons.Default.GridView
        else         -> Icons.Default.CardGiftcard
    }

    val swipeAcc = remember { mutableStateOf(0f) }
    Column(
        Modifier.width(46.dp).fillMaxHeight().padding(top = 4.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    swipeAcc.value += delta
                    if (swipeAcc.value > 60f) { onHistorySwipe(true); swipeAcc.value = 0f }
                    else if (swipeAcc.value < -60f) { onHistorySwipe(false); swipeAcc.value = 0f }
                },
                onDragStarted = { swipeAcc.value = 0f }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 📜 historial — casilla fija superior (spec D-002); toggle del panel
        RailItem("📜", theme, enabled = !editMode, onClick = onHistory)
        // 📚 biblioteca — fija; cierra el panel del historial y sube la lista arriba
        RailItem("📚", theme, highlighted = true, enabled = !editMode, onClick = onLibrary)
        HorizontalDivider(color = theme.border, thickness = 1.dp, modifier = Modifier.width(22.dp).padding(vertical = 3.dp))
        // Feedback 13-07 (4): los botones no arrastrados se DESLIZAN a su hueco nuevo
        // (posiciones absolutas animadas en vez de flujo de Column)
        Box(Modifier.height(46.dp * order.size).fillMaxWidth()) {
        RAIL_DEFAULT_ORDER.forEach { dest ->
            val idx = order.indexOf(dest)
            var dragging by remember(dest) { mutableStateOf(false) }
            var dragOffset by remember(dest) { mutableStateOf(0f) }
            val settledY by androidx.compose.animation.core.animateFloatAsState(
                targetValue = idx * slotPx,
                animationSpec = tween(durationMillis = 220),
                label = "rail_slot_$dest"
            )
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(if (dragging) 1f else 0f)
                    .offset { IntOffset(0, (if (dragging) idx * slotPx + dragOffset else settledY).roundToInt()) }
                    .then(
                        if (editMode) Modifier.pointerInput(dest) {
                            detectDragGestures(
                                onDragStart = { dragging = true },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val shift = (dragOffset / slotPx).roundToInt()
                                    if (shift != 0) {
                                        val from = order.indexOf(dest)
                                        val to = (from + shift).coerceIn(0, order.lastIndex)
                                        if (to != from) {
                                            order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
                                            dragOffset -= (to - from) * slotPx
                                        }
                                    }
                                },
                                onDragEnd = { dragging = false; dragOffset = 0f },
                                onDragCancel = { dragging = false; dragOffset = 0f }
                            )
                        } else Modifier
                    )
            ) {
                RailItem(
                    null, theme,
                    icon = railIcon(dest),
                    highlighted = editMode,
                    enabled = !editMode,
                    onLongPress = { editMode = true },
                    onClick = {
                        when (dest) {
                            "challenges" -> onChallenges()
                            "stats"      -> onStats()
                            "bingo"      -> onBingo()
                            else         -> onWrapped()
                        }
                    }
                )
            }
        }
        } // Box de posiciones absolutas (animación de reorden)
        if (editMode) {
            RailItem(null, theme, highlighted = true, icon = Icons.Default.Check, onClick = {
                prefs.edit().putString("rail_order", order.joinToString(",")).apply()
                editMode = false
            })
        }
    }
}

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
        Modifier.fillMaxSize().background(theme.bgDark).systemBarsPadding(),
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
                    Text(stringResource(R.string.tutorial_mock_pages), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                    Text(stringResource(R.string.tutorial_mock_days), color = theme.textMuted, fontSize = 12.sp, maxLines = 1)
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
        StatBox("136p", stringResource(R.string.pill_pags_leidas), Modifier.weight(1f), theme, highlight = true, highlightColor = Accent)
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
                    DrawerStatChipH("⏱️ 28m", Sky, Modifier.weight(1f))
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
                    DataChip("⏱️ 28m", Sky.copy(alpha = 0.15f), Sky, Modifier.weight(1f))
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

// v2.5: descriptionComposable soporta contenido enriquecido (icono inline, etc).
// Si != null se renderiza en vez de `description` (que puede quedar vacío).
data class TutorialPage(
    val icon: String,
    val title: String,
    val description: String,
    val visual: (@Composable () -> Unit)? = null,
    val descriptionComposable: (@Composable (Theme) -> Unit)? = null
)

/** v2.5: descripción de tutorial con un icono vectorial REAL inline (mismo que en la UI). */
@Composable
fun TutorialInlineIconDesc(
    pre: String, post: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color, theme: Theme
) {
    val inlineId = "inlineIcon"
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        append(pre); appendInlineContent(inlineId, "[icon]"); append(post)
    }
    val inline = mapOf(inlineId to androidx.compose.foundation.text.InlineTextContent(
        androidx.compose.ui.text.Placeholder(
            width = 18.sp, height = 16.sp,
            placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter
        )
    ) { Icon(icon, contentDescription = null, tint = iconTint) })
    Text(
        annotated, inlineContent = inline,
        color = theme.textMuted, fontSize = 15.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp
    )
}

@Composable
fun TutorialPageContent(page: TutorialPage, theme: Theme, isLandscape: Boolean = false) {
    // v2.4: adaptativo (mockup aprobado). Landscape → dos columnas (visual | texto).
    // Portrait/tablet → contenido centrado con ancho máximo, visual acotado.
    if (isLandscape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Box(
                Modifier.weight(0.42f).widthIn(max = 300.dp),
                contentAlignment = Alignment.Center
            ) {
                if (page.visual != null) page.visual.invoke()
                else Text(page.icon, fontSize = 56.sp)
            }
            Column(Modifier.weight(0.58f), horizontalAlignment = Alignment.Start) {
                Text(page.title, color = theme.textMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (page.descriptionComposable != null) page.descriptionComposable.invoke(theme)
                else Text(page.description, color = theme.textMuted, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Box(Modifier.widthIn(max = 480.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (page.visual != null) {
                        Box(Modifier.widthIn(max = 380.dp)) { page.visual.invoke() }
                    } else {
                        Text(page.icon, fontSize = 64.sp)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(page.title, color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    if (page.descriptionComposable != null) page.descriptionComposable.invoke(theme)
                    else Text(page.description, color = theme.textMuted, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TutorialSlideshow(theme: Theme, onComplete: () -> Unit, onSkip: () -> Unit) {
    // P-021 (12-07-2026): rework 14 → 10 slides (plan: Documentación/Plan — Tutorial capturas v2).
    // Fusiones: 1+3→1 · 2→2 · 4+8→3 · 5+7→4 · 6→5. Orden final fijado por Víctor (QA r2):
    // widget → herramientas → batería → feedback → donaciones. Ediciones: sin slide propia,
    // una frase dentro de la 1. Textos cortos (máx ~2 frases). Capturas reales ES/EN: al
    // final de la Fase 4 (los mocks actuales se mantienen hasta entonces).
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

    BoxWithConstraints(Modifier.fillMaxSize().background(theme.bgDark)) {
        // v2.4: adaptativo (mockup aprobado) — landscape = dos columnas por página,
        // pantallas anchas = footer y contenido centrados a un ancho máximo.
        val isLandscape = maxWidth > maxHeight
        val footerMax = if (maxWidth > 600.dp) 720.dp else Dp.Infinity
        // P-021 + QA 12-07 r2: orden final widget → herramientas → BATERÍA → FEEDBACK →
        // donaciones (Víctor 12-07). La slide 4 se reparte distinto en horizontal para
        // que el mock de la sesión desplegada no se corte: el historial pasa a ser el
        // visual de la izquierda y las pills acompañan al texto a la derecha.
        val pages = listOf(
            // 1 — Tu biblioteca (antes 1+3; tarjeta mock + frase de ediciones)
            TutorialPage("📚", stringResource(R.string.tut10_library_title), stringResource(R.string.tut10_library_desc),
                visual = { TutorialBookCardVisual(theme) }),
            // 2 — Importa y respalda
            TutorialPage("📤", stringResource(R.string.tut10_backup_title), stringResource(R.string.tut10_backup_desc)),
            // 3 — Sesiones (antes 4+8: cronómetro + registro manual)
            TutorialPage("⏱️", stringResource(R.string.tut10_sessions_title), stringResource(R.string.tut10_sessions_desc)),
            // 4 — Estadísticas (antes 5+7; mock de pills — opción A de Víctor: el mock usa
            // los StatBox/DrawerStatChipH reales, así que hereda los colores vigentes)
            if (isLandscape)
                TutorialPage("📊", stringResource(R.string.tut10_stats_title), "",
                    visual = { TutorialHistoryRowVisual(theme) },
                    descriptionComposable = { th ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.tut10_stats_desc),
                                color = th.textMuted, fontSize = 15.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            TutorialStatsPillsVisual(th)
                        }
                    }
                )
            else
                TutorialPage("📊", stringResource(R.string.tut10_stats_title), "",
                    visual = { TutorialStatsPillsVisual(theme) },
                    descriptionComposable = { th ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.tut10_stats_desc),
                                color = th.textMuted, fontSize = 15.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            TutorialHistoryRowVisual(th)
                        }
                    }
                ),
            // 5 — Retos
            TutorialPage("🏆", stringResource(R.string.tut10_challenges_title), stringResource(R.string.tut10_challenges_desc)),
            // 6 — Widget
            TutorialPage("🧩", stringResource(R.string.tut_widget_title), stringResource(R.string.tut_widget_desc), visual = { TutorialWidgetVisual(theme) }),
            // 7 — Herramientas en Ajustes
            TutorialPage("🛠️", stringResource(R.string.tut_p10_title), stringResource(R.string.tut_p10_desc)),
            // 8 — Restricciones de batería
            TutorialPage("🔋", stringResource(R.string.tut_p12_title), stringResource(R.string.tut_p12_desc)),
            // 9 — Feedback
            TutorialPage("📨", stringResource(R.string.tut_p11_title), stringResource(R.string.tut_p11_desc)),
            // 10 — Donaciones
            TutorialPage("", stringResource(R.string.tut_donations_title), stringResource(R.string.tut_donations_desc), visual = { TutorialDonationsVisual(theme) })
        )
        val pagerState = androidx.compose.foundation.pager.rememberPagerState { pages.size }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TutorialPageContent(pages[page], theme, isLandscape)
                }
            }

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

            // Feedback 2.6: 3 slots simétricos (weight 1 a cada lado) — Saltar queda centrado
            // en pantalla Y equidistante de Atrás/Siguiente. El texto de los botones laterales
            // se auto-reduce si no cabe en su slot (evita el wrap de "¡Comenzar!" en ES).
            val isLastPage = pagerState.currentPage == pages.size - 1
            var nextTextSize by remember(isLastPage) { mutableStateOf(14.sp) }
            Row(
                Modifier.widthIn(max = footerMax).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.5.dp, theme.border),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = theme.textMuted),
                            // Feedback 2.7: mismo padding y anchura mínima que "Siguiente" —
                            // los dos botones laterales quedan del mismo tamaño
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(44.dp).defaultMinSize(minWidth = 108.dp)
                        ) { Text(stringResource(R.string.txt_c673411e), fontSize = 14.sp, maxLines = 1) }
                    }
                }
                OutlinedButton(
                    onClick = { showSkipDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.5.dp, Accent.copy(alpha = 0.6f)),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(stringResource(R.string.txt_a6e39241), fontSize = 14.sp, maxLines = 1)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Button(
                        onClick = {
                            if (isLastPage) onComplete()
                            else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Accent),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        // Feedback 2.7: anchura mínima compartida con "Atrás" (mismo tamaño)
                        modifier = Modifier.height(44.dp).defaultMinSize(minWidth = 108.dp)
                    ) {
                        Text(
                            stringResource(if (isLastPage) R.string.txt_d4d1809c else R.string.txt_eccc5922),
                            color = Color.White, fontSize = nextTextSize, fontWeight = FontWeight.Bold,
                            maxLines = 1, softWrap = false,
                            onTextLayout = { if (it.hasVisualOverflow && nextTextSize > 10.sp) nextTextSize *= 0.92f }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    onChallenges: () -> Unit = {},
    onDetail: (Long) -> Unit,
    onSettings: () -> Unit = {},
    onImportExport: () -> Unit = {},
    onScanIsbnSearch: () -> Unit = {},
    onNavigateToBookSearch: () -> Unit = {},
    onAddWithIsbn: (String) -> Unit = {},
    onEasterEgg: () -> Unit = {}
) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val booksAll by vm.books.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf(vm.savedSearchQuery) }
    var sortOrderName by rememberSaveable { mutableStateOf(vm.savedSortOrder.name) }
    val sortOrder = SortOrder.entries.firstOrNull { it.name == sortOrderName } ?: SortOrder.DATE_DESC
    var showSortMenu by remember { mutableStateOf(false) }
    var showIsbnScanDialog by remember { mutableStateOf(false) }
    var scannedIsbnForDialog by remember { mutableStateOf("") }
    // D-002/T1: selector de inicio rápido de sesión desde la barra (⏱️)
    var showQuickStartSheet by remember { mutableStateOf(false) }
    // Feedback 13-07: el historial es un panel del home (no un drawer modal) — así el
    // rail sigue visible y FUNCIONAL con el panel abierto
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = historyOpen) { historyOpen = false }

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
    // v2.4: grid state — 1 columna en móvil, 2 en ≥840dp (mockup aprobado)
    val listState = rememberLazyGridState()
    val listScope = rememberCoroutineScope()
    // v2.4 rework: Snackbar de favoritos (preferido sobre Toast)
    val favSnackbarState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(searchQuery) { vm.savedSearchQuery = searchQuery }
    LaunchedEffect(sortOrder) {
        vm.savedSortOrder = sortOrder
        prefs.edit().putString("sort_order", sortOrder.name).apply()
        listScope.launch { listState.animateScrollToItem(0) }
    }
    LaunchedEffect(activeTab) { listScope.launch { listState.animateScrollToItem(0) } }
    // v2.4 rework: al activar/desactivar el filtro de favoritos, volver arriba
    LaunchedEffect(vm.showFavoritesOnly) { listScope.launch { listState.animateScrollToItem(0) } }

    // Compute per-shelf book lists (filtered + sorted) — with fuzzy/accent-insensitive search
    val searchFiltered = if (searchQuery.isBlank()) booksAll
        else booksAll.filter {
            fuzzyMatch(searchQuery, it.title) || fuzzyMatch(searchQuery, it.author)
        }
    // v2.4 rework: el filtro de favoritos se aplica a TODAS las pestañas
    val allFiltered = if (vm.showFavoritesOnly) searchFiltered.filter { it.isFavorite } else searchFiltered
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
        // P-021: toast de celebración al terminar Y al saltar el tutorial
        val tutCtx = LocalContext.current
        val tutDoneMsg = stringResource(R.string.tut_done_toast)
        TutorialSlideshow(
            theme = theme,
            onComplete = { vm.completeTutorial(prefs); android.widget.Toast.makeText(tutCtx, tutDoneMsg, android.widget.Toast.LENGTH_SHORT).show() },
            onSkip    = { vm.completeTutorial(prefs); android.widget.Toast.makeText(tutCtx, tutDoneMsg, android.widget.Toast.LENGTH_SHORT).show() }
        )
        return
    }

    // ── D-002/T1: bottom sheet de inicio rápido — libros Leyendo/Releyendo con ▶ ──
    if (showQuickStartSheet) {
        val qsBooks = booksAll.filter {
            it.status == BookStatus.READING || it.status == BookStatus.REREADING || it.isRereading
        }
        ModalBottomSheet(
            onDismissRequest = { showQuickStartSheet = false },
            containerColor = theme.bgMid,
            contentColor = theme.textMain
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text(
                    stringResource(R.string.quickstart_title),
                    color = theme.textMain, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (qsBooks.isEmpty()) {
                    Text(
                        stringResource(R.string.quickstart_empty),
                        color = theme.textMuted, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                qsBooks.forEach { b ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = theme.surface,
                        border = BorderStroke(1.dp, theme.border),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            BookCover(b.coverUrl, b.title, size = 34, isbnFallback = b.isbn)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(b.title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    statusLabel(if (b.status == BookStatus.REREADING || b.isRereading) BookStatus.REREADING else BookStatus.READING),
                                    color = statusColor(if (b.status == BookStatus.REREADING || b.isRereading) BookStatus.REREADING else BookStatus.READING),
                                    fontSize = 11.sp
                                )
                            }
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Green)
                                    .clickable {
                                        showQuickStartSheet = false
                                        TimerQuickStart.pendingBookId = b.id
                                        onDetail(b.id)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val gridColumns = if (maxWidth >= 840.dp) 2 else 1
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
            // ── App title + barra de acciones (D-002 Fase 4): tema · ⏱️ · ⚙️ · ＋ ──
            // El hamburger desaparece: el historial vive en el rail (📜). El ＋ sube
            // aquí desde la fila de iconos — las tarjetas quedan despejadas.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 28.dp, bottom = 2.dp)
            ) {
                Text(
                    stringResource(R.string.txt_4d8b0a6f),
                    color = theme.textMain,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                // Feedback 13-07 (7): el selector de tema vuelve a la barra de acciones,
                // a la IZQUIERDA del crono (pegado al título no convencía)
                var showThemeMenu by remember { mutableStateOf(false) }
                val barContext = LocalContext.current
                Box {
                    IconButton(onClick = { showThemeMenu = true }, modifier = Modifier.size(34.dp)) {
                        // Feedback 2.6: Aurora recupera su icono PNG (ic_theme_aurora) en vez del emoji
                        if (vm.themeMode == ThemeMode.AURORA) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_theme_aurora),
                                contentDescription = stringResource(R.string.theme_aurora),
                                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
                            )
                        } else {
                            Text(
                                when (vm.themeMode) {
                                    ThemeMode.LIGHT  -> "☀️"
                                    ThemeMode.DARK   -> "🌙"
                                    ThemeMode.AMOLED -> "⬛"
                                    else             -> "🌌"
                                },
                                fontSize = 15.sp
                            )
                        }
                    }
                    DropdownMenu(expanded = showThemeMenu, onDismissRequest = { showThemeMenu = false }) {
                        listOf(
                            ThemeMode.LIGHT  to stringResource(R.string.theme_light),
                            ThemeMode.DARK   to stringResource(R.string.theme_dark),
                            ThemeMode.AURORA to stringResource(R.string.theme_aurora),
                            ThemeMode.AMOLED to stringResource(R.string.theme_oled)
                        ).forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Feedback 2.6: icono PNG para Aurora también en el desplegable
                                        if (mode == ThemeMode.AURORA) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_theme_aurora),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(label, color = if (vm.themeMode == mode) Accent else theme.textMain, fontWeight = if (vm.themeMode == mode) FontWeight.Bold else FontWeight.Normal)
                                    }
                                },
                                onClick = { vm.setThemeMode(mode, prefs, barContext); showThemeMenu = false }
                            )
                        }
                    }
                }
                // ⏱️ crono desde el home (T1 elegida por Víctor): selector Leyendo/Releyendo
                IconButton(onClick = { showQuickStartSheet = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Timer, contentDescription = "Timer", tint = Accent, modifier = Modifier.size(19.dp))
                }
                // Feedback 13-07 (4): acceso rápido a Importar/Exportar backups
                IconButton(onClick = onImportExport, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ImportExport, contentDescription = "Import/Export", tint = Accent, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Accent, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) { Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
            // ── Contador + eslogan (D-002 v3: se conservan bajo el título) ────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text("📖", fontSize = 13.sp)
                Text(
                    stringResource(R.string.label_books_total, booksAll.size) + " · " + stringResource(R.string.txt_e860710c),
                    color = theme.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // ── D-002 (Fase 4): rail a la izquierda + contenido a la derecha ────────
        Row(Modifier.weight(1f)) {
            HomeRail(
                theme = theme,
                prefs = prefs,
                onHistory = { historyOpen = !historyOpen },
                // (la línea separadora del rail se pinta a su derecha, ver Box de abajo)
                // Feedback 13-07: 📚 = "volver a la biblioteca": cierra el panel y sube arriba
                onLibrary = {
                    historyOpen = false
                    listScope.launch { listState.animateScrollToItem(0) }
                },
                onStats = { historyOpen = false; onStats() },
                onChallenges = { historyOpen = false; onChallenges() },
                onBingo = { historyOpen = false; onEasterEgg() },
                onWrapped = { historyOpen = false; onWrappedHistory() },
                onHistorySwipe = { open -> historyOpen = open }
            )
            // Feedback 13-07 (4): separación visual del rail — línea fina vertical
            // con el color de borde del tema
            Box(Modifier.fillMaxHeight().width(1.dp).background(theme.border))
            Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
            // Feedback 13-07: la línea del rail queda ENTRE MEDIAS — mismo aire a ambos
            // lados (el rail ya deja ~3dp; el contenido deja 10dp aquí)
            Column(Modifier.padding(end = 16.dp, start = 10.dp)) {

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
                        // Feedback 13-07: la búsqueda online (antes 🔍 del rail) vive aquí,
                        // junto a la ordenación — buscar libros nuevos en las APIs
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Default.TravelExplore, contentDescription = stringResource(R.string.txt_113f7428), tint = Accent, modifier = Modifier.size(20.dp))
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
                                // v2.4 rework: filtro persistente "Ver solo favoritos"
                                HorizontalDivider(color = theme.border)
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                stringResource(R.string.show_favorites_only),
                                                color = if (vm.showFavoritesOnly) FavoriteRed else theme.textMain,
                                                fontWeight = if (vm.showFavoritesOnly) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Switch(
                                                checked = vm.showFavoritesOnly,
                                                onCheckedChange = { vm.setShowFavoritesOnly(it, prefs) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = FavoriteRed,
                                                    uncheckedTrackColor = theme.border
                                                )
                                            )
                                        }
                                    },
                                    onClick = { vm.setShowFavoritesOnly(!vm.showFavoritesOnly, prefs) }
                                )
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
                                    // QA 12-07 r2: número en textMain — con textDim apenas
                                    // se leía sobre la pill en AMOLED y Aurora.
                                    Text(
                                        "$count",
                                        fontSize = 8.sp,
                                        color = if (selected) Color.White else theme.textMain,
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

        if (booksAll.isEmpty()) {
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                state = listState,
                // D-002: alineado con la barra de búsqueda (línea del rail entre medias)
                modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeBooks.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (vm.showFavoritesOnly) "❤️" else statusEmoji(activeStatus), fontSize = 44.sp)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (vm.showFavoritesOnly) stringResource(R.string.empty_shelf_favorites)
                                    else emptyShelfHint(activeStatus),
                                    color = theme.textDim,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                // Feedback 13-07 (4): la lupa de la barra no comunica "buscar
                                // libros nuevos" — CTA contextual: si la búsqueda local no
                                // encuentra nada, ofrecer buscarlo en internet AQUÍ, justo
                                // cuando el usuario lo necesita (sin depender del tutorial)
                                if (searchQuery.isNotBlank() && !vm.showFavoritesOnly) {
                                    Spacer(Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            listMainRef?.pendingScannedIsbn?.value = searchQuery
                                            onNavigateToBookSearch()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.TravelExplore, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.search_online_cta, searchQuery), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }
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
                        // v2.4 rework: swipe izquierda/derecha alterna favorito
                        val favAddedMsg   = stringResource(R.string.favorite_added, book.title)
                        val favRemovedMsg = stringResource(R.string.favorite_removed, book.title)
                        FavoriteSwipe(
                            isFavorite = book.isFavorite,
                            onToggleFavorite = {
                                val nowFav = vm.toggleFavorite(book.id, prefs)
                                listScope.launch {
                                    favSnackbarState.currentSnackbarData?.dismiss()
                                    favSnackbarState.showSnackbar(
                                        message = if (nowFav) favAddedMsg else favRemovedMsg,
                                        duration = androidx.compose.material3.SnackbarDuration.Short
                                    )
                                }
                            }
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
                        } // FavoriteSwipe
                        } // AnimatedVisibility
                        Spacer(Modifier.height(10.dp))
                    }

                }
            }
        }
        } // right column (D-002)
            // Feedback 13-07 (3): gesto de ABRIR el historial deslizando desde el borde
            // junto al rail (franja estrecha para no chocar con el swipe de favoritos).
            // Feedback 13-07 (8): franja ampliada a 28dp; el gesto principal ahora vive
            // en el propio rail (ver HomeRail.onHistorySwipe)
            if (!historyOpen) {
                val openAcc = remember { mutableStateOf(0f) }
                Box(
                    Modifier.align(Alignment.CenterStart).width(28.dp).fillMaxHeight()
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                openAcc.value += delta
                                if (openAcc.value > 60f) { historyOpen = true; openAcc.value = 0f }
                            },
                            onDragStarted = { openAcc.value = 0f }
                        )
                )
            }
            // Feedback 13-07: scrim SOLO sobre el contenido (el rail queda libre) + panel
            // del historial deslizante encajado contra el rail
            if (historyOpen) {
                Box(Modifier.fillMaxSize().background(Color(0x88000000)).clickable { historyOpen = false })
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = historyOpen,
                enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }),
                exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it })
            ) {
                // Feedback 13-07 (3): fondo con el MISMO degradado azulado de la app (nada
                // de huecos negros entre cards) + gesto de CERRAR deslizando a la izquierda
                val closeAcc = remember { mutableStateOf(0f) }
                Box(
                    Modifier
                        .fillMaxHeight().widthIn(max = 400.dp).fillMaxWidth(0.94f)
                        .background(Brush.verticalGradient(listOf(theme.bgDark, theme.bgMid, theme.bgDeep)))
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                closeAcc.value += delta
                                if (closeAcc.value < -60f) { historyOpen = false; closeAcc.value = 0f }
                            },
                            onDragStarted = { closeAcc.value = 0f }
                        )
                ) {
                    SessionHistoryScreen(
                        vm = vm,
                        theme = theme,
                        onClose = { historyOpen = false },
                        onDetail = { id -> historyOpen = false; onDetail(id) }
                    )
                }
            }
        } // Box contenido + panel (D-002)
        } // Row rail + contenido (D-002)
    }
    // v2.4 rework: host del Snackbar de favoritos, superpuesto al contenido
    androidx.compose.material3.SnackbarHost(
        hostState = favSnackbarState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
    )
    } // Box
}

@Composable
fun emptyShelfHint(status: BookStatus) = when (status) {
    BookStatus.READING   -> stringResource(R.string.empty_shelf_reading)
    BookStatus.REREADING -> stringResource(R.string.empty_shelf_rereading)
    BookStatus.FINISHED  -> stringResource(R.string.empty_shelf_finished)
    BookStatus.PENDING   -> stringResource(R.string.empty_shelf_pending)
    BookStatus.DROPPED   -> stringResource(R.string.empty_shelf_dropped)
}

// Fase 1.3: FullBackup, export/import JSON+CSV y portadas embebidas viven en repository/BackupRepository.kt

// ── ImportExportScreen ────────────────────────────────────────────────────────
@Composable
fun ImportExportScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
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
                Text(if (books.size == 1) stringResource(R.string.library_books_count, books.size) else stringResource(R.string.library_books_count_plural, books.size), color = theme.textMuted, fontSize = 13.sp)
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
                        val reading  = books.count { it.status == BookStatus.READING || it.status == BookStatus.REREADING }
                        val finished = books.count { it.status == BookStatus.FINISHED }
                        val pending  = books.count { it.status == BookStatus.PENDING }
                        ExportStatCell("$finished", stringResource(R.string.export_stat_finished), Green)
                        ExportStatCell("$reading",  stringResource(R.string.export_stat_reading),  Amber)
                        ExportStatCell("$pending",  stringResource(R.string.export_stat_pending),  theme.textDim)
                        ExportStatCell("${books.size}", stringResource(R.string.export_stat_total), theme.textMain)
                    }
                }

                Text(
                    stringResource(R.string.txt_8ccc576b),
                    color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp)
                )

                Button(
                    onClick = {
                        if (books.isEmpty()) { exportMsg = context.getString(R.string.msg_no_books_export); return@Button }
                        isExporting = true; exportMsg = null; importMsg = null
                        scope.launch {
                            val uri = exportBooksToCSV(context, books)
                            isExporting = false
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Mi biblioteca Lecturameter")
                                    putExtra(Intent.EXTRA_TEXT, "Aquí tienes mi biblioteca exportada desde Lecturameter (${books.size} libros).")
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
                    enabled = !isExporting && books.isNotEmpty()
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
                        if (books.isEmpty()) { backupMsg = context.getString(R.string.msg_no_data_export); return@Button }
                        isBackingUp = true; backupMsg = null
                        scope.launch {
                            val uri = withContext(kotlinx.coroutines.Dispatchers.IO) { exportFullBackup(context, vm) }
                            isBackingUp = false
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Backup Lecturameter")
                                    putExtra(Intent.EXTRA_TEXT, "Copia de seguridad completa de Lecturameter (${books.size} libros, ${sessions.size} sesiones).")
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
                    enabled = !isBackingUp && books.isNotEmpty()
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
                        if (books.isEmpty()) { backupMsg = context.getString(R.string.msg_no_data_export); return@OutlinedButton }
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
                    enabled = !isLocalAutoBackingUp && books.isNotEmpty()
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
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    // v2.6 (Wrapped r1): años cerrados con snapshot guardado → usar el snapshot
    // (formato/datos exactos de esa edición). Año en curso O año dentro de su
    // ventana wrapped (1–26 ene el año anterior sigue "vivo") → cálculo fresco,
    // para que el auto-guardado de la ventana siga refrescando datos.
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    // D-004: wrappedHistory es StateFlow; se colecciona en la raíz de la pantalla
    val wrappedHistory by vm.wrappedHistory.collectAsState()
    val wrapped = remember(year, books, wrappedHistory) {
        val inOwnWindow = isInWrappedWindow() && wrappedWindowYear() == year
        if (year < currentYear && !inOwnWindow) vm.wrappedForYear(year) ?: vm.computeWrapped(year)
        else vm.computeWrapped(year)
    }
    // v2.6: favoritos CONGELADOS del año (permanentes; ver favoritesForWrapped)
    val favBooks = remember(wrapped, books) {
        if (wrapped != null) vm.favoritesForWrapped(year, prefs) else emptyList()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { if (wrapped != null) 10 else 1 }
    // v2.5: coordenadas del pager para recortar screenshot (excluir barra superior)
    var pagerBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    // Auto-guardar al abrir durante la ventana
    LaunchedEffect(wrapped) {
        if (wrapped != null && isInWrappedWindow() && wrappedWindowYear() == year) {
            vm.saveWrappedForYear(wrapped, prefs)
        }
    }

    // v2.5: share captura real de pantalla (PixelCopy) — sin menús
    fun shareScreenshot() {
        if (sharing) return
        sharing = true
        scope.launch {
            try {
                val activity = context as? android.app.Activity ?: throw Exception("No Activity")
                val window = activity.window
                val rootView = window.decorView
                val fullBitmap = android.graphics.Bitmap.createBitmap(rootView.width, rootView.height, android.graphics.Bitmap.Config.ARGB_8888)
                val captured = kotlinx.coroutines.suspendCancellableCoroutine<android.graphics.Bitmap> { cont ->
                    android.view.PixelCopy.request(window, fullBitmap, { result ->
                        if (result == android.view.PixelCopy.SUCCESS) cont.resume(fullBitmap) {}
                        else cont.resumeWithException(Exception("PixelCopy error $result"))
                    }, android.os.Handler(android.os.Looper.getMainLooper()))
                }
                val file = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val cropped = pagerBounds?.let { b ->
                        val x = b.left.coerceIn(0, captured.width - 1)
                        val y = b.top.coerceIn(0, captured.height - 1)
                        val w = b.width().coerceAtMost(captured.width - x)
                        val h = b.height().coerceAtMost(captured.height - y)
                        if (w > 0 && h > 0) android.graphics.Bitmap.createBitmap(captured, x, y, w, h) else captured
                    } ?: captured
                    val f = java.io.File(context.cacheDir, "wrapped_${year}_s${pagerState.currentPage}.png")
                    java.io.FileOutputStream(f).use { out -> cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
                    if (cropped !== captured) cropped.recycle()
                    captured.recycle()
                    f
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.wrapped_share_text, year))
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.wcard_dialog_title)))
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, context.getString(R.string.msg_share_error, e.message), android.widget.Toast.LENGTH_SHORT).show()
            } finally { sharing = false }
        }
    }

    // v2.4: fondo con glow degradado (funciona sobre tema claro y oscuro)
    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Accent.copy(alpha = 0.16f), Color.Transparent, Accent2.copy(alpha = 0.12f)))
    )) {
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
                        repeat(10) { i ->
                            Box(Modifier.size(if (i == pagerState.currentPage) 8.dp else 5.dp)
                                .clip(CircleShape)
                                .background(if (i == pagerState.currentPage) Accent else theme.border))
                        }
                    }
                }
            }
            if (wrapped != null) {
                // v2.6: guardar simulación en historial (solo si este año aún no está guardado)
                if (vm.wrappedForYear(year) == null) {
                    IconButton(onClick = {
                        vm.saveWrappedForYear(wrapped, prefs)
                        android.widget.Toast.makeText(context,
                            context.getString(R.string.wrapped_sim_saved, year),
                            android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = stringResource(R.string.wrapped_sim_save), tint = Amber)
                    }
                }
                // v2.4 rework: el slide de favoritos no tiene tarjeta share (renderer 0-6)
                IconButton(onClick = { shareScreenshot() }, enabled = !sharing) {
                    Icon(if (sharing) Icons.Default.Refresh else Icons.Default.Share, null,
                        tint = if (sharing) theme.textDim else Accent)
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
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    pagerBounds = android.graphics.Rect(
                        pos.x.toInt(), pos.y.toInt(),
                        (pos.x + coords.size.width).toInt(),
                        (pos.y + coords.size.height).toInt()
                    )
                }
        ) { page ->
            val sm = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)

            when (page) {

                // ── SLIDE 0: RESUMEN ──────────────────────────────────────────
                0 -> Column(sm) {
                    // Fondo temático del slide (superpuesto al glow global)
                    Spacer(Modifier.height(8.dp))
                    // Año protagonista con gradiente
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF1E1B4B)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                            Text("${wrapped.year}", fontSize = 80.sp, fontWeight = FontWeight.Black,
                                style = androidx.compose.ui.text.TextStyle(
                                    brush = Brush.horizontalGradient(listOf(Color(0xFF818CF8), Color(0xFF22D3EE)))
                                ))
                            Text(stringResource(R.string.wcard_subtitle), color = Color(0xFFC7D2FE), fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // 2 stats enormes
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WrappedBigCard(wrapped.totalPages.toLocaleString(), stringResource(R.string.wcard_pages), Accent,
                            Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF1E1B4B))), Modifier.weight(1f))
                        WrappedBigCard("${wrapped.totalBooks}", stringResource(R.string.wcard_books), Accent2,
                            Brush.linearGradient(listOf(Color(0xFF2D1B69), Color(0xFF1E1B4B))), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    // 3 mini stats
                    val hTot = wrapped.totalMinutes / 60; val mTot = wrapped.totalMinutes % 60
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WrappedMiniCard(if (hTot > 0) "${hTot}h" else "${wrapped.totalMinutes}m", stringResource(R.string.wcard_hours), Amber, Modifier.weight(1f))
                        WrappedMiniCard("${wrapped.longestStreakDays}d", stringResource(R.string.wcard_streak), Green, Modifier.weight(1f))
                        WrappedMiniCard("${wrapped.totalSessions}", stringResource(R.string.wcard_sessions), Sky, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    // Autor + género
                    if (wrapped.favoriteAuthor.isNotBlank()) {
                        WrappedFavRow("", stringResource(R.string.wcard_author_year), wrapped.favoriteAuthor,
                            "${wrapped.favoriteAuthorBooks} libros", Accent2, theme)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (wrapped.favoriteGenre.isNotBlank()) {
                        WrappedFavRow("", stringResource(R.string.wcard_genre_year), displayGenre(wrapped.favoriteGenre),
                            "${wrapped.favoriteGenreBooks} libros", Accent, theme)
                    }
                    if (wrapped.longestStreakDays > 0) {
                        Spacer(Modifier.height(12.dp))
                        Surface(shape = RoundedCornerShape(16.dp), color = Red.copy(0.12f),
                            border = BorderStroke(1.dp, Red.copy(0.3f)), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🔥", fontSize = 28.sp); Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(stringResource(R.string.txt_362b636c), color = Red.copy(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                    Text("${wrapped.longestStreakDays} ${stringResource(R.string.word_day)}s ${stringResource(R.string.wrapped_streak_suffix)}", color = theme.textMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 1: TIEMPO ───────────────────────────────────────────
                1 -> Column(sm) {
                    // Feedback WhatsApp 10-07: header violeta profundo (antes marrón/ámbar v2.6,
                    // "cambiar el marrón naranja ese por un color más bonito") — on-brand con
                    // Accent2 y distinto del Sky de las tarjetas inferiores.
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF150B33)))),
                        contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // v2.4 rework: eliminado emoji decorativo de cabecera
                            val hW = wrapped.totalMinutes / 60; val mW = wrapped.totalMinutes % 60
                            Text(if (hW > 0) "${hW}h ${mW}m" else "${mW}m",
                                fontSize = 64.sp, fontWeight = FontWeight.Black,
                                style = androidx.compose.ui.text.TextStyle(
                                    brush = Brush.horizontalGradient(listOf(Color(0xFFA78BFA), Color(0xFFDDD6FE)))
                                ))
                            Text(stringResource(R.string.wcard_hours), color = Color(0xFFC4B5FD), fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WrappedBigCard("${wrapped.totalSessions}", stringResource(R.string.wcard_sessions), Sky,
                            Brush.linearGradient(listOf(Color(0xFF0C4A6E), Color(0xFF0F172A))), Modifier.weight(1f))
                        if (wrapped.maxSessionPages > 0)
                            WrappedBigCard("${wrapped.maxSessionPages}", stringResource(R.string.wrapped_record_session), Sky,
                                Brush.linearGradient(listOf(Color(0xFF0C4A6E), Color(0xFF0F172A))), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    // Top libros por tiempo
                    if (wrapped.longestBooksTop3.isNotEmpty()) {
                        Text(stringResource(R.string.txt_1db69449), color = Sky, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(8.dp))
                        val maxM = wrapped.longestBooksTop3.maxOf { it.second }
                        wrapped.longestBooksTop3.forEachIndexed { i, (title, mins) ->
                            val hB = mins / 60; val mB = mins % 60
                            Surface(shape = RoundedCornerShape(14.dp), color = Sky.copy(0.08f),
                                border = BorderStroke(1.dp, Sky.copy(0.2f)), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(wrappedMedal(i), fontSize = 20.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(if (hB > 0) "${hB}h ${mB}m" else "${mB}m", color = Sky, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressBar(mins.toFloat() / maxM, Sky)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (wrapped.mostReadDay.isNotBlank() && wrapped.mostReadDayPages > 0) {
                        Spacer(Modifier.height(4.dp))
                        Surface(shape = RoundedCornerShape(14.dp), color = Accent.copy(0.08f),
                            border = BorderStroke(1.dp, Accent.copy(0.2f)), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📅", fontSize = 24.sp); Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(stringResource(R.string.wrapped_most_read_day, fmtDate(wrapped.mostReadDay), wrapped.mostReadDayPages),
                                        color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 2: TOPS ─────────────────────────────────────────────
                2 -> Column(sm) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF1E1B4B)))),
                        contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // v2.4 rework: eliminado emoji decorativo de cabecera
                            Text(stringResource(R.string.txt_a6f46d56), color = Color(0xFFC4B5FD),
                                fontSize = 32.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (wrapped.favoriteAuthor.isNotBlank())
                            WrappedBigCard(wrapped.favoriteAuthor, "${wrapped.favoriteAuthorBooks} libros", Accent2,
                                Brush.linearGradient(listOf(Color(0xFF2D1B69), Color(0xFF1E1B4B))), Modifier.weight(1f), maxLines = 2)
                        if (wrapped.favoriteGenre.isNotBlank())
                            WrappedBigCard(displayGenre(wrapped.favoriteGenre), "${wrapped.favoriteGenreBooks} libros", Accent,
                                Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF312E81))), Modifier.weight(1f), maxLines = 2)
                    }
                    Spacer(Modifier.height(12.dp))
                    val medals = listOf("🥇","🥈","🥉")
                    if (wrapped.topAuthorsTop3.isNotEmpty()) {
                        Text(stringResource(R.string.wcard_authors), color = Accent2, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(6.dp))
                        wrapped.topAuthorsTop3.forEachIndexed { i, (name, n) ->
                            WrappedTop3Row(medals[i], name, "$n libros", i == 0, Accent2, theme)
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (wrapped.topGenresTop3.isNotEmpty()) {
                        Text(stringResource(R.string.wcard_genres), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(6.dp))
                        wrapped.topGenresTop3.forEachIndexed { i, (name, n) ->
                            WrappedTop3Row(medals[i], displayGenre(name), "$n libros", i == 0, Accent, theme)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 3: MEJOR Y MÁS RÁPIDO ──────────────────────────────
                3 -> Column(sm) {
                    val top3 = wrapped.bestRatedTop3.ifEmpty {
                        if (wrapped.bestRatedTitle.isNotBlank()) listOf(Triple(wrapped.bestRatedTitle, wrapped.bestRatedScore, "")) else emptyList()
                    }
                    if (top3.isNotEmpty()) {
                        // Hero: libro nº1 — v2.6: card compacta (padding/portada/número reducidos)
                        // + degradado dorado visible, simétrico al verde de Fastest Book
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF92400E), Color(0xFF1A1200)))),
                            contentAlignment = Alignment.Center) {
                            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                val bestBook = books.firstOrNull { it.title == top3[0].first }
                                if (bestBook != null) {
                                    BookCover(bestBook.coverUrl, bestBook.title, size = 64, isbnFallback = bestBook.isbn)
                                    Spacer(Modifier.height(6.dp))
                                }
                                Text("${top3[0].second}/10", fontSize = 30.sp, fontWeight = FontWeight.Black,
                                    style = androidx.compose.ui.text.TextStyle(
                                        brush = Brush.horizontalGradient(listOf(Gold, Color(0xFFFDE68A)))
                                    ))
                                Text(top3[0].first, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                Text(stringResource(R.string.wrapped_mejor_puntuado), color = Gold.copy(0.7f), fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        top3.drop(1).forEachIndexed { i, (title, score, _) ->
                            val medal = if (i == 0) "🥈" else "🥉"
                            Surface(shape = RoundedCornerShape(14.dp), color = Gold.copy(0.08f),
                                border = BorderStroke(1.dp, Gold.copy(0.25f)), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(medal, fontSize = 24.sp); Spacer(Modifier.width(10.dp))
                                    Text(title, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$score/10", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    // v2.5: top 3 libros más rápidos (antes solo 1)
                    val fastTop3 = wrapped.fastestBooksTop3.ifEmpty {
                        if (wrapped.fastestBookTitle.isNotBlank()) listOf(Triple(wrapped.fastestBookTitle, wrapped.fastestBookPpd, wrapped.fastestBookPages)) else emptyList()
                    }
                    if (fastTop3.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        // Hero: libro más rápido — v2.6: card compacta (simétrica a Best Rated)
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF064E3B), Color(0xFF0A2818)))),
                            contentAlignment = Alignment.Center) {
                            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                val fastBook = books.firstOrNull { it.title == fastTop3[0].first }
                                if (fastBook != null) {
                                    BookCover(fastBook.coverUrl, fastBook.title, size = 60, isbnFallback = fastBook.isbn)
                                    Spacer(Modifier.height(6.dp))
                                }
                                Text("${String.format("%.1f", fastTop3[0].second)} p/d", fontSize = 28.sp, fontWeight = FontWeight.Black,
                                    style = androidx.compose.ui.text.TextStyle(
                                        brush = Brush.horizontalGradient(listOf(Green, Color(0xFF22D3EE)))
                                    ))
                                Text(fastTop3[0].first, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.wrapped_libro_mas_rapido), color = Green.copy(0.7f), fontSize = 11.sp)
                            }
                        }
                        // 2º y 3º más rápidos
                        if (fastTop3.size > 1) {
                            Spacer(Modifier.height(10.dp))
                            fastTop3.drop(1).forEachIndexed { i, (title, ppd, _) ->
                                val medal = if (i == 0) "🥈" else "🥉"
                                Surface(shape = RoundedCornerShape(14.dp), color = Green.copy(0.08f),
                                    border = BorderStroke(1.dp, Green.copy(0.25f)), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(medal, fontSize = 24.sp); Spacer(Modifier.width(10.dp))
                                        Text(title, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${String.format("%.1f", ppd)} p/d", color = Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    // Feedback 2.6: la card de libros abandonados se movió a la slide de
                    // cierre — esta slide es la de mejores/más rápidos.
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 4: GRÁFICA ──────────────────────────────────────────
                4 -> Column(sm) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43)))),
                        contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // v2.4 rework: eliminado emoji decorativo de cabecera
                            Text(stringResource(R.string.txt_bd81d36d), color = Color(0xFF94A3B8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // Barras mensuales rediseñadas
                    if (wrapped.pagesPerMonth.sum() > 0) {
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF0F2027), border = BorderStroke(1.dp, Color(0x226366F1)),
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                val maxP = wrapped.pagesPerMonth.max().coerceAtLeast(1)
                                // v2.6: escala min-max entre meses CON actividad. Antes ratio lineal
                                // desde 0: 450 vs 415 páginas → barras 100% vs 92%, indistinguibles.
                                // Feedback 2.6: el ancla del 25% exageraba (415 parecía 1/3 de 450);
                                // el mínimo ancla ahora en 55% — se distingue sin distorsionar.
                                val nonZero = wrapped.pagesPerMonth.filter { it > 0 }
                                val minP = (nonZero.minOrNull() ?: 0)
                                val range = (maxP - minP).coerceAtLeast(1)
                                val months = listOf("E","F","M","A","M","J","J","A","S","O","N","D")
                                Row(Modifier.fillMaxWidth().height(200.dp), verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    wrapped.pagesPerMonth.forEachIndexed { i, p ->
                                        val ratio = when {
                                            p <= 0 -> 0f
                                            nonZero.size <= 1 || maxP == minP -> 1f
                                            else -> 0.55f + 0.45f * (p - minP).toFloat() / range
                                        }
                                        val isMax = p == maxP
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Bottom) {
                                            if (p > 0)
                                                Text(if (p >= 1000) "${p/1000}k" else "$p", color = if (isMax) Color(0xFF22D3EE) else Accent2, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(2.dp))
                                            Box(Modifier.fillMaxWidth()
                                                .height((200.dp * ratio.coerceAtLeast(if (p > 0) 0.03f else 0f)))
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .background(if (isMax)
                                                    Brush.verticalGradient(listOf(Color(0xFF22D3EE), Color(0xFF0EA5E9)))
                                                else Brush.verticalGradient(listOf(Accent2, Accent2.copy(0.5f)))))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    months.forEach { m -> Text(m, color = Color(0xFF94A3B8), fontSize = 9.sp,
                                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
                                }
                                val bestIdx = wrapped.pagesPerMonth.indexOf(maxP)
                                val monthNames = LocalContext.current.resources.getStringArray(R.array.month_names_full).toList()
                                if (bestIdx >= 0) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(stringResource(R.string.wrapped_best_month, monthNames.getOrElse(bestIdx) { "" }, maxP),
                                        color = Color(0xFF22D3EE), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // Donut géneros
                    if (wrapped.genreCountsTop6.isNotEmpty()) {
                        val gColors = listOf(Accent, Green, Sky, Amber, Red, Color(0xFF8B5CF6))
                        val totalG = wrapped.genreCountsTop6.sumOf { it.second }
                        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF1E1B4B),
                            border = BorderStroke(1.dp, Color(0x336366F1)), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                DonutChart(wrapped.genreCountsTop6.map { it.second }, gColors, Modifier.size(100.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    wrapped.genreCountsTop6.forEachIndexed { i, (g, n) ->
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(gColors[i % gColors.size]))
                                            Text("${displayGenre(g).take(16)} · $n (${if (totalG > 0) n * 100 / totalG else 0}%)",
                                                color = theme.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 5: TU MEJOR DÍA DE CADA MES (v2.6) ─────────────────
                5 -> Column(sm) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF0F172A)))),
                        contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.wrapped_bestday_title), color = Color(0xFFA5B4FC),
                                fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (wrapped.bestDayPerMonth.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📅", fontSize = 44.sp); Spacer(Modifier.height(10.dp))
                                Text(stringResource(R.string.wrapped_bestday_empty), color = theme.textDim,
                                    fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        val monthNames = LocalContext.current.resources.getStringArray(R.array.month_names_full).toList()
                        // Feedback 2.6: todos los días son clicables — el desglose de abajo
                        // muestra el día seleccionado (por defecto, el mejor del año).
                        var selectedDay by remember(wrapped) { mutableStateOf(wrapped.mostReadDay) }
                        wrapped.bestDayPerMonth.forEach { (m, date, pages) ->
                            val isBest = date == wrapped.mostReadDay
                            val isSelected = date == selectedDay
                            // Feedback 2.7: el mes seleccionado destaca más (fondo/borde más
                            // intensos y texto en cian, tamaño +1sp); el resto de filas queda
                            // igual de compacto para que los 12 meses sigan siendo legibles.
                            Surface(
                                onClick = { selectedDay = date },
                                shape = RoundedCornerShape(14.dp),
                                color = when {
                                    isSelected -> Color(0xFF22D3EE).copy(0.22f)
                                    isBest     -> Color(0xFF22D3EE).copy(0.08f)
                                    else       -> Accent.copy(0.06f)
                                },
                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, when {
                                    isSelected -> Color(0xFF22D3EE).copy(0.9f)
                                    isBest     -> Color(0xFF22D3EE).copy(0.35f)
                                    else       -> Accent.copy(0.18f)
                                }),
                                modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(monthNames.getOrElse(m) { "" },
                                        color = if (isSelected) Color(0xFF22D3EE) else theme.textMain,
                                        fontSize = if (isSelected) 14.sp else 13.sp,
                                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(fmtDate(date), color = if (isSelected) theme.textMain else theme.textMuted, fontSize = 12.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(stringResource(R.string.wrapped_fav_pages, pages),
                                        color = if (isBest || isSelected) Color(0xFF22D3EE) else Accent2,
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        // Desglose del día seleccionado (sesiones en vivo; si el año es un
                        // snapshot sin sesiones, cae a los valores guardados del mejor día)
                        if (selectedDay.isNotBlank()) {
                            val daySessions = sessions.filter { it.date == selectedDay }
                            val rowPages = wrapped.bestDayPerMonth.firstOrNull { it.second == selectedDay }?.third ?: 0
                            val isGlobalBest = selectedDay == wrapped.mostReadDay
                            val pagesSel = when {
                                daySessions.isNotEmpty() -> daySessions.sumOf { it.pages }
                                isGlobalBest             -> wrapped.mostReadDayPages
                                else                     -> rowPages
                            }
                            val sessionsSel = if (daySessions.isNotEmpty()) daySessions.size
                                else if (isGlobalBest) wrapped.bestDaySessions else 0
                            val booksSel = if (daySessions.isNotEmpty()) daySessions.map { it.bookId }.distinct().size
                                else if (isGlobalBest) wrapped.bestDayBooks else 0
                            val ppmSel = if (daySessions.isNotEmpty()) {
                                val mins = daySessions.mapNotNull { it.minutes }.sum()
                                if (mins > 0) pagesSel.toDouble() / mins else 0.0
                            } else if (isGlobalBest) wrapped.bestDayPagesPerMin else 0.0
                            Spacer(Modifier.height(8.dp))
                            // Feedback 2.7: dorado (antes cian, idéntico a la pill de págs —
                            // combina con la paleta Wrapped pero ya no se confunde con ella)
                            Text(stringResource(R.string.wrapped_bestday_breakdown, fmtDate(selectedDay)),
                                color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                WrappedMiniCard("$pagesSel", stringResource(R.string.wcard_pages), Color(0xFF22D3EE), Modifier.weight(1f))
                                WrappedMiniCard(if (sessionsSel > 0) "$sessionsSel" else "—", stringResource(R.string.wcard_sessions), Sky, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                WrappedMiniCard(if (booksSel > 0) "$booksSel" else "—", stringResource(R.string.wcard_books), Accent2, Modifier.weight(1f))
                                WrappedMiniCard(
                                    if (ppmSel > 0) String.format("%.2f", ppmSel) else "—",
                                    stringResource(R.string.wrapped_ppm_label), Green, Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 6: FRANJA HORARIA FAVORITA (v2.6) ──────────────────
                6 -> Column(sm) {
                    val slots = wrapped.pagesPerTimeSlot
                    val slotLabels = listOf("00–03h","03–06h","06–09h","09–12h","12–15h","15–18h","18–21h","21–24h")
                    val totalSlot = slots.sum()
                    if (totalSlot <= 0) {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0F172A)))),
                            contentAlignment = Alignment.Center) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.wrapped_timeslot_title), color = Color(0xFFA5B4FC),
                                    fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🕐", fontSize = 44.sp); Spacer(Modifier.height(10.dp))
                                Text(stringResource(R.string.wrapped_timeslot_empty), color = theme.textDim,
                                    fontSize = 13.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 20.dp))
                            }
                        }
                    } else {
                        val favIdx = slots.indices.maxByOrNull { slots[it] } ?: 0
                        val maxSlot = slots.max().coerceAtLeast(1)
                        // Hero: franja estrella
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF0C4A6E)))),
                            contentAlignment = Alignment.Center) {
                            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(slotLabels[favIdx], fontSize = 44.sp, fontWeight = FontWeight.Black,
                                    style = androidx.compose.ui.text.TextStyle(
                                        brush = Brush.horizontalGradient(listOf(Color(0xFF818CF8), Color(0xFF22D3EE)))
                                    ))
                                Text(stringResource(R.string.wrapped_timeslot_title), color = Color(0xFF7DD3FC), fontSize = 13.sp)
                                Text(stringResource(R.string.wrapped_fav_pages, slots[favIdx]),
                                    color = Color(0xFF22D3EE), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        // Distribución por franjas
                        Text(stringResource(R.string.wrapped_timeslot_dist), color = Sky, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0x226366F1)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                slots.forEachIndexed { i, p ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(slotLabels[i], color = if (i == favIdx) Color(0xFF22D3EE) else Color(0xFF94A3B8),
                                            fontSize = 11.sp, fontWeight = if (i == favIdx) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.width(56.dp))
                                        Box(Modifier.weight(1f).height(14.dp)
                                            .clip(RoundedCornerShape(7.dp)).background(Color(0x1AFFFFFF))) {
                                            if (p > 0) Box(Modifier.fillMaxHeight()
                                                .fillMaxWidth(p.toFloat() / maxSlot)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(if (i == favIdx)
                                                    Brush.horizontalGradient(listOf(Color(0xFF22D3EE), Color(0xFF0EA5E9)))
                                                else Brush.horizontalGradient(listOf(Accent2, Accent2.copy(0.5f)))))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text("$p", color = if (i == favIdx) Color(0xFF22D3EE) else theme.textMuted,
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.wrapped_timeslot_note), color = theme.textDim, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 7: VS AÑO ANTERIOR ──────────────────────────────────
                7 -> Column(sm, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (wrapped.previousYearBooks > 0 || wrapped.previousYearPages > 0) {
                        val dBooks = wrapped.totalBooks - wrapped.previousYearBooks
                        val dPages = wrapped.totalPages - wrapped.previousYearPages
                        // Header VS
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(Green.copy(0.25f), Green.copy(0.08f)))),
                                contentAlignment = Alignment.Center) {
                                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${wrapped.year}", color = Green, fontSize = 32.sp, fontWeight = FontWeight.Black)
                                    Text("${wrapped.totalBooks}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
                                    Text(stringResource(R.string.txt_76aee4f9), color = Green.copy(0.7f), fontSize = 12.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(wrapped.totalPages.toLocaleString(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.txt_47bcdf9a), color = Green.copy(0.7f), fontSize = 11.sp)
                                }
                            }
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(Color(0x33F87171), Color(0x10F87171)))),
                                contentAlignment = Alignment.Center) {
                                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${wrapped.year - 1}", color = Red.copy(0.7f), fontSize = 32.sp, fontWeight = FontWeight.Black)
                                    Text("${wrapped.previousYearBooks}", color = Color.White.copy(0.5f), fontSize = 40.sp, fontWeight = FontWeight.Black)
                                    Text(stringResource(R.string.txt_76aee4f9), color = Red.copy(0.5f), fontSize = 12.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(wrapped.previousYearPages.toLocaleString(), color = Color.White.copy(0.5f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.txt_47bcdf9a), color = Red.copy(0.5f), fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        // Delta libros
                        val bookSign = if (dBooks > 0) "+" else ""
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = (if (dBooks >= 0) Green else Red).copy(0.12f),
                            border = BorderStroke(1.dp, (if (dBooks >= 0) Green else Red).copy(0.3f)),
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(if (dBooks == 0) R.string.wrapped_same_books else if (dBooks > 0) R.string.wrapped_more_books else R.string.wrapped_less_books),
                                    color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("$bookSign$dBooks", color = if (dBooks >= 0) Green else Red,
                                    fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val pageSign = if (dPages > 0) "+" else ""
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = (if (dPages >= 0) Accent else Red).copy(0.12f),
                            border = BorderStroke(1.dp, (if (dPages >= 0) Accent else Red).copy(0.3f)),
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(if (dPages == 0) R.string.wrapped_same_pages else if (dPages > 0) R.string.wrapped_more_pages else R.string.wrapped_less_pages),
                                    color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("$pageSign${dPages.toLocaleString()}", color = if (dPages >= 0) Accent else Red,
                                    fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        if (wrapped.longestStreakDays > 0) {
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = RoundedCornerShape(16.dp), color = Red.copy(0.1f),
                                border = BorderStroke(1.dp, Red.copy(0.3f)), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🔥", fontSize = 24.sp); Spacer(Modifier.width(10.dp))
                                    Text(stringResource(R.string.wrapped_streak_days, wrapped.longestStreakDays), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📊", fontSize = 48.sp); Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.wrapped_no_prev_year), color = theme.textDim, fontSize = 15.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 8: CIERRE ───────────────────────────────────────────
                8 -> Column(sm, horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(12.dp))
                    // Número enorme: páginas protagonista
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF4C1D95), Color(0xFF0F0D2E))))) {
                        // v2.4 rework: eliminada marca de agua decorativa
                        Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(wrapped.totalPages.toLocaleString(), fontSize = 80.sp, fontWeight = FontWeight.Black,
                                style = androidx.compose.ui.text.TextStyle(
                                    brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFA78BFA)))
                                ))
                            Text(stringResource(R.string.wcard_pages_read), color = Color(0xFFE9D5FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Logros — Feedback 2.6: de vuelta los emojis (el hero de páginas leídas
                    // se queda sin emoji, a petición)
                    val items = buildList {
                        add("📚 " + stringResource(R.string.wcard_books_done, wrapped.totalBooks) to Accent)
                        if (wrapped.longestStreakDays > 0) add("🔥 " + stringResource(R.string.wrapped_streak_days, wrapped.longestStreakDays) to Red)
                        if (wrapped.maxSessionPages > 0) add("⚡ " + stringResource(R.string.wrapped_record_line, wrapped.maxSessionPages) to Amber)
                        if (wrapped.favoriteAuthor.isNotBlank()) add("✍️ " + wrapped.favoriteAuthor to Accent2)
                    }
                    items.forEach { (txt, col) ->
                        Surface(shape = RoundedCornerShape(14.dp), color = col.copy(0.1f),
                            border = BorderStroke(1.dp, col.copy(0.25f)),
                            modifier = Modifier.fillMaxWidth()) {
                            Text(txt, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(16.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // Feedback 2.6: libros abandonados — movidos aquí desde la slide 4
                    // (mejores/más rápidos); en el cierre encajan como parte del balance.
                    if (wrapped.droppedBooks > 0) {
                        Spacer(Modifier.height(2.dp))
                        Surface(shape = RoundedCornerShape(16.dp), color = Red.copy(0.1f),
                            border = BorderStroke(1.dp, Red.copy(0.3f)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("❌", fontSize = 22.sp); Spacer(Modifier.width(10.dp))
                                    Text(stringResource(R.string.wrapped_dropped_text, wrapped.droppedBooks, if (wrapped.droppedBooks != 1) "s" else ""),
                                        color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                if (wrapped.droppedBookTitles.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    wrapped.droppedBookTitles.forEach { title ->
                                        Text("· $title", color = theme.textDim, fontSize = 12.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(start = 32.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(16.dp), color = theme.surface,
                        border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.txt_b7e522e3), color = theme.textDim, fontSize = 13.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CardGiftcard, null, tint = Accent, modifier = Modifier.size(14.dp))
                                Text(" Lecturameter", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 9: TUS 3 FAVORITOS DEL AÑO (v2.4 rework, congelados v2.6) ──
                9 -> Column(sm) {
                    // Cabecera
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF3B0D0D), Color(0xFF1A0808)))),
                        contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.wrapped_favorites_section), color = FavoriteRed,
                                fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                            // v2.6: eliminado subtítulo "Aleatorios entre tus favoritos…" — los
                            // favoritos ahora son fijos por Wrapped (favoritesForWrapped)
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    if (favBooks.isEmpty()) {
                        Surface(shape = RoundedCornerShape(16.dp), color = theme.surface,
                            border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("❤️", fontSize = 40.sp)
                                Spacer(Modifier.height(10.dp))
                                Text(stringResource(R.string.wrapped_favorites_empty),
                                    color = theme.textDim, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        val favMedals = listOf("\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49")
                        favBooks.forEachIndexed { i, book ->
                            val bookSessions = vm.sessionsForBook(book.id)
                            val favMinutes = bookSessions.sumOf { it.minutes ?: 0 }
                            Surface(shape = RoundedCornerShape(18.dp), color = theme.surface,
                                border = BorderStroke(1.dp, FavoriteRed.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp)) {
                                    // Portada real + medalla funcional de ranking
                                    Box {
                                        BookCover(book.coverUrl, book.title, size = 84, isbnFallback = book.isbn)
                                        Text(favMedals.getOrElse(i) { "" }, fontSize = 18.sp,
                                            modifier = Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-6).dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(book.title, color = theme.textMain, fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (book.author.isNotBlank())
                                            Text(book.author, color = theme.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (book.genres.isNotEmpty()) {
                                            // precomputar fuera de joinToString (displayGenre es @Composable)
                                            val genreLabel = book.genres.map { displayGenre(it) }.joinToString(" · ")
                                            Text(genreLabel,
                                                color = Sky, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(stringResource(R.string.wrapped_fav_pages, book.pages), color = theme.textMuted, fontSize = 12.sp)
                                            if (favMinutes > 0) {
                                                val fh = favMinutes / 60; val fm = favMinutes % 60
                                                Text(if (fh > 0) "${fh}h ${fm}m" else "${fm}m", color = theme.textMuted, fontSize = 12.sp)
                                            }
                                        }
                                        if (book.rating > 0) {
                                            Spacer(Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val favStars = kotlin.math.ceil(book.rating / 2.0).toInt().coerceIn(0, 5)
                                                Text("★".repeat(favStars) + "☆".repeat(5 - favStars),
                                                    color = Gold, fontSize = 13.sp, letterSpacing = 1.sp)
                                                Spacer(Modifier.width(6.dp))
                                                Text("${book.rating}/10", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        if (book.comment.isNotBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Surface(shape = RoundedCornerShape(10.dp), color = Accent.copy(alpha = 0.08f)) {
                                                Text("\u201C${book.comment}\u201D", color = theme.textMuted, fontSize = 12.sp,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        // v2.6: eliminada nota "se renuevan cada vez que abres el Wrapped" —
                        // ya no aplica (favoritos congelados)
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }

        // Flechas de navegación: eliminadas — el pager ya soporta swipe nativo
        // y las flechas superpuestas solapaban el texto de los slides.
    }
}

@Composable
fun WrappedBigCard(value: String, label: String, color: Color, bg: Brush, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(bg), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 30.sp, fontWeight = FontWeight.Black,
                maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 34.sp)
            Text(label.uppercase(), color = color.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
fun WrappedMiniCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(0.1f),
        border = BorderStroke(1.dp, color.copy(0.25f)), modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(label.uppercase(), color = color.copy(0.7f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun WrappedFavRow(emoji: String, label: String, value: String, sub: String, color: Color, theme: Theme) {
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(0.08f),
        border = BorderStroke(1.dp, color.copy(0.2f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (emoji.isNotBlank()) { Text(emoji, fontSize = 26.sp); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), color = color.copy(0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text(value, color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, color = color.copy(0.7f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun WrappedTop3Row(medal: String, name: String, count: String, isGold: Boolean, color: Color, theme: Theme) {
    val bg = if (isGold) color.copy(0.15f) else color.copy(0.07f)
    val border = if (isGold) color.copy(0.4f) else color.copy(0.18f)
    Surface(shape = RoundedCornerShape(12.dp), color = bg,
        border = BorderStroke(1.dp, border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(medal, fontSize = 22.sp); Spacer(Modifier.width(8.dp))
            Text(name, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LinearProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(0.15f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f,1f)).fillMaxHeight()
            .clip(RoundedCornerShape(3.dp)).background(color))
    }
}

fun wrappedMedal(i: Int): String = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }

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
    // D-004: wrappedHistory es StateFlow; se colecciona en la raíz de la pantalla
    val wrappedHistory by vm.wrappedHistory.collectAsState()
    val history = wrappedHistory.sortedByDescending { it.year }
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

// ── Estadísticas avanzadas (v2.4 rework) ─────────────────────────────────────
//
// Tres secciones nuevas al final de StatsScreen, cada una en su Card y oculta
// si no hay datos: heatmap horario, estadísticas de saga y outliers de velocidad.

data class SagaStat(val name: String, val books: Int, val minutes: Int, val pages: Int)
data class SpeedOutlier(val book: Book, val ppd: Double, val deltaPct: Int)

/** Detecta la saga desde el título: "Título (Nombre de Saga, #3)" o "(Saga #3)". */
private val SAGA_REGEX = Regex("""\(([^()#]+?),?\s*#\s*\d+\)""")
fun sagaNameFromTitle(title: String): String? =
    SAGA_REGEX.find(title)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

/** Agrupa libros por saga (≥2 libros) con tiempo y páginas acumulados de sus sesiones. */
fun computeSagaStats(books: List<Book>, sessions: List<ReadingSession>): List<SagaStat> {
    val bySaga = books.mapNotNull { b -> sagaNameFromTitle(b.title)?.let { it to b } }
        .groupBy({ it.first }, { it.second })
        .filter { it.value.size >= 2 }
    return bySaga.map { (saga, sagaBooks) ->
        val ids = sagaBooks.map { it.id }.toHashSet()
        val sagaSessions = sessions.filter { it.bookId in ids }
        val pages = sagaSessions.sumOf { it.pages }.takeIf { it > 0 }
            ?: sagaBooks.filter { it.status == BookStatus.FINISHED || it.status == BookStatus.REREADING }.sumOf { it.pages }
        SagaStat(
            name = saga,
            books = sagaBooks.size,
            minutes = sagaSessions.sumOf { it.minutes ?: 0 },
            pages = pages
        )
    }.sortedByDescending { it.minutes * 10_000 + it.pages }
}

/** Outliers de velocidad respecto a la media global de págs/día (umbral ±25%).
 *  Devuelve (media global, lista de outliers ordenada por |delta|). */
fun computeOutliers(books: List<Book>, sessions: List<ReadingSession>): Pair<Double, List<SpeedOutlier>> {
    val entries = books
        .filter {
            it.status == BookStatus.FINISHED && it.startDate != null && it.endDate != null &&
            it.startDate != it.endDate && daysBetween(it.startDate, it.endDate) >= 2
        }
        .map { b ->
            val sessPages = sessions.filter { s -> s.bookId == b.id }.sumOf { it.pages }
            val pages = if (sessPages > 0) sessPages else b.pages
            val d = daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1)
            b to pages.toDouble() / d
        }
    if (entries.size < 3) return 0.0 to emptyList()
    val mean = entries.map { it.second }.average()
    if (mean <= 0.0) return mean to emptyList()
    val outliers = entries.mapNotNull { (b, ppd) ->
        val deltaPct = (((ppd - mean) / mean) * 100).toInt()
        if (kotlin.math.abs(deltaPct) >= 25) SpeedOutlier(b, ppd, deltaPct) else null
    }.sortedByDescending { kotlin.math.abs(it.deltaPct) }.take(6)
    return mean to outliers
}

/** Matriz [día 0=Lun..6=Dom][franja 0..7 de 3h] con páginas leídas, desde startTimestamp.
 *  null si ninguna sesión tiene hora registrada. */
fun buildHeatmapData(sessions: List<ReadingSession>): Array<IntArray>? {
    val timed = sessions.filter { it.startTimestamp != null && it.startTimestamp > 0 }
    if (timed.isEmpty()) return null
    val grid = Array(7) { IntArray(8) }
    val cal = java.util.Calendar.getInstance()
    timed.forEach { s ->
        cal.timeInMillis = s.startTimestamp!!
        // Calendar: SUNDAY=1..SATURDAY=7 → 0=Lun..6=Dom
        val dow = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
        val slot = cal.get(java.util.Calendar.HOUR_OF_DAY) / 3
        grid[dow][slot] += s.pages.coerceAtLeast(1)
    }
    return grid
}

// Feedback 2.6: card del heatmap horario extraída a composable propio — ahora vive en
// la pestaña Heatmap (junto al de año/mes) y reacciona a sus filtros de período.
@Composable
fun HourlyHeatmapCard(sessions: List<ReadingSession>, theme: Theme) {
    val hourlyGrid = remember(sessions) { buildHeatmapData(sessions) }
    Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.adv_hourly_title), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (hourlyGrid == null) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.adv_hourly_empty), color = theme.textDim, fontSize = 12.sp, lineHeight = 17.sp)
            } else {
                val maxCell = hourlyGrid.maxOf { row -> row.max() }.coerceAtLeast(1)
                Text(stringResource(R.string.adv_hourly_subtitle), color = theme.textMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp))
                val slotLabels = listOf("0", "3", "6", "9", "12", "15", "18", "21")
                val dayLabels = stringResource(R.string.adv_hourly_days).split(",")
                // Cabecera de franjas
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(24.dp))
                    slotLabels.forEach { l ->
                        Text(l, color = theme.textDim, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(2.dp))
                hourlyGrid.forEachIndexed { day, row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(dayLabels.getOrElse(day) { "" }, color = theme.textDim, fontSize = 9.sp, modifier = Modifier.width(24.dp))
                        row.forEach { v ->
                            val intensity = v.toFloat() / maxCell
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(18.dp)
                                    .padding(horizontal = 1.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        // Feedback WhatsApp 10-07: misma escala rojiza que el heatmap
                                        // mensual (heatColor de HeatmapView) por consistencia visual
                                        if (v == 0) theme.border.copy(alpha = 0.35f)
                                        else when {
                                            intensity < 0.20f -> Color(0xFF78350F) // marrón oscuro
                                            intensity < 0.40f -> Color(0xFFB45309) // ámbar oscuro
                                            intensity < 0.60f -> Color(0xFFF59E0B) // ámbar
                                            intensity < 0.80f -> Color(0xFFEA580C) // naranja
                                            else              -> Color(0xFFDC2626) // rojo
                                        }
                                    )
                            )
                        }
                    }
                }
                // Pico de lectura
                var peakDay = 0; var peakSlot = 0; var peakVal = 0
                hourlyGrid.forEachIndexed { d, row -> row.forEachIndexed { s, v -> if (v > peakVal) { peakVal = v; peakDay = d; peakSlot = s } } }
                if (peakVal > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.adv_hourly_peak, dayLabels.getOrElse(peakDay) { "" }, peakSlot * 3, peakSlot * 3 + 3),
                        color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun AdvancedStatsSections(vm: BooksViewModel, theme: Theme) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val sagaStats = remember(books, sessions) { computeSagaStats(books, sessions) }
    val outlierResult = remember(books, sessions) { computeOutliers(books, sessions) }
    val globalMean = outlierResult.first
    val outliers = outlierResult.second
    // Feedback 2.6: el heatmap horario se movió a la pestaña Heatmap (junto al de
    // año/mes), donde responde a los filtros de período. Sin sagas ni outliers ya no
    // queda contenido → no pintar el título huérfano.
    if (sagaStats.isEmpty() && outliers.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(R.string.adv_stats_title),
            color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )

        // ── Estadísticas de saga ──────────────────────────────────────────────
        if (sagaStats.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.adv_saga_title), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                    sagaStats.take(8).forEach { saga ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(saga.name, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            val time = if (saga.minutes > 0) {
                                val h = saga.minutes / 60; val mns = saga.minutes % 60
                                (if (h > 0) "${h}h ${mns}m" else "${mns}m") + " · "
                            } else ""
                            Text(
                                time + stringResource(R.string.adv_saga_books, saga.books),
                                color = theme.textMuted, fontSize = 12.sp
                            )
                        }
                        HorizontalDivider(color = theme.border.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // ── Outliers de velocidad ─────────────────────────────────────────────
        if (outliers.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.adv_outliers_title), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.adv_outliers_subtitle, String.format("%.1f", globalMean)), color = theme.textMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                    outliers.forEach { o ->
                        val faster = o.deltaPct > 0
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(o.book.title, color = theme.textMain, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(20.dp), color = (if (faster) Green else Amber).copy(alpha = 0.12f)) {
                                Text(
                                    if (faster) stringResource(R.string.adv_outlier_faster, o.deltaPct)
                                    else stringResource(R.string.adv_outlier_slower, -o.deltaPct),
                                    color = if (faster) Green else Amber,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── StatsScreen ───────────────────────────────────────────────────────────────

@Composable
fun StatsScreen(vm: BooksViewModel, _prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit, onWrapped: (Int) -> Unit, onWrappedHistory: () -> Unit, onDetail: (Long) -> Unit = {}, onDetailWithDate: (Long, String) -> Unit = { _, _ -> }, onDailySessions: (String) -> Unit = {}) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    // Solo FINISHED con fechas distintas (mismo día distorsiona velocidad)
    val finished = books.filter {
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
    val allGenres = books.flatMap { b -> b.genres }.distinct().sorted()
    val allAuthors = books.map { it.author }.filter { it.isNotBlank() }.distinct().sorted()

    val filtered = finished
        .let { list -> if (filterGenre != null) list.filter { it.genres.contains(filterGenre) } else list }
        .let { list -> if (filterAuthor != null) list.filter { it.author == filterAuthor } else list }

    // Derive stats
    data class SpeedEntry(val book: Book, val ppd: Double, val days: Int)

    val speedList = filtered
        .filter { it.startDate != it.endDate && daysBetween(it.startDate!!, it.endDate!!) >= 2 }
        .map { b ->
            val sessPages = sessions.filter { s -> s.bookId == b.id }.sumOf { it.pages }
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
        sessions.filter { s -> s.bookId == b.id }.sumOf { it.pages }.takeIf { it > 0 } ?: b.pages
    }
    val speedFiltered = filtered.filter { it.startDate != it.endDate && daysBetween(it.startDate!!, it.endDate!!) >= 2 }
    val avgPpd = if (speedFiltered.isNotEmpty()) speedFiltered.map { b ->
        val sp = sessions.filter { s -> s.bookId == b.id }.sumOf { it.pages }
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
                // Feedback 2.6: el título se auto-reduce en vez de partirse ("Estadística / s")
                var statsTitleSize by remember { mutableStateOf(22.sp) }
                Text(
                    stringResource(R.string.txt_a6db8091), color = theme.textMain,
                    fontSize = statsTitleSize, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false,
                    onTextLayout = { if (it.hasVisualOverflow && statsTitleSize > 14.sp) statsTitleSize *= 0.92f }
                )
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
            // Feedback 2.7: eliminado el botón 🎁 de historial Wrapped de este header —
            // comprimía el espacio y truncaba "N libros terminados". El historial sigue
            // accesible desde el icono 🎁 de la pantalla principal y el banner en ventana.
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
                // v2.4 rework: secciones avanzadas al final de la pantalla
                item { AdvancedStatsSections(vm, theme) }
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

                // v2.4 rework: secciones avanzadas al final de la pantalla
                item { AdvancedStatsSections(vm, theme) }

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
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val authorBooks = books.filter { it.author.equals(author, ignoreCase = true) }
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
    // v2.5: aviso de duplicado antes de añadir
    var duplicateCandidate by remember { mutableStateOf<Book?>(null) }
    val scope = rememberCoroutineScope()
    // Dialog de ISBN escaneado — se activa cuando llega un ISBN real desde la cámara
    var showScanDialog by remember { mutableStateOf(false) }

    duplicateCandidate?.let { cand ->
        val existing = vm.findDuplicate(cand) ?: cand
        DuplicateBookDialog(cand, existing, theme,
            onConfirm = { vm.addBook(cand, prefs); duplicateCandidate = null },
            onDismiss = { duplicateCandidate = null })
    }
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
            // v2.6: idioma de la app como idioma preferido de búsqueda (automático)
            // Feedback 2.6: onPartial pinta los resultados según van llegando las fases
            val found = try {
                searchOpenLibrary(query, vm.currentLanguage) { partial ->
                    scope.launch { if (isLoading) results = partial }
                }
            } catch (_: Exception) { emptyList() }
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
        // Feedback 2.7: el género detectado por la API era inamovible — ahora editable
        // (multi-select hasta 2, mismo patrón que AddScreen), prefijado con lo detectado
        var searchGenres by remember { mutableStateOf(mapApiGenre(r.genre).ifEmpty { if (r.genre.isNotBlank()) listOf("Otro") else emptyList() }) }
        var searchGenreExpanded by remember { mutableStateOf(false) }
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
                    // Feedback 2.7: género editable antes de guardar (máx 2)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.txt_57d644ad), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                    // P-012: bottom sheet con buscador y grupos en lugar del dropdown plano
                    val searchSuggestedGenres = remember { searchGenres }
                    Box {
                        OutlinedTextField(
                            value = if (searchGenres.isEmpty()) "" else searchGenres.map { displayGenre(it) }.joinToString(" · "),
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text(stringResource(R.string.txt_84a8f3ea), color = theme.textDim, fontSize = 13.sp) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = theme.textDim) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(theme),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Box(Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).clickable { searchGenreExpanded = true })
                    }
                    if (searchGenreExpanded) {
                        GenreSelectorSheet(
                            initial = searchGenres,
                            suggested = searchSuggestedGenres,
                            theme = theme,
                            onDismiss = { searchGenreExpanded = false },
                            onConfirm = { searchGenres = it }
                        )
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
                    // Feedback 2.7: género elegido/confirmado por el usuario en el diálogo
                    val newBook = Book(title = r.title, author = r.author, pages = if (r.pages > 0) r.pages else 1, status = status, startDate = storedStart, endDate = storedEnd, rating = if (status == BookStatus.FINISHED) searchRating else 0, coverUrl = cleanCoverUrl(r.coverUrl), isbn = r.isbn, genres = searchGenres, firstFunctionalPage = searchFirstFunc.toIntOrNull(), lastFunctionalPage = searchLastFunc.toIntOrNull())
                    // Fase 0 QA: la primera edición hereda el idioma del resultado de búsqueda
                    // (r.language, v2.6) o, en su defecto, el deducido del prefijo ISBN.
                    // Solo sin señal alguna se cae al genérico "mul"/🌐 de siempre.
                    val edMeta: Triple<String, String, String>? = when (r.language) {
                        "es" -> Triple("es", "Español", "🇪🇸")
                        "ca" -> Triple("ca", "Català", "🇪🇸 (CAT)")
                        "en" -> Triple("original", "English", "🌐")
                        "fr" -> Triple("fr", "Français", "🇫🇷")
                        "de" -> Triple("de", "Deutsch", "🇩🇪")
                        "it" -> Triple("it", "Italiano", "🇮🇹")
                        "pt" -> Triple("pt", "Português", "🇵🇹")
                        else -> r.isbn?.let { isbnToLanguageMeta(it) }?.takeIf { it.second != "Original" }
                    }
                    val firstEdition = BookEdition(id = newBook.id, language = edMeta?.first ?: "mul", languageLabel = edMeta?.second ?: "Edición principal", flag = edMeta?.third ?: "🌐", title = newBook.title, pages = newBook.pages, coverUrl = newBook.coverUrl, isbn = newBook.isbn, isActive = true)
                    val toAdd = newBook.copy(editions = listOf(firstEdition))
                    // v2.5: aviso de duplicado (antes se añadía sin avisar)
                    if (vm.findDuplicate(toAdd) != null) { duplicateCandidate = toAdd } else { vm.addBook(toAdd, prefs) }
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
            // Feedback 2.6: spinner a pantalla completa solo mientras no hay NINGÚN
            // resultado; con parciales se muestra la lista con un indicador pequeño.
            isLoading && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = Accent); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.txt_65dc881f), color = theme.textMuted, fontSize = 14.sp) } }
            errorMsg.isNotBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (networkError) "📡" else "🔍", fontSize = 40.sp); Spacer(Modifier.height(12.dp)); Text(errorMsg, color = if (networkError) Red else theme.textMuted, fontSize = 14.sp, fontWeight = if (networkError) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center); Spacer(Modifier.height(8.dp)); Text(if (networkError) stringResource(R.string.err_check_wifi_retry) else stringResource(R.string.err_try_other_language), color = if (networkError) Red.copy(alpha = 0.7f) else theme.textDim, fontSize = 12.sp, textAlign = TextAlign.Center) } }
            !searched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🔎", fontSize = 48.sp); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.txt_af80d2f5), color = theme.textMain, fontSize = 16.sp) } }
            else -> {
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_65dc881f), color = theme.textDim, fontSize = 11.sp)
                    }
                } else {
                    Text(stringResource(R.string.search_results_summary, results.size), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
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
    // Feedback 11-07: fuera "Edición principal" (no aporta como opción elegible) y
    // cooficiales estandarizadas como 🇪🇸 (CAT) / 🇪🇸 (EUS) / 🇪🇸 (GAL), con Euskera
    // y Galego añadidas.
    val languages = listOf(
        Triple("es",  "Español",           "🇪🇸"),
        Triple("en-us","English (US)",     "🇺🇸"),
        Triple("en-uk","English (UK)",     "🇬🇧"),
        // QA 12-07 (B-013): eliminado el "English 🌐" genérico del selector — redundante
        // con US/UK. Las ediciones existentes con 🌐 se conservan tal cual.
        Triple("fr",  "Français",          "🇫🇷"),
        Triple("de",  "Deutsch",           "🇩🇪"),
        Triple("it",  "Italiano",          "🇮🇹"),
        Triple("pt",  "Português",         "🇵🇹"),
        Triple("ca",  "Català",            "🇪🇸 (CAT)"),
        Triple("eu",  "Euskera",           "🇪🇸 (EUS)"),
        Triple("gl",  "Galego",            "🇪🇸 (GAL)"),
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
    // v2.5: aviso de duplicado antes de añadir
    var duplicateCandidate by remember { mutableStateOf<Book?>(null) }
    var editionLanguage by remember { mutableStateOf("es") }; var editionLanguageLabel by remember { mutableStateOf("Español") }; var editionFlag by remember { mutableStateOf("🇪🇸") }
    var firstFuncPage by remember { mutableStateOf("") }; var lastFuncPage by remember { mutableStateOf("") }
    var manualCoverUrl by remember { mutableStateOf<String?>(null) }
    var showAddCoverDialog by remember { mutableStateOf(false) }
    var addCoverUrlInput by remember { mutableStateOf("") }

    duplicateCandidate?.let { cand ->
        val existing = vm.findDuplicate(cand) ?: cand
        DuplicateBookDialog(cand, existing, theme,
            onConfirm = { vm.addBook(cand, prefs); duplicateCandidate = null; onBack() },
            onDismiss = { duplicateCandidate = null })
    }
    val addCoverPreviewUrl: String? = when {
        manualCoverUrl != null -> manualCoverUrl
        isbn.trim().length >= 10 -> "https://covers.openlibrary.org/b/isbn/${isbn.trim()}-M.jpg"
        else -> null
    }

    // Rellenar ISBN si viene de scanner externo (solo dígitos/X, 10-13 chars)
    // v20.9: también autorellena título, autor, páginas y géneros vía API
    var isbnSearching by remember { mutableStateOf(false) }
    var isbnAutoError by remember { mutableStateOf(false) }
    LaunchedEffect(externalIsbn) {
        if (!externalIsbn.isNullOrBlank()) {
            val safeIsbn = externalIsbn.replace(Regex("[^\\dXx]"), "").uppercase()
            if (safeIsbn.length in 10..13) {
                isbn = safeIsbn
                isbnSearching = true
                isbnAutoError = false
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
                    isbnAutoError = true
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
                            Text(if (manualCoverUrl != null) stringResource(R.string.btn_change_cover) else stringResource(R.string.btn_add_cover), fontSize = 12.sp)
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
                // P-012: bottom sheet con buscador y grupos en lugar del dropdown plano
                Box(Modifier.padding(bottom = 16.dp)) {
                    OutlinedTextField(
                        value = if (genres.isEmpty()) "" else genres.map { displayGenre(it) }.joinToString(" · "),
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text(stringResource(R.string.txt_84a8f3ea), color = theme.textDim, fontSize = 13.sp) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = theme.textDim) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(theme),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Box(Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).clickable { genreExpanded = true })
                }
                if (genreExpanded) {
                    GenreSelectorSheet(
                        initial = genres,
                        suggested = emptyList(),
                        theme = theme,
                        onDismiss = { genreExpanded = false },
                        onConfirm = { genres = it }
                    )
                }
                Text(stringResource(R.string.txt_4239bda5), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                LanguageSelector(selectedLanguage = editionLanguage, onLanguageSelected = { code, label, flag -> editionLanguage = code; editionLanguageLabel = label; editionFlag = flag }, modifier = Modifier.padding(bottom = 16.dp))
                // ISBN con botón de escaneo
                Text(stringResource(R.string.txt_fb84daae), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = if (isbnSearching || isbnAutoError) 4.dp else 16.dp)) {
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
                } else if (isbnAutoError) {
                    // v2.5: rojo (antes gris/textDim) + i18n (antes hardcoded ES)
                    // Feedback 2.7: informativo (el ISBN se conserva), no error fatal → ámbar
                    Text(stringResource(R.string.isbn_scan_not_found), color = Amber, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
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
                                val toAdd = newBook.copy(editions = listOf(firstEdition))
                                if (vm.findDuplicate(toAdd) != null) { duplicateCandidate = toAdd } else { vm.addBook(toAdd, prefs); onBack() }
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
                        val toAdd = newBook.copy(editions = listOf(firstEdition))
                        if (vm.findDuplicate(toAdd) != null) { duplicateCandidate = toAdd } else { vm.addBook(toAdd, prefs); onBack() }
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
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val book = books.find { it.id == id } ?: run { onBack(); return }
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
    // D-002/T1: llegada desde el selector rápido del home (⏱️) — arrancar el crono
    // con el mismo flujo de permisos que el botón ▶ del detalle.
    LaunchedEffect(id) {
        if (TimerQuickStart.pendingBookId == id) {
            TimerQuickStart.pendingBookId = -1L
            if (!TimerStateHolder.running) {
                startTimerWithPermCheck { com.lecturameter.TimerService.start(context, id, book.title) }
            }
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
        // v2.5: escanear ISBN de la edición física directamente desde el sheet
        val editionScanLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val scanned = result.data?.getStringExtra("isbn")
            if (result.resultCode == android.app.Activity.RESULT_OK && !scanned.isNullOrBlank()) {
                editionsLoading = true
                scope.launch {
                    var ed = fetchEditionByIsbn(scanned)
                    if (ed == null) {
                        // Cadena reforzada v2.5: OL edición directa vía fetchIsbnFullMetadata
                        val meta = withContext(Dispatchers.IO) { fetchIsbnFullMetadata(scanned) }
                        if (!meta.title.isNullOrBlank()) {
                            val (lId, lLabel, lFlag) = isbnToLanguageMeta(scanned)
                            ed = EditionResult(lId, lLabel, lFlag, meta.title, meta.pages ?: 0, meta.coverUrl, scanned, "", "")
                        }
                    }
                    // Feedback 2.6 (caso Gollancz 9781399630467): las ediciones tan nuevas que
                    // aún no están indexadas en NINGUNA API (verificado: ni OL ni Google conocen
                    // ese ISBN) no se podían añadir. Como aquí estamos en el contexto de un libro
                    // concreto, ofrecemos la edición con el ISBN escaneado + título del libro +
                    // idioma inferido del prefijo ISBN; el usuario decide si añadirla.
                    val resolved = ed ?: run {
                        val (lId, lLabel, lFlag) = isbnToLanguageMeta(scanned)
                        android.widget.Toast.makeText(context, context.getString(R.string.edition_scan_unindexed, scanned), android.widget.Toast.LENGTH_LONG).show()
                        EditionResult(lId, lLabel, lFlag, book.title, 0, null, scanned, "", "")
                    }
                    editionsLoading = false
                    availableEditions = listOf(resolved) + availableEditions.filter { it.isbn != resolved.isbn }
                    selectedEditionResult = resolved
                }
            }
        }
        val editionCamPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) editionScanLauncher.launch(android.content.Intent(context, ScannerActivity::class.java))
        }
        AlertDialog(
            onDismissRequest = { showChangeEditionSheet = false; showAddEditionSheet = false },
            containerColor = theme.bgMid,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isAdding) stringResource(R.string.sheet_add_edition) else stringResource(R.string.sheet_change_edition),
                        color = theme.textMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    // v2.5: escanear ISBN de una edición física
                    IconButton(
                        onClick = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                editionScanLauncher.launch(android.content.Intent(context, ScannerActivity::class.java))
                            } else {
                                editionCamPermLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        // Feedback 2.6: icono de código de barras (como en el resto de la app), no QR
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_barcode), contentDescription = "Scan ISBN", tint = Accent, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            if (!editionsLoading) {
                                availableEditions = emptyList()
                                editionsLoading = true
                                vm.loadAvailableEditions(
                                    id,
                                    forceRefresh = true,
                                    onPartial = { partial -> availableEditions = partial }   // r3 feedback progresivo
                                ) { results ->
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
                // v2.4: scroll de respaldo para landscape móvil (mockup aprobado)
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
                                Text("⏱️", fontSize = 16.sp)
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
                            startPage = start, endPage = end,
                            // v2.4 rework: hora de inicio para el heatmap horario.
                            // Solo sesiones del día actual (retro-añadir fechas pasadas daría horas falsas).
                            startTimestamp = if (effectiveDate == today())
                                System.currentTimeMillis() - ((m ?: 0) * 60_000L)
                            else null
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
                                vm.loadAvailableEditions(
                                    id,
                                    onPartial = { partial -> availableEditions = partial }   // r3 feedback progresivo
                                ) { results ->
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
                                vm.loadAvailableEditions(
                                    id,
                                    onPartial = { partial -> availableEditions = partial }   // r3 feedback progresivo
                                ) { results ->
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
                        // v2.4: con la saga restaurada en títulos, truncar a 3 líneas
                        Text(book.title, color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (book.author.isNotBlank()) Text(stringResource(R.string.by_author, book.author), color = Accent, fontSize = 14.sp, modifier = Modifier.clickable { onAuthorClick(book.author) })
                        // Género — toca para cambiar; botón swap si hay 2
                        var showGenreMenu by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          // P-012: bottom sheet con buscador y grupos; "Limpiar" cubre el antiguo
                          // "Sin género" y la recarga de la API va como acción propia del sheet.
                          Text(
                              text = if (book.genres.isNotEmpty()) book.genres.map { displayGenre(it) }.joinToString(" · ") else stringResource(R.string.genre_add_button),
                              color = if (book.genres.isNotEmpty()) theme.textDim else Accent.copy(alpha = 0.7f),
                              fontSize = 12.sp,
                              modifier = Modifier.padding(top = 2.dp).clickable { showGenreMenu = true }
                          )
                          if (showGenreMenu) {
                              GenreSelectorSheet(
                                  initial = book.genres,
                                  suggested = emptyList(),
                                  theme = theme,
                                  onDismiss = { showGenreMenu = false },
                                  onConfirm = { newGenres ->
                                      vm.updateGenres(id, newGenres, prefs)
                                      if (book.noCoverFound) vm.updateNoCoverFound(id, false, prefs)
                                  },
                                  onRefreshFromApi = {
                                      showGenreMenu = false
                                      vm.refreshGenre(id, prefs) { found ->
                                          refreshMsg = if (found) context.getString(R.string.msg_genre_updated) else context.getString(R.string.msg_genre_not_found)
                                      }
                                  }
                              )
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
                        // Feedback 2.6 (estandarización pills): páginas = índigo en toda la app
                        if (pagesRead > 0) StatBox("${pagesRead}p", stringResource(R.string.pill_pags_leidas), Modifier.weight(1f), theme, highlight = true, highlightColor = Accent)
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
                                // v2.5: la fecha de inicio no puede ser posterior a la de fin
                                // (regla por ciclo: start≤end de la lectura original y reread≤reread_end por ocurrencia)
                                val startEv = newEvents.firstOrNull { it.type == "start" }
                                val endEv   = newEvents.firstOrNull { it.type == "end" }
                                if (startEv != null && endEv != null && startEv.date > endEv.date) {
                                    dateError = context.getString(R.string.err_date_start_after_end)
                                    return@Button
                                }
                                for (rr in newEvents.filter { it.type == "reread" }) {
                                    val rrEnd = newEvents.firstOrNull { it.type == "reread_end" && it.occurrence == rr.occurrence }
                                    if (rrEnd != null && rr.date > rrEnd.date) {
                                        dateError = context.getString(R.string.err_date_start_after_end)
                                        return@Button
                                    }
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
                    val cycles = computeCycleStats(book, sessions)
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
                            // Feedback 2.6 (estandarización pills): color por métrica en toda la app —
                            // páginas=índigo (Accent), tiempo=Sky, velocidad=verde. Antes páginas iba
                            // en verde y págs/día heredaba el color del ciclo.
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                StatBox(daysTxt, daysLabel, Modifier.weight(1f), theme)
                                if (sessMins > 0) StatBox(fmtMinutes(sessMins), stringResource(R.string.stat_total_time), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
                                if (sessPages > 0) StatBox("${sessPages}p", stringResource(R.string.pill_pags_leidas), Modifier.weight(1f), theme, highlight = true, highlightColor = Accent)
                                if (cyclePct != null) StatBox("$cyclePct%", stringResource(R.string.pill_porcentaje_leido), Modifier.weight(1f), theme)
                                if (c.pagesPerDay != null) {
                                    StatBox(String.format("%.1f", c.pagesPerDay), stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, highlight = true, highlightColor = Green)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }

                // ── Historial de sesiones (v20.0 G2: una sección por ciclo) ──
                // v20.3: precalculamos cycles para poder usar pagesPerDay en las pills de sesiones
                val sessionCycles = computeCycleStats(book, sessions)
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
                            // Feedback 2.6 (estandarización pills): páginas=índigo, tiempo=Sky,
                            // velocidad (p/d y p/min)=verde. El color del ciclo queda solo en la
                            // cabecera de sección (lectura índigo / relectura cian).
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                                StatBox(daysTxt, daysLabel, Modifier.weight(1f), theme)
                                if (totalSessMins > 0) StatBox(fmtMinutes(totalSessMins), stringResource(R.string.stat_total_time), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
                                if (totalSessPages > 0) StatBox("${totalSessPages}p", stringResource(R.string.pill_pags_leidas), Modifier.weight(1f), theme, highlight = true, highlightColor = Accent)
                                if (pagPerDay != null) StatBox(String.format("%.1f", pagPerDay), stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, highlight = true, highlightColor = Green)
                                if (avgSessPagesPerMin != null) StatBox(String.format("%.1f", avgSessPagesPerMin), stringResource(R.string.pill_pags_min), Modifier.weight(1f), theme, highlight = true, highlightColor = Green)
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
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("lecturameter", android.content.Context.MODE_PRIVATE)

    var newestFirst by remember { mutableStateOf(vm.savedSessionNewestFirst) }
    var searchQuery by remember { mutableStateOf("") }

    val booksWithSessions = remember(sessions, books) {
        // Agrupar por (bookId, language) — misma lengua = mismo "libro", distinta lengua = libro separado
        val bookEditionMap = books.associate { book ->
            book.id to book.editions.associate { it.id to it.language }
        }
        val keys = sessions.mapNotNull { s ->
            val book = books.find { it.id == s.bookId } ?: return@mapNotNull null
            val edMap = bookEditionMap[s.bookId] ?: emptyMap()
            val lang = s.editionId?.let { edMap[it] } ?: run {
                // Legacy: sin editionId → asignar al idioma de la edición activa
                book.editions.firstOrNull { it.isActive }?.language ?: "original"
            }
            BookLangKey(s.bookId, lang)
        }.toSet()
        keys.mapNotNull { key ->
            val book = books.find { it.id == key.bookId } ?: return@mapNotNull null
            book to key.language
        }.sortedBy { (book, _) -> book.title }
    }

    // Numeración FIJA por (bookId, lang): #1 = sesión más antigua de esa combinación
    val fixedNumberMap = remember(sessions, books) {
        val map = mutableMapOf<Long, Int>()
        val bookEditionMap = books.associate { book ->
            book.id to book.editions.associate { it.id to it.language }
        }
        // Agrupar por (bookId, language)
        val byBookLang = sessions.groupBy { s ->
            val edMap = bookEditionMap[s.bookId] ?: emptyMap()
            val lang = s.editionId?.let { edMap[it] }
                ?: books.find { it.id == s.bookId }?.editions?.firstOrNull { it.isActive }?.language
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
    val filteredSessions = remember(sessions, searchQuery, fixedNumberMap) {
        val q = searchQuery.trim()
        if (q.isBlank()) sessions
        else if (q.startsWith("#")) {
            val num = q.removePrefix("#").toIntOrNull()
            if (num != null) sessions.filter { fixedNumberMap[it.id] == num }
            else sessions
        } else if (q.length > 2 && q.startsWith("\"") && q.endsWith("\"")) {
            val exactTitle = q.removeSurrounding("\"").trim()
            val matchingBookIds = books.filter { it.title.equals(exactTitle, ignoreCase = true) }.map { it.id }.toSet()
            sessions.filter { it.bookId in matchingBookIds }
        } else {
            sessions.filter { it.note.contains(q, ignoreCase = true) }
        }
    }

    // (bookId, lang) que tienen sesiones tras el filtro
    val bookEditionMapForFilter = books.associate { book ->
        book.id to book.editions.associate { it.id to it.language }
    }
    val filteredBooksWithSessions = remember(filteredSessions, booksWithSessions) {
        val filteredKeys = filteredSessions.map { s ->
            val edMap = bookEditionMapForFilter[s.bookId] ?: emptyMap()
            val lang = s.editionId?.let { edMap[it] }
                ?: books.find { it.id == s.bookId }?.editions?.firstOrNull { it.isActive }?.language
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

    // Auto-expandir cuando hay búsqueda activa; al limpiar la búsqueda, volver a colapsar.
    // Fix v2.4: antes quedaba TODO expandido para siempre tras cualquier búsqueda
    // (visible sobre todo en tablet, MD §7 «historial siempre expandido»).
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            filteredBooksWithSessions.forEach { (book, lang) -> expanded["${book.id}_$lang"] = true }
        } else {
            expanded.keys.forEach { expanded[it] = false }
        }
    }

    val totalPages = sessions.sumOf { it.pages }
    val totalMins  = sessions.mapNotNull { it.minutes }.sum()

    Column(Modifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                // Feedback 13-07: bgDeep ya nunca es Transparent (== bgDark si el tema no
                // define deep) — en ese caso la cabecera cae a bgMid como siempre
                .background(Brush.verticalGradient(listOf(theme.bgDark, if (theme.bgDeep != theme.bgDark) theme.bgDeep else theme.bgMid)))
                // Feedback 13-07 (3): el panel ya no vive bajo la status bar (drawer) —
                // el top de 40dp dejaba un hueco; alineado con el icono 📜 del rail
                .padding(top = 14.dp, bottom = 14.dp, start = 20.dp, end = 12.dp)
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
                    HistoryStat("${sessions.size}", stringResource(R.string.history_stat_sessions), Modifier.weight(1f), theme, valueColor = Accent)
                    HistoryStat(if (totalMins > 0) fmtMinutes(totalMins) else "—", stringResource(R.string.history_stat_total_time), Modifier.weight(1f), theme, valueColor = Sky)
                    HistoryStat("$totalPages", stringResource(R.string.pill_paginas_totales), Modifier.weight(1f), theme, valueColor = Green)
                    HistoryStat(if (totalMins > 0) String.format("%.1f", totalPages.toDouble() / totalMins) else "—", stringResource(R.string.pill_pags_min), Modifier.weight(1f), theme, valueColor = Green)
                    // v2.5: media global de págs/día — en la misma fila
                    val historyGlobalMean = remember(books, sessions) { computeOutliers(books, sessions).first }
                    if (historyGlobalMean > 0.0) {
                        HistoryStat(String.format("%.1f", historyGlobalMean), stringResource(R.string.history_stat_global_ppd), Modifier.weight(1f), theme, valueColor = Amber)
                    }
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

        if (sessions.isEmpty()) {
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
                    val bookEdMap = books.find { it.id == book.id }?.editions?.associate { it.id to it.language } ?: emptyMap()
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
                                    DrawerStatChipH(if (bookTotalMins > 0) "⏱️ ${fmtMinutes(bookTotalMins)}" else "⏱️ —", Sky, Modifier.weight(1f))
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
    // QA 12-07 r2 (Aurora): acento del tema en vez del índigo global (legibilidad)
    val effColor = if (valueColor == Accent) accentForTheme(theme) else valueColor
    val color = effColor ?: theme.textMain
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(10.dp),
        color = effColor?.copy(alpha = 0.12f) ?: theme.surface,
        border = BorderStroke(1.dp, effColor?.copy(alpha = 0.35f) ?: theme.border)
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
fun DrawerStatChip(text: String, color0: Color) {
    val color = themedAccentOr(color0)
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
fun DrawerStatChipH(text: String, color0: Color, modifier: Modifier = Modifier) {
    val color = themedAccentOr(color0)
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
                    DataChipSm("⏱️ ${fmtMinutes(session.minutes)}", Sky.copy(alpha = 0.15f), Sky, Modifier.width(chipWidth))
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
fun DataChip(text: String, bg0: Color, fg0: Color, modifier: Modifier = Modifier) {
    val bg = themedAccentOr(bg0); val fg = themedAccentOr(fg0)
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
fun DataChipSm(text: String, bg0: Color, fg0: Color, modifier: Modifier = Modifier) {
    val bg = themedAccentOr(bg0); val fg = themedAccentOr(fg0)
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
                        DataChip("⏱️ ${session.minutes} min", Sky.copy(alpha = 0.15f), Sky, Modifier.weight(1f))
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
    // QA 12-07 r2 (Aurora): el índigo global apenas contrasta sobre el fondo teal —
    // cuando el highlight es el acento, usar el acento DEL TEMA (morado en Aurora).
    val hl = if (highlightColor == Accent) accentForTheme(theme) else highlightColor
    val bgColor   = if (highlight) hl.copy(alpha = 0.07f) else theme.surface
    val brdColor  = if (highlight) hl.copy(alpha = 0.3f)  else theme.border
    val txtColor  = if (highlight) hl else theme.textMain
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
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val screenTitle = if (type == "genres") stringResource(R.string.bulk_refresh_title_genres)
        else stringResource(R.string.bulk_refresh_title_covers)
    val allBooks = books

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
        .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
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
    ) { uris ->
        if (uris.isNotEmpty()) {
            // Copiar imágenes a cache inmediatamente para que los URIs no se invaliden
            selectedImages = uris.take(3).mapNotNull { uri ->
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
                    val file = java.io.File(context.cacheDir, "feedback_img_${System.currentTimeMillis()}_${selectedImages.size}.jpg")
                    file.writeBytes(bytes)
                    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                } catch (_: Exception) { null }
            }
        }
    }

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
            val typeLabel = when (type) { "bug" -> "Bug"; "suggestion" -> "Sugerencia"; else -> "Otro" }
            val model = android.os.Build.MODEL
            val subject = "[FEEDBACK Lecturameter] $typeLabel - $model"
            val json = org.json.JSONObject().apply {
                put("type", type)
                put("subject", subject)
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
fun SettingsScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit, onBulkReload: (String) -> Unit = {}, onResetTutorial: () -> Unit = {}, onImportExport: () -> Unit = {}) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
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
                        // Feedback 2.6: Aurora muestra su icono PNG delante del nombre
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                        ) {
                            if (mode == ThemeMode.AURORA) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_theme_aurora),
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp).clip(RoundedCornerShape(3.dp))
                                )
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(
                                label, color = if (selected) Accent else theme.textMuted,
                                fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1
                            )
                        }
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

        // Feedback 13-07 (4): la entrada "Reordenar accesos del inicio" se ELIMINA de
        // Ajustes a petición de Víctor — la reordenación queda solo por long-press en el rail.

        // ── BACKUPS ───────────────────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_section_backups),
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
            // v2.4 rework: "Guardar ahora" y "Última copia" viven ahora en Ajustes
            HorizontalDivider(color = theme.border, modifier = Modifier.padding(bottom = 12.dp))
            var saveNowLocalRunning by remember { mutableStateOf(false) }
            var saveNowDriveRunning by remember { mutableStateOf(false) }
            var saveNowMsg by remember { mutableStateOf<String?>(null) }
            var lastLocalText by remember { mutableStateOf(formatLastLocalBackup(context, prefs)) }
            var lastDriveText by remember { mutableStateOf(DriveBackupManager.formatLastBackup(context, prefs)) }
            Text(stringResource(R.string.settings_save_now_title), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.local_backup_last, lastLocalText), color = theme.textMuted, fontSize = 12.sp)
            Text(stringResource(R.string.drive_backup_last, lastDriveText), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (!localBackupEnabled) { showActivateLocalDialog = true; return@OutlinedButton }
                        if (books.isEmpty()) { saveNowMsg = context.getString(R.string.msg_no_data_export); return@OutlinedButton }
                        saveNowLocalRunning = true; saveNowMsg = null
                        val req = OneTimeWorkRequestBuilder<JsonBackupWorker>().build()
                        val wm = WorkManager.getInstance(context)
                        wm.enqueueUniqueWork("lecturameter_json_backup_manual", ExistingWorkPolicy.REPLACE, req)
                        scope.launch {
                            val finalState = kotlinx.coroutines.withTimeoutOrNull(20_000) {
                                wm.getWorkInfosForUniqueWorkFlow("lecturameter_json_backup_manual")
                                    .mapNotNull { infos -> infos.firstOrNull { it.id == req.id } }
                                    .first { it.state.isFinished }
                            }
                            saveNowLocalRunning = false
                            lastLocalText = formatLastLocalBackup(context, prefs)
                            saveNowMsg = when {
                                finalState == null -> context.getString(R.string.msg_backup_pending)
                                finalState.state == androidx.work.WorkInfo.State.SUCCEEDED -> context.getString(R.string.msg_backup_saved_default)
                                else -> context.getString(R.string.msg_backup_error)
                            }
                        }
                    },
                    enabled = !saveNowLocalRunning,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (localBackupEnabled) Green else theme.border)
                ) {
                    if (saveNowLocalRunning) CircularProgressIndicator(color = Green, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Save, null, tint = if (localBackupEnabled) Green else theme.textDim, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_save_now_local), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (localBackupEnabled) Green else theme.textDim, maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        if (!driveBackupEnabled) { showActivateDriveDialog = true; return@OutlinedButton }
                        if (driveAccount == null) { driveSignInLauncher.launch(driveSignInClient.signInIntent); return@OutlinedButton }
                        if (books.isEmpty()) { saveNowMsg = context.getString(R.string.msg_no_data_export); return@OutlinedButton }
                        saveNowDriveRunning = true; saveNowMsg = null
                        scope.launch {
                            val result = DriveBackupManager.backup(context, prefs)
                            saveNowDriveRunning = false
                            lastDriveText = DriveBackupManager.formatLastBackup(context, prefs)
                            saveNowMsg = result.fold(
                                onSuccess = { context.getString(R.string.msg_drive_saved) },
                                onFailure = { context.getString(R.string.msg_drive_error, it.message ?: "") }
                            )
                        }
                    },
                    enabled = !saveNowDriveRunning,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (driveBackupEnabled) Color(0xFF4285F4) else theme.border)
                ) {
                    if (saveNowDriveRunning) CircularProgressIndicator(color = Color(0xFF4285F4), modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.CloudUpload, null, tint = if (driveBackupEnabled) Color(0xFF4285F4) else theme.textDim, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_save_now_drive), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (driveBackupEnabled) Color(0xFF4285F4) else theme.textDim, maxLines = 1)
                }
            }
            saveNowMsg?.let { msg ->
                Surface(shape = RoundedCornerShape(10.dp), color = if (msg.startsWith("✅")) Color(0x1A10B981) else Color(0x1AF59E0B), border = BorderStroke(1.dp, if (msg.startsWith("✅")) Color(0x4D10B981) else Color(0x4DF59E0B)), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(msg, color = if (msg.startsWith("✅")) Green else Amber, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                }
            }
            // Acceso a Importar/Exportar/Restaurar (Goodreads, CSV, JSON, Drive restore)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.border),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                onClick = onImportExport
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SwapVert, null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_import_export_title), color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_import_export_subtitle), color = theme.textMuted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted, modifier = Modifier.size(18.dp))
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
            title = stringResource(R.string.settings_section_widget),
            subtitle = stringResource(R.string.settings_widget_subtitle),
            expanded = sectWidget,
            onToggle = { val newVal = !sectWidget; sectWidget = newVal; prefs.edit().putBoolean("sect_widget_expanded", newVal).apply() },
            theme = theme
        ) {
            // v2.5: editar chips del widget sin quitarlo del launcher
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.border),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                onClick = {
                    context.startActivity(android.content.Intent(context, com.lecturameter.widget.WidgetConfigActivity::class.java))
                }
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🧩", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.widget_config_title), color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_widget_customize_sub), color = theme.textMuted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted, modifier = Modifier.size(18.dp))
                }
            }
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

// ── ChallengesScreen (v2.4 rework) ────────────────────────────────────────────
//
// Pantalla de retos de lectura: predeterminados + personalizados, con barra de
// progreso calculada en vivo desde libros/sesiones. Persistencia: prefs "challenges".

@Composable
fun challengeTypeLabel(type: ChallengeType): String = when (type) {
    ChallengeType.PAGES    -> stringResource(R.string.challenge_type_pages)
    ChallengeType.BOOKS    -> stringResource(R.string.challenge_type_books)
    ChallengeType.SESSIONS -> stringResource(R.string.challenge_type_sessions)
    ChallengeType.STREAK   -> stringResource(R.string.challenge_type_streak)
    ChallengeType.MINUTES  -> stringResource(R.string.challenge_type_minutes)
}

@Composable
fun ChallengesScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Challenge?>(null) }

    // ── Diálogo crear reto ────────────────────────────────────────────────────
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var typeSelected by remember { mutableStateOf(ChallengeType.BOOKS) }
        var targetText by remember { mutableStateOf("") }
        var titleFilterText by remember { mutableStateOf("") }
        var startDateText by remember { mutableStateOf("") }
        var endDateText by remember { mutableStateOf("") }
        var createError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = theme.bgMid,
            // Feedback 2.7: ancho fijo — el AlertDialog de M3 crece con el ancho intrínseco
            // del OutlinedTextField y, con un nombre de reto largo, se salía de la pantalla
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.92f),
            title = { Text(stringResource(R.string.challenge_create_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                // v2.4: scroll de respaldo para landscape móvil
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        placeholder = { Text(stringResource(R.string.challenge_name_hint), color = theme.textDim, fontSize = 13.sp) },
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Tipo de reto (chips)
                    Text(stringResource(R.string.challenge_type_label), color = theme.textMuted, fontSize = 12.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChallengeType.entries.chunked(3).forEach { rowTypes ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowTypes.forEach { t ->
                                    val selected = typeSelected == t
                                    Surface(
                                        onClick = { typeSelected = t },
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (selected) Accent.copy(alpha = 0.15f) else theme.surface,
                                        border = BorderStroke(1.dp, if (selected) Accent else theme.border),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            challengeTypeLabel(t),
                                            color = if (selected) Accent else theme.textMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it.filter { c -> c.isDigit() }; createError = "" },
                        placeholder = { Text(stringResource(R.string.challenge_target_hint), color = theme.textDim, fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (typeSelected == ChallengeType.BOOKS) {
                        OutlinedTextField(
                            value = titleFilterText,
                            onValueChange = { titleFilterText = it },
                            placeholder = { Text(stringResource(R.string.challenge_filter_hint), color = theme.textDim, fontSize = 13.sp) },
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Feedback 2.6: fecha de inicio opcional. Antes se imponía el día de creación,
                    // dejando fuera los libros ya terminados (retos de saga siempre a 0).
                    OutlinedTextField(
                        value = startDateText,
                        onValueChange = { startDateText = it; createError = "" },
                        placeholder = { Text(stringResource(R.string.challenge_start_date_hint), color = theme.textDim, fontSize = 13.sp) },
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = endDateText,
                        onValueChange = { endDateText = it; createError = "" },
                        placeholder = { Text(stringResource(R.string.challenge_end_date_hint), color = theme.textDim, fontSize = 13.sp) },
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.challenge_dates_note), color = theme.textDim, fontSize = 11.sp)
                    if (createError.isNotBlank()) Text(createError, color = Red, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = targetText.toIntOrNull()
                    val startParsed = if (startDateText.isBlank()) null else parseFlexibleDate(startDateText.trim())
                    val endParsed = if (endDateText.isBlank()) null else parseFlexibleDate(endDateText.trim())
                    when {
                        name.isBlank() -> { createError = context.getString(R.string.challenge_err_name); return@TextButton }
                        target == null || target < 1 -> { createError = context.getString(R.string.challenge_err_target); return@TextButton }
                        startDateText.isNotBlank() && startParsed == null -> { createError = context.getString(R.string.err_date_invalid_format); return@TextButton }
                        endDateText.isNotBlank() && endParsed == null -> { createError = context.getString(R.string.err_date_invalid_format); return@TextButton }
                        startParsed != null && endParsed != null && startParsed > endParsed -> { createError = context.getString(R.string.err_date_invalid_format); return@TextButton }
                    }
                    vm.addChallenge(
                        Challenge(
                            id = System.currentTimeMillis(),
                            name = name.trim(),
                            type = typeSelected,
                            target = target!!,
                            // Feedback 2.6: sin fecha de inicio → año natural (antes today(), que
                            // excluía todo lo terminado antes de crear el reto)
                            startDate = startParsed,
                            endDate = endParsed,
                            isDefault = false,
                            titleFilter = titleFilterText.trim().takeIf { it.isNotBlank() && typeSelected == ChallengeType.BOOKS }
                        ), prefs
                    )
                    showCreateDialog = false
                }) { Text(stringResource(R.string.challenge_save), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }

    // ── Diálogo borrar reto: Cancel = Accent, Delete = Red (convención) ───────
    deleteTarget?.let { challenge ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.challenge_delete_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.challenge_delete_text, challenge.name), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { vm.deleteChallenge(challenge.id, prefs); deleteTarget = null }) {
                    Text(stringResource(R.string.txt_5b5c9f9d), color = Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.txt_847607d7), color = Accent) } }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.challenge_title), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.challenge_subtitle), color = theme.textMuted, fontSize = 13.sp)
            }
        }

        // D-004: challenges es StateFlow; se colecciona en la raíz de la pantalla
        val challenges by vm.challenges.collectAsState()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
            if (challenges.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EmojiEvents, null, tint = theme.textDim, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.challenge_empty), color = theme.textDim, fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            items(challenges, key = { it.id }) { challenge ->
                val (current, target) = vm.challengeProgress(challenge)
                val ratio = (current.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
                val done = current >= target
                Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, if (done) Green.copy(alpha = 0.5f) else theme.border)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                // v2.5: los retos predeterminados se traducen al idioma actual
                                val displayName = if (challenge.isDefault) when (challenge.type) {
                                    ChallengeType.BOOKS  -> stringResource(R.string.challenge_default_books)
                                    ChallengeType.STREAK -> stringResource(R.string.challenge_default_streak)
                                    else -> challenge.name
                                } else challenge.name
                                Text(displayName, color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val meta = buildString {
                                    append(if (challenge.isDefault) stringResource(R.string.challenge_meta_default) else stringResource(R.string.challenge_meta_custom))
                                    append(" · ").append(challengeTypeLabel(challenge.type))
                                    challenge.startDate?.let { append(" · ").append(stringResource(R.string.challenge_meta_from, fmtDate(it))) }
                                    challenge.endDate?.let { append(" · ").append(stringResource(R.string.challenge_meta_until, fmtDate(it))) }
                                    challenge.titleFilter?.takeIf { it.isNotBlank() }?.let { append(" · «").append(it).append("»") }
                                }
                                Text(meta, color = theme.textMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(20.dp), color = if (done) Green.copy(alpha = 0.15f) else Accent.copy(alpha = 0.12f)) {
                                Text(
                                    stringResource(R.string.challenge_progress, current, target),
                                    color = if (done) Green else Accent,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { deleteTarget = challenge }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, stringResource(R.string.txt_5b5c9f9d), tint = Red.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressBar(ratio, if (done) Green else Accent, Modifier.fillMaxWidth())
                        if (done) {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.challenge_completed), color = Green, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Accent)
                ) {
                    Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.challenge_create_button), color = Accent, fontWeight = FontWeight.SemiBold)
                }
            }
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
                                if (totalMinutes > 0) Text("⏱️ ${formatMinutes(totalMinutes)}", color = theme.textDim, fontSize = 11.sp)
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
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val sessions by vm.sessions.collectAsState()
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

    val availableYears = sessions
        .mapNotNull { it.date.takeIf { d -> d.length >= 4 }?.take(4) }
        .filter { it.matches(Regex("\\d{4}")) }
        .toSortedSet().toList().reversed()
        .ifEmpty { listOf(java.time.LocalDate.now().year.toString()) }

    var selYear by remember { mutableStateOf(availableYears.first()) }
    var selMonth by remember { mutableStateOf<Int?>(null) }
    var showYearMenu by remember { mutableStateOf(false) }
    var showMonthMenu by remember { mutableStateOf(false) }

    val pagesByDate: Map<String, Int> = remember(sessions, selYear) {
        sessions
            .filter { it.date.startsWith(selYear) }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.sumOf { it.pages } }
    }

    // Pre-calcular libros por fecha — evita O(n*31) en la composición del heatmap
    val booksByDate: Map<String, List<Long>> = remember(sessions, selYear) {
        sessions
            .filter { it.date.startsWith(selYear) }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.map { it.bookId }.distinct() }
    }

    // Feedback 2.6: sesiones del período seleccionado (año y, si aplica, mes) para el
    // heatmap horario que ahora vive en esta pestaña.
    val periodSessions = remember(sessions, selYear, selMonth) {
        val mm = selMonth?.toString()?.padStart(2, '0')
        sessions.filter { s ->
            s.date.startsWith(selYear) &&
                (mm == null || (s.date.length >= 7 && s.date.substring(5, 7) == mm))
        }
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

            // Feedback 2.6: string localizado (antes hardcodeado en inglés)
            Text(stringResource(R.string.heatmap_tap_month, selYear), color = theme.textDim, fontSize = 11.sp,
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
                // Feedback 2.6: heatmap horario del per\u00edodo seleccionado, a ancho completo
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        HourlyHeatmapCard(periodSessions, theme)
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
                // Feedback 2.6: heatmap horario del mes seleccionado, a ancho completo
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        HourlyHeatmapCard(periodSessions, theme)
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
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val sessions by vm.sessions.collectAsState()
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

    val sessionsByBook = sessions.groupBy { it.bookId }

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
    val filteredSessions = sessions
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
    vm.books.value.filter { it.status == BookStatus.FINISHED }.forEach { b ->
        val days = if (b.startDate != null && b.endDate != null) daysBetween(b.startDate, b.endDate) else 0
        val ppd = if (days > 0) String.format("%.1f", b.pages.toDouble() / days) else ""
        sb.appendLine("\"${b.title}\",\"${b.author}\",${b.pages},\"${b.genres.joinToString("; ")}\",${b.rating},${b.startDate ?: ""},${b.endDate ?: ""},$days,$ppd")
    }
    return sb.toString()
}

private fun buildStatsJSON(vm: BooksViewModel): String {
    val arr = org.json.JSONArray()
    vm.books.value.filter { it.status == BookStatus.FINISHED }.forEach { b ->
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

