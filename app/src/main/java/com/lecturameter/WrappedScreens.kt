package com.lecturameter

// Wrapped: pantalla anual, tarjetas e historial.
// Extraido de MainActivity.kt el 15-07-2026 (ruptura del monolito, sin cambios funcionales).


import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.*
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.widget.Toast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import com.lecturameter.model.*
import com.lecturameter.utils.*
import com.lecturameter.utils.WrappedPalette.Slot
import androidx.navigation.compose.composable

// ── WrappedScreen ─────────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WrappedScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, year: Int, onBack: () -> Unit) {
    // D-004: books/sessions son StateFlow; se coleccionan en la raiz de la pantalla
    val books by vm.books.collectAsState()
    val sessions by vm.sessions.collectAsState()
    // v2.6 (Wrapped r1): años cerrados con snapshot guardado → usar el snapshot
    // (formato/datos exactos de esa edición). Año en curso O año dentro de su
    // ventana wrapped (1–26 ene el año anterior sigue "vivo") → cálculo fresco,
    // para que el auto-guardado de la ventana siga refrescando datos.
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    // D-004: wrappedHistory es StateFlow; se colecciona en la raíz de la pantalla
    val wrappedHistory by vm.wrappedHistory.collectAsState()
    val wrapped = remember(year, books, wrappedHistory) {
        val inOwnWindow = isInWrappedWindow() && wrappedWindowYear() == year
        if (year < currentYear && !inOwnWindow) vm.wrappedForYear(year) ?: vm.computeWrapped(year)
        else vm.computeWrapped(year)
    }
    // v2.6: favoritos CONGELADOS del año (permanentes; ver favoritesForWrapped)
    val favBooks = remember(wrapped, books) {
        if (wrapped != null) vm.favoritesForWrapped(year, prefs) else emptyList()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    // Fase 6.3: tarjeta anual del Bingo — solo si el año tiene casillas registradas
    // en el historial mensual (se acumula desde esta versión; regla: no inventar)
    val bingoYear = remember(year) {
        com.lecturameter.utils.BingoManager.loadMonthSummaries(prefs).filter { it.monthKey.startsWith("$year-") && it.cellsDone > 0 }
    }
    val hasBingoSlide = bingoYear.isNotEmpty()
    // 0..9 clásicas + Bingo anual (condicional, 6.3) + cierre-comparativa (P-015)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { if (wrapped != null) 11 + (if (hasBingoSlide) 1 else 0) else 1 }
    // v2.5: coordenadas del pager para recortar screenshot (excluir barra superior)
    var pagerBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }

    // Auto-guardar al abrir durante la ventana
    LaunchedEffect(wrapped) {
        if (wrapped != null && isInWrappedWindow() && wrappedWindowYear() == year) {
            vm.saveWrappedForYear(wrapped, prefs)
        }
    }

    // v2.5: share captura real de pantalla (PixelCopy) — sin menús
    fun shareScreenshot() {
        if (sharing) return
        sharing = true
        scope.launch {
            try {
                val activity = context as? android.app.Activity ?: throw Exception("No Activity")
                val window = activity.window
                val rootView = window.decorView
                val fullBitmap = android.graphics.Bitmap.createBitmap(rootView.width, rootView.height, android.graphics.Bitmap.Config.ARGB_8888)
                val captured = kotlinx.coroutines.suspendCancellableCoroutine<android.graphics.Bitmap> { cont ->
                    android.view.PixelCopy.request(window, fullBitmap, { result ->
                        if (result == android.view.PixelCopy.SUCCESS) cont.resume(fullBitmap) {}
                        else cont.resumeWithException(Exception("PixelCopy error $result"))
                    }, android.os.Handler(android.os.Looper.getMainLooper()))
                }
                val file = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val cropped = pagerBounds?.let { b ->
                        val x = b.left.coerceIn(0, captured.width - 1)
                        val y = b.top.coerceIn(0, captured.height - 1)
                        val w = b.width().coerceAtMost(captured.width - x)
                        val h = b.height().coerceAtMost(captured.height - y)
                        if (w > 0 && h > 0) android.graphics.Bitmap.createBitmap(captured, x, y, w, h) else captured
                    } ?: captured
                    val f = java.io.File(context.cacheDir, "wrapped_${year}_s${pagerState.currentPage}.png")
                    java.io.FileOutputStream(f).use { out -> cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
                    if (cropped !== captured) cropped.recycle()
                    captured.recycle()
                    f
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.wrapped_share_text, year))
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.wcard_dialog_title)))
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, context.getString(R.string.msg_share_error, e.message), android.widget.Toast.LENGTH_SHORT).show()
            } finally { sharing = false }
        }
    }

    // Theming: acento del tema activo (índigo por defecto; oro en Cuero, morado en Aurora)
    val acc = accentForTheme(theme)

    // v2.4: fondo con glow degradado (funciona sobre tema claro y oscuro)
    // B-037: el glow iba en índigo→violeta FIJO en los cinco temas. En Claro teñía el papel
    // de LAVANDA, que es exactamente el fondo del que el tema huyó en la r2 ("el Claro era el
    // Oscuro invertido: lavanda frío"). Ahora sigue al acento. En Oscuro no cambia nada:
    // accentGradient devuelve allí el par histórico Accent→Accent2.
    val glow = accentGradient(theme)
    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(glow[0].copy(alpha = 0.16f), Color.Transparent, glow[1].copy(alpha = 0.12f)))
    )) {
        // ── TOP BAR ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.wrapped_year_header, year), color = theme.textMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (wrapped != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Feedback 15-07 (reincidencia): el "repeat(10)" fijo dejaba sin punto
                        // a la slide de cierre-comparativa (P-015) y, con Bingo, también a esa
                        // extra. Ahora sigue siempre el nº real de páginas del pager.
                        repeat(pagerState.pageCount) { i ->
                            Box(Modifier.size(if (i == pagerState.currentPage) 8.dp else 5.dp)
                                .clip(CircleShape)
                                .background(if (i == pagerState.currentPage) acc else theme.border))
                        }
                    }
                }
            }
            if (wrapped != null) {
                // v2.6: guardar simulación en historial (solo si este año aún no está guardado)
                // Feedback 15-07 ("el guardado sobra"): en la slide 8 (CIERRE, el resumen final
                // de páginas/libros/racha/récord/autor/sin terminar) el icono de guardar sobra
                // — es el único candidato de guardado/compartir de esa slide y ahí se oculta;
                // en el resto de slides se conserva tal cual.
                if (vm.wrappedForYear(year) == null && pagerState.currentPage != 8) {
                    IconButton(onClick = {
                        vm.saveWrappedForYear(wrapped, prefs)
                        android.widget.Toast.makeText(context,
                            context.getString(R.string.wrapped_sim_saved, year),
                            android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = stringResource(R.string.wrapped_sim_save), tint = Amber)
                    }
                }
                // v2.4 rework: el slide de favoritos no tiene tarjeta share (renderer 0-6)
                IconButton(onClick = { shareScreenshot() }, enabled = !sharing) {
                    Icon(if (sharing) Icons.Default.Refresh else Icons.Default.Share, null,
                        tint = if (sharing) theme.textDim else actionIconTint(theme))
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        if (wrapped == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📚", fontSize = 48.sp); Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.wrapped_no_finished, year.toString()), color = theme.textMain, fontSize = 17.sp)
                    Text(stringResource(R.string.wrapped_empty_hint, year), color = theme.textDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp))
                }
            }
            return@Box
        }

        // ── PAGER ──────────────────────────────────────────────────────────
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(top = 60.dp)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    pagerBounds = android.graphics.Rect(
                        pos.x.toInt(), pos.y.toInt(),
                        (pos.x + coords.size.width).toInt(),
                        (pos.y + coords.size.height).toInt()
                    )
                }
        ) { page ->
            val sm = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)

            when (page) {

                // ── SLIDE 0: RESUMEN ──────────────────────────────────────────
                0 -> Column(sm) {
                    // Fondo temático del slide (superpuesto al glow global)
                    Spacer(Modifier.height(4.dp))
                    WrappedNarrative(stringResource(R.string.wrapped_narr_1), theme)
                    // Año protagonista con gradiente
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(wrappedSlabFor(Slot.YEAR, theme))),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                            // El "2026" recupera su degradado (en Oscuro, el #818CF8 -> #22D3EE de siempre).
                            Text("${wrapped.year}", fontSize = 80.sp, fontWeight = FontWeight.Black,
                                style = androidx.compose.ui.text.TextStyle(
                                    brush = Brush.horizontalGradient(wrappedHeroFor(Slot.YEAR, theme))
                                ))
                            Text(stringResource(R.string.wcard_subtitle), color = onSlabMutedFor(Slot.YEAR, theme), fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // 2 stats enormes
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WrappedBigCard(wrapped.totalPages.toLocaleString(), stringResource(R.string.wcard_pages), theme,
                            Slot.YEAR, Modifier.weight(1f))
                        WrappedBigCard("${wrapped.totalBooks}", stringResource(R.string.wcard_books), theme,
                            Slot.YEAR_ALT, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    // 3 mini stats
                    val hTot = wrapped.totalMinutes / 60; val mTot = wrapped.totalMinutes % 60
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WrappedMiniCard(if (hTot > 0) "${hTot}h" else "${wrapped.totalMinutes}m", stringResource(R.string.wcard_hours), Amber, theme, Modifier.weight(1f))
                        WrappedMiniCard("${wrapped.longestStreakDays}d", stringResource(R.string.wcard_streak), Green, theme, Modifier.weight(1f))
                        WrappedMiniCard("${wrapped.totalSessions}", stringResource(R.string.wcard_sessions), Sky, theme, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    // Autor + género
                    if (wrapped.favoriteAuthor.isNotBlank()) {
                        WrappedFavRow("", stringResource(R.string.wcard_author_year), wrapped.favoriteAuthor,
                            androidx.compose.ui.res.pluralStringResource(R.plurals.wrapped_book_count, wrapped.favoriteAuthorBooks, wrapped.favoriteAuthorBooks), Accent2, theme)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (wrapped.favoriteGenre.isNotBlank()) {
                        WrappedFavRow("", stringResource(R.string.wcard_genre_year), displayGenre(wrapped.favoriteGenre),
                            androidx.compose.ui.res.pluralStringResource(R.plurals.wrapped_book_count, wrapped.favoriteGenreBooks, wrapped.favoriteGenreBooks), acc, theme)
                    }
                    if (wrapped.longestStreakDays > 0) {
                        Spacer(Modifier.height(12.dp))
                        Surface(shape = RoundedCornerShape(16.dp), color = Red.copy(0.12f),
                            border = BorderStroke(1.dp, wrappedInk(Red, theme).copy(0.35f)), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🔥", fontSize = 28.sp); Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(stringResource(R.string.txt_362b636c), color = wrappedInk(Red, theme), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.wrapped_streak_full, wrapped.longestStreakDays, wrapped.longestStreakDays), color = theme.textMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 1: TIEMPO ───────────────────────────────────────────
                1 -> Column(sm) {
                    WrappedNarrative(stringResource(R.string.wrapped_narr_2), theme)
                    // Feedback WhatsApp 10-07: header violeta profundo (antes marrón/ámbar v2.6,
                    // "cambiar el marrón naranja ese por un color más bonito"). B-037: el violeta
                    // fijo se va con el resto de las losas; ahora la cabecera es del tema.
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(wrappedSlabFor(Slot.HOURS, theme))),
                        contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // v2.4 rework: eliminado emoji decorativo de cabecera
                            val hW = wrapped.totalMinutes / 60; val mW = wrapped.totalMinutes % 60
                            Text(if (hW > 0) "${hW}h ${mW}m" else "${mW}m",
                                fontSize = 64.sp, fontWeight = FontWeight.Black,
                                style = androidx.compose.ui.text.TextStyle(
                                    brush = Brush.horizontalGradient(wrappedHeroFor(Slot.HOURS, theme))
                                ))
                            Text(stringResource(R.string.wcard_hours), color = onSlabMutedFor(Slot.HOURS, theme), fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WrappedBigCard("${wrapped.totalSessions}", stringResource(R.string.wcard_sessions), theme,
                            Slot.HOURS_ALT, Modifier.weight(1f))
                        if (wrapped.maxSessionPages > 0)
                            WrappedBigCard("${wrapped.maxSessionPages}", stringResource(R.string.wrapped_record_session), theme,
                                Slot.HOURS_ALT, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    // Top libros por tiempo
                    if (wrapped.longestBooksTop3.isNotEmpty()) {
                        Text(stringResource(R.string.txt_1db69449), color = wrappedInk(Sky, theme), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(8.dp))
                        val maxM = wrapped.longestBooksTop3.maxOf { it.second }
                        wrapped.longestBooksTop3.forEachIndexed { i, (title, mins) ->
                            val hB = mins / 60; val mB = mins % 60
                            Surface(shape = RoundedCornerShape(14.dp), color = Sky.copy(0.08f),
                                border = BorderStroke(1.dp, wrappedInk(Sky, theme).copy(0.3f)), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(wrappedMedal(i), fontSize = 20.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(title, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(if (hB > 0) "${hB}h ${mB}m" else "${mB}m", color = wrappedInk(Sky, theme), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressBar(mins.toFloat() / maxM, wrappedInk(Sky, theme))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (wrapped.mostReadDay.isNotBlank() && wrapped.mostReadDayPages > 0) {
                        Spacer(Modifier.height(4.dp))
                        Surface(shape = RoundedCornerShape(14.dp), color = acc.copy(0.08f),
                            border = BorderStroke(1.dp, wrappedInk(acc, theme).copy(0.3f)), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("📅", fontSize = 24.sp); Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(stringResource(R.string.wrapped_most_read_day, fmtDate(wrapped.mostReadDay), wrapped.mostReadDayPages),
                                        color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 2: TOPS ─────────────────────────────────────────────
                2 -> Column(sm) {
                    WrappedNarrative(stringResource(R.string.wrapped_narr_3), theme)
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(wrappedSlabFor(Slot.TOPS, theme))),
                        contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // v2.4 rework: eliminado emoji decorativo de cabecera
                            Text(stringResource(R.string.txt_a6f46d56), color = onSlabTitleFor(Slot.TOPS, theme),
                                fontSize = 32.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (wrapped.favoriteAuthor.isNotBlank())
                            WrappedBigCard(wrapped.favoriteAuthor, androidx.compose.ui.res.pluralStringResource(R.plurals.wrapped_book_count, wrapped.favoriteAuthorBooks, wrapped.favoriteAuthorBooks), theme,
                                Slot.TOPS_AUTHOR, Modifier.weight(1f), maxLines = 2)
                        if (wrapped.favoriteGenre.isNotBlank())
                            WrappedBigCard(displayGenre(wrapped.favoriteGenre), androidx.compose.ui.res.pluralStringResource(R.plurals.wrapped_book_count, wrapped.favoriteGenreBooks, wrapped.favoriteGenreBooks), theme,
                                Slot.TOPS_GENRE, Modifier.weight(1f), maxLines = 2)
                    }
                    Spacer(Modifier.height(12.dp))
                    val medals = listOf("🥇","🥈","🥉")
                    if (wrapped.topAuthorsTop3.isNotEmpty()) {
                        Text(stringResource(R.string.wcard_authors), color = wrappedInk(Accent2, theme), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(6.dp))
                        wrapped.topAuthorsTop3.forEachIndexed { i, (name, n) ->
                            WrappedTop3Row(medals[i], name, androidx.compose.ui.res.pluralStringResource(R.plurals.wrapped_book_count, n, n), i == 0, Accent2, theme)
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (wrapped.topGenresTop3.isNotEmpty()) {
                        Text(stringResource(R.string.wcard_genres), color = wrappedInk(acc, theme), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(6.dp))
                        wrapped.topGenresTop3.forEachIndexed { i, (name, n) ->
                            WrappedTop3Row(medals[i], displayGenre(name), androidx.compose.ui.res.pluralStringResource(R.plurals.wrapped_book_count, n, n), i == 0, acc, theme)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 3: MEJOR Y MÁS RÁPIDO ──────────────────────────────
                3 -> Column(sm) {
                    // Feedback 15-07 ("por qué sólo 1 si hay varios"): los snapshots de Wrapped
                    // guardados antes de v19.x/v21.41 no tienen bestRatedTop3/fastestBooksTop3
                    // (Gson los deserializa como lista vacía) y solo guardan el título/nota
                    // legacy de UN libro. Si eso pasa pero hay más libros reales de ese año que
                    // cumplen el mismo criterio (nota > 0 / velocidad calculable), se reconstruye
                    // la lista desde los libros reales — mismo criterio que computeWrapped,
                    // sin inventar datos — en vez de enseñar 1 sola tarjeta con hueco vacío.
                    val yearFinished = remember(wrapped.year, books) {
                        books.filter {
                            it.status == BookStatus.FINISHED && it.endDate != null && it.startDate != null &&
                                it.endDate.startsWith(wrapped.year.toString()) && !it.importedFromGoodreads
                        }
                    }
                    val top3 = wrapped.bestRatedTop3.ifEmpty {
                        yearFinished.filter { it.rating > 0 }
                            .sortedWith(compareByDescending<Book> { it.rating }.thenByDescending { it.endDate ?: "" })
                            .take(3)
                            .map { Triple(it.title, it.rating, it.endDate ?: "") }
                            .ifEmpty {
                                if (wrapped.bestRatedTitle.isNotBlank()) listOf(Triple(wrapped.bestRatedTitle, wrapped.bestRatedScore, "")) else emptyList()
                            }
                    }
                    WrappedNarrative(stringResource(R.string.wrapped_narr_4), theme)
                    if (top3.isNotEmpty()) {
                        // Hero: libro nº1 — v2.6: card compacta (padding/portada/número reducidos)
                        // + degradado dorado visible, simétrico al verde de Fastest Book.
                        // B-037: el oro se CONSERVA (aquí significa "mejor puntuado", no es
                        // decoración), pero la losa se deriva de él en vez de ir a un marrón fijo.
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(wrappedSlabFor(Slot.RATED, theme))),
                            contentAlignment = Alignment.Center) {
                            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                val bestBook = books.firstOrNull { it.title == top3[0].first }
                                if (bestBook != null) {
                                    BookCover(bestBook.coverUrl, bestBook.title, size = 64, isbnFallback = bestBook.isbn)
                                    Spacer(Modifier.height(6.dp))
                                }
                                Text("${top3[0].second}/10", fontSize = 30.sp, fontWeight = FontWeight.Black,
                                    style = androidx.compose.ui.text.TextStyle(
                                        brush = Brush.horizontalGradient(wrappedHeroFor(Slot.RATED, theme))
                                    ))
                                Text(top3[0].first, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                Text(stringResource(R.string.wrapped_mejor_puntuado), color = onSlabMutedFor(Slot.RATED, theme), fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        top3.drop(1).forEachIndexed { i, (title, score, _) ->
                            val medal = if (i == 0) "🥈" else "🥉"
                            Surface(shape = RoundedCornerShape(14.dp), color = Gold.copy(0.08f),
                                border = BorderStroke(1.dp, wrappedInk(Gold, theme).copy(0.35f)), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(medal, fontSize = 24.sp); Spacer(Modifier.width(10.dp))
                                    Text(title, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$score/10", color = wrappedInk(Gold, theme), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    // v2.5: top 3 libros más rápidos (antes solo 1)
                    // Mismo fallback que bestRatedTop3: reconstruir desde libros reales
                    // (multi-día, mismo criterio que computeWrapped) si el snapshot es legacy.
                    val fastTop3 = wrapped.fastestBooksTop3.ifEmpty {
                        yearFinished.filter { it.startDate != it.endDate && daysBetween(it.startDate!!, it.endDate!!) >= 2 }
                            .map { b -> Triple(b.title, b.pages.toDouble() / daysBetween(b.startDate!!, b.endDate!!), b.pages) }
                            .sortedByDescending { it.second }
                            .take(3)
                            .ifEmpty {
                                if (wrapped.fastestBookTitle.isNotBlank()) listOf(Triple(wrapped.fastestBookTitle, wrapped.fastestBookPpd, wrapped.fastestBookPages)) else emptyList()
                            }
                    }
                    if (fastTop3.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        // Hero: libro más rápido — v2.6: card compacta (simétrica a Best Rated).
                        // B-037: el verde se CONSERVA (significa "más rápido"); la losa se deriva de él.
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(wrappedSlabFor(Slot.FASTEST, theme))),
                            contentAlignment = Alignment.Center) {
                            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                val fastBook = books.firstOrNull { it.title == fastTop3[0].first }
                                if (fastBook != null) {
                                    BookCover(fastBook.coverUrl, fastBook.title, size = 60, isbnFallback = fastBook.isbn)
                                    Spacer(Modifier.height(6.dp))
                                }
                                Text("${String.format("%.1f", fastTop3[0].second)} p/d", fontSize = 28.sp, fontWeight = FontWeight.Black,
                                    style = androidx.compose.ui.text.TextStyle(
                                        brush = Brush.horizontalGradient(wrappedHeroFor(Slot.FASTEST, theme))
                                    ))
                                Text(fastTop3[0].first, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.wrapped_libro_mas_rapido), color = onSlabMutedFor(Slot.FASTEST, theme), fontSize = 11.sp)
                            }
                        }
                        // 2º y 3º más rápidos
                        if (fastTop3.size > 1) {
                            Spacer(Modifier.height(10.dp))
                            fastTop3.drop(1).forEachIndexed { i, (title, ppd, _) ->
                                val medal = if (i == 0) "🥈" else "🥉"
                                Surface(shape = RoundedCornerShape(14.dp), color = Green.copy(0.08f),
                                    border = BorderStroke(1.dp, Green.copy(0.25f)), modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(medal, fontSize = 24.sp); Spacer(Modifier.width(10.dp))
                                        Text(title, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${String.format("%.1f", ppd)} p/d", color = wrappedInk(Green, theme), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    // Feedback 2.6: la card de libros abandonados se movió a la slide de
                    // cierre — esta slide es la de mejores/más rápidos.
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 4: GRÁFICA ──────────────────────────────────────────
                4 -> Column(sm) {
                    WrappedNarrative(stringResource(R.string.wrapped_narr_5), theme)
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(wrappedSlabFor(Slot.CHART, theme))),
                        contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // v2.4 rework: eliminado emoji decorativo de cabecera
                            Text(stringResource(R.string.txt_bd81d36d), color = onSlabTitleFor(Slot.CHART, theme), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // Barras mensuales rediseñadas
                    if (wrapped.pagesPerMonth.sum() > 0) {
                        // B-037: la tarjeta era un #0F2027 fijo, o sea una tarjeta de noche sobre el
                        // papel. Pasa a la superficie del tema (blanca en Claro, como el resto de la app).
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = theme.surface, border = BorderStroke(1.dp, acc.copy(alpha = 0.13f)),
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                val maxP = wrapped.pagesPerMonth.max().coerceAtLeast(1)
                                // Feedback 15-07: la escala VUELVE A SER LINEAL DESDE 0.
                                //
                                // Historia de esto: era lineal, alguien reportó que 450 y 415 págs
                                // salían casi iguales (100% vs 92%) y se "arregló" anclando el mínimo
                                // primero al 25% y luego al 55% del alto. Pero 450 y 415 SON casi
                                // iguales: el gráfico no estaba roto, lo estaba la expectativa. Con el
                                // ancla al 55%, un mes de 10 págs y otro de 450 se dibujaban al 55% y al
                                // 100% — 45x de diferencia real pintada como 2x. Las barras mentían.
                                //
                                // Un gráfico de barras compara ÁREAS: si la base no es 0, la comparación
                                // visual es falsa. Para distinguir meses parecidos ya está el número
                                // encima de cada barra y el resalte del máximo; eso informa sin engañar.
                                val months = listOf("E","F","M","A","M","J","J","A","S","O","N","D")
                                val bestIdx = wrapped.pagesPerMonth.indexOf(maxP)
                                // B3 fase B (variante B1): el máximo ya iba en cian, pero contra 11
                                // barras moradas de intensidad idéntica no saltaba — competía con
                                // todas a la vez. Se resalta con TRES refuerzos que no tocan la
                                // escala: opacidad por ranking (el podio al 75%, el resto al 45%,
                                // así el máximo es lo único a plena intensidad), la etiqueta
                                // "MEJOR MES" sobre su barra, y su letra del eje en cian.
                                // Ninguno cambia la altura de ninguna barra: sigue siendo lineal
                                // desde 0 (ver el comentario de arriba; eso no se negocia).
                                val podium = wrapped.pagesPerMonth.withIndex()
                                    .filter { it.value > 0 }.sortedByDescending { it.value }
                                    .take(3).map { it.index }.toSet()
                                // El alto del hueco de etiquetas se RESERVA fuera del alto de las
                                // barras: si no, la barra del máximo (que ocupa el 100%) más su
                                // etiqueta encima se salen de la Row y el número se recorta.
                                val chartH = 200.dp
                                val labelH = 26.dp
                                val barMaxH = chartH - labelH
                                Row(Modifier.fillMaxWidth().height(chartH), verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    wrapped.pagesPerMonth.forEachIndexed { i, p ->
                                        // Mínimo visible del 2% para que un mes con 1-2 págs no
                                        // desaparezca del todo y parezca un mes sin leer.
                                        val ratio = if (p <= 0) 0f
                                                    else (p.toFloat() / maxP).coerceAtLeast(0.02f)
                                        val isMax = i == bestIdx
                                        // El desvanecido es SOLO de la barra. El número de encima es
                                        // texto y no se pinta con alfa (B-037: mezclar el color con el
                                        // fondo antes de leerlo es la causa raíz de aquello); su
                                        // jerarquía va por tamaño y peso, que es lo que toca.
                                        val barAlpha = when {
                                            isMax -> 1f
                                            i in podium -> 0.75f
                                            else -> 0.45f
                                        }
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Bottom) {
                                            // Feedback 17-07: la etiqueta "MEJOR MES" flotante encima de la
                                            // barra máxima se apilaba sobre el número y empujaba la barra hacia
                                            // abajo (el hueco reservado, labelH, solo contaba UN número, no la
                                            // etiqueta extra), así que el máximo salía MÁS BAJO que el segundo
                                            // y encima quedaba feo apretado. El máximo ya se distingue de sobra
                                            // por el número en cian y negrita, la letra del eje en cian y la
                                            // frase "Tu mejor mes fue..." bajo la gráfica. Fuera la etiqueta.
                                            if (p > 0)
                                                Text(if (p >= 1000) "${p/1000}k" else "$p",
                                                    // Feedback 17-07: el número también en el acento del tema.
                                                    color = wrappedInk(acc, theme),
                                                    fontSize = if (isMax) 9.sp else 7.sp,
                                                    fontWeight = if (isMax || i in podium) FontWeight.Black else FontWeight.Normal)
                                            Spacer(Modifier.height(2.dp))
                                            Box(Modifier.fillMaxWidth()
                                                .height((barMaxH * ratio.coerceAtLeast(if (p > 0) 0.03f else 0f)))
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .alpha(barAlpha)
                                                // Feedback 17-07: las barras adoptan el ACENTO DEL TEMA (oro en
                                                // Cuero, morado en Aurora, índigo en Oscuro…) en vez de la paleta
                                                // fija cian/violeta, que desentonaba sobre Cuero. El mejor mes
                                                // destaca por la opacidad plena (barAlpha=1 vs 0.45 del resto) más
                                                // el número y la letra del eje. wrappedGraphic sigue garantizando
                                                // el contraste 3:1 del gráfico sobre la tarjeta de cada tema.
                                                .background(Brush.verticalGradient(listOf(
                                                    wrappedGraphic(acc, theme),
                                                    wrappedGraphic(acc, theme).copy(0.5f)))))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    months.forEachIndexed { i, m ->
                                        val isMax = i == bestIdx
                                        Text(m, color = if (isMax) wrappedInk(acc, theme) else theme.textMuted,
                                            fontSize = 9.sp,
                                            fontWeight = if (isMax) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                    }
                                }
                                val monthNames = LocalContext.current.resources.getStringArray(R.array.month_names_full).toList()
                                if (bestIdx >= 0) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(stringResource(R.string.wrapped_best_month, monthNames.getOrElse(bestIdx) { "" }, maxP),
                                        color = wrappedInk(acc, theme), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // Donut géneros
                    if (wrapped.genreCountsTop6.isNotEmpty()) {
                        // B-037 r2: el donut es GRÁFICO (mínimo 3:1) y se pinta sobre la tarjeta,
                        // que en Claro es BLANCA. Crudos, cinco de las seis porciones bajaban de
                        // 3:1 ahí (el ámbar, 2,15:1) y dos en Aurora.
                        val gColors = listOf(Accent, Green, Sky, Amber, Red, Accent2).map { wrappedGraphic(it, theme) }
                        val totalG = wrapped.genreCountsTop6.sumOf { it.second }
                        Surface(shape = RoundedCornerShape(18.dp), color = theme.surface,
                            border = BorderStroke(1.dp, acc.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                DonutChart(wrapped.genreCountsTop6.map { it.second }, gColors, Modifier.size(100.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    wrapped.genreCountsTop6.forEachIndexed { i, (g, n) ->
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(gColors[i % gColors.size]))
                                            Text("${displayGenre(g).take(16)} · $n (${if (totalG > 0) n * 100 / totalG else 0}%)",
                                                color = theme.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 5: TU MEJOR DÍA DE CADA MES (v2.6) ─────────────────
                5 -> Column(sm) {
                    WrappedNarrative(stringResource(R.string.wrapped_narr_6), theme)
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(wrappedSlabFor(Slot.BESTDAY, theme))),
                        contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.wrapped_bestday_title), color = onSlabTitleFor(Slot.BESTDAY, theme),
                                fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (wrapped.bestDayPerMonth.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            WrappedEmptyState("📅", theme)
                        }
                    } else {
                        val monthNames = LocalContext.current.resources.getStringArray(R.array.month_names_full).toList()
                        // Feedback 2.6: todos los días son clicables — el desglose de abajo
                        // muestra el día seleccionado (por defecto, el mejor del año).
                        var selectedDay by remember(wrapped) { mutableStateOf(wrapped.mostReadDay) }
                        // B3 fase B (variante A1): los 12 meses pasan de FILAS a REJILLA de 3
                        // columnas. Motivo duro, medido en el emulador: 12 filas de 48dp ocupaban
                        // 648dp de los 665 de contenido, o sea que diciembre y el desglose entero
                        // se iban de pantalla y había que scrollear. La rejilla baja a ~298dp y la
                        // slide entra de un golpe de vista, que es el objetivo de esta pantalla.
                        //
                        // Se conserva todo lo demás: cada mes sigue siendo clicable, el desglose
                        // de abajo sigue el mes seleccionado y el mejor del año sigue de partida.
                        // Cae el año de la fecha ("6 jun" en vez de "6 junio 2026"): lo dice la
                        // cabecera, y repetirlo 12 veces era lo que no dejaba encoger la celda.
                        //
                        // Rejilla a mano (Column de Rows) y no LazyVerticalGrid: esta slide vive
                        // dentro de un verticalScroll y una lazy grid ahí revienta por alto infinito.
                        wrapped.bestDayPerMonth.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { (m, date, pages) ->
                                    val isSelected = date == selectedDay
                                    // Feedback 17-07: SOLO el mes seleccionado se resalta en cian. Antes el
                                    // "mejor del año" (isBest) llevaba ADEMÁS un tinte cian permanente, así
                                    // que al tocar otro mes el mejor (p. ej. junio) se quedaba marcado en azul
                                    // y parecía seleccionado a la vez que el nuevo. El mejor sigue siendo la
                                    // selección de partida al abrir la slide; eso ya lo señala de sobra.
                                    Surface(
                                        onClick = { selectedDay = date },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) Cyan.copy(0.22f) else acc.copy(0.06f),
                                        border = BorderStroke(if (isSelected) 2.dp else 1.dp,
                                            if (isSelected) wrappedInk(Cyan, theme) else acc.copy(0.18f)),
                                        modifier = Modifier.weight(1f)) {
                                        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                                            Text(monthNames.getOrElse(m) { "" },
                                                color = if (isSelected) wrappedInk(Cyan, theme) else theme.textMain,
                                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(fmtDateShort(date),
                                                color = if (isSelected) theme.textMain else theme.textMuted,
                                                fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            // La maqueta dejaba la celda en un número pelado; se
                                            // conserva la unidad porque cabe de sobra a este ancho
                                            // y sin ella "38" no dice de qué es.
                                            Text(stringResource(R.string.wrapped_fav_pages, pages),
                                                color = if (isSelected) wrappedInk(Cyan, theme) else wrappedInk(Accent2, theme),
                                                fontSize = 12.sp, fontWeight = FontWeight.Black,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                // Última fila incompleta: huecos vacíos para que las celdas que hay
                                // conserven su ancho en vez de estirarse a media pantalla.
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        // Desglose del día seleccionado (sesiones en vivo; si el año es un
                        // snapshot sin sesiones, cae a los valores guardados del mejor día)
                        if (selectedDay.isNotBlank()) {
                            val daySessions = sessions.filter { it.date == selectedDay }
                            val rowPages = wrapped.bestDayPerMonth.firstOrNull { it.second == selectedDay }?.third ?: 0
                            val isGlobalBest = selectedDay == wrapped.mostReadDay
                            val pagesSel = when {
                                daySessions.isNotEmpty() -> daySessions.sumOf { it.pages }
                                isGlobalBest             -> wrapped.mostReadDayPages
                                else                     -> rowPages
                            }
                            val sessionsSel = if (daySessions.isNotEmpty()) daySessions.size
                                else if (isGlobalBest) wrapped.bestDaySessions else 0
                            val booksSel = if (daySessions.isNotEmpty()) daySessions.map { it.bookId }.distinct().size
                                else if (isGlobalBest) wrapped.bestDayBooks else 0
                            val ppmSel = if (daySessions.isNotEmpty()) {
                                val mins = daySessions.mapNotNull { it.minutes }.sum()
                                if (mins > 0) pagesSel.toDouble() / mins else 0.0
                            } else if (isGlobalBest) wrapped.bestDayPagesPerMin else 0.0
                            Spacer(Modifier.height(8.dp))
                            // Feedback 2.7: dorado (antes cian, idéntico a la pill de págs —
                            // combina con la paleta Wrapped pero ya no se confunde con ella)
                            Text(stringResource(R.string.wrapped_bestday_breakdown, fmtDate(selectedDay)),
                                color = wrappedInk(Gold, theme), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                WrappedMiniCard("$pagesSel", stringResource(R.string.wcard_pages), Cyan, theme, Modifier.weight(1f))
                                WrappedMiniCard(if (sessionsSel > 0) "$sessionsSel" else "-", stringResource(R.string.wcard_sessions), Sky, theme, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                WrappedMiniCard(if (booksSel > 0) "$booksSel" else "-", stringResource(R.string.wcard_books), Accent2, theme, Modifier.weight(1f))
                                WrappedMiniCard(
                                    if (ppmSel > 0) String.format("%.2f", ppmSel) else "-",
                                    stringResource(R.string.wrapped_ppm_label), Green, theme, Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 6: FRANJA HORARIA FAVORITA (v2.6) ──────────────────
                6 -> Column(sm) {
                    WrappedNarrative(stringResource(R.string.wrapped_narr_7), theme)
                    val slots = wrapped.pagesPerTimeSlot
                    val slotLabels = listOf("00–03h","03–06h","06–09h","09–12h","12–15h","15–18h","18–21h","21–24h")
                    val totalSlot = slots.sum()
                    if (totalSlot <= 0) {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(wrappedSlabFor(Slot.TIMESLOT, theme))),
                            contentAlignment = Alignment.Center) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.wrapped_timeslot_title), color = onSlabTitleFor(Slot.TIMESLOT, theme),
                                    fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            WrappedEmptyState("🕐", theme)
                        }
                    } else {
                        val favIdx = slots.indices.maxByOrNull { slots[it] } ?: 0
                        val maxSlot = slots.max().coerceAtLeast(1)
                        // Hero: franja estrella. El cian se conserva porque aquí SIGNIFICA tiempo
                        // (es el semántico de la franja), pero la losa la pone el tema.
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(wrappedSlabFor(Slot.TIMESLOT_HERO, theme))),
                            contentAlignment = Alignment.Center) {
                            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(slotLabels[favIdx], fontSize = 44.sp, fontWeight = FontWeight.Black,
                                    style = androidx.compose.ui.text.TextStyle(
                                        brush = Brush.horizontalGradient(wrappedHeroFor(Slot.TIMESLOT_HERO, theme))
                                    ))
                                Text(stringResource(R.string.wrapped_timeslot_title), color = onSlabMutedFor(Slot.TIMESLOT_HERO, theme), fontSize = 13.sp)
                                Text(stringResource(R.string.wrapped_fav_pages, slots[favIdx]),
                                    color = onSlabMutedFor(Slot.TIMESLOT_HERO, theme), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        // Distribución por franjas
                        Text(stringResource(R.string.wrapped_timeslot_dist), color = wrappedInk(Sky, theme), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(8.dp))
                        Surface(shape = RoundedCornerShape(18.dp), color = theme.surface,
                            border = BorderStroke(1.dp, acc.copy(alpha = 0.13f)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                slots.forEachIndexed { i, p ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(slotLabels[i], color = if (i == favIdx) wrappedInk(Cyan, theme) else theme.textMuted,
                                            fontSize = 11.sp, fontWeight = if (i == favIdx) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.width(56.dp))
                                        // B-037: el carril iba en blanco al 10%, que sobre el papel es
                                        // invisible. Es el mismo fallo del carril del widget (revisión B2).
                                        Box(Modifier.weight(1f).height(14.dp)
                                            .clip(RoundedCornerShape(7.dp)).background(theme.border)) {
                                            if (p > 0) Box(Modifier.fillMaxHeight()
                                                .fillMaxWidth(p.toFloat() / maxSlot)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(if (i == favIdx)
                                                    Brush.horizontalGradient(listOf(wrappedInk(Cyan, theme), wrappedInk(Sky, theme)))
                                                else Brush.horizontalGradient(listOf(wrappedInk(Accent2, theme), wrappedInk(Accent2, theme).copy(0.5f)))))
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text("$p", color = if (i == favIdx) wrappedInk(Cyan, theme) else theme.textMuted,
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.wrapped_timeslot_note), color = theme.textDim, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 7: VS AÑO ANTERIOR ──────────────────────────────────
                7 -> Column(sm, horizontalAlignment = Alignment.CenterHorizontally) {
                    WrappedNarrative(stringResource(R.string.wrapped_narr_8), theme)
                    if (wrapped.previousYearBooks > 0 || wrapped.previousYearPages > 0) {
                        // B3 (17-07): ambos lados cuentan ya las relecturas (y sus páginas);
                        // computeWrapped las suma también al año anterior.
                        val dBooks = wrapped.totalBooks - wrapped.previousYearBooks
                        val dPages = wrapped.totalPages - wrapped.previousYearPages
                        val dSessions = wrapped.totalSessions - wrapped.previousYearSessions
                        val dStreak = wrapped.longestStreakDays - wrapped.previousYearStreak
                        // Header VS
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // B-037: los dos lados del VS iban en blanco sobre un tinte translúcido,
                            // o sea blanco sobre crema en Claro. El año pasado se atenúa con
                            // textMuted en vez de con alfa sobre el blanco.
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(Green.copy(0.25f), Green.copy(0.08f)))),
                                contentAlignment = Alignment.Center) {
                                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${wrapped.year}", color = wrappedInk(Green, theme), fontSize = 32.sp, fontWeight = FontWeight.Black)
                                    Text("${wrapped.totalBooks}", color = theme.textMain, fontSize = 40.sp, fontWeight = FontWeight.Black)
                                    Text(stringResource(R.string.txt_76aee4f9), color = wrappedInk(Green, theme), fontSize = 12.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(wrapped.totalPages.toLocaleString(), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.txt_47bcdf9a), color = wrappedInk(Green, theme), fontSize = 11.sp)
                                }
                            }
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(Red.copy(0.20f), Red.copy(0.06f)))),
                                contentAlignment = Alignment.Center) {
                                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${wrapped.year - 1}", color = wrappedInk(Red, theme), fontSize = 32.sp, fontWeight = FontWeight.Black)
                                    Text("${wrapped.previousYearBooks}", color = theme.textMuted, fontSize = 40.sp, fontWeight = FontWeight.Black)
                                    Text(stringResource(R.string.txt_76aee4f9), color = wrappedInk(Red, theme), fontSize = 12.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(wrapped.previousYearPages.toLocaleString(), color = theme.textMuted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.txt_47bcdf9a), color = wrappedInk(Red, theme), fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        // Delta libros
                        val bookSign = if (dBooks > 0) "+" else ""
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = (if (dBooks >= 0) Green else Red).copy(0.12f),
                            border = BorderStroke(1.dp, (if (dBooks >= 0) Green else Red).copy(0.3f)),
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(if (dBooks == 0) R.string.wrapped_same_books else if (dBooks > 0) R.string.wrapped_more_books else R.string.wrapped_less_books),
                                    color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("$bookSign$dBooks", color = if (dBooks >= 0) Green else Red,
                                    fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val pageSign = if (dPages > 0) "+" else ""
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = (if (dPages >= 0) acc else Red).copy(0.12f),
                            border = BorderStroke(1.dp, (if (dPages >= 0) acc else Red).copy(0.3f)),
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(if (dPages == 0) R.string.wrapped_same_pages else if (dPages > 0) R.string.wrapped_more_pages else R.string.wrapped_less_pages),
                                    color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("$pageSign${dPages.toLocaleString()}", color = if (dPages >= 0) acc else Red,
                                    fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        // B3 (17-07): sesiones, mismo patrón de pill que libros/páginas
                        Spacer(Modifier.height(8.dp))
                        val sessSign = if (dSessions > 0) "+" else ""
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = (if (dSessions >= 0) Green else Red).copy(0.12f),
                            border = BorderStroke(1.dp, (if (dSessions >= 0) Green else Red).copy(0.3f)),
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(if (dSessions == 0) R.string.wrapped_same_sessions else if (dSessions > 0) R.string.wrapped_more_sessions else R.string.wrapped_less_sessions),
                                    color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("$sessSign${dSessions.toLocaleString()}", color = if (dSessions >= 0) Green else Red,
                                    fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        // B3 (17-07): la racha pasa a ser COMPARATIVA (antes: dato suelto del
                        // año en curso, siempre en rojo). Verde si fue más larga, roja si más
                        // corta — el verde/rojo aquí es semántico, no del tema.
                        if (wrapped.longestStreakDays > 0 || wrapped.previousYearStreak > 0) {
                            Spacer(Modifier.height(8.dp))
                            val streakCol = if (dStreak >= 0) Green else Red
                            Surface(shape = RoundedCornerShape(16.dp), color = streakCol.copy(0.12f),
                                border = BorderStroke(1.dp, streakCol.copy(0.3f)), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🔥", fontSize = 24.sp); Spacer(Modifier.width(10.dp))
                                    Text(
                                        if (dStreak == 0) androidx.compose.ui.res.pluralStringResource(R.plurals.wrapped_streak_equal, wrapped.longestStreakDays, wrapped.longestStreakDays)
                                        else if (dStreak > 0) stringResource(R.string.wrapped_streak_longer, dStreak)
                                        else stringResource(R.string.wrapped_streak_shorter, -dStreak),
                                        color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            WrappedEmptyState("📊", theme)
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 8: CIERRE ───────────────────────────────────────────
                8 -> Column(sm, horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(4.dp))
                    WrappedNarrative(stringResource(R.string.wrapped_narr_9), theme)
                    // Número enorme: páginas protagonista. Esta es la losa del cierre, la más
                    // grande de todas, así que conserva sus tres stops (los otros van a dos).
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(wrappedSlabFor(Slot.CLOSE, theme)))) {
                        // v2.4 rework: eliminada marca de agua decorativa
                        Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // El cierre va en VERTICAL (blanco -> #A78BFA en Oscuro) y no en horizontal
                            // como el resto: el numero ocupa el ancho entero y asi el degradado se ve.
                            Text(wrapped.totalPages.toLocaleString(), fontSize = 80.sp, fontWeight = FontWeight.Black,
                                style = androidx.compose.ui.text.TextStyle(
                                    brush = Brush.verticalGradient(wrappedHeroFor(Slot.CLOSE, theme))
                                ))
                            Text(stringResource(R.string.wcard_pages_read), color = onSlabMutedFor(Slot.CLOSE, theme), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Logros — Feedback 2.6: de vuelta los emojis (el hero de páginas leídas
                    // se queda sin emoji, a petición)
                    // B3 (17-07): fuera la racha de días — ya es comparativa en la 8ª pantalla
                    // y aquí se repetía como dato suelto.
                    val items = buildList {
                        add("📚 " + stringResource(R.string.wcard_books_done, wrapped.totalBooks) to acc)
                        if (wrapped.maxSessionPages > 0) add("⚡ " + stringResource(R.string.wrapped_record_line, wrapped.maxSessionPages) to Amber)
                        if (wrapped.favoriteAuthor.isNotBlank()) add("✍️ " + wrapped.favoriteAuthor to Accent2)
                    }
                    items.forEach { (txt, col) ->
                        // B-037: el texto iba en BLANCO fijo sobre una pill translúcida, o sea
                        // blanco sobre crema en el tema Claro. Pasa al texto del tema.
                        Surface(shape = RoundedCornerShape(14.dp), color = col.copy(0.1f),
                            border = BorderStroke(1.dp, wrappedInk(col, theme).copy(0.35f)),
                            modifier = Modifier.fillMaxWidth()) {
                            Text(txt, color = theme.textMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(16.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // Feedback 2.6: libros abandonados — movidos aquí desde la slide 4
                    // (mejores/más rápidos); en el cierre encajan como parte del balance.
                    if (wrapped.droppedBooks > 0) {
                        Spacer(Modifier.height(2.dp))
                        Surface(shape = RoundedCornerShape(16.dp), color = Red.copy(0.1f),
                            border = BorderStroke(1.dp, wrappedInk(Red, theme).copy(0.35f)), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("❌", fontSize = 22.sp); Spacer(Modifier.width(10.dp))
                                    Text(stringResource(R.string.wrapped_dropped_text, wrapped.droppedBooks, if (wrapped.droppedBooks != 1) "s" else ""),
                                        color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                if (wrapped.droppedBookTitles.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    wrapped.droppedBookTitles.forEach { title ->
                                        Text("· $title", color = theme.textDim, fontSize = 12.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(start = 32.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // B3 (17-07): eliminada la tarjeta "Guardado automáticamente en tu
                    // historial de Wrapped" del pie de esta slide (petición de Víctor).
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDE 9: TUS 3 FAVORITOS DEL AÑO (v2.4 rework, congelados v2.6) ──
                9 -> Column(sm) {
                    WrappedNarrative(stringResource(R.string.wrapped_narr_10), theme)
                    // Cabecera
                    // B-037: el rojo de favoritos se CONSERVA (es su semántico); la losa se deriva de él.
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(wrappedSlabFor(Slot.FAVORITES, theme))),
                        contentAlignment = Alignment.Center) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.wrapped_favorites_section), color = onSlabTitleFor(Slot.FAVORITES, theme),
                                fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                            // v2.6: eliminado subtítulo "Aleatorios entre tus favoritos…" — los
                            // favoritos ahora son fijos por Wrapped (favoritesForWrapped)
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    if (favBooks.isEmpty()) {
                        Surface(shape = RoundedCornerShape(16.dp), color = theme.surface,
                            border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
                            WrappedEmptyState("❤️", theme, Modifier.padding(24.dp))
                        }
                    } else {
                        val favMedals = listOf("\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49")
                        favBooks.forEachIndexed { i, book ->
                            val bookSessions = vm.sessionsForBook(book.id)
                            val favMinutes = bookSessions.sumOf { it.minutes ?: 0 }
                            Surface(shape = RoundedCornerShape(18.dp), color = theme.surface,
                                border = BorderStroke(1.dp, FavoriteRed.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp)) {
                                    // Portada real + medalla funcional de ranking
                                    Box {
                                        BookCover(book.coverUrl, book.title, size = 84, isbnFallback = book.isbn)
                                        Text(favMedals.getOrElse(i) { "" }, fontSize = 18.sp,
                                            modifier = Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-6).dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(book.title, color = theme.textMain, fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (book.author.isNotBlank())
                                            Text(book.author, color = theme.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (book.genres.isNotEmpty()) {
                                            // precomputar fuera de joinToString (displayGenre es @Composable)
                                            val genreLabel = book.genres.map { displayGenre(it) }.joinToString(" · ")
                                            Text(genreLabel,
                                                color = wrappedInk(Sky, theme), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(stringResource(R.string.wrapped_fav_pages, book.pages), color = theme.textMuted, fontSize = 12.sp)
                                            if (favMinutes > 0) {
                                                val fh = favMinutes / 60; val fm = favMinutes % 60
                                                Text(if (fh > 0) "${fh}h ${fm}m" else "${fm}m", color = theme.textMuted, fontSize = 12.sp)
                                            }
                                        }
                                        if (book.rating > 0) {
                                            Spacer(Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val favStars = kotlin.math.ceil(book.rating / 2.0).toInt().coerceIn(0, 5)
                                                Text("★".repeat(favStars) + "☆".repeat(5 - favStars),
                                                    color = wrappedInk(Gold, theme), fontSize = 13.sp, letterSpacing = 1.sp)
                                                Spacer(Modifier.width(6.dp))
                                                Text("${book.rating}/10", color = wrappedInk(Gold, theme), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        if (book.comment.isNotBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Surface(shape = RoundedCornerShape(10.dp), color = acc.copy(alpha = 0.08f)) {
                                                Text("\u201C${book.comment}\u201D", color = theme.textMuted, fontSize = 12.sp,
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        // v2.6: eliminada nota "se renuevan cada vez que abres el Wrapped" —
                        // ya no aplica (favoritos congelados)
                    }
                    Spacer(Modifier.height(40.dp))
                }

                // ── SLIDES DINÁMICAS: Bingo anual (6.3, condicional) + cierre (P-015) ──
                else -> {
                    val isBingoPage = hasBingoSlide && page == 10
                    if (isBingoPage) Column(sm, horizontalAlignment = Alignment.CenterHorizontally) {
                        // B4 (2): por PORCENTAJE, no por casillas: desde que el 3×3 de Pro
                        // comparte historial con el 4×4, cellsDone no es comparable entre
                        // entradas (9/9 perdía contra 10/16). Ver bestMonthSummary.
                        val best = com.lecturameter.utils.BingoManager.bestMonthSummary(bingoYear)!!
                        val isEsW = androidx.compose.ui.platform.LocalConfiguration.current.locales.get(0)?.language == "es"
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.wbingo_title), fontSize = 30.sp, fontWeight = FontWeight.Black,
                            style = androidx.compose.ui.text.TextStyle(
                                brush = Brush.horizontalGradient(accentGradientText(theme))
                            ), textAlign = TextAlign.Center)
                        Text(stringResource(R.string.wbingo_best_month, fmtMonthName(best.monthKey)).replaceFirstChar { it.uppercase(appDisplayLocale) },
                            color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                        // Cartón visual del mejor mes (patrón 1/0)
                        val bSide = com.lecturameter.utils.BingoManager.sideOf(best.pattern.length).coerceAtLeast(3)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            for (r in 0 until bSide) {
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    for (cIdx in 0 until bSide) {
                                        val on = best.pattern.getOrNull(r * bSide + cIdx) == '1'
                                        Box(
                                            Modifier.size(46.dp).clip(RoundedCornerShape(9.dp))
                                                // B-037: la casilla vacía iba en blanco al 6%, invisible
                                                // sobre el papel. Mismo caso que el heatmap del B2.
                                                .background(if (on) acc.copy(alpha = 0.30f) else theme.bgSurf2)
                                                .border(1.dp, if (on) acc else theme.border, RoundedCornerShape(9.dp)),
                                            contentAlignment = Alignment.Center
                                        ) { if (on) Text("✓", color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WrappedMiniCard("${bingoYear.sumOf { it.cellsDone }}", stringResource(R.string.wbingo_cells), acc, theme, Modifier.weight(1f))
                            WrappedMiniCard("${bingoYear.sumOf { it.lines }}", stringResource(R.string.wbingo_lines), Accent2, theme, Modifier.weight(1f))
                            WrappedMiniCard("${bingoYear.count { it.complete }}", stringResource(R.string.wbingo_full_cards), Amber, theme, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                        bingoYear.filter { it.lines > 0 }.minByOrNull { it.monthKey }?.let { first ->
                            WrappedFavRow("", stringResource(R.string.wbingo_first_line), fmtMonthName(first.monthKey), "", acc, theme)
                            Spacer(Modifier.height(8.dp))
                        }
                        // Casilla más difícil: la etiqueta que más veces quedó sin completar
                        val hardest = bingoYear.flatMap { if (isEsW) it.uncompletedEs else it.uncompletedEn }
                            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                        if (hardest != null) {
                            WrappedFavRow("", stringResource(R.string.wbingo_hardest), "«$hardest»", "", Accent2, theme)
                            Spacer(Modifier.height(8.dp))
                        }
                        bingoYear.filter { it.complete }.minByOrNull { it.monthKey }?.let { full ->
                            Surface(shape = RoundedCornerShape(999.dp), color = Gold.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f))) {
                                Text(stringResource(R.string.wbingo_completed_ribbon, fmtMonthName(full.monthKey)),
                                    color = wrappedInk(Gold, theme), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                    } else Column(sm, horizontalAlignment = Alignment.CenterHorizontally) {
                        // ── P-015: cierre con comparativa vs año anterior ────────
                        Spacer(Modifier.height(12.dp))
                        // B3 (17-07): el texto grande cierra con un ":(" en la misma letra
                        // en degradado (narrativización, 11ª pantalla). Lleva un espacio duro U+00A0
                        // a propósito: con espacio normal el ":(" se va solo a la línea de
                        // abajo y la carita queda partida.
                        Text(stringResource(R.string.wclose_title) + " :(", fontSize = 26.sp, fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center, lineHeight = 32.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                brush = Brush.horizontalGradient(accentGradientText(theme))
                            ))
                        val hasPrev = wrapped.previousYearBooks > 0 || wrapped.previousYearPages > 0
                        Text(
                            if (hasPrev) stringResource(R.string.wclose_vs, wrapped.year, wrapped.year - 1)
                            else stringResource(R.string.wclose_totals),
                            color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                        @Composable
                        fun cmpRow(label: String, value: String, delta: Int?) {
                            Surface(shape = RoundedCornerShape(12.dp), color = theme.surface,
                                border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Text(value, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    if (delta != null && delta != 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (delta > 0) "▲ +${delta.toLocaleString()}" else "▼ ${delta.toLocaleString()}",
                                            color = if (delta > 0) Green else Red, fontSize = 12.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                        }
                        cmpRow(stringResource(R.string.wclose_books), "${wrapped.totalBooks}",
                            if (hasPrev) wrapped.totalBooks - wrapped.previousYearBooks else null)
                        cmpRow(stringResource(R.string.wclose_pages), wrapped.totalPages.toLocaleString(),
                            if (hasPrev) wrapped.totalPages - wrapped.previousYearPages else null)
                        if (wrapped.totalMinutes > 0) {
                            val prevMinutes = vm.wrappedForYear(year - 1)?.totalMinutes ?: 0
                            cmpRow(stringResource(R.string.wclose_time), fmtMinutes(wrapped.totalMinutes),
                                if (prevMinutes > 0) wrapped.totalMinutes - prevMinutes else null)
                        }
                        // B3 fase B (C1): sesiones. El Δ sale de previousYearSessions, que se
                        // calcula fresco en computeWrapped y no depende de que el Wrapped del
                        // año anterior llegara a guardarse (a diferencia del tiempo de arriba).
                        if (wrapped.totalSessions > 0 || wrapped.previousYearSessions > 0) {
                            cmpRow(stringResource(R.string.wclose_sessions), "${wrapped.totalSessions}",
                                if (hasPrev) wrapped.totalSessions - wrapped.previousYearSessions else null)
                        }
                        // B3 (17-07): "GÉNERO DEL AÑO" pasa a ser género de este año vs el del
                        // año pasado. Si no hay dato del año anterior, se muestra solo el de
                        // este año; si no hay ninguno, el estado vacío unificado.
                        //
                        // B3 fase B (variante C1): el mismo bloque lo usan ya tres comparativas
                        // (género, autor, libro nº1), así que sale a un composable. Va en COMPACTO
                        // (padding 8dp, etiqueta 11sp, valor 12sp): a tamaño normal las tres filas
                        // más los chips se salían 183dp de la pantalla; en compacto entra todo con
                        // ~40dp de margen. Es un margen fino, y con la fuente del sistema muy
                        // grande esto se corta: es el precio de C1, decidido a sabiendas.
                        @Composable
                        fun vsRow(label: String, valueNow: String, valuePrev: String) {
                            Surface(shape = RoundedCornerShape(12.dp), color = theme.surface,
                                border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                    Text(label, color = theme.textMuted, fontSize = 11.sp)
                                    Spacer(Modifier.height(3.dp))
                                    if (valueNow.isBlank() && valuePrev.isBlank()) {
                                        Text(stringResource(R.string.wrapped_no_stats_yet), color = theme.textDim,
                                            fontSize = 12.sp, lineHeight = 17.sp)
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text("${wrapped.year}", color = wrappedInk(acc, theme), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                Text(valueNow.ifBlank { "-" }, color = theme.textMain, fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text("vs", color = theme.textDim, fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text("${wrapped.year - 1}", color = theme.textDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                Text(valuePrev.ifBlank { "-" }, color = theme.textMuted, fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        vsRow(stringResource(R.string.wclose_genre_vs),
                            if (wrapped.favoriteGenre.isNotBlank()) displayGenre(wrapped.favoriteGenre) else "",
                            if (wrapped.previousYearGenre.isNotBlank()) displayGenre(wrapped.previousYearGenre) else "")
                        vsRow(stringResource(R.string.wclose_author_vs),
                            wrapped.favoriteAuthor, wrapped.previousYearAuthor)
                        // El libro nº1 sale de los favoritos CONGELADOS de cada año. Si el Wrapped
                        // del año anterior nunca se guardó no hay con qué comparar y la fila cae al
                        // estado vacío: es lo esperable el primer año, no un fallo.
                        vsRow(stringResource(R.string.wclose_book_vs),
                            favBooks.firstOrNull()?.title ?: "",
                            remember(year, books) { vm.wrappedTopFavoriteTitle(year - 1, prefs) })
                        // Los tres chips: día, mes y semana, este año contra el pasado. Se cuentan
                        // en otras slides (el mejor día es la 2ª y la 6ª entera; el mejor mes es la
                        // frase de la 5ª), pero Víctor los quiere también aquí y en formato chip
                        // caben los tres en una sola fila de 71dp.
                        val monthNamesClose = LocalContext.current.resources.getStringArray(R.array.month_names_full).toList()
                        @Composable
                        fun RowScope.closeChip(label: String, now: String, prev: String) {
                            Surface(shape = RoundedCornerShape(12.dp), color = theme.surface,
                                border = BorderStroke(1.dp, theme.border), modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, color = theme.textMuted, fontSize = 8.5.sp, maxLines = 1)
                                    Text(now.ifBlank { "-" }, color = theme.textMain, fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (prev.isBlank()) "-" else stringResource(R.string.wclose_chip_prev, wrapped.year - 1, prev),
                                        color = theme.textDim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        val bestMonthNow = wrapped.pagesPerMonth.indices
                            .filter { wrapped.pagesPerMonth[it] > 0 }
                            .maxByOrNull { wrapped.pagesPerMonth[it] } ?: -1
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            closeChip(stringResource(R.string.wclose_chip_day),
                                if (wrapped.mostReadDay.isNotBlank()) fmtDateShort(wrapped.mostReadDay) else "",
                                if (wrapped.previousYearMostReadDay.isNotBlank()) fmtDateShort(wrapped.previousYearMostReadDay) else "")
                            closeChip(stringResource(R.string.wclose_chip_month),
                                monthNamesClose.getOrElse(bestMonthNow) { "" },
                                // previousYearBestMonth es 1-basado y 0 significa "sin datos",
                                // así que el -1 lo devuelve al índice del array (y el 0 se cae
                                // solo a getOrElse, que es lo que se busca).
                                monthNamesClose.getOrElse(wrapped.previousYearBestMonth - 1) { "" })
                            closeChip(stringResource(R.string.wclose_chip_week),
                                if (wrapped.bestWeekNumber > 0) stringResource(R.string.wclose_week_short, wrapped.bestWeekNumber) else "",
                                if (wrapped.previousYearBestWeekNumber > 0) stringResource(R.string.wclose_week_short, wrapped.previousYearBestWeekNumber) else "")
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.wclose_quote), color = theme.textMuted, fontSize = 14.sp,
                            fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 12.dp))
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }

        // Flechas de navegación: eliminadas — el pager ya soporta swipe nativo
        // y las flechas superpuestas solapaban el texto de los slides.
    }
}

// B3 (17-07): narrativización — texto pequeño en blanco (tono según tema) que
// encabeza cada slide, por encima de su primera card. Textos literales de Víctor.
@Composable
fun WrappedNarrative(text: String, theme: Theme) {
    Text(text, color = theme.textMain, fontSize = 12.5.sp, lineHeight = 17.sp,
        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
}

// B3 (17-07): estado vacío unificado del Wrapped ("aún no tienes estadísticas
// de esto, pero vuelve el año que viene"). Sustituye a los 4 textos sueltos.
@Composable
fun WrappedEmptyState(emoji: String, theme: Theme, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.wrapped_no_stats_yet), color = theme.textDim,
            fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp))
    }
}

// B-037: la losa lleva texto BLANCO en los cinco temas (onSlab). El acento sobre su propia
// losa oscurecida no pasa AA en ninguno: en Oscuro, "Páginas" llevaba 2,56:1 y su etiqueta
// 1,95:1 desde antes de que existiera el tema Claro. La etiqueta pierde el alfa, que era la
// causa raíz: pintar texto con alfa lo mezcla con el fondo antes de leerse. La jerarquía la
// dan el tamaño y las mayúsculas, no el desvanecido.
// Revision de B-037: recibe el SLOT y no un pincel ya montado. Antes las dos cajas de una fila
// se distinguian por que quien llamaba pasase wrappedSlab a una y wrappedSlabAlt a la otra, o sea
// que la identidad de la losa vivia en el sitio de la llamada, y por eso se pudo aplanar sin que
// se notara. Ahora la losa y su etiqueta salen del mismo slot y no se pueden desparejar.
@Composable
fun WrappedBigCard(value: String, label: String, theme: Theme, slot: Slot, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(wrappedSlabFor(slot, theme))), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = onSlab(theme), fontSize = 30.sp, fontWeight = FontWeight.Black,
                maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 34.sp)
            Text(label.uppercase(), color = onSlabMutedFor(slot, theme), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
fun WrappedMiniCard(value: String, label: String, color: Color, theme: Theme, modifier: Modifier = Modifier) {
    val ink = wrappedInk(color, theme)
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(0.1f),
        border = BorderStroke(1.dp, ink.copy(0.35f)), modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text(label.uppercase(), color = ink, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun WrappedFavRow(emoji: String, label: String, value: String, sub: String, color: Color, theme: Theme) {
    val ink = wrappedInk(color, theme)
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(0.08f),
        border = BorderStroke(1.dp, ink.copy(0.3f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (emoji.isNotBlank()) { Text(emoji, fontSize = 26.sp); Spacer(Modifier.width(10.dp)) }
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), color = ink, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text(value, color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, color = ink, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun WrappedTop3Row(medal: String, name: String, count: String, isGold: Boolean, color: Color, theme: Theme) {
    val ink = wrappedInk(color, theme)
    val bg = if (isGold) color.copy(0.15f) else color.copy(0.07f)
    val border = if (isGold) ink.copy(0.5f) else ink.copy(0.25f)
    Surface(shape = RoundedCornerShape(12.dp), color = bg,
        border = BorderStroke(1.dp, border), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(medal, fontSize = 22.sp); Spacer(Modifier.width(8.dp))
            Text(name, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LinearProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(0.15f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f,1f)).fillMaxHeight()
            .clip(RoundedCornerShape(3.dp)).background(color))
    }
}

fun wrappedMedal(i: Int): String = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }

@Composable
fun WrappedBigStat(value: String, label: String, modifier: Modifier, color: Color, theme: Theme) {
    val ink = wrappedInk(color, theme)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = ink, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun WrappedHighlightCard(emoji: String, label: String, value: String, sub: String, color: Color, theme: Theme, modifier: Modifier) {
    val ink = wrappedInk(color, theme)
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.1f), border = BorderStroke(1.dp, ink.copy(alpha = 0.4f)), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.height(6.dp))
            Text(label, color = ink, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, color = ink, fontSize = 11.sp)
        }
    }
}

@Composable
fun WrappedBookCard(emoji: String, label: String, title: String, detail: String, color: Color, theme: Theme) {
    val ink = wrappedInk(color, theme)
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.08f), border = BorderStroke(1.dp, ink.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = ink, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(3.dp))
                Text(title, color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(detail, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── WrappedHistoryScreen ──────────────────────────────────────────────────────

@Composable
fun WrappedHistoryScreen(vm: BooksViewModel, theme: Theme, onBack: () -> Unit, onOpen: (Int) -> Unit) {
    // D-004: wrappedHistory es StateFlow; se colecciona en la raíz de la pantalla
    val wrappedHistory by vm.wrappedHistory.collectAsState()
    val history = wrappedHistory.sortedByDescending { it.year }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 24.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.txt_2d903c83), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (history.size == 1) stringResource(R.string.wrapped_history_count_one, history.size) else stringResource(R.string.wrapped_history_count_other, history.size), color = theme.textMuted, fontSize = 13.sp)
                // B-037: iba en Gold, que sobre el papel da 1,52:1 (invisible).
                Text(nextWrappedSubtitle(), color = wrappedInk(Gold, theme), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎁", fontSize = 48.sp); Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.txt_5718979a), color = theme.textMain, fontSize = 16.sp)
                    Text(stringResource(R.string.txt_80b7fc7c), color = theme.textDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history) { w ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = theme.surface,
                        border = BorderStroke(1.dp, theme.border),
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(w.year) }
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Year badge
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(accentGradient(theme))),
                                contentAlignment = Alignment.Center
                            ) { Text("${w.year}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.wrapped_history_year_label, w.year), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                val booksLabel = if (w.rereadBooks > 0)
                                    stringResource(R.string.wrapped_history_summary_reread, w.totalBooks, w.rereadBooks, w.totalPages.toLocaleString())
                                else
                                    stringResource(R.string.wrapped_history_summary, w.totalBooks, w.totalPages.toLocaleString())
                                Text(booksLabel, color = theme.textMuted, fontSize = 13.sp)
                                if (w.favoriteAuthor.isNotBlank())
                                    Text("✍️ ${w.favoriteAuthor}", color = theme.textDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(String.format("%.1f p/d", w.avgPagesPerDay), color = wrappedInk(Green, theme), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (w.bestRatedTitle.isNotBlank())
                                    Text("⭐ ${w.bestRatedScore}/10", color = wrappedInk(Gold, theme), fontSize = 11.sp)
                            }
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.ChevronRight, null, tint = theme.textDim, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

