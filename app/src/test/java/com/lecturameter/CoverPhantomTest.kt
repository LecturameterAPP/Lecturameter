package com.lecturameter

// Portada fantasma de Open Library. Los tests corren sin red (JUnitCore + android.jar, ver
// run_tests.ps1), asi que se prueba la funcion PURA que decide, con bytes calcados de la
// respuesta real.
//
// Medido el 21-07-2026 sobre 22 obras del catalogo sin cover_id:
//   18 (82%) -> HTTP 200 con 43 bytes, un GIF transparente de 1x1 servido como .jpg
//    4 (18%) -> portada real (12 KB, 26 KB, 35 KB, 47 KB)
//    0       -> entre 200 bytes y 8 KB
// Por eso el corte esta en 200 bytes y no en el umbral de 8 KB de isCoverUrlValid: son
// dos preguntas distintas. Aquella separa portada de placeholder editorial; esta separa
// imagen de "no tengo nada".

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverPhantomTest {

    /** Los 43 bytes exactos que sirve covers.openlibrary.org cuando no tiene la portada. */
    private fun gifFantasma(): ByteArray {
        val cabecera = "GIF89a".toByteArray(Charsets.US_ASCII)
        return ByteArray(43).also { cabecera.copyInto(it) }
    }

    /** Cabecera de un JPEG de verdad: SOI + APP0/JFIF. */
    private fun jpegReal(tam: Int): ByteArray {
        val b = ByteArray(tam)
        b[0] = 0xFF.toByte(); b[1] = 0xD8.toByte(); b[2] = 0xFF.toByte(); b[3] = 0xE0.toByte()
        return b
    }

    @Test
    fun `el gif de 43 bytes es fantasma`() {
        val b = gifFantasma()
        assertTrue(esPortadaFantasma(b, b.size))
    }

    @Test
    fun `un jpeg de 1 KB no es fantasma`() {
        val b = jpegReal(1024)
        assertFalse(esPortadaFantasma(b, 1024))
    }

    @Test
    fun `respuesta vacia es fantasma`() {
        assertTrue(esPortadaFantasma(ByteArray(1024), 0))
    }

    @Test
    fun `justo en el limite de 200 bytes es fantasma`() {
        assertTrue(esPortadaFantasma(jpegReal(1024), 200))
    }

    @Test
    fun `201 bytes de jpeg ya no es fantasma`() {
        assertFalse(esPortadaFantasma(jpegReal(1024), 201))
    }

    // Si algun dia Open Library engorda el pixel de relleno, el corte por tamano deja de
    // valer y tiene que salvarlo la firma. Por eso se comprueban las dos cosas y no una.
    @Test
    fun `un gif grande sigue siendo fantasma por la firma`() {
        val b = ByteArray(1024)
        "GIF89a".toByteArray(Charsets.US_ASCII).copyInto(b)
        assertTrue(esPortadaFantasma(b, 1024))
    }

    // Ante la duda se prefiere dar la portada por buena: una portada de mas solo cuesta una
    // imagen fea, y BookCover todavia la caza al pintarla. Una portada de menos es un libro
    // que se queda sin cubierta real habiendola.
    @Test
    fun `un png real no es fantasma`() {
        val b = ByteArray(1024)
        byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()).copyInto(b)
        assertFalse(esPortadaFantasma(b, 1024))
    }
}
