package com.lecturameter

import com.lecturameter.utils.ContrastUtils
import com.lecturameter.utils.WrappedPalette
import com.lecturameter.utils.WrappedPalette.Slot
import com.lecturameter.utils.WrappedPalette.ThemeKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las losas del Wrapped, medidas.
 *
 * Este test existe porque el mismo bug va por su quinta vuelta y las cuatro anteriores se
 * colaron por mirar los colores en vez de medirlos. Aquí no se mira nada: se recorren las 15
 * losas por los 5 temas (75 combinaciones) y se comprueba que el texto que va encima se lee.
 *
 * Y al revés: comprueba que el Oscuro NO se ha movido. Esa es la mitad importante. El arreglo de
 * B-037 rompió el Oscuro para arreglar el papel; si alguien vuelve a "unificar" la tabla, esto se
 * pone rojo antes de que llegue al móvil de nadie.
 */
class WrappedPaletteTest {

    private fun hex(v: Int) = "#%06X".format(v)

    // ── La mitad que protege al Oscuro ───────────────────────────────────────

    @Test
    fun `el Oscuro devuelve los literales de de3180f, sin derivar nada`() {
        // Copiados a mano del fichero de antes de B-037. Que estén dos veces es el punto: si
        // alguien toca la tabla, tiene que tocar tambien este test, y ahi se para a pensar.
        val esperado = mapOf(
            Slot.YEAR to intArrayOf(0x312E81, 0x1E1B4B),
            Slot.HOURS to intArrayOf(0x4C1D95, 0x150B33),
            Slot.HOURS_ALT to intArrayOf(0x0C4A6E, 0x0F172A),
            Slot.CHART to intArrayOf(0x0F2027, 0x203A43),
            Slot.BESTDAY to intArrayOf(0x312E81, 0x0F172A),
            Slot.TIMESLOT_HERO to intArrayOf(0x1E1B4B, 0x0C4A6E),
            Slot.CLOSE to intArrayOf(0x312E81, 0x4C1D95, 0x0F0D2E),
            Slot.RATED to intArrayOf(0x92400E, 0x1A1200),
            Slot.FASTEST to intArrayOf(0x064E3B, 0x0A2818),
            Slot.FAVORITES to intArrayOf(0x3B0D0D, 0x1A0808)
        )
        esperado.forEach { (slot, stops) ->
            assertArrayEquals(
                "La losa $slot del Oscuro no es la de de3180f. En Oscuro no habia nada que\n" +
                    "arreglar: el bug de B-037 era el papel. No la derives.",
                stops, WrappedPalette.slab(slot, ThemeKey.DARK)
            )
        }
    }

    @Test
    fun `los seis numeros protagonistas del Oscuro conservan su degradado`() {
        val esperado = mapOf(
            Slot.YEAR to intArrayOf(0x818CF8, 0x22D3EE),
            Slot.HOURS to intArrayOf(0xA78BFA, 0xDDD6FE),
            Slot.TIMESLOT_HERO to intArrayOf(0x818CF8, 0x22D3EE),
            Slot.CLOSE to intArrayOf(0xFFFFFF, 0xA78BFA),
            Slot.RATED to intArrayOf(0xFFBB33, 0xFDE68A),
            Slot.FASTEST to intArrayOf(0x10B981, 0x22D3EE)
        )
        assertEquals("son SEIS numeros heroe, ni mas ni menos", 6, esperado.size)
        esperado.forEach { (slot, stops) ->
            assertArrayEquals(
                "El numero heroe de $slot en Oscuro perdio su degradado. B-037 lo dejo en blanco\n" +
                    "plano; esto lo devuelve y no se vuelve a ir.",
                stops, WrappedPalette.hero(slot, ThemeKey.DARK)
            )
        }
    }

    @Test
    fun `las etiquetas y titulos del Oscuro conservan su tinte`() {
        // B-037 los paso a blanco "por contraste". Pero eran OPACOS y pasaban AA de sobra (de
        // 5,93:1 a 8,39:1, ver el test de abajo): se perdio el tinte de cada losa a cambio de
        // nada. Lo que si era el bug de verdad, el Gold.copy(0.7f) de las semanticas, sigue fuera
        // de la tabla y sigue derivandose y midiendose.
        val muted = mapOf(
            Slot.YEAR to 0xC7D2FE, Slot.HOURS to 0xC4B5FD,
            Slot.TIMESLOT_HERO to 0x7DD3FC, Slot.CLOSE to 0xE9D5FF
        )
        muted.forEach { (slot, c) ->
            assertEquals("la etiqueta de $slot en Oscuro", c, WrappedPalette.muted(slot, ThemeKey.DARK))
        }
        val titulos = mapOf(
            Slot.TOPS to 0xC4B5FD, Slot.CHART to 0x94A3B8,
            Slot.BESTDAY to 0xA5B4FC, Slot.TIMESLOT to 0xA5B4FC, Slot.FAVORITES to 0xCC4A4A
        )
        titulos.forEach { (slot, c) ->
            assertEquals("el titulo de $slot en Oscuro", c, WrappedPalette.title(slot, ThemeKey.DARK))
        }
    }

