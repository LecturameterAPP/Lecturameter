package com.lecturameter.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lecturameter.model.*
import com.lecturameter.MainActivity
import com.lecturameter.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val WIDGET_PREFS = "widget_book_selection"
const val WIDGET_BOOK_KEY = "selected_book_id"

// ── v2.4 rework: personalización de chips por appWidgetId ─────────────────────
data class WidgetDisplayConfig(
    val showEmojis: Boolean = true,
    val showDays: Boolean = true,
    val showTime: Boolean = true,
    val showSessions: Boolean = true,
    val showPages: Boolean = true,
    val showPercent: Boolean = true
)

// v2.5: la configuración de chips pasa a ser GLOBAL (una sola para todos los widgets),
// editable desde Ajustes → WIDGET sin quitar/volver a poner el widget.
// Lectura con fallback legacy per-appWidgetId (configs guardadas en v2.4).
fun loadWidgetDisplayConfig(context: Context, appWidgetId: Int = 0): WidgetDisplayConfig {
    val p = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
    fun g(name: String): Boolean = when {
        p.contains("cfg_global_$name") -> p.getBoolean("cfg_global_$name", true)
        appWidgetId != 0 && p.contains("cfg_${appWidgetId}_$name") -> p.getBoolean("cfg_${appWidgetId}_$name", true)
        else -> true
    }
    return WidgetDisplayConfig(
        showEmojis   = g("emojis"),
        showDays     = g("days"),
        showTime     = g("time"),
        showSessions = g("sessions"),
        showPages    = g("pages"),
        showPercent  = g("percent")
    )
}

fun saveWidgetDisplayConfig(context: Context, appWidgetId: Int, cfg: WidgetDisplayConfig) {
    val p = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
    p.edit()
        .putBoolean("cfg_global_emojis",   cfg.showEmojis)
        .putBoolean("cfg_global_days",     cfg.showDays)
        .putBoolean("cfg_global_time",     cfg.showTime)
        .putBoolean("cfg_global_sessions", cfg.showSessions)
        .putBoolean("cfg_global_pages",    cfg.showPages)
        .putBoolean("cfg_global_percent",  cfg.showPercent)
        .apply()
}

private const val WIDGET_COVER_MAX_SIZE = 300
private val widgetUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// ── Tema del widget (Bug fix v21.14): el widget no seguía el tema de la app,
// siempre se veía oscuro. Lee el mismo "theme_mode" que usa MainActivity y
// aplica fondo + colores de texto a juego, sin tocar el layout XML base. ──
private data class WidgetThemeColors(
    val bgDrawable: Int,
    val textMain: Int,
    val textMuted: Int
)

private fun resolveWidgetTheme(context: Context): WidgetThemeColors {
    val prefs = context.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
    return when (prefs.getString("theme_mode", "dark")) {
        "light"  -> WidgetThemeColors(R.drawable.widget_background_light, 0xFF1E293B.toInt(), 0xFF475569.toInt())
        "aurora" -> WidgetThemeColors(R.drawable.widget_background_aurora, 0xFFEDE9FF.toInt(), 0xFFA78BFA.toInt())
        "amoled" -> WidgetThemeColors(R.drawable.widget_background_amoled, 0xFFF1F5F9.toInt(), 0xFF94A3B8.toInt())
        else     -> WidgetThemeColors(R.drawable.widget_background_dark, 0xFFF1F5F9.toInt(), 0xFF94A3B8.toInt())
    }
}

fun saveWidgetBook(context: Context, bookId: Long) {
    context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putLong(WIDGET_BOOK_KEY, bookId)
        .commit()
}

fun loadWidgetBook(context: Context): Long =
    context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        .getLong(WIDGET_BOOK_KEY, -1L)

fun clearWidgetCoverCache(context: Context) {
    try {
        context.applicationContext.cacheDir
            .listFiles { file -> file.name.startsWith("widget_cover_") }
            ?.forEach { it.delete() }
    } catch (_: Exception) {
    }
}

