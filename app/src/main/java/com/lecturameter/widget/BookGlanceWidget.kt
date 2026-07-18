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
import androidx.glance.ColorFilter
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
import androidx.glance.layout.Spacer
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

// Colores de los iconos de las pills, misma paleta que las celdas del widget de stats
private val ChipDays     = Color(0xFF0EA5E9)
private val ChipTime     = Color(0xFFF59E0B)
private val ChipSessions = Color(0xFF8B5CF6)
private val ChipPages    = Color(0xFF10B981)
private val ChipPercent  = Color(0xFFEC4899)

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
            val editionId = loadWidgetEdition(appContext)
            val book = if (bookId == -1L) null else loadBookById(appContext, bookId)
            val sessions = if (book == null) emptyList() else loadSessions(appContext)
            val activeEdition = if (editionId != -1L) book?.editions?.firstOrNull { it.id == editionId } else null
            val coverUrl = activeEdition?.coverUrl ?: book?.coverUrl
            val cover: Bitmap? = coverUrl?.let { loadCoverBitmapBlocking(appContext, it) }
            val stats: WidgetStats? = book?.let { computeWidgetStats(it, sessions, editionId) }
            val displayTitle = activeEdition?.title?.takeIf { it.isNotBlank() } ?: book?.title
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
            WidgetContent(book, displayTitle, stats, cover, theme, cfg, texts, updated, compact, mini)
        }
    }
}

@Composable
private fun WidgetContent(
    book: Book?,
    displayTitle: String?,
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
    // Feedback 17-07 (Bloque 2): acento del widget por tema (oro en Cuero, morado en
    // Aurora). Antes los overlays y la barra eran índigo/sky fijos y ensuciaban el marrón.
    val progressColor = ColorProvider(Color(theme.progressColor))
    val progressTrack = ColorProvider(Color(theme.progressTrackColor))
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
                .background(ImageProvider(theme.accentCoverDrawable)),
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
                text = displayTitle ?: "Lecturameter",
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
                // P-038: los emojis de las pills pasan a iconos Material tintados, cada uno
                // con su color (misma paleta que las celdas del widget de stats). El toggle
                // showEmojis sigue controlando su visibilidad.
                val chips = listOfNotNull(
                    if (cfg.showDays) Triple(R.drawable.widget_ic_calendar, ChipDays, "${stats.days} d") else null,
                    if (cfg.showTime) stats.lastSessionMinutes?.let { Triple(R.drawable.widget_ic_timer, ChipTime, formatMinutes(it)) } else null,
                    if (cfg.showSessions) Triple(R.drawable.widget_ic_book, ChipSessions, "${stats.sessions} ${texts.sessionsLabel}") else null,
                    if (cfg.showPages) stats.lastSessionPages?.let { Triple(R.drawable.widget_ic_pages, ChipPages, "${it}p") } else null,
                    if (cfg.showPercent) stats.completionPct?.let { Triple(R.drawable.widget_ic_percent, ChipPercent, "${it}%") } else null
                )
                if (chips.isNotEmpty()) {
                    Row(modifier = GlanceModifier.padding(top = 6.dp)) {
                        chips.forEach { (iconRes, iconTint, label) ->
                            Box(modifier = GlanceModifier.padding(end = 2.dp)) {
                                Row(
                                    modifier = GlanceModifier
                                        .background(ImageProvider(theme.accentChipDrawable))
                                        .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.Vertical.CenterVertically
                                ) {
                                    if (cfg.showEmojis) {
                                        Image(
                                            provider = ImageProvider(iconRes),
                                            contentDescription = null,
                                            modifier = GlanceModifier.width(11.dp).height(11.dp),
                                            colorFilter = ColorFilter.tint(ColorProvider(iconTint))
                                        )
                                        Spacer(GlanceModifier.width(3.dp))
                                    }
                                    Text(
                                        text = label,
                                        style = TextStyle(fontSize = if (compact) 10.sp else 12.sp, color = textMain),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
                val pct = stats.completionPct
                if (cfg.showProgressBar && pct != null && pct > 0) {
                    // padding ANTES de height: al revés, el padding interno se come los 4dp de barra
                    LinearProgressIndicator(
                        progress = pct / 100f,
                        modifier = GlanceModifier.fillMaxWidth().padding(top = 5.dp).height(9.dp),
                        color = progressColor,
                        backgroundColor = progressTrack
                    )
                }
            }

            // Mini: sin chips ni hora, pero la barra de progreso sí cabe
            if (mini && book != null && stats != null) {
                val pct = stats.completionPct
                if (cfg.showProgressBar && pct != null && pct > 0) {
                    LinearProgressIndicator(
                        progress = pct / 100f,
                        modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp).height(8.dp),
                        color = progressColor,
                        backgroundColor = progressTrack
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
