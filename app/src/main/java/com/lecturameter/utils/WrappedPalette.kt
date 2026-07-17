package com.lecturameter.utils

/**
 * Las losas del Wrapped: una tabla, no un color.
 *
 * # Por qué existe este fichero
 *
 * El Wrapped se diseñó cuando los cinco temas eran oscuros. Cada slide tenía SU losa (índigo el
 * año, violeta profundo las horas, pizarra la gráfica, océano la franja) y los seis números
 * protagonistas iban en degradado. Era un degradado navy OPACO y FIJO, así que cuando llegó el
 * tema Claro "Papel" (18-07) esas losas se quedaron de noche cerrada sobre el crema: eso fue
 * B-037.
 *
 * El arreglo de B-037 se pasó de frenada. Aplanó los CINCO temas: las siete losas temáticas
 * pasaron a ser el mismo `slabOf(acento)` y los números protagonistas perdieron el degradado y se
 * quedaron en blanco plano. O sea que para arreglar el papel se rompió el Oscuro, donde no había
 * nada que arreglar, y encima el Wrapped es la pantalla que la gente comparte. Va contra la regla
 * de oro del proyecto: ninguna funcionalidad desaparece, se expande, nunca se sustituye.
 *
 * # Qué hace
 *
 * Dos caminos, a propósito:
 *
 *  1. **Oscuro va por tabla LITERAL** ([DARK_SLABS], [DARK_HEROES]), copiada de de3180f, que es
 *     el fichero de antes de que B-037 lo tocara. No se deriva de nada ni se aproxima con un
 *     modelo: se restaura. El Oscuro tiene que quedar como estaba, y la única forma de
 *     garantizarlo es no calcularlo.
 *  2. **Los otros cuatro temas se derivan** de sus propios anclajes, con [slab]. Cada slide
 *     conserva identidad propia en vez de ser la misma losa doce veces.
 *
 * # Los tres ejes de la derivación, y por qué son tres
 *
 * Solo Aurora tiene DOS colores de verdad en su propio fondo (teal ↔ morado), así que solo Aurora
 * puede dar variedad de MATIZ sin meter un color ajeno. Cuero (oro), AMOLED (gris) y Claro (verde
 * salvia) son monocromos: con un solo matiz, cualquier variedad de color pasa por importar un
 * color de fuera, y meter azul en el Cuero es exactamente lo que el Bloque 2 se pasó una sesión
 * entera quitando. Así que la variedad se saca de donde sí la hay:
 *
 *  - [Slot.mix]: recorre las dos anclas del tema. En Aurora es un recorrido de matiz real
 *    (morado → teal, los dos colores de su propio fondo). En los monocromos la segunda ancla es
 *    la versión CENIZA de la primera ([ash]): mismo matiz, menos croma. O sea que este eje da
 *    saturación, no color nuevo. En AMOLED las dos anclas son el mismo gris y el eje no existe.
 *  - [Slot.depth]: posición en la banda MEDIDA del tema, de -1 (lo más honda que puede ir sin
 *    despegarse menos de 3:1 del fondo) a +1 (lo más clara que puede ir sin que el texto blanco
 *    baje de 4,5:1). El 0 es `slabOf(acento)` a secas, o sea la losa que Víctor aprobó en el
 *    mockup de B-037: la del año no se mueve de ahí en ningún tema.
 *  - [Slot.spread] y [Slot.flip]: distancia entre los dos stops y orden de los stops. Una losa
 *    plana y una dramática se distinguen aunque sean del mismo color, y darle la vuelta cambia
 *    de sitio la esquina clara. Esto no es un truco nuevo: la losa de género del Oscuro era
 *    literalmente la del año dada la vuelta (#1E1B4B→#312E81 contra #312E81→#1E1B4B).
 *
 * # La limitación, sin disimular
 *
 * En AMOLED el fondo es negro puro y el acento es un gris neutro. Si se exige a la vez que el
 * texto blanco pase 4,5:1 y que la losa se despegue 3:1 del negro, la banda entera son 28 niveles
 * de gris (#5A5A5A..#767676). Las doce losas caben ahí, pero la diferencia máxima entre dos de
 * ellas es de unos 10 de deltaE: se nota, y poco más. No hay forma de arreglarlo sin traicionar
 * al tema, que es negro y gris por definición. En Cuero la banda también es estrecha, porque el
 * fondo ya es marrón y el acento es oro: el tema entero vive en un corredor de luminancia corto.
 * Aurora y Claro sí tienen sitio de sobra.
 *
 * Todo lo que se afirma aquí está medido en `WrappedPaletteTest`, no mirado.
 */
