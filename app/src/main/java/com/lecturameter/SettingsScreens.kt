package com.lecturameter

// BulkReloadScreen, SurveyDialog, FeedbackDialog, FeedbackSender, SettingsScreen y componentes; challengeTypeLabel.
// Extraido de MainActivity.kt el 15-07-2026 (ruptura del monolito, sin cambios funcionales).


import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
// v21.42: Icons.Outlined.Star eliminado — estrellas usan ★/☆ Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
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
import androidx.navigation.compose.composable

// ── BulkReloadScreen ──────────────────────────────────────────────────────────

@Composable
fun BulkReloadScreen(
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    theme: Theme,
    type: String,     // "genres" | "covers"
    onBack: () -> Unit
) {
    val acc = accentForTheme(theme)
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val screenTitle = if (type == "genres") stringResource(R.string.bulk_refresh_title_genres)
        else stringResource(R.string.bulk_refresh_title_covers)
    val allBooks = books

    // Búsqueda y filtros
    var searchQuery by remember { mutableStateOf("") }
    var filterAuthor by remember { mutableStateOf<String?>(null) }
    var filterGenre by remember { mutableStateOf<String?>(null) }
    var filterShelf by remember { mutableStateOf<BookStatus?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    // Selección — se mantiene con filtros activos
    val selectedIds = remember { mutableStateMapOf<Long, Boolean>() }

    // Inicializar todos seleccionados
    LaunchedEffect(Unit) { allBooks.forEach { selectedIds[it.id] = true } }

    // Lista filtrada
    val filtered = remember(searchQuery, filterAuthor, filterGenre, filterShelf, allBooks.size) {
        allBooks.filter { book ->
            val q = searchQuery.trim()
            val matchSearch = q.isEmpty() || fuzzyMatch(q, book.title) || fuzzyMatch(q, book.author)
            val matchAuthor = filterAuthor == null || book.author == filterAuthor
            val matchGenre = filterGenre == null || book.genres.contains(filterGenre)
            val matchShelf = filterShelf == null || book.status == filterShelf
            matchSearch && matchAuthor && matchGenre && matchShelf
        }
    }

    val selectedCount = selectedIds.count { it.value }

    // Estado de recarga
    var reloading by remember { mutableStateOf(false) }
    var reloadProgress by remember { mutableStateOf("") }
    var reloadDone by remember { mutableStateOf(false) }
    var processedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var updatedCount by remember { mutableStateOf(0) }
    var errorCount by remember { mutableStateOf(0) }
    var elapsedSecs by remember { mutableStateOf(0L) }
    var showFinalConfirm by remember { mutableStateOf(false) }

    // Diálogo de confirmación final
    if (showFinalConfirm) {
        val selIds = selectedIds.filter { it.value }.keys.toList()
        AlertDialog(
            onDismissRequest = { showFinalConfirm = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_4466d686), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (type == "genres") stringResource(R.string.bulk_confirm_text_genres)
                    else stringResource(R.string.bulk_confirm_text_covers),
                    color = theme.textMuted, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showFinalConfirm = false
                    reloading = true
                    val startMs = System.currentTimeMillis()
                    if (type == "genres") {
                        vm.bulkRefreshGenres(prefs, selIds,
                            onProgress = { done, total ->
                                reloadProgress = "$done / $total"
                                processedCount = done; totalCount = total
                                elapsedSecs = (System.currentTimeMillis() - startMs) / 1000
                            },
                            onDone = { ok, errors ->
                                reloading = false
                                reloadDone = true
                                updatedCount = ok; errorCount = errors
                                elapsedSecs = (System.currentTimeMillis() - startMs) / 1000
                            }
                        )
                    } else {
                        vm.bulkRefreshCovers(prefs, selIds,
                            onProgress = { done, total ->
                                reloadProgress = "$done / $total"
                                processedCount = done; totalCount = total
                                elapsedSecs = (System.currentTimeMillis() - startMs) / 1000
                            },
                            onDone = { ok, errors ->
                                reloading = false
                                reloadDone = true
                                updatedCount = ok; errorCount = errors
                                elapsedSecs = (System.currentTimeMillis() - startMs) / 1000
                            }
                        )
                    }
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = acc, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showFinalConfirm = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, start = 4.dp, end = 16.dp, bottom = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
                Spacer(Modifier.width(4.dp))
                Text(screenTitle, color = theme.textMain, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }

            // Progreso / resultado
            if (reloading || reloadDone) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (reloadDone) Color(0x1A10B981) else acc.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, if (reloadDone) Color(0x4D10B981) else acc.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (reloading) {
                            val pct = if (totalCount > 0) processedCount * 100 / totalCount else 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = acc, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.reload_progress_label, reloadProgress, pct), color = acc, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (totalCount > 0) processedCount.toFloat() / totalCount else 0f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = acc,
                                trackColor = theme.border
                            )
                        } else {
                            Text(stringResource(R.string.reload_done_label, elapsedSecs), color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.reload_updated_label, updatedCount, errorCount), color = theme.textMuted, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Barra de búsqueda + filtros
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = theme.textDim, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = LocalTextStyle.current.copy(color = theme.textMain, fontSize = 13.sp),
                            cursorBrush = SolidColor(acc),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) Text(stringResource(R.string.txt_38fe9f72), color = theme.textDim, fontSize = 13.sp)
                                inner()
                            }
                        )
                        if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, null, tint = theme.textDim, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Box {
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(Icons.Default.FilterList, null, tint = if (filterAuthor != null || filterGenre != null || filterShelf != null) acc else theme.textDim)
                    }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                        Text(stringResource(R.string.txt_3397e69c), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        DropdownMenuItem(text = { Text(stringResource(R.string.txt_b73ecdab), color = if (filterShelf == null) acc else theme.textMain, fontSize = 13.sp) }, onClick = { filterShelf = null; showFilterMenu = false })
                        SHELF_ORDER.forEach { s ->
                            DropdownMenuItem(text = { Text(statusLabel(s), color = if (filterShelf == s) acc else theme.textMain, fontSize = 13.sp) }, onClick = { filterShelf = s; showFilterMenu = false })
                        }
                        HorizontalDivider(color = theme.border)
                        Text(stringResource(R.string.txt_c481b00a), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        DropdownMenuItem(text = { Text(stringResource(R.string.txt_426f7ea7), color = if (filterAuthor == null) acc else theme.textMain, fontSize = 13.sp) }, onClick = { filterAuthor = null; showFilterMenu = false })
                        allBooks.map { it.author }.filter { it.isNotBlank() }.distinct().sorted().forEach { a ->
                            DropdownMenuItem(text = { Text(a, color = if (filterAuthor == a) acc else theme.textMain, fontSize = 13.sp) }, onClick = { filterAuthor = a; showFilterMenu = false })
                        }
                        HorizontalDivider(color = theme.border)
                        Text(stringResource(R.string.txt_98f7ba16), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        DropdownMenuItem(text = { Text(stringResource(R.string.txt_8afc8680), color = if (filterGenre == null) acc else theme.textMain, fontSize = 13.sp) }, onClick = { filterGenre = null; showFilterMenu = false })
                        allBooks.flatMap { it.genres }.distinct().sorted().forEach { g ->
                            DropdownMenuItem(text = { Text(displayGenre(g), color = if (filterGenre == g) acc else theme.textMain, fontSize = 13.sp) }, onClick = { filterGenre = g; showFilterMenu = false })
                        }
                        HorizontalDivider(color = theme.border)
                        DropdownMenuItem(text = { Text(stringResource(R.string.txt_af85d57c), color = Red, fontSize = 13.sp) }, onClick = { filterAuthor = null; filterGenre = null; filterShelf = null; showFilterMenu = false })
                    }
                }
            }

            // Seleccionar todos / deseleccionar todos
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { allBooks.forEach { selectedIds[it.id] = true } }) {
                    Text(stringResource(R.string.txt_bb6b0333), color = acc, fontSize = 12.sp)
                }
                TextButton(onClick = { allBooks.forEach { selectedIds[it.id] = false } }) {
                    Text(stringResource(R.string.txt_a60bc74b), color = theme.textMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("${filtered.size} libros", color = theme.textDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically))
            }

            // Lista de libros
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filtered, key = { it.id }) { book ->
                    val isSelected = selectedIds[book.id] == true
                    Surface(
                        onClick = { selectedIds[book.id] = !isSelected },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) acc.copy(alpha = 0.07f) else theme.surface,
                        border = BorderStroke(1.dp, if (isSelected) acc.copy(alpha = 0.4f) else theme.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { selectedIds[book.id] = it },
                                colors = CheckboxDefaults.colors(checkedColor = acc, uncheckedColor = theme.border)
                            )
                            BookCover(book.coverUrl, book.title, size = 44, onBroken = {})
                            Column(Modifier.weight(1f)) {
                                Text(book.title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (book.author.isNotBlank()) Text(book.author, color = acc, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (book.genres.isNotEmpty()) Text(book.genres.take(2).map { displayGenre(it) }.joinToString(" · "), color = theme.textDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = statusColor(book.status).copy(alpha = 0.15f)) {
                                Text(statusLabel(book.status), color = statusColor(book.status), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        // Sticky bottom bar con el botón de recarga
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = theme.bgMid,
            shadowElevation = 8.dp
        ) {
            Button(
                onClick = { if (selectedCount > 0) showFinalConfirm = true },
                enabled = selectedCount > 0 && !reloading,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = onAccentColor(theme), disabledContainerColor = theme.border)
            ) {
                if (reloading) {
                    CircularProgressIndicator(color = onAccentColor(theme), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.txt_8b2335bb), fontWeight = FontWeight.Bold)
                } else {
                    val countLabel = if (selectedCount == allBooks.size) stringResource(R.string.txt_32630ca9) else "$selectedCount"
                    Text(stringResource(R.string.bulk_refresh_button, countLabel), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

// ── SettingsScreen ─────────────────────────────────────────────────────────────

private fun scheduleBackup(context: android.content.Context, intervalHours: Int) {
    // Bug fix v21.15: ver comentario equivalente en onCreate — sin NetworkType.CONNECTED,
    // el backup local sigue funcionando offline; Drive reintenta solo cuando hay red.
    val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(intervalHours.toLong(), TimeUnit.HOURS)
        .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "lecturameter_drive_backup",
        ExistingPeriodicWorkPolicy.REPLACE,
        request
    )
}

/** Convierte una URI de árbol SAF en un nombre de carpeta legible.
 *  Ej: ".../tree/primary%3ADownload%2FMisBackups" → "Download/MisBackups". */
internal fun readableFolderName(treeUriStr: String): String = try {
    val uri = android.net.Uri.parse(treeUriStr)
    val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
    // docId típico: "primary:Download/MisBackups"
    val path = docId.substringAfter(":", docId)
    if (path.isBlank()) docId else path
} catch (_: Exception) {
    treeUriStr
}

private fun scheduleWidgetRefresh(context: android.content.Context, intervalMinutes: Int, replace: Boolean = true) {
    val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "lecturameter_widget_refresh",
        if (replace) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

// ── FeedbackDialog ────────────────────────────────────────────────────────────

// ── Fase 6.2: micro-encuesta voluntaria (mockup E-2 aprobado 14-07) ───────────
// Una pregunta rotativa de opción única; envío por la misma Cloud Function del
// feedback (type "survey"). NO es telemetría: solo se envía al pulsar Enviar.
@Composable
fun SurveyDialog(theme: Theme, prefs: android.content.SharedPreferences, onDismiss: () -> Unit, onOpenFeedback: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val acc = accentForTheme(theme)
    val questions = listOf(
        Triple("section", R.string.survey_q_section, listOf(
            "library" to R.string.survey_o_library, "stats" to R.string.survey_o_stats,
            "challenges" to R.string.survey_o_challenges, "bingo" to R.string.survey_o_bingo,
            "wrapped" to R.string.survey_o_wrapped)),
        Triple("missing", R.string.survey_q_missing, listOf(
            "challenges" to R.string.survey_o_more_challenges, "stats" to R.string.survey_o_more_stats,
            "social" to R.string.survey_o_social, "nothing" to R.string.survey_o_nothing)),
        Triple("recommend", R.string.survey_q_recommend, listOf(
            "yes" to R.string.survey_o_yes, "no" to R.string.survey_o_no, "not_yet" to R.string.survey_o_not_yet))
    )
    val idx = remember { prefs.getInt("survey_index", 0).mod(questions.size) }
    val (qid, qRes, options) = questions[idx]
    var selected by remember { mutableStateOf<String?>(null) }
    // P-030: pregunta abierta opcional, no bloquea el envío si se deja vacía
    var openAnswer by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val sentMsg = stringResource(R.string.survey_sent_toast)
    val failMsg = stringResource(R.string.err_feedback_send_retry)

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        containerColor = theme.bgMid,
        title = { Text(stringResource(qRes), color = theme.textMain, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(stringResource(R.string.survey_privacy), color = theme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                options.forEach { (key, res) ->
                    val sel = selected == key
                    Surface(
                        onClick = { selected = key },
                        shape = RoundedCornerShape(11.dp),
                        color = if (sel) acc.copy(alpha = 0.14f) else theme.surface,
                        border = BorderStroke(1.dp, if (sel) acc else theme.border),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Text(
                            stringResource(res),
                            color = if (sel) acc else theme.textMain, fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                // P-030: pregunta abierta opcional, viaja con la respuesta rotativa en el mismo envío
                OutlinedTextField(
                    value = openAnswer,
                    onValueChange = { openAnswer = it },
                    label = { Text(stringResource(R.string.survey_open_label), color = theme.textMuted, fontSize = 12.sp) },
                    placeholder = { Text(stringResource(R.string.survey_open_placeholder), color = theme.textDim, fontSize = 12.sp) },
                    singleLine = false,
                    minLines = 2, maxLines = 4,
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain,
                        focusedBorderColor = acc, unfocusedBorderColor = theme.border
                    )
                )
                Text(
                    stringResource(R.string.survey_more_link), color = acc, fontSize = 11.5.sp,
                    modifier = Modifier.clickable(enabled = !sending) { onOpenFeedback() }.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null && !sending, onClick = {
                sending = true
                scope.launch {
                    val info = "App: Lecturameter ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Package: ${BuildConfig.APPLICATION_ID}\n" +
                        "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
                    // La pregunta abierta viaja añadida al mismo texto, sin romper el formato "Q/A" existente
                    val answerText = buildString {
                        append("Q: $qid\nA: $selected")
                        if (openAnswer.isNotBlank()) append("\nQ: open\nA: ${openAnswer.trim()}")
                    }
                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        FeedbackSender.send("survey", answerText, info, null, emptyList())
                    }
                    sending = false
                    if (ok) {
                        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                        prefs.edit()
                            .putLong("survey_last_ts", System.currentTimeMillis())
                            .putInt("survey_index", idx + 1)
                            .putInt("survey_year", year)
                            .putInt("survey_count_year", prefs.getInt("survey_count_year", 0) + 1)
                            .apply()
                        android.widget.Toast.makeText(context, sentMsg, android.widget.Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        android.widget.Toast.makeText(context, failMsg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                if (sending) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = acc)
                else Text(stringResource(R.string.survey_send), color = acc, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !sending, onClick = onDismiss) {
                Text(stringResource(R.string.survey_not_now), color = theme.textMuted)
            }
        }
    )
}

@Composable
fun FeedbackDialog(theme: Theme, onDismiss: () -> Unit, onSent: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val acc = accentForTheme(theme)
    var feedbackType by remember { mutableStateOf("bug") }
    var description by remember { mutableStateOf("") }
    var includeLogs by remember { mutableStateOf(true) }
    var selectedImages by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var isSending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var sendSuccess by remember { mutableStateOf(false) }

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(maxItems = 3)
    ) { uris ->
        if (uris.isNotEmpty()) {
            // Copiar imágenes a cache inmediatamente para que los URIs no se invaliden
            selectedImages = uris.take(3).mapNotNull { uri ->
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
                    val file = java.io.File(context.cacheDir, "feedback_img_${System.currentTimeMillis()}_${selectedImages.size}.jpg")
                    file.writeBytes(bytes)
                    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                } catch (_: Exception) { null }
            }
        }
    }

    val deviceInfo = remember {
        buildString {
            append("App: Lecturameter ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            // 14-07: el refac comparte versionName con la 2.7 — el package distingue origen
            append("Package: ${BuildConfig.APPLICATION_ID}\n")
            append("Marca: ${android.os.Build.BRAND}\n")
            append("Modelo: ${android.os.Build.MODEL}\n")
            append("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
            val skin = when {
                android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ->
                    if (android.os.Build.VERSION.SDK_INT >= 34) "HyperOS" else "MIUI"
                android.os.Build.MANUFACTURER.equals("Oppo", ignoreCase = true) ||
                android.os.Build.MANUFACTURER.equals("OnePlus", ignoreCase = true) -> "ColorOS"
                android.os.Build.MANUFACTURER.equals("Samsung", ignoreCase = true) -> "One UI"
                android.os.Build.MANUFACTURER.equals("Google", ignoreCase = true) -> "Stock Android"
                else -> android.os.Build.MANUFACTURER
            }
            append("Capa: $skin\n")
            append("Fecha: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        containerColor = theme.bgMid,
        title = { Text(stringResource(R.string.txt_76760a79), color = theme.textMain, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.txt_a5389e2a), color = theme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("bug" to "🐛 Bug", "suggestion" to "💡 Suggestion", "other" to "📝 Other")
                        .forEach { (type, label) ->
                            FilterChip(
                                selected = feedbackType == type,
                                onClick = { feedbackType = type },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.txt_d80248ef), color = theme.textMuted) },
                    placeholder = { Text(stringResource(R.string.txt_4204835c), color = theme.textDim, fontSize = 12.sp) },
                    minLines = 4, maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain,
                        focusedBorderColor = acc, unfocusedBorderColor = theme.border
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
                    )
                )
                Spacer(Modifier.height(10.dp))
                // Checkbox: logs adjuntos
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeLogs, onCheckedChange = { includeLogs = it })
                    Column {
                        Text(stringResource(R.string.txt_e4270dac), color = theme.textMuted, fontSize = 13.sp)
                        Text(stringResource(R.string.txt_1f8cb5ce), color = theme.textDim, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Imágenes propias del usuario (máx 3)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_2d283ff6), color = theme.textMuted, fontSize = 13.sp)
                        Text(stringResource(R.string.txt_fcfc243e), color = theme.textDim, fontSize = 11.sp)
                    }
                    TextButton(
                        enabled = selectedImages.size < 3,
                        onClick = { imagePickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    ) { Text(stringResource(R.string.txt_227430b0), color = if (selectedImages.size < 3) acc else theme.textDim, fontWeight = FontWeight.Bold) }
                }
                if (selectedImages.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedImages.forEach { uri ->
                            Box(Modifier.size(64.dp)) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(uri),
                                    contentDescription = stringResource(R.string.cd_attached_image),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                )
                                IconButton(
                                    onClick = { selectedImages = selectedImages.filter { it != uri } },
                                    modifier = Modifier.size(20.dp).align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                ) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                            }
                        }
                    }
                }
                // Info dispositivo (siempre visible)
                Spacer(Modifier.height(8.dp))
                Surface(color = theme.surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, theme.border)) {
                    Text(deviceInfo, modifier = Modifier.padding(10.dp), color = theme.textDim,
                        fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                if (sendError != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(color = Red.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Red.copy(alpha = 0.3f))) {
                        Text(stringResource(R.string.err_feedback_send_failed, sendError ?: ""), modifier = Modifier.padding(10.dp), color = Red, fontSize = 12.sp)
                    }
                }
                if (sendSuccess) {
                    Spacer(Modifier.height(8.dp))
                    Surface(color = Green.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Green.copy(alpha = 0.3f))) {
                        Text(stringResource(R.string.txt_0bf4acb4), modifier = Modifier.padding(10.dp), color = Green, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSending && !sendSuccess,
                onClick = {
                    if (description.isBlank()) {
                        android.widget.Toast.makeText(context, context.getString(R.string.msg_describe_feedback), android.widget.Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    isSending = true
                    sendError = null
                    scope.launch {
                        val logsText = if (includeLogs) com.lecturameter.utils.AppLogger.getLogs().takeLast(4000) else null
                        val imagesB64 = selectedImages.mapNotNull { uri ->
                            try {
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    val bytes = stream.readBytes()
                                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                }
                            } catch (_: Exception) { null }
                        }
                        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            FeedbackSender.send(feedbackType, description, deviceInfo, logsText, imagesB64)
                        }
                        isSending = false
                        if (result) {
                            sendSuccess = true
                            onSent()
                            onDismiss()
                        } else {
                            sendError = context.getString(R.string.err_feedback_send_retry)
                        }
                    }
                }
            ) {
                if (isSending) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = acc)
                else Text(stringResource(R.string.txt_30cc00ae), color = acc, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSending, onClick = onDismiss) {
                Text(stringResource(R.string.txt_847607d7), color = Red)
            }
        }
    )
}

// ── Envío de feedback al backend (Cloud Function) ──────────────────────────
object FeedbackSender {
    // URL de la Cloud Function desplegada — ver Lecturameter_documentacion.md, sección "Backend de feedback"
    private const val ENDPOINT_URL = "https://lectuameter-feedback-1045574439348.europe-west1.run.app"

    fun send(type: String, description: String, deviceInfo: String, logs: String?, imagesB64: List<String>): Boolean {
        return try {
            val typeLabel = when (type) { "bug" -> "Bug"; "suggestion" -> "Sugerencia"; else -> "Otro" }
            val model = android.os.Build.MODEL
            val subject = "[FEEDBACK Lecturameter] $typeLabel - $model"
            val json = org.json.JSONObject().apply {
                put("type", type)
                put("subject", subject)
                put("description", description)
                put("deviceInfo", deviceInfo)
                if (logs != null) put("logs", logs)
                put("images", org.json.JSONArray(imagesB64))
            }
            val url = java.net.URL(ENDPOINT_URL)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) { false }
    }
}

@Composable
fun SettingsScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit, onBulkReload: (String) -> Unit = {}, onResetTutorial: () -> Unit = {}, onImportExport: () -> Unit = {}, onPrivacyPolicy: () -> Unit = {}) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val acc = accentForTheme(theme)

    var backupIntervalHours by remember { mutableStateOf(prefs.getInt("backup_interval_hours", 2)) }
    var localBackupEnabled by remember { mutableStateOf(prefs.getBoolean("local_backup_enabled", true)) }
    var driveBackupEnabled by remember { mutableStateOf(prefs.getBoolean("drive_backup_enabled", true)) }
    var widgetRefreshMinutes by remember { mutableStateOf(prefs.getInt("widget_refresh_minutes", 30)) }

    var showActivateLocalDialog by remember { mutableStateOf(false) }
    var showActivateDriveDialog by remember { mutableStateOf(false) }
    var bulkGenresRunning by remember { mutableStateOf(false) }
    var bulkCoversRunning by remember { mutableStateOf(false) }
    var bulkProgress by remember { mutableStateOf("") }

    // Secciones colapsadas por defecto; estado persistente en prefs
    var sectBackup by remember { mutableStateOf(prefs.getBoolean("sect_backup_expanded", true)) }
    var sectWidget by remember { mutableStateOf(prefs.getBoolean("sect_widget_expanded", true)) }
    var sectTools  by remember { mutableStateOf(prefs.getBoolean("sect_tools_expanded",  true)) }
    var sectHelp   by remember { mutableStateOf(prefs.getBoolean("sect_help_expanded",   true)) }
    var sectTutorial by remember { mutableStateOf(prefs.getBoolean("sect_tutorial_expanded", true)) }
    var showFeedback by remember { mutableStateOf(false) }
    if (showFeedback) FeedbackDialog(
        theme = theme,
        onDismiss = { showFeedback = false },
        onSent = {
            android.widget.Toast.makeText(context, context.getString(R.string.msg_thanks_feedback), android.widget.Toast.LENGTH_LONG).show()
        }
    )

    // Carpeta local de backup (SAF). Estado reactivo para refrescar la UI al elegir.
    var localFolderUri by remember { mutableStateOf(prefs.getString("local_backup_folder_uri", null)) }

    // Picker de carpeta para backup local (SAF)
    val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
            prefs.edit().putString("local_backup_folder_uri", uri.toString()).apply()
            localFolderUri = uri.toString()
        }
    }

    val driveSignInClient = remember {
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, DriveBackupManager.buildSignInOptions())
    }
    var driveAccount by remember { mutableStateOf(DriveBackupManager.getSignedInAccount(context)) }
    val driveSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (com.google.android.gms.auth.api.signin.GoogleSignIn.hasPermissions(account, DriveBackupManager.REQUIRED_SCOPE)) {
                driveAccount = account
            }
        } catch (_: Exception) {}
    }

    // Activar local dialog
    if (showActivateLocalDialog) {
        AlertDialog(
            onDismissRequest = { showActivateLocalDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_2d639552), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_632543a9), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    localBackupEnabled = true
                    prefs.edit().putBoolean("local_backup_enabled", true).apply()
                    showActivateLocalDialog = false
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = acc, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showActivateLocalDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }
    if (showActivateDriveDialog) {
        AlertDialog(
            onDismissRequest = { showActivateDriveDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_dcd9cd29), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_eec2e836), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    driveBackupEnabled = true
                    prefs.edit().putBoolean("drive_backup_enabled", true).apply()
                    showActivateDriveDialog = false
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = acc, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showActivateDriveDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }
    // Estado para los diálogos de recarga masiva — nueva versión con opciones
    var bulkReloadTargetType by remember { mutableStateOf("") }  // "genres" | "covers"
    var showBulkOptionDialog by remember { mutableStateOf(false) }
    var bulkOptionSelected by remember { mutableStateOf("all") }  // "all" | "select"
    var showBulkAllConfirmDialog by remember { mutableStateOf(false) }

    // Strings for bulk progress (needed outside Composable lambdas)
    val strBulkStarting         = stringResource(R.string.bulk_starting)
    val strBulkProgressGenres   = stringResource(R.string.bulk_progress_genres_partial)
    val strBulkProgressCovers   = stringResource(R.string.bulk_progress_covers_partial)
    val strBulkDoneGenres       = stringResource(R.string.bulk_done_genres)
    val strBulkDoneCovers       = stringResource(R.string.bulk_done_covers)
    val strBulkRefreshAll       = stringResource(R.string.bulk_refresh_all)
    val strBulkRefreshSelect    = stringResource(R.string.bulk_refresh_select)

    // Diálogo con opciones: Refresh all / Refresh selecting
    if (showBulkOptionDialog) {
        val dialogTitle = if (bulkReloadTargetType == "genres") stringResource(R.string.bulk_refresh_title_genres)
                          else stringResource(R.string.bulk_refresh_title_covers)
        AlertDialog(
            onDismissRequest = { showBulkOptionDialog = false },
            containerColor = theme.bgMid,
            title = { Text(dialogTitle, color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.txt_8e00796a), color = theme.textMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    listOf("all" to strBulkRefreshAll, "select" to strBulkRefreshSelect).forEach { (key, text) ->
                        Surface(
                            onClick = { bulkOptionSelected = key },
                            shape = RoundedCornerShape(10.dp),
                            color = if (bulkOptionSelected == key) acc.copy(alpha = 0.12f) else theme.surface,
                            border = BorderStroke(1.dp, if (bulkOptionSelected == key) acc else theme.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text, color = if (bulkOptionSelected == key) acc else theme.textMain, fontSize = 13.sp, fontWeight = if (bulkOptionSelected == key) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showBulkOptionDialog = false
                    if (bulkOptionSelected == "all") showBulkAllConfirmDialog = true
                    else onBulkReload(bulkReloadTargetType)
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = acc, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showBulkOptionDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }

    // Diálogo de confirmación para "Refresh all"
    if (showBulkAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBulkAllConfirmDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_4466d686), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (bulkReloadTargetType == "genres") stringResource(R.string.bulk_confirm_text_genres)
                    else stringResource(R.string.bulk_confirm_text_covers),
                    color = theme.textMuted, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBulkAllConfirmDialog = false
                    bulkProgress = strBulkStarting
                    if (bulkReloadTargetType == "genres") {
                        bulkGenresRunning = true
                        vm.bulkRefreshGenres(prefs, null,
                            onProgress = { done, total -> bulkProgress = String.format(strBulkProgressGenres, done, total) },
                            onDone = { ok, errors -> bulkGenresRunning = false; bulkProgress = String.format(strBulkDoneGenres, ok, errors) }
                        )
                    } else {
                        bulkCoversRunning = true
                        vm.bulkRefreshCovers(prefs, null,
                            onProgress = { done, total -> bulkProgress = String.format(strBulkProgressCovers, done, total) },
                            onDone = { ok, errors -> bulkCoversRunning = false; bulkProgress = String.format(strBulkDoneCovers, ok, errors) }
                        )
                    }
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = acc, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showBulkAllConfirmDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, start = 4.dp, end = 16.dp, bottom = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.txt_f5d52eba), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }

        // D-013: upsell de Pro (lo abren los temas de pago y la sección Pro de abajo)
        var showProUpsell by remember { mutableStateOf(false) }
        var proRefresh by remember { mutableStateOf(0) }
        if (showProUpsell) {
            // B-041: al ganar Pro en caliente (compra, canje o trial), devolver el tema que
            // la caducidad de la prueba tuvo que apagar. Sin esto no volvería hasta reiniciar.
            ProUpsellSheet(theme, prefs, onDismiss = { showProUpsell = false }, onProChanged = {
                proRefresh++
                vm.reclaimThemeIfUnlocked(prefs, context)
            })
        }

        // ── TEMA ─────────────────────────────────────────────────────────────
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.txt_057acd78), color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    ThemeMode.LIGHT  to stringResource(R.string.theme_light),
                    ThemeMode.DARK   to stringResource(R.string.theme_dark),
                    ThemeMode.AURORA to stringResource(R.string.theme_aurora),
                    ThemeMode.AMOLED to stringResource(R.string.theme_oled),
                    ThemeMode.CUERO  to stringResource(R.string.theme_cuero)
                ).forEach { (mode, label) ->
                    val selected = vm.themeMode == mode
                    Surface(
                        onClick = {
                            // D-013: los temas de pago abren el upsell si no hay Pro
                            if (!com.lecturameter.utils.Pro.themeAllowed(prefs, mode)) showProUpsell = true
                            else vm.setThemeMode(mode, prefs, context)
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) acc.copy(alpha = 0.15f) else theme.surface,
                        border = BorderStroke(1.dp, if (selected) acc else theme.border),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Feedback 2.6: Aurora muestra su icono PNG delante del nombre
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                        ) {
                            if (mode == ThemeMode.AURORA) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_theme_aurora),
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp).clip(RoundedCornerShape(3.dp))
                                )
                                Spacer(Modifier.width(3.dp))
                            } else if (mode == ThemeMode.CUERO) {
                                // D-015: icono dedicado del tema Cuero (tapa con marco)
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_theme_cuero),
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(
                                label, color = if (selected) acc else theme.textMuted,
                                fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                // A5: en pantallas pequeñas el nombre del tema se cortaba a media
                                // palabra; con ellipsis se recorta limpio.
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // ── IDIOMA ────────────────────────────────────────────────────────────
        // Feedback 17-07: el idioma va SIEMPRE justo debajo de los temas
        // (orden fijo: temas > idioma > Pro > icono de la app > resto)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.txt_36f1a4d2), color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("es" to stringResource(R.string.txt_95b01315), "en" to stringResource(R.string.txt_f759fe35)).forEach { (lang, label) ->
                    val selected = vm.currentLanguage == lang
                    Surface(
                        onClick = {
                            if (!selected) {
                                vm.setLanguage(lang, prefs)
                                (context as? android.app.Activity)?.recreate()
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) acc.copy(alpha = 0.15f) else theme.surface,
                        border = BorderStroke(1.dp, if (selected) acc else theme.border),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            label, color = if (selected) acc else theme.textMuted,
                            fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        // ── LECTURAMETER PRO (D-013) ──────────────────────────────────────────
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.pro_section_title), color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
            val proStatusText = remember(proRefresh) {
                when {
                    prefs.getBoolean(com.lecturameter.utils.Pro.PREF_KEY, false) -> "active"
                    com.lecturameter.utils.Pro.trialActive(prefs) -> "trial"
                    else -> "free"
                }
            }
            Surface(
                onClick = { showProUpsell = true },
                shape = RoundedCornerShape(14.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, if (proStatusText == "free") theme.border else accentForTheme(theme).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⭐", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pro_title), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when (proStatusText) {
                                "active" -> stringResource(R.string.pro_status_active)
                                "trial" -> stringResource(R.string.pro_status_trial, com.lecturameter.utils.Pro.trialDaysLeft(prefs))
                                else -> stringResource(R.string.pro_status_free)
                            },
                            color = if (proStatusText == "free") theme.textDim else accentForTheme(theme),
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = theme.textDim, modifier = Modifier.size(18.dp))
                }
            }

            // Icono de la app conmutable (Pro): alterna los activity-alias LauncherClassic
            // y LauncherGold. Se habilita el nuevo ANTES de deshabilitar el viejo para que
            // nunca haya cero entradas en el launcher. Si el Pro caduca NO se revierte solo
            // (cambiar componentes puede matar el proceso y el icono es inofensivo).
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.pro_icon_label), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            var appIcon by remember { mutableStateOf(prefs.getString("app_icon", "classic") ?: "classic") }
            val iconToastMsg = stringResource(R.string.pro_icon_applied)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "classic" to stringResource(R.string.pro_icon_classic),
                    "gold" to stringResource(R.string.pro_icon_gold)
                ).forEach { (value, label) ->
                    val selected = appIcon == value
                    Surface(
                        onClick = {
                            if (selected) return@Surface
                            if (!com.lecturameter.utils.Pro.isPro(prefs)) { showProUpsell = true; return@Surface }
                            val pm = context.packageManager
                            val pkg = context.packageName
                            val classic = android.content.ComponentName(pkg, "com.lecturameter.LauncherClassic")
                            val gold = android.content.ComponentName(pkg, "com.lecturameter.LauncherGold")
                            val (enable, disable) = if (value == "gold") gold to classic else classic to gold
                            pm.setComponentEnabledSetting(enable, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                            pm.setComponentEnabledSetting(disable, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                            prefs.edit().putString("app_icon", value).apply()
                            appIcon = value
                            android.widget.Toast.makeText(context, iconToastMsg, android.widget.Toast.LENGTH_LONG).show()
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) acc.copy(alpha = 0.15f) else theme.surface,
                        border = BorderStroke(1.dp, if (selected) acc else theme.border),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(if (value == "gold") R.mipmap.ic_launcher_pro else R.mipmap.ic_launcher),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(label, color = if (selected) acc else theme.textMuted, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            if (!com.lecturameter.utils.Pro.isPro(prefs) && value == "gold") {
                                Spacer(Modifier.width(5.dp))
                                Icon(Icons.Default.Lock, contentDescription = null, tint = theme.textDim, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }

        // Feedback 13-07 (4): la entrada "Reordenar accesos del inicio" se ELIMINA de
        // Ajustes a petición de Víctor — la reordenación queda solo por long-press en el rail.

        // ── BACKUPS ───────────────────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_section_backups),
            subtitle = stringResource(R.string.settings_backup_subtitle),
            expanded = sectBackup,
            onToggle = { val newVal = !sectBackup; sectBackup = newVal; prefs.edit().putBoolean("sect_backup_expanded", newVal).apply() },
            theme = theme
        ) {
            // Frecuencia
            Column(Modifier.padding(bottom = 16.dp)) {
                Text(stringResource(R.string.txt_60520d3a), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.txt_ae333e6f), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 4, 6, 8, 10).forEach { h ->
                        val selected = backupIntervalHours == h
                        Surface(
                            onClick = {
                                backupIntervalHours = h
                                prefs.edit().putInt("backup_interval_hours", h).apply()
                                scheduleBackup(context, h)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) acc else theme.surface,
                            border = BorderStroke(1.dp, if (selected) acc else theme.border),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${h}h", color = if (selected) onAccentColor(theme) else theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
            HorizontalDivider(color = theme.border, modifier = Modifier.padding(bottom = 12.dp))
            // Backup Local
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(acc.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PhoneAndroid, null, tint = acc, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.txt_bfd55cba), color = if (localBackupEnabled) theme.textMain else theme.textDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.txt_f68be150), color = theme.textMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = localBackupEnabled,
                    onCheckedChange = { checked ->
                        localBackupEnabled = checked
                        prefs.edit().putBoolean("local_backup_enabled", checked).apply()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = onAccentColor(theme), checkedTrackColor = acc, uncheckedTrackColor = theme.border)
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (localBackupEnabled) theme.surface else theme.bgMid,
                border = BorderStroke(1.dp, theme.border),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                onClick = {
                    if (localBackupEnabled) folderPickerLauncher.launch(null)
                    else showActivateLocalDialog = true
                }
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_db955caf), color = if (localBackupEnabled) theme.textMain else theme.textDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            localFolderUri?.let { readableFolderName(it) } ?: stringResource(R.string.label_default_backup_folder),
                            color = theme.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Si hay carpeta personalizada, botón para volver a la de por defecto
                    if (localFolderUri != null && localBackupEnabled) {
                        IconButton(onClick = {
                            prefs.edit().remove("local_backup_folder_uri").apply()
                            localFolderUri = null
                        }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.cd_use_default_folder), tint = theme.textDim, modifier = Modifier.size(16.dp))
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = if (localBackupEnabled) theme.textMuted else theme.textDim, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = theme.border, modifier = Modifier.padding(bottom = 12.dp))
            // Backup Drive
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1A4285F4)), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.txt_cdd0bfa9), fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.txt_2ac042cb), color = if (driveBackupEnabled) theme.textMain else theme.textDim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.txt_02d47d1a), color = theme.textMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = driveBackupEnabled,
                    onCheckedChange = { checked ->
                        driveBackupEnabled = checked
                        prefs.edit().putBoolean("drive_backup_enabled", checked).apply()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = onAccentColor(theme), checkedTrackColor = acc, uncheckedTrackColor = theme.border)
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (driveBackupEnabled) theme.surface else theme.bgMid,
                border = BorderStroke(1.dp, theme.border),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                onClick = {
                    when {
                        !driveBackupEnabled -> showActivateDriveDialog = true
                        driveAccount != null -> {
                            // Cambiar de cuenta sin perder configuración
                            driveSignInClient.signOut().addOnCompleteListener {
                                driveSignInLauncher.launch(driveSignInClient.signInIntent)
                            }
                        }
                        else -> driveSignInLauncher.launch(driveSignInClient.signInIntent)
                    }
                }
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_c37472ea), color = if (driveBackupEnabled) theme.textMain else theme.textDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            driveAccount?.email?.let { stringResource(R.string.drive_account_connected, it) } ?: stringResource(R.string.drive_account_disconnected),
                            color = theme.textMuted, fontSize = 11.sp
                        )
                    }
                }
            }
            // v2.4 rework: "Guardar ahora" y "Última copia" viven ahora en Ajustes
            HorizontalDivider(color = theme.border, modifier = Modifier.padding(bottom = 12.dp))
            var saveNowLocalRunning by remember { mutableStateOf(false) }
            var saveNowDriveRunning by remember { mutableStateOf(false) }
            var saveNowMsg by remember { mutableStateOf<String?>(null) }
            var lastLocalText by remember { mutableStateOf(formatLastLocalBackup(context, prefs)) }
            var lastDriveText by remember { mutableStateOf(DriveBackupManager.formatLastBackup(context, prefs)) }
            Text(stringResource(R.string.settings_save_now_title), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.local_backup_last, lastLocalText), color = theme.textMuted, fontSize = 12.sp)
            Text(stringResource(R.string.drive_backup_last, lastDriveText), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Button(
                    onClick = {
                        if (!localBackupEnabled) { showActivateLocalDialog = true; return@Button }
                        if (books.isEmpty()) { saveNowMsg = context.getString(R.string.msg_no_data_export); return@Button }
                        saveNowLocalRunning = true; saveNowMsg = null
                        val req = OneTimeWorkRequestBuilder<JsonBackupWorker>().build()
                        val wm = WorkManager.getInstance(context)
                        wm.enqueueUniqueWork("lecturameter_json_backup_manual", ExistingWorkPolicy.REPLACE, req)
                        scope.launch {
                            val finalState = kotlinx.coroutines.withTimeoutOrNull(20_000) {
                                wm.getWorkInfosForUniqueWorkFlow("lecturameter_json_backup_manual")
                                    .mapNotNull { infos -> infos.firstOrNull { it.id == req.id } }
                                    .first { it.state.isFinished }
                            }
                            saveNowLocalRunning = false
                            lastLocalText = formatLastLocalBackup(context, prefs)
                            saveNowMsg = when {
                                finalState == null -> context.getString(R.string.msg_backup_pending)
                                finalState.state == androidx.work.WorkInfo.State.SUCCEEDED -> context.getString(R.string.msg_backup_saved_default)
                                else -> context.getString(R.string.msg_backup_error)
                            }
                        }
                    },
                    enabled = !saveNowLocalRunning,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (localBackupEnabled) Green else Color.Transparent, contentColor = Color.White),
                    border = if (localBackupEnabled) null else BorderStroke(1.dp, theme.border)
                ) {
                    if (saveNowLocalRunning) CircularProgressIndicator(color = if (localBackupEnabled) Color.White else Green, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Save, null, tint = if (localBackupEnabled) Color.White else theme.textDim, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_save_now_local), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (localBackupEnabled) Color.White else theme.textDim, maxLines = 1)
                }
                Button(
                    onClick = {
                        if (!driveBackupEnabled) { showActivateDriveDialog = true; return@Button }
                        if (driveAccount == null) { driveSignInLauncher.launch(driveSignInClient.signInIntent); return@Button }
                        if (books.isEmpty()) { saveNowMsg = context.getString(R.string.msg_no_data_export); return@Button }
                        saveNowDriveRunning = true; saveNowMsg = null
                        scope.launch {
                            val result = DriveBackupManager.backup(context, prefs)
                            saveNowDriveRunning = false
                            lastDriveText = DriveBackupManager.formatLastBackup(context, prefs)
                            saveNowMsg = result.fold(
                                onSuccess = { context.getString(R.string.msg_drive_saved) },
                                onFailure = { context.getString(R.string.msg_drive_error, it.message ?: "") }
                            )
                        }
                    },
                    enabled = !saveNowDriveRunning,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (driveBackupEnabled) Color(0xFF4285F4) else Color.Transparent, contentColor = Color.White),
                    border = if (driveBackupEnabled) null else BorderStroke(1.dp, theme.border)
                ) {
                    if (saveNowDriveRunning) CircularProgressIndicator(color = if (driveBackupEnabled) Color.White else Color(0xFF4285F4), modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.CloudUpload, null, tint = if (driveBackupEnabled) Color.White else theme.textDim, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_save_now_drive), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (driveBackupEnabled) Color.White else theme.textDim, maxLines = 1)
                }
            }
            saveNowMsg?.let { msg ->
                Surface(shape = RoundedCornerShape(10.dp), color = if (msg.startsWith("✅")) Color(0x1A10B981) else Color(0x1AF59E0B), border = BorderStroke(1.dp, if (msg.startsWith("✅")) Color(0x4D10B981) else Color(0x4DF59E0B)), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(msg, color = if (msg.startsWith("✅")) Green else Amber, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                }
            }
            // Acceso a Importar/Exportar/Restaurar (Goodreads, CSV, JSON, Drive restore)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.border),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                onClick = onImportExport
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SwapVert, null, tint = acc, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_import_export_title), color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_import_export_subtitle), color = theme.textMuted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted, modifier = Modifier.size(18.dp))
                }
            }
            // Info note
            Surface(shape = RoundedCornerShape(10.dp), color = acc.copy(alpha = 0.06f), border = BorderStroke(1.dp, acc.copy(alpha = 0.25f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = acc, modifier = Modifier.size(15.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.txt_2509a87a), color = acc.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── WIDGET ────────────────────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_section_widget),
            subtitle = stringResource(R.string.settings_widget_subtitle),
            expanded = sectWidget,
            onToggle = { val newVal = !sectWidget; sectWidget = newVal; prefs.edit().putBoolean("sect_widget_expanded", newVal).apply() },
            theme = theme
        ) {
            // v2.5: editar chips del widget sin quitarlo del launcher
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.border),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                onClick = {
                    context.startActivity(android.content.Intent(context, com.lecturameter.widget.WidgetConfigActivity::class.java))
                }
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🧩", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.widget_config_title), color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_widget_customize_sub), color = theme.textMuted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted, modifier = Modifier.size(18.dp))
                }
            }
            Text(stringResource(R.string.txt_5ccb97b8), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.txt_bd3d357e), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30 to "30 mins", 60 to "1h", 90 to "1h 30min", 120 to "2h").forEach { (mins, label) ->
                    val selected = widgetRefreshMinutes == mins
                    Surface(
                        onClick = {
                            widgetRefreshMinutes = mins
                            prefs.edit().putInt("widget_refresh_minutes", mins).apply()
                            scheduleWidgetRefresh(context, mins)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) acc else theme.surface,
                        border = BorderStroke(1.dp, if (selected) acc else theme.border),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, color = if (selected) onAccentColor(theme) else theme.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── HERRAMIENTAS ──────────────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_tools_title),
            subtitle = stringResource(R.string.settings_tools_subtitle),
            expanded = sectTools,
            onToggle = { val newVal = !sectTools; sectTools = newVal; prefs.edit().putBoolean("sect_tools_expanded", newVal).apply() },
            theme = theme
        ) {
            if (bulkProgress.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(10.dp), color = if (bulkProgress.startsWith("✅")) Color(0x1A10B981) else acc.copy(alpha = 0.06f), border = BorderStroke(1.dp, if (bulkProgress.startsWith("✅")) Color(0x4D10B981) else acc.copy(alpha = 0.25f)), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(bulkProgress, color = if (bulkProgress.startsWith("✅")) Green else acc, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                }
            }
            SettingsToolRow(
                icon = "🔄",
                title = stringResource(R.string.bulk_refresh_title_genres),
                subtitle = stringResource(R.string.bulk_refresh_subtitle_genres),
                running = bulkGenresRunning,
                theme = theme,
                onClick = { bulkReloadTargetType = "genres"; bulkOptionSelected = "all"; showBulkOptionDialog = true }
            )
            Spacer(Modifier.height(8.dp))
            SettingsToolRow(
                icon = "🖼️",
                title = stringResource(R.string.bulk_refresh_title_covers),
                subtitle = stringResource(R.string.bulk_refresh_subtitle_covers),
                running = bulkCoversRunning,
                theme = theme,
                onClick = { bulkReloadTargetType = "covers"; bulkOptionSelected = "all"; showBulkOptionDialog = true }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── TUTORIAL ─────────────────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_tutorial_title),
            subtitle = stringResource(R.string.settings_tutorial_subtitle),
            expanded = sectTutorial,
            onToggle = { val newVal = !sectTutorial; sectTutorial = newVal; prefs.edit().putBoolean("sect_tutorial_expanded", newVal).apply() },
            theme = theme
        ) {
            SettingsToolRow(
                icon = "📖",
                title = stringResource(R.string.settings_tutorial_row_title),
                subtitle = stringResource(R.string.settings_tutorial_row_subtitle),
                running = false,
                theme = theme,
                onClick = { vm.resetTutorial(prefs); Tips.resetAll(prefs); onResetTutorial() }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── AYUDA Y FEEDBACK ─────────────────────────────────────────────────
        SettingsSection(
            title = stringResource(R.string.settings_help_title),
            subtitle = stringResource(R.string.settings_help_subtitle),
            expanded = sectHelp,
            onToggle = { val newVal = !sectHelp; sectHelp = newVal; prefs.edit().putBoolean("sect_help_expanded", newVal).apply() },
            theme = theme
        ) {
            SettingsToolRow(
                icon = "📨",
                title = stringResource(R.string.settings_feedback_title),
                subtitle = stringResource(R.string.settings_feedback_subtitle),
                running = false,
                theme = theme,
                onClick = { showFeedback = true }
            )
            Spacer(Modifier.height(8.dp))
            // TAREA 1 (lanzamiento): acceso a la política de privacidad in-app
            SettingsToolRow(
                icon = "🔒",
                title = stringResource(R.string.settings_privacy_policy_title),
                subtitle = stringResource(R.string.settings_privacy_policy_subtitle),
                running = false,
                theme = theme,
                onClick = onPrivacyPolicy
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── APOYA EL PROYECTO ─────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = theme.surface,
            border = BorderStroke(1.dp, theme.border),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.txt_76306812), color = acc, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text(stringResource(R.string.txt_e30acdbb), color = theme.textMuted, fontSize = 11.sp)
                // C: nota informativa (sin lógica) sobre canjear donaciones por códigos de Pro
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.support_code_hint), color = theme.textDim, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(12.dp))
                // Ko-fi
                Surface(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://ko-fi.com/lecturameter"))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF29ABE0).copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color(0xFF29ABE0).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("☕", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.txt_eedfdf2b), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.txt_bb61c189), color = theme.textMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = theme.textDim, modifier = Modifier.size(16.dp))
                    }
                }
                // PayPal
                Surface(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://paypal.me/Lecturameter"))
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0070BA).copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color(0xFF0070BA).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("💙", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.txt_ad69e733), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.txt_f72dc94e), color = theme.textMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = theme.textDim, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // v21.35: separación igual al resto de cards entre Support y About
        Spacer(Modifier.height(12.dp))

        // ── ACERCA DE ─────────────────────────────────────────────────────────
        // v21.35: eliminada card exterior (título + subtítulo). Solo el recuadro interior, centrado.
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = acc.copy(alpha = 0.06f),
            border = BorderStroke(1.5.dp, acc.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(acc.copy(alpha = 0.15f)).border(2.dp, acc.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📚", fontSize = 28.sp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.txt_4d8b0a6f), color = theme.textMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.app_version_label, com.lecturameter.BuildConfig.VERSION_NAME), color = acc, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.txt_572a06a1), color = theme.textMuted, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun SettingsSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    theme: Theme,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, theme.border),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle
                )
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = accentForTheme(theme), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(subtitle, color = theme.textMuted, fontSize = 11.sp)
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    null,
                    tint = accentForTheme(theme),
                    modifier = Modifier.size(20.dp).rotate(if (expanded) 0f else -90f)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
fun SettingsToolRow(icon: String, title: String, subtitle: String, running: Boolean, theme: Theme, onClick: () -> Unit) {
    Surface(
        onClick = { if (!running) onClick() },
        shape = RoundedCornerShape(12.dp),
        color = theme.bgMid,
        border = BorderStroke(1.dp, theme.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(accentForTheme(theme).copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                if (running) CircularProgressIndicator(color = accentForTheme(theme), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(icon, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = theme.textMuted, fontSize = 11.sp, softWrap = true, overflow = TextOverflow.Visible)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}

// ── ChallengesScreen (v2.4 rework) ────────────────────────────────────────────
//
// Pantalla de retos de lectura: predeterminados + personalizados, con barra de
// progreso calculada en vivo desde libros/sesiones. Persistencia: prefs "challenges".

@Composable
fun challengeTypeLabel(type: ChallengeType): String = when (type) {
    ChallengeType.PAGES    -> stringResource(R.string.challenge_type_pages)
    ChallengeType.BOOKS    -> stringResource(R.string.challenge_type_books)
    ChallengeType.SESSIONS -> stringResource(R.string.challenge_type_sessions)
    ChallengeType.STREAK   -> stringResource(R.string.challenge_type_streak)
    ChallengeType.MINUTES  -> stringResource(R.string.challenge_type_minutes)
}
