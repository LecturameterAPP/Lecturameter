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
    theme.bgDark == BgDarkC                            -> AccentCuero   // Cuero (D-015)
    else -> Accent
}

fun isCueroTheme(theme: Theme): Boolean = theme.bgDark == BgDarkC

// D-015 r3: en Cuero los iconos de rail y acciones van en oro suave FIJO (el azul Material
// quedaba raro sobre marrón+dorado); en el resto de temas se mantiene el azul de siempre.
fun actionIconTint(theme: Theme): Color = if (isCueroTheme(theme)) GoldIconCuero else Accent
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

// Cuero (D-015, mockup r3 aprobado 16-07): cuero marrón con oro imitando encuadernaciones
// en piel. Tres stops de fondo (bgDeep), tarjeta sólida, y DOS intensidades de oro:
// el acento (#D9AC5C) para numerales/marcos y el oro suave (#C7A055) para los iconos de
// rail y acciones — un paso por debajo para mantener la jerarquía (nada de azul en este tema).
val BgDarkC = Color(0xFF281B10); val BgMidC = Color(0xFF1E140A); val BgDeepC = Color(0xFF120B05)
val SurfaceC = Color(0xFF291D10); val BorderC = Color(0xFF54401E)
val TextMainC = Color(0xFFFAF3E3); val TextMutedC = Color(0xFFD6C7A5); val TextDimC = Color(0xFF9C8B66)
val AccentCuero = Color(0xFFD9AC5C)
val GoldIconCuero = Color(0xFFC7A055)
val GoldSoftCuero = Color(0xFF7E6230)

// QA r2 12-07: el tema Dinámico (Material You) se ELIMINA a petición de Víctor —
// los 4 temas quedan fijos: Claro, Oscuro, Aurora y AMOLED.
enum class ThemeMode(val value: String) {
    LIGHT("light"), DARK("dark"), AURORA("aurora"), AMOLED("amoled"), CUERO("cuero")
}

// Fase 3: bgDeep = tercer stop del degradado de fondo (solo Aurora lo usa; Transparent = 2 stops clásicos)
// accent = acento propio del tema (solo Dinámico lo define; null = accentForTheme decide por bgDark)
data class Theme(val bgDark: Color, val bgMid: Color, val surface: Color, val border: Color, val textMain: Color, val textMuted: Color, val textDim: Color, val isDark: Boolean, val bgSurf: Color = Color.Transparent, val bgSurf2: Color = Color.Transparent, val bgDeep: Color = Color.Transparent, val accent: Color? = null)

