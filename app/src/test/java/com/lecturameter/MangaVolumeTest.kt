package com.lecturameter

// CP-4 del MangaResolver (ver Documentacion/DISENO_MANGA_RESOLVER.md). extractSeriesAndVolume
// separa el titulo de una obra en (serie sin marcador de tomo, numero de tomo). Puro, sin red.
// Un tomo de manga NO se resuelve por ISBN (el ISBN de Goodreads apunta a la edicion extranjera):
// va por serie + numero de tomo, asi que esta extraccion es la base del resolutor. Vigila ademas
// que la regex del marcador quede consolidada en un solo sitio (VOLUME_MARKER_REGEX).

// extractSeriesAndVolume vive en SearchRepository.kt, package com.lecturameter (mismo que este
// test pese a estar el fichero en la carpeta repository/): acceso directo, sin import.
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaVolumeTest {

    @Test fun tomoEspanol() {
        val (serie, tomo) = extractSeriesAndVolume("Dandadan tomo 9")
        assertEquals("Dandadan", serie)
        assertEquals(9, tomo)
    }

    @Test fun volAbreviadoConPunto() {
        val (serie, tomo) = extractSeriesAndVolume("Chainsaw Man Vol. 12")
        assertEquals("Chainsaw Man", serie)
        assertEquals(12, tomo)
    }

    @Test fun almohadilla() {
        val (serie, tomo) = extractSeriesAndVolume("Berserk #3")
        assertEquals("Berserk", serie)
        assertEquals(3, tomo)
    }

    @Test fun volumenLargo() {
        val (serie, tomo) = extractSeriesAndVolume("One Piece volumen 105")
        assertEquals("One Piece", serie)
        assertEquals(105, tomo)
    }

    @Test fun sinMarcadorDejaElTituloYtomoNulo() {
        val (serie, tomo) = extractSeriesAndVolume("Nausicaa")
        assertEquals("Nausicaa", serie)
        assertNull(tomo)
    }

    @Test fun cerosALaIzquierda() {
        // El "0*" del regex se traga los ceros: "tomo 03" -> 3, serie limpia.
        val (serie, tomo) = extractSeriesAndVolume("Naruto tomo 03")
        assertEquals("Naruto", serie)
        assertEquals(3, tomo)
    }
}
