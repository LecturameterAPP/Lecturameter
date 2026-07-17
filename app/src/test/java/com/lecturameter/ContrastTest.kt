package com.lecturameter

import com.lecturameter.utils.ContrastUtils
import com.lecturameter.utils.ContrastUtils.AA_TEXT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-037. La misma clase de fallo lleva tres apariciones: el heatmap invisible en Claro (que
 * dejó la revisión del B2 en NOT READY), el carril del widget, y el Wrapped entero. Siempre
 * la misma causa: un color elegido mirándolo sobre UN fondo, con el otro tema dado por hecho.
 *
 * Con cinco temas, uno papel crema y otro negro puro, el ojo no llega. Esto lo mide.
 */
class ContrastTest {

    // Fondos reales de los cinco temas (bgDark), y si son oscuros.
    private val temas = listOf(
        Triple("Claro",  0xFAF8F2, false),
        Triple("Oscuro", 0x0F172A, true),
        Triple("Aurora", 0x03343A, true),
        Triple("AMOLED", 0x000000, true),
        Triple("Cuero",  0x281B10, true)
    )

    // Los colores que el Wrapped usa como texto de pill.
    private val semanticos = listOf(
        "Green" to 0x10B981, "Red" to 0xF87171, "Amber" to 0xF59E0B,
        "Gold" to 0xFFBB33, "Sky" to 0x0EA5E9, "Cyan" to 0x22D3EE,
        "Accent" to 0x6366F1, "Accent2" to 0x8B5CF6
    )

    // Los cinco acentos, uno por tema.
    private val acentos = listOf(
        "indigo (Oscuro)" to 0x6366F1, "verde (Claro)" to 0x41755A,
        "morado (Aurora)" to 0xB794F6, "oro (Cuero)" to 0xD9AC5C,
        "gris (AMOLED)" to 0xA1A1AA
    )

    private val BLANCO = 0xFFFFFF

    // ── el invariante que importa ────────────────────────────────────────────

    @Test
    fun `toda pill del Wrapped es legible en los cinco temas`() {
        for ((nombreTema, bg, isDark) in temas) {
            for ((nombreColor, color) in semanticos) {
                val ink = ContrastUtils.inkFor(color, bg, isDark, fallback = 0x000000)
                val pill = ContrastUtils.flatten(ink, ContrastUtils.PILL_ALPHA, bg)
                val ratio = ContrastUtils.contrast(ink, pill)
                assertTrue(
                    "$nombreColor sobre $nombreTema da ${"%.2f".format(ratio)}:1 (minimo $AA_TEXT)",
                    ratio >= AA_TEXT
                )
            }
        }
    }

    @Test
    fun `toda losa aguanta texto blanco, sea del tema o semantica`() {
        for ((nombre, color) in acentos + semanticos) {
            val (stop1, stop2) = ContrastUtils.slabStops(color)
            assertTrue(
                "blanco sobre la losa de $nombre (stop claro) da ${"%.2f".format(ContrastUtils.contrast(BLANCO, stop1))}:1",
                ContrastUtils.contrast(BLANCO, stop1) >= AA_TEXT
            )
            // El stop2 es mas oscuro que el stop1, asi que si pasa el primero pasa el segundo.
            assertTrue(
                "el segundo stop de $nombre no es mas oscuro que el primero",
                ContrastUtils.luminance(stop2) < ContrastUtils.luminance(stop1)
            )
        }
    }

    @Test
    fun `el subtitulo de la losa es legible sobre ella`() {
        // OJO si este test se pone rojo tras un cambio inocuo: el oro vive EN EL FILO. Gold da
        // 4,51:1 y el oro del Cuero 4,53:1, contra un minimo de 4,50, o sea 0,01 y 0,03 de
        // margen. Cualquier retoque de SLAB_DARKEN, del paso del bucle o del redondeo los cruza.
        // Que se ponga rojo NO significa que se haya roto nada grave: significa que el oro se ha
        // caido del filo y hay que decidir a mano (bajar el suelo del subtitulo o aclararlo mas).
        for ((nombre, color) in acentos + semanticos) {
            val sub = ContrastUtils.onSlabMutedFor(color)
            val stop1 = ContrastUtils.slabStops(color).first
            val ratio = ContrastUtils.contrast(sub, stop1)
            assertTrue(
                "el subtitulo de la losa de $nombre da ${"%.2f".format(ratio)}:1 (minimo $AA_TEXT)",
                ratio >= AA_TEXT
            )
        }
    }

