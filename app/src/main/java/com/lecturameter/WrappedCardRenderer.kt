package com.lecturameter

// v2.5: Renderer de tarjetas Wrapped — una variante por slide (0-6).
// Canvas puro (android.graphics), 1080x1920 story 9:16. Sin Compose.
// slide 0=Resumen  1=Tiempo  2=Tops  3=Mejor/Rápido  4=Gráfica  5=Vs año  6=Cierre (bonus visual)

import android.content.Context
import android.graphics.*
import android.text.*
import android.util.TypedValue

object WrappedCardRenderer {

    const val SLIDE_SUMMARY  = 0
    const val SLIDE_TIME     = 1
    const val SLIDE_TOPS     = 2
    const val SLIDE_BEST     = 3
    const val SLIDE_CHART    = 4
    const val SLIDE_VS       = 5
    const val SLIDE_FINALE   = 6

    private const val W = 1080f
    private const val H = 1920f

    fun render(ctx: Context, w: YearWrapped, slide: Int): Bitmap {
        val bmp = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        when (slide) {
            SLIDE_TIME    -> drawTime(ctx, c, w)
            SLIDE_TOPS    -> drawTops(ctx, c, w)
            SLIDE_BEST    -> drawBest(ctx, c, w)
            SLIDE_CHART   -> drawChart(ctx, c, w)
            SLIDE_VS      -> drawVs(ctx, c, w)
            SLIDE_FINALE  -> drawFinale(ctx, c, w)
            else          -> drawSummary(ctx, c, w)
        }
        return bmp
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun tp(size: Float, color: Int, bold: Boolean = false, ls: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (ls != 0f) letterSpacing = ls
        }

    private fun fp(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun sp(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = w
    }

    private fun Canvas.ct(text: String, cx: Float, y: Float, p: Paint) =
        drawText(text, cx - p.measureText(text) / 2f, y, p)

    private fun ell(text: String, p: TextPaint, maxW: Float) =
        TextUtils.ellipsize(text, p, maxW, TextUtils.TruncateAt.END).toString()

    private fun genreLabel(ctx: Context, raw: String) =
        GENRE_DISPLAY_KEY[raw]?.let { ctx.getString(it) } ?: raw

    private fun fmtMin(min: Int): String {
        val h = min / 60; val m = min % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun bg(c: Canvas, vararg cols: Int) {
        c.drawRect(0f, 0f, W, H, Paint().apply {
            shader = LinearGradient(0f, 0f, W * 0.3f, H,
                intArrayOf(*cols), null, Shader.TileMode.CLAMP)
        })
    }

    private fun blob(c: Canvas, cx: Float, cy: Float, r: Float, col: Int) =
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, r, col, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })

    private fun card(c: Canvas, l: Float, t: Float, r: Float, b: Float, fill: Int, stroke: Int) {
        val rect = RectF(l, t, r, b)
        c.drawRoundRect(rect, 48f, 48f, fp(fill))
        c.drawRoundRect(rect, 48f, 48f, sp(stroke, 3f))
    }

    private fun gradTxt(c: Canvas, text: String, cx: Float, y: Float, size: Float, c1: Int, c2: Int) {
        val p = tp(size, Color.WHITE, bold = true)
        val tw = p.measureText(text)
        p.shader = LinearGradient(cx - tw / 2f, 0f, cx + tw / 2f, 0f, intArrayOf(c1, c2), null, Shader.TileMode.CLAMP)
        c.ct(text, cx, y, p)
    }

    private fun foot(c: Canvas) =
        c.ct("📖 Lecturameter", W / 2f, H - 60f,
            tp(30f, 0xFF818CF8.toInt(), bold = true, ls = 0.1f))

    private fun brand(c: Canvas, year: Int) =
        c.ct("📖 LECTURAMETER WRAPPED $year", W / 2f, 130f,
            tp(34f, 0xFFA5B4FC.toInt(), bold = true, ls = 0.2f))

    private fun hBar(c: Canvas, value: Float, maxValue: Float, l: Float, t: Float, bW: Float, bH: Float, col: Int) {
        val ratio = (value / maxValue.coerceAtLeast(1f)).coerceIn(0f, 1f)
        c.drawRoundRect(RectF(l, t, l + bW, t + bH), bH / 2, bH / 2, fp(0x22FFFFFF))
        if (ratio > 0f) c.drawRoundRect(RectF(l, t, l + bW * ratio, t + bH), bH / 2, bH / 2,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(l, 0f, l + bW, 0f, intArrayOf(col, col and 0x00FFFFFF or (0x99 shl 24)), null, Shader.TileMode.CLAMP)
            })
    }

