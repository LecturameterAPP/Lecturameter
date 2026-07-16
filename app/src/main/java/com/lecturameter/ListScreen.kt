package com.lecturameter

// ListScreen (home con rail, estanterias y buscador) + emptyShelfHint.
// Extraido de MainActivity.kt el 15-07-2026 (ruptura del monolito, sin cambios funcionales).


import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
// v21.42: Icons.Outlined.Star eliminado — estrellas usan ★/☆ Text
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.MutableTransitionState
import android.widget.Toast
import kotlinx.coroutines.flow.first
import com.lecturameter.model.*
import com.lecturameter.utils.*
import androidx.navigation.compose.composable

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    theme: Theme,
    onAdd: () -> Unit,
    onSearch: () -> Unit,
    onStats: () -> Unit,
    onWrappedHistory: () -> Unit,
    onWrapped: (Int) -> Unit = {},
    onChallenges: () -> Unit = {},
    onDetail: (Long) -> Unit,
    onSettings: () -> Unit = {},
    onImportExport: () -> Unit = {},
    onScanIsbnSearch: () -> Unit = {},
    onNavigateToBookSearch: () -> Unit = {},
    onAddWithIsbn: (String) -> Unit = {},
    onEasterEgg: () -> Unit = {},
    onWeeklyRecap: () -> Unit = {}
) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val booksAll by vm.books.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf(vm.savedSearchQuery) }
    var sortOrderName by rememberSaveable { mutableStateOf(vm.savedSortOrder.name) }
    val sortOrder = SortOrder.entries.firstOrNull { it.name == sortOrderName } ?: SortOrder.DATE_DESC
    var showSortMenu by remember { mutableStateOf(false) }
    var showIsbnScanDialog by remember { mutableStateOf(false) }
    var scannedIsbnForDialog by remember { mutableStateOf("") }
    // D-002/T1: selector de inicio rápido de sesión desde la barra (⏱️)
    var showQuickStartSheet by remember { mutableStateOf(false) }
    // Feedback 13-07: el historial es un panel del home (no un drawer modal) — así el
    // rail sigue visible y FUNCIONAL con el panel abierto
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = historyOpen) { historyOpen = false }

    // Detectar ISBN escaneado y mostrar dialog de elección
    val listMainRef = androidx.compose.ui.platform.LocalContext.current as? MainActivity
    val listScannedIsbn = listMainRef?.pendingScannedIsbn?.value
    LaunchedEffect(listScannedIsbn) {
        if (!listScannedIsbn.isNullOrBlank()) {
            scannedIsbnForDialog = listScannedIsbn
            showIsbnScanDialog = true
        }
    }
    // Which tab is active: index into SHELF_ORDER
    var activeTab by rememberSaveable { mutableStateOf(0) }
    // v2.4: grid state — 1 columna en móvil, 2 en ≥840dp (mockup aprobado)
    val listState = rememberLazyGridState()
    val listScope = rememberCoroutineScope()
    // v2.4 rework: Snackbar de favoritos (preferido sobre Toast)
    val favSnackbarState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(searchQuery) { vm.savedSearchQuery = searchQuery }
    // Feedback 14-07 (F8): estos efectos hacían scroll-a-0 también en su PRIMERA
    // ejecución — es decir, al VOLVER del detalle (el grid restaura su posición pero
    // estos la machacaban). Se saltan la primera pasada y solo actúan ante cambios.
    var sortEffectArmed by remember { mutableStateOf(false) }
    LaunchedEffect(sortOrder) {
        vm.savedSortOrder = sortOrder
        prefs.edit().putString("sort_order", sortOrder.name).apply()
        if (sortEffectArmed) listScope.launch { listState.animateScrollToItem(0) }
        else sortEffectArmed = true
    }
    var tabEffectArmed by remember { mutableStateOf(false) }
    LaunchedEffect(activeTab) {
        if (tabEffectArmed) listScope.launch { listState.animateScrollToItem(0) }
        else tabEffectArmed = true
    }
    // v2.4 rework: al activar/desactivar el filtro de favoritos, volver arriba
    var favEffectArmed by remember { mutableStateOf(false) }
    LaunchedEffect(vm.showFavoritesOnly) {
        if (favEffectArmed) listScope.launch { listState.animateScrollToItem(0) }
        else favEffectArmed = true
    }

    // Feedback 14-07 v2: rail auto-hide en scroll con UMBRAL acumulado (la versión
    // por-evento parpadeaba con cualquier temblor del dedo). Además el rail se puede
    // cerrar a mano (swipe ← desde el rail/raya) y reabrir (swipe → desde el borde).
    // Cierre manual = pegajoso: el scroll no lo reabre hasta que se reabra a mano.
    var railVisible by rememberSaveable { mutableStateOf(true) }
    var railManuallyHidden by rememberSaveable { mutableStateOf(false) }
    val hideThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 32.dp.toPx() }
    LaunchedEffect(listState) {
        var lastIdx = listState.firstVisibleItemIndex
        var lastOff = listState.firstVisibleItemScrollOffset
        var acc = 0f
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (idx, off) ->
                // Aproximación del delta: dentro del mismo item es exacto; al cambiar
                // de item se asume el signo del cambio con un paso grande
                val delta = when {
                    idx == lastIdx -> (off - lastOff).toFloat()
                    idx > lastIdx  -> hideThresholdPx
                    else           -> -hideThresholdPx
                }
                lastIdx = idx; lastOff = off
                if (idx == 0 && off == 0) {
                    acc = 0f
                    if (!railManuallyHidden) railVisible = true
                } else {
                    // El acumulador se resetea al cambiar de dirección
                    acc = if (delta > 0 == acc > 0) acc + delta else delta
                    if (acc > hideThresholdPx) { railVisible = false; acc = 0f }
                    else if (acc < -hideThresholdPx) {
                        if (!railManuallyHidden) railVisible = true
                        acc = 0f
                    }
                }
            }
    }
    // Feedback 14-07 (F4): al DEJAR de hacer scroll el rail vuelve solo (si no está
    // cerrado a mano) — el auto-hide es solo mientras el dedo mueve la lista
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling && !railManuallyHidden) railVisible = true
            }
    }
    val railWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (railVisible) 46.dp else 0.dp,
        animationSpec = tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "rail_auto_hide"
    )

    // Compute per-shelf book lists (filtered + sorted) — with fuzzy/accent-insensitive search
    val searchFiltered = if (searchQuery.isBlank()) booksAll
        else booksAll.filter {
            fuzzyMatch(searchQuery, it.title) || fuzzyMatch(searchQuery, it.author)
        }
    // v2.4 rework: el filtro de favoritos se aplica a TODAS las pestañas
    val allFiltered = if (vm.showFavoritesOnly) searchFiltered.filter { it.isFavorite } else searchFiltered
    val shelves: Map<BookStatus, List<Book>> = run {
        val widgetBookId = com.lecturameter.widget.loadWidgetBook(LocalContext.current)
        SHELF_ORDER.associateWith { status ->
            val books = when (status) {
                BookStatus.REREADING -> allFiltered.filter { it.status == BookStatus.REREADING || it.isRereading }
                BookStatus.FINISHED  -> allFiltered.filter { it.status == BookStatus.FINISHED }
                else -> allFiltered.filter { it.status == status }
            }
            val sorted = books.applySort(sortOrder)
            // El libro del widget va siempre al inicio de su lista
            if (widgetBookId > 0L) {
                val widgetBook = sorted.firstOrNull { it.id == widgetBookId }
                if (widgetBook != null) listOf(widgetBook) + sorted.filter { it.id != widgetBookId }
                else sorted
            } else sorted
        }
    }

    // Conteo sin doble conteo: FINISHED cuenta los FINISHED puros, REREADING los que relean
    val shelfCounts: Map<BookStatus, Int> = SHELF_ORDER.associateWith { status ->
        when (status) {
            BookStatus.FINISHED  -> allFiltered.count { it.status == BookStatus.FINISHED }
            BookStatus.REREADING -> allFiltered.count { it.status == BookStatus.REREADING || it.isRereading }
            else -> allFiltered.count { it.status == status }
        }
    }

    // Tutorial overlay — se muestra encima de todo mientras no esté completado
    if (!vm.tutorialCompleted) {
        // P-021: toast de celebración al terminar Y al saltar el tutorial
        val tutCtx = LocalContext.current
        val tutDoneMsg = stringResource(R.string.tut_done_toast)
        TutorialSlideshow(
            theme = theme,
            onComplete = { vm.completeTutorial(prefs); android.widget.Toast.makeText(tutCtx, tutDoneMsg, android.widget.Toast.LENGTH_SHORT).show() },
            onSkip    = { vm.completeTutorial(prefs); android.widget.Toast.makeText(tutCtx, tutDoneMsg, android.widget.Toast.LENGTH_SHORT).show() }
        )
        return
    }

    // ── Fase 6.1 (D-008): tips contextuales — estado y selector ──────────────────
    val sessionsForTips by vm.sessions.collectAsState()
    var railTipVisible by remember { mutableStateOf(!Tips.seen(prefs, Tips.RAIL)) }
    var historyTipVisible by remember { mutableStateOf(!Tips.seen(prefs, Tips.HISTORY)) }
    var activeTipSnack by remember { mutableStateOf<TipSnack?>(null) }
    // Marca del primer arranque (para el tip de sección no abierta tras N días)
    LaunchedEffect(Unit) {
        if (!prefs.contains("first_launch_ts")) prefs.edit().putLong("first_launch_ts", System.currentTimeMillis()).apply()
    }
    val tipFirstBook = TipSnack(Tips.FIRST_BOOK, stringResource(R.string.tip_first_book_title), stringResource(R.string.tip_first_book_body), stringResource(R.string.tip_first_book_action)) { onStats() }
    val tipWidget = TipSnack(Tips.WIDGET, stringResource(R.string.tip_widget_title), stringResource(R.string.tip_widget_body))
    val tipRecap = TipSnack(Tips.FIRST_RECAP, stringResource(R.string.tip_recap_title), stringResource(R.string.tip_recap_body), stringResource(R.string.tip_recap_action)) { onWeeklyRecap() }
    val tipUnvisited = TipSnack(Tips.UNVISITED, stringResource(R.string.tip_unvisited_title), "", stringResource(R.string.tip_unvisited_action)) { onStats() }
    // Fase 6.2: micro-encuesta voluntaria (invitación por el mismo canal de snackbars)
    var showSurveyDialog by remember { mutableStateOf(false) }
    var showSurveyFeedback by remember { mutableStateOf(false) }
    val surveyInvite = TipSnack("survey", stringResource(R.string.survey_invite_title), stringResource(R.string.survey_invite_body), stringResource(R.string.survey_invite_action)) { showSurveyDialog = true }
    LaunchedEffect(booksAll, sessionsForTips, railTipVisible) {
        // Uno por arranque; la card del rail tiene prioridad y silencia los snackbars
        if (Tips.snackShownThisLaunch || railTipVisible || activeTipSnack != null || booksAll.isEmpty()) return@LaunchedEffect
        val finishedCount = booksAll.count { it.status == BookStatus.FINISHED }
        val recapReady = com.lecturameter.utils.computeWeeklyRecap(booksAll, sessionsForTips, vm.bingoCard.value, vm.challenges.value, today()) != null
        val daysSinceFirst = (System.currentTimeMillis() - prefs.getLong("first_launch_ts", System.currentTimeMillis())) / 86_400_000L
        val candidate = when {
            !Tips.seen(prefs, Tips.FIRST_BOOK) && finishedCount == 1 -> tipFirstBook
            !Tips.seen(prefs, Tips.WIDGET) && sessionsForTips.size == 1 -> tipWidget
            !Tips.seen(prefs, Tips.FIRST_RECAP) && recapReady -> tipRecap
            !Tips.seen(prefs, Tips.UNVISITED) && !prefs.getBoolean("stats_opened", false) && daysSinceFirst >= 7 -> tipUnvisited
            else -> null
        }
        if (candidate != null) {
            Tips.mark(prefs, candidate.key)
            Tips.snackShownThisLaunch = true
            activeTipSnack = candidate
        } else {
            // ── Fase 6.2: la invitación de la micro-encuesta usa el mismo hueco ──
            // (un solo snackbar por arranque; los tips tienen prioridad)
            val now = System.currentTimeMillis()
            val first = prefs.getLong("first_launch_ts", now)
            val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            if (prefs.getInt("survey_year", 0) != year) {
                prefs.edit().putInt("survey_year", year).putInt("survey_count_year", 0).apply()
            }
            val eligible = finishedCount >= 5 || now - first >= 60L * 86_400_000
            val cooldownOk = now - prefs.getLong("survey_last_ts", 0L) >= 120L * 86_400_000
            val snoozeOk = now >= prefs.getLong("survey_snooze_until", 0L)
            if (eligible && cooldownOk && snoozeOk && prefs.getInt("survey_count_year", 0) < 3) {
                Tips.snackShownThisLaunch = true
                activeTipSnack = surveyInvite
            }
        }
    }

    // ── Fase 6.2: diálogo de la encuesta + acceso al feedback completo ──────────
    if (showSurveyDialog) {
        SurveyDialog(
            theme, prefs,
            onDismiss = {
                showSurveyDialog = false
                prefs.edit().putLong("survey_snooze_until", System.currentTimeMillis() + 60L * 86_400_000).apply()
            },
            onOpenFeedback = { showSurveyDialog = false; showSurveyFeedback = true }
        )
    }
    if (showSurveyFeedback) {
        FeedbackDialog(theme, onDismiss = { showSurveyFeedback = false })
    }

    // ── D-002/T1: bottom sheet de inicio rápido — libros Leyendo/Releyendo con ▶ ──
    if (showQuickStartSheet) {
        val qsBooks = booksAll.filter {
            it.status == BookStatus.READING || it.status == BookStatus.REREADING || it.isRereading
        }
        ModalBottomSheet(
            onDismissRequest = { showQuickStartSheet = false },
            containerColor = theme.bgMid,
            contentColor = theme.textMain
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text(
                    stringResource(R.string.quickstart_title),
                    color = theme.textMain, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (qsBooks.isEmpty()) {
                    Text(
                        stringResource(R.string.quickstart_empty),
                        color = theme.textMuted, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                qsBooks.forEach { b ->
                    // Feedback 14-07 (F13a): si el crono YA corre para este libro, la fila
                    // lo indica (borde ámbar + icono de crono en vez de ▶)
                    val timerActiveHere = TimerStateHolder.running && TimerStateHolder.activeBookId == b.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = theme.surface,
                        border = BorderStroke(1.dp, if (timerActiveHere) Amber else theme.border),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            BookCover(b.coverUrl, b.title, size = 34, isbnFallback = b.isbn)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(b.title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (timerActiveHere) {
                                    Text(
                                        stringResource(R.string.quickstart_session_running),
                                        color = Amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        statusLabel(if (b.status == BookStatus.REREADING || b.isRereading) BookStatus.REREADING else BookStatus.READING),
                                        color = statusColor(if (b.status == BookStatus.REREADING || b.isRereading) BookStatus.REREADING else BookStatus.READING),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (timerActiveHere) Amber else Green)
                                    .clickable {
                                        showQuickStartSheet = false
                                        // Con el crono ya corriendo en este libro, solo navegar
                                        if (!timerActiveHere) TimerQuickStart.pendingBookId = b.id
                                        onDetail(b.id)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (timerActiveHere) Icons.Default.Timer else Icons.Default.PlayArrow,
                                    contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    val gridColumns = if (maxWidth >= 840.dp) 2 else 1
    Column(Modifier.fillMaxSize()) {

        // ── Header ──────────────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(theme.bgDark, theme.bgDark))
                )
                .padding(horizontal = 16.dp)
        ) {
            // ── App title + barra de acciones (D-002 Fase 4): tema · ⏱️ · ⚙️ · ＋ ──
            // El hamburger desaparece: el historial vive en el rail (📜). El ＋ sube
            // aquí desde la fila de iconos — las tarjetas quedan despejadas.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 28.dp, bottom = 2.dp)
            ) {
                Text(
                    stringResource(R.string.txt_4d8b0a6f),
                    color = theme.textMain,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                // Feedback 13-07 (7): el selector de tema vuelve a la barra de acciones,
                // a la IZQUIERDA del crono (pegado al título no convencía)
                var showThemeMenu by remember { mutableStateOf(false) }
                // D-013: los temas de pago (Cuero/Aurora/AMOLED) abren el upsell si no hay Pro
                var showThemeUpsell by remember { mutableStateOf(false) }
                if (showThemeUpsell) {
                    ProUpsellSheet(theme, prefs, onDismiss = { showThemeUpsell = false })
                }
                val barContext = LocalContext.current
                Box {
                    IconButton(onClick = { showThemeMenu = true }, modifier = Modifier.size(34.dp)) {
                        // Feedback 2.6: Aurora recupera su icono PNG (ic_theme_aurora) en vez del emoji
                        // D-015: Cuero también lleva icono dedicado (tapa con marco, SVG sin emoji)
                        if (vm.themeMode == ThemeMode.AURORA) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_theme_aurora),
                                contentDescription = stringResource(R.string.theme_aurora),
                                modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
                            )
                        } else if (vm.themeMode == ThemeMode.CUERO) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_theme_cuero),
                                contentDescription = stringResource(R.string.theme_cuero),
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                when (vm.themeMode) {
                                    ThemeMode.LIGHT  -> "☀️"
                                    ThemeMode.DARK   -> "🌙"
                                    ThemeMode.AMOLED -> "⬛"
                                    else             -> "🌌"
                                },
                                fontSize = 15.sp
                            )
                        }
                    }
                    DropdownMenu(expanded = showThemeMenu, onDismissRequest = { showThemeMenu = false }) {
                        listOf(
                            ThemeMode.LIGHT  to stringResource(R.string.theme_light),
                            ThemeMode.DARK   to stringResource(R.string.theme_dark),
                            ThemeMode.AURORA to stringResource(R.string.theme_aurora),
                            ThemeMode.AMOLED to stringResource(R.string.theme_oled),
                            ThemeMode.CUERO  to stringResource(R.string.theme_cuero)
                        ).forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Feedback 2.6: icono PNG para Aurora también en el desplegable
                                        // D-015: icono dedicado de Cuero también aquí
                                        if (mode == ThemeMode.AURORA) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_theme_aurora),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        } else if (mode == ThemeMode.CUERO) {
                                            androidx.compose.foundation.Image(
                                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_theme_cuero),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(label, color = if (vm.themeMode == mode) Accent else theme.textMain, fontWeight = if (vm.themeMode == mode) FontWeight.Bold else FontWeight.Normal)
                                        // D-013: candado en los temas de pago sin Pro
                                        if (!com.lecturameter.utils.Pro.themeAllowed(prefs, mode)) {
                                            Spacer(Modifier.width(5.dp))
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = theme.textDim, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    if (!com.lecturameter.utils.Pro.themeAllowed(prefs, mode)) showThemeUpsell = true
                                    else vm.setThemeMode(mode, prefs, barContext)
                                    showThemeMenu = false
                                }
                            )
                        }
                    }
                }
                // ⏱️ crono desde el home (T1 elegida por Víctor): selector Leyendo/Releyendo
                // D-015 r3: en Cuero los iconos de la barra van en oro suave (actionIconTint)
                IconButton(onClick = { showQuickStartSheet = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Timer, contentDescription = "Timer", tint = actionIconTint(theme), modifier = Modifier.size(19.dp))
                }
                // Feedback 13-07 (4): acceso rápido a Importar/Exportar backups
                IconButton(onClick = onImportExport, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ImportExport, contentDescription = "Import/Export", tint = actionIconTint(theme), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = actionIconTint(theme), modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = onAdd,
                    colors = if (isCueroTheme(theme))
                        ButtonDefaults.buttonColors(containerColor = AccentCuero, contentColor = Color(0xFF241608))
                    else ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) { Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
            // ── Contador + eslogan (D-002 v3: se conservan bajo el título) ────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Text("📖", fontSize = 13.sp)
                Text(
                    stringResource(R.string.label_books_total, booksAll.size) + " · " + stringResource(R.string.txt_e860710c),
                    color = theme.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // ── D-002 (Fase 4): rail a la izquierda + contenido a la derecha ────────
        Row(Modifier.weight(1f)) {
            if (railWidth > 0.dp) {
            Box(Modifier.width(railWidth)) {
            HomeRail(
                theme = theme,
                prefs = prefs,
                onHistory = { historyOpen = !historyOpen },
                onLibrary = {
                    historyOpen = false
                    listScope.launch { listState.animateScrollToItem(0) }
                },
                onStats = { historyOpen = false; onStats() },
                onChallenges = { historyOpen = false; onChallenges() },
                onBingo = { historyOpen = false; onEasterEgg() },
                onWrapped = { historyOpen = false; onWrappedHistory() },
                onHistoryOpen = { historyOpen = true },
                onRailClose = { railManuallyHidden = true; railVisible = false },
            )
            }
            Box(Modifier.fillMaxHeight().width(1.dp).background(theme.border))
            }
            Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
            // Feedback 13-07: la línea del rail queda ENTRE MEDIAS — mismo aire a ambos
            // lados (el rail ya deja ~3dp; el contenido deja 10dp aquí)
            Column(Modifier.padding(end = 16.dp, start = 10.dp)) {

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.txt_84f524c0), color = theme.textDim, fontSize = 13.sp) },
                leadingIcon  = { Icon(Icons.Default.Search, null, tint = theme.textDim, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = theme.textDim, modifier = Modifier.size(18.dp))
                            }
                        }
                        // Feedback 13-07: la búsqueda online (antes 🔍 del rail) vive aquí,
                        // junto a la ordenación — buscar libros nuevos en las APIs
                        // Feedback 17-07: si ya hay texto escrito, se lleva a la búsqueda
                        // online y la barra local queda limpia al volver
                        IconButton(onClick = {
                            if (searchQuery.isNotBlank()) {
                                listMainRef?.pendingSearchQuery?.value = searchQuery
                                searchQuery = ""
                            }
                            onSearch()
                        }) {
                            Icon(Icons.Default.TravelExplore, contentDescription = stringResource(R.string.txt_113f7428), tint = Accent, modifier = Modifier.size(20.dp))
                        }
                        // Sort button inside search row
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Filled.Sort, null, tint = Accent, modifier = Modifier.size(20.dp))
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(sortLabel(order), color = if (sortOrder == order) Accent else theme.textMain) },
                                        onClick = { sortOrderName = order.name; showSortMenu = false }
                                    )
                                }
                                // v2.4 rework: filtro persistente "Ver solo favoritos"
                                HorizontalDivider(color = theme.border)
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                stringResource(R.string.show_favorites_only),
                                                color = if (vm.showFavoritesOnly) FavoriteRed else theme.textMain,
                                                fontWeight = if (vm.showFavoritesOnly) FontWeight.SemiBold else FontWeight.Normal,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Switch(
                                                checked = vm.showFavoritesOnly,
                                                onCheckedChange = { vm.setShowFavoritesOnly(it, prefs) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = FavoriteRed,
                                                    uncheckedTrackColor = theme.border
                                                )
                                            )
                                        }
                                    },
                                    onClick = { vm.setShowFavoritesOnly(!vm.showFavoritesOnly, prefs) }
                                )
                            }
                        }
                        // (scan ISBN movido a BookSearchScreen)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 0.dp),
                colors = fieldColors(theme),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // ── Dialog elección post-scan ISBN ────────────────────────
            if (showIsbnScanDialog && scannedIsbnForDialog.isNotBlank()) {
                AlertDialog(
                    onDismissRequest = {
                        showIsbnScanDialog = false
                        listMainRef?.pendingScannedIsbn?.value = null
                    },
                    containerColor = theme.bgMid,
                    title = { Text(stringResource(R.string.txt_016a31c7), color = theme.textMain, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(8.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
                                Text(scannedIsbnForDialog, color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                            Text(stringResource(R.string.txt_55800f4c), color = theme.textMuted, fontSize = 13.sp)
                        }
                    },
                    confirmButton = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Button(
                                onClick = {
                                    showIsbnScanDialog = false
                                    // Buscar automáticamente → BookSearch con ISBN como query
                                    // pendingScannedIsbn queda seteado para que BookSearchScreen lo use
                                    onNavigateToBookSearch()  // navega a BookSearch (el isbn ya está en pending)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.txt_69e20782), fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = {
                                    showIsbnScanDialog = false
                                    listMainRef?.pendingScannedIsbn?.value = null
                                    onAddWithIsbn(scannedIsbnForDialog)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Accent)
                            ) {
                                Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.txt_97225860), color = Accent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    },
                    dismissButton = {}
                )
            }

            // ── Shelf tabs (fixed, all visible) ─────────────────
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = Color.Transparent,
                contentColor = Accent,
                indicator = { tabPositions ->
                    if (activeTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = statusColor(SHELF_ORDER[activeTab]),
                            height = 2.dp
                        )
                    }
                },
                divider = { HorizontalDivider(color = theme.border, thickness = 0.5.dp) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                SHELF_ORDER.forEachIndexed { index, status ->
                    val count = shelfCounts[status] ?: 0
                    val color = statusColor(status)
                    val selected = activeTab == index
                    Tab(
                        selected = selected,
                        onClick  = { activeTab = index },
                        modifier = Modifier.padding(bottom = 0.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)
                        ) {
                            Text(
                                statusEmoji(status),
                                fontSize = 13.sp
                            )
                            Text(
                                statusLabel(status),
                                fontSize = 8.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) color else theme.textDim,
                                maxLines = 1
                            )
                            if (count > 0) {
                                Box(
                                    Modifier
                                        .padding(top = 2.dp)
                                        .background(
                                            if (selected) color else theme.border,
                                            RoundedCornerShape(50)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    // QA 12-07 r2: número en textMain — con textDim apenas
                                    // se leía sobre la pill en AMOLED y Aurora.
                                    Text(
                                        "$count",
                                        fontSize = 8.sp,
                                        color = if (selected) Color.White else theme.textMain,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // D-015 (Cuero): nervio de lomo entre las estanterías y la lista (solo tema Cuero)
        CueroNervio(theme, Modifier.padding(start = 10.dp, end = 16.dp, top = 4.dp))

        // ── Fase 6.1 (D-008, T1): card del rail — primera vez en el home ─────
        if (railTipVisible) {
            TipCard(
                stringResource(R.string.tip_rail_title), stringResource(R.string.tip_rail_body), theme,
                onDismiss = { Tips.mark(prefs, Tips.RAIL); railTipVisible = false },
                modifier = Modifier.padding(start = 10.dp, end = 16.dp, top = 6.dp)
            )
        }

        // ── Content: books for active shelf ─────────────────────────────────
        val activeStatus = SHELF_ORDER[activeTab]
        val activeBooks  = shelves[activeStatus] ?: emptyList()

        if (booksAll.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📚", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.txt_66308bc7), color = theme.textMain, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.txt_eb8e218c), color = theme.textDim, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                state = listState,
                // D-002: alineado con la barra de búsqueda (línea del rail entre medias)
                modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeBooks.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (vm.showFavoritesOnly) "❤️" else statusEmoji(activeStatus), fontSize = 44.sp)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    if (vm.showFavoritesOnly) stringResource(R.string.empty_shelf_favorites)
                                    else emptyShelfHint(activeStatus),
                                    color = theme.textDim,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                // Feedback 13-07 (4): la lupa de la barra no comunica "buscar
                                // libros nuevos" — CTA contextual: si la búsqueda local no
                                // encuentra nada, ofrecer buscarlo en internet AQUÍ, justo
                                // cuando el usuario lo necesita (sin depender del tutorial)
                                if (searchQuery.isNotBlank() && !vm.showFavoritesOnly) {
                                    Spacer(Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            // Feedback 14-07 (F6): canal propio de query — NO
                                            // pendingScannedIsbn (disparaba el diálogo del escáner)
                                            listMainRef?.pendingSearchQuery?.value = searchQuery
                                            // Feedback 17-07: al volver de la búsqueda online la barra local queda limpia
                                            searchQuery = ""
                                            onNavigateToBookSearch()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.TravelExplore, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.search_online_cta, searchQuery), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(activeBooks, key = { it.id }) { book ->
                        val context = LocalContext.current
                        val widgetBookId = com.lecturameter.widget.loadWidgetBook(context)
                        val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
                        AnimatedVisibility(
                            visibleState = visibleState,
                            enter = slideInVertically(
                                initialOffsetY = { it / 5 },
                                animationSpec = tween(durationMillis = 280)
                            ) + fadeIn(animationSpec = tween(durationMillis = 320))
                        ) {
                        // v2.4 rework: swipe izquierda/derecha alterna favorito
                        val favAddedMsg   = stringResource(R.string.favorite_added, book.title)
                        val favRemovedMsg = stringResource(R.string.favorite_removed, book.title)
                        FavoriteSwipe(
                            isFavorite = book.isFavorite,
                            onToggleFavorite = {
                                val nowFav = vm.toggleFavorite(book.id, prefs)
                                listScope.launch {
                                    favSnackbarState.currentSnackbarData?.dismiss()
                                    favSnackbarState.showSnackbar(
                                        message = if (nowFav) favAddedMsg else favRemovedMsg,
                                        duration = androidx.compose.material3.SnackbarDuration.Short
                                    )
                                }
                            }
                        ) {
                        BookCard(
                            book, theme,
                            onClick = { onDetail(book.id) },
                            onDelete = { vm.deleteBook(book.id, prefs) },
                            isWidgetBook = widgetBookId == book.id,
                            isRefreshingCover = vm.isCoverRefreshing(book.id),
                            onRefreshCover = {
                                vm.refreshCover(book.id, prefs) { coverFound, genreFound ->
                                    if (coverFound || genreFound) {
                                        refreshWidgetForBookIfSelected(context, book.id, clearCoverCache = true)
                                    }
                                    if (!coverFound || !genreFound) {
                                        val missing = listOfNotNull(
                                            if (!coverFound) context.getString(R.string.word_cover) else null,
                                            if (!genreFound) context.getString(R.string.word_genre) else null
                                        )
                                        val msg = context.getString(R.string.msg_not_found_prefix, missing.joinToString(context.getString(R.string.word_join_nor)))
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onApplyCoverUrl = { newUrl ->
                                vm.updateCover(book.id, newUrl, prefs)
                                refreshWidgetForBookIfSelected(context, book.id, clearCoverCache = true)
                            },
                            onBroken = { vm.markCoverBroken(book.id, prefs) },
                            sessionPages = vm.sessionsForBook(book.id).sumOf { it.pages },
                            sessionDays = vm.sessionsForBook(book.id).map { it.date }.toSet().size
                        )
                        } // FavoriteSwipe
                        } // AnimatedVisibility
                        Spacer(Modifier.height(10.dp))
                    }

                }
            }
        }
        } // right column (D-002)
        } // Box contenido (D-002)
        } // Row rail + contenido (D-002)
    } // Column (header + contenido)

    // Feedback 14-07: rail cerrado → swipe → desde el borde izquierdo lo reabre
    // (franja invisible de 16dp; solo existe con el rail oculto, no roba gestos)
    if (!railVisible && !historyOpen) {
        val reopenAcc = remember { mutableStateOf(0f) }
        Box(
            Modifier.align(Alignment.CenterStart).width(16.dp).fillMaxHeight()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        reopenAcc.value += delta
                        if (reopenAcc.value > 60f) {
                            railManuallyHidden = false
                            railVisible = true
                            reopenAcc.value = 0f
                        }
                    },
                    onDragStarted = { reopenAcc.value = 0f }
                )
        )
    }

    // Feedback 14-07: historial CASI a pantalla completa — vive al nivel del Box raíz
    // (dentro del Column con weight le quedaba altura 0 y no se veía nada).
    // Deja ~16dp a la derecha con el scrim asomando: pista visual de que el panel
    // se cierra con swipe ← (tocar la franja también cierra). Pendiente de explicarse
    // en el onboarding contextual (Fase 6).
    if (historyOpen) {
        Box(Modifier.fillMaxSize().background(Color(0x88000000)).clickable { historyOpen = false })
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = historyOpen,
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it })
    ) {
        val closeAcc = remember { mutableStateOf(0f) }
        Box(
            Modifier
                .fillMaxSize()
                // Feedback 14-07: mismo hueco que dejaba el rail (46dp) — el home asoma
                // detrás y la pista de "esto se cierra deslizando" es inequívoca
                .padding(end = 46.dp)
                .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                .border(1.dp, theme.border, RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                .background(Brush.verticalGradient(listOf(theme.bgDark, theme.bgMid, theme.bgDeep)))
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        closeAcc.value += delta
                        if (closeAcc.value < -60f) { historyOpen = false; closeAcc.value = 0f }
                    },
                    onDragStarted = { closeAcc.value = 0f }
                )
        ) {
            SessionHistoryScreen(
                vm = vm,
                theme = theme,
                onClose = { historyOpen = false },
                onDetail = { id -> historyOpen = false; onDetail(id) },
                // Fase 6.1 (D-008, T2): primera apertura del historial — cómo se cierra
                showCloseTip = historyTipVisible,
                onDismissCloseTip = { Tips.mark(prefs, Tips.HISTORY); historyTipVisible = false }
            )
        }
    }
    // v2.4 rework: host del Snackbar de favoritos, superpuesto al contenido
    androidx.compose.material3.SnackbarHost(
        hostState = favSnackbarState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
    )
    // Fase 6.1 (D-008): snackbar de tips (formato A) — hitos de datos.
    // 6.2: si es la invitación de la encuesta, descartarla pospone 2 meses.
    activeTipSnack?.let { t ->
        TipSnackbar(
            t, theme,
            onGone = {
                if (t.key == "survey") prefs.edit().putLong("survey_snooze_until", System.currentTimeMillis() + 60L * 86_400_000).apply()
                activeTipSnack = null
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).padding(bottom = 14.dp)
        )
    }
    } // Box
}

@Composable
fun emptyShelfHint(status: BookStatus) = when (status) {
    BookStatus.READING   -> stringResource(R.string.empty_shelf_reading)
    BookStatus.REREADING -> stringResource(R.string.empty_shelf_rereading)
    BookStatus.FINISHED  -> stringResource(R.string.empty_shelf_finished)
    BookStatus.PENDING   -> stringResource(R.string.empty_shelf_pending)
    BookStatus.DROPPED   -> stringResource(R.string.empty_shelf_dropped)
}
