package com.lecturameter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La red que faltaba.
 *
 * Este mismo bug (un color elegido contra un fondo oscuro que se rompe sobre el papel crema del
 * tema Claro) va por su CUARTA aparición: el heatmap invisible, el carril del widget, el Wrapped
 * entero (B-037), y seis textos más que el propio arreglo de B-037 se dejó, incluida la cinta del
 * bingo con el bug original literal e intacto a 1,49:1.
 *
 * `ContrastTest` prueba la matemática y pasa siempre, porque la matemática nunca fue el problema:
 * el fallo está en las LLAMADAS QUE NO SE HACEN. Un test de aritmética no puede ver un
 * `color = Gold` sin envolver. Este sí: lee el fuente y prohíbe el patrón.
 *
 * Si estás aquí porque este test se ha puesto rojo: no lo silencies. Envuelve el color en
 * `wrappedInk(...)` si es TEXTO (mínimo 4,5:1) o en `wrappedGraphic(...)` si es una barra, una
 * porción de donut o un carril (mínimo 3:1). Si de verdad es una excepción, añádela a
 * [EXCEPCIONES] con el motivo escrito.
 */
class WrappedColorLintTest {

    /** Los colores semánticos: fijos, pensados contra un fondo oscuro, y por tanto sospechosos. */
    private val SEMANTICOS = listOf("Gold", "Green", "Red", "Amber", "Sky", "Cyan", "Accent2", "Accent", "acc", "FavoriteRed")

    /**
     * Excepciones con motivo. `color = X.copy(alfa)` dentro de un `Surface` es el FONDO de una
     * pill: es alfa sobre el fondo del tema, así que se adapta solo y es correcto. Lo que no se
     * adapta, y es lo que este test persigue, es el TEXTO.
     */
    private val EXCEPCIONES = Regex("""Surface\(|\.background\(|border\s*=|BorderStroke""")

