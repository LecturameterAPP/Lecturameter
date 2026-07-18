package com.lecturameter.widget

// ── FALLBACK RemoteViews (Fase 2) ─────────────────────────────────────────────
// Render RemoteViews de 2.7 conservado INTACTO como modo conservador, tal como
// exige el plan ("si Glance falla en alguna API: fallback a RemoteViews").
// Para activarlo: en AndroidManifest.xml cambiar el receiver del widget de
// .widget.BookWidgetReceiver a .widget.LegacyBookWidgetReceiver (una línea).
// No está registrado en el manifest, así que no participa en runtime.

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.lecturameter.MainActivity
import com.lecturameter.R
import com.lecturameter.model.*
import com.lecturameter.utils.computeWidgetStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val legacyUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class LegacyBookWidgetReceiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BookWidgetReceiver.ACTION_REFRESH_WIDGET,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                val pendingResult = goAsync()
                val appContext = context.applicationContext
                legacyUpdateScope.launch {
                    try {
                        updateBookWidgetsLegacy(appContext)
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

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        legacyUpdateScope.launch { try { updateBookWidgetsLegacy(context.applicationContext) } catch (_: Exception) {} }
    }
}

suspend fun updateBookWidgetsLegacy(context: Context) = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val manager = AppWidgetManager.getInstance(appContext)
    val ids = manager.getAppWidgetIds(ComponentName(appContext, LegacyBookWidgetReceiver::class.java))
    if (ids.isEmpty()) return@withContext

    val bookId = loadWidgetBook(appContext)
    val editionId = loadWidgetEdition(appContext)
    val book = if (bookId == -1L) null else loadBookById(appContext, bookId)
    val sessions = if (book == null) emptyList() else loadSessions(appContext)
    val activeEdition = if (editionId != -1L) book?.editions?.firstOrNull { it.id == editionId } else null
    val coverUrl = activeEdition?.coverUrl ?: book?.coverUrl
    val coverBitmap = coverUrl?.let { loadCoverBitmap(appContext, it) }

    ids.forEach { appWidgetId ->
        val minWidthDp = try {
            manager.getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        } catch (_: Exception) { 0 }
        val compact = minWidthDp in 1 until 250
        val cfg = loadWidgetDisplayConfig(appContext, appWidgetId)
        manager.updateAppWidget(
            appWidgetId,
            buildWidgetViews(appContext, book, sessions, coverBitmap, compact, cfg, editionId)
        )
    }
}

private fun buildWidgetViews(
    context: Context,
    book: Book?,
    sessions: List<ReadingSession>,
    coverBitmap: Bitmap?,
    compact: Boolean = false,
    cfg: WidgetDisplayConfig = WidgetDisplayConfig(),
    editionId: Long = -1L
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
    // Feedback 17-07 (Bloque 2): los overlays de acento y la barra siguen al tema (oro en
    // Cuero, morado en Aurora). Antes eran índigo fijo y sobre el marrón del cuero se veían
    // como un "azul sucio". setColorStateList por RemoteViews requiere API 31+; por debajo
    // la barra se queda con el sky del layout (aceptado: el fondo y los chips ya van a juego).
    views.setInt(R.id.widget_cover_frame, "setBackgroundResource", widgetTheme.accentCoverDrawable)
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        views.setColorStateList(
            R.id.widget_progress_bar, "setProgressTintList",
            android.content.res.ColorStateList.valueOf(widgetTheme.progressColor)
        )
        views.setColorStateList(
            R.id.widget_progress_bar, "setProgressBackgroundTintList",
            android.content.res.ColorStateList.valueOf(widgetTheme.progressTrackColor)
        )
    }
    views.setTextColor(R.id.widget_title, widgetTheme.textMain)
    views.setTextColor(R.id.widget_cover_placeholder, widgetTheme.textMain)
    views.setTextColor(R.id.widget_author, widgetTheme.textMuted)
    views.setTextColor(R.id.widget_updated, widgetTheme.textMuted)
    for (chipId in listOf(R.id.widget_days_chip, R.id.widget_minutes_chip, R.id.widget_sessions_chip, R.id.widget_pages_chip, R.id.widget_percent_chip)) {
        views.setInt(chipId, "setBackgroundResource", widgetTheme.accentChipDrawable)
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

    val stats = computeWidgetStats(book, sessions, editionId)
    val activeEd = if (editionId != -1L) book.editions.firstOrNull { it.id == editionId } else null
    val displayTitle = activeEd?.title?.takeIf { it.isNotBlank() } ?: book.title
    views.setTextViewText(R.id.widget_title, displayTitle)
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
        if (cfg.showTime) stats.lastSessionMinutes?.let { "${emo("⏱️")}${formatMinutes(it)}" } else null)
    setOptionalChip(views, R.id.widget_sessions_chip,
        if (cfg.showSessions) "${emo("📖")}${stats.sessions} ${ctx.getString(R.string.history_stat_sessions)}" else null)
    setOptionalChip(views, R.id.widget_pages_chip,
        if (cfg.showPages) stats.lastSessionPages?.let { "${emo("📄")}${it}p" } else null)
    setOptionalChip(views, R.id.widget_percent_chip,
        if (cfg.showPercent) stats.completionPct?.let { "${emo("📊")}${it}%" } else null)
    // v21.35: progress bar sky — visible when pct available
    val pct = stats.completionPct
    if (cfg.showProgressBar && pct != null && pct > 0) {
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
