package com.lecturameter

// F8: tests de los helpers de DateEvent (relecturas, occurrences, migración legacy).

import com.lecturameter.model.Book
import com.lecturameter.model.BookStatus
import com.lecturameter.model.DateEvent
import com.lecturameter.model.computeReadingIndex
import com.lecturameter.model.currentReadStart
import com.lecturameter.model.migrateLegacyToEvents
import com.lecturameter.model.renumberOccurrences
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {

    @Test fun computeReadingIndex_antes_y_despues_de_relecturas() {
        val events = listOf(
            DateEvent("start", "2026-01-01"),
            DateEvent("reread", "2026-03-01", 1),
            DateEvent("reread", "2026-06-01", 2)
        )
        assertEquals(0, computeReadingIndex("2026-02-15", events))
        assertEquals(1, computeReadingIndex("2026-03-01", events))
        assertEquals(1, computeReadingIndex("2026-05-30", events))
        assertEquals(2, computeReadingIndex("2026-07-01", events))
    }

    @Test fun renumberOccurrences_corrige_huecos_y_duplicados() {
        val events = listOf(
            DateEvent("drop", "2026-02-01", 3),   // hueco: debería ser 1
            DateEvent("resume", "2026-02-10", 3),
            DateEvent("drop", "2026-04-01", 3),   // duplicado: debería ser 2
            DateEvent("start", "2026-01-01", 1)
        )
        val fixed = renumberOccurrences(events)
        val drops = fixed.filter { it.type == "drop" }.map { it.occurrence }
        assertEquals(listOf(1, 2), drops)
        assertEquals(1, fixed.first { it.type == "resume" }.occurrence)
        // El orden final es cronológico
        assertEquals("start", fixed.first().type)
    }

    @Test fun migrateLegacy_genera_eventos_desde_campos_viejos() {
        val b = Book(
            id = 1L, title = "T", author = "A", pages = 100,
            startDate = "2026-01-01", endDate = "2026-02-01",
            status = BookStatus.FINISHED,
            dropDate = "2026-01-10", resumedDate = "2026-01-20"
        )
        val events = migrateLegacyToEvents(b)
        assertEquals(listOf("start", "drop", "resume", "end"), events.map { it.type })
        assertEquals("2026-01-10", events[1].date)
    }

    @Test fun migrateLegacy_respeta_eventos_existentes() {
        val existing = listOf(DateEvent("start", "2025-05-05"))
        val b = Book(
            id = 1L, title = "T", author = "A", pages = 100,
            startDate = "2026-01-01", endDate = null,
            status = BookStatus.READING, dateEvents = existing
        )
        assertEquals(existing, migrateLegacyToEvents(b))
    }

    // ── currentReadStart (B-025) ──────────────────────────────────────────────

    /** Caso real que destapó el bug: El imperio final, leído en 2022 y releído
     *  desde el 01-07-2026, mostraba "1359 días" porque contaba desde startDate. */
    @Test fun currentReadStart_releido_usa_la_fecha_de_relectura() {
        val b = Book(
            id = 1L, title = "El imperio final", author = "Brandon Sanderson", pages = 688,
            startDate = "2022-10-25", endDate = null,
            status = BookStatus.READING,
            dateEvents = listOf(
                DateEvent("start", "2022-10-25"),
                DateEvent("end", "2022-11-12"),
                DateEvent("reread", "2026-07-01", 1)
            )
        )
        assertEquals("2026-07-01", currentReadStart(b))
    }

    @Test fun currentReadStart_coge_la_relectura_mas_reciente() {
        val b = Book(
            id = 2L, title = "X", author = "Y", pages = 100,
            startDate = "2020-01-01", endDate = null,
            status = BookStatus.REREADING,
            dateEvents = listOf(
                DateEvent("start", "2020-01-01"),
                DateEvent("end", "2020-02-01"),
                DateEvent("reread", "2023-05-01", 1),
                DateEvent("reread_end", "2023-06-01", 1),
                DateEvent("reread", "2026-07-01", 2)
            )
        )
        assertEquals("2026-07-01", currentReadStart(b))
    }

    /** Un abandono retomado cuenta desde el resume, no desde el start original. */
    @Test fun currentReadStart_usa_el_resume_tras_un_drop() {
        val b = Book(
            id = 3L, title = "X", author = "Y", pages = 100,
            startDate = "2026-01-01", endDate = null,
            status = BookStatus.READING,
            dateEvents = listOf(
                DateEvent("start", "2026-01-01"),
                DateEvent("drop", "2026-02-01", 1),
                DateEvent("resume", "2026-06-20", 1)
            )
        )
        assertEquals("2026-06-20", currentReadStart(b))
    }

    /** Libros anteriores a v19.8 no tienen dateEvents: se cae al startDate legacy. */
    @Test fun currentReadStart_sin_eventos_cae_al_startDate() {
        val b = Book(
            id = 4L, title = "El dragon renacido", author = "Robert Jordan", pages = 672,
            startDate = "2026-06-03", endDate = null,
            status = BookStatus.READING, dateEvents = emptyList()
        )
        assertEquals("2026-06-03", currentReadStart(b))
    }
}
