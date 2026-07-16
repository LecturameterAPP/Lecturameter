package com.lecturameter

// B-029 opción B: portadas base64 fuera de SharedPreferences.
// Estos tests fijan (1) que solo se decodifican data URIs con MIME/magic bytes
// válidos (misma guarda que BackupRepository al restaurar), (2) que la extracción
// a fichero deja intactos los libros sin portada embebida y (3) que el intercambio
// de rutas en applyExtractedPaths solo toca lo que se extrajo de verdad, sin pisar
// portadas ya locales o remotas.

import com.lecturameter.model.Book
import com.lecturameter.model.BookEdition
import com.lecturameter.model.BookStatus
import com.lecturameter.utils.CoverStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CoverStoreTest {

    // 1x1 PNG válido (magic bytes 89 50 4E 47) en base64.
    private val validPngB64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    private val validPngDataUri = "data:image/png;base64,$validPngB64"

    private fun book(id: Long, coverUrl: String?, editions: List<BookEdition> = emptyList()) = Book(
        id = id,
        title = "t",
        author = "a",
        pages = 100,
        startDate = null,
        endDate = null,
        status = BookStatus.PENDING,
        coverUrl = coverUrl,
        editions = editions
    )

    @Test
    fun `decodeCoverDataUri acepta PNG valido`() {
        val bytes = CoverStore.decodeCoverDataUri(validPngDataUri)
        assertNotNull(bytes)
        assertTrue(CoverStore.isValidCoverImage(bytes!!))
    }

    @Test
    fun `decodeCoverDataUri rechaza mime no permitido`() {
        assertNull(CoverStore.decodeCoverDataUri("data:image/gif;base64,$validPngB64"))
    }

    @Test
    fun `decodeCoverDataUri rechaza magic bytes que no casan con el mime`() {
        // Declara PNG pero el payload no tiene los magic bytes de PNG.
        val fakeB64 = java.util.Base64.getEncoder().encodeToString("no soy una imagen".toByteArray())
        assertNull(CoverStore.decodeCoverDataUri("data:image/png;base64,$fakeB64"))
    }

    @Test
    fun `decodeCoverDataUri rechaza payload por encima del limite`() {
        val huge = "A".repeat(6_700_001)
        assertNull(CoverStore.decodeCoverDataUri("data:image/png;base64,$huge"))
    }

    @Test
    fun `decodeCoverDataUri devuelve null si no es data uri`() {
        assertNull(CoverStore.decodeCoverDataUri("https://example.com/cover.jpg"))
        assertNull(CoverStore.decodeCoverDataUri("/data/user/0/com.lecturameter/files/covers/1.jpg"))
        assertNull(CoverStore.decodeCoverDataUri(null))
    }

    @Test
    fun `hasEmbeddedCover detecta portada de libro y de edicion`() {
        assertTrue(CoverStore.hasEmbeddedCover(book(1, validPngDataUri)))
        assertTrue(CoverStore.hasEmbeddedCover(
            book(2, null, listOf(BookEdition(id = 9, coverUrl = validPngDataUri)))
        ))
        assertFalse(CoverStore.hasEmbeddedCover(book(3, "https://example.com/x.jpg")))
        assertFalse(CoverStore.hasEmbeddedCover(book(4, "/files/covers/4.jpg")))
    }

    @Test
    fun `extractEmbeddedCovers escribe fichero y applyExtractedPaths cambia coverUrl a la ruta`() {
        val tmpDir = Files.createTempDirectory("covers-test").toFile()
        try {
            val coversDir = File(tmpDir, "covers")
            val libro = book(42, validPngDataUri)
            val extracted = CoverStore.extractEmbeddedCovers(coversDir, listOf(libro))

            assertEquals(1, extracted.size)
            val name = CoverStore.coverFileName(42)
            assertTrue(extracted.containsKey(name))
            val writtenFile = File(extracted.getValue(name))
            assertTrue(writtenFile.exists())
            assertTrue(CoverStore.isValidCoverImage(writtenFile.readBytes()))

            val (migrated, changed) = CoverStore.applyExtractedPaths(listOf(libro), extracted)
            assertTrue(changed)
            assertEquals(writtenFile.absolutePath, migrated[0].coverUrl)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `extractEmbeddedCovers tambien migra la portada de una edicion`() {
        val tmpDir = Files.createTempDirectory("covers-test-ed").toFile()
        try {
            val coversDir = File(tmpDir, "covers")
            val edicion = BookEdition(id = 7, coverUrl = validPngDataUri)
            val libro = book(10, null, listOf(edicion))
            val extracted = CoverStore.extractEmbeddedCovers(coversDir, listOf(libro))

            val name = CoverStore.editionCoverFileName(10, 7)
            assertTrue(extracted.containsKey(name))

            val (migrated, changed) = CoverStore.applyExtractedPaths(listOf(libro), extracted)
            assertTrue(changed)
            assertEquals(extracted.getValue(name), migrated[0].editions[0].coverUrl)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `applyExtractedPaths no toca libros sin portada embebida`() {
        val remoto = book(1, "https://example.com/x.jpg")
        val local = book(2, "/files/covers/2.jpg")
        val (result, changed) = CoverStore.applyExtractedPaths(
            listOf(remoto, local),
            mapOf(CoverStore.coverFileName(1) to "/no/deberia/usarse.jpg")
        )
        assertFalse(changed)
        assertEquals("https://example.com/x.jpg", result[0].coverUrl)
        assertEquals("/files/covers/2.jpg", result[1].coverUrl)
    }

    @Test
    fun `extractEmbeddedCovers no escribe nada si no hay portadas embebidas`() {
        val tmpDir = Files.createTempDirectory("covers-test-empty").toFile()
        try {
            val coversDir = File(tmpDir, "covers")
            val libros = listOf(book(1, "https://example.com/x.jpg"), book(2, null))
            val extracted = CoverStore.extractEmbeddedCovers(coversDir, libros)
            assertTrue(extracted.isEmpty())
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