object WrappedPalette {

    /** Losa: el texto blanco encima tiene que pasar AA. */
    private const val WHITE = 0xFFFFFF
    /** Lo más honda que se deja ir una losa aunque el fondo permita más: pasado este punto deja
     *  de leerse como una losa y pasa a ser un agujero. */
    private const val MAX_SINK = 0.55f

    // ── Los temas ────────────────────────────────────────────────────────────────
    // Los hexes están duplicados desde MainActivity.kt a propósito: esta capa es aritmética pura
    // y no puede importar Compose (los tests corren con JUnitCore y sin Compose en el classpath,
    // ver run_tests.ps1 y gradle#12660). Para que la duplicación no se pudra en silencio,
    // `WrappedColorLintTest` LEE los dos fuentes y compara los valores.

    /**
     * Las dos anclas del recorrido de cada tema, y el fondo contra el que se mide la banda.
     *
     * Aurora es el único con dos anclas de matiz distinto, y no son inventadas: son los dos
     * colores de su propio fondo (`BgDarkA` teal → `BgDeepA` morado), que además ya viven juntos
     * en su divisor. El resto lleva su acento y la ceniza de su acento.
     */
    enum class ThemeKey(val anchorA: Int, val bg: Int, private val hueB: Int? = null) {
        /** Oscuro no deriva nada: va por tabla. El ancla está por completitud. */
        DARK(0x6366F1, 0x0F172A, 0x8B5CF6),
        /** El único con dos matices propios: los dos extremos de su fondo (BgDarkA → BgDeepA). */
        AURORA(0xB794F6, 0x03343A, 0x5EEAD4),
        /** Monocromo: oro → oro viejo. */
        CUERO(0xD9AC5C, 0x281B10),
        /** Monocromo y además ya neutro: las dos anclas son la misma y el eje de matiz no existe. */
        AMOLED(0xA1A1AA, 0x000000),
        /** Monocromo: salvia → salvia apagada. */
        LIGHT(0x41755A, 0xFAF8F2);

        /** La segunda ancla: el segundo matiz si el tema tiene uno de verdad, y si no la ceniza
         *  del acento. Se calcula y no se escribe a mano para que no se despegue de [anchorA]. */
        val anchorB: Int get() = hueB ?: ash(anchorA)
    }

    /**
     * Las doce losas del Wrapped, cada una con su sitio en el espacio (mix, depth, spread, flip).
     *
     * [tint] distinto de null = losa SEMÁNTICA: su color significa algo (oro = mejor puntuado,
     * verde = más rápido, rojo = favoritos), así que no recorre el tema ni se hunde. Es la misma
     * en los cuatro temas derivados, que es justo lo que se quiere: el oro tiene que seguir
     * siendo oro también sobre el papel.
     */
    enum class Slot(
        val mix: Float,
        val depth: Float,
        val spread: Float,
        val flip: Boolean = false,
        val tint: Int? = null
    ) {
        YEAR(0.00f, 0.00f, 0.22f),                  // el ancla: slabOf(acento) exacto
        YEAR_ALT(0.30f, -0.45f, 0.14f),
        HOURS(0.55f, 0.50f, 0.34f),                 // la más clara y la más dramática
        HOURS_ALT(1.00f, -0.15f, 0.18f),
        TOPS(0.75f, -0.40f, 0.22f),
        TOPS_AUTHOR(0.30f, -0.45f, 0.14f),          // gemela de YEAR_ALT, como en Oscuro
        TOPS_GENRE(0.00f, 0.00f, 0.22f, flip = true), // la del año dada la vuelta, como en Oscuro
        CHART(0.65f, -0.90f, 0.16f, flip = true),
        BESTDAY(0.15f, 0.85f, 0.30f),
        TIMESLOT(0.40f, -0.60f, 0.26f),
        TIMESLOT_HERO(0.90f, 0.30f, 0.24f),
        CLOSE(0.10f, 0.55f, 0.22f),                 // la del cierre: la única de tres stops
        RATED(0f, 0f, 0.22f, tint = 0xFFBB33),      // Gold
        FASTEST(0f, 0f, 0.22f, tint = 0x10B981),    // Green
        FAVORITES(0f, 0f, 0.22f, tint = 0xCC4A4A);  // FavoriteRed
    }

