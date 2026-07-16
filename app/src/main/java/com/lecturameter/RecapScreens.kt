package com.lecturameter

// Estadisticas avanzadas (heatmap horario, sagas, outliers) + recaps mensual y semanal.
// Extraido de MainActivity.kt el 15-07-2026 (ruptura del monolito, sin cambios funcionales).


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
// v21.42: Icons.Outlined.Star eliminado — estrellas usan ★/☆ Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import com.lecturameter.model.*
import com.lecturameter.utils.*
import androidx.navigation.compose.composable

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
                // Feedback 15-07: el número suelto ("0", "3"…) no decía si la columna era
                // la hora de inicio o el centro de la franja. Cada columna son 3 horas, así
                // que se etiqueta con el rango real. La última cierra en 00, no en 24.
                val slotLabels = listOf("0-3", "3-6", "6-9", "9-12", "12-15", "15-18", "18-21", "21-00")
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

// Cambio de última hora de Víctor 16-07: el mapa de calor HORARIO pasa a ser Pro. Este
// placeholder ocupa aproximadamente el mismo espacio que HourlyHeatmapCard (misma Surface,
// mismo padding) para que la pantalla no "salte" al activar/desactivar Pro, y al tocarlo
// abre el ProUpsellSheet (lo gestiona quien lo invoca, vía onClick).
@Composable
fun HourlyHeatmapLockedCard(theme: Theme, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.adv_hourly_title), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Icon(Icons.Default.Lock, null, tint = theme.textDim, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.adv_hourly_locked),
                color = theme.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(14.dp))
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

// ── Fase 5 (P-023): Recap semanal — pantalla R-1 del mockup_predictor_recap.html ──
// Una página de un golpe de vista (scroll solo de seguridad en pantallas muy bajas).
// Regla de oro: métrica sin datos suficientes = no aparece; nunca se inventa.

private fun fmtDayMonth(iso: String): String = try {
    java.text.SimpleDateFormat("d MMM", appDisplayLocale)
        .format(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(iso)!!)
} catch (_: Exception) { iso }

private fun fmtWeekdayName(iso: String): String = try {
    java.text.SimpleDateFormat("EEEE", appDisplayLocale)
        .format(java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(iso)!!)
        .replaceFirstChar { it.uppercase(appDisplayLocale) }
} catch (_: Exception) { iso }

