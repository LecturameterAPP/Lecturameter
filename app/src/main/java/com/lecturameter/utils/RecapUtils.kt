package com.lecturameter.utils

// ── Fase 5: Predictor de finalización + Recap semanal ────────────────────────
// Lógica PURA (sin Android) para poder testearla en JVM. Regla de oro de ambas
// features: si no hay datos suficientes, se devuelve null y la UI no pinta nada
// — nunca se inventan métricas.

import com.lecturameter.model.Book
import com.lecturameter.model.BingoCard
import com.lecturameter.model.Challenge
import com.lecturameter.model.ChallengeType
import com.lecturameter.model.ReadingSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil

private val ISO = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

private fun isoToCal(iso: String): Calendar? = try {
    Calendar.getInstance().apply { time = ISO.get()!!.parse(iso)!! }
} catch (_: Exception) { null }

internal fun isoPlusDays(iso: String, days: Int): String {
    val c = isoToCal(iso) ?: return iso
    c.add(Calendar.DAY_OF_YEAR, days)
    return ISO.get()!!.format(c.time)
}

/** Días naturales entre dos fechas ISO (0 si son el mismo día). */
internal fun isoDaysBetween(startIso: String, endIso: String): Int {
    val a = isoToCal(startIso) ?: return 0
    val b = isoToCal(endIso) ?: return 0
    val ms = b.timeInMillis - a.timeInMillis
    return (ms / (24L * 60 * 60 * 1000)).toInt()
}

/** Lunes de la semana que contiene [iso]. */
internal fun mondayOf(iso: String): String {
    val c = isoToCal(iso) ?: return iso
    val shift = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7  // 0 = lunes … 6 = domingo
    c.add(Calendar.DAY_OF_YEAR, -shift)
    return ISO.get()!!.format(c.time)
}

// ── Predictor de finalización (P-C) ───────────────────────────────────────────

data class FinishPrediction(
    val pagesPerDay: Int,
    val daysLeft: Int,
    val targetDateIso: String
)

/**
 * Predice la fecha de finalización al ritmo actual.
 * Requisitos (si no se cumplen → null, la línea no aparece):
 *  - ≥3 sesiones con páginas en los últimos 30 días
 *  - quedan páginas por leer
 * Ritmo = páginas de las últimas 5 sesiones (ventana 30 días) ÷ días naturales
 * desde la más antigua de esas sesiones hasta HOY — así una pausa larga degrada
 * la predicción sola, sin heurísticas.
 */
fun predictFinish(sessions: List<ReadingSession>, pagesRemaining: Int, todayIso: String): FinishPrediction? {
    if (pagesRemaining <= 0) return null
    val cutoff = isoPlusDays(todayIso, -30)
    val window = sessions
        .filter { it.pages > 0 && it.date >= cutoff && it.date <= todayIso }
        .sortedByDescending { it.date }
        .take(5)
    if (window.size < 3) return null
    val oldest = window.minOf { it.date }
    val spanDays = (isoDaysBetween(oldest, todayIso) + 1).coerceAtLeast(1)
    val rate = window.sumOf { it.pages }.toDouble() / spanDays
    if (rate < 0.5) return null
    val daysLeft = ceil(pagesRemaining / rate).toInt().coerceAtLeast(1)
    return FinishPrediction(
        pagesPerDay = Math.round(rate).toInt().coerceAtLeast(1),
        daysLeft = daysLeft,
        targetDateIso = isoPlusDays(todayIso, daysLeft)
    )
}

// ── Recap semanal (R-1) ───────────────────────────────────────────────────────

data class WeeklyRecap(
    val weekStartIso: String,
    val weekEndIso: String,
    val sessionsCount: Int,
    val pages: Int,
    val minutes: Int?,              // null si ninguna sesión cronometrada
    val pagesPerMin: Double?,       // null si <10 min cronometrados
    val topSlotStartHour: Int?,     // 0/3/6…21 — null si <2 sesiones con hora
    val deltaPages: Int?,           // vs semana anterior; null si la anterior no tuvo sesiones
    val bestDayIso: String?,
    val bestDayPages: Int,
    val longestSessionMinutes: Int?,
    val finishedCount: Int,
    val startedCount: Int,
    val challengesAdvanced: Int,    // retos cuya métrica avanzó esta semana
    val bingoCellsCompleted: Int,   // celdas con completedAt en la semana (0 si el cartón es antiguo)
    val bookOfWeekId: Long?,
    val bookOfWeekPages: Int
)

// ── Recap mensual (6.4) ───────────────────────────────────────────────────────

data class MonthlyRecap(
    val monthKey: String,            // yyyy-MM del mes CERRADO
    val startIso: String,
    val endIso: String,
    val finishedCount: Int,
    val startedCount: Int,
    val droppedCount: Int,
    val pages: Int,
    val deltaPages: Int?,            // vs mes anterior; null si aquel no tuvo sesiones
    val minutes: Int?,
    val pagesPerMin: Double?,
    val bestWeekStartIso: String?,   // lunes de la mejor semana; null si <2 semanas con sesiones
    val bestWeekPages: Int,
    val bookOfMonthId: Long?,
    val bookOfMonthPages: Int
)

internal fun monthRange(monthKey: String): Pair<String, String> {
    val first = "$monthKey-01"
    val c = isoToCal(first) ?: return first to first
    c.add(Calendar.MONTH, 1)
    c.add(Calendar.DAY_OF_YEAR, -1)
    return first to ISO.get()!!.format(c.time)
}

