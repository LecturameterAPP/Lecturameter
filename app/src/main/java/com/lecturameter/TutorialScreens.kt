package com.lecturameter

// Tips contextuales (D-008) y tutorial de 6 slides: Tips, TipCard, TipSnackbar, visuales y TutorialSlideshow.
// Extraido de MainActivity.kt el 15-07-2026 (ruptura del monolito, sin cambios funcionales).


import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
// v21.42: Icons.Outlined.Star eliminado — estrellas usan ★/☆ Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import java.util.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import com.lecturameter.model.*
import com.lecturameter.utils.*
import androidx.navigation.compose.composable

// ── Fase 6.1 (D-008): tips contextuales de una sola vez ──────────────────────
// 7 tips en toda la vida de la app: rail · historial · primer libro terminado ·
// primer recap · bingo completado · sección no abierta · widget (1ª sesión).
// Formato B (card anclada) para gestos/UI; formato A (snackbar) para hitos.
// Un flag por tip en prefs; nunca dos snackbars en el mismo arranque;
// "Restablecer tutorial" también los resetea.
object Tips {
    const val RAIL = "tip_rail_shown"
    const val HISTORY = "tip_history_shown"
    const val FIRST_BOOK = "tip_first_book_shown"
    const val FIRST_RECAP = "tip_first_recap_shown"
    const val BINGO_DONE = "tip_bingo_done_shown"
    const val UNVISITED = "tip_unvisited_shown"
    const val WIDGET = "tip_widget_shown"
    // D-016: los retos completados o vencidos se archivan al historial (cambio de comportamiento)
    const val CHALLENGE_ARCHIVE = "tip_challenge_archive_shown"
    val ALL = listOf(RAIL, HISTORY, FIRST_BOOK, FIRST_RECAP, BINGO_DONE, UNVISITED, WIDGET, CHALLENGE_ARCHIVE)
    /** Si dos hitos coinciden, el segundo espera a la siguiente apertura. */
    var snackShownThisLaunch = false
    fun seen(prefs: android.content.SharedPreferences, key: String) = prefs.getBoolean(key, false)
    fun mark(prefs: android.content.SharedPreferences, key: String) { prefs.edit().putBoolean(key, true).apply() }
    fun resetAll(prefs: android.content.SharedPreferences) {
        prefs.edit().apply { ALL.forEach { remove(it) } }.apply()
        snackShownThisLaunch = false
    }
}

