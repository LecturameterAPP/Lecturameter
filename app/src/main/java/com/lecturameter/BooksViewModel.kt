package com.lecturameter

// Fase 1.2: BooksViewModel extraído de MainActivity.kt sin cambios funcionales.

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.rememberDrawerState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.getValue
import android.widget.Toast
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import java.util.concurrent.TimeUnit
import com.lecturameter.model.*
import com.lecturameter.utils.*

class BooksViewModel : ViewModel() {
    private val gson = Gson()
    // Fase 1.2/D-004: books y sessions migrados a StateFlow. La UI colecciona en la
    // raíz de cada pantalla (collectAsState); el código no-composable usa .value.
    // booksInternal/sessionsInternal son alias privados para el cuerpo del VM.
    private val _books = kotlinx.coroutines.flow.MutableStateFlow<List<Book>>(emptyList())
    val books: kotlinx.coroutines.flow.StateFlow<List<Book>> = _books
    private val _sessions = kotlinx.coroutines.flow.MutableStateFlow<List<ReadingSession>>(emptyList())
    val sessions: kotlinx.coroutines.flow.StateFlow<List<ReadingSession>> = _sessions
    private var booksInternal: List<Book>
        get() = _books.value
        set(v) { _books.value = v }
    private var sessionsInternal: List<ReadingSession>
        get() = _sessions.value
        set(v) { _sessions.value = v }
    /** Escrituras controladas para BackupRepository (restauración de backups). */
    fun setBooks(v: List<Book>) { _books.value = v }
    fun setSessions(v: List<ReadingSession>) { _sessions.value = v }
    var themeMode by mutableStateOf(ThemeMode.DARK)
        private set
    var tutorialCompleted by mutableStateOf(false)
        private set
    fun completeTutorial(prefs: android.content.SharedPreferences) {
        tutorialCompleted = true
        prefs.edit().putBoolean("tutorial_completed", true).apply()
    }
    fun resetTutorial(prefs: android.content.SharedPreferences) {
        tutorialCompleted = false
        prefs.edit().putBoolean("tutorial_completed", false).apply()
    }
    fun loadTutorialStatus(prefs: android.content.SharedPreferences) {
        tutorialCompleted = prefs.getBoolean("tutorial_completed", false)
    }
    var currentLanguage by mutableStateOf("es")
        private set
    var languageChosen by mutableStateOf(false)
        private set
    fun setLanguage(lang: String, prefs: android.content.SharedPreferences) {
        currentLanguage = lang
        prefs.edit().putString("app_language", lang).commit()
        // Feedback 11-07: las fechas visibles (fmtDate) siguen el idioma de la app
        com.lecturameter.utils.appDisplayLocale = java.util.Locale(lang.take(2))
    }
    fun setLanguageChosen(prefs: android.content.SharedPreferences) {
        languageChosen = true
        prefs.edit().putBoolean("language_chosen", true).apply()
    }
    fun loadLanguageStatus(prefs: android.content.SharedPreferences) {
        currentLanguage = prefs.getString("app_language", "es") ?: "es"
        languageChosen  = prefs.getBoolean("language_chosen", false)
        com.lecturameter.utils.appDisplayLocale = java.util.Locale(currentLanguage.take(2))
    }
    // Persisted list state
    var savedSortOrder by mutableStateOf(SortOrder.DATE_DESC)
    var savedSearchQuery by mutableStateOf("")
    var savedSessionNewestFirst by mutableStateOf(true)

    // ── Favoritos (v2.4 rework) ────────────────────────────────────────────────
    var showFavoritesOnly by mutableStateOf(false)
        private set
    fun setShowFavoritesOnly(value: Boolean, prefs: android.content.SharedPreferences) {
        showFavoritesOnly = value
        prefs.edit().putBoolean("show_favorites_only", value).apply()
    }
    /** Alterna isFavorite y devuelve el nuevo estado (true = ahora es favorito). */
    fun toggleFavorite(id: Long, prefs: android.content.SharedPreferences): Boolean {
        var nowFavorite = false
        booksInternal = booksInternal.map {
            if (it.id == id) { nowFavorite = !it.isFavorite; it.copy(isFavorite = nowFavorite) } else it
        }
        save(prefs)
        return nowFavorite
    }

    /** v2.4 rework: 3 favoritos aleatorios terminados en el año (para el Wrapped).
     *  Regla: isFavorite && (FINISHED || REREADING) && terminado dentro del año. */
    fun pickRandomFavorites(year: Int): List<Book> {
        val y = year.toString()
        fun finishedInYear(b: Book): Boolean {
            if (b.endDate?.startsWith(y) == true) return true
            return b.dateEvents.any { (it.type == "end" || it.type == "reread_end") && it.date.startsWith(y) }
        }
        return booksInternal
            .filter { it.isFavorite && (it.status == BookStatus.FINISHED || it.status == BookStatus.REREADING) && finishedInYear(it) }
            .shuffled()
            .take(3)
    }

    // ── v2.6 (Wrapped r1): favoritos CONGELADOS por Wrapped ────────────────────
    // La primera vez que se abre el Wrapped de un año se eligen hasta 3 favoritos y
    // se persisten (pref "wrapped_favs_<year>"). Nunca se sustituyen ni reordenan;
    // si se congelaron <3, los huecos se rellenan con favoritos nuevos hasta llegar
    // a 3 (los congelados son permanentes). Fallback de lectura: ids embebidos en
    // el historial (backups restaurados en otro dispositivo).
    fun favoritesForWrapped(year: Int, prefs: android.content.SharedPreferences): List<Book> {
        val key = "wrapped_favs_$year"
        val listType = object : com.google.gson.reflect.TypeToken<List<Long>>() {}.type
        val fromPref: List<Long>? = prefs.getString(key, null)?.let {
            try { gson.fromJson<List<Long>>(it, listType) } catch (_: Exception) { null }
        }
        var frozen = fromPref ?: wrappedForYear(year)?.favoriteBookIds ?: emptyList()
        // Rellenar huecos hasta 3 con favoritos nuevos (sin tocar los congelados)
        if (frozen.size < 3) {
            val extra = pickRandomFavorites(year).map { it.id }.filter { it !in frozen }
            if (extra.isNotEmpty()) frozen = (frozen + extra).take(3)
        }
        if (frozen.isEmpty()) return emptyList()
        if (frozen != fromPref) prefs.edit().putString(key, gson.toJson(frozen)).apply()
        return frozen.mapNotNull { id -> booksInternal.find { it.id == id } }
    }

    // ── Retos de lectura (v2.4 rework) ─────────────────────────────────────────
    // Fase 1.2/D-004: migrado de mutableStateOf a StateFlow (la UI colecciona en su raíz)
    private val _challenges = kotlinx.coroutines.flow.MutableStateFlow<List<Challenge>>(emptyList())
    val challenges: kotlinx.coroutines.flow.StateFlow<List<Challenge>> = _challenges
    private fun saveChallenges(prefs: android.content.SharedPreferences) {
        com.lecturameter.repository.ChallengeRepository.save(prefs, _challenges.value)
    }
    fun loadChallenges(prefs: android.content.SharedPreferences) {
        com.lecturameter.repository.ChallengeRepository.loadOrNull(prefs)?.let { _challenges.value = it }
        // Migración Feedback 2.6 (ONE-SHOT, flag en prefs): hasta v2.6 los retos
        // personalizados se creaban con startDate = día de creación, así que los libros
        // ya terminados no contaban (reto de saga «Rueda del Tiempo» siempre 0/N).
        // El id es el epoch de creación: si startDate == día de creación, era el valor
        // impuesto por el bug → null (año natural). El flag evita "migrar" retos nuevos
        // donde el usuario eligió a propósito su día de creación como fecha de inicio.
        if (!prefs.getBoolean("challenges_startdate_migration_v26", false)) {
            if (_challenges.value.any { !it.isDefault && it.startDate != null }) {
                val sdfMig = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                var migrated = false
                _challenges.value = _challenges.value.map { c ->
                    val creationDay = try { sdfMig.format(java.util.Date(c.id)) } catch (_: Exception) { null }
                    if (!c.isDefault && c.startDate != null && c.startDate == creationDay) {
                        migrated = true; c.copy(startDate = null)
                    } else c
                }
                if (migrated) saveChallenges(prefs)
            }
            prefs.edit().putBoolean("challenges_startdate_migration_v26", true).apply()
        }
        // Sembrar retos predeterminados una sola vez (si el usuario los borra no se recrean).
        // QA r2 12-07 (petición de Víctor): 5 retos por defecto — a los 2 clásicos se suman
        // páginas, sesiones y minutos. El flag _v2 añade los 3 nuevos también a las
        // instalaciones que ya habían sembrado los 2 originales.
        val ctx = appContext
        fun defaultFive(): List<Challenge> {
            val now = System.currentTimeMillis()
            return listOf(
                Challenge(
                    id = now,
                    name = ctx?.getString(R.string.challenge_default_books) ?: "12 libros este año",
                    type = ChallengeType.BOOKS, target = 12,
                    startDate = null, endDate = null, isDefault = true
                ),
                Challenge(
                    id = now + 1,
                    name = ctx?.getString(R.string.challenge_default_streak) ?: "Racha de 7 días",
                    type = ChallengeType.STREAK, target = 7,
                    startDate = null, endDate = null, isDefault = true
                ),
                Challenge(
                    id = now + 2,
                    name = ctx?.getString(R.string.challenge_default_pages) ?: "3.000 páginas este año",
                    type = ChallengeType.PAGES, target = 3000,
                    startDate = null, endDate = null, isDefault = true
                ),
                Challenge(
                    id = now + 3,
                    name = ctx?.getString(R.string.challenge_default_sessions) ?: "50 sesiones este año",
                    type = ChallengeType.SESSIONS, target = 50,
                    startDate = null, endDate = null, isDefault = true
                ),
                Challenge(
                    id = now + 4,
                    name = ctx?.getString(R.string.challenge_default_minutes) ?: "24 horas de lectura este año",
                    type = ChallengeType.MINUTES, target = 1440,
                    startDate = null, endDate = null, isDefault = true
                )
            )
        }
        if (!prefs.getBoolean("challenges_defaults_seeded", false)) {
            _challenges.value = _challenges.value + defaultFive().filter { d -> _challenges.value.none { it.name == d.name } }
            prefs.edit()
                .putBoolean("challenges_defaults_seeded", true)
                .putBoolean("challenges_defaults_seeded_v2", true)
                .apply()
            saveChallenges(prefs)
        } else if (!prefs.getBoolean("challenges_defaults_seeded_v2", false)) {
            // Instalación con los 2 defaults antiguos: añadir SOLO los 3 nuevos
            val newOnes = defaultFive().drop(2)
            _challenges.value = _challenges.value + newOnes.filter { d -> _challenges.value.none { it.name == d.name } }
            prefs.edit().putBoolean("challenges_defaults_seeded_v2", true).apply()
            saveChallenges(prefs)
        }
    }
    // ── Bingo con plantillas rotativas (Fase 5, MD5) ────────────────────────────
    // Cartón 3×3 mensual. Las celdas se validan solas: al terminar/valorar un libro
    // (genre, pages, rating, author_new, saga) y al registrar sesión (streak).
    private val _bingoCard = kotlinx.coroutines.flow.MutableStateFlow<com.lecturameter.model.BingoCard?>(null)
    val bingoCard: kotlinx.coroutines.flow.StateFlow<com.lecturameter.model.BingoCard?> = _bingoCard

