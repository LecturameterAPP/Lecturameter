package com.lecturameter.utils

// ── Utilidades puras ──────────────────────────────────────────────────────────
// Fase 1.1: extraídas de MainActivity.kt sin cambios funcionales.
// Nota: consensusPages(), isCoverUrlValid() y MIN_PLAUSIBLE_PAGES pertenecen a la
// pila de búsqueda y se moverán íntegros con SearchRepository en la Fase 1.3.

import com.lecturameter.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

// ── CSV ───────────────────────────────────────────────────────────────────────

fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>(); var inQuotes = false; val cur = StringBuilder()
    for (c in line) { when { c == '"' -> inQuotes = !inQuotes; c == ',' && !inQuotes -> { result.add(cur.toString()); cur.clear() }; else -> cur.append(c) } }
    result.add(cur.toString()); return result
}

// ── Fechas ────────────────────────────────────────────────────────────────────

// v1.4: SimpleDateFormat NO es thread-safe. Se usa desde main y Dispatchers.IO
// (backups, workers). ThreadLocal da una instancia por hilo — sin locks, sin corrupción.
private val sdfTL = java.lang.ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
private val sdfDisplayTL = java.lang.ThreadLocal.withInitial { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }
private val sdfInputTL = java.lang.ThreadLocal.withInitial { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()) }
val sdf: SimpleDateFormat get() = sdfTL.get()!!
val sdfDisplay: SimpleDateFormat get() = sdfDisplayTL.get()!!
val sdfInput: SimpleDateFormat get() = sdfInputTL.get()!!

fun today(): String = sdf.format(Date())
fun todayDisplay(): String = sdfInput.format(Date())

/** Parsea "d-M-yyyy" o "dd-MM-yyyy" — acepta 1 o 2 dígitos en día y mes.
 *  Rechaza fechas imposibles (39/01, 29/02 en año no bisiesto, etc.).
 *  Devuelve "yyyy-MM-dd" para almacenamiento, o null si la fecha es inválida. */
fun parseFlexibleDate(input: String): String? {
    val parts = input.trim().split("-")
    if (parts.size != 3) return null
    if (parts[0].length !in 1..2 || parts[1].length !in 1..2 || parts[2].length != 4) return null
    val day   = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val year  = parts[2].toIntOrNull() ?: return null
    if (month < 1 || month > 12) return null
    if (day < 1) return null
    val cal = java.util.Calendar.getInstance()
    cal.isLenient = false
    return try {
        cal.set(year, month - 1, day)
        cal.time
        "%04d-%02d-%02d".format(year, month, day)
    } catch (_: Exception) { null }
}

fun storedToDisplay(date: String): String =
    try { sdfInput.format(sdf.parse(date)!!) } catch (_: Exception) { date }

/** Convierte "dd-MM-yyyy" (input del usuario) → "yyyy-MM-dd" (almacenamiento). */
fun displayToStored(date: String): String =
    try { sdf.format(sdfInput.parse(date)!!) } catch (_: Exception) { date }

fun daysBetween(start: String, end: String): Int {
    return try {
        val s = sdf.parse(start) ?: return 1
        val e = sdf.parse(end) ?: return 1
        val d = ceil((e.time - s.time) / 86400000.0).toInt()
        if (d < 0) 0 else d
    } catch (_: Exception) { 1 }
}

// Returns true if a finished book has a real multi-day reading span (not same-day)
fun hasValidReadingSpan(book: Book): Boolean =
    book.startDate != null && book.endDate != null && book.startDate != book.endDate && daysBetween(book.startDate, book.endDate) >= 1

fun fmtDays(days: Int): String = if (days == 1) "1 día" else "$days días"

fun fmtDate(date: String): String = try { sdfDisplay.format(sdf.parse(date)!!) } catch (_: Exception) { date }

// Only FINISHED books get pagesPerDay — no estimates for READING
// Books with same startDate and endDate are excluded from pagesPerDay (same-day = unreliable speed)
fun getStats(book: Book): BookStats? = when {
    book.status == BookStatus.FINISHED && book.startDate != null && book.endDate != null -> {
        val d = daysBetween(book.startDate, book.endDate).coerceAtLeast(1)
        val ppd = if (book.startDate != book.endDate) {
            val funcPages = if (book.firstFunctionalPage != null && book.lastFunctionalPage != null)
                (book.lastFunctionalPage - book.firstFunctionalPage + 1).toDouble()
            else book.pages.toDouble()
            if (funcPages > 0) funcPages / d else null
        } else null
        BookStats(d, ppd)
    }
    (book.status == BookStatus.READING || book.status == BookStatus.REREADING) && book.startDate != null ->
        BookStats(daysBetween(book.startDate, today()).coerceAtLeast(1), null)
    else -> null
}

