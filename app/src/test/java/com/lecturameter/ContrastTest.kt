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
        for ((nombre, color) in acentos + semanticos) {
            val sub = ContrastUtils.onSlabMutedFor(color)
            val stop1 = ContrastUtils.slabStops(color).first
            val ratio = ContrastUtils.contrast(sub, stop1)
            assertTrue(
                "el subtitulo de la losa de $nombre da ${"%.2f".format(ratio)}:1",
                ratio >= AA_TEXT
            )
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
        assertEquals(21.0, ContrastUtils.contrast(0x000000, 0xFFFFFF), 0.01)
        assertEquals(1.0, ContrastUtils.contrast(0x777777, 0x777777), 0.001)
        // Valor conocido: #767676 sobre blanco es el gris limite de AA (4,54:1).
        assertEquals(4.54, ContrastUtils.contrast(0x767676, 0xFFFFFF), 0.02)
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
