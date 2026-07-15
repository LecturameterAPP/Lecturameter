package com.lecturameter.model

// ── Modelo ────────────────────────────────────────────────────────────────────
// Fase 1.1: extraído de MainActivity.kt sin cambios funcionales.

enum class BookStatus { PENDING, READING, FINISHED, DROPPED, REREADING }

// v19.8: eventos de fecha para soportar múltiples ciclos de abandono/retomado/relectura.
// type: "start" | "end" | "drop" | "resume" | "reread" | "reread_end"
// occurrence: 1 = primer evento de ese tipo, 2 = segundo, etc. (para drop/resume/reread/reread_end)
//             start/end siempre tienen occurrence = 1
data class DateEvent(
    val type: String,
    val date: String,           // yyyy-MM-dd
    val occurrence: Int = 1
)

// ── Bingo (Fase 5, MD5) ────────────────────────────────────────────────────────
// Cartón 3×3 con plantillas rotativas mensuales (assets/bingo_templates.json).
// Las celdas se marcan solas: genre/pages/rating/author_new/saga al terminar (o
// valorar) un libro; streak al registrar sesión. completedLines: "row0".."diag1".
data class BingoCell(
    val conditionType: String,          // genre | pages_gt | pages_lt | rating | streak | author_new | saga
    val conditionValue: String,         // género canónico ES, número, o "" según el tipo
    val labelEs: String,
    val labelEn: String,
    val isCompleted: Boolean = false,
    val completedByBookId: Long? = null, // null en celdas streak (no las completa un libro)
    // Fase 5 (Recap semanal): fecha de completado yyyy-MM-dd. Null en celdas
    // marcadas antes de añadir el campo (Gson tolera la ausencia).
    val completedAt: String? = null
)

data class BingoCard(
    val templateId: String,
    val templateNameEs: String,
    val templateNameEn: String,
    val monthKey: String,               // yyyy-MM del cartón; si != mes actual, rota
    val cells: List<BingoCell>,
    val completedLines: List<String> = emptyList()
)

// ── Edición de un libro ────────────────────────────────────────────────────────
// Representa una edición concreta (española, original, etc.) del mismo libro.
// Un Book puede tener 1 o más ediciones. Las sesiones siguen perteneciendo al
// Book (por bookId) y se distribuyen entre ediciones mediante editionId.
// Si editionId es null en una ReadingSession, pertenece a la edición activa.
data class BookEdition(
    val id: Long = System.currentTimeMillis(),
    val language: String = "unknown",          // "es" | "original" | código ISO
    val languageLabel: String = "Edición principal",// etiqueta visible: "Español", "English", etc.
    val flag: String = "🌐",             // emoji de bandera o 🌐
    val title: String = "",               // título en este idioma (puede diferir)
    val pages: Int = 0,
    val coverUrl: String? = null,
    val isbn: String? = null,
    val publisher: String = "",
    val publishYear: String = "",
    val noCoverFound: Boolean = false,
    val isActive: Boolean = true,          // edición cuya portada se muestra en la biblioteca
    val comment: String = ""              // comentario específico de esta edición/idioma
)

data class Book(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val author: String,
    val pages: Int,                       // páginas de la edición activa (cache para la UI de lista)
    val startDate: String?,
    val endDate: String?,
    val status: BookStatus,
    val rating: Int = 0,
    val coverUrl: String? = null,         // portada de la edición activa (cache para la UI de lista)
    val isbn: String? = null,             // ISBN de la edición activa (cache)
    val comment: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val genre: String = "",          // mantenido para migración JSON → leer via sanitizeBook
    val genres: List<String> = emptyList(), // campo activo; sustituye a genre
    val importedFromGoodreads: Boolean = false,
    val isRereading: Boolean = false,
    val noCoverFound: Boolean = false,
    val editions: List<BookEdition> = emptyList(), // vacío = edición única implícita (migración hacia atrás)
    val firstFunctionalPage: Int? = null,  // primera página real del contenido (v13)
    val lastFunctionalPage: Int? = null,   // última página real del contenido (v13)
    val dropDate: String? = null,          // fecha de abandono (DROPPED), automática al cambiar de estado (v17.10) — legacy, ahora cache del primer drop
    val resumedDate: String? = null,       // fecha de retomado (DROPPED→READING), automática (v17.10) — legacy, ahora cache del primer resume
    val dateEvents: List<DateEvent> = emptyList(),  // v19.8: lista completa de eventos cronológicos (start/end/drop×N/resume×N/reread×N/reread_end×N)
    val isFavorite: Boolean = false                 // v2.4 rework: favorito (swipe en la tarjeta). Gson asigna false a libros antiguos.
)

