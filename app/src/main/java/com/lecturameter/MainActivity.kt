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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.layout.aspectRatio
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
    !theme.isDark                                      -> AccentLight   // Claro (único tema claro)
    theme.bgDark == BgDarkA                            -> AccentAurora  // Aurora
    else -> Accent
}
val Green   = Color(0xFF10B981); val Red     = Color(0xFFF87171)
val Amber   = Color(0xFFF59E0B); val Gold    = Color(0xFFFFBB33)
val Sky     = Color(0xFF0EA5E9)

val BgDarkD = Color(0xFF0F172A); val BgMidD = Color(0xFF1E1B4B)
// Feedback 14-07 (F5): borde del tema oscuro más visible (8% → 17% de blanco,
// contraste comparable al borde teal al 25% de Aurora)
val SurfaceD = Color(0x0DFFFFFF); val BorderD = Color(0x2BFFFFFF)
val TextMainD = Color(0xFFF1F5F9); val TextMutedD = Color(0xFF94A3B8); val TextDimD = Color(0xFF64748B)

// Fase 3 (Claro C2 "Híbrido", mockup aprobado 14-07): fondo frío menos gris que el anterior
// (#DDE3EC → #F4F4FB) y la mejora clave tomada de REX: tarjetas BLANCAS que despegan del fondo.
val BgDarkL = Color(0xFFF4F4FB); val BgMidL = Color(0xFFE8EAF6)
val SurfaceL = Color(0xFFFFFFFF); val BorderL = Color(0xFFCBD0E4)
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
    ThemeMode.LIGHT  -> Theme(BgDarkL,  BgMidL,  SurfaceL,  BorderL,  TextMainL,  TextMutedL,  TextDimL,  false, bgSurf = Color(0xFFFFFFFF), bgSurf2 = Color(0xFFE8EAF6))
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

internal fun refreshWidgetForBookIfSelected(context: Context, bookId: Long, clearCoverCache: Boolean = false) {
    val appContext = context.applicationContext
    if (com.lecturameter.widget.loadWidgetBook(appContext) != bookId) return
    if (clearCoverCache) com.lecturameter.widget.clearWidgetCoverCache(appContext)
    com.lecturameter.widget.requestBookWidgetUpdate(appContext)
}

internal fun showBookInWidget(context: Context, bookId: Long) {
    val appContext = context.applicationContext
    com.lecturameter.widget.saveWidgetBook(appContext, bookId)
    com.lecturameter.widget.clearWidgetCoverCache(appContext)
    com.lecturameter.widget.requestBookWidgetUpdate(appContext)
}

