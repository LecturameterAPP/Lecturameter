package com.lecturameter

// StatsScreen, componentes de estadisticas (SummaryCell, SpeedBookRow, GenreRow, AuthorStatRow), BookCard, BookCover y AuthorBooksScreen.
// Extraido de MainActivity.kt el 15-07-2026 (ruptura del monolito, sin cambios funcionales).


import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.net.URL
import java.util.*
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import android.widget.Toast
import kotlinx.coroutines.flow.first
import com.lecturameter.model.*
import com.lecturameter.utils.*
import androidx.navigation.compose.composable

@Composable
fun StatsScreen(vm: BooksViewModel, _prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit, onWrapped: (Int) -> Unit, onWrappedHistory: () -> Unit, onDetail: (Long) -> Unit = {}, onDetailWithDate: (Long, String) -> Unit = { _, _ -> }, onDailySessions: (String) -> Unit = {}, onWeeklyRecap: () -> Unit = {}) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val acc = accentForTheme(theme)
    // Fase 6.1 (D-008, T6): registrar que Estadísticas ya se ha abierto
    LaunchedEffect(Unit) { _prefs.edit().putBoolean("stats_opened", true).apply() }
    // Solo FINISHED con fechas distintas (mismo día distorsiona velocidad)
    val finished = books.filter {
        it.status == BookStatus.FINISHED &&
        it.startDate != null && it.endDate != null &&
        it.startDate != it.endDate &&
        !it.importedFromGoodreads
    }

    val wrappedYear  = remember { wrappedWindowYear() }
    val inWindow     = wrappedYear != -1

    // Filter state
    // null = never interacted (show placeholder); "" = "Todos" selected; non-blank = specific filter
    var filterGenre by remember { mutableStateOf<String?>(null) }
    var filterAuthor by remember { mutableStateOf<String?>(null) }
    var genreUserSelected by remember { mutableStateOf(false) }
    var authorUserSelected by remember { mutableStateOf(false) }
    var showGenreMenu by remember { mutableStateOf(false) }
    var showAuthorMenu by remember { mutableStateOf(false) }
    // Feedback 15-07: el heatmap pasa a ser la vista por defecto (antes "charts").
    var statsView by remember { mutableStateOf(_prefs.getString("stats_view_mode", "heatmap") ?: "heatmap") }
    fun setStatsView(v: String) { statsView = v; _prefs.edit().putString("stats_view_mode", v).apply() }
    val showCharts = statsView == "charts"

    // Normalise stored genres: if a book has a raw API value that isn't in BOOK_GENRES,
    // map it on-the-fly so the filter only shows clean genre names.
    // Los dropdowns incluyen TODOS los libros (también importados de Goodreads — Kirkman, Fujimoto, Tatsu…)
    val allGenres = books.flatMap { b -> b.genres }.distinct().sorted()
    val allAuthors = books.map { it.author }.filter { it.isNotBlank() }.distinct().sorted()

    val filtered = finished
        .let { list -> if (filterGenre != null) list.filter { it.genres.contains(filterGenre) } else list }
        .let { list -> if (filterAuthor != null) list.filter { it.author == filterAuthor } else list }

    // Derive stats
    data class SpeedEntry(val book: Book, val ppd: Double, val days: Int)

    val speedList = filtered
        .filter { it.startDate != it.endDate && daysBetween(it.startDate!!, it.endDate!!) >= 2 }
        .map { b ->
            val sessPages = sessions.filter { s -> s.bookId == b.id }.sumOf { it.pages }
            val d = daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1)
            val funcPages = if (b.firstFunctionalPage != null && b.lastFunctionalPage != null)
                (b.lastFunctionalPage - b.firstFunctionalPage + 1) else null
            val pages = when {
                sessPages > 0 -> sessPages
                funcPages != null && funcPages > 0 -> funcPages
                else -> b.pages
            }
            SpeedEntry(b, pages.toDouble() / d, d)
        }.sortedByDescending { it.ppd }

    val totalPages = filtered.sumOf { b ->
        sessions.filter { s -> s.bookId == b.id }.sumOf { it.pages }.takeIf { it > 0 } ?: b.pages
    }
    val speedFiltered = filtered.filter { it.startDate != it.endDate && daysBetween(it.startDate!!, it.endDate!!) >= 2 }
    val avgPpd = if (speedFiltered.isNotEmpty()) speedFiltered.map { b ->
        val sp = sessions.filter { s -> s.bookId == b.id }.sumOf { it.pages }
        val fp = if (b.firstFunctionalPage != null && b.lastFunctionalPage != null) (b.lastFunctionalPage - b.firstFunctionalPage + 1) else null
        val pages = when { sp > 0 -> sp; fp != null && fp > 0 -> fp; else -> b.pages }
        val d = daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1)
        pages.toDouble() / d
    }.average() else 0.0
    val avgDays = if (speedFiltered.isNotEmpty()) speedFiltered.map { b ->
        daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1)
    }.average() else 0.0
    val avgRating = filtered.filter { it.rating > 0 }.let { r -> if (r.isNotEmpty()) r.map { it.rating }.average() else null }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                // Feedback 2.6: el título se auto-reduce en vez de partirse ("Estadística / s")
                var statsTitleSize by remember { mutableStateOf(22.sp) }
                Text(
                    stringResource(R.string.txt_a6db8091), color = theme.textMain,
                    fontSize = statsTitleSize, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false,
                    onTextLayout = { if (it.hasVisualOverflow && statsTitleSize > 14.sp) statsTitleSize *= 0.92f }
                )
                Text(stringResource(R.string.stats_finished_books, filtered.size), color = theme.textMuted, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Feedback 15-07: el heatmap va primero — es la vista por defecto y no
                // tenía sentido que su botón fuese el último de la fila.
                listOf("heatmap" to "🔥", "charts" to "📈", "table" to "📋").forEach { (mode, icon) ->
                    val active = statsView == mode
                    Surface(
                        onClick = { setStatsView(mode) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) acc else theme.surface,
                        border = BorderStroke(1.dp, if (active) acc else theme.border)
                    ) {
                        Text(icon, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                    }
                }
            }
            // Feedback 2.7: eliminado el botón 🎁 de historial Wrapped de este header —
            // comprimía el espacio y truncaba "N libros terminados". El historial sigue
            // accesible desde el icono 🎁 de la pantalla principal y el banner en ventana.
        }

        // Banner Wrapped (solo en ventana)
        if (inWindow) {
            val alreadySaved = vm.wrappedForYear(wrappedYear) != null
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0x1AF59E0B),
                border = BorderStroke(1.dp, Color(0x4DF59E0B)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable { onWrapped(wrappedYear) }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎉", fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        // RF-M24: Gold crudo daba 1,52:1 sobre el papel del Claro (B-037 dejo este
                        // banner fuera). wrappedInk garantiza AA conservando el matiz, y sin alfa:
                        // el alfa sobre el tinte era justo la causa raiz de B-037.
                        Text(stringResource(R.string.wrapped_ready, wrappedYear), color = wrappedInk(Gold, theme), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(if (alreadySaved) stringResource(R.string.wrapped_banner_saved) else stringResource(R.string.wrapped_banner_available), color = wrappedInk(Gold, theme), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = wrappedInk(Gold, theme))
                }
            }
        }

        // ── Fase 5 (P-023, acceso A) + Fase 6.4 (M-2): tarjetas de recap ─────────
        // B-035 (reincidencia, WhatsApp 15-07 18:57): estas tarjetas vivían FUERA del
        // área con scroll (el scroll está dentro de cada vista), así que se quedaban
        // pegadas arriba mientras las gráficas/tabla scrolleaban. Ahora son el primer
        // item del contenido scrolleable; en el heatmap (sin scroll) van fijas como antes.
        val weeklyRecap = remember(books, sessions) {
            com.lecturameter.utils.computeWeeklyRecap(books, sessions, vm.bingoCard.value, vm.challenges.value, today())
        }
        val recapCards: @Composable () -> Unit = {
            if (weeklyRecap != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.border),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable { onWeeklyRecap() }
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, null, tint = theme.textMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.recap_card_line, weeklyRecap.pages, weeklyRecap.sessionsCount),
                            color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ChevronRight, null, tint = theme.textMuted)
                    }
                }
            }
        }

        // (Los filtros de Género y Autor se muestran ahora dentro de la tarjeta "Filtros" de las gráficas)

        if (statsView == "heatmap") {
            recapCards()
            HeatmapView(vm = vm, prefs = _prefs, theme = theme, onNavigateToSession = { bookId, date -> onDetailWithDate(bookId, date) }, onNavigateToDailySessions = onDailySessions)
        } else if (showCharts) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                item { recapCards() }
                item { StatsChartsView(vm, theme, filterGenre, { filterGenre = it; genreUserSelected = true }, allGenres, filterAuthor, { filterAuthor = it; authorUserSelected = true }, allAuthors) }
                // v2.4 rework: secciones avanzadas al final de la pantalla
                item { AdvancedStatsSections(vm, theme) }
                // Feedback 23-07: "Exportar estadísticas" va lo ÚLTIMO del todo, tras las avanzadas
                item { StatsExportSection(vm, theme) }
            }
        } else if (filtered.isEmpty()) {
            recapCards()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📈", fontSize = 48.sp); Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.txt_13f65f8c), color = theme.textMain, fontSize = 16.sp)
                    Text(stringResource(R.string.txt_30fc9acb), color = theme.textDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { recapCards() }
                // Global summary card
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = accentForTheme(theme).copy(alpha = 0.1f), border = BorderStroke(1.dp, accentForTheme(theme).copy(alpha = 0.2f))) {
                        Column(Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.txt_316406f4), color = accentForTheme(theme), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            Spacer(Modifier.height(14.dp))
                            // Plan landscape v3.0 (P3): en horizontal el resumen numerico se
                            // presenta en una unica fila compacta; la puntuacion media, que en
                            // portrait cuelga como bloque dividido debajo, pasa a ser una celda
                            // inline mas — asi aprovecha el ancho extra y ahorra alto (escaso en
                            // landscape). Portrait queda EXACTAMENTE igual (rama else).
                            if (isLandscape()) {
                                Row(Modifier.fillMaxWidth()) {
                                    SummaryCell("${filtered.size}", stringResource(R.string.pill_leidos), Modifier.weight(1f), theme)
                                    SummaryCell(totalPages.toLocaleString(), stringResource(R.string.txt_939f09a3), Modifier.weight(1f), theme)
                                    // RF-M25: Green crudo como texto no llega a AA en Claro; tinta medida
                                    SummaryCell(if (speedFiltered.isNotEmpty()) String.format("%.1f", avgPpd) else "-", stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, wrappedInk(Green, theme))
                                    SummaryCell(if (speedFiltered.isNotEmpty()) String.format("%.0f d", avgDays) else "-", stringResource(R.string.pill_dias_libro), Modifier.weight(1f), theme)
                                    if (avgRating != null) {
                                        SummaryCell(String.format("%.1f/10", avgRating), stringResource(R.string.txt_a70ecc0a), Modifier.weight(1f), theme, wrappedInk(Gold, theme))
                                    }
                                }
                            } else {
                                Row(Modifier.fillMaxWidth()) {
                                    SummaryCell("${filtered.size}", stringResource(R.string.pill_leidos), Modifier.weight(1f), theme)
                                    SummaryCell(totalPages.toLocaleString(), stringResource(R.string.txt_939f09a3), Modifier.weight(1f), theme)
                                    // RF-M25: Green crudo como texto no llega a AA en Claro; tinta medida
                                    SummaryCell(if (speedFiltered.isNotEmpty()) String.format("%.1f", avgPpd) else "-", stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, wrappedInk(Green, theme))
                                    SummaryCell(if (speedFiltered.isNotEmpty()) String.format("%.0f d", avgDays) else "-", stringResource(R.string.pill_dias_libro), Modifier.weight(1f), theme)
                                }
                                if (avgRating != null) {
                                    Spacer(Modifier.height(12.dp))
                                    HorizontalDivider(color = theme.border)
                                    Spacer(Modifier.height(12.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.txt_a70ecc0a), color = theme.textMuted, fontSize = 13.sp)
                                        Spacer(Modifier.width(8.dp))
                                        val stars = kotlin.math.ceil(avgRating / 2.0).toInt().coerceIn(0, 5)
                                        Text(
                                            "★".repeat(stars) + "☆".repeat(5 - stars),
                                            color = wrappedInk(Gold, theme), fontSize = 15.sp, letterSpacing = 1.sp
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(String.format("%.1f/10", avgRating), color = wrappedInk(Gold, theme), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Fastest readers ranking — solo si hay libros con span >= 2 días
                if (speedList.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.txt_fca0bbb8), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        Text(stringResource(R.string.txt_012f4789), color = theme.textDim, fontSize = 11.sp)
                    }
                    items(speedList.take(20)) { entry ->
                        SpeedBookRow(entry.book, entry.ppd, entry.days, speedList.first().ppd, theme, onClick = { onDetail(entry.book.id) })
                    }
                }

                // By genre breakdown — siempre visible, mensaje si no hay géneros asignados
                if (filterGenre == null && filterAuthor == null) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.txt_667166ae), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    val genreGroups = finished.filter { it.genres.isNotEmpty() }
                        .flatMap { book -> book.genres.map { g -> g to book } }
                        .groupBy({ it.first }, { it.second })
                        .filter { it.key.isNotBlank() }
                        .entries.sortedByDescending { it.value.size }
                    if (genreGroups.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.txt_896b3fde),
                                color = theme.textDim,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }
                    } else {
                        items(genreGroups.take(10)) { (genre, books) ->
                            val avgSpeed = books
                                .map { b -> b.pages.toDouble() / daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1) }
                                .average()
                            GenreRow(genre, books.size, avgSpeed, theme)
                        }
                    }
                }

                // By author breakdown
                if (allAuthors.size > 1 && filterGenre == null && filterAuthor == null) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.txt_9303ba12), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    val authorGroups = finished.filter { it.author.isNotBlank() }
                        .groupBy { it.author }
                        .entries.sortedByDescending { it.value.size }
                    items(authorGroups.take(10)) { (author, books) ->
                        val avgSpeed = books.filter { it.startDate != null && it.endDate != null && it.startDate != it.endDate && daysBetween(it.startDate, it.endDate) >= 2 }
                            .map { b -> b.pages.toDouble() / daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1) }
                            .let { if (it.isNotEmpty()) it.average() else null }
                        AuthorStatRow(author, books.size, avgSpeed, theme)
                    }
                }

                // v2.4 rework: secciones avanzadas al final de la pantalla
                item { AdvancedStatsSections(vm, theme) }

                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
