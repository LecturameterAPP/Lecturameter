package com.lecturameter

// F8: tests de RecapUtils (predictor + recap semanal).
// Pendiente desde Fase 5 ("Tests de RecapUtils → Fase 8").

import com.lecturameter.model.ReadingSession
import com.lecturameter.utils.computeWeeklyRecap
import com.lecturameter.utils.predictFinish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecapUtilsTest {

    private fun session(date: String, pages: Int, minutes: Int? = null, bookId: Long = 1L, id: Long = System.nanoTime()) =
        ReadingSession(id = id, bookId = bookId, date = date, pages = pages, minutes = minutes)

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

    @Test fun predictor_usa_el_ritmo_por_dia_de_lectura() {
        // 3 sesiones de 30 págs en 3 días distintos → 90/3 = 30 págs por día leído
        val sessions = listOf(
            session("2026-07-13", 30), session("2026-07-14", 30), session("2026-07-15", 30)
        )
        val p = predictFinish(sessions, pagesRemaining = 90, todayIso = "2026-07-15")
        assertNotNull(p)
        assertEquals(30.0, p!!.pagesPerDay, 0.05)
        assertEquals(3, p.readingDaysLeft)
    }

    /** Dos sesiones el MISMO día cuentan como un solo día de lectura — es lo que
     *  hace la pill "Págs/día", y ambos números deben coincidir. */
    @Test fun predictor_cuenta_dias_no_sesiones() {
        val sessions = listOf(
            session("2026-07-14", 20), session("2026-07-15", 20), session("2026-07-15", 20)
        )
        val p = predictFinish(sessions, pagesRemaining = 60, todayIso = "2026-07-15")
        assertNotNull(p)
        assertEquals(30.0, p!!.pagesPerDay, 0.05)   // 60 págs / 2 días, no / 3 sesiones
        assertEquals(2, p.readingDaysLeft)
    }

    /** Caso real que destapó la disonancia: El dragón renacido, 172 págs en 9 días
     *  con sesión. La pill decía 19,1 y el predictor 5 — ahora dicen lo mismo. */
    @Test fun predictor_reproduce_el_caso_del_dragon_renacido() {
        val sessions = listOf(
            session("2026-06-03", 19), session("2026-06-05", 14), session("2026-06-14", 13),
            session("2026-06-18", 18), session("2026-06-24", 14), session("2026-06-26", 42),
            session("2026-06-27", 14), session("2026-07-14", 12), session("2026-07-15", 26)
        )
        val p = predictFinish(sessions, pagesRemaining = 485, todayIso = "2026-07-15")
        assertNotNull(p)
        assertEquals(19.1, p!!.pagesPerDay, 0.05)          // == la pill
        assertEquals(26, p.readingDaysLeft)                // ceil(485 / 19.111)
    }

    /** Un libro parado hace medio año no debe seguir prometiendo nada, aunque su
     *  ritmo histórico fuese bueno. */
    @Test fun predictor_null_si_no_hay_actividad_reciente() {
        val sessions = listOf(
            session("2026-01-10", 30), session("2026-01-11", 30), session("2026-01-12", 30)
        )
        assertNull(predictFinish(sessions, 100, "2026-07-15"))
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
}
