package com.lecturameter.widget

// ── Fase 2: widget reescrito con Glance (no parcheado) ────────────────────────
// Réplica visual 1:1 del layout RemoteViews de 2.7 (book_widget.xml):
// portada 64×92 con marco, título 15sp bold ×2 líneas, autor 11sp, pills
// 12sp (10sp en modo compacto <250dp), barra de progreso sky 4dp y hora 10sp.
// El fallback RemoteViews vive en LegacyBookWidgetReceiver.kt (ver comentario allí).

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lecturameter.MainActivity
import com.lecturameter.R
import com.lecturameter.model.Book
import com.lecturameter.utils.WidgetStats
import com.lecturameter.utils.computeWidgetStats

/** Textos resueltos con el idioma de la APP (el widget corre fuera del proceso). */
internal data class WidgetTexts(
    val chooseBook: String,
    val sessionsLabel: String
)

class BookGlanceWidget : GlanceAppWidget() {

    // Exact para conocer el ancho real y compactar pills como hacía v2.3b
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        provideContent {
            // B-009: la carga de datos vive DENTRO de la composición, y la composición
            // LEE el estado Glance (tick de refresco): Glance solo recompone una sesión
            // viva cuando cambia LocalState — leer prefs a secas no es observable. Cada
            // requestBookWidgetUpdate() escribe un tick nuevo en el estado y eso invalida
            // esta lectura → se relee prefs, libro, sesiones, tema, config e idioma.
            // La composición de Glance corre fuera del main thread, así que el I/O
            // ligero (prefs+Gson+bitmap cacheado) es seguro.
            androidx.glance.currentState(WIDGET_REFRESH_TICK)
            val bookId = loadWidgetBook(appContext)
            val book = if (bookId == -1L) null else loadBookById(appContext, bookId)
            val sessions = if (book == null) emptyList() else loadSessions(appContext)
            val cover: Bitmap? = book?.coverUrl?.let { loadCoverBitmapBlocking(appContext, it) }
            val stats: WidgetStats? = book?.let { computeWidgetStats(it, sessions) }
            val theme = resolveWidgetTheme(appContext)
            val cfg = loadWidgetDisplayConfig(appContext)
            val ctx = appLocalizedContext(appContext)
            val texts = WidgetTexts(
                chooseBook = ctx.getString(R.string.widget_choose_book_help),
                sessionsLabel = ctx.getString(R.string.history_stat_sessions)
            )
            val updated = currentTime()
            val compact = LocalSize.current.width < 250.dp
            // B-010: con poca altura no caben título+autor+chips+barra+hora — el host
            // recorta por abajo. Modo "mini": solo título, autor y barra de progreso.
            val mini = LocalSize.current.height < 90.dp
            WidgetContent(book, stats, cover, theme, cfg, texts, updated, compact, mini)
        }
    }
}

@Composable
private fun WidgetContent(
    book: Book?,
    stats: WidgetStats?,
    cover: Bitmap?,
    theme: WidgetThemeColors,
    cfg: WidgetDisplayConfig,
    texts: WidgetTexts,
    updated: String,
    compact: Boolean,
    mini: Boolean = false
) {
    val textMain = ColorProvider(Color(theme.textMain))
    val textMuted = ColorProvider(Color(theme.textMuted))
    val openIntent = if (book != null) {
        Intent(Intent.ACTION_VIEW, Uri.parse("lecturameter://book/${book.id}"))
            .setClass(androidx.glance.LocalContext.current, MainActivity::class.java)
    } else {
        Intent(androidx.glance.LocalContext.current, MainActivity::class.java)
    }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP) }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(theme.bgDrawable))
            .padding(10.dp)
            .clickable(actionStartActivity(openIntent)),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // ── Portada 64×92 con marco (widget_accent_bg_cover, como en RemoteViews) ──
        Box(
            modifier = GlanceModifier
                .width(if (mini) 44.dp else 64.dp)
                .height(if (mini) 63.dp else 92.dp)
                .background(ImageProvider(R.drawable.widget_accent_bg_cover)),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                Image(
                    provider = ImageProvider(cover),
                    contentDescription = book?.title,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = if (book == null) "📚" else "📖",
                    style = TextStyle(fontSize = 26.sp, color = textMain)
                )
            }
        }

        Column(
            modifier = GlanceModifier.defaultWeight().padding(start = 12.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = book?.title ?: "Lecturameter",
                style = TextStyle(fontSize = if (mini) 13.sp else 15.sp, fontWeight = FontWeight.Bold, color = textMain),
                maxLines = if (mini) 1 else 2
            )
            Text(
                text = book?.author ?: texts.chooseBook,
                style = TextStyle(fontSize = 11.sp, color = textMuted),
                maxLines = if (book == null) { if (mini) 2 else 3 } else 1,
                modifier = GlanceModifier.padding(top = 2.dp)
            )

            if (book != null && stats != null && !mini) {
                fun emo(e: String) = if (cfg.showEmojis) "$e " else ""
                val chips = listOfNotNull(
                    if (cfg.showDays) "${emo("📅")}${stats.days} d" else null,
                    if (cfg.showTime) stats.lastSessionMinutes?.let { "${emo("⏱️")}${formatMinutes(it)}" } else null,
                    if (cfg.showSessions) "${emo("📖")}${stats.sessions} ${texts.sessionsLabel}" else null,
                    if (cfg.showPages) stats.lastSessionPages?.let { "${emo("📄")}${it}p" } else null,
                    if (cfg.showPercent) stats.completionPct?.let { "${emo("📊")}${it}%" } else null
                )
                if (chips.isNotEmpty()) {
                    Row(modifier = GlanceModifier.padding(top = 6.dp)) {
                        chips.forEach { label ->
                            Box(modifier = GlanceModifier.padding(end = 2.dp)) {
                                Text(
                                    text = label,
                                    style = TextStyle(fontSize = if (compact) 10.sp else 12.sp, color = textMain),
                                    maxLines = 1,
                                    modifier = GlanceModifier
                                        .background(ImageProvider(R.drawable.widget_accent_bg_chip))
                                        .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
                val pct = stats.completionPct
                if (pct != null && pct > 0) {
                    // padding ANTES de height: al revés, el padding interno se come los 4dp de barra
                    LinearProgressIndicator(
                        progress = pct / 100f,
                        modifier = GlanceModifier.fillMaxWidth().padding(top = 5.dp).height(9.dp),
                        color = ColorProvider(Color(0xFF0EA5E9)),
                        backgroundColor = ColorProvider(Color(0x40FFFFFF))
                    )
                }
            }

            // Mini: sin chips ni hora, pero la barra de progreso sí cabe
            if (mini && book != null && stats != null) {
                val pct = stats.completionPct
                if (pct != null && pct > 0) {
                    LinearProgressIndicator(
                        progress = pct / 100f,
                        modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp).height(8.dp),
                        color = ColorProvider(Color(0xFF0EA5E9)),
                        backgroundColor = ColorProvider(Color(0x40FFFFFF))
                    )
                }
            }
            if (!mini) {
                Text(
                    text = updated,
                    style = TextStyle(fontSize = 10.sp, color = textMuted),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(top = 4.dp)
                )
            }
        }
    }
}
