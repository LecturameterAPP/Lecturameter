package com.lecturameter.utils

object LanguageHelper {
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
