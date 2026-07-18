package com.lecturameter.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lecturameter.MainActivity
import com.lecturameter.R
import com.lecturameter.utils.GlobalStats
import com.lecturameter.utils.StatsWidgetDataProvider

private val StatBooks  = Color(0xFF8B5CF6)
private val StatPages  = Color(0xFF10B981)
private val StatTime   = Color(0xFFF59E0B)
private val StatDays   = Color(0xFF0EA5E9)
private val StatStreak = Color(0xFFEF4444)
private val StatBest   = Color(0xFF6366F1)
private val StatPeak   = Color(0xFFEC4899)
private val StatGenre  = Color(0xFF06B6D4)

private val WidgetGreen = Color(0xFF10B981)
private val WidgetRed   = Color(0xFFF87171)

private const val PREFS_KEY = "lecturameter"
private const val KEY_HIDE_PAGES = "sw_hide_pages"
private const val KEY_HIDE_TIME = "sw_hide_time"
private const val KEY_HIDE_STREAK = "sw_hide_streak"
private const val KEY_HIDE_SESSIONS = "sw_hide_sessions"

class StatsGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        provideContent {
            androidx.glance.currentState(WIDGET_REFRESH_TICK)
            val ctx = appLocalizedContext(appContext)
            val theme = resolveWidgetTheme(appContext)
            val prefs = appContext.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
            val isPro = com.lecturameter.utils.Pro.isPro(prefs)

            if (!isPro) {
                ProGateContent(theme, ctx)
            } else {
                val books = StatsWidgetDataProvider.loadAllBooks(appContext)
                val sessions = loadSessions(appContext)
                val stats = StatsWidgetDataProvider.compute(books, sessions)
                val updated = currentTime()
                val hiddenBars = HiddenBars(
                    pages = prefs.getBoolean(KEY_HIDE_PAGES, false),
                    time = prefs.getBoolean(KEY_HIDE_TIME, false),
                    streak = prefs.getBoolean(KEY_HIDE_STREAK, false),
                    sessions = prefs.getBoolean(KEY_HIDE_SESSIONS, false)
                )
                StatsWidgetContent(stats, theme, ctx, updated, hiddenBars)
            }
        }
    }
}

private data class HiddenBars(
    val pages: Boolean = false,
    val time: Boolean = false,
    val streak: Boolean = false,
    val sessions: Boolean = false
) {
    val visibleCount: Int get() = listOf(!pages, !time, !streak, !sessions).count { it }
}

class StatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatsGlanceWidget()
}