@Composable
private fun RecapMini(value: String, label: String, valueColor: Color?, theme: Theme, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = modifier.fillMaxHeight()) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AutoSizeText(value, color = valueColor ?: theme.textMain, maxFontSize = 16.sp, minFontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(label, color = theme.textDim, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 11.sp, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Fase 6.4: Recap mensual — mes CERRADO, mockup M-1 aprobado 14-07 ──────────
// Ajustes de Víctor: sin emoji de medalla en "mejor semana"; la banda del Bingo
// usa el icono Material (GridView, el del rail); el libro del mes lleva PORTADA
// real y navega a su detalle.

// Nombre del mes en el idioma de la app, tal como lo da el locale (en ES minúscula:
// "julio" — los meses en español no se capitalizan a mitad de frase). Donde el mes
// abre frase (subtítulo del Wrapped) se capitaliza el resultado completo.
internal fun fmtMonthName(monthKey: String): String = try {
    java.text.SimpleDateFormat("LLLL", appDisplayLocale)
        .format(java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).parse(monthKey)!!)
} catch (_: Exception) { monthKey }

@Composable
private fun RecapBand(theme: Theme, icon: @Composable () -> Unit, title: String, sub: String,
                      onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border),
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it }
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, color = theme.textMain, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, color = theme.textMuted, fontSize = 11.5.sp)
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun MonthlyRecapScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit, onDetail: (Long) -> Unit) {
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val recap = remember(books, sessions) {
        com.lecturameter.utils.computeMonthlyRecap(books, sessions, today())
    }
    val bingoMonth = remember(recap?.monthKey) {
        recap?.let { r ->
            com.lecturameter.utils.BingoManager.loadMonthSummaries(prefs).filter { it.monthKey == r.monthKey }
        } ?: emptyList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recapm_title, recap?.monthKey?.let { fmtMonthName(it) } ?: ""),
                    color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold
                )
                if (recap != null) {
                    Text("${fmtDayMonth(recap.startIso)} – ${fmtDayMonth(recap.endIso)}", color = theme.textMuted, fontSize = 13.sp)
                }
            }
        }

        if (recap == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("📆", fontSize = 48.sp) }
        } else {
            val minis = buildList {
                add(Triple(recap.finishedCount.toString(), stringResource(R.string.recapm_finished), null as Color?))
                add(Triple(recap.startedCount.toString(), stringResource(R.string.recapm_started), null))
                add(Triple(recap.droppedCount.toString(), stringResource(R.string.recapm_dropped), null))
                add(Triple(recap.pages.toString(), stringResource(R.string.recap_pages), null))
                recap.minutes?.let { add(Triple(fmtMinutes(it), stringResource(R.string.recap_time), null)) }
                recap.pagesPerMin?.let { add(Triple(String.format(appDisplayLocale, "%.1f", it), stringResource(R.string.recap_speed), null)) }
                recap.deltaPages?.let { d ->
                    val txt = when { d > 0 -> "▲ +$d"; d < 0 -> "▼ $d"; else -> "＝ 0" }
                    add(Triple(txt, stringResource(R.string.recap_delta), when { d > 0 -> Green; d < 0 -> Red; else -> null }))
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                minis.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                        rowItems.forEach { (v, l, c) -> RecapMini(v, l, c, theme, Modifier.weight(1f)) }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                // Mejor semana (sin emoji — ajuste de Víctor); solo con ≥2 semanas con sesiones
                recap.bestWeekStartIso?.let { ws ->
                    RecapBand(
                        theme,
                        icon = { Icon(Icons.Default.DateRange, null, tint = accentForTheme(theme), modifier = Modifier.size(22.dp)) },
                        title = stringResource(R.string.recapm_best_week_title, fmtDayMonth(ws), fmtDayMonth(com.lecturameter.utils.isoPlusDays(ws, 6))),
                        sub = stringResource(R.string.recapm_best_week_sub, recap.bestWeekPages)
                    )
                }
                // Bingo del mes — icono Material GridView (ajuste de Víctor)
                if (bingoMonth.isNotEmpty()) {
                    val cells = bingoMonth.sumOf { it.cellsDone }
                    val lines = bingoMonth.sumOf { it.lines }
                    val completed = bingoMonth.any { it.complete }
                    RecapBand(
                        theme,
                        icon = { Icon(Icons.Default.GridView, null, tint = accentForTheme(theme), modifier = Modifier.size(22.dp)) },
                        title = stringResource(R.string.recapm_bingo_title, fmtMonthName(recap.monthKey)),
                        sub = stringResource(R.string.recapm_bingo_sub, cells, lines) +
                              if (completed) stringResource(R.string.recapm_bingo_completed_suffix) else ""
                    )
                }
                // Libro del mes — PORTADA real, clicable al detalle (ajuste de Víctor)
                recap.bookOfMonthId?.let { id ->
                    books.find { it.id == id }?.let { b ->
                        RecapBand(
                            theme,
                            icon = { BookCover(b.coverUrl, b.title, size = 40, isbnFallback = b.isbn) },
                            title = b.title,
                            sub = stringResource(R.string.recapm_book, recap.bookOfMonthPages),
                            onClick = { onDetail(b.id) },
                            trailing = { Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted) }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun WeeklyRecapScreen(vm: BooksViewModel, theme: Theme, onBack: () -> Unit, onDetail: (Long) -> Unit) {
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val bingoCard by vm.bingoCard.collectAsState()
    val challenges by vm.challenges.collectAsState()
    val recap = remember(books, sessions, bingoCard, challenges) {
        com.lecturameter.utils.computeWeeklyRecap(books, sessions, bingoCard, challenges, today())
    }
    val streak = remember(sessions) { vm.currentReadingStreak() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.recap_title), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (recap != null) {
                    Text("${fmtDayMonth(recap.weekStartIso)} – ${fmtDayMonth(recap.weekEndIso)}", color = theme.textMuted, fontSize = 13.sp)
                }
            }
        }

        if (recap == null) {
            // Solo alcanzable por estados intermedios: la tarjeta de acceso ya filtra
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("📅", fontSize = 48.sp) }
        } else {
            val minis = buildList {
                add(Triple(recap.sessionsCount.toString(), stringResource(R.string.recap_sessions), null as Color?))
                add(Triple(recap.pages.toString(), stringResource(R.string.recap_pages), null))
                recap.minutes?.let { add(Triple(fmtMinutes(it), stringResource(R.string.recap_time), null)) }
                recap.pagesPerMin?.let { add(Triple(String.format(appDisplayLocale, "%.1f", it), stringResource(R.string.recap_speed), null)) }
                recap.topSlotStartHour?.let { add(Triple(stringResource(R.string.recap_slot_value, it, it + 3), stringResource(R.string.recap_slot), null)) }
                recap.deltaPages?.let { d ->
                    val txt = when { d > 0 -> "▲ +$d"; d < 0 -> "▼ $d"; else -> "＝ 0" }
                    add(Triple(txt, stringResource(R.string.recap_delta), when { d > 0 -> Green; d < 0 -> Red; else -> null }))
                }
                if (streak > 0) add(Triple(androidx.compose.ui.res.pluralStringResource(R.plurals.recap_streak_value, streak, streak), stringResource(R.string.recap_streak), Amber))
                recap.bestDayIso?.let { add(Triple(fmtWeekdayName(it), stringResource(R.string.recap_best_day, recap.bestDayPages), null)) }
                recap.longestSessionMinutes?.let { add(Triple(fmtMinutes(it), stringResource(R.string.recap_longest), null)) }
                add(Triple(stringResource(R.string.recap_finished_started_value, recap.finishedCount, recap.startedCount), stringResource(R.string.recap_finished_started), null))
                add(Triple(stringResource(R.string.recap_challenges_bingo_value, recap.challengesAdvanced, recap.bingoCellsCompleted), stringResource(R.string.recap_challenges_bingo), null))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                minis.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                        rowItems.forEach { (v, l, c) -> RecapMini(v, l, c, theme, Modifier.weight(1f)) }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                // Libro de la semana — banda inferior; es lo primero que cae si no cabe (regla 11-07)
                recap.bookOfWeekId?.let { id ->
                    books.find { it.id == id }?.let { b ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = theme.surface,
                            border = BorderStroke(1.dp, theme.border),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clickable { onDetail(b.id) }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                BookCover(b.coverUrl, b.title, size = 44, isbnFallback = b.isbn)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(b.title, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(R.string.recap_book_of_week, recap.bookOfWeekPages), color = theme.textMuted, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