    // ── SLIDE 0: Resumen del año ──────────────────────────────────────────────
    private fun drawSummary(ctx: Context, c: Canvas, w: YearWrapped) {
        bg(c, 0xFF312E81.toInt(), 0xFF1E1B4B.toInt(), 0xFF0F0D2E.toInt())
        blob(c, W - 80f, 100f, 420f, 0x556366F1); blob(c, -60f, H - 400f, 360f, 0x448B5CF6)
        brand(c, w.year)

        // Año protagonista
        gradTxt(c, "${w.year}", W / 2f, 380f, 240f, 0xFF818CF8.toInt(), 0xFF22D3EE.toInt())
        c.ct(ctx.getString(R.string.wcard_subtitle), W / 2f, 440f, tp(40f, 0xFFC7D2FE.toInt()))

        // 2 números enormes
        c.ct(w.totalPages.toLocaleString(), W / 2f, 700f, tp(160f, Color.WHITE, bold = true))
        c.ct(ctx.getString(R.string.wcard_pages).uppercase(), W / 2f, 758f, tp(30f, 0xFFA5B4FC.toInt(), bold = true, ls = 0.2f))

        c.ct("${w.totalBooks}", W / 2f, 930f, tp(160f, Color.WHITE, bold = true))
        c.ct(ctx.getString(R.string.wcard_books).uppercase(), W / 2f, 988f, tp(30f, 0xFFA5B4FC.toInt(), bold = true, ls = 0.2f))

        // 3 mini-stats
        val hTot = w.totalMinutes / 60
        val minis = listOf(
            Triple(if (hTot > 0) "${hTot}h" else "${w.totalMinutes}m", ctx.getString(R.string.wcard_hours), 0xFFF59E0B.toInt()),
            Triple("${w.longestStreakDays}d", ctx.getString(R.string.wcard_streak), 0xFF10B981.toInt()),
            Triple("${w.totalSessions}", ctx.getString(R.string.wcard_sessions), 0xFF0EA5E9.toInt())
        )
        val mTop = 1060f; val mH = 190f; val gap = 28f; val side = 60f
        val mW = (W - side * 2 - gap * 2) / 3f
        minis.forEachIndexed { i, (num, lbl, col) ->
            val l = side + i * (mW + gap)
            card(c, l, mTop, l + mW, mTop + mH, 0x14FFFFFF, 0x22FFFFFF)
            c.ct(num, l + mW / 2f, mTop + 100f, tp(76f, col, bold = true))
            c.ct(lbl, l + mW / 2f, mTop + 155f, tp(24f, 0xFFA5B4FC.toInt(), bold = true, ls = 0.1f))
        }

        // Autor + género
        var fy = 1320f
        val favs = mutableListOf<Triple<String, String, String>>()
        if (w.favoriteAuthor.isNotBlank())
            favs += Triple("✍️", ctx.getString(R.string.wcard_author_year), "${w.favoriteAuthor} · ${w.favoriteAuthorBooks} libros")
        if (w.favoriteGenre.isNotBlank())
            favs += Triple("🎭", ctx.getString(R.string.wcard_genre_year), "${genreLabel(ctx, w.favoriteGenre)} · ${w.favoriteGenreBooks} libros")
        val valP = tp(40f, Color.WHITE, bold = true)
        favs.forEach { (emoji, lbl, value) ->
            card(c, side, fy, W - side, fy + 152f, 0x14FFFFFF, 0x22FFFFFF)
            c.drawText(emoji, side + 36f, fy + 98f, tp(56f, Color.WHITE))
            c.drawText(lbl, side + 128f, fy + 60f, tp(24f, 0xFFA5B4FC.toInt(), bold = true, ls = 0.1f))
            c.drawText(ell(value, valP, W - side * 2 - 168f), side + 128f, fy + 118f, valP)
            fy += 178f
        }
        foot(c)
    }

