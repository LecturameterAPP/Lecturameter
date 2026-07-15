package com.lecturameter

// F8: tests de RecapUtils (predictor + recap semanal + recap mensual).
// Pendiente desde Fase 5 ("Tests de RecapUtils → Fase 8").

import com.lecturameter.model.Book
import com.lecturameter.model.BookStatus
import com.lecturameter.model.ReadingSession
import com.lecturameter.utils.computeMonthlyRecap
import com.lecturameter.utils.computeWeeklyRecap
import com.lecturameter.utils.predictFinish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecapUtilsTest {

    private fun session(date: String, pages: Int, minutes: Int? = null, bookId: Long = 1L, id: Long = System.nanoTime()) =
        ReadingSession(id = id, bookId = bookId, date = date, pages = pages, minutes = minutes)

    private fun book(
        id: Long = 1L, status: BookStatus = BookStatus.READING,
        start: String? = "2026-07-01", end: String? = null
    ) = Book(id = id, title = "T", author = "A", pages = 300, startDate = start, endDate = end, status = status)

    // ── predictFinish ─────────────────────────────────────────────────────────

    @Test fun predictor_null_con_menos_de_3_sesiones() {
        val sessions = listOf(session("2026-07-13", 20), session("2026-07-14", 20))
        assertNull(predictFinish(sessions, pagesRemaining = 100, todayIso = "2026-07-15"))
    }

    @Test fun predictor_null_sin_paginas_restantes() {
        assertNull(predictFinish(emptyList(), pagesRemaining = 0, todayIso = "2026-07-15"))
    }

    @Test fun predictor_null_si_sesiones_fuera_de_ventana_30d() {
        val sessions = listOf(
            session("2026-05-01", 30), session("2026-05-02", 30), session("2026-05-03", 30)
        )
        assertNull(predictFinish(sessions, 100, "2026-07-15"))
    }

    @Test fun predictor_calcula_ritmo_y_fecha() {
        // 3 sesiones de 30 págs en los días 13/14/15 → span 3 días → 30 págs/día
        val sessions = listOf(
            session("2026-07-13", 30), session("2026-07-14", 30), session("2026-07-15", 30)
        )
        val p = predictFinish(sessions, pagesRemaining = 90, todayIso = "2026-07-15")
        assertNotNull(p)
        assertEquals(30, p!!.pagesPerDay)
        assertEquals(3, p.daysLeft)
        assertEquals("2026-07-18", p.targetDateIso)
    }

    // ── computeWeeklyRecap ────────────────────────────────────────────────────

    @Test fun recap_semanal_null_sin_sesiones_en_la_semana() {
        val sessions = listOf(session("2026-07-05", 40))  // domingo semana anterior
        assertNull(computeWeeklyRecap(emptyList(), sessions, null, emptyList(), "2026-07-15"))
    }

    @Test fun recap_semanal_suma_y_ventana_lunes_domingo() {
        // 15-07-2026 es miércoles; su semana = 13..19 de julio
        val sessions = listOf(
            session("2026-07-13", 12, 30),
            session("2026-07-15", 26, 37),
            session("2026-07-12", 99)          // domingo anterior: fuera
        )
        val r = computeWeeklyRecap(emptyList(), sessions, null, emptyList(), "2026-07-15")
        assertNotNull(r)
        assertEquals("2026-07-13", r!!.weekStartIso)
        assertEquals("2026-07-19", r.weekEndIso)
        assertEquals(2, r.sessionsCount)
        assertEquals(38, r.pages)
        assertEquals(67, r.minutes)
        // delta vs semana anterior (99 págs): 38 - 99 = -61
        assertEquals(-61, r.deltaPages)
        assertEquals("2026-07-15", r.bestDayIso)
        assertEquals(26, r.bestDayPages)
    }

    @Test fun recap_semanal_libro_de_la_semana() {
        val sessions = listOf(
            session("2026-07-14", 10, bookId = 1L),
            session("2026-07-15", 50, bookId = 2L)
        )
        val r = computeWeeklyRecap(emptyList(), sessions, null, emptyList(), "2026-07-15")
        assertEquals(2L, r!!.bookOfWeekId)
        assertEquals(50, r.bookOfWeekPages)
    }

    // ── computeMonthlyRecap ───────────────────────────────────────────────────

    @Test fun recap_mensual_es_del_mes_cerrado() {
        val sessions = listOf(
            session("2026-06-10", 100, 60),
            session("2026-06-20", 50),
            session("2026-07-01", 999)   // mes en curso: fuera
        )
        val r = computeMonthlyRecap(emptyList(), sessions, "2026-07-15")
        assertNotNull(r)
        assertEquals("2026-06", r!!.monthKey)
        assertEquals(150, r.pages)
        assertEquals(60, r.minutes)
    }

    @Test fun recap_mensual_null_si_junio_vacio() {
        val sessions = listOf(session("2026-07-01", 10))
        assertNull(computeMonthlyRecap(emptyList(), sessions, "2026-07-15"))
    }

    @Test fun recap_mensual_libros_terminados_y_empezados() {
        val books = listOf(
            book(id = 1L, status = BookStatus.FINISHED, start = "2026-05-01", end = "2026-06-15"),
            book(id = 2L, status = BookStatus.READING, start = "2026-06-20")
        )
        val sessions = listOf(session("2026-06-10", 10))
        val r = computeMonthlyRecap(books, sessions, "2026-07-15")
        assertEquals(1, r!!.finishedCount)
        assertEquals(1, r.startedCount)
    }
}
