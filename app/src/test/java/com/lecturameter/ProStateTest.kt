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

    @Test
    fun `retrasar el reloj del sistema antes del inicio del trial NO lo reactiva`() {
        val prefs = FakePrefs()
        Pro.activateTrial(prefs, T0)
        // El usuario pone el reloj 30 días atrás: la caducidad absoluta quedaría
        // "en el futuro" para siempre; la guarda por inicio lo corta.
        assertFalse(Pro.trialActive(prefs, T0 - days(30)))
        assertFalse(Pro.isPro(prefs, T0 - days(30)))
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

    // ── Tope de ediciones (P-031) ────────────────────────────────────────────

    @Test
    fun `tope de ediciones 2 gratis y 5 Pro`() {
        val prefs = FakePrefs()
        assertEquals(Pro.FREE_EDITIONS, Pro.editionLimit(prefs))
        Pro.markPurchased(prefs)
        assertEquals(Pro.PRO_EDITIONS, Pro.editionLimit(prefs))
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
