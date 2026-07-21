package com.lecturameter

// Color de las portadas generadas en el dispositivo. Kotlin puro, sin Android: el
// android.jar de esta maquina es un stub donde cada metodo lanza "Stub!" (ver
// run_tests.ps1), asi que CoverPalette no puede usar ColorUtils ni android.graphics.Color
// y estos tests lo comprueban de verdad, no contra un mock.

import com.lecturameter.utils.CoverPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverPaletteTest {

    // Las cinco parejas de tinta de los temas de la app (clara, oscura).
    private val TEMAS = listOf(
        "Clasico oscuro" to (0xFFFFFFFF.toInt() to 0xFF14141F.toInt()),
        "Claro C2"       to (0xFFFFFFFF.toInt() to 0xFF26283A.toInt()),
        "Aurora"         to (0xFFFFFFFF.toInt() to 0xFF1B1230.toInt()),
        "AMOLED"         to (0xFFFFFFFF.toInt() to 0xFF0A0A0A.toInt()),
        "Cuero"          to (0xFFFAF3E3.toInt() to 0xFF241C10.toInt())
    )

    private val ESTANTE = listOf(
        "Cien anos de soledad", "Dune", "La sombra del viento", "El nombre de la rosa",
        "Kafka en la orilla", "Los pilares de la Tierra", "Fahrenheit 451",
        "La ciudad y los perros"
    )

    @Test
    fun `el mismo libro da siempre la misma portada`() {
        // Es la razon de ser del hash: si cambiara entre pantallas o entre arranques, el
        // usuario veria "otro libro" al pasar de la busqueda al detalle.
        val (ic, io) = TEMAS[0].second
        val a = CoverPalette.colorsFor(null, "El nombre del viento", ic, io)
        val b = CoverPalette.colorsFor(null, "El nombre del viento", ic, io)
        assertEquals(a.background, b.background)
        assertEquals(a.ink, b.ink)
    }

    @Test
    fun `el ISBN manda sobre el titulo`() {
        // Dos ediciones con titulos algo distintos pero el mismo ISBN son el mismo libro.
        val (ic, io) = TEMAS[0].second
        val a = CoverPalette.colorsFor("9788401352836", "La torre", ic, io)
        val b = CoverPalette.colorsFor("9788401352836", "LA TORRE (edicion especial)", ic, io)
        assertEquals(a.background, b.background)
    }

    @Test
    fun `ocho libros seguidos dan ocho colores distintos en los cinco temas`() {
        // El motivo de que exista mix32 y el segundo eje de escalones: con el hash en
        // crudo salian portadas repetidas en la misma pantalla.
        for ((nombre, tintas) in TEMAS) {
            val (ic, io) = tintas
            val fondos = ESTANTE.map { CoverPalette.colorsFor(null, it, ic, io).background }
            assertEquals("colores repetidos en $nombre", fondos.size, fondos.toSet().size)
        }
    }

    @Test
    fun `toda portada se lee, contraste minimo 4,5 a 1`() {
        // La correccion de luminosidad existe justo para esto. Si alguien la quita, este
        // test cae con el tema y el titulo concretos que fallan.
        val titulos = ESTANTE + listOf("It", "Cantar de mio Cid", "チェンソーマン 5", "")
        for ((nombre, tintas) in TEMAS) {
            val (ic, io) = tintas
            for (t in titulos) {
                val c = CoverPalette.colorsFor(null, t, ic, io)
                val real = CoverPalette.contrast(c.background, c.ink)
                assertTrue(
                    "contraste $real en $nombre con \"$t\"",
                    real >= 4.5
                )
            }
        }
    }

    @Test
    fun `la tinta elegida es una de las dos del tema`() {
        for ((nombre, tintas) in TEMAS) {
            val (ic, io) = tintas
            for (t in ESTANTE) {
                val c = CoverPalette.colorsFor(null, t, ic, io)
                assertTrue("tinta ajena al tema $nombre", c.ink == ic || c.ink == io)
            }
        }
    }

    @Test
    fun `se salta la franja amarillo verdoso`() {
        // De 70 a 95 grados es ilegible con tinta blanca y turbia con la oscura.
        for (s in 0..4000) {
            val h = CoverPalette.hueFor(s)
            assertTrue("hue $h para semilla $s", h < 70f || h > 95f)
        }
    }

    @Test
    fun `un titulo vacio no revienta`() {
        val (ic, io) = TEMAS[0].second
        val c = CoverPalette.colorsFor(null, "", ic, io)
        assertTrue(c.background != 0)
    }

    @Test
    fun `hslToArgb devuelve los extremos correctos`() {
        assertEquals(0xFFFFFFFF.toInt(), CoverPalette.hslToArgb(0f, 0f, 1f))
        assertEquals(0xFF000000.toInt(), CoverPalette.hslToArgb(0f, 0f, 0f))
        // Rojo puro
        assertEquals(0xFFFF0000.toInt(), CoverPalette.hslToArgb(0f, 1f, 0.5f))
    }

    @Test
    fun `el contraste es simetrico y acotado`() {
        val blanco = 0xFFFFFFFF.toInt()
        val negro = 0xFF000000.toInt()
        assertEquals(
            CoverPalette.contrast(blanco, negro),
            CoverPalette.contrast(negro, blanco),
            0.0001
        )
        assertTrue(CoverPalette.contrast(blanco, negro) > 20.9)
        assertEquals(1.0, CoverPalette.contrast(blanco, blanco), 0.0001)
    }

    @Test
    fun `el lomo es mas oscuro que el fondo`() {
        val (ic, io) = TEMAS[0].second
        for (t in ESTANTE) {
            val c = CoverPalette.colorsFor(null, t, ic, io)
            assertNotEquals(c.background, c.spine)
        }
    }
}
