package com.lecturameter

// Feedback 20-07: el widget Pro pasa a ser 100% mensual. Estos tests fijan lo que
// antes miraba al global — dias activos, hora pico y genero favorito — al mes en curso.

import com.lecturameter.model.Book
import com.lecturameter.model.BookStatus
import com.lecturameter.model.ReadingSession
import com.lecturameter.utils.StatsWidgetDataProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatsWidgetMonthlyTest {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Dia [day] del mes actual (0) o de meses anteriores (-1, -2...). */
    private fun dayIn(monthsAgo: Int, day: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, monthsAgo)
        cal.set(Calendar.DAY_OF_MONTH, day)
        return sdf.format(cal.time)
    }

    private fun book(id: Long, genres: List<String>) = Book(
        id = id, title = "T$id", author = "A", pages = 300,
        startDate = null, endDate = null, status = BookStatus.FINISHED, genres = genres
    )

    private fun session(bookId: Long, date: String, pages: Int, hour: Int? = null): ReadingSession {
        val ts = hour?.let {
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(date)!!
            cal.set(Calendar.HOUR_OF_DAY, it)
            cal.timeInMillis
        }
        return ReadingSession(
            id = System.nanoTime(), bookId = bookId, date = date,
            pages = pages, minutes = 30, startTimestamp = ts
        )
    }

    @Test fun dias_activos_cuentan_solo_los_del_mes() {
        val sessions = listOf(
            session(1L, dayIn(0, 2), 10),
            session(1L, dayIn(0, 2), 10),   // mismo dia: no suma
            session(1L, dayIn(0, 5), 10),
            session(1L, dayIn(-1, 3), 10),
            session(1L, dayIn(-1, 8), 10),
            session(1L, dayIn(-1, 9), 10)
        )
        val stats = StatsWidgetDataProvider.compute(emptyList(), sessions)
        assertEquals(2, stats.thisMonthActiveDays)
        assertEquals(3, stats.lastMonthActiveDays)
    }

    @Test fun genero_favorito_es_el_del_mes_no_el_global() {
        val books = listOf(book(1L, listOf("Fantasia")), book(2L, listOf("Ensayo")))
        val sessions = listOf(
            // Fantasia domina el historico, pero este mes solo se ha leido Ensayo
            session(1L, dayIn(-1, 4), 900),
            session(2L, dayIn(0, 6), 120)
        )
        val stats = StatsWidgetDataProvider.compute(books, sessions)
        assertEquals("Fantasia", stats.favoriteGenre)
        assertEquals("Ensayo", stats.monthFavoriteGenre)
    }

    @Test fun genero_del_mes_pondera_por_paginas() {
        val books = listOf(book(1L, listOf("Poesia")), book(2L, listOf("Terror")))
        val sessions = listOf(
            session(1L, dayIn(0, 1), 10),
            session(1L, dayIn(0, 2), 10),
            session(1L, dayIn(0, 3), 10),   // 3 sesiones, 30 paginas
            session(2L, dayIn(0, 4), 200)   // 1 sesion, 200 paginas
        )
        assertEquals("Terror", StatsWidgetDataProvider.compute(books, sessions).monthFavoriteGenre)
    }

    @Test fun hora_pico_es_la_del_mes_no_la_global() {
        val sessions = listOf(
            // Historico de madrugada (slot 0), este mes todo por la noche (slot 7)
            session(1L, dayIn(-1, 4), 10, hour = 1),
            session(1L, dayIn(-1, 5), 10, hour = 2),
            session(1L, dayIn(-1, 6), 10, hour = 1),
            session(1L, dayIn(0, 7), 10, hour = 22)
        )
        val stats = StatsWidgetDataProvider.compute(emptyList(), sessions)
        assertEquals(0, stats.peakHourSlot)
        assertEquals(7, stats.monthPeakHourSlot)
    }

    @Test fun sin_sesiones_este_mes_no_hay_genero_ni_hora_pico() {
        val books = listOf(book(1L, listOf("Fantasia")))
        val stats = StatsWidgetDataProvider.compute(books, listOf(session(1L, dayIn(-1, 4), 10, hour = 3)))
        assertNull(stats.monthFavoriteGenre)
        assertNull(stats.monthPeakHourSlot)
        assertEquals(0, stats.thisMonthActiveDays)
    }
}
