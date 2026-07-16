package com.lecturameter.utils

// ── B-029 opción B: portadas base64 FUERA de SharedPreferences ────────────────
//
// La clave "books" de prefs llegaba a 6,7 MB porque las portadas legacy de la 2.7
// viajan como data URIs base64 dentro de coverUrl. Cada save() serializa ese JSON
// en el hilo principal (síncrono a propósito, ver BooksViewModel.save y B-009):
// el arreglo de fondo es sacar los bytes a ficheros en filesDir/covers y dejar en
// coverUrl la ruta absoluta, que UI (BookCover), widget (loadCoverBitmap) y backup
// (embedLocalCoverUrl re-embebe al exportar) ya entienden. El formato del backup
// NO cambia: al exportar, las rutas locales vuelven a ser data URIs (compat 2.7/F9).
//
// La extracción usa java.util.Base64 (minSdk 26) para que la lógica sea testeable
// en JVM sin Android (regla de esta máquina: JUnitCore directo, sin robolectric).

import com.lecturameter.model.Book
import java.io.File

object CoverStore {

    /** Cap de payload como en BackupRepository (base64 de ~5 MB ≈ 6,67 M chars). */
    private const val MAX_B64_CHARS = 6_700_000

    private val ALLOWED_MIMES = listOf("image/jpeg", "image/png", "image/webp")

    /**
     * Decodifica un data URI de imagen con las mismas guardas que la restauración
     * de backups: MIME explícito permitido, tamaño acotado y magic bytes reales
     * (JPEG, PNG o WebP). Devuelve null si algo no cuadra.
     */
    fun decodeCoverDataUri(url: String?): ByteArray? {
        if (url == null || !url.startsWith("data:image")) return null
        return try {
            val mime = url.substringAfter("data:").substringBefore(";")
            if (mime !in ALLOWED_MIMES) return null
            val b64 = url.substringAfter("base64,", missingDelimiterValue = "")
            if (b64.isEmpty() || b64.length > MAX_B64_CHARS) return null
            val bytes = java.util.Base64.getMimeDecoder().decode(b64)
            if (isValidCoverImage(bytes)) bytes else null
        } catch (_: Exception) { null }
    }

    /** Magic bytes: JPEG (FF D8 FF), PNG (89 50 4E 47) o WebP (RIFF....WEBP). */
    fun isValidCoverImage(bytes: ByteArray): Boolean = when {
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> true                                  // JPEG
        bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> true     // PNG
        bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() -> true // WebP
        else -> false
    }

    /** true si el libro (o alguna de sus ediciones) lleva una portada base64 embebida. */
    fun hasEmbeddedCover(book: Book): Boolean =
        book.coverUrl?.startsWith("data:image") == true ||
            book.editions.any { it.coverUrl?.startsWith("data:image") == true }

    fun coverFileName(bookId: Long): String = "$bookId.jpg"
    fun editionCoverFileName(bookId: Long, editionId: Long): String = "${bookId}_$editionId.jpg"

    /**
     * Extrae a [coversDir] todas las portadas base64 de [books] (libro y ediciones).
     * Devuelve un mapa nombreDeFichero → ruta absoluta con SOLO las que se escribieron
     * bien; las inválidas se saltan (se quedan como estaban en prefs, sin romper nada).
     *
     * Solo I/O de ficheros: el intercambio de URLs lo hace el llamador sobre la lista
     * viva (así una extracción en background no pisa cambios del usuario).
     */
    fun extractEmbeddedCovers(coversDir: File, books: List<Book>): Map<String, String> {
        val written = mutableMapOf<String, String>()
        if (!coversDir.exists() && !coversDir.mkdirs()) return written
        for (book in books) {
            decodeCoverDataUri(book.coverUrl)?.let { bytes ->
                val name = coverFileName(book.id)
                try {
                    val dest = File(coversDir, name)
                    dest.writeBytes(bytes)
                    written[name] = dest.absolutePath
                } catch (_: Exception) { /* disco lleno u otro fallo: se queda en prefs */ }
            }
            for (ed in book.editions) {
                decodeCoverDataUri(ed.coverUrl)?.let { bytes ->
                    val name = editionCoverFileName(book.id, ed.id)
                    try {
                        val dest = File(coversDir, name)
                        dest.writeBytes(bytes)
                        written[name] = dest.absolutePath
                    } catch (_: Exception) { }
                }
            }
        }
        return written
    }

    /**
     * Sustituye en [books] cada portada base64 por su ruta extraída (si está en
     * [extracted]). Devuelve la lista nueva y true si cambió algo. Las portadas
     * cuyo fichero no llegó a escribirse se conservan tal cual.
     */
    fun applyExtractedPaths(books: List<Book>, extracted: Map<String, String>): Pair<List<Book>, Boolean> {
        if (extracted.isEmpty()) return books to false
        var changed = false
        val result = books.map { book ->
            val newCover = if (book.coverUrl?.startsWith("data:image") == true)
                extracted[coverFileName(book.id)] ?: book.coverUrl
            else book.coverUrl
            val newEditions = book.editions.map { ed ->
                if (ed.coverUrl?.startsWith("data:image") == true) {
                    val path = extracted[editionCoverFileName(book.id, ed.id)]
                    if (path != null) { changed = true; ed.copy(coverUrl = path) } else ed
                } else ed
            }
            if (newCover !== book.coverUrl || newEditions != book.editions) {
                if (newCover !== book.coverUrl) changed = true
                book.copy(coverUrl = newCover, editions = newEditions)
            } else book
        }
        return result to changed
    }
}
