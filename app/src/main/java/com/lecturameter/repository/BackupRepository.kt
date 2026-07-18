// Fase 1.3: BackupRepository — JSON local, restore, CSV y portadas base64,
// migrado integro desde MainActivity.kt. Sin cambios de logica.
// Mantiene package com.lecturameter para no romper referencias (igual que SearchRepository).
package com.lecturameter

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

// ── JSON Backup / Restore ─────────────────────────────────────────────────────

data class FullBackup(
    val version: Int = 5,
    val exportedAt: Long = System.currentTimeMillis(),
    val books: List<Book>? = null,
    val sessions: List<ReadingSession>? = null,
    val wrappedHistory: List<YearWrapped>? = null,
    // v3 (16-07-2026): el cartón del Bingo del mes, su historial mensual y los retos
    // también viajan. Hasta ahora una reinstalación los perdía (el cartón 6/16 de julio
    // de Víctor se perdió así). Claves nuevas = compatibles: la 2.7 y los backups v2
    // simplemente no las traen y quedan a null.
    val challenges: List<Challenge>? = null,
    val bingoCard: BingoCard? = null,
    val bingoMonthHistory: List<com.lecturameter.utils.BingoMonthSummary>? = null,
    // v4 (D-016): historial de retos archivados. Misma regla de compatibilidad.
    val challengeHistory: List<ChallengeSnapshot>? = null,
    // v5 (B4, 2): el cartón 3×3 de Pro. `bingoCard` sigue siendo el 4×4 y NO cambia de
    // significado, para que un backup v3/v4 restaure exactamente igual que antes. Un
    // cartón que no se respalda es una regresión, y el 3×3 cuesta de llenar: si no
    // viajara, reinstalar le borraría a un Pro el reto del mes.
    val bingoCard3: BingoCard? = null,
    // v5 (M5, revisión de lanzamiento 19-07): el tema activo. El backup NO llevaba ningún
    // ajuste, y eso se volvió un problema de dinero al estrenar el paywall: un usuario de la
    // 2.7 con Aurora (gratis entonces) que migre por JSON instala con 0 libros, importa el
    // backup y su tema no vuelve. Al arranque siguiente el grandfathering one-shot mira
    // theme_mode, ve el "dark" por defecto, se marca como hecho y no concede nada: Aurora
    // pasa a ser de pago para quien ya lo tenía gratis. Con el tema dentro del backup, el
    // import concede el heredado ANTES de que el one-shot se queme.
    // Clave nueva = compatible: los backups v3/v4 y los de la 2.7 la traen a null.
    val themeMode: String? = null
)

/**
 * Constructor ÚNICO del backup (v3) desde prefs — lo usan el export manual, el worker
 * local de 2h y el de Drive, que antes duplicaban esta lectura cada uno por su lado.
 *
 * Devuelve null con la biblioteca vacía (0 libros y 0 sesiones): los workers mantienen
 * UN SOLO fichero por carpeta (borran por prefijo y sobrescriben), así que una pasada
 * en vacío —p. ej. el arranque tras una reinstalación, ANTES de restaurar— pisaría el
 * único backup bueno con uno de 85 bytes. Visto en el dispositivo real el 16-07.
 */
fun buildFullBackupFromPrefs(prefs: android.content.SharedPreferences): FullBackup? {
    val gson = Gson()
    val books: List<Book> = gson.fromJson(
        prefs.getString("books", "[]"),
        object : TypeToken<List<Book>>() {}.type
    ) ?: emptyList()
    val sessions: List<ReadingSession> = gson.fromJson(
        prefs.getString("sessions", "[]"),
        object : TypeToken<List<ReadingSession>>() {}.type
    ) ?: emptyList()
    if (books.isEmpty() && sessions.isEmpty()) return null
    val wrapped: List<YearWrapped> = gson.fromJson(
        prefs.getString("wrapped_history", "[]"),
        object : TypeToken<List<YearWrapped>>() {}.type
    ) ?: emptyList()
    val booksWithCovers = books.map { book ->
        val embCover = embedLocalCoverUrl(book.coverUrl)
        val embEditions = book.editions.map { ed -> ed.copy(coverUrl = embedLocalCoverUrl(ed.coverUrl)) }
        book.copy(coverUrl = embCover, editions = embEditions)
    }
    return FullBackup(
        books = booksWithCovers,
        sessions = sessions,
        wrappedHistory = wrapped,
        challenges = com.lecturameter.repository.ChallengeRepository.loadOrNull(prefs),
        bingoCard = com.lecturameter.repository.BingoRepository.loadOrNull(prefs, BingoManager.SIDE_4),
        bingoMonthHistory = com.lecturameter.utils.BingoManager.loadMonthSummaries(prefs).ifEmpty { null },
        challengeHistory = com.lecturameter.repository.ChallengeHistoryRepository.load(prefs).ifEmpty { null },
        bingoCard3 = com.lecturameter.repository.BingoRepository.loadOrNull(prefs, BingoManager.SIDE_3),
        // M5: el tema tal cual está en prefs (null si nunca se tocó). Solo el tema: esto NO
        // es la puerta de entrada para meter el resto de ajustes en el backup, que no toca
        // a dos días del lanzamiento.
        themeMode = prefs.getString("theme_mode", null)
    )
}

