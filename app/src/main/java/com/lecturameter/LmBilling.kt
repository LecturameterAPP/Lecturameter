package com.lecturameter

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * D-013: Play Billing para el pago único de Lecturameter Pro (modelo Augur adaptado:
 * un solo producto INAPP no consumible, sin propinas). El producto `lecturameter_pro`
 * debe existir en Play Console; hasta entonces productDetails queda vacío y el botón
 * de compra del upsell se muestra deshabilitado ("Disponible en Google Play").
 *
 * La compra y la restauración escriben el flag `pro_unlocked` vía Pro.markPurchased,
 * el mismo punto único que usan el canje de códigos y todos los gates.
 */
const val SKU_LM_PRO = "lecturameter_pro"
const val SKU_TIP_COFFEE = "tip_coffee"
const val SKU_TIP_MEAL = "tip_meal"
const val SKU_TIP_GENEROUS = "tip_generous"
val TIP_SKUS = listOf(SKU_TIP_COFFEE, SKU_TIP_MEAL, SKU_TIP_GENEROUS)

object LmBilling : PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    private var appContext: Context? = null

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    // Señal para que la UI reaccione a una compra o restauración completada
    private val _purchaseCompleted = MutableStateFlow(0)
    val purchaseCompleted: StateFlow<Int> = _purchaseCompleted.asStateFlow()

    private val _tipCompleted = MutableStateFlow(0)
    val tipCompleted: StateFlow<Int> = _tipCompleted.asStateFlow()

    // Reintentos acotados de reconexión: el servicio de Play se cae p. ej. al actualizarse
    // la Play Store; sin reintento el botón de compra quedaría muerto hasta reabrir la app.
    private const val MAX_RECONNECTS = 3
    private var reconnects = 0

    // RF-M22: como mucho UN resultado vacío cuenta por proceso. "Dos vacíos consecutivos"
    // significa en arranques distintos: dos taps a "Restaurar compra" en la misma sesión
    // no deben sumar dos y revocar lo que un solo arranque no justifica.
    private var emptyRestoreCounted = false

    fun init(context: Context) {
        if (billingClient != null) return
        appContext = context.applicationContext
        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        connect()
    }

    private fun connect() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    reconnects = 0
                    queryProducts()
                    restorePurchases()
                } else {
                    com.lecturameter.utils.AppLogger.log("Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                com.lecturameter.utils.AppLogger.log("Billing disconnected")
                if (reconnects < MAX_RECONNECTS) { reconnects++; connect() }
                // A8: agotados los reintentos, vaciar productDetails para que el botón de
                // compra del upsell pase a deshabilitado en vez de quedar "vivo" sin servicio.
                // M3: esto ya NO es definitivo; reconnect() da marcha atrás (ver abajo).
                else _productDetails.value = emptyMap()
            }
        })
    }

    /**
     * M3: vuelta atrás desde el "billing muerto para siempre".
     *
     * init() se llama UNA vez en onCreate. Agotados los 3 reintentos no había NADIE que
     * volviera a conectar: bastaba con que la Play Store se actualizara sola en segundo
     * plano (pasa a diario) para que el usuario volviera a la app, abriera la hoja y el
     * precio no estuviera nunca más, hasta matar el proceso. Y asumiría que Pro no está
     * a la venta.
     *
     * Se llama desde MainActivity.onResume y al abrir ProUpsellSheet: los dos momentos en
     * los que el usuario puede querer pagar. Resetea el contador porque un usuario que
     * vuelve a la app es un intento nuevo, no la continuación de la ráfaga anterior.
     *
     * Barato de llamar: si ya está conectado y con precio, no hace nada.
     */
    fun reconnect() {
        val client = billingClient ?: return
        when (client.connectionState) {
            BillingClient.ConnectionState.CONNECTED -> {
                // Conectado pero sin precio (p. ej. la query falló en su día): reintentarla,
                // que es justo lo que deja el botón de compra deshabilitado.
                if (_productDetails.value.isEmpty()) queryProducts()
            }
            // Ya hay una conexión en curso: dejarla trabajar, no apilar startConnection.
            BillingClient.ConnectionState.CONNECTING -> Unit
            else -> { reconnects = 0; connect() }
        }
    }

    fun launchPurchase(activity: Activity): Boolean {
        val details = _productDetails.value[SKU_LM_PRO] ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            ))
            .build()
        // A8: comprobar el resultado de launchBillingFlow y devolver false si falla (p. ej.
        // el servicio se cayó justo antes), para que la UI no crea que la compra arrancó.
        val result = billingClient?.launchBillingFlow(activity, params)
        return result?.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun launchTipPurchase(activity: Activity, sku: String): Boolean {
        val details = _productDetails.value[sku] ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            ))
            .build()
        val result = billingClient?.launchBillingFlow(activity, params)
        return result?.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { handlePurchase(it) }
            // Ya lo compró con esta cuenta (p. ej. reinstalación con la caché de Play
            // desincronizada): recuperar el entitlement en vez de fallar en silencio
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                restorePurchases()
            BillingClient.BillingResponseCode.USER_CANCELED -> { /* sin ruido */ }
            else ->
                com.lecturameter.utils.AppLogger.log("Billing purchase failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val isTip = purchase.products.any { it in TIP_SKUS }
        if (isTip) {
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient?.consumeAsync(consumeParams) { _, _ ->
                _tipCompleted.value = _tipCompleted.value + 1
            }
            return
        }
        if (SKU_LM_PRO !in purchase.products) return
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient?.acknowledgePurchase(params) { }
        }
        appContext?.let { ctx ->
            val prefs = ctx.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
            com.lecturameter.utils.Pro.markPurchased(prefs)
            _purchaseCompleted.value = _purchaseCompleted.value + 1
        }
    }

    private fun restorePurchases() {
        restore { }   // restauración automática del arranque: sin UI, nada que distinguir
    }

    /**
     * C2: resultado de una restauración. Antes esto era un Boolean y colapsaba dos cosas
     * MUY distintas en el mismo `false`: "la cuenta no tiene la compra" y "Play no ha
     * contestado" (servicio caído, sin red, Billing no disponible). La UI enseñaba
     * "no consta ninguna compra" a quien había pagado y estaba en el metro.
     */
    enum class RestoreResult {
        /** Se encontró la compra de Pro en la cuenta y ya está aplicada. */
        FOUND,
        /** Play contestó OK y esta cuenta no tiene la compra. Es un "no" de verdad. */
        NONE,
        /** Play no contestó: no se sabe nada. NO se le puede decir al usuario que no compró. */
        UNAVAILABLE
    }

    /** Restauración manual ("Restaurar compra" del upsell) y automática del arranque.
     *  Se invoca en el hilo de Billing. */
    fun restore(onResult: (RestoreResult) -> Unit) {
        val client = billingClient ?: run { onResult(RestoreResult.UNAVAILABLE); return }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
                val found = purchases.any { p ->
                    SKU_LM_PRO in p.products && p.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                // CRÍTICO (auditoría dinero 17-07): Play respondió OK y la cuenta NO tiene la
                // compra → probable reembolso o cancelación. RF-M22: pero un solo vacío puede
                // ser un falso negativo con comprador legítimo (otra cuenta de Google activa en
                // Play, o primer arranque tras restore de Auto Backup con la caché de Play sin
                // sincronizar), así que ya no se revoca a la primera: registerPlayEmptyRestore
                // exige dos vacíos en arranques distintos y respeta el Pro de código y el trial.
                // Un responseCode != OK no pasa por aquí (rama else): no cuenta como vacío.
                if (!found) appContext?.let { ctx ->
                    val prefs = ctx.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
                    val wasPro = com.lecturameter.utils.Pro.isPro(prefs)
                    if (!emptyRestoreCounted) {
                        emptyRestoreCounted = true
                        com.lecturameter.utils.Pro.registerPlayEmptyRestore(prefs)
                    }
                    // Si de verdad se retiró algo, avisar a la UI para que recomponga (candado
                    // de temas de pago, topes de retos/ediciones) sin esperar a reiniciar.
                    if (wasPro && !com.lecturameter.utils.Pro.isPro(prefs))
                        _purchaseCompleted.value = _purchaseCompleted.value + 1
                } else appContext?.let { ctx ->
                    // RF-M22: cualquier resultado CON compra resetea el contador de vacíos.
                    val prefs = ctx.getSharedPreferences("lecturameter", Context.MODE_PRIVATE)
                    com.lecturameter.utils.Pro.resetPlayEmptyRestores(prefs)
                    emptyRestoreCounted = false
                }
                onResult(if (found) RestoreResult.FOUND else RestoreResult.NONE)
            } else {
                // SERVICE_DISCONNECTED / SERVICE_UNAVAILABLE / BILLING_UNAVAILABLE / sin red.
                // Un responseCode != OK no dice NADA sobre lo que compró el usuario.
                com.lecturameter.utils.AppLogger.log("Billing restore failed: ${result.responseCode} ${result.debugMessage}")
                onResult(RestoreResult.UNAVAILABLE)
            }
        }
    }

    private fun queryProducts() {
        val allSkus = listOf(SKU_LM_PRO) + TIP_SKUS
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(allSkus.map { sku ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(sku)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            })
            .build()
        // Billing 8: el callback pasa a recibir QueryProductDetailsResult (antes era
        // List<ProductDetails>). Los productos no encontrados ahora vienen aparte en
        // getUnfetchedProductList(); aquí solo interesan los resueltos.
        billingClient?.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = queryResult.productDetailsList.associateBy { it.productId }
            }
        }
    }
}