/** Recap del mes CERRADO anterior a [todayIso] (julio se ve en agosto).
 *  Null si ese mes no tiene ninguna sesión — ni tarjeta ni pantalla. */
fun computeMonthlyRecap(books: List<Book>, sessions: List<ReadingSession>, todayIso: String): MonthlyRecap? {
    val firstOfCurrent = todayIso.take(7) + "-01"
    val lastOfPrev = isoPlusDays(firstOfCurrent, -1)
    val monthKey = lastOfPrev.take(7)
    if (monthKey >= todayIso.take(7)) return null
    val (start, end) = monthRange(monthKey)
    val month = sessions.filter { it.date in start..end }
    if (month.isEmpty()) return null

    val pages = month.sumOf { it.pages }
    val mins = month.mapNotNull { it.minutes }.sum().takeIf { it > 0 }
    val ppm = if (mins != null && mins >= 10 && pages > 0) pages.toDouble() / mins else null

    val prevKey = isoPlusDays(start, -1).take(7)
    val (pStart, pEnd) = monthRange(prevKey)
    val prev = sessions.filter { it.date in pStart..pEnd }
    val delta = if (prev.isNotEmpty()) pages - prev.sumOf { it.pages } else null

    // Mejor semana (lunes como clave); con una sola semana con sesiones no hay "mejor"
    val byWeek = month.groupBy { mondayOf(it.date) }.mapValues { (_, s) -> s.sumOf { it.pages } }
    val bestWeek = if (byWeek.size >= 2) byWeek.maxByOrNull { it.value } else null

    val finished = books.count { it.endDate != null && it.endDate in start..end }
    val started = books.count { it.startDate != null && it.startDate in start..end }
    val dropped = books.count { b -> b.dateEvents.any { it.type == "drop" && it.date in start..end } }

    val byBook = month.groupBy { it.bookId }.mapValues { (_, s) -> s.sumOf { it.pages } }
    val topBook = byBook.filterValues { it > 0 }.maxByOrNull { it.value }

    return MonthlyRecap(
        monthKey = monthKey, startIso = start, endIso = end,
        finishedCount = finished, startedCount = started, droppedCount = dropped,
        pages = pages, deltaPages = delta, minutes = mins, pagesPerMin = ppm,
        bestWeekStartIso = bestWeek?.key, bestWeekPages = bestWeek?.value ?: 0,
        bookOfMonthId = topBook?.key, bookOfMonthPages = topBook?.value ?: 0
    )
}

/** Recap de la semana (lunes–domingo) que contiene [todayIso]. Devuelve null si
 *  la semana no tiene ninguna sesión (no hay nada que contar). */
fun computeWeeklyRecap(
    books: List<Book>,
    sessions: List<ReadingSession>,
    bingoCard: BingoCard?,
    challenges: List<Challenge>,
    todayIso: String
): WeeklyRecap? {
    val start = mondayOf(todayIso)
    val end = isoPlusDays(start, 6)
    val week = sessions.filter { it.date in start..end }
    if (week.isEmpty()) return null

    val pages = week.sumOf { it.pages }
    val mins = week.mapNotNull { it.minutes }.sum().takeIf { it > 0 }
    val ppm = if (mins != null && mins >= 10 && pages > 0) pages.toDouble() / mins else null

    // Franja horaria: solo sesiones del crono (startTimestamp) — mínimo 2
    val timed = week.mapNotNull { it.startTimestamp }
    val topSlot = if (timed.size >= 2) {
        timed.groupingBy { ts ->
            Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY) / 3 * 3
        }.eachCount().maxByOrNull { it.value }?.key
    } else null

    val prevStart = isoPlusDays(start, -7)
    val prevEnd = isoPlusDays(start, -1)
    val prev = sessions.filter { it.date in prevStart..prevEnd }
    val delta = if (prev.isNotEmpty()) pages - prev.sumOf { it.pages } else null

    val byDay = week.groupBy { it.date }.mapValues { (_, s) -> s.sumOf { it.pages } }
    val bestDay = byDay.maxByOrNull { it.value }

    val longest = week.mapNotNull { it.minutes }.maxOrNull()

    val finished = books.count { it.endDate != null && it.endDate in start..end }
    val started = books.count { it.startDate != null && it.startDate in start..end }

    val advanced = challenges.count { ch ->
        when (ch.type) {
            ChallengeType.PAGES    -> pages > 0
            ChallengeType.SESSIONS -> week.isNotEmpty()
            ChallengeType.MINUTES  -> (mins ?: 0) > 0
            ChallengeType.BOOKS    -> finished > 0
            ChallengeType.STREAK   -> week.isNotEmpty()
        }
    }

    val bingoWeek = bingoCard?.cells?.count { it.completedAt != null && it.completedAt!! in start..end } ?: 0

    val byBook = week.groupBy { it.bookId }.mapValues { (_, s) -> s.sumOf { it.pages } }
    val topBook = byBook.filterValues { it > 0 }.maxByOrNull { it.value }

    return WeeklyRecap(
        weekStartIso = start, weekEndIso = end,
        sessionsCount = week.size, pages = pages,
        minutes = mins, pagesPerMin = ppm,
        topSlotStartHour = topSlot,
        deltaPages = delta,
        bestDayIso = bestDay?.key, bestDayPages = bestDay?.value ?: 0,
        longestSessionMinutes = longest,
        finishedCount = finished, startedCount = started,
        challengesAdvanced = advanced,
        bingoCellsCompleted = bingoWeek,
        bookOfWeekId = topBook?.key, bookOfWeekPages = topBook?.value ?: 0
    )
}
