package com.lecturameter

// D-013: hoja de venta de Lecturameter Pro (modelo Augur adaptado al tema de la app).
// Se abre desde los gates (temas de pago, ediciones, historial de retos) y desde Ajustes.
// Precio decidido 16-07: 4,99 con oferta de lanzamiento a 2,99 y prueba gratis de 7 días.

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lecturameter.utils.Pro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val LM_CODE_REGEX = Regex("^LM-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")

/** Formatea la entrada en vivo a LM-XXXX-XXXX-XXXX. Internal para testearla (ProStateTest). */
internal fun formatLmCode(raw: String): String {
    val clean = raw.uppercase().filter { it.isLetterOrDigit() }
        .removePrefix("LM").take(12)
    return buildString {
        append("LM-")
        clean.forEachIndexed { i, c ->
            if (i == 4 || i == 8) append('-')
            append(c)
        }
    }.take(17)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProUpsellSheet(
    theme: Theme,
    prefs: android.content.SharedPreferences,
    onDismiss: () -> Unit,
    onProChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val acc = accentForTheme(theme)
    var showCodeEntry by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    val isPro = remember(refresh) { Pro.isPro(prefs) }
    val productDetails by LmBilling.productDetails.collectAsState()
    val purchaseTick by LmBilling.purchaseCompleted.collectAsState()
    LaunchedEffect(purchaseTick) { if (purchaseTick > 0) { refresh++; onProChanged() } }
    // M3: al abrir la hoja, asegurarse de que Billing sigue vivo. Si el servicio se cayó
    // (la Play Store se actualiza sola a menudo) y se agotaron los reintentos, sin esto el
    // precio no vuelve JAMÁS y el usuario cree que Pro no está a la venta. No cuesta nada
    // si ya está conectado.
    LaunchedEffect(Unit) { LmBilling.reconnect() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = theme.bgMid) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isPro) {
                Text(stringResource(R.string.pro_active_title), color = theme.textMain, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                val statusLine = when {
                    Pro.trialActive(prefs) && !prefs.getBoolean(Pro.PREF_KEY, false) ->
                        stringResource(R.string.pro_status_trial, Pro.trialDaysLeft(prefs))
                    else -> stringResource(R.string.pro_active_body)
                }
                Text(statusLine, color = theme.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, acc)) {
                    Text(stringResource(R.string.pro_close), color = acc)
                }
            } else if (showCodeEntry) {
                CodeEntry(theme, prefs, acc, onBack = { showCodeEntry = false }, onRedeemed = { refresh++; onProChanged() })
            } else {
                Text(stringResource(R.string.pro_title), color = theme.textMain, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.pro_benefits), color = theme.textMuted, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                // B: tabla comparativa compacta Gratis vs Pro
                ProCompareTable(theme)
                Spacer(Modifier.height(16.dp))
                if (Pro.trialAvailable(prefs)) {
                    // P-033: contar el regalo ANTES de empezar la prueba. Sin este aviso el
                    // regalo no existe de cara al usuario, porque se entera el día 7.
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = acc.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, acc),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.trial_gift_teaser_title), color = theme.textMain, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.trial_gift_teaser_body), color = theme.textMuted, fontSize = 11.5.sp, lineHeight = 16.sp)
                        }
                    }
                    Button(
                        onClick = { Pro.activateTrial(prefs); refresh++; onProChanged() },
                        colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = onAccentColor(theme)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) { Text(stringResource(R.string.pro_trial_button), fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp))
                }
                // Compra: precio real de Play si está disponible; si no, botón deshabilitado
                val price = productDetails[SKU_LM_PRO]?.oneTimePurchaseOfferDetails?.formattedPrice
                val buyErrorMsg = stringResource(R.string.pro_buy_error)
                OutlinedButton(
                    // M2: launchPurchase devuelve Boolean A PROPÓSITO (ver LmBilling) y la UI
                    // tiraba el valor. Si Play se caía entre poblar el precio y el tap, el
                    // usuario pulsaba "Comprar por 2,99 €" y no pasaba absolutamente nada:
                    // ni Toast, ni spinner, ni flujo de Play. En el botón que cobra, eso no.
                    onClick = {
                        if (activity != null && !LmBilling.launchPurchase(activity)) {
                            android.widget.Toast.makeText(context, buyErrorMsg, android.widget.Toast.LENGTH_LONG).show()
                            // El servicio se ha caído: intentar recuperarlo para el siguiente tap.
                            LmBilling.reconnect()
                        }
                    },
                    enabled = price != null && activity != null,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (price != null) acc else theme.border),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text(
                        if (price != null) stringResource(R.string.pro_buy_button, price)
                        else stringResource(R.string.pro_buy_unavailable),
                        color = if (price != null) acc else theme.textDim
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showCodeEntry = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pro_have_code), color = acc, fontSize = 13.sp)
                }
                // Restauración manual: si compró con esta cuenta y el restore automático
                // del arranque no llegó (caché de Play desincronizada), este botón lo fuerza.
                var restoring by remember { mutableStateOf(false) }
                val restoreNoneMsg = stringResource(R.string.pro_restore_none)
                val restoreUnavailableMsg = stringResource(R.string.pro_restore_unavailable)
                TextButton(
                    onClick = {
                        restoring = true
                        // C2: los tres casos son distintos y antes dos de ellos daban el
                        // mismo Toast. Decirle "no consta tu compra" a quien pagó (y solo
                        // está sin cobertura) es la peor frase que puede leer.
                        LmBilling.restore { result ->
                            // El callback de Billing llega en su propio hilo: al principal
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                restoring = false
                                when (result) {
                                    LmBilling.RestoreResult.FOUND -> { refresh++; onProChanged() }
                                    LmBilling.RestoreResult.NONE ->
                                        android.widget.Toast.makeText(context, restoreNoneMsg, android.widget.Toast.LENGTH_SHORT).show()
                                    LmBilling.RestoreResult.UNAVAILABLE -> {
                                        android.widget.Toast.makeText(context, restoreUnavailableMsg, android.widget.Toast.LENGTH_LONG).show()
                                        LmBilling.reconnect()
                                    }
                                }
                            }
                        }
                    },
                    enabled = !restoring,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.pro_restore_button), color = theme.textMuted, fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.pro_not_now), color = theme.textDim, fontSize = 12.sp)
                }
            }
        }
    }
}