fun SummaryCell(value: String, label: String, modifier: Modifier, theme: Theme, valueColor: Color? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor ?: theme.textMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = theme.textDim, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun SpeedBookRow(book: Book, ppd: Double, days: Int, maxPpd: Double, theme: Theme, onClick: () -> Unit = {}) {
    val fraction = if (maxPpd > 0) (ppd / maxPpd).toFloat().coerceIn(0f, 1f) else 0f
    Surface(shape = RoundedCornerShape(12.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(book.coverUrl, book.title, size = 44)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (book.author.isNotBlank()) Text(book.author, color = theme.textMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(String.format("%.1f p/d", ppd), color = wrappedInk(Green, theme), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(fmtDaysLocalized(days), color = theme.textDim, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            // Speed bar — muestra la velocidad de este libro respecto al más rápido leído
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Plan landscape v3.0 (P3): la barra ya se expande a lo ancho (weight 1f); en
                // horizontal gana 2dp de alto para que la barra ancha tenga mas cuerpo.
                val barH = if (isLandscape()) 6.dp else 4.dp
                Box(Modifier.weight(1f).height(barH).clip(RoundedCornerShape(2.dp)).background(theme.border)) {
                    Box(Modifier.fillMaxWidth(fraction).height(barH).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(accentForTheme(theme), Green))))
                }
                Text(stringResource(R.string.txt_1f750a24), color = theme.textDim, fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun GenreRow(genre: String, count: Int, avgPpd: Double?, theme: Theme) {
    // Pick a color per genre deterministically
    val genreColors = listOf(Accent, Accent2, Green, Sky, Gold, Color(0xFFEC4899), Color(0xFFF97316), Color(0xFF8B5CF6))
    val colorIndex = (genre.hashCode() and 0x7FFFFFFF) % genreColors.size
    val dotColor = genreColors[colorIndex]

    Surface(shape = RoundedCornerShape(12.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(displayGenre(genre), color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$count ${stringResource(R.string.word_book)}${if (count != 1) "s" else ""}", color = theme.textMuted, fontSize = 11.sp)
            }
            if (avgPpd != null) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(String.format("%.1f", avgPpd), color = wrappedInk(Green, theme), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.txt_2686dd75), color = theme.textDim, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun AuthorStatRow(author: String, count: Int, avgPpd: Double?, theme: Theme) {
    Surface(shape = RoundedCornerShape(10.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Accent2))
            Spacer(Modifier.width(10.dp))
            Text(author, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (count == 1) stringResource(R.string.shelf_books_count, count) else stringResource(R.string.shelf_books_count_plural, count), color = theme.textMuted, fontSize = 12.sp)
            if (avgPpd != null) {
                Spacer(Modifier.width(10.dp))
                Text(String.format("%.1f p/d", avgPpd), color = wrappedInk(Green, theme), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
fun SectionHeader(label: String, count: Int, color: Color, expanded: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label.uppercase(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
        Text("$count", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun EmptySectionHint(text: String, theme: Theme) { Text(text, color = theme.textDim, fontSize = 12.sp, modifier = Modifier.padding(start = 18.dp, bottom = 10.dp)) }

@Composable
fun BookCard(
    book: Book,
    theme: Theme,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    isWidgetBook: Boolean = false,
    isRefreshingCover: Boolean = false,
    onRefreshCover: (() -> Unit)? = null,
    onApplyCoverUrl: ((String) -> Unit)? = null,
    onBroken: (() -> Unit)? = null,
    sessionPages: Int = -1,  // total de páginas leídas; -1 = desconocido
    sessionDays: Int = -1    // días únicos con sesión; -1 = desconocido
) {
    val stats = getStats(book)
    val acc = accentForTheme(theme)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCoverDialog by remember { mutableStateOf(false) }
    var coverUrlInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val coverImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && onApplyCoverUrl != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { /* algunos URIs no admiten permisos persistentes */ }
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val activeEdId = book.editions.firstOrNull { it.isActive }?.id
                val localPath = copyUriToInternalStorage(context, uri, book.id, activeEdId)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onApplyCoverUrl(localPath ?: uri.toString())
                }
            }
        }
    }

    if (showCoverDialog) {
        AlertDialog(
            onDismissRequest = { showCoverDialog = false },
            title = { Text(stringResource(R.string.txt_adbe3283), color = theme.textMain) },
            text = {
                Column {
                    Text(stringResource(R.string.txt_aa02a2da), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(value = coverUrlInput, onValueChange = { coverUrlInput = it }, placeholder = { Text(stringResource(R.string.txt_14f2b208), color = theme.textDim) }, colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { coverImagePicker.launch("image/*"); showCoverDialog = false }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, acc.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = acc)) { Text(stringResource(R.string.txt_5cd0defc)) }
                }
            },
            dismissButton = { TextButton(onClick = { showCoverDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = wrappedInk(Red, theme)) } },
            confirmButton = {
                TextButton(onClick = {
                    if (coverUrlInput.isNotBlank()) onApplyCoverUrl?.invoke(coverUrlInput.trim())
                    showCoverDialog = false
                }) { Text(stringResource(R.string.txt_f0ed2dc3), color = acc) }
            },
            containerColor = theme.bgMid
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_b375487f), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_4259b7dc, book.title), color = theme.textMuted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete?.invoke() }) {
                    Text(stringResource(R.string.txt_5b5c9f9d), color = wrappedInk(Red, theme), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.txt_847607d7), color = acc)
                }
            }
        )
    }

    // D-015 (Cuero): filete dorado interior en la tarjeta de libro (solo tema Cuero)
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).cueroFilete(theme, 16.dp), shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
        Row(Modifier.padding(12.dp)) {
            // ── Cover con badges ─────────────────────────────────────────────
            Box(modifier = Modifier.size(70.dp, (70 * 1.42f).dp)) {
                BookCover(book.coverUrl, book.title, size = 70, onBroken = onBroken, isbnFallback = book.isbn)

                // Widget badge — arriba izquierda
                if (isWidgetBook) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .offset(x = (-5).dp, y = (-5).dp)
                            .clip(CircleShape)
                            .background(acc)
                            .border(2.dp, theme.surface, CircleShape)
                            .align(Alignment.TopStart),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📺", fontSize = 10.sp, lineHeight = 10.sp)
                    }
                }

                // Refresh button — abajo izquierda
                if (onRefreshCover != null) {
                    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "refresh")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.LinearEasing)
                        ), label = "rot"
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .offset(x = (-5).dp, y = 5.dp)
                            .clip(CircleShape)
                            .background(actionFillColor(theme))
                            .border(2.dp, theme.surface, CircleShape)
                            .align(Alignment.BottomStart)
                            .clickable(enabled = !isRefreshingCover) { onRefreshCover() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh cover",
                            tint = Color.White,
                            modifier = Modifier
                                .size(13.dp)
                                .then(if (isRefreshingCover) Modifier.rotate(rotation) else Modifier)
                        )
                    }
                }

                // Warning badge — arriba derecha (sin portada encontrada)
                if (book.noCoverFound) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .offset(x = 5.dp, y = (-5).dp)
                            .clip(CircleShape)
                            .background(Amber)
                            .border(2.dp, theme.surface, CircleShape)
                            .align(Alignment.TopEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚠", fontSize = 10.sp, lineHeight = 10.sp)
                    }
                }

                // Change cover button — abajo derecha (mismo estilo que refresh)
                if (onApplyCoverUrl != null) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .offset(x = 5.dp, y = 5.dp)
                            .clip(CircleShape)
                            .background(acc)
                            .border(2.dp, theme.surface, CircleShape)
                            .align(Alignment.BottomEnd)
                            .clickable {
                                coverUrlInput = book.coverUrl ?: ""
                                showCoverDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.txt_adbe3283),
                            tint = onAccentColor(theme),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(book.title, color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (onDelete != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Red.copy(alpha = 0.12f))
                                .clickable { showDeleteConfirm = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.txt_b375487f),
                                // RF-M25: Red con alfa sobre su tinte era el patron de B-037; tinta medida
                                tint = wrappedInk(Red, theme),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        color = theme.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (book.genres.isNotEmpty()) Text(book.genres.map { displayGenre(it) }.joinToString(" · "), color = accentForTheme(theme).copy(alpha = 0.85f), fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val funcLabel = if (book.firstFunctionalPage != null && book.lastFunctionalPage != null)
                        " (${book.lastFunctionalPage - book.firstFunctionalPage + 1}p)"
                    else if (book.lastFunctionalPage != null)
                        stringResource(R.string.func_pages_upto, book.lastFunctionalPage)
                    else ""
                    Text("📄 ${book.pages} ${stringResource(R.string.word_pages_abbr)}$funcLabel", color = theme.textMuted, fontSize = 12.sp)
                    if (stats != null && (book.status == BookStatus.FINISHED || book.status == BookStatus.REREADING) && stats.pagesPerDay != null) {
                        // Con sesiones: media real por días con sesión. Sin sesiones (fechas
                        // añadidas a posteriori): stats.pagesPerDay (funcionales o totales / días).
                        val ppdDisplay = when {
                            sessionPages > 0 && sessionDays >= 1 -> sessionPages.toDouble() / sessionDays
                            sessionPages > 0 && stats.days > 0   -> sessionPages.toDouble() / stats.days
                            else -> stats.pagesPerDay
                        }
                        Text("⚡ ${String.format("%.1f", ppdDisplay)} p/d", color = theme.textMuted, fontSize = 12.sp)
                    }
                    if (stats != null && book.status == BookStatus.READING)
                        Text("⏳ ${fmtDaysLocalized(stats.days)}", color = theme.textMuted, fontSize = 12.sp)
                    if (book.status == BookStatus.REREADING)
                        Text(stringResource(R.string.txt_1ee4f1c7), color = wrappedInk(Color(0xFF06B6D4), theme), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                if (book.comment.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text("💬 ${book.comment}", color = theme.textDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (book.rating > 0) { Spacer(Modifier.height(4.dp)); MiniRating(book.rating) }
            }
        }
    }
}

@Composable
fun BookCover(url: String?, _title: String, size: Int, onBroken: (() -> Unit)? = null, isbnFallback: String? = null, author: String = "") {
    // Estado local para fallback: si la imagen principal falla y tenemos ISBN,
    // reintentamos con la URL OL por ISBN antes de marcar como rota
    var fallbackTriggered by remember(url) { mutableStateOf(false) }
    // Una URL de portada que existe pero no carga (404, imagen de relleno, host caído)
    // dejaba un HUECO VACÍO del tamaño exacto de la portada, sin nada dentro. Se veía en
    // los resultados del catálogo: las obras de la Biblioteca Nacional no traen cover_id,
    // así que caen en la URL por ISBN de Open Library, que para muchas no existe.
    // Al fallar la carga se pasa a la portada generada, igual que si no hubiera URL.
    var imagenRota by remember(url) { mutableStateOf(false) }
    val effectiveUrl: String? = when {
        url.isNullOrBlank() -> null
        fallbackTriggered && !isbnFallback.isNullOrBlank() -> {
            val clean = isbnFallback.replace(Regex("[-\\s]"), "")
            if (clean.matches(Regex("[0-9Xx]{10,13}"))) "https://covers.openlibrary.org/b/isbn/$clean-L.jpg"
            else null
        }
        else -> url
    }
    if (!effectiveUrl.isNullOrBlank() && !imagenRota) {
        // Si empieza por '/' es una ruta local al almacenamiento interno
        val isLocal = effectiveUrl.startsWith("/")
        val localFile = if (isLocal) java.io.File(effectiveUrl) else null
        val lastMod = localFile?.lastModified() ?: 0L
        // Cache-buster para portadas locales: si el archivo cambia (sobreescritura tras cambio
        // de portada) la key cambia y Coil recarga inmediatamente sin cerrar pantalla.
        val cacheKey = if (isLocal) "$effectiveUrl-$lastMod" else effectiveUrl
        val model = localFile ?: effectiveUrl
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onError = {
                // Si ya hemos probado el fallback ISBN o no hay ISBN → marcar rota
                if (fallbackTriggered || isbnFallback.isNullOrBlank()) {
                    imagenRota = true
                    onBroken?.invoke()
                } else {
                    fallbackTriggered = true
                }
            },
            onSuccess = { estado ->
                // Open Library NO devuelve 404 cuando no tiene portada: responde HTTP 200
                // con un GIF TRANSPARENTE DE 1x1 PÍXEL, y encima servido como .jpg.
                // Verificado el 21-07 con
                // covers.openlibrary.org/b/isbn/9798234101372-L.jpg → 200, 43 bytes, GIF 1x1.
                // Coil lo carga sin error, lo escala a 60dp y pinta un píxel transparente:
                // el resultado era un HUECO PERFECTO del tamaño de la portada, sin nada
                // dentro, y `onError` nunca se disparaba. Por eso hay que mirar el tamaño
                // real de lo que llega, no solo si la carga tuvo éxito.
                val d = estado.result.drawable
                if (d.intrinsicWidth in 1..2 || d.intrinsicHeight in 1..2) imagenRota = true
            },
            modifier = Modifier.size(size.dp, (size * 1.42f).dp).clip(RoundedCornerShape(8.dp))
        )
    } else {
        GeneratedCover(_title, author, isbnFallback, size)
    }
}