data class ReadingSession(
    val id: Long = System.currentTimeMillis(),
    val bookId: Long,
    val date: String,          // yyyy-MM-dd
    val pages: Int,
    val minutes: Int? = null,
    val note: String = "",
    val editionId: Long? = null,  // null = edición activa en el momento de la sesión
    val startPage: Int? = null,   // página de inicio de la sesión (v13)
    val endPage: Int? = null,     // página final de la sesión (v13)
    val readingIndex: Int? = 0,   // v19.8: 0 = lectura original, 1 = primera relectura, etc. Nullable para compatibilidad Gson con JSONs viejos
    val startTimestamp: Long? = null  // v2.4 rework: epoch ms de inicio de sesión (solo sesiones nuevas del día actual) → heatmap horario
)

private var editionIdCounter = System.currentTimeMillis()
fun newEditionId(): Long {
    // Garantiza unicidad incluso si se crean múltiples ediciones en el mismo milisegundo
    val t = System.currentTimeMillis()
    editionIdCounter = if (t > editionIdCounter) t else editionIdCounter + 1
    return editionIdCounter
}

// ── DateEvent helpers (v19.8) ──────────────────────────────────────────────────

/** Devuelve la siguiente occurrence para un tipo de evento dado. */
fun nextOccurrence(events: List<DateEvent>, type: String): Int =
    (events.filter { it.type == type }.maxOfOrNull { it.occurrence } ?: 0) + 1

/**
 * v20.0 (G6 fix): renumera las `occurrence` de los tipos drop/resume/reread/reread_end
 * para garantizar que sean 1..N consecutivas según orden cronológico real.
 * Cualquier hueco o duplicado por ediciones manuales se corrige aquí.
 */
fun renumberOccurrences(events: List<DateEvent>): List<DateEvent> {
    val typesWithOccurrence = setOf("drop", "resume", "reread", "reread_end")
    val sorted = events.sortedWith(compareBy({ it.date }, { typeOrder(it.type) }, { it.occurrence }))
    val counters = mutableMapOf<String, Int>()
    return sorted.map { e ->
        if (e.type in typesWithOccurrence) {
            val n = (counters[e.type] ?: 0) + 1
            counters[e.type] = n
            e.copy(occurrence = n)
        } else e
    }
}

/** Cuenta cuántas relecturas completadas hay (reread_end). */
fun completedRereads(events: List<DateEvent>): Int =
    events.count { it.type == "reread_end" }

/** Cuenta cuántas relecturas iniciadas (incluyendo en curso). */
fun startedRereads(events: List<DateEvent>): Int =
    events.count { it.type == "reread" }

/**
 * B-025: fecha en la que arrancó el tramo de lectura EN CURSO.
 *
 * Un libro releído conserva su `startDate` original (la primera lectura, que puede
 * ser de hace años), así que contar días desde ahí da cifras absurdas — el caso real
 * fue El imperio final: leído en 2022, releído desde el 01-07-2026, mostraba
 * "1359 días". El tramo actual empieza en el último evento que ABRE lectura.
 * Sin eventos (libros previos a v19.8) se cae al `startDate` legacy.
 */
fun currentReadStart(book: Book): String? {
    val openers = setOf("start", "resume", "reread")
    return sortedDateEvents(book.dateEvents).lastOrNull { it.type in openers }?.date
        ?: book.startDate
}

/** Genera dateEvents a partir de los campos legacy (startDate/endDate/dropDate/resumedDate).
 *  Solo se usa si el libro no tiene dateEvents (migración perezosa).
 *  v20.0 (G6): renumera occurrences para evitar huecos en libros guardados con bugs previos. */
fun migrateLegacyToEvents(book: Book): List<DateEvent> {
    if (book.dateEvents.isNotEmpty()) return renumberOccurrences(book.dateEvents)
    val out = mutableListOf<DateEvent>()
    if (book.startDate != null) out.add(DateEvent("start", book.startDate))
    if (book.dropDate != null) out.add(DateEvent("drop", book.dropDate, 1))
    if (book.resumedDate != null) out.add(DateEvent("resume", book.resumedDate, 1))
    if (book.endDate != null && (book.status == BookStatus.FINISHED || book.status == BookStatus.REREADING || book.status == BookStatus.DROPPED)) {
        out.add(DateEvent("end", book.endDate))
    }
    return renumberOccurrences(out)
}

/** Orden lógico de tipos cuando coinciden fechas: start < drop < resume < end < reread < reread_end. */
fun typeOrder(t: String): Int = when (t) {
    "start" -> 0; "drop" -> 1; "resume" -> 2; "end" -> 3; "reread" -> 4; "reread_end" -> 5; else -> 9
}

/** Ordena eventos por fecha, y dentro del mismo día por tipo + occurrence. */
fun sortedDateEvents(events: List<DateEvent>): List<DateEvent> =
    events.sortedWith(compareBy({ it.date }, { typeOrder(it.type) }, { it.occurrence }))

/** Calcula el readingIndex de una sesión a partir de su fecha y los dateEvents.
 *  0 = lectura original; 1 = primera relectura; etc.
 *  Una sesión pertenece a la relectura N si su fecha es >= reread_N. */
fun computeReadingIndex(sessionDate: String, events: List<DateEvent>): Int {
    val rereads = events.filter { it.type == "reread" }.sortedBy { it.occurrence }
    var idx = 0
    for (r in rereads) {
        if (sessionDate >= r.date) idx = r.occurrence else break
    }
    return idx
}

