package com.lecturameter

// ChallengesScreen, DailySessionsScreen, HeatmapView, StatsChartsView y componentes de graficos.
// Extraido de MainActivity.kt el 15-07-2026 (ruptura del monolito, sin cambios funcionales).


import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.aspectRatio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import com.lecturameter.model.*
import com.lecturameter.utils.*
import androidx.navigation.compose.composable

/** B-026: tope del objetivo de un reto. 1.000.000 cubre cualquier meta real
 *  (páginas, libros, sesiones, minutos o días de racha) sin desbordar el Int. */
const val MAX_CHALLENGE_TARGET = 1_000_000L

// Feedback 14-07 + D-016: nombre traducido de los retos por defecto (el sembrado congela
// el nombre en el idioma del momento) — lo usan la pantalla de retos Y el historial
@Composable
internal fun challengeDefaultName(type: ChallengeType): String = when (type) {
    ChallengeType.BOOKS    -> stringResource(R.string.challenge_default_books)
    ChallengeType.STREAK   -> stringResource(R.string.challenge_default_streak)
    ChallengeType.PAGES    -> stringResource(R.string.challenge_default_pages)
    ChallengeType.SESSIONS -> stringResource(R.string.challenge_default_sessions)
    ChallengeType.MINUTES  -> stringResource(R.string.challenge_default_minutes)
}

// P-026: unidad corta de lo que aporta cada libro al reto
@Composable
internal fun challengeUnitLabel(type: ChallengeType): String = when (type) {
    ChallengeType.PAGES    -> stringResource(R.string.challenge_unit_pages)
    ChallengeType.MINUTES  -> stringResource(R.string.challenge_unit_minutes)
    ChallengeType.SESSIONS -> stringResource(R.string.challenge_unit_sessions)
    ChallengeType.BOOKS    -> stringResource(R.string.challenge_unit_books)
    ChallengeType.STREAK   -> ""
}

