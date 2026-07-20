package com.lecturameter

// BingoScreen, DuplicateBookDialog, RailItem, HomeRail (mini-rail D-002), normalizeSearchText y fuzzyMatch.
// Extraido de MainActivity.kt el 15-07-2026 (ruptura del monolito, sin cambios funcionales).


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.aspectRatio
import com.lecturameter.model.*
import com.lecturameter.utils.*
import androidx.navigation.compose.composable

// ── Bingo (Fase 5, MD5): cartón con plantillas rotativas mensuales ────────────
// Las celdas se marcan solas (ver BingoManager). Al completar el cartón entero
// antes de fin de mes se ofrece uno nuevo inmediatamente; si no, rota el día 1.
//
// B4 (2): la pantalla muestra DOS cartones que conviven, con un selector arriba.
//   · Mensual 4×4 → gratis para todos, el de siempre, con el acento del tema.
//   · Reto 3×3    → extra de Pro, más corto y más duro, teñido de rojo (redForTheme).
// Un usuario gratis ve la pestaña del 3×3 y puede pulsarla: lo que salta es la hoja de
// Pro explicándolo. Se enseña, no se esconde: es la diferencia entre un límite y un muro.
@Composable
fun BingoScreen(vm: BooksViewModel, prefs: android.content.SharedPreferences, theme: Theme, onBack: () -> Unit, onHistory: () -> Unit = {}) {
    BackHandler { onBack() }
    val card4 by vm.bingoCard.collectAsState()
    val card3 by vm.bingoCard3.collectAsState()
    val books by vm.books.collectAsState()
    // Etiquetas del JSON en el idioma de la app (mismo criterio que el resto de la UI)
    val isEs = androidx.compose.ui.platform.LocalConfiguration.current.locales.get(0)?.language == "es"

    // El Pro puede cambiar mientras la pantalla está viva (se compra en la propia hoja),
    // así que se relee con un tick en vez de fijarlo en el primer frame.
    var proTick by remember { mutableStateOf(0) }
    val isPro = remember(proTick) { com.lecturameter.utils.Pro.isPro(prefs) }
    // Dos hojas encadenadas: primero la explicación del 3×3 (el "mensaje" que pidió
    // Víctor) y solo si el usuario quiere, la de venta. Enseñar el precio de golpe al
    // tocar una pestaña es agresivo; explicar qué es primero, no.
    var showUpsell by remember { mutableStateOf(false) }
    var showProSheet by remember { mutableStateOf(false) }

    // El Pro puede haberse comprado en AJUSTES, no aquí: en ese caso nadie ha creado aún el
    // cartón 3×3 y sin esto habría que reiniciar la app para verlo. ensureBingoCard es
    // idempotente (si ya existe y es del mes, no hace nada), así que entrar al Bingo es un
    // sitio seguro para asegurarlo. Al revés también: si el trial caducó estando la app
    // abierta, aquí se archiva el 3×3 en el historial en vez de dejarlo colgado.
    LaunchedEffect(isPro) {
        if (isPro) vm.ensureBingoCard(prefs, com.lecturameter.utils.BingoManager.SIDE_3)
        else vm.reconcileBingo3Entitlement(prefs)
    }
    // Pestaña elegida, recordada entre visitas. Quien dejó abierto el 3×3 y ya no es Pro
    // (trial caducado) vuelve al 4×4: su 3×3 no se ha perdido, está en el historial.
    var side by remember {
        val saved = prefs.getInt("bingo_side", com.lecturameter.utils.BingoManager.SIDE_4)
        val safe = if (saved == com.lecturameter.utils.BingoManager.SIDE_3 && !com.lecturameter.utils.Pro.isPro(prefs))
            com.lecturameter.utils.BingoManager.SIDE_4 else saved
        mutableStateOf(safe)
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.bgDark).systemBarsPadding()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 28.dp, start = 16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = theme.textMain)
        }
        // Historial de bingos (los dos tamaños). Mismo gesto que el historial de retos.
        IconButton(
            onClick = onHistory,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 16.dp)
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = stringResource(R.string.bingo_history_open),
                tint = actionIconTint(theme)
            )
        }
        val c = if (side == com.lecturameter.utils.BingoManager.SIDE_3) card3 else card4
        // Título + selector + cuerpo en UNA columna, y el cuerpo scrollea dentro. Antes el
        // cuerpo iba posicionado con un padding fijo desde arriba: con el selector metido en
        // medio ese número deja de cuadrar (y se solapa) en cuanto cambia una tipografía o
        // el tamaño de fuente del sistema. Aquí lo resuelve el layout, no una constante.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(top = 76.dp)
        ) {
            Text(stringResource(R.string.bingo_title), color = theme.textMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            BingoSideSelector(
                side = side, isPro = isPro, theme = theme,
                onSelect = { wanted ->
                    if (wanted == com.lecturameter.utils.BingoManager.SIDE_3 && !isPro) showUpsell = true
                    else { side = wanted; prefs.edit().putInt("bingo_side", wanted).apply() }
                }
            )
            Spacer(Modifier.height(12.dp))
            if (c == null) {
                // Sin cartón (no debería ocurrir en el 4×4: ensureBingoCard corre en load).
                // En el 3×3 es lo normal durante el primer frame tras hacerse Pro.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("…", color = theme.textMuted)
                }
            } else {
                BingoCardBody(vm, prefs, theme, c, books, isEs, isPro, Modifier.weight(1f))
            }
        }
        if (showUpsell) {
            Bingo3GateSheet(
                theme = theme,
                onDismiss = { showUpsell = false },
                onSeePro = { showUpsell = false; showProSheet = true }
            )
        }
        if (showProSheet) {
            ProUpsellSheet(
                theme, prefs,
                onDismiss = { showProSheet = false },
                onProChanged = {
                    proTick++
                    // Recién comprado o estrenado el trial: el 3×3 se crea aquí mismo
                    vm.ensureBingoCard(prefs, com.lecturameter.utils.BingoManager.SIDE_3)
                }
            )
        }
    }
}