/** Convierte portadas locales (rutas absolutas a filesDir) en base64 data URIs
 *  para que sobrevivan al backup/restore. Las URLs https se dejan tal cual. */
internal fun embedLocalCoverUrl(url: String?): String? {
    if (url == null || url.startsWith("http") || url.startsWith("data:")) return url
    return try {
        val file = java.io.File(url)
        // A2: si el fichero local ya no existe, devolver null (no la ruta cruda). Embeber una
        // ruta /data/... en el backup dejaría la portada rota para siempre al restaurar en otro
        // dispositivo; con null, el flujo normal de "portada rota se recarga" la recupera.
        if (!file.exists()) return null
        // Seguridad: solo leer si el archivo está bajo una ruta esperada de portadas locales.
        // Esto evita que un coverUrl manipulado (vía backup malicioso restaurado, por ejemplo)
        // exfiltre archivos privados de la app en el siguiente backup.
        val canonical = file.canonicalPath
        if (!canonical.contains("/files/covers/") && !canonical.contains("/cache/")) return url
        if (file.length() > 5_000_000L) return url
        val bytes = file.readBytes()
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    } catch (_: Exception) { url }
}

private fun embedLocalCoversForExport(context: Context, books: List<Book>): List<Book> =
    books.map { book ->
        val embeddedCover = embedLocalCoverUrl(book.coverUrl)
        val embeddedEditions = book.editions.map { ed ->
            ed.copy(coverUrl = embedLocalCoverUrl(ed.coverUrl))
        }
        book.copy(coverUrl = embeddedCover, editions = embeddedEditions)
    }

/** Al restaurar, extrae base64 data URIs y las guarda de nuevo en filesDir. */
private fun restoreLocalCoversFromBackup(context: Context, books: List<Book>): List<Book> =
    books.map { book ->
        val url = book.coverUrl ?: return@map book
        if (!url.startsWith("data:image")) return@map book
        try {
            // Seguridad: validar tipo MIME explícito
            val mimeType = url.substringAfter("data:").substringBefore(";")
            if (mimeType !in listOf("image/jpeg", "image/png", "image/webp")) return@map book

            val b64 = url.substringAfter("base64,")

            // Seguridad: rechazar payloads > 5 MB (Base64 de 5 MB ≈ 6.67 M chars)
            if (b64.length > 6_700_000) return@map book

            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)

            // Seguridad: validar magic bytes — JPEG (FF D8 FF) o PNG (89 50 4E 47)
            val isValidImage = when {
                bytes.size >= 3 &&
                    bytes[0] == 0xFF.toByte() &&
                    bytes[1] == 0xD8.toByte() &&
                    bytes[2] == 0xFF.toByte() -> true  // JPEG
                bytes.size >= 4 &&
                    bytes[0] == 0x89.toByte() &&
                    bytes[1] == 0x50.toByte() &&
                    bytes[2] == 0x4E.toByte() &&
                    bytes[3] == 0x47.toByte() -> true  // PNG
                else -> false
            }
            if (!isValidImage) return@map book

            val coversDir = java.io.File(context.filesDir, "covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val dest = java.io.File(coversDir, "${book.id}.jpg")
            dest.writeBytes(bytes)
            val restoredEditions = book.editions.map { ed ->
                val edUrl = ed.coverUrl ?: return@map ed
                if (!edUrl.startsWith("data:image")) return@map ed
                try {
                    val edMime = edUrl.substringAfter("data:").substringBefore(";")
                    if (edMime !in listOf("image/jpeg", "image/png", "image/webp")) return@map ed
                    val edB64 = edUrl.substringAfter("base64,")
                    if (edB64.length > 6_700_000) return@map ed
                    val edBytes = android.util.Base64.decode(edB64, android.util.Base64.DEFAULT)
                    val coversDir2 = java.io.File(context.filesDir, "covers")
                    if (!coversDir2.exists()) coversDir2.mkdirs()
                    val edDest = java.io.File(coversDir2, "${book.id}_${ed.id}.jpg")
                    edDest.writeBytes(edBytes)
                    ed.copy(coverUrl = edDest.absolutePath)
                } catch (_: Exception) { ed }
            }
            book.copy(coverUrl = dest.absolutePath, editions = restoredEditions)
        } catch (_: Exception) { book }
    }