fun requestBookWidgetUpdate(context: Context) {
    val appContext = context.applicationContext
    widgetUpdateScope.launch {
        try {
            updateBookWidgets(appContext)
        } catch (_: Exception) {
        }
    }
}

suspend fun updateBookWidgets(context: Context) = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val manager = AppWidgetManager.getInstance(appContext)
    val ids = manager.getAppWidgetIds(ComponentName(appContext, BookWidgetReceiver::class.java))
    if (ids.isEmpty()) return@withContext

    val bookId = loadWidgetBook(appContext)
    val book = if (bookId == -1L) null else loadBookById(appContext, bookId)
    val sessions = if (book == null) emptyList() else loadSessions(appContext)
    val coverBitmap = book?.coverUrl?.let { loadCoverBitmap(appContext, it) }

    ids.forEach { appWidgetId ->
        // v2.3b: adaptativo — leer ancho real de ESTE widget y compactar chips si es estrecho.
        // El launcher reporta minWidth en dp via options; OPTIONS_CHANGED ya dispara update.
        val minWidthDp = try {
            manager.getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        } catch (_: Exception) { 0 }
        val compact = minWidthDp in 1 until 250
        // v2.4 rework: configuración de chips propia de cada widget
        val cfg = loadWidgetDisplayConfig(appContext, appWidgetId)
        manager.updateAppWidget(
            appWidgetId,
            buildWidgetViews(appContext, book, sessions, coverBitmap, compact, cfg)
        )
    }
}

class BookWidgetReceiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH_WIDGET,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                val pendingResult = goAsync()
                val appContext = context.applicationContext
                widgetUpdateScope.launch {
                    try {
                        updateBookWidgets(appContext)
                    } catch (_: Exception) {
                    } finally {
                        pendingResult.finish()
                    }
                }
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        requestBookWidgetUpdate(context)
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.lecturameter.REFRESH_WIDGET"
    }
}

// v21.41: el widget corre fuera del proceso de la app; fuerza el locale elegido por el usuario
private fun appLocalizedContext(context: Context): Context {
    return try {
        val prefs = context.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "es") ?: "es"
        val locale = java.util.Locale(lang)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.createConfigurationContext(config)
    } catch (_: Exception) {
        context
    }
}

