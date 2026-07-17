package com.lecturameter

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
        // Las losas iban en navy opaco fijo y sobre el papel crema se quedaban a 10:1 contra el
        // fondo. Ahora derivan del tema con slabOf()/wrappedSlab(). Un hex de 6 cifras opaco
        // suelto en este fichero es, casi seguro, una losa nueva que no se entero.
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
            "Color OPACO fijo en el Wrapped. Si es una losa, usa wrappedSlab(theme) o slabOf(color);\n" +
                "si es un semantico, dale nombre en MainActivity y pasalo por wrappedInk:\n" +
                ofensas.joinToString("\n"),
            ofensas.isEmpty()
        )
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