@Composable
private fun ProGateContent(theme: WidgetThemeColors, ctx: Context) {
    val textMain = ColorProvider(Color(theme.textMain))
    val textMuted = ColorProvider(Color(theme.textMuted))
    val openIntent = Intent(ctx, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(theme.bgDrawable))
            .padding(16.dp)
            .clickable(actionStartActivity(openIntent)),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = "Lecturameter Pro",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textMain)
        )
        Text(
            text = ctx.getString(R.string.sw_pro_gate),
            style = TextStyle(fontSize = 12.sp, color = textMuted, textAlign = TextAlign.Center),
            modifier = GlanceModifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun StatsWidgetContent(
    stats: GlobalStats,
    theme: WidgetThemeColors,
    ctx: Context,
    updated: String,
    hiddenBars: HiddenBars
) {
    val textMain = ColorProvider(Color(theme.textMain))
    val textMuted = ColorProvider(Color(theme.textMuted))
    val cellBg = ColorProvider(Color(theme.progressColor).copy(alpha = 0.08f))
    val openIntent = Intent(ctx, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    val widgetWidth = LocalSize.current.width

    val barsVisible = hiddenBars.visibleCount
    val hSepHeight = when {
        theme.hasDiamondSep -> 7.dp
        theme.hasWaveSep -> 9.dp
        else -> 3.dp
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(theme.bgDrawable))
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clickable(actionStartActivity(openIntent))
    ) {
        // Header: Title left, Hour right
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Titulo prominente, mismo estilo que el titulo del libro en BookGlanceWidget
            // (Bold + textMain; 13.sp como su variante mini)
            Text(
                text = ctx.getString(R.string.sw_monthly_comparison),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textMain),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                text = updated,
                style = TextStyle(fontSize = 10.sp, color = textMuted),
                maxLines = 1
            )
        }

        ThemedHSep(theme, hSepHeight)

        // Row 1: Books, Pages, Time, Active Days. Weighted: the stat rows absorb all
        // the free height so the widget never shows dead space below the delta bars.
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            StatCell(stats.totalBooks.toString(), ctx.getString(R.string.sw_label_books), R.drawable.widget_ic_book, StatBooks, textMuted, cellBg, GlanceModifier.defaultWeight())
            VerticalSep(theme)
            StatCell(formatCompact(stats.totalPages), ctx.getString(R.string.sw_label_pages), R.drawable.widget_ic_pages, StatPages, textMuted, cellBg, GlanceModifier.defaultWeight())
            VerticalSep(theme)
            StatCell(if (stats.totalMinutes > 0) formatMinutes(stats.totalMinutes) else "-", ctx.getString(R.string.sw_label_time), R.drawable.widget_ic_timer, StatTime, textMuted, cellBg, GlanceModifier.defaultWeight())
            VerticalSep(theme)
            StatCell(stats.activeDays.toString(), ctx.getString(R.string.sw_label_active_days), R.drawable.widget_ic_calendar, StatDays, textMuted, cellBg, GlanceModifier.defaultWeight())
        }

        // Themed horizontal separator (with diamonds in Cuero)
        ThemedHSep(theme, hSepHeight)

        // Row 2: Streak, Best, Peak Hour, Fav Genre
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            StatCell(if (stats.currentStreak > 0) stats.currentStreak.toString() else "-", ctx.getString(R.string.sw_label_streak), R.drawable.widget_ic_fire, StatStreak, textMuted, cellBg, GlanceModifier.defaultWeight())
            VerticalSep(theme)
            StatCell(if (stats.bestStreak > 0) stats.bestStreak.toString() else "-", ctx.getString(R.string.sw_label_best), R.drawable.widget_ic_trophy, StatBest, textMuted, cellBg, GlanceModifier.defaultWeight())
            VerticalSep(theme)
            StatCell(formatPeakHourSlot(stats.peakHourSlot), ctx.getString(R.string.sw_label_peak_hour), R.drawable.widget_ic_timer, StatPeak, textMuted, cellBg, GlanceModifier.defaultWeight())
            VerticalSep(theme)
            StatCell(stats.favoriteGenre?.take(8) ?: "-", ctx.getString(R.string.sw_label_fav_genre), R.drawable.widget_ic_genre, StatGenre, textMuted, cellBg, GlanceModifier.defaultWeight())
        }

        // Delta bars wrapped in a single Column to stay under Glance's 10-child limit.
        // Padding superior de 4.dp: sin la frase footer las barras quedaban pegadas
        // al separador de las celdas.
        if (barsVisible > 0) {
            ThemedHSep(theme, hSepHeight)
            Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
                val barMaxWidth = (widgetWidth - 106.dp).coerceAtLeast(40.dp)
                val trackColor = ColorProvider(Color(theme.progressColor).copy(alpha = 0.12f))
                if (!hiddenBars.pages) {
                    DeltaBar(ctx.getString(R.string.sw_label_pages_short), stats.thisMonthPages, stats.lastMonthPages, trackColor, textMuted, barMaxWidth)
                }
                if (!hiddenBars.time) {
                    DeltaBar(ctx.getString(R.string.sw_label_time_short), stats.thisMonthMinutes, stats.lastMonthMinutes, trackColor, textMuted, barMaxWidth)
                }
                if (!hiddenBars.streak) {
                    DeltaBar(ctx.getString(R.string.sw_label_streak_short), stats.thisMonthStreak, stats.lastMonthStreak, trackColor, textMuted, barMaxWidth)
                }
                if (!hiddenBars.sessions) {
                    DeltaBar(ctx.getString(R.string.sw_label_sessions_short), stats.thisMonthSessionCount, stats.lastMonthSessionCount, trackColor, textMuted, barMaxWidth)
                }
            }
        }
    }
}

@Composable
private fun VerticalSep(theme: WidgetThemeColors) {
    Box(
        GlanceModifier
            .width(1.dp)
            .fillMaxHeight()
            .background(ImageProvider(theme.vSepDrawable))
    ) {}
}

