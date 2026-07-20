package com.lecturameter.utils

object LanguageHelper {

    /** Idioma efectivo de la app.
     *  Prioridad: la preferencia explícita del usuario (selector de Ajustes) y, si nunca
     *  ha elegido, el idioma del SISTEMA reducido a lo que la app traduce.
     *
     *  Centraliza el default que antes estaba hardcodeado a "es" en 7 sitios distintos
     *  (Activity, ViewModel, worker de recaps, TimerService, config del widget, tutorial
     *  y appLocalizedContext). Si cada uno mantiene su propio default, un widget o una
     *  notificación disparados ANTES del primer arranque saldrían en español en un
     *  dispositivo en francés. */
    fun resolveLanguage(prefs: android.content.SharedPreferences): String =
        prefs.getString("app_language", null) ?: systemLanguage()

    /** Idioma del sistema acotado a los idiomas con recursos: "es" si el dispositivo está
     *  en español, "en" en cualquier otro caso (solo existen values/ y values-en/).
     *
     *  Se lee de Resources.getSystem() y NO de Locale.getDefault() a propósito:
     *  attachBaseContext hace Locale.setDefault() con el idioma ya resuelto de la app, así
     *  que el default devolvería nuestra propia respuesta en vez del idioma real del
     *  dispositivo. */
    fun systemLanguage(): String {
        val locales = android.content.res.Resources.getSystem().configuration.locales
        val lang = if (locales.isEmpty) java.util.Locale.getDefault().language
                   else locales.get(0).language
        return if (lang == "es") "es" else "en"
    }

    fun getLanguageDisplay(code: String): String = when (code) {
        "es" -> "Español"
        "ca" -> "Catalán (CAT)"
        "eu" -> "Euskera (EUS)"
        "gl" -> "Gallego (GAL)"
        "va" -> "Valenciano (VAL)"
        "en" -> "English"
        "fr" -> "Français"
        "de" -> "Deutsch"
        "it" -> "Italiano"
        "pt" -> "Português"
        "ja" -> "日本語 (JPN)"
        "zh" -> "中文 (CHN)"
        "ru" -> "Русский (RUS)"
        "ar" -> "العربية (ARA)"
        else -> code.uppercase()
    }

    fun getLanguageFlag(code: String): String = when (code) {
        "es", "ca", "eu", "gl", "va" -> "🇪🇸"
        "en" -> "🇬🇧"
        "fr" -> "🇫🇷"
        "de" -> "🇩🇪"
        "it" -> "🇮🇹"
        "pt" -> "🇵🇹"
        "ja" -> "🇯🇵"
        "zh" -> "🇨🇳"
        "ru" -> "🇷🇺"
        "ar" -> "🇸🇦"
        else -> "🌐"
    }
}
