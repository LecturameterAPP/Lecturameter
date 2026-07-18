package com.lecturameter.utils

// ── Stats del widget (Fase 2) ─────────────────────────────────────────────────
// Extraído de widget/BookWidget.kt para compartirse entre el widget (reescritura
// Glance de la Fase 2) y TimerService, tal como marca el plan. Sin cambios de lógica.

import com.lecturameter.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

data class WidgetStats(
    val days: Int,
    val totalMinutes: Int,
    val sessions: Int,
    val lastSessionMinutes: Int?,
    val lastSessionPages: Int?,   // v20.9: páginas de la última sesión (sustituye avgPages en widget)
    val completionPct: Int?
)

fun computeWidgetStats(
    book: Book,
    sessions: List<ReadingSession>,
    editionId: Long = -1L
): WidgetStats {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = sdf.format(Date())
    val allBookSessions = sessions.filter { it.bookId == book.id }
    val bookSessions = if (editionId != -1L) {
        allBookSessions.filter { it.editionId == editionId || it.editionId == null }
    } else {
        allBookSessions
    }
    val lastSessionDate = bookSessions.mapNotNull { it.date }.filter { it.isNotBlank() }.maxOrNull()
    val firstSessionDate = bookSessions.mapNotNull { it.date }.filter { it.isNotBlank() }.minOrNull()

    val days = if (editionId != -1L && firstSessionDate != null) {
        try {
            val start = sdf.parse(firstSessionDate)!!
            val endStr = lastSessionDate ?: today
            val end = sdf.parse(endStr)!!
            val diff = ceil((end.time - start.time) / 86400000.0).toInt()
            diff.coerceAtLeast(1)
        } catch (_: Exception) {
            1
        }
    } else {
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
        val endRef = when {
            book.status == BookStatus.FINISHED && !book.endDate.isNullOrBlank() -> book.endDate
            book.status == BookStatus.FINISHED && lastSessionDate != null -> lastSessionDate
            book.status == BookStatus.FINISHED && !book.startDate.isNullOrBlank() -> book.startDate
            else -> today
        }
        if (!effectiveStart.isNullOrBlank()) {
            try {
                val start = sdf.parse(effectiveStart)!!
                val end = sdf.parse(endRef)!!
                val diff = ceil((end.time - start.time) / 86400000.0).toInt()
                diff.coerceAtLeast(1)
            } catch (_: Exception) { 1 }
        } else { 1 }
    }

    val sortedSessions = bookSessions
        .sortedWith(compareByDescending<ReadingSession> { it.date }.thenByDescending { it.id })
    val lastMins = sortedSessions
        .firstOrNull { (it.minutes ?: 0) > 0 }
        ?.minutes
    val lastSessionPages = sortedSessions
        .firstOrNull { it.pages > 0 }
        ?.pages
        ?.takeIf { it > 0 }
    val pagesRead = bookSessions.sumOf { it.pages }

    val edition = if (editionId != -1L) book.editions.firstOrNull { it.id == editionId } else null
    val editionPages = edition?.pages?.takeIf { it > 0 }
    val effectiveTotal = when {
        editionPages != null -> editionPages
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