@Composable
private fun ThemedHSep(theme: WidgetThemeColors, height: Dp) {
    if (theme.hasDiamondSep) {
        val lineColor = ColorProvider(Color(0x40D9AC5C))
        Row(
            GlanceModifier.fillMaxWidth().height(height),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Box(GlanceModifier.defaultWeight().height(1.dp).background(lineColor)) {}
            Box(GlanceModifier.width(5.dp).height(5.dp).background(ImageProvider(R.drawable.widget_diamond_cuero))) {}
            Box(GlanceModifier.defaultWeight().height(1.dp).background(lineColor)) {}
            Box(GlanceModifier.width(5.dp).height(5.dp).background(ImageProvider(R.drawable.widget_diamond_cuero))) {}
            Box(GlanceModifier.defaultWeight().height(1.dp).background(lineColor)) {}
            Box(GlanceModifier.width(5.dp).height(5.dp).background(ImageProvider(R.drawable.widget_diamond_cuero))) {}
            Box(GlanceModifier.defaultWeight().height(1.dp).background(lineColor)) {}
        }
    } else if (theme.hasWaveSep) {
        // Aurora: la onda con halo ocupa toda la caja, el drawable ya trae su geometria
        Box(
            GlanceModifier
                .fillMaxWidth()
                .height(height)
                .background(ImageProvider(theme.hSepDrawable))
        ) {}
    } else {
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(height),
            contentAlignment = Alignment.Center
        ) {
            Box(
                GlanceModifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ImageProvider(theme.hSepDrawable))
            ) {}
        }
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    iconRes: Int,
    valueColor: Color,
    labelColor: ColorProvider,
    cellBg: ColorProvider,
    modifier: GlanceModifier
) {
    Column(
        modifier = modifier.fillMaxHeight().background(cellBg).padding(horizontal = 1.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = value,
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ColorProvider(valueColor)),
            maxLines = 1
        )
        // P-038: icono Material tintado con el color del stat, delante de la etiqueta
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.width(11.dp).height(11.dp),
                colorFilter = ColorFilter.tint(ColorProvider(valueColor))
            )
            Spacer(GlanceModifier.width(3.dp))
            Text(
                text = label,
                style = TextStyle(fontSize = 10.sp, color = labelColor),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DeltaBar(
    label: String,
    thisMonth: Int,
    lastMonth: Int,
    trackColor: ColorProvider,
    textMuted: ColorProvider,
    maxBarWidth: Dp
) {
    val noData = thisMonth == 0 && lastMonth == 0

    val deltaVal = if (lastMonth > 0) {
        ((thisMonth - lastMonth) * 100) / lastMonth
    } else if (thisMonth > 0) 100 else 0

    val absDelta = if (deltaVal < 0) -deltaVal else deltaVal
    val fillRatio = (minOf(absDelta, 100)).toFloat() / 100f
    val fillWidth = if (noData) 0.dp else (maxBarWidth * fillRatio).coerceAtLeast(2.dp)

    val isUp = thisMonth >= lastMonth
    val barColor = if (isUp) WidgetGreen else WidgetRed

    val deltaText = when {
        noData -> "-"
        deltaVal >= 0 -> "+${deltaVal}%"
        else -> "${deltaVal}%"
    }
    val deltaColor = when {
        noData -> textMuted
        isUp -> ColorProvider(WidgetGreen)
        else -> ColorProvider(WidgetRed)
    }

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 9.sp, color = textMuted),
            maxLines = 1,
            modifier = GlanceModifier.width(46.dp)
        )
        Box(
            modifier = GlanceModifier.defaultWeight().height(7.dp).background(trackColor),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                GlanceModifier
                    .width(fillWidth)
                    .height(7.dp)
                    .background(ColorProvider(barColor))
            ) {}
        }
        Text(
            text = deltaText,
            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = deltaColor),
            maxLines = 1,
            modifier = GlanceModifier.width(38.dp).padding(start = 4.dp)
        )
    }
}

private fun formatPeakHourSlot(slot: Int?): String {
    if (slot == null) return "-"
    val start = slot * 3
    val end = (start + 3) % 24
    return "${start.toString().padStart(2, '0')}-${end.toString().padStart(2, '0')}h"
}

private fun formatCompact(n: Int): String = when {
    n >= 1000 -> "${n / 1000}.${(n % 1000) / 100}k"
    else -> n.toString()
}