/** B4 (2): color del cartón de cada tamaño. El 4×4 se queda con el acento de su tema
 *  (no cambia NADA respecto a hoy) y el 3×3 se tiñe de rojo. */
internal fun cardTint(theme: Theme, side: Int): Color =
    if (side == com.lecturameter.utils.BingoManager.SIDE_3) redForTheme(theme) else accentForTheme(theme)

/** Tinta legible sobre un relleno de cardTint (ver onAccentColor / onRedColor). */
internal fun onCardTint(theme: Theme, side: Int): Color =
    if (side == com.lecturameter.utils.BingoManager.SIDE_3) onRedColor(theme) else onAccentColor(theme)

// ── B4 (2): selector de cartón ────────────────────────────────────────────────
// Dos pestañas en una pastilla. La activa se rellena con el color de SU cartón (acento
// para el 4×4, rojo para el 3×3), así el color enseña de un vistazo en cuál estás. El
// relleno se desliza entre las dos con animación, en vez de aparecer de golpe.
//
// La pestaña del 3×3 NO se deshabilita para el usuario gratis: se puede pulsar y lo que
// salta es la explicación. Un control gris y muerto no comunica nada; uno que responde y
// te cuenta qué es, sí.
@Composable
private fun BingoSideSelector(
    side: Int,
    isPro: Boolean,
    theme: Theme,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val s3 = com.lecturameter.utils.BingoManager.SIDE_3
    val s4 = com.lecturameter.utils.BingoManager.SIDE_4
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            // cardColor() y no bgMid: en AMOLED bgMid ES bgDark y la pastilla sería invisible
            .background(cardColor(theme))
            .border(1.dp, theme.border, RoundedCornerShape(12.dp))
            .padding(3.dp)
    ) {
        for (s in listOf(s4, s3)) {
            val selected = s == side
            val tint = cardTint(theme, s)
            // El relleno de la pestaña activa entra con transición, no de golpe
            val bg by androidx.compose.animation.animateColorAsState(
                targetValue = if (selected) tint else Color.Transparent,
                animationSpec = tween(durationMillis = 260), label = "bingo_tab_bg"
            )
            val fg by androidx.compose.animation.animateColorAsState(
                targetValue = if (selected) onCardTint(theme, s) else theme.textMuted,
                animationSpec = tween(durationMillis = 260), label = "bingo_tab_fg"
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onSelect(s) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        s == s4    -> stringResource(R.string.bingo_tab_4)
                        isPro      -> stringResource(R.string.bingo_tab_3)
                        else       -> stringResource(R.string.bingo_tab_3_locked)
                    },
                    color = fg, fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ── B4 (2): el mensaje del gate ───────────────────────────────────────────────
// Lo que ve un usuario gratis al tocar la pestaña del 3×3. Explica QUÉ es y, sobre todo,
// deja claro que su 4×4 no se toca: el miedo razonable al ver un candado en una función
// que ya usabas es "me han quitado algo", y aquí no se le ha quitado nada a nadie.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Bingo3GateSheet(theme: Theme, onDismiss: () -> Unit, onSeePro: () -> Unit) {
    val acc = accentForTheme(theme)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = theme.bgMid) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.bingo_3_gate_title),
                color = theme.textMain, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.bingo_3_gate_body),
                color = theme.textMuted, fontSize = 13.sp, lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onSeePro,
                colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = onAccentColor(theme)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text(stringResource(R.string.bingo_3_gate_cta), fontWeight = FontWeight.Bold) }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bingo_3_gate_dismiss), color = theme.textDim, fontSize = 12.sp)
            }
        }
    }
}

