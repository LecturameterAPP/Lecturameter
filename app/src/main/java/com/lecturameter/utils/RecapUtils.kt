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
    /** Págs/día EN DÍAS DE LECTURA — el mismo número que la pill "Págs/día". */
    val pagesPerDay: Double,
    /** Días DE LECTURA que faltan (no días naturales). */
    val readingDaysLeft: Int
)

/**
 * Estima cuánto queda de un libro al ritmo real de lectura.
 *
 * Decisión de Víctor (15-07-2026) tras detectar la disonancia: **el predictor usa
 * el mismo ritmo que la pill "Págs/día"**, es decir páginas ÷ días CON SESIÓN, no
 * días naturales. Antes usaba las últimas 5 sesiones ÷ su span natural, y salían
 * dos números irreconciliables en la misma pantalla (19,1 en la pill y 5 en la
 * predicción) que nadie podía comprobar a mano.
 *
 * Consecuencia importante: si el ritmo es "por día que lees", lo que queda son
 * **días de lectura**, no días de calendario — convertirlos a una fecha exigiría
 * adivinar cada cuánto lee, que es justo la clase de invento que el proyecto
 * prohíbe. Por eso ya no se devuelve fecha objetivo: se dice el dato comprobable.
 *
 * Requisitos (si no se cumplen → null, la línea no aparece):
 *  - quedan páginas por leer
 *  - ≥3 sesiones con páginas (regla original: no inventar con dos datos)
 *  - alguna sesión en los últimos 30 días — sin esto, un libro parado hace medio
 *    año seguiría prometiendo una fecha.
 */
fun predictFinish(sessions: List<ReadingSession>, pagesRemaining: Int, todayIso: String): FinishPrediction? {
    if (pagesRemaining <= 0) return null
    val withPages = sessions.filter { it.pages > 0 && it.date <= todayIso }
    if (withPages.size < 3) return null
    val cutoff = isoPlusDays(todayIso, -30)
    if (withPages.none { it.date >= cutoff }) return null
    val readingDays = withPages.map { it.date }.toSet().size.coerceAtLeast(1)
    val rate = withPages.sumOf { it.pages }.toDouble() / readingDays
    if (rate < 0.5) return null
    return FinishPrediction(
        pagesPerDay = rate,
        readingDaysLeft = ceil(pagesRemaining / rate).toInt().coerceAtLeast(1)
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
    val bookOfWeekPages: Int,
    val topGenre: String?            // género con más páginas leídas esta semana; null si ningún libro tiene género
)

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

    val bookMap = books.associateBy { it.id }
    val byGenre = mutableMapOf<String, Int>()
    week.forEach { s ->
        val book = bookMap[s.bookId] ?: return@forEach
        book.genres.forEach { g -> if (g.isNotBlank()) byGenre[g] = (byGenre[g] ?: 0) + s.pages }
    }
    val topGenre = byGenre.maxByOrNull { it.value }?.key

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
        bookOfWeekId = topBook?.key, bookOfWeekPages = topBook?.value ?: 0,
        topGenre = topGenre
    )
}
