package com.lecturameter

// F8: tests de la validación del Bingo (celdas, líneas, saga, autor nuevo).

import com.lecturameter.model.BingoCard
import com.lecturameter.model.BingoCell
import com.lecturameter.model.Book
import com.lecturameter.model.BookStatus
import com.lecturameter.utils.BingoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BingoManagerTest {

    private fun cell(type: String, value: String, done: Boolean = false) =
        BingoCell(conditionType = type, conditionValue = value, labelEs = "", labelEn = "", isCompleted = done)

    private fun card(cells: List<BingoCell>) = BingoCard(
        templateId = "t", templateNameEs = "", templateNameEn = "", monthKey = "2026-07", cells = cells
    )

    private fun book(
        title: String = "Libro", genres: List<String> = emptyList(),
        pages: Int = 300, rating: Int = 0, author: String = "Autor",
        status: BookStatus = BookStatus.FINISHED
    ) = Book(
        id = 1L, title = title, author = author, pages = pages,
        startDate = "2026-07-01", endDate = "2026-07-10", status = status,
        rating = rating, genres = genres
    )

    // ── sideOf ────────────────────────────────────────────────────────────────

    @Test fun sideOf_cuadrados_perfectos() {
        assertEquals(3, BingoManager.sideOf(9))
        assertEquals(4, BingoManager.sideOf(16))
        assertEquals(0, BingoManager.sideOf(10))
    }

    @Test fun lineIndices_diagonales_4x4() {
        assertEquals(listOf(0, 5, 10, 15), BingoManager.lineIndices(4, "diag0"))
        assertEquals(listOf(3, 6, 9, 12), BingoManager.lineIndices(4, "diag1"))
    }

    // ── evaluateBookFinished ──────────────────────────────────────────────────

    @Test fun celda_genero_ignora_mayusculas() {
        val c = card(List(9) { if (it == 0) cell("genre", "Fantasía") else cell("streak", "99") })
        val result = BingoManager.evaluateBookFinished(c, book(genres = listOf("fantasía")), emptyList())
        assertTrue(result.cells[0].isCompleted)
        assertEquals(1L, result.cells[0].completedByBookId)
    }

    @Test fun celda_pages_gt_y_lt() {
        val c = card(List(9) {
            when (it) { 0 -> cell("pages_gt", "500"); 1 -> cell("pages_lt", "150"); else -> cell("streak", "99") }
        })
        val grande = BingoManager.evaluateBookFinished(c, book(pages = 501), emptyList())
        assertTrue(grande.cells[0].isCompleted)
        assertFalse(grande.cells[1].isCompleted)
        val corto = BingoManager.evaluateBookFinished(c, book(pages = 100), emptyList())
        assertFalse(corto.cells[0].isCompleted)
        assertTrue(corto.cells[1].isCompleted)
    }

    @Test fun celda_pages_lt_rechaza_cero_paginas() {
        val c = card(List(9) { if (it == 0) cell("pages_lt", "150") else cell("streak", "99") })
        val result = BingoManager.evaluateBookFinished(c, book(pages = 0), emptyList())
        assertFalse(result.cells[0].isCompleted)
    }

    @Test fun celda_rating_umbral() {
        val c = card(List(9) { if (it == 0) cell("rating", "8") else cell("streak", "99") })
        assertFalse(BingoManager.evaluateBookFinished(c, book(rating = 7), emptyList()).cells[0].isCompleted)
        assertTrue(BingoManager.evaluateBookFinished(c, book(rating = 8), emptyList()).cells[0].isCompleted)
    }

    @Test fun autor_nuevo_solo_si_primer_terminado() {
        val c = card(List(9) { if (it == 0) cell("author_new", "") else cell("streak", "99") })
        val nuevo = book(author = "Robert Jordan")
        // Solo él mismo terminado → nuevo
        assertTrue(BingoManager.evaluateBookFinished(c, nuevo, listOf(nuevo)).cells[0].isCompleted)
        // Ya había otro terminado del mismo autor → no
        val previo = book(title = "Otro", author = "robert jordan").copy(id = 2L)
        assertFalse(BingoManager.evaluateBookFinished(c, nuevo, listOf(nuevo, previo)).cells[0].isCompleted)
    }

    @Test fun completar_fila_registra_linea() {
        // 3×3: fila 0 = celdas 0,1,2. Dos ya hechas, la tercera la completa el libro.
        val cells = List(9) {
            when (it) {
                0, 1 -> cell("genre", "Fantasía", done = true)
                2 -> cell("pages_gt", "100")
                else -> cell("streak", "99")
            }
        }
        val result = BingoManager.evaluateBookFinished(card(cells), book(pages = 300), emptyList())
        assertTrue("row0" in result.completedLines)
    }

    @Test fun evaluateStreak_solo_celdas_streak() {
        val cells = List(9) { if (it == 4) cell("streak", "5") else cell("genre", "Poesía") }
        val result = BingoManager.evaluateStreak(card(cells), streakDays = 5)
        assertTrue(result.cells[4].isCompleted)
        assertFalse(result.cells[0].isCompleted)
        val insuficiente = BingoManager.evaluateStreak(card(cells), streakDays = 4)
        assertFalse(insuficiente.cells[4].isCompleted)
    }

    // ── looksLikeSaga ─────────────────────────────────────────────────────────

    @Test fun saga_detecta_formatos_habituales() {
        assertTrue(BingoManager.looksLikeSaga(book(title = "El Ojo del Mundo (La Rueda del Tiempo, #1)")))
        assertTrue(BingoManager.looksLikeSaga(book(title = "Mistborn Vol. 2")))
        assertTrue(BingoManager.looksLikeSaga(book(title = "Dune Libro 3")))
        assertFalse(BingoManager.looksLikeSaga(book(title = "1984")))
        assertFalse(BingoManager.looksLikeSaga(book(title = "El Camino de los Reyes")))
    }

    @Test fun cartas_completas() {
        val done = card(List(9) { cell("genre", "x", done = true) })
        val notDone = card(List(9) { cell("genre", "x", done = it != 8) })
        assertTrue(BingoManager.isCardComplete(done))
        assertFalse(BingoManager.isCardComplete(notDone))
    }
}
