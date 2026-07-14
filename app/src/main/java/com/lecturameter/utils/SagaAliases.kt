package com.lecturameter.utils

// ── Alias de sagas ES ↔ EN ────────────────────────────────────────────────────
// Feedback 14-07: al cambiar de edición, el sufijo "(Saga, #N)" del título se
// conservaba en el idioma original (las APIs no traen sufijos de saga estilo
// Goodreads). Esta tabla curada traduce el NOMBRE de la saga al idioma de la
// edición destino — mismo patrón que KNOWN_SPANISH_EDITIONS en SearchRepository.
// Si la saga no está en la tabla, el sufijo se conserva tal cual (comportamiento
// anterior): nunca se inventa una traducción.

private val SAGA_ALIASES: List<Pair<String, String>> = listOf(
    // ES ↔ EN
    "La rueda del tiempo" to "The Wheel of Time",
    "Nacidos de la bruma" to "Mistborn",
    "El archivo de las tormentas" to "The Stormlight Archive",
    "Canción de hielo y fuego" to "A Song of Ice and Fire",
    "Crónica del asesino de reyes" to "The Kingkiller Chronicle",
    "El señor de los anillos" to "The Lord of the Rings",
    "Los juegos del hambre" to "The Hunger Games",
    "Trono de cristal" to "Throne of Glass",
    "Saga de Geralt de Rivia" to "The Witcher",
    "Fundación" to "Foundation",
    "Crepúsculo" to "Twilight",
    "Las crónicas de Narnia" to "The Chronicles of Narnia",
    "Percy Jackson y los dioses del Olimpo" to "Percy Jackson and the Olympians",
    "Mundodisco" to "Discworld",
    "La torre oscura" to "The Dark Tower",
    "El legado" to "The Inheritance Cycle",
    "El cementerio de los libros olvidados" to "The Cemetery of Forgotten Books",
    "Terramar" to "Earthsea",
    "Una corte de rosas y espinas" to "A Court of Thorns and Roses",
    "Empíreo" to "The Empyrean",
    "Los Bridgerton" to "Bridgertons",
)

private fun normalizeSaga(s: String): String =
    java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

/**
 * Resuelve el idioma efectivo de una edición para la traducción de sagas.
 * El código `language` puede ser "original" (p. ej. edición inglesa de una obra
 * en inglés) — en ese caso el idioma real solo está en `languageLabel`.
 * Devuelve "en", "es" o null (idioma sin traducción de sagas).
 */
fun sagaTargetLang(language: String?, languageLabel: String?): String? {
    val lang = language?.trim()?.lowercase().orEmpty()
    if (lang.startsWith("en")) return "en"
    if (lang == "es" || lang.startsWith("es-")) return "es"
    val label = normalizeSaga(languageLabel.orEmpty())
    if (label.startsWith("english") || label.startsWith("ingles")) return "en"
    if (label.startsWith("espanol") || label.startsWith("spanish") || label.startsWith("castellano")) return "es"
    return null
}

/**
 * Traduce el nombre de una saga al idioma de la edición destino ("es…" o "en…").
 * Devuelve el nombre sin tocar si el idioma no es ES/EN o la saga no está en la
 * tabla. Si el nombre ya está en el idioma destino, se devuelve tal cual.
 */
fun translateSagaName(name: String, targetLang: String?): String {
    if (name.isBlank() || targetLang.isNullOrBlank()) return name
    val lang = targetLang.trim().lowercase()
    val toEnglish = lang.startsWith("en")
    val toSpanish = lang.startsWith("es")
    if (!toEnglish && !toSpanish) return name
    val norm = normalizeSaga(name)
    for ((es, en) in SAGA_ALIASES) {
        if (norm == normalizeSaga(es) || norm == normalizeSaga(en)) {
            return if (toEnglish) en else es
        }
    }
    return name
}
