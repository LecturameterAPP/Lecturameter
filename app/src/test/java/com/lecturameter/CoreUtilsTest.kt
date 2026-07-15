package com.lecturameter

// F8: tests de utilidades puras (CoreUtils). Ejecutar con run_tests.ps1
// (el test worker de Gradle falla en esta máquina — gradle/gradle#12660).

import com.google.gson.Gson
import com.lecturameter.model.Book
import com.lecturameter.model.BookStatus
import com.lecturameter.utils.canonicalIsbn
import com.lecturameter.utils.cleanIsbn
import com.lecturameter.utils.daysBetween
import com.lecturameter.utils.isbn10To13
import com.lecturameter.utils.isbn13To10
import com.lecturameter.utils.parseCsvLine
import com.lecturameter.utils.parseFlexibleDate
import com.lecturameter.utils.sanitizeBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUtilsTest {

    // ── parseFlexibleDate ─────────────────────────────────────────────────────

    @Test fun parseFlexibleDate_acepta_1_o_2_digitos() {
        assertEquals("2026-07-05", parseFlexibleDate("5-7-2026"))
        assertEquals("2026-07-15", parseFlexibleDate("15-07-2026"))
    }

    @Test fun parseFlexibleDate_rechaza_fechas_imposibles() {
        assertNull(parseFlexibleDate("39-01-2026"))
        assertNull(parseFlexibleDate("29-02-2025"))   // 2025 no es bisiesto
        assertNull(parseFlexibleDate("01-13-2026"))
        assertNull(parseFlexibleDate("garbage"))
        assertNull(parseFlexibleDate("1-2"))
    }

    @Test fun parseFlexibleDate_acepta_bisiesto() {
        assertEquals("2024-02-29", parseFlexibleDate("29-02-2024"))
    }

    // ── parseCsvLine ──────────────────────────────────────────────────────────

    @Test fun parseCsvLine_respeta_comillas() {
        assertEquals(listOf("a", "b,c", "d"), parseCsvLine("a,\"b,c\",d"))
    }

    @Test fun parseCsvLine_campos_vacios() {
        assertEquals(listOf("", "x", ""), parseCsvLine(",x,"))
    }

    // ── daysBetween ───────────────────────────────────────────────────────────

    @Test fun daysBetween_mismo_dia_es_cero() {
        assertEquals(0, daysBetween("2026-07-15", "2026-07-15"))
    }

    @Test fun daysBetween_normal_y_negativo() {
        assertEquals(9, daysBetween("2026-07-01", "2026-07-10"))
        assertEquals(0, daysBetween("2026-07-10", "2026-07-01"))  // invertido → 0
    }

    // ── ISBN ──────────────────────────────────────────────────────────────────

    @Test fun isbn_conversion_ida_y_vuelta() {
        // El Ojo del Mundo (Minotauro): 9788445007020 ↔ 8445007025
        assertEquals("8445007025", isbn13To10("9788445007020"))
        assertEquals("9788445007020", isbn10To13("8445007025"))
    }

    @Test fun isbn10_con_check_X() {
        // ISBN-13 cuyo ISBN-10 termina en X: 9780306406157 → 030640615?
        val ten = isbn13To10("9780306406157")
        assertEquals("0306406152", ten)
    }

    @Test fun canonicalIsbn_siempre_13() {
        assertEquals("9788445007020", canonicalIsbn("84-45007-02-5"))
        assertEquals("9788445007020", canonicalIsbn("9788445007020"))
        assertNull(canonicalIsbn("123"))
        assertNull(canonicalIsbn(null))
    }

    @Test fun cleanIsbn_filtra_caracteres() {
        assertEquals("9788445007020", cleanIsbn(" 978-84-450-0702-0 "))
        assertNull(cleanIsbn("abc"))
    }

    // ── sanitizeBook ──────────────────────────────────────────────────────────

    private val gson = Gson()

    /** Simula el camino real: JSON de una versión vieja sin campos nuevos → Gson deja nulls. */
    private fun bookFromLegacyJson(json: String): Book = gson.fromJson(json, Book::class.java)

    @Test fun sanitizeBook_repara_nulls_de_gson() {
        val legacy = bookFromLegacyJson(
            """{"id":1,"pages":100,"status":"READING","startDate":"2026-01-01","endDate":null}"""
        )
        val b = sanitizeBook(legacy)
        assertEquals("", b.title)
        assertEquals("", b.author)
        assertTrue(b.genres.isEmpty())
        assertTrue(b.editions.isEmpty())
        assertTrue(b.dateEvents.isEmpty())
    }

    @Test fun sanitizeBook_migra_genre_legacy_y_horror() {
        val legacy = bookFromLegacyJson(
            """{"id":1,"title":"X","author":"Y","pages":100,"status":"READING","startDate":"2026-01-01","genre":"Horror"}"""
        )
        val b = sanitizeBook(legacy)
        assertEquals(listOf("Terror"), b.genres)
        assertEquals("", b.genre)
    }

    @Test fun sanitizeBook_finished_sin_endDate_usa_startDate() {
        val legacy = bookFromLegacyJson(
            """{"id":1,"title":"X","author":"Y","pages":100,"status":"FINISHED","startDate":"2026-01-01"}"""
        )
        assertEquals("2026-01-01", sanitizeBook(legacy).endDate)
    }

    @Test fun sanitizeBook_reconvierte_fechas_ddMMyyyy() {
        val legacy = bookFromLegacyJson(
            """{"id":1,"title":"X","author":"Y","pages":100,"status":"READING","startDate":"15-07-2026"}"""
        )
        assertEquals("2026-07-15", sanitizeBook(legacy).startDate)
    }

    @Test fun sanitizeBook_paginas_funcionales_invalidas_a_null() {
        val legacy = bookFromLegacyJson(
            """{"id":1,"title":"X","author":"Y","pages":100,"status":"READING","startDate":"2026-01-01",
                "firstFunctionalPage":10,"lastFunctionalPage":5}"""
        )
        val b = sanitizeBook(legacy)
        assertEquals(10, b.firstFunctionalPage)
        assertNull(b.lastFunctionalPage)   // last < first → descartada
    }
}