/**
 * Portada dibujada en el dispositivo para libros sin cubierta.
 *
 * Hace falta porque las cubiertas no se pueden empaquetar (copyright de las editoriales,
 * ninguna fuente las sublicencia) y las 250.000 obras que aporta la Biblioteca Nacional
 * llegan sin imagen. Antes se pintaba un degradado con un emoji de libro, igual para
 * todos: ocho resultados sin portada eran ocho rectángulos idénticos.
 *
 * El color sale del libro (ver [CoverPalette]), así que la misma obra tiene siempre la
 * misma portada en búsqueda, biblioteca y detalle. Nivel P2, elegido por Víctor.
 *
 * Es dibujo puro: ni disco, ni red, ni Canvas. Un Box con color de fondo y dos textos.
 */
@Composable
fun GeneratedCover(title: String, author: String, isbn: String?, size: Int) {
    val theme = LocalAppTheme.current
    val oscuro = theme?.isDark ?: true
    // Las dos tintas candidatas salen del propio tema, así que Cuero conserva sus cremas
    // cálidas y el tema Claro su tinta azulada en vez de un negro genérico.
    val inkClaro = if (oscuro) (theme?.textMain ?: Color.White) else Color.White
    val inkOscuro = if (oscuro) (theme?.bgDark ?: Color(0xFF14141F))
                    else (theme?.textMain ?: Color(0xFF26283A))

    val c = remember(title, author, isbn, inkClaro, inkOscuro) {
        CoverPalette.colorsFor(isbn, title, inkClaro.toArgb(), inkOscuro.toArgb())
    }
    val ink = Color(c.ink)
    // El autor solo cabe en portadas grandes: a tamaño 34 (la miniatura de la lista) su
    // texto saldría a 2,6sp, ilegible, y le robaría líneas al título, que es lo que de
    // verdad identifica el libro.
    val conAutor = author.isNotBlank() && size >= 50

    Box(
        Modifier
            .size(size.dp, (size * 1.42f).dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(c.background))
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = (size * 0.10f).dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = ink,
                fontSize = (size * 0.125f).sp,
                lineHeight = (size * 0.145f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                // Sin autor el título gana una línea en vez de dejar un hueco.
                maxLines = if (conAutor) 4 else 5,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (conAutor) {
                Spacer(Modifier.height((size * 0.07f).dp))
                Box(
                    Modifier
                        .size(width = (size * 0.26f).dp, height = (size * 0.02f).dp)
                        .background(ink.copy(alpha = 0.62f))
                )
                Spacer(Modifier.height((size * 0.07f).dp))
                Text(
                    author,
                    color = ink.copy(alpha = 0.82f),
                    fontSize = (size * 0.076f).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        // Lomo: da lectura de libro y rompe la sensación de etiqueta de color plana.
        Box(
            Modifier
                .fillMaxHeight()
                .width((size * 0.05f).dp)
                .background(Color(c.spine).copy(alpha = 0.34f))
        )
    }
}

@Composable
fun MiniRating(rating: Int) {
    // RF-M25: Gold crudo no llega a AA sobre superficies claras. MiniRating es un composable
    // hoja sin `theme`, asi que se resuelve por LocalAppTheme (mismo caso que BookCover).
    val goldInk = LocalAppTheme.current?.let { wrappedInk(Gold, it) } ?: Gold
    Row(verticalAlignment = Alignment.CenterVertically) {
        val filledStars = ((rating + 1) / 2).coerceIn(0, 5)
        Text(
            "★".repeat(filledStars) + "☆".repeat(5 - filledStars),
            color = goldInk,
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.width(4.dp))
        Text("$rating/10", color = goldInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusBadge(status: BookStatus) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.background(statusColor(status), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(statusLabel(status), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FooterStats(finished: List<Book>, _theme: Theme) {
    val total = finished.sumOf { it.pages }
    // Exclude same-day books (startDate == endDate) from speed average
    val speedBooks = finished.filter { b -> b.startDate != null && b.endDate != null && b.startDate != b.endDate && daysBetween(b.startDate, b.endDate) >= 2 }
    val avg = if (speedBooks.isNotEmpty()) speedBooks.map { b -> b.pages.toDouble() / daysBetween(b.startDate!!, b.endDate!!).coerceAtLeast(1) }.average() else null
    val rated = finished.filter { it.rating > 0 }
    val avgRating = if (rated.isNotEmpty()) rated.map { it.rating }.average() else null
    Surface(shape = RoundedCornerShape(16.dp), color = accentForTheme(_theme).copy(alpha = 0.1f), border = BorderStroke(1.dp, accentForTheme(_theme).copy(alpha = 0.2f))) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.txt_9096e7e0), color = accentForTheme(_theme), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                FooterStat("${finished.size}", stringResource(R.string.pill_leidos), Modifier.weight(1f))
                FooterStat(total.toLocaleString(), stringResource(R.string.txt_939f09a3), Modifier.weight(1f))
                FooterStat(if (avg != null) String.format("%.1f", avg) else "-", stringResource(R.string.pill_pags_dia), Modifier.weight(1f))
                if (avgRating != null) FooterStat(String.format("%.1f", avgRating) + "/10", stringResource(R.string.pill_punt), Modifier.weight(1f))
            }
        }
    }
}

fun Int.toLocaleString() = String.format("%,d", this).replace(',', '.')

@Composable
fun FooterStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextMainD, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextDimD, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

// ── AuthorBooksScreen ─────────────────────────────────────────────────────────

@Composable
fun AuthorBooksScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, author: String, onBack: () -> Unit, onDetail: (Long) -> Unit) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val authorBooks = books.filter { it.author.equals(author, ignoreCase = true) }
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    val byStatus = linkedMapOf(
        BookStatus.READING   to authorBooks.filter { it.status == BookStatus.READING }.applySort(sortOrder),
        BookStatus.REREADING to authorBooks.filter { it.status == BookStatus.REREADING }.applySort(sortOrder),
        BookStatus.FINISHED  to authorBooks.filter { it.status == BookStatus.FINISHED }.applySort(sortOrder),
        BookStatus.PENDING   to authorBooks.filter { it.status == BookStatus.PENDING }.applySort(sortOrder),
        BookStatus.DROPPED   to authorBooks.filter { it.status == BookStatus.DROPPED }.applySort(sortOrder)
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(author, color = theme.textMain, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (authorBooks.size == 1) stringResource(R.string.shelf_books_count, authorBooks.size) else stringResource(R.string.shelf_books_count_plural, authorBooks.size), color = theme.textMuted, fontSize = 13.sp)
            }
            Box {
                IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Filled.Sort, null, tint = theme.textMuted) }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(text = { Text(sortLabel(order), color = if (sortOrder == order) accentForTheme(theme) else theme.textMain) }, onClick = { sortOrder = order; showSortMenu = false })
                    }
                }
            }
        }
        LazyColumn {
            byStatus.forEach { (status, list) ->
                if (list.isNotEmpty()) {
                    item { SectionHeader(statusLabel(status), list.size, statusColor(status), true) {} }
                    items(list) { book ->
                        val context = LocalContext.current
                        val widgetBookId = com.lecturameter.widget.loadWidgetBook(context)
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
                            sessionPages = vm.sessionsForBook(book.id).sumOf { it.pages },
                            sessionDays = vm.sessionsForBook(book.id).map { it.date }.toSet().size
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