    @Test
    fun `todo grafico se distingue de la tarjeta sobre la que se pinta`() {
        // Los graficos no son texto: WCAG pide 3:1. Se pintan sobre la TARJETA (theme.surface),
        // que en Claro es BLANCO PURO, no el crema del fondo. Esa diferencia es la que hundia el
        // cian de las barras a 1,81:1 (revision 18-07).
        val tarjetas = listOf(
            Triple("Claro", 0xFFFFFF, false), Triple("Oscuro", 0x1A2233, true),
            Triple("Aurora", 0x0D3D42, true), Triple("AMOLED", 0x101010, true),
            Triple("Cuero", 0x291D10, true)
        )
        for ((nombreTema, bg, isDark) in tarjetas) {
            for ((nombreColor, color) in semanticos) {
                val g = ContrastUtils.solidFor(color, bg, isDark, fallback = 0x000000)
                val ratio = ContrastUtils.contrast(g, bg)
                assertTrue(
                    "$nombreColor sobre la tarjeta de $nombreTema da ${"%.2f".format(ratio)}:1 (minimo ${ContrastUtils.AA_LARGE})",
                    ratio >= ContrastUtils.AA_LARGE
                )
            }
        }
    }

    // ── las regresiones concretas que ya nos han mordido ─────────────────────

    @Test
    fun `los colores que se borraban en Claro ya no se borran`() {
        val crema = 0xFAF8F2
        // Medidos ANTES del arreglo: asi de mal estaban sobre el papel.
        val rotos = mapOf("Gold" to 0xFFBB33, "Cyan" to 0x22D3EE, "Amber" to 0xF59E0B, "Sky" to 0x0EA5E9)
        for ((nombre, color) in rotos) {
            val pillCruda = ContrastUtils.flatten(color, 0.12f, crema)
            assertTrue(
                "$nombre deberia fallar SIN el arreglo (si no, este test ya no prueba nada)",
                ContrastUtils.contrast(color, pillCruda) < AA_TEXT
            )
            val ink = ContrastUtils.inkFor(color, crema, isDark = false, fallback = 0x000000)
            val pill = ContrastUtils.flatten(ink, ContrastUtils.PILL_ALPHA, crema)
            assertTrue("$nombre sigue roto en Claro", ContrastUtils.contrast(ink, pill) >= AA_TEXT)
        }
    }

    @Test
    fun `Accent2 llevaba fallando en Oscuro desde antes del tema Claro`() {
        val oscuro = 0x0F172A
        val accent2 = 0x8B5CF6
        // No es dano del tema Papel: sobre el negro tampoco llegaba (3,81:1).
        val pillCruda = ContrastUtils.flatten(accent2, 0.15f, oscuro)
        assertTrue(
            "si Accent2 ya pasara en Oscuro, sobraria la correccion hacia arriba",
            ContrastUtils.contrast(accent2, pillCruda) < AA_TEXT
        )
        // Sobre negro se corrige ACLARANDO, no oscureciendo.
        val ink = ContrastUtils.inkFor(accent2, oscuro, isDark = true, fallback = 0xFFFFFF)
        assertTrue(
            "sobre el negro la tinta tiene que ir hacia la luz, no hacia el negro",
            ContrastUtils.luminance(ink) > ContrastUtils.luminance(accent2)
        )
    }

    @Test
    fun `la losa del Claro reproduce el mockup que aprobo Victor`() {
        // Opcion 3 "Hibrido", mockup_wrapped_claro.html: #284938 -> #1A2F24.
        val (stop1, stop2) = ContrastUtils.slabStops(0x41755A)
        assertEquals("stop claro", 0x284938, stop1)
        assertEquals("stop oscuro", 0x1A2F24, stop2)
    }

