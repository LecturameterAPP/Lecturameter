package com.lecturameter

// F8: tests de los helpers de DateEvent (relecturas, occurrences, migración legacy).

import com.lecturameter.model.Book
import com.lecturameter.model.BookStatus
import com.lecturameter.model.DateEvent
import com.lecturameter.model.computeReadingIndex
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
}