// ── ISBN ──────────────────────────────────────────────────────────────────────

fun cleanIsbn(value: String?): String? {
    val clean = value?.replace(Regex("[^\\dXx]"), "")?.uppercase().orEmpty()
    return clean.takeIf { it.length in 10..13 }
}

// Feedback 2.6: conversión ISBN-10 ↔ ISBN-13. Algunas fuentes (GB, OL) indexan una
// edición solo bajo una de las dos formas; escaneo y duplicados comparan ambas.
/** ISBN-13 → ISBN-10 (solo prefijo 978). null si no es convertible. */
fun isbn13To10(isbn13: String): String? {
    val c = cleanIsbn(isbn13) ?: return null
    if (c.length != 13 || !c.startsWith("978") || !c.all { it.isDigit() }) return null
    val core = c.substring(3, 12)
    var sum = 0
    for (i in 0 until 9) sum += (core[i] - '0') * (10 - i)
    val check = (11 - sum % 11) % 11
    return core + if (check == 10) "X" else check.toString()
}

/** ISBN-10 → ISBN-13 (prefijo 978). null si la entrada no es un ISBN-10. */
fun isbn10To13(isbn10: String): String? {
    val c = cleanIsbn(isbn10) ?: return null
    if (c.length != 10) return null
    val core = "978" + c.substring(0, 9)
    if (!core.all { it.isDigit() }) return null
    var sum = 0
    for (i in core.indices) sum += (core[i] - '0') * (if (i % 2 == 0) 1 else 3)
    val check = (10 - sum % 10) % 10
    return core + check
}

/** Forma canónica para comparar ISBNs: siempre ISBN-13 si es posible. */
fun canonicalIsbn(value: String?): String? {
    val c = cleanIsbn(value) ?: return null
    return if (c.length == 10) isbn10To13(c) ?: c else c
}

// ── Sanitización de libros (migraciones Gson) ─────────────────────────────────

// ── Heurística de bandera US/UK (v18.4) ───────────────────────────────────
// Para ediciones inglesas (language="original"/"unknown" y flag="🌐"), intenta
// clasificarlas como 🇺🇸 o 🇬🇧 según el publisher (señal más fiable) o el ISBN
// (señal secundaria). Si no hay señal clara, devuelve null y se conserva 🌐.
//
// Retorna Triple(flag, languageLabel, language) o null si no hay clasificación.
fun inferEnglishFlag(isbn: String?, publisher: String?): Triple<String, String, String>? {
    val pub = (publisher ?: "").lowercase()
    val isb = (isbn ?: "").replace(Regex("[-\\s]"), "")

    // Publishers conocidos: prioridad sobre ISBN
    val usPublishers = listOf(
        "tor books", "tor.com", "dragonsteel", "random house", "scribner", "knopf",
        "harpercollins us", "bantam", "anchor", "henry holt", "farrar", "fsg",
        "crown", "doubleday", "riverhead", "norton", "simon & schuster", "simon and schuster",
        "del rey", "vintage books", "ace books", "daw books", "subterranean press"
    )
    val ukPublishers = listOf(
        "gollancz", "orbit uk", "bloomsbury", "penguin uk", "penguin books ltd",
        "hodder", "pan macmillan", "faber", "headline", "tor uk", "voyager",
        "harpercollins uk", "vintage uk", "picador uk", "jonathan cape", "harvill secker"
    )
    val isUs = usPublishers.any { pub.contains(it) }
    val isUk = ukPublishers.any { pub.contains(it) }
    if (isUs && !isUk) return Triple("🇺🇸", "Inglés (EE.UU.)", "original")
    if (isUk && !isUs) return Triple("🇬🇧", "Inglés (Reino Unido)", "original")

    // Caso especial: "tor" como prefijo sin sufijo claro → tratamos como US (más probable)
    if (pub.matches(Regex("^tor[\\s,].*")) || pub == "tor") return Triple("🇺🇸", "Inglés (EE.UU.)", "original")

    // ISBN-13: 978-0-XX y 978-1-XX. Reglas aproximadas por rango de prefijo de grupo
    // (registration agency). Es imprecisa pero útil cuando el publisher falla.
    if (isb.length >= 13 && (isb.startsWith("9780") || isb.startsWith("9781"))) {
        // Tomar 2 dígitos siguientes al "978X" para decidir
        val grp = isb.substring(4, 6).toIntOrNull() ?: return null
        if (isb.startsWith("9780")) {
            return when {
                grp in 0..19 -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")  // mayoría US (Penguin, Scribner, etc.)
                grp in 20..29 -> Triple("🇬🇧", "Inglés (Reino Unido)", "original")  // UK Allen & Unwin, Faber...
                grp in 30..49 -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")
                grp in 50..69 -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")
                grp in 70..79 -> Triple("🇬🇧", "Inglés (Reino Unido)", "original")  // Hodder, Pan...
                grp in 80..87 -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")
                else -> null
            }
        } else { // 9781
            return when {
                grp in 0..6   -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")
                grp in 39..54 -> Triple("🇬🇧", "Inglés (Reino Unido)", "original")  // 1-40 a 1-54 UK
                grp in 55..86 -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")
                grp in 90..99 -> Triple("🇺🇸", "Inglés (EE.UU.)", "original")
                else -> null
            }
        }
    }
    return null
}

