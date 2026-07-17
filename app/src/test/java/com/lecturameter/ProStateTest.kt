package com.lecturameter

// D-013 (16-07-2026): tests de ProState, el punto único de verdad del entitlement Pro.
// Cubren: estado por defecto, compra, trial de 7 días (activación, caducidad, un solo uso,
// días restantes y guarda contra retrasar el reloj), grandfathering del tema activo,
// themeAllowed, tope de ediciones, lockout del canje y el autoformato LM-XXXX-XXXX-XXXX.
//
// Nota de runner: requieren android.jar en el classpath (solo por las INTERFACES
// SharedPreferences/Editor, implementadas aquí en memoria; no se toca ningún stub).

import android.content.SharedPreferences
import com.lecturameter.utils.Pro
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** SharedPreferences en memoria: suficiente para ProState (String/Boolean/Long/Int). */
class FakePrefs : SharedPreferences {
    val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String, def: String?) = map[key] as? String ?: def
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, def: MutableSet<String>?) = map[key] as? MutableSet<String> ?: def
    override fun getInt(key: String, def: Int) = map[key] as? Int ?: def
    override fun getLong(key: String, def: Long) = map[key] as? Long ?: def
    override fun getFloat(key: String, def: Float) = map[key] as? Float ?: def
    override fun getBoolean(key: String, def: Boolean) = map[key] as? Boolean ?: def
    override fun contains(key: String) = key in map
    override fun edit(): SharedPreferences.Editor = FakeEditor(this)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val prefs: FakePrefs) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removals.add(key) }
        override fun clear() = apply { clearAll = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clearAll) prefs.map.clear()
            removals.forEach { prefs.map.remove(it) }
            pending.forEach { (k, v) -> if (v == null) prefs.map.remove(k) else prefs.map[k] = v }
        }
        private fun apply(block: () -> Unit): SharedPreferences.Editor { block(); return this }
    }
}

class ProStateTest {

    private val T0 = 1_752_600_000_000L // instante base arbitrario
    private fun days(n: Long) = TimeUnit.DAYS.toMillis(n)
    private fun hours(n: Long) = TimeUnit.HOURS.toMillis(n)

    // ── Estado por defecto y compra ──────────────────────────────────────────

    @Test
    fun `por defecto no hay Pro ni trial activo y el trial esta disponible`() {
        val prefs = FakePrefs()
        assertFalse(Pro.isPro(prefs, T0))
        assertFalse(Pro.trialActive(prefs, T0))
        assertTrue(Pro.trialAvailable(prefs, T0))
        assertEquals(0, Pro.trialDaysLeft(prefs, T0))
    }

    @Test
    fun `markPurchased desbloquea Pro permanente con fuente play`() {
        val prefs = FakePrefs()
        Pro.markPurchased(prefs)
        assertTrue(Pro.isPro(prefs, T0))
        assertTrue(Pro.isPro(prefs, T0 + days(3650))) // no caduca
        assertEquals("play", prefs.getString(Pro.SRC_KEY, null))
        assertFalse(Pro.trialAvailable(prefs, T0)) // comprado: la prueba ya no se ofrece
    }

    // ── Trial de 7 días ──────────────────────────────────────────────────────

