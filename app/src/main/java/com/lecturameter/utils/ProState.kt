package com.lecturameter.utils

import android.content.SharedPreferences
import com.lecturameter.ThemeMode
import java.net.HttpURLConnection
import java.net.URL

/**
 * D-013 (monetización, modelo Augur adaptado): punto ÚNICO de verdad del entitlement Pro.
 *
 * Precio decidido por Víctor 16-07: 4,99 con oferta de lanzamiento a 2,99 y una semana
 * de prueba gratis. La prueba es LOCAL (Play no ofrece trials en pagos únicos): flag con
 * caducidad en prefs, un solo uso por instalación, igual que hace Augur.
 *
 * Fuentes de Pro: compra en Play (BillingManager → markPurchased), código LM canjeado
 * contra el worker de Cloudflare (redeemCode), o prueba de 7 días (activateTrial).
 *
 * Gates que lo consultan: historial de retos (3 páginas por año gratis), tope de retos
 * activos (3 páginas), temas de pago (Cuero/Aurora/AMOLED, con grandfathering del tema
 * ACTIVO al actualizar) y tope de ediciones (2 gratis / 5 Pro, P-031).
 */
object Pro {
    const val PREF_KEY = "pro_unlocked"                 // entitlement permanente (código o Play)
    const val SRC_KEY = "pro_source"                    // "code" | "play"
    const val TRIAL_EXPIRES_KEY = "pro_trial_expires"
    const val TRIAL_USED_KEY = "pro_trial_used"
    const val GRANDFATHER_KEY = "pro_grandfathered_theme"
    private const val GRANDFATHER_DONE_KEY = "pro_grandfather_done"
    private const val ATTEMPTS_KEY = "pro_code_attempts"
    private const val LOCKOUT_KEY = "pro_code_lockout_until"
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_MS = 24L * 60 * 60 * 1000
    private const val TRIAL_MS = 7L * 24 * 60 * 60 * 1000

    // Worker de códigos propio de Lecturameter (prefijo LM-). El código del worker vive en
    // C:\Refrac\backend; hasta que se despliegue con wrangler, el canje devuelve error de red.
    const val BACKEND_URL = "https://lm-codes.appaugur.workers.dev/redeem"

    /** Páginas de retos activos incluidas en el plan gratis (5 retos por página). */
    const val FREE_CHALLENGE_PAGES = 3

    /** Páginas de historial de retos POR AÑO en el plan gratis (acumulativo: cada año suma 3). */
    const val FREE_HISTORY_PAGES_PER_YEAR = 3

    const val PER_PAGE = 5

    /** P-031: tope de ediciones por libro. Quien ya tenga más NUNCA pierde datos: solo se
     *  bloquea AÑADIR por encima del tope. */
    const val FREE_EDITIONS = 2
    const val PRO_EDITIONS = 5

    /** Temas de pago (D-013). El resto son gratis siempre. */
    val PAID_THEMES = setOf(ThemeMode.CUERO, ThemeMode.AURORA, ThemeMode.AMOLED)

    fun isPro(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_KEY, false) || trialActive(prefs)

    fun trialActive(prefs: SharedPreferences): Boolean =
        System.currentTimeMillis() < prefs.getLong(TRIAL_EXPIRES_KEY, 0L)

    fun trialAvailable(prefs: SharedPreferences): Boolean =
        !prefs.getBoolean(TRIAL_USED_KEY, false) && !prefs.getBoolean(PREF_KEY, false) && !trialActive(prefs)

    fun trialDaysLeft(prefs: SharedPreferences): Int {
        val left = prefs.getLong(TRIAL_EXPIRES_KEY, 0L) - System.currentTimeMillis()
        return if (left <= 0) 0 else ((left + 86_399_999) / 86_400_000).toInt()
    }

    fun activateTrial(prefs: SharedPreferences) {
        prefs.edit()
            .putLong(TRIAL_EXPIRES_KEY, System.currentTimeMillis() + TRIAL_MS)
            .putBoolean(TRIAL_USED_KEY, true)
            .apply()
    }

    fun markPurchased(prefs: SharedPreferences) {
        prefs.edit().putBoolean(PREF_KEY, true).putString(SRC_KEY, "play").apply()
    }

    private fun markCodeRedeemed(prefs: SharedPreferences) {
        prefs.edit()
            .putBoolean(PREF_KEY, true).putString(SRC_KEY, "code")
            .remove(ATTEMPTS_KEY).remove(LOCKOUT_KEY)
            .apply()
    }

    fun editionLimit(prefs: SharedPreferences): Int = if (isPro(prefs)) PRO_EDITIONS else FREE_EDITIONS

    /** ¿Puede este usuario USAR este tema? Pro todo; gratis los no-de-pago más el tema
     *  que ya tenía activo al actualizar (grandfathering aprobado por Víctor). */
    fun themeAllowed(prefs: SharedPreferences, mode: ThemeMode): Boolean =
        isPro(prefs) || mode !in PAID_THEMES || prefs.getString(GRANDFATHER_KEY, null) == mode.value

    /** One-shot al actualizar: si el usuario venía usando Aurora o AMOLED (gratis hasta la
     *  2.7), se le respeta ESE tema para siempre. Cuero es nuevo y no se hereda. */
    fun grandfatherCurrentThemeIfNeeded(prefs: SharedPreferences) {
        if (prefs.getBoolean(GRANDFATHER_DONE_KEY, false)) return
        val current = prefs.getString("theme_mode", null)
        if (current == "aurora" || current == "amoled") {
            prefs.edit().putString(GRANDFATHER_KEY, current).apply()
        }
        prefs.edit().putBoolean(GRANDFATHER_DONE_KEY, true).apply()
    }

    // ── Canje de código (red: llamar SIEMPRE desde Dispatchers.IO) ──────────────
    sealed class RedeemResult {
        object Success : RedeemResult()
        object InvalidCode : RedeemResult()
        object TooManyAttempts : RedeemResult()
        data class NetworkError(val httpCode: Int?) : RedeemResult()
    }

    fun redeemCode(prefs: SharedPreferences, code: String, deviceId: String): RedeemResult {
        val now = System.currentTimeMillis()
        if (now < prefs.getLong(LOCKOUT_KEY, 0L)) return RedeemResult.TooManyAttempts
        return try {
            val conn = URL(BACKEND_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val safeCode = code.replace("\"", "")
            val safeDevice = deviceId.replace("\"", "")
            conn.outputStream.use { it.write("""{"code":"$safeCode","deviceId":"$safeDevice"}""".toByteArray()) }
            when (conn.responseCode) {
                200 -> { markCodeRedeemed(prefs); RedeemResult.Success }
                400, 404, 409 -> { registerFailedAttempt(prefs, now); RedeemResult.InvalidCode }
                else -> RedeemResult.NetworkError(conn.responseCode)
            }
        } catch (e: Exception) {
            AppLogger.logError("Canje de código Pro falló", e, "Pro")
            RedeemResult.NetworkError(null)
        }
    }

    private fun registerFailedAttempt(prefs: SharedPreferences, now: Long) {
        val attempts = prefs.getInt(ATTEMPTS_KEY, 0) + 1
        prefs.edit().apply {
            if (attempts >= MAX_ATTEMPTS) {
                putLong(LOCKOUT_KEY, now + LOCKOUT_MS)
                remove(ATTEMPTS_KEY)
            } else putInt(ATTEMPTS_KEY, attempts)
        }.apply()
    }
}
