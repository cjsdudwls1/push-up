package com.pushuprpg.app.share

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.pushuprpg.app.R
import java.util.Locale

/**
 * Draws a share card.
 *
 * Deliberately plain [android.graphics] rather than Compose: this runs off the main thread with no
 * composition, no window and no layout pass, and it has to produce the identical image on a device
 * that is mid-run. Every string comes from resources — the card is the most public surface in the
 * app and it is not the place to start hardcoding Korean.
 *
 * The size is 1080×1350 (4:5). That is the tallest aspect Instagram and KakaoTalk will show without
 * cropping, so the card survives being reposted, which is the only reason it exists.
 */
object ShareCardRenderer {

    const val WIDTH = 1080
    const val HEIGHT = 1350

    private const val MARGIN = 72f
    private const val PANEL_TOP = 186f
    private const val PANEL_BOTTOM = 690f
    private const val MIN_SLAB = 70f

    // The palette, duplicated from the Compose theme as plain ints. Duplicated rather than shared
    // because ui.theme.Palette is Compose Color and this file must not depend on Compose.
    private const val STROKE = 0xFF2E3542.toInt()
    private const val TEXT_PRIMARY = 0xFFF2F5FA.toInt()
    private const val TEXT_SECONDARY = 0xFFA9B3C4.toInt()
    private const val TEXT_TERTIARY = 0xFF6E7A8E.toInt()
    private const val TEXT_ON_ACCENT = 0xFF0B0D12.toInt()
    private const val DEEP = 0xFFFFC53D.toInt()
    private const val ACCEPT = 0xFF35E08A.toInt()
    private const val INFO = 0xFF35C3FF.toInt()

    // The pixel demo's room and its cheese cat.
    private const val CAT_BG = 0xFF07081A.toInt()
    private const val CAT_PANEL = 0xFF0A0B22.toInt()
    private const val ROOM = 0xFF0A0B22.toInt()
    private const val ROOM_STRIPE = 0xFF15143A.toInt()
    private const val FLOOR_EDGE = 0xFF3A3470.toInt()
    private const val FLOOR = 0xFF0F0E2A.toInt()
    private const val FLOOR_BOARD = 0xFF1A1840.toInt()
    private const val BRICK = 0xFF4A4870.toInt()
    private const val BRICK_DARK = 0xFF2E2C50.toInt()
    private const val BRICK_LIT = 0xFF7C7AB0.toInt()
    private const val CAT_OUTLINE = 0xFF2B1A12.toInt()
    private const val CAT_FUR = 0xFFFF9A2E.toInt()
    private const val CAT_STRIPE = 0xFFE0761A.toInt()
    private const val CAT_LIGHT = 0xFFFFD9A8.toInt()
    private const val CAT_EAR = 0xFFFF8FAB.toInt()
    private const val CAT_EYE = 0xFF1E1A2E.toInt()

    private val bold: Typeface get() = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val regular: Typeface get() = Typeface.SANS_SERIF