// B: tabla comparativa Gratis vs Pro. Compacta (labelSmall/bodySmall) para no alargar el
// sheet. Usa los tokens de color del tema activo. La fila final es una NOTA, no una columna:
// deja claro que Wrapped/recaps/estadísticas/widget/backups son gratis para todos siempre.
@Composable
private fun ProCompareTable(theme: Theme) {
    val acc = accentForTheme(theme)
    Surface(shape = RoundedCornerShape(12.dp), color = theme.bgSurf, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Cabecera de columnas
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1.5f))
                Text(stringResource(R.string.pro_compare_free), Modifier.weight(1f), color = theme.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.pro_compare_pro), Modifier.weight(1f), color = acc, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            ProCompareRow(theme, R.string.pro_compare_themes_label, R.string.pro_compare_themes_free, R.string.pro_compare_themes_pro)
            ProCompareRow(theme, R.string.pro_compare_challenges_label, R.string.pro_compare_challenges_free, R.string.pro_compare_challenges_pro)
            // Paywall 17-07: heatmap horario GRATIS (fuera de la tabla, sale en el
            // Wrapped); ediciones gratis base + 2 (3 totales) y Pro sin límite
            ProCompareRow(theme, R.string.pro_compare_editions_label, R.string.pro_compare_editions_free, R.string.pro_compare_editions_pro)
            ProCompareRow(theme, R.string.pro_compare_icon_label, R.string.pro_compare_icon_free, R.string.pro_compare_icon_pro)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.pro_compare_footer),
                color = theme.textDim, fontSize = 10.sp, lineHeight = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProCompareRow(theme: Theme, label: Int, free: Int, pro: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.weight(1.5f), color = theme.textMuted, fontSize = 11.sp)
        Text(stringResource(free), Modifier.weight(1f), color = theme.textDim, fontSize = 11.sp, textAlign = TextAlign.Center)
        Text(stringResource(pro), Modifier.weight(1f), color = theme.textMain, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/**
 * P-032 (Bloque 4, feature 7, opción B de Víctor): al acabar la prueba de 7 días, el usuario
 * ELIGE uno de los 3 temas de pago y se lo queda para siempre.
 *
 * Por qué opción B y no A (regalo automático del que tuviera puesto): decisión de Víctor,
 * "es lo más a gusto". Se conserva lo bueno de A igualmente: el tema que tenía al caducar
 * viene PRESELECCIONADO (B-041 lo guarda en theme_before_lock justo antes de apagarlo), así
 * que quien no quiera pensar solo pulsa "Quedármelo" y se lleva exactamente el de la opción A.
 *
 * El diálogo se puede cerrar sin elegir y NO se pierde el regalo: trial_gift_done solo se
 * marca al conceder, así que vuelve a salir en el siguiente arranque. Si compra Pro, deja de
 * salir solo (trialGiftPending mira pro_unlocked) porque ya tiene los tres.
 */
@Composable
fun TrialGiftDialog(
    vm: BooksViewModel,
    prefs: android.content.SharedPreferences,
    theme: Theme,
    onSeePro: () -> Unit
) {
    val context = LocalContext.current
    // Se evalúa una vez por composición del host: si no toca, no cuesta nada.
    var pending by remember { mutableStateOf(Pro.trialGiftPending(prefs)) }
    if (!pending) return

    val acc = accentForTheme(theme)
    // Preselección: el tema que tenía puesto al caducar (lo de la opción A). Si no hay
    // ninguno guardado (p. ej. pasó la prueba entera en Oscuro), no preseleccionamos nada
    // y que elija: no se le puede adivinar el gusto.
    val kept = remember { prefs.getString(Pro.THEME_BEFORE_LOCK_KEY, null) }
    // MAYOR (auditoría dinero 17-07): NO ofrecer un tema de pago que el usuario YA tiene
    // (Aurora/AMOLED heredado de la 2.7). Regalarle algo que ya posee quemaba el one-shot sin
    // darle nada. Solo se ofrecen los de pago a los que NO tiene derecho (Cuero siempre entra:
    // no se hereda). El tema que tenía al caducar (kept) va preseleccionado si está en la lista.
    val offered = remember {
        listOf(ThemeMode.AURORA, ThemeMode.AMOLED, ThemeMode.CUERO)
            .filter { !Pro.themeAllowed(prefs, it) }
    }
    var choice by remember { mutableStateOf(offered.firstOrNull { it.value == kept } ?: offered.firstOrNull()) }

    AlertDialog(
        onDismissRequest = { pending = false },   // se cierra, pero el regalo sigue pendiente
        // bgMid y no surface: es la convención de TODOS los diálogos del proyecto, y además
        // theme.surface es TRANSLÚCIDO en Oscuro/Aurora/AMOLED (0x0D-0x10 blanco), así que
        // como contenedor de diálogo se vería el fondo a través. Los diálogos se libran del
        // agujero de bgMid en AMOLED porque llevan scrim detrás.
        containerColor = theme.bgMid,
        titleContentColor = theme.textMain,
        textContentColor = theme.textMuted,
        title = { Text(stringResource(R.string.trial_gift_title), fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column {
                Text(stringResource(R.string.trial_gift_body), color = theme.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(14.dp))
                // Los temas de pago que el usuario NO tiene ya, en el orden del selector.
                offered.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.AURORA -> stringResource(R.string.theme_aurora)
                        ThemeMode.AMOLED -> stringResource(R.string.theme_oled)
                        else             -> stringResource(R.string.theme_cuero)
                    }
                    val sel = choice == mode
                    Surface(
                        onClick = { choice = mode },
                        shape = RoundedCornerShape(12.dp),
                        // AMOLED: bgMid es negro puro y una tarjeta con bgMid es invisible.
                        // cardColor da el gris que sí despega del fondo (error ya cometido
                        // cinco veces en el proyecto: heatmap, widget, Wrapped, historial, bingo).
                        color = if (sel) acc.copy(alpha = 0.15f) else cardColor(theme),
                        border = BorderStroke(if (sel) 2.dp else 1.dp, if (sel) acc else theme.border),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                // La etiqueta va SIEMPRE en textMain, también la elegida: medido,
                                // el acento sobre su propio tinte al 15% se queda en 3,0:1 (Oscuro)
                                // y 3,8:1 (Claro). Lo que marca la elección es el borde y el ✓, que
                                // son gráficos (3:1) y ahí el acento sí llega en los 5 temas.
                                Text(label, color = theme.textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                if (mode.value == kept) {
                                    Text(stringResource(R.string.trial_gift_kept), color = theme.textMuted, fontSize = 11.sp)
                                }
                            }
                            if (sel) Text("✓", color = acc, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        // Punto 7 de Víctor (18-07): "Ver el Pro" va RELLENO, igual que "Empezar prueba".
        // Rompe a propósito el patrón de ProUpsellSheet (donde el relleno es el trial y la
        // compra va en OutlinedButton): aquí el momento de venta es este y lo quiere primario.
        confirmButton = {
            Button(
                onClick = { pending = false; onSeePro() },
                colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = onAccentColor(theme)),
                shape = RoundedCornerShape(12.dp)
            ) { Text(stringResource(R.string.trial_gift_see_pro), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    val m = choice ?: return@OutlinedButton
                    // grantTrialGiftTheme ya limpia theme_before_lock (B-041): elegir el
                    // regalo responde a la pregunta de qué tema quiere.
                    Pro.grantTrialGiftTheme(prefs, m)
                    vm.setThemeMode(m, prefs, context)   // aplicarlo en caliente
                    pending = false
                },
                enabled = choice != null,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (choice != null) acc else theme.border)
            ) {
                Text(stringResource(R.string.trial_gift_confirm), color = if (choice != null) acc else theme.textDim)
            }
        }
    )
}

@Composable
private fun CodeEntry(
    theme: Theme,
    prefs: android.content.SharedPreferences,
    acc: androidx.compose.ui.graphics.Color,
    onBack: () -> Unit,
    onRedeemed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("LM-") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val errFormat = stringResource(R.string.pro_code_err_format)
    val errInvalid = stringResource(R.string.pro_code_err_invalid)
    val errAttempts = stringResource(R.string.pro_code_err_attempts)
    val errNetwork = stringResource(R.string.pro_code_err_network)
    val errExhausted = stringResource(R.string.pro_code_err_exhausted)

    Text(stringResource(R.string.pro_redeem_title), color = theme.textMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { code = formatLmCode(it); error = null },
        placeholder = { Text("LM-XXXX-XXXX-XXXX", color = theme.textDim) },
        isError = error != null,
        supportingText = error?.let { { Text(it, color = Red, fontSize = 11.sp) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        colors = fieldColors(theme),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, theme.border)) {
            Text(stringResource(R.string.txt_847607d7), color = theme.textMuted)
        }
        Button(
            onClick = {
                if (!LM_CODE_REGEX.matches(code)) { error = errFormat; return@Button }
                loading = true
                scope.launch {
                    val deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "unknown"
                    val result = withContext(Dispatchers.IO) { Pro.redeemCode(prefs, code, deviceId) }
                    loading = false
                    when (result) {
                        is Pro.RedeemResult.Success -> onRedeemed()
                        is Pro.RedeemResult.InvalidCode -> error = errInvalid
                        is Pro.RedeemResult.TooManyAttempts -> error = errAttempts
                        is Pro.RedeemResult.CodeExhausted -> error = errExhausted
                        is Pro.RedeemResult.NetworkError -> error = errNetwork
                    }
                }
            },
            enabled = code.length == 17 && !loading,
            colors = ButtonDefaults.buttonColors(containerColor = acc, contentColor = onAccentColor(theme)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = onAccentColor(theme))
            else Text(stringResource(R.string.pro_redeem_button), fontWeight = FontWeight.Bold)
        }
    }
}