/** Datos de un tip de formato A (snackbar con acción opcional). */
data class TipSnack(val key: String, val title: String, val body: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

/** Formato B: card destacada in-place, anclada al elemento del que habla. */
@Composable
fun TipCard(title: String, body: String, theme: Theme, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val acc = accentForTheme(theme)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = acc.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, acc.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 2.dp)) {
            Column(Modifier.weight(1f)) {
                // El título iba en el ACENTO sobre un relleno del propio acento al 12%: 3,54:1 en
                // Oscuro y 4,32:1 en Claro, con 4,5:1 de mínimo (a 12,5sp no cuela como texto
                // grande). Afectaba a los 7 tips que ya están en producción.
                //
                // Pasa a textMain, que es lo que YA hacía TipSnackbar (más abajo, línea ~112): los
                // dos formatos de tip eran incoherentes entre sí y el que estaba bien era el otro.
                // El acento no se pierde: sigue en el relleno y en el borde de la card.
                Text(title, color = theme.textMain, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                if (body.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(body, color = theme.textMuted, fontSize = 11.5.sp, lineHeight = 15.sp)
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, tint = theme.textDim, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Formato A: snackbar inferior con acción; se descarta sola (~8 s), con ✕ o al actuar. */
@Composable
fun TipSnackbar(tip: TipSnack, theme: Theme, onGone: () -> Unit, modifier: Modifier = Modifier) {
    val acc = accentForTheme(theme)
    LaunchedEffect(tip.key) { kotlinx.coroutines.delay(8000); onGone() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = theme.bgMid,
        border = BorderStroke(1.dp, acc.copy(alpha = 0.5f)),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(tip.title, color = theme.textMain, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                if (tip.body.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(tip.body, color = theme.textMuted, fontSize = 11.5.sp, lineHeight = 15.sp)
                }
                if (tip.actionLabel != null) {
                    Spacer(Modifier.height(4.dp))
                    // Aquí el acento SÍ significa algo (es lo que hace que parezca pulsable), así
                    // que no puede pasar a textMain como el título: se corrige la luminosidad y se
                    // conserva el matiz. Iba a 3,58:1 en Oscuro sobre bgMid, con 4,5:1 de mínimo.
                    Text(
                        tip.actionLabel, color = inkOnSolid(acc, theme.bgMid, theme),
                        fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { tip.onAction?.invoke(); onGone() }
                    )
                }
            }
            IconButton(onClick = onGone, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, tint = theme.textDim, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Tutorial ──────────────────────────────────────────────────────────────────

// ── Mockups visuales para el tutorial (réplicas SIN lógica real) ──────────────
// Reusan los mismos tokens de color/forma que los componentes reales (BookCover,
// DataChip, DrawerStatChipH, StatBox) para que se vean "como son realmente",
// pero no están conectados a ViewModel ni tienen onClick funcionales.

@Composable
fun TutorialBookCardVisual(theme: Theme) {
    val acc = accentForTheme(theme)
    Surface(
        modifier = Modifier.fillMaxWidth(0.86f),
        shape = RoundedCornerShape(16.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border)
    ) {
        Row(Modifier.padding(12.dp)) {
            Box(modifier = Modifier.size(70.dp, (70 * 1.42f).dp)) {
                // B-022: la portada del tutorial va empaquetada, no se descarga. Antes
                // salía de OpenLibrary y en la primera ejecución (sin red, o con la API
                // lenta) la slide se veía vacía. La variante inglesa vive en drawable-en,
                // así que la elige el propio idioma de la app (attachBaseContext).
                Image(
                    painter = painterResource(R.drawable.tutorial_cover_eye),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(70.dp, (70 * 1.42f).dp).clip(RoundedCornerShape(8.dp))
                )
                Box(
                    Modifier.size(22.dp).offset(x = (-5).dp, y = 5.dp).clip(CircleShape)
                        .background(actionFillColor(theme)).border(2.dp, theme.surface, CircleShape).align(Alignment.BottomStart),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
                Box(
                    Modifier.size(22.dp).offset(x = 5.dp, y = 5.dp).clip(CircleShape)
                        .background(acc).border(2.dp, theme.surface, CircleShape).align(Alignment.BottomEnd),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Edit, null, tint = onAccentColor(theme), modifier = Modifier.size(13.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(stringResource(R.string.tutorial_mock_book_title), color = theme.textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(Red.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Delete, null, tint = Red.copy(alpha = 0.7f), modifier = Modifier.size(11.dp)) }
                }
                Text(stringResource(R.string.tutorial_mock_book_author), color = theme.textMuted, fontSize = 12.sp)
                Text(stringResource(R.string.tutorial_mock_genres), color = acc.copy(alpha = 0.8f), fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.tutorial_mock_pages), color = theme.textMuted, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                    Text(stringResource(R.string.tutorial_mock_days), color = theme.textMuted, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun TutorialStatsPillsVisual(theme: Theme) {
    Row(Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatBox(stringResource(R.string.tutorial_mock_days_pill), stringResource(R.string.pill_dias_leyendo), Modifier.weight(1f), theme)
        StatBox("3h 30m", stringResource(R.string.stat_total_time), Modifier.weight(1f), theme, highlight = true, highlightColor = Sky)
        StatBox("136p", stringResource(R.string.pill_pags_leidas), Modifier.weight(1f), theme, highlight = true, highlightColor = accentForTheme(theme))
        StatBox("20%", stringResource(R.string.pill_porcentaje_leido), Modifier.weight(1f), theme)
        StatBox("17.0", stringResource(R.string.pill_pags_dia), Modifier.weight(1f), theme, highlight = true, highlightColor = Green)
    }
}

@Composable
fun TutorialHistoryRowVisual(theme: Theme) {
    val acc = accentForTheme(theme)
    Column(Modifier.fillMaxWidth(0.88f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Fila de libro colapsable
        Surface(
            shape = RoundedCornerShape(14.dp), color = theme.surface, border = BorderStroke(1.dp, acc.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = acc, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                    Text("🇪🇸", fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp))
                    Column(Modifier.weight(1f)) {
                        Text("TEST", color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Lecturameter", color = theme.textDim, fontSize = 11.sp)
                    }
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(Red.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Delete, null, tint = Red.copy(alpha = 0.7f), modifier = Modifier.size(11.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DrawerStatChipH(stringResource(R.string.tutorial_mock_ses_chip), acc, Modifier.weight(1f))
                    DrawerStatChipH("⏱️ 28m", Sky, Modifier.weight(1f))
                    DrawerStatChipH(stringResource(R.string.tutorial_mock_pages_chip), Green, Modifier.weight(1f))
                }
            }
        }
        // Sesión expandida dentro del libro
        Surface(shape = RoundedCornerShape(10.dp), color = theme.surface, border = BorderStroke(1.dp, theme.border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#1", color = acc, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.tutorial_mock_date), color = theme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Edit, null, tint = acc.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Default.Delete, null, tint = Red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DataChip("📄 25p", acc.copy(alpha = 0.15f), acc, Modifier.weight(1f))
                    DataChip("⏱️ 28m", Sky.copy(alpha = 0.15f), Sky, Modifier.weight(1f))
                    DataChip("⚡ 0.9 p/m", Green.copy(alpha = 0.15f), Green, Modifier.weight(1f))
                    DataChip("📖 12-36", Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun TutorialWidgetVisual(theme: Theme) {
    // v1.0: captura real del widget según idioma de la app
    val ctx = LocalContext.current
    val lang = ctx.getSharedPreferences("lecturameter", android.content.Context.MODE_PRIVATE)
        .getString("app_language", "es") ?: "es"
    val resId = if (lang == "en") R.drawable.tutorial_widget_en else R.drawable.tutorial_widget_es
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = resId),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .clip(RoundedCornerShape(16.dp))
    )
}

@Composable
fun TutorialDonationsVisual(theme: Theme) {
    Column(Modifier.fillMaxWidth(0.78f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = Gold.copy(alpha = 0.10f), border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⭐", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text("Play Store", color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF29ABE0).copy(alpha = 0.10f), border = BorderStroke(1.dp, Color(0xFF29ABE0).copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("☕", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text("Ko-fi", color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF0070BA).copy(alpha = 0.10f), border = BorderStroke(1.dp, Color(0xFF0070BA).copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💙", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text("PayPal", color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// v2.5: descriptionComposable soporta contenido enriquecido (icono inline, etc).
// Si != null se renderiza en vez de `description` (que puede quedar vacío).
data class TutorialPage(
    val icon: String,
    val title: String,
    val description: String,
    val visual: (@Composable () -> Unit)? = null,
    val descriptionComposable: (@Composable (Theme) -> Unit)? = null
)

/** v2.5: descripción de tutorial con un icono vectorial REAL inline (mismo que en la UI). */
@Composable
fun TutorialInlineIconDesc(
    pre: String, post: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color, theme: Theme
) {
    val inlineId = "inlineIcon"
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        append(pre); appendInlineContent(inlineId, "[icon]"); append(post)
    }
    val inline = mapOf(inlineId to androidx.compose.foundation.text.InlineTextContent(
        androidx.compose.ui.text.Placeholder(
            width = 18.sp, height = 16.sp,
            placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter
        )
    ) { Icon(icon, contentDescription = null, tint = iconTint) })
    Text(
        annotated, inlineContent = inline,
        color = theme.textMuted, fontSize = 15.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp
    )
}

@Composable
fun TutorialPageContent(page: TutorialPage, theme: Theme, isLandscape: Boolean = false) {
    // v2.4: adaptativo (mockup aprobado). Landscape → dos columnas (visual | texto).
    // Portrait/tablet → contenido centrado con ancho máximo, visual acotado.
    if (isLandscape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Box(
                Modifier.weight(0.42f).widthIn(max = 300.dp),
                contentAlignment = Alignment.Center
            ) {
                if (page.visual != null) page.visual.invoke()
                else Text(page.icon, fontSize = 56.sp)
            }
            Column(Modifier.weight(0.58f), horizontalAlignment = Alignment.Start) {
                Text(page.title, color = theme.textMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                if (page.descriptionComposable != null) page.descriptionComposable.invoke(theme)
                else Text(page.description, color = theme.textMuted, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Box(Modifier.widthIn(max = 480.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (page.visual != null) {
                        Box(Modifier.widthIn(max = 380.dp)) { page.visual.invoke() }
                    } else {
                        Text(page.icon, fontSize = 64.sp)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(page.title, color = theme.textMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    if (page.descriptionComposable != null) page.descriptionComposable.invoke(theme)
                    else Text(page.description, color = theme.textMuted, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TutorialSlideshow(theme: Theme, onComplete: () -> Unit, onSkip: () -> Unit) {
    // P-021 (12-07-2026): rework 14 → 10 slides (plan: Documentación/Plan — Tutorial capturas v2).
    // Fusiones: 1+3→1 · 2→2 · 4+8→3 · 5+7→4 · 6→5. Orden final fijado por Víctor (QA r2):
    // widget → herramientas → batería → feedback → donaciones. Ediciones: sin slide propia,
    // una frase dentro de la 1. Textos cortos (máx ~2 frases). Capturas reales ES/EN: al
    // final de la Fase 4 (los mocks actuales se mantienen hasta entonces).
    val scope = rememberCoroutineScope()
    val acc = accentForTheme(theme)
    var showSkipDialog by remember { mutableStateOf(false) }

    if (showSkipDialog) {
        AlertDialog(
            onDismissRequest = { showSkipDialog = false },
            containerColor = theme.bgMid,
            title = { Text(stringResource(R.string.txt_145bed95), color = theme.textMain, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.txt_96b976d8), color = theme.textMuted) },
            confirmButton = { TextButton(onClick = { onSkip(); showSkipDialog = false }) { Text(stringResource(R.string.txt_a6e39241), color = Red) } },
            dismissButton = { TextButton(onClick = { showSkipDialog = false }) { Text(stringResource(R.string.txt_bd23eb60), color = acc) } }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(theme.bgDark)) {
        // v2.4: adaptativo (mockup aprobado) — landscape = dos columnas por página,
        // pantallas anchas = footer y contenido centrados a un ancho máximo.
        val isLandscape = maxWidth > maxHeight
        val footerMax = if (maxWidth > 600.dp) 720.dp else Dp.Infinity
        // P-021 + QA 12-07 r2: orden final widget → herramientas → BATERÍA → FEEDBACK →
        // donaciones (Víctor 12-07). La slide 4 se reparte distinto en horizontal para
        // que el mock de la sesión desplegada no se corte: el historial pasa a ser el
        // visual de la izquierda y las pills acompañan al texto a la derecha.
        // Fase 4 (D-009, 14-07): rework 10 → 5 slides FILOSÓFICAS (mockup r2 aprobado).
        // 15-07: +1 slide de Goodreads (excepción acordada) → 6.
        // El tutorial ya no explica features (eso son los tips contextuales de Fase 6):
        // valor → básico → promesa → privacidad → batería. Textos estilo Augur, 1-2 frases.
        // La slide 2 llevará captura real POR IDIOMA al cerrar F4; hasta entonces usa el
        // mock de tarjeta (theme-aware y traducido). Emojis provisionales ("ya se cambiarán").
        val tutContext = androidx.compose.ui.platform.LocalContext.current
        val pages = listOf(
            // 1 — Propuesta de valor
            TutorialPage("📚", stringResource(R.string.tut5_value_title), stringResource(R.string.tut5_value_desc)),
            // 2 — Lo mínimo para empezar (captura real por idioma pendiente de F4)
            TutorialPage("＋", stringResource(R.string.tut5_basics_title), stringResource(R.string.tut5_basics_desc),
                visual = { TutorialBookCardVisual(theme) }),
            // 3 — Goodreads (15-07): título de A + pasos de B. Excepción consciente a
            // D-009 — traerse la biblioteca es lo PRIMERO que necesita quien viene de
            // otra app, no una feature que descubrir luego. Sin CTA a propósito: la
            // exportación solo existe en la web de Goodreads, así que aquí no hay nada
            // que resolver todavía; la slide solo deja el poso.
            TutorialPage("📥", stringResource(R.string.tut5_goodreads_title), "",
                descriptionComposable = { th ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.tut5_goodreads_desc),
                            color = th.textMuted, fontSize = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = th.surface,
                            border = BorderStroke(1.dp, th.border)
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                listOf(
                                    R.string.tut5_goodreads_s1,
                                    R.string.tut5_goodreads_s2,
                                    R.string.tut5_goodreads_s3
                                ).forEachIndexed { i, res ->
                                    Row(Modifier.padding(vertical = 3.dp)) {
                                        Text("${i + 1}", color = Accent2, fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                                        Text(stringResource(res), color = th.textMuted,
                                            fontSize = 12.sp, lineHeight = 18.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.tut5_goodreads_note),
                            color = th.textDim, fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }),
            // 4 — La promesa (emoji provisional 🧭)
            TutorialPage("🧭", stringResource(R.string.tut5_promise_title), stringResource(R.string.tut5_promise_desc)),
            // 4 — Privacidad como identidad
            TutorialPage("🔒", stringResource(R.string.tut5_privacy_title), stringResource(R.string.tut5_privacy_desc)),
            // 5 — Batería, con CTA que abre los ajustes del sistema
            TutorialPage("🔋", stringResource(R.string.tut5_battery_title), "",
                descriptionComposable = { th ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.tut5_battery_desc),
                            color = th.textMuted, fontSize = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            onClick = {
                                try {
                                    tutContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                } catch (_: Exception) {
                                    try {
                                        tutContext.startActivity(android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            android.net.Uri.parse("package:" + tutContext.packageName)))
                                    } catch (_: Exception) { }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x26F59E0B),
                            border = BorderStroke(1.dp, Color(0x80F59E0B))
                        ) {
                            Text(
                                stringResource(R.string.tut5_battery_cta),
                                color = Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                })
        )
        val pagerState = androidx.compose.foundation.pager.rememberPagerState { pages.size }
        // D-009: el atrás del sistema retrocede de slide (en la 1 no intercepta)
        BackHandler(enabled = pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // D-009: barra de progreso superior por segmentos (sustituye al contador y a
            // los puntitos de abajo — feedback 14-07)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.widthIn(max = footerMax).fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
            ) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i <= pagerState.currentPage) acc else theme.border)
                    )
                }
            }
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TutorialPageContent(pages[page], theme, isLandscape)
                }
            }

            // D-009 (feedback 14-07): contador y puntitos ELIMINADOS — la posición la
            // indica solo la barra de segmentos superior.
            Spacer(Modifier.height(16.dp))

            // Feedback 2.6: 3 slots simétricos (weight 1 a cada lado) — Saltar queda centrado
            // en pantalla Y equidistante de Atrás/Siguiente. El texto de los botones laterales
            // se auto-reduce si no cabe en su slot (evita el wrap de "¡Comenzar!" en ES).
            val isLastPage = pagerState.currentPage == pages.size - 1
            var nextTextSize by remember(isLastPage) { mutableStateOf(14.sp) }
            Row(
                Modifier.widthIn(max = footerMax).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.5.dp, theme.border),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = theme.textMuted),
                            // Feedback 2.7: mismo padding y anchura mínima que "Siguiente" —
                            // los dos botones laterales quedan del mismo tamaño
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(44.dp).defaultMinSize(minWidth = 108.dp)
                        ) { Text(stringResource(R.string.txt_c673411e), fontSize = 14.sp, maxLines = 1) }
                    }
                }
                OutlinedButton(
                    onClick = { showSkipDialog = true },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.5.dp, acc.copy(alpha = 0.6f)),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = acc),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(stringResource(R.string.txt_a6e39241), fontSize = 14.sp, maxLines = 1)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Button(
                        onClick = {
                            if (isLastPage) onComplete()
                            else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = acc),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        // Feedback 2.7: anchura mínima compartida con "Atrás" (mismo tamaño)
                        modifier = Modifier.height(44.dp).defaultMinSize(minWidth = 108.dp)
                    ) {
                        Text(
                            stringResource(if (isLastPage) R.string.txt_d4d1809c else R.string.txt_eccc5922),
                            color = onAccentColor(theme), fontSize = nextTextSize, fontWeight = FontWeight.Bold,
                            maxLines = 1, softWrap = false,
                            onTextLayout = { if (it.hasVisualOverflow && nextTextSize > 10.sp) nextTextSize *= 0.92f }
                        )
                    }
                }
            }
        }
    }
}