private fun buildWidgetViews(
    context: Context,
    book: Book?,
    sessions: List<ReadingSession>,
    coverBitmap: Bitmap?,
    compact: Boolean = false,
    cfg: WidgetDisplayConfig = WidgetDisplayConfig()
): RemoteViews {
    // ctx garantiza strings en el idioma de la app, no del sistema
    val ctx = appLocalizedContext(context)
    val views = RemoteViews(context.packageName, R.layout.book_widget)

    // v2.3b: chips escalan con el ancho del widget. Normal 12sp/6dp (legible),
    // compact 10sp/4dp (widgets estrechos, evita truncado). Layout base queda en compact.
    run {
        val chipIds = intArrayOf(
            R.id.widget_days_chip, R.id.widget_minutes_chip, R.id.widget_sessions_chip,
            R.id.widget_pages_chip, R.id.widget_percent_chip
        )
        val sp = if (compact) 10f else 12f
        val padH = ((if (compact) 4 else 6) * context.resources.displayMetrics.density).toInt()
        val padV = (3 * context.resources.displayMetrics.density).toInt()
        for (id in chipIds) {
            views.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_SP, sp)
            views.setViewPadding(id, padH, padV, padH, padV)
        }
    }
    views.setOnClickPendingIntent(R.id.widget_root, buildOpenAppPendingIntent(context, book))

    val widgetTheme = resolveWidgetTheme(context)
    views.setInt(R.id.widget_root, "setBackgroundResource", widgetTheme.bgDrawable)
    views.setInt(R.id.widget_cover_frame, "setBackgroundResource", R.drawable.widget_accent_bg_cover)
    views.setTextColor(R.id.widget_title, widgetTheme.textMain)
    views.setTextColor(R.id.widget_cover_placeholder, widgetTheme.textMain)
    views.setTextColor(R.id.widget_author, widgetTheme.textMuted)
    views.setTextColor(R.id.widget_updated, widgetTheme.textMuted)
    for (chipId in listOf(R.id.widget_days_chip, R.id.widget_minutes_chip, R.id.widget_sessions_chip, R.id.widget_pages_chip, R.id.widget_percent_chip)) {
        views.setInt(chipId, "setBackgroundResource", R.drawable.widget_accent_bg_chip)
        views.setTextColor(chipId, widgetTheme.textMain)
    }

    if (book == null) {
        views.setTextViewText(R.id.widget_title, "Lecturameter")
        views.setTextViewText(
            R.id.widget_author,
            ctx.getString(R.string.widget_choose_book_help)
        )
        views.setTextViewText(R.id.widget_updated, currentTime())
        views.setViewVisibility(R.id.widget_chips_row, View.GONE)
        views.setViewVisibility(R.id.widget_cover, View.GONE)
        views.setViewVisibility(R.id.widget_cover_placeholder, View.VISIBLE)
        views.setTextViewText(R.id.widget_cover_placeholder, "📚")
        // v2.5 fix: la barra de progreso se quedaba con el valor del libro anterior
        views.setInt(R.id.widget_progress_bar, "setProgress", 0)
        views.setViewVisibility(R.id.widget_progress_bar, View.GONE)
        return views
    }

    val stats = computeWidgetStats(book, sessions)
    views.setTextViewText(R.id.widget_title, book.title)
    views.setTextViewText(R.id.widget_author, book.author)
    views.setTextViewText(R.id.widget_updated, currentTime())
    views.setViewVisibility(R.id.widget_chips_row, View.VISIBLE)

    if (coverBitmap != null) {
        views.setImageViewBitmap(R.id.widget_cover, coverBitmap)
        views.setViewVisibility(R.id.widget_cover, View.VISIBLE)
        views.setViewVisibility(R.id.widget_cover_placeholder, View.GONE)
    } else {
        views.setViewVisibility(R.id.widget_cover, View.GONE)
        views.setViewVisibility(R.id.widget_cover_placeholder, View.VISIBLE)
        views.setTextViewText(R.id.widget_cover_placeholder, "📖")
    }

    // v2.4 rework: cada chip respeta su toggle; los emojis se pueden ocultar por widget
    fun emo(e: String) = if (cfg.showEmojis) "$e " else ""
    setOptionalChip(views, R.id.widget_days_chip, if (cfg.showDays) "${emo("📅")}${stats.days} d" else null)
    // orden: días → tiempo → sesiones → páginas → %
    setOptionalChip(views, R.id.widget_minutes_chip,
        if (cfg.showTime) stats.lastSessionMinutes?.let { "${emo("\u23F1\uFE0F")}${formatMinutes(it)}" } else null)
    setOptionalChip(views, R.id.widget_sessions_chip,
        if (cfg.showSessions) "${emo("📖")}${stats.sessions} ${ctx.getString(R.string.history_stat_sessions)}" else null)
    setOptionalChip(views, R.id.widget_pages_chip,
        if (cfg.showPages) stats.lastSessionPages?.let { "${emo("📄")}${it}p" } else null)
    setOptionalChip(views, R.id.widget_percent_chip,
        if (cfg.showPercent) stats.completionPct?.let { "${emo("📊")}${it}%" } else null)
    // v21.35: progress bar sky — visible when pct available
    val pct = stats.completionPct
    if (pct != null && pct > 0) {
        views.setViewVisibility(R.id.widget_progress_bar, View.VISIBLE)
        views.setInt(R.id.widget_progress_bar, "setProgress", pct)
    } else {
        views.setViewVisibility(R.id.widget_progress_bar, View.GONE)
    }
    return views
}