    fun render(res: Resources, data: ShareCardData): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        when (data) {
            is ShareCardData.Survival -> drawSurvival(res, canvas, data)
        }
        return bitmap
    }

    // ---------------------------------------------------------------- 고양이

    private fun drawSurvival(res: Resources, canvas: Canvas, data: ShareCardData.Survival) {
        background(canvas, top = CAT_BG, bottom = 0xFF1A0E3A.toInt(), glow = DEEP)
        header(res, canvas, chip = data.movement, chipColor = DEEP)

        val panel = panel(canvas, CAT_PANEL)
        drawCeilingAndCat(canvas, panel)

        headline(canvas, res.getString(R.string.share_card_survival_headline), DEEP)
        hero(
            canvas,
            value = format(data.score),
            suffix = res.getString(R.string.share_card_point_suffix),
            color = TEXT_PRIMARY,
            suffixColor = DEEP,
        )
        heroLabel(canvas, res.getString(R.string.share_card_survival_hero_label))

        // Printing the all-time best next to an identical score wastes the most valuable tile on
        // the card. When this run *is* the best, say so instead — that is the line worth posting.
        val isRecord = data.score >= data.best
        stats(
            canvas,
            Stat(format(data.reps), res.getString(R.string.share_card_stat_reps), ACCEPT),
            Stat(duration(res, data.seconds), res.getString(R.string.share_card_stat_time), INFO),
            if (isRecord) {
                Stat(
                    res.getString(R.string.share_card_new_record),
                    res.getString(R.string.share_card_new_record_label),
                    DEEP,
                )
            } else {
                Stat(format(data.best), res.getString(R.string.share_card_stat_best), DEEP)
            },
        )
        footer(res, canvas)
    }

    /**
     * The pixel demo's room (2026-11-10, the look the owner chose): the dark wall, the brick ceiling
     * with its teeth, and the cat holding it up, all on one grid of the art's pixels.
     *
     * The ceiling stops a little above the cat's raised paws rather than on them: the card is read by
     * someone who has never played, and a ceiling resting on the cat reads as a squashed cat.
     *
     * The Play feature graphic draws its picture with this too (`tools/sharecard-preview`), so the
     * store shows the same cat the card does. Only rectangles, so the preview's shim draws it as is.
     */
    internal fun drawCeilingAndCat(canvas: Canvas, panel: RectF) {
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(panel, 44f, 44f, Path.Direction.CW) })
        val paint = Paint()
        // One of the art's pixels; the cat is 20 cells of 4 of them, a third of the panel across.
        val u = Math.round(panel.width() / 240f).coerceAtLeast(1).toFloat()
        val cols = (panel.width() / u).toInt() + 2
        val rows = (panel.height() / u).toInt() + 2
        val floorRow = rows - 1 - Math.round(panel.height() * 0.08f / u)
        fun px(c: Int, r: Int, w: Int, h: Int, color: Int) {
            paint.color = color
            canvas.drawRect(panel.left + c * u, panel.top + r * u, panel.left + (c + w) * u, panel.top + (r + h) * u, paint)
        }
        // The wall, its stripes, the floor's edge and its boards.
        px(0, 0, cols, rows, ROOM)
        var x = 8
        while (x < cols) { px(x, 0, 2, floorRow, ROOM_STRIPE); x += 24 }
        px(0, floorRow, cols, 4, FLOOR_EDGE)
        px(0, floorRow + 4, cols, rows, FLOOR)
        var y = floorRow + 12
        while (y < rows) { px(0, y, cols, 2, FLOOR_BOARD); y += 10 }

        val cell = 4
        val catCols = CAT_SPRITE[0].length
        val catLeft = cols / 2 - catCols * cell / 2
        val catTop = floorRow - CAT_SPRITE.size * cell
        // The paws, raised: four cells over the ears. The ceiling's teeth stop two cells above them.
        val reach = -4
        val ceilingBottom = maxOf(catTop + (reach - 3) * cell - 11, Math.round(MIN_SLAB / u))

        val paws = intArrayOf(-2, 19)
        for (a in paws) {
            px(catLeft + a * cell - 1, catTop + reach * cell, 3 * cell + 2, (11 - reach) * cell, CAT_OUTLINE)
            px(catLeft + a * cell + 1, catTop + reach * cell + 2, 3 * cell - 2, (11 - reach) * cell - 2, CAT_FUR)
            px(catLeft + a * cell - 2, catTop + (reach - 1) * cell + 2, 4 * cell, 2 * cell, CAT_OUTLINE)
            px(catLeft + a * cell, catTop + reach * cell, 3 * cell, cell, CAT_LIGHT)
            px(catLeft + (a + 1) * cell, catTop + reach * cell + 1, cell, 2, CAT_EAR)
        }
        for ((r, line) in CAT_SPRITE.withIndex()) {
            for ((c, ch) in line.withIndex()) {
                val color = when (ch) {
                    'K' -> CAT_OUTLINE
                    'O' -> CAT_FUR
                    'S' -> CAT_STRIPE
                    'L' -> CAT_LIGHT
                    'P' -> CAT_EAR
                    else -> continue
                }
                px(catLeft + c * cell, catTop + r * cell, cell, cell, color)
            }
        }
        // The eyes, three cells each with a glint, and the cat is fine: it is holding.
        for (start in intArrayOf(3, 14)) {
            px(catLeft + start * cell, catTop + 6 * cell, 3 * cell, 3 * cell, CAT_EYE)
            px(catLeft + (start + 2) * cell, catTop + 6 * cell, cell, cell, 0xFFFFFFFF.toInt())
        }

        // The ceiling: bricks down to its underside, a lit edge, and stepped teeth.
        px(0, 0, cols, ceilingBottom, BRICK)
        var by = ceilingBottom - 12
        var row = 0
        while (by > -12) {
            px(0, by, cols, 2, BRICK_DARK)
            var bx = (row % 2) * 16
            while (bx < cols) { px(bx, by, 2, 12, BRICK_DARK); bx += 32 }
            by -= 12
            row++
        }
        px(0, ceilingBottom - 4, cols, 2, BRICK_LIT)
        var tx = 0
        while (tx < cols) {
            px(tx + 2, ceilingBottom, 12, 4, BRICK)
            px(tx + 4, ceilingBottom + 4, 8, 4, BRICK)
            px(tx + 6, ceilingBottom + 8, 4, 2, BRICK)
            px(tx + 6, ceilingBottom + 10, 4, 1, BRICK_DARK)
            tx += 16
        }
        canvas.restore()
    }

    /** The app's cheese cat, the demo's own, cell for cell as `CatArt` draws it. */
    private val CAT_SPRITE = arrayOf(
        "..KK............KK..",
        ".KOOK..........KOOK.",
        ".KOPOK........KOPOK.",
        "KOOPOOKKKKKKKKOOPOOK",
        "KOOOOOOOSSSSOOOOOOOK",
        "KOOOOOOOOSSOOOOOOOOK",
        "KOOOOOOOOOOOOOOOOOOK",
        "KOOOOOOOOOOOOOOOOOOK",
        "KOOOOOOOOOOOOOOOOOOK",
        "KOOOOOOOOPPOOOOOOOOK",
        "KOPPOOOOKOOKOOOOPPOK",
        "KOOOOOOOOKKOOOOOOOOK",
        ".KOOOOOOOOOOOOOOOOK.",
        "..KKKOOOOOOOOOOKKK..",
        "....KOLLLLLLLLOK....",
        "...KOOLLLLLLLLOOK...",
        "...KOOLLLLLLLLOOK...",
        "...KOOOLLLLLLOOOK...",
        "...KOOOOOOOOOOOOK...",
        "...KLLLKKKKKKLLLK...",
        "....KKK......KKK....",
    )

    // ---------------------------------------------------------------- shared furniture

    private fun background(canvas: Canvas, top: Int, bottom: Int, glow: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, HEIGHT.toFloat(),
            intArrayOf(top, bottom), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

        // A single soft glow behind the picture. Enough to stop the card reading as a screenshot of a
        // settings page when it lands in a feed of photographs.
        paint.shader = RadialGradient(
            WIDTH / 2f, PANEL_TOP + 180f, WIDTH * 0.72f,
            intArrayOf(withAlpha(glow, 0x38), withAlpha(glow, 0x00)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
    }

    private fun header(res: Resources, canvas: Canvas, chip: String, chipColor: Int) {
        val paint = textPaint(44f, TEXT_PRIMARY, bold)
        paint.letterSpacing = 0.04f
        paint.textAlign = Paint.Align.LEFT
        val baseline = 132f
        canvas.drawText(res.getString(R.string.survival_title), MARGIN, baseline, paint)

        val chipPaint = textPaint(32f, TEXT_ON_ACCENT, bold)
        chipPaint.textAlign = Paint.Align.CENTER
        val label = ellipsize(chipPaint, chip, 420f)
        val padding = 30f
        val chipW = chipPaint.measureText(label) + padding * 2
        val chipRect = RectF(WIDTH - MARGIN - chipW, baseline - 48f, WIDTH - MARGIN, baseline + 10f)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = chipColor
        canvas.drawRoundRect(chipRect, chipRect.height() / 2f, chipRect.height() / 2f, fill)
        canvas.drawText(label, chipRect.centerX(), centeredBaseline(chipPaint, chipRect), chipPaint)
    }

    private fun panel(canvas: Canvas, fillColor: Int): RectF {
        val rect = RectF(MARGIN, PANEL_TOP, WIDTH - MARGIN, PANEL_BOTTOM)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = fillColor
        canvas.drawRoundRect(rect, 44f, 44f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = STROKE
        canvas.drawRoundRect(rect, 44f, 44f, paint)
        return rect
    }

    private fun headline(canvas: Canvas, text: String, color: Int) {
        val paint = textPaint(58f, color, bold)
        paint.textAlign = Paint.Align.CENTER
        shrinkToFit(paint, text, WIDTH - MARGIN * 2)
        canvas.drawText(text, WIDTH / 2f, 770f, paint)
    }

    /** The number, with its unit riding alongside at a smaller weight. */
    private fun hero(canvas: Canvas, value: String, suffix: String, color: Int, suffixColor: Int) {
        val numberPaint = textPaint(156f, color, bold)
        numberPaint.textAlign = Paint.Align.LEFT
        val suffixPaint = textPaint(60f, suffixColor, bold)
        suffixPaint.textAlign = Paint.Align.LEFT

        val gap = 14f
        var total = numberPaint.measureText(value) + gap + suffixPaint.measureText(suffix)
        val maxWidth = WIDTH - MARGIN * 2
        if (total > maxWidth) {
            val scale = maxWidth / total
            numberPaint.textSize *= scale
            suffixPaint.textSize *= scale
            total = numberPaint.measureText(value) + gap + suffixPaint.measureText(suffix)
        }

        val baseline = 950f
        val left = (WIDTH - total) / 2f
        canvas.drawText(value, left, baseline, numberPaint)
        canvas.drawText(suffix, left + numberPaint.measureText(value) + gap, baseline, suffixPaint)
    }

    private fun heroLabel(canvas: Canvas, text: String) {
        val paint = textPaint(34f, TEXT_SECONDARY, regular)
        paint.textAlign = Paint.Align.CENTER
        shrinkToFit(paint, text, WIDTH - MARGIN * 2)
        canvas.drawText(text, WIDTH / 2f, 1004f, paint)
    }

    private class Stat(val value: String, val label: String, val accent: Int)

    private fun stats(canvas: Canvas, vararg tiles: Stat) {
        val gap = 20f
        val top = 1046f
        val bottom = 1208f
        val width = (WIDTH - MARGIN * 2 - gap * (tiles.size - 1)) / tiles.size

        // Sized once for the whole row, at whatever the longest entry can take. Fitting each tile
        // independently is what produced three different type sizes side by side, which reads as a
        // rendering bug rather than as emphasis.
        val valueSize = commonSize(56f, tiles.map { it.value }, width - 28f, bold)
        val labelSize = commonSize(28f, tiles.map { it.label }, width - 20f, regular)

        tiles.forEachIndexed { index, stat ->
            val left = MARGIN + index * (width + gap)
            val rect = RectF(left, top, left + width, bottom)

            val fill = Paint(Paint.ANTI_ALIAS_FLAG)
            fill.color = 0xFF141822.toInt()
            canvas.drawRoundRect(rect, 28f, 28f, fill)
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = 3f
            fill.color = 0x1AFFFFFF
            canvas.drawRoundRect(rect, 28f, 28f, fill)

            val valuePaint = textPaint(valueSize, stat.accent, bold)
            valuePaint.textAlign = Paint.Align.CENTER
            canvas.drawText(stat.value, rect.centerX(), top + 90f, valuePaint)

            val labelPaint = textPaint(labelSize, TEXT_TERTIARY, regular)
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(stat.label, rect.centerX(), top + 136f, labelPaint)
        }
    }

    /** The largest size at or below [preferred] at which every one of [texts] fits [maxWidth]. */
    private fun commonSize(preferred: Float, texts: List<String>, maxWidth: Float, face: Typeface): Float {
        val probe = textPaint(preferred, 0, face)
        var size = preferred
        texts.forEach { text ->
            probe.textSize = preferred
            shrinkToFit(probe, text, maxWidth)
            size = minOf(size, probe.textSize)
        }
        return size
    }

    private fun footer(res: Resources, canvas: Canvas) {
        val paint = textPaint(30f, TEXT_TERTIARY, regular)
        paint.textAlign = Paint.Align.CENTER
        paint.letterSpacing = 0.03f
        canvas.drawText(res.getString(R.string.share_card_footer), WIDTH / 2f, 1288f, paint)
    }

    // ---------------------------------------------------------------- text plumbing

    private fun textPaint(size: Float, color: Int, face: Typeface): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.isSubpixelText = true
        paint.textSize = size
        paint.color = color
        paint.typeface = face
        return paint
    }

    /**
     * Shrinks until it fits.
     *
     * Korean labels and six-digit scores vary in width far more than the layout can absorb, and a
     * card is worthless if the one number on it is clipped. Shrinking is always preferable to
     * wrapping here: every string on the card is a label, and a wrapped label looks broken.
     */
    private fun shrinkToFit(paint: Paint, text: String, maxWidth: Float) {
        if (text.isEmpty()) return
        var width = paint.measureText(text)
        while (width > maxWidth && paint.textSize > 12f) {
            paint.textSize *= (maxWidth / width).coerceAtMost(0.94f)
            width = paint.measureText(text)
        }
    }

    private fun ellipsize(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun centeredBaseline(paint: Paint, rect: RectF): Float =
        rect.centerY() - (paint.ascent() + paint.descent()) / 2f

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    private fun format(value: Int): String = String.format(Locale.KOREA, "%,d", value)

    private fun duration(res: Resources, seconds: Int): String =
        if (seconds < 60) {
            res.getString(R.string.share_card_seconds, seconds)
        } else {
            res.getString(R.string.share_card_minutes, seconds / 60, seconds % 60)
        }
}
