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
    // RF-M22: restauraciones consecutivas con respuesta OK pero SIN la compra. Ver
    // registerPlayEmptyRestore: con una sola no se revoca nada.
    const val PLAY_EMPTY_RESTORES_KEY = "play_empty_restores"
    const val TRIAL_EXPIRES_KEY = "pro_trial_expires"
    const val TRIAL_STARTED_KEY = "pro_trial_started"   // guarda contra retrasar el reloj
    const val TRIAL_USED_KEY = "pro_trial_used"

    // M1: reloj efectivo del trial. Ver effectiveNow().
    const val TRIAL_SEEN_MAX_KEY = "pro_trial_seen_max"
    const val TRIAL_CLOCK_OFFSET_KEY = "pro_trial_clock_offset"
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
    /** M1: por debajo de este salto no se reescribe la marca de agua. Ver effectiveNow. */
    private const val SEEN_MAX_GRANULARITY_MS = 60_000L

    // C1 (revisión de lanzamiento, 19-07): el trial vive en "lecturameter.xml", que SÍ se
    // respalda.
    //
    // OJO, que el comentario que había aquí (A6) razonaba AL REVÉS y regalaba la app.
    // Decía que sacar pro_trial_* del Auto Backup impedía "reiniciar la prueba al
    // reinstalar". Es justo al contrario: excluir una clave del backup no impide restaurar
    // un valor MALO, impide restaurar el BUENO. Al reinstalar, el fichero excluido no
    // existe, pro_trial_used vuelve a su default (false) y trialAvailable() ofrece otros
    // 7 días. Y como lecturameter.xml sí se restaura, el usuario recupera libros, sesiones,
    // wrapped y retos: gastar la prueba, desinstalar, reinstalar desde Play y volver a
    // empezar era gratis, cómodo, sin root ni ADB, y repetible para siempre.
    //
    // Respaldando el trial, reinstalar restaura pro_trial_used = true y la prueba NO vuelve.
    // Regla para el siguiente que pase por aquí: una marca de "ya lo has gastado" SIEMPRE
    // quiere backup; lo que no quiere backup es un secreto o una credencial.
    //
    // Lo que esto NO cubre: "Borrar datos" desde los Ajustes de Android (se lleva por
    // delante también el backup). Cerrar esa vía exige registrar el dispositivo en el
    // backend (endpoint /trial); ver el informe de esta rama.
    //
    // pro_code_attempts y pro_code_lockout se quedan en el fichero local: no conceden Pro
    // por sí solos y el gate real del canje es el worker.
    private const val PREFS_MAIN = "lecturameter"
    private const val PREFS_LOCAL = "lecturameter_pro_local"
    private const val MIGRATED_KEY = "pro_local_migrated"        // legacy A6: principal → local
    private const val TRIAL_BACK_KEY = "pro_trial_back_to_main"  // C1: local → principal

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
        migrateCodeCountersToLocal(app, local)
        migrateTrialBackToMain(app, local)
        localPrefsRef = local
    }

    /** Prefs donde viven los contadores del canje: la local si init corrió (producción), o
     *  el prefs pasado si no (tests). El trial YA NO pasa por aquí: vive en el principal. */
    private fun localFor(prefs: SharedPreferences): SharedPreferences = localPrefsRef ?: prefs

    /** Migración one-shot (resto de A6): contadores del canje del principal al local. Se
     *  mantiene solo para no perder un lockout vigente de instalaciones ya migradas. */
    private fun migrateCodeCountersToLocal(ctx: Context, local: SharedPreferences) {
        if (local.getBoolean(MIGRATED_KEY, false)) return
        val main = ctx.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)
        val le = local.edit()
        if (main.contains(ATTEMPTS_KEY)) le.putInt(ATTEMPTS_KEY, main.getInt(ATTEMPTS_KEY, 0))
        if (main.contains(LOCKOUT_KEY)) le.putLong(LOCKOUT_KEY, main.getLong(LOCKOUT_KEY, 0L))
        le.putBoolean(MIGRATED_KEY, true).apply()
        main.edit().remove(ATTEMPTS_KEY).remove(LOCKOUT_KEY).apply()
    }

    /**
     * C1: devuelve las claves del trial al fichero respaldado, para las instalaciones que
     * ya corrieron la migración de A6 y las tienen en el local.
     *
     * Conservadora a propósito: NUNCA regala una prueba nueva. Si cualquiera de los dos
     * ficheros dice que la prueba se gastó, se gastó; y la ventana solo se copia si el
     * principal no la trae ya (un backup restaurado manda sobre el fichero local, que es
     * el que puede haber nacido vacío tras reinstalar).
     */
    private fun migrateTrialBackToMain(ctx: Context, local: SharedPreferences) {
        val main = ctx.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)
        if (main.getBoolean(TRIAL_BACK_KEY, false)) return
        val me = main.edit()
        if (!main.contains(TRIAL_STARTED_KEY) && local.contains(TRIAL_STARTED_KEY))
            me.putLong(TRIAL_STARTED_KEY, local.getLong(TRIAL_STARTED_KEY, 0L))
        if (!main.contains(TRIAL_EXPIRES_KEY) && local.contains(TRIAL_EXPIRES_KEY))
            me.putLong(TRIAL_EXPIRES_KEY, local.getLong(TRIAL_EXPIRES_KEY, 0L))
        if (local.getBoolean(TRIAL_USED_KEY, false)) me.putBoolean(TRIAL_USED_KEY, true)
        me.putBoolean(TRIAL_BACK_KEY, true).apply()
        local.edit()
            .remove(TRIAL_STARTED_KEY).remove(TRIAL_EXPIRES_KEY).remove(TRIAL_USED_KEY)
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

    /**
     * M1: reloj efectivo del trial, a prueba de retrasar la hora del sistema.
     *
     * La guarda vieja (`now in started until expires`) solo cortaba si retrasabas el reloj
     * ANTES del inicio del trial, cosa que no hace nadie. El ataque real es el de dentro de
     * la ventana: estás en el día 6, pones la fecha en el día 1 y `now` sigue estando dentro
     * de [inicio, fin) → Pro permanente mientras no toques la fecha.
     *
     * Aquí se guarda el mayor instante efectivo visto (seen_max) más un desfase acumulado.
     * El desfase es lo que hace que esto funcione, y por eso NO basta un simple
     * max(now, seenMax): con solo el máximo, dejar la fecha atrasada congela `now` por
     * debajo de la marca, el efectivo se queda clavado en el día 6 y el trial no caduca
     * NUNCA. Absorbiendo el retroceso en el desfase, el reloj efectivo no baja y además
     * sigue AVANZANDO con el tiempo real aunque la fecha del sistema se quede atrás.
     * Retroceder mil veces no regala ni un minuto.
     *
     * Adelantar el reloj sí acorta la prueba, y es lo correcto: quien se la quiere acabar
     * antes, allá él.
     */
    private fun effectiveNow(prefs: SharedPreferences, now: Long): Long {
        val seenMax = prefs.getLong(TRIAL_SEEN_MAX_KEY, 0L)
        val offset = prefs.getLong(TRIAL_CLOCK_OFFSET_KEY, 0L)
        var eff = now + offset
        if (seenMax > 0L && eff < seenMax) {
            // Reloj retrasado: el desfase se queda EXACTAMENTE con lo que se intentó ganar.
            prefs.edit().putLong(TRIAL_CLOCK_OFFSET_KEY, offset + (seenMax - eff)).apply()
            eff = seenMax
        } else if (eff > seenMax + SEEN_MAX_GRANULARITY_MS) {
            // Solo se reescribe la marca a saltos: isPro() se llama en cada recomposición
            // (themeAllowed) y un write por recomposición sería absurdo. Un minuto de
            // margen no le da para un exploit a nadie.
            prefs.edit().putLong(TRIAL_SEEN_MAX_KEY, eff).apply()
        }
        return eff
    }

    /** La prueba está activa entre su inicio y su caducidad, contra el reloj EFECTIVO
     *  (ver effectiveNow: retrasar la hora del sistema no estira la prueba). Instalaciones
     *  que activaron la prueba antes de existir TRIAL_STARTED_KEY: se deriva el inicio como
     *  caducidad menos 7 días. */
    fun trialActive(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): Boolean {
        val expires = prefs.getLong(TRIAL_EXPIRES_KEY, 0L)
        if (expires <= 0L) return false
        val started = prefs.getLong(TRIAL_STARTED_KEY, 0L).takeIf { it > 0L } ?: (expires - TRIAL_MS)
        return effectiveNow(prefs, now) in started until expires
    }

    fun trialAvailable(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): Boolean =
        !prefs.getBoolean(TRIAL_USED_KEY, false) && !prefs.getBoolean(PREF_KEY, false) && !trialActive(prefs, now)

    fun trialDaysLeft(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): Int {
        if (!trialActive(prefs, now)) return 0
        val left = prefs.getLong(TRIAL_EXPIRES_KEY, 0L) - effectiveNow(prefs, now)
        return if (left <= 0) 0 else ((left + 86_399_999) / 86_400_000).toInt()
    }

    fun activateTrial(prefs: SharedPreferences, now: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putLong(TRIAL_STARTED_KEY, now)
            .putLong(TRIAL_EXPIRES_KEY, now + TRIAL_MS)
            .putBoolean(TRIAL_USED_KEY, true)
            // M1: la marca de agua arranca en el inicio y el desfase a cero.
            .putLong(TRIAL_SEEN_MAX_KEY, now)
            .putLong(TRIAL_CLOCK_OFFSET_KEY, 0L)
            .apply()
    }

    fun markPurchased(prefs: SharedPreferences) {
        // RF-M22: una compra confirmada resetea el contador de restauraciones vacías.
        prefs.edit().putBoolean(PREF_KEY, true).putString(SRC_KEY, "play")
            .remove(PLAY_EMPTY_RESTORES_KEY).apply()
    }

    /**
     * CRÍTICO (auditoría dinero 17-07): un reembolso o cancelación de Play debe retirar Pro.
     * Antes nada escribía nunca pro_unlocked=false, así que comprar por 2,99, pedir el
     * reembolso autoservicio de Play y quedarse Pro para siempre era un procedimiento de dos
     * pasos sin root ni ADB.
     *
     * Se llama SOLO cuando Play ha respondido OK y la cuenta NO tiene la compra activa (una
     * restauración con resultado NONE, no UNAVAILABLE: un servicio caído no debe revocar nada).
     *
     * Retira ÚNICAMENTE el Pro que vino de una COMPRA (SRC_KEY=="play"). El Pro de CÓDIGO no se
     * toca: un usuario de código no tiene compra en Play y su consulta daría NONE siempre, así
     * que revocarlo aquí le quitaría lo que canjeó. El trial es aparte (no usa PREF_KEY).
     *
     * RF-M22: desde el fix de la revisión final ya NO la llama LmBilling directamente; el
     * punto de entrada es registerPlayEmptyRestore, que exige dos vacíos consecutivos.
     */
    fun revokePlayEntitlementIfGone(prefs: SharedPreferences) {
        if (!prefs.getBoolean(PREF_KEY, false)) return
        if (prefs.getString(SRC_KEY, null) != "play") return
        prefs.edit().remove(PREF_KEY).remove(SRC_KEY).apply()
    }

    /**
     * RF-M22: un ÚNICO resultado vacío de Play puede ser un falso negativo con comprador
     * legítimo: otra cuenta de Google del mismo dispositivo activa en Play, o el primer
     * arranque tras un restore de Auto Backup con la caché de Play aún sin sincronizar.
     * Por eso la revocación exige DOS resultados vacíos consecutivos en arranques distintos
     * (LmBilling cuenta como mucho uno por proceso): con el primero solo se incrementa el
     * contador y el Pro se mantiene; cualquier resultado con compra lo resetea a 0
     * (resetPlayEmptyRestores o markPurchased). Un error de conexión/servicio no cuenta:
     * LmBilling solo llama aquí cuando el responseCode fue OK.
     *
     * Solo aplica al Pro de COMPRA (SRC_KEY=="play"): el de código y el trial ni se cuentan
     * ni se tocan. Devuelve true si esta llamada revocó el entitlement.
     */
    fun registerPlayEmptyRestore(prefs: SharedPreferences): Boolean {
        if (!prefs.getBoolean(PREF_KEY, false)) return false
        if (prefs.getString(SRC_KEY, null) != "play") return false
        val count = prefs.getInt(PLAY_EMPTY_RESTORES_KEY, 0) + 1
        if (count >= 2) {
            revokePlayEntitlementIfGone(prefs)
            prefs.edit().remove(PLAY_EMPTY_RESTORES_KEY).apply()
            return true
        }
        prefs.edit().putInt(PLAY_EMPTY_RESTORES_KEY, count).apply()
        return false
    }

    /** RF-M22: Play respondió con la compra presente; el contador de vacíos vuelve a 0. */
    fun resetPlayEmptyRestores(prefs: SharedPreferences) {
        if (prefs.contains(PLAY_EMPTY_RESTORES_KEY))
            prefs.edit().remove(PLAY_EMPTY_RESTORES_KEY).apply()
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
     *
     * M4: esto era un AND entre claves de DOS ficheros de prefs con políticas de backup
     * opuestas (pro_trial_used en el local, sin respaldar; el resto en el principal, sí),
     * así que al reinstalar el regalo prometido se evaporaba y theme_before_lock se quedaba
     * huérfano. Con el trial de vuelta en el principal (C1) las cuatro claves viajan juntas
     * y el regalo sobrevive a la reinstalación.
     */
    fun trialGiftPending(prefs: SharedPreferences, now: Long = System.currentTimeMillis()): Boolean =
        prefs.getBoolean(TRIAL_USED_KEY, false) &&
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
        // CRÍTICO (auditoría dinero 17-07): un tema solo se HEREDA si venía de ANTES de que
        // existiera Pro. Un tema de pago puesto durante la prueba de 7 días o con Pro comprado
        // NO es herencia, es entitlement, y no puede quedarse gratis al caducar.
        //
        // El agujero: en instalación limpia, load() sale antes por biblioteca vacía y este
        // one-shot no se quema en el primer arranque; el usuario nuevo prueba los 7 días, elige
        // Aurora/AMOLED, mete un libro y en el siguiente arranque el one-shot se quemaba ya con
        // theme_mode="aurora" → tema de pago gratis para siempre (el 100% de instalaciones de
        // Play). El usuario legítimo de la 2.7 NO se ve afectado: tiene libros, así que su
        // primer onCreate quema el one-shot antes de poder siquiera activar la prueba.
        if (prefs.contains(TRIAL_EXPIRES_KEY) || prefs.getBoolean(PREF_KEY, false)) {
            prefs.edit().putBoolean(GRANDFATHER_DONE_KEY, true).apply()
            return
        }
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
        /** CRÍTICO (auditoría dinero 17-07): código REAL pero ya canjeado en el máximo de
         *  dispositivos (worker: 409 already_redeemed). Es un comprador legítimo que cambió de
         *  móvil, NO un código falso: mensaje propio y NO cuenta hacia el bloqueo de 24 h. */
        object CodeExhausted : RedeemResult()
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
            conn.setRequestProperty("User-Agent", com.lecturameter.APP_USER_AGENT)
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
                200 -> {
                    // MAYOR (auditoría dinero 17-07): un 200 NO basta para conceder Pro. Un portal
                    // cautivo, un interstitial de CDN/Cloudflare o un MITM en red hostil devuelven
                    // 200 con HTML y regalarían la app. Se exige el cuerpo JSON del worker
                    // ({"status":"ok"...}); si no cuadra, no es una respuesta suya y no se concede.
                    val bodyText = try {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } catch (e: Exception) { "" }
                    val ok = try {
                        org.json.JSONObject(bodyText).optString("status") == "ok"
                    } catch (e: Exception) { false }
                    if (ok) { markCodeRedeemed(prefs); RedeemResult.Success }
                    else RedeemResult.NetworkError(200)
                }
                // 409 = código válido que ya gastó sus dispositivos. NO es inválido y NO gasta
                // intento hacia el bloqueo: al comprador que cambia de móvil no se le acusa de
                // usar un código falso ni se le autobloquea 24 h.
                409 -> RedeemResult.CodeExhausted
                400, 404 -> { registerFailedAttempt(prefs, now); RedeemResult.InvalidCode }
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
