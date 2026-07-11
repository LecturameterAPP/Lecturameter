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
        // Toda la carga de datos ocurre ANTES de componer (I/O fuera del árbol Glance)
        val appContext = context.applicationContext
        val bookId = loadWidgetBook(appContext)
        val book = if (bookId == -1L) null else loadBookById(appContext, bookId)
        val sessions = if (book == null) emptyList() else loadSessions(appContext)
        val cover: Bitmap? = book?.coverUrl?.let { loadCoverBitmap(appContext, it) }
        val stats: WidgetStats? = book?.let { computeWidgetStats(it, sessions) }
        val theme = resolveWidgetTheme(appContext)
        val cfg = loadWidgetDisplayConfig(appContext)
        val ctx = appLocalizedContext(appContext)
        val texts = WidgetTexts(
            chooseBook = ctx.getString(R.string.widget_choose_book_help),
            sessionsLabel = ctx.getString(R.string.history_stat_sessions)
        )
        val updated = currentTime()

        provideContent {
            val compact = LocalSize.current.width < 250.dp
            WidgetContent(book, stats, cover, theme, cfg, texts, updated, compact)
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
    compact: Boolean
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
                .width(64.dp)
                .height(92.dp)
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
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textMain),
                maxLines = 2
            )
            Text(
                text = book?.author ?: texts.chooseBook,
                style = TextStyle(fontSize = 11.sp, color = textMuted),
                maxLines = if (book == null) 3 else 1,
                modifier = GlanceModifier.padding(top = 2.dp)
            )

            if (book != null && stats != null) {
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

            Text(
                text = updated,
                style = TextStyle(fontSize = 10.sp, color = textMuted),
                maxLines = 1,
                modifier = GlanceModifier.padding(top = 4.dp)
            )
        }
    }
}
