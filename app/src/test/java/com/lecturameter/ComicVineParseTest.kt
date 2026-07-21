package com.lecturameter

// Parseo de Comic Vine. Los tests corren sin red (JUnitCore + android.jar, ver
// run_tests.ps1), asi que se prueba el MAPEO con JSON calcado de la respuesta REAL de
// https://comicvine.gamespot.com/api/search/?resources=volume&query=Dandadan
// (verificada el 21-07-2026: 6 resultados, todos llamados "Dandadan").

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicVineParseTest {

    // Recorte literal de la respuesta real. Ojo al detalle que motiva medio test suite:
    // Comic Vine devuelve una entrada por EDICION NACIONAL, todas con el mismo `name`.
    private val dandadanJson = """
    {
      "error": "OK",
      "limit": 5,
      "number_of_total_results": 6,
      "status_code": 1,
      "results": [
        { "id": 145549, "name": "Dandadan", "start_year": "2022",
          "count_of_issues": 12, "publisher": { "name": "Viz" },
          "image": { "medium_url": "https://comicvine.example/med/145549.jpg",
                     "original_url": "https://comicvine.example/orig/145549.jpg" },
          "resource_type": "volume" },
        { "id": 138036, "name": "Dandadan", "start_year": "2021",
          "count_of_issues": 20, "publisher": { "name": "Shueisha" },
          "image": { "medium_url": "https://comicvine.example/med/138036.jpg" },
          "resource_type": "volume" },
        { "id": 160374, "name": "Dandadan", "start_year": "2022",
          "count_of_issues": 16, "publisher": { "name": "Edizioni BD" },
          "image": { "medium_url": "https://comicvine.example/med/160374.jpg" },
          "resource_type": "volume" }
      ]
    }
    """.trimIndent()

    @Test
    fun `mapea nombre, anio, portada y clave cv_`() {
        val out = parseComicVineResults(dandadanJson, "Dandadan")
        assertEquals(3, out.size)
        val first = out[0]
        assertEquals("Dandadan", first.title)
        assertEquals("2022", first.publishYear)
        assertEquals("cv_145549", first.olKey)
        assertEquals("https://comicvine.example/med/145549.jpg", first.coverUrl)
    }

    @Test
    fun `no inventa paginas, autor ni ISBN porque Comic Vine no los tiene`() {
        // Es la limitacion central de esta fuente y esta escrita en el codigo: si algun
        // dia alguien "arregla" el parseo rellenando estos campos, este test lo caza.
        val first = parseComicVineResults(dandadanJson, "Dandadan").first()
        assertEquals(0, first.pages)
        assertEquals("", first.author)
        assertNull(first.isbn)
    }

    @Test
    fun `la clave cv_ queda exenta del filtro de relevancia global`() {
        assertTrue(isMangaSourceKey(parseComicVineResults(dandadanJson, "Dandadan").first().olKey))
    }

    @Test
    fun `status_code distinto de 1 no devuelve nada`() {
        // Comic Vine responde HTTP 200 con status_code 100 cuando la key es invalida.
        // Tratarlo como exito devolveria una lista fantasma.
        val error = """{ "error": "Invalid API Key", "status_code": 100, "results": [] }"""
        assertTrue(parseComicVineResults(error, "Dandadan").isEmpty())
    }

    @Test
    fun `json roto no revienta`() {
        assertTrue(parseComicVineResults("no soy json", "Dandadan").isEmpty())
        assertTrue(parseComicVineResults("", "Dandadan").isEmpty())
    }

    @Test
    fun `filtra lo que no se parece a la consulta`() {
        // Comic Vine no devuelve vacio cuando no conoce algo: devuelve lo que mas se le
        // parece. Sin el filtro por solapamiento de tokens, contaminaria la lista.
        val ruido = """
        {
          "error": "OK", "status_code": 1,
          "results": [
            { "id": 1, "name": "Spider-Man", "start_year": "1963",
              "image": { "medium_url": "https://comicvine.example/med/1.jpg" } }
          ]
        }
        """.trimIndent()
        assertTrue(parseComicVineResults(ruido, "Dandadan").isEmpty())
    }

    @Test
    fun `descarta resultados sin nombre o sin id`() {
        val parcial = """
        {
          "error": "OK", "status_code": 1,
          "results": [
            { "id": 0, "name": "Dandadan" },
            { "id": 99, "name": "" }
          ]
        }
        """.trimIndent()
        assertTrue(parseComicVineResults(parcial, "Dandadan").isEmpty())
    }

    @Test
    fun `sin imagen la portada queda nula, no en cadena vacia`() {
        // Una cadena vacia como URL hace que el cargador de imagenes intente una peticion
        // a "" en vez de caer al placeholder.
        val sinImagen = """
        { "error": "OK", "status_code": 1,
          "results": [ { "id": 7, "name": "Dandadan", "start_year": "2021" } ] }
        """.trimIndent()
        assertNull(parseComicVineResults(sinImagen, "Dandadan").first().coverUrl)
    }
}