    @Test
    fun `un factor fijo no habria valido para el oro`() {
        // Por que slabStops mide en vez de usar SLAB_DARKEN a secas. El oro es el caso claro
        // (4,22:1 con el factor fijo). El cian cae en 4,46, tan pegado al minimo que no sirve
        // de ejemplo: medio paso de redondeo lo cruza. Justamente por eso se mide.
        val oro = 0xFFBB33
        val fijo = ContrastUtils.darken(oro, ContrastUtils.SLAB_DARKEN)
        assertTrue(
            "el oro con el factor fijo deberia quedarse corto (si no, sobra medir)",
            ContrastUtils.contrast(BLANCO, fijo) < AA_TEXT
        )
        val medido = ContrastUtils.slabStops(oro).first
        assertTrue("el oro medido si pasa", ContrastUtils.contrast(BLANCO, medido) >= AA_TEXT)
    }

    // ── la matematica ────────────────────────────────────────────────────────

    @Test
    fun `el contraste de referencia sale bien`() {
        // Se cae `contrast(x, x) == 1`: era TAUTOLOGICO. (L+0.05)/(L+0.05) da 1 por algebra, para
        // cualquier x y hasta con una luminance() rota (revision 18-07). No probaba nada.
        assertEquals(21.0, ContrastUtils.contrast(0x000000, 0xFFFFFF), 0.01)
        // Valores conocidos: solo estos prueban que luminance() es la de verdad. Los tres primarios
        // puros valen porque cada uno aisla un coeficiente distinto (0.2126 / 0.7152 / 0.0722), asi
        // que una luminance() con los pesos cambiados de sitio los falla.
        assertEquals(4.54, ContrastUtils.contrast(0x767676, 0xFFFFFF), 0.02)
        assertEquals(8.59, ContrastUtils.contrast(0x0000FF, 0xFFFFFF), 0.02)  // azul, el mas oscuro
        assertEquals(1.37, ContrastUtils.contrast(0x00FF00, 0xFFFFFF), 0.02)  // verde, el mas claro
        assertEquals(3.99, ContrastUtils.contrast(0xFF0000, 0xFFFFFF), 0.02)  // rojo, en medio
    }

    @Test
    fun `aplanar con alfa no es lo mismo que no aplanar`() {
        val crema = 0xFAF8F2
        val oro = 0xFFBB33
        // Un color al 15% sobre crema queda casi crema: por eso medir sin aplanar miente.
        val plano = ContrastUtils.flatten(oro, 0.15f, crema)
        assertTrue("el tinte tiene que quedar cerca del fondo", ContrastUtils.contrast(plano, crema) < 1.2)
        assertEquals("alfa 0 = el fondo", crema, ContrastUtils.flatten(oro, 0f, crema))
        assertEquals("alfa 1 = el color", oro, ContrastUtils.flatten(oro, 1f, crema))
    }

    // ── B4 (2): el rojo del cartón 3×3 del Bingo ─────────────────────────────
    //
    // Réplica de redForTheme()/onRedColor() y de los fondos REALES de la casilla. No se
    // pueden importar (viven en MainActivity.kt y devuelven Color de Compose, que no está
    // en el classpath de los tests: ver la cabecera de ContrastUtils). Si alguien toca
    // aquellas funciones y no esto, el test deja de cubrirlas: por eso van los valores
    // duplicados y comentados, igual que hace el resto de este fichero con los acentos.
    private val rojoPorTema = mapOf(
        "Claro" to 0xB91C1C, "Oscuro" to 0xF87171, "Aurora" to 0xFF9494,
        "AMOLED" to 0xF87171, "Cuero" to 0xF87171
    )
    // Tinta de onRedColor(): blanco en Claro (su rojo es oscuro), la tinta oscura del tema
    // en los otros cuatro (sus rojos son claros).
    private val tintaSobreRojo = mapOf(
        "Claro" to 0xFFFFFF, "Oscuro" to 0x0F172A, "Aurora" to 0x1A1030,
        "AMOLED" to 0x000000, "Cuero" to 0x241608
    )
    // darken(color, 0.4): réplica del contorno mas oscuro de la casilla del 3×3 (rojo del
    // relleno oscurecido al 60%). Cada canal * (1 - 0.4). Feedback de Víctor 17-07: outline
    // mas oscuro para definir la casilla sobre el relleno rojo.
    private fun darken40(rgb: Int): Int {
        val r = ((rgb shr 16 and 0xFF) * 0.6f).toInt()
        val g = ((rgb shr 8 and 0xFF) * 0.6f).toInt()
        val b = ((rgb and 0xFF) * 0.6f).toInt()
        return (r shl 16) or (g shl 8) or b
    }