    /** Garantiza que hay cartón y que es del mes actual; con [force] rota ya
     *  (botón "Nuevo cartón" al completarlo antes de fin de mes). Las plantillas
     *  rotan en orden circular según el índice persistido. */
    fun ensureBingoCard(prefs: android.content.SharedPreferences, force: Boolean = false) {
        val ctx = appContext ?: return
        val templates = com.lecturameter.utils.BingoManager.loadTemplates(ctx)
        if (templates.isEmpty()) return
        val month = com.lecturameter.utils.BingoManager.currentMonthKey()
        val cur = _bingoCard.value
        if (!force && cur != null && cur.monthKey == month) return
        val nextIdx = (prefs.getInt("bingo_template_index", -1) + 1).mod(templates.size)
        val card = com.lecturameter.utils.BingoManager.newCard(templates[nextIdx], month)
        _bingoCard.value = card
        prefs.edit().putInt("bingo_template_index", nextIdx).apply()
        com.lecturameter.repository.BingoRepository.save(prefs, card)
    }

    /** Hook al terminar (o valorar) un libro: evalúa las celdas de tipo libro. */
    private fun bingoOnBookFinished(book: Book, prefs: android.content.SharedPreferences) {
        val card = _bingoCard.value ?: return
        val updated = com.lecturameter.utils.BingoManager.evaluateBookFinished(card, book, booksInternal)
        if (updated !== card) {
            _bingoCard.value = updated
            com.lecturameter.repository.BingoRepository.save(prefs, updated)
        }
    }

    /** Hook al registrar sesión: evalúa las celdas de racha. */
    private fun bingoOnSession(prefs: android.content.SharedPreferences) {
        val card = _bingoCard.value ?: return
        val updated = com.lecturameter.utils.BingoManager.evaluateStreak(card, currentReadingStreak())
        if (updated !== card) {
            _bingoCard.value = updated
            com.lecturameter.repository.BingoRepository.save(prefs, updated)
        }
    }

    fun addChallenge(challenge: Challenge, prefs: android.content.SharedPreferences) {
        _challenges.value = _challenges.value + challenge
        saveChallenges(prefs)
    }
    fun deleteChallenge(id: Long, prefs: android.content.SharedPreferences) {
        _challenges.value = _challenges.value.filter { it.id != id }
        saveChallenges(prefs)
    }

    /** Progreso actual de un reto: Pair(valorActual, objetivo). */
    fun challengeProgress(c: Challenge): Pair<Int, Int> {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val rangeStart = c.startDate ?: "$year-01-01"
        val rangeEnd   = c.endDate   ?: "$year-12-31"
        fun inRange(date: String?) = date != null && date >= rangeStart && date <= rangeEnd
        val current = when (c.type) {
            ChallengeType.PAGES    -> sessionsInternal.filter { inRange(it.date) }.sumOf { it.pages }
            ChallengeType.MINUTES  -> sessionsInternal.filter { inRange(it.date) }.sumOf { it.minutes ?: 0 }
            ChallengeType.SESSIONS -> sessionsInternal.count { inRange(it.date) }
            ChallengeType.BOOKS    -> {
                // Feedback 2.6: el filtro de saga ignora tildes ("oráculo" ≡ "oraculo") y
                // también mira los títulos de las ediciones del libro.
                fun normF(s: String) = java.text.Normalizer.normalize(s.lowercase().trim(), java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}"), "")
                val filterNorm = c.titleFilter?.takeIf { it.isNotBlank() }?.let { normF(it) }
                booksInternal.count { b ->
                    (b.status == BookStatus.FINISHED || b.status == BookStatus.REREADING) && inRange(b.endDate) &&
                        (filterNorm == null ||
                            normF(b.title).contains(filterNorm) ||
                            b.editions.any { normF(it.title).contains(filterNorm) })
                }
            }
            ChallengeType.STREAK   -> currentReadingStreak()
        }
        return current to c.target
    }

    /** Racha actual de días consecutivos con al menos una sesión (contando hoy o ayer como ancla). */
    fun currentReadingStreak(): Int {
        val daysWithSession = sessionsInternal.map { it.date }.toHashSet()
        if (daysWithSession.isEmpty()) return 0
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        // Ancla: hoy si hay sesión hoy; si no, ayer (racha aún viva hasta fin del día)
        var anchor = sdf.format(cal.time)
        if (anchor !in daysWithSession) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            anchor = sdf.format(cal.time)
            if (anchor !in daysWithSession) return 0
        }
        var streak = 0
        while (sdf.format(cal.time) in daysWithSession) {
            streak++
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }
    private var refreshingCoverIds by mutableStateOf<Set<Long>>(emptySet())
    // Job para cargar ediciones — cancelable para evitar cargas concurrentes y crashes
    private var loadEditionsJob: kotlinx.coroutines.Job? = null