internal fun clearWidgetBookIfSelected(context: Context, bookId: Long) {
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
    // Feedback 14-07 (F6): query inicial para BookSearch (CTA «Buscar "X" en internet»).
    // Canal PROPIO: antes se reutilizaba pendingScannedIsbn y al volver a la lista el
    // observador del escáner mostraba el diálogo "ISBN escaneado: Sanderson"
    internal var pendingSearchQuery = androidx.compose.runtime.mutableStateOf<String?>(null)
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

        // Notificaciones de resúmenes (semanal domingo / mensual días 1-7 / wrapped
        // en ventana 26-dic→26-ene) — chequeo diario, cada aviso una vez por periodo
        val recapWorkRequest = PeriodicWorkRequestBuilder<RecapNotificationWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "lecturameter_recap_notifications",
            ExistingPeriodicWorkPolicy.UPDATE,
            recapWorkRequest
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
        // Feedback 13-07 (12): rotación del Bingo también al volver a primer plano —
        // cubre la app viva en recientes cruzando la medianoche del día 1. Comprobación
        // perezosa a propósito: nada de alarmas/workers (MIUI los mata y gastan batería).
        vm.ensureBingoCard(getSharedPreferences("lecturameter", MODE_PRIVATE))
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
    // Fase 5: recap semanal (R-1 + acceso A/C aprobados 14-07)
    object WeeklyRecap : Screen()
    object MonthlyRecap : Screen()
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
    is Screen.WeeklyRecap -> "weekly_recap"
    is Screen.MonthlyRecap -> "monthly_recap"
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

    // Deep link de las notificaciones de resúmenes:
    // "lecturameter://recap/{weekly|monthly|wrapped/{año}}"
    fun readRecapFromIntent(): Screen? {
        val uri = activity?.intent?.data ?: return null
        if (uri.scheme != "lecturameter" || uri.host != "recap") return null
        return when (uri.pathSegments.firstOrNull()) {
            "weekly" -> Screen.WeeklyRecap
            "monthly" -> Screen.MonthlyRecap
            "wrapped" -> Screen.Wrapped(
                uri.lastPathSegment?.toIntOrNull()
                    ?: wrappedWindowYear().takeIf { it != -1 }
                    ?: (Calendar.getInstance().get(Calendar.YEAR) - 1)
            )
            else -> null
        }
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
        // Prioridad 3: deep link del widget o de una notificación de recap
        } else {
            val recapScreen = readRecapFromIntent()
            val bookId = readBookIdFromIntent()
            if (recapScreen != null) {
                consumeIntentBookId()
                recapScreen
            } else if (bookId > 0L) {
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
        // Deep link del widget o de una notificación de recap (solo si es un intent
        // real, no la composición inicial)
        if (intentTrigger.value == 0) return@LaunchedEffect
        val recapScreen = readRecapFromIntent()
        if (recapScreen != null) {
            consumeIntentBookId()
            navigateTo(recapScreen)
            return@LaunchedEffect
        }
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
            // Fase 4 (D-003, 1A): transición slide+fade direccional ~220 ms — la pantalla
            // nueva entra deslizando ligeramente desde la derecha; al volver, desde la
            // izquierda. Sustituye al snap() de Fase 1.4 (B-005: None no era instantáneo;
            // con animación real el reloj de transición se usa de verdad y no hay solape).
            // v2.4: pantallas secundarias centradas en anchos ≥600dp via WideScreenCenter.
            val navSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(220)
            val navFadeSpec = androidx.compose.animation.core.tween<Float>(220)
            NavHost(
                navController = navController,
                startDestination = "list",
                enterTransition = { androidx.compose.animation.slideInHorizontally(navSpec) { it / 6 } + androidx.compose.animation.fadeIn(navFadeSpec) },
                exitTransition = { androidx.compose.animation.slideOutHorizontally(navSpec) { -it / 8 } + androidx.compose.animation.fadeOut(navFadeSpec) },
                popEnterTransition = { androidx.compose.animation.slideInHorizontally(navSpec) { -it / 6 } + androidx.compose.animation.fadeIn(navFadeSpec) },
                popExitTransition = { androidx.compose.animation.slideOutHorizontally(navSpec) { it / 8 } + androidx.compose.animation.fadeOut(navFadeSpec) }
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
                                onWeeklyRecap = { navigateTo(Screen.WeeklyRecap) },
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
                            // Feedback 14-07 (F6): la query de texto llega por su canal propio;
                            // pendingScannedIsbn queda solo para ISBNs reales del escáner
                            val isbnForSearch = bsMain?.pendingSearchQuery?.value
                                ?: bsMain?.pendingScannedIsbn?.value ?: ""
                            val isbnFromScanner = bsMain?.isbnFromScannerForBookSearch?.value
                            BookSearchScreen(
                                vm, prefs, theme,
                                onBack = { bsMain?.pendingSearchQuery?.value = null; bsMain?.pendingScannedIsbn?.value = null; bsMain?.isbnFromScannerForBookSearch?.value = null; goBack() },
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
                composable("stats") { WideScreenCenter { StatsScreen(vm, prefs, theme, onBack = { goBack() }, onWrapped = { y -> navigateTo(Screen.Wrapped(y)) }, onWrappedHistory = { navigateTo(Screen.WrappedHistory) }, onDetail = { navigateTo(Screen.Detail(it)) }, onDetailWithDate = { bookId, date -> navigateTo(Screen.Detail(bookId, date)) }, onDailySessions = { date -> navigateTo(Screen.DailySessions(date)) }, onWeeklyRecap = { navigateTo(Screen.WeeklyRecap) }, onMonthlyRecap = { navigateTo(Screen.MonthlyRecap) }) } }
                composable("weekly_recap") { WideScreenCenter { WeeklyRecapScreen(vm, theme, onBack = { goBack() }, onDetail = { navigateTo(Screen.Detail(it)) }) } }
                composable("monthly_recap") { WideScreenCenter { MonthlyRecapScreen(vm, prefs, theme, onBack = { goBack() }, onDetail = { navigateTo(Screen.Detail(it)) }) } }
                composable("import_export") { WideScreenCenter { ImportExportScreen(vm, prefs, theme, onBack = { goBack() }) } }
                composable("wrapped_history") { WideScreenCenter { WrappedHistoryScreen(vm, theme, onBack = { goBack() }, onOpen = { y -> navigateTo(Screen.Wrapped(y)) }) } }
                composable("settings") { WideScreenCenter { SettingsScreen(vm, prefs, theme, onBack = { goBack() }, onBulkReload = { type -> navigateTo(Screen.BulkReload(type)) }, onResetTutorial = { navigateTo(Screen.List) }, onImportExport = { navigateTo(Screen.ImportExport) }) } }
                composable("challenges") { WideScreenCenter { ChallengesScreen(vm, prefs, theme, onBack = { goBack() }) } }
                composable("bingo") { WideScreenCenter { BingoScreen(vm, prefs, theme, onBack = { goBack() }) } }
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

// ── Bingo (Fase 5, MD5): cartón 3×3 con plantillas rotativas mensuales ─────────
// Las celdas se marcan solas (ver BingoManager). Al completar el cartón entero
// antes de fin de mes se ofrece uno nuevo inmediatamente; si no, rota el día 1.
@Composable
fun BingoScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit) {
    BackHandler { onBack() }
    val card by vm.bingoCard.collectAsState()
    val books by vm.books.collectAsState()
    // Etiquetas del JSON en el idioma de la app (mismo criterio que el resto de la UI)
    val isEs = androidx.compose.ui.platform.LocalConfiguration.current.locales.get(0)?.language == "es"
    val accent = accentForTheme(theme)
    Box(modifier = Modifier.fillMaxSize().background(theme.bgDark).systemBarsPadding()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 28.dp, start = 16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = theme.textMain)
        }
        val c = card
        if (c == null) {
            // Sin cartón (no debería ocurrir: ensureBingoCard corre en load)
            Text("…", color = theme.textMuted, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        // Feedback 13-07 (10): cartón 4×4 (lado dinámico según la plantilla)
        val side = com.lecturameter.utils.BingoManager.sideOf(c.cells.size).coerceAtLeast(3)

        // ── Fase 4 (D-003, 4): flip + glow fusionados, SOLO la primera vez ──────────
        // La celda completada gira una vez y suelta un destello que se apaga; al cerrar
        // una línea, el glow la recorre y muere. Un set persistido por cartón recuerda
        // qué celdas/líneas ya animaron (al volver al Bingo se pintan quietas).
        // Con "reducir animaciones" del sistema se marca sin animar.
        val bingoCtx = androidx.compose.ui.platform.LocalContext.current
        val bingoReduceMotion = remember {
            android.provider.Settings.Global.getFloat(
                bingoCtx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
        var animatedCells by remember { mutableStateOf(prefs.getStringSet("bingo_anim_cells", emptySet())!!.toSet()) }
        var animatedLines by remember { mutableStateOf(prefs.getStringSet("bingo_anim_lines", emptySet())!!.toSet()) }
        fun markCellAnimated(k: String) { animatedCells = animatedCells + k; prefs.edit().putStringSet("bingo_anim_cells", animatedCells).apply() }
        fun markLineAnimated(k: String) { animatedLines = animatedLines + k; prefs.edit().putStringSet("bingo_anim_lines", animatedLines).apply() }
        val cellAnim = remember(c.monthKey, c.templateId) { List(c.cells.size) { androidx.compose.animation.core.Animatable(0f) } }
        val sweepAnim = remember(c.monthKey, c.templateId) { List(c.cells.size) { androidx.compose.animation.core.Animatable(0f) } }
        LaunchedEffect(c.completedLines, c.monthKey) {
            val pendingLines = c.completedLines.filter { "${c.monthKey}:${c.templateId}:$it" !in animatedLines }
            for (line in pendingLines) {
                val key = "${c.monthKey}:${c.templateId}:$line"
                if (!bingoReduceMotion) {
                    kotlinx.coroutines.delay(450)
                    for (i in com.lecturameter.utils.BingoManager.lineIndices(side, line)) {
                        launch {
                            sweepAnim[i].snapTo(0f)
                            sweepAnim[i].animateTo(1f, tween(300))
                            sweepAnim[i].animateTo(0f, tween(650))
                        }
                        kotlinx.coroutines.delay(150)
                    }
                }
                markLineAnimated(key)
            }
        }
        val doneCount = c.cells.count { it.isCompleted }
        val complete = doneCount == c.cells.size
        // Tipografías según densidad del cartón (4×4 necesita textos más compactos)
        val labelSize = if (side >= 4) 8.5.sp else 10.5.sp
        val labelLineHeight = if (side >= 4) 10.5.sp else 13.sp
        val checkSize = if (side >= 4) 15.sp else 20.sp
        // Nombre del mes del cartón a partir de monthKey (yyyy-MM), en el idioma de la app
        val monthLabel = remember(c.monthKey, isEs) {
            try {
                val d = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).parse(c.monthKey)
                java.text.SimpleDateFormat("LLLL yyyy", if (isEs) java.util.Locale("es") else java.util.Locale.ENGLISH)
                    .format(d!!).replaceFirstChar { it.uppercase() }
            } catch (_: Exception) { c.monthKey }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 76.dp, start = 16.dp, end = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.bingo_title), color = theme.textMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.bingo_subtitle, if (isEs) c.templateNameEs else c.templateNameEn, monthLabel),
                color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.bingo_progress, doneCount, c.cells.size, c.completedLines.size),
                color = theme.textMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(14.dp))
            // Cartón side×side
            for (row in 0 until side) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    for (col in 0 until side) {
                        val cellIdx = row * side + col
                        val cell = c.cells[cellIdx]
                        val bg by androidx.compose.animation.animateColorAsState(
                            targetValue = if (cell.isCompleted) accent.copy(alpha = 0.18f) else theme.bgMid,
                            animationSpec = tween(durationMillis = 300), label = "bingo_cell_bg"
                        )
                        // Fase 4 (D-003, 4): dispara el flip+glow SOLO si esta celda nunca animó
                        val cellKey = "${c.monthKey}:${c.templateId}:$cellIdx"
                        LaunchedEffect(cell.isCompleted, cellKey) {
                            if (cell.isCompleted && cellKey !in animatedCells) {
                                if (!bingoReduceMotion) {
                                    cellAnim[cellIdx].snapTo(0.001f)
                                    cellAnim[cellIdx].animateTo(1f, tween(1500))
                                    cellAnim[cellIdx].snapTo(0f)
                                }
                                markCellAnimated(cellKey)
                            }
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    val p = cellAnim[cellIdx].value
                                    if (p > 0f) {
                                        rotationY = 360f * (p / 0.38f).coerceAtMost(1f)
                                        cameraDistance = 12f * density
                                    }
                                }
                                .drawBehind {
                                    val p = cellAnim[cellIdx].value
                                    val glowA = if (p > 0.2f) (1f - kotlin.math.abs(p - 0.6f) / 0.4f).coerceIn(0f, 1f) else 0f
                                    val a = maxOf(glowA, sweepAnim[cellIdx].value)
                                    if (a > 0f) drawRoundRect(
                                        color = accent.copy(alpha = 0.6f * a),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx()),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                                    )
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(bg)
                                .border(
                                    width = if (cell.isCompleted) 1.5.dp else 1.dp,
                                    color = if (cell.isCompleted) accent else theme.border,
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(6.dp)
                            ) {
                                // Animación clave 4 (Fase 4): la celda celebra al completarse
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = cell.isCompleted,
                                    enter = androidx.compose.animation.scaleIn(
                                        animationSpec = androidx.compose.animation.core.spring(
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
                                        )
                                    ) + fadeIn()
                                ) {
                                    Text("✓", color = accent, fontSize = checkSize, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    if (isEs) cell.labelEs else cell.labelEn,
                                    color = if (cell.isCompleted) theme.textMain else theme.textMuted,
                                    fontSize = labelSize,
                                    lineHeight = labelLineHeight,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3
                                )
                                // Libro que completó la celda (si aplica)
                                cell.completedByBookId?.let { bid ->
                                    books.firstOrNull { it.id == bid }?.let { b ->
                                        Text(
                                            b.title, color = accent, fontSize = 7.5.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (complete) {
                Text(stringResource(R.string.bingo_completed), color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.ensureBingoCard(prefs, force = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.bingo_new_card), fontWeight = FontWeight.Bold) }
            } else {
                Text(stringResource(R.string.bingo_hint), color = theme.textMuted, fontSize = 11.5.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.bingo_renews), color = theme.textMuted, fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
        // Fase 6.1 (D-008, T5): primer cartón completado — tip de la rotación mensual
        var bingoTipVisible by remember { mutableStateOf(false) }
        LaunchedEffect(complete) {
            if (complete && !Tips.seen(prefs, Tips.BINGO_DONE) && !Tips.snackShownThisLaunch) {
                Tips.mark(prefs, Tips.BINGO_DONE)
                Tips.snackShownThisLaunch = true
                bingoTipVisible = true
            }
        }
        if (bingoTipVisible) {
            TipSnackbar(
                TipSnack(Tips.BINGO_DONE, stringResource(R.string.tip_bingo_title), stringResource(R.string.tip_bingo_body)),
                theme, onGone = { bingoTipVisible = false },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
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
    // Feedback 14-07: swipe → sobre 📜 abre el historial; swipe ← en cualquier punto
    // del rail (incluida la raya separadora, que forma parte de su borde) lo cierra
    onHistoryOpen: () -> Unit = {},
    onRailClose: () -> Unit = {},
) {
    var order by remember {
        mutableStateOf(
            prefs.getString("rail_order", null)
                ?.split(",")?.filter { it in RAIL_DEFAULT_ORDER }
                ?.let { saved -> saved + RAIL_DEFAULT_ORDER.filter { it !in saved } }
                ?: RAIL_DEFAULT_ORDER
        )
    }
    var editMode by remember { mutableStateOf(false) }
    val slotPx = with(androidx.compose.ui.platform.LocalDensity.current) { 46.dp.toPx() }

    fun railIcon(dest: String) = when (dest) {
        "challenges" -> Icons.Default.EmojiEvents
        "stats"      -> Icons.Default.BarChart
        "bingo"      -> Icons.Default.GridView
        else         -> Icons.Default.CardGiftcard
    }

    // Feedback 14-07: gestos del rail — swipe ← lo cierra (el gesto arranca en el propio
    // rail o en la raya que lo separa de los libros); swipe → no hace nada aquí (el de
    // reabrir vive en la franja del borde cuando está cerrado, ver call site)
    val railAcc = remember { mutableStateOf(0f) }
    Column(
        Modifier.width(46.dp).fillMaxHeight().padding(top = 4.dp)
            // Feedback 14-07 (F10): en landscape no caben los 6 iconos — la columna
            // scrollea en vertical para que todos sean alcanzables
            .verticalScroll(rememberScrollState())
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    railAcc.value += delta
                    if (railAcc.value < -60f) { onRailClose(); railAcc.value = 0f }
                },
                onDragStarted = { railAcc.value = 0f }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 📜 con gesto propio: swipe → abre el historial (además del tap); swipe ← cierra
        // el rail (consume el drag del padre, así que replica ese caso)
        val histAcc = remember { mutableStateOf(0f) }
        Box(
            Modifier.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    histAcc.value += delta
                    if (histAcc.value > 60f) { onHistoryOpen(); histAcc.value = 0f }
                    else if (histAcc.value < -60f) { onRailClose(); histAcc.value = 0f }
                },
                onDragStarted = { histAcc.value = 0f }
            )
        ) {
            RailItem("📜", theme, enabled = !editMode, onClick = onHistory)
        }
        RailItem("📚", theme, enabled = !editMode, onClick = onLibrary)
        HorizontalDivider(color = theme.border, thickness = 1.dp, modifier = Modifier.width(22.dp).padding(vertical = 3.dp))
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
        }
        if (editMode) {
            RailItem(null, theme, highlighted = true, icon = Icons.Default.Check, onClick = {
                prefs.edit().putString("rail_order", order.joinToString(",")).apply()
                editMode = false
            })
        }
    }
}

// Orden canónico de estantes (igual al que muestra la barra de pestañas)
internal val SHELF_ORDER = listOf(
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

// ── Fase 6.1 (D-008): tips contextuales de una sola vez ──────────────────────
// 7 tips en toda la vida de la app: rail · historial · primer libro terminado ·
// primer recap · bingo completado · sección no abierta · widget (1ª sesión).
// Formato B (card anclada) para gestos/UI; formato A (snackbar) para hitos.
// Un flag por tip en prefs; nunca dos snackbars en el mismo arranque;
// "Restablecer tutorial" también los resetea.
object Tips {
    const val RAIL = "tip_rail_shown"
    const val HISTORY = "tip_history_shown"
    const val FIRST_BOOK = "tip_first_book_shown"
    const val FIRST_RECAP = "tip_first_recap_shown"
    const val BINGO_DONE = "tip_bingo_done_shown"
    const val UNVISITED = "tip_unvisited_shown"
    const val WIDGET = "tip_widget_shown"
    val ALL = listOf(RAIL, HISTORY, FIRST_BOOK, FIRST_RECAP, BINGO_DONE, UNVISITED, WIDGET)
    /** Si dos hitos coinciden, el segundo espera a la siguiente apertura. */
    var snackShownThisLaunch = false
    fun seen(prefs: android.content.SharedPreferences, key: String) = prefs.getBoolean(key, false)
    fun mark(prefs: android.content.SharedPreferences, key: String) { prefs.edit().putBoolean(key, true).apply() }
    fun resetAll(prefs: android.content.SharedPreferences) {
        prefs.edit().apply { ALL.forEach { remove(it) } }.apply()
        snackShownThisLaunch = false
    }
}

/** Datos de un tip de formato A (snackbar con acción opcional). */
data class TipSnack(val key: String, val title: String, val body: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

/** Formato B: card destacada in-place, anclada al elemento del que habla. */
@Composable
fun TipCard(title: String, body: String, theme: Theme, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val acc = accentForTheme(theme)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = acc.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, acc.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 2.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, color = acc, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(body, color = theme.textMuted, fontSize = 11.5.sp, lineHeight = 15.sp)
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, tint = theme.textDim, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Formato A: snackbar inferior con acción; se descarta sola (~8 s), con ✕ o al actuar. */
@Composable
fun TipSnackbar(tip: TipSnack, theme: Theme, onGone: () -> Unit, modifier: Modifier = Modifier) {
    val acc = accentForTheme(theme)
    LaunchedEffect(tip.key) { kotlinx.coroutines.delay(8000); onGone() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = theme.bgMid,
        border = BorderStroke(1.dp, acc.copy(alpha = 0.5f)),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(tip.title, color = theme.textMain, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                if (tip.body.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(tip.body, color = theme.textMuted, fontSize = 11.5.sp, lineHeight = 15.sp)
                }
                if (tip.actionLabel != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tip.actionLabel, color = acc, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { tip.onAction?.invoke(); onGone() }
                    )
                }
            }
            IconButton(onClick = onGone, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, tint = theme.textDim, modifier = Modifier.size(14.dp))
            }
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
                BookCover(stringResource(R.string.tutorial_mock_cover_url), stringResource(R.string.tutorial_mock_book_title), size = 70)
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
                    Text(stringResource(R.string.tutorial_mock_book_title), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(Red.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Delete, null, tint = Red.copy(alpha = 0.7f), modifier = Modifier.size(11.dp)) }
                }
                Text(stringResource(R.string.tutorial_mock_book_author), color = theme.textMuted, fontSize = 12.sp)
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
        // Fase 4 (D-009, 14-07): rework 10 → 5 slides FILOSÓFICAS (mockup r2 aprobado).
        // El tutorial ya no explica features (eso son los tips contextuales de Fase 6):
        // valor → básico → promesa → privacidad → batería. Textos estilo Augur, 1-2 frases.
        // La slide 2 llevará captura real POR IDIOMA al cerrar F4; hasta entonces usa el
        // mock de tarjeta (theme-aware y traducido). Emojis provisionales ("ya se cambiarán").
        val tutContext = androidx.compose.ui.platform.LocalContext.current
        val pages = listOf(
            // 1 — Propuesta de valor
            TutorialPage("📚", stringResource(R.string.tut5_value_title), stringResource(R.string.tut5_value_desc)),
            // 2 — Lo mínimo para empezar (captura real por idioma pendiente de F4)
            TutorialPage("＋", stringResource(R.string.tut5_basics_title), stringResource(R.string.tut5_basics_desc),
                visual = { TutorialBookCardVisual(theme) }),
            // 3 — La promesa (emoji provisional 🧭)
            TutorialPage("🧭", stringResource(R.string.tut5_promise_title), stringResource(R.string.tut5_promise_desc)),
            // 4 — Privacidad como identidad
            TutorialPage("🔒", stringResource(R.string.tut5_privacy_title), stringResource(R.string.tut5_privacy_desc)),
            // 5 — Batería, con CTA que abre los ajustes del sistema
            TutorialPage("🔋", stringResource(R.string.tut5_battery_title), "",
                descriptionComposable = { th ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.tut5_battery_desc),
                            color = th.textMuted, fontSize = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            onClick = {
                                try {
                                    tutContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                } catch (_: Exception) {
                                    try {
                                        tutContext.startActivity(android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.parse("package:" + tutContext.packageName)))
                                    } catch (_: Exception) { }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x26F59E0B),
                            border = BorderStroke(1.dp, Color(0x80F59E0B))
                        ) {
                            Text(
                                stringResource(R.string.tut5_battery_cta),
                                color = Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                })
        )
        val pagerState = androidx.compose.foundation.pager.rememberPagerState { pages.size }
        // D-009: el atrás del sistema retrocede de slide (en la 1 no intercepta)
        BackHandler(enabled = pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // D-009: barra de progreso superior por segmentos (sustituye al contador y a
            // los puntitos de abajo — feedback 14-07)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.widthIn(max = footerMax).fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
            ) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i <= pagerState.currentPage) Accent else theme.border)
                    )
                }
            }
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TutorialPageContent(pages[page], theme, isLandscape)
                }
            }

            // D-009 (feedback 14-07): contador y puntitos ELIMINADOS — la posición la
            // indica solo la barra de segmentos superior.
            Spacer(Modifier.height(16.dp))

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
    onEasterEgg: () -> Unit = {},
    onWeeklyRecap: () -> Unit = {}
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
    // Feedback 14-07 (F8): estos efectos hacían scroll-a-0 también en su PRIMERA
    // ejecución — es decir, al VOLVER del detalle (el grid restaura su posición pero
    // estos la machacaban). Se saltan la primera pasada y solo actúan ante cambios.
    var sortEffectArmed by remember { mutableStateOf(false) }
    LaunchedEffect(sortOrder) {
        vm.savedSortOrder = sortOrder
        prefs.edit().putString("sort_order", sortOrder.name).apply()
        if (sortEffectArmed) listScope.launch { listState.animateScrollToItem(0) }
        else sortEffectArmed = true
    }
    var tabEffectArmed by remember { mutableStateOf(false) }
    LaunchedEffect(activeTab) {
        if (tabEffectArmed) listScope.launch { listState.animateScrollToItem(0) }
        else tabEffectArmed = true
    }
    // v2.4 rework: al activar/desactivar el filtro de favoritos, volver arriba
    var favEffectArmed by remember { mutableStateOf(false) }
    LaunchedEffect(vm.showFavoritesOnly) {
        if (favEffectArmed) listScope.launch { listState.animateScrollToItem(0) }
        else favEffectArmed = true
    }

    // Feedback 14-07 v2: rail auto-hide en scroll con UMBRAL acumulado (la versión
    // por-evento parpadeaba con cualquier temblor del dedo). Además el rail se puede
    // cerrar a mano (swipe ← desde el rail/raya) y reabrir (swipe → desde el borde).
    // Cierre manual = pegajoso: el scroll no lo reabre hasta que se reabra a mano.
    var railVisible by rememberSaveable { mutableStateOf(true) }
    var railManuallyHidden by rememberSaveable { mutableStateOf(false) }
    val hideThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 32.dp.toPx() }
    LaunchedEffect(listState) {
        var lastIdx = listState.firstVisibleItemIndex
        var lastOff = listState.firstVisibleItemScrollOffset
        var acc = 0f
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (idx, off) ->
                // Aproximación del delta: dentro del mismo item es exacto; al cambiar
                // de item se asume el signo del cambio con un paso grande
                val delta = when {
                    idx == lastIdx -> (off - lastOff).toFloat()
                    idx > lastIdx  -> hideThresholdPx
                    else           -> -hideThresholdPx
                }
                lastIdx = idx; lastOff = off
                if (idx == 0 && off == 0) {
                    acc = 0f
                    if (!railManuallyHidden) railVisible = true
                } else {
                    // El acumulador se resetea al cambiar de dirección
                    acc = if (delta > 0 == acc > 0) acc + delta else delta
                    if (acc > hideThresholdPx) { railVisible = false; acc = 0f }
                    else if (acc < -hideThresholdPx) {
                        if (!railManuallyHidden) railVisible = true
                        acc = 0f
                    }
                }
            }
    }
    // Feedback 14-07 (F4): al DEJAR de hacer scroll el rail vuelve solo (si no está
    // cerrado a mano) — el auto-hide es solo mientras el dedo mueve la lista
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling && !railManuallyHidden) railVisible = true
            }
    }
    val railWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (railVisible) 46.dp else 0.dp,
        animationSpec = tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "rail_auto_hide"
    )

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

    // ── Fase 6.1 (D-008): tips contextuales — estado y selector ──────────────────
    val sessionsForTips by vm.sessions.collectAsState()
    var railTipVisible by remember { mutableStateOf(!Tips.seen(prefs, Tips.RAIL)) }
    var historyTipVisible by remember { mutableStateOf(!Tips.seen(prefs, Tips.HISTORY)) }
    var activeTipSnack by remember { mutableStateOf<TipSnack?>(null) }
    // Marca del primer arranque (para el tip de sección no abierta tras N días)
    LaunchedEffect(Unit) {
        if (!prefs.contains("first_launch_ts")) prefs.edit().putLong("first_launch_ts", System.currentTimeMillis()).apply()
    }
    val tipFirstBook = TipSnack(Tips.FIRST_BOOK, stringResource(R.string.tip_first_book_title), stringResource(R.string.tip_first_book_body), stringResource(R.string.tip_first_book_action)) { onStats() }
    val tipWidget = TipSnack(Tips.WIDGET, stringResource(R.string.tip_widget_title), stringResource(R.string.tip_widget_body))
    val tipRecap = TipSnack(Tips.FIRST_RECAP, stringResource(R.string.tip_recap_title), stringResource(R.string.tip_recap_body), stringResource(R.string.tip_recap_action)) { onWeeklyRecap() }
    val tipUnvisited = TipSnack(Tips.UNVISITED, stringResource(R.string.tip_unvisited_title), "", stringResource(R.string.tip_unvisited_action)) { onStats() }
    // Fase 6.2: micro-encuesta voluntaria (invitación por el mismo canal de snackbars)
    var showSurveyDialog by remember { mutableStateOf(false) }
    var showSurveyFeedback by remember { mutableStateOf(false) }
    val surveyInvite = TipSnack("survey", stringResource(R.string.survey_invite_title), stringResource(R.string.survey_invite_body), stringResource(R.string.survey_invite_action)) { showSurveyDialog = true }
    LaunchedEffect(booksAll, sessionsForTips, railTipVisible) {
        // Uno por arranque; la card del rail tiene prioridad y silencia los snackbars
        if (Tips.snackShownThisLaunch || railTipVisible || activeTipSnack != null || booksAll.isEmpty()) return@LaunchedEffect
        val finishedCount = booksAll.count { it.status == BookStatus.FINISHED }
        val recapReady = com.lecturameter.utils.computeWeeklyRecap(booksAll, sessionsForTips, vm.bingoCard.value, vm.challenges.value, today()) != null
        val daysSinceFirst = (System.currentTimeMillis() - prefs.getLong("first_launch_ts", System.currentTimeMillis())) / 86_400_000L
        val candidate = when {
            !Tips.seen(prefs, Tips.FIRST_BOOK) && finishedCount == 1 -> tipFirstBook
            !Tips.seen(prefs, Tips.WIDGET) && sessionsForTips.size == 1 -> tipWidget
            !Tips.seen(prefs, Tips.FIRST_RECAP) && recapReady -> tipRecap
            !Tips.seen(prefs, Tips.UNVISITED) && !prefs.getBoolean("stats_opened", false) && daysSinceFirst >= 7 -> tipUnvisited
            else -> null
        }
        if (candidate != null) {
            Tips.mark(prefs, candidate.key)
            Tips.snackShownThisLaunch = true
            activeTipSnack = candidate
        } else {
            // ── Fase 6.2: la invitación de la micro-encuesta usa el mismo hueco ──
            // (un solo snackbar por arranque; los tips tienen prioridad)
            val now = System.currentTimeMillis()
            val first = prefs.getLong("first_launch_ts", now)
            val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            if (prefs.getInt("survey_year", 0) != year) {
                prefs.edit().putInt("survey_year", year).putInt("survey_count_year", 0).apply()
            }
            val eligible = finishedCount >= 5 || now - first >= 60L * 86_400_000
            val cooldownOk = now - prefs.getLong("survey_last_ts", 0L) >= 120L * 86_400_000
            val snoozeOk = now >= prefs.getLong("survey_snooze_until", 0L)
            if (eligible && cooldownOk && snoozeOk && prefs.getInt("survey_count_year", 0) < 3) {
                Tips.snackShownThisLaunch = true
                activeTipSnack = surveyInvite
            }
        }
    }

    // ── Fase 6.2: diálogo de la encuesta + acceso al feedback completo ──────────
    if (showSurveyDialog) {
        SurveyDialog(
            theme, prefs,
            onDismiss = {
                showSurveyDialog = false
                prefs.edit().putLong("survey_snooze_until", System.currentTimeMillis() + 60L * 86_400_000).apply()
            },
            onOpenFeedback = { showSurveyDialog = false; showSurveyFeedback = true }
        )
    }
    if (showSurveyFeedback) {
        FeedbackDialog(theme, onDismiss = { showSurveyFeedback = false })
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
                    // Feedback 14-07 (F13a): si el crono YA corre para este libro, la fila
                    // lo indica (borde ámbar + icono de crono en vez de ▶)
                    val timerActiveHere = TimerStateHolder.running && TimerStateHolder.activeBookId == b.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = theme.surface,
                        border = BorderStroke(1.dp, if (timerActiveHere) Amber else theme.border),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            BookCover(b.coverUrl, b.title, size = 34, isbnFallback = b.isbn)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(b.title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (timerActiveHere) {
                                    Text(
                                        stringResource(R.string.quickstart_session_running),
                                        color = Amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        statusLabel(if (b.status == BookStatus.REREADING || b.isRereading) BookStatus.REREADING else BookStatus.READING),
                                        color = statusColor(if (b.status == BookStatus.REREADING || b.isRereading) BookStatus.REREADING else BookStatus.READING),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (timerActiveHere) Amber else Green)
                                    .clickable {
                                        showQuickStartSheet = false
                                        // Con el crono ya corriendo en este libro, solo navegar
                                        if (!timerActiveHere) TimerQuickStart.pendingBookId = b.id
                                        onDetail(b.id)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (timerActiveHere) Icons.Default.Timer else Icons.Default.PlayArrow,
                                    contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)
                                )
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
            if (railWidth > 0.dp) {
            Box(Modifier.width(railWidth)) {
            HomeRail(
                theme = theme,
                prefs = prefs,
                onHistory = { historyOpen = !historyOpen },
                onLibrary = {
                    historyOpen = false
                    listScope.launch { listState.animateScrollToItem(0) }
                },
                onStats = { historyOpen = false; onStats() },
                onChallenges = { historyOpen = false; onChallenges() },
                onBingo = { historyOpen = false; onEasterEgg() },
                onWrapped = { historyOpen = false; onWrappedHistory() },
                onHistoryOpen = { historyOpen = true },
                onRailClose = { railManuallyHidden = true; railVisible = false },
            )
            }
            Box(Modifier.fillMaxHeight().width(1.dp).background(theme.border))
            }
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

        // ── Fase 6.1 (D-008, T1): card del rail — primera vez en el home ─────
        if (railTipVisible) {
            TipCard(
                stringResource(R.string.tip_rail_title), stringResource(R.string.tip_rail_body), theme,
                onDismiss = { Tips.mark(prefs, Tips.RAIL); railTipVisible = false },
                modifier = Modifier.padding(start = 10.dp, end = 16.dp, top = 6.dp)
            )
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
                                            // Feedback 14-07 (F6): canal propio de query — NO
                                            // pendingScannedIsbn (disparaba el diálogo del escáner)
                                            listMainRef?.pendingSearchQuery?.value = searchQuery
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
        } // Box contenido (D-002)
        } // Row rail + contenido (D-002)
    } // Column (header + contenido)

    // Feedback 14-07: rail cerrado → swipe → desde el borde izquierdo lo reabre
    // (franja invisible de 16dp; solo existe con el rail oculto, no roba gestos)
    if (!railVisible && !historyOpen) {
        val reopenAcc = remember { mutableStateOf(0f) }
        Box(
            Modifier.align(Alignment.CenterStart).width(16.dp).fillMaxHeight()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        reopenAcc.value += delta
                        if (reopenAcc.value > 60f) {
                            railManuallyHidden = false
                            railVisible = true
                            reopenAcc.value = 0f
                        }
                    },
                    onDragStarted = { reopenAcc.value = 0f }
                )
        )
    }

    // Feedback 14-07: historial CASI a pantalla completa — vive al nivel del Box raíz
    // (dentro del Column con weight le quedaba altura 0 y no se veía nada).
    // Deja ~16dp a la derecha con el scrim asomando: pista visual de que el panel
    // se cierra con swipe ← (tocar la franja también cierra). Pendiente de explicarse
    // en el onboarding contextual (Fase 6).
    if (historyOpen) {
        Box(Modifier.fillMaxSize().background(Color(0x88000000)).clickable { historyOpen = false })
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = historyOpen,
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it })
    ) {
        val closeAcc = remember { mutableStateOf(0f) }
        Box(
            Modifier
                .fillMaxSize()
                // Feedback 14-07: mismo hueco que dejaba el rail (46dp) — el home asoma
                // detrás y la pista de "esto se cierra deslizando" es inequívoca
                .padding(end = 46.dp)
                .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                .border(1.dp, theme.border, RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
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
                onDetail = { id -> historyOpen = false; onDetail(id) },
                // Fase 6.1 (D-008, T2): primera apertura del historial — cómo se cierra
                showCloseTip = historyTipVisible,
                onDismissCloseTip = { Tips.mark(prefs, Tips.HISTORY); historyTipVisible = false }
            )
        }
    }
    // v2.4 rework: host del Snackbar de favoritos, superpuesto al contenido
    androidx.compose.material3.SnackbarHost(
        hostState = favSnackbarState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
    )
    // Fase 6.1 (D-008): snackbar de tips (formato A) — hitos de datos.
    // 6.2: si es la invitación de la encuesta, descartarla pospone 2 meses.
    activeTipSnack?.let { t ->
        TipSnackbar(
            t, theme,
            onGone = {
                if (t.key == "survey") prefs.edit().putLong("survey_snooze_until", System.currentTimeMillis() + 60L * 86_400_000).apply()
                activeTipSnack = null
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).padding(bottom = 14.dp)
        )
    }
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


