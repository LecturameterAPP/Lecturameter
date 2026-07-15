package com.lecturameter

// ImportExportScreen: backups locales/Drive, import/export JSON y CSV.
// Extraido de MainActivity.kt el 15-07-2026 (ruptura del monolito, sin cambios funcionales).


import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.lecturameter.model.*
import com.lecturameter.utils.*
import androidx.navigation.compose.composable

// ── ImportExportScreen ────────────────────────────────────────────────────────
@Composable
fun ImportExportScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val context = LocalContext.current
    var importMsg by remember { mutableStateOf<String?>(null) }
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var backupMsg by remember { mutableStateOf<String?>(null) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isLocalAutoBackingUp by remember { mutableStateOf(false) }
    var lastLocalBackupText by remember { mutableStateOf(formatLastLocalBackup(context, prefs)) }
    // Flags de backup desde Ajustes — los botones manuales quedan grises si están desactivados
    var localBackupOn by remember { mutableStateOf(prefs.getBoolean("local_backup_enabled", true)) }
    var driveBackupOn by remember { mutableStateOf(prefs.getBoolean("drive_backup_enabled", true)) }
    var showActivateLocalBk by remember { mutableStateOf(false) }
    var showActivateDriveBk by remember { mutableStateOf(false) }
    // Permiso WRITE_EXTERNAL_STORAGE (solo Android 8-9)
    var showStoragePermDialog by remember { mutableStateOf(false) }
    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) pendingStorageAction?.invoke()
        else android.widget.Toast.makeText(context, context.getString(R.string.msg_no_storage_permission_backup), android.widget.Toast.LENGTH_SHORT).show()
        pendingStorageAction = null
    }
    if (showStoragePermDialog) {
        AlertDialog(
            onDismissRequest = { showStoragePermDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_5f9fd925), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_b7a882b4), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showStoragePermDialog = false
                    storagePermLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }) { Text(stringResource(R.string.txt_5fcafeb2), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showStoragePermDialog = false; pendingStorageAction = null }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }
    fun runWithStoragePerm(action: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT in 26..28 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingStorageAction = action
            showStoragePermDialog = true
        } else {
            action()
        }
    }
    val scope = rememberCoroutineScope()
    val driveSignInClient = remember {
        GoogleSignIn.getClient(context, DriveBackupManager.buildSignInOptions())
    }
    var driveAccount by remember { mutableStateOf(DriveBackupManager.getSignedInAccount(context)) }
    var driveMsg by remember { mutableStateOf<String?>(null) }
    var isDriveLoading by remember { mutableStateOf(false) }
    var lastBackupText by remember { mutableStateOf(DriveBackupManager.formatLastBackup(context, prefs)) }

    if (showActivateLocalBk) {
        AlertDialog(
            onDismissRequest = { showActivateLocalBk = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_2d639552), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_632543a9), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    localBackupOn = true
                    prefs.edit().putBoolean("local_backup_enabled", true).apply()
                    showActivateLocalBk = false
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showActivateLocalBk = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }
    if (showActivateDriveBk) {
        AlertDialog(
            onDismissRequest = { showActivateDriveBk = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_dcd9cd29), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_eec2e836), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    driveBackupOn = true
                    prefs.edit().putBoolean("drive_backup_enabled", true).apply()
                    showActivateDriveBk = false
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showActivateDriveBk = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val count = vm.importFromGoodreads(context, uri, prefs)
            importMsg = if (count > 0) context.getString(R.string.msg_import_goodreads_ok, count) else context.getString(R.string.msg_import_goodreads_empty)
            exportMsg = null
        }
    }

    val jsonRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val (ok, msg) = importFullBackup(context, uri, vm, prefs)
            backupMsg = if (ok) "✅ $msg" else "❌ $msg"
            importMsg = null; exportMsg = null
        }
    }

    val driveSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (GoogleSignIn.hasPermissions(account, DriveBackupManager.REQUIRED_SCOPE)) {
                driveAccount = account
                driveMsg = context.getString(R.string.msg_drive_connected, account.email ?: "")
            } else {
                driveMsg = context.getString(R.string.msg_drive_no_perms)
            }
        } catch (e: ApiException) {
            driveMsg = context.getString(R.string.msg_drive_connect_error, e.message ?: "")
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 24.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.txt_7ddf5345), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (books.size == 1) stringResource(R.string.library_books_count, books.size) else stringResource(R.string.library_books_count_plural, books.size), color = theme.textMuted, fontSize = 13.sp)
            }
        }

        // ── IMPORTAR ─────────────────────────────────────────────────────────
        Text(stringResource(R.string.txt_e346618a), color = theme.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 10.dp))

        Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1A6366F1)), contentAlignment = Alignment.Center) {
                        Text("📚", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_62da2239), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.txt_10932fd3), color = theme.textMuted, fontSize = 12.sp)
                    }
                }
                Text(
                    stringResource(R.string.txt_16f666bb),
                    color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp)
                )
                Button(
                    onClick = { csvLauncher.launch("text/*"); importMsg = null; exportMsg = null },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.txt_e6b9af9f), fontWeight = FontWeight.Bold)
                }
                importMsg?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = if (msg.startsWith("✅")) Color(0x1A10B981) else Color(0x1AF59E0B), border = BorderStroke(1.dp, if (msg.startsWith("✅")) Color(0x4D10B981) else Color(0x4DF59E0B))) {
                        Text(msg, color = if (msg.startsWith("✅")) Green else Amber, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── EXPORTAR ─────────────────────────────────────────────────────────
        Text("📥 " + stringResource(R.string.txt_53d6215e), color = theme.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 10.dp))

        Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1A10B981)), contentAlignment = Alignment.Center) {
                        Text("📊", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_f1fb0dc3), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.txt_30fddd15), color = theme.textMuted, fontSize = 12.sp)
                    }
                }

                // Resumen de lo que se exportará
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0x0D10B981), border = BorderStroke(1.dp, Color(0x1A10B981)), modifier = Modifier.padding(bottom = 14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        val reading  = books.count { it.status == BookStatus.READING || it.status == BookStatus.REREADING }
                        val finished = books.count { it.status == BookStatus.FINISHED }
                        val pending  = books.count { it.status == BookStatus.PENDING }
                        ExportStatCell("$finished", stringResource(R.string.export_stat_finished), Green)
                        ExportStatCell("$reading",  stringResource(R.string.export_stat_reading),  Amber)
                        ExportStatCell("$pending",  stringResource(R.string.export_stat_pending),  theme.textDim)
                        ExportStatCell("${books.size}", stringResource(R.string.export_stat_total), theme.textMain)
                    }
                }

                Text(
                    stringResource(R.string.txt_8ccc576b),
                    color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp)
                )

                Button(
                    onClick = {
                        if (books.isEmpty()) { exportMsg = context.getString(R.string.msg_no_books_export); return@Button }
                        isExporting = true; exportMsg = null; importMsg = null
                        scope.launch {
                            val uri = exportBooksToCSV(context, books)
                            isExporting = false
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Mi biblioteca Lecturameter")
                                    putExtra(Intent.EXTRA_TEXT, "Aquí tienes mi biblioteca exportada desde Lecturameter (${books.size} libros).")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Compartir biblioteca"))
                                exportMsg = context.getString(R.string.msg_export_ready)
                            } else {
                                exportMsg = context.getString(R.string.msg_export_error_gen)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green),
                    enabled = !isExporting && books.isNotEmpty()
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_cad19441), fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_583569fd), fontWeight = FontWeight.Bold)
                    }
                }
                exportMsg?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = if (msg.startsWith("✅")) Color(0x1A10B981) else if (msg.startsWith("⚠️")) Color(0x1AF59E0B) else Color(0x1AF87171), border = BorderStroke(1.dp, if (msg.startsWith("✅")) Color(0x4D10B981) else if (msg.startsWith("⚠️")) Color(0x4DF59E0B) else Color(0x4DF87171)), modifier = Modifier.fillMaxWidth()) {
                        Text(msg, color = if (msg.startsWith("✅")) Green else if (msg.startsWith("⚠️")) Amber else Red, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── BACKUP COMPLETO ───────────────────────────────────────────────────
        Text(stringResource(R.string.txt_78932aed), color = theme.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 10.dp))

        Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1AF59E0B)), contentAlignment = Alignment.Center) {
                        Text("🔒", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_c5ed541f), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.txt_28d57875), color = theme.textMuted, fontSize = 11.sp)
                    }
                }
                Text(
                    stringResource(R.string.txt_f7464b62),
                    color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp)
                )
                // Exportar backup JSON
                Button(
                    onClick = {
                        if (books.isEmpty()) { backupMsg = context.getString(R.string.msg_no_data_export); return@Button }
                        isBackingUp = true; backupMsg = null
                        scope.launch {
                            val uri = withContext(kotlinx.coroutines.Dispatchers.IO) { exportFullBackup(context, vm) }
                            isBackingUp = false
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Backup Lecturameter")
                                    putExtra(Intent.EXTRA_TEXT, "Copia de seguridad completa de Lecturameter (${books.size} libros, ${sessions.size} sesiones).")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Guardar backup"))
                                backupMsg = context.getString(R.string.msg_backup_ready_share)
                            } else {
                                backupMsg = context.getString(R.string.msg_backup_error_gen)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber),
                    enabled = !isBackingUp && books.isNotEmpty()
                ) {
                    if (isBackingUp) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_cad19441), fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_ea8ca034), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Restaurar backup JSON
                OutlinedButton(
                    onClick = {
                        jsonRestoreLauncher.launch(arrayOf("application/json", "text/plain", "text/*", "*/*"))
                        backupMsg = null
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Amber),
                ) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp), tint = Amber)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.txt_37af485a), fontWeight = FontWeight.Bold, color = Amber)
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = theme.border)
                Spacer(Modifier.height(12.dp))
                // Guardar backup local en Descargas
                Text(stringResource(R.string.local_backup_last, lastLocalBackupText), color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                OutlinedButton(
                    onClick = {
                        if (!localBackupOn) { showActivateLocalBk = true; return@OutlinedButton }
                        if (books.isEmpty()) { backupMsg = context.getString(R.string.msg_no_data_export); return@OutlinedButton }
                        runWithStoragePerm {
                        isLocalAutoBackingUp = true; backupMsg = null
                        val req = OneTimeWorkRequestBuilder<JsonBackupWorker>().build()
                        val wm = WorkManager.getInstance(context)
                        wm.enqueueUniqueWork(
                            "lecturameter_json_backup_manual",
                            ExistingWorkPolicy.REPLACE,
                            req
                        )
                        scope.launch {
                            // Bug fix v21.15: antes se asumía éxito tras esperar 3s fijos sin
                            // comprobar si el Worker había terminado de verdad (race condition —
                            // con muchas portadas embebidas en base64 puede tardar más). Ahora se
                            // sigue el estado real del WorkInfo hasta que termina, con timeout.
                            val finalState = kotlinx.coroutines.withTimeoutOrNull(20_000) {
                                wm.getWorkInfosForUniqueWorkFlow("lecturameter_json_backup_manual")
                                    .mapNotNull { infos -> infos.firstOrNull { it.id == req.id } }
                                    .first { it.state.isFinished }
                            }
                            isLocalAutoBackingUp = false
                            lastLocalBackupText = formatLastLocalBackup(context, prefs)
                            val customFolder = prefs.getString("local_backup_folder_uri", null)
                            backupMsg = when {
                                finalState == null ->
                                    context.getString(R.string.msg_backup_pending)
                                finalState.state == androidx.work.WorkInfo.State.SUCCEEDED ->
                                    if (customFolder != null) context.getString(R.string.msg_backup_saved_custom, readableFolderName(customFolder))
                                    else context.getString(R.string.msg_backup_saved_default)
                                else -> context.getString(R.string.msg_backup_error)
                            }
                        }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (localBackupOn) Green else theme.border),
                    enabled = !isLocalAutoBackingUp && books.isNotEmpty()
                ) {
                    if (isLocalAutoBackingUp) {
                        CircularProgressIndicator(color = Green, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_9b9c0afb), fontWeight = FontWeight.Bold, color = Green)
                    } else {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp), tint = if (localBackupOn) Green else theme.textDim)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_56446f3b), fontWeight = FontWeight.Bold, color = if (localBackupOn) Green else theme.textDim)
                    }
                }
                backupMsg?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = if (msg.startsWith("✅")) Color(0x1A10B981) else if (msg.startsWith("⚠️")) Color(0x1AF59E0B) else Color(0x1AF87171), border = BorderStroke(1.dp, if (msg.startsWith("✅")) Color(0x4D10B981) else if (msg.startsWith("⚠️")) Color(0x4DF59E0B) else Color(0x4DF87171)), modifier = Modifier.fillMaxWidth()) {
                        Text(msg, color = if (msg.startsWith("✅")) Green else if (msg.startsWith("⚠️")) Amber else Red, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── GOOGLE DRIVE ──────────────────────────────────────────────────────
        Text(stringResource(R.string.txt_f40582e2), color = theme.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 10.dp))

        Surface(shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1A4285F4)), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.txt_cdd0bfa9), fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_afe10477), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        val driveIntervalHours = prefs.getInt("backup_interval_hours", 2)
                        Text(stringResource(R.string.drive_backup_auto_hint, driveIntervalHours), color = theme.textMuted, fontSize = 12.sp)
                    }
                }

                if (driveAccount == null) {
                    Text(
                        stringResource(R.string.txt_ed177a89),
                        color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp)
                    )
                    Button(
                        onClick = { driveSignInLauncher.launch(driveSignInClient.signInIntent) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                    ) {
                        Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_737672f6), fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(driveAccount!!.email ?: "", color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.drive_backup_last, lastBackupText), color = theme.textDim, fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            // Cambiar de cuenta sin perder la configuración: cerrar sesión y volver a elegir cuenta
                            driveSignInClient.signOut().addOnCompleteListener {
                                driveMsg = null
                                driveSignInLauncher.launch(driveSignInClient.signInIntent)
                            }
                        }) {
                            Text(stringResource(R.string.txt_d1bdc329), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = {
                            driveSignInClient.signOut()
                            driveAccount = null
                            driveMsg = null
                        }) {
                            Text(stringResource(R.string.txt_f306dfe2), color = Red, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = {
                            if (!driveBackupOn) { showActivateDriveBk = true; return@Button }
                            isDriveLoading = true; driveMsg = null
                            scope.launch {
                                val result = DriveBackupManager.backup(context, prefs)
                                isDriveLoading = false
                                lastBackupText = DriveBackupManager.formatLastBackup(context, prefs)
                                driveMsg = result.fold(
                                    onSuccess = { context.getString(R.string.msg_drive_saved) },
                                    onFailure = { context.getString(R.string.msg_drive_error, it.message ?: "") }
                                )
                            }
                        },
                        enabled = !isDriveLoading,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (driveBackupOn) Color(0xFF4285F4) else theme.border)
                    ) {
                        if (isDriveLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.txt_9b9c0afb), fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.txt_3f98c4c5), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            isDriveLoading = true; driveMsg = null
                            scope.launch {
                                val result = DriveBackupManager.restore(context, vm, prefs)
                                isDriveLoading = false
                                driveMsg = result.fold(
                                    onSuccess = { "✅ $it" },
                                    onFailure = { context.getString(R.string.msg_drive_error, it.message ?: "") }
                                )
                            }
                        },
                        enabled = !isDriveLoading,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF4285F4))
                    ) {
                        Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp), tint = Color(0xFF4285F4))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_c8a3ca02), fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                    }
                }

                driveMsg?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    val isOk = msg.startsWith("✅")
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isOk) Color(0x1A10B981) else Color(0x1AF87171),
                        border = BorderStroke(1.dp, if (isOk) Color(0x4D10B981) else Color(0x4DF87171)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(msg, color = if (isOk) Green else Red, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun ExportStatCell(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = color.copy(alpha = 0.7f), fontSize = 10.sp)
    }
}