    @Test
    fun `el 3x3 rojo se lee y su contorno oscuro se distingue`() {
        // Diseño real (feedback 17-07): la casilla del 3×3 va RELLENA de rojo, con el texto
        // en onRedColor y un contorno mas oscuro. El viejo test (texto rojo sobre cardColor)
        // ya no refleja nada: esa combinacion desaparecio al pintar la casilla de rojo.
        for ((nombreTema, bg, _) in temas) {
            val rojo = rojoPorTema.getValue(nombreTema)
            val tinta = tintaSobreRojo.getValue(nombreTema)
            // 1) El texto de la casilla (onRedColor) se lee sobre el relleno rojo.
            val ratioTexto = ContrastUtils.contrast(tinta, rojo)
            assertTrue(
                "el texto de $nombreTema sobre la casilla roja da ${"%.2f".format(ratioTexto)}:1 (minimo $AA_TEXT)",
                ratioTexto >= AA_TEXT
            )
            // 2) El contorno mas oscuro se distingue del relleno (si no, no se veria el borde).
            val ratioBorde = ContrastUtils.contrast(darken40(rojo), rojo)
            assertTrue(
                "el contorno de $nombreTema no se distingue del relleno: ${"%.2f".format(ratioBorde)}:1",
                ratioBorde >= 1.2
            )
            // 3) El mensaje "carton completado" va en rojo directo sobre el fondo de pantalla.
            assertTrue(
                "el rojo de $nombreTema sobre el fondo da ${"%.2f".format(ContrastUtils.contrast(rojo, bg))}:1",
                ContrastUtils.contrast(rojo, bg) >= AA_TEXT
            )
        }
    }

    @Test
    fun `el boton relleno de rojo aguanta su propio texto`() {
        // Aqui es donde onAccentColor NO servia: en Oscuro devuelve blanco, y el rojo de
        // Oscuro es el #F87171, que es CLARO. Blanco encima da 2,77:1. De ahi onRedColor.
        for ((nombreTema, _, _) in temas) {
            val rojo = rojoPorTema.getValue(nombreTema)
            val tinta = tintaSobreRojo.getValue(nombreTema)
            val ratio = ContrastUtils.contrast(tinta, rojo)
            assertTrue(
                "la tinta de $nombreTema sobre su boton rojo da ${"%.2f".format(ratio)}:1 (minimo $AA_TEXT)",
                ratio >= AA_TEXT
            )
        }
    }

    @Test
    fun `el rojo del 3x3 se distingue del acento del 4x4`() {
        // Los dos cartones conviven en la misma pantalla y el color es lo unico que los
        // separa de un vistazo. Si en algun tema el rojo y el acento fueran casi el mismo
        // color, el selector no comunicaria nada. 1,3 de ratio ENTRE ellos es poco margen,
        // pero es que Cuero (oro) y Claro (verde) ya son de familias muy distintas al rojo.
        val acentoPorTema = mapOf(
            "Claro" to 0x41755A, "Oscuro" to 0x6366F1, "Aurora" to 0xB794F6,
            "AMOLED" to 0xA1A1AA, "Cuero" to 0xD9AC5C
        )
        for ((nombreTema, _, _) in temas) {
            val rojo = rojoPorTema.getValue(nombreTema)
            val acento = acentoPorTema.getValue(nombreTema)
            assertTrue(
                "en $nombreTema el rojo del 3x3 y el acento del 4x4 son casi el mismo color",
                rojo != acento
            )
        }
    }

    @Test
    fun `oscurecer y aclarar conservan el matiz`() {
        val sky = 0x0EA5E9
        val oscuro = ContrastUtils.darken(sky, 0.5f)
        // El azul sigue dominando sobre el rojo, y en la misma proporcion.
        assertTrue(ContrastUtils.blue(oscuro) > ContrastUtils.green(oscuro))
        assertTrue(ContrastUtils.green(oscuro) > ContrastUtils.red(oscuro))
        assertEquals(0x000000, ContrastUtils.darken(sky, 1f))
        assertEquals(0xFFFFFF, ContrastUtils.lighten(sky, 1f))
        assertEquals(sky, ContrastUtils.darken(sky, 0f))
    }
}
