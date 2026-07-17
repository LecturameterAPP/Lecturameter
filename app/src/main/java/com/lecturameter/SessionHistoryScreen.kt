package com.lecturameter

// SessionHistoryScreen (drawer lateral) + componentes de historial y AutoSizeText.
// Extraido de MainActivity.kt el 15-07-2026 (ruptura del monolito, sin cambios funcionales).


import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import java.util.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import com.lecturameter.model.*
import com.lecturameter.utils.*
import androidx.navigation.compose.composable

// ── SessionHistoryScreen (drawer lateral) ─────────────────────────────────────

/** Clave compuesta para agrupar sesiones por libro + idioma de edición (v12). */
private data class BookLangKey(val bookId: Long, val language: String)

@Composable
fun SessionHistoryScreen(vm: BooksViewModel, theme: Theme, onClose: () -> Unit, onDetail: (Long) -> Unit = {}, showCloseTip: Boolean = false, onDismissCloseTip: () -> Unit = {}) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("lecturameter", android.content.Context.MODE_PRIVATE)
    val acc = accentForTheme(theme)

    var newestFirst by remember { mutableStateOf(vm.savedSessionNewestFirst) }
    var searchQuery by remember { mutableStateOf("") }

    val booksWithSessions = remember(sessions, books) {
        // Agrupar por (bookId, language) — misma lengua = mismo "libro", distinta lengua = libro separado
        val bookEditionMap = books.associate { book ->
            book.id to book.editions.associate { it.id to it.language }
        }
        val keys = sessions.mapNotNull { s ->
            val book = books.find { it.id == s.bookId } ?: return@mapNotNull null
            val edMap = bookEditionMap[s.bookId] ?: emptyMap()
            // TAREA 4 (B-034): unificado con sessionsForBookAndLanguage vía vm.activeLanguage
            val lang = s.editionId?.let { edMap[it] } ?: vm.activeLanguage(book.id)
            BookLangKey(s.bookId, lang)
        }.toSet()
        keys.mapNotNull { key ->
            val book = books.find { it.id == key.bookId } ?: return@mapNotNull null
            book to key.language
        }.sortedBy { (book, _) -> book.title }
    }

    // Numeración FIJA por (bookId, lang): #1 = sesión más antigua de esa combinación
    val fixedNumberMap = remember(sessions, books) {
        val map = mutableMapOf<Long, Int>()
        val bookEditionMap = books.associate { book ->
            book.id to book.editions.associate { it.id to it.language }
        }
        // Agrupar por (bookId, language)
        val byBookLang = sessions.groupBy { s ->
            val edMap = bookEditionMap[s.bookId] ?: emptyMap()
            // TAREA 4 (B-034): unificado con sessionsForBookAndLanguage vía vm.activeLanguage
            val lang = s.editionId?.let { edMap[it] } ?: vm.activeLanguage(s.bookId)
            "${s.bookId}_$lang"
        }
        for ((_, sessions) in byBookLang) {
            sessions.sortedBy { it.date + it.id.toString().padStart(20, '0') }
                .forEachIndexed { idx, s -> map[s.id] = idx + 1 }
        }
        map
    }

    // Filtrar sesiones según búsqueda: "#3" → número fijo, "título" → libro exacto, texto libre → nota
    val filteredSessions = remember(sessions, searchQuery, fixedNumberMap) {
        val q = searchQuery.trim()
        if (q.isBlank()) sessions
        else if (q.startsWith("#")) {
            val num = q.removePrefix("#").toIntOrNull()
            if (num != null) sessions.filter { fixedNumberMap[it.id] == num }
            else sessions
        } else if (q.length > 2 && q.startsWith("\"") && q.endsWith("\"")) {
            val exactTitle = q.removeSurrounding("\"").trim()
            val matchingBookIds = books.filter { it.title.equals(exactTitle, ignoreCase = true) }.map { it.id }.toSet()
            sessions.filter { it.bookId in matchingBookIds }
        } else {
            sessions.filter { it.note.contains(q, ignoreCase = true) }
        }
    }

    // (bookId, lang) que tienen sesiones tras el filtro
    val bookEditionMapForFilter = books.associate { book ->
        book.id to book.editions.associate { it.id to it.language }
    }
    val filteredBooksWithSessions = remember(filteredSessions, booksWithSessions) {
        val filteredKeys = filteredSessions.map { s ->
            val edMap = bookEditionMapForFilter[s.bookId] ?: emptyMap()
            // TAREA 4 (B-034): unificado con sessionsForBookAndLanguage vía vm.activeLanguage
            val lang = s.editionId?.let { edMap[it] } ?: vm.activeLanguage(s.bookId)
            "${s.bookId}_$lang"
        }.toSet()
        booksWithSessions.filter { (book, lang) -> "${book.id}_$lang" in filteredKeys }
    }

    // Clave de expansión: "bookId_language"
    val expanded = remember(booksWithSessions) {
        mutableStateMapOf<String, Boolean>().also { map ->
            booksWithSessions.forEach { (book, lang) -> map["${book.id}_$lang"] = false }
        }
    }
    // v20.5: expansión de sub-ciclos en historial: "bookId_language_readingIdx"
    val cycleExpanded = remember { mutableStateMapOf<String, Boolean>() }

    // Auto-expandir cuando hay búsqueda activa; al limpiar la búsqueda, volver a colapsar.
    // Fix v2.4: antes quedaba TODO expandido para siempre tras cualquier búsqueda
    // (visible sobre todo en tablet, MD §7 «historial siempre expandido»).
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            filteredBooksWithSessions.forEach { (book, lang) -> expanded["${book.id}_$lang"] = true }
        } else {
            expanded.keys.forEach { expanded[it] = false }
        }
    }

    val totalPages = sessions.sumOf { it.pages }
    val totalMins  = sessions.mapNotNull { it.minutes }.sum()

    Column(Modifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                // Feedback 13-07: bgDeep ya nunca es Transparent (== bgDark si el tema no
                // define deep) — en ese caso la cabecera cae a bgMid como siempre
                .background(Brush.verticalGradient(listOf(theme.bgDark, if (theme.bgDeep != theme.bgDark) theme.bgDeep else theme.bgMid)))
                // Feedback 13-07 (3): el panel ya no vive bajo la status bar (drawer) —
                // el top de 40dp dejaba un hueco; alineado con el icono 📜 del rail
                .padding(top = 14.dp, bottom = 14.dp, start = 20.dp, end = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📖", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.txt_beea2815),
                        color = theme.textMain, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                    HistoryStat("${sessions.size}", stringResource(R.string.history_stat_sessions), Modifier.weight(1f), theme, valueColor = acc)
                    HistoryStat(if (totalMins > 0) fmtMinutes(totalMins) else "—", stringResource(R.string.history_stat_total_time), Modifier.weight(1f), theme, valueColor = Sky)
                    HistoryStat("$totalPages", stringResource(R.string.pill_paginas_totales), Modifier.weight(1f), theme, valueColor = Green)
                    HistoryStat(if (totalMins > 0) String.format("%.1f", totalPages.toDouble() / totalMins) else "—", stringResource(R.string.pill_pags_min), Modifier.weight(1f), theme, valueColor = Green)
                    // v2.5: media global de págs/día — en la misma fila
                    val historyGlobalMean = remember(books, sessions) { computeOutliers(books, sessions).first }
                    if (historyGlobalMean > 0.0) {
                        HistoryStat(String.format("%.1f", historyGlobalMean), stringResource(R.string.history_stat_global_ppd), Modifier.weight(1f), theme, valueColor = Amber)
                    }
                }
            }
        }

        HorizontalDivider(color = theme.border, thickness = 0.5.dp)

        // Fase 6.1 (D-008, T2): primera apertura — cómo se cierra el panel.
        // En el flujo del layout (no overlay): no tapa las pills ni el orden.
        if (showCloseTip) {
            TipCard(
                stringResource(R.string.tip_history_title), stringResource(R.string.tip_history_body), theme,
                onDismiss = onDismissCloseTip,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // ── Orden + Búsqueda ──────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .background(theme.bgMid)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Fila de orden
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.txt_26afc116), color = theme.textDim, fontSize = 12.sp)
                Surface(
                    onClick = {
                        val newVal = !newestFirst
                        newestFirst = newVal
                        vm.savedSessionNewestFirst = newVal
                        prefs.edit().putBoolean("session_newest_first", newVal).apply()
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (newestFirst) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            null, tint = acc, modifier = Modifier.size(15.dp)
                        )
                        Text(
                            if (newestFirst) stringResource(R.string.sort_newest_first) else stringResource(R.string.sort_oldest_first),
                            color = acc, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            // Barra de búsqueda
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, if (searchQuery.isNotBlank()) acc else theme.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔍", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = theme.textMain,
                            fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default
                        ),
                        cursorBrush = SolidColor(acc),
                        modifier = Modifier.weight(1f).padding(vertical = 9.dp),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text(
                                stringResource(R.string.txt_a27470a5),
                                color = theme.textDim, fontSize = 13.sp
                            )
                            inner()
                        }
                    )
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, tint = theme.textDim, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            // Contador de resultados cuando hay búsqueda activa
            if (searchQuery.isNotBlank()) {
                val searchResultText = if (filteredSessions.size == 1)
                    stringResource(R.string.history_search_one, filteredSessions.size)
                else
                    stringResource(R.string.history_search_other, filteredSessions.size)
                Text(
                    searchResultText,
                    color = if (filteredSessions.isEmpty()) Color(0xFFEF4444) else acc,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        HorizontalDivider(color = theme.border, thickness = 0.5.dp)

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.txt_0fd1abda),
                        color = theme.textDim, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else if (filteredSessions.isEmpty() && searchQuery.isNotBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 36.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.txt_57be44d3, searchQuery),
                        color = theme.textDim, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                filteredBooksWithSessions.forEach { (book, lang) ->
                    val expandKey = "${book.id}_$lang"
                    val bookEdMap = books.find { it.id == book.id }?.editions?.associate { it.id to it.language } ?: emptyMap()
                    val bookSessions = filteredSessions.filter { s ->
                        if (s.bookId != book.id) return@filter false
                        // TAREA 4 (B-034): unificado con sessionsForBookAndLanguage vía vm.activeLanguage
                        val sLang = s.editionId?.let { bookEdMap[it] } ?: vm.activeLanguage(book.id)
                        sLang == lang
                    }
                    val bookTotalPages = bookSessions.sumOf { it.pages }
                    val bookTotalMins = bookSessions.mapNotNull { it.minutes }.sum()
                    // B-033 (regla 16-07): el borrado masivo va por edición Y POR CICLO. Si esta
                    // edición mezcla ciclos, la papelera de cabecera desaparece y cada sub-ciclo
                    // lleva la suya; con un solo ciclo, cabecera == ciclo y se queda.
                    val bookCycleKeys = bookSessions.map { it.readingIndex ?: 0 }.toSet()
                    val singleCycle = bookCycleKeys.size <= 1
                    val isExpanded = expanded[expandKey] == true
                    // Obtener la edición del idioma correspondiente para mostrar el flag
                    val editionForLang = book.editions.firstOrNull { it.language == lang }
                    val langFlag = editionForLang?.flag ?: if (lang == "es") "🇪🇸" else "🌐"

                    // ── Fila desplegable del libro ─────────────────────────────
                    item(key = "header_$expandKey") {
                        var showDeleteAllHistory by remember { mutableStateOf(false) }
                        if (showDeleteAllHistory) {
                            // B-033: solo existe con un ciclo único (cabecera == ciclo) y el
                            // texto dice cuántas sesiones y de qué ciclo se van.
                            val singleCycleLabel = when {
                                bookCycleKeys.firstOrNull() == 0 || bookCycleKeys.isEmpty() -> stringResource(R.string.sessions_reading)
                                else -> stringResource(R.string.sessions_rereading)
                            }
                            AlertDialog(
                                onDismissRequest = { showDeleteAllHistory = false },
                                containerColor = theme.bgMid,
                                title = { Text(stringResource(R.string.txt_995fe186), color = theme.textMain, fontWeight = FontWeight.Bold) },
                                text = { Text(
                                    if (bookSessions.size == 1) stringResource(R.string.dialog_delete_cycle_one, singleCycleLabel)
                                    else stringResource(R.string.dialog_delete_cycle_msg, bookSessions.size, singleCycleLabel),
                                    color = theme.textMuted
                                ) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        vm.deleteSessions(bookSessions.map { it.id }, prefs)
                                        showDeleteAllHistory = false
                                    }) { Text(stringResource(R.string.txt_5b5c9f9d), color = Red, fontWeight = FontWeight.Bold) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteAllHistory = false }) {
                                        Text(stringResource(R.string.txt_847607d7), color = acc)
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            onClick = { expanded[expandKey] = !isExpanded },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isExpanded) theme.surface else theme.bgMid,
                            border = BorderStroke(1.dp, if (isExpanded) acc.copy(alpha = 0.5f) else theme.border),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = acc,
                                        modifier = Modifier.size(18.dp).rotate(if (isExpanded) 0f else -90f).padding(top = 2.dp)
                                    )
                                    Text(langFlag, fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            book.title,
                                            color = theme.textMain,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            softWrap = true,
                                            lineHeight = 17.sp,
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                indication = null
                                            ) { onDetail(book.id) }
                                        )
                                        if (book.author.isNotBlank()) {
                                            Text(
                                                book.author,
                                                color = theme.textDim,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    // v20.4 (C1): misma posición/estilo que BookCard shelf
                                    // B-033: con ciclos mezclados la papelera baja a cada sub-ciclo
                                    if (singleCycle) Box(
                                        Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Red.copy(alpha = 0.12f))
                                            .clickable { showDeleteAllHistory = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.txt_995fe186),
                                            tint = Red.copy(alpha = 0.7f),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    DrawerStatChipH("📚 ${bookSessions.size} Ses", acc, Modifier.weight(1f))
                                    DrawerStatChipH(if (bookTotalMins > 0) "⏱️ ${fmtMinutes(bookTotalMins)}" else "⏱️ —", Sky, Modifier.weight(1f))
                                    DrawerStatChipH("📄 $bookTotalPages Págs", Green, Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                    }

                    // ── Sesiones del libro/idioma ──────────────────────────────
                    if (isExpanded) {
                        val sortedSessions = if (newestFirst)
                            bookSessions.sortedByDescending { it.date + it.id.toString().padStart(20, '0') }
                        else
                            bookSessions.sortedBy { it.date + it.id.toString().padStart(20, '0') }

                        // v20.5: si el libro tiene sesiones de varios ciclos, mostrar sub-secciones desplegables
                        val cycleGroups = sortedSessions.groupBy { it.readingIndex ?: 0 }
                        val hasMixedCycles = cycleGroups.keys.any { it > 0 } && cycleGroups.keys.any { it == 0 }

                        if (hasMixedCycles) {
                            // ── Sub-secciones por ciclo ────────────────────────
                            val sortedCycleKeys = cycleGroups.keys.sorted()
                            val multipleCycles = sortedCycleKeys.count { it > 0 } > 1

                            for (cycleIdx in sortedCycleKeys) {
                                val cycleSess = cycleGroups[cycleIdx] ?: continue
                                val cycleKey = "${expandKey}_$cycleIdx"
                                val isCycleOriginal = cycleIdx == 0
                                val cycleAccent = if (isCycleOriginal) acc else Color(0xFF06B6D4)
                                val isCycleExpanded = cycleExpanded[cycleKey] ?: false

                                item(key = "cycle_header_$cycleKey") {
                                    val cycleLabelStr = when {
                                        isCycleOriginal -> stringResource(R.string.sessions_reading)
                                        multipleCycles -> stringResource(R.string.sessions_rereading_n, cycleIdx)
                                        else -> stringResource(R.string.sessions_rereading)
                                    }
                                    // B-033: papelera POR CICLO (la de cabecera desaparece al mezclar
                                    // ciclos) con diálogo que dice cuántas sesiones y de qué ciclo.
                                    var showDeleteCycle by remember(cycleKey) { mutableStateOf(false) }
                                    if (showDeleteCycle) {
                                        AlertDialog(
                                            onDismissRequest = { showDeleteCycle = false },
                                            containerColor = theme.bgMid,
                                            title = { Text(stringResource(R.string.txt_995fe186), color = theme.textMain, fontWeight = FontWeight.Bold) },
                                            text = { Text(
                                                if (cycleSess.size == 1) stringResource(R.string.dialog_delete_cycle_one, cycleLabelStr)
                                                else stringResource(R.string.dialog_delete_cycle_msg, cycleSess.size, cycleLabelStr),
                                                color = theme.textMuted
                                            ) },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    vm.deleteSessions(cycleSess.map { it.id }, prefs)
                                                    showDeleteCycle = false
                                                }) { Text(stringResource(R.string.txt_5b5c9f9d), color = Red, fontWeight = FontWeight.Bold) }
                                            },
                                            dismissButton = { TextButton(onClick = { showDeleteCycle = false }) { Text(stringResource(R.string.txt_847607d7), color = acc) } }
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        onClick = { cycleExpanded[cycleKey] = !isCycleExpanded },
                                        shape = RoundedCornerShape(10.dp),
                                        color = theme.bgMid,
                                        border = BorderStroke(1.dp, if (isCycleOriginal) theme.border else Color(0x3306B6D4)),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(cycleLabelStr, color = cycleAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Text("${cycleSess.size}", color = cycleAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(5.dp))
                                            Icon(
                                                if (isCycleExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = cycleAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Box(
                                                Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(Red.copy(alpha = 0.12f))
                                                    .clickable { showDeleteCycle = true },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.txt_995fe186),
                                                    tint = Red.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isCycleExpanded) {
                                    itemsIndexed(cycleSess) { _, session ->
                                        val fixedNum = fixedNumberMap[session.id] ?: 0
                                        Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                            HistorySessionCard(
                                                session = session,
                                                sessionNumber = fixedNum,
                                                book = null,
                                                theme = theme,
                                                onDelete = { vm.deleteSession(session.id, prefs); refreshWidgetForBookIfSelected(context, session.bookId) },
                                                onEdit = { updated -> vm.updateSession(updated, prefs); refreshWidgetForBookIfSelected(context, updated.bookId) },
                                                chipWidth = 60.dp
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // ── Lista plana (solo un ciclo) ────────────────────
                            itemsIndexed(sortedSessions) { _, session ->
                                val fixedNum = fixedNumberMap[session.id] ?: 0
                                Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                    HistorySessionCard(
                                        session = session,
                                        sessionNumber = fixedNum,
                                        book = null,
                                        theme = theme,
                                        onDelete = { vm.deleteSession(session.id, prefs); refreshWidgetForBookIfSelected(context, session.bookId) },
                                        onEdit = { updated -> vm.updateSession(updated, prefs); refreshWidgetForBookIfSelected(context, updated.bookId) },
                                        chipWidth = 60.dp
                                    )
                                }
                            }
                        }

                        item(key = "spacer_$expandKey") {
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = theme.border, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

fun fmtMinutes(totalMins: Int): String {
    val h = totalMins / 60
    val m = totalMins % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
fun HistoryStat(value: String, label: String, modifier: Modifier, theme: Theme, valueColor: Color? = null) {
    // QA 12-07 r2 (Aurora): acento del tema en vez del índigo global (legibilidad)
    val effColor = if (valueColor == Accent) accentForTheme(theme) else valueColor
    val color = effColor ?: theme.textMain
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(10.dp),
        color = effColor?.copy(alpha = 0.12f) ?: theme.surface,
        border = BorderStroke(1.dp, effColor?.copy(alpha = 0.35f) ?: theme.border)
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AutoSizeText(value, color = color, maxFontSize = 15.sp, minFontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(label, color = theme.textDim, fontSize = 7.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 9.sp, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DrawerStatChip(text: String, color0: Color) {
    val color = themedAccentOr(color0)
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        AutoSizeText(
            text = text,
            color = color,
            maxFontSize = 10.sp,
            minFontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DrawerStatChipH(text: String, color0: Color, modifier: Modifier = Modifier) {
    val color = themedAccentOr(color0)
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f)),
        modifier = modifier
    ) {
        AutoSizeText(
            text = text,
            color = color,
            maxFontSize = 10.sp,
            minFontSize = 6.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun HistoryPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun HistorySessionCard(
    session: ReadingSession,
    sessionNumber: Int = 0,
    book: Book?,
    theme: Theme,
    onDelete: (() -> Unit)? = null,
    onEdit: ((ReadingSession) -> Unit)? = null,
    chipWidth: androidx.compose.ui.unit.Dp = 76.dp
) {
    val context = LocalContext.current
    val acc = accentForTheme(theme)
    val pagesPerMin = if (session.minutes != null && session.minutes > 0)
        String.format("%.1f", session.pages.toDouble() / session.minutes)
    else null

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog    by remember { mutableStateOf(false) }

    // ── Diálogo de confirmación de eliminación ────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.txt_03f6aff4), color = theme.textMain) },
            text  = { Text(stringResource(R.string.dialog_delete_session_date, fmtDate(session.date)), color = theme.textMuted) },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteConfirm = false }) {
                    Text(stringResource(R.string.txt_5b5c9f9d), color = Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.txt_847607d7), color = acc)
                }
            },
            containerColor = theme.bgMid
        )
    }

    // ── Diálogo de edición ────────────────────────────────────────────────────
    if (showEditDialog) {
        var startPageText by remember { mutableStateOf(session.startPage?.toString() ?: "") }
        var endPageText   by remember { mutableStateOf(session.endPage?.toString() ?: "") }
        var minsText   by remember { mutableStateOf(session.minutes?.toString() ?: "") }
        var noteText   by remember { mutableStateOf(session.note) }
        var dateText   by remember { mutableStateOf(storedToDisplay(session.date)) }
        var dateError  by remember { mutableStateOf("") }
        var pageError  by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_8e892422), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it; dateError = "" },
                        label = { Text(stringResource(R.string.txt_39464d70), color = theme.textDim, fontSize = 12.sp) },
                        isError = dateError.isNotEmpty(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.textMain,
                            unfocusedTextColor = theme.textMain,
                            focusedBorderColor = acc,
                            unfocusedBorderColor = theme.border
                        )
                    )
                    if (dateError.isNotEmpty()) {
                        Text(dateError, color = Red, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = startPageText,
                            onValueChange = { startPageText = it.filter { c -> c.isDigit() }; pageError = "" },
                            label = { Text(stringResource(R.string.txt_739eacc1), color = theme.textDim, fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = pageError.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = theme.textMain,
                                unfocusedTextColor = theme.textMain,
                                focusedBorderColor = acc,
                                unfocusedBorderColor = theme.border
                            )
                        )
                        OutlinedTextField(
                            value = endPageText,
                            onValueChange = { endPageText = it.filter { c -> c.isDigit() }; pageError = "" },
                            label = { Text(stringResource(R.string.txt_28be9b81), color = theme.textDim, fontSize = 12.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = pageError.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = theme.textMain,
                                unfocusedTextColor = theme.textMain,
                                focusedBorderColor = acc,
                                unfocusedBorderColor = theme.border
                            )
                        )
                    }
                    if (pageError.isNotEmpty()) {
                        Text(pageError, color = Red, fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = minsText,
                        onValueChange = { minsText = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.txt_0a43aca4), color = theme.textDim, fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.textMain,
                            unfocusedTextColor = theme.textMain,
                            focusedBorderColor = acc,
                            unfocusedBorderColor = theme.border
                        )
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(stringResource(R.string.txt_7cd6ad03), color = theme.textDim, fontSize = 12.sp) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.textMain,
                            unfocusedTextColor = theme.textMain,
                            focusedBorderColor = acc,
                            unfocusedBorderColor = theme.border
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val stored = parseFlexibleDate(dateText.trim())
                    if (stored == null) { dateError = context.getString(R.string.err_date_invalid_correct); return@TextButton }
                    // v20.2 (B4+B5): validar páginas
                    val newStart = startPageText.toIntOrNull()
                    val newEnd   = endPageText.toIntOrNull()
                    if (startPageText.isBlank() || endPageText.isBlank()) {
                        pageError = context.getString(R.string.err_pages_start_end_required); return@TextButton
                    }
                    if (newStart != null && newEnd != null) {
                        if (newStart >= newEnd) {
                            pageError = if (newStart >= newEnd)
                                context.getString(R.string.err_page_start_lt_end)
                            else
                                context.getString(R.string.err_page_end_gt_start)
                            return@TextButton
                        }
                    }
                    val pages = if (newStart != null && newEnd != null) newEnd - newStart + 1 else session.pages
                    val mins  = minsText.toIntOrNull()
                    onEdit?.invoke(session.copy(pages = pages, minutes = mins, note = noteText.trim(), date = stored, startPage = newStart, endPage = newEnd))
                    showEditDialog = false
                }) { Text(stringResource(R.string.txt_d3270bdb), color = acc, fontWeight = FontWeight.Bold) }
            },
            confirmButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(R.string.txt_847607d7), color = Red)
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, theme.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            // ── Cabecera: número + fecha + botones ────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sessionNumber > 0) {
                    Text(
                        "#$sessionNumber",
                        color = acc.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Text(
                    fmtDate(session.date),
                    color = theme.textDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (onEdit != null) {
                    IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, null, tint = acc.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Red.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            // Título y autor — solo si el book se pasa (en historial global).
            // Cuando book == null el libro ya aparece en la cabecera del grupo desplegable.
            if (book != null) {
                Text(
                    book.title,
                    color = theme.textMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    softWrap = true
                )
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        color = acc,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            // Chips de datos — ancho fijo idéntico para todos, texto se reduce si no cabe
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DataChipSm("📄 ${session.pages}p", acc.copy(alpha = 0.15f), acc, Modifier.width(chipWidth))
                if (session.minutes != null) {
                    DataChipSm("⏱️ ${fmtMinutes(session.minutes)}", Sky.copy(alpha = 0.15f), Sky, Modifier.width(chipWidth))
                }
                if (pagesPerMin != null) {
                    DataChipSm("⚡ $pagesPerMin p/m", Green.copy(alpha = 0.15f), Green, Modifier.width(chipWidth))
                }
                if (session.startPage != null && session.endPage != null) {
                    val amber = Color(0xFFF59E0B)
                    DataChipSm("📖 ${session.startPage}-${session.endPage}", amber.copy(alpha = 0.15f), amber, Modifier.width(chipWidth))
                }
            }
            // Nota — expandible si es larga
            if (session.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                var noteExpanded by remember { mutableStateOf(false) }
                val needsExpand = session.note.length > 90 || session.note.contains('\n')
                Column(
                    modifier = if (needsExpand) Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { noteExpanded = !noteExpanded }
                        .padding(vertical = 2.dp)
                    else Modifier
                ) {
                    Text(
                        "💬 ${session.note}",
                        color = theme.textDim,
                        fontSize = 11.sp,
                        maxLines = if (noteExpanded || !needsExpand) Int.MAX_VALUE else 2,
                        overflow = if (noteExpanded || !needsExpand) TextOverflow.Visible else TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (needsExpand) {
                        Icon(
                            if (noteExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (noteExpanded) stringResource(R.string.cd_collapse_note) else stringResource(R.string.cd_expand_note),
                            tint = acc.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp).align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DataChip(text: String, bg0: Color, fg0: Color, modifier: Modifier = Modifier) {
    val bg = themedAccentOr(bg0); val fg = themedAccentOr(fg0)
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AutoSizeText(text, color = fg, maxFontSize = 11.sp, minFontSize = 8.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun DataChipSm(text: String, bg0: Color, fg0: Color, modifier: Modifier = Modifier) {
    val bg = themedAccentOr(bg0); val fg = themedAccentOr(fg0)
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        AutoSizeText(text, color = fg, maxFontSize = 11.sp, minFontSize = 7.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SessionChart(sessions: List<ReadingSession>, theme: Theme, reversed: Boolean = false) {
    // Agrupa por fecha y suma páginas
    val sorted = sessions
        .groupBy { it.date }
        .mapValues { (_, list) -> list.sumOf { it.pages } }
        .entries
        .let { if (reversed) it.sortedByDescending { e -> e.key } else it.sortedBy { e -> e.key } }
        .takeLast(14) // últimos/primeros 14 días con sesiones

    if (sorted.isEmpty()) return

    val maxPages = sorted.maxOf { it.value }.toFloat().coerceAtLeast(1f)
    val barColor = Brush.verticalGradient(listOf(Accent, Accent2))

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accentForTheme(theme).copy(alpha = 0.05f),
        border = BorderStroke(1.dp, accentForTheme(theme).copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (reversed) stringResource(R.string.chart_pages_per_day_recent) else stringResource(R.string.chart_pages_per_day),
                color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                sorted.forEach { (date, pages) ->
                    val fraction = (pages.toFloat() / maxPages).coerceIn(0.05f, 1f)
                                        Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Text("$pages", color = theme.textDim, fontSize = 8.sp, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor)
                        )
                    }
                }
            }
            // Etiquetas de fecha debajo
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                sorted.forEach { (date, _) ->
                    val dayLabel = date.takeLast(2)
                    Text(dayLabel, color = theme.textDim, fontSize = 8.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SessionRow(session: ReadingSession, sessionNumber: Int = 0, theme: Theme, onDelete: () -> Unit, onEdit: (ReadingSession) -> Unit) {
    val context = LocalContext.current
    val acc = accentForTheme(theme)
    var showConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(true) }
    val pagesPerMin = if (session.minutes != null && session.minutes > 0)
        String.format("%.1f", session.pages.toDouble() / session.minutes)
    else null
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.txt_03f6aff4), color = theme.textMain) },
            text = { Text(stringResource(R.string.dialog_delete_session_date, fmtDate(session.date)), color = theme.textMuted) },
            confirmButton = { TextButton(onClick = { onDelete(); showConfirm = false }) { Text(stringResource(R.string.txt_5b5c9f9d), color = Red) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.txt_847607d7), color = acc) } },
            containerColor = theme.bgMid
        )
    }
    if (showEditDialog) {
        var startPageText by remember { mutableStateOf(session.startPage?.toString() ?: "") }
        var endPageText   by remember { mutableStateOf(session.endPage?.toString() ?: "") }
        var minsText  by remember { mutableStateOf(session.minutes?.toString() ?: "") }
        var noteText  by remember { mutableStateOf(session.note) }
        var dateText  by remember { mutableStateOf(storedToDisplay(session.date)) }
        var dateError by remember { mutableStateOf("") }
        var pageError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_8e892422), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dateText, onValueChange = { dateText = it; dateError = "" },
                        label = { Text(stringResource(R.string.txt_39464d70), color = theme.textDim, fontSize = 12.sp) },
                        isError = dateError.isNotEmpty(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, focusedBorderColor = acc, unfocusedBorderColor = theme.border)
                    )
                    if (dateError.isNotEmpty()) Text(dateError, color = Red, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = startPageText, onValueChange = { startPageText = it.filter { c -> c.isDigit() }; pageError = "" },
                            label = { Text(stringResource(R.string.txt_739eacc1), color = theme.textDim, fontSize = 12.sp) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = pageError.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, focusedBorderColor = acc, unfocusedBorderColor = theme.border)
                        )
                        OutlinedTextField(
                            value = endPageText, onValueChange = { endPageText = it.filter { c -> c.isDigit() }; pageError = "" },
                            label = { Text(stringResource(R.string.txt_28be9b81), color = theme.textDim, fontSize = 12.sp) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = pageError.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, focusedBorderColor = acc, unfocusedBorderColor = theme.border)
                        )
                    }
                    if (pageError.isNotEmpty()) Text(pageError, color = Red, fontSize = 12.sp)
                    OutlinedTextField(
                        value = minsText, onValueChange = { minsText = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.txt_0a43aca4), color = theme.textDim, fontSize = 12.sp) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, focusedBorderColor = acc, unfocusedBorderColor = theme.border)
                    )
                    OutlinedTextField(
                        value = noteText, onValueChange = { noteText = it },
                        label = { Text(stringResource(R.string.txt_7cd6ad03), color = theme.textDim, fontSize = 12.sp) },
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, focusedBorderColor = acc, unfocusedBorderColor = theme.border)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val stored = parseFlexibleDate(dateText.trim())
                    if (stored == null) { dateError = context.getString(R.string.err_date_invalid_correct); return@TextButton }
                    // v20.2 (B4+B5): validar páginas
                    val newStart = startPageText.toIntOrNull()
                    val newEnd   = endPageText.toIntOrNull()
                    if (startPageText.isBlank() || endPageText.isBlank()) {
                        pageError = context.getString(R.string.err_pages_start_end_required); return@TextButton
                    }
                    if (newStart != null && newEnd != null) {
                        if (newStart >= newEnd) {
                            pageError = if (startPageText.toInt() >= endPageText.toInt())
                                context.getString(R.string.err_page_start_lt_end)
                            else
                                context.getString(R.string.err_page_end_gt_start)
                            return@TextButton
                        }
                    }
                    val pages = if (newStart != null && newEnd != null) newEnd - newStart + 1 else session.pages
                    val mins  = minsText.toIntOrNull()
                    onEdit(session.copy(pages = pages, minutes = mins, note = noteText.trim(), date = stored, startPage = newStart, endPage = newEnd))
                    showEditDialog = false
                }) { Text(stringResource(R.string.txt_d3270bdb), color = acc, fontWeight = FontWeight.Bold) }
            },
            confirmButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) }
            }
        )
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, theme.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).clickable { expanded = !expanded }
                ) {
                    if (sessionNumber > 0) {
                        Text("#$sessionNumber", color = acc, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(fmtDate(session.date), color = theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = acc,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, null, tint = acc.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
            }
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DataChip("📄 ${session.pages} págs", acc.copy(alpha = 0.15f), acc, Modifier.weight(1f))
                    if (session.minutes != null) {
                        DataChip("⏱️ ${session.minutes} min", Sky.copy(alpha = 0.15f), Sky, Modifier.weight(1f))
                    }
                    if (pagesPerMin != null) {
                        DataChip("⚡ $pagesPerMin p/min", Green.copy(alpha = 0.15f), Green, Modifier.weight(1f))
                    }
                    if (session.startPage != null && session.endPage != null) {
                        val amber = Color(0xFFF59E0B)
                        DataChip("📖 ${session.startPage}-${session.endPage}", amber.copy(alpha = 0.15f), amber, Modifier.weight(1f))
                    }
                }
                if (session.note.isNotBlank()) {
                    var noteExpanded by remember { mutableStateOf(false) }
                    var noteOverflows by remember { mutableStateOf(false) }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "💬 ${session.note}",
                        color = theme.textDim,
                        fontSize = 11.sp,
                        maxLines = if (noteExpanded) Int.MAX_VALUE else 2,
                        overflow = if (noteExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                        onTextLayout = { result -> noteOverflows = result.hasVisualOverflow }
                    )
                    if (noteOverflows || noteExpanded) {
                        Icon(
                            if (noteExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = acc,
                            modifier = Modifier.size(18.dp).align(Alignment.CenterHorizontally).clickable { noteExpanded = !noteExpanded }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxFontSize: TextUnit = 14.sp,
    minFontSize: TextUnit = 8.sp,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    var readyToDraw by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.drawWithContent { if (readyToDraw) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth) {
                val next = fontSize * 0.9f
                if (next >= minFontSize) fontSize = next
                else { fontSize = minFontSize; readyToDraw = true }
            } else {
                readyToDraw = true
            }
        }
    )
}
