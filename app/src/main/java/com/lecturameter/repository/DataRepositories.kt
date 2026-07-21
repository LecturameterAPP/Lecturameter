package com.lecturameter.repository

// ── Repositorios de datos (Fase 1.3) ──────────────────────────────────────────
// CRUD sobre SharedPreferences + Gson, extraído de BooksViewModel sin cambios
// funcionales. Los repos devuelven null cuando la clave no existe para que el
// ViewModel conserve sus early-returns de primera ejecución.
// SearchRepository y BackupRepository se extraen aparte (bloques grandes del monolito).

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lecturameter.model.*
import com.lecturameter.utils.BingoManager
import com.lecturameter.utils.sanitizeBook

// RF-C2: recuperación ante JSON corrupto en prefs. Sin esto, una clave truncada convertía
// el arranque en un crash-loop sin salida (ni siquiera para exportar). Se conserva el crudo
// en "<clave>_corrupt" (clave fija, sobrescribe la anterior) por si hay rescate manual, se
// elimina la clave dañada y se marca "repo_data_recovered" para que la UI avise al usuario.
// El catch es amplio a propósito: Gson no solo lanza JsonSyntaxException, también
// IllegalStateException, EOFException o NPEs internas según cómo esté roto el JSON.
private fun recoverCorruptPref(prefs: SharedPreferences, key: String, json: String) {
    prefs.edit()
        .putString("${key}_corrupt", json)
        .remove(key)
        .putBoolean("repo_data_recovered", true)
        .apply()
}

object BookRepository {
    private val gson = Gson()

    /** null = clave "books" ausente (primera ejecución) o retirada por corrupción. */
    fun loadOrNull(prefs: SharedPreferences): List<Book>? {
        val json = prefs.getString("books", null) ?: return null
        val type = object : TypeToken<List<Book>>() {}.type
        val parsed = try {
            gson.fromJson<List<Book>>(json, type)
        } catch (_: Exception) {
            recoverCorruptPref(prefs, "books", json)
            return null
        }
        // distinctBy { id }: si un proceso previo (restauración con IDs colisionantes) dejó
        // libros fantasma con el mismo ID, se conserva solo el primero. Evita duplicados ocultos.
        return (parsed ?: emptyList())
            .filterNotNull()
            .map { sanitizeBook(it) }
            .distinctBy { it.id }
    }

    fun save(prefs: SharedPreferences, books: List<Book>) {
        // v21.41: apply() en lugar de commit() — asíncrono, no bloquea hilo principal
        prefs.edit().putString("books", gson.toJson(books)).apply()
    }
}

object SessionRepository {
    private val gson = Gson()

    /** null = clave "sessions" ausente o retirada por corrupción. */
    fun loadOrNull(prefs: SharedPreferences): List<ReadingSession>? {
        val json = prefs.getString("sessions", null) ?: return null
        val type = object : TypeToken<List<ReadingSession>>() {}.type
        val parsed = try {
            gson.fromJson<List<ReadingSession>>(json, type)
        } catch (_: Exception) {
            recoverCorruptPref(prefs, "sessions", json)
            return null
        }
        // Sanitización: Gson instancia con Unsafe, así que un backup corrupto puede dejar
        // date o note (declarados non-null) a null, y computeWrapped hace substring sobre
        // date sin red. Se descartan las sesiones inservibles: elemento null, campo non-null
        // a null o date más corto que "yyyy-MM" (mismo umbral length >= 7 que usa el heatmap).
        return (parsed ?: emptyList())
            .filterNotNull()
            .filter { it.date != null && it.note != null && it.date.length >= 7 }
            .distinctBy { it.id }
    }

    fun save(prefs: SharedPreferences, sessions: List<ReadingSession>) {
        prefs.edit().putString("sessions", gson.toJson(sessions)).apply()
    }
}

object WrappedRepository {
    private val gson = Gson()

