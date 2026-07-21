package com.lecturameter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Paridad entre la normalizacion de la app (CatalogRepository.normalize) y la del ETL
 * del catalogo (catalog-etl/common.py:normalize).
 *
 * Es critico que coincidan: el ETL guarda works.title_normalized ya normalizado, y la app
 * normaliza la consulta del usuario antes del MATCH. Si divergen, el fallo no es un crash
 * sino algo peor, busquedas que devuelven vacio sin motivo aparente.
 *
 * Los valores esperados NO estan escritos a mano: se generaron ejecutando la funcion de
 * Python sobre estas mismas entradas (catalog-etl/gen_norm_cases.py). Si se toca cualquiera
 * de las dos implementaciones, hay que regenerarlos y volver a pasar este test.
 */
class CatalogNormalizeTest {

    private fun check(entrada: String, esperado: String) {
        assertEquals(
            "normalize(\"$entrada\") diverge del ETL de Python",
            esperado,
            CatalogRepository.normalize(entrada)
        )
    }

    @Test
    fun normalizacionCoincideConElEtlDePython() {
        check("El Quijote", "el quijote")
        check("Cien anos de soledad", "cien anos de soledad")
        check("Cien años de soledad", "cien anos de soledad")
        check("L'Étranger", "l etranger")
        check("Harry Potter & the Philosopher's Stone", "harry potter the philosopher s stone")
        check("¿Quién soy?", "quien soy")
        check("Crime and Punishment (Penguin Classics)", "crime and punishment penguin classics")
        check("Niebla   con    espacios", "niebla con espacios")
        check("MAYÚSCULAS Y ACENTOS ÁÉÍÓÚÑ", "mayusculas y acentos aeioun")
        check("1Q84", "1q84")
        check("Café-Bar: el libro", "cafe bar el libro")
        check("Ödipus", "odipus")
        check("", "")
        check("   ", "")
        // El \w de Python conserva el guion bajo; el regex de Kotlin debe hacerlo tambien.
        check("foo_bar libro", "foo_bar libro")
    }

    @Test
    fun buscarSinAcentosEncuentraElTituloAcentuado() {
        // El caso de uso real: nadie escribe "años" en el buscador.
        assertEquals(
            CatalogRepository.normalize("Cien años de soledad"),
            CatalogRepository.normalize("cien anos de soledad")
        )
    }

    // -- construccion de la expresion MATCH de FTS4 --------------------------

    @Test
    fun elUltimoTokenLlevaPrefijoParaBuscarMientrasSeEscribe() {
        assertEquals("cien anos de soled*", CatalogRepository.ftsQuery("Cien años de soled"))
    }

    @Test
    fun unSoloTokenTambienLlevaPrefijo() {
        assertEquals("quijot*", CatalogRepository.ftsQuery("Quijot"))
    }

    @Test
    fun tokenFinalDeUnaLetraNoLlevaPrefijo() {
        // "a*" casaria con media base; no aporta nada y penaliza la consulta.
        assertEquals("el libro a", CatalogRepository.ftsQuery("El libro a"))
    }

    @Test
    fun consultaSinTokensUtilesDevuelveNull() {
        // Sin esto se lanzaria un MATCH vacio, que en FTS4 es un error de sintaxis.
        assertNull(CatalogRepository.ftsQuery(""))
        assertNull(CatalogRepository.ftsQuery("   "))
        assertNull(CatalogRepository.ftsQuery("¿?¡!.,;"))
    }

    @Test
    fun laPuntuacionNoSeCuelaEnLaExpresionMatch() {
        // Comillas y parentesis sin escapar romperian la sintaxis de FTS4.
        val q = CatalogRepository.ftsQuery("Harry Potter & the Philosopher's Stone")
        assertEquals("harry potter the philosopher s stone*", q)
    }
}
