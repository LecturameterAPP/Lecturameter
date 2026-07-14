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

// Fase 6.3/6.4: resumen de un cartón retirado (uno por cartón; un mes puede tener
// dos si se completó y se pidió cartón nuevo). Campos default para Gson.
data class BingoMonthSummary(
    val monthKey: String = "",
    val templateNameEs: String = "",
    val templateNameEn: String = "",
    val cellsDone: Int = 0,
    val cellsTotal: Int = 0,
    val lines: Int = 0,
    val complete: Boolean = false,
    val pattern: String = "",                       // '1'/'0' por celda (cartón visual del Wrapped)
    val uncompletedEs: List<String> = emptyList(),  // etiquetas no completadas ("casilla más difícil")
    val uncompletedEn: List<String> = emptyList()
)

object BingoManager {
    private val gson = Gson()
    private var cachedTemplates: List<BingoTemplate>? = null

    // Feedback 13-07 (10): el cartón ya no es fijo 3×3 — el lado se deduce del nº de
    // celdas (9/16/25) y las líneas (filas, columnas, diagonales) se generan según el lado.
    fun sideOf(cellCount: Int): Int {
        val s = Math.round(Math.sqrt(cellCount.toDouble())).toInt()
        return if (s * s == cellCount) s else 0
    }

    private fun linesFor(side: Int): Map<String, List<Int>> {
        val lines = mutableMapOf<String, List<Int>>()
        for (r in 0 until side) lines["row$r"] = (0 until side).map { r * side + it }
        for (c in 0 until side) lines["col$c"] = (0 until side).map { it * side + c }
        lines["diag0"] = (0 until side).map { it * side + it }
        lines["diag1"] = (0 until side).map { it * side + (side - 1 - it) }
        return lines
    }

    /** Fase 4 (D-003, animación de línea): índices de las celdas de una línea ("row0".."diag1"). */
    fun lineIndices(side: Int, lineKey: String): List<Int> = linesFor(side)[lineKey] ?: emptyList()

    // ── Fase 6.3/6.4: historial mensual del Bingo ─────────────────────────────
    // Al retirar un cartón (rotación del día 1 o "cartón nuevo" tras completar) se
    // guarda un resumen del mes. Lo consumen la tarjeta anual del Wrapped y el
    // recap mensual. Entra en el backup al vivir en las prefs normales.
    private const val HISTORY_KEY = "bingo_month_history"

    fun loadMonthSummaries(prefs: android.content.SharedPreferences): List<BingoMonthSummary> {
        return try {
            val json = prefs.getString(HISTORY_KEY, null) ?: return emptyList()
            gson.fromJson(json, Array<BingoMonthSummary>::class.java)?.toList() ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun appendMonthSummary(prefs: android.content.SharedPreferences, card: BingoCard) {
        val done = card.cells.count { it.isCompleted }
        // Cartones sin ningún progreso (incluidos los regenerados por migración) no cuentan
        if (done == 0) return
        val summary = BingoMonthSummary(
            monthKey = card.monthKey,
            templateNameEs = card.templateNameEs,
            templateNameEn = card.templateNameEn,
            cellsDone = done,
            cellsTotal = card.cells.size,
            lines = card.completedLines.size,
            complete = done == card.cells.size,
            pattern = card.cells.joinToString("") { if (it.isCompleted) "1" else "0" },
            uncompletedEs = card.cells.filter { !it.isCompleted }.map { it.labelEs },
            uncompletedEn = card.cells.filter { !it.isCompleted }.map { it.labelEn }
        )
        val list = loadMonthSummaries(prefs) + summary
        prefs.edit().putString(HISTORY_KEY, gson.toJson(list)).apply()
    }

    fun loadTemplates(context: Context): List<BingoTemplate> {
        cachedTemplates?.let { return it }
        return try {
            val json = context.assets.open("bingo_templates.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsed = gson.fromJson(json, BingoTemplateFile::class.java)?.templates
                ?.filter { sideOf(it.cells.size) >= 3 } ?: emptyList()
            cachedTemplates = parsed
            parsed
        } catch (_: Exception) { emptyList() }
    }

    fun currentMonthKey(): String =
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    // Fase 5 (Recap semanal): fecha de completado de las celdas
    private fun todayIso(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

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
            if (ok) { changed = true; cell.copy(isCompleted = true, completedByBookId = book.id, completedAt = todayIso()) } else cell
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
                changed = true; cell.copy(isCompleted = true, completedAt = todayIso())
            } else cell
        }
        if (!changed) return card
        return card.copy(cells = cells, completedLines = computeLines(cells))
    }

    fun isCardComplete(card: BingoCard): Boolean = card.cells.all { it.isCompleted }

    private fun computeLines(cells: List<BingoCell>): List<String> {
        val side = sideOf(cells.size)
        if (side < 3) return emptyList()
        return linesFor(side).filter { (_, idx) -> idx.all { cells[it].isCompleted } }.keys.toList()
    }

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
