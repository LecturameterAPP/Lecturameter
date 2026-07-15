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
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val books: List<Book>? = null,
    val sessions: List<ReadingSession>? = null,
    val wrappedHistory: List<YearWrapped>? = null
)

/** Convierte portadas locales (rutas absolutas a filesDir) en base64 data URIs
 *  para que sobrevivan al backup/restore. Las URLs https se dejan tal cual. */
internal fun embedLocalCoverUrl(url: String?): String? {
    if (url == null || url.startsWith("http") || url.startsWith("data:")) return url
    return try {
        val file = java.io.File(url)
        if (!file.exists()) return url
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
        val backup = FullBackup(
            books = embedLocalCoversForExport(context, vm.books.value),
            sessions = vm.sessions.value,
            wrappedHistory = vm.wrappedHistory.value
        )
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
        val newSessions = backupSessions2
            .filter { it.id !in existingSessionIds }
            .mapNotNull { s -> bookIdRemap[s.bookId]?.let { resolvedId -> s.copy(bookId = resolvedId) } }
        val backupBookById2 = backupBooks2.associateBy { it.id }
        vm.setBooks(vm.books.value.map { existing ->
            val fromBackup = backupBookById2[existing.id]
            if (fromBackup != null) {
                val restoredEditions2 = if (fromBackup.editions.isNotEmpty())
                    restoreLocalCoversFromBackup(context, listOf(
                        existing.copy(editions = fromBackup.editions)
                    )).first().editions
                else existing.editions
                val restoredCover2 = if (fromBackup.coverUrl?.startsWith("data:image") == true)
                    restoreLocalCoversFromBackup(context, listOf(fromBackup)).first().coverUrl
                else fromBackup.coverUrl ?: existing.coverUrl
                existing.copy(
                    firstFunctionalPage = fromBackup.firstFunctionalPage,
                    lastFunctionalPage  = fromBackup.lastFunctionalPage,
                    coverUrl  = restoredCover2,
                    editions  = restoredEditions2
                )
            } else existing
        } + newBooks.map { sanitizeBook(restoreLocalCoversFromBackup(context, listOf(it)).first()) })
        val backupSessionById2 = backupSessions2.associateBy { it.id }
        vm.setSessions(vm.sessions.value.map { existing ->
            val fromBackup = backupSessionById2[existing.id]
            if (fromBackup != null) existing.copy(
                startPage = fromBackup.startPage ?: existing.startPage,
                endPage   = fromBackup.endPage   ?: existing.endPage,
                pages     = fromBackup.pages
            ) else existing
        } + newSessions)
        val existingYears = vm.wrappedHistory.value.map { it.year }.toSet()
        val newWrapped = backupWrapped2.filter { it.year !in existingYears }
        vm.setWrappedHistory(vm.wrappedHistory.value + newWrapped)
        prefs.edit()
            .putString("books", gson.toJson(vm.books.value))
            .putString("sessions", gson.toJson(vm.sessions.value))
            .putString("wrapped_history", gson.toJson(vm.wrappedHistory.value))
            .apply()
        val msg = buildString {
            if (newBooks.isNotEmpty()) append(context.getString(R.string.import_restored_books, newBooks.size))
            if (newSessions.isNotEmpty()) { if (isNotEmpty()) append(", "); append(context.getString(R.string.import_restored_sessions, newSessions.size)) }
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

