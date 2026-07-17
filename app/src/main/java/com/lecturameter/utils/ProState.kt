package com.lecturameter.utils

import android.content.Context
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
 * Gates que lo consultan: tope de retos activos (3 páginas; el historial de retos es
 * GRATIS COMPLETO desde el cambio de Víctor 16-07), temas de pago (Cuero/Aurora/AMOLED,
 * con grandfathering del tema ACTIVO al actualizar) y tope de ediciones (3 gratis =
 * base + 2 / ilimitadas en Pro, decisión de Víctor 17-07). El mapa de calor HORARIO
 * pasó a GRATIS el 17-07 (sale en el Wrapped, no puede estar tras paywall).
 */
object Pro {
    const val PREF_KEY = "pro_unlocked"                 // entitlement permanente (código o Play)
    const val SRC_KEY = "pro_source"                    // "code" | "play"
    const val TRIAL_EXPIRES_KEY = "pro_trial_expires"
    const val TRIAL_STARTED_KEY = "pro_trial_started"   // guarda contra retrasar el reloj
    const val TRIAL_USED_KEY = "pro_trial_used"
    const val GRANDFATHER_KEY = "pro_grandfathered_theme"
    private const val GRANDFATHER_DONE_KEY = "pro_grandfather_done"

    // P-032 (Bloque 4, feature 7, opción B de Víctor): al acabar la prueba de 7 días el
    // usuario ELIGE uno de los 3 temas de pago y se lo queda para siempre.
    //
    // Clave APARTE de GRANDFATHER_KEY a propósito: esa ya la usan los usuarios de la 2.7
    // que venían con Aurora o AMOLED, y es de valor único. Si el regalo escribiera ahí,
    // quien viniera de la 2.7 con Aurora heredado, probara el trial y eligiera Cuero,
    // PERDERÍA el Aurora. Con dos claves, themeAllowed mira las dos y conserva ambos.
    const val TRIAL_GIFT_KEY = "pro_trial_gift_theme"
    /** El regalo ya se resolvió (elegido o descartado): no volver a preguntar. */
    const val TRIAL_GIFT_DONE_KEY = "pro_trial_gift_done"

    /**
     * B-041: el tema de pago que la app tuvo que apagar al caducar la prueba.
     *
     * Antes, cuando el trial caducaba, BooksViewModel pisaba `theme_mode` con "dark" y el
     * tema original se perdía PARA SIEMPRE: quien compraba Pro después no lo recuperaba y
     * ni se acordaba de cuál era. Aquí se guarda antes de pisarlo, para devolverlo en
     * cuanto se vuelva a tener derecho a él (compra, código o regalo del trial).
     */
    const val THEME_BEFORE_LOCK_KEY = "pro_theme_before_lock"
    private const val ATTEMPTS_KEY = "pro_code_attempts"
    private const val LOCKOUT_KEY = "pro_code_lockout_until"
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_MS = 24L * 60 * 60 * 1000
    private const val TRIAL_MS = 7L * 24 * 60 * 60 * 1000

    // A6 (seguridad): el trial y el lockout del canje NO deben viajar en el Auto Backup de
    // Android. Si lo hacen, reinstalar restaura un backup viejo de Google y permite reiniciar
    // (o reactivar) la prueba gratis. Por eso viven en un fichero de prefs SEPARADO,
    // "lecturameter_pro_local.xml", excluido de backup_rules.xml y data_extraction_rules.xml.
    // pro_unlocked y pro_source SÍ se quedan en "lecturameter.xml" (deseable respaldarlos:
    // quien compró Pro no debe perderlo al reinstalar).
    private const val PREFS_MAIN = "lecturameter"
    private const val PREFS_LOCAL = "lecturameter_pro_local"
    private const val MIGRATED_KEY = "pro_local_migrated"

    // Formato de código LM canjeable. Validado aquí ademas de en la UI (defensa en profundidad).
    private val CODE_FORMAT = Regex("^LM-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$")

