package com.lecturameter

// B3 fase B: tests de la semana natural lunes-domingo, el dato nuevo de la 11ª
// pantalla del Wrapped ("la semana que más leíste").
//
// Se testea a fondo el calendario porque el cálculo es aritmética de días desde la
// época escrita a mano (no Calendar, que arrastra el firstDayOfWeek del locale) y
// porque un error de un día aquí no se ve: solo desplaza el número de semana y nadie
// lo nota hasta que alguien cuenta los lunes a mano.

import com.lecturameter.utils.bestNaturalWeek
import com.lecturameter.utils.isoWeekNumber
import com.lecturameter.utils.weekStartOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NaturalWeekTest {

    // ── weekStartOf: el lunes de la semana ────────────────────────────────────

    @Test fun `el lunes es su propio inicio de semana`() {
        assertEquals("2026-06-01", weekStartOf("2026-06-01"))
    }

    @Test fun `el domingo cierra la semana del lunes anterior`() {
        // Si la semana fuera de domingo a sábado (criterio EE.UU.), este domingo
        // abriría semana y devolvería 2026-06-07. Es justo el fallo que se evita.
        assertEquals("2026-06-01", weekStartOf("2026-06-07"))
    }

    @Test fun `un dia cualquiera cae en el lunes de su semana`() {
        assertEquals("2026-06-01", weekStartOf("2026-06-06"))
    }

    @Test fun `la semana puede empezar en el año anterior`() {
        // 2026-01-01 fue jueves: su semana arranca el lunes 29 de diciembre de 2025.
        assertEquals("2025-12-29", weekStartOf("2026-01-01"))
    }

    @Test fun `epoca y años bisiestos`() {
        // 1970-01-01 fue jueves — el origen del cálculo, donde un signo mal puesto duele.
        assertEquals("1969-12-29", weekStartOf("1970-01-01"))
        // 29 de febrero de 2024 (bisiesto) fue jueves.
        assertEquals("2024-02-26", weekStartOf("2024-02-29"))
        // 2000 fue bisiesto (divisible por 400) y 1900 no lo fue: el caso que rompe
        // las implementaciones ingenuas de "año bisiesto".
        assertEquals("2000-02-28", weekStartOf("2000-02-29"))
    }

    @Test fun `fecha ilegible no revienta`() {
        assertNull(weekStartOf("no-soy-una-fecha"))
        assertNull(weekStartOf("2026-13-01"))
        assertNull(weekStartOf(""))
    }

    // ── isoWeekNumber ─────────────────────────────────────────────────────────

    @Test fun `numero de semana ISO`() {
        // 2026-01-01 es jueves → su semana SÍ es la 1 de 2026 (contiene el primer jueves).
        assertEquals(1, isoWeekNumber("2026-01-01"))
        // El lunes de esa misma semana cae en 2025 pero sigue siendo la semana 1 de 2026.
        assertEquals(1, isoWeekNumber("2025-12-29"))
        // Mismo criterio que la maqueta del B3: el 6 de junio de 2026 es la semana 23.
        assertEquals(23, isoWeekNumber("2026-06-06"))
        assertEquals(23, isoWeekNumber("2026-06-01"))
        assertEquals(24, isoWeekNumber("2026-06-08"))
    }

    @Test fun `un año puede tener 53 semanas`() {
        // 2026 empieza en jueves y no es bisiesto → tiene 53 semanas ISO.
        assertEquals(53, isoWeekNumber("2026-12-31"))
    }

    @Test fun `los primeros dias de enero pueden ser la ultima semana del año anterior`() {
        // 2027-01-01 es viernes: su jueves es 2026-12-31, así que esa semana pertenece
        // a 2026 y es la 53, no la 1 de 2027.
        assertEquals(53, isoWeekNumber("2027-01-01"))
    }

    // ── bestNaturalWeek ───────────────────────────────────────────────────────

    @Test fun `sin sesiones no hay mejor semana`() {
        assertNull(bestNaturalWeek(emptyMap()))
    }

    @Test fun `una semana con paginas a cero no cuenta`() {
        assertNull(bestNaturalWeek(mapOf("2026-06-01" to 0)))
    }

    @Test fun `gana la semana con mas paginas sumadas, no el mejor dia suelto`() {
        val best = bestNaturalWeek(mapOf(
            // Semana del 1 de junio: cuatro días flojos que suman 160.
            "2026-06-01" to 40, "2026-06-03" to 40, "2026-06-05" to 40, "2026-06-07" to 40,
            // Semana del 8 de junio: un solo día enorme, 120. Pierde: 160 > 120.
            "2026-06-10" to 120
        ))!!
        assertEquals("2026-06-01", best.startDate)
        assertEquals(23, best.weekNumber)
        assertEquals(160, best.pages)
    }

    @Test fun `el domingo suma a la semana del lunes anterior`() {
        // Comprobación del límite: si el domingo se contase como inicio de semana,
        // saldrían dos semanas de 50 en vez de una de 100.
        val best = bestNaturalWeek(mapOf(
            "2026-06-01" to 50,  // lunes
            "2026-06-07" to 50,  // domingo de la MISMA semana
            "2026-06-08" to 90   // lunes siguiente
        ))!!
        assertEquals("2026-06-01", best.startDate)
        assertEquals(100, best.pages)
    }

    @Test fun `en un empate gana la semana mas antigua`() {
        val best = bestNaturalWeek(mapOf("2026-06-01" to 70, "2026-06-08" to 70))!!
        assertEquals("2026-06-01", best.startDate)
    }

    @Test fun `una semana a caballo de enero solo suma los dias que se le pasan`() {
        // computeWrapped pasa las sesiones de UN año, así que la semana del 29-dic-2025
        // solo suma aquí sus días de 2026. Decisión consciente: el dato de un año no se
        // come páginas de otro, y el criterio es el mismo a los dos lados del "vs".
        val best = bestNaturalWeek(mapOf("2026-01-01" to 30, "2026-01-04" to 25))!!
        assertEquals("2025-12-29", best.startDate)
        assertEquals(1, best.weekNumber)
        assertEquals(55, best.pages)
    }

    @Test fun `una fecha corrupta se ignora sin tumbar el resto`() {
        val best = bestNaturalWeek(mapOf("basura" to 999, "2026-06-01" to 10))!!
        assertEquals("2026-06-01", best.startDate)
        assertEquals(10, best.pages)
    }
}
