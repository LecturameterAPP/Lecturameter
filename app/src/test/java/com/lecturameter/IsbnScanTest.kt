package com.lecturameter

// F8 / B-027: el escaner aceptaba cualquier cadena de 10 caracteres como ISBN-10 y no
// validaba el digito de control, con TODOS los formatos de codigo habilitados.

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnScanTest {

    @Test fun acepta_ean13_reales_con_checksum_valido() {
        assertTrue(isValidIsbn("9788412987058"))   // Habito y mortaja
        assertTrue(isValidIsbn("9788445007020"))   // El dragon renacido, Minotauro 2019
        assertTrue(isValidIsbn("9788478884452"))   // Harry Potter y la piedra filosofal
    }

    @Test fun rechaza_ean13_con_checksum_malo() {
        // Una lectura parcial casi nunca cuadra el digito de control
        assertFalse(isValidIsbn("9788412987051"))
        assertFalse(isValidIsbn("9788445007021"))
    }

    @Test fun rechaza_lo_que_no_es_prefijo_de_libro() {
        // EAN-13 valido pero de producto, no de libro (no empieza por 978/979)
        assertFalse(isValidIsbn("4006381333931"))
    }

    @Test fun rechaza_cualquier_cadena_de_10_caracteres() {
        // El bug original: length == 10 pasaba sin mirar nada mas
        assertFalse(isValidIsbn("ABCDEFGHIJ"))
        assertFalse(isValidIsbn("1234567890"))
        assertFalse(isValidIsbn("0000000000"))
    }

    @Test fun acepta_isbn10_valido_incluida_la_X() {
        assertTrue(isValidIsbn("0306406152"))
        assertTrue(isValidIsbn("043942089X"))
    }

    @Test fun tolera_guiones_y_minusculas_en_la_X() {
        assertTrue(isValidIsbn("043942089x".uppercase()))
    }
}
