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
import kotlin.math.cos
import kotlin.math.sin

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
    private const val TEETH = 14
    private const val MIN_SLAB = 70f
    private const val EAR_GAP = 34f

    /** Floor to ear tip, in the units [drawCat] multiplies by its scale. */
    private const val CAT_HEIGHT_UNITS = 46f * 1.05f + 30f * (0.72f + 1.58f)

    // The palette, duplicated from the Compose theme as plain ints. Duplicated rather than shared
    // because ui.theme.Palette is Compose Color and this file must not depend on Compose.
    private const val BG0 = 0xFF07080C.toInt()
    private const val BG1 = 0xFF0E1117.toInt()
    private const val BG2 = 0xFF161A23.toInt()
    private const val STROKE = 0xFF2E3542.toInt()
    private const val TEXT_PRIMARY = 0xFFF2F5FA.toInt()
    private const val TEXT_SECONDARY = 0xFFA9B3C4.toInt()
    private const val TEXT_TERTIARY = 0xFF6E7A8E.toInt()
    private const val TEXT_ON_ACCENT = 0xFF0B0D12.toInt()
    private const val BRAND = 0xFF7C6BFF.toInt()
    private const val BRAND_LIGHT = 0xFF9A8CFF.toInt()
    private const val DEEP = 0xFFFFC53D.toInt()
    private const val COMBO = 0xFFFF9F43.toInt()
    private const val ACCEPT = 0xFF35E08A.toInt()
    private const val INFO = 0xFF35C3FF.toInt()

    // 고냥이's warm set. The mode looks nothing like the dungeon on purpose and the card keeps that.
    private const val CAT_BG = 0xFF1A1208.toInt()
    private const val CAT_PANEL = 0xFF241809.toInt()
    private const val CAT_FUR = 0xFFF3D9A8.toInt()
    private const val CAT_INK = 0xFF3A2A18.toInt()
    private const val CAT_EAR = 0xFFE0A88C.toInt()
    private const val CAT_SLAB = 0xFF7A4A32.toInt()

    private val bold: Typeface get() = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val regular: Typeface get() = Typeface.SANS_SERIF

    fun render(res: Resources, data: ShareCardData): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        when (data) {
            is ShareCardData.Survival -> drawSurvival(res, canvas, data)
            is ShareCardData.Dungeon -> drawDungeon(res, canvas, data)
        }
        return bitmap
    }

    // ---------------------------------------------------------------- 고냥이

    private fun drawSurvival(res: Resources, canvas: Canvas, data: ShareCardData.Survival) {
        background(canvas, top = CAT_BG, bottom = 0xFF120C05.toInt(), glow = DEEP)
        header(res, canvas, chip = res.getString(R.string.survival_title), chipColor = DEEP)

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
     * The slab, the gap, and the cat.
     *
     * Frozen a little above the cat rather than at the height the run ended: the card is read by
     * someone who has never played, and a slab resting on the cat reads as a squashed cat.
     */
    private fun drawCeilingAndCat(canvas: Canvas, panel: RectF) {
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(panel, 44f, 44f, Path.Direction.CW) })

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val floorY = panel.bottom - panel.height() * 0.08f
        val scale = panel.width() / 340f

        // The ceiling is positioned from the cat's ears rather than from the panel, so the gap that
        // is the entire point of the mode is a fixed, deliberate distance instead of whatever two
        // independently-tuned fractions happen to leave.
        val toothWidth = panel.width() / TEETH
        val toothDepth = toothWidth * 0.55f
        val slabBottom = (floorY - CAT_HEIGHT_UNITS * scale - EAR_GAP - toothDepth)
            .coerceAtLeast(panel.top + MIN_SLAB)

        paint.color = CAT_SLAB
        canvas.drawRect(panel.left, panel.top, panel.right, slabBottom, paint)

        val tooth = Path()
        for (i in 0 until TEETH) {
            tooth.reset()
            tooth.moveTo(panel.left + i * toothWidth, slabBottom)
            tooth.lineTo(panel.left + (i + 0.5f) * toothWidth, slabBottom + toothDepth)
            tooth.lineTo(panel.left + (i + 1f) * toothWidth, slabBottom)
            tooth.close()
            canvas.drawPath(tooth, paint)
        }

        drawCat(canvas, panel.centerX(), floorY, scale)

        paint.color = CAT_INK
        canvas.drawRect(panel.left, floorY, panel.right, panel.bottom, paint)

        canvas.restore()
    }

    /**
     * 고냥이.
     *
     * The mascot, and the only drawing in the app a stranger judges before deciding whether to
     * install anything. Circles alone came out as a snowman, so the parts that actually say "cat" —
     * tall swept ears, whiskers, a tail — are drawn even though they cost nothing in the game and
     * everything in recognisability here.
     */
    private fun drawCat(canvas: Canvas, centerX: Float, baseY: Float, scale: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val body = 46f * scale
        val head = 30f * scale
        val headY = baseY - body * 1.05f - head * 0.72f

        // Tail first, so the body edge covers where it joins.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = head * 0.30f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = CAT_FUR
        val tail = Path()
        tail.moveTo(centerX + body * 0.82f, baseY - body * 0.18f)
        tail.quadTo(
            centerX + body * 1.62f, baseY - body * 0.30f,
            centerX + body * 1.48f, baseY - body * 0.98f,
        )
        tail.quadTo(
            centerX + body * 1.36f, baseY - body * 1.46f,
            centerX + body * 0.98f, baseY - body * 1.40f,
        )
        canvas.drawPath(tail, paint)
        paint.style = Paint.Style.FILL

        canvas.drawRoundRect(
            RectF(centerX - body, baseY - body * 1.05f, centerX + body, baseY),
            body * 0.62f, body * 0.62f, paint,
        )

        // Ears before the head, so their bases disappear under it.
        val ear = Path()
        listOf(-1f, 1f).forEach { side ->
            ear.reset()
            ear.moveTo(centerX + side * head * 0.16f, headY - head * 0.62f)
            ear.lineTo(centerX + side * head * 0.74f, headY - head * 1.58f)
            ear.lineTo(centerX + side * head * 1.00f, headY - head * 0.34f)
            ear.close()
            canvas.drawPath(ear, paint)
        }
        paint.color = CAT_EAR
        listOf(-1f, 1f).forEach { side ->
            ear.reset()
            ear.moveTo(centerX + side * head * 0.36f, headY - head * 0.66f)
            ear.lineTo(centerX + side * head * 0.71f, headY - head * 1.28f)
            ear.lineTo(centerX + side * head * 0.84f, headY - head * 0.52f)
            ear.close()
            canvas.drawPath(ear, paint)
        }

        paint.color = CAT_FUR
        canvas.drawCircle(centerX, headY, head, paint)

        paint.color = CAT_INK
        val eyeY = headY - head * 0.10f
        listOf(-1f, 1f).forEach { side ->
            canvas.drawCircle(centerX + side * head * 0.36f, eyeY, head * 0.15f, paint)
        }

        // Nose: a small downward triangle, the one mark that stops this reading as a bear.
        val nose = Path()
        val noseY = eyeY + head * 0.30f
        nose.moveTo(centerX - head * 0.10f, noseY)
        nose.lineTo(centerX + head * 0.10f, noseY)
        nose.lineTo(centerX, noseY + head * 0.11f)
        nose.close()
        canvas.drawPath(nose, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = head * 0.055f
        paint.strokeCap = Paint.Cap.ROUND

        // A closed, content mouth: a cat that is fine is a cat worth protecting.
        val mouthY = noseY + head * 0.12f
        canvas.drawLine(centerX, mouthY, centerX - head * 0.14f, mouthY + head * 0.12f, paint)
        canvas.drawLine(centerX, mouthY, centerX + head * 0.14f, mouthY + head * 0.12f, paint)

        listOf(-1f, 1f).forEach { side ->
            val from = centerX + side * head * 0.42f
            canvas.drawLine(from, noseY - head * 0.04f, from + side * head * 0.72f, noseY - head * 0.22f, paint)
            canvas.drawLine(from, noseY + head * 0.12f, from + side * head * 0.76f, noseY + head * 0.16f, paint)
        }
    }

    // ---------------------------------------------------------------- 던전

    private fun drawDungeon(res: Resources, canvas: Canvas, data: ShareCardData.Dungeon) {
        val accent = if (data.cleared) DEEP else BRAND_LIGHT
        background(canvas, top = BG1, bottom = BG0, glow = accent)
        header(res, canvas, chip = data.dungeonName, chipColor = accent)

        val panel = panel(canvas, BG2)
        drawCrest(canvas, panel, cleared = data.cleared)

        headline(
            canvas,
            res.getString(
                if (data.cleared) R.string.result_cleared else R.string.result_defeat
            ),
            accent,
        )
        hero(
            canvas,
            value = format(if (data.inSeconds) data.heldSeconds else data.reps),
            suffix = res.getString(
                if (data.inSeconds) R.string.share_card_second_suffix else R.string.share_card_rep_suffix
            ),
            color = TEXT_PRIMARY,
            suffixColor = accent,
        )
        heroLabel(
            canvas,
            res.getString(
                if (data.inSeconds) R.string.share_card_dungeon_hero_label_hold
                else R.string.share_card_dungeon_hero_label
            ),
        )

        // The run's length is plain 시간, as the result screen names it. 버틴 시간 is what a hold is
        // counted in and what the cat's card counts, and on a pushup's card it read as a plank.
        val time = Stat(duration(res, data.seconds), res.getString(R.string.result_tile_time), INFO)
        val rank = Stat(data.rankKorean, res.getString(R.string.share_card_stat_rank), ACCEPT)
        if (data.inSeconds) {
            // A hold builds no combo, so there is no ×0 tile.
            stats(canvas, time, rank)
        } else {
            stats(
                canvas,
                Stat(
                    res.getString(R.string.result_combo_value, data.maxCombo),
                    res.getString(R.string.result_tile_combo),
                    COMBO,
                ),
                time,
                rank,
            )
        }
        footer(res, canvas)
    }

    /**
     * A hexagonal crest.
     *
     * Cleared fills it; a loss leaves it outlined and cracked. Both are worth posting, which is the
     * point — the run banked its reps either way and the card must not look like a consolation.
     */
    private fun drawCrest(canvas: Canvas, panel: RectF, cleared: Boolean) {
        val cx = panel.centerX()
        val cy = panel.centerY()
        val r = panel.height() * 0.38f

        val hex = Path()
        for (i in 0 until 6) {
            val a = Math.toRadians((60.0 * i) - 90.0)
            val x = cx + r * cos(a).toFloat()
            val y = cy + r * sin(a).toFloat()
            if (i == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
        }
        hex.close()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (cleared) {
            paint.shader = LinearGradient(
                cx, cy - r, cx, cy + r,
                intArrayOf(DEEP, COMBO), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
        } else {
            paint.color = 0xFF1F2430.toInt()
        }
        canvas.drawPath(hex, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.color = if (cleared) 0x66FFFFFF else BRAND
        canvas.drawPath(hex, paint)

        drawSword(canvas, cx, cy, r, ink = if (cleared) TEXT_ON_ACCENT else BRAND_LIGHT)

        if (!cleared) {
            // A crack, not a cross-out. The run happened.
            paint.color = 0x59FFFFFF
            paint.strokeWidth = 7f
            val crack = Path()
            crack.moveTo(cx - r * 0.75f, cy - r * 0.35f)
            crack.lineTo(cx - r * 0.18f, cy + r * 0.05f)
            crack.lineTo(cx + r * 0.12f, cy - r * 0.28f)
            crack.lineTo(cx + r * 0.78f, cy + r * 0.30f)
            canvas.drawPath(crack, paint)
        }
    }

    private fun drawSword(canvas: Canvas, cx: Float, cy: Float, r: Float, ink: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ink

        val bladeW = r * 0.16f
        val tipY = cy - r * 0.62f
        val guardY = cy + r * 0.18f
        val blade = Path()
        blade.moveTo(cx, tipY)
        blade.lineTo(cx + bladeW, tipY + bladeW * 1.6f)
        blade.lineTo(cx + bladeW, guardY)
        blade.lineTo(cx - bladeW, guardY)
        blade.lineTo(cx - bladeW, tipY + bladeW * 1.6f)
        blade.close()
        canvas.drawPath(blade, paint)

        canvas.drawRoundRect(
            RectF(cx - r * 0.46f, guardY, cx + r * 0.46f, guardY + r * 0.13f),
            r * 0.06f, r * 0.06f, paint,
        )
        canvas.drawRoundRect(
            RectF(cx - bladeW * 0.7f, guardY + r * 0.13f, cx + bladeW * 0.7f, cy + r * 0.62f),
            bladeW * 0.5f, bladeW * 0.5f, paint,
        )
        canvas.drawCircle(cx, cy + r * 0.68f, r * 0.09f, paint)
    }

    // ---------------------------------------------------------------- shared furniture

    private fun background(canvas: Canvas, top: Int, bottom: Int, glow: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, HEIGHT.toFloat(),
            intArrayOf(top, bottom), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

        // A single soft glow behind the crest. Enough to stop the card reading as a screenshot of a
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
        canvas.drawText(res.getString(R.string.app_name), MARGIN, baseline, paint)

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
     * Korean dungeon names and rank names vary in width far more than the layout can absorb, and a
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
