package com.lecturameter.utils

// ── Fase 5: Bingo con plantillas rotativas (MD5) ──────────────────────────────
// Carga las plantillas de assets/bingo_templates.json, crea cartones mensuales
// y evalúa las condiciones de las celdas. Sin estado propio: el cartón vive en
// BooksViewModel y se persiste vía BingoRepository (prefs "bingo_card").

import android.content.Context
import com.google.gson.Gson
import com.lecturameter.model.BingoCard
import com.lecturameter.model.BingoCell
import com.lecturameter.model.Book
import com.lecturameter.model.BookStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BingoTemplateCell(
    val type: String = "",
    val value: String = "",
    val es: String = "",
    val en: String = ""
)

data class BingoTemplate(
    val id: String = "",
    val es: String = "",
    val en: String = "",
    val cells: List<BingoTemplateCell> = emptyList()
)

private data class BingoTemplateFile(val templates: List<BingoTemplate> = emptyList())

object BingoManager {
    private val gson = Gson()
    private var cachedTemplates: List<BingoTemplate>? = null

    // Líneas del cartón 3×3 (índices 0..8): filas, columnas y diagonales
    private val LINES = mapOf(
        "row0" to listOf(0, 1, 2), "row1" to listOf(3, 4, 5), "row2" to listOf(6, 7, 8),
        "col0" to listOf(0, 3, 6), "col1" to listOf(1, 4, 7), "col2" to listOf(2, 5, 8),
        "diag0" to listOf(0, 4, 8), "diag1" to listOf(2, 4, 6)
    )

    fun loadTemplates(context: Context): List<BingoTemplate> {
        cachedTemplates?.let { return it }
        return try {
            val json = context.assets.open("bingo_templates.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsed = gson.fromJson(json, BingoTemplateFile::class.java)?.templates
                ?.filter { it.cells.size == 9 } ?: emptyList()
            cachedTemplates = parsed
            parsed
        } catch (_: Exception) { emptyList() }
    }

    fun currentMonthKey(): String =
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    fun newCard(template: BingoTemplate, monthKey: String): BingoCard = BingoCard(
        templateId = template.id,
        templateNameEs = template.es,
        templateNameEn = template.en,
        monthKey = monthKey,
        cells = template.cells.map {
            BingoCell(conditionType = it.type, conditionValue = it.value, labelEs = it.es, labelEn = it.en)
        }
    )

    /** Evalúa las celdas de libro (genre/pages/rating/author_new/saga) contra [book].
     *  Se llama al pasar a FINISHED y también al valorar un libro ya terminado
     *  (la valoración suele llegar después de terminar). Devuelve el mismo cartón
     *  si nada cambió. */
    fun evaluateBookFinished(card: BingoCard, book: Book, allBooks: List<Book>): BingoCard {
        var changed = false
        val cells = card.cells.map { cell ->
            if (cell.isCompleted) return@map cell
            val ok = when (cell.conditionType) {
                "genre"      -> book.genres.any { it.equals(cell.conditionValue, ignoreCase = true) }
                "pages_gt"   -> book.pages > (cell.conditionValue.toIntOrNull() ?: Int.MAX_VALUE)
                "pages_lt"   -> book.pages in 1 until (cell.conditionValue.toIntOrNull() ?: 0)
                "rating"     -> book.rating >= (cell.conditionValue.toIntOrNull() ?: Int.MAX_VALUE)
                "author_new" -> isNewAuthor(book, allBooks)
                "saga"       -> looksLikeSaga(book)
                else         -> false
            }
            if (ok) { changed = true; cell.copy(isCompleted = true, completedByBookId = book.id) } else cell
        }
        if (!changed) return card
        return card.copy(cells = cells, completedLines = computeLines(cells))
    }

    /** Evalúa las celdas de racha contra la racha actual (días consecutivos con sesión). */
    fun evaluateStreak(card: BingoCard, streakDays: Int): BingoCard {
        var changed = false
        val cells = card.cells.map { cell ->
            if (cell.isCompleted || cell.conditionType != "streak") return@map cell
            if (streakDays >= (cell.conditionValue.toIntOrNull() ?: Int.MAX_VALUE)) {
                changed = true; cell.copy(isCompleted = true)
            } else cell
        }
        if (!changed) return card
        return card.copy(cells = cells, completedLines = computeLines(cells))
    }

    fun isCardComplete(card: BingoCard): Boolean = card.cells.all { it.isCompleted }

    private fun computeLines(cells: List<BingoCell>): List<String> =
        LINES.filter { (_, idx) -> idx.all { cells[it].isCompleted } }.keys.toList()

    /** Primer libro TERMINADO de este autor (contándose a sí mismo). */
    private fun isNewAuthor(book: Book, allBooks: List<Book>): Boolean =
        book.author.isNotBlank() && allBooks.count {
            it.author.equals(book.author, ignoreCase = true) &&
                (it.status == BookStatus.FINISHED || it.status == BookStatus.REREADING)
        } <= 1

    /** El modelo no tiene campo saga: heurística sobre el título. Cubre el formato
     *  Goodreads "Título (Saga, #2)" y variantes habituales "#N", "Vol. N", "Libro N". */
    private val SAGA_REGEX = Regex(
        """#\s*\d+|\bvol(\.|umen|ume)?\s*\d+|\blibro\s+\d+\b|\bbook\s+\d+\b|\(.+,\s*#?\d+\)""",
        RegexOption.IGNORE_CASE
    )
    fun looksLikeSaga(book: Book): Boolean = SAGA_REGEX.containsMatchIn(book.title)
}