    // ── Oscuro: la tabla literal de de3180f ──────────────────────────────────────
    // Estos hexes NO se tocan y NO se derivan. Son el Wrapped que Víctor tiene en la cabeza,
    // sacados de `git show de3180f:app/src/main/java/com/lecturameter/WrappedScreens.kt`.
    // Si alguien los "mejora", el Oscuro deja de ser el Oscuro.

    val DARK_SLABS: Map<Slot, IntArray> = mapOf(
        Slot.YEAR          to intArrayOf(0x312E81, 0x1E1B4B),  // índigo
        Slot.YEAR_ALT      to intArrayOf(0x2D1B69, 0x1E1B4B),  // violeta
        Slot.HOURS         to intArrayOf(0x4C1D95, 0x150B33),  // violeta profundo
        Slot.HOURS_ALT     to intArrayOf(0x0C4A6E, 0x0F172A),  // océano
        Slot.TOPS          to intArrayOf(0x4C1D95, 0x1E1B4B),
        Slot.TOPS_AUTHOR   to intArrayOf(0x2D1B69, 0x1E1B4B),
        Slot.TOPS_GENRE    to intArrayOf(0x1E1B4B, 0x312E81),  // la del año, del revés
        Slot.CHART         to intArrayOf(0x0F2027, 0x203A43),  // pizarra → teal
        Slot.BESTDAY       to intArrayOf(0x312E81, 0x0F172A),
        Slot.TIMESLOT      to intArrayOf(0x1E1B4B, 0x0F172A),
        Slot.TIMESLOT_HERO to intArrayOf(0x1E1B4B, 0x0C4A6E),
        Slot.CLOSE         to intArrayOf(0x312E81, 0x4C1D95, 0x0F0D2E),  // tres stops
        Slot.RATED         to intArrayOf(0x92400E, 0x1A1200),
        Slot.FASTEST       to intArrayOf(0x064E3B, 0x0A2818),
        Slot.FAVORITES     to intArrayOf(0x3B0D0D, 0x1A0808)
    )

    /**
     * Los seis números protagonistas del Oscuro, también literales de de3180f. Los cuatro slots
     * sin entrada no llevan número en degradado, ni lo llevaban.
     *
     * Todos están medidos contra su losa en `WrappedPaletteTest`: el peor es el "2026" a 3,83:1,
     * y son de 26 a 80sp, así que el mínimo que aplica es AA_LARGE (3:1). El diseño original
     * estaba bien; el problema nunca fue el Oscuro.
     */
    val DARK_HEROES: Map<Slot, IntArray> = mapOf(
        Slot.YEAR          to intArrayOf(0x818CF8, 0x22D3EE),  // el "2026" a 80sp
        Slot.HOURS         to intArrayOf(0xA78BFA, 0xDDD6FE),
        Slot.TIMESLOT_HERO to intArrayOf(0x818CF8, 0x22D3EE),
        Slot.CLOSE         to intArrayOf(0xFFFFFF, 0xA78BFA),  // el cierre, en vertical
        Slot.RATED         to intArrayOf(0xFFBB33, 0xFDE68A),  // Gold → ámbar claro
        Slot.FASTEST       to intArrayOf(0x10B981, 0x22D3EE)   // Green → cian
    )

    /**
     * Las etiquetas secundarias del Oscuro, también literales de de3180f.
     *
     * B-037 las pasó a blanco por contraste, y ahí se equivocó dos veces: eran OPACAS (no eran el
     * `Gold.copy(0.7f)` que sí era el bug) y pasaban AA de sobra, entre 5,93:1 y 8,39:1. O sea que
     * se perdió el tinte de cada losa a cambio de nada. Medido en `WrappedPaletteTest`.
     *
     * Las de las losas semánticas NO están aquí: esas sí iban con alfa (`Gold.copy(0.7f)`,
     * `Green.copy(0.7f)`), que es la causa raíz de B-037, y se quedan derivadas y medidas.
     */
    val DARK_MUTED: Map<Slot, Int> = mapOf(
        Slot.YEAR          to 0xC7D2FE,
        Slot.HOURS         to 0xC4B5FD,
        Slot.TIMESLOT_HERO to 0x7DD3FC,
        Slot.CLOSE         to 0xE9D5FF
    )