// Sanitiza campos String que Gson puede dejar como null al deserializar datos de versiones anteriores
fun sanitizeBook(b: Book): Book {
    // Gson puede deserializar null en campos con valor por defecto en Kotlin
    // cuando el JSON proviene de una version anterior que no tenia ese campo.
    // Todos los campos de tipo lista deben ser null-safe aqui.
    val safeGenres   = b.genres   ?: emptyList()
    val safeEditions = b.editions ?: emptyList()

    // Migracion: si genres esta vacio pero genre (campo legacy) tiene valor, convertir
    val legacyGenre = b.genre.orEmpty()
    val baseGenres = when {
        safeGenres.isNotEmpty() -> safeGenres
        legacyGenre.isNotBlank() -> listOf(legacyGenre)
        else -> emptyList()
    }
    // v20.9: "Horror" eliminado de BOOK_GENRES → normalizar a "Terror"
    val migratedGenres = baseGenres.map { if (it == "Horror") "Terror" else it }
    val safeFirst = b.firstFunctionalPage?.takeIf { it >= 1 }
    val safeLast  = b.lastFunctionalPage?.takeIf { it >= 1 && (safeFirst == null || it >= safeFirst) }
    // Migración v17.11: dropDate/resumedDate de versiones previas pudieron guardarse en dd-MM-yyyy.
    // El formato interno de fechas es yyyy-MM-dd; reconvertir si es necesario.
    fun fixInternalDate(d: String?): String? {
        if (d == null) return null
        if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(d)) return d
        return parseFlexibleDate(d)  // acepta dd-MM-yyyy y variantes; null si no se entiende
    }
    return b.copy(
        title    = b.title    ?: "",
        author   = b.author   ?: "",
        comment  = b.comment  ?: "",
        genre    = "",
        genres   = migratedGenres,
        firstFunctionalPage = safeFirst,
        lastFunctionalPage  = safeLast,
        // v20.9: si está DROPPED y no tiene startDate/dropDate, asignamos hoy
        startDate = fixInternalDate(b.startDate) ?: if (b.status == BookStatus.DROPPED) today() else null,
        endDate = fixInternalDate(b.endDate) ?: run {
            // v21.37: FINISHED sin endDate → usar startDate (el usuario lo editará si es incorrecto)
            val fixedStart = fixInternalDate(b.startDate)
            if (b.status == BookStatus.FINISHED && fixedStart != null) fixedStart else null
        },
        dropDate = fixInternalDate(b.dropDate) ?: if (b.status == BookStatus.DROPPED) today() else null,
        resumedDate = fixInternalDate(b.resumedDate),
        dateEvents = b.dateEvents ?: emptyList(),   // v19.9 fix: Gson pone null si el campo no existe en el JSON
        editions = safeEditions.map { e ->
            val sanitized = e.copy(
                language      = e.language      ?: "unknown",
                languageLabel = e.languageLabel ?: "Edición principal",
                flag          = e.flag          ?: "🌐",
                title         = e.title         ?: "",
                publisher     = e.publisher     ?: "",
                publishYear   = e.publishYear   ?: ""
            )
            // v18.4: auto-clasificación US/UK para ediciones inglesas con flag=🌐.
            // Solo se aplica si el usuario no ha elegido manualmente otra bandera.
            val needsInfer = sanitized.flag == "🌐" &&
                (sanitized.language == "original" || sanitized.language == "unknown")
            if (needsInfer) {
                val inferred = inferEnglishFlag(sanitized.isbn, sanitized.publisher)
                if (inferred != null) {
                    val (newFlag, newLabel, newLang) = inferred
                    sanitized.copy(flag = newFlag, languageLabel = newLabel, language = newLang)
                } else sanitized
            } else sanitized
        }
    )
}
