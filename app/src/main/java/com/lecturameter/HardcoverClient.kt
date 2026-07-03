package com.lecturameter

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente para la API GraphQL de Hardcover (hardcover.app).
 * Usa HttpURLConnection nativo — sin dependencias extra.
 *
 * La API es pública y no requiere token para búsquedas básicas.
 * Endpoint: https://api.hardcover.app/v1/graphql
 *
 * Integrado en fetchEditionsForBook como Fase B (complemento a Google Books).
 * Sus resultados se incluyen automáticamente en el EditionCache.
 */
object HardcoverClient {

    private const val BASE_URL = "https://api.hardcover.app/v1/graphql"
    private const val TIMEOUT_MS = 10_000

    /**
     * Busca ediciones de un libro en Hardcover por ISBN o título.
     * Devuelve lista de EditionResult compatible con el resto del flujo.
     */
    suspend fun searchEditions(title: String, isbn: String? = null): List<EditionResult> =
        withContext(Dispatchers.IO) {
            try {
                val query = buildGraphQLQuery(title, isbn)
                val conn = (URL(BASE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MS
                    readTimeout    = TIMEOUT_MS
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    outputStream.use { it.write(query.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                if (code != 200) { conn.disconnect(); return@withContext emptyList() }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                parseResponse(body)
            } catch (_: Exception) {
                emptyList()
            }
        }

    // ── GraphQL query ────────────────────────────────────────────────────────

    private fun buildGraphQLQuery(title: String, isbn: String?): String {
        // Escapar el valor para que sea seguro dentro de una cadena GraphQL
        val rawSearch = if (!isbn.isNullOrBlank()) isbn else title
        val escapedSearch = rawSearch
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", "")
            .take(200) // límite razonable

        val gql = """
            query {
              books(where: {_or: [
                {isbn_13: {_eq: "$escapedSearch"}},
                {isbn_10: {_eq: "$escapedSearch"}},
                {title:   {_ilike: "%$escapedSearch%"}}
              ]}, limit: 10) {
                title
                contributions { author { name } }
                image { url }
                pages
                release_year
                default_physical_edition {
                  isbn_13
                  isbn_10
                  publisher { name }
                  language { language }
                }
              }
            }
        """.trimIndent()

        return """{"query":${escapeJsonString(gql)}}"""
    }

    /** Escapa el query GQL para incrustarlo como string JSON. */
    private fun escapeJsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '"'  -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    // ── Parseo de respuesta ──────────────────────────────────────────────────

    private fun parseResponse(json: String): List<EditionResult> {
        val results = mutableListOf<EditionResult>()
        try {
            val root  = JsonParser.parseString(json).asJsonObject
            val data  = root.getAsJsonObject("data") ?: return emptyList()
            val books = data.getAsJsonArray("books") ?: return emptyList()

            for (element in books) {
                val book = element.asJsonObject ?: continue
                val title = book.get("title")?.asString?.takeIf { it.isNotBlank() } ?: continue

                // Autor
                val author = book.getAsJsonArray("contributions")
                    ?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("author")
                    ?.get("name")?.asString ?: ""

                // Portada
                val coverUrl = book.getAsJsonObject("image")?.get("url")?.asString

                // Páginas
                val pages = book.get("pages")?.asInt ?: 0

                // Año
                val year = book.get("release_year")?.asInt?.toString() ?: ""

                // Edición física (isbn, idioma, editorial)
                val physEd = book.getAsJsonObject("default_physical_edition")
                val isbn13 = physEd?.get("isbn_13")?.asString
                val isbn10 = physEd?.get("isbn_10")?.asString
                val isbn   = cleanIsbn(isbn13) ?: cleanIsbn(isbn10)
                val publisher = physEd?.getAsJsonObject("publisher")?.get("name")?.asString ?: ""
                val langCode  = physEd?.getAsJsonObject("language")?.get("language")?.asString ?: "en"

                // Mapear idioma a los campos que usa EditionResult
                val (language, languageLabel, flag) = editionLanguageMetaFromCode(langCode, isbn, publisher)

                results.add(
                    EditionResult(
                        language      = language,
                        languageLabel = languageLabel,
                        flag          = flag,
                        title         = title,
                        pages         = pages,
                        coverUrl      = coverUrl,
                        isbn          = isbn,
                        publisher     = publisher,
                        publishYear   = year
                    )
                )
            }
        } catch (_: Exception) { /* mal JSON o schema inesperado → lista vacía */ }
        return results
    }

    /**
     * Mapea el código de idioma de Hardcover (ej. "Spanish", "English")
     * a los campos language/languageLabel/flag que usa EditionResult.
     * Hardcover devuelve el nombre completo del idioma en inglés.
     */
    private fun editionLanguageMetaFromCode(
        langName: String,
        isbn: String?,
        publisher: String
    ): Triple<String, String, String> {
        val lc = langName.lowercase().trim()
        return when {
            lc.contains("spanish") || lc == "es" -> Triple("es", "Español", "🇪🇸")
            // Bug fix v21.15: el resto de la app usa "original" como código interno para
            // inglés (ver editionLanguageMeta/gbLang en MainActivity.kt), no "en". Con "en"
            // las ediciones inglesas de Hardcover quedaban fuera de los filtros/orden que
            // comprueban `language == "original"` (sorting, columna idioma, etc.).
            lc.contains("english") || lc == "en"  -> Triple("original", "English", "🌐")
            lc.contains("french")  || lc == "fr"  -> Triple("fr", "Français", "🇫🇷")
            lc.contains("german")  || lc == "de"  -> Triple("de", "Deutsch", "🇩🇪")
            lc.contains("italian") || lc == "it"  -> Triple("it", "Italiano", "🇮🇹")
            lc.contains("portuguese") || lc == "pt" -> Triple("pt", "Português", "🇵🇹")
            lc.contains("japanese") || lc == "ja" -> Triple("ja", "日本語", "🇯🇵")
            lc.contains("chinese") || lc == "zh"  -> Triple("zh", "中文", "🇨🇳")
            lc.contains("catalan") || lc == "ca"  -> Triple("ca", "Català (CAT)", "🇪🇸")
            lc.contains("basque")  || lc == "eu"  -> Triple("eu", "Euskara (EUS)", "🇪🇸")
            lc.contains("galician")|| lc == "gl"  -> Triple("gl", "Galego (GAL)", "🇪🇸")
            else -> {
                // Fallback: inferir por ISBN si no reconocemos el idioma
                val isSpanishIsbn = isbn?.startsWith("978-84") == true || isbn?.startsWith("9788") == true
                    || isbn?.startsWith("97884") == true
                if (isSpanishIsbn) Triple("es", "Español", "🇪🇸")
                else Triple(lc.take(2).ifBlank { "??" }, langName.take(20), "🌐")
            }
        }
    }
}