@Composable
fun StatsScreen(vm: BooksViewModel, _prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit, onWrapped: (Int) -> Unit, onWrappedHistory: () -> Unit, onDetail: (Long) -> Unit = {}, onDetailWithDate: (Long, String) -> Unit = { _, _ -> }, onDailySessions: (String) -> Unit = {}, onWeeklyRecap: () -> Unit = {}, onMonthlyRecap: () -> Unit = {}) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    // Fase 6.1 (D-008, T6): registrar que Estadísticas ya se ha abierto
    LaunchedEffect(Unit) { _prefs.edit().putBoolean("stats_opened", true).apply() }
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

        // ── Fase 5 (P-023, acceso A): tarjeta compacta del recap semanal ─────────
        // Solo aparece si la semana en curso tiene alguna sesión (regla: no inventar)
        val weeklyRecap = remember(books, sessions) {
            com.lecturameter.utils.computeWeeklyRecap(books, sessions, vm.bingoCard.value, vm.challenges.value, today())
        }
        if (weeklyRecap != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.border),
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable { onWeeklyRecap() }
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.recap_card_line, weeklyRecap.pages, weeklyRecap.sessionsCount),
                        color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted)
                }
            }
        }

        // ── Fase 6.4 (M-2): tarjeta del recap MENSUAL — solo los primeros 7 días ─
        // del mes nuevo, y solo si el mes cerrado tuvo sesiones
        val monthlyRecap = remember(books, sessions) {
            val dayOfMonth = today().takeLast(2).toIntOrNull() ?: 99
            if (dayOfMonth <= 7) com.lecturameter.utils.computeMonthlyRecap(books, sessions, today()) else null
        }
        if (monthlyRecap != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, accentForTheme(theme).copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable { onMonthlyRecap() }
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.recapm_card_line, fmtMonthName(monthlyRecap.monthKey)),
                        color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted)
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
internal fun readableFolderName(treeUriStr: String): String = try {
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

// ── Fase 6.2: micro-encuesta voluntaria (mockup E-2 aprobado 14-07) ───────────
// Una pregunta rotativa de opción única; envío por la misma Cloud Function del
// feedback (type "survey"). NO es telemetría: solo se envía al pulsar Enviar.
@Composable
fun SurveyDialog(theme: Theme, prefs: android.content.SharedPreferences, onDismiss: () -> Unit, onOpenFeedback: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val questions = listOf(
        Triple("section", R.string.survey_q_section, listOf(
            "library" to R.string.survey_o_library, "stats" to R.string.survey_o_stats,
            "challenges" to R.string.survey_o_challenges, "bingo" to R.string.survey_o_bingo,
            "wrapped" to R.string.survey_o_wrapped)),
        Triple("missing", R.string.survey_q_missing, listOf(
            "challenges" to R.string.survey_o_more_challenges, "stats" to R.string.survey_o_more_stats,
            "social" to R.string.survey_o_social, "nothing" to R.string.survey_o_nothing)),
        Triple("recommend", R.string.survey_q_recommend, listOf(
            "yes" to R.string.survey_o_yes, "no" to R.string.survey_o_no, "not_yet" to R.string.survey_o_not_yet))
    )
    val idx = remember { prefs.getInt("survey_index", 0).mod(questions.size) }
    val (qid, qRes, options) = questions[idx]
    var selected by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val sentMsg = stringResource(R.string.survey_sent_toast)
    val failMsg = stringResource(R.string.err_feedback_send_retry)

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        containerColor = theme.bgMid,
        title = { Text(stringResource(qRes), color = theme.textMain, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(stringResource(R.string.survey_privacy), color = theme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                options.forEach { (key, res) ->
                    val sel = selected == key
                    Surface(
                        onClick = { selected = key },
                        shape = RoundedCornerShape(11.dp),
                        color = if (sel) Accent.copy(alpha = 0.14f) else theme.surface,
                        border = BorderStroke(1.dp, if (sel) Accent else theme.border),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Text(
                            stringResource(res),
                            color = if (sel) Accent else theme.textMain, fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
                Text(
                    stringResource(R.string.survey_more_link), color = Accent, fontSize = 11.5.sp,
                    modifier = Modifier.clickable(enabled = !sending) { onOpenFeedback() }.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null && !sending, onClick = {
                sending = true
                scope.launch {
                    val info = "App: Lecturameter ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Package: ${BuildConfig.APPLICATION_ID}\n" +
                        "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        FeedbackSender.send("survey", "Q: $qid\nA: $selected", info, null, emptyList())
                    }
                    sending = false
                    if (ok) {
                        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                        prefs.edit()
                            .putLong("survey_last_ts", System.currentTimeMillis())
                            .putInt("survey_index", idx + 1)
                            .putInt("survey_year", year)
                            .putInt("survey_count_year", prefs.getInt("survey_count_year", 0) + 1)
                            .apply()
                        android.widget.Toast.makeText(context, sentMsg, android.widget.Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        android.widget.Toast.makeText(context, failMsg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                if (sending) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                else Text(stringResource(R.string.survey_send), color = Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) {
                Text(stringResource(R.string.survey_not_now), color = theme.textMuted)
            }
        }
    )
}

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
            // 14-07: el refac comparte versionName con la 2.7 — el package distingue origen
            append("Package: ${BuildConfig.APPLICATION_ID}\n")
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
                onClick = { vm.resetTutorial(prefs); Tips.resetAll(prefs); onResetTutorial() }
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ChallengesScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Challenge?>(null) }

    // Feedback 14-07: nombre traducido de los retos por defecto (el sembrado congela
    // el nombre en el idioma del momento) — usado en la tarjeta Y en el diálogo de borrar
    @Composable
    fun challengeDisplayName(challenge: Challenge): String = if (challenge.isDefault) when (challenge.type) {
        ChallengeType.BOOKS    -> stringResource(R.string.challenge_default_books)
        ChallengeType.STREAK   -> stringResource(R.string.challenge_default_streak)
        ChallengeType.PAGES    -> stringResource(R.string.challenge_default_pages)
        ChallengeType.SESSIONS -> stringResource(R.string.challenge_default_sessions)
        ChallengeType.MINUTES  -> stringResource(R.string.challenge_default_minutes)
    } else challenge.name

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
            text = { Text(stringResource(R.string.challenge_delete_text, challengeDisplayName(challenge)), color = theme.textMuted, fontSize = 13.sp) },
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
        // Feedback 13-07 (11): sin scroll infinito — los retos van en PÁGINAS horizontales,
        // con dot-pagination (mismo estilo que el tutorial). El botón de crear queda
        // fijo abajo, fuera del pager. Feedback 14-07 (F3): caben 5 por página.
        val perPage = 5
        if (challenges.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EmojiEvents, null, tint = theme.textDim, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.challenge_empty), color = theme.textDim, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            val pageCount = (challenges.size + perPage - 1) / perPage
            // El lambda relee `challenges` (delegado de State): si borras retos y baja el
            // nº de páginas, el pager se ajusta solo
            val pagerState = androidx.compose.foundation.pager.rememberPagerState { (challenges.size + perPage - 1) / perPage }
            // Feedback 14-07 (F15): reajuste EN VIVO — al borrar el último reto de la
            // última página, currentPage quedaba fuera de rango y la pantalla se veía
            // vacía hasta reentrar. Clamp inmediato a la última página válida.
            LaunchedEffect(pageCount) {
                if (pagerState.currentPage >= pageCount) {
                    pagerState.animateScrollToPage((pageCount - 1).coerceAtLeast(0))
                }
            }
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.weight(1f)
            ) { page ->
                val pageItems = challenges.drop(page * perPage).take(perPage)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    pageItems.forEach { challenge ->
                val (current, target) = vm.challengeProgress(challenge)
                val ratio = (current.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
                val done = current >= target
                Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, if (done) Green.copy(alpha = 0.5f) else theme.border)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                // v2.5 + Feedback 14-07: los retos predeterminados se traducen
                                // al idioma actual (los 5, no solo libros/racha)
                                val displayName = challengeDisplayName(challenge)
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
                    } // forEach pageItems
                } // Column página
            } // HorizontalPager
            // Dot-pagination (solo con más de una página)
            if (pageCount > 1) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(pageCount) { i ->
                            Box(
                                Modifier
                                    .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (i == pagerState.currentPage) Accent else theme.border)
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
        OutlinedButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 0.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Accent)
        ) {
            Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.challenge_create_button), color = Accent, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(28.dp))
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

