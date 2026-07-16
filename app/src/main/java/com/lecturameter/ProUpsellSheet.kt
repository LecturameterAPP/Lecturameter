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
                Spacer(Modifier.height(20.dp))
                if (Pro.trialAvailable(prefs)) {
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
                OutlinedButton(
                    onClick = { if (activity != null) LmBilling.launchPurchase(activity) },
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
                TextButton(
                    onClick = {
                        restoring = true
                        LmBilling.restore { found ->
                            // El callback de Billing llega en su propio hilo: al principal
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                restoring = false
                                if (found) { refresh++; onProChanged() }
                                else android.widget.Toast.makeText(context, restoreNoneMsg, android.widget.Toast.LENGTH_SHORT).show()
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
