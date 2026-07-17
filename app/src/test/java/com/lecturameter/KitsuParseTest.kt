package com.lecturameter

// P-029: parseo de Kitsu. Los tests corren sin red (JUnitCore + android.jar, ver
// run_tests.ps1), asi que se prueba el MAPEO con JSON de ejemplo calcado de las
// respuestas reales de https://kitsu.app/api/edge/manga (verificadas el 17-07-2026).

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class KitsuParseTest {

    // mapApiGenre vive en MainActivity.kt y arrastra Compose, que no esta en el
    // classpath de estos tests. Se inyecta un mapeo equivalente para las categorias
    // que usan los fixtures (Horror -> Terror, Adventure -> Aventura), que es lo que
    // mapApiGenre hace de verdad. Lo que se prueba aqui es el parseo de Kitsu, no
    // mapApiGenre (que es previo a P-029 y lo usan ya las otras 8 fuentes).
    private val fakeMapper: (List<String>) -> List<String> = { cats ->
        cats.mapNotNull {
            when (it.lowercase()) {
                "horror" -> "Terror"
                "adventure" -> "Aventura"
                "romance" -> "Romance"
                else -> null
            }
        }.distinct()
    }

    // Respuesta real recortada: Berserk, con autor (staff -> person) y categorias.
    private val berserkJson = """
    {
      "data": [{
        "id": "8",
        "type": "manga",
        "attributes": {
          "canonicalTitle": "Berserk",
          "titles": { "en": "Berserk", "ja_jp": "ベルセルク" },
          "abbreviatedTitles": ["Berserk: The Prototype"],
          "startDate": "1989-08-25",
          "subtype": "manga",
          "posterImage": {
            "tiny": "https://media.kitsu.app/manga/8/poster_image/tiny-x.jpeg",
            "medium": "https://media.kitsu.app/manga/8/poster_image/medium-x.jpeg",
            "large": "https://media.kitsu.app/manga/8/poster_image/large-x.jpeg",
            "original": "https://media.kitsu.app/manga/8/poster_image/original-x.jpeg"
          }
        },
        "relationships": {
          "categories": { "data": [{ "type": "categories", "id": "207" }, { "type": "categories", "id": "67" }] },
          "staff": { "data": [{ "type": "mediaStaff", "id": "160603" }] }
        }
      }],
      "included": [
        { "id": "207", "type": "categories", "attributes": { "title": "Horror", "slug": "horror" } },
        { "id": "67",  "type": "categories", "attributes": { "title": "Adventure", "slug": "adventure" } },
        { "id": "160603", "type": "mediaStaff", "attributes": { "role": "Story & Art" },
          "relationships": { "person": { "data": { "type": "people", "id": "1677" } } } },
        { "id": "1677", "type": "people", "attributes": { "name": "Kentarou Miura" } }
      ]
    }
    """.trimIndent()

    @Test fun mapea_una_serie_real_con_autor_portada_y_generos() {
        val r = parseKitsuMangaResults(berserkJson, "berserk", genreMapper = fakeMapper)
        assertEquals(1, r.size)
        val b = r[0]
        assertEquals("Berserk", b.title)
        assertEquals("Kentarou Miura", b.author)
        // Portada: se prefiere original sobre large/medium
        assertEquals("https://media.kitsu.app/manga/8/poster_image/original-x.jpeg", b.coverUrl)
        assertEquals("kt_8", b.olKey)
        assertEquals("1989", b.publishYear)
        // Serie, no tomo: ni paginas ni ISBN
        assertEquals(0, b.pages)
        assertNull(b.isbn)
    }

    @Test fun el_genero_lleva_manga_por_delante_y_luego_lo_de_las_categorias() {
        val b = parseKitsuMangaResults(berserkJson, "berserk", genreMapper = fakeMapper)[0]
        // "Manga" siempre primero; Horror -> Terror via mapApiGenre. Maximo 2.
        assertTrue("genero real: ${b.genre}", b.genre.startsWith("Manga"))
        assertTrue("genero real: ${b.genre}", b.genre.contains("Terror"))
        assertTrue("no debe pasar de 2 generos: ${b.genre}", b.genre.split(";").size <= 2)
    }

    @Test fun descarta_la_basura_que_kitsu_devuelve_con_aplomo() {
        // Caso REAL: filter[text]="El ataque de los titanes" devuelve "Mato Seihei no
        // Slave". Kitsu no responde vacio cuando no conoce el titulo, responde otra
        // cosa. Sin el filtro de matchScore, esto contaminaria la busqueda.
        val basura = """
        {
          "data": [{
            "id": "99999",
            "type": "manga",
            "attributes": {
              "canonicalTitle": "Mato Seihei no Slave",
              "titles": { "en": "Mato Seihei no Slave" },
              "abbreviatedTitles": [],
              "subtype": "manga",
              "posterImage": { "original": "https://media.kitsu.app/manga/99999/poster_image/original-x.jpeg" }
            },
            "relationships": { "categories": { "data": [] }, "staff": { "data": [] } }
          }],
          "included": []
        }
        """.trimIndent()
        assertTrue(parseKitsuMangaResults(basura, "El ataque de los titanes").isEmpty())
    }

    @Test fun acepta_el_titulo_espanol_que_kitsu_si_conoce() {
        // Caso REAL contrario: filter[text]="Ataque a los Titanes" SI encuentra
        // Attack on Titan, porque Kitsu guarda el titulo es_es. Debe pasar el
        // matchScore (que mira TODOS los titulos) y mostrarse en espanol.
        val aot = """
        {
          "data": [{
            "id": "14916",
            "type": "manga",
            "attributes": {
              "canonicalTitle": "Attack on Titan",
              "titles": { "en": "Attack on Titan", "es_es": "Ataque a los Titanes", "ja_jp": "進撃の巨人" },
              "abbreviatedTitles": ["AOT", "SNK"],
              "startDate": "2009-09-09",
              "subtype": "manga",
              "posterImage": { "original": "https://media.kitsu.app/manga/14916/poster_image/original-x.jpeg" }
            },
            "relationships": { "categories": { "data": [] }, "staff": { "data": [] } }
          }],
          "included": []
        }
        """.trimIndent()
        val es = parseKitsuMangaResults(aot, "Ataque a los Titanes", preferredLang = "es")
        assertEquals(1, es.size)
        assertEquals("Ataque a los Titanes", es[0].title)
        // Con la app en ingles se muestra el canonico
        val en = parseKitsuMangaResults(aot, "Ataque a los Titanes", preferredLang = "en")
        assertEquals("Attack on Titan", en[0].title)
    }

    @Test fun no_cuela_una_serie_cuyo_alias_casa_pero_cuyo_titulo_visible_no() {
        // Caso REAL (payload de kitsu.app, 17-07-2026): buscar "Berserk" devuelve esta
        // serie porque uno de sus abbreviatedTitles es "Berserk Peerless Battle Spirit",
        // pero se mostraria como "Peerless Battle Spirit". El usuario no puede
        // relacionar eso con lo que ha buscado: es ruido.
        val alias = """
        {
          "data": [{
            "id": "54865",
            "type": "manga",
            "attributes": {
              "canonicalTitle": "Peerless Battle Spirit",
              "titles": { "en_us": "Peerless Battle Spirit", "zh_cn": "狂霸战皇" },
              "abbreviatedTitles": ["Berserk Peerless Battle Spirit", "Berserk Sovereign of Battle"],
              "subtype": "manga",
              "posterImage": { "original": "https://media.kitsu.app/manga/54865/poster_image/original-x.jpeg" }
            },
            "relationships": { "categories": { "data": [] }, "staff": { "data": [] } }
          }],
          "included": []
        }
        """.trimIndent()
        assertTrue(parseKitsuMangaResults(alias, "Berserk").isEmpty())
    }

    @Test fun no_cuela_un_libro_que_no_es_manga_por_un_alias_en_espanol() {
        // Caso REAL: buscar "El Imperio Final" (Sanderson, que de manga no tiene nada)
        // devuelve "Mekkoku no Kangan", porque uno de sus alias es "El eunuco del
        // Imperio" y comparte el token "imperio".
        val eunuco = """
        {
          "data": [{
            "id": "74443",
            "type": "manga",
            "attributes": {
              "canonicalTitle": "Mekkoku no Kangan",
              "titles": { "en": "Eunuch of Empire", "ja_jp": "滅国の宦官" },
              "abbreviatedTitles": ["El eunuco del Imperio"],
              "subtype": "manga",
              "posterImage": { "original": "https://media.kitsu.app/manga/74443/poster_image/original-x.jpeg" }
            },
            "relationships": { "categories": { "data": [] }, "staff": { "data": [] } }
          }],
          "included": []
        }
        """.trimIndent()
        assertTrue(parseKitsuMangaResults(eunuco, "El Imperio Final").isEmpty())
    }

    @Test fun un_alias_que_coincide_exacto_si_vale() {
        // Contrapunto de los dos anteriores: buscar por el titulo original japones
        // debe seguir encontrando la serie aunque se muestre con el titulo ingles.
        val snk = """
        {
          "data": [{
            "id": "14916",
            "type": "manga",
            "attributes": {
              "canonicalTitle": "Attack on Titan",
              "titles": { "en": "Attack on Titan", "en_jp": "Shingeki no Kyojin" },
              "abbreviatedTitles": ["AOT"],
              "subtype": "manga",
              "posterImage": { "original": "https://media.kitsu.app/manga/14916/poster_image/original-x.jpeg" }
            },
            "relationships": { "categories": { "data": [] }, "staff": { "data": [] } }
          }],
          "included": []
        }
        """.trimIndent()
        val r = parseKitsuMangaResults(snk, "Shingeki no Kyojin")
        assertEquals(1, r.size)
        assertEquals("Attack on Titan", r[0].title)
    }

    @Test fun language_queda_vacio_aunque_el_titulo_sea_espanol() {
        // Es una ficha de SERIE sin ISBN ni paginas: marcarla "es" la colaria por
        // encima de ediciones espanolas reales en el comparator de la busqueda.
        val b = parseKitsuMangaResults(berserkJson, "berserk", preferredLang = "es", genreMapper = fakeMapper)[0]
        assertEquals("", b.language)
    }

    @Test fun una_novela_ligera_no_se_etiqueta_como_manga() {
        // Caso REAL: filter[text]=naruto devuelve "Naruto Ninden Series", subtype=novel.
        val novela = """
        {
          "data": [{
            "id": "20055",
            "type": "manga",
            "attributes": {
              "canonicalTitle": "Naruto Ninden Series",
              "titles": { "en": "Naruto Ninden Series" },
              "abbreviatedTitles": [],
              "subtype": "novel",
              "posterImage": { "original": "https://media.kitsu.app/manga/20055/poster_image/original-x.jpeg" }
            },
            "relationships": { "categories": { "data": [] }, "staff": { "data": [] } }
          }],
          "included": []
        }
        """.trimIndent()
        val r = parseKitsuMangaResults(novela, "naruto")
        assertEquals(1, r.size)
        assertFalse("una novela no es manga: ${r[0].genre}", r[0].genre.contains("Manga"))
    }

    @Test fun sin_staff_el_autor_queda_vacio_y_no_revienta() {
        // Caso REAL: Chainsaw Man no tiene staff en Kitsu (count=0), pese a ser de
        // Fujimoto. Debe salir igual, solo que sin autor.
        val sinAutor = """
        {
          "data": [{
            "id": "54139",
            "type": "manga",
            "attributes": {
              "canonicalTitle": "Chainsaw Man",
              "titles": { "en": "Chainsaw Man" },
              "abbreviatedTitles": ["CSM"],
              "subtype": "manga",
              "posterImage": { "original": "https://media.kitsu.app/manga/54139/poster_image/original-x.jpeg" }
            },
            "relationships": { "categories": { "data": [] }, "staff": { "data": [] } }
          }],
          "included": []
        }
        """.trimIndent()
        val r = parseKitsuMangaResults(sinAutor, "chainsaw man")
        assertEquals(1, r.size)
        assertEquals("", r[0].author)
        assertEquals("Chainsaw Man", r[0].title)
    }

    @Test fun sin_portada_no_inventa_url() {
        val sinPortada = """
        {
          "data": [{
            "id": "1",
            "type": "manga",
            "attributes": {
              "canonicalTitle": "Berserk",
              "titles": { "en": "Berserk" },
              "abbreviatedTitles": [],
              "subtype": "manga"
            },
            "relationships": { "categories": { "data": [] }, "staff": { "data": [] } }
          }],
          "included": []
        }
        """.trimIndent()
        assertNull(parseKitsuMangaResults(sinPortada, "berserk")[0].coverUrl)
    }

    @Test fun degrada_sin_romperse_ante_respuestas_invalidas() {
        // Si Kitsu cae o cambia el formato, la busqueda no puede reventar.
        assertTrue(parseKitsuMangaResults("", "berserk").isEmpty())
        assertTrue(parseKitsuMangaResults("no soy json", "berserk").isEmpty())
        assertTrue(parseKitsuMangaResults("{}", "berserk").isEmpty())
        assertTrue(parseKitsuMangaResults("""{"data":[]}""", "berserk").isEmpty())
        assertTrue(parseKitsuMangaResults("""{"errors":[{"status":"500"}]}""", "berserk").isEmpty())
        // data con entradas rotas (sin id, sin attributes, sin titulos)
        assertTrue(parseKitsuMangaResults("""{"data":[{},{"id":"1"},{"id":"2","attributes":{}}]}""", "berserk").isEmpty())
    }

    @Test fun las_claves_manga_quedan_exentas_del_filtro_de_relevancia() {
        assertTrue(isMangaSourceKey("kt_54139"))
        assertTrue(isMangaSourceKey("md_abc-123"))
        assertFalse(isMangaSourceKey("/works/OL123W"))
        assertFalse(isMangaSourceKey("gb_xyz"))
    }
}
