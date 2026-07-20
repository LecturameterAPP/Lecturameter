package com.lecturameter

// DetailScreen: detalle del libro (sesiones, crono, ediciones, predictor).
// Extraido de MainActivity.kt el 15-07-2026 (ruptura del monolito, sin cambios funcionales).


import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import android.widget.Toast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import com.lecturameter.model.*
import com.lecturameter.utils.*
import androidx.navigation.compose.composable

@Composable
fun DetailScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, id: Long, highlightDate: String? = null, onBack: () -> Unit, onAuthorClick: (String) -> Unit) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val context = LocalContext.current
    val book = books.find { it.id == id } ?: run {
        // rm-4: si este era el libro con sesión pendiente, limpiar prefs para evitar loop
        val tp = context.getSharedPreferences(TimerService.TIMER_PREFS, android.content.Context.MODE_PRIVATE)
        if (tp.getLong("book_id", -1L) == id) {
            tp.edit().clear().apply()
            TimerStateHolder.shouldOpenDialog = false
            TimerStateHolder.activeBookId = -1L
        }
        onBack()
        return
    }
    val stats = getStats(book)
    val scope = rememberCoroutineScope()
    // Theming: acento del tema activo (oro en Cuero, morado en Aurora) y su color de contenido
    val acc = accentForTheme(theme)
    val onAcc = onAccentColor(theme)
    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) { true }
    }
    val activeEdition = vm.editionsForBook(id).firstOrNull { it.isActive }
    val activeLang = activeEdition?.language ?: "original"
    val bookSessions = vm.sessionsForBookAndLanguage(id, activeLang)
    val detailSessionNumberMap = remember(bookSessions) {
        // v20.1 (G3): numerar por ciclo — cada ciclo (readingIndex) empieza en #1
        val map = mutableMapOf<Long, Int>()
        val byCycle = bookSessions.groupBy { it.readingIndex ?: 0 }
        for ((_, cycleSessions) in byCycle) {
            cycleSessions
                .sortedBy { it.date + it.id.toString().padStart(20, '0') }
                .forEachIndexed { index, session -> map[session.id] = index + 1 }
        }
        map
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    // B-019: sesión huérfana detectada al arrancar el crono de este libro.
    var orphanSecondsPending by remember { mutableStateOf(0L) }
    var pendingResumeStart   by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showStatusMenu by remember { mutableStateOf(false) }
    // v20.0 (G5): confirmación abandono
    var showAbandonDialog by remember { mutableStateOf(false) }
    var showCoverDialog by remember { mutableStateOf(false) }
    var showSessionDialog by remember { mutableStateOf(false) }
    var showConflictSessionDialog by remember { mutableStateOf(false) }
    var showSessionSummary by remember(id) { mutableStateOf(highlightDate != null) }
    // Diálogo educativo permiso POST_NOTIFICATIONS (Android 13+)
    var showNotifPermDialog by remember { mutableStateOf(false) }
    var showNotifPermDeniedDialog by remember { mutableStateOf(false) }
    var pendingTimerAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showFuncPagesOnboarding by remember { mutableStateOf(!prefs.getBoolean("func_pages_onboarding_done", false)) }
    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) showNotifPermDeniedDialog = true
        // Lanzar igualmente — el servicio funciona, solo no habrá notificación visible
        pendingTimerAction?.invoke()
        pendingTimerAction = null
    }
    if (showNotifPermDialog) {
        AlertDialog(
            onDismissRequest = { showNotifPermDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_5c0e66ea), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_33fe5747), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showNotifPermDialog = false
                    if (android.os.Build.VERSION.SDK_INT >= 33)
                        notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else
                        pendingTimerAction?.invoke().also { pendingTimerAction = null }
                }) { Text(stringResource(R.string.txt_5fcafeb2), color = acc, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNotifPermDialog = false
                    showNotifPermDeniedDialog = true
                    // Iniciar igualmente sin notificación
                    pendingTimerAction?.invoke()
                    pendingTimerAction = null
                }) { Text(stringResource(R.string.txt_ca8f9bb3), color = Red) }
            }
        )
    }
    // Aviso tras rechazar: explica cómo activarlo más tarde desde permisos de la app
    if (showNotifPermDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showNotifPermDeniedDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_1fa1de14), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_9731be9d), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showNotifPermDeniedDialog = false }) { Text(stringResource(R.string.txt_3f346645), color = acc, fontWeight = FontWeight.Bold) }
            }
        )
    }
    if (showFuncPagesOnboarding) {
        AlertDialog(
            onDismissRequest = {
                showFuncPagesOnboarding = false
                prefs.edit().putBoolean("func_pages_onboarding_done", true).apply()
            },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.func_pages_onboarding_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.func_pages_onboarding_body), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    showFuncPagesOnboarding = false
                    prefs.edit().putBoolean("func_pages_onboarding_done", true).apply()
                }) { Text(stringResource(R.string.func_pages_onboarding_cta), color = acc, fontWeight = FontWeight.Bold) }
            }
        )
    }
    fun requestNotifPermThenStart(action: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val alreadyAsked = prefs.getBoolean("notif_perm_asked", false)
            val activity = context as? android.app.Activity
            val canAskAgain = activity != null && androidx.core.app.ActivityCompat
                .shouldShowRequestPermissionRationale(activity, android.Manifest.permission.POST_NOTIFICATIONS)
            if (!alreadyAsked || canAskAgain) {
                prefs.edit().putBoolean("notif_perm_asked", true).apply()
                pendingTimerAction = action
                showNotifPermDialog = true
            } else {
                // Permanentemente denegado: arrancar sin notificación y enseñar aviso de ajustes
                action()
                showNotifPermDeniedDialog = true
            }
        } else {
            action()
        }
    }

    // B-019: si el proceso murió con el crono en marcha (crash, force-stop, matanza
    // del sistema), los segundos siguen en prefs y el servicio los restauraba SIN
    // avisar — al arrancar "una sesión nueva" del mismo libro aparecía el tiempo
    // viejo. Ahora se pregunta y se enseña cuánto es.
    fun orphanTimerSeconds(): Long {
        if (TimerStateHolder.seconds != 0L) return 0L   // el crono ya vive en memoria
        val tp = context.getSharedPreferences(com.lecturameter.TimerService.TIMER_PREFS, android.content.Context.MODE_PRIVATE)
        val savedSecs = tp.getLong("running_seconds", 0L)
        val savedBook = tp.getLong("running_book_id", -1L)
        return if (savedSecs > 0L && savedBook == id) savedSecs else 0L
    }

    fun startTimerWithPermCheck(action: () -> Unit) {
        val orphan = orphanTimerSeconds()
        if (orphan > 0L) {
            orphanSecondsPending = orphan
            pendingResumeStart = action
        } else {
            requestNotifPermThenStart(action)
        }
    }
    // D-002/T1: llegada desde el selector rápido del home (⏱️) — arrancar el crono
    // con el mismo flujo de permisos que el botón ▶ del detalle.
    LaunchedEffect(id) {
        if (TimerQuickStart.pendingBookId == id) {
            TimerQuickStart.pendingBookId = -1L
            if (!TimerStateHolder.running) {
                startTimerWithPermCheck { com.lecturameter.TimerService.start(context, id, book.title) }
            } else if (TimerStateHolder.activeBookId != id) {
                // Feedback 14-07 (F13b): crono activo en OTRO libro — misma pantalla de
                // conflicto que el ▶ manual del detalle (antes: no pasaba nada y el crono
                // seguía corriendo "invisible" para este libro)
                showConflictSessionDialog = true
            }
        }
    }
    // v20.0 (G2): map de secciones de historial expandidas (key = readingIndex).
    // Si highlightDate viene del heatmap se abrirá la sección correspondiente automáticamente.
    val sectionExpanded = remember(id) { androidx.compose.runtime.mutableStateMapOf<Int, Boolean>() }
    var activeHighlightDate by remember(id) { mutableStateOf(highlightDate) }

    // Advertencia al cambiar edición activa (sin timer): informa que cambian sesiones/comentarios visibles
    var showActiveEditionWarning by remember { mutableStateOf(false) }
    // B-024: borrar una edición no pedía confirmación (la ✕ actuaba a la primera).
    var pendingRemoveEditionId by remember { mutableStateOf<Long?>(null) }
    var pendingActiveEditionId by remember { mutableStateOf<Long?>(null) }

    // Advertencia cuando hay sesión en curso y se intenta cambiar edición: pausa + advierte
    var showTimerEditionWarning by remember { mutableStateOf(false) }
    var pendingChangeEditionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Si el aviso viene de onSetActive (no de onChangeEdition), aquí guardamos el edId destino
    // para aplicarlo DESPUÉS de que el usuario guarde la sesión
    var pendingSetActiveEditionAfterSession by remember { mutableStateOf<Long?>(null) }

    // ── Estado de ediciones ───────────────────────────────────────────────────
    var showChangeEditionSheet by remember { mutableStateOf(false) }
    var showAddEditionSheet    by remember { mutableStateOf(false) }
    var editionSheetTarget     by remember { mutableStateOf<Long?>(null) }  // editionId a cambiar
    var availableEditions      by remember { mutableStateOf<List<EditionResult>>(emptyList()) }
    var editionsLoading        by remember { mutableStateOf(false) }
    var editionsNetworkError   by remember { mutableStateOf(false) }
    var selectedEditionResult  by remember { mutableStateOf<EditionResult?>(null) }
    // B-023: edición escaneada cuyo autor no casa con el del libro — a confirmar.
    var pendingScannedMismatch by remember { mutableStateOf<EditionResult?>(null) }
    // P-028: la última edición escaneada tiene un idioma asignado por suposición, no por
    // detección real (ni la API ni el prefijo del ISBN lo confirman). Avisar al usuario.
    var scannedEditionLanguageUncertain by remember { mutableStateOf(false) }
    val bookEditions = vm.editionsForBook(id)
    // v20.0 (G2): states obsoletos eliminados (search/order ahora son locales por sección).
    var selectedRating by remember(book.rating) { mutableStateOf(book.rating) }    // Sincronizar solo si el valor guardado cambia externamente (ej. otro dispositivo)
    // v21.41: selectedRating usa remember(book.rating) — reacciona directo sin LaunchedEffect
    // El comentario se lee de la edición activa (si la tiene) o del libro como fallback legacy
    val editionComment = activeEdition?.comment ?: ""
    val effectiveComment = editionComment.ifBlank { book.comment }
    var commentText by remember(effectiveComment) { mutableStateOf(effectiveComment) }
    var editingComment by remember { mutableStateOf(false) }
    var editingPages by remember { mutableStateOf(false) }
    var pagesInput by remember(book.pages) { mutableStateOf(book.pages.toString()) }
    // Valor capturado al abrir edición — permite que Cancelar restaure el dato original
    var pagesOriginal by remember { mutableStateOf(book.pages) }
    var coverUrlInput by remember(book.coverUrl) { mutableStateOf(book.coverUrl ?: "") }
    val isRefreshingCover = vm.isCoverRefreshing(id)
    val scrollState = rememberScrollState()
    // Contador que se incrementa para disparar el scroll al fondo tras guardar sesion
    var scrollToBottomTrigger by remember { mutableStateOf(0) }
    val capturedMaxValueBeforeExpand = remember { mutableStateOf(0) }

    // Cuando se activa el trigger, captura el maxValue actual (con formularios cerrados),
    // luego espera a que maxValue CAMBIE (layout expandido con formularios abiertos) y scrollea.
    LaunchedEffect(scrollToBottomTrigger) {
        if (scrollToBottomTrigger == 0) return@LaunchedEffect
        capturedMaxValueBeforeExpand.value = scrollState.maxValue
        // Esperar hasta que el layout se expanda (maxValue aumenta) o timeout de 1s
        val deadline = System.currentTimeMillis() + 1000L
        while (System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(50)
            if (scrollState.maxValue > capturedMaxValueBeforeExpand.value) break
        }
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Si viene del heatmap con highlightDate: abrir sesiones y hacer scroll al fondo
    // v21.41: delays reducidos — sensación de respuesta instantánea
    LaunchedEffect(highlightDate) {
        if (highlightDate != null) {
            showSessionSummary = true
            // v20.0 (G2): abrir también la sección correspondiente al ciclo de la sesión matcheada
            val matchingSession = bookSessions.firstOrNull { it.date == highlightDate }
            val idx = matchingSession?.readingIndex ?: 0
            sectionExpanded[idx] = true
            // Delay inicial mínimo para que el layout empiece a expandirse
            kotlinx.coroutines.delay(30)
            val before = scrollState.maxValue
            val deadline = System.currentTimeMillis() + 500L
            while (System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(20)
                if (scrollState.maxValue > before) break
            }
            // Scroll al fondo para mostrar las sesiones expandidas
            scrollState.animateScrollTo(scrollState.maxValue)
            // Segundo intento por si el layout terminó de medirse después del scroll
            kotlinx.coroutines.delay(80)
            if (scrollState.value < scrollState.maxValue) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }

    var refreshMsg by remember { mutableStateOf("") }

    // ── Temporizador automático (con servicio foreground para pantalla bloqueada) ─
    // El estado vive en TimerStateHolder. Aquí mantenemos copias locales que se
    // sincronizan periódicamente para que la UI se recomponga.
    var timerState by remember { mutableStateOf(
        when {
            !TimerStateHolder.running || TimerStateHolder.activeBookId != id -> TimerState.IDLE
            TimerStateHolder.paused   -> TimerState.PAUSED
            else                      -> TimerState.RUNNING
        }
    ) }
    var timerSeconds by remember { mutableStateOf(TimerStateHolder.seconds) }
    var autoSessionMinutes by remember { mutableStateOf<Int?>(null) }

    fun closeSessionDialog() {
        showSessionDialog = false
        autoSessionMinutes = null
        TimerStateHolder.shouldOpenDialog = false
        com.lecturameter.TimerService.cancelSessionEndNotification(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            (context as? android.app.Activity)?.setShowWhenLocked(false)
        }
        // Si el aviso venía de onSetActive, aplicar el cambio de edición activa ahora
        // que la sesión ya se ha guardado (o cancelado) con la edición correcta
        val switchTo = pendingSetActiveEditionAfterSession
        if (switchTo != null) {
            pendingSetActiveEditionAfterSession = null
            vm.setActiveEdition(id, switchTo, prefs)
            refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
        }
    }

    // Polling cada 500ms para sincronizar el estado con el servicio.
    // Es ligero y permite que cambios desde la notificación (Pause/Resume) se reflejen
    // en la UI sin necesitar Flow ni LiveData.
    LaunchedEffect(Unit) {
        val timerPrefs = context.getSharedPreferences(com.lecturameter.TimerService.TIMER_PREFS, android.content.Context.MODE_PRIVATE)
        // Comprobación inmediata: en memoria O en prefs (proceso muerto y restaurado)
        val inMemory = TimerStateHolder.shouldOpenDialog && TimerStateHolder.activeBookId == id
        val inPrefs  = timerPrefs.getBoolean("pending", false) && timerPrefs.getLong("book_id", -1L) == id
        if (inMemory || inPrefs) {
            val secs = if (TimerStateHolder.seconds > 0L) TimerStateHolder.seconds
                       else timerPrefs.getLong("seconds", 60L)
            timerPrefs.edit().clear().apply()
            TimerStateHolder.shouldOpenDialog = false
            com.lecturameter.TimerService.cancelSessionEndNotification(context)
            autoSessionMinutes = ((secs + 30) / 60).toInt().coerceAtLeast(1)
            TimerStateHolder.reset()
            showSessionDialog = true
        }
        while (true) {
            kotlinx.coroutines.delay(500L)
            timerSeconds = TimerStateHolder.seconds
            val newState = when {
                !TimerStateHolder.running || TimerStateHolder.activeBookId != id -> TimerState.IDLE
                TimerStateHolder.paused   -> TimerState.PAUSED
                else                      -> TimerState.RUNNING
            }
            if (newState != timerState) timerState = newState

            // Si el usuario pulsó Finalizar desde la notificación, abrir el diálogo
            if (TimerStateHolder.shouldOpenDialog) {
                val secs = TimerStateHolder.seconds
                TimerStateHolder.shouldOpenDialog = false
                com.lecturameter.TimerService.cancelSessionEndNotification(context)
                context.getSharedPreferences(com.lecturameter.TimerService.TIMER_PREFS, android.content.Context.MODE_PRIVATE).edit().clear().apply()
                autoSessionMinutes = ((secs + 30) / 60).toInt().coerceAtLeast(1)
                TimerStateHolder.reset()
                showSessionDialog = true
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // Tomar permiso persistente si está disponible (content URIs de galería)
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* algunos URIs no admiten permisos persistentes */ }

            // Copiar siempre al almacenamiento interno para garantizar persistencia
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val activeEdId = book.editions.firstOrNull { it.isActive }?.id
                val localPath = copyUriToInternalStorage(context, uri, id, activeEdId)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    vm.updateCover(id, localPath ?: uri.toString(), prefs)
                    refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
                }
            }
        }
    }

    // ── Diálogo: Sesión en curso en otro libro ────────────────────────────────
    if (showConflictSessionDialog) {
        AlertDialog(
            onDismissRequest = { showConflictSessionDialog = false },
            title = { Text(stringResource(R.string.txt_8bac5764), color = theme.textMain) },
            text = { Text(stringResource(R.string.txt_fb71a7c6), color = theme.textMuted) },
            confirmButton = {
                TextButton(onClick = {
                    showConflictSessionDialog = false
                    // Si estaba pausado, reanudar; si estaba corriendo, dejarlo correr
                    if (TimerStateHolder.paused) {
                        com.lecturameter.TimerService.resume(context)
                    }
                }) { Text(stringResource(R.string.txt_bafd7322), color = theme.textMuted) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConflictSessionDialog = false
                    val secs = TimerStateHolder.seconds
                    val mins = ((secs + 30) / 60).toInt().coerceAtLeast(1)
                    com.lecturameter.TimerService.stop(context, showEndNotification = false)
                    TimerStateHolder.shouldOpenDialog = false
                    TimerStateHolder.reset()
                    autoSessionMinutes = mins
                    timerSeconds = 0
                    showSessionDialog = true
                }) { Text(stringResource(R.string.txt_bdd207ee), color = Green) }
            },
            containerColor = theme.bgMid
        )
    }

    // ── Sheet: Cambiar/Añadir edición ─────────────────────────────────────────
    if (showChangeEditionSheet || showAddEditionSheet) {
        val isAdding = showAddEditionSheet
        // v2.5: escanear ISBN de la edición física directamente desde el sheet
        val editionScanLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val scanned = result.data?.getStringExtra("isbn")
            if (result.resultCode == android.app.Activity.RESULT_OK && !scanned.isNullOrBlank()) {
                editionsLoading = true
                scope.launch {
                    var ed = fetchEditionByIsbn(scanned)
                    if (ed == null) {
                        // Cadena reforzada v2.5: OL edición directa vía fetchIsbnFullMetadata
                        val meta = withContext(Dispatchers.IO) { fetchIsbnFullMetadata(scanned) }
                        if (!meta.title.isNullOrBlank()) {
                            val (lId, lLabel, lFlag) = isbnToLanguageMeta(scanned)
                            ed = EditionResult(lId, lLabel, lFlag, meta.title, meta.pages ?: 0, meta.coverUrl, scanned, "", "", meta.author.orEmpty())
                        }
                    }
                    // Feedback 2.6 (caso Gollancz 9781399630467): las ediciones tan nuevas que
                    // aún no están indexadas en NINGUNA API (verificado: ni OL ni Google conocen
                    // ese ISBN) no se podían añadir. Como aquí estamos en el contexto de un libro
                    // concreto, ofrecemos la edición con el ISBN escaneado + título del libro +
                    // idioma inferido del prefijo ISBN; el usuario decide si añadirla.
                    val resolved = ed ?: run {
                        val (lId, lLabel, lFlag) = isbnToLanguageMeta(scanned)
                        android.widget.Toast.makeText(context, context.getString(R.string.edition_scan_unindexed, scanned), android.widget.Toast.LENGTH_LONG).show()
                        EditionResult(lId, lLabel, lFlag, book.title, 0, null, scanned, "", "")
                    }
                    editionsLoading = false
                    // P-028: si el idioma asignado viene solo de un prefijo ISBN no reconocido
                    // (isbnToLanguageMeta cayó en su valor por defecto), no es una detección de
                    // fiar. Avisamos para que el usuario revise el idioma antes de guardar.
                    scannedEditionLanguageUncertain = !isbnLanguageIsConfident(scanned)
                    // B-023: si el ISBN pertenece claramente a otra obra (autor distinto),
                    // preguntar antes de colarla como edición de este libro.
                    if (editionAuthorMismatch(book.author, resolved.author)) {
                        pendingScannedMismatch = resolved
                    } else {
                        availableEditions = listOf(resolved) + availableEditions.filter { it.isbn != resolved.isbn }
                        selectedEditionResult = resolved
                    }
                }
            }
        }
        val editionCamPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) editionScanLauncher.launch(android.content.Intent(context, ScannerActivity::class.java))
        }

        // B-023: el ISBN escaneado parece de otra obra. No lo bloqueamos (puede ser un
        // recopilatorio, un seudónimo o un dato malo de la API), pero que sea decisión
        // explícita y no un añadido silencioso.
        pendingScannedMismatch?.let { scannedEd ->
            AlertDialog(
                onDismissRequest = { pendingScannedMismatch = null },
                containerColor = theme.bgMid,
                title = { Text(stringResource(R.string.edition_scan_mismatch_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        stringResource(
                            R.string.edition_scan_mismatch_msg,
                            scannedEd.title, scannedEd.author, book.title, book.author
                        ),
                        color = theme.textMuted, fontSize = 13.sp
                    )
                },
                dismissButton = {
                    TextButton(onClick = { pendingScannedMismatch = null }) {
                        Text(stringResource(R.string.txt_847607d7), color = Red)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        availableEditions = listOf(scannedEd) + availableEditions.filter { it.isbn != scannedEd.isbn }
                        selectedEditionResult = scannedEd
                        pendingScannedMismatch = null
                    }) {
                        Text(stringResource(R.string.edition_scan_mismatch_add), color = acc)
                    }
                }
            )
        }
        BackHandler {
            showChangeEditionSheet = false
            showAddEditionSheet = false
            scannedEditionLanguageUncertain = false
        }
        AlertDialog(
            onDismissRequest = { showChangeEditionSheet = false; showAddEditionSheet = false; scannedEditionLanguageUncertain = false },
            containerColor = theme.bgMid,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isAdding) stringResource(R.string.sheet_add_edition) else stringResource(R.string.sheet_change_edition),
                        color = theme.textMain, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    // v2.5: escanear ISBN de una edición física
                    IconButton(
                        onClick = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                editionScanLauncher.launch(android.content.Intent(context, ScannerActivity::class.java))
                            } else {
                                editionCamPermLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        // Feedback 2.6: icono de código de barras (como en el resto de la app), no QR
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_barcode), contentDescription = "Scan ISBN", tint = acc, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            if (!editionsLoading) {
                                availableEditions = emptyList()
                                editionsLoading = true
                                vm.loadAvailableEditions(
                                    id,
                                    forceRefresh = true,
                                    onPartial = { partial -> availableEditions = partial }   // r3 feedback progresivo
                                ) { results ->
                                    availableEditions = results
                                    editionsLoading = false
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh editions", tint = if (editionsLoading) theme.textDim else acc, modifier = Modifier.size(18.dp))
                    }
                }
            },
            text = {
                // A11: con fuentes de accesibilidad grandes el contenido del diálogo se
                // cortaba (no tenía scroll). La lista interna ya está acotada en altura, así
                // que envolver el Column en verticalScroll no anida scrolls sin límite.
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Text(
                        if (isAdding)
                            stringResource(R.string.add_edition_info)
                        else
                            stringResource(R.string.txt_c1404abf),
                        color = theme.textMuted, fontSize = 13.sp
                    )
                    // P-028: el idioma de la última edición escaneada es solo una suposición
                    // (ningún prefijo ISBN reconocido ni metadato de API lo confirma).
                    if (scannedEditionLanguageUncertain) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = acc.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, acc.copy(alpha = 0.3f))
                        ) {
                            Text(
                                stringResource(R.string.edition_scan_language_uncertain),
                                modifier = Modifier.padding(10.dp),
                                color = theme.textMain, fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    when {
                        editionsLoading -> {
                            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = acc, modifier = Modifier.size(28.dp))
                            }
                        }
                        editionsNetworkError -> {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📡", fontSize = 32.sp)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.txt_75fe6e58), color = Red, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.txt_af20f31b), color = Red.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        }
                        availableEditions.isEmpty() -> {
                            Text(stringResource(R.string.txt_fd67b9a7), color = theme.textDim, fontSize = 12.sp)
                        }
                        else -> {
                            val esEditions = availableEditions.filter { it.language == "es" }
                            val enEditions = availableEditions.filter { it.language != "es" }
                            // showingEs: true = Español, false = Global/English
                            var showingEs by remember { mutableStateOf(esEditions.isNotEmpty()) }
                            val activeEditions = if (showingEs) esEditions else enEditions

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // ── Toggle Español / Global ───────────────────
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.bgSurf)
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    listOf(true to stringResource(R.string.txt_95b01315), false to "🌐 Global").forEach { (isEs, label) ->
                                        val active = showingEs == isEs
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (active) acc else Color.Transparent)
                                                .clickable {
                                                    showingEs = isEs
                                                    // Limpiar selección si cambia de idioma
                                                    if (selectedEditionResult != null) {
                                                        val selLang = selectedEditionResult!!.language
                                                        if ((isEs && selLang != "es") || (!isEs && selLang == "es"))
                                                            selectedEditionResult = null
                                                    }
                                                }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                label,
                                                color = if (active) onAcc else theme.textDim,
                                                fontSize = 12.sp,
                                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                                // ── Lista de ediciones del idioma activo (scrollable) ─
                                if (activeEditions.isEmpty()) {
                                    Text(
                                        if (showingEs) stringResource(R.string.msg_editions_not_found_es)
                                        else stringResource(R.string.msg_editions_not_found_other),
                                        color = theme.textDim, fontSize = 12.sp
                                    )
                                } else {
                                    val listScrollState = androidx.compose.foundation.rememberScrollState()
                                    Column(
                                        modifier = Modifier
                                            .heightIn(max = 320.dp)
                                            .verticalScroll(listScrollState),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        activeEditions.forEach { ed ->
                                            val isSelected = selectedEditionResult == ed
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) acc.copy(alpha = 0.13f) else theme.bgSurf,
                                                border = BorderStroke(1.dp, if (isSelected) acc else theme.border),
                                                modifier = Modifier.fillMaxWidth().clickable { selectedEditionResult = ed; scannedEditionLanguageUncertain = false }
                                            ) {
                                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Text(ed.flag, fontSize = 22.sp)
                                                    Column(Modifier.weight(1f)) {
                                                        Text(ed.title.ifBlank { book.title }, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                        Text("${ed.languageLabel} · ${ed.publisher.ifBlank { "-" }} · ${ed.publishYear.ifBlank { "-" }}", color = theme.textDim, fontSize = 11.sp)
                                                        if (!ed.isbn.isNullOrBlank()) Text("ISBN: ${ed.isbn}", color = theme.textMuted, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                                        if (ed.pages > 0) Text(stringResource(R.string.search_pages_count, ed.pages), color = theme.textMuted, fontSize = 11.sp)
                                                    }
                                                    if (isSelected) Text("✓", color = acc, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val sel = selectedEditionResult
                        if (sel != null) {
                            val newEdition = BookEdition(
                                id            = if (isAdding) newEditionId() else (editionSheetTarget ?: newEditionId()),
                                language      = sel.language,
                                languageLabel = sel.languageLabel,
                                flag          = sel.flag,
                                title         = sel.title.ifBlank { book.title },
                                pages         = sel.pages,
                                coverUrl      = sel.coverUrl,
                                isbn          = sel.isbn,
                                publisher     = sel.publisher,
                                publishYear   = sel.publishYear,
                                isActive      = !isAdding  // si cambiamos, la nueva es activa; si añadimos, no
                            )
                            vm.upsertEdition(id, newEdition, prefs)
                            refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
                        }
                        showChangeEditionSheet = false; showAddEditionSheet = false; scannedEditionLanguageUncertain = false
                    },
                    enabled = selectedEditionResult != null
                ) { Text(if (isAdding) stringResource(R.string.txt_d20f652b) else stringResource(R.string.txt_d1bdc329), color = if (selectedEditionResult != null) acc else theme.textDim) }
            },
            confirmButton = {
                TextButton(onClick = { showChangeEditionSheet = false; showAddEditionSheet = false; scannedEditionLanguageUncertain = false }) {
                    Text(stringResource(R.string.txt_847607d7), color = Red)
                }
            }
        )
    }

    // B-019: elegir entre retomar el tiempo huérfano o empezar de cero. Retomar =
    // dejar las prefs como están (el servicio las restaura); empezar de cero =
    // limpiarlas antes de arrancar.
    if (orphanSecondsPending > 0L && pendingResumeStart != null) {
        val secs = orphanSecondsPending
        val h = secs / 3600; val m = (secs % 3600) / 60; val s = secs % 60
        val pretty = if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
        val startAction = pendingResumeStart
        AlertDialog(
            onDismissRequest = { orphanSecondsPending = 0L; pendingResumeStart = null },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.timer_resume_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.timer_resume_msg, pretty), color = theme.textMuted, fontSize = 13.sp) },
            dismissButton = {
                TextButton(onClick = {
                    context.getSharedPreferences(com.lecturameter.TimerService.TIMER_PREFS, android.content.Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    TimerStateHolder.reset()
                    orphanSecondsPending = 0L; pendingResumeStart = null
                    startAction?.let { requestNotifPermThenStart(it) }
                }) { Text(stringResource(R.string.timer_resume_fresh), color = Red) }
            },
            confirmButton = {
                TextButton(onClick = {
                    orphanSecondsPending = 0L; pendingResumeStart = null
                    startAction?.let { requestNotifPermThenStart(it) }
                }) { Text(stringResource(R.string.timer_resume_confirm, pretty), color = acc, fontWeight = FontWeight.Bold) }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(onDismissRequest = { showDeleteDialog = false }, title = { Text(stringResource(R.string.txt_b375487f), color = theme.textMain) }, text = { Text(stringResource(R.string.txt_2750cc8c, book.title), color = theme.textMuted) },
            confirmButton = { TextButton(onClick = { vm.deleteBook(id, prefs); clearWidgetBookIfSelected(context, id); onBack() }) { Text(stringResource(R.string.txt_5b5c9f9d), color = Red) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = acc) } }, containerColor = theme.bgMid)
    }

    // v20.0 (G5): confirmación abandono
    if (showAbandonDialog) {
        AlertDialog(
            onDismissRequest = { showAbandonDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_13d1d160), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_c13603f4, book.title), color = theme.textMuted) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateStatus(id, BookStatus.DROPPED, prefs)
                    refreshWidgetForBookIfSelected(context, id)
                    showAbandonDialog = false
                }) { Text(stringResource(R.string.txt_39e254c6), color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showAbandonDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = acc) } }
        )
    }

    // ── Diálogo: cambio de edición activa (sin timer) ─────────────────────────
    // B-024: confirmación al borrar una edición. Las sesiones NO se borran (siguen
    // en el libro), solo dejan de estar asociadas a esa edición — el texto lo dice.
    pendingRemoveEditionId?.let { edId ->
        val edTitle = bookEditions.firstOrNull { it.id == edId }?.title?.ifBlank { book.title } ?: book.title
        AlertDialog(
            onDismissRequest = { pendingRemoveEditionId = null },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.edition_delete_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.edition_delete_msg, edTitle), color = theme.textMuted, fontSize = 13.sp) },
            dismissButton = {
                TextButton(onClick = { pendingRemoveEditionId = null }) {
                    Text(stringResource(R.string.txt_847607d7), color = acc)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (vm.removeEdition(id, edId, prefs)) {
                        refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
                    }
                    pendingRemoveEditionId = null
                }) {
                    Text(stringResource(R.string.edition_delete_confirm), color = Red)
                }
            }
        )
    }

    if (showActiveEditionWarning) {
        AlertDialog(
            onDismissRequest = { showActiveEditionWarning = false; pendingActiveEditionId = null },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_ca1da6c8), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.txt_c1404abf),
                    color = theme.textMuted, fontSize = 13.sp
                )
            },
            dismissButton = {
                TextButton(onClick = { showActiveEditionWarning = false; pendingActiveEditionId = null }) {
                    Text(stringResource(R.string.txt_847607d7), color = Red)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val edId = pendingActiveEditionId
                    if (edId != null) {
                        vm.setActiveEdition(id, edId, prefs)
                        refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
                    }
                    showActiveEditionWarning = false; pendingActiveEditionId = null
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = acc) }
            }
        )
    }

    // ── Diálogo: cambio de edición con timer activo ───────────────────────────
    if (showTimerEditionWarning) {
        val isActiveSwitch = pendingSetActiveEditionAfterSession != null
        AlertDialog(
            onDismissRequest = {
                showTimerEditionWarning = false
                pendingChangeEditionAction = null
                pendingSetActiveEditionAfterSession = null
                TimerService.resume(context)
            },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_8bac5764), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isActiveSwitch)
                        "Tienes una sesión en curso. Si cambias de edición ahora, la sesión se guardará automáticamente con la edición actual antes de hacer el cambio."
                    else
                        "Tienes una sesión de lectura en curso. Si cambias de edición ahora, la sesión se finalizará.",
                    color = theme.textMuted, fontSize = 13.sp, textAlign = TextAlign.Justify
                )
            },
            dismissButton = {
                TextButton(onClick = {
                    showTimerEditionWarning = false
                    pendingChangeEditionAction = null
                    pendingSetActiveEditionAfterSession = null
                    TimerService.resume(context)
                }) { Text(stringResource(R.string.txt_847607d7), color = Red) }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTimerEditionWarning = false
                    val action = pendingChangeEditionAction
                    pendingChangeEditionAction = null
                    // Capture seconds BEFORE stopping
                    val secs = TimerStateHolder.seconds
                    val mins = ((secs + 30) / 60).toInt().coerceAtLeast(1)
                    TimerService.stop(context, showEndNotification = false)
                    TimerStateHolder.shouldOpenDialog = false
                    TimerStateHolder.reset()
                    autoSessionMinutes = mins
                    timerSeconds = 0
                    showSessionDialog = true
                    if (action != null) pendingChangeEditionAction = action
                }) { Text(stringResource(R.string.txt_d1cdc7bc), color = acc) }
            }
        )
    }

    // ── Diálogo: Registrar sesión ─────────────────────────────────────────────
    if (showSessionDialog) {
        SessionSaveDialog(
            book = book,
            bookSessions = bookSessions,
            vm = vm,
            prefs = prefs,
            theme = theme,
            autoSessionMinutes = autoSessionMinutes,
            onDismiss = { closeSessionDialog() },
            onSaved = { timerSeconds = 0; closeSessionDialog() }
        )
    }

    if (showCoverDialog) {
        AlertDialog(onDismissRequest = { showCoverDialog = false }, title = { Text(stringResource(R.string.txt_adbe3283), color = theme.textMain) },
            text = {
                Column {
                    Text(stringResource(R.string.txt_aa02a2da), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(value = coverUrlInput, onValueChange = { coverUrlInput = it }, placeholder = { Text(stringResource(R.string.txt_14f2b208), color = theme.textDim) }, colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { imagePicker.launch("image/*"); showCoverDialog = false }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, acc.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = acc)) { Text(stringResource(R.string.txt_5cd0defc)) }
                }
            },
            confirmButton = { TextButton(onClick = { if (coverUrlInput.isNotBlank()) { vm.updateCover(id, coverUrlInput.trim(), prefs); refreshWidgetForBookIfSelected(context, id, clearCoverCache = true) }; showCoverDialog = false }) { Text(stringResource(R.string.txt_f0ed2dc3), color = acc) } },
            dismissButton = { TextButton(onClick = { showCoverDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }, containerColor = theme.bgMid)
    }

    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 28.dp, bottom = 20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
            Column(Modifier.padding(24.dp)) {

                // Cover + Edit + Refresh
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BookCover(book.coverUrl, book.title, size = 120, onBroken = { vm.markCoverBroken(id, prefs) }, isbnFallback = book.isbn)
                    Box(Modifier.align(Alignment.BottomEnd).offset(x = (-32).dp).size(32.dp).clip(CircleShape).background(acc).clickable { coverUrlInput = book.coverUrl ?: ""; showCoverDialog = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Edit, null, tint = onAcc, modifier = Modifier.size(16.dp))
                    }
                    Box(Modifier.align(Alignment.BottomStart).offset(x = 32.dp).size(32.dp).clip(CircleShape).background(if (isRefreshingCover) Color(0xFF64748B) else actionFillColor(theme)).clickable(enabled = !isRefreshingCover) {
                        refreshMsg = ""
                        vm.refreshCover(id, prefs) { coverFound, genreFound ->
                            if (coverFound || genreFound) {
                                refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
                                val parts = listOfNotNull(
                                    if (coverFound) context.getString(R.string.word_cover) else null,
                                    if (genreFound) context.getString(R.string.word_genre) else null
                                )
                                refreshMsg = context.getString(R.string.msg_refresh_updated, parts.joinToString(context.getString(R.string.word_and)))
                            }
                            if (!coverFound || !genreFound) {
                                val missing = listOfNotNull(
                                    if (!coverFound) context.getString(R.string.word_cover) else null,
                                    if (!genreFound) context.getString(R.string.word_genre) else null
                                )
                                val errorMsg = context.getString(R.string.msg_not_found_prefix, missing.joinToString(context.getString(R.string.word_join_nor)))
                                if (refreshMsg.isEmpty()) refreshMsg = errorMsg
                                else refreshMsg = "$refreshMsg · $errorMsg"
                            }
                        }
                    }, contentAlignment = Alignment.Center) {
                        if (isRefreshingCover) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    if (book.noCoverFound) {
                        Box(Modifier.align(Alignment.BottomEnd).offset(x = (-72).dp).size(32.dp).clip(CircleShape).background(Amber), contentAlignment = Alignment.Center) {
                            Text("⚠", fontSize = 14.sp, lineHeight = 14.sp)
                        }
                    }
                }
                if (refreshMsg.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(refreshMsg, color = if (refreshMsg.startsWith("✅")) Green else Amber, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally)) }

                // ── Ediciones ────────────────────────────────────────────────
                Spacer(Modifier.height(14.dp))
                // D-015 (Cuero): nervio de lomo antes de la tarjeta de ediciones
                CueroNervio(theme, Modifier.padding(bottom = 8.dp))
                EditionsSection(
                    book         = book,
                    editions     = bookEditions,
                    theme        = theme,
                    onChangeEdition = { edId ->
                        val action = {
                            editionSheetTarget = edId
                            selectedEditionResult = null
                            availableEditions = emptyList()
                            editionsNetworkError = false
                            if (!isOnline()) {
                                editionsLoading = false
                                editionsNetworkError = true
                                showChangeEditionSheet = true
                            } else {
                                editionsLoading = true
                                showChangeEditionSheet = true
                                vm.loadAvailableEditions(
                                    id,
                                    onPartial = { partial -> availableEditions = partial }   // r3 feedback progresivo
                                ) { results ->
                                    availableEditions = results
                                    editionsLoading = false
                                    // Si tras la carga seguimos offline y no hay resultados → error de red
                                    if (results.isEmpty() && !isOnline()) editionsNetworkError = true
                                }
                            }
                        }
                        if (TimerStateHolder.running && TimerStateHolder.activeBookId == id) {
                            TimerService.pause(context)
                            pendingChangeEditionAction = action
                            showTimerEditionWarning = true
                        } else {
                            action()
                        }
                    },
                    onAddEdition = {
                        val action = {
                            selectedEditionResult = null
                            availableEditions = emptyList()
                            editionsNetworkError = false
                            if (!isOnline()) {
                                editionsLoading = false
                                editionsNetworkError = true
                                showAddEditionSheet = true
                            } else {
                                editionsLoading = true
                                showAddEditionSheet = true
                                vm.loadAvailableEditions(
                                    id,
                                    onPartial = { partial -> availableEditions = partial }   // r3 feedback progresivo
                                ) { results ->
                                    availableEditions = results
                                    editionsLoading = false
                                    if (results.isEmpty() && !isOnline()) editionsNetworkError = true
                                }
                            }
                        }
                        if (TimerStateHolder.running && TimerStateHolder.activeBookId == id) {
                            TimerService.pause(context)
                            pendingChangeEditionAction = action
                            showTimerEditionWarning = true
                        } else {
                            action()
                        }
                    },
                    onSetActive  = { edId ->
                        if (TimerStateHolder.running && TimerStateHolder.activeBookId == id) {
                            // Sesión en curso: pausar y pedir confirmación antes de cambiar
                            TimerService.pause(context)
                            pendingSetActiveEditionAfterSession = edId
                            pendingChangeEditionAction = null
                            showTimerEditionWarning = true
                        } else {
                            val currentLang = vm.editionsForBook(id).firstOrNull { it.isActive }?.language ?: "original"
                            val newLang = vm.editionsForBook(id).firstOrNull { it.id == edId }?.language ?: "original"
                            if (currentLang != newLang && vm.editionsForBook(id).size > 1) {
                                pendingActiveEditionId = edId
                                showActiveEditionWarning = true
                            } else {
                                vm.setActiveEdition(id, edId, prefs)
                                refreshWidgetForBookIfSelected(context, id, clearCoverCache = true)
                            }
                        }
                    },
                    onRemove     = { edId -> pendingRemoveEditionId = edId },   // B-024: confirmar antes de borrar
                    onUpdatePages = { edId, pages ->
                        vm.upsertEdition(id, bookEditions.first { it.id == edId }.copy(pages = pages), prefs)
                        if (bookEditions.firstOrNull { it.id == edId }?.isActive == true) vm.updatePages(id, pages, prefs)
                    }
                )

                // ── Temporizador de lectura ────────────────────────────────────
                if (book.status == BookStatus.READING || book.isRereading || book.status == BookStatus.REREADING) {
                    Spacer(Modifier.height(12.dp))
                    val timerHours = timerSeconds / 3600
                    val timerMins = (timerSeconds % 3600) / 60
                    val timerSecs = timerSeconds % 60
                    val timerLabel = if (timerHours > 0) String.format("%d:%02d:%02d", timerHours, timerMins, timerSecs)
                                     else String.format("%02d:%02d", timerMins, timerSecs)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = when (timerState) {
                            TimerState.RUNNING -> Color(0x1510B981)
                            TimerState.PAUSED  -> Color(0x15F59E0B)
                            else               -> acc.copy(alpha = 0.05f)
                        },
                        border = BorderStroke(1.dp, when (timerState) {
                            TimerState.RUNNING -> Color(0x4010B981)
                            TimerState.PAUSED  -> Color(0x40F59E0B)
                            else               -> acc.copy(alpha = 0.13f)
                        }),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // Rebote al iniciar/reanudar — declarado fuera del if para estabilidad del remember
                            val timerScale = remember { androidx.compose.animation.core.Animatable(1f) }
                            LaunchedEffect(timerState) {
                                if (timerState == TimerState.RUNNING) {
                                    timerScale.animateTo(1.12f, animationSpec = tween(100))
                                    timerScale.animateTo(1f, animationSpec = tween(120))
                                }
                            }
                            if (timerState != TimerState.IDLE) {
                                Text(
                                    timerLabel,
                                    color = when (timerState) {
                                        TimerState.RUNNING -> Green
                                        TimerState.PAUSED  -> Amber
                                        else               -> theme.textMuted
                                    },
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.scale(timerScale.value)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    when (timerState) { TimerState.RUNNING -> stringResource(R.string.timer_reading); TimerState.PAUSED -> stringResource(R.string.timer_paused); else -> "" },
                                    color = theme.textDim, fontSize = 11.sp
                                )
                                Spacer(Modifier.height(10.dp))
                            } else {
                                Text(stringResource(R.string.txt_d8125eae), color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Play / Pause — llaman al servicio para que la notificación
                                // de pantalla de bloqueo funcione mientras hay sesión activa.
                                if (timerState != TimerState.RUNNING) {
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape)
                                            .background(Green)
                                            .clickable {
                                                // Si hay una sesión activa para OTRO libro, preguntar
                                                if (TimerStateHolder.running && TimerStateHolder.activeBookId != id) {
                                                    showConflictSessionDialog = true
                                                } else if (timerState == TimerState.IDLE) {
                                                    startTimerWithPermCheck {
                                                        com.lecturameter.TimerService.start(context, id, book.title)
                                                    }
                                                } else {
                                                    com.lecturameter.TimerService.resume(context)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                } else {
                                    // Fase 4 (D-003, 2A): halo que respira alrededor del control
                                    // mientras la sesión corre. drawBehind no altera el layout.
                                    // Con "reducir animaciones" del sistema, el halo no se pinta.
                                    val haloT = androidx.compose.animation.core.rememberInfiniteTransition(label = "timerHalo")
                                    val haloP by haloT.animateFloat(
                                        initialValue = 0f, targetValue = 1f,
                                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                            animation = tween(1600, easing = androidx.compose.animation.core.LinearEasing)
                                        ), label = "timerHaloP"
                                    )
                                    val reduceMotion = remember {
                                        android.provider.Settings.Global.getFloat(
                                            context.contentResolver,
                                            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
                                        ) == 0f
                                    }
                                    Box(
                                        Modifier.size(44.dp)
                                            .drawBehind {
                                                if (!reduceMotion) {
                                                    drawCircle(
                                                        color = Amber.copy(alpha = 0.35f * (1f - haloP)),
                                                        radius = size.minDimension / 2f + 9.dp.toPx() * haloP
                                                    )
                                                }
                                            }
                                            .clip(CircleShape)
                                            .background(Amber)
                                            .clickable { com.lecturameter.TimerService.pause(context) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White, modifier = Modifier.size(24.dp))
                                    }
                                }
                                // Stop (solo si hay tiempo acumulado) — finaliza el servicio
                                // y abre el diálogo aquí mismo (la app está abierta).
                                if (timerState != TimerState.IDLE) {
                                    Box(
                                        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                            .background(Red.copy(alpha = 0.85f))
                                            .clickable {
                                                val secs = TimerStateHolder.seconds
                                                val mins = ((secs + 30) / 60).toInt().coerceAtLeast(1)
                                                com.lecturameter.TimerService.stop(context, showEndNotification = false)
                                                // Reset del flag (el Service lo iba a poner pero
                                                // como ya estamos abiertos abrimos directo)
                                                TimerStateHolder.shouldOpenDialog = false
                                                TimerStateHolder.reset()
                                                autoSessionMinutes = mins
                                                timerSeconds = 0
                                                showSessionDialog = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.cd_end_session), tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Title / author / status
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        // v2.4: con la saga restaurada en títulos, truncar a 3 líneas
                        Text(book.title, color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (book.author.isNotBlank()) Text(stringResource(R.string.by_author, book.author), color = acc, fontSize = 14.sp, modifier = Modifier.clickable { onAuthorClick(book.author) })
                        // Género — toca para cambiar; botón swap si hay 2
                        var showGenreMenu by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          // P-012: bottom sheet con buscador y grupos; "Limpiar" cubre el antiguo
                          // "Sin género" y la recarga de la API va como acción propia del sheet.
                          Text(
                              text = if (book.genres.isNotEmpty()) book.genres.map { displayGenre(it) }.joinToString(" · ") else stringResource(R.string.genre_add_button),
                              color = if (book.genres.isNotEmpty()) theme.textDim else acc.copy(alpha = 0.7f),
                              fontSize = 12.sp,
                              modifier = Modifier.padding(top = 2.dp).clickable { showGenreMenu = true }
                          )
                          if (showGenreMenu) {
                              GenreSelectorSheet(
                                  initial = book.genres,
                                  suggested = emptyList(),
                                  theme = theme,
                                  onDismiss = { showGenreMenu = false },
                                  onConfirm = { newGenres ->
                                      vm.updateGenres(id, newGenres, prefs)
                                      if (book.noCoverFound) vm.updateNoCoverFound(id, false, prefs)
                                  },
                                  onRefreshFromApi = {
                                      showGenreMenu = false
                                      vm.refreshGenre(id, prefs) { found ->
                                          refreshMsg = if (found) context.getString(R.string.msg_genre_updated) else context.getString(R.string.msg_genre_not_found)
                                      }
                                  }
                              )
                          }
                          // Botón intercambiar orden — solo si hay 2 géneros
                          if (book.genres.size == 2) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        vm.updateGenres(id, listOf(book.genres[1], book.genres[0]), prefs)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.SwapHoriz,
                                    contentDescription = stringResource(R.string.cd_swap_genres),
                                    tint = acc,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                          }
                        }
                    }
                    // Fase 4 (D-003, 3A): al pasar a Leído, tinte verde breve tras los badges
                    // + pop del ✅ que se desvanece. Una vez por transición; con "reducir
                    // animaciones" del sistema no se anima.
                    var prevStatusAnim by remember { mutableStateOf(book.status) }
                    val finishFlash = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(book.status) {
                        val was = prevStatusAnim; prevStatusAnim = book.status
                        val reduce = android.provider.Settings.Global.getFloat(
                            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
                        if (book.status == BookStatus.FINISHED && was != BookStatus.FINISHED && !reduce) {
                            finishFlash.snapTo(1f)
                            finishFlash.animateTo(0f, animationSpec = tween(850))
                        }
                    }
                    Box(
                        Modifier.drawBehind {
                            if (finishFlash.value > 0f) drawRoundRect(
                                color = Green.copy(alpha = 0.30f * finishFlash.value),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                            )
                        }
                    ) {
                        // v20.7: xN = relecturas iniciadas (incluyendo la en curso)
                        val completedRR = startedRereads(book.dateEvents)
                        // Ambos badges: FINISHED+isRereading, o REREADING+endDate (retrocompat), o solo el estado
                        when {
                            book.status == BookStatus.FINISHED && book.isRereading -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StatusBadge(BookStatus.FINISHED)
                                    StatusBadge(BookStatus.REREADING)
                                    if (completedRR > 0) Text("×$completedRR", color = Color(0xFF06B6D4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            book.status == BookStatus.REREADING -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StatusBadge(BookStatus.FINISHED)
                                    StatusBadge(BookStatus.REREADING)
                                    if (completedRR > 0) Text("×$completedRR", color = Color(0xFF06B6D4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            book.status == BookStatus.FINISHED && completedRR > 0 -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StatusBadge(book.status)
                                    Text("×$completedRR", color = Color(0xFF06B6D4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            else -> StatusBadge(book.status)
                        }
                        Box(Modifier.matchParentSize().clickable { showStatusMenu = true })
                        DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                            Text(stringResource(R.string.txt_c4b4e77a), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            SHELF_ORDER.forEach { s -> DropdownMenuItem(text = { Text(statusLabel(s), color = if (book.status == s) statusColor(s) else theme.textMain) }, onClick = { vm.updateStatus(id, s, prefs); refreshWidgetForBookIfSelected(context, id); showStatusMenu = false }) }
                        }
                        if (finishFlash.value > 0f) {
                            Text(
                                "✅", fontSize = 18.sp,
                                modifier = Modifier.align(Alignment.CenterEnd)
                                    .offset(x = 26.dp)
                                    .scale(1f + 0.35f * finishFlash.value)
                                    .alpha(finishFlash.value)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                // v20.0 (G3): título "Resumen global" sobre las pills agregadas del libro,
                // SOLO si hay relecturas (si no, las pills son auto-explicativas).
                val hasRereads = book.dateEvents.any { it.type == "reread" }
                // "Resumen global" eliminado (v21.37)

                // Stats row — solo si NO hay relecturas (con relecturas las pills van por ciclo)
                // v21.36: cuando hasRereads=true este bloque se oculta; los datos van a las pills de ciclo
                if (!hasRereads) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (stats != null && book.status == BookStatus.FINISHED) {
                        val finishedPages = bookSessions.sumOf { it.pages }
                        val effectiveTotalFinished = when {
                            book.firstFunctionalPage != null && book.lastFunctionalPage != null ->
                                (book.lastFunctionalPage - book.firstFunctionalPage + 1).coerceAtLeast(1)
                            book.lastFunctionalPage != null -> book.lastFunctionalPage
                            else -> book.pages
                        }
                        StatBox(fmtDaysLocalized(stats.days), stringResource(R.string.stat_days), Modifier.weight(1f), theme)
                        run {
                            val totalMinsF = bookSessions.mapNotNull { it.minutes }.sum()
                            if (totalMinsF > 0) StatBox(fmtMinutes(totalMinsF), stringResource(R.string.stat_total_time), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
                        }
                        run {
                            val ppdFinished: Double? = if (finishedPages > 0) {
                                val sessionDaysFinished = bookSessions.map { it.date }.toSet().size.coerceAtLeast(1)
                                finishedPages.toDouble() / sessionDaysFinished
                            } else stats.pagesPerDay
                            if (ppdFinished != null) {
                                StatBox(String.format("%.1f", ppdFinished), stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, highlight = true)
                            }
                        }
                        if (finishedPages > 0 && effectiveTotalFinished > 0) {
                            val pctFinished = (finishedPages * 100 / effectiveTotalFinished).coerceIn(0, 100)
                            StatBox("$pctFinished%", stringResource(R.string.pill_porcentaje_leido), Modifier.weight(1f), theme)
                        }
                    }
                    if (stats != null && (book.status == BookStatus.READING || book.status == BookStatus.REREADING || book.isRereading)) {
                        val pagesRead = bookSessions.sumOf { it.pages }
                        StatBox(fmtDaysLocalized(stats.days), stringResource(R.string.pill_dias_leyendo), Modifier.weight(1f), theme)
                        run {
                            val totalMinsR = bookSessions.mapNotNull { it.minutes }.sum()
                            if (totalMinsR > 0) StatBox(fmtMinutes(totalMinsR), stringResource(R.string.stat_total_time), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
                        }
                        // Feedback 2.6 (estandarización pills): páginas = índigo en toda la app
                        if (pagesRead > 0) StatBox("${pagesRead}p", stringResource(R.string.pill_pags_leidas), Modifier.weight(1f), theme, highlight = true, highlightColor = acc)
                        val effectiveTotal = when {
                            book.firstFunctionalPage != null && book.lastFunctionalPage != null ->
                                (book.lastFunctionalPage - book.firstFunctionalPage + 1).coerceAtLeast(1)
                            book.lastFunctionalPage != null -> book.lastFunctionalPage
                            else -> book.pages
                        }
                        val pct = if (effectiveTotal > 0) (pagesRead * 100 / effectiveTotal).coerceIn(0, 100) else 0
                        if (pagesRead > 0) StatBox("$pct%", stringResource(R.string.pill_porcentaje_leido), Modifier.weight(1f), theme)
                    }
                    // Págs/día de sesiones — solo para libros no FINISHED
                    if (book.status != BookStatus.FINISHED) {
                        val totalSessionPages = bookSessions.sumOf { it.pages }
                        val sessionDays = bookSessions.map { it.date }.toSet().size.coerceAtLeast(1)
                        if (totalSessionPages > 0) {
                            val avgPerSessionDay = totalSessionPages.toDouble() / sessionDays
                            StatBox(String.format("%.1f", avgPerSessionDay), stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, highlight = true)
                        }
                    }
                }
                // ── Fase 5: Predictor de finalización (P-C aprobada 14-07) ──────────
                // Línea sutil bajo las pills. Solo lectura en curso con ≥3 sesiones con
                // páginas en 30 días; si no hay datos, no aparece (regla: no inventar).
                if (book.status == BookStatus.READING && !book.isRereading) {
                    val predPagesRead = bookSessions.sumOf { it.pages }
                    val predTotal = when {
                        book.firstFunctionalPage != null && book.lastFunctionalPage != null ->
                            (book.lastFunctionalPage - book.firstFunctionalPage + 1).coerceAtLeast(1)
                        book.lastFunctionalPage != null -> book.lastFunctionalPage
                        else -> book.pages
                    }
                    val prediction = remember(bookSessions, predTotal) {
                        com.lecturameter.utils.predictFinish(bookSessions, predTotal - predPagesRead, today())
                    }
                    if (prediction != null) {
                        // El ritmo se formatea igual que la pill (%.1f) para que se vea
                        // que es EL MISMO número y la cuenta se pueda hacer a mano.
                        Text(
                            stringResource(
                                R.string.finish_prediction_line,
                                String.format("%.1f", prediction.pagesPerDay),
                                prediction.readingDaysLeft
                            ),
                            color = theme.textMuted, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                } // end !hasRereads
                Spacer(Modifier.height(16.dp))

                // Botones de acción (para libros en lectura o releyendo)
                if (book.status == BookStatus.READING || book.status == BookStatus.REREADING || book.isRereading) {
                    // Botón registrar sesión
                    Button(
                        onClick = { autoSessionMinutes = null; showSessionDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = acc)
                    ) {
                        Text(stringResource(R.string.txt_e810b914), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(10.dp))

                    // Botón mostrar en widget
                    run {
                        var widgetBookId by remember(id) {
                            mutableStateOf(com.lecturameter.widget.loadWidgetBook(context))
                        }
                        val isCurrentWidget = widgetBookId == book.id
                        var showRemoveWidgetDialog by remember { mutableStateOf(false) }

                        if (showRemoveWidgetDialog) {
                            AlertDialog(
                                onDismissRequest = { showRemoveWidgetDialog = false },
                                containerColor = theme.bgMid,
                                title = { Text(stringResource(R.string.txt_d4b36426), color = theme.textMain, fontWeight = FontWeight.Bold) },
                                text = { Text(stringResource(R.string.txt_40a68828), color = theme.textMuted) },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            com.lecturameter.widget.saveWidgetBook(context, -1L)
                                            com.lecturameter.widget.requestBookWidgetUpdate(context)
                                            widgetBookId = -1L
                                            showRemoveWidgetDialog = false
                                            android.widget.Toast.makeText(context, context.getString(R.string.msg_book_no_longer_widget, book.title), android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text(stringResource(R.string.txt_f46b3218)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRemoveWidgetDialog = false }) {
                                        Text(stringResource(R.string.txt_847607d7), color = Red)
                                    }
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (isCurrentWidget) {
                                    showRemoveWidgetDialog = true
                                } else {
                                    showBookInWidget(context, book.id, activeEdition?.id ?: -1L)
                                    widgetBookId = book.id
                                    android.widget.Toast.makeText(context, context.getString(R.string.msg_now_displayed_widget), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isCurrentWidget) Color(0xFF10B981) else acc.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isCurrentWidget) Color(0xFF10B981) else acc
                            )
                        ) {
                            Text(
                                if (isCurrentWidget) stringResource(R.string.widget_currently_showing) else stringResource(R.string.widget_show_button),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                } else {
                    Spacer(Modifier.height(4.dp))
                }

                // ── Páginas totales (editable) ─────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(stringResource(R.string.txt_ebd28578), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (!editingPages) {
                        Text("${book.pages}", color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        val pencilColor = if (book.status == BookStatus.READING || book.status == BookStatus.REREADING) Color(0xFFF59E0B) else acc
                        IconButton(onClick = { pagesOriginal = book.pages; editingPages = true; pagesInput = book.pages.toString() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, null, tint = pencilColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (editingPages) {
                    OutlinedTextField(
                        value = pagesInput,
                        onValueChange = { v ->
                            pagesInput = v.filter { c -> c.isDigit() }
                            // Actualización en tiempo real mientras se escribe
                            val p = pagesInput.toIntOrNull()
                            if (p != null && p > 0) { vm.updatePages(id, p, prefs); refreshWidgetForBookIfSelected(context, id) }
                        },
                        placeholder = { Text(stringResource(R.string.txt_5a411185), color = theme.textDim) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { val p = pagesInput.toIntOrNull(); if (p != null && p > 0) { vm.updatePages(id, p, prefs); refreshWidgetForBookIfSelected(context, id) }; editingPages = false }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = acc), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.txt_d3270bdb)) }
                        OutlinedButton(onClick = { val orig = pagesOriginal; pagesInput = orig.toString(); if (orig > 0) { vm.updatePages(id, orig, prefs); refreshWidgetForBookIfSelected(context, id) }; editingPages = false }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.txt_847607d7)) }
                    }
                }
                Spacer(Modifier.height(20.dp))

                // ── Páginas funcionales (editable a posteriori) ─────────────────
                run {
                    var editingFuncPages by remember { mutableStateOf(false) }
                    var funcFirstInput by remember(book.firstFunctionalPage) { mutableStateOf(book.firstFunctionalPage?.toString() ?: "") }
                    var funcLastInput  by remember(book.lastFunctionalPage)  { mutableStateOf(book.lastFunctionalPage?.toString()  ?: "") }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(stringResource(R.string.txt_b9015f44), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (!editingFuncPages) {
                            val label = when {
                                book.firstFunctionalPage != null && book.lastFunctionalPage != null ->
                                    "${book.firstFunctionalPage}–${book.lastFunctionalPage}  ›  ${book.lastFunctionalPage - book.firstFunctionalPage + 1}p"
                                book.firstFunctionalPage != null -> context.getString(R.string.func_pages_from, book.firstFunctionalPage)
                                book.lastFunctionalPage != null  -> context.getString(R.string.func_pages_to, book.lastFunctionalPage)
                                else -> "-"
                            }
                            Text(label, color = theme.textMain, fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            IconButton(onClick = { editingFuncPages = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, null, tint = acc, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (editingFuncPages) {
                        Text(stringResource(R.string.txt_6f50e674), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = funcFirstInput,
                                onValueChange = { funcFirstInput = it.filter { c -> c.isDigit() } },
                                label = { Text(stringResource(R.string.txt_3c75ceda), color = theme.textDim, fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = funcLastInput,
                                onValueChange = { funcLastInput = it.filter { c -> c.isDigit() } },
                                label = { Text(stringResource(R.string.txt_27de21f4), color = theme.textDim, fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.updateFunctionalPages(id, funcFirstInput.toIntOrNull(), funcLastInput.toIntOrNull(), prefs)
                                    refreshWidgetForBookIfSelected(context, id)
                                    editingFuncPages = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = acc),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.txt_d3270bdb)) }
                            OutlinedButton(
                                onClick = {
                                    funcFirstInput = book.firstFunctionalPage?.toString() ?: ""
                                    funcLastInput  = book.lastFunctionalPage?.toString()  ?: ""
                                    editingFuncPages = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.txt_847607d7)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))

                // Rating
                Text(stringResource(R.string.txt_ec0bec6b), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                RatingSelector(selectedRating) { r -> selectedRating = r; vm.updateRating(id, r, prefs) }
                Spacer(Modifier.height(20.dp))

                // Comment
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(stringResource(R.string.txt_41f5582a), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (!editingComment) IconButton(onClick = { editingComment = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, tint = acc, modifier = Modifier.size(16.dp)) }
                }
                if (editingComment) {
                    OutlinedTextField(value = commentText, onValueChange = { commentText = it }, placeholder = { Text(stringResource(R.string.txt_ea889da9), color = theme.textDim) }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), maxLines = 6)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val trimmed = commentText.trim()
                            if (activeEdition != null) {
                                vm.updateEditionComment(id, activeEdition.id, trimmed, prefs)
                            } else {
                                vm.updateComment(id, trimmed, prefs)
                            }
                            editingComment = false
                        }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = acc), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.txt_d3270bdb)) }
                        OutlinedButton(onClick = { commentText = effectiveComment; editingComment = false }, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.txt_847607d7)) }
                    }
                } else {
                    if (effectiveComment.isNotBlank()) Surface(shape = RoundedCornerShape(10.dp), color = acc.copy(alpha = 0.05f), border = BorderStroke(1.dp, acc.copy(alpha = 0.1f))) { Text(effectiveComment, color = theme.textMain, fontSize = 13.sp, modifier = Modifier.padding(12.dp)) }
                    else Text(stringResource(R.string.txt_e33e3006), color = theme.textDim, fontSize = 12.sp)
                }
                Spacer(Modifier.height(20.dp))

                // Editable dates (v19.8: soporta múltiples ciclos vía dateEvents)
                run {
                    var editingDates by remember { mutableStateOf(false) }
                    var dateError by remember { mutableStateOf("") }
                    // v20.0: índice del evento que el usuario quiere eliminar (con confirm)
                    var dateToDelete by remember { mutableStateOf<Int?>(null) }

                    // Lista de eventos efectiva (migra legacy si dateEvents vacío)
                    val effectiveEvents = remember(book.dateEvents, book.startDate, book.endDate, book.dropDate, book.resumedDate) {
                        sortedDateEvents(migrateLegacyToEvents(book))
                    }

                    // Estado de edición: pares (evento, fecha-en-display) para todos los eventos
                    val editInputs = remember(editingDates, effectiveEvents) {
                        mutableStateListOf<Pair<DateEvent, androidx.compose.runtime.MutableState<String>>>().apply {
                            effectiveEvents.forEach { ev ->
                                add(ev to mutableStateOf(storedToDisplay(ev.date)))
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(stringResource(R.string.txt_5d69fc39), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (!editingDates) IconButton(onClick = { editingDates = true; dateError = "" }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, null, tint = acc, modifier = Modifier.size(16.dp)) }
                    }

                    if (editingDates) {
                        // Render de campos editables agrupados por ciclo
                        var lastSection = ""
                        val editRereads = editInputs.map { it.first }.filter { it.type == "reread" }.sortedBy { it.occurrence }
                        val editRereadEnds = editInputs.map { it.first }.filter { it.type == "reread_end" }.sortedBy { it.occurrence }
                        val multipleEditRereads = editRereads.size > 1
                        // v20.4 (B3): índice posicional en lista ordenada para asignación drop/resume
                        val editEvList = editInputs.map { it.first }
                        val editRereadIdxMap = editRereads.associate { r ->
                            r.occurrence to editEvList.indexOfFirst { it.type == "reread" && it.occurrence == r.occurrence }
                        }
                        val editRereadEndIdxMap = editRereadEnds.associate { r ->
                            r.occurrence to editEvList.indexOfFirst { it.type == "reread_end" && it.occurrence == r.occurrence }
                        }
                        fun editEvKey(date: String, type: String) = date + typeOrder(type).toString().padStart(2, '0')
                        editInputs.forEachIndexed { idx, (ev, state) ->
                            val section = when (ev.type) {
                                "reread", "reread_end" -> "reread_${ev.occurrence}"
                                "start", "end" -> "main"
                                "drop", "resume" -> {
                                    var cycleSection = "main"
                                    for (r in editRereads) {
                                        val rStartIdx = editRereadIdxMap[r.occurrence] ?: continue
                                        val rEndIdx = editRereadEndIdxMap[r.occurrence] ?: Int.MAX_VALUE
                                        if (idx > rStartIdx && idx < rEndIdx) {
                                            cycleSection = "reread_${r.occurrence}"; break
                                        }
                                    }
                                    cycleSection
                                }
                                else -> "other"
                            }
                            if (section != lastSection) {
                                val label = when {
                                    section == "main" -> stringResource(R.string.dates_section_reading)
                                    section.startsWith("reread_") -> {
                                        val n = section.removePrefix("reread_").toIntOrNull() ?: 1
                                        stringResource(R.string.dates_section_rereading_n, n)
                                    }
                                    else -> ""
                                }
                            }
                            Text("${dateEventLabel(ev)} (dd-mm-yyyy)", color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                OutlinedTextField(
                                    value = state.value,
                                    onValueChange = { state.value = it },
                                    placeholder = { Text(todayDisplay(), color = theme.textDim) },
                                    colors = fieldColors(theme),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                                )
                                IconButton(onClick = { dateToDelete = idx }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.txt_00b343f8), tint = Red, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (dateError.isNotBlank()) Text(dateError, color = Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))

                        // v20.0: confirmación eliminar fecha
                        val delIdx = dateToDelete
                        if (delIdx != null && delIdx in editInputs.indices) {
                            val ev = editInputs[delIdx].first
                            AlertDialog(
                                onDismissRequest = { dateToDelete = null },
                                containerColor = theme.bgMid,
                                title = { Text(stringResource(R.string.txt_00b343f8), color = theme.textMain, fontWeight = FontWeight.Bold) },
                                text = { Text(stringResource(R.string.delete_date_confirm, dateEventLabel(ev)), color = theme.textMuted) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        // Persistir: quitar el evento de la lista y guardar
                                        val remaining = mutableListOf<DateEvent>()
                                        editInputs.forEachIndexed { i, pair ->
                                            if (i == delIdx) return@forEachIndexed
                                            val s = pair.second.value.trim()
                                            if (s.isBlank()) return@forEachIndexed
                                            if (parseFlexibleDate(s) != null) {
                                                remaining.add(pair.first.copy(date = displayToStored(s)))
                                            } else {
                                                remaining.add(pair.first)
                                            }
                                        }
                                        vm.updateDateEvents(id, remaining, prefs)
                                        refreshWidgetForBookIfSelected(context, id)
                                        editInputs.removeAt(delIdx)
                                        dateToDelete = null
                                    }) { Text(stringResource(R.string.txt_5b5c9f9d), color = Red, fontWeight = FontWeight.Bold) }
                                },
                                dismissButton = { TextButton(onClick = { dateToDelete = null }) { Text(stringResource(R.string.txt_847607d7), color = acc) } }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            OutlinedButton(
                                onClick = { editingDates = false; dateError = "" },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.txt_847607d7)) }
                            Button(onClick = {
                                // Validar todas las fechas
                                val newEvents = mutableListOf<DateEvent>()
                                for ((ev, state) in editInputs) {
                                    val s = state.value.trim()
                                    if (s.isBlank()) continue  // se omite el evento si vacío
                                    if (parseFlexibleDate(s) == null) {
                                        dateError = context.getString(R.string.err_date_invalid_label, dateEventLabelCtx(ev, context))
                                        return@Button
                                    }
                                    newEvents.add(ev.copy(date = displayToStored(s)))
                                }
                                // v2.5: la fecha de inicio no puede ser posterior a la de fin
                                // (regla por ciclo: start≤end de la lectura original y reread≤reread_end por ocurrencia)
                                val startEv = newEvents.firstOrNull { it.type == "start" }
                                val endEv   = newEvents.firstOrNull { it.type == "end" }
                                if (startEv != null && endEv != null && startEv.date > endEv.date) {
                                    dateError = context.getString(R.string.err_date_start_after_end)
                                    return@Button
                                }
                                for (rr in newEvents.filter { it.type == "reread" }) {
                                    val rrEnd = newEvents.firstOrNull { it.type == "reread_end" && it.occurrence == rr.occurrence }
                                    if (rrEnd != null && rr.date > rrEnd.date) {
                                        dateError = context.getString(R.string.err_date_start_after_end)
                                        return@Button
                                    }
                                }
                                vm.updateDateEvents(id, newEvents, prefs)
                                refreshWidgetForBookIfSelected(context, id)
                                editingDates = false
                                dateError = ""
                            }, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = acc), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.txt_d3270bdb)) }
                        }
                    } else {
                        // Vista de lectura: agrupar por sección (lectura original / relectura N)
                        if (effectiveEvents.isNotEmpty()) {
                            Surface(shape = RoundedCornerShape(10.dp), color = acc.copy(alpha = 0.05f), border = BorderStroke(1.dp, acc.copy(alpha = 0.1f))) {
                                Column(Modifier.padding(12.dp)) {
                                    val dispRereads = effectiveEvents.filter { it.type == "reread" }.sortedBy { it.occurrence }
                                    val dispRereadEnds = effectiveEvents.filter { it.type == "reread_end" }.sortedBy { it.occurrence }
                                    // v20.4 (B3): índices posicionales para asignación drop/resume
                                    val rereadIndexMap = dispRereads.associate { r ->
                                        r.occurrence to effectiveEvents.indexOfFirst { it.type == "reread" && it.occurrence == r.occurrence }
                                    }
                                    val rereadEndIndexMap = dispRereadEnds.associate { r ->
                                        r.occurrence to effectiveEvents.indexOfFirst { it.type == "reread_end" && it.occurrence == r.occurrence }
                                    }
                                    // v20.5: asignar cada evento a su sección
                                    fun assignSection(ev: DateEvent, idx: Int): String = when (ev.type) {
                                        "reread", "reread_end" -> "reread_${ev.occurrence}"
                                        "start", "end" -> "main"
                                        "drop", "resume" -> {
                                            var cycleSection = "main"
                                            for (r in dispRereads) {
                                                val rStartIdx = rereadIndexMap[r.occurrence] ?: continue
                                                val rEndIdx = rereadEndIndexMap[r.occurrence] ?: Int.MAX_VALUE
                                                if (idx > rStartIdx && idx < rEndIdx) {
                                                    cycleSection = "reread_${r.occurrence}"; break
                                                }
                                            }
                                            cycleSection
                                        }
                                        else -> "other"
                                    }
                                    // v20.5: agrupar eventos por sección y mostrarlos en orden de ciclo
                                    val eventsWithSection = effectiveEvents.mapIndexed { idx, ev ->
                                        Pair(assignSection(ev, idx), ev)
                                    }
                                    val sectionOrder = buildList {
                                        add("main")
                                        dispRereads.forEach { add("reread_${it.occurrence}") }
                                    }
                                    val eventsBySection = eventsWithSection.groupBy { it.first }
                                    sectionOrder.forEachIndexed { sIdx, section ->
                                        val eventsInSection = eventsBySection[section] ?: return@forEachIndexed
                                        if (sIdx > 0) Spacer(Modifier.height(8.dp))
                                        val label = when {
                                            section == "main" -> stringResource(R.string.dates_section_reading)
                                            section.startsWith("reread_") -> {
                                                val n = section.removePrefix("reread_").toIntOrNull() ?: 1
                                                stringResource(R.string.dates_section_rereading_n, n)
                                            }
                                            else -> ""
                                        }
                                        val color = if (section == "main") acc else Color(0xFF06B6D4)
                                        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                                        eventsInSection.forEachIndexed { eIdx, (_, ev) ->
                                            if (eIdx > 0) Spacer(Modifier.height(3.dp))
                                            Row {
                                                Text("${dateEventLabel(ev)}: ", color = theme.textDim, fontSize = 13.sp)
                                                Text(fmtDate(ev.date), color = theme.textMuted, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        } else Text(stringResource(R.string.txt_11a95b58), color = theme.textDim, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Estadísticas por ciclo (v20.0 G3: pills por ciclo, solo con relecturas) ─
                run {
                    // B-032: bookSessions (edición ACTIVA), no sessions. Con `sessions` estas
                    // pills sumaban las sesiones de TODAS las ediciones mientras el historial
                    // de abajo filtra por la activa: El imperio final anunciaba "11p leídas"
                    // (sesión de la edición inglesa) y luego no mostraba ni una sesión.
                    val cycles = computeCycleStats(book, bookSessions)
                    if (cycles.size > 1) {
                        // Nivel 1: título principal
                        Text(stringResource(R.string.txt_aa1b8e40), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                        cycles.forEachIndexed { cIdx, c ->
                            val isOriginal = c.readingIndex == 0
                            val color = if (isOriginal) acc else Color(0xFF06B6D4)
                            // Niveles 2/3/...: cada ciclo tiene su título + pills propios
                            if (cIdx > 0) Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                                Spacer(Modifier.width(8.dp))
                                val ongoingSuffix = if (c.isOngoing) " (${stringResource(R.string.status_reading).lowercase()})" else ""
                                val cycleDisplayLabel = when {
                                    c.readingIndex == 0 -> stringResource(R.string.cycle_label_reading)
                                    c.sessions.isNotEmpty() && c.readingIndex > 1 -> stringResource(R.string.cycle_label_rereading_n, c.readingIndex)
                                    else -> stringResource(R.string.cycle_label_rereading)
                                }
                                Text("📖 $cycleDisplayLabel$ongoingSuffix", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            // Pills del ciclo (v21.33: Row; v21.36: añadido % + sesiones siempre visibles)
                            val sessPages = c.sessions.sumOf { it.pages }
                            val sessMins = c.sessions.mapNotNull { it.minutes }.sum()
                            val daysLabel = if (isOriginal) stringResource(R.string.pill_dias_leyendo) else stringResource(R.string.pill_dias_releyendo)
                            val daysTxt = c.days?.let { "$it ${if (it == 1) stringResource(R.string.word_day) else stringResource(R.string.word_days)}" } ?: "-"
                            // v21.36: % del ciclo = páginas del ciclo / páginas funcionales del libro
                            val effectiveTotalCycle = when {
                                book.firstFunctionalPage != null && book.lastFunctionalPage != null ->
                                    (book.lastFunctionalPage - book.firstFunctionalPage + 1).coerceAtLeast(1)
                                book.lastFunctionalPage != null -> book.lastFunctionalPage
                                else -> book.pages
                            }
                            val cyclePct = if (sessPages > 0 && effectiveTotalCycle > 0)
                                (sessPages * 100 / effectiveTotalCycle).coerceIn(0, 100) else null
                            // Feedback 2.6 (estandarización pills): color por métrica en toda la app —
                            // páginas=acento del tema, tiempo=Sky, velocidad=verde. Antes páginas iba
                            // en verde y págs/día heredaba el color del ciclo.
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                StatBox(daysTxt, daysLabel, Modifier.weight(1f), theme)
                                if (sessMins > 0) StatBox(fmtMinutes(sessMins), stringResource(R.string.stat_total_time), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
                                if (sessPages > 0) StatBox("${sessPages}p", stringResource(R.string.pill_pags_leidas), Modifier.weight(1f), theme, highlight = true, highlightColor = acc)
                                if (cyclePct != null) StatBox("$cyclePct%", stringResource(R.string.pill_porcentaje_leido), Modifier.weight(1f), theme)
                                if (c.pagesPerDay != null) {
                                    StatBox(String.format("%.1f", c.pagesPerDay), stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, highlight = true, highlightColor = Green)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }

                // ── Historial de sesiones (v20.0 G2: una sección por ciclo) ──
                // v20.3: precalculamos cycles para poder usar pagesPerDay en las pills de sesiones
                // B-032: bookSessions, no sessions (mismo motivo que en Estadísticas por ciclo)
                val sessionCycles = computeCycleStats(book, bookSessions)
                if (bookSessions.isNotEmpty()) {
                    // Agrupar sesiones por readingIndex, ordenado ascendente
                    val cycleSessionsByIndex = bookSessions.groupBy { it.readingIndex ?: 0 }
                        .toSortedMap()
                    // v20.2: si hay >1 relectura numeramos todas
                    val multipleRereadSections = cycleSessionsByIndex.keys.count { it > 0 } > 1

                    cycleSessionsByIndex.entries.forEachIndexed { sectionIdx, (readingIdx, cycleSessions) ->
                        val isOriginal = readingIdx == 0
                        val sectionLabel = when {
                            isOriginal -> stringResource(R.string.sessions_reading)
                            multipleRereadSections -> stringResource(R.string.sessions_rereading_n, readingIdx)
                            else -> stringResource(R.string.sessions_rereading)
                        }
                        val sectionAccent = if (isOriginal) acc else Color(0xFF06B6D4)
                        // Estado expand por sección. La sección 0 hereda el flag legacy `showSessionSummary` para
                        // mantener compat con el LaunchedEffect del heatmap si no hay match explícito.
                        val expanded = sectionExpanded[readingIdx] ?: (isOriginal && showSessionSummary)

                        // States locales por sección — clave (id, readingIdx) para aislar entre libros y ciclos
                        var localSearchQuery by rememberSaveable(id, readingIdx) { mutableStateOf("") }
                        var localChronological by rememberSaveable(id, readingIdx) { mutableStateOf(true) }
                        var showDeleteAll by remember(id, readingIdx) { mutableStateOf(false) }

                        if (sectionIdx > 0) Spacer(Modifier.height(10.dp))

                        Surface(
                            onClick = {
                                val newVal = !expanded
                                sectionExpanded[readingIdx] = newVal
                                if (isOriginal) showSessionSummary = newVal
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = theme.surface,
                            border = BorderStroke(1.dp, if (isOriginal) theme.border else Color(0x3306B6D4)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(sectionLabel, color = sectionAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("${cycleSessions.size}", color = sectionAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = sectionAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (expanded) {
                            Spacer(Modifier.height(10.dp))

                            // Filtrar sesiones de ESTE ciclo
                            val filteredLocal = if (localSearchQuery.isBlank()) cycleSessions
                            else if (localSearchQuery.trim().startsWith("#")) {
                                val num = localSearchQuery.trim().removePrefix("#").toIntOrNull()
                                if (num != null) cycleSessions.filter { detailSessionNumberMap[it.id] == num }
                                else cycleSessions
                            } else cycleSessions.filter { it.note.contains(localSearchQuery.trim(), ignoreCase = true) }

                            val sortedLocal = if (localChronological)
                                filteredLocal.sortedBy { it.date + it.id.toString().padStart(20, '0') }
                            else
                                filteredLocal.sortedByDescending { it.date + it.id.toString().padStart(20, '0') }

                            // Orden + botón Eliminar todas (v20.0 P4)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.txt_26afc116), color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                Surface(
                                    onClick = { localChronological = !localChronological },
                                    shape = RoundedCornerShape(10.dp),
                                    color = theme.surface,
                                    border = BorderStroke(1.dp, theme.border)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            if (localChronological) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            null, tint = sectionAccent, modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            if (localChronological) stringResource(R.string.sort_oldest_first) else stringResource(R.string.sort_newest_first),
                                            color = sectionAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { showDeleteAll = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete all", tint = Red, modifier = Modifier.size(18.dp))
                                }
                            }

                            // Barra de búsqueda
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = theme.surface,
                                border = BorderStroke(1.dp, if (localSearchQuery.isNotBlank()) sectionAccent else theme.border),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🔍", fontSize = 13.sp)
                                    Spacer(Modifier.width(6.dp))
                                    BasicTextField(
                                        value = localSearchQuery,
                                        onValueChange = { localSearchQuery = it },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(color = theme.textMain, fontSize = 12.sp),
                                        cursorBrush = SolidColor(sectionAccent),
                                        modifier = Modifier.weight(1f).padding(vertical = 9.dp),
                                        decorationBox = { inner ->
                                            if (localSearchQuery.isEmpty()) Text(stringResource(R.string.txt_a27470a5), color = theme.textDim, fontSize = 12.sp)
                                            inner()
                                        }
                                    )
                                    if (localSearchQuery.isNotBlank()) {
                                        IconButton(onClick = { localSearchQuery = "" }, modifier = Modifier.size(26.dp)) {
                                            Icon(Icons.Default.Close, null, tint = theme.textDim, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                }
                            }

                            // Gráfico de páginas por día (del ciclo)
                            SessionChart(filteredLocal.ifEmpty { cycleSessions }, theme, reversed = !localChronological)
                            Spacer(Modifier.height(14.dp))

                            // Estadísticas rápidas — v20.3: orden Días / Tiempo / Págs / Págs-día / Págs-min
                            val totalSessPages = filteredLocal.sumOf { it.pages }
                            val totalSessMins = filteredLocal.mapNotNull { it.minutes }.sum()
                            val avgSessPagesPerMin = if (totalSessMins > 0) totalSessPages.toDouble() / totalSessMins else null
                            val cycleForSection = sessionCycles.firstOrNull { it.readingIndex == readingIdx }
                            val cycledays = cycleForSection?.days
                            val daysLabel = if (isOriginal) stringResource(R.string.pill_dias_leyendo) else stringResource(R.string.pill_dias_releyendo)
                            val daysTxt = cycledays?.let { "$it ${if (it == 1) stringResource(R.string.word_day) else stringResource(R.string.word_days)}" } ?: "-"
                            // v20.4: si CycleStats no da pagesPerDay (en curso o sin funcPages), calcularlo desde sesiones/días
                            val pagPerDay: Double? = cycleForSection?.pagesPerDay
                                ?: if (cycledays != null && cycledays >= 1 && totalSessPages > 0)
                                    totalSessPages.toDouble() / cycledays else null
                            // Feedback 2.6 (estandarización pills): páginas=acento del tema, tiempo=Sky,
                            // velocidad (p/d y p/min)=verde. El color del ciclo queda solo en la
                            // cabecera de sección (lectura índigo / relectura cian).
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                                StatBox(daysTxt, daysLabel, Modifier.weight(1f), theme)
                                if (totalSessMins > 0) StatBox(fmtMinutes(totalSessMins), stringResource(R.string.stat_total_time), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
                                if (totalSessPages > 0) StatBox("${totalSessPages}p", stringResource(R.string.pill_pags_leidas), Modifier.weight(1f), theme, highlight = true, highlightColor = acc)
                                if (pagPerDay != null) StatBox(String.format("%.1f", pagPerDay), stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, highlight = true, highlightColor = Green)
                                if (avgSessPagesPerMin != null) StatBox(String.format("%.1f", avgSessPagesPerMin), stringResource(R.string.pill_pags_min), Modifier.weight(1f), theme, highlight = true, highlightColor = Green)
                            }

                            // Lista de sesiones del ciclo
                            sortedLocal.forEach { session ->
                                val isHighlighted = activeHighlightDate != null && session.date == activeHighlightDate
                                if (isHighlighted) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Transparent,
                                        border = BorderStroke(2.dp, Color(0xFFF59E0B)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        SessionRow(session, detailSessionNumberMap[session.id] ?: 0, theme,
                                            onDelete = { vm.deleteSession(session.id, prefs); refreshWidgetForBookIfSelected(context, session.bookId) },
                                            onEdit   = { updated -> vm.updateSession(updated, prefs); refreshWidgetForBookIfSelected(context, updated.bookId) }
                                        )
                                    }
                                } else {
                                    SessionRow(session, detailSessionNumberMap[session.id] ?: 0, theme,
                                        onDelete = { vm.deleteSession(session.id, prefs); refreshWidgetForBookIfSelected(context, session.bookId) },
                                        onEdit   = { updated -> vm.updateSession(updated, prefs); refreshWidgetForBookIfSelected(context, updated.bookId) }
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                            Spacer(Modifier.height(12.dp))

                            // Confirm dialog eliminar todas las sesiones del ciclo
                            // B-033: el diálogo dice la verdad — cuántas sesiones y de qué ciclo
                            // (con el buscador activo se VEN menos de las que se borran).
                            if (showDeleteAll) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteAll = false },
                                    containerColor = theme.bgMid,
                                    title = { Text(stringResource(R.string.txt_995fe186), color = theme.textMain, fontWeight = FontWeight.Bold) },
                                    text = { Text(
                                        if (cycleSessions.size == 1) stringResource(R.string.dialog_delete_cycle_one, sectionLabel)
                                        else stringResource(R.string.dialog_delete_cycle_msg, cycleSessions.size, sectionLabel),
                                        color = theme.textMuted
                                    ) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            vm.deleteSessions(cycleSessions.map { it.id }, prefs)
                                            refreshWidgetForBookIfSelected(context, id)
                                            showDeleteAll = false
                                        }) { Text(stringResource(R.string.txt_5b5c9f9d), color = Red, fontWeight = FontWeight.Bold) }
                                    },
                                    dismissButton = { TextButton(onClick = { showDeleteAll = false }) { Text(stringResource(R.string.txt_847607d7), color = acc) } }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Toggle "Releyendo" — para libros terminados o en relectura (v19.8)
                if (book.status == BookStatus.FINISHED || book.status == BookStatus.REREADING) {
                    val rereading = book.status == BookStatus.REREADING || book.isRereading
                    OutlinedButton(
                        onClick = { vm.toggleRereading(id, prefs); refreshWidgetForBookIfSelected(context, id) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (rereading) Color(0xFF06B6D4).copy(alpha = 0.6f) else theme.border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (rereading) Color(0xFF06B6D4) else theme.textMuted,
                            containerColor = if (rereading) Color(0xFF06B6D4).copy(alpha = 0.1f) else Color.Transparent
                        )
                    ) {
                        Text(if (rereading) "📖 ${stringResource(R.string.date_event_reread_end)}" else "📖 ${stringResource(R.string.date_event_reread)}", fontWeight = FontWeight.SemiBold)
                    }
                }

                // v20.0 (G5): botón Abandonar — solo si Leyendo/Releyendo
                if (book.status == BookStatus.READING || book.status == BookStatus.REREADING) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showAbandonDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFF87171),
                            containerColor = Color(0xFFF87171).copy(alpha = 0.08f)
                        )
                    ) {
                        Text(stringResource(R.string.txt_7603b36e), fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0x33F87171)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red, containerColor = Color(0x1AF87171))) {
                    Icon(Icons.Default.Delete, null, tint = Red); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.txt_b375487f), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
} // v21.34: cierre de DetailScreen faltante

// ── Diálogo de sesión cronometrada / manual, reutilizable ─────────────────────
// Extraído de DetailScreen el 17-07 para poder invocarlo también desde la hoja de
// inicio rápido del home (crono en la fila). Mismo comportamiento y validaciones.
//   - autoSessionMinutes != null → sesión cronometrada (tiempo fijo, fecha = hoy).
//   - autoSessionMinutes == null → sesión manual (tiempo y fecha editables).
// onSaved se dispara SOLO al guardar; onDismiss al descartar/cerrar.
@Composable
internal fun SessionSaveDialog(
    book: Book,
    bookSessions: List<ReadingSession>,
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    theme: Theme,
    autoSessionMinutes: Int?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val acc = accentForTheme(theme)
    val onAcc = onAccentColor(theme)
    val id = book.id
    // v20.0 (G1): autofill considera el ciclo actual.
    val autoStart = remember(bookSessions, book.status, book.dateEvents, book.firstFunctionalPage) {
        val isRereadingNow = book.status == BookStatus.REREADING
        if (isRereadingNow) {
            val evs = migrateLegacyToEvents(book)
            val openReread = evs.filter { it.type == "reread" }
                .lastOrNull { ev ->
                    evs.none { it.type == "reread_end" && it.occurrence == ev.occurrence }
                }
            if (openReread != null) {
                val cycleSessions = bookSessions.filter { (it.readingIndex ?: 0) == openReread.occurrence }
                val lastEndPage = cycleSessions.firstOrNull()?.endPage
                when {
                    lastEndPage != null -> {
                        val lastFunc = book.lastFunctionalPage
                        val next = lastEndPage + 1
                        if (lastFunc != null && next > lastFunc) book.firstFunctionalPage?.toString() ?: ""
                        else next.toString()
                    }
                    book.firstFunctionalPage != null -> book.firstFunctionalPage.toString()
                    else -> ""
                }
            } else {
                book.firstFunctionalPage?.toString() ?: ""
            }
        } else {
            val lastEndPage = bookSessions.firstOrNull()?.endPage
            when {
                lastEndPage != null -> (lastEndPage + 1).toString()
                book.firstFunctionalPage != null -> book.firstFunctionalPage.toString()
                else -> ""
            }
        }
    }

    var pageStart   by remember(autoStart) { mutableStateOf(autoStart) }
    var pageEnd     by remember { mutableStateOf("") }
    var sessionMinutes by remember { mutableStateOf(autoSessionMinutes?.toString() ?: "") }
    var sessionNote by remember { mutableStateOf("") }
    var sessionError by remember { mutableStateOf("") }
    val fromTimer = autoSessionMinutes != null
    var sessionDate by remember { mutableStateOf(todayDisplay()) }
    var showFinishedQuery by remember { mutableStateOf(false) }

    val totalPages = if (pageEnd.toIntOrNull() != null && pageStart.toIntOrNull() != null) (pageEnd.toIntOrNull()!! - pageStart.toIntOrNull()!! + 1).coerceAtLeast(0) else 0
    val mins = if (fromTimer) autoSessionMinutes else sessionMinutes.toIntOrNull()
    val ppm = if (totalPages > 0 && mins != null && mins > 0)
        String.format("%.1f", totalPages.toDouble() / mins) else null

    AlertDialog(
        onDismissRequest = { onDismiss() },
        containerColor = theme.bgMid,
        title = { Text(if (fromTimer) "⏱️ ${stringResource(R.string.timed_session_title)}" else stringResource(R.string.txt_e810b914), color = theme.textMain, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (fromTimer) {
                    Text("${stringResource(R.string.txt_5d69fc39).removePrefix("📅 ")}: ${fmtDate(today())}", color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                } else {
                    Text(stringResource(R.string.txt_78359316), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(
                        value = sessionDate,
                        onValueChange = { sessionDate = it; sessionError = "" },
                        placeholder = { Text(todayDisplay(), color = theme.textDim) },
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                }
                if (fromTimer) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Green.copy(alpha = 0.12f), border = BorderStroke(1.dp, Green.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Text("⏱️", fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.timer_recorded_time, fmtMinutes(autoSessionMinutes!!)), color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_900cd8a8), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = pageStart, onValueChange = { pageStart = it.filter { c -> c.isDigit() }; sessionError = "" },
                            placeholder = { Text(stringResource(R.string.txt_3015bbac), color = theme.textDim) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_c8d92673), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = pageEnd, onValueChange = { pageEnd = it.filter { c -> c.isDigit() }; sessionError = "" },
                            placeholder = { Text(stringResource(R.string.txt_78cf172d), color = theme.textDim) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (totalPages > 0) {
                    Surface(shape = RoundedCornerShape(8.dp), color = acc.copy(alpha = 0.1f), border = BorderStroke(1.dp, acc.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$totalPages", color = acc, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.txt_47bcdf9a), color = theme.textDim, fontSize = 10.sp)
                            }
                            if (ppm != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(ppm, color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.txt_ef31b7ae), color = theme.textDim, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                if (!fromTimer) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.txt_f7772d77), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(
                        value = sessionMinutes, onValueChange = { sessionMinutes = it.filter { c -> c.isDigit() } },
                        placeholder = { Text(stringResource(R.string.txt_e4bf49dd), color = theme.textDim) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }
                Text(stringResource(R.string.txt_7cd6ad03), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                OutlinedTextField(
                    value = sessionNote, onValueChange = { sessionNote = it },
                    placeholder = { Text(stringResource(R.string.txt_2136819a), color = theme.textDim) },
                    colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                    maxLines = 3, modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp)
                )
                if (sessionError.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(sessionError, color = Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = pageStart.toIntOrNull()
                    val end   = pageEnd.toIntOrNull()
                    val lastFunc = book.lastFunctionalPage
                    val effectiveDate: String = if (fromTimer) today() else {
                        val raw = sessionDate.trim()
                        if (raw.isBlank()) {
                            sessionError = context.getString(R.string.err_date_required); return@Button
                        }
                        val parsed = parseFlexibleDate(raw)
                        if (parsed == null) {
                            sessionError = context.getString(R.string.err_date_invalid_format); return@Button
                        }
                        val todayStored = today()
                        if (parsed > todayStored) {
                            sessionError = context.getString(R.string.err_date_invalid_generic); return@Button
                        }
                        val bookStart = book.startDate
                        if (bookStart != null && parsed < bookStart) {
                            sessionError = context.getString(R.string.err_date_invalid_generic); return@Button
                        }
                        displayToStored(raw)
                    }
                    when {
                        start == null || start < 1 ->
                            { sessionError = context.getString(R.string.err_page_start_min1); return@Button }
                        end == null || end > book.pages ->
                            { sessionError = context.getString(R.string.err_page_end_over_total_n, book.pages); return@Button }
                        lastFunc != null && end > lastFunc ->
                            { sessionError = context.getString(R.string.err_page_end_over_func, lastFunc); return@Button }
                        end < start ->
                            { sessionError = context.getString(R.string.err_page_end_lt_start); return@Button }
                    }
                    val prevEndPage = bookSessions.firstOrNull()?.endPage
                    val pages = if (prevEndPage != null && start == prevEndPage) end!! - start!!
                                else end!! - start!! + 1
                    val m = if (fromTimer) autoSessionMinutes else sessionMinutes.toIntOrNull()
                    val activeEditionId = book.editions.firstOrNull { it.isActive }?.id
                    vm.addSession(ReadingSession(
                        bookId = id, date = effectiveDate, pages = pages, minutes = m,
                        note = sessionNote.trim(), editionId = activeEditionId,
                        startPage = start, endPage = end,
                        startTimestamp = if (effectiveDate == today())
                            System.currentTimeMillis() - ((m ?: 0) * 60_000L)
                        else null
                    ), prefs)
                    refreshWidgetForBookIfSelected(context, id)
                    val effectiveMax = book.lastFunctionalPage ?: book.pages
                    val reachedEnd = end != null && end >= effectiveMax
                    val canAskFinish = book.status == BookStatus.READING || book.status == BookStatus.REREADING
                    if (reachedEnd && canAskFinish) {
                        showFinishedQuery = true
                    } else {
                        onSaved()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = onAcc),
                shape = RoundedCornerShape(10.dp)
            ) { Text(stringResource(R.string.txt_d3270bdb)) }
        },
        dismissButton = { TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
    )

    if (showFinishedQuery) {
        AlertDialog(
            onDismissRequest = { showFinishedQuery = false; onSaved() },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.finished_book_query_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.finished_book_query_body, book.title), color = theme.textMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        vm.updateStatus(id, BookStatus.FINISHED, prefs)
                        showFinishedQuery = false
                        onSaved()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = onAcc),
                    shape = RoundedCornerShape(10.dp)
                ) { Text(stringResource(R.string.finished_book_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showFinishedQuery = false; onSaved() }) {
                    Text(stringResource(R.string.finished_book_no), color = theme.textDim)
                }
            }
        )
    }
}
