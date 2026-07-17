package com.lecturameter.utils

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Matemática de contraste WCAG, sobre enteros 0xRRGGBB y sin depender de Compose.
 *
 * Vive aquí y no junto a los colores del tema por dos motivos. Uno, los tests unitarios del
 * refac corren con JUnitCore y android.jar (ver run_tests.ps1, gradle#12660), sin Compose en
 * el classpath: si `Color` apareciera en estas firmas no se podrían probar. Y dos, esto es
 * aritmética pura, así que se puede clavar con tests de verdad en vez de con la vista.
 *
 * El porqué: esta app tiene cinco temas, uno de ellos papel crema y otro negro puro. Elegir
 * un color mirándolo sobre uno de los dos y dar por hecho el otro ya ha fallado tres veces
 * (el heatmap invisible en Claro, el carril del widget, y el Wrapped entero con B-037). El
 * ojo no sirve para esto; el número sí.
 */
object ContrastUtils {

    /** Mínimo WCAG AA para texto normal. */
    const val AA_TEXT = 4.5f
    /** Mínimo WCAG AA para texto grande (>=18pt, o >=14pt en negrita) y para gráficos. */
    const val AA_LARGE = 3.0f

    private fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun red(rgb: Int) = (rgb shr 16) and 0xFF
    fun green(rgb: Int) = (rgb shr 8) and 0xFF
    fun blue(rgb: Int) = rgb and 0xFF
    fun rgb(r: Int, g: Int, b: Int) =
        (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Luminancia relativa WCAG de un color OPACO. */
    fun luminance(rgb: Int): Double =
        0.2126 * channel(red(rgb)) + 0.7152 * channel(green(rgb)) + 0.0722 * channel(blue(rgb))

    /** Ratio de contraste WCAG entre dos colores OPACOS: de 1:1 (idénticos) a 21:1. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    // Se REDONDEA, no se trunca: truncar sesga todos los canales hacia el negro y desplaza el
    // color medio paso por canal. Es imperceptible a la vista, pero con el cian rondando justo
    // el 4,5 la diferencia decide si un color pasa o no.
    private fun Float.round8() = (this + 0.5f).toInt().coerceIn(0, 255)

    /** Mezcla [rgb] con negro en [factor] (0 = igual, 1 = negro). Conserva el matiz. */
    fun darken(rgb: Int, factor: Float): Int {
        val k = 1f - factor
        return rgb((red(rgb) * k).round8(), (green(rgb) * k).round8(), (blue(rgb) * k).round8())
    }

    /** Mezcla [rgb] con blanco en [factor] (0 = igual, 1 = blanco). Conserva el matiz. */
    fun lighten(rgb: Int, factor: Float): Int = rgb(
        (red(rgb) + (255 - red(rgb)) * factor).round8(),
        (green(rgb) + (255 - green(rgb)) * factor).round8(),
        (blue(rgb) + (255 - blue(rgb)) * factor).round8()
    )

    /** Aplana [fg] con opacidad [alpha] sobre [bg] opaco. Medir el contraste de un color con
     *  alfa sin aplanarlo antes da un número que no existe en pantalla. */
    fun flatten(fg: Int, alpha: Float, bg: Int): Int = rgb(
        (red(fg) * alpha + red(bg) * (1 - alpha)).round8(),
        (green(fg) * alpha + green(bg) * (1 - alpha)).round8(),
        (blue(fg) * alpha + blue(bg) * (1 - alpha)).round8()
    )

    /** El tinte más fuerte que usa una pill del Wrapped: sirve de peor caso al medir. */
    const val PILL_ALPHA = 0.15f

    /**
     * Color de texto legible para una pill teñida con el propio [color], sobre el fondo [bg]
     * del tema. Conserva el matiz y mueve solo la luminosidad, que es lo que mantiene el
     * significado del color.
     *
     * Corrige en las dos direcciones: sobre papel oscurece, sobre negro aclara. Devuelve el
     * color intacto si ya pasa. [fallback] es la salida si ni el negro ni el blanco llegan.
     */
    fun inkFor(color: Int, bg: Int, isDark: Boolean, fallback: Int, target: Float = AA_TEXT): Int {
        fun pillOf(c: Int) = flatten(c, PILL_ALPHA, bg)
        if (contrast(color, pillOf(color)) >= target) return color
        var f = 0f
        while (f <= 0.98f) {
            val cand = if (isDark) lighten(color, f) else darken(color, f)
            if (contrast(cand, pillOf(cand)) >= target) return cand
            f += 0.01f
        }
        return fallback
    }

    /** Punto de partida al oscurecer una losa. Con el acento del Claro cae justo aquí, que es
     *  lo que reproduce el mockup aprobado por Víctor (#284938 → #1A2F24). */
    const val SLAB_DARKEN = 0.38f
    private const val WHITE = 0xFFFFFF

    /**
     * Los dos stops de una losa a partir de [color]. La losa lleva SIEMPRE texto blanco, así
     * que se oscurece hasta medirlo: un factor fijo vale para los cinco acentos pero se rompe
     * con los colores muy luminosos (el oro se quedaba en 4,22:1 y el cian en 4,46:1).
     */
    fun slabStops(color: Int, target: Float = AA_TEXT): Pair<Int, Int> {
        var f = SLAB_DARKEN
        while (f < 0.90f && contrast(WHITE, darken(color, f)) < target) f += 0.01f
        return darken(color, f) to darken(color, min(f + 0.22f, 0.95f))
    }

    /** Texto secundario sobre la losa de [color]: el propio color aclarado hacia el blanco,
     *  para que la losa conserve su tinte en vez de ser blanco sobre color a secas. */
    fun onSlabMutedFor(color: Int, target: Float = AA_TEXT): Int {
        val top = slabStops(color, target).first
        var f = 0.85f
        while (f < 0.99f && contrast(lighten(color, f), top) < target) f += 0.01f
        return lighten(color, f)
    }
}