    @Test
    fun `los titulos pasan AA sobre su propia losa en los cinco temas`() {
        // El minimo depende del TAMANO al que se pinta cada titulo, no del gusto de nadie. WCAG
        // llama grande a >=18pt (o >=14pt en negrita), y en Android eso son ~24sp (o ~19sp bold).
        //
        // Solo el titulo de Favoritos entra: va a 24sp Black. Y por eso puede seguir siendo el
        // FavoriteRed historico, que da 3,72:1 sobre su losa: pasa el 3:1 que le toca, aunque no
        // llegue al 4,5:1 que le tocaria si fuese texto normal. Escribi este test pidiendo 4,5:1
        // a todo y se puso rojo justo ahi: el color no estaba mal, lo estaba el listón.
        //
        // Los demas (16sp Bold, 18sp Black, 32sp Black) NO llegan a grandes segun WCAG, asi que
        // van al 4,5:1 completo. El de Tops es de 32sp y podria relajarse, pero pasa de sobra y no
        // hace falta gastarse una excepcion.
        val grandes = setOf(Slot.FAVORITES)
        val fallos = mutableListOf<String>()
        ThemeKey.values().forEach { key ->
            Slot.values().forEach { slot ->
                val claro = WrappedPalette.lightestOf(WrappedPalette.slab(slot, key))
                val t = WrappedPalette.title(slot, key)
                val minimo = if (slot in grandes) ContrastUtils.AA_LARGE else ContrastUtils.AA_TEXT
                val r = ContrastUtils.contrast(t, claro)
                if (r < minimo)
                    fallos += "  $key/$slot ${hex(t)} sobre ${hex(claro)} -> %.2f:1 (min $minimo)".format(r)
            }
        }
        assertTrue("Titulo ilegible sobre su losa:\n" + fallos.joinToString("\n"), fallos.isEmpty())
    }

    @Test
    fun `los titulos de las losas no son blancos, que es lo que aplano la pantalla`() {
        // Blanco sobre losa es la salida facil y es exactamente lo que hizo B-037. El titulo lleva
        // el tinte de su losa: en Oscuro el literal, en el resto el derivado y medido.
        ThemeKey.values().forEach { key ->
            WrappedPalette.DARK_TITLE.keys.forEach { slot ->
                assertTrue(
                    "El titulo de $slot en $key ha vuelto a ser blanco puro.",
                    WrappedPalette.title(slot, key) != 0xFFFFFF
                )
            }
        }
    }

    @Test
    fun `el cierre es la unica losa de tres stops, en los cinco temas`() {
        ThemeKey.values().forEach { key ->
            assertEquals("el cierre lleva 3 stops en $key", 3, WrappedPalette.slab(Slot.CLOSE, key).size)
            Slot.values().filter { it != Slot.CLOSE }.forEach { slot ->
                assertEquals("$slot en $key lleva 2 stops", 2, WrappedPalette.slab(slot, key).size)
            }
        }
    }

    // ── La mitad que protege el contraste ────────────────────────────────────

    @Test
    fun `el texto blanco pasa AA sobre las 15 losas de los 5 temas`() {
        val fallos = mutableListOf<String>()
        ThemeKey.values().forEach { key ->
            Slot.values().forEach { slot ->
                WrappedPalette.slab(slot, key).forEach { stop ->
                    val r = ContrastUtils.contrast(0xFFFFFF, stop)
                    if (r < ContrastUtils.AA_TEXT)
                        fallos += "  $key/$slot stop ${hex(stop)} -> %.2f:1".format(r)
                }
            }
        }
        assertTrue(
            "El blanco no se lee sobre estas losas (minimo 4,5:1). onSlab() devuelve blanco en\n" +
                "los cinco temas y da por hecho que la losa lo aguanta:\n" + fallos.joinToString("\n"),
            fallos.isEmpty()
        )
    }