    @Test
    fun `activateTrial da Pro durante 7 dias y caduca al octavo`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        assertTrue(Pro.isPro(prefs, T0))
        assertTrue(Pro.trialActive(prefs, T0 + days(6) + hours(23)))
        assertFalse(Pro.trialActive(prefs, T0 + days(7)))
        assertFalse(Pro.isPro(prefs, T0 + days(7)))
    }

    @Test
    fun `el trial es de un solo uso, caducado no vuelve a estar disponible`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        assertFalse(Pro.trialAvailable(prefs, T0 + days(1)))  // en curso
        assertFalse(Pro.trialAvailable(prefs, T0 + days(30))) // caducado
    }

    @Test
    fun `trialDaysLeft cuenta dias redondeando hacia arriba y 0 fuera de la ventana`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        assertEquals(7, Pro.trialDaysLeft(prefs, T0))
        assertEquals(7, Pro.trialDaysLeft(prefs, T0 + hours(1)))   // 6d23h → "7 días"
        assertEquals(1, Pro.trialDaysLeft(prefs, T0 + days(6) + hours(12)))
        assertEquals(0, Pro.trialDaysLeft(prefs, T0 + days(8)))
    }

    // ── M1: retrasar el reloj del sistema ────────────────────────────────────
    //
    // El test que había aquí ("retrasar el reloj ANTES del inicio del trial NO lo reactiva")
    // daba FALSA SEGURIDAD: pasaba, pero cubría el único caso que no hace nadie (retrasar
    // el reloj antes de empezar la prueba, cuando aún no tienes nada que estirar). El
    // agujero real estaba justo al lado y no lo tocaba ningún test: retrasar el reloj DENTRO
    // de la ventana. Estos tres lo cubren.

    @Test
    fun `retrasar el reloj DENTRO de la ventana del trial no regala tiempo`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        // Día 6 de la prueba: activa y legítima.
        assertTrue(Pro.trialActive(prefs, T0 + days(6)))
        // El usuario pone la fecha en el día 1. Antes esto daba Pro permanente: el día 1
        // sigue estando dentro de [inicio, fin) y la guarda por inicio ni se enteraba.
        // Ahora el reloj efectivo no baja del día 6.
        assertTrue(Pro.trialActive(prefs, T0 + days(1)))          // no se le corta a traición
        assertEquals(1, Pro.trialDaysLeft(prefs, T0 + days(1)))   // pero sigue siendo el día 6
        // Y con la fecha atrasada CONGELADA, el tiempo real sigue corriendo: dos días reales
        // después (fecha del sistema = día 3) la prueba está caducada igual.
        assertFalse(Pro.trialActive(prefs, T0 + days(3)))
        assertFalse(Pro.isPro(prefs, T0 + days(3)))
    }

    @Test
    fun `retrasar el reloj muchas veces no estira la prueba ni un dia`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        assertTrue(Pro.trialActive(prefs, T0 + days(6)))
        // Rebobinar al día 1 una y otra vez: cada intento se absorbe en el desfase y el
        // reloj efectivo se queda clavado en el día 6, sin bajar.
        repeat(5) { assertTrue(Pro.trialActive(prefs, T0 + days(1))) }
        // Un día real después (fecha del sistema en el día 2) la prueba está caducada:
        // rebobinar no le ha regalado ni un minuto.
        assertFalse(Pro.trialActive(prefs, T0 + days(2)))
    }

    @Test
    fun `retrasar el reloj antes del inicio del trial no reactiva ni regala nada`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        // Retrasar 30 días nada más empezar: el reloj efectivo se queda en el inicio, así
        // que la prueba sigue activa (no se le castiga por tener mal la hora)...
        assertTrue(Pro.trialActive(prefs, T0 - days(30)))
        // ...pero no ha ganado tiempo: a los 7 días reales caduca igual.
        assertFalse(Pro.trialActive(prefs, T0 - days(23)))
    }

    @Test
    fun `trial legacy sin clave de inicio sigue funcionando (compat hacia atras)`() {
        val prefs = FakePrefs()
        // Instalación que activó la prueba con el código anterior: solo expires + used
        prefs.map[Pro.TRIAL_EXPIRES_KEY] = T0 + days(5)
        prefs.map[Pro.TRIAL_USED_KEY] = true
        assertTrue(Pro.trialActive(prefs, T0))               // dentro de la ventana derivada
        assertFalse(Pro.trialActive(prefs, T0 + days(5)))    // caduca igual
        assertFalse(Pro.trialActive(prefs, T0 - days(3)))    // la guarda derivada también corta
    }

    // ── C1 / M4: reinstalación y Auto Backup ─────────────────────────────────
    //
    // Ningún test cubría la reinstalación, y de ahí salieron C1 y M4. El Auto Backup de
    // Android respalda EL FICHERO "lecturameter.xml" ENTERO y lo restaura al reinstalar
    // desde Play. Aquí se simula exactamente eso: se copia el fichero respaldado a una
    // instalación nueva. Todo lo que NO esté en ese fichero nace con su valor por defecto.
    //
    // La regresión que cazan: mientras pro_trial_used vivía en un fichero excluido del
    // backup, reinstalar lo devolvía a false y "Empezar prueba" volvía a salir. Gastar la
    // prueba, desinstalar, reinstalar y repetir era gratis y para siempre, con la
    // biblioteca intacta porque lecturameter.xml sí se restauraba.

    /** Simula reinstalar desde Play: instalación nueva con lecturameter.xml restaurado. */
    private fun reinstallWithAutoBackup(old: FakePrefs): FakePrefs {
        val restored = FakePrefs()
        restored.map.putAll(old.map)   // el backup se lleva el fichero principal entero
        return restored
    }

    @Test
    fun `reinstalar tras gastar la prueba NO la vuelve a ofrecer`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        assertFalse(Pro.trialAvailable(prefs, T0 + days(30)))   // gastada y caducada

        val fresh = reinstallWithAutoBackup(prefs)
        assertFalse(Pro.trialAvailable(fresh, T0 + days(30)))   // y sigue gastada
        assertFalse(Pro.isPro(fresh, T0 + days(30)))
    }

    @Test
    fun `la marca de prueba gastada viaja en el fichero respaldado`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        // Explícito: si alguien vuelve a sacar estas claves del fichero respaldado, este
        // test cae. La marca de "ya lo has gastado" SIEMPRE quiere backup.
        assertTrue(prefs.map.containsKey(Pro.TRIAL_USED_KEY))
        assertTrue(prefs.map.containsKey(Pro.TRIAL_EXPIRES_KEY))
        assertTrue(prefs.map.containsKey(Pro.TRIAL_STARTED_KEY))
    }

    @Test
    fun `reinstalar en mitad de la prueba la conserva, no la reinicia`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        val fresh = reinstallWithAutoBackup(prefs)
        assertTrue(Pro.trialActive(fresh, T0 + days(3)))        // sigue el mismo trial
        assertFalse(Pro.trialAvailable(fresh, T0 + days(3)))    // no se puede reiniciar
        assertFalse(Pro.trialActive(fresh, T0 + days(7)))       // y caduca cuando tocaba
    }

    @Test
    fun `quien compro Pro lo conserva al reinstalar`() {
        val prefs = FakePrefs()
        Pro.markPurchased(prefs)
        val fresh = reinstallWithAutoBackup(prefs)
        assertTrue(Pro.isPro(fresh, T0))
        assertEquals("play", fresh.getString(Pro.SRC_KEY, null))
    }

    // M4: trialGiftPending era un AND entre claves de DOS ficheros con políticas de backup
    // opuestas, así que al reinstalar el regalo prometido se evaporaba. Con el trial de
    // vuelta en el principal, las cuatro claves viajan juntas.
    @Test
    fun `el regalo del trial sobrevive a la reinstalacion`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        Pro.rememberThemeBeforeLock(prefs, ThemeMode.CUERO)
        assertTrue(Pro.trialGiftPending(prefs, T0 + days(8)))   // pendiente antes de reinstalar

        val fresh = reinstallWithAutoBackup(prefs)
        assertTrue(Pro.trialGiftPending(fresh, T0 + days(8)))   // y sigue pendiente después
        // theme_before_lock no se queda huérfano: sigue apuntando al tema preseleccionado.
        assertEquals("cuero", fresh.getString(Pro.THEME_BEFORE_LOCK_KEY, null))
    }

    @Test
    fun `el tema ya regalado sigue siendo suyo tras reinstalar`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        Pro.grantTrialGiftTheme(prefs, ThemeMode.AURORA)
        val fresh = reinstallWithAutoBackup(prefs)
        assertTrue(Pro.themeAllowed(fresh, ThemeMode.AURORA))
        assertFalse(Pro.trialGiftPending(fresh, T0 + days(8)))  // no se le vuelve a preguntar
    }

    @Test
    fun `el tema heredado de la 2_7 sigue siendo suyo tras reinstalar`() {
        val prefs = FakePrefs()
        prefs.map["theme_mode"] = "aurora"
        Pro.grandfatherCurrentThemeIfNeeded(prefs)
        val fresh = reinstallWithAutoBackup(prefs)
        assertTrue(Pro.themeAllowed(fresh, ThemeMode.AURORA))
    }

    // ── Temas de pago y grandfathering ───────────────────────────────────────

    @Test
    fun `gratis puede usar los temas libres pero no los de pago`() {
        val prefs = FakePrefs()
        assertTrue(Pro.themeAllowed(prefs, ThemeMode.DARK))
        assertTrue(Pro.themeAllowed(prefs, ThemeMode.LIGHT))
        assertFalse(Pro.themeAllowed(prefs, ThemeMode.CUERO))
        assertFalse(Pro.themeAllowed(prefs, ThemeMode.AURORA))
        assertFalse(Pro.themeAllowed(prefs, ThemeMode.AMOLED))
    }

    @Test
    fun `Pro puede usar todos los temas y al caducar el trial los pierde`() {
        val prefs = FakePrefs()
        // themeAllowed no tiene parámetro de tiempo y usa el reloj real: activamos el
        // trial también con el reloj real (T0 es un instante arbitrario del pasado y
        // rompería la comprobación si se usara aquí).
        Pro.activateTrial(prefs)
        assertTrue(Pro.themeAllowed(prefs, ThemeMode.CUERO))
        // Simulamos la caducidad moviendo la ventana del trial al pasado
        val now = System.currentTimeMillis()
        prefs.map[Pro.TRIAL_STARTED_KEY] = now - days(30)
        prefs.map[Pro.TRIAL_EXPIRES_KEY] = now - days(23)
        assertFalse(Pro.themeAllowed(prefs, ThemeMode.CUERO))
        assertTrue(Pro.themeAllowed(prefs, ThemeMode.DARK))
    }

    @Test
    fun `grandfathering respeta el Aurora o AMOLED activo al actualizar`() {
        val prefs = FakePrefs()
        prefs.map["theme_mode"] = "aurora"
        Pro.grandfatherCurrentThemeIfNeeded(prefs)
        assertTrue(Pro.themeAllowed(prefs, ThemeMode.AURORA))   // el suyo, para siempre
        assertFalse(Pro.themeAllowed(prefs, ThemeMode.AMOLED))  // los demás de pago, no
        assertFalse(Pro.themeAllowed(prefs, ThemeMode.CUERO))
    }

    @Test
    fun `el grandfathering es one-shot y no se regana cambiando el tema despues`() {
        val prefs = FakePrefs()
        prefs.map["theme_mode"] = "dark"
        Pro.grandfatherCurrentThemeIfNeeded(prefs)
        // Más tarde pone amoled en prefs (p. ej. durante un trial) y se relanza el one-shot
        prefs.map["theme_mode"] = "amoled"
        Pro.grandfatherCurrentThemeIfNeeded(prefs)
        assertFalse(Pro.themeAllowed(prefs, ThemeMode.AMOLED))
    }

    @Test
    fun `Cuero no se hereda por grandfathering aunque estuviera en prefs`() {
        val prefs = FakePrefs()
        prefs.map["theme_mode"] = "cuero"
        Pro.grandfatherCurrentThemeIfNeeded(prefs)
        assertFalse(Pro.themeAllowed(prefs, ThemeMode.CUERO))
    }

    // CRÍTICO (auditoría dinero 17-07): un tema de pago elegido DURANTE la prueba de 7 días
    // NO es herencia de la 2.7, es entitlement, y no puede quedarse gratis al caducar. En
    // instalación limpia el one-shot no se quemaba en vacío y se quemaba luego con theme_mode
    // ya en "aurora" → tema de pago gratis para siempre para el 100% de instalaciones de Play.
    @Test
    fun `un tema de pago elegido durante la prueba NO se hereda al caducar`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)                 // hay historial de prueba
        prefs.map["theme_mode"] = "aurora"           // permitido mientras el trial vive
        Pro.grandfatherCurrentThemeIfNeeded(prefs)   // primer load() con libros ya guardados
        assertNull(prefs.getString(Pro.GRANDFATHER_KEY, null))
    }

    // El comprador de Pro tampoco hereda por grandfathering un tema que puso teniendo Pro.
    @Test
    fun `con Pro comprado el grandfathering no concede ningun tema heredado`() {
        val prefs = FakePrefs()
        Pro.markPurchased(prefs)
        prefs.map["theme_mode"] = "amoled"
        Pro.grandfatherCurrentThemeIfNeeded(prefs)
        assertNull(prefs.getString(Pro.GRANDFATHER_KEY, null))
    }

    // CRÍTICO (auditoría dinero 17-07): un reembolso de Play (compra que desaparece de la
    // cuenta) retira el Pro COMPRADO, pero nunca el de código.
    @Test
    fun `revoke retira el Pro comprado pero respeta el de codigo`() {
        val comprado = FakePrefs()
        Pro.markPurchased(comprado)
        Pro.revokePlayEntitlementIfGone(comprado)
        assertFalse(Pro.isPro(comprado))            // reembolso: fuera Pro

        val codigo = FakePrefs()
        codigo.map[Pro.PREF_KEY] = true
        codigo.map[Pro.SRC_KEY] = "code"            // canjeó un código, no compró en Play
        Pro.revokePlayEntitlementIfGone(codigo)
        assertTrue(Pro.isPro(codigo))               // NO se toca: no tiene compra en Play
    }

    // ── P-032: regalo de un tema al acabar la prueba ─────────────────────────

    @Test
    fun `el regalo no se ofrece sin prueba gastada ni con la prueba en curso`() {
        val prefs = FakePrefs()
        assertFalse(Pro.trialGiftPending(prefs, T0))          // nunca probó: nada que regalar
        Pro.activateTrial(prefs, T0)
        assertFalse(Pro.trialGiftPending(prefs, T0 + days(1))) // en curso: aún no toca
    }

    @Test
    fun `al caducar la prueba el regalo queda pendiente y se cierra al concederlo`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        assertTrue(Pro.trialGiftPending(prefs, T0 + days(8)))
        Pro.grantTrialGiftTheme(prefs, ThemeMode.CUERO)
        assertFalse(Pro.trialGiftPending(prefs, T0 + days(8))) // no se vuelve a preguntar
    }

    @Test
    fun `a quien compra Pro no se le ofrece el regalo porque ya tiene los tres temas`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        Pro.markPurchased(prefs)
        assertFalse(Pro.trialGiftPending(prefs, T0 + days(8)))
    }

    @Test
    fun `el tema regalado se puede usar para siempre y los otros de pago siguen bloqueados`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        Pro.grantTrialGiftTheme(prefs, ThemeMode.CUERO)
        assertTrue(Pro.themeAllowed(prefs, ThemeMode.CUERO))
        assertFalse(Pro.themeAllowed(prefs, ThemeMode.AURORA))
        assertFalse(Pro.themeAllowed(prefs, ThemeMode.AMOLED))
    }

    // Mina 2 del mockup: si el regalo escribiera en pro_grandfathered_theme, quien viniera
    // de la 2.7 con Aurora heredado y eligiera Cuero PERDERÍA el Aurora. Son claves aparte.
    @Test
    fun `el regalo NO pisa el tema heredado de la 2_7 y se conservan los dos`() {
        val prefs = FakePrefs()
        prefs.map["theme_mode"] = "aurora"
        Pro.grandfatherCurrentThemeIfNeeded(prefs)   // usuario 2.7 con Aurora
        Pro.activateTrial(prefs, T0)
        Pro.grantTrialGiftTheme(prefs, ThemeMode.CUERO)
        assertTrue(Pro.themeAllowed(prefs, ThemeMode.AURORA))  // el heredado NO se pierde
        assertTrue(Pro.themeAllowed(prefs, ThemeMode.CUERO))   // y el regalado también vale
        assertFalse(Pro.themeAllowed(prefs, ThemeMode.AMOLED)) // el tercero sigue de pago
        assertEquals("aurora", prefs.getString(Pro.GRANDFATHER_KEY, null))
        assertEquals("cuero", prefs.getString(Pro.TRIAL_GIFT_KEY, null))
    }

    @Test
    fun `no se puede regalar un tema gratis`() {
        val prefs = FakePrefs()
        Pro.grantTrialGiftTheme(prefs, ThemeMode.DARK)
        assertNull(prefs.getString(Pro.TRIAL_GIFT_KEY, null))
        assertFalse(prefs.getBoolean(Pro.TRIAL_GIFT_DONE_KEY, false))
    }

    // ── B-041: no perder el tema al caducar la prueba ────────────────────────

    @Test
    fun `el tema apagado al caducar la prueba vuelve al comprar Pro`() {
        val prefs = FakePrefs()
        // Estaba en Cuero durante el trial y el trial caducó: la app lo apaga y lo recuerda
        Pro.rememberThemeBeforeLock(prefs, ThemeMode.CUERO)
        assertNull(Pro.reclaimThemeAfterUnlock(prefs))   // sin derecho todavía: no se devuelve
        Pro.markPurchased(prefs)
        assertEquals(ThemeMode.CUERO, Pro.reclaimThemeAfterUnlock(prefs))
        // Se consume: no vuelve a pedir restaurar una y otra vez
        assertNull(Pro.reclaimThemeAfterUnlock(prefs))
    }

    // Elegir el regalo cierra la pregunta "qué tema quieres": el apagado deja de estar
    // pendiente de restaurar. Si no, quien tenía Cuero en la prueba, elige Aurora de regalo
    // y compra Pro meses después se encontraría con que la app le cambia sola a Cuero.
    @Test
    fun `el regalo cierra la restauracion y comprar Pro despues NO pisa el tema elegido`() {
        val prefs = FakePrefs()
        Pro.rememberThemeBeforeLock(prefs, ThemeMode.CUERO)   // tenía Cuero al caducar
        Pro.grantTrialGiftTheme(prefs, ThemeMode.AURORA)      // pero eligió Aurora
        assertNull(prefs.getString(Pro.THEME_BEFORE_LOCK_KEY, null))
        Pro.markPurchased(prefs)
        assertNull(Pro.reclaimThemeAfterUnlock(prefs))        // Cuero NO resucita
    }

    @Test
    fun `solo se recuerda el primer tema apagado y nunca uno gratis`() {
        val prefs = FakePrefs()
        Pro.rememberThemeBeforeLock(prefs, ThemeMode.DARK)     // gratis: no se guarda
        assertNull(prefs.getString(Pro.THEME_BEFORE_LOCK_KEY, null))
        Pro.rememberThemeBeforeLock(prefs, ThemeMode.CUERO)
        Pro.rememberThemeBeforeLock(prefs, ThemeMode.AURORA)   // el segundo NO pisa al primero
        assertEquals("cuero", prefs.getString(Pro.THEME_BEFORE_LOCK_KEY, null))
    }

    // ── Tope de ediciones (P-031) ────────────────────────────────────────────

    // Decisión Víctor 17-07: gratis base + 2 (3 totales), Pro sin límite.
    // Los asserts van contra literales A PROPÓSITO: comparar la constante consigo misma
    // (como hacía la versión vieja) pasa con cualquier valor y no protege el gate.
    @Test
    fun `tope de ediciones 3 gratis y sin limite en Pro`() {
        val prefs = FakePrefs()
        assertEquals(3, Pro.editionLimit(prefs))
        Pro.markPurchased(prefs)
        assertEquals(Int.MAX_VALUE, Pro.editionLimit(prefs))
    }

    // ── Canje: lockout local ─────────────────────────────────────────────────

    @Test
    fun `con lockout vigente el canje devuelve TooManyAttempts sin tocar la red`() {
        val prefs = FakePrefs()
        prefs.map["pro_code_lockout_until"] = System.currentTimeMillis() + hours(1)
        val result = Pro.redeemCode(prefs, "LM-AAAA-BBBB-CCCC", "device1")
        assertTrue(result is Pro.RedeemResult.TooManyAttempts)
        assertFalse(Pro.isPro(prefs))
    }

    // ── Autoformato del código LM (UI del canje) ─────────────────────────────

    @Test
    fun `formatLmCode agrupa la entrada en LM-XXXX-XXXX-XXXX`() {
        assertEquals("LM-ABCD-2345-WXYZ", formatLmCode("LM-abcd2345wxyz"))
        assertEquals("LM-ABCD-2345-WXYZ", formatLmCode("lm abcd 2345 wxyz"))
        assertEquals("LM-ABCD", formatLmCode("LM-ABCD"))
        assertEquals("LM-", formatLmCode(""))
    }

    @Test
    fun `formatLmCode descarta el exceso y el resultado valida contra el regex`() {
        val formatted = formatLmCode("LM-ABCD2345WXYZEXTRA")
        assertEquals(17, formatted.length)
        assertTrue(LM_CODE_REGEX.matches(formatted))
        assertFalse(LM_CODE_REGEX.matches("LM-ABCD-2345"))
        assertFalse(LM_CODE_REGEX.matches("AU-ABCD-2345-WXYZ"))
    }
}
