package com.lecturameter.utils

// ── Color de las portadas generadas en el dispositivo ────────────────────────
//
// Cuando un libro no tiene cubierta se dibuja una a partir del título y el autor. El color
// se deriva del libro, así que la portada de un mismo libro es SIEMPRE la misma, en la
// búsqueda, en la biblioteca y en el detalle, y no cambia al reinstalar.
//
// Nivel de pastel elegido por Víctor tras comparar tres (21-07-2026): P2 medio.
// P1 (S 0,20 L 0,74) se quedaba corto, dos tonos separados 20 grados se leían como el
// mismo color. P3 (S 0,44 L 0,50) distinguía muy bien pero destacaba más que las portadas
// reales que tuviera al lado.
//
// Todo aquí es Kotlin puro a propósito: los tests de esta máquina corren con JUnitCore
// contra un android.jar que es un stub donde cada método lanza "Stub!", así que no se
// puede usar androidx.core.graphics.ColorUtils ni android.graphics.Color.
object CoverPalette {

    const val SAT = 0.32f      // P2 medio
    const val LIGHT = 0.62f

    /** Semilla estable: el ISBN si lo hay, y si no el título normalizado. */
    fun seedFor(isbn: String?, title: String): Int {
        val base = isbn?.filter { it.isDigit() || it == 'X' || it == 'x' }
            ?.takeIf { it.isNotBlank() }
            ?: normaliza(title)
        var h = 0
        for (c in base) h = h * 31 + c.code
        return h
    }

    /**
     * Mezcla de avalancha estilo fmix32. **No se puede quitar**: con el hash en crudo,
     * `seed % 360` colisiona tanto que en una estantería de ocho libros salían tres
     * portadas del mismo tono seguidas (comprobado en el mockup).
     */
    fun mix32(v: Int): Int {
        var x = v
        x = x xor (x ushr 16); x *= 0x7feb352d.toInt()
        x = x xor (x ushr 15); x *= 0x846ca68b.toInt()
        x = x xor (x ushr 16)
        return x
    }

    /** Tono libre de 0 a 360, sin bandas por tema (esa era la queja de la ronda 1). */
    fun hueFor(seed: Int): Float {
        val m = mix32(seed).toLong() and 0xFFFFFFFFL
        var h = (m % 360L).toFloat()
        // La franja amarillo verdoso es ilegible con tinta blanca y turbia con la oscura.
        if (h in 70f..95f) h = (h + 30f) % 360f
        return h
    }

    /**
     * Colores de una portada generada. Devuelve ARGB.
     *
     * `inkClaro` e `inkOscuro` son las dos tintas del tema; se elige la que más contraste
     * dé contra el color de fondo. No vale fijar la tinta por tema: con luminosidades
     * pastel el blanco deja de leerse sobre los tonos claros.
     */
    fun colorsFor(isbn: String?, title: String, inkClaro: Int, inkOscuro: Int): Colors {
        val seed = seedFor(isbn, title)
        val hue = hueFor(seed)

        // Segundo eje, independiente del primero. Con el tono libre, ocho títulos reparten
        // ocho puntos al azar en 360 grados y de vez en cuando dos caen a menos de 10
        // grados. Estos escalones discretos hacen que esos dos se sigan distinguiendo.
        // Debe ser una mezcla APARTE: reaprovechando bits del mismo valor, dos tonos
        // vecinos arrastran también el mismo escalón y no sirve de nada.
        val m2 = mix32(seed xor 0x9E3779B8.toInt()).toLong() and 0xFFFFFFFFL
        var light = LIGHT + ((m2 % 3L).toInt() - 1) * 0.07f
        var sat = SAT + (((m2 ushr 7) % 3L).toInt() - 1) * 0.06f
        sat = sat.coerceIn(0.05f, 1f)
        light = light.coerceIn(0.10f, 0.90f)

        // Si ninguna de las dos tintas llega a 4,5:1 se empuja la luminosidad hacia el
        // extremo más cercano en pasos de 0,03 hasta que alguna cumpla. Afecta a pocos
        // casos (tonos verdes y amarillos en la franja media) y el desplazamiento típico
        // es de un solo paso, así que no se nota como incoherencia de paleta.
        var fondo = hslToArgb(hue, sat, light)
        var cClaro = contrast(fondo, inkClaro)
        var cOscuro = contrast(fondo, inkOscuro)
        var vueltas = 0
        while (maxOf(cClaro, cOscuro) < 4.5 && vueltas < 8) {
            light = if (light >= 0.55f) (light + 0.03f).coerceAtMost(0.90f)
                    else (light - 0.03f).coerceAtLeast(0.10f)
            fondo = hslToArgb(hue, sat, light)
            cClaro = contrast(fondo, inkClaro)
            cOscuro = contrast(fondo, inkOscuro)
            vueltas++
        }

        return Colors(
            background = fondo,
            ink = if (cOscuro >= cClaro) inkOscuro else inkClaro,
            spine = hslToArgb(hue, (sat + 0.08f).coerceAtMost(1f), light * 0.68f),
            contrast = maxOf(cClaro, cOscuro)
        )
    }

    data class Colors(val background: Int, val ink: Int, val spine: Int, val contrast: Double)

    // -- utilidades de color, puras ------------------------------------------

    fun hslToArgb(hDeg: Float, s: Float, l: Float): Int {
        val h = (((hDeg % 360f) + 360f) % 360f) / 360f
        val r: Float; val g: Float; val b: Float
        if (s == 0f) {
            r = l; g = l; b = l
        } else {
            val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            r = hue2rgb(p, q, h + 1f / 3f)
            g = hue2rgb(p, q, h)
            b = hue2rgb(p, q, h - 1f / 3f)
        }
        return (0xFF shl 24) or
            ((r * 255).toInt().coerceIn(0, 255) shl 16) or
            ((g * 255).toInt().coerceIn(0, 255) shl 8) or
            (b * 255).toInt().coerceIn(0, 255)
    }

    private fun hue2rgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0) t += 1f
        if (t > 1) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    /** Contraste WCAG entre dos colores ARGB. Devuelve de 1,0 a 21,0. */
    fun contrast(a: Int, b: Int): Double {
        val la = relLuminance(a)
        val lb = relLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun relLuminance(argb: Int): Double {
        fun canal(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * canal((argb shr 16) and 0xFF) +
               0.7152 * canal((argb shr 8) and 0xFF) +
               0.0722 * canal(argb and 0xFF)
    }

    private fun normaliza(s: String): String =
        s.lowercase()
            .replace(Regex("[áàäâ]"), "a").replace(Regex("[éèëê]"), "e")
            .replace(Regex("[íìïî]"), "i").replace(Regex("[óòöô]"), "o")
            .replace(Regex("[úùüû]"), "u").replace("ñ", "n")
            .replace(Regex("[^\\p{L}\\p{N} ]"), "")
            .replace(Regex("\\s+"), " ").trim()
}