    @Test
    fun `los numeros protagonistas pasan AA_LARGE sobre su propia losa`() {
        // Son de 26 a 80sp, asi que el minimo que aplica es 3:1, no 4,5:1. Se mide contra el stop
        // MAS CLARO de la losa, que es el peor caso para un numero claro.
        val fallos = mutableListOf<String>()
        ThemeKey.values().forEach { key ->
            Slot.values().forEach { slot ->
                val claro = WrappedPalette.lightestOf(WrappedPalette.slab(slot, key))
                WrappedPalette.hero(slot, key).forEach { extremo ->
                    val r = ContrastUtils.contrast(extremo, claro)
                    if (r < ContrastUtils.AA_LARGE)
                        fallos += "  $key/$slot ${hex(extremo)} sobre ${hex(claro)} -> %.2f:1".format(r)
                }
            }
        }
        assertTrue(
            "Un extremo del degradado del numero no llega a 3:1 sobre su losa. Los DOS extremos\n" +
                "tienen que pasar: si solo pasa uno, media palabra se pierde:\n" + fallos.joinToString("\n"),
            fallos.isEmpty()
        )
    }

    @Test
    fun `las etiquetas secundarias pasan AA sobre su propia losa`() {
        val fallos = mutableListOf<String>()
        ThemeKey.values().forEach { key ->
            Slot.values().forEach { slot ->
                val claro = WrappedPalette.lightestOf(WrappedPalette.slab(slot, key))
                val m = WrappedPalette.muted(slot, key)
                val r = ContrastUtils.contrast(m, claro)
                if (r < ContrastUtils.AA_TEXT)
                    fallos += "  $key/$slot ${hex(m)} sobre ${hex(claro)} -> %.2f:1".format(r)
            }
        }
        assertTrue(
            "Etiqueta secundaria ilegible sobre su losa (minimo 4,5:1). Es texto normal, no\n" +
                "grande, asi que aqui no vale el 3:1:\n" + fallos.joinToString("\n"),
            fallos.isEmpty()
        )
    }

    @Test
    fun `ninguna losa derivada se pierde contra el fondo mas que la que Victor aprobo`() {
        // Una losa que no se despega del fondo deja de ser una losa, y la losa es el golpe de
        // efecto del Wrapped. Pero OJO con el numero que se le exige, porque escribi este test
        // pidiendo 3:1 a todo y se puso rojo con el propio Oscuro historico: sus losas van de
        // 1,12:1 a 1,89:1 contra el navy del fondo. No estaban rotas. Ese diseno NUNCA separo la
        // losa por luminancia: la separa por MATIZ (indigo sobre navy, violeta sobre navy) y por
        // la esquina redondeada. El ratio WCAG mide luminancia y no sabe ver eso, asi que
        // aplicarselo a una losa decorativa es pedirle al numero algo que el numero no dice.
        //
        // Lo que si se puede exigir, y es lo que el modelo promete, es no EMPEORAR: ninguna losa
        // derivada puede quedar mas perdida contra su fondo que la losa que ya estaba aprobada en
        // el mockup de B-037 (slabOf del acento). Y si el tema da para 3:1, se pide 3:1.
        //
        // El Oscuro queda fuera por definicion: no deriva, restaura. Las semanticas tambien: el
        // oro y el rojo no se mueven por tema, que es justo su gracia.
        val fallos = mutableListOf<String>()
        ThemeKey.values().filter { it != ThemeKey.DARK }.forEach { key ->
            val aprobada = ContrastUtils.slabStops(key.anchorA).first
            val suelo = minOf(ContrastUtils.AA_LARGE.toDouble(), ContrastUtils.contrast(aprobada, key.bg))
            Slot.values().filter { it.tint == null }.forEach { slot ->
                val claro = WrappedPalette.lightestOf(WrappedPalette.slab(slot, key))
                val r = ContrastUtils.contrast(claro, key.bg)
                if (r < suelo - 0.01)
                    fallos += "  $key/$slot ${hex(claro)} vs fondo ${hex(key.bg)} -> %.2f:1 (suelo %.2f)".format(r, suelo)
            }
        }
        assertTrue(
            "Una losa derivada se pierde contra el fondo mas que la losa aprobada de su tema:\n" +
                fallos.joinToString("\n"),
            fallos.isEmpty()
        )
    }

    // ── La mitad que protege la VARIEDAD, que es el encargo ──────────────────

    @Test
    fun `las losas tematicas no son todas la misma, en ningun tema`() {
        // Este es el test que hubiera cazado B-037 a la primera. Despues del arreglo, las siete
        // losas tematicas eran literalmente el mismo color en los cinco temas.
        val tematicas = Slot.values().filter { it.tint == null }
        ThemeKey.values().forEach { key ->
            val tops = tematicas.map { WrappedPalette.lightestOf(WrappedPalette.slab(it, key)) }
            assertTrue(
                "En $key las losas tematicas se han quedado en ${tops.toSet().size} colores\n" +
                    "distintos de ${tematicas.size}. Cada slide tiene que tener identidad propia:\n" +
                    "  " + tops.map { hex(it) },
                tops.toSet().size >= 6
            )
        }
    }