    private fun fuente(nombre: String): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val f = File(dir, "app/src/main/java/com/lecturameter/$nombre")
            if (f.isFile) return f
            dir = dir.parentFile ?: return@repeat
        }
        throw AssertionError("No encuentro $nombre desde ${System.getProperty("user.dir")}")
    }

    @Test
    fun `ningun texto del Wrapped usa un color semantico crudo`() {
        val src = fuente("WrappedScreens.kt")
        val patron = Regex("""color\s*=\s*(${SEMANTICOS.joinToString("|")})\s*(,|\)|\.copy)""")
        val ofensas = mutableListOf<String>()

        src.readLines().forEachIndexed { i, linea ->
            val limpia = linea.substringBefore("//")
            if (EXCEPCIONES.containsMatchIn(limpia)) return@forEachIndexed
            patron.find(limpia)?.let {
                ofensas += "  WrappedScreens.kt:${i + 1}  ${limpia.trim().take(96)}"
            }
        }

        assertTrue(
            "Color semantico CRUDO como texto en el Wrapped. Sobre el papel del tema Claro esto\n" +
                "no se lee (Gold da 1,52:1 y el minimo es 4,5:1). Envuelvelo en wrappedInk(color, theme),\n" +
                "o en wrappedGraphic(color, theme) si es un grafico:\n" + ofensas.joinToString("\n"),
            ofensas.isEmpty()
        )
    }

    @Test
    fun `el Wrapped no pinta texto con alfa`() {
        // La causa raiz de B-037: `color.copy(alpha = 0.7f)` en las etiquetas. Pintar texto con
        // alfa lo mezcla con el fondo ANTES de leerse, y por eso la tinta tenia que irse a un
        // marron casi negro para pasar AA. La jerarquia se hace con tamano y peso, no con alfa.
        val src = fuente("WrappedScreens.kt")
        val patron = Regex("""color\s*=\s*\w+\.copy\(\s*(alpha\s*=\s*)?0\.\d+f?\s*\)""")
        val ofensas = mutableListOf<String>()

        src.readLines().forEachIndexed { i, linea ->
            val limpia = linea.substringBefore("//")
            if (EXCEPCIONES.containsMatchIn(limpia)) return@forEachIndexed
            patron.find(limpia)?.let {
                ofensas += "  WrappedScreens.kt:${i + 1}  ${limpia.trim().take(96)}"
            }
        }

        assertTrue(
            "Texto pintado con alfa en el Wrapped. Es la causa raiz de B-037: el alfa mezcla el\n" +
                "color con el fondo antes de leerse. Usa un color solido (wrappedInk) y haz la\n" +
                "jerarquia con el tamano:\n" + ofensas.joinToString("\n"),
            ofensas.isEmpty()
        )
    }

    @Test
    fun `el Wrapped no tiene degradados oscuros fijos`() {
        // Las losas iban en navy opaco fijo y sobre el papel crema se quedaban de noche cerrada.
        // Eso fue B-037.
        //
        // La revision de B-037 devolvio esos mismos hex al tema Oscuro, donde nunca hubo bug: son
        // el Wrapped que Victor conoce y la pantalla que la gente comparte. Pero NO vuelven aqui.
        // Viven en la tabla de utils/WrappedPalette (DARK_SLABS/DARK_HEROES), en un solo sitio,
        // con su porque escrito y con WrappedPaletteTest midiendolos uno a uno contra los cinco
        // temas. Un hex suelto en ESTE fichero sigue siendo lo que era: una losa que se eligio
        // mirando un tema y dando por hecho los otros cuatro.
        //
        // O sea que la regla no se ha ablandado, se ha movido de sitio: aqui, cero hex.
        val src = fuente("WrappedScreens.kt")
        val patron = Regex("""Color\(0xFF[0-9A-Fa-f]{6}\)""")
        val ofensas = mutableListOf<String>()

        src.readLines().forEachIndexed { i, linea ->
            val limpia = linea.substringBefore("//")
            patron.find(limpia)?.let {
                ofensas += "  WrappedScreens.kt:${i + 1}  ${limpia.trim().take(96)}"
            }
        }

        assertTrue(
            "Color OPACO fijo en el Wrapped. Si es una losa, dale su slot en WrappedPalette y\n" +
                "usa wrappedSlabFor(Slot.X, theme); si es un semantico, dale nombre en MainActivity\n" +
                "y pasalo por wrappedInk:\n" + ofensas.joinToString("\n"),
            ofensas.isEmpty()
        )
    }

    @Test
    fun `cada losa del Wrapped sale de un slot y no de un pincel montado a mano`() {
        // La forma en que B-037 pudo aplanar el Wrapped sin que nadie lo viera: la identidad de
        // cada losa vivia en el sitio de la llamada (quien pintaba pasaba wrappedSlab a una caja y
        // wrappedSlabAlt a la de al lado). Cambiando los dos helpers, las doce losas se volvieron
        // una sola y el diff parecia una limpieza.
        //
        // Ahora la losa se pide por slot y el slot manda tambien su etiqueta y su numero, asi que
        // no se pueden desparejar. Esto prohibe volver atras.
        val src = fuente("WrappedScreens.kt")
        val prohibidos = Regex("""\b(wrappedSlab|wrappedSlabAlt|slabAltOf|onSlabMuted)\s*\(""")
        val ofensas = src.readLines().mapIndexedNotNull { i, linea ->
            val limpia = linea.substringBefore("//")
            if (prohibidos.containsMatchIn(limpia)) "  WrappedScreens.kt:${i + 1}  ${limpia.trim().take(96)}" else null
        }
        assertTrue(
            "Losa del Wrapped montada con los helpers planos. Todas las losas salian del acento\n" +
                "del tema, o sea que en AMOLED las siete tematicas eran el mismo gris (revision de\n" +
                "B-037). Usa wrappedSlabFor(Slot.X, theme) / wrappedHeroFor / onSlabMutedFor:\n" +
                ofensas.joinToString("\n"),
            ofensas.isEmpty()
        )
    }

    @Test
    fun `las anclas de WrappedPalette son las mismas que los acentos de MainActivity`() {
        // WrappedPalette es aritmetica pura y no puede importar Compose (los tests corren con
        // JUnitCore sin Compose en el classpath, ver run_tests.ps1 y gradle#12660), asi que los
        // acentos estan escritos dos veces. Una duplicacion que nadie vigila se pudre: si alguien
        // vuelve a retocar el verde del Claro en MainActivity (ya paso dos veces el mismo dia), el
        // Wrapped se quedaria con el verde viejo y nadie se enteraria hasta verlo en el movil.
        //
        // Esto lo caza leyendo los dos fuentes y comparando los numeros.
        val main = fuente("MainActivity.kt").readText()
        val palette = fuente("utils/WrappedPalette.kt").readText()

        fun tokenDeMain(nombre: String): String =
            Regex("""val\s+$nombre\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)""").find(main)
                ?.groupValues?.get(1)?.uppercase()
                ?: throw AssertionError("No encuentro el token $nombre en MainActivity.kt")

        fun anclaDePalette(entrada: String): String =
            Regex("""\b$entrada\(0x([0-9A-Fa-f]{6})\s*,""").find(palette)
                ?.groupValues?.get(1)?.uppercase()
                ?: throw AssertionError("No encuentro el ancla de $entrada en WrappedPalette.kt")

        val pares = listOf(
            "DARK" to "Accent", "AURORA" to "AccentAurora",
            "CUERO" to "AccentCuero", "AMOLED" to "AccentAmoled", "LIGHT" to "AccentLight"
        )
        pares.forEach { (entrada, token) ->
            assertEquals(
                "El ancla de $entrada en WrappedPalette no es $token. Los dos ficheros tienen que\n" +
                    "decir lo mismo: si cambias el acento de un tema, cambia tambien la tabla.",
                tokenDeMain(token), anclaDePalette(entrada)
            )
        }
    }

    @Test
    fun `accentGradient no se usa para pintar texto`() {
        // accentGradient() es de FONDO: su segundo stop va al 55% de alfa. Como pincel de texto,
        // la mitad derecha de la palabra se pinta al 55% (en Aurora el titulo del bingo caia a
        // 2,63:1). Para texto existe accentGradientText(), con los dos extremos opacos.
        val src = fuente("WrappedScreens.kt")
        val ofensas = src.readLines().mapIndexedNotNull { i, linea ->
            val limpia = linea.substringBefore("//")
            if (limpia.contains("brush") && Regex("""accentGradient\(""").containsMatchIn(limpia))
                "  WrappedScreens.kt:${i + 1}  ${limpia.trim().take(96)}" else null
        }
        assertTrue(
            "accentGradient() como pincel de TEXTO. Es de fondo y su 2o stop lleva alfa.\n" +
                "Usa accentGradientText(theme):\n" + ofensas.joinToString("\n"),
            ofensas.isEmpty()
        )
    }

    @Test
    fun `no hay em dashes visibles en el Wrapped`() {
        // Regla de estilo de Victor, repetida muchas veces: nada de guiones largos en textos de
        // la app. En comentarios se toleran, asi que se recorta el "//" antes de mirar.
        val src = fuente("WrappedScreens.kt")
        val ofensas = src.readLines().mapIndexedNotNull { i, linea ->
            val limpia = linea.substringBefore("//")
            if (limpia.contains('—')) "  WrappedScreens.kt:${i + 1}  ${limpia.trim().take(96)}" else null
        }
        assertTrue("Em dash en texto visible del Wrapped:\n" + ofensas.joinToString("\n"), ofensas.isEmpty())
    }
}