fun buildTheme(mode: ThemeMode) = when (mode) {
    ThemeMode.LIGHT  -> Theme(BgDarkL,  BgMidL,  SurfaceL,  BorderL,  TextMainL,  TextMutedL,  TextDimL,  false, bgSurf = Color(0xFFFFFFFF), bgSurf2 = Color(0xFFE8EAF6))
    ThemeMode.DARK   -> Theme(BgDarkD,  BgMidD,  SurfaceD,  BorderD,  TextMainD,  TextMutedD,  TextDimD,  true,  bgSurf = Color(0x1AFFFFFF), bgSurf2 = Color(0x0DFFFFFF))
    ThemeMode.AURORA -> Theme(BgDarkA,  BgMidA,  SurfaceA,  BorderA,  TextMainA,  TextMutedA,  TextDimA,  true,  bgSurf = Color(0x12FFFFFF), bgSurf2 = Color(0x08FFFFFF), bgDeep = BgDeepA)
    ThemeMode.AMOLED -> Theme(BgDarkAm, BgMidAm, SurfaceAm, BorderAm, TextMainAm, TextMutedAm, TextDimAm, true,  bgSurf = Color(0x0FFFFFFF), bgSurf2 = Color(0x06FFFFFF))
    // bgSurf/bgSurf2 en crema translúcida (no blanco frío) para que las superficies
    // secundarias no rompan la calidez del cuero
    ThemeMode.CUERO  -> Theme(BgDarkC,  BgMidC,  SurfaceC,  BorderC,  TextMainC,  TextMutedC,  TextDimC,  true,  bgSurf = Color(0x14FAF3E3), bgSurf2 = Color(0x0AFAF3E3), bgDeep = BgDeepC)
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
        // D-013: conectar Play Billing (restaura compras de lecturameter_pro si las hay)
        LmBilling.init(this)
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

// D-015 (Cuero): filete dorado interior — una línea de 1dp en oro suave, 3dp por dentro
// del borde de la tarjeta (solo tarjetas grandes: libro del home y ediciones del detalle).
// Con cualquier otro tema el modifier es un no-op.
fun Modifier.cueroFilete(theme: Theme, corner: androidx.compose.ui.unit.Dp): Modifier =
    if (!isCueroTheme(theme)) this else this.drawWithContent {
        drawContent()
        val inset = 3.dp.toPx()
        val r = (corner.toPx() - inset).coerceAtLeast(0f)
        drawRoundRect(
            color = GoldSoftCuero,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )
    }

// D-015 (Cuero): separador "nervio de lomo" — doble línea con gema romboidal al centro,
// como los nervios de una encuadernación en piel. Solo se pinta con Cuero activo.
@Composable
fun CueroNervio(theme: Theme, modifier: Modifier = Modifier) {
    if (!isCueroTheme(theme)) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        val line: @Composable (Modifier) -> Unit = { m ->
            Box(m.height(3.dp).drawBehind {
                val w = 1.dp.toPx()
                drawRect(GoldSoftCuero, size = androidx.compose.ui.geometry.Size(size.width, w))
                drawRect(GoldSoftCuero, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - w), size = androidx.compose.ui.geometry.Size(size.width, w))
            })
        }
        line(Modifier.weight(1f))
        Box(
            Modifier.padding(horizontal = 6.dp).size(5.dp)
                .graphicsLayer { rotationZ = 45f }
                .background(GoldSoftCuero)
        )
        line(Modifier.weight(1f))
    }
}

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
    // B-031: aquí había un BoxWithConstraints y cerraba la app en release.
    //
    // Esta función envuelve CADA pantalla del NavHost, y el NavHost tiene transiciones
    // (slideInHorizontally + fadeIn), que por dentro son AnimatedVisibility.
    // BoxWithConstraints hace subcompose, y subcomponer dentro de la medida de un
    // AnimatedVisibility corrompe el SlotTable de Compose (bug conocido de 1.6.x):
    //   ArrayIndexOutOfBoundsException: length=0; index=-5
    //     at androidx.compose.runtime.SlotTableKt.key(SlotTable.kt:3522)
    //     at ...BoxWithConstraints... at AnimatedEnterExitMeasurePolicy.measure
    // Es sensible al timing: en debug no salta y en release sí. El home se libraba de
    // rebote porque se compone con enabled=false (sale por el return de arriba).
    //
    // Para decidir "¿pantalla ancha?" no hacen falta las constraints del padre: basta
    // el ancho de la ventana, que da LocalConfiguration sin subcomponer nada.
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    if (screenWidthDp <= 600.dp) {
        content()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = maxContentWidth).fillMaxSize()) { content() }
        }
    }
}

sealed class Screen {
    // P-020: Screen.SessionHistory eliminado — el historial es un drawer, no un destino
    // (la ruta "session_history" no tenía composable y navegar a ella habría crasheado)
    object List : Screen(); object Add : Screen(); object BookSearch : Screen(); object Stats : Screen()
    object ImportExport : Screen(); object WrappedHistory : Screen()
    object Bingo : Screen(); object Settings : Screen(); object Challenges : Screen()
    // TAREA 1 (lanzamiento): política de privacidad in-app
    object PrivacyPolicy : Screen()
    // D-016 (P-011): historial de retos archivados
    object ChallengeHistory : Screen()
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
    is Screen.PrivacyPolicy -> "privacy_policy"
    is Screen.Challenges -> "challenges"
    is Screen.ChallengeHistory -> "challenge_history"
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
                composable("settings") { WideScreenCenter { SettingsScreen(vm, prefs, theme, onBack = { goBack() }, onBulkReload = { type -> navigateTo(Screen.BulkReload(type)) }, onResetTutorial = { navigateTo(Screen.List) }, onImportExport = { navigateTo(Screen.ImportExport) }, onPrivacyPolicy = { navigateTo(Screen.PrivacyPolicy) }) } }
                // TAREA 1 (lanzamiento): política de privacidad in-app
                composable("privacy_policy") { WideScreenCenter { PrivacyPolicyScreen(theme, onBack = { goBack() }) } }
                composable("challenges") { WideScreenCenter { ChallengesScreen(vm, prefs, theme, onBack = { goBack() }, onHistory = { navigateTo(Screen.ChallengeHistory) }) } }
                // D-016 (P-011): historial de retos archivados
                composable("challenge_history") { WideScreenCenter { ChallengeHistoryScreen(vm, prefs, theme, onBack = { goBack() }) } }
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




// Fase 1.3: FullBackup, export/import JSON+CSV y portadas embebidas viven en repository/BackupRepository.kt





// === BOOKQUEST INTEGRATION PATCH ===
// Easter egg should now open:
// com.lecturameter.bookquest.BookQuestScreen()
//
// BookQuest primary flow enabled.
// ===================================