    /** Se inicializa una sola vez desde MainActivity.onCreate para poder lanzar backups sin pasar Context a cada función. */
    private var appContext: android.content.Context? = null
    fun initContext(context: android.content.Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun load(prefs: android.content.SharedPreferences) {
        // v1.0: cargar idioma/tutorial SIEMPRE, antes del early return por libros vacíos
        loadLanguageStatus(prefs)
        loadTutorialStatus(prefs)
        // v2.4 rework: favoritos y retos se cargan SIEMPRE, antes del early return por libros vacíos
        showFavoritesOnly = prefs.getBoolean("show_favorites_only", false)
        loadChallenges(prefs)
        // Fase 5 (MD5): cartón de Bingo — cargar el persistido y rotar si cambió el mes
        com.lecturameter.repository.BingoRepository.loadOrNull(prefs)?.let { _bingoCard.value = it }
        ensureBingoCard(prefs)
        // Fase 1.3: carga delegada a los repositorios (mismo early-return de primera ejecución)
        booksInternal = com.lecturameter.repository.BookRepository.loadOrNull(prefs) ?: return
        themeMode = when (prefs.getString("theme_mode", "dark")) {
            "light"   -> ThemeMode.LIGHT
            "aurora"  -> ThemeMode.AURORA
            "amoled"  -> ThemeMode.AMOLED
            // QA r2 12-07: Dinámico eliminado — prefs antiguas con "dynamic" caen a oscuro
            else      -> ThemeMode.DARK
        }
        com.lecturameter.repository.SessionRepository.loadOrNull(prefs)?.let { sessionsInternal = it }
        loadWrapped(prefs)
        autoRepairFinishedBooks(prefs)
        repairLegacyFlags(prefs)
        repairLegacyEditionIconsV8900(prefs)
        genreLogicRepairV8601(prefs)
        repairSpanishFlagsV1470(prefs)
        migrateAnglophoneFlagsV1840(prefs)
        migrateStuckGlobalFlagsV1880(prefs)
        repairBlindAnglophoneDefaultV1900(prefs)
        savedSessionNewestFirst = prefs.getBoolean("session_newest_first", true)
        savedSortOrder = SortOrder.entries.firstOrNull { it.name == prefs.getString("sort_order", null) } ?: SortOrder.DATE_DESC
        // loadTutorialStatus y loadLanguageStatus ya llamados al inicio de load()
    }
    /**
     * Migración única v7.35: recarga portada y género de todos los libros LEÍDOS.
     * Corrige las portadas rotas por el bug zoom=0 y los géneros erróneos causados
     * por incluir la sinopsis en el pool de candidatos.
     * Se ejecuta una sola vez en background tras la primera carga post-actualización.
     * Velocidad: 1 libro cada 2 s para no saturar las APIs externas.
     */
    fun autoRepairFinishedBooks(prefs: android.content.SharedPreferences) {
        val flagKey = "cover_genre_repair_v800"
        if (prefs.getBoolean(flagKey, false)) return   // ya ejecutado
        // v8.0: repara portadas de todos los libros (no solo FINISHED) y género de cualquiera
        // que esté vacío, sea "Otro" o tenga sólo un género genérico
        val targets = booksInternal.filter { book ->
            book.noCoverFound || book.coverUrl.isNullOrBlank() ||
            book.genres.isEmpty() || book.genres == listOf("Otro") ||
            book.genres == listOf("Historia") || book.genres == listOf("Drama")
        }
        if (targets.isEmpty()) { prefs.edit().putBoolean(flagKey, true).apply(); return }
        viewModelScope.launch {
            targets.forEach { book ->
                // Evitar solapamiento con refreshes manuales en curso
                if (book.id in refreshingCoverIds) {
                    kotlinx.coroutines.delay(2_000)
                    return@forEach
                }
                refreshingCoverIds = refreshingCoverIds + book.id
                try {
                    val activeEdition = editionsForBook(book.id).firstOrNull { it.isActive }
                    val searchTitle = activeEdition?.title?.ifBlank { book.title } ?: book.title
                    val searchIsbn  = activeEdition?.isbn ?: book.isbn
                    val meta = try {
                        fetchBookMetadata(searchTitle, book.author, searchIsbn)
                    } catch (_: Exception) { BookMetadata() }

                    // Portada
                    if (meta.coverUrl != null) {
                        if (activeEdition != null) {
                            upsertEdition(book.id, activeEdition.copy(coverUrl = meta.coverUrl, noCoverFound = false), prefs)
                        }
                        updateCover(book.id, meta.coverUrl, prefs)
                    } else if (book.coverUrl?.contains("zoom=0") == true) {
                        // URL de zoom=0 rota y no encontramos sustituta → limpiar
                        markCoverBroken(book.id, prefs)
                    }

                    // Género (sólo actualizar si encontramos algo mejor o el actual está vacío)
                    if (meta.genres.isNotEmpty() && (book.genres.isEmpty() || book.genres == listOf("Otro"))) {
                        updateGenres(book.id, meta.genres, prefs)
                    }
                } finally {
                    refreshingCoverIds = refreshingCoverIds - book.id
                }
                kotlinx.coroutines.delay(2_000)
            }
            prefs.edit().putBoolean(flagKey, true).apply()
        }
    }

    /** Migración v8.6.1 — Bug 2: corrige ediciones legacy con 🇪🇸 implícito sintético. */
    fun repairLegacyFlags(prefs: android.content.SharedPreferences) {
        val flagKey = "flag_repair_v8601"
        if (prefs.getBoolean(flagKey, false)) return

        // Fecha de lanzamiento de la versión 6 (1 de enero de 2024 UTC)
        val V6_RELEASE_TIMESTAMP = 1704067200000L

        booksInternal = booksInternal.map { book ->
            val editions = book.editions
            if (editions.size == 1 && editions[0].flag == "🇪🇸" && editions[0].language == "es"
                && editions[0].id == book.id
                && book.addedAt < V6_RELEASE_TIMESTAMP) {
                book.copy(editions = listOf(
                    editions[0].copy(
                        language = "unknown",
                        languageLabel = "Edición principal",
                        flag = "🌐"
                    )
                ))
            } else book
        }
        save(prefs)
        prefs.edit().putBoolean(flagKey, true).apply()
    }

    /** Migración v8.9: corrige de nuevo iconos de ediciones sintéticas antiguas marcadas como español. */
    fun repairLegacyEditionIconsV8900(prefs: android.content.SharedPreferences) {
        val flagKey = "legacy_edition_icon_repair_v8900"
        if (prefs.getBoolean(flagKey, false)) return

        val v6ReleaseTimestamp = 1704067200000L
        var changed = false

        booksInternal = booksInternal.map { book ->
            val editions = book.editions
            if (editions.size != 1) return@map book

            val ed = editions[0]
            val sameCachedEdition = ed.id == book.id

            val oldBookSignal =
                book.addedAt <= 0L ||
                book.addedAt < v6ReleaseTimestamp ||
                book.importedFromGoodreads ||
                ed.publisher.isBlank() ||
                ed.publishYear.isBlank()

            val syntheticSpanishIcon =
                ed.language == "es" &&
                ed.flag == "🇪🇸" &&
                (ed.languageLabel.equals("Español", ignoreCase = true) ||
                 ed.languageLabel.equals("Spanish", ignoreCase = true))

            // No resetear libros que el usuario ha empezado a leer — su bandera fue corregida
            val userHasReadBook = !book.startDate.isNullOrBlank()
            if (sameCachedEdition && oldBookSignal && syntheticSpanishIcon && !userHasReadBook) {
                changed = true
                book.copy(editions = listOf(
                    ed.copy(
                        language = "unknown",
                        languageLabel = "Edición principal",
                        flag = "🌐"
                    )
                ))
            } else book
        }

        if (changed) save(prefs)
        prefs.edit().putBoolean(flagKey, true).apply()
    }

    /** Migración v8.6.1 — Bug 3: rehidrata géneros en libros que se saltaron la reparación v8.0. */
    fun genreLogicRepairV8601(prefs: android.content.SharedPreferences) {
        val flagKey = "genre_logic_repair_v8601"
        if (prefs.getBoolean(flagKey, false)) return

        val targets = booksInternal.filter { book ->
            book.genres.isEmpty() ||
            book.genres == listOf("Otro") ||
            book.genres == listOf("Historia") ||
            book.genres == listOf("Drama") ||
            book.genres.size == 1   // candidato a tener 2 géneros con la nueva lógica
        }

        if (targets.isEmpty()) {
            prefs.edit().putBoolean(flagKey, true).apply()
            return
        }

        viewModelScope.launch {
            targets.forEach { book ->
                if (book.id in refreshingCoverIds) {
                    kotlinx.coroutines.delay(1_500)
                    return@forEach
                }
                refreshingCoverIds = refreshingCoverIds + book.id
                try {
                    val activeEdition = editionsForBook(book.id).firstOrNull { it.isActive }
                    val searchTitle   = activeEdition?.title?.ifBlank { book.title } ?: book.title
                    val searchIsbn    = activeEdition?.isbn ?: book.isbn
                    val meta = try {
                        fetchBookMetadata(searchTitle, book.author, searchIsbn)
                    } catch (_: Exception) { BookMetadata() }

                    if (meta.genres.isNotEmpty()) {
                        updateGenres(book.id, meta.genres, prefs)
                    }
                } finally {
                    refreshingCoverIds = refreshingCoverIds - book.id
                }
                kotlinx.coroutines.delay(1_500)  // throttle: 1 libro cada 1.5s
            }
            prefs.edit().putBoolean(flagKey, true).apply()
        }
    }

    /**
     * Migración v14.7: asigna bandera 🇪🇸 a ediciones con language="unknown" y flag="🌐"
     * cuyo título contiene indicios claros de estar en español (artículos, tildes, ñ, signos).
     * Solo actualiza la edición activa. No toca ediciones ya con bandera explícita.
     */
    fun repairSpanishFlagsV1470(prefs: android.content.SharedPreferences) {
        val flagKey = "spanish_flag_repair_v1470"
        if (prefs.getBoolean(flagKey, false)) return

        val spanishStartWords = setOf(
            "el", "la", "los", "las", "un", "una", "unos", "unas",
            "en", "por", "al", "del", "de", "lo", "hay",
            "hábito", "habito", "crónica", "cronica", "historia",
            "mundo", "ciudad", "tierra", "sangre", "sombra", "fuego",
            "hijo", "hija", "rey", "reina", "señor", "señora"
        )
        val spanishCharsRegex = Regex("[áéíóúüñ¿¡]", RegexOption.IGNORE_CASE)

        var changed = false
        booksInternal = booksInternal.map { book ->
            val activeEd = book.editions.firstOrNull { it.isActive } ?: return@map book
            if (activeEd.language != "unknown" || activeEd.flag != "🌐") return@map book

            val titleLower = activeEd.title.ifBlank { book.title }.lowercase()
            val firstWord  = titleLower.trim().split(Regex("\\s+")).firstOrNull() ?: ""

            val isSpanish = firstWord in spanishStartWords ||
                            spanishCharsRegex.containsMatchIn(activeEd.title.ifBlank { book.title })

            if (!isSpanish) return@map book

            changed = true
            val updatedEditions = book.editions.map { ed ->
                if (ed.id == activeEd.id) ed.copy(language = "es", languageLabel = "Español", flag = "🇪🇸")
                else ed
            }
            book.copy(editions = updatedEditions)
        }

        if (changed) save(prefs)
        prefs.edit().putBoolean(flagKey, true).apply()
    }

    /**
     * Migración v18.4 — clasifica las ediciones inglesas existentes con 🌐 en 🇺🇸/🇬🇧
     * cuando publisher o ISBN permite inferirlo con certeza. Solo afecta a ediciones
     * con flag 🌐 y language en ("original", "en", "eng"). El resto se conservan.
     *
     * Corregido en v18.5: usa ed.publisher (BookEdition SÍ guarda publisher).
     */
    fun migrateAnglophoneFlagsV1840(prefs: android.content.SharedPreferences) {
        val flagKey = "anglophone_flags_migration_v1840_v2"
        if (prefs.getBoolean(flagKey, false)) return

        var changed = false
        booksInternal = booksInternal.map { book ->
            val updated = book.editions.map { ed ->
                val isEnglish = ed.language in setOf("original", "en", "eng")
                if (!isEnglish || ed.flag != "🌐") return@map ed
                val refined = inferAnglophoneFlag(ed.isbn, ed.publisher)
                if (refined != null) {
                    changed = true
                    ed.copy(flag = refined)
                } else ed
            }
            if (updated !== book.editions) book.copy(editions = updated) else book
        }

        if (changed) save(prefs)
        prefs.edit().putBoolean(flagKey, true).apply()
    }

    /**
     * Migración v18.9 (CORREGIDA en v19.0): refina banderas 🌐 a 🇺🇸/🇬🇧 SOLO cuando
     * inferAnglophoneFlag(isbn, publisher) da señal real. Si no hay señal, la edición
     * se queda en 🌐 — NO se defaultea a 🇺🇸. El default ciego original rompía libros
     * legítimamente internacionales/sin idioma claro (manga, cómics, ediciones sin
     * publisher/ISBN reconocible, ej. "Invencible Compendium 2").
     */
    fun migrateStuckGlobalFlagsV1880(prefs: android.content.SharedPreferences) {
        val flagKey = "stuck_global_flags_migration_v1881"
        if (prefs.getBoolean(flagKey, false)) return

        var changed = false
        booksInternal = booksInternal.map { book ->
            val updated = book.editions.map { ed ->
                if (ed.flag != "🌐") return@map ed
                val refined = inferAnglophoneFlag(ed.isbn, ed.publisher) ?: return@map ed
                val newLabel = when (refined) {
                    "🇺🇸" -> "Inglés (EE.UU.)"
                    "🇬🇧" -> "Inglés (Reino Unido)"
                    else   -> ed.languageLabel
                }
                changed = true
                ed.copy(flag = refined, language = "original", languageLabel = newLabel)
            }
            if (updated !== book.editions) book.copy(editions = updated) else book
        }

        if (changed) save(prefs)
        prefs.edit().putBoolean(flagKey, true).apply()
    }

    /**
     * Migración v19.0 — Bug: corrige el daño hecho por la migración v18.9 original, que
     * defaulteaba CUALQUIER bandera 🌐 sin señal a 🇺🇸 (sin filtrar idioma/tipo de libro).
     * Esto afectó a manga, cómics y libros internacionales legítimos (ej. "Invencible
     * Compendium 2"), que perdieron su 🌐 correcto.
     * Detecta el daño así: edición con language="original", flag en (🇺🇸,🇬🇧) Y
     * inferAnglophoneFlag(isbn, publisher) devuelve null HOY — es decir, nunca hubo
     * señal real, por lo que esa bandera solo puede venir del default ciego. Se revierte a 🌐.
     */
    fun repairBlindAnglophoneDefaultV1900(prefs: android.content.SharedPreferences) {
        val flagKey = "blind_anglophone_default_repair_v1900"
        if (prefs.getBoolean(flagKey, false)) return

        var changed = false
        booksInternal = booksInternal.map { book ->
            val updated = book.editions.map { ed ->
                if (ed.language != "original" || (ed.flag != "🇺🇸" && ed.flag != "🇬🇧")) return@map ed
                val realSignal = inferAnglophoneFlag(ed.isbn, ed.publisher)
                if (realSignal != null) return@map ed   // bandera con señal real, no toca
                changed = true
                ed.copy(flag = "🌐", languageLabel = "English")
            }
            if (updated !== book.editions) book.copy(editions = updated) else book
        }

        if (changed) save(prefs)
        prefs.edit().putBoolean(flagKey, true).apply()
    }

    fun save(prefs: android.content.SharedPreferences, triggerBackup: Boolean = true) {
        com.lecturameter.repository.BookRepository.save(prefs, booksInternal)
        if (triggerBackup) triggerDriveBackup(prefs)
    }
    fun saveSessions(prefs: android.content.SharedPreferences, triggerBackup: Boolean = true) {
        com.lecturameter.repository.SessionRepository.save(prefs, sessionsInternal)
        if (triggerBackup) triggerDriveBackup(prefs)
    }

    /** Lanza un backup inmediato a Drive en background (no bloquea la UI). Solo actua si hay cuenta conectada. */
    fun triggerDriveBackup(_prefs: android.content.SharedPreferences) {
        val ctx = appContext ?: return
        if (DriveBackupManager.getSignedInAccount(ctx) == null) return
        // Usar OneTimeWorkRequest garantiza que el backup se ejecuta incluso si
        // la app pasa a segundo plano o el ViewModel es destruido antes de que
        // termine la coroutine. REPLACE descarta backups pendientes solapados.
        val request = androidx.work.OneTimeWorkRequestBuilder<DriveBackupWorker>().build()
        androidx.work.WorkManager.getInstance(ctx).enqueueUniqueWork(
            "lecturameter_drive_immediate_backup",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }
    /** Sobrecarga con context por compatibilidad con llamadas explicitas existentes. */
    fun triggerDriveBackup(context: android.content.Context, prefs: android.content.SharedPreferences) {
        if (appContext == null) appContext = context.applicationContext
        triggerDriveBackup(prefs)
    }

    fun setThemeMode(mode: ThemeMode, prefs: android.content.SharedPreferences, context: android.content.Context? = null) {
        themeMode = mode
        prefs.edit().putString("theme_mode", mode.value).apply()
        // Bug fix v21.14: el widget no seguía el tema de la app — refrescarlo aquí también
        if (context != null) {
            viewModelScope.launch { com.lecturameter.widget.updateBookWidgets(context) }
        }
    }
    // Compatibilidad: toggle rápido claro/oscuro desde la topbar
    fun toggleTheme(prefs: android.content.SharedPreferences, context: android.content.Context? = null) {
        val next = if (themeMode == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
        setThemeMode(next, prefs, context)
    }
    val isDarkMode get() = themeMode != ThemeMode.LIGHT
    fun addBook(book: Book, prefs: android.content.SharedPreferences) { booksInternal = listOf(book) + booksInternal; save(prefs) }

    /** v2.5: duplicado = mismo ISBN (no vacío) o mismo título+autor normalizados.
     *  Feedback 2.6 (duplicados inconsistentes): ahora compara también los ISBNs de
     *  todas las ediciones (con equivalencia 10↔13) y los títulos sin el sufijo de
     *  saga "(Saga, #N)" ni puntuación — "El imperio final" ≡ "El imperio final
     *  (Nacidos de la bruma, #1)". */
    fun findDuplicate(candidate: Book): Book? {
        fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase().trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        fun baseTitle(t: String) = norm(t.replace(Regex("""\s*\([^()]*#\s*[\d.\-]+\s*\)\s*$"""), ""))
        fun isbnsOf(b: Book): Set<String> = buildSet {
            canonicalIsbn(b.isbn)?.let { add(it) }
            b.editions.forEach { e -> canonicalIsbn(e.isbn)?.let { add(it) } }
        }
        val cIsbns = isbnsOf(candidate)
        val cTitle = baseTitle(candidate.title)
        val cAuthor = norm(candidate.author)
        return booksInternal.firstOrNull { b ->
            (cIsbns.isNotEmpty() && isbnsOf(b).any { it in cIsbns }) ||
            (cTitle.isNotEmpty() && baseTitle(b.title) == cTitle && norm(b.author) == cAuthor)
        }
    }
    fun deleteBook(id: Long, prefs: android.content.SharedPreferences) { booksInternal = booksInternal.filter { it.id != id }; sessionsInternal = sessionsInternal.filter { it.bookId != id }; save(prefs, triggerBackup = false); saveSessions(prefs, triggerBackup = false) }
    fun updateRating(id: Long, rating: Int, prefs: android.content.SharedPreferences) {
        booksInternal = booksInternal.map { if (it.id == id) it.copy(rating = rating) else it }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { save(prefs) }
        // Fase 5 (MD5): la valoración suele llegar DESPUÉS de terminar el libro —
        // re-evaluar las celdas del Bingo si el libro ya está terminado
        booksInternal.firstOrNull { it.id == id }?.let { b ->
            if (b.status == BookStatus.FINISHED || b.status == BookStatus.REREADING) {
                bingoOnBookFinished(b, prefs)
            }
        }
    }
    fun updateGenres(id: Long, genres: List<String>, prefs: android.content.SharedPreferences) { booksInternal = booksInternal.map { if (it.id == id) it.copy(genres = genres) else it }; save(prefs) }
    @Deprecated("Use updateGenres") fun updateGenre(id: Long, genre: String, prefs: android.content.SharedPreferences) = updateGenres(id, if (genre.isBlank()) emptyList() else listOf(genre), prefs)
    fun updateCover(id: Long, coverUrl: String, prefs: android.content.SharedPreferences) {
        booksInternal = booksInternal.map { book ->
            if (book.id != id) return@map book
            val updatedEditions = if (book.editions.isNotEmpty()) {
                book.editions.map { ed -> if (ed.isActive) ed.copy(coverUrl = coverUrl, noCoverFound = false) else ed }
            } else listOf(
                BookEdition(
                    id = book.id,
                    language = "mul",
                    languageLabel = "Edición principal",
                    flag = "🌐",
                    title = book.title,
                    pages = book.pages,
                    coverUrl = coverUrl,
                    isbn = book.isbn,
                    noCoverFound = false,
                    isActive = true
                )
            )
            book.copy(coverUrl = coverUrl, noCoverFound = false, editions = updatedEditions)
        }
        save(prefs)
    }
    fun updateNoCoverFound(id: Long, noCoverFound: Boolean, prefs: android.content.SharedPreferences) {
        booksInternal = booksInternal.map { book ->
            if (book.id != id) return@map book
            // Actualizar también la edición activa para que upsertEdition no revierta el flag
            val updatedEditions = if (book.editions.isNotEmpty()) {
                book.editions.map { ed -> if (ed.isActive) ed.copy(noCoverFound = noCoverFound) else ed }
            } else {
                // Libro pre-v6 sin ediciones: materializar la edición sintética para persistir el flag
                listOf(BookEdition(
                    id = book.id, language = "mul", languageLabel = "Edición principal", flag = "🌐",
                    title = book.title, pages = book.pages,
                    coverUrl = book.coverUrl, isbn = book.isbn,
                    noCoverFound = noCoverFound, isActive = true
                ))
            }
            book.copy(noCoverFound = noCoverFound, editions = updatedEditions)
        }
        save(prefs)
    }
    /** Marca una portada como rota (fallo de carga en Coil): limpia la URL y activa el badge ⚠️.
     *  Afecta al libro y a la edición activa para que el siguiente refresh busque de cero. */
    fun markCoverBroken(id: Long, prefs: android.content.SharedPreferences) {
        booksInternal = booksInternal.map { book ->
            if (book.id != id) return@map book
            val editions = editionsForBook(id)
            val updatedEditions = editions.map { ed ->
                if (ed.isActive) ed.copy(coverUrl = null, noCoverFound = true) else ed
            }
            book.copy(coverUrl = null, noCoverFound = true,
                editions = if (book.editions.isNotEmpty()) updatedEditions else book.editions)
        }
        save(prefs)
    }
    fun isCoverRefreshing(id: Long): Boolean = id in refreshingCoverIds
    fun refreshCover(
        id: Long,
        prefs: android.content.SharedPreferences,
        onFinished: ((coverFound: Boolean, genreFound: Boolean) -> Unit)? = null
    ) {
        if (id in refreshingCoverIds) return

        val book = booksInternal.find { it.id == id } ?: run {
            onFinished?.invoke(false, false)
            return
        }

        refreshingCoverIds = refreshingCoverIds + id

        viewModelScope.launch {
            // Si hay edición activa, usar su título e ISBN (más específicos)
            // para encontrar la portada correcta (p.ej. edición española con título diferente)
            val activeEdition = editionsForBook(id).firstOrNull { it.isActive }
            val searchTitle = activeEdition?.title?.ifBlank { book.title } ?: book.title
            val searchIsbn  = activeEdition?.isbn ?: book.isbn

            val metadata = try {
                fetchBookMetadata(searchTitle, book.author, searchIsbn)
            } catch (_: Exception) {
                BookMetadata()
            }

            // ── PORTADA ─────────────────────────────────────────────
            val finalCoverFound = when {
                metadata.coverUrl != null -> {
                    // Actualizar también la coverUrl de la edición activa si existe
                    if (activeEdition != null) {
                        val updatedEd = activeEdition.copy(coverUrl = metadata.coverUrl, noCoverFound = false)
                        upsertEdition(id, updatedEd, prefs)
                    }
                    updateCover(id, metadata.coverUrl, prefs)
                    true
                }
                // La edición activa tiene portada válida (no marcada como rota) → mantenerla
                activeEdition != null && !activeEdition.coverUrl.isNullOrBlank() && !activeEdition.noCoverFound -> true
                // Sin edición explícita, el libro tiene portada y no está marcada como rota
                activeEdition == null && !book.coverUrl.isNullOrBlank() && !book.noCoverFound -> true
                else -> {
                    // No hay portada para esta edición: marcar en la edición Y en el libro
                    if (activeEdition != null) {
                        upsertEdition(id, activeEdition.copy(noCoverFound = true), prefs)
                    }
                    updateNoCoverFound(id, true, prefs)
                    false
                }
            }

            // ── GÉNERO ──────────────────────────────────────────────
            val finalGenreFound = when {
                metadata.genres.isNotEmpty() -> {
                    updateGenres(id, metadata.genres, prefs)
                    true
                }
                book.genres.isNotEmpty() && book.genres != listOf("Otro") -> true
                else -> false
            }

            refreshingCoverIds = refreshingCoverIds - id

            onFinished?.invoke(finalCoverFound, finalGenreFound)
        }
    }
    fun refreshGenre(id: Long, prefs: android.content.SharedPreferences, onFinished: ((Boolean) -> Unit)? = null) {
        val book = booksInternal.find { it.id == id } ?: run {
            onFinished?.invoke(false)
            return
        }
        viewModelScope.launch {
            val activeEdition = editionsForBook(id).firstOrNull { it.isActive }
            val searchTitle = activeEdition?.title?.ifBlank { book.title } ?: book.title
            val genres = try {
                fetchBookMetadata(searchTitle, book.author, activeEdition?.isbn ?: book.isbn).genres
            } catch (_: Exception) {
                emptyList()
            }
            if (genres.isNotEmpty()) updateGenres(id, genres, prefs)
            onFinished?.invoke(genres.isNotEmpty())
        }
    }

    fun bulkRefreshGenres(prefs: android.content.SharedPreferences, bookIds: List<Long>?, onProgress: (Int, Int) -> Unit = { _, _ -> }, onDone: (ok: Int, errors: Int) -> Unit = { _, _ -> }) {
        val targets = if (bookIds != null) booksInternal.filter { it.id in bookIds } else booksInternal.toList()
        if (targets.isEmpty()) { onDone(0, 0); return }
        viewModelScope.launch {
            var okCount = 0
            targets.forEachIndexed { idx, book ->
                if (book.id !in refreshingCoverIds) {
                    refreshingCoverIds = refreshingCoverIds + book.id
                    try {
                        val activeEdition = editionsForBook(book.id).firstOrNull { it.isActive }
                        val searchTitle = activeEdition?.title?.ifBlank { book.title } ?: book.title
                        val meta = try { fetchBookMetadata(searchTitle, book.author, activeEdition?.isbn ?: book.isbn) } catch (_: Exception) { BookMetadata() }
                        if (meta.genres.isNotEmpty()) { updateGenres(book.id, meta.genres, prefs); okCount++ }
                    } finally { refreshingCoverIds = refreshingCoverIds - book.id }
                }
                onProgress(idx + 1, targets.size)
                kotlinx.coroutines.delay(1_500)
            }
            onDone(okCount, targets.size - okCount)
        }
    }

    fun bulkRefreshCovers(prefs: android.content.SharedPreferences, bookIds: List<Long>?, onProgress: (Int, Int) -> Unit = { _, _ -> }, onDone: (ok: Int, errors: Int) -> Unit = { _, _ -> }) {
        val targets = if (bookIds != null) booksInternal.filter { it.id in bookIds } else booksInternal.toList()
        if (targets.isEmpty()) { onDone(0, 0); return }
        viewModelScope.launch {
            var okCount = 0
            targets.forEachIndexed { idx, book ->
                if (book.id !in refreshingCoverIds) {
                    refreshingCoverIds = refreshingCoverIds + book.id
                    try {
                        val activeEdition = editionsForBook(book.id).firstOrNull { it.isActive }
                        val searchTitle = activeEdition?.title?.ifBlank { book.title } ?: book.title
                        val meta = try { fetchBookMetadata(searchTitle, book.author, activeEdition?.isbn ?: book.isbn) } catch (_: Exception) { BookMetadata() }
                        if (meta.coverUrl != null) {
                            if (activeEdition != null) upsertEdition(book.id, activeEdition.copy(coverUrl = meta.coverUrl, noCoverFound = false), prefs)
                            updateCover(book.id, meta.coverUrl, prefs)
                            okCount++
                        }
                    } finally { refreshingCoverIds = refreshingCoverIds - book.id }
                }
                onProgress(idx + 1, targets.size)
                kotlinx.coroutines.delay(1_500)
            }
            onDone(okCount, targets.size - okCount)
        }
    }
    fun updateComment(id: Long, comment: String, prefs: android.content.SharedPreferences) { booksInternal = booksInternal.map { if (it.id == id) it.copy(comment = comment) else it }; save(prefs) }

    fun updateEditionComment(bookId: Long, editionId: Long, comment: String, prefs: android.content.SharedPreferences) {
        booksInternal = booksInternal.map { book ->
            if (book.id != bookId) return@map book
            book.copy(editions = book.editions.map { ed ->
                if (ed.id == editionId) ed.copy(comment = comment) else ed
            })
        }
        save(prefs)
    }

    fun activeLanguage(bookId: Long): String =
        editionsForBook(bookId).firstOrNull { it.isActive }?.language ?: "original"

    fun sessionsForBookAndLanguage(bookId: Long, language: String): List<ReadingSession> {
        val bookEditions = editionsForBook(bookId)
        val editionIdToLanguage = bookEditions.associate { it.id to it.language }
        val singleEdition = bookEditions.size <= 1
        return sessionsInternal.filter { s ->
            if (s.bookId != bookId) return@filter false
            if (singleEdition) return@filter true
            val sessionLang = s.editionId?.let { editionIdToLanguage[it] } ?: "original"
            sessionLang == language
        }.sortedByDescending { it.date }
    }
    fun updatePages(id: Long, pages: Int, prefs: android.content.SharedPreferences) {
        booksInternal = booksInternal.map { book ->
            if (book.id != id) return@map book
            // Actualizar tanto book.pages como la edición activa (si existe)
            val updatedEditions = book.editions.map { ed ->
                if (ed.isActive) ed.copy(pages = pages) else ed
            }
            book.copy(pages = pages, editions = updatedEditions)
        }
        save(prefs)
    }

    // ── Gestión de ediciones ──────────────────────────────────────────────────

    /** Devuelve las ediciones de un libro. Si no tiene ninguna definida, sintetiza
     *  una edición implícita a partir de los datos actuales del libro (migración). */
    fun editionsForBook(id: Long): List<BookEdition> {
        val book = booksInternal.find { it.id == id } ?: return emptyList()
        if (book.editions.isNotEmpty()) return book.editions
        // Migración: libro sin ediciones → crear edición implícita
        return listOf(BookEdition(
            id          = book.id,
            language    = "mul",
            languageLabel = "Edición principal",
            flag        = "🌐",
            title       = book.title,
            pages       = book.pages,
            coverUrl    = book.coverUrl,
            isbn        = book.isbn,
            isActive    = true
        ))
    }

    /** Añade o reemplaza una edición en un libro. Si la edición tiene isActive=true,
     *  desactiva el resto y sincroniza los campos cache del libro (coverUrl, pages, isbn). */
    /** v2.4: conserva el sufijo de saga "(Saga, #N)" del título anterior cuando el
     *  título nuevo (de una edición OL/GB) no trae ninguno. Evita que cambiar de
     *  edición borre la saga del libro. El título de la EDICIÓN no se toca. */
    private fun preserveSagaSuffix(oldTitle: String, newTitle: String): String {
        if (newTitle.isBlank()) return oldTitle
        val sagaRegex = Regex("""\s*(\([^()]*#\s*[\d.\-]+\s*\))\s*$""")
        val oldSuffix = sagaRegex.find(oldTitle)?.groupValues?.get(1) ?: return newTitle
        return if (sagaRegex.containsMatchIn(newTitle)) newTitle else "$newTitle $oldSuffix"
    }

    fun upsertEdition(bookId: Long, edition: BookEdition, prefs: android.content.SharedPreferences) {
        booksInternal = booksInternal.map { book ->
            if (book.id != bookId) return@map book
            val currentEditions = editionsForBook(bookId).toMutableList()
            val existingIdx = currentEditions.indexOfFirst { it.id == edition.id }
            if (existingIdx >= 0) currentEditions[existingIdx] = edition
            else currentEditions.add(edition)
            // Si la nueva es activa, desactivar el resto
            val finalEditions = if (edition.isActive)
                currentEditions.map { it.copy(isActive = it.id == edition.id) }
            else currentEditions
            // Sincronizar cache del Book con la edición activa
            val active = finalEditions.firstOrNull { it.isActive } ?: finalEditions.first()
            book.copy(
                editions     = finalEditions,
                title        = preserveSagaSuffix(book.title, active.title.ifBlank { book.title }),
                coverUrl     = active.coverUrl,
                pages        = if (active.pages > 0) active.pages else book.pages,
                isbn         = active.isbn ?: book.isbn,
                noCoverFound = active.noCoverFound
            )
        }
        save(prefs)
        // Auditoría APIs: al añadir/editar una edición manualmente, la caché de
        // resultados de búsqueda queda obsoleta (TTL 7 días) — invalidar.
        EditionCache.clearCacheForBook(bookId)
    }

    /** Elimina una edición. No permite eliminar si es la única. */
    fun removeEdition(bookId: Long, editionId: Long, prefs: android.content.SharedPreferences): Boolean {
        val book = booksInternal.find { it.id == bookId } ?: return false
        val editions = editionsForBook(bookId)
        if (editions.size <= 1) return false
        val updated = editions.filter { it.id != editionId }
        // Si la eliminada era la activa, activar la primera restante
        val needsNewActive = editions.firstOrNull { it.id == editionId }?.isActive == true
        val finalEditions = if (needsNewActive)
            updated.mapIndexed { i, e -> if (i == 0) e.copy(isActive = true) else e }
        else updated
        val active = finalEditions.firstOrNull { it.isActive } ?: finalEditions.first()
        booksInternal = booksInternal.map { b ->
            if (b.id != bookId) b
            else b.copy(
                editions     = finalEditions,
                title        = preserveSagaSuffix(b.title, active.title.ifBlank { b.title }),
                coverUrl     = active.coverUrl,
                pages        = if (active.pages > 0) active.pages else b.pages,
                isbn         = active.isbn ?: b.isbn,
                noCoverFound = active.noCoverFound
            )
        }
        save(prefs, triggerBackup = false)
        EditionCache.clearCacheForBook(bookId)   // auditoría APIs: caché obsoleta tras borrar edición
        return true
    }
    fun setActiveEdition(bookId: Long, editionId: Long, prefs: android.content.SharedPreferences) {
        booksInternal = booksInternal.map { book ->
            if (book.id != bookId) return@map book
            val editions = editionsForBook(bookId).map { it.copy(isActive = it.id == editionId) }
            val active = editions.firstOrNull { it.isActive } ?: return@map book
            book.copy(
                editions     = editions,
                title        = preserveSagaSuffix(book.title, active.title.ifBlank { book.title }),
                coverUrl     = active.coverUrl,
                pages        = if (active.pages > 0) active.pages else book.pages,
                isbn         = active.isbn ?: book.isbn,
                noCoverFound = active.noCoverFound
            )
        }
        save(prefs)
    }

    /** Recarga la portada para una edición concreta */
    fun refreshEditionCover(
        bookId: Long,
        editionId: Long,
        prefs: android.content.SharedPreferences,
        onFinished: ((Boolean) -> Unit)? = null
    ) {
        val book = booksInternal.find { it.id == bookId } ?: return
        val edition = editionsForBook(bookId).firstOrNull { it.id == editionId } ?: return
        viewModelScope.launch {
            // Para búsqueda en español, incluir el título en español si difiere
            val searchTitle = edition.title.ifBlank { book.title }
            val coverUrl = try {
                fetchCoverForBook(searchTitle, book.author, edition.isbn)
            } catch (_: Exception) { null }
            val updated = edition.copy(
                coverUrl     = coverUrl ?: edition.coverUrl,
                noCoverFound = coverUrl == null
            )
            upsertEdition(bookId, updated, prefs)
            // Si esta edición es la activa, también actualizar el noCoverFound del libro
            if (edition.isActive) {
                booksInternal = booksInternal.map { b ->
                    if (b.id == bookId) b.copy(noCoverFound = coverUrl == null) else b
                }
                save(prefs)
            }
            onFinished?.invoke(coverUrl != null)
        }
    }

    /** Busca ediciones disponibles en OpenLibrary + Google Books para el selector "Cambiar edición" */
    fun loadAvailableEditions(
        bookId: Long,
        forceRefresh: Boolean = false,
        // Auditoría APIs r3: feedback progresivo — se invoca en Main con la lista
        // parcial cada vez que una fase termina. Null → sin feedback (igual que antes).
        onPartial: ((List<EditionResult>) -> Unit)? = null,
        onResult: (List<EditionResult>) -> Unit
    ) {
        val book = booksInternal.find { it.id == bookId } ?: return
        if (forceRefresh) EditionCache.clearCacheForBook(bookId)
        // Cancel any previous ongoing load to avoid concurrent network storms and crashes
        loadEditionsJob?.cancel()
        loadEditionsJob = viewModelScope.launch {
            val activeEdition = editionsForBook(bookId).firstOrNull { it.isActive }
            // Usar el ISBN del libro (original) preferentemente para que belongsToBook lo reconozca.
            // Si no hay, caemos al ISBN de la edición activa.
            val baseIsbn = book.isbn ?: activeEdition?.isbn
            val activeTitle = activeEdition?.title?.ifBlank { book.title } ?: book.title
            val pages = if (activeEdition != null && activeEdition.pages > 0) activeEdition.pages else book.pages
            val editions = try {
                EditionCache.getEditions(
                    bookId = bookId,
                    isbn = baseIsbn,
                    title = activeTitle,
                    forceRefresh = forceRefresh
                ) {
                kotlinx.coroutines.withTimeout(95_000L) {   // auditoría r2: 65s → 95s (global 90s + margen)
                    kotlinx.coroutines.coroutineScope {
                        // Auditoría APIs r3: adaptador onPartial → Main thread (Compose state).
                        // Se aplica SOLO a la fase primaria (activeTitle); la secondary es un
                        // refuerzo cuando el título original difiere y su resultado se mergea al
                        // final, sin necesidad de feedback progresivo adicional.
                        val partialAdapter: (suspend (List<EditionResult>) -> Unit)? = onPartial?.let { cb ->
                            { list -> withContext(Dispatchers.Main) { cb(list) } }
                        }
                        val primaryDeferred = async { fetchEditionsForBook(activeTitle, book.author, baseIsbn, pages, partialAdapter) }
                        val secondaryDeferred = if (activeTitle.trim().equals(book.title.trim(), ignoreCase = true)) null
                            else async { fetchEditionsForBook(book.title, book.author, baseIsbn, pages) }
                        val primary = primaryDeferred.await()
                        val secondary = secondaryDeferred?.await() ?: emptyList()
                        val combined = (primary + secondary)
                            .distinctBy { it.isbn ?: "${it.language}|${it.title}|${it.publishYear}" }
                        val activeIsbnClean = activeEdition?.isbn?.let { cleanIsbn(it) }
                        val alreadyHasActive = activeIsbnClean != null && combined.any { cleanIsbn(it.isbn) == activeIsbnClean }
                        if (activeEdition != null && !alreadyHasActive) {
                            val syntheticEs = activeEdition.languageLabel.equals("Español", ignoreCase = true) ||
                                              activeEdition.languageLabel.equals("Spanish", ignoreCase = true)
                            if (syntheticEs) {
                                combined + EditionResult(
                                    language = "es", languageLabel = "Español", flag = "🇪🇸",
                                    title = activeEdition.title.ifBlank { book.title },
                                    pages = activeEdition.pages,
                                    coverUrl = activeEdition.coverUrl,
                                    isbn = activeEdition.isbn,
                                    publisher = "", publishYear = ""
                                )
                            } else combined
                        } else combined
                    }
                }
                } // cierre lambda EditionCache.getEditions
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // BUG v18.x: este timeout es NUESTRO (withTimeout de arriba), no una cancelación
                // externa del job. Antes se relanzaba como CancellationException y onResult()
                // nunca se llamaba → editionsLoading se quedaba en true para siempre (spinner
                // infinito en "Añadir edición"). Ahora se trata como fallo normal: lista vacía.
                emptyList()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelación externa real (ej. loadEditionsJob?.cancel() por una búsqueda nueva).
                // Aquí sí hay que relanzar para que la corrutina se cierre correctamente.
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            onResult(editions)
        }
    }
    fun updateDates(id: Long, startDate: String?, endDate: String?, prefs: android.content.SharedPreferences) {
        booksInternal = booksInternal.map {
            if (it.id == id) {
                // Los libros en estado READING nunca deben tener fecha de fin
                val cleanEnd = if (it.status == BookStatus.READING) null
                               else endDate?.takeIf { s -> s.isNotBlank() }
                it.copy(
                    startDate = startDate?.takeIf { s -> s.isNotBlank() },
                    endDate = cleanEnd
                )
            } else it
        }; save(prefs)
    }

    /** v19.8: actualiza la lista completa de dateEvents y sincroniza los campos legacy. */
    fun updateDateEvents(id: Long, newEvents: List<DateEvent>, prefs: android.content.SharedPreferences) {
        booksInternal = booksInternal.map { b ->
            if (b.id == id) {
                // v20.0 (G6): renumerar occurrences para evitar huecos/duplicados que rompen los labels
                val renumbered = renumberOccurrences(newEvents)
                val sorted = sortedDateEvents(renumbered)
                val firstStart = sorted.firstOrNull { it.type == "start" }?.date
                val firstEnd = sorted.firstOrNull { it.type == "end" }?.date
                val firstDrop = sorted.firstOrNull { it.type == "drop" }?.date
                val firstResume = sorted.firstOrNull { it.type == "resume" }?.date
                b.copy(
                    dateEvents = sorted,
                    startDate = firstStart ?: b.startDate,
                    endDate = firstEnd ?: b.endDate,
                    dropDate = firstDrop,
                    resumedDate = firstResume
                )
            } else b
        }
        // Recalcular readingIndex de todas las sesiones afectadas
        val book = booksInternal.firstOrNull { it.id == id }
        if (book != null) {
            val events = book.dateEvents
            sessionsInternal = sessionsInternal.map { s ->
                if (s.bookId == id) s.copy(readingIndex = computeReadingIndex(s.date, events)) else s
            }
            saveSessions(prefs, triggerBackup = false)
        }
        save(prefs)
    }
    fun updateStatus(id: Long, status: BookStatus, prefs: android.content.SharedPreferences) {
        booksInternal = booksInternal.map {
            if (it.id == id) {
                val sd = when {
                    status == BookStatus.PENDING -> null
                    it.startDate == null -> today()
                    else -> it.startDate
                }
                val ed = when {
                    status == BookStatus.FINISHED && it.endDate == null -> today()
                    status == BookStatus.REREADING -> it.endDate  // preserve original finish date
                    status == BookStatus.READING   -> null         // leyendo = sin fecha de fin
                    status == BookStatus.PENDING   -> null
                    else -> it.endDate
                }
                // Al cambiar a un estado distinto de FINISHED, limpiar el flag isRereading
                val newLastFunctional = if (status == BookStatus.FINISHED && it.lastFunctionalPage == null) {
                    sessionsInternal.filter { s -> s.bookId == id }
                        .maxByOrNull { s -> s.date }?.endPage
                } else null
                // Fecha de abandono: automática al pasar a DROPPED (no se sobreescribe si ya existe)
                val newDropDate = when {
                    status == BookStatus.DROPPED && it.dropDate == null -> today()
                    else -> it.dropDate
                }
                // Fecha de retomado: automática al pasar de DROPPED a READING/REREADING
                val newResumedDate = when {
                    (status == BookStatus.READING || status == BookStatus.REREADING) &&
                        it.status == BookStatus.DROPPED && it.resumedDate == null -> today()
                    else -> it.resumedDate
                }

                // v19.8: registrar eventos automáticos en dateEvents
                val baseEvents = renumberOccurrences(migrateLegacyToEvents(it)).toMutableList()
                val prevStatus = it.status
                val todayStr = today()
                // start inicial si no existe y se pasa a un estado activo
                if (status != BookStatus.PENDING && baseEvents.none { e -> e.type == "start" } && sd != null) {
                    baseEvents.add(DateEvent("start", sd))
                }
                // → DROPPED: añadir drop si el último evento no es drop sin resume posterior
                if (status == BookStatus.DROPPED && prevStatus != BookStatus.DROPPED) {
                    val occ = nextOccurrence(baseEvents, "drop")
                    baseEvents.add(DateEvent("drop", todayStr, occ))
                }
                // DROPPED → READING/REREADING: añadir resume
                if ((status == BookStatus.READING || status == BookStatus.REREADING) && prevStatus == BookStatus.DROPPED) {
                    val occ = nextOccurrence(baseEvents, "resume")
                    baseEvents.add(DateEvent("resume", todayStr, occ))
                }
                // → FINISHED por primera vez: añadir "end" si no hay aún
                if (status == BookStatus.FINISHED && prevStatus != BookStatus.FINISHED && prevStatus != BookStatus.REREADING) {
                    if (baseEvents.none { e -> e.type == "end" }) {
                        baseEvents.add(DateEvent("end", ed ?: todayStr))
                    }
                }
                // → REREADING desde cualquier otro estado: añadir "reread"
                // Bug fix v21.19: antes solo se registraba si prevStatus == FINISHED, que es
                // el único camino que usa el botón dedicado "Releer" (toggleRereading, siempre
                // gateado a FINISHED). Pero el desplegable genérico de estado permite pasar a
                // REREADING desde cualquier estado (Leyendo, Pendiente, Abandonado...) sin pasar
                // por ese botón — en esos casos nunca se registraba el evento y la relectura no
                // aparecía como iniciada en el historial de fechas.
                if (status == BookStatus.REREADING && prevStatus != BookStatus.REREADING) {
                    val occ = nextOccurrence(baseEvents, "reread")
                    baseEvents.add(DateEvent("reread", todayStr, occ))
                }
                // REREADING → FINISHED: añadir "reread_end"
                if (status == BookStatus.FINISHED && prevStatus == BookStatus.REREADING) {
                    val occ = nextOccurrence(baseEvents, "reread_end")
                    baseEvents.add(DateEvent("reread_end", todayStr, occ))
                }

                it.copy(status = status, startDate = sd, endDate = ed,
                    isRereading = if (status != BookStatus.FINISHED) false else it.isRereading,
                    lastFunctionalPage = newLastFunctional ?: it.lastFunctionalPage,
                    dropDate = newDropDate,
                    resumedDate = newResumedDate,
                    // v20.0 (G6): renumerar occurrences para garantizar 1..N consecutivas
                    dateEvents = renumberOccurrences(baseEvents.toList()))
            } else it
        }
        // Recalcular readingIndex de sesiones del libro tras cambio de status
        val updatedBook = booksInternal.firstOrNull { it.id == id }
        // Fase 5 (MD5): al pasar a FINISHED se evalúan las celdas del Bingo
        if (status == BookStatus.FINISHED && updatedBook != null) bingoOnBookFinished(updatedBook, prefs)
        if (updatedBook != null) {
            val events = updatedBook.dateEvents
            // v20.4 (B5): al pasar a REREADING, NO mover sesiones de Lectura a Relectura.
            // Solo recalcular sesiones que tengan readingIndex == null (nunca asignado).
            // Las sesiones con readingIndex ya asignado (0 = Lectura, N = RelecturaN) se preservan.
            sessionsInternal = sessionsInternal.map { s ->
                if (s.bookId == id) {
                    if (s.readingIndex == null) {
                        s.copy(readingIndex = computeReadingIndex(s.date, events))
                    } else s
                } else s
            }
            saveSessions(prefs, triggerBackup = false)
        }
        // Si el libro ya no está en lectura activa, quitarlo del widget
        if (status != BookStatus.READING && status != BookStatus.REREADING) {
            val ctx = appContext
            if (ctx != null && com.lecturameter.widget.loadWidgetBook(ctx) == id) {
                com.lecturameter.widget.saveWidgetBook(ctx, -1L)
                com.lecturameter.widget.requestBookWidgetUpdate(ctx)
            }
        }
        save(prefs)
    }

    fun toggleRereading(id: Long, prefs: android.content.SharedPreferences) {
        val book = booksInternal.firstOrNull { it.id == id } ?: return
        // v19.8: si está releyendo (REREADING o flag isRereading) → terminar relectura
        // si no, empezar relectura (debe estar FINISHED)
        val isCurrentlyRereading = book.status == BookStatus.REREADING || book.isRereading
        if (isCurrentlyRereading) {
            // Terminar relectura: REREADING → FINISHED registra reread_end
            updateStatus(id, BookStatus.FINISHED, prefs)
        } else if (book.status == BookStatus.FINISHED) {
            // Empezar relectura: FINISHED → REREADING registra reread
            updateStatus(id, BookStatus.REREADING, prefs)
        }
    }

    // ── Sessions ───────────────────────────────────────────────────────────────
    fun addSession(session: ReadingSession, prefs: android.content.SharedPreferences) {
        // v19.8: calcular readingIndex automáticamente según fecha y eventos del libro
        val book = booksInternal.firstOrNull { it.id == session.bookId }
        val finalSession = if (book != null) {
            val events = migrateLegacyToEvents(book)
            session.copy(readingIndex = computeReadingIndex(session.date, events))
        } else session
        sessionsInternal = listOf(finalSession) + sessionsInternal
        saveSessions(prefs)
        // Fase 5 (MD5): la racha puede haber crecido → evaluar celdas streak del Bingo
        bingoOnSession(prefs)
    }
    fun deleteSession(sessionId: Long, prefs: android.content.SharedPreferences) {
        sessionsInternal = sessionsInternal.filter { it.id != sessionId }
        saveSessions(prefs, triggerBackup = false)
    }
    /** v20.0: borra varias sesiones a la vez (botón "Eliminar todas" del historial). */
    fun deleteSessions(sessionIds: Collection<Long>, prefs: android.content.SharedPreferences) {
        if (sessionIds.isEmpty()) return
        val toRemove = sessionIds.toHashSet()
        sessionsInternal = sessionsInternal.filter { it.id !in toRemove }
        saveSessions(prefs, triggerBackup = false)
    }
    fun updateSession(updated: ReadingSession, prefs: android.content.SharedPreferences) {
        sessionsInternal = sessionsInternal.map { if (it.id == updated.id) updated else it }
        saveSessions(prefs)
    }
    fun updateFunctionalPages(bookId: Long, firstPage: Int?, lastPage: Int?, prefs: android.content.SharedPreferences) {
        booksInternal = booksInternal.map {
            if (it.id == bookId) it.copy(firstFunctionalPage = firstPage, lastFunctionalPage = lastPage) else it
        }; save(prefs)
    }

    fun sessionsForBook(bookId: Long): List<ReadingSession> =
        sessionsInternal.filter { it.bookId == bookId }.sortedByDescending { it.date }

    /**
     * Aplana la biblioteca a un libro virtual por edición (v18.3).
     *
     * Para libros con 2+ ediciones, genera una copia de Book por cada edición con sus
     * datos específicos (título, páginas, portada, ISBN, lengua). El `book.id` se
     * conserva para que las sesiones se sigan asociando al libro padre. Cada entrada
     * mantiene su única edición marcada como `isActive=true` para que los donuts de
     * idioma y similares lean el idioma correcto.
     *
     * Libros con 0 o 1 edición devuelven la lista normal (sin duplicar).
     */
    fun booksByEdition(): List<Book> = booksInternal.flatMap { book ->
        val eds = book.editions
        if (eds.size <= 1) listOf(book)
        else eds.map { ed ->
            book.copy(
                title    = ed.title.ifBlank { book.title },
                pages    = ed.pages.takeIf { it > 0 } ?: book.pages,
                coverUrl = ed.coverUrl,
                isbn     = ed.isbn,
                noCoverFound = ed.noCoverFound,
                editions = listOf(ed.copy(isActive = true))
            )
        }
    }

    // ── Year Wrapped ───────────────────────────────────────────────────────────
    // Fase 1.2/D-004: migrado de mutableStateOf a StateFlow (la UI colecciona en su raíz)
    private val _wrappedHistory = kotlinx.coroutines.flow.MutableStateFlow<List<YearWrapped>>(emptyList())
    val wrappedHistory: kotlinx.coroutines.flow.StateFlow<List<YearWrapped>> = _wrappedHistory
    /** Escritura controlada para BackupRepository (restauración de backups). */
    fun setWrappedHistory(history: List<YearWrapped>) { _wrappedHistory.value = history }

    private fun loadWrapped(prefs: android.content.SharedPreferences) {
        com.lecturameter.repository.WrappedRepository.loadOrNull(prefs)?.let { _wrappedHistory.value = it }
    }
    private fun saveWrapped(prefs: android.content.SharedPreferences) {
        com.lecturameter.repository.WrappedRepository.save(prefs, _wrappedHistory.value)
        triggerDriveBackup(prefs)
    }
    fun saveWrappedForYear(wrapped: YearWrapped, prefs: android.content.SharedPreferences) {
        // v2.6: embeber los favoritos congelados del año (viajan en backups/historial)
        val listType = object : com.google.gson.reflect.TypeToken<List<Long>>() {}.type
        val frozenIds: List<Long>? = prefs.getString("wrapped_favs_${wrapped.year}", null)?.let {
            try { gson.fromJson<List<Long>>(it, listType) } catch (_: Exception) { null }
        }
        val enriched = if (frozenIds != null) wrapped.copy(favoriteBookIds = frozenIds) else wrapped
        // Reemplaza si ya existe ese año, si no añade
        _wrappedHistory.value = listOf(enriched) + _wrappedHistory.value.filter { it.year != enriched.year }
        saveWrapped(prefs)
    }
    fun wrappedForYear(year: Int): YearWrapped? = _wrappedHistory.value.find { it.year == year }

    fun computeWrapped(year: Int): YearWrapped? {
        val finished = booksInternal.filter {
            it.status == BookStatus.FINISHED &&
            it.endDate != null && it.startDate != null &&
            it.endDate.startsWith(year.toString()) &&
            !it.importedFromGoodreads
        }
        // v19.8: relecturas completadas en el año (reread_end events del año)
        val rereadsThisYear = booksInternal.filter { !it.importedFromGoodreads }.flatMap { b ->
            b.dateEvents.filter { it.type == "reread_end" && it.date.startsWith(year.toString()) }
                .map { b to it }
        }
        if (finished.isEmpty() && rereadsThisYear.isEmpty()) return null

        val rereadCount = rereadsThisYear.size
        val totalBooks = finished.size + rereadCount
        // v19.8: páginas de las relecturas también se suman
        val totalPages = finished.sumOf { it.pages } + rereadsThisYear.sumOf { (b, _) -> b.pages }
        // Exclude same-day booksInternal (startDate == endDate) from speed-based stats
        val multiDayBooks = finished.filter { it.startDate != it.endDate && daysBetween(it.startDate!!, it.endDate!!) >= 2 }
        val speeds = multiDayBooks.map { b ->
            val d = daysBetween(b.startDate!!, b.endDate!!)
            Pair(b, b.pages.toDouble() / d)
        }
        val avgPpd = if (speeds.isNotEmpty()) speeds.map { it.second }.average() else 0.0
        val avgDays = if (multiDayBooks.isNotEmpty()) multiDayBooks.map { b -> daysBetween(b.startDate!!, b.endDate!!).toDouble() }.average() else 0.0

        val favAuthorEntry = finished.filter { it.author.isNotBlank() }
            .groupBy { it.author }.maxByOrNull { it.value.size }
        val favGenreEntry = finished.flatMap { b -> b.genres.map { g -> g to b } }
            .groupBy({ it.first }, { it.second }).maxByOrNull { it.value.size }

        val fastest = if (speeds.isNotEmpty()) speeds.maxByOrNull { it.second } else null
        val fastestTop3 = speeds.sortedByDescending { it.second }.take(3).map { (b, ppd) -> Triple(b.title, ppd, b.pages) }
        val bestRated = finished.filter { it.rating > 0 }.maxByOrNull { it.rating }
        // v21.41: top 3 libros por nota, desempate por endDate desc (los más recientes primero)
        val bestRatedTop3List = finished
            .filter { it.rating > 0 }
            .sortedWith(compareByDescending<Book> { it.rating }.thenByDescending { it.endDate ?: "" })
            .take(3)
            .map { Triple(it.title, it.rating, it.endDate ?: "") }

        // Racha más larga: días consecutivos con al menos 1 libro terminado
        // v1.4 fix crash: si el año solo tiene relecturas, finished está vacío →
        // endDates.first() lanzaba NoSuchElementException. Guard + valores neutros.
        val endDates = finished.map { it.endDate!! }.toSortedSet()
        var maxStreak = if (endDates.isEmpty()) 0 else 1
        var streakStart = endDates.firstOrNull() ?: ""
        var streakEnd = endDates.firstOrNull() ?: ""
        if (endDates.isNotEmpty()) {
            var streak = 1
            var curStart = endDates.first()
            val dateList = endDates.toList()
            for (i in 1 until dateList.size) {
                val prev = dateList[i - 1]; val curr = dateList[i]
                if (daysBetween(prev, curr) == 1) {
                    streak++
                    if (streak > maxStreak) { maxStreak = streak; streakStart = curStart; streakEnd = curr }
                } else { streak = 1; curStart = curr }
            }
        }

        // ── v18.3: nuevos cálculos ──────────────────────────────────────────
        // Sesiones del año (basadas en su fecha, no en el libro)
        val sessionsInYear = sessionsInternal.filter { it.date.startsWith(year.toString()) }
        val totalSessions = sessionsInYear.size
        val totalMinutes = sessionsInYear.mapNotNull { it.minutes }.sum()
        val maxSession = sessionsInYear.maxByOrNull { it.pages }
        val maxSessionPages = maxSession?.pages ?: 0
        val maxSessionDate = maxSession?.date ?: ""
        // Día con más páginas leídas
        val pagesByDay = sessionsInYear.groupBy { it.date }.mapValues { (_, lst) -> lst.sumOf { it.pages } }
        val mostReadEntry = pagesByDay.maxByOrNull { it.value }
        val mostReadDay = mostReadEntry?.key ?: ""
        val mostReadDayPages = mostReadEntry?.value ?: 0
        // ── v2.6 (Wrapped r1): día top de cada mes + desglose del mejor día global ──
        val bestDayPerMonth = pagesByDay.entries
            .groupBy { it.key.substring(5, 7).toIntOrNull()?.minus(1) ?: -1 }
            .filterKeys { it in 0..11 }
            .mapNotNull { (m, entries) ->
                entries.maxByOrNull { it.value }?.let { best -> Triple(m, best.key, best.value) }
            }
            .sortedBy { it.first }
        val bestDaySessionsList = if (mostReadDay.isNotBlank()) sessionsInYear.filter { it.date == mostReadDay } else emptyList()
        val bestDaySessions = bestDaySessionsList.size
        val bestDayBooks = bestDaySessionsList.map { it.bookId }.distinct().size
        val bestDayMinutes = bestDaySessionsList.sumOf { it.minutes ?: 0 }
        val bestDayPagesPerMin = if (bestDayMinutes > 0) mostReadDayPages.toDouble() / bestDayMinutes else 0.0
        // v2.6: páginas por franja horaria de 3h (mismo criterio que buildHeatmapData:
        // solo sesiones con startTimestamp; páginas mínimas 1 para que cuenten)
        val pagesPerTimeSlot = IntArray(8)
        val slotCal = java.util.Calendar.getInstance()
        sessionsInYear.forEach { s ->
            val ts = s.startTimestamp ?: return@forEach
            if (ts <= 0) return@forEach
            slotCal.timeInMillis = ts
            pagesPerTimeSlot[slotCal.get(java.util.Calendar.HOUR_OF_DAY) / 3] += s.pages.coerceAtLeast(1)
        }
        // Libros abandonados ese año (dropDate dentro del año)
        val droppedList = booksInternal.filter { b ->
            b.status == BookStatus.DROPPED &&
            (b.dropDate?.startsWith(year.toString()) == true) &&
            !b.importedFromGoodreads
        }
        val droppedBooks = droppedList.size
        val droppedBookTitles = droppedList.map { it.title }
        // Top 3 autores y géneros
        // v18.6: cada autor lleva (nº libros únicos, nº ediciones totales). Los autores
        // se ordenan por libros únicos (no por ediciones). Si Sanderson tiene 1 libro en
        // 2 ediciones, aparece como "1 libro (2 ediciones distintas)" en Wrapped.
        val topAuthorsGrouped = finished.filter { it.author.isNotBlank() }
            .groupBy { it.author }
            .entries.sortedByDescending { it.value.size }
            .take(3)
        val topAuthorsTop3 = topAuthorsGrouped.map { it.key to it.value.size }
        val topAuthorsTop3Editions = topAuthorsGrouped.map { entry ->
            entry.value.sumOf { b -> b.editions.size.coerceAtLeast(1) }
        }
        val topGenresTop3 = finished.flatMap { b -> b.genres.map { g -> g to b } }
            .groupBy({ it.first }, { it.second })
            .entries.sortedByDescending { it.value.size }
            .take(3)
            .map { it.key to it.value.size }

        // v18.4: páginas y libros por mes
        val pagesPerMonth = IntArray(12)
        val booksPerMonth = IntArray(12)
        sessionsInYear.forEach { s ->
            val m = s.date.substring(5, 7).toIntOrNull()?.minus(1)
            if (m != null && m in 0..11) pagesPerMonth[m] += s.pages
        }
        finished.forEach { b ->
            val m = b.endDate!!.substring(5, 7).toIntOrNull()?.minus(1)
            if (m != null && m in 0..11) booksPerMonth[m] += 1
        }
        // Comparativa año anterior
        val prevYearFinished = booksInternal.filter { b ->
            b.status == BookStatus.FINISHED && b.endDate != null &&
            b.endDate.startsWith((year - 1).toString()) &&
            !b.importedFromGoodreads
        }
        val previousYearBooks = prevYearFinished.size
        val previousYearPages = prevYearFinished.sumOf { it.pages }

        // v18.5: distribución por género (género principal de cada libro, top 6 para donut)
        val genreCountsTop6 = finished
            .mapNotNull { b -> (b.genres.firstOrNull()?.ifBlank { null }) ?: b.genre.ifBlank { null } }
            .groupBy { it }
            .entries.sortedByDescending { it.value.size }
            .take(6)
            .map { it.key to it.value.size }

        // v19.3: top 3 libros por minutos totales de sesión en el año
        val longestBooksTop3 = sessionsInternal
            .filter { it.date.startsWith(year.toString()) && (it.minutes ?: 0) > 0 }
            .groupBy { it.bookId }
            .mapNotNull { (bookId, sess) ->
                val book = booksInternal.find { it.id == bookId } ?: return@mapNotNull null
                val totalMins = sess.sumOf { it.minutes ?: 0 }
                if (totalMins > 0) Pair(book.title, totalMins) else null
            }
            .sortedByDescending { it.second }
            .take(3)

        return YearWrapped(
            year = year,
            totalBooks = totalBooks,
            totalPages = totalPages,
            avgPagesPerDay = avgPpd,
            avgDaysPerBook = avgDays,
            favoriteAuthor = favAuthorEntry?.key ?: "",
            favoriteAuthorBooks = favAuthorEntry?.value?.size ?: 0,
            favoriteGenre = favGenreEntry?.key ?: "",
            favoriteGenreBooks = favGenreEntry?.value?.size ?: 0,
            fastestBookTitle = fastest?.first?.title ?: "",
            fastestBookPpd = fastest?.second ?: 0.0,
            fastestBookPages = fastest?.first?.pages ?: 0,
            bestRatedTitle = bestRated?.title ?: "",
            bestRatedScore = bestRated?.rating ?: 0,
            longestStreakDays = maxStreak,
            longestStreakStart = streakStart,
            longestStreakEnd = streakEnd,
            totalSessions = totalSessions,
            totalMinutes = totalMinutes,
            maxSessionPages = maxSessionPages,
            maxSessionDate = maxSessionDate,
            mostReadDay = mostReadDay,
            mostReadDayPages = mostReadDayPages,
            droppedBooks = droppedBooks,
            topAuthorsTop3 = topAuthorsTop3,
            topAuthorsTop3Editions = topAuthorsTop3Editions,
            topGenresTop3 = topGenresTop3,
            pagesPerMonth = pagesPerMonth.toList(),
            booksPerMonth = booksPerMonth.toList(),
            previousYearBooks = previousYearBooks,
            previousYearPages = previousYearPages,
            genreCountsTop6 = genreCountsTop6,
            longestBooksTop3 = longestBooksTop3,
            rereadBooks = rereadCount,
            bestRatedTop3 = bestRatedTop3List,
            fastestBooksTop3 = fastestTop3,
            droppedBookTitles = droppedBookTitles,
            bestDayPerMonth = bestDayPerMonth,
            bestDaySessions = bestDaySessions,
            bestDayBooks = bestDayBooks,
            bestDayPagesPerMin = bestDayPagesPerMin,
            pagesPerTimeSlot = pagesPerTimeSlot.toList()
        )
    }

    fun importFromGoodreads(context: Context, uri: Uri, prefs: android.content.SharedPreferences): Int {
        var imported = 0
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return 0
            val reader = BufferedReader(InputStreamReader(stream))
            val header = reader.readLine() ?: return 0
            val cols = header.split(",").map { it.trim().trim('"') }
            val idxTitle = cols.indexOfFirst { it.contains("Title", true) }
            val idxAuthor = cols.indexOfFirst { it.contains("Author", true) }
            val idxPages = cols.indexOfFirst { it.contains("Pages", true) }
            val idxStatus = cols.indexOfFirst { it.contains("Shelf", true) || it.contains("Status", true) }
            val idxRating = cols.indexOfFirst { it.contains("My Rating", true) }
            val idxDateRead = cols.indexOfFirst { it.contains("Date Read", true) }
            val idxISBN = cols.indexOfFirst { it.contains("ISBN", true) }
            val idxBookshelves = cols.indexOfFirst { it.equals("Bookshelves", true) }
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val parts = parseCsvLine(line)
                val title = parts.getOrNull(idxTitle)?.trim()?.trim('"') ?: return@forEachLine
                if (title.isBlank()) return@forEachLine
                val author = parts.getOrNull(idxAuthor)?.trim()?.trim('"') ?: ""
                val pages = parts.getOrNull(idxPages)?.trim()?.trim('"')?.toIntOrNull() ?: 0
                val shelfRaw = parts.getOrNull(idxStatus)?.trim()?.trim('"')?.lowercase() ?: ""
                val ratingRaw = parts.getOrNull(idxRating)?.trim()?.trim('"')?.toIntOrNull() ?: 0
                val dateRead = parts.getOrNull(idxDateRead)?.trim()?.trim('"')?.let { dr ->
                    if (dr.matches(Regex("\\d{4}/\\d{2}/\\d{2}"))) dr.replace("/", "-") else null
                }
                val isbn = parts.getOrNull(idxISBN)?.trim()?.trim('"')?.replace("=", "")?.replace("\"", "")?.trimStart('0')
                    ?.let { raw -> if (raw.length >= 10 && raw.matches(Regex("^[0-9X]+$"))) raw else null }
                val rating10 = if (ratingRaw in 1..5) ratingRaw * 2 else 0
                val status = when {
                    shelfRaw.contains("read") && !shelfRaw.contains("to") && !shelfRaw.contains("currently") -> BookStatus.FINISHED
                    shelfRaw.contains("currently") || shelfRaw.contains("reading") -> BookStatus.READING
                    else -> BookStatus.PENDING
                }
                val coverUrl = if (!isbn.isNullOrBlank()) "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg" else null
                // Auto-assign genre: try Bookshelves column first, then infer from title
                val bookshelvesRaw = if (idxBookshelves >= 0) parts.getOrNull(idxBookshelves)?.trim()?.trim('"') ?: "" else ""
                // Solo intentar inferir género desde la columna Bookshelves (etiquetas reales de Goodreads).
                // No intentar inferir desde el título — produce demasiados falsos positivos.
                val autoGenres = if (bookshelvesRaw.isNotBlank()) mapApiGenre(bookshelvesRaw) else emptyList()
                booksInternal = listOf(Book(id = System.currentTimeMillis() + imported, title = title, author = author, pages = if (pages > 0) pages else 1, startDate = if (status != BookStatus.PENDING) (dateRead ?: today()) else null, endDate = if (status == BookStatus.FINISHED) dateRead else null, status = status, rating = rating10, coverUrl = coverUrl, isbn = isbn, genres = autoGenres, importedFromGoodreads = true)) + booksInternal
                imported++
            }
            if (imported > 0) save(prefs)
        } catch (e: Exception) { e.printStackTrace() }
        return imported
    }
}
