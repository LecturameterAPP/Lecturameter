package com.lecturameter

// F8 / B-023: la guardia que evita que un ISBN escaneado de OTRA obra entre como
// edición del libro abierto (caso real: "Hábito y Mortaja" dentro de "El dragón
// renacido"). Regla: comparar por autor, nunca por título — las ediciones cambian
// de título entre idiomas.

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditionGuardTest {

    @Test fun detecta_el_caso_real_habito_y_mortaja() {
        assertTrue(editionAuthorMismatch("Robert Jordan", "Carlos di Urarte"))
    }

    @Test fun no_avisa_en_la_edicion_inglesa_del_mismo_autor() {
        // "The Dragon Reborn" es edición legítima de "El dragón renacido"
        assertFalse(editionAuthorMismatch("Robert Jordan", "Robert Jordan"))
    }

    @Test fun tolera_orden_e_iniciales_del_nombre() {
        assertFalse(editionAuthorMismatch("J.R.R. Tolkien", "Tolkien, J. R. R."))
        assertFalse(editionAuthorMismatch("Brandon Sanderson", "SANDERSON, BRANDON"))
    }

    @Test fun tolera_acentos_y_mayusculas() {
        assertFalse(editionAuthorMismatch("Gabriel García Márquez", "Gabriel Garcia Marquez"))
    }

    @Test fun no_avisa_si_falta_el_autor_en_algun_lado() {
        // Sin dato no hay juicio: preferimos callar antes que dar un falso positivo
        assertFalse(editionAuthorMismatch("Robert Jordan", ""))
        assertFalse(editionAuthorMismatch("", "Carlos di Urarte"))
        assertFalse(editionAuthorMismatch("", ""))
    }

    @Test fun coautores_comparten_token_y_no_avisan() {
        // Ediciones que listan solo a uno de los dos autores
        assertFalse(editionAuthorMismatch("Terry Pratchett y Neil Gaiman", "Neil Gaiman"))
    }
}
