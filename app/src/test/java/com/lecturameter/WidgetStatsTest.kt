package com.lecturameter

// F8: tests de computeWidgetStats (lógica pura del widget).

import com.lecturameter.model.Book
import com.lecturameter.model.BookStatus
import com.lecturameter.model.DateEvent
import com.lecturameter.model.ReadingSession
import com.lecturameter.utils.computeWidgetStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetStatsTest {

    private fun book(
        id: Long = 1L,
        status: BookStatus = BookStatus.READING,
        start: String? = "2026-07-01",
        end: String? = null,
        pages: Int = 200,
        first: Int? = null,
        last: Int? = null,
        events: List<DateEvent> = emptyList()
    ) = Book(
        id = id, title = "T", author = "A", pages = pages,
        startDate = start, endDate = end, status = status,
        firstFunctionalPage = first, lastFunctionalPage = last, dateEvents = events
    )

    private fun session(bookId: Long, date: String, pages: Int, minutes: Int? = null, id: Long = System.nanoTime()) =
        ReadingSession(id = id, bookId = bookId, date = date, pages = pages, minutes = minutes)

    @Test fun libro_terminado_usa_endDate_para_los_dias() {
        val b = book(status = BookStatus.FINISHED, start = "2026-07-01", end = "2026-07-10")
        val stats = computeWidgetStats(b, emptyList())
        assertEquals(9, stats.days)
    }

    @Test fun sesiones_de_otros_libros_no_cuentan() {
        val b = book(id = 1L)
        val sessions = listOf(session(1L, "2026-07-02", 20, 30), session(99L, "2026-07-02", 50, 60))
        val stats = computeWidgetStats(b, sessions)
        assertEquals(1, stats.sessions)
        assertEquals(30, stats.totalMinutes)
    }

    @Test fun porcentaje_con_paginas_funcionales() {
        // Contenido real: págs 11..110 = 100 págs; leídas 25 → 25%
        val b = book(first = 11, last = 110, pages = 200)
        val stats = computeWidgetStats(b, listOf(session(1L, "2026-07-02", 25)))
        assertEquals(25, stats.completionPct)
    }

    @Test fun porcentaje_capado_a_100() {
        val b = book(pages = 50)
        val stats = computeWidgetStats(b, listOf(session(1L, "2026-07-02", 80)))
        assertEquals(100, stats.completionPct)
    }

    @Test fun sin_sesiones_no_hay_porcentaje() {
        assertNull(computeWidgetStats(book(), emptyList()).completionPct)
    }

    @Test fun ultima_sesion_por_fecha_y_id() {
        val sessions = listOf(
            session(1L, "2026-07-01", 10, 15, id = 1),
            session(1L, "2026-07-03", 30, 45, id = 2),
            session(1L, "2026-07-02", 20, 25, id = 3)
        )
        val stats = computeWidgetStats(book(), sessions)
        assertEquals(45, stats.lastSessionMinutes)
        assertEquals(30, stats.lastSessionPages)
    }

    @Test fun relectura_cuenta_dias_desde_el_evento_reread() {
        val b = book(
            status = BookStatus.REREADING,
            start = "2025-01-01",
            events = listOf(DateEvent("start", "2025-01-01"), DateEvent("reread", "2026-07-10"))
        )
        val stats = computeWidgetStats(b, emptyList())
        // Desde el reread (10-07) hasta hoy — muchos menos días que desde 2025.
        // No fijamos "hoy" así que solo comprobamos que no usa la fecha de 2025 (>500 días).
        org.junit.Assert.assertTrue("días=${stats.days}", stats.days < 400)
    }
}
