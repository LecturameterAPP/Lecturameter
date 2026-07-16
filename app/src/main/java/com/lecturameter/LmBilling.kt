package com.lecturameter

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
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

object LmBilling : PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    private var appContext: Context? = null

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    // Señal para que la UI reaccione a una compra o restauración completada
    private val _purchaseCompleted = MutableStateFlow(0)
    val purchaseCompleted: StateFlow<Int> = _purchaseCompleted.asStateFlow()

    // Reintentos acotados de reconexión: el servicio de Play se cae p. ej. al actualizarse
    // la Play Store; sin reintento el botón de compra quedaría muerto hasta reabrir la app.
    private const val MAX_RECONNECTS = 3
    private var reconnects = 0

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
            }
        })
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
        billingClient?.launchBillingFlow(activity, params)
        return true
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
        restore { }
    }

    /** Restauración manual ("Restaurar compra" del upsell) y automática del arranque.
     *  onResult(true) si se encontró la compra de Pro en la cuenta; false si no hay nada
     *  que restaurar o Billing no está disponible. Se invoca en el hilo de Billing. */
    fun restore(onResult: (Boolean) -> Unit) {
        val client = billingClient ?: run { onResult(false); return }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach { handlePurchase(it) }
                onResult(purchases.any { p ->
                    SKU_LM_PRO in p.products && p.purchaseState == Purchase.PurchaseState.PURCHASED
                })
            } else {
                com.lecturameter.utils.AppLogger.log("Billing restore failed: ${result.responseCode} ${result.debugMessage}")
                onResult(false)
            }
        }
    }

    private fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SKU_LM_PRO)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ))
            .build()
        billingClient?.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = detailsList.associateBy { it.productId }
            }
        }
    }
}