    @Test
    fun `las losas semanticas son iguales en los cuatro temas derivados`() {
        // El oro significa "mejor puntuado" y el verde "mas rapido". Ese significado no depende
        // del tema, asi que la losa tampoco. En Oscuro llevan su literal historico, que si es
        // distinto, y por eso el Oscuro se excluye aqui.
        val derivados = ThemeKey.values().filter { it != ThemeKey.DARK }
        Slot.values().filter { it.tint != null }.forEach { slot ->
            val todas = derivados.map { WrappedPalette.slab(slot, it).toList() }.toSet()
            assertEquals("$slot deberia ser la misma losa en los cuatro temas derivados", 1, todas.size)
        }
    }

    @Test
    fun `Aurora recorre de verdad sus dos matices`() {
        // Aurora es el unico tema con dos colores propios (los dos extremos de su fondo). Si el
        // recorrido no llega de uno a otro, es que el eje de matiz se ha perdido.
        val morada = WrappedPalette.lightestOf(WrappedPalette.slab(Slot.YEAR, ThemeKey.AURORA))
        val teal = WrappedPalette.lightestOf(WrappedPalette.slab(Slot.HOURS_ALT, ThemeKey.AURORA))
        assertTrue("la losa del anyo tiene que ser MORADA (rojo > verde)",
            ContrastUtils.red(morada) > ContrastUtils.green(morada))
        assertTrue("la losa de sesiones tiene que ser TEAL (verde > rojo)",
            ContrastUtils.green(teal) > ContrastUtils.red(teal))
    }

    @Test
    fun `los temas monocromos no importan un matiz ajeno`() {
        // El Bloque 2 se paso una sesion entera sacando el azul del Cuero. Esta es la red para que
        // no vuelva: en un tema de un solo color, TODAS las losas tienen que seguir en la familia
        // de su acento. Se compara el orden de los canales, que es lo que define la familia.
        fun orden(c: Int) = listOf(
            "r" to ContrastUtils.red(c), "g" to ContrastUtils.green(c), "b" to ContrastUtils.blue(c)
        ).sortedByDescending { it.second }.map { it.first }

        listOf(ThemeKey.CUERO to "oro", ThemeKey.LIGHT to "verde salvia").forEach { (key, familia) ->
            val esperado = orden(key.anchorA)
            Slot.values().filter { it.tint == null }.forEach { slot ->
                WrappedPalette.slab(slot, key).forEach { stop ->
                    assertEquals(
                        "La losa $slot de $key (${hex(stop)}) se ha salido de la familia $familia.\n" +
                            "Meter un color ajeno en un tema monocromo es exactamente lo que el\n" +
                            "Bloque 2 quito del Cuero. La variedad ahi sale de profundidad y croma.",
                        esperado, orden(stop)
                    )
                }
            }
        }
    }

    @Test
    fun `la ceniza conserva el matiz y solo baja el croma`() {
        val oro = 0xD9AC5C
        val ceniza = WrappedPalette.ash(oro)
        // Sigue siendo oro: mismo orden de canales.
        assertTrue(ContrastUtils.red(ceniza) > ContrastUtils.green(ceniza))
        assertTrue(ContrastUtils.green(ceniza) > ContrastUtils.blue(ceniza))
        // Pero mas cerca del gris: la distancia entre el canal alto y el bajo se encoge.
        val cromaOro = ContrastUtils.red(oro) - ContrastUtils.blue(oro)
        val cromaCeniza = ContrastUtils.red(ceniza) - ContrastUtils.blue(ceniza)
        assertTrue("la ceniza tiene que tener menos croma que el oro", cromaCeniza < cromaOro)
        assertEquals("factor 0 = el color intacto", oro, WrappedPalette.ash(oro, 0f))
    }

    @Test
    fun `la banda de un tema esta bien ordenada`() {
        // El techo (lo mas claro que aguanta el blanco) tiene que ser mas claro que el suelo.
        ThemeKey.values().forEach { key ->
            val techo = WrappedPalette.ceiling(key.anchorA)
            val suelo = WrappedPalette.floor(key.anchorA, key.bg)
            assertTrue(
                "$key: el techo ${hex(techo)} no es mas claro que el suelo ${hex(suelo)}",
                ContrastUtils.luminance(techo) >= ContrastUtils.luminance(suelo)
            )
        }
    }
}