    // Contexto de aplicación y prefs local, fijados una sola vez en el arranque (init).
    // En tests unitarios no se llama a init: appContext queda null y localFor() cae al prefs
    // que se pasa por parámetro (FakePrefs en memoria), asi los tests no necesitan Context.
    @Volatile private var localPrefsRef: SharedPreferences? = null

    /** Se llama una vez desde LecturameterApplication.onCreate (en cada proceso). Fija el
     *  fichero local y ejecuta la migración de claves de trial/lockout desde el fichero viejo. */
    fun init(context: Context) {
        val app = context.applicationContext
        val local = app.getSharedPreferences(PREFS_LOCAL, Context.MODE_PRIVATE)
        migrateIfNeeded(app, local)
        localPrefsRef = local
    }

    /** Prefs donde viven trial+lockout: la local si init corrió (producción), o el prefs
     *  pasado si no (tests). */
    private fun localFor(prefs: SharedPreferences): SharedPreferences = localPrefsRef ?: prefs

    /** Migración one-shot: la primera vez, copia las claves de trial/lockout del fichero
     *  principal al local y las borra del principal, para no perder el estado de usuarios
     *  ya instalados. A partir de ahí el principal deja de arrastrarlas en sus backups. */
    private fun migrateIfNeeded(ctx: Context, local: SharedPreferences) {
        if (local.getBoolean(MIGRATED_KEY, false)) return
        val main = ctx.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)
        val le = local.edit()
        if (main.contains(TRIAL_STARTED_KEY)) le.putLong(TRIAL_STARTED_KEY, main.getLong(TRIAL_STARTED_KEY, 0L))
        if (main.contains(TRIAL_EXPIRES_KEY)) le.putLong(TRIAL_EXPIRES_KEY, main.getLong(TRIAL_EXPIRES_KEY, 0L))
        if (main.contains(TRIAL_USED_KEY)) le.putBoolean(TRIAL_USED_KEY, main.getBoolean(TRIAL_USED_KEY, false))
        if (main.contains(ATTEMPTS_KEY)) le.putInt(ATTEMPTS_KEY, main.getInt(ATTEMPTS_KEY, 0))
        if (main.contains(LOCKOUT_KEY)) le.putLong(LOCKOUT_KEY, main.getLong(LOCKOUT_KEY, 0L))
        le.putBoolean(MIGRATED_KEY, true).apply()
        main.edit()
            .remove(TRIAL_STARTED_KEY).remove(TRIAL_EXPIRES_KEY).remove(TRIAL_USED_KEY)
            .remove(ATTEMPTS_KEY).remove(LOCKOUT_KEY)
            .apply()
    }

    // Worker de códigos propio de Lecturameter (prefijo LM-). El código del worker vive en
    // C:\Refrac\backend; DESPLEGADO en Cloudflare el 16-07-2026 (ver backend/COMO_GENERAR_CODIGOS.md).
    const val BACKEND_URL = "https://lm-codes.appaugur.workers.dev/redeem"

    /** Páginas de retos activos incluidas en el plan gratis (5 retos por página). */
    const val FREE_CHALLENGE_PAGES = 3

    const val PER_PAGE = 5

    /** P-031: tope de ediciones por libro. Quien ya tenga más NUNCA pierde datos: solo se
     *  bloquea AÑADIR por encima del tope. Decisión Víctor 17-07: gratis base + 2 (3
     *  totales) y Pro sin límite. */
    const val FREE_EDITIONS = 3
    const val PRO_EDITIONS = Int.MAX_VALUE

    /** Temas de pago (D-013). El resto son gratis siempre. */
    val PAID_THEMES = setOf(ThemeMode.CUERO, ThemeMode.AURORA, ThemeMode.AMOLED)

    fun isPro(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): Boolean =
        prefs.getBoolean(PREF_KEY, false) || trialActive(prefs, now)

    /** La prueba está activa entre su inicio y su caducidad. La ventana [inicio, fin) hace
     *  que retrasar el reloj del sistema más allá del inicio NO estire la prueba (edge case
     *  clásico de los trials locales). Instalaciones que activaron la prueba antes de existir
     *  TRIAL_STARTED_KEY: se deriva el inicio como caducidad menos 7 días. */
    fun trialActive(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): Boolean {
        val lp = localFor(prefs)
        val expires = lp.getLong(TRIAL_EXPIRES_KEY, 0L)
        if (expires <= 0L) return false
        val started = lp.getLong(TRIAL_STARTED_KEY, 0L).takeIf { it > 0L } ?: (expires - TRIAL_MS)
        return now in started until expires
    }

    fun trialAvailable(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): Boolean =
        !localFor(prefs).getBoolean(TRIAL_USED_KEY, false) && !prefs.getBoolean(PREF_KEY, false) && !trialActive(prefs, now)

    fun trialDaysLeft(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): Int {
        if (!trialActive(prefs, now)) return 0
        val left = localFor(prefs).getLong(TRIAL_EXPIRES_KEY, 0L) - now
        return if (left <= 0) 0 else ((left + 86_399_999) / 86_400_000).toInt()
    }

    fun activateTrial(prefs: SharedPreferences, now: Long = System.currentTimeMillis()) {
        localFor(prefs).edit()
            .putLong(TRIAL_STARTED_KEY, now)
            .putLong(TRIAL_EXPIRES_KEY, now + TRIAL_MS)
            .putBoolean(TRIAL_USED_KEY, true)
            .apply()
    }

    fun markPurchased(prefs: SharedPreferences) {
        prefs.edit().putBoolean(PREF_KEY, true).putString(SRC_KEY, "play").apply()
    }

    private fun markCodeRedeemed(prefs: SharedPreferences) {
        // El entitlement va al principal (respaldable); los contadores de canje al local.
        prefs.edit().putBoolean(PREF_KEY, true).putString(SRC_KEY, "code").apply()
        localFor(prefs).edit().remove(ATTEMPTS_KEY).remove(LOCKOUT_KEY).apply()
    }

    fun editionLimit(prefs: SharedPreferences): Int = if (isPro(prefs)) PRO_EDITIONS else FREE_EDITIONS

    /** ¿Puede este usuario USAR este tema? Pro todo; gratis los no-de-pago, más el tema
     *  que ya tenía activo al actualizar (grandfathering aprobado por Víctor) y el que
     *  eligió como regalo al acabar la prueba (P-032). Las dos claves son independientes:
     *  se pueden tener las dos a la vez y ninguna pisa a la otra. */
    fun themeAllowed(prefs: SharedPreferences, mode: ThemeMode): Boolean =
        isPro(prefs) ||
        mode !in PAID_THEMES ||
        prefs.getString(GRANDFATHER_KEY, null) == mode.value ||
        prefs.getString(TRIAL_GIFT_KEY, null) == mode.value

    // ── P-032: regalo de un tema al acabar la prueba ────────────────────────────

    /**
     * ¿Toca ofrecer el regalo? Solo a quien gastó la prueba y ya se le acabó, sin haber
     * comprado Pro y sin haber elegido todavía.
     *
     * Ojo con el orden: a quien compra Pro NO se le pregunta, porque ya tiene los 3 temas
     * y el regalo no le añade nada. Si algún día cancela... no puede: la compra es única.
     */
    fun trialGiftPending(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): Boolean =
        localFor(prefs).getBoolean(TRIAL_USED_KEY, false) &&
        !trialActive(prefs, now) &&
        !prefs.getBoolean(PREF_KEY, false) &&
        !prefs.getBoolean(TRIAL_GIFT_DONE_KEY, false)

    /** Concede el tema elegido para siempre y cierra el regalo. Solo temas de pago: los
     *  gratis ya los tiene y "regalar" uno gratis sería mentirle.
     *
     *  Borra THEME_BEFORE_LOCK_KEY a propósito: elegir el regalo YA RESPONDE a la pregunta
     *  "qué tema quieres". Si no se borrara, quien tenía Cuero en la prueba, eligiera Aurora
     *  de regalo y comprara Pro meses después se encontraría con que la app le cambia solo a
     *  Cuero, pisando el Aurora que había elegido a conciencia. */
    fun grantTrialGiftTheme(prefs: SharedPreferences, mode: ThemeMode) {
        if (mode !in PAID_THEMES) return
        prefs.edit()
            .putString(TRIAL_GIFT_KEY, mode.value)
            .putBoolean(TRIAL_GIFT_DONE_KEY, true)
            .remove(THEME_BEFORE_LOCK_KEY)
            .apply()
    }

    // ── B-041: no perder el tema al caducar la prueba ───────────────────────────

    /** Guarda el tema de pago que se va a apagar, para poder devolverlo luego. No pisa un
     *  valor ya guardado: el primero es el bueno (el que el usuario eligió de verdad). */
    fun rememberThemeBeforeLock(prefs: SharedPreferences, mode: ThemeMode) {
        if (mode !in PAID_THEMES) return
        if (prefs.contains(THEME_BEFORE_LOCK_KEY)) return
        prefs.edit().putString(THEME_BEFORE_LOCK_KEY, mode.value).apply()
    }

    /**
     * ¿Hay un tema apagado que ahora vuelva a estar permitido (compró Pro, canjeó código o
     * lo eligió como regalo)? Lo devuelve y limpia la clave. Null si no hay nada que hacer.
     */
    fun reclaimThemeAfterUnlock(prefs: SharedPreferences): ThemeMode? {
        val saved = prefs.getString(THEME_BEFORE_LOCK_KEY, null) ?: return null
        val mode = ThemeMode.entries.firstOrNull { it.value == saved }
        if (mode == null || !themeAllowed(prefs, mode)) return null
        prefs.edit().remove(THEME_BEFORE_LOCK_KEY).apply()
        return mode
    }

    /** One-shot al actualizar: si el usuario venía usando Aurora o AMOLED (gratis hasta la
     *  2.7), se le respeta ESE tema para siempre. Cuero es nuevo y no se hereda. */
    fun grandfatherCurrentThemeIfNeeded(prefs: SharedPreferences) {
        if (prefs.getBoolean(GRANDFATHER_DONE_KEY, false)) return
        val current = prefs.getString("theme_mode", null)
        val edit = prefs.edit().putBoolean(GRANDFATHER_DONE_KEY, true)
        if (current == "aurora" || current == "amoled") edit.putString(GRANDFATHER_KEY, current)
        edit.apply()
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
        val lp = localFor(prefs)
        if (now < lp.getLong(LOCKOUT_KEY, 0L)) return RedeemResult.TooManyAttempts
        // A9: validar el formato aquí también (no solo en la UI). Un código mal formado no
        // llega a la red y no gasta intento.
        if (!CODE_FORMAT.matches(code)) return RedeemResult.InvalidCode
        return try {
            val conn = URL(BACKEND_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            // A9: escapar barras invertidas ANTES que las comillas (si no, la barra que
            // escapa la comilla se volvería a escapar y rompería el JSON).
            fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
            val safeCode = esc(code)
            val safeDevice = esc(deviceId)
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
        val lp = localFor(prefs)
        val attempts = lp.getInt(ATTEMPTS_KEY, 0) + 1
        lp.edit().apply {
            if (attempts >= MAX_ATTEMPTS) {
                putLong(LOCKOUT_KEY, now + LOCKOUT_MS)
                remove(ATTEMPTS_KEY)
            } else putInt(ATTEMPTS_KEY, attempts)
        }.apply()
    }
}
