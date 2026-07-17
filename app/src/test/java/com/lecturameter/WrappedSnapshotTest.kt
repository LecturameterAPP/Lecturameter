package com.lecturameter

// B3 fase B: tests del sanitizado de los snapshots viejos del Wrapped.
//
// Por qué existe esto: Gson instancia con Unsafe y rellena campo a campo, así que un campo
// que NO está en el JSON no recibe el default de Kotlin, recibe el cero de la JVM. En un
// String eso es null aunque el tipo declare `String`, y cualquier `.isNotBlank()` sobre él
// revienta con NPE contra un tipo supuestamente no-nulo. Como el cierre-comparativa lee un
// puñado de campos nuevos de los Wrapped guardados de años anteriores, ese caso es el
// camino normal, no el raro: basta con abrir el Wrapped de un año pasado.
//
// Nota de runner: requieren android.jar por la INTERFAZ SharedPreferences (FakePrefs).

import com.lecturameter.repository.WrappedRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WrappedSnapshotTest {

    /** El JSON mínimo de un Wrapped guardado por una versión anterior: sin ninguno de los
     *  campos del cierre-comparativa, que es exactamente lo que hay en los móviles. */
    private val legacyJson = """
        [{"year":2025,"totalBooks":4,"totalPages":1835,"avgPagesPerDay":12.0,
          "avgDaysPerBook":30.0,"favoriteAuthorBooks":2,"favoriteGenreBooks":2,
          "fastestBookPpd":0.0,"fastestBookPages":0,"bestRatedScore":8,
          "longestStreakDays":3,"savedAt":1750000000000}]
    """.trimIndent()

    private fun load(json: String): List<com.lecturameter.model.YearWrapped> {
        val prefs = FakePrefs()
        prefs.map["wrapped_history"] = json
        return WrappedRepository.loadOrNull(prefs)!!
    }

    /**
     * La premisa del sanitizado, clavada aquí para que no sea un acto de fe: Gson SÍ deja
     * nulls en campos declarados no-nulos. Se lee por reflexión a propósito, porque el
     * getter de Kotlin lleva su propio null-check y lanzaría antes de dejarnos mirar.
     *
     * Si algún día se cambia Gson por un parser que respete los defaults de Kotlin, este
     * test se cae: entonces (y solo entonces) sanitizeWrapped sobra.
     */
    @Test fun `Gson deja null en los String que faltan pese al tipo no nulo`() {
        val raw = com.google.gson.Gson()
            .fromJson(legacyJson, Array<com.lecturameter.model.YearWrapped>::class.java).single()
        fun field(name: String): Any? =
            com.lecturameter.model.YearWrapped::class.java.getDeclaredField(name)
                .apply { isAccessible = true }.get(raw)
        assertEquals(null, field("favoriteAuthor"))
        assertEquals(null, field("previousYearGenre"))
        assertEquals(null, field("previousYearAuthor"))
        assertEquals(null, field("pagesPerMonth"))
        // Y en los Int deja el cero de la JVM, no el default de Kotlin. De ahí que
        // previousYearBestMonth sea 1-basado.
        assertEquals(0, field("previousYearBestMonth"))
    }

    @Test fun `un snapshot viejo se carga sin campos nulos`() {
        val w = load(legacyJson).single()
        assertEquals(2025, w.year)
        assertEquals(4, w.totalBooks)
    }

    @Test fun `los String que faltan en el JSON no son null tras cargar`() {
        val w = load(legacyJson).single()
        // Estas llamadas son el crash: sobre un String null, isNotBlank() lanza NPE.
        // Que el test llegue al final ya es media prueba; los assert son la otra media.
        assertTrue(w.favoriteAuthor.isEmpty())
        assertTrue(w.favoriteGenre.isEmpty())
        assertTrue(w.previousYearGenre.isEmpty())
        assertTrue(w.previousYearAuthor.isEmpty())
        assertTrue(w.previousYearMostReadDay.isEmpty())
        assertTrue(w.mostReadDay.isEmpty())
        assertTrue(w.bestRatedTitle.isEmpty())
        assertTrue(w.fastestBookTitle.isEmpty())
        assertTrue(w.bestWeekStart.isEmpty())
        assertTrue(w.previousYearBestWeekStart.isEmpty())
    }

    @Test fun `las listas que faltan en el JSON no son null tras cargar`() {
        val w = load(legacyJson).single()
        assertTrue(w.topAuthorsTop3.isEmpty())
        assertTrue(w.bestDayPerMonth.isEmpty())
        assertTrue(w.droppedBookTitles.isEmpty())
        assertTrue(w.genreCountsTop6.isEmpty())
        // Las dos series de 12/8 huecos se rellenan con su forma, no con una lista vacía:
        // la gráfica de barras indexa pagesPerMonth[0..11] a pelo.
        assertEquals(12, w.pagesPerMonth.size)
        assertEquals(12, w.booksPerMonth.size)
        assertEquals(8, w.pagesPerTimeSlot.size)
    }

    @Test fun `un snapshot viejo no inventa el mejor mes del año anterior`() {
        // El campo es 1-basado justo por esto: si fuese 0-basado, el cero que deja Gson
        // se leería como "enero" y el cierre afirmaría un dato que nadie calculó nunca.
        val w = load(legacyJson).single()
        assertEquals(0, w.previousYearBestMonth)
    }

    @Test fun `un snapshot nuevo conserva sus valores al ir y volver`() {
        // El sanitizado no debe pisar lo que sí viene en el JSON.
        val prefs = FakePrefs()
        val original = com.lecturameter.model.YearWrapped(
            year = 2026, totalBooks = 62, totalPages = 17440, avgPagesPerDay = 47.0,
            avgDaysPerBook = 5.0, favoriteAuthor = "Tatsuki Fujimoto", favoriteAuthorBooks = 11,
            favoriteGenre = "Manga", favoriteGenreBooks = 20, fastestBookTitle = "Chainsaw Man",
            fastestBookPpd = 60.0, fastestBookPages = 200, bestRatedTitle = "Chainsaw Man",
            bestRatedScore = 10, longestStreakDays = 9, longestStreakStart = "2026-06-01",
            longestStreakEnd = "2026-06-09",
            previousYearAuthor = "B. Sanderson", previousYearMostReadDay = "2025-03-03",
            previousYearBestMonth = 3, bestWeekStart = "2026-06-01", bestWeekNumber = 23,
            bestWeekPages = 900, previousYearBestWeekStart = "2025-03-03",
            previousYearBestWeekNumber = 10, previousYearBestWeekPages = 300,
            pagesPerMonth = List(12) { it * 10 }
        )
        WrappedRepository.save(prefs, listOf(original))
        val back = WrappedRepository.loadOrNull(prefs)!!.single()
        assertNotNull(back)
        assertEquals("Tatsuki Fujimoto", back.favoriteAuthor)
        assertEquals("B. Sanderson", back.previousYearAuthor)
        assertEquals("2025-03-03", back.previousYearMostReadDay)
        assertEquals(3, back.previousYearBestMonth)
        assertEquals(23, back.bestWeekNumber)
        assertEquals(10, back.previousYearBestWeekNumber)
        assertEquals(List(12) { it * 10 }, back.pagesPerMonth)
    }

    @Test fun `un JSON corrupto no tumba la app`() {
        val prefs = FakePrefs()
        prefs.map["wrapped_history"] = "{esto no es json}"
        assertTrue(WrappedRepository.loadOrNull(prefs)!!.isEmpty())
    }
}