// Cuerpo del cartón (título, cuadrícula y pie). Extraído de BingoScreen al entrar el
// selector: los dos tamaños comparten ESTE código, así que el 3×3 hereda la animación
// del 4×4 por construcción y no por copia. Si se retoca el flip, se retoca para los dos.
@Composable
private fun BingoCardBody(
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    theme: Theme,
    c: BingoCard,
    books: List<Book>,
    isEs: Boolean,
    isPro: Boolean,
    modifier: Modifier = Modifier
) {
    // El alto lo fija quien llama (weight en la columna de BingoScreen): así el cartón ocupa
    // lo que sobra bajo el selector y su scroll interno se queda dentro de ese hueco.
    Box(modifier.fillMaxWidth()) {
        // Feedback 13-07 (10): lado dinámico según la plantilla (9 → 3×3, 16 → 4×4)
        val side = com.lecturameter.utils.BingoManager.sideOf(c.cells.size).coerceAtLeast(3)
        // B4 (2): el 3×3 se tiñe de rojo por tema; el 4×4 conserva su acento intacto.
        val accent = cardTint(theme, side)

        // ── Fase 4 (D-003, 4): flip + glow fusionados, SOLO la primera vez ──────────
        // La celda completada gira una vez y suelta un destello que se apaga; al cerrar
        // una línea, el glow la recorre y muere. Un set persistido por cartón recuerda
        // qué celdas/líneas ya animaron (al volver al Bingo se pintan quietas).
        // Con "reducir animaciones" del sistema se marca sin animar.
        val bingoCtx = androidx.compose.ui.platform.LocalContext.current
        val bingoReduceMotion = remember {
            android.provider.Settings.Global.getFloat(
                bingoCtx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
        var animatedCells by remember { mutableStateOf(prefs.getStringSet("bingo_anim_cells", emptySet())!!.toSet()) }
        var animatedLines by remember { mutableStateOf(prefs.getStringSet("bingo_anim_lines", emptySet())!!.toSet()) }
        fun markCellAnimated(k: String) { animatedCells = animatedCells + k; prefs.edit().putStringSet("bingo_anim_cells", animatedCells).apply() }
        fun markLineAnimated(k: String) { animatedLines = animatedLines + k; prefs.edit().putStringSet("bingo_anim_lines", animatedLines).apply() }
        val cellAnim = remember(c.monthKey, c.templateId) { List(c.cells.size) { androidx.compose.animation.core.Animatable(0f) } }
        val sweepAnim = remember(c.monthKey, c.templateId) { List(c.cells.size) { androidx.compose.animation.core.Animatable(0f) } }
        LaunchedEffect(c.completedLines, c.monthKey) {
            val pendingLines = c.completedLines.filter { "${c.monthKey}:${c.templateId}:$it" !in animatedLines }
            for (line in pendingLines) {
                val key = "${c.monthKey}:${c.templateId}:$line"
                if (!bingoReduceMotion) {
                    kotlinx.coroutines.delay(450)
                    for (i in com.lecturameter.utils.BingoManager.lineIndices(side, line)) {
                        launch {
                            sweepAnim[i].snapTo(0f)
                            sweepAnim[i].animateTo(1f, tween(300))
                            sweepAnim[i].animateTo(0f, tween(650))
                        }
                        kotlinx.coroutines.delay(150)
                    }
                }
                markLineAnimated(key)
            }
        }
        val doneCount = c.cells.count { it.isCompleted }
        val complete = doneCount == c.cells.size
        // Tipografías según densidad del cartón (4×4 necesita textos más compactos)
        val labelSize = if (side >= 4) 8.5.sp else 10.5.sp
        val labelLineHeight = if (side >= 4) 10.5.sp else 13.sp
        val checkSize = if (side >= 4) 15.sp else 20.sp
        // Nombre del mes del cartón a partir de monthKey (yyyy-MM), en el idioma de la app
        val monthLabel = remember(c.monthKey, isEs) {
            try {
                val d = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).parse(c.monthKey)
                java.text.SimpleDateFormat("LLLL yyyy", if (isEs) java.util.Locale("es") else java.util.Locale.ENGLISH)
                    .format(d!!).replaceFirstChar { it.uppercase() }
            } catch (_: Exception) { c.monthKey }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                stringResource(R.string.bingo_subtitle, if (isEs) c.templateNameEs else c.templateNameEn, monthLabel),
                color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.bingo_progress, doneCount, c.cells.size, c.completedLines.size),
                color = theme.textMuted, fontSize = 12.sp
            )
            Spacer(Modifier.height(14.dp))
            // Cartón side×side
            for (row in 0 until side) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    for (col in 0 until side) {
                        val cellIdx = row * side + col
                        val cell = c.cells[cellIdx]
                        // B4: el 3×3 se tiñe de rojo. Sus casillas se rellenan con el MISMO rojo
                        // que el boton (accent == redForTheme) y su texto va en onCardTint
                        // (onRedColor), la tinta legible sobre ese rojo en los cinco temas. El 4×4
                        // no cambia: sigue con su casilla neutra y su acento.
                        val isRed = side == com.lecturameter.utils.BingoManager.SIDE_3
                        val cellInk = onCardTint(theme, side)
                        // La casilla sin marcar iba en theme.bgMid, y en AMOLED bgMid ES bgDark:
                        // negro sobre negro, o sea el cartón entero invisible salvo por los bordes.
                        // Es la QUINTA vez que aparece este error (heatmap, carril del widget,
                        // Wrapped, historial y esto): cualquier tarjeta pintada con bgMid tiene el
                        // mismo agujero en AMOLED. cardColor() ya existía y solo se usaba en una
                        // pantalla; el barrido del resto sigue pendiente.
                        val bg by androidx.compose.animation.animateColorAsState(
                            targetValue = if (isRed) accent
                                          else if (cell.isCompleted) accent.copy(alpha = 0.18f) else cardColor(theme),
                            animationSpec = tween(durationMillis = 300), label = "bingo_cell_bg"
                        )
                        // Fase 4 (D-003, 4): dispara el flip+glow SOLO si esta celda nunca animó
                        val cellKey = "${c.monthKey}:${c.templateId}:$cellIdx"
                        LaunchedEffect(cell.isCompleted, cellKey) {
                            if (cell.isCompleted && cellKey !in animatedCells) {
                                if (!bingoReduceMotion) {
                                    cellAnim[cellIdx].snapTo(0.001f)
                                    cellAnim[cellIdx].animateTo(1f, tween(1500))
                                    cellAnim[cellIdx].snapTo(0f)
                                }
                                markCellAnimated(cellKey)
                            }
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    val p = cellAnim[cellIdx].value
                                    if (p > 0f) {
                                        rotationY = 360f * (p / 0.38f).coerceAtMost(1f)
                                        cameraDistance = 12f * density
                                    }
                                }
                                .drawBehind {
                                    val p = cellAnim[cellIdx].value
                                    val glowA = if (p > 0.2f) (1f - kotlin.math.abs(p - 0.6f) / 0.4f).coerceIn(0f, 1f) else 0f
                                    val a = maxOf(glowA, sweepAnim[cellIdx].value)
                                    if (a > 0f) drawRoundRect(
                                        color = (if (isRed) cellInk else accent).copy(alpha = 0.6f * a),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx()),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                                    )
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(bg)
                                .border(
                                    // El 3×3 lleva un contorno mas GRUESO (2.5dp) y mas oscuro para
                                    // que se note; el 4×4 conserva su grosor original.
                                    width = if (isRed) 2.5.dp else if (cell.isCompleted) 1.5.dp else 1.dp,
                                    // El 3×3 rojo lleva un contorno mas oscuro (el propio rojo
                                    // oscurecido) para definir la casilla sobre el relleno rojo,
                                    // en los cinco temas. El 4×4 conserva su borde original.
                                    color = if (isRed) darken(accent, 0.4f)
                                            else if (cell.isCompleted) accent else theme.border,
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(6.dp)
                            ) {
                                // Animación clave 4 (Fase 4): la celda celebra al completarse
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = cell.isCompleted,
                                    enter = androidx.compose.animation.scaleIn(
                                        animationSpec = androidx.compose.animation.core.spring(
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
                                        )
                                    ) + fadeIn()
                                ) {
                                    Text("✓", color = if (isRed) cellInk else accent, fontSize = checkSize, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    if (isEs) cell.labelEs else cell.labelEn,
                                    color = if (isRed) cellInk else if (cell.isCompleted) theme.textMain else theme.textMuted,
                                    fontSize = labelSize,
                                    lineHeight = labelLineHeight,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3
                                )
                                // Libro que completó la celda (si aplica)
                                cell.completedByBookId?.let { bid ->
                                    books.firstOrNull { it.id == bid }?.let { b ->
                                        Text(
                                            b.title, color = if (isRed) cellInk else accent, fontSize = 7.5.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (complete) {
                Text(stringResource(R.string.bingo_completed), color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                // "Nuevo cartón" rota SOLO el tamaño que estás viendo: completar el 4×4 no
                // debe tirar tu 3×3 a medias, ni al revés. Si el trial ya caducó, el 3×3 no
                // se puede renovar (ensureBingoCard lo corta), así que ni se ofrece.
                if (side != com.lecturameter.utils.BingoManager.SIDE_3 || isPro) {
                    Button(
                        onClick = { vm.ensureBingoCard(prefs, side, force = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onCardTint(theme, side)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.bingo_new_card), fontWeight = FontWeight.Bold) }
                }
            } else {
                Text(stringResource(R.string.bingo_hint), color = theme.textMuted, fontSize = 11.5.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.bingo_renews), color = theme.textMuted, fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
        // Fase 6.1 (D-008, T5): primer cartón completado — tip de la rotación mensual
        var bingoTipVisible by remember { mutableStateOf(false) }
        LaunchedEffect(complete) {
            if (complete && !Tips.seen(prefs, Tips.BINGO_DONE) && !Tips.snackShownThisLaunch) {
                Tips.mark(prefs, Tips.BINGO_DONE)
                Tips.snackShownThisLaunch = true
                bingoTipVisible = true
            }
        }
        if (bingoTipVisible) {
            TipSnackbar(
                TipSnack(Tips.BINGO_DONE, stringResource(R.string.tip_bingo_title), stringResource(R.string.tip_bingo_body)),
                theme, onGone = { bingoTipVisible = false },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }
}

// ── BingoHistoryScreen (B4, 2) ────────────────────────────────────────────────
//
// Historial de bingos, pedido por Víctor: "que englobe a los dos tipos de bingos". Mismo
// patrón que el historial de retos (ChallengeHistoryScreen): cabecera, chips de año y
// tarjetas, con su vacío cuando no hay nada.
//
// GRATIS y completo para todos, igual que el de retos. Eso no es un descuido del paywall:
// es lo que hace que el 3×3 que te llevas de la prueba de 7 días se pueda seguir viendo
// después, que es justo lo que pidió Víctor. Cobrar por MIRAR lo que ya jugaste sería
// quitar algo, y aquí no se le quita nada a nadie.
//
// Cada tarjeta lleva el mini cartón repintado desde `pattern` (el mismo truco que la slide
// del Wrapped), teñido con el color de SU tamaño: acento el 4×4, rojo el 3×3.
@Composable
fun BingoHistoryScreen(vm: BooksViewModel, theme: Theme, onBack: () -> Unit) {
    BackHandler { onBack() }
    val history by vm.bingoHistory.collectAsState()
    val isEs = androidx.compose.ui.platform.LocalConfiguration.current.locales.get(0)?.language == "es"

    Column(Modifier.fillMaxSize().background(theme.bgDark).systemBarsPadding().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)) {
            // Pantalla nueva: se estrena con el icono no deprecado (idéntico en LTR). El resto
            // de la app sigue con Icons.Default.ArrowBack; ese barrido no es de este encargo.
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.textMain)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.bingo_history_title), color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.bingo_history_subtitle), color = theme.textMuted, fontSize = 13.sp)
            }
        }

        if (history.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, tint = theme.textDim, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.bingo_history_empty), color = theme.textDim,
                        fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            // Los resúmenes sin monthKey válido no deberían existir, pero el historial es
            // dato restaurado de un backup: si uno viene roto, se ignora en vez de reventar.
            val years = remember(history) {
                history.mapNotNull { it.monthKey.take(4).toIntOrNull() }.distinct().sortedDescending()
            }
            if (years.isEmpty()) return@Column
            var selectedYear by rememberSaveable { mutableStateOf(years.first()) }
            if (selectedYear !in years) selectedYear = years.first()

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 10.dp)
            ) {
                val acc = accentForTheme(theme)
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

            // Más reciente arriba. Dentro de un mes, primero el 4×4 (el cartón principal).
            val visible = remember(history, selectedYear) {
                history.filter { it.monthKey.startsWith("$selectedYear-") }
                    .sortedWith(compareByDescending<com.lecturameter.utils.BingoMonthSummary> { it.monthKey }
                        .thenByDescending { it.cellsTotal })
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                visible.forEach { s -> BingoHistoryCard(s, theme, isEs) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BingoHistoryCard(s: com.lecturameter.utils.BingoMonthSummary, theme: Theme, isEs: Boolean) {
    val side = com.lecturameter.utils.BingoManager.sideOfSummary(s).coerceAtLeast(3)
    val tint = cardTint(theme, side)
    val monthLabel = remember(s.monthKey, isEs) {
        try {
            val d = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).parse(s.monthKey)
            java.text.SimpleDateFormat("LLLL yyyy", if (isEs) java.util.Locale("es") else java.util.Locale.ENGLISH)
                .format(d!!).replaceFirstChar { it.uppercase() }
        } catch (_: Exception) { s.monthKey }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        // cardColor(): en AMOLED bgMid es negro puro y la tarjeta desaparecería
        color = cardColor(theme),
        border = BorderStroke(1.dp, theme.border),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Mini cartón repintado desde el patrón guardado ('1' = casilla hecha)
            Column(Modifier.size(52.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (r in 0 until side) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (col in 0 until side) {
                            val on = s.pattern.getOrNull(r * side + col) == '1'
                            Box(
                                Modifier.weight(1f).fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (on) tint else theme.border)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(monthLabel, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    // Distintivo del tamaño: es lo que separa los dos bingos de un vistazo
                    Surface(shape = RoundedCornerShape(6.dp), color = tint.copy(alpha = 0.18f)) {
                        Text(
                            stringResource(
                                if (side == com.lecturameter.utils.BingoManager.SIDE_3) R.string.bingo_history_badge_3
                                else R.string.bingo_history_badge_4
                            ),
                            color = tint, fontSize = 9.5.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    if (isEs) s.templateNameEs else s.templateNameEn,
                    color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.bingo_history_row, s.cellsDone, s.cellsTotal, s.lines),
                    color = theme.textMuted, fontSize = 11.sp
                )
                if (s.complete) {
                    Text(
                        stringResource(R.string.bingo_history_badge_completed),
                        color = Amber, fontSize = 9.5.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// v2.5: aviso de libro duplicado (Cancelar rojo / Añadir igualmente en el acento del tema)
@Composable
fun DuplicateBookDialog(candidate: Book, existing: Book, theme: Theme, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.bgMid,
        title = { Text(stringResource(R.string.dup_title), color = theme.textMain, fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(R.string.dup_text, existing.title), color = theme.textMuted, fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.dup_add_anyway), color = accentForTheme(theme), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.txt_847607d7), color = Red) } }
    )
}

// ── ListScreen ────────────────────────────────────────────────────────────────
//
// Estilo Goodreads: pestañas por estante + búsqueda + ordenación.
// Cada pestaña muestra sólo los libros de ese estado → no hay listas infinitas.

// ── D-002 (Fase 4): mini-rail del home ────────────────────────────────────────
// El rail vive SOLO en la biblioteca: 📜 historial (fijo) + 📚 home (fijo) + destinos
// reordenables. Destinos = push a pantalla completa (semántica 2.7); el historial se
// despliega como panel encajado contra el rail. Iconos = emojis del sistema (D-002b).
// Long-press en un destino → modo edición con arrastre vertical; ✓ guarda en prefs.
// Feedback 13-07: la lupa sale del rail (la búsqueda online vive en la barra de búsqueda)
private val RAIL_DEFAULT_ORDER = listOf("challenges", "stats", "bingo", "wrapped")

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RailItem(
    emoji: String?,
    theme: Theme,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    // Feedback 13-07: los destinos vuelven a los iconos Material azules; solo
    // historial (📜) y biblioteca (📚) conservan emoji (más el icono del tema en la barra)
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    // 18-07: se probó a poner una pastilla bajo los dos emojis del rail para que el 📜 no se
    // perdiera sobre el papel del tema Claro, y Víctor la descartó ("queda raro"). Vuelven a ir
    // sueltos. El 📜 sigue costando de ver en Claro: queda PENDIENTE buscar otra vía que no
    // sea ni cambiar el emoji ni meterlo en una caja.
    Box(
        Modifier
            .padding(vertical = 3.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) actionIconTint(theme).copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (enabled) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // D-015 r3: en Cuero los iconos del rail van en oro suave; azul en el resto de temas
        if (icon != null) Icon(icon, contentDescription = null, tint = actionIconTint(theme), modifier = Modifier.size(19.dp))
        else Text(emoji ?: "", fontSize = 17.sp)
    }
}

@Composable
fun HomeRail(
    theme: Theme,
    prefs: android.content.SharedPreferences,
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onStats: () -> Unit,
    onChallenges: () -> Unit,
    onBingo: () -> Unit,
    onWrapped: () -> Unit,
    // Feedback 14-07: swipe → sobre 📜 abre el historial; swipe ← en cualquier punto
    // del rail (incluida la raya separadora, que forma parte de su borde) lo cierra
    onHistoryOpen: () -> Unit = {},
    onRailClose: () -> Unit = {},
) {
    var order by remember {
        mutableStateOf(
            prefs.getString("rail_order", null)
                ?.split(",")?.filter { it in RAIL_DEFAULT_ORDER }
                ?.let { saved -> saved + RAIL_DEFAULT_ORDER.filter { it !in saved } }
                ?: RAIL_DEFAULT_ORDER
        )
    }
    var editMode by remember { mutableStateOf(false) }
    val slotPx = with(androidx.compose.ui.platform.LocalDensity.current) { 46.dp.toPx() }

    fun railIcon(dest: String) = when (dest) {
        "challenges" -> Icons.Default.EmojiEvents
        "stats"      -> Icons.Default.BarChart
        "bingo"      -> Icons.Default.GridView
        else         -> Icons.Default.CardGiftcard
    }

    // Feedback 14-07: gestos del rail — swipe ← lo cierra (el gesto arranca en el propio
    // rail o en la raya que lo separa de los libros); swipe → no hace nada aquí (el de
    // reabrir vive en la franja del borde cuando está cerrado, ver call site)
    val railAcc = remember { mutableStateOf(0f) }
    Column(
        Modifier.width(46.dp).fillMaxHeight().padding(top = 4.dp)
            // Feedback 14-07 (F10): en landscape no caben los 6 iconos — la columna
            // scrollea en vertical para que todos sean alcanzables
            .verticalScroll(rememberScrollState())
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    railAcc.value += delta
                    if (railAcc.value < -60f) { onRailClose(); railAcc.value = 0f }
                },
                onDragStarted = { railAcc.value = 0f }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 📜 con gesto propio: swipe → abre el historial (además del tap); swipe ← cierra
        // el rail (consume el drag del padre, así que replica ese caso)
        val histAcc = remember { mutableStateOf(0f) }
        Box(
            Modifier.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    histAcc.value += delta
                    if (histAcc.value > 60f) { onHistoryOpen(); histAcc.value = 0f }
                    else if (histAcc.value < -60f) { onRailClose(); histAcc.value = 0f }
                },
                onDragStarted = { histAcc.value = 0f }
            )
        ) {
            RailItem("📜", theme, enabled = !editMode, onClick = onHistory)
        }
        RailItem("📚", theme, enabled = !editMode, onClick = onLibrary)
        HorizontalDivider(color = railLineColor(theme), thickness = 1.dp, modifier = Modifier.width(22.dp).padding(vertical = 3.dp))
        Box(Modifier.height(46.dp * order.size).fillMaxWidth()) {
        RAIL_DEFAULT_ORDER.forEach { dest ->
            val idx = order.indexOf(dest)
            var dragging by remember(dest) { mutableStateOf(false) }
            var dragOffset by remember(dest) { mutableStateOf(0f) }
            val settledY by androidx.compose.animation.core.animateFloatAsState(
                targetValue = idx * slotPx,
                animationSpec = tween(durationMillis = 220),
                label = "rail_slot_$dest"
            )
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(if (dragging) 1f else 0f)
                    .offset { IntOffset(0, (if (dragging) idx * slotPx + dragOffset else settledY).roundToInt()) }
                    .then(
                        if (editMode) Modifier.pointerInput(dest) {
                            detectDragGestures(
                                onDragStart = { dragging = true },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val shift = (dragOffset / slotPx).roundToInt()
                                    if (shift != 0) {
                                        val from = order.indexOf(dest)
                                        val to = (from + shift).coerceIn(0, order.lastIndex)
                                        if (to != from) {
                                            order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
                                            dragOffset -= (to - from) * slotPx
                                        }
                                    }
                                },
                                onDragEnd = { dragging = false; dragOffset = 0f },
                                onDragCancel = { dragging = false; dragOffset = 0f }
                            )
                        } else Modifier
                    )
            ) {
                RailItem(
                    null, theme,
                    icon = railIcon(dest),
                    highlighted = editMode,
                    enabled = !editMode,
                    onLongPress = { editMode = true },
                    onClick = {
                        when (dest) {
                            "challenges" -> onChallenges()
                            "stats"      -> onStats()
                            "bingo"      -> onBingo()
                            else         -> onWrapped()
                        }
                    }
                )
            }
        }
        }
        if (editMode) {
            RailItem(null, theme, highlighted = true, icon = Icons.Default.Check, onClick = {
                prefs.edit().putString("rail_order", order.joinToString(",")).apply()
                editMode = false
            })
        }
    }
}

// Orden canónico de estantes (igual al que muestra la barra de pestañas)
internal val SHELF_ORDER = listOf(
    BookStatus.READING,
    BookStatus.FINISHED,
    BookStatus.REREADING,
    BookStatus.PENDING,
    BookStatus.DROPPED
)

fun normalizeSearchText(text: String): String {
    val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
    return normalized.replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
        .replace(" ", "").lowercase()
}

fun fuzzyMatch(query: String, target: String): Boolean {
    val nq = normalizeSearchText(query)
    val nt = normalizeSearchText(target)
    return nt.contains(nq)
}

// La pantalla de selección de idioma inicial (LanguageSelectionScreen) se eliminó el
// 20-07: el idioma se resuelve del sistema con fallback a inglés (LanguageHelper) y se
// cambia desde Ajustes.