/** P-026: hoja con el desglose de libros que aportan a un reto (activo o histórico).
 *  STREAK no tiene desglose por libro (decisión D-016: una racha va por días). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeContributionSheet(
    title: String,
    type: ChallengeType,
    rangeStart: String,
    rangeEnd: String,
    titleFilter: String?,
    vm: BooksViewModel,
    theme: Theme,
    onDismiss: () -> Unit,
    // Decisión 17-07: los retos ARCHIVADOS traen su desglose congelado en el snapshot;
    // null = reto activo (o snapshot antiguo sin el campo) → se calcula en vivo.
    frozen: List<com.lecturameter.model.FrozenContribution>? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = theme.bgMid) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(title, color = theme.textMain, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.challenge_contrib_title), color = theme.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            if (type == ChallengeType.STREAK) {
                Text(stringResource(R.string.challenge_contrib_streak), color = theme.textDim, fontSize = 13.sp)
            } else {
                val contribs = remember(type, rangeStart, rangeEnd, titleFilter, frozen) {
                    frozen?.map { Triple(it.title, it.value, it.frac) }
                        ?: vm.challengeContributions(type, rangeStart, rangeEnd, titleFilter)
                }
                if (contribs.isEmpty()) {
                    Text(stringResource(R.string.challenge_contrib_none), color = theme.textDim, fontSize = 13.sp)
                } else {
                    val unit = challengeUnitLabel(type)
                    val acc = accentForTheme(theme)
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        contribs.forEach { (bookTitle, value, frac) ->
                            Surface(shape = RoundedCornerShape(12.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            bookTitle.ifBlank { stringResource(R.string.challenge_contrib_deleted) },
                                            color = if (bookTitle.isBlank()) theme.textDim else theme.textMain,
                                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (unit.isBlank()) "$value" else "$value $unit",
                                            color = acc, fontSize = 12.sp, fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        // A10: si el valor real es mayor que 0 pero redondea a 0%,
                                        // mostrar "menor que 1%" en vez de un "0%" engañoso.
                                        val pct = Math.round(frac * 100)
                                        Text(
                                            if (pct == 0 && frac > 0f) stringResource(R.string.pct_less_than_one) else "$pct%",
                                            color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressBar(frac, acc, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ChallengesScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit, onHistory: () -> Unit = {}) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Challenge?>(null) }
    // P-026: reto cuyo desglose de libros está abierto
    var contribTarget by remember { mutableStateOf<Challenge?>(null) }
    // D-016: límite del plan gratis alcanzado (diálogo informativo)
    var showFreeLimitDialog by remember { mutableStateOf(false) }

    // D-016: el barrido de archivado corre también al entrar (además de al arrancar),
    // para que un reto completado ayer se archive sin reiniciar la app
    LaunchedEffect(Unit) { vm.reconcileChallenges(prefs) }

    @Composable
    fun challengeDisplayName(challenge: Challenge): String =
        if (challenge.isDefault) challengeDefaultName(challenge.type) else challenge.name

    // ── Diálogo crear reto ────────────────────────────────────────────────────
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var typeSelected by remember { mutableStateOf(ChallengeType.BOOKS) }
        var targetText by remember { mutableStateOf("") }
        var titleFilterText by remember { mutableStateOf("") }
        var startDateText by remember { mutableStateOf("") }
        var endDateText by remember { mutableStateOf("") }
        var createError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = theme.bgMid,
            // Feedback 2.7: ancho fijo — el AlertDialog de M3 crece con el ancho intrínseco
            // del OutlinedTextField y, con un nombre de reto largo, se salía de la pantalla
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.92f),
            title = { Text(stringResource(R.string.challenge_create_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                // v2.4: scroll de respaldo para landscape móvil
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        placeholder = { Text(stringResource(R.string.challenge_name_hint), color = theme.textDim, fontSize = 13.sp) },
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Tipo de reto (chips)
                    Text(stringResource(R.string.challenge_type_label), color = theme.textMuted, fontSize = 12.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChallengeType.entries.chunked(3).forEach { rowTypes ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowTypes.forEach { t ->
                                    val selected = typeSelected == t
                                    Surface(
                                        onClick = { typeSelected = t },
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (selected) Accent.copy(alpha = 0.15f) else theme.surface,
                                        border = BorderStroke(1.dp, if (selected) Accent else theme.border),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            challengeTypeLabel(t),
                                            color = if (selected) Accent else theme.textMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = targetText,
                        // B-026: el campo aceptaba cifras de cualquier longitud; 17 dígitos
                        // desbordaban el Int y toIntOrNull() daba null → el error decía
                        // "debe ser mayor que 0" con un número enorme delante. Se corta en 7.
                        onValueChange = { targetText = it.filter { c -> c.isDigit() }.take(7); createError = "" },
                        placeholder = { Text(stringResource(R.string.challenge_target_hint), color = theme.textDim, fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (typeSelected == ChallengeType.BOOKS) {
                        OutlinedTextField(
                            value = titleFilterText,
                            onValueChange = { titleFilterText = it },
                            placeholder = { Text(stringResource(R.string.challenge_filter_hint), color = theme.textDim, fontSize = 13.sp) },
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Feedback 2.6: fecha de inicio opcional. Antes se imponía el día de creación,
                    // dejando fuera los libros ya terminados (retos de saga siempre a 0).
                    OutlinedTextField(
                        value = startDateText,
                        onValueChange = { startDateText = it; createError = "" },
                        placeholder = { Text(stringResource(R.string.challenge_start_date_hint), color = theme.textDim, fontSize = 13.sp) },
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = endDateText,
                        onValueChange = { endDateText = it; createError = "" },
                        placeholder = { Text(stringResource(R.string.challenge_end_date_hint), color = theme.textDim, fontSize = 13.sp) },
                        colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.challenge_dates_note), color = theme.textDim, fontSize = 11.sp)
                    if (createError.isNotBlank()) Text(createError, color = Red, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // B-026: toLongOrNull + rango explícito. Antes un objetivo fuera de
                    // rango se confundía con "no es un número".
                    val target = targetText.toLongOrNull()?.takeIf { it in 1..MAX_CHALLENGE_TARGET }?.toInt()
                    val startParsed = if (startDateText.isBlank()) null else parseFlexibleDate(startDateText.trim())
                    val endParsed = if (endDateText.isBlank()) null else parseFlexibleDate(endDateText.trim())
                    when {
                        name.isBlank() -> { createError = context.getString(R.string.challenge_err_name); return@TextButton }
                        target == null || target < 1 -> { createError = context.getString(R.string.challenge_err_target); return@TextButton }
                        startDateText.isNotBlank() && startParsed == null -> { createError = context.getString(R.string.err_date_invalid_format); return@TextButton }
                        endDateText.isNotBlank() && endParsed == null -> { createError = context.getString(R.string.err_date_invalid_format); return@TextButton }
                        startParsed != null && endParsed != null && startParsed > endParsed -> { createError = context.getString(R.string.err_date_invalid_format); return@TextButton }
                    }
                    vm.addChallenge(
                        Challenge(
                            id = System.currentTimeMillis(),
                            name = name.trim(),
                            type = typeSelected,
                            target = target!!,
                            // Feedback 2.6: sin fecha de inicio → año natural (antes today(), que
                            // excluía todo lo terminado antes de crear el reto)
                            startDate = startParsed,
                            endDate = endParsed,
                            isDefault = false,
                            titleFilter = titleFilterText.trim().takeIf { it.isNotBlank() && typeSelected == ChallengeType.BOOKS }
                        ), prefs
                    )
                    showCreateDialog = false
                }) { Text(stringResource(R.string.challenge_save), color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
    }

    // ── Diálogo borrar reto: Cancel = Accent, Delete = Red (convención) ───────
    deleteTarget?.let { challenge ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.challenge_delete_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.challenge_delete_text, challengeDisplayName(challenge)), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { vm.deleteChallenge(challenge.id, prefs); deleteTarget = null }) {
                    Text(stringResource(R.string.txt_5b5c9f9d), color = Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.txt_847607d7), color = Accent) } }
        )
    }

    // P-026: hoja de desglose del reto activo (rango = el mismo que usa el progreso)
    contribTarget?.let { challenge ->
        val yearNow = Calendar.getInstance().get(Calendar.YEAR)
        ChallengeContributionSheet(
            title = challengeDisplayName(challenge),
            type = challenge.type,
            rangeStart = challenge.startDate ?: "$yearNow-01-01",
            rangeEnd = challenge.endDate ?: "$yearNow-12-31",
            titleFilter = challenge.titleFilter,
            vm = vm, theme = theme,
            onDismiss = { contribTarget = null }
        )
    }

    // D-013: upsell de Pro (lo abren el límite de retos y el aviso del historial)
    var showProUpsell by remember { mutableStateOf(false) }
    if (showProUpsell) {
        ProUpsellSheet(theme, prefs, onDismiss = { showProUpsell = false })
    }

    // D-016: tope del plan gratis (3 páginas de retos activos)
    if (showFreeLimitDialog) {
        AlertDialog(
            onDismissRequest = { showFreeLimitDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.challenge_free_limit_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.challenge_free_limit_text), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showFreeLimitDialog = false; showProUpsell = true }) {
                    Text(stringResource(R.string.pro_title), color = accentForTheme(theme), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFreeLimitDialog = false }) {
                    Text(stringResource(R.string.txt_847607d7), color = theme.textMuted)
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.challenge_title), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.challenge_subtitle), color = theme.textMuted, fontSize = 13.sp)
            }
            // D-016 (P-011): acceso al historial de retos — icono `history` elegido por Víctor
            IconButton(onClick = onHistory) {
                Icon(Icons.Default.History, contentDescription = stringResource(R.string.challenge_history_title), tint = actionIconTint(theme))
            }
        }

        // D-016: tip de onboarding — los retos completados o vencidos se archivan solos
        var archiveTipVisible by remember { mutableStateOf(!Tips.seen(prefs, Tips.CHALLENGE_ARCHIVE)) }
        if (archiveTipVisible) {
            TipCard(
                stringResource(R.string.tip_challenge_archive_title),
                stringResource(R.string.tip_challenge_archive_body),
                theme,
                onDismiss = { Tips.mark(prefs, Tips.CHALLENGE_ARCHIVE); archiveTipVisible = false },
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // D-004: challenges es StateFlow; se colecciona en la raíz de la pantalla
        val challenges by vm.challenges.collectAsState()
        // Feedback 13-07 (11): sin scroll infinito — los retos van en PÁGINAS horizontales,
        // con dot-pagination (mismo estilo que el tutorial). El botón de crear queda
        // fijo abajo, fuera del pager. Feedback 14-07 (F3): caben 5 por página.
        val perPage = 5
        if (challenges.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EmojiEvents, null, tint = theme.textDim, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.challenge_empty), color = theme.textDim, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            val pageCount = (challenges.size + perPage - 1) / perPage
            // El lambda relee `challenges` (delegado de State): si borras retos y baja el
            // nº de páginas, el pager se ajusta solo
            val pagerState = androidx.compose.foundation.pager.rememberPagerState { (challenges.size + perPage - 1) / perPage }
            // Feedback 14-07 (F15): reajuste EN VIVO — al borrar el último reto de la
            // última página, currentPage quedaba fuera de rango y la pantalla se veía
            // vacía hasta reentrar. Clamp inmediato a la última página válida.
            LaunchedEffect(pageCount) {
                if (pagerState.currentPage >= pageCount) {
                    pagerState.animateScrollToPage((pageCount - 1).coerceAtLeast(0))
                }
            }
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.weight(1f)
            ) { page ->
                val pageItems = challenges.drop(page * perPage).take(perPage)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    pageItems.forEach { challenge ->
                val (current, target) = vm.challengeProgress(challenge)
                val ratio = (current.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
                val done = current >= target
                // P-026: tocar la tarjeta abre el desglose de libros que aportan
                Surface(onClick = { contribTarget = challenge }, shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, if (done) Green.copy(alpha = 0.5f) else theme.border)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                // v2.5 + Feedback 14-07: los retos predeterminados se traducen
                                // al idioma actual (los 5, no solo libros/racha)
                                val displayName = challengeDisplayName(challenge)
                                Text(displayName, color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val meta = buildString {
                                    append(if (challenge.isDefault) stringResource(R.string.challenge_meta_default) else stringResource(R.string.challenge_meta_custom))
                                    append(" · ").append(challengeTypeLabel(challenge.type))
                                    challenge.startDate?.let { append(" · ").append(stringResource(R.string.challenge_meta_from, fmtDate(it))) }
                                    challenge.endDate?.let { append(" · ").append(stringResource(R.string.challenge_meta_until, fmtDate(it))) }
                                    challenge.titleFilter?.takeIf { it.isNotBlank() }?.let { append(" · «").append(it).append("»") }
                                }
                                Text(meta, color = theme.textMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(20.dp), color = if (done) Green.copy(alpha = 0.15f) else Accent.copy(alpha = 0.12f)) {
                                Text(
                                    stringResource(R.string.challenge_progress, current, target),
                                    color = if (done) Green else Accent,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { deleteTarget = challenge }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, stringResource(R.string.txt_5b5c9f9d), tint = Red.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // P-027: el porcentaje va junto a la barra (antes solo el absoluto arriba)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressBar(ratio, if (done) Green else Accent, Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${(ratio * 100).toInt()}%",
                                color = if (done) Green else theme.textMuted,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        if (done) {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.challenge_completed), color = Green, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                    } // forEach pageItems
                } // Column página
            } // HorizontalPager
            // Dot-pagination (solo con más de una página)
            if (pageCount > 1) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(pageCount) { i ->
                            Box(
                                Modifier
                                    .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (i == pagerState.currentPage) Accent else theme.border)
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
        OutlinedButton(
            onClick = {
                // D-016: el plan gratis incluye 3 páginas de retos activos (5 por página)
                val freeMax = com.lecturameter.utils.Pro.FREE_CHALLENGE_PAGES * com.lecturameter.utils.Pro.PER_PAGE
                if (!com.lecturameter.utils.Pro.isPro(prefs) && challenges.size >= freeMax) showFreeLimitDialog = true
                else showCreateDialog = true
            },
            modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 0.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Accent)
        ) {
            Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.challenge_create_button), color = Accent, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ── ChallengeHistoryScreen (D-016 / P-011) ────────────────────────────────────

/** Historial de retos archivados: selector de año + paginación (5 por página, como Retos).
 *  Gratis: 3 páginas por año (acumulativo, cada año suma 3); Pro: completo. Los snapshots
 *  ocultos por el límite NO se borran, solo dejan de mostrarse. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun ChallengeHistoryScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit) {
    val history by vm.challengeHistory.collectAsState()
    val acc = accentForTheme(theme)
    // P-026 aplica también a retos históricos (decisión 16-07)
    var contribTarget by remember { mutableStateOf<ChallengeSnapshot?>(null) }

    contribTarget?.let { snap ->
        ChallengeContributionSheet(
            title = if (snap.isDefault) challengeDefaultName(snap.type) else snap.name,
            type = snap.type,
            rangeStart = snap.startDate ?: "${snap.year}-01-01",
            rangeEnd = snap.endDate ?: "${snap.year}-12-31",
            titleFilter = snap.titleFilter,
            vm = vm, theme = theme,
            onDismiss = { contribTarget = null },
            frozen = snap.contributions
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.challenge_history_title), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.challenge_history_subtitle), color = theme.textMuted, fontSize = 13.sp)
            }
        }

        if (history.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, tint = theme.textDim, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.challenge_history_empty), color = theme.textDim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        } else {
            val years = history.map { it.year }.distinct().sortedDescending()
            var selectedYear by rememberSaveable { mutableStateOf(years.first()) }
            if (selectedYear !in years) selectedYear = years.first()

            // Selector de año (chips horizontales)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp)
            ) {
                years.forEach { y ->
                    val selected = selectedYear == y
                    Surface(
                        onClick = { selectedYear = y },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) acc.copy(alpha = 0.15f) else theme.surface,
                        border = BorderStroke(1.dp, if (selected) acc else theme.border)
                    ) {
                        Text(
                            "$y", color = if (selected) acc else theme.textMuted,
                            fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Historial de retos GRATIS COMPLETO para todos (cambio de última hora de
            // Víctor 16-07): ya no hay límite de páginas por año ni aviso Pro cerrable.
            val visible = history.filter { it.year == selectedYear }.sortedByDescending { it.archivedAt }

            if (visible.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.challenge_history_empty), color = theme.textDim, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            } else {
                val perPage = com.lecturameter.utils.Pro.PER_PAGE
                val pageCount = (visible.size + perPage - 1) / perPage
                val pagerState = androidx.compose.foundation.pager.rememberPagerState { (visible.size + perPage - 1) / perPage }
                LaunchedEffect(pageCount, selectedYear) {
                    if (pagerState.currentPage >= pageCount) {
                        pagerState.scrollToPage((pageCount - 1).coerceAtLeast(0))
                    }
                }
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val pageItems = visible.drop(page * perPage).take(perPage)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                        pageItems.forEach { snap ->
                            val ratio = (snap.finalProgress.toFloat() / snap.target.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val barColor = if (snap.completed) Green else Amber
                            Surface(
                                onClick = { contribTarget = snap },
                                shape = RoundedCornerShape(16.dp), color = theme.surface,
                                border = BorderStroke(1.dp, theme.border)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                if (snap.isDefault) challengeDefaultName(snap.type) else snap.name,
                                                color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis
                                            )
                                            val meta = buildString {
                                                append(challengeTypeLabel(snap.type))
                                                append(" · ").append(snap.year)
                                                snap.titleFilter?.takeIf { it.isNotBlank() }?.let { append(" · «").append(it).append("»") }
                                            }
                                            Text(meta, color = theme.textMuted, fontSize = 11.sp)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        // Badge COMPLETADO / VENCIDO con el % final congelado
                                        Surface(shape = RoundedCornerShape(20.dp), color = barColor.copy(alpha = 0.15f)) {
                                            Text(
                                                if (snap.completed) stringResource(R.string.challenge_history_badge_completed)
                                                else stringResource(R.string.challenge_history_badge_expired),
                                                color = barColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // A3: la barra sigue capada al 100% (ratio), pero el texto
                                        // muestra el porcentaje REAL sin capar (p. ej. 62/12 = 516%),
                                        // porque capar el texto a 100% era engañoso.
                                        val realPct = snap.finalProgress * 100 / snap.target.coerceAtLeast(1)
                                        LinearProgressBar(ratio, barColor, Modifier.weight(1f))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "${snap.finalProgress}/${snap.target} · ${realPct}%",
                                            color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (pageCount > 1) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(pageCount) { i ->
                                Box(
                                    Modifier
                                        .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(if (i == pagerState.currentPage) acc else theme.border)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ── DailySessionsScreen ───────────────────────────────────────────────────────

@Composable
fun DailySessionsScreen(
    date: String,
    sessions: List<ReadingSession>,
    books: List<Book>,
    theme: Theme,
    onNavigateToDetail: (Long, String) -> Unit,
    onBack: () -> Unit
) {
    // Agrupar sesiones por libro, descartar libros eliminados
    val bookEntries = sessions
        .groupBy { it.bookId }
        .mapNotNull { (bookId, bookSessions) ->
            val book = books.find { it.id == bookId } ?: return@mapNotNull null
            val lastDate = bookSessions.maxByOrNull { it.date }?.date ?: ""
            Triple(book, bookSessions, lastDate)
        }
        .sortedByDescending { it.third } // más reciente primero

    val formattedDate = remember(date) {
        try {
            val inFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val parsed = inFmt.parse(date) ?: return@remember date
            // Usar Locale del sistema (respeta idioma de la app configurado via AppCompatDelegate)
            val outFmt = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault())
            outFmt.format(parsed)
        } catch (_: Exception) { date }
    }

    fun formatMinutes(min: Int): String = when {
        min >= 60 -> { val h = min / 60; val m = min % 60; if (m > 0) "${h}h ${m}m" else "${h}h" }
        else -> "${min}m"
    }

    Column(Modifier.fillMaxSize().background(theme.bgDark)) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(theme.bgMid)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Text("←", color = theme.textMain, fontSize = 20.sp)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(stringResource(R.string.txt_0c2c2f10), color = theme.textMuted, fontSize = 11.sp)
                Text(formattedDate, color = theme.textMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (bookEntries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.txt_8d5e2184), color = theme.textMuted, fontSize = 14.sp)
            }
            return@Column
        }

        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            items(bookEntries, key = { it.first.id }) { (book, bookSessions, _) ->
                val totalPages = bookSessions.sumOf { it.pages }
                val totalMinutes = bookSessions.sumOf { it.minutes ?: 0 }
                val sessionCount = bookSessions.size

                Surface(
                    onClick = { onNavigateToDetail(book.id, date) },
                    shape = RoundedCornerShape(14.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Portada
                        BookCover(
                            url = book.coverUrl,
                            _title = book.title,
                            size = 64,
                            isbnFallback = book.isbn
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(book.title, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(book.author, color = theme.textMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("📄 $totalPages págs", color = theme.textDim, fontSize = 11.sp)
                                if (totalMinutes > 0) Text("⏱️ ${formatMinutes(totalMinutes)}", color = theme.textDim, fontSize = 11.sp)
                                Text(if (sessionCount == 1) "1 sesión" else "$sessionCount sesiones", color = theme.textDim, fontSize = 11.sp)
                            }
                        }
                        Icon(Icons.Default.KeyboardArrowRight, null, tint = theme.textDim, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ── HeatmapView ─────────────────────────────────────────────────────────────
@Composable
fun HeatmapView(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onNavigateToSession: (Long, String) -> Unit, onNavigateToDailySessions: (String) -> Unit = {}) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val sessions by vm.sessions.collectAsState()
    // Decisión Víctor 17-07: el mapa de calor horario vuelve a ser GRATIS (sale en el
    // Wrapped, no puede estar tras paywall). Se retira el gate del 16-07.
    // v21.41: los meses siguen el idioma de la app (string-array), no el locale del sistema
    val ctx = LocalContext.current
    val monthNames = ctx.resources.getStringArray(R.array.month_names_short).toList()
    val monthNamesFull = ctx.resources.getStringArray(R.array.month_names_full).toList()
    val dayNames = run {
        // Lunes a domingo — DateFormatSymbols devuelve D,L,M,X,J,V,S (índice 0=Domingo)
        // Reordenamos: lunes=índice 2 ... domingo=índice 1
        val raw = java.text.DateFormatSymbols(java.util.Locale.getDefault()).shortWeekdays
        listOf(raw[2], raw[3], raw[4], raw[5], raw[6], raw[7], raw[1])
            .map { it.take(1).uppercase() }
    }

    val availableYears = sessions
        .mapNotNull { it.date.takeIf { d -> d.length >= 4 }?.take(4) }
        .filter { it.matches(Regex("\\d{4}")) }
        .toSortedSet().toList().reversed()
        .ifEmpty { listOf(java.time.LocalDate.now().year.toString()) }

    var selYear by remember { mutableStateOf(availableYears.first()) }
    var selMonth by remember { mutableStateOf<Int?>(null) }
    var showYearMenu by remember { mutableStateOf(false) }
    var showMonthMenu by remember { mutableStateOf(false) }

    val pagesByDate: Map<String, Int> = remember(sessions, selYear) {
        sessions
            .filter { it.date.startsWith(selYear) }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.sumOf { it.pages } }
    }

    // Pre-calcular libros por fecha — evita O(n*31) en la composición del heatmap
    val booksByDate: Map<String, List<Long>> = remember(sessions, selYear) {
        sessions
            .filter { it.date.startsWith(selYear) }
            .groupBy { it.date }
            .mapValues { (_, list) -> list.map { it.bookId }.distinct() }
    }

    // Feedback 2.6: sesiones del período seleccionado (año y, si aplica, mes) para el
    // heatmap horario que ahora vive en esta pestaña.
    val periodSessions = remember(sessions, selYear, selMonth) {
        val mm = selMonth?.toString()?.padStart(2, '0')
        sessions.filter { s ->
            s.date.startsWith(selYear) &&
                (mm == null || (s.date.length >= 7 && s.date.substring(5, 7) == mm))
        }
    }

    // Colores cálidos: vacío → amarillo suave → naranja → rojo intenso
    fun heatColor(value: Int, max: Int): Color {
        if (max == 0 || value == 0) return Color(0xFF1E293B)
        val ratio = (value.toFloat() / max).coerceIn(0f, 1f)
        return when {
            ratio < 0.20f -> Color(0xFF78350F) // marrón oscuro
            ratio < 0.40f -> Color(0xFFB45309) // ámbar oscuro
            ratio < 0.60f -> Color(0xFFF59E0B) // ámbar
            ratio < 0.80f -> Color(0xFFEA580C) // naranja
            else           -> Color(0xFFDC2626) // rojo
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Controles: año + mes dropdown ─────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selector año
            Box {
                Surface(
                    onClick = { showYearMenu = true },
                    shape = RoundedCornerShape(8.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.border)
                ) {
                    Text("$selYear \u25be", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false },
                    modifier = Modifier.background(Color(0xFF1E293B))) {
                    availableYears.forEach { y ->
                        DropdownMenuItem(
                            text = { Text(y, color = if (y == selYear) Accent else Color(0xFFF1F5F9), fontSize = 13.sp) },
                            onClick = { selYear = y; showYearMenu = false }
                        )
                    }
                }
            }

            // Selector mes (dropdown)
            Box {
                Surface(
                    onClick = { showMonthMenu = true },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selMonth != null) Accent else theme.surface,
                    border = BorderStroke(1.dp, if (selMonth != null) Accent else theme.border)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            selMonth?.let { monthNames[it - 1] } ?: stringResource(R.string.filter_month_placeholder),
                            color = if (selMonth != null) Color.White else theme.textMuted,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.txt_f3baf2ca), color = if (selMonth != null) Color.White else theme.textMuted, fontSize = 10.sp)
                    }
                }
                DropdownMenu(expanded = showMonthMenu, onDismissRequest = { showMonthMenu = false },
                    modifier = Modifier.background(Color(0xFF1E293B))) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.txt_e8b7bada), color = if (selMonth == null) Accent else Color(0xFFF1F5F9), fontSize = 13.sp) },
                        onClick = { selMonth = null; showMonthMenu = false }
                    )
                    (1..12).forEach { m ->
                        val mm = m.toString().padStart(2, '0')
                        val hasDat = pagesByDate.keys.any { it.length >= 7 && it.substring(5, 7) == mm }
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(monthNamesFull[m - 1],
                                        color = if (selMonth == m) Accent else if (hasDat) Color(0xFFF1F5F9) else Color(0xFF64748B),
                                        fontSize = 13.sp)
                                    if (hasDat) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(Modifier.size(6.dp).background(Color(0xFFF59E0B), androidx.compose.foundation.shape.CircleShape))
                                    }
                                }
                            },
                            onClick = { selMonth = m; showMonthMenu = false }
                        )
                    }
                }
            }

            // Leyenda compacta
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.txt_ae0dca8d), color = theme.textDim, fontSize = 9.sp)
                listOf(Color(0xFF78350F), Color(0xFFB45309), Color(0xFFF59E0B), Color(0xFFEA580C), Color(0xFFDC2626)).forEach { c ->
                    Box(Modifier.size(10.dp).background(c, RoundedCornerShape(2.dp)))
                }
                Text(stringResource(R.string.txt_f1caef3b), color = theme.textDim, fontSize = 9.sp)
            }
        }

        val currentMonth = selMonth
        if (currentMonth == null) {
            // ── Vista anual: 12 celdas ──────────────────────────────────────
            val monthTotals = (1..12).map { m ->
                val mm = m.toString().padStart(2, '0')
                pagesByDate.entries.filter { it.key.length >= 7 && it.key.substring(5, 7) == mm }.sumOf { it.value }
            }
            val maxMonthly = monthTotals.maxOrNull()?.coerceAtLeast(1) ?: 1

            // Feedback 2.6: string localizado (antes hardcodeado en inglés)
            Text(stringResource(R.string.heatmap_tap_month, selYear), color = theme.textDim, fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(12) { idx ->
                    val pages = monthTotals[idx]
                    Surface(
                        onClick = { selMonth = idx + 1 },
                        shape = RoundedCornerShape(12.dp),
                        color = heatColor(pages, maxMonthly),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF))
                    ) {
                        Column(
                            Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(monthNames[idx], color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(if (pages > 0) "$pages" else "\u2014",
                                color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (pages > 0) Text(stringResource(R.string.txt_df94c6d3), color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
                        }
                    }
                }
                // Feedback 2.6: heatmap horario del per\u00edodo seleccionado, a ancho completo
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        HourlyHeatmapCard(periodSessions, theme)
                    }
                }
            }
        } else {
            // ── Vista mensual: grid 7 columnas ──────────────────────────────
            val mm = currentMonth.toString().padStart(2, '0')
            val year = selYear.toIntOrNull() ?: java.time.LocalDate.now().year
            val daysInMonth = java.time.YearMonth.of(year, currentMonth).lengthOfMonth()
            val firstDow = java.time.LocalDate.of(year, currentMonth, 1).dayOfWeek.value // 1=Mon..7=Sun

            val dayTotals = (1..daysInMonth).map { d ->
                val key = "$selYear-$mm-${d.toString().padStart(2, '0')}"
                pagesByDate[key] ?: 0
            }
            val maxDaily = dayTotals.maxOrNull()?.coerceAtLeast(1) ?: 1

            Text(stringResource(R.string.heatmap_month_label, "${monthNamesFull[currentMonth - 1]} $selYear"),
                color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))

            // Cabecera días semana
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                dayNames.forEach { d ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(d, color = theme.textDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(firstDow - 1) {
                    Box(Modifier.aspectRatio(1f))
                }
                items(daysInMonth) { idx ->
                    val day = idx + 1
                    val pages = dayTotals[idx]
                    val dateKey = "$selYear-$mm-${day.toString().padStart(2, '0')}"
                    val booksForDay = booksByDate[dateKey] ?: emptyList()
                    Surface(
                        onClick = {
                            if (pages > 0 && booksForDay.isNotEmpty()) {
                                if (booksForDay.size == 1) {
                                    onNavigateToSession(booksForDay.first(), dateKey)
                                } else {
                                    onNavigateToDailySessions(dateKey)
                                }
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = heatColor(pages, maxDaily),
                        border = BorderStroke(0.5.dp, Color(0x22FFFFFF)),
                        modifier = Modifier.aspectRatio(1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$day", color = Color.White.copy(alpha = if (pages > 0) 0.95f else 0.4f),
                                    fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                if (pages > 0) Text("$pages", color = Color.White.copy(alpha = 0.75f), fontSize = 8.sp)
                            }
                        }
                    }
                }
                // Feedback 2.6: heatmap horario del mes seleccionado, a ancho completo
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        HourlyHeatmapCard(periodSessions, theme)
                    }
                }
            }
        }
    }
}


// Gráficas adicionales dentro de StatsScreen
// Se accede mediante toggle "Vista" en la barra de filtros.

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsChartsView(
    vm: BooksViewModel,
    theme: Theme,
    filterGenre: String?,
    onGenreChange: (String?) -> Unit,
    allGenres: List<String>,
    filterAuthor: String?,
    onAuthorChange: (String?) -> Unit,
    allAuthors: List<String>
) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val sessions by vm.sessions.collectAsState()
    val context = LocalContext.current
    // Base: TODOS los libros aplanados por edición (v18.3) — cada edición cuenta como una entrada.
    // Para libros con varias ediciones, cada una aparece con su título, páginas, idioma y portada
    // propios. Libros sin ediciones múltiples se devuelven como antes.
    val filtered = vm.booksByEdition()
        .let { list -> if (filterGenre != null) list.filter { it.genres.contains(filterGenre) } else list }
        .let { list -> if (filterAuthor != null) list.filter { it.author == filterAuthor } else list }

    // Filtros locales
    var ascending by remember { mutableStateOf(false) }
    var selYear by remember { mutableStateOf<String?>(null) }   // "2026" o null = todos los años
    var selMonth by remember { mutableStateOf<Int?>(null) }     // 1..12 o null = todos los meses
    val statusFilters = remember { mutableStateListOf<BookStatus>() }  // multiselección, máx 2
    var hideFunctionalless by remember { mutableStateOf(false) }

    val booksForCharts = filtered
        .let { list -> if (statusFilters.isNotEmpty()) list.filter { it.status in statusFilters } else list }
        .let { list -> if (hideFunctionalless) list.filter { it.firstFunctionalPage != null || it.lastFunctionalPage != null } else list }

    val sessionsByBook = sessions.groupBy { it.bookId }

    // ¿Una fecha "yyyy-MM-dd" cae en el período seleccionado (año y/o mes)?
    fun dateInPeriod(d: String?): Boolean {
        if (d == null || d.length < 4) return false
        selYear?.let { if (!d.startsWith(it)) return false }
        selMonth?.let { m -> val mm = m.toString().padStart(2, '0'); if (d.length < 7 || d.substring(5, 7) != mm) return false }
        return true
    }
    // Libros que pertenecen al período: por fecha de inicio/fin o por tener alguna sesión en él.
    // Los donuts (estado, género, idioma, autor) usan esto para reaccionar a año/mes.
    val booksInPeriod = if (selYear == null && selMonth == null) booksForCharts
        else booksForCharts.filter { b ->
            dateInPeriod(b.endDate) || dateInPeriod(b.startDate) ||
            (sessionsByBook[b.id]?.any { dateInPeriod(it.date) } == true)
        }

    // Sesiones de los libros filtrados + filtro de período (año y mes combinables)
    val filteredBookIds = booksForCharts.map { it.id }.toSet()
    val filteredSessions = sessions
        .filter { it.bookId in filteredBookIds }
        .let { list ->
            var l = list
            selYear?.let { y -> l = l.filter { it.date.startsWith(y) } }
            selMonth?.let { m ->
                val mm = m.toString().padStart(2, '0')
                l = l.filter { it.date.length >= 7 && it.date.substring(5, 7) == mm }
            }
            l
        }

    // Años disponibles: derivados de TODOS los libros (filtro de género/autor), nunca del
    // filtro de estado ni del período — así el dropdown no se queda solo con 2026.
    val availableYears = run {
        val ys = mutableSetOf<String>()
        filtered.forEach { b ->
            b.startDate?.takeIf { it.length >= 4 }?.let { ys.add(it.take(4)) }
            b.endDate?.takeIf { it.length >= 4 }?.let { ys.add(it.take(4)) }
            sessionsByBook[b.id]?.forEach { if (it.date.length >= 4) ys.add(it.date.take(4)) }
        }
        ys.filter { it.matches(Regex("\\d{4}")) }.toSortedSet().toList().reversed()
    }
    // v21.41: meses según idioma de la app
    val statsCtx = LocalContext.current
    val monthNames = statsCtx.resources.getStringArray(R.array.month_names_short).toList()


    val streakDays = run {
        val sessionDates = filteredSessions.map { it.date }.toSortedSet().toList()
        var maxStreak = 0; var curStreak = 0; var prevDate: java.util.Calendar? = null
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        for (dateStr in sessionDates) {
            val cal = java.util.Calendar.getInstance().apply { time = sdf.parse(dateStr) ?: return@apply }
            if (prevDate == null) { curStreak = 1 } else {
                val prev = prevDate!!
                prev.add(java.util.Calendar.DAY_OF_YEAR, 1)
                curStreak = if (cal.get(java.util.Calendar.DAY_OF_YEAR) == prev.get(java.util.Calendar.DAY_OF_YEAR) && cal.get(java.util.Calendar.YEAR) == prev.get(java.util.Calendar.YEAR)) curStreak + 1 else 1
            }
            if (curStreak > maxStreak) maxStreak = curStreak
            prevDate = java.util.Calendar.getInstance().apply { time = sdf.parse(dateStr) ?: return@apply }
        }
        maxStreak
    }

    // Ratio de terminados — usa booksInPeriod para respetar filtros año/mes
    val showRatioFinished = statusFilters.isEmpty() || BookStatus.FINISHED in statusFilters
    val ratioFinished = run {
        val base = booksInPeriod
        val started = base.count { it.status != BookStatus.PENDING }
        val finished = base.count { it.status == BookStatus.FINISHED }
        if (started > 0) finished.toFloat() / started else 0f
    }

    val avgSessionPages = if (filteredSessions.isNotEmpty()) filteredSessions.map { it.pages }.average() else 0.0
    val avgSessionMins = filteredSessions.mapNotNull { it.minutes }.let { if (it.isNotEmpty()) it.average() else 0.0 }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ── Filtros ────────────────────────────────────────────────────────────
        Text(stringResource(R.string.txt_9042f197), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.txt_26afc116), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                    listOf(false to "↓ Mayor", true to "↑ Menor").forEach { (asc, lbl) ->
                        val sel = ascending == asc
                        Surface(onClick = { ascending = asc }, shape = RoundedCornerShape(8.dp), color = if (sel) Accent else theme.bgMid, border = BorderStroke(1.dp, if (sel) Accent else theme.border)) {
                            Text(lbl, color = if (sel) Color.White else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
                // Período: selector de Año y de Mes, combinables
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.txt_7d6c2883), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                    // Dropdown de Año
                    var showYearMenu by remember { mutableStateOf(false) }
                    Box {
                        Surface(onClick = { showYearMenu = true }, shape = RoundedCornerShape(8.dp), color = if (selYear != null) Accent.copy(alpha = 0.15f) else theme.bgMid, border = BorderStroke(1.dp, if (selYear != null) Accent else theme.border)) {
                            Text("${selYear ?: stringResource(R.string.filter_year_placeholder)} ▾", color = if (selYear != null) Accent else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.txt_32630ca9), color = if (selYear == null) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { selYear = null; showYearMenu = false })
                            availableYears.forEach { y ->
                                DropdownMenuItem(text = { Text(y, color = if (selYear == y) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { selYear = y; showYearMenu = false })
                            }
                        }
                    }
                    // Dropdown de Mes
                    var showMonthMenu by remember { mutableStateOf(false) }
                    Box {
                        Surface(onClick = { showMonthMenu = true }, shape = RoundedCornerShape(8.dp), color = if (selMonth != null) Accent.copy(alpha = 0.15f) else theme.bgMid, border = BorderStroke(1.dp, if (selMonth != null) Accent else theme.border)) {
                            Text("${selMonth?.let { monthNames[it - 1] } ?: stringResource(R.string.filter_month_placeholder)} ▾", color = if (selMonth != null) Accent else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        DropdownMenu(expanded = showMonthMenu, onDismissRequest = { showMonthMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.txt_32630ca9), color = if (selMonth == null) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { selMonth = null; showMonthMenu = false })
                            (1..12).forEach { m ->
                                DropdownMenuItem(text = { Text(monthNames[m - 1], color = if (selMonth == m) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { selMonth = m; showMonthMenu = false })
                            }
                        }
                    }
                }
                // Estados — multiselección (máx 2), en varias líneas con FlowRow
                Row(verticalAlignment = Alignment.Top) {
                    Text(stringResource(R.string.txt_e7396239), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.width(60.dp).padding(top = 5.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(onClick = { statusFilters.clear() }, shape = RoundedCornerShape(8.dp), color = if (statusFilters.isEmpty()) Accent else theme.bgMid, border = BorderStroke(1.dp, if (statusFilters.isEmpty()) Accent else theme.border)) {
                            Text(stringResource(R.string.txt_32630ca9), color = if (statusFilters.isEmpty()) Color.White else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                        SHELF_ORDER.forEach { s ->
                            val sel = s in statusFilters
                            Surface(
                                onClick = {
                                    if (sel) statusFilters.remove(s)
                                    else {
                                        if (statusFilters.size >= 2) statusFilters.removeAt(0)
                                        statusFilters.add(s)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) statusColor(s).copy(alpha = 0.9f) else theme.bgMid,
                                border = BorderStroke(1.dp, if (sel) statusColor(s) else theme.border)
                            ) {
                                Text(statusLabel(s), color = if (sel) Color.White else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hideFunctionalless, onCheckedChange = { hideFunctionalless = it }, colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = theme.border), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.txt_73fa4a4d), color = theme.textMuted, fontSize = 12.sp)
                }
                // Género y Autor (mismo bloque de filtros, dropdowns como en la lista)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.txt_d086767f), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                    var showGenreMenu by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        Surface(onClick = { showGenreMenu = true }, shape = RoundedCornerShape(8.dp), color = if (filterGenre != null) Accent.copy(alpha = 0.15f) else theme.bgMid, border = BorderStroke(1.dp, if (filterGenre != null) Accent else theme.border), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Category, null, modifier = Modifier.size(13.dp), tint = if (filterGenre != null) Accent else theme.textMuted)
                                Spacer(Modifier.width(4.dp))
                                Text("${filterGenre?.let { displayGenre(it) } ?: stringResource(R.string.txt_98f7ba16)} ▾", color = if (filterGenre != null) Accent else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        DropdownMenu(expanded = showGenreMenu, onDismissRequest = { showGenreMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.txt_8afc8680), color = if (filterGenre == null) Accent else theme.textMain, fontSize = 13.sp) }, onClick = { onGenreChange(null); showGenreMenu = false })
                            allGenres.forEach { g ->
                                DropdownMenuItem(text = { Text(displayGenre(g), color = if (filterGenre == g) Accent else theme.textMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onGenreChange(g); showGenreMenu = false })
                            }
                        }
                    }
                    var showAuthorMenu by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        Surface(onClick = { showAuthorMenu = true }, shape = RoundedCornerShape(8.dp), color = if (filterAuthor != null) Accent2.copy(alpha = 0.15f) else theme.bgMid, border = BorderStroke(1.dp, if (filterAuthor != null) Accent2 else theme.border), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(13.dp), tint = if (filterAuthor != null) Accent2 else theme.textMuted)
                                Spacer(Modifier.width(4.dp))
                                Text("${filterAuthor ?: stringResource(R.string.txt_c481b00a)} ▾", color = if (filterAuthor != null) Accent2 else theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        DropdownMenu(expanded = showAuthorMenu, onDismissRequest = { showAuthorMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.txt_426f7ea7), color = if (filterAuthor == null) Accent2 else theme.textMain, fontSize = 13.sp) }, onClick = { onAuthorChange(null); showAuthorMenu = false })
                            allAuthors.forEach { a ->
                                DropdownMenuItem(text = { Text(a, color = if (filterAuthor == a) Accent2 else theme.textMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { onAuthorChange(a); showAuthorMenu = false })
                            }
                        }
                    }
                }
            }
        }

        // ── Empty state cuando hay filtro de período pero ningún libro ────────
        if ((selYear != null || selMonth != null) && booksInPeriod.isEmpty()) {
            val periodLbl = buildString {
                if (selMonth != null) { append(monthNames[selMonth!! - 1]); if (selYear != null) append(" ") }
                if (selYear != null) append(selYear)
            }
            Surface(shape = RoundedCornerShape(14.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📊", fontSize = 38.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.stats_no_data, periodLbl), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.txt_158a98d7), color = theme.textDim, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            return@Column
        }

        // ── Indicadores clave ──────────────────────────────────────────────────
        Text(stringResource(R.string.stats_key_indicators, booksForCharts.size), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("$streakDays ${stringResource(R.string.word_days)}", stringResource(R.string.pill_racha_maxima), Modifier.weight(1f), theme, highlight = true, highlightColor = Amber)
            if (showRatioFinished) StatBox("${String.format("%.0f%%", ratioFinished * 100)}", stringResource(R.string.pill_libros_terminados), Modifier.weight(1f), theme, highlight = true, highlightColor = Green)
            StatBox(if (filteredSessions.isNotEmpty()) String.format("%.1f p", avgSessionPages) else "—", stringResource(R.string.pill_media_pags_ses), Modifier.weight(1f), theme, highlight = true, highlightColor = Accent)
            StatBox(if (avgSessionMins > 0) String.format("%.0f min", avgSessionMins) else "—", stringResource(R.string.pill_media_mins_ses), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
        }

        // ── (El gráfico de barras "Páginas por período" se muestra al FINAL de la sección) ──

        // ── Géneros (donut + barras) — SIEMPRE primero. Cada libro cuenta UNA vez por su género principal ──
        val genreList = booksInPeriod
            .mapNotNull { b -> (b.genres.firstOrNull()?.ifBlank { null }) ?: b.genre.ifBlank { null } }
            .groupBy { it }.mapValues { it.value.size }
            .entries.let { if (ascending) it.sortedBy { e -> e.value } else it.sortedByDescending { e -> e.value } }.take(6)
        if (genreList.isNotEmpty()) {
            val gColors = listOf(Accent, Green, Sky, Amber, Red, Color(0xFF8B5CF6))
            val total = genreList.sumOf { it.value }
            ChartCard(title = stringResource(R.string.chart_genre_title, total), theme = theme) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(genreList.map { it.value }, gColors, Modifier.size(100.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        genreList.forEachIndexed { i, entry ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(gColors[i % gColors.size]))
                                Text("${displayGenre(entry.key)}: ${entry.value} (${if (total > 0) entry.value * 100 / total else 0}%)", color = theme.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (genreList.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalBarChart(genreList.map { Triple(displayGenre(it.key), it.value, Accent) }, theme, max = genreList.maxOf { it.value }.coerceAtLeast(1))
                }
            }
        }

        // ── Estado (donut) — refleja período; oculto en años anteriores a 2026 ──
        // (puntos 1 y 4: antes de 2026 solo hay libros "Leído", la distribución no aporta)
        val showStatusChart = (selYear == null || selYear!! >= "2026")
        if (showStatusChart && booksInPeriod.isNotEmpty()) {
            val counts = mapOf(
                stringResource(R.string.chart_status_leidos) to booksInPeriod.count { it.status == BookStatus.FINISHED },
                stringResource(R.string.chart_status_leyendo) to booksInPeriod.count { it.status == BookStatus.READING },
                stringResource(R.string.chart_status_releyendo) to booksInPeriod.count { it.status == BookStatus.REREADING },
                stringResource(R.string.chart_status_pendiente) to booksInPeriod.count { it.status == BookStatus.PENDING },
                stringResource(R.string.chart_status_abandonado) to booksInPeriod.count { it.status == BookStatus.DROPPED }
            ).filter { it.value > 0 }
            if (counts.isNotEmpty()) {
                val stateColors = listOf(Green, Sky, Color(0xFF06B6D4), Amber, Red)
                val total = counts.values.sum()
                ChartCard(title = stringResource(R.string.chart_distribution_title, total), theme = theme) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(counts.values.toList(), stateColors, Modifier.size(100.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            counts.entries.forEachIndexed { i, (lbl, value) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(stateColors[i % stateColors.size]))
                                    Text("$lbl: $value (${if (total > 0) value * 100 / total else 0}%)", color = theme.textMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Idioma (donut) — "Global"/"Edición principal" NO es idioma: se excluye ──
        val langList = booksInPeriod.mapNotNull { book ->
            val lbl = book.editions.firstOrNull { it.isActive }?.languageLabel
            when {
                lbl == null -> null
                lbl.equals("Edición principal", ignoreCase = true) -> null  // Global no es un idioma
                lbl.equals("Global", ignoreCase = true) -> null
                else -> lbl
            }
        }.groupBy { it }.mapValues { it.value.size }
            .entries.let { if (ascending) it.sortedBy { e -> e.value } else it.sortedByDescending { e -> e.value } }
        if (langList.isNotEmpty()) {
            val lColors = listOf(Color(0xFF3B82F6), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF8B5CF6), Color(0xFFEF4444))
            val total = langList.sumOf { it.value }
            ChartCard(title = stringResource(R.string.stats_by_language, total), theme = theme) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(langList.map { it.value }, lColors, Modifier.size(100.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        langList.forEachIndexed { i, entry ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(lColors[i % lColors.size]))
                                Text("${entry.key}: ${entry.value} (${if (total > 0) entry.value * 100 / total else 0}%)", color = theme.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // ── Autores (donut + barras) ────────────────────────────────────────────
        val authorList = booksInPeriod.filter { it.author.isNotBlank() }
            .groupBy { it.author }.mapValues { it.value.size }
            .entries.let { if (ascending) it.sortedBy { e -> e.value } else it.sortedByDescending { e -> e.value } }.take(6)
        if (authorList.isNotEmpty()) {
            val aColors = listOf(Color(0xFFEC4899), Accent, Sky, Green, Amber, Color(0xFF8B5CF6))
            val total = authorList.sumOf { it.value }
            ChartCard(title = stringResource(R.string.stats_top_authors, total), theme = theme) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(authorList.map { it.value }, aColors, Modifier.size(100.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        authorList.forEachIndexed { i, entry ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(aColors[i % aColors.size]))
                                Text("${entry.key.split(" ").last()}: ${entry.value} (${if (total > 0) entry.value * 100 / total else 0}%)", color = theme.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (authorList.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalBarChart(authorList.map { Triple(it.key.split(" ").last(), it.value, Color(0xFFEC4899)) }, theme)
                }
            }
        }

        // ── Páginas por período (gráfico de barras AL FINAL de la sección) ───────
        // Granularidad adaptativa: solo mes → por día; solo año → por mes; nada → últimos 12 meses.
        val groupKey: (String) -> String = { date ->
            when {
                selMonth != null -> date            // mes concreto → barras por día (fecha completa)
                selYear != null -> date.take(7)     // año concreto sin mes → barras por mes
                else -> date.take(7)                // sin filtro → por mes (últimos 12)
            }
        }
        val rawPeriodData = filteredSessions.groupBy { groupKey(it.date) }
            .mapValues { it.value.sumOf { s -> s.pages } }
            .entries.sortedBy { it.key }
        val periodData = if (ascending) rawPeriodData.sortedBy { it.value } else rawPeriodData.sortedByDescending { it.value }
        val displayPeriod = when {
            ascending -> periodData.take(12)
            selYear == null && selMonth == null -> rawPeriodData.takeLast(12)
            else -> rawPeriodData
        }
        if (displayPeriod.isNotEmpty()) {
            val chartTitleStr = if (selMonth != null) stringResource(R.string.chart_pages_per_day) else stringResource(R.string.chart_pages_per_month)
            // Período mostrado (mm-aa) a la derecha del título cuando hay año y/o mes seleccionados
            val periodLabel = when {
                selYear != null && selMonth != null -> "${selMonth!!.toString().padStart(2, '0')}-${selYear!!.takeLast(2)}"
                selYear != null -> selYear!!
                selMonth != null -> monthNames[selMonth!! - 1]
                else -> ""
            }
            val totalPagesCount = displayPeriod.sumOf { it.value }
            val totalPagesWord = stringResource(R.string.txt_47bcdf9a)
            val fullTitle = (if (periodLabel.isNotBlank()) "$chartTitleStr $periodLabel" else chartTitleStr) +
                " ($totalPagesCount $totalPagesWord)"
            ChartCard(title = fullTitle, theme = theme) {
                val maxP = displayPeriod.maxOf { it.value }.coerceAtLeast(1)
                val items = displayPeriod.map { entry ->
                    val key = entry.key
                    val label = when (key.length) {
                        10 -> key.takeLast(2)                                           // "yyyy-MM-dd" → "dd"
                        7  -> { val p = key.split("-"); "${p[1]}-${p[0].takeLast(2)}" }  // "yyyy-MM" → "MM-AA"
                        else -> key
                    }
                    label to entry.value
                }
                VerticalBarChartWithValues(items, maxP, theme)
            }
        }

        // ── Exportar estadísticas ─────────────────────────────────────────────
        val scope = rememberCoroutineScope()
        var exportError by remember { mutableStateOf<String?>(null) }
        fun exportStats(asCsv: Boolean) {
            scope.launch {
                try {
                    val (fileName, mime, content) = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        if (asCsv) Triple("estadisticas_lecturameter.csv", "text/csv", buildStatsCSV(vm))
                        else Triple("estadisticas_lecturameter.json", "application/json", buildStatsJSON(vm))
                    }
                    val file = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        java.io.File(context.cacheDir, fileName).apply { writeText(content) }
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    // startActivity siempre en el hilo principal — en IO crashea en MIUI
                    context.startActivity(android.content.Intent.createChooser(intent, "Exportar estadísticas"))
                    exportError = null
                } catch (e: Exception) {
                    exportError = "❌ Error al exportar: ${e.message}"
                }
            }
        }
        Surface(shape = RoundedCornerShape(14.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.txt_def36a08), color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportStats(asCsv = true) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Green), colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)
                    ) { Text(stringResource(R.string.txt_cc8d68c5), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { exportStats(asCsv = false) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Amber), colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber)
                    ) { Text(stringResource(R.string.txt_0ecd11c1), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                exportError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Red, fontSize = 12.sp)
                }
            }
        }
    }
}


@Composable
fun ChartCard(title: String, theme: Theme, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
fun HorizontalBarChart(items: List<Triple<String, Int, Color>>, theme: Theme, max: Int = items.maxOfOrNull { it.second } ?: 1) {
    val maxVal = max.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, value, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(label, color = theme.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(90.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(theme.bgMid)) {
                    val fraction = value.toFloat() / maxVal
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.8f)))
                }
                Spacer(Modifier.width(8.dp))
                Text("$value", color = theme.textMuted, fontSize = 11.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
fun VerticalBarChart(items: List<Pair<String, Int>>, max: Int, theme: Theme) {
    val maxVal = max.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        items.forEach { (label, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val fraction = value.toFloat() / maxVal
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(Accent.copy(alpha = 0.75f)))
                }
                Spacer(Modifier.height(2.dp))
                Text(label, color = theme.textDim, fontSize = 7.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun VerticalBarChartWithValues(items: List<Pair<String, Int>>, max: Int, theme: Theme) {
    val maxVal = max.coerceAtLeast(1)
    var selectedIdx by remember { mutableStateOf<Int?>(null) }
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        items.forEachIndexed { idx, (label, value) ->
            val isSelected = selectedIdx == idx
            Column(
                Modifier.weight(1f).clickable { selectedIdx = if (isSelected) null else idx },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text("$value", color = if (isSelected) Accent else theme.textMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(1.dp))
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val fraction = value.toFloat() / maxVal
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fraction.coerceAtLeast(0.02f)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(if (isSelected) Accent else Accent.copy(alpha = 0.65f)))
                }
                Spacer(Modifier.height(2.dp))
                Text(label, color = if (isSelected) Accent else theme.textDim, fontSize = 7.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    if (selectedIdx != null && selectedIdx!! < items.size) {
        val (lbl, v) = items[selectedIdx!!]
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.chart_bar_label, lbl, v), color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DonutChart(values: List<Int>, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = values.sum().toFloat().coerceAtLeast(1f)
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.18f
        val radius = (size.minDimension - stroke) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        var startAngle = -90f
        values.forEachIndexed { i, v ->
            val sweep = v / total * 360f
            drawArc(
                color = colors[i % colors.size],
                startAngle = startAngle,
                sweepAngle = (sweep - 1f).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(cx - radius, cy - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

private fun buildStatsCSV(vm: BooksViewModel): String {
    val sb = StringBuilder()
    sb.appendLine("Título,Autor,Páginas,Género,Puntuación,Inicio,Fin,Días,Págs/día")
    vm.books.value.filter { it.status == BookStatus.FINISHED }.forEach { b ->
        val days = if (b.startDate != null && b.endDate != null) daysBetween(b.startDate, b.endDate) else 0
        val ppd = if (days > 0) String.format("%.1f", b.pages.toDouble() / days) else ""
        sb.appendLine("\"${b.title}\",\"${b.author}\",${b.pages},\"${b.genres.joinToString("; ")}\",${b.rating},${b.startDate ?: ""},${b.endDate ?: ""},$days,$ppd")
    }
    return sb.toString()
}

private fun buildStatsJSON(vm: BooksViewModel): String {
    val arr = org.json.JSONArray()
    vm.books.value.filter { it.status == BookStatus.FINISHED }.forEach { b ->
        arr.put(org.json.JSONObject().apply {
            put("title", b.title)
            put("author", b.author)
            put("pages", b.pages)
            put("genres", org.json.JSONArray(b.genres))
            put("rating", b.rating)
            put("startDate", b.startDate ?: "")
            put("endDate", b.endDate ?: "")
        })
    }
    return arr.toString(2)
}