fun formatLastLocalBackup(context: Context, prefs: android.content.SharedPreferences): String {
    val ts = prefs.getLong("last_local_backup_ms", 0L)
    if (ts == 0L) return context.getString(R.string.backup_time_never)
    val diff = kotlin.math.abs(System.currentTimeMillis() - ts)
    val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        mins < 2    -> context.getString(R.string.backup_time_moment)
        mins < 60   -> context.getString(R.string.backup_time_mins, mins)
        mins < 1440 -> context.getString(R.string.backup_time_hours, java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diff))
        else        -> context.getString(R.string.backup_time_days, java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diff))
    }
}

fun exportFullBackup(context: Context, vm: BooksViewModel): Uri? {
    return try {
        // v3: mismo constructor que los workers (prefs = fuente de verdad; el VM
        // persiste en cada mutación). Biblioteca vacía → null → la UI avisa de error
        // en vez de compartir un backup vacío.
        val prefs = context.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
        val backup = buildFullBackupFromPrefs(prefs) ?: return null
        val gson = Gson()
        val json = gson.toJson(backup)
        val sdf = SimpleDateFormat("ddMMyy", Locale.getDefault())
        val fileName = "Backup_Lecturameter_${sdf.format(Date())}.json"
        val file = File(context.cacheDir, fileName)
        FileWriter(file).use { it.write(json) }
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (_: Exception) { null }
}

// Fase 1.1: inferEnglishFlag() y sanitizeBook() viven en utils/CoreUtils.kt

fun importFullBackup(
    context: Context,
    uri: Uri,
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences
): Pair<Boolean, String> {
    return try {
        val json = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.readText() ?: return Pair(false, context.getString(R.string.err_backup_read_failed))
        importFullBackupFromJson(json, vm, prefs, context)
    } catch (e: Exception) {
        com.lecturameter.utils.AppLogger.logError("importFullBackup failed", e, "BackupRestore")
        Pair(false, "Error: ${e.message}")
    }
}

// ── CSV Export ────────────────────────────────────────────────────────────────

fun exportBooksToCSV(context: Context, books: List<Book>): Uri? {
    return try {
        val header = "Title,Author,ISBN,My Rating,Average Rating,Publisher,Binding,Number of Pages,Year Published,Original Publication Year,Date Read,Date Added,Bookshelves,Exclusive Shelf,My Review,Spoiler,Private Notes,Read Count,Recommended For,Recommended By,Owned Copies,Original Purchase Date,Purchase Location,Condition,Condition Description,BCID"
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val rows = books.map { b ->
            val shelf = when (b.status) {
                BookStatus.FINISHED  -> "read"
                BookStatus.READING   -> "currently-reading"
                BookStatus.REREADING -> "currently-reading"
                BookStatus.PENDING   -> "to-read"
                BookStatus.DROPPED   -> "did-not-finish"
            }
            val dateRead = if (b.status == BookStatus.FINISHED && b.endDate != null)
                try { sdf.format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(b.endDate)!!) } catch (_: Exception) { "" }
            else ""
            val dateAdded = try { sdf.format(Date(b.addedAt)) } catch (_: Exception) { "" }
            // v21.41: mismo mapping que MiniRating — cada 2 puntos = 1 estrella, con redondeo hacia arriba
            val rating = if (b.rating > 0) ((b.rating + 1) / 2).coerceIn(1, 5).toString() else "0"
            fun esc(s: String) = "\"${s.replace("\"", "\"\"")}\""
            listOf(
                esc(b.title), esc(b.author), b.isbn ?: "", rating, "",
                "", "", b.pages.toString(), "", "",
                dateRead, dateAdded, shelf, shelf,
                esc(b.comment), "", "", "1", "", "", "0", "", "", "", "", ""
            ).joinToString(",")
        }
        val csv = (listOf(header) + rows).joinToString("\n")
        val file = File(context.cacheDir, "lecturameter_export.csv")
        FileWriter(file).use { it.write(csv) }
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (_: Exception) { null }
}