    // ── SLIDE 1: Tiempo y sesiones ────────────────────────────────────────────
    private fun drawTime(ctx: Context, c: Canvas, w: YearWrapped) {
        bg(c, 0xFF0C4A6E.toInt(), 0xFF0F172A.toInt(), 0xFF050A14.toInt())
        blob(c, W / 2f, 300f, 500f, 0x330EA5E9); blob(c, W, H * 0.6f, 360f, 0x226366F1)
        brand(c, w.year)

        c.ct("⏱️", W / 2f, 380f, tp(120f, Color.WHITE))
        gradTxt(c, fmtMin(w.totalMinutes), W / 2f, 600f, 130f, 0xFF7DD3FC.toInt(), 0xFF22D3EE.toInt())
        c.ct(ctx.getString(R.string.wcard_hours).uppercase(), W / 2f, 660f, tp(30f, 0xFF7DD3FC.toInt(), bold = true, ls = 0.2f))

        // Stats fila
        val side = 60f; val bW = (W - side * 2 - 28f) / 2f
        val stats = listOf(
            Triple("${w.totalSessions}", ctx.getString(R.string.wcard_sessions), 0xFF22D3EE.toInt()),
            Triple("${w.maxSessionPages}p", ctx.getString(R.string.wrapped_record_session), 0xFFF59E0B.toInt())
        )
        stats.forEachIndexed { i, (num, lbl, col) ->
            val l = side + i * (bW + 28f)
            card(c, l, 740f, l + bW, 940f, 0x14FFFFFF, 0x22FFFFFF)
            c.ct(num, l + bW / 2f, 840f, tp(80f, col, bold = true))
            c.ct(lbl, l + bW / 2f, 900f, tp(26f, 0xFF7DD3FC.toInt(), bold = true, ls = 0.1f))
        }

        // Top libros por tiempo
        if (w.longestBooksTop3.isNotEmpty()) {
            c.ct(ctx.getString(R.string.txt_1db69449), W / 2f, 1020f,
                tp(28f, 0xFF7DD3FC.toInt(), bold = true, ls = 0.2f))
            val medals = listOf("🥇", "🥈", "🥉")
            val maxM = w.longestBooksTop3.maxOf { it.second }.toFloat()
            w.longestBooksTop3.forEachIndexed { i, (title, mins) ->
                val y0 = 1060f + i * 220f
                card(c, side, y0, W - side, y0 + 200f, 0x14FFFFFF, 0x22FFFFFF)
                c.drawText(medals.getOrElse(i) { "${i+1}." }, side + 28f, y0 + 110f, tp(56f, Color.WHITE))
                val nmP = tp(38f, Color.WHITE, bold = true)
                c.drawText(ell(title, nmP, W - side * 2 - 160f), side + 118f, y0 + 82f, nmP)
                c.drawText(fmtMin(mins), side + 118f, y0 + 130f, tp(30f, 0xFF7DD3FC.toInt()))
                hBar(c, mins.toFloat(), maxM, side + 118f, y0 + 148f, W - side * 2 - 118f, 18f, 0xFF0EA5E9.toInt())
            }
        }
        foot(c)
    }

