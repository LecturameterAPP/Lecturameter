package com.lecturameter

// BookSearchScreen, SearchResultCard, LanguageSelector, AddScreen, Rating, FormField, EditionsSection y StatBox.
// Extraido de MainActivity.kt el 15-07-2026 (ruptura del monolito, sin cambios funcionales).


import android.content.Context
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.lecturameter.model.*
import com.lecturameter.utils.*
import androidx.navigation.compose.composable

// ── BookSearchScreen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSearchScreen(
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    theme: Theme,
    onBack: () -> Unit,
    initialQuery: String = "",
    isbnFromScanner: String? = null,
    onClearIsbnFromScanner: () -> Unit = {},
    onAddWithIsbn: (String) -> Unit = {},
    onScanIsbn: () -> Unit = {}
) {
    val context = LocalContext.current
    val acc = accentForTheme(theme)
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<OpenLibraryResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var networkError by remember { mutableStateOf(false) }
    var selectedResult by remember { mutableStateOf<OpenLibraryResult?>(null) }
    // Ficha de detalle: tocar una tarjeta abre esta vista de SOLO LECTURA con los datos
    // completos del libro; desde ahí se decide si añadirlo. El "+" de la tarjeta sigue
    // siendo el atajo para añadir directo sin pasar por aquí.
    var detailResult by remember { mutableStateOf<OpenLibraryResult?>(null) }
    // v2.5: aviso de duplicado antes de añadir
    var duplicateCandidate by remember { mutableStateOf<Book?>(null) }
    val scope = rememberCoroutineScope()
    // Dialog de ISBN escaneado — se activa cuando llega un ISBN real desde la cámara
    var showScanDialog by remember { mutableStateOf(false) }

    duplicateCandidate?.let { cand ->
        val existing = vm.findDuplicate(cand) ?: cand
        DuplicateBookDialog(cand, existing, theme,
            onConfirm = { vm.addBook(cand, prefs); duplicateCandidate = null },
            onDismiss = { duplicateCandidate = null })
    }
    var dialogIsbn by remember { mutableStateOf("") }
    val mainActivity = context as? MainActivity

    // Cuando llega un ISBN escaneado desde ScannerActivity (vía isbnFromScanner)
    LaunchedEffect(isbnFromScanner) {
        if (!isbnFromScanner.isNullOrBlank() && isbnFromScanner != "__scanning__") {
            dialogIsbn = isbnFromScanner
            showScanDialog = true
        }
    }

    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) { true } // si falla la comprobación, intentar de todos modos
    }

    fun doSearch() {
        if (query.isBlank()) return
        searched = true; errorMsg = ""; networkError = false; results = emptyList()
        // Catálogo local: sin conexión ya no se aborta, porque la búsqueda puede resolverse
        // en local. Solo se corta si además NO hay catálogo, que es el caso de antes.
        if (!isOnline() && !CatalogRepository.isAvailable) {
            networkError = true
            errorMsg = context.getString(R.string.err_no_internet_search)
            return
        }
        isLoading = true
        scope.launch {
            // v2.6: idioma de la app como idioma preferido de búsqueda (automático)
            // Feedback 2.6: onPartial pinta los resultados según van llegando las fases
            val found = try {
                searchOpenLibrary(query, vm.currentLanguage) { partial ->
                    scope.launch { if (isLoading) results = partial }
                }
            } catch (_: Exception) { emptyList() }
            results = found
            isLoading = false
            if (found.isEmpty()) {
                // Re-verificar conectividad: si seguimos offline tras la búsqueda fallida.
                // Con catálogo local, quedarse sin resultados offline significa que el libro
                // no está en el catálogo, no que falte internet: el mensaje correcto es
                // "no encontrado", no "sin conexión".
                if (!isOnline() && !CatalogRepository.isAvailable) {
                    networkError = true
                    errorMsg = context.getString(R.string.err_no_internet_search)
                } else {
                    errorMsg = context.getString(R.string.txt_57be44d3, query)
                }
            }
        }
    }

    // Si viene query inicial del scanner, sanitizar y lanzar búsqueda automáticamente
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            // Solo ISBN: solo dígitos/X. Si no es ISBN puro, usar tal cual (búsqueda manual)
            val isIsbnLike = initialQuery.replace(Regex("[^\\dXx]"), "").length in 10..13
            if (isIsbnLike) {
                query = initialQuery.replace(Regex("[^\\dXx]"), "").uppercase()
            }
            doSearch()
        }
    }

    // Dialog ISBN escaneado desde BookSearchScreen
    if (showScanDialog && dialogIsbn.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false; onClearIsbnFromScanner() },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_016a31c7), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = theme.surface) {
                        Text(dialogIsbn, color = acc, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    Text(stringResource(R.string.txt_55800f4c), color = theme.textDim, fontSize = 14.sp)
                    Button(
                        onClick = {
                            showScanDialog = false
                            query = dialogIsbn
                            onClearIsbnFromScanner()
                            doSearch()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = onAccentColor(theme)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.txt_69e20782), fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = {
                            showScanDialog = false
                            onClearIsbnFromScanner()
                            onAddWithIsbn(dialogIsbn)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, acc),
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.Add, null, tint = acc, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.txt_97225860), color = acc) }
                }
            },
            confirmButton = {}
        )
    }

    // ── Ficha de detalle (solo lectura) ───────────────────────────────────────
    // Muestra lo que el resultado ya trae, sin ninguna llamada de red extra. No lleva
    // indicador de origen a propósito: con el catálogo como fuente principal, marcar lo
    // normal no aporta (decisión 21-07). El botón principal abre el diálogo de añadir de
    // siempre reutilizando selectedResult.
    detailResult?.let { r ->
        val acc = accentForTheme(theme)
        val langMeta: Pair<String, String>? = when (r.language) {
            "es" -> "🇪🇸" to "Español"; "ca" -> "🇪🇸" to "Català"; "en" -> "🇬🇧" to "English"
            "fr" -> "🇫🇷" to "Français"; "de" -> "🇩🇪" to "Deutsch"; "it" -> "🇮🇹" to "Italiano"
            "pt" -> "🇵🇹" to "Português"; "ja" -> "🇯🇵" to "日本語"; else -> null
        }
        AlertDialog(
            onDismissRequest = { detailResult = null },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.detail_book_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.Top) {
                        BookCover(r.coverUrl, r.title, size = 96, author = r.author)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = theme.textMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            if (r.author.isNotBlank()) Text(r.author, color = theme.textMuted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                            if (r.genre.isNotBlank()) Text(mapApiGenre(r.genre).joinToString(" · ").ifBlank { r.genre }, color = acc.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (r.publishYear.isNotBlank()) Text("📅 ${r.publishYear}", color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    if (r.pages > 1) Text(stringResource(R.string.search_pages_count, r.pages), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    else Text(stringResource(R.string.txt_0c78d77a), color = Amber, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    if (!r.isbn.isNullOrBlank()) Text(stringResource(R.string.detail_isbn, r.isbn), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                    langMeta?.let { (flag, label) -> Text("$flag $label", color = theme.textMuted, fontSize = 13.sp) }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedResult = r; detailResult = null },
                    colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = onAccentColor(theme)),
                    shape = RoundedCornerShape(10.dp)
                ) { Text(stringResource(R.string.detail_add_to_library)) }
            },
            dismissButton = { TextButton(onClick = { detailResult = null }) { Text(stringResource(R.string.detail_close), color = Red) } }
        )
    }

    selectedResult?.let { r ->
        var status by remember { mutableStateOf(BookStatus.PENDING) }
        var statusExpanded by remember { mutableStateOf(false) }
        var searchFirstFunc by remember { mutableStateOf("") }
        var searchLastFunc  by remember { mutableStateOf("") }
        var searchStartDate by remember { mutableStateOf("") }
        var searchEndDate   by remember { mutableStateOf("") }
        var searchRating    by remember { mutableStateOf(0) }
        // Feedback 2.7: el género detectado por la API era inamovible — ahora editable
        // (multi-select hasta 2, mismo patrón que AddScreen), prefijado con lo detectado
        var searchGenres by remember { mutableStateOf(mapApiGenre(r.genre).ifEmpty { if (r.genre.isNotBlank()) listOf("Otro") else emptyList() }) }
        var searchGenreExpanded by remember { mutableStateOf(false) }
        // P-012: sugeridos = lo detectado por la API al abrir el diálogo
        val searchSuggestedGenres = remember { searchGenres }
        val needsDates  = status in listOf(BookStatus.READING, BookStatus.FINISHED, BookStatus.REREADING, BookStatus.DROPPED)
        val needsEndDate = status in listOf(BookStatus.FINISHED, BookStatus.REREADING, BookStatus.DROPPED)
        AlertDialog(onDismissRequest = { selectedResult = null }, containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_281db514), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BookCover(r.coverUrl, r.title, size = 60, author = r.author)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (r.author.isNotBlank()) Text(r.author, color = theme.textMuted, fontSize = 13.sp)
                            if (r.pages > 1) Text(stringResource(R.string.search_pages_count, r.pages), color = theme.textDim, fontSize = 12.sp)
                            else Text(stringResource(R.string.txt_0c78d77a), color = Amber, fontSize = 12.sp)
                            if (r.publishYear.isNotBlank()) Text(r.publishYear, color = theme.textDim, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.txt_24610ea2), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = it }) {
                        OutlinedTextField(value = statusLabel(status), onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = fieldColors(theme))
                        ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                            listOf(BookStatus.READING, BookStatus.FINISHED, BookStatus.REREADING, BookStatus.PENDING, BookStatus.DROPPED).forEach { s -> DropdownMenuItem(text = { Text(statusLabel(s), color = theme.textMain) }, onClick = { status = s; statusExpanded = false }) }
                        }
                    }
                    // Fechas — siempre visibles, activadas según estado
                    if (status != BookStatus.PENDING) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.txt_1f34cadf), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                                OutlinedTextField(
                                    value = searchStartDate,
                                    onValueChange = { if (it.length <= 10) searchStartDate = it },
                                    placeholder = { Text(stringResource(R.string.txt_d047c2a8), color = theme.textDim, fontSize = 11.sp) },
                                    singleLine = true, colors = fieldColors(theme),
                                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (needsEndDate) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.txt_e706900d), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    OutlinedTextField(
                                        value = searchEndDate,
                                        onValueChange = { if (it.length <= 10) searchEndDate = it },
                                        placeholder = { Text(stringResource(R.string.txt_d047c2a8), color = theme.textDim, fontSize = 11.sp) },
                                        singleLine = true, colors = fieldColors(theme),
                                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    // Puntuación
                    if (status == BookStatus.FINISHED) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.txt_7a9622a0), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        // Feedback 17-07: dos filas de 5 (en una sola fila se cortaba en pantallas estrechas)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(1..5, 6..10).forEach { range ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    range.forEach { n ->
                                        Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(if (n <= searchRating) Amber.copy(alpha = 0.85f) else theme.surface).border(1.dp, if (n <= searchRating) Amber else theme.border, RoundedCornerShape(6.dp)).clickable { searchRating = if (searchRating == n) 0 else n }, contentAlignment = Alignment.Center) {
                                            Text("$n", color = if (n <= searchRating) Color.White else theme.textDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Feedback 2.7: género editable antes de guardar (máx 2)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.txt_57d644ad), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Box {
                        OutlinedTextField(
                            value = if (searchGenres.isEmpty()) "" else searchGenres.map { displayGenre(it) }.joinToString(" · "),
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text(stringResource(R.string.txt_84a8f3ea), color = theme.textDim, fontSize = 13.sp) },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = theme.textDim) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(theme),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Box(Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).clickable { searchGenreExpanded = true })
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.txt_066bbf84), color = theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = searchFirstFunc, onValueChange = { searchFirstFunc = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.txt_3c75ceda), color = theme.textDim, fontSize = 11.sp) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = searchLastFunc, onValueChange = { searchLastFunc = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.txt_27de21f4), color = theme.textDim, fontSize = 11.sp) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val storedStart = searchStartDate.trim().let { d -> if (d.length == 10) displayToStored(d) else if (needsDates) today() else null }
                    val storedEnd   = searchEndDate.trim().let { d -> if (d.length == 10) displayToStored(d) else if (needsEndDate) today() else null }
                    // Feedback 2.7: género elegido/confirmado por el usuario en el diálogo
                    val newBook = Book(title = r.title, author = r.author, pages = if (r.pages > 0) r.pages else 1, status = status, startDate = storedStart, endDate = storedEnd, rating = if (status == BookStatus.FINISHED) searchRating else 0, coverUrl = cleanCoverUrl(r.coverUrl), isbn = r.isbn, genres = searchGenres, firstFunctionalPage = searchFirstFunc.toIntOrNull(), lastFunctionalPage = searchLastFunc.toIntOrNull())
                    // Fase 0 QA: la primera edición hereda el idioma del resultado de búsqueda
                    // (r.language, v2.6) o, en su defecto, el deducido del prefijo ISBN.
                    // Solo sin señal alguna se cae al genérico "mul"/🌐 de siempre.
                    val edMeta: Triple<String, String, String>? = when (r.language) {
                        "es" -> Triple("es", "Español", "🇪🇸")
                        "ca" -> Triple("ca", "Català", "🇪🇸 (CAT)")
                        "en" -> Triple("original", "English", "🌐")
                        "fr" -> Triple("fr", "Français", "🇫🇷")
                        "de" -> Triple("de", "Deutsch", "🇩🇪")
                        "it" -> Triple("it", "Italiano", "🇮🇹")
                        "pt" -> Triple("pt", "Português", "🇵🇹")
                        else -> r.isbn?.let { isbnToLanguageMeta(it) }?.takeIf { it.second != "Original" }
                    }
                    val firstEdition = BookEdition(id = newBook.id, language = edMeta?.first ?: "mul", languageLabel = edMeta?.second ?: "Edición principal", flag = edMeta?.third ?: "🌐", title = newBook.title, pages = newBook.pages, coverUrl = newBook.coverUrl, isbn = newBook.isbn, isActive = true)
                    val toAdd = newBook.copy(editions = listOf(firstEdition))
                    // v2.5: aviso de duplicado (antes se añadía sin avisar)
                    if (vm.findDuplicate(toAdd) != null) { duplicateCandidate = toAdd } else { vm.addBook(toAdd, prefs) }
                    selectedResult = null
                }, colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = onAccentColor(theme)), shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.txt_d20f652b)) }
            },
            dismissButton = { TextButton(onClick = { selectedResult = null }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
        )
        // Feedback 17-07: el sheet vive FUERA del AlertDialog — dentro quedaba
        // restringido al ancho de la ventana del diálogo y se cortaba por la
        // derecha en algunos móviles.
        if (searchGenreExpanded) {
            GenreSelectorSheet(
                initial = searchGenres,
                suggested = searchSuggestedGenres,
                theme = theme,
                onDismiss = { searchGenreExpanded = false },
                onConfirm = { searchGenres = it }
            )
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.txt_0b059290), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = query, onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.txt_a4fddd8f), color = theme.textDim, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = theme.textDim, modifier = Modifier.size(20.dp)) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null, tint = theme.textDim, modifier = Modifier.size(18.dp)) } },
                modifier = Modifier.weight(1f), colors = fieldColors(theme), shape = RoundedCornerShape(12.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { doSearch() })
            )
            Button(onClick = { doSearch() }, colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = onAccentColor(theme)), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), enabled = query.isNotBlank() && !isLoading) { Text(stringResource(R.string.txt_113f7428), fontWeight = FontWeight.Bold) }
        }
        // Botón escanear ISBN — fila separada debajo de la barra de búsqueda
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.surface)
                .clickable(onClick = onScanIsbn)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_barcode), contentDescription = null, tint = acc, modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.txt_e82171d9), color = theme.textDim, fontSize = 13.sp)
        }
        when {
            // Feedback 2.6: spinner a pantalla completa solo mientras no hay NINGÚN
            // resultado; con parciales se muestra la lista con un indicador pequeño.
            isLoading && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = acc); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.txt_65dc881f), color = theme.textMuted, fontSize = 14.sp) } }
            errorMsg.isNotBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (networkError) "📡" else "🔍", fontSize = 40.sp); Spacer(Modifier.height(12.dp)); Text(errorMsg, color = if (networkError) Red else theme.textMuted, fontSize = 14.sp, fontWeight = if (networkError) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center); Spacer(Modifier.height(8.dp)); Text(if (networkError) stringResource(R.string.err_check_wifi_retry) else stringResource(R.string.err_try_other_language), color = if (networkError) Red.copy(alpha = 0.7f) else theme.textDim, fontSize = 12.sp, textAlign = TextAlign.Center) } }
            // Onboarding de la búsqueda sin conexión (1 de 3): este estado vacío lo ve todo
            // el que entra a buscar, no interrumpe a nadie y aparece justo cuando la
            // información sirve. Los otros dos sitios son la hoja de añadir libro y la
            // slide 4 del tutorial. Se descartaron una pantalla dedicada y una slide nueva.
            !searched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("🔎", fontSize = 48.sp); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.txt_af80d2f5), color = theme.textMain, fontSize = 16.sp); Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.search_empty_offline_hint), color = theme.textDim, fontSize = 12.sp, textAlign = TextAlign.Center) } }
            else -> {
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        CircularProgressIndicator(color = acc, strokeWidth = 2.dp, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_65dc881f), color = theme.textDim, fontSize = 11.sp)
                    }
                } else {
                    // Con un solo resultado decía "1 resultados". Se ve en cuanto buscas por ISBN,
                    // que es el caso más común de resultado único.
                    val resumen = if (results.size == 1) stringResource(R.string.search_results_summary_one)
                                  else stringResource(R.string.search_results_summary, results.size)
                    Text(resumen, color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(results) { result -> SearchResultCard(result, theme, onOpen = { detailResult = result }, onAdd = { selectedResult = result }) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(result: OpenLibraryResult, theme: Theme, onOpen: () -> Unit, onAdd: () -> Unit) {
    val acc = accentForTheme(theme)
    // Tocar la tarjeta abre la ficha de detalle; el "+" es el atajo para añadir directo.
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen), shape = RoundedCornerShape(14.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(result.coverUrl, result.title, size = 60, author = result.author)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(result.title, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (result.author.isNotBlank()) Text(result.author, color = theme.textMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                    if (result.pages > 0) Text(stringResource(R.string.stat_pages_short, result.pages), color = theme.textDim, fontSize = 11.sp)
                    if (result.publishYear.isNotBlank()) Text("📅 ${result.publishYear}", color = theme.textDim, fontSize = 11.sp)
                }
                if (result.genre.isNotBlank()) Text(mapApiGenre(result.genre).joinToString(" · ").ifBlank { result.genre }.take(50), color = acc.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onAdd, modifier = Modifier.size(36.dp).clip(CircleShape).background(acc)) { Icon(Icons.Default.Add, null, tint = onAccentColor(theme), modifier = Modifier.size(20.dp)) }
        }
    }
}

// ── LanguageSelector ──────────────────────────────────────────────────────────
@Composable
fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (code: String, label: String, flag: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Feedback 11-07: fuera "Edición principal" (no aporta como opción elegible) y
    // cooficiales estandarizadas como 🇪🇸 (CAT) / 🇪🇸 (EUS) / 🇪🇸 (GAL), con Euskera
    // y Galego añadidas.
    val languages = listOf(
        Triple("es",  "Español",           "🇪🇸"),
        Triple("en-us","English (US)",     "🇺🇸"),
        Triple("en-uk","English (UK)",     "🇬🇧"),
        // QA 12-07 (B-013): eliminado el "English 🌐" genérico del selector — redundante
        // con US/UK. Las ediciones existentes con 🌐 se conservan tal cual.
        Triple("fr",  "Français",          "🇫🇷"),
        Triple("de",  "Deutsch",           "🇩🇪"),
        Triple("it",  "Italiano",          "🇮🇹"),
        Triple("pt",  "Português",         "🇵🇹"),
        Triple("ca",  "Català",            "🇪🇸 (CAT)"),
        Triple("eu",  "Euskera",           "🇪🇸 (EUS)"),
        Triple("gl",  "Galego",            "🇪🇸 (GAL)"),
        Triple("ja",  "日本語",              "🇯🇵"),
        Triple("zh",  "中文",               "🇨🇳"),
        Triple("ko",  "한국어",              "🇰🇷")
    )
    var expanded by remember { mutableStateOf(false) }
    val selected = languages.firstOrNull { it.first == selectedLanguage } ?: languages.first()
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            // Este composable no recibe `theme`: el acento se remapea con themedAccentOr
            // (LocalAppTheme), el mecanismo previsto para componentes hoja
            border = BorderStroke(1.dp, if (expanded) themedAccentOr(Accent) else Color.Gray.copy(alpha = 0.3f))
        ) {
            Text("${selected.third} ${selected.second}", fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { (code, label, flag) ->
                DropdownMenuItem(
                    text = { Text("$flag $label", color = if (code == selectedLanguage) themedAccentOr(Accent) else Color.Unspecified, fontWeight = if (code == selectedLanguage) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onLanguageSelected(code, label, flag); expanded = false }
                )
            }
        }
    }
}

// ── AddScreen ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    theme: Theme,
    onBack: () -> Unit,
    externalIsbn: String? = null,
    onClearExternalIsbn: () -> Unit = {},
    onScanIsbn: () -> Unit = {}
) {
    val acc = accentForTheme(theme)
    var title by remember { mutableStateOf("") }; var author by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf("") }; var isbn by remember { mutableStateOf("") }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }; var status by remember { mutableStateOf(BookStatus.READING) }
    var startDate by remember { mutableStateOf(todayDisplay()) }; var endDate by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(0) }; var comment by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }; var statusExpanded by remember { mutableStateOf(false) }; var genreExpanded by remember { mutableStateOf(false) }
    var showOnePage by remember { mutableStateOf(false) }
    // v2.5: aviso de duplicado antes de añadir
    var duplicateCandidate by remember { mutableStateOf<Book?>(null) }
    var editionLanguage by remember { mutableStateOf("es") }; var editionLanguageLabel by remember { mutableStateOf("Español") }; var editionFlag by remember { mutableStateOf("🇪🇸") }
    var firstFuncPage by remember { mutableStateOf("") }; var lastFuncPage by remember { mutableStateOf("") }
    var manualCoverUrl by remember { mutableStateOf<String?>(null) }
    var showAddCoverDialog by remember { mutableStateOf(false) }
    var addCoverUrlInput by remember { mutableStateOf("") }

    duplicateCandidate?.let { cand ->
        val existing = vm.findDuplicate(cand) ?: cand
        DuplicateBookDialog(cand, existing, theme,
            onConfirm = { vm.addBook(cand, prefs); duplicateCandidate = null; onBack() },
            onDismiss = { duplicateCandidate = null })
    }
    val addCoverPreviewUrl: String? = when {
        manualCoverUrl != null -> manualCoverUrl
        isbn.trim().length >= 10 -> "https://covers.openlibrary.org/b/isbn/${isbn.trim()}-M.jpg"
        else -> null
    }

    // Rellenar ISBN si viene de scanner externo (solo dígitos/X, 10-13 chars)
    // v20.9: también autorellena título, autor, páginas y géneros vía API
    var isbnSearching by remember { mutableStateOf(false) }
    var isbnAutoError by remember { mutableStateOf(false) }
    LaunchedEffect(externalIsbn) {
        if (!externalIsbn.isNullOrBlank()) {
            val safeIsbn = externalIsbn.replace(Regex("[^\\dXx]"), "").uppercase()
            if (safeIsbn.length in 10..13) {
                isbn = safeIsbn
                isbnSearching = true
                isbnAutoError = false
                // withContext en vez de launch anidado: si LaunchedEffect se cancela,
                // la búsqueda también se cancela (sin race condition).
                val meta = withContext(Dispatchers.IO) { fetchIsbnFullMetadata(safeIsbn) }
                isbnSearching = false
                if (meta.title != null && title.isBlank()) title = meta.title
                if (meta.author != null && author.isBlank()) author = meta.author
                if (meta.pages != null && pages.isBlank()) pages = meta.pages.toString()
                if (meta.genres.isNotEmpty() && genres.isEmpty()) genres = meta.genres
                if (meta.coverUrl != null && manualCoverUrl == null) manualCoverUrl = meta.coverUrl
                if (meta.title == null && meta.author == null && meta.pages == null) {
                    isbnAutoError = true
                }
            }
            onClearExternalIsbn()
        }
    }

    val addContext = androidx.compose.ui.platform.LocalContext.current
    val addImagePickerReal = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val coversDir = java.io.File(addContext.filesDir, "covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val tmpId = System.currentTimeMillis()
            val dest = java.io.File(coversDir, "add_tmp_$tmpId.jpg")
            addContext.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            manualCoverUrl = dest.absolutePath
        } catch (_: Exception) {}
    }

    if (showAddCoverDialog) {
        AlertDialog(
            onDismissRequest = { showAddCoverDialog = false },
            title = { Text(stringResource(R.string.txt_41118960), color = theme.textMain) },
            text = {
                Column {
                    Text(stringResource(R.string.txt_aa02a2da), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(value = addCoverUrlInput, onValueChange = { addCoverUrlInput = it }, placeholder = { Text(stringResource(R.string.txt_14f2b208), color = theme.textDim) }, colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { addImagePickerReal.launch("image/*"); showAddCoverDialog = false }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, acc.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = acc)) { Text(stringResource(R.string.txt_a8830f9a)) }
                }
            },
            confirmButton = { TextButton(onClick = { if (addCoverUrlInput.isNotBlank()) { manualCoverUrl = addCoverUrlInput.trim() }; showAddCoverDialog = false }) { Text(stringResource(R.string.txt_f0ed2dc3), color = acc) } },
            dismissButton = { TextButton(onClick = { showAddCoverDialog = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } },
            containerColor = theme.bgMid
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 28.dp, bottom = 20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = theme.textMain) }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.txt_29b6d9fc), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(20.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)) {
            Column(Modifier.padding(24.dp)) {
                // Cover preview + button
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (addCoverPreviewUrl != null) {
                            BookCover(addCoverPreviewUrl, title, size = 90)
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedButton(
                            onClick = { addCoverUrlInput = manualCoverUrl ?: ""; showAddCoverDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, acc.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = acc)
                        ) {
                            Text(if (manualCoverUrl != null) stringResource(R.string.btn_change_cover) else stringResource(R.string.btn_add_cover), fontSize = 12.sp)
                        }
                        if (manualCoverUrl != null) {
                            TextButton(onClick = { manualCoverUrl = null; addCoverUrlInput = "" }) {
                                Text(stringResource(R.string.txt_ba94296a), color = Red, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FormField("Title *", title, "The book's name", theme) { title = it }
                FormField(stringResource(R.string.txt_c481b00a), author, "Author name", theme) { author = it }
                FormField("Pages *", pages, "350", theme, KeyboardType.Number) { pages = it }
                // Páginas funcionales (opcionales) — entre páginas y género
                Text(stringResource(R.string.txt_066bbf84), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                Text(stringResource(R.string.txt_3d8b847e), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_8803fa48), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = firstFuncPage, onValueChange = { firstFuncPage = it.filter { c -> c.isDigit() } },
                            placeholder = { Text(stringResource(R.string.txt_a7931e50), color = theme.textDim) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.txt_11938ee1), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = lastFuncPage, onValueChange = { lastFuncPage = it.filter { c -> c.isDigit() } },
                            placeholder = { Text(stringResource(R.string.txt_5dedc405), color = theme.textDim) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors(theme), shape = RoundedCornerShape(10.dp),
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // Género — multi-select hasta 2
                Text(stringResource(R.string.txt_57d644ad), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                // P-012: bottom sheet con buscador y grupos en lugar del dropdown plano
                Box(Modifier.padding(bottom = 16.dp)) {
                    OutlinedTextField(
                        value = if (genres.isEmpty()) "" else genres.map { displayGenre(it) }.joinToString(" · "),
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text(stringResource(R.string.txt_84a8f3ea), color = theme.textDim, fontSize = 13.sp) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, tint = theme.textDim) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(theme),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Box(Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).clickable { genreExpanded = true })
                }
                if (genreExpanded) {
                    GenreSelectorSheet(
                        initial = genres,
                        suggested = emptyList(),
                        theme = theme,
                        onDismiss = { genreExpanded = false },
                        onConfirm = { genres = it }
                    )
                }
                Text(stringResource(R.string.txt_4239bda5), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                LanguageSelector(selectedLanguage = editionLanguage, onLanguageSelected = { code, label, flag -> editionLanguage = code; editionLanguageLabel = label; editionFlag = flag }, modifier = Modifier.padding(bottom = 16.dp))
                // ISBN con botón de escaneo
                Text(stringResource(R.string.txt_fb84daae), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = if (isbnSearching || isbnAutoError) 4.dp else 16.dp)) {
                    OutlinedTextField(
                        value = isbn,
                        onValueChange = { isbn = it },
                        placeholder = { Text(stringResource(R.string.txt_eb9316fa), color = theme.textDim, fontSize = 13.sp) },
                        colors = fieldColors(theme),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onScanIsbn,
                        modifier = Modifier.size(48.dp).background(acc.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_barcode), contentDescription = "Scan ISBN", tint = acc, modifier = Modifier.size(22.dp))
                    }
                }
                // v20.9: estado del autorelleno por ISBN
                if (isbnSearching) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = acc)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.txt_f01d3d6b), color = theme.textDim, fontSize = 12.sp)
                    }
                } else if (isbnAutoError) {
                    // v2.5: rojo (antes gris/textDim) + i18n (antes hardcoded ES)
                    // Feedback 2.7: informativo (el ISBN se conserva), no error fatal → ámbar
                    Text(stringResource(R.string.isbn_scan_not_found), color = Amber, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                }
                Text(stringResource(R.string.txt_a3b8e497), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                OutlinedTextField(value = comment, onValueChange = { comment = it }, placeholder = { Text(stringResource(R.string.txt_f52cebe0), color = theme.textDim, fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).heightIn(min = 80.dp), colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), maxLines = 4)
                Text(stringResource(R.string.txt_3397e69c), color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = it }) {
                    OutlinedTextField(value = statusLabel(status), onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = fieldColors(theme))
                    ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                        listOf(BookStatus.READING, BookStatus.FINISHED, BookStatus.REREADING, BookStatus.PENDING, BookStatus.DROPPED).forEach { s -> DropdownMenuItem(text = { Text(statusLabel(s), color = theme.textMain) }, onClick = { status = s; statusExpanded = false; if (s == BookStatus.PENDING) { startDate = ""; endDate = "" } else if (startDate.isEmpty()) startDate = todayDisplay() }) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (status == BookStatus.READING || status == BookStatus.FINISHED || status == BookStatus.REREADING || status == BookStatus.DROPPED) FormField(stringResource(R.string.form_start_date), startDate, todayDisplay(), theme, KeyboardType.Ascii) { startDate = it }
                if (status == BookStatus.FINISHED || status == BookStatus.REREADING) {
                    FormField(stringResource(R.string.form_end_date), endDate, todayDisplay(), theme, KeyboardType.Ascii) { endDate = it }
                    Text(stringResource(R.string.txt_950a32e2), color = theme.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
                    RatingSelector(rating) { rating = it }; Spacer(Modifier.height(16.dp))
                }
                if (error.isNotEmpty()) Text(error, color = Red, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                if (showOnePage) {
                    AlertDialog(
                        onDismissRequest = { showOnePage = false },
                        containerColor = theme.bgMid,
                        title = { Text(stringResource(R.string.txt_fd31153a), color = theme.textMain, fontWeight = FontWeight.Bold) },
                        text = { Text(stringResource(R.string.txt_5f6c3709), color = theme.textMuted, fontSize = 13.sp) },
                        confirmButton = {
                            TextButton(onClick = {
                                showOnePage = false
                                val cover = manualCoverUrl ?: if (isbn.trim().length >= 10) "https://covers.openlibrary.org/b/isbn/${isbn.trim()}-M.jpg" else null
                                val hasEnd = status == BookStatus.FINISHED || status == BookStatus.REREADING
                                val newBook = Book(title = title.trim(), author = author.trim(), pages = 1, status = status, startDate = startDate.takeIf { it.isNotEmpty() }?.let { displayToStored(it) }, endDate = endDate.takeIf { it.isNotEmpty() && hasEnd }?.let { displayToStored(it) }, dropDate = if (status == BookStatus.DROPPED) (startDate.takeIf { it.isNotEmpty() }?.let { displayToStored(it) } ?: today()) else null, rating = if (hasEnd) rating else 0, coverUrl = cover, isbn = isbn.trim().takeIf { it.isNotEmpty() }, comment = comment.trim(), genres = genres, firstFunctionalPage = firstFuncPage.toIntOrNull(), lastFunctionalPage = lastFuncPage.toIntOrNull())
                                val firstEdition = BookEdition(id = newBook.id, language = editionLanguage, languageLabel = editionLanguageLabel, flag = editionFlag, title = newBook.title, pages = newBook.pages, coverUrl = newBook.coverUrl, isbn = newBook.isbn, isActive = true)
                                val toAdd = newBook.copy(editions = listOf(firstEdition))
                                if (vm.findDuplicate(toAdd) != null) { duplicateCandidate = toAdd } else { vm.addBook(toAdd, prefs); onBack() }
                            }) { Text(stringResource(R.string.txt_d1cdc7bc), color = acc, fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = { TextButton(onClick = { showOnePage = false }) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
                    )
                }
                Button(onClick = {
                    error = validate(addContext, title, pages, status, displayToStored(startDate), displayToStored(endDate))
                    if (error.isEmpty() && pages.trim() == "1") { showOnePage = true; return@Button }
                    if (error.isEmpty()) {
                        val cover = manualCoverUrl ?: if (isbn.trim().length >= 10) "https://covers.openlibrary.org/b/isbn/${isbn.trim()}-M.jpg" else null
                        val hasEnd = status == BookStatus.FINISHED || status == BookStatus.REREADING
                        val newBook = Book(title = title.trim(), author = author.trim(), pages = pages.toInt(), status = status, startDate = startDate.takeIf { it.isNotEmpty() }?.let { displayToStored(it) }, endDate = endDate.takeIf { it.isNotEmpty() && hasEnd }?.let { displayToStored(it) }, dropDate = if (status == BookStatus.DROPPED) (startDate.takeIf { it.isNotEmpty() }?.let { displayToStored(it) } ?: today()) else null, rating = if (hasEnd) rating else 0, coverUrl = cover, isbn = isbn.trim().takeIf { it.isNotEmpty() }, comment = comment.trim(), genres = genres, firstFunctionalPage = firstFuncPage.toIntOrNull(), lastFunctionalPage = lastFuncPage.toIntOrNull())
                        val firstEdition = BookEdition(
                            id            = newBook.id,
                            language      = editionLanguage,
                            languageLabel = editionLanguageLabel,
                            flag          = editionFlag,
                            title         = newBook.title,
                            pages         = newBook.pages,
                            coverUrl      = newBook.coverUrl,
                            isbn          = newBook.isbn,
                            isActive      = true
                        )
                        val toAdd = newBook.copy(editions = listOf(firstEdition))
                        if (vm.findDuplicate(toAdd) != null) { duplicateCandidate = toAdd } else { vm.addBook(toAdd, prefs); onBack() }
                    }
                }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = onAccentColor(theme))) { Text(stringResource(R.string.txt_e3ea89d7), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
        }
    }
}

@Composable
fun RatingSelector(current: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { (1..5).forEach { n -> RatingChip(n, current == n) { onSelect(n) } } }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { (6..10).forEach { n -> RatingChip(n, current == n) { onSelect(n) } } }
        if (current > 0) { Spacer(Modifier.height(6.dp)); Text(ratingLabelLocalized(current), color = Gold, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally)) }
    }
}

@Composable
fun RatingChip(value: Int, selected: Boolean, onClick: () -> Unit) {
    // v21.35: eliminado debounce de 300ms que causaba retraso perceptible
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (selected) Gold else Color(0x22FFBB33))
            .clickable(onClick = onClick)
    ) {
        Text("$value", color = if (selected) Color.White else Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

fun ratingLabel(r: Int) = when (r) { 1, 2 -> "Muy malo 😞"; 3, 4 -> "Regular 😐"; 5, 6 -> "Bien 🙂"; 7, 8 -> "Muy bueno 😊"; 9 -> "¡Excelente! 🌟"; 10 -> "Obra maestra ✨"; else -> "" }

@Composable
fun ratingLabelLocalized(r: Int) = when (r) {
    1, 2 -> stringResource(R.string.rating_muy_malo)
    3, 4 -> stringResource(R.string.rating_regular)
    5, 6 -> stringResource(R.string.rating_bien)
    7, 8 -> stringResource(R.string.rating_muy_bueno)
    9    -> stringResource(R.string.rating_excelente)
    10   -> stringResource(R.string.rating_obra_maestra)
    else -> ""
}

fun validate(context: android.content.Context, title: String, pages: String, status: BookStatus, start: String, end: String): String {
    if (title.isBlank()) return context.getString(R.string.err_title_required)
    if (pages.toIntOrNull()?.let { it < 1 } != false) return context.getString(R.string.err_pages_invalid)
    if (status != BookStatus.PENDING && start.isEmpty()) return context.getString(R.string.err_start_date_required)
    if ((status == BookStatus.FINISHED || status == BookStatus.REREADING) && end.isEmpty()) return context.getString(R.string.err_end_date_required)
    if ((status == BookStatus.FINISHED || status == BookStatus.REREADING) && end < start) return context.getString(R.string.err_end_before_start)
    return ""
}

@Composable
fun FormField(label: String, value: String, placeholder: String, theme: Theme, keyboardType: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    Text(label, color = theme.textMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
    OutlinedTextField(value = value, onValueChange = onChange, placeholder = { Text(placeholder, color = theme.textDim) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), keyboardOptions = KeyboardOptions(keyboardType = keyboardType), colors = fieldColors(theme), shape = RoundedCornerShape(10.dp), singleLine = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun fieldColors(theme: Theme) = OutlinedTextFieldDefaults.colors(focusedTextColor = theme.textMain, unfocusedTextColor = theme.textMain, focusedBorderColor = accentForTheme(theme), unfocusedBorderColor = theme.border, cursorColor = accentForTheme(theme), focusedContainerColor = theme.surface, unfocusedContainerColor = theme.surface)

// ── DetailScreen ──────────────────────────────────────────────────────────────

enum class TimerState { IDLE, RUNNING, PAUSED }

@OptIn(ExperimentalMaterial3Api::class)
// ── EditionsSection composable ────────────────────────────────────────────────
@Composable
fun EditionsSection(
    book: Book,
    editions: List<BookEdition>,
    theme: Theme,
    onChangeEdition: (Long) -> Unit,
    onAddEdition: () -> Unit,
    onSetActive: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onUpdatePages: (editionId: Long, pages: Int) -> Unit = { _, _ -> }
) {
    val acc = accentForTheme(theme)
    // D-015 (Cuero): filete dorado interior en la tarjeta de ediciones (solo tema Cuero)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = theme.bgSurf,
        border = BorderStroke(1.dp, theme.border),
        modifier = Modifier.fillMaxWidth().cueroFilete(theme, 14.dp)
    ) {
        Column {
            // Header
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.txt_e38ba5d3),
                    color = theme.textDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.weight(1f)
                )
                if (editions.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = acc.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "${editions.size}",
                            color = acc,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Divider(color = theme.border, thickness = 1.dp)

            // Edition cards
            editions.forEachIndexed { idx, ed ->
                val isReading = book.status == BookStatus.READING || book.isRereading || book.status == BookStatus.REREADING
                val isActiveReading = ed.isActive && isReading
                // Use the shelf color for the active edition highlight (matches the shelve color)
                val shelfColor = when (book.status) {
                    BookStatus.READING   -> Color(0xFFF59E0B) // Amber
                    BookStatus.REREADING -> Color(0xFF06B6D4) // Cyan
                    BookStatus.FINISHED  -> Color(0xFF10B981) // Green
                    BookStatus.PENDING   -> Color(0xFF8B5CF6) // Purple
                    BookStatus.DROPPED   -> Color(0xFFF87171) // Red
                }
                val borderStart = if (ed.isActive) shelfColor else Color.Transparent

                // P-031 (Bloque 4, feature 3, opción A de Víctor): ediciones colapsables.
                // Solo UI: no hay dato nuevo ni migración. La lista larguísima de antes
                // (ISBN y tres botones por edición, siempre desplegados) se pliega y deja a
                // la vista lo que sirve para ELEGIR de un vistazo: bandera, idioma, año,
                // páginas y el estado.
                //
                // Cuál se abre por defecto: la activa (decisión del mockup). Estado NO
                // persistido: en Ajustes sí (sect_backup_expanded) porque son secciones
                // fijas, pero las ediciones van y vienen y guardar por id ensuciaría las
                // prefs para nada. remember(ed.id) y ya.
                var edExpanded by remember(ed.id) { mutableStateOf(ed.isActive) }

                Column(
                    Modifier
                        // AMOLED: bgMid ES bgDark (negro puro), así que una tarjeta pintada
                        // con bgMid es invisible. cardColor da #141414 plegada y #242424
                        // abierta: se distinguen sin romper el negro puro.
                        .background(cardColor(theme, edExpanded))
                        // La activa lleva además su tinte de estantería ENCIMA del gris base
                        .background(if (ed.isActive) shelfColor.copy(alpha = 0.06f) else Color.Transparent)
                        .drawBehind {
                            if (ed.isActive) drawRect(color = borderStart, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                        }
                ) {
                    // ── Cabecera plegable ────────────────────────────────────────
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { edExpanded = !edExpanded }
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        // Bandera (solo display, no interactiva)
                        Text(
                            ed.flag,
                            fontSize = 22.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            // Idioma y año: lo que distingue una edición de otra de un vistazo.
                            // Si la edición no tiene ni idioma ni año (importaciones viejas),
                            // cae al título: una cabecera vacía sería imposible de identificar.
                            // Feedback 17-07: el año solo se muestra si es un año de verdad. Las
                            // ediciones importadas de OpenLibrary guardaban a veces el mes ("Oct")
                            // en publishYear (su publish_date es "Oct 2020" y el parser hacía
                            // take(4)); mostrar "Inglés, Oct" cantaba. Si el valor no es un año de
                            // 4 cifras, no se enseña. El parser también se arregló para futuras altas.
                            val yearShown = ed.publishYear.takeIf { Regex("^\\d{4}$").matches(it) }
                            val head = listOfNotNull(
                                ed.languageLabel.ifBlank { null },
                                yearShown
                            ).joinToString(", ").ifBlank { ed.title.ifBlank { book.title } }
                            Text(head, color = theme.textMain, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            // Feedback 17-07: la editorial también distingue una edición de otra
                            // (misma lengua y año, sello distinto). Va en la sublínea junto a las
                            // páginas ("Minotauro · 320 págs") para no recargar la cabecera; el
                            // maxLines=1 + ellipsis la recorta si no cabe, así que nunca desborda.
                            val subParts = listOfNotNull(
                                ed.publisher.ifBlank { null },
                                if (ed.pages > 0) stringResource(R.string.editions_pages_short, ed.pages) else null
                            )
                            if (subParts.isNotEmpty()) {
                                // textMuted y no textDim: medido, textDim sobre la tarjeta se
                                // queda en 2,1:1 (Oscuro abierta) y 2,7:1 (AMOLED). Con
                                // textMuted el peor caso sube a 3,97:1 (Oscuro abierta), que
                                // es el mismo suelo que ya tiene el subtítulo de SettingsSection.
                                Text(subParts.joinToString(" · "), color = theme.textMuted, fontSize = 10.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                        // Status pill — v21.41: distingue Reading de Rereading
                        if (isActiveReading) {
                            val pillText = if (book.status == BookStatus.REREADING)
                                stringResource(R.string.pill_rereading)
                            else
                                stringResource(R.string.txt_125ed9b0)
                            Surface(shape = RoundedCornerShape(20.dp), color = shelfColor.copy(alpha = 0.13f)) {
                                Text(pillText, color = shelfColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                            }
                        } else if (ed.isActive) {
                            Surface(shape = RoundedCornerShape(20.dp), color = shelfColor.copy(alpha = 0.13f)) {
                                Text(statusLabel(book.status), color = shelfColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                            }
                        }
                        Icon(
                            Icons.Default.KeyboardArrowDown, null,
                            tint = theme.textDim,
                            modifier = Modifier.size(18.dp).rotate(if (edExpanded) 0f else -90f)
                        )
                    }

                    // ── Cuerpo (solo abierta) ────────────────────────────────────
                    if (edExpanded) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 11.dp)) {
                    // El título de la edición baja aquí: puede diferir por idioma y sigue
                    // siendo útil, pero no es lo que se mira para elegir.
                    Text(ed.title.ifBlank { book.title }, color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    // La editorial ya sale en la sublínea de la cabecera (siempre visible); no
                    // se repite aquí.
                    if (!ed.isbn.isNullOrBlank()) {
                        Text(
                            "ISBN: ${ed.isbn}",
                            color = theme.textMuted,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    if (ed.pages > 0 || ed.isActive) {
                        Spacer(Modifier.height(7.dp))
                        var editingEditionPages by remember(ed.id) { mutableStateOf(false) }
                        var editionPagesInput by remember(ed.id) { mutableStateOf(ed.pages.toString().takeIf { ed.pages > 0 } ?: "") }
                        Surface(shape = RoundedCornerShape(9.dp), color = theme.bgSurf2, border = BorderStroke(1.dp, if (ed.isActive) shelfColor.copy(alpha = 0.27f) else theme.border), modifier = Modifier.fillMaxWidth()) {
                            if (editingEditionPages) {
                                Row(Modifier.padding(horizontal = 11.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    androidx.compose.material3.OutlinedTextField(
                                        value = editionPagesInput,
                                        onValueChange = { editionPagesInput = it.filter { c -> c.isDigit() } },
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        textStyle = androidx.compose.ui.text.TextStyle(color = theme.textMain, fontSize = 14.sp)
                                    )
                                    TextButton(onClick = { editingEditionPages = false }) { Text("✕", color = Red) }
                                    TextButton(onClick = {
                                        val p = editionPagesInput.toIntOrNull()
                                        if (p != null && p > 0) {
                                            onUpdatePages(ed.id, p)
                                        }
                                        editingEditionPages = false
                                    }) { Text("✓", color = acc) }
                                }
                            } else {
                                Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp).clickable { editingEditionPages = true; editionPagesInput = ed.pages.toString().takeIf { ed.pages > 0 } ?: "" }, verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.txt_939f09a3), color = theme.textDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                    Text(if (ed.pages > 0) "${ed.pages}" else "-", color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    Spacer(Modifier.width(6.dp))
                                    Text("✎", color = if (ed.isActive) shelfColor else theme.textDim, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (!ed.isActive) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = acc.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, acc.copy(alpha = 0.45f)),
                                modifier = Modifier.clickable { onSetActive(ed.id) }
                            ) {
                                Text(stringResource(R.string.txt_59d90b1a), color = acc, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        // Change edition button
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = theme.bgSurf2,
                            border = BorderStroke(1.dp, theme.border),
                            modifier = Modifier.weight(1f).clickable { onChangeEdition(ed.id) }
                        ) {
                            Text(stringResource(R.string.txt_d6578c6a), color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        // Remove if more than one
                        if (editions.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, Color(0x44EF4444)),
                                modifier = Modifier.clickable { onRemove(ed.id) }
                            ) {
                                Text("✕", color = Color(0xFFEF4444), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                            }
                        }
                    }
                    }   // fin del cuerpo desplegado
                }
                if (idx < editions.lastIndex) Divider(color = theme.border, thickness = 1.dp)
            }

            // P-031 + D-013 (r2 17-07): tope de ediciones 3 gratis (base + 2) / Pro sin
            // límite. Quien ya tenga más del tope gratis NUNCA pierde nada: solo se
            // bloquea añadir.
            val edContext = LocalContext.current
            val edPrefs = remember { edContext.getSharedPreferences("lecturameter", android.content.Context.MODE_PRIVATE) }
            var showEditionUpsell by remember { mutableStateOf(false) }
            if (showEditionUpsell) {
                ProUpsellSheet(theme, edPrefs, onDismiss = { showEditionUpsell = false })
            }
            val edLimit = com.lecturameter.utils.Pro.editionLimit(edPrefs)
            if (editions.size < edLimit) {
                Divider(color = theme.border, thickness = 1.dp)
                Box(
                    Modifier.fillMaxWidth().clickable { onAddEdition() }.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.txt_b7369062), color = theme.textDim, fontSize = 13.sp)
                }
            } else if (!com.lecturameter.utils.Pro.isPro(edPrefs)) {
                Divider(color = theme.border, thickness = 1.dp)
                Box(
                    Modifier.fillMaxWidth().clickable { showEditionUpsell = true }.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.pro_editions_hint), color = accentForTheme(theme), fontSize = 13.sp)
                }
            }

            // Multi-edition note
            if (editions.size > 1) {
                Divider(color = theme.border, thickness = 1.dp)
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📊", fontSize = 12.sp)
                    Text(
                        stringResource(R.string.editions_summary, editions.size, editions.sumOf { it.pages }),
                        color = theme.textDim,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}



@Composable
fun StatBox(value: String, label: String, modifier: Modifier, theme: Theme, highlight: Boolean = false, highlightColor: Color = Green) {
    // QA 12-07 r2 (Aurora): el índigo global apenas contrasta sobre el fondo teal —
    // cuando el highlight es el acento, usar el acento DEL TEMA (morado en Aurora).
    val hl = if (highlightColor == Accent) accentForTheme(theme) else highlightColor
    val bgColor   = if (highlight) hl.copy(alpha = 0.07f) else theme.surface
    val brdColor  = if (highlight) hl.copy(alpha = 0.3f)  else theme.border
    val txtColor  = if (highlight) hl else theme.textMain
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = bgColor, border = BorderStroke(1.dp, brdColor)) {
        Column(
            Modifier.height(66.dp).padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AutoSizeText(value, color = txtColor, maxFontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(2.dp))
            Text(label, color = theme.textDim, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2, softWrap = true, lineHeight = 11.sp, overflow = TextOverflow.Visible, modifier = Modifier.fillMaxWidth())
        }
    }
}