// ── importFullBackupFromJson (helper compartido con DriveBackupManager) ───────

fun importFullBackupFromJson(
    json: String,
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    context: android.content.Context
): Pair<Boolean, String> {
    return try {
        val gson = Gson()

        // Resiliente: si books/sessions vienen como STRING (JSON escapado) en vez de ARRAY,
        // re-parsear el JSON corregido antes de deserializar como FullBackup.
        val correctedJson = try {
            val root = com.google.gson.JsonParser.parseString(json).asJsonObject
            var changed = false
            for (field in listOf("books", "sessions", "wrappedHistory")) {
                val elem = root.get(field)
                if (elem != null && elem.isJsonPrimitive && elem.asJsonPrimitive.isString) {
                    root.add(field, com.google.gson.JsonParser.parseString(elem.asString))
                    changed = true
                }
            }
            if (changed) {
                com.lecturameter.utils.AppLogger.log("Backup: campos string-wrapped detectados y corregidos", "BackupRestore")
                gson.toJson(root)
            } else json
        } catch (_: Exception) { json }

        val type = object : TypeToken<FullBackup>() {}.type
        val backup: FullBackup = gson.fromJson(correctedJson, type)
            ?: return Pair(false, context.getString(R.string.err_backup_format_invalid))

        // ── M5: el tema, ANTES que nada ───────────────────────────────────────
        // Va lo primero a propósito: el grandfathering es un one-shot que se quema solo
        // (BooksViewModel.load) y si se quema mirando un theme_mode que aún no ha vuelto
        // del backup, le quita para siempre el tema heredado a quien lo tenía gratis. La
        // clave "ya hecho" no se deshace.
        //
        // ALCANCE REAL, que conviene no confundirlo: esto NO rescata a un usuario que venga
        // de la 2.7 por JSON. Los backups de la 2.7 son v2 y NO tienen el campo themeMode
        // (comprobado contra Backup_Lecturameter_Demo.json: solo books, sessions,
        // wrappedHistory, version y exportedAt), así que ese dato sencillamente no existe y
        // no hay nada que restaurar. A esos les salva la actualización en sitio, que es la
        // vía normal: mismo applicationId, las prefs no se tocan y theme_mode sigue ahí.
        // Lo que SÍ cubre esto es de la 3.0 en adelante: quien se cambia de móvil y restaura
        // por JSON conserva su tema y, si era heredado, su derecho a él.
        backup.themeMode?.let { restoredTheme ->
            // CRÍTICO (auditoría dinero 17-07): el JSON del backup es editable a mano. Antes
            // este bloque escribía theme_mode=restoredTheme y llamaba a grandfather, que CONCEDÍA
            // Aurora/AMOLED de forma permanente por lo que pusiera el campo → tema de pago gratis
            // para cualquiera que editara un fichero de texto. Ya no se concede nada desde aquí.
            //
            // El derecho (Pro, código, o grandfather REAL del arranque en sitio) NO viaja en el
            // JSON y no puede fabricarse con él. El tema restaurado solo se APLICA si el usuario
            // YA tiene derecho a él; si no, se ignora y se conserva el tema actual. Los backups
            // de la 2.7 (v2) ni siquiera traen themeMode, así que esto no afecta a esa migración;
            // la vía normal de la 2.7 es la actualización en sitio, que conserva prefs y tema.
            val previous = prefs.getString("theme_mode", null)
            val mode = ThemeMode.entries.firstOrNull { it.value == restoredTheme }
            val keep = previous == null && mode != null &&
                com.lecturameter.utils.Pro.themeAllowed(prefs, mode)
            if (keep) vm.setThemeMode(mode!!, prefs, context)
            // Si no procede (tema de pago sin derecho, o el usuario ya eligió tema en esta
            // instalación), no se toca theme_mode: se queda el actual o el de por defecto.
        }

        val backupBooks2 = backup.books ?: emptyList()
        val backupSessions2 = backup.sessions ?: emptyList()
        val backupWrapped2 = backup.wrappedHistory ?: emptyList()

        val existingIsbns = vm.books.value.mapNotNull { it.isbn }.toSet()
        val existingKeys = vm.books.value.map { "${it.title.trim().lowercase()}|${it.author.trim().lowercase()}" }.toSet()
        val existingBookIds = vm.books.value.map { it.id }.toSet()
        val newBooks = backupBooks2.filter { b ->
            val key = "${b.title.trim().lowercase()}|${b.author.trim().lowercase()}"
            b.id !in existingBookIds && (b.isbn == null || b.isbn !in existingIsbns) && key !in existingKeys
        }
        val existingSessionIds = vm.sessions.value.map { it.id }.toSet()
        // Bug fix v21.15: una sesión "nueva a restaurar" hay que reasignarla al id LOCAL del
        // libro, no al id que traía en el backup. Si el libro ya existía localmente (mismo id,
        // mismo ISBN o mismo título+autor — caso típico al restaurar un backup de OTRA instalación,
        // p.ej. dev -> public, donde los ids de libro son System.currentTimeMillis() y nunca coinciden)
        // las sesiones deben apuntar al id local existente, no quedar huérfanas.
        val bookIdRemap: Map<Long, Long> = backupBooks2.associate { b ->
            val key = "${b.title.trim().lowercase()}|${b.author.trim().lowercase()}"
            val matched = vm.books.value.firstOrNull { it.id == b.id }
                ?: vm.books.value.firstOrNull { b.isbn != null && it.isbn == b.isbn }
                ?: vm.books.value.firstOrNull { "${it.title.trim().lowercase()}|${it.author.trim().lowercase()}" == key }
            b.id to (matched?.id ?: b.id)
        }
        // B-032: deduplicar por id NO basta. `ReadingSession.id` es System.currentTimeMillis(),
        // así que la MISMA sesión registrada en dos instalaciones distintas nunca comparte id
        // — exactamente el mismo motivo por el que los libros se remapean por isbn/título+autor
        // en el bloque de arriba, pero a las sesiones no se les aplicó ese razonamiento.
        // Reproducido restaurando el backup del refac sobre los datos de la 2.7: salían dos
        // sesiones duplicadas exactas (Dragón 14-07 143-154 y Imperio Final 10-07 13-23),
        // contando doble en las estadísticas.
        // Una sesión queda definida por libro + fecha + páginas + rango: si eso coincide, es
        // la misma lectura aunque venga de otra instalación.
        fun sessionKey(bookId: Long, s: ReadingSession) =
            "$bookId|${s.date}|${s.pages}|${s.startPage}|${s.endPage}"
        val existingSessionKeys = vm.sessions.value.map { sessionKey(it.bookId, it) }.toSet()
        val newSessions = backupSessions2
            .filter { it.id !in existingSessionIds }
            .mapNotNull { s -> bookIdRemap[s.bookId]?.let { resolvedId -> s.copy(bookId = resolvedId) } }
            .filter { sessionKey(it.bookId, it) !in existingSessionKeys }
            .distinctBy { sessionKey(it.bookId, it) }   // y que el propio backup no traiga repes
        val backupBookById2 = backupBooks2.associateBy { it.id }
        vm.setBooks(vm.books.value.map { existing ->
            val fromBackup = backupBookById2[existing.id]
            if (fromBackup != null) {
                // RC-1: merge editions additively (same pattern as challenges/bingo).
                // Keep all local editions; add backup editions only when no local
                // edition shares the same id OR same language code.
                val localEditionIds = existing.editions.map { it.id }.toSet()
                val localEditionLangs = existing.editions.map { it.language }.toSet()
                val incomingEditions = fromBackup.editions
                    .filter { it.id !in localEditionIds && it.language !in localEditionLangs }
                val mergedEditions = existing.editions + incomingEditions
                val restoredEditions2 = if (mergedEditions.isNotEmpty())
                    restoreLocalCoversFromBackup(context, listOf(
                        existing.copy(editions = mergedEditions)
                    )).first().editions
                else existing.editions
                val restoredCover2 = if (fromBackup.coverUrl?.startsWith("data:image") == true)
                    restoreLocalCoversFromBackup(context, listOf(fromBackup)).first().coverUrl
                else fromBackup.coverUrl ?: existing.coverUrl
                existing.copy(
                    firstFunctionalPage = existing.firstFunctionalPage ?: fromBackup.firstFunctionalPage,
                    lastFunctionalPage  = existing.lastFunctionalPage  ?: fromBackup.lastFunctionalPage,
                    coverUrl  = existing.coverUrl ?: restoredCover2,
                    editions  = restoredEditions2
                )
            } else existing
        } + newBooks.map { sanitizeBook(restoreLocalCoversFromBackup(context, listOf(it)).first()) })
        val backupSessionById2 = backupSessions2.associateBy { it.id }
        vm.setSessions(vm.sessions.value.map { existing ->
            val fromBackup = backupSessionById2[existing.id]
            if (fromBackup != null) existing.copy(
                startPage = existing.startPage ?: fromBackup.startPage,
                endPage   = existing.endPage   ?: fromBackup.endPage,
                pages     = existing.pages      // rm-7: keep local corrections
            ) else existing
        } + newSessions)
        val existingYears = vm.wrappedHistory.value.map { it.year }.toSet()
        val newWrapped = backupWrapped2.filter { it.year !in existingYears }
            .map { com.lecturameter.repository.WrappedRepository.sanitizeWrapped(it) }
        vm.setWrappedHistory(vm.wrappedHistory.value + newWrapped)
        prefs.edit()
            .putString("books", gson.toJson(vm.books.value))
            .putString("sessions", gson.toJson(vm.sessions.value))
            .putString("wrapped_history", gson.toJson(vm.wrappedHistory.value))
            .apply()

        // ── v3: retos + bingo ─────────────────────────────────────────────────
        // Retos: se añaden los que no existen. Dedupe por id Y por (nombre|tipo|objetivo):
        // los 5 por defecto se siembran con id = System.currentTimeMillis() en CADA
        // instalación, así que tras una reinstalación el id nunca coincide y solo el
        // criterio de contenido evita duplicarlos (mismo criterio que usa el seeding).
        // No se pisan los locales: un reto presente puede tener un target editado.
        var newChallengesCount = 0
        backup.challenges?.let { fromBackup ->
            val local = com.lecturameter.repository.ChallengeRepository.loadOrNull(prefs) ?: emptyList()
            val localIds = local.map { it.id }.toSet()
            fun key(c: Challenge) = "${c.name.trim().lowercase()}|${c.type}|${c.target}"
            val localKeys = local.map(::key).toSet()
            val incoming = fromBackup.filter { it.id !in localIds && key(it) !in localKeys }
            if (incoming.isNotEmpty()) {
                com.lecturameter.repository.ChallengeRepository.save(prefs, local + incoming)
                vm.loadChallenges(prefs)
                newChallengesCount = incoming.size
            }
        }
        // Cartón del Bingo: solo si es del MES ACTUAL (uno viejo rotaría igualmente) y
        // solo si trae MÁS progreso que el local — restaurar nunca degrada un cartón.
        //
        // B4 (2): mismo criterio para los dos tamaños, cada uno contra el suyo. El 3×3 se
        // restaura aunque el usuario no sea Pro ahora mismo: si el backup es de cuando lo
        // era, el dato es suyo y no se tira. Si ya no le toca jugarlo,
        // reconcileBingo3Entitlement lo archivará en el historial en el siguiente arranque,
        // que es justo lo que pidió Víctor (conservarlo y poder verlo).
        var bingoRestored = false
        // [expectedSide]: el campo del backup del que sale este cartón manda sobre lo que
        // digan sus celdas. Un backup anterior a "Feedback 13-07 (10)" (cartón fijo 3×3) trae
        // un `bingoCard` de 9 celdas, y como la clave se derivaba del nº de celdas resucitaba
        // como el cartón 3×3 de Pro: un cartón viejo del bingo GRATIS colándose en el extra de
        // pago. Ningún usuario real puede tener ese backup (el 3×3 nació dentro del refac),
        // pero Víctor sí guarda backups de pruebas de esa época. Si el tamaño no casa con el
        // campo, el cartón es de otra era y se ignora: el historial no depende de esto.
        fun restoreCard(fromBackup: BingoCard?, expectedSide: Int) {
            if (fromBackup == null) return
            if (fromBackup.monthKey != com.lecturameter.utils.BingoManager.currentMonthKey()) return
            val side = com.lecturameter.utils.BingoManager.sideOf(fromBackup.cells.size)
            if (side < 3) return  // cartón corrupto: nº de celdas que no es un cuadrado
            if (side != expectedSide) return
            val local = com.lecturameter.repository.BingoRepository.loadOrNull(prefs, side)
            val localDone = local?.cells?.count { it.isCompleted } ?: -1
            val backupDone = fromBackup.cells.count { it.isCompleted }
            if (backupDone > localDone) {
                com.lecturameter.repository.BingoRepository.save(prefs, fromBackup)
                bingoRestored = true
            }
        }
        restoreCard(backup.bingoCard, com.lecturameter.utils.BingoManager.SIDE_4)
        restoreCard(backup.bingoCard3, com.lecturameter.utils.BingoManager.SIDE_3)
        if (bingoRestored) vm.reloadBingoCard(prefs)
        // Historial mensual del Bingo: unión por IDENTIDAD del resumen, no por mes.
        //
        // B4 (2): iba por monthKey, y eso ya perdía datos antes de los dos cartones (un mes
        // puede archivar dos cartones: completas el 4×4, pides otro y ese también se guarda).
        // Con el 3×3 conviviendo el fallo es sistemático: un mes normal de Pro tiene un 4×4
        // Y un 3×3, y el filtro por mes se comía el segundo en cada restauración. summaryKey
        // distingue mes + plantilla + tamaño + progreso.
        backup.bingoMonthHistory?.let { fromBackup ->
            val local = com.lecturameter.utils.BingoManager.loadMonthSummaries(prefs)
            // El merge y el archivado comparten identidad (summaryKey) y criterio: un cartón
            // que ya está en el historial local no entra otra vez, y si el backup lo trae MÁS
            // avanzado gana el más avanzado. Restaurar nunca suma dos veces el mismo cartón
            // (inflaba el Wrapped) ni degrada lo que ya había.
            val merged = com.lecturameter.utils.BingoManager
                .mergeSummaries(local, fromBackup.filter { it.monthKey.isNotBlank() })
                .sortedBy { it.monthKey }
            if (merged != local) {
                com.lecturameter.utils.BingoManager.saveMonthSummaries(prefs, merged)
                vm.loadBingoHistory(prefs)
            }
        }
        // v4 (D-016): historial de retos — unión por contenido (nombre|tipo|objetivo|año),
        // NUNCA por id (los defaults se resiembran con id nuevo en cada instalación).
        backup.challengeHistory?.let { fromBackup ->
            val local = com.lecturameter.repository.ChallengeHistoryRepository.load(prefs)
            fun key(s: ChallengeSnapshot) = "${s.name.trim().lowercase()}|${s.type}|${s.target}|${s.year}"
            val localKeys = local.map(::key).toSet()
            val incoming = fromBackup.filter { it.name != null && key(it) !in localKeys }
            if (incoming.isNotEmpty()) {
                com.lecturameter.repository.ChallengeHistoryRepository.save(prefs, local + incoming)
                vm.loadChallengeHistory(prefs)
            }
        }

        val msg = buildString {
            // Nitpick 16-07: plurales reales ("1 reto", no "1 retos")
            if (newBooks.isNotEmpty()) append(context.resources.getQuantityString(R.plurals.import_restored_books_q, newBooks.size, newBooks.size))
            if (newSessions.isNotEmpty()) { if (isNotEmpty()) append(", "); append(context.resources.getQuantityString(R.plurals.import_restored_sessions_q, newSessions.size, newSessions.size)) }
            if (newChallengesCount > 0) { if (isNotEmpty()) append(", "); append(context.resources.getQuantityString(R.plurals.import_restored_challenges_q, newChallengesCount, newChallengesCount)) }
            if (bingoRestored) { if (isNotEmpty()) append(", "); append(context.getString(R.string.import_restored_bingo)) }
            if (isEmpty()) append(context.getString(R.string.import_all_up_to_date))
        }
        Pair(true, msg)
    } catch (e: OutOfMemoryError) {
        throw e // No silenciar — el sistema necesita reaccionar
    } catch (e: Exception) {
        com.lecturameter.utils.AppLogger.logError("Restore backup failed", e, "BackupRestore")
        Pair(false, "Error: ${e.message}")
    }
}

