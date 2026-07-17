package com.lecturameter

// F8: tests de la validación del Bingo (celdas, líneas, saga, autor nuevo).

import com.lecturameter.model.BingoCard
import com.lecturameter.model.BingoCell
import com.lecturameter.model.Book
import com.lecturameter.model.BookStatus
import com.lecturameter.utils.BingoManager
import com.lecturameter.utils.BingoMonthSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BingoManagerTest {

    private fun cell(type: String, value: String, done: Boolean = false) =
        BingoCell(conditionType = type, conditionValue = value, labelEs = "", labelEn = "", isCompleted = done)

    private fun card(cells: List<BingoCell>) = BingoCard(
        templateId = "t", templateNameEs = "", templateNameEn = "", monthKey = "2026-07", cells = cells
    )

    private fun summary(
        monthKey: String = "2026-07", cellsTotal: Int = 16, name: String = "El clásico",
        pattern: String = ""
    ) = BingoMonthSummary(
        monthKey = monthKey, templateNameEs = name, templateNameEn = name,
        cellsDone = 1, cellsTotal = cellsTotal, lines = 0, complete = false, pattern = pattern
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

    // ── B4 (2): convivencia del 4×4 y el 3×3 ──────────────────────────────────

    @Test fun lineIndices_diagonales_3x3() {
        // El 3×3 cierra linea con 3 casillas, no con 4: por eso es mas duro por geometria.
        assertEquals(listOf(0, 4, 8), BingoManager.lineIndices(3, "diag0"))
        assertEquals(listOf(2, 4, 6), BingoManager.lineIndices(3, "diag1"))
        assertEquals(listOf(0, 1, 2), BingoManager.lineIndices(3, "row0"))
        assertEquals(listOf(0, 3, 6), BingoManager.lineIndices(3, "col0"))
    }

    @Test fun completar_fila_de_3_registra_linea() {
        val cells = List(9) {
            when (it) {
                0, 1 -> cell("genre", "Fantasía", done = true)
                2 -> cell("pages_gt", "100")
                else -> cell("streak", "99")
            }
        }
        val result = BingoManager.evaluateBookFinished(card(cells), book(pages = 300), emptyList())
        assertTrue("row0" in result.completedLines)
        // Y la columna 0 NO, que solo tiene una casilla hecha
        assertFalse("col0" in result.completedLines)
    }

    @Test fun sideOfSummary_deduce_el_tamano_de_cellsTotal() {
        // La clave de que el historial englobe los dos bingos sin migrar nada: el tamaño
        // sale de cellsTotal, que TODOS los resumenes ya guardados tienen.
        assertEquals(3, BingoManager.sideOfSummary(summary(cellsTotal = 9)))
        assertEquals(4, BingoManager.sideOfSummary(summary(cellsTotal = 16)))
    }

    @Test fun summaryKey_distingue_el_3x3_del_4x4_del_mismo_mes() {
        // El fallo que arregla: el merge del backup iba por monthKey, asi que un mes con
        // sus dos cartones (lo normal en un Pro) perdia uno al restaurar.
        val c4 = summary(monthKey = "2026-07", cellsTotal = 16, name = "El clásico")
        val c3 = summary(monthKey = "2026-07", cellsTotal = 9, name = "El tocho")
        assertNotEquals(BingoManager.summaryKey(c4), BingoManager.summaryKey(c3))
    }

    @Test fun summaryKey_distingue_dos_cartones_del_mismo_mes_y_tamano() {
        // Caso que YA existia antes del 3×3: completas el 4×4, pides otro, y el mes archiva
        // dos cartones de 16. Se diferencian por plantilla.
        val a = summary(monthKey = "2026-07", cellsTotal = 16, name = "El clásico")
        val b = summary(monthKey = "2026-07", cellsTotal = 16, name = "El explorador")
        assertNotEquals(BingoManager.summaryKey(a), BingoManager.summaryKey(b))
    }

    @Test fun summaryKey_el_mismo_resumen_dos_veces_es_el_mismo() {
        val a = summary(monthKey = "2026-07", cellsTotal = 16, name = "El clásico")
        val b = summary(monthKey = "2026-07", cellsTotal = 16, name = "El clásico")
        assertEquals(BingoManager.summaryKey(a), BingoManager.summaryKey(b))
    }

    // ── B4 (2, costura bingo↔wrapped): el historial no se duplica al archivar ──────
    // Escenario real: reconcileBingo3Entitlement archiva el 3×3 en CADA load(), y restaurar
    // un backup repone un cartón ya archivado (restoreCard ve local=null → localDone=-1 → todo
    // progreso gana). El Wrapped suma cellsDone/lines del año: duplicar = inflar las cifras.

    /** Cartón de [total] celdas con las [done] primeras marcadas. */
    private fun playedCard(
        total: Int, done: Int, name: String = "El tocho", monthKey: String = "2026-07"
    ) = BingoCard(
        templateId = name, templateNameEs = name, templateNameEn = name, monthKey = monthKey,
        cells = (0 until total).map {
            BingoCell(conditionType = "genre", conditionValue = "x", labelEs = "c$it", labelEn = "c$it",
                isCompleted = it < done)
        }
    )

    @Test fun appendMonthSummary_el_mismo_carton_archivado_dos_veces_no_duplica_el_mes() {
        // Trial con 4 casillas del 3×3 → caduca → reconcile archiva → restauras el backup
        // (repone bingo_card_3) → siguiente arranque, reconcile vuelve a archivar.
        val prefs = FakePrefs()
        val card = playedCard(total = 9, done = 4)
        BingoManager.appendMonthSummary(prefs, card)
        BingoManager.appendMonthSummary(prefs, card)
        val history = BingoManager.loadMonthSummaries(prefs)
        assertEquals(1, history.size)
        // Lo que veía el Wrapped: 8 casillas de un cartón que solo tiene 4 hechas.
        assertEquals(4, history.sumOf { it.cellsDone })
    }

    @Test fun appendMonthSummary_reponer_el_carton_con_mas_progreso_no_suma_las_dos_veces() {
        // El duplicado que NO cazaba deduplicar por patrón: mismo cartón archivado con
        // progresos distintos (archivado a 4 → restauras un backup con 6 → se rearchiva).
        val prefs = FakePrefs()
        BingoManager.appendMonthSummary(prefs, playedCard(total = 9, done = 4))
        BingoManager.appendMonthSummary(prefs, playedCard(total = 9, done = 6))
        val history = BingoManager.loadMonthSummaries(prefs)
        assertEquals(1, history.size)
        // Gana el más avanzado, y suma 6 (no 4+6=10)
        assertEquals(6, history.single().cellsDone)
    }

    @Test fun appendMonthSummary_el_4x4_y_el_3x3_del_mismo_mes_conviven() {
        // Lo normal en un Pro: el mes archiva un cartón de cada tamaño. Deduplicar no puede
        // comerse uno de los dos.
        val prefs = FakePrefs()
        BingoManager.appendMonthSummary(prefs, playedCard(total = 16, done = 5, name = "El clásico"))
        BingoManager.appendMonthSummary(prefs, playedCard(total = 9, done = 4, name = "El tocho"))
        assertEquals(2, BingoManager.loadMonthSummaries(prefs).size)
    }

    @Test fun appendMonthSummary_dos_cartones_distintos_del_mismo_mes_y_tamano_conviven() {
        // Completas el 4×4, pides otro, y ese también se archiva: distinta plantilla.
        val prefs = FakePrefs()
        BingoManager.appendMonthSummary(prefs, playedCard(total = 16, done = 16, name = "El clásico"))
        BingoManager.appendMonthSummary(prefs, playedCard(total = 16, done = 3, name = "El explorador"))
        val history = BingoManager.loadMonthSummaries(prefs)
        assertEquals(2, history.size)
        assertEquals(19, history.sumOf { it.cellsDone })
    }

    @Test fun mergeSummaries_restaurar_dos_veces_el_mismo_backup_no_infla_el_historial() {
        // El backup hace MERGE, no reemplazo: restaurar dos veces el mismo fichero tiene que
        // dar el mismo historial.
        val local = listOf(summary(monthKey = "2026-03", cellsTotal = 9, name = "El tocho"))
        val once = BingoManager.mergeSummaries(local, local)
        val twice = BingoManager.mergeSummaries(once, local)
        assertEquals(1, once.size)
        assertEquals(once, twice)
    }

    // ── B4 (2, costura bingo↔wrapped): "tu mejor mes" compara porcentajes ─────────

    private fun played(
        monthKey: String, done: Int, total: Int, lines: Int = 0, name: String = "t"
    ) = BingoMonthSummary(
        monthKey = monthKey, templateNameEs = name, templateNameEn = name, cellsDone = done,
        cellsTotal = total, lines = lines, complete = done == total, pattern = "1".repeat(done)
    )

    @Test fun bestMonthSummary_el_3x3_perfecto_gana_al_4x4_a_medias() {
        // El bug: maxByOrNull { cellsDone } daba julio porque 10 > 9, y el ÚNICO cartón
        // perfecto del año no salía en la slide que presume de él.
        val marzo = played("2026-03", done = 9, total = 9, lines = 8)   // 9/9 = 1.00
        val julio = played("2026-07", done = 10, total = 16, lines = 1) // 10/16 = 0.63
        assertEquals("2026-03", BingoManager.bestMonthSummary(listOf(marzo, julio))?.monthKey)
    }

    @Test fun bestMonthSummary_a_igual_porcentaje_gana_el_carton_grande() {
        // Dos cartones perfectos: pesa más el de 16 casillas.
        val tresPerfecto = played("2026-03", done = 9, total = 9, lines = 8)
        val cuatroPerfecto = played("2026-07", done = 16, total = 16, lines = 10)
        assertEquals("2026-07", BingoManager.bestMonthSummary(listOf(tresPerfecto, cuatroPerfecto))?.monthKey)
    }

    @Test fun bestMonthSummary_a_igual_porcentaje_desempata_por_lineas() {
        val pocasLineas = played("2026-02", done = 8, total = 16, lines = 0)
        val masLineas = played("2026-05", done = 8, total = 16, lines = 2)
        assertEquals("2026-05", BingoManager.bestMonthSummary(listOf(pocasLineas, masLineas))?.monthKey)
    }

    @Test fun bestMonthSummary_aguanta_un_resumen_corrupto_sin_dividir_por_cero() {
        // cellsTotal 0 = default de Gson en un resumen corrupto: no puede reventar el Wrapped.
        val roto = played("2026-01", done = 0, total = 0)
        val bueno = played("2026-04", done = 2, total = 16)
        assertEquals(0f, BingoManager.progressRatio(roto), 0f)
        assertEquals("2026-04", BingoManager.bestMonthSummary(listOf(roto, bueno))?.monthKey)
        assertEquals(null, BingoManager.bestMonthSummary(emptyList()))
    }
}