    /**
     * Los títulos de las losas que no llevan número protagonista, otra vez literales de de3180f.
     * Mismo caso que [DARK_MUTED]: eran opacos, pasaban AA, y B-037 los blanqueó igual.
     *
     * En los cuatro temas derivados no hay literal que restaurar, así que el título va con el
     * tinte de su propia losa ([muted]), que es lo que hacía el Oscuro. Blanco sería más fácil,
     * pero es justo lo que aplanó la pantalla.
     */
    val DARK_TITLE: Map<Slot, Int> = mapOf(
        Slot.TOPS      to 0xC4B5FD,
        Slot.CHART     to 0x94A3B8,
        Slot.BESTDAY   to 0xA5B4FC,
        Slot.TIMESLOT  to 0xA5B4FC,
        Slot.FAVORITES to 0xCC4A4A   // FavoriteRed: el rojo ES el titulo de esa slide
    )

    // ── Aritmética ───────────────────────────────────────────────────────────────

    /** Interpola dos colores canal a canal. */
    fun lerp(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun mix(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca * (1 - k) + cb * k + 0.5f).toInt().coerceIn(0, 255)
        }
        return ContrastUtils.rgb(mix(16), mix(8), mix(0))
    }

    /**
     * La versión CENIZA de [rgb]: el mismo color mezclado con el gris de su propia luminancia.
     * Conserva el matiz y baja el croma, que es la única variedad honesta que admite un tema de
     * un solo color. En el Cuero da un oro viejo; en el Claro, una salvia apagada.
     */
    fun ash(rgb: Int, factor: Float = 0.70f): Int {
        val y = (0.2126f * ContrastUtils.red(rgb) +
            0.7152f * ContrastUtils.green(rgb) +
            0.0722f * ContrastUtils.blue(rgb) + 0.5f).toInt().coerceIn(0, 255)
        return lerp(rgb, ContrastUtils.rgb(y, y, y), factor)
    }

    /** La losa más CLARA de la familia de [base] con la que el blanco todavía pasa AA (4,5:1). */
    fun ceiling(base: Int): Int {
        val anchor = ContrastUtils.slabStops(base).first
        var best = anchor
        var f = 0f
        while (f <= 0.60f) {
            val cand = ContrastUtils.lighten(anchor, f)
            if (ContrastUtils.contrast(WHITE, cand) < ContrastUtils.AA_TEXT) break
            best = cand
            f += 0.01f
        }
        return best
    }

    /**
     * La losa más HONDA de la familia de [base] que todavía se despega 3:1 del fondo [bg]. Sin
     * este suelo, en AMOLED (fondo negro puro) una losa hundida deja de verse: el golpe de efecto
     * del Wrapped es la losa, así que una losa invisible no es una losa.
     */
    fun floor(base: Int, bg: Int): Int {
        val anchor = ContrastUtils.slabStops(base).first
        var best = anchor
        var f = 0f
        while (f <= MAX_SINK) {
            val cand = ContrastUtils.darken(anchor, f)
            if (ContrastUtils.contrast(cand, bg) < ContrastUtils.AA_LARGE) break
            best = cand
            f += 0.01f
        }
        return best
    }

    /** El color base de [slot] en [key]: el semántico si lo tiene, o el punto del recorrido. */
    fun baseOf(slot: Slot, key: ThemeKey): Int =
        slot.tint ?: lerp(key.anchorA, key.anchorB, slot.mix)

    /**
     * El stop claro de la losa: `slabOf(base)` movido dentro de la banda medida del tema.
     * `depth` positivo va hacia el techo (más clara), negativo hacia el suelo (más honda), y el
     * 0 deja la losa exactamente donde `slabOf` la pone.
     */
    fun toneOf(slot: Slot, key: ThemeKey): Int {
        val base = baseOf(slot, key)
        val anchor = ContrastUtils.slabStops(base).first
        // Las losas semánticas no se mueven: el oro tiene que ser el mismo oro en los cuatro temas.
        if (slot.tint != null) return anchor
        return when {
            slot.depth > 0f -> lerp(anchor, ceiling(base), slot.depth)
            slot.depth < 0f -> lerp(anchor, floor(base, key.bg), -slot.depth)
            else            -> anchor
        }
    }

    /**
     * Los stops de la losa de [slot] en [key]. En [ThemeKey.DARK] devuelve la tabla literal tal
     * cual; en el resto, deriva.
     */
    fun slab(slot: Slot, key: ThemeKey): IntArray {
        DARK_SLABS[slot]?.takeIf { key == ThemeKey.DARK }?.let { return it.copyOf() }
        val top = toneOf(slot, key)
        val bot = ContrastUtils.darken(top, slot.spread)
        val stops = if (slot.flip) intArrayOf(bot, top) else intArrayOf(top, bot)
        // El cierre lleva tres stops en Oscuro y los conserva en todos: es la losa más grande de
        // la pantalla y con dos se queda plana.
        return if (slot == Slot.CLOSE) stops + ContrastUtils.darken(top, 0.78f) else stops
    }

    /** El stop más CLARO de una losa: el peor caso al medir cualquier cosa que vaya encima. */
    fun lightestOf(stops: IntArray): Int = stops.maxByOrNull { ContrastUtils.luminance(it) }!!

    /**
     * Los dos extremos del número protagonista de [slot]. En Oscuro, los literales de de3180f;
     * en el resto, dos tonos claros del propio color de la losa, aclarados hasta MEDIR 3:1
     * (AA_LARGE: estos números van de 26 a 80sp) contra el stop más claro de su losa.
     *
     * Nunca con alfa. El degradado de TEXTO tiene que llevar los dos extremos opacos: si el
     * segundo lleva alfa, la mitad derecha del número se pinta mezclada con el fondo. Es el
     * pecado que B-037 vino a matar y en el que cayó su propio arreglo.
     */
    fun hero(slot: Slot, key: ThemeKey): IntArray {
        DARK_HEROES[slot]?.takeIf { key == ThemeKey.DARK }?.let { return it.copyOf() }
        val light = lightestOf(slab(slot, key))
        val tint = baseOf(slot, key)
        var f = 0f
        while (f <= 0.98f &&
            ContrastUtils.contrast(ContrastUtils.lighten(tint, f), light) < ContrastUtils.AA_LARGE
        ) f += 0.01f
        val a = ContrastUtils.lighten(tint, f)
        // Si ni al 98% de blanco llega (hoy inalcanzable), se cierra en blanco, que siempre pasa
        // porque slabStops lo garantiza por construcción.
        if (ContrastUtils.contrast(a, light) < ContrastUtils.AA_LARGE) return intArrayOf(WHITE, WHITE)
        return intArrayOf(a, ContrastUtils.lighten(tint, minOf(f + 0.42f, 0.99f)))
    }

    /**
     * La etiqueta secundaria de una losa: el propio color de la losa aclarado hasta pasar AA
     * (4,5:1) sobre su stop más claro, para que la losa conserve el tinte en vez de ser blanco
     * sobre color a secas. Es lo que hacían a mano los #C7D2FE / #C4B5FD / #E9D5FF del Oscuro.
     */
    fun muted(slot: Slot, key: ThemeKey): Int {
        DARK_MUTED[slot]?.takeIf { key == ThemeKey.DARK }?.let { return it }
        val light = lightestOf(slab(slot, key))
        // El tinte de partida es el color de la losa, salvo en las semánticas, donde es el propio
        // semántico: la etiqueta del mejor puntuado tiene que seguir leyéndose como oro.
        val tint = baseOf(slot, key)
        var f = 0f
        while (f <= 0.98f &&
            ContrastUtils.contrast(ContrastUtils.lighten(tint, f), light) < ContrastUtils.AA_TEXT
        ) f += 0.01f
        val cand = ContrastUtils.lighten(tint, f)
        return if (ContrastUtils.contrast(cand, light) >= ContrastUtils.AA_TEXT) cand else WHITE
    }

    /**
     * El título de una losa que no lleva número protagonista. En Oscuro, el literal de de3180f;
     * en el resto, el tinte de la propia losa, que es lo que hace el Oscuro.
     */
    fun title(slot: Slot, key: ThemeKey): Int =
        DARK_TITLE[slot]?.takeIf { key == ThemeKey.DARK } ?: muted(slot, key)
}