// ── Modelo Wrapped ─────────────────────────────────────────────────────────────

data class YearWrapped(
    val year: Int,
    val totalBooks: Int,
    val totalPages: Int,
    val avgPagesPerDay: Double,
    val avgDaysPerBook: Double,
    val favoriteAuthor: String,
    val favoriteAuthorBooks: Int,
    val favoriteGenre: String,
    val favoriteGenreBooks: Int,
    val fastestBookTitle: String,
    val fastestBookPpd: Double,
    val fastestBookPages: Int,
    val bestRatedTitle: String,
    val bestRatedScore: Int,
    val longestStreakDays: Int,
    val longestStreakStart: String,
    val longestStreakEnd: String,
    // ── v18.3 additions ────────────────────────────────────────────────────
    val totalSessions: Int = 0,            // nº de sesiones registradas en el año
    val totalMinutes: Int = 0,             // minutos totales de lectura (sesiones del año)
    val maxSessionPages: Int = 0,          // página récord de una sola sesión
    val maxSessionDate: String = "",       // fecha de esa sesión
    val mostReadDay: String = "",          // día con más páginas leídas (yyyy-MM-dd)
    val mostReadDayPages: Int = 0,         // páginas leídas ese día
    val droppedBooks: Int = 0,             // libros abandonados en el año
    val topAuthorsTop3: List<Pair<String, Int>> = emptyList(),  // top 3 autores con su nº libros únicos
    val topAuthorsTop3Editions: List<Int> = emptyList(),        // v18.6: nº de ediciones totales por autor, alineado con topAuthorsTop3
    val topGenresTop3: List<Pair<String, Int>> = emptyList(),   // top 3 géneros con su nº libros
    // ── v18.4: gráfico mensual + comparativa año anterior ──────────────────
    val pagesPerMonth: List<Int> = List(12) { 0 },              // páginas leídas por mes (índice 0=ene, 11=dic)
    val booksPerMonth: List<Int> = List(12) { 0 },              // libros terminados por mes
    val previousYearBooks: Int = 0,                             // libros del año anterior (para Δ)
    val previousYearPages: Int = 0,                             // páginas del año anterior
    // ── v18.5: donut género del año ────────────────────────────────────────
    val genreCountsTop6: List<Pair<String, Int>> = emptyList(), // top 6 géneros (género principal) para donut
    // ── v19.3: libro que más tiempo te robó ────────────────────────────────
    val longestBooksTop3: List<Pair<String, Int>> = emptyList(), // (título, minutos totales)
    val rereadBooks: Int = 0,       // v19.9: relecturas completadas en el año (reread_end events)
    // v21.41: top 3 libros por nota (desempate por endDate desc = más recientes primero)
    val bestRatedTop3: List<Triple<String, Int, String>> = emptyList(), // (título, rating, endDate)
    val fastestBooksTop3: List<Triple<String, Double, Int>> = emptyList(), // (título, ppd, pages)
    val droppedBookTitles: List<String> = emptyList(),
    // ── v2.6 (Wrapped r1) ───────────────────────────────────────────────────
    // Favoritos congelados de este Wrapped (ids de Book). null = aún sin congelar.
    val favoriteBookIds: List<Long>? = null,
    // Día con más páginas de cada mes con actividad: (mes 0-11, fecha yyyy-MM-dd, páginas)
    val bestDayPerMonth: List<Triple<Int, String, Int>> = emptyList(),
    // Desglose del mostReadDay (mejor día global del año)
    val bestDaySessions: Int = 0,          // sesiones ese día
    val bestDayBooks: Int = 0,             // libros distintos ese día
    val bestDayPagesPerMin: Double = 0.0,  // páginas/minuto ese día (0 si sin minutos)
    // Páginas por franja horaria de 3h (índice 0 = 00-03h … 7 = 21-24h); requiere startTimestamp
    val pagesPerTimeSlot: List<Int> = List(8) { 0 },
    val savedAt: Long = System.currentTimeMillis()
)

// Extended sort: added RATING_DESC / RATING_ASC
enum class SortOrder {
    DATE_DESC, DATE_ASC,
    ALPHA_AZ, ALPHA_ZA,
    RATING_DESC, RATING_ASC,
    LENGTH_DESC, LENGTH_ASC
}

data class BookStats(val days: Int, val pagesPerDay: Double?)

// ── Retos de lectura (v2.4 rework) ─────────────────────────────────────────────

enum class ChallengeType {
    PAGES, BOOKS, SESSIONS, STREAK, MINUTES
}

data class Challenge(
    val id: Long,
    val name: String,
    val type: ChallengeType,
    val target: Int,
    val startDate: String?,          // yyyy-MM-dd; null = año natural actual
    val endDate: String?,            // yyyy-MM-dd; null = año natural actual
    val isDefault: Boolean = false,
    val titleFilter: String? = null  // v2.5: solo BOOKS — el título debe contener este texto (saga)
)