    // ── SLIDE 2: Tops autores + géneros ──────────────────────────────────────
    private fun drawTops(ctx: Context, c: Canvas, w: YearWrapped) {
        bg(c, 0xFF4C1D95.toInt(), 0xFF1E1B4B.toInt(), 0xFF0B0F1E.toInt())
        blob(c, W / 2f, -60f, 520f, 0x448B5CF6); blob(c, 0f, H * 0.6f, 400f, 0x336366F1)
        brand(c, w.year)

        val side = 60f; val medals = listOf("🥇", "🥈", "🥉")
        val gold = 0xFFF59E0B.toInt()

        fun section(title: String, items: List<Triple<String, String, Boolean>>, startY: Float, col: Int): Float {
            c.drawText(title, side, startY, tp(30f, col, bold = true, ls = 0.2f))
            var y = startY + 40f
            items.forEachIndexed { i, (name, count, isGold) ->
                val fill = if (isGold) 0x22F59E0B else 0x12FFFFFF
                val stroke = if (isGold) 0x55F59E0B else 0x1FFFFFFF
                card(c, side, y, W - side, y + 120f, fill, stroke)
                c.drawText(medals.getOrElse(i) { "${i+1}." }, side + 28f, y + 82f, tp(52f, Color.WHITE))
                val cw = tp(32f, col, bold = true).measureText(count)
                val nmP = tp(42f, Color.WHITE, bold = true)
                c.drawText(ell(name, nmP, W - side * 2 - 190f - cw), side + 116f, y + 80f, nmP)
                c.drawText(count, W - side - 30f - cw, y + 78f, tp(32f, col, bold = true))
                y += 146f
            }
            return y + 40f
        }

        var y = 240f
        if (w.topAuthorsTop3.isNotEmpty()) {
            y = section("✍️ ${ctx.getString(R.string.wcard_authors)}",
                w.topAuthorsTop3.mapIndexed { i, (n, cnt) -> Triple(n, "$cnt libros", i == 0) },
                y, 0xFFC084FC.toInt())
        }
        if (w.topGenresTop3.isNotEmpty()) {
            section("🎭 ${ctx.getString(R.string.wcard_genres)}",
                w.topGenresTop3.mapIndexed { i, (n, cnt) ->
                    Triple(genreLabel(ctx, n), "$cnt libros", i == 0) },
                y, 0xFF818CF8.toInt())
        }
        foot(c)
    }

    // ── SLIDE 3: Mejor valorado + más rápido ─────────────────────────────────
    private fun drawBest(ctx: Context, c: Canvas, w: YearWrapped) {
        bg(c, 0xFF1A1200.toInt(), 0xFF1C1410.toInt(), 0xFF0A0800.toInt())
        blob(c, W * 0.2f, H * 0.3f, 500f, 0x44FFBB33); blob(c, W * 0.8f, H * 0.7f, 400f, 0x3310B981)
        brand(c, w.year)

        val gold = 0xFFFFBB33.toInt(); val green = 0xFF10B981.toInt()
        val side = 60f

        // Mejor valorado
        val top3 = w.bestRatedTop3.ifEmpty {
            if (w.bestRatedTitle.isNotBlank()) listOf(Triple(w.bestRatedTitle, w.bestRatedScore, "")) else emptyList()
        }
        if (top3.isNotEmpty()) {
            c.ct("⭐", W / 2f, 340f, tp(110f, Color.WHITE))
            gradTxt(c, "${top3[0].second}/10", W / 2f, 560f, 130f, gold, 0xFFFDE68A.toInt())
            val nmP = tp(46f, Color.WHITE, bold = true)
            c.ct(ell(top3[0].first, nmP, W - side * 2), W / 2f, 628f, nmP)
            c.ct(ctx.getString(R.string.wrapped_mejor_puntuado).uppercase(), W / 2f, 680f,
                tp(26f, 0xFFFFBB33.toInt(), bold = true, ls = 0.2f))

            // 2 y 3
            top3.drop(1).forEachIndexed { i, (title, score, _) ->
                val medal = if (i == 0) "🥈" else "🥉"
                val y0 = 740f + i * 170f
                card(c, side, y0, W - side, y0 + 150f, 0x14FFFFFF, 0x22FFFFFF)
                c.drawText(medal, side + 28f, y0 + 98f, tp(52f, Color.WHITE))
                val nmP2 = tp(38f, Color.WHITE, bold = true)
                c.drawText(ell(title, nmP2, W - side * 2 - 200f), side + 110f, y0 + 80f, nmP2)
                c.drawText("$score/10", W - side - 30f - tp(34f, gold, bold = true).measureText("$score/10"),
                    y0 + 78f, tp(34f, gold, bold = true))
            }
        }

        // Más rápido
        if (w.fastestBookTitle.isNotBlank()) {
            val y0 = if (top3.size >= 2) 1100f else 800f
            c.ct("🚀", W / 2f, y0, tp(80f, Color.WHITE))
            gradTxt(c, "${String.format("%.1f", w.fastestBookPpd)} p/d", W / 2f, y0 + 160f, 110f, 0xFF34D399.toInt(), 0xFF22D3EE.toInt())
            c.ct(ctx.getString(R.string.wrapped_libro_mas_rapido).uppercase(), W / 2f, y0 + 220f,
                tp(26f, 0xFF34D399.toInt(), bold = true, ls = 0.2f))
            val fP = tp(42f, Color.WHITE, bold = true)
            c.ct(ell(w.fastestBookTitle, fP, W - side * 2), W / 2f, y0 + 290f, fP)
            c.ct("${w.fastestBookPages} pág.", W / 2f, y0 + 345f, tp(30f, 0xFF6EE7B7.toInt()))
        }
        foot(c)
    }