    fun loadOrNull(prefs: SharedPreferences): List<YearWrapped>? {
        val json = prefs.getString("wrapped_history", null) ?: return null
        val type = object : TypeToken<List<YearWrapped>>() {}.type
        return try {
            (gson.fromJson<List<YearWrapped>>(json, type) ?: emptyList()).filterNotNull().map { sanitizeWrapped(it) }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * B3 fase B: red de seguridad para los snapshots viejos, gemela de sanitizeBook().
     *
     * Gson instancia con Unsafe y rellena campo a campo, así que un campo que NO está en el
     * JSON no recibe el default de Kotlin: recibe el cero de la JVM, y en un String eso es
     * null aunque el tipo diga `String`. Al abrir el Wrapped de un año guardado con una
     * versión anterior, cualquier `previousYearGenre.isNotBlank()` revienta con NPE contra
     * un tipo declarado no-nulo. Cada campo nuevo del cierre-comparativa añade una ocasión
     * más de que pase, así que se corta aquí, en el único sitio por el que entran.
     */
    internal fun sanitizeWrapped(w: YearWrapped): YearWrapped = w.copy(
        favoriteAuthor = w.favoriteAuthor ?: "",
        favoriteGenre = w.favoriteGenre ?: "",
        fastestBookTitle = w.fastestBookTitle ?: "",
        bestRatedTitle = w.bestRatedTitle ?: "",
        longestStreakStart = w.longestStreakStart ?: "",
        longestStreakEnd = w.longestStreakEnd ?: "",
        maxSessionDate = w.maxSessionDate ?: "",
        mostReadDay = w.mostReadDay ?: "",
        previousYearGenre = w.previousYearGenre ?: "",
        previousYearAuthor = w.previousYearAuthor ?: "",
        previousYearMostReadDay = w.previousYearMostReadDay ?: "",
        bestWeekStart = w.bestWeekStart ?: "",
        previousYearBestWeekStart = w.previousYearBestWeekStart ?: "",
        topAuthorsTop3 = w.topAuthorsTop3 ?: emptyList(),
        topAuthorsTop3Editions = w.topAuthorsTop3Editions ?: emptyList(),
        topGenresTop3 = w.topGenresTop3 ?: emptyList(),
        pagesPerMonth = w.pagesPerMonth ?: List(12) { 0 },
        booksPerMonth = w.booksPerMonth ?: List(12) { 0 },
        genreCountsTop6 = w.genreCountsTop6 ?: emptyList(),
        longestBooksTop3 = w.longestBooksTop3 ?: emptyList(),
        bestRatedTop3 = w.bestRatedTop3 ?: emptyList(),
        fastestBooksTop3 = w.fastestBooksTop3 ?: emptyList(),
        droppedBookTitles = w.droppedBookTitles ?: emptyList(),
        bestDayPerMonth = w.bestDayPerMonth ?: emptyList(),
        pagesPerTimeSlot = w.pagesPerTimeSlot ?: List(8) { 0 }
    )

    fun save(prefs: SharedPreferences, history: List<YearWrapped>) {
        prefs.edit().putString("wrapped_history", gson.toJson(history)).apply()
    }
}

// ── Bingo (Fase 5, MD5): cartón mensual con plantillas rotativas ──────────────
//
// B4 (2): a partir de aquí hay DOS cartones vivos a la vez, uno por tamaño.
//
// El 4×4 CONSERVA su clave histórica `bingo_card` tal cual. Eso no es pereza: es lo que
// hace que no haya migración ninguna. Quien viene de la 2.7 con su cartón de julio a
// medias lo encuentra intacto, porque nadie ha tocado ni la clave ni el formato. El 3×3
// estrena claves propias, y si no existen simplemente no hay cartón 3×3 todavía, que es
// exactamente el estado correcto para quien nunca ha sido Pro.
object BingoRepository {
    private val gson = Gson()

    /** Clave del cartón de cada tamaño. El 4 se queda con la histórica (cero migración). */
    private fun cardKey(side: Int): String =
        if (side == BingoManager.SIDE_4) "bingo_card" else "bingo_card_$side"

    /** Clave del índice de rotación de plantillas. Cada tamaño rota por su cuenta: si
     *  compartieran índice, estrenar el 3×3 saltaría plantillas del 4×4. */
    fun templateIndexKey(side: Int): String =
        if (side == BingoManager.SIDE_4) "bingo_template_index" else "bingo_template_index_$side"

    /** null = sin cartón aún (primera ejecución, JSON corrupto o 3×3 nunca estrenado). */
    fun loadOrNull(prefs: SharedPreferences, side: Int = BingoManager.SIDE_4): BingoCard? {
        val json = prefs.getString(cardKey(side), null) ?: return null
        return try { gson.fromJson(json, BingoCard::class.java) } catch (_: Exception) { null }
    }

    fun save(prefs: SharedPreferences, card: BingoCard) {
        val side = BingoManager.sideOf(card.cells.size)
        prefs.edit().putString(cardKey(side), gson.toJson(card)).apply()
    }

    /** B4 (2): retira el cartón vivo de un tamaño. Se usa al caducar el trial con un 3×3
     *  en marcha: el cartón se archiva ANTES en el historial, así que esto no pierde nada
     *  (lo jugado se conserva; lo único que se para es seguir jugándolo). */
    fun clear(prefs: SharedPreferences, side: Int) {
        prefs.edit().remove(cardKey(side)).apply()
    }
}

object ChallengeRepository {
    private val gson = Gson()

    /** null = clave "challenges" ausente o retirada por corrupción. */
    fun loadOrNull(prefs: SharedPreferences): List<Challenge>? {
        val json = prefs.getString("challenges", null) ?: return null
        val type = object : TypeToken<List<Challenge>>() {}.type
        val parsed = try {
            gson.fromJson<List<Challenge>>(json, type)
        } catch (_: Exception) {
            recoverCorruptPref(prefs, "challenges", json)
            return null
        }
        return (parsed ?: emptyList())
            .filterNotNull()  // un elemento null en la lista haría NPE en el filtro siguiente
            .filter { it.name != null && it.type != null }  // Gson puede colar nulls desde JSON corrupto
    }

    fun save(prefs: SharedPreferences, challenges: List<Challenge>) {
        prefs.edit().putString("challenges", gson.toJson(challenges)).apply()
    }
}

// D-016: historial de retos archivados (clave prefs `challenge_history`; entra en backup v4)
object ChallengeHistoryRepository {
    private val gson = Gson()

    fun load(prefs: SharedPreferences): List<ChallengeSnapshot> {
        val json = prefs.getString("challenge_history", null) ?: return emptyList()
        val type = object : TypeToken<List<ChallengeSnapshot>>() {}.type
        val parsed = try {
            gson.fromJson<List<ChallengeSnapshot>>(json, type)
        } catch (_: Exception) {
            recoverCorruptPref(prefs, "challenge_history", json)
            return emptyList()
        }
        return (parsed ?: emptyList())
            .filterNotNull()  // un elemento null en la lista haría NPE en el filtro siguiente
            .filter { it.name != null && it.type != null }
    }

    fun save(prefs: SharedPreferences, history: List<ChallengeSnapshot>) {
        prefs.edit().putString("challenge_history", gson.toJson(history)).apply()
    }
}