private fun setOptionalChip(views: RemoteViews, viewId: Int, label: String?) {
    if (label == null) {
        views.setViewVisibility(viewId, View.GONE)
    } else {
        views.setTextViewText(viewId, label)
        views.setViewVisibility(viewId, View.VISIBLE)
    }
}

private fun buildOpenAppPendingIntent(context: Context, book: Book?): PendingIntent {
    val intent = if (book != null) {
        Intent(Intent.ACTION_VIEW, Uri.parse("lecturameter://book/${book.id}")).apply {
            setClass(context, MainActivity::class.java)
        }
    } else {
        Intent(context, MainActivity::class.java)
    }.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    val requestCode = book?.id?.let { (it xor (it ushr 32)).toInt() } ?: 0
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

suspend fun loadCoverBitmap(context: Context, coverUrl: String): Bitmap? =
    withContext(Dispatchers.IO) {
        val normalizedUrl = coverUrl.trim()
        try {
            when {
                normalizedUrl.startsWith("/") -> {
                    val file = File(normalizedUrl)
                    if (file.exists()) decodeWidgetBitmapFile(file) else null
                }
                normalizedUrl.startsWith("file://") -> {
                    val path = Uri.parse(normalizedUrl).path ?: return@withContext null
                    val file = File(path)
                    if (file.exists()) decodeWidgetBitmapFile(file) else null
                }
                normalizedUrl.startsWith("content://") -> {
                    val bytes = context.contentResolver.openInputStream(Uri.parse(normalizedUrl))
                        ?.use { it.readBytes() }
                    bytes?.let { decodeWidgetBitmapBytes(it) }
                }
                normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://") -> {
                    val cacheFile = File(context.cacheDir, "widget_cover_${normalizedUrl.hashCode()}.jpg")
                    val bytes: ByteArray? = if (cacheFile.exists() && cacheFile.length() > 0) {
                        cacheFile.readBytes()
                    } else {
                        val conn = URL(normalizedUrl).openConnection() as HttpURLConnection
                        try {
                            conn.connectTimeout = 2500
                            conn.readTimeout = 2500
                            // Seguridad: rechazar descargas > 5 MB para evitar OOM
                            val contentLength = conn.contentLengthLong
                            if (contentLength > 5_000_000L) {
                                null
                            } else {
                                conn.inputStream.use { it.readBytes() }
                                    .also { cacheFile.writeBytes(it) }
                            }
                        } finally {
                            conn.disconnect()
                        }
                    }
                    bytes?.let { decodeWidgetBitmapBytes(it) }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

private fun decodeWidgetBitmapFile(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateWidgetSampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private fun decodeWidgetBitmapBytes(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateWidgetSampleSize(bounds.outWidth, bounds.outHeight)
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun calculateWidgetSampleSize(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 1

    var sampleSize = 1
    var scaledWidth = width
    var scaledHeight = height
    while (scaledWidth > WIDGET_COVER_MAX_SIZE || scaledHeight > WIDGET_COVER_MAX_SIZE) {
        sampleSize *= 2
        scaledWidth /= 2
        scaledHeight /= 2
    }
    return sampleSize
}

data class WidgetStats(
    val days: Int,
    val totalMinutes: Int,
    val sessions: Int,
    val lastSessionMinutes: Int?,
    val lastSessionPages: Int?,   // v20.9: páginas de la última sesión (sustituye avgPages en widget)
    val completionPct: Int?
)

fun computeWidgetStats(book: Book, sessions: List<ReadingSession>): WidgetStats {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = sdf.format(Date())
    val bookSessions = sessions.filter { it.bookId == book.id }
    val lastSessionDate = bookSessions.mapNotNull { it.date }.filter { it.isNotBlank() }.maxOrNull()

    // Fecha inicio efectiva del ciclo actual:
    // REREADING → fecha del último evento "reread" en dateEvents (o startDate si no hay)
    // READING   → startDate
    val effectiveStart: String? = when (book.status) {
        BookStatus.REREADING -> {
            book.dateEvents
                .filter { it.type == "reread" }
                .mapNotNull { it.date }
                .filter { it.isNotBlank() }
                .maxOrNull()
                ?: book.startDate
        }
        else -> book.startDate
    }

    // Fecha fin efectiva:
    // FINISHED  → endDate > última sesión > startDate
    // REREADING → hoy (lectura en curso)
    // otros     → hoy
    val endRef = when {
        book.status == BookStatus.FINISHED && !book.endDate.isNullOrBlank() -> book.endDate
        book.status == BookStatus.FINISHED && lastSessionDate != null -> lastSessionDate
        book.status == BookStatus.FINISHED && !book.startDate.isNullOrBlank() -> book.startDate
        else -> today
    }

    val days = if (!effectiveStart.isNullOrBlank()) {
        try {
            val start = sdf.parse(effectiveStart)!!
            val end = sdf.parse(endRef)!!
            val diff = ceil((end.time - start.time) / 86400000.0).toInt()
            diff.coerceAtLeast(1)
        } catch (_: Exception) {
            1
        }
    } else {
        1
    }

    val sortedSessions = bookSessions
        .sortedWith(compareByDescending<ReadingSession> { it.date }.thenByDescending { it.id })
    val lastMins = sortedSessions
        .firstOrNull { (it.minutes ?: 0) > 0 }
        ?.minutes
    // v20.9: páginas de la sesión más reciente (por fecha+id)
    val lastSessionPages = sortedSessions
        .firstOrNull { it.pages > 0 }
        ?.pages
        ?.takeIf { it > 0 }
    val pagesRead = bookSessions.sumOf { it.pages }
    val effectiveTotal = when {
        book.firstFunctionalPage != null && book.lastFunctionalPage != null ->
            (book.lastFunctionalPage - book.firstFunctionalPage + 1).coerceAtLeast(1)
        book.lastFunctionalPage != null -> book.lastFunctionalPage
        else -> book.pages
    }
    val pct = if (effectiveTotal > 0 && pagesRead > 0) {
        (pagesRead * 100 / effectiveTotal).coerceIn(0, 100)
    } else {
        null
    }

    return WidgetStats(
        days = days,
        totalMinutes = bookSessions.sumOf { it.minutes ?: 0 },
        sessions = bookSessions.size,
        lastSessionMinutes = lastMins,
        lastSessionPages = lastSessionPages,
        completionPct = pct
    )
}

fun currentTime(): String =
    SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.getDefault()).format(Date())

fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h == 0) "${m}m" else if (m == 0) "${h}h" else "${h}h${m}m"
}

fun loadBookById(context: Context, bookId: Long): Book? {
    if (bookId == -1L) return null
    return try {
        val prefs = context.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
        val json = prefs.getString("books", null) ?: return null
        val books: List<Book> = Gson().fromJson(
            json,
            object : TypeToken<List<Book>>() {}.type
        ) ?: return null
        // Gson devuelve null en campos List con default en Kotlin si el JSON es de version anterior
        books.firstOrNull { it.id == bookId }?.let { b ->
            b.copy(
                title      = b.title      ?: "",
                author     = b.author     ?: "",
                genres     = b.genres     ?: emptyList(),
                editions   = b.editions   ?: emptyList(),
                dateEvents = b.dateEvents ?: emptyList()   // v19.9 fix: Gson pone null en campo nuevo
            )
        }
    } catch (_: Exception) {
        null
    }
}

fun loadSessions(context: Context): List<ReadingSession> {
    return try {
        val prefs = context.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
        val json = prefs.getString("sessions", null) ?: return emptyList()
        Gson().fromJson(
            json,
            object : TypeToken<List<ReadingSession>>() {}.type
        ) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}