    // ── SLIDE 4: Gráfica meses ────────────────────────────────────────────────
    private fun drawChart(ctx: Context, c: Canvas, w: YearWrapped) {
        bg(c, 0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt())
        blob(c, W / 2f, H / 2f, 700f, 0x22227C72)
        brand(c, w.year)

        val side = 60f; val chartL = side; val chartR = W - side
        // v2.5: chart más alto para diferenciar mejor barras similares
        val chartT = 300f; val chartB = 900f
        val chartW = chartR - chartL; val chartH = chartB - chartT

        val months = listOf("E","F","M","A","M","J","J","A","S","O","N","D")
        val maxP = w.pagesPerMonth.max().coerceAtLeast(1)
        val barW = chartW / 12f

        // Barras
        w.pagesPerMonth.forEachIndexed { i, p ->
            if (p == 0) return@forEachIndexed
            val ratio = p.toFloat() / maxP
            val bH = (chartH * ratio).coerceAtLeast(8f)
            val l = chartL + i * barW + barW * 0.1f
            val r = chartL + (i + 1) * barW - barW * 0.1f
            val t = chartB - bH
            val isMax = p == maxP
            val col1 = if (isMax) 0xFF22D3EE.toInt() else 0xFF6366F1.toInt()
            val col2 = if (isMax) 0xFF0EA5E9.toInt() else 0xFF4338CA.toInt()
            c.drawRoundRect(RectF(l, t, r, chartB), 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, t, 0f, chartB, intArrayOf(col1, col2), null, Shader.TileMode.CLAMP)
            })
            run {
                val lbl = if (p >= 1000) "${p/1000}k" else "$p"
                c.ct(lbl, l + (r - l) / 2f, t - 12f, tp(22f, if (isMax) 0xFF22D3EE.toInt() else 0xFF818CF8.toInt(), bold = true))
            }
        }

        // Etiquetas mes
        months.forEachIndexed { i, m ->
            c.ct(m, chartL + i * barW + barW / 2f, chartB + 44f, tp(30f, 0xFF94A3B8.toInt()))
        }

        // Línea base
        c.drawLine(chartL, chartB, chartR, chartB, sp(0x44FFFFFF, 2f))

        // Stats debajo
        val bestIdx = w.pagesPerMonth.indexOf(maxP)
        val bestName = ctx.resources.getStringArray(R.array.month_names_full).getOrElse(bestIdx) { "" }
        c.ct(ctx.getString(R.string.wrapped_best_month, bestName, maxP), W / 2f, 940f,
            tp(36f, 0xFF22D3EE.toInt(), bold = true))

        // Total año prominente
        gradTxt(c, w.totalPages.toLocaleString(), W / 2f, 1180f, 160f, 0xFF818CF8.toInt(), 0xFF22D3EE.toInt())
        c.ct(ctx.getString(R.string.wcard_pages).uppercase(), W / 2f, 1238f,
            tp(30f, 0xFF94A3B8.toInt(), bold = true, ls = 0.2f))

        // Donut géneros (pequeño, decorativo)
        if (w.genreCountsTop6.isNotEmpty()) {
            val total = w.genreCountsTop6.sumOf { it.second }
            val gCols = intArrayOf(0xFF6366F1.toInt(), 0xFF10B981.toInt(), 0xFF0EA5E9.toInt(),
                0xFFF59E0B.toInt(), 0xFFF87171.toInt(), 0xFF8B5CF6.toInt())
            val cx = W / 2f; val cy = 1500f; val r = 130f; val thick = 40f
            var startAngle = -90f
            w.genreCountsTop6.forEachIndexed { i, (_, n) ->
                val sweep = 360f * n / total
                c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), startAngle, sweep - 2f, false,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = gCols[i % gCols.size]; style = Paint.Style.STROKE
                        strokeWidth = thick; strokeCap = Paint.Cap.ROUND
                    })
                startAngle += sweep
            }
            // Leyenda inline
            var lx = side
            w.genreCountsTop6.take(3).forEachIndexed { i, (name, _) ->
                c.drawCircle(lx + 14f, cy + r + 30f, 14f, fp(gCols[i % gCols.size]))
                val lP = tp(24f, 0xFFCBD5E1.toInt())
                c.drawText(genreLabel(ctx, name), lx + 36f, cy + r + 38f, lP)
                lx += lP.measureText(genreLabel(ctx, name)) + 70f
            }
        }
        foot(c)
    }

    // ── SLIDE 5: Vs año anterior ──────────────────────────────────────────────
    private fun drawVs(ctx: Context, c: Canvas, w: YearWrapped) {
        bg(c, 0xFF0F2027.toInt(), 0xFF1A1A2E.toInt(), 0xFF0D0D1A.toInt())
        blob(c, W * 0.15f, H * 0.45f, 450f, 0x3310B981); blob(c, W * 0.85f, H * 0.45f, 450f, 0x33F87171)
        brand(c, w.year)

        val side = 60f; val prev = w.year - 1
        val dBooks = w.totalBooks - w.previousYearBooks
        val dPages = w.totalPages - w.previousYearPages

        // VS label
        c.ct("${w.year}  VS  $prev", W / 2f, 320f, tp(70f, Color.WHITE, bold = true))
        c.drawLine(W / 2f, 350f, W / 2f, H - 200f, sp(0x22FFFFFF, 2f))

        // Libros
        val bookGreen = dBooks >= 0; val pageGreen = dPages >= 0
        c.ct("${w.totalBooks}", W * 0.25f, 580f, tp(150f, Color.WHITE, bold = true))
        c.ct("libros", W * 0.25f, 640f, tp(30f, 0xFF94A3B8.toInt(), bold = true))
        c.ct("${w.previousYearBooks}", W * 0.75f, 580f, tp(150f, 0xFF94A3B8.toInt(), bold = true))
        c.ct("libros", W * 0.75f, 640f, tp(30f, 0xFF64748B.toInt(), bold = true))

        val bookSign = if (dBooks > 0) "+" else ""
        val bookCol = if (bookGreen) 0xFF10B981.toInt() else 0xFFF87171.toInt()
        gradTxt(c, "$bookSign$dBooks", W / 2f, 800f, 100f, bookCol, if (bookGreen) 0xFF34D399.toInt() else 0xFFFCA5A5.toInt())
        c.ct(if (dBooks == 0) "igual" else if (dBooks > 0) "más libros 📈" else "menos libros 📉",
            W / 2f, 860f, tp(32f, bookCol, bold = true))

        // Páginas
        c.ct(w.totalPages.toLocaleString(), W * 0.25f, 1080f, tp(100f, Color.WHITE, bold = true))
        c.ct("páginas", W * 0.25f, 1140f, tp(28f, 0xFF94A3B8.toInt(), bold = true))
        c.ct(w.previousYearPages.toLocaleString(), W * 0.75f, 1080f, tp(100f, 0xFF94A3B8.toInt(), bold = true))
        c.ct("páginas", W * 0.75f, 1140f, tp(28f, 0xFF64748B.toInt(), bold = true))

        val pageSign = if (dPages > 0) "+" else ""
        val pageCol = if (pageGreen) 0xFF10B981.toInt() else 0xFFF87171.toInt()
        gradTxt(c, "$pageSign${dPages.toLocaleString()}", W / 2f, 1300f, 90f, pageCol, if (pageGreen) 0xFF34D399.toInt() else 0xFFFCA5A5.toInt())
        c.ct(if (dPages == 0) "igual" else if (dPages > 0) "más páginas 📈" else "menos páginas 📉",
            W / 2f, 1362f, tp(32f, pageCol, bold = true))

        // Racha
        if (w.longestStreakDays > 0) {
            card(c, side, 1440f, W - side, 1620f, 0x14FFFFFF, 0x22FFFFFF)
            c.ct("🔥 ${w.longestStreakDays} días de racha", W / 2f, 1548f, tp(50f, 0xFFF87171.toInt(), bold = true))
        }
        foot(c)
    }

    // ── SLIDE 6: Cierre visual ────────────────────────────────────────────────
    private fun drawFinale(ctx: Context, c: Canvas, w: YearWrapped) {
        bg(c, 0xFF312E81.toInt(), 0xFF4C1D95.toInt(), 0xFF0F0D2E.toInt())
        blob(c, W / 2f, H * 0.4f, 700f, 0x336366F1)
        blob(c, W * 0.1f, H * 0.8f, 300f, 0x558B5CF6)
        blob(c, W * 0.9f, H * 0.2f, 300f, 0x4422D3EE)
        brand(c, w.year)

        // Marca agua 📚
        c.save(); c.rotate(-12f, W / 2f, H * 0.42f)
        c.ct("📚", W / 2f, H * 0.42f + 300f, tp(700f, Color.WHITE).apply { alpha = 14 })
        c.restore()

        // Número protagonista: páginas
        gradTxt(c, w.totalPages.toLocaleString(), W / 2f, 880f, 200f, 0xFFFFFFFF.toInt(), 0xFFA78BFA.toInt())
        c.ct(ctx.getString(R.string.wcard_pages_read).uppercase(), W / 2f, 950f, tp(40f, 0xFFE9D5FF.toInt(), bold = true, ls = 0.2f))

        // 3 logros
        val items = mutableListOf<String>()
        items += "📚 ${w.totalBooks} libros terminados"
        if (w.longestStreakDays > 0) items += "🔥 Racha de ${w.longestStreakDays} días"
        if (w.maxSessionPages > 0) items += "⚡ Récord: ${w.maxSessionPages} pág. en una sesión"
        if (w.favoriteAuthor.isNotBlank()) items += "✍️ ${w.favoriteAuthor}"

        var iy = 1060f
        items.take(4).forEach { line ->
            c.ct(line, W / 2f, iy, tp(42f, 0xFFC4B5FD.toInt(), bold = true)); iy += 88f
        }

        // Footer especial
        c.ct("Un año de muchas páginas.", W / 2f, H - 160f, tp(38f, 0xFFC4B5FD.toInt()))
        foot(c)
    }
}
