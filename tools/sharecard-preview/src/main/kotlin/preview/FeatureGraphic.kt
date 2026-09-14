package preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * The 1024x500 banner Play shows at the top of the listing.
 *
 * Generated rather than designed because it is a hard requirement for publishing and there is no
 * designer on this project. It is built from the same shapes and palette as the app, so it is at
 * least honest about what the product looks like.
 *
 * Play crops this asset differently across surfaces and overlays a play button on some of them, so
 * nothing load-bearing goes in the centre or within 10% of any edge.
 */
object FeatureGraphic {

    const val WIDTH = 1024
    const val HEIGHT = 500

    private const val BG0 = 0xFF07080C.toInt()
    private const val BG2 = 0xFF161A23.toInt()
    private const val TEXT = 0xFFF2F5FA.toInt()
    private const val TEXT_2 = 0xFFA9B3C4.toInt()
    private const val BRAND = 0xFF7C6BFF.toInt()
    private const val DEEP = 0xFFFFC53D.toInt()
    private const val ACCEPT = 0xFF35E08A.toInt()
    private const val BOSS = 0xFFFF4D5E.toInt()

    /** Where the text column ends and the art begins. */
    private const val TEXT_RIGHT = 636f

    fun render(): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(
            0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
            intArrayOf(0xFF0B0D16.toInt(), BG0), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

        paint.shader = RadialGradient(
            WIDTH * 0.74f, HEIGHT * 0.44f, WIDTH * 0.44f,
            intArrayOf((BRAND and 0x00FFFFFF) or 0x4A000000, (BRAND and 0x00FFFFFF)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null

        val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val left = 72f

        // The text column stops at TEXT_RIGHT and the art starts after it. Sizing the headline to
        // that boundary rather than by eye is what keeps the boss health bar off the descenders.
        val headline = fit(listOf("팔굽혀펴기 한 개가", "몬스터를 한 번 때려요"), TEXT_RIGHT - left, 62f, bold)

        text(canvas, "푸쉬업 RPG", left, 146f, 38f, DEEP, bold, spacing = 0.06f)
        text(canvas, "팔굽혀펴기 한 개가", left, 240f, headline, TEXT, bold)
        text(canvas, "몬스터를 한 번 때려요", left, 240f + headline * 1.24f, headline, TEXT, bold)
        text(canvas, "카메라가 깊이를 재서 세요. 얕게 하면 안 맞아요.", left, 400f, 24f, TEXT_2, Typeface.SANS_SERIF)

        // Kept clear of the right 10%: Play crops this asset differently across its surfaces.
        drawMonster(canvas, cx = 778f, cy = 202f, r = 98f)
        drawGauge(canvas, x = 902f, top = 112f, height = 224f)

        return bitmap
    }

    private fun drawMonster(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xFF3B2F52.toInt()
        canvas.drawCircle(cx, cy, r, paint)

        // Curved back and set wide, so they read as horns rather than as the cat's ears.
        val horns = Path()
        listOf(-1f, 1f).forEach { side ->
            horns.moveTo(cx + side * r * 0.86f, cy - r * 0.46f)
            horns.quadTo(
                cx + side * r * 1.14f, cy - r * 1.22f,
                cx + side * r * 0.58f, cy - r * 1.26f,
            )
            horns.quadTo(
                cx + side * r * 0.74f, cy - r * 0.92f,
                cx + side * r * 0.44f, cy - r * 0.80f,
            )
            horns.close()
        }
        canvas.drawPath(horns, paint)

        paint.color = BOSS
        canvas.drawCircle(cx - r * 0.34f, cy - r * 0.10f, r * 0.15f, paint)
        canvas.drawCircle(cx + r * 0.34f, cy - r * 0.10f, r * 0.15f, paint)

        // A jagged mouth. Two eyes on a circle is a face; teeth are what make it something to hit.
        paint.color = 0xFF1B1328.toInt()
        val mouth = Path()
        val mouthY = cy + r * 0.36f
        mouth.moveTo(cx - r * 0.46f, mouthY)
        var step = 0
        while (step < 6) {
            val x0 = cx - r * 0.46f + r * 0.92f / 6f * step
            val x1 = x0 + r * 0.92f / 12f
            val x2 = x0 + r * 0.92f / 6f
            mouth.lineTo(x1, mouthY + r * 0.20f)
            mouth.lineTo(x2, mouthY)
            step++
        }
        mouth.lineTo(cx + r * 0.46f, mouthY - r * 0.14f)
        mouth.lineTo(cx - r * 0.46f, mouthY - r * 0.14f)
        mouth.close()
        canvas.drawPath(mouth, paint)

        // Its health, as the number the feedback asked for.
        val track = RectF(cx - r * 1.15f, cy + r * 1.32f, cx + r * 1.15f, cy + r * 1.48f)
        paint.color = 0xFF2E3542.toInt()
        canvas.drawRoundRect(track, track.height() / 2f, track.height() / 2f, paint)
        paint.color = BOSS
        canvas.drawRoundRect(
            RectF(track.left, track.top, track.left + track.width() * 0.62f, track.bottom),
            track.height() / 2f, track.height() / 2f, paint,
        )
        text(
            canvas, "1,240 / 2,000", track.centerX(), track.bottom + 34f, 22f, TEXT_2,
            Typeface.SANS_SERIF, align = Paint.Align.CENTER,
        )
    }

    private fun drawGauge(canvas: Canvas, x: Float, top: Float, height: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val width = 34f
        val rect = RectF(x, top, x + width, top + height)

        paint.color = BG2
        canvas.drawRoundRect(rect, width / 2f, width / 2f, paint)

        paint.shader = LinearGradient(
            x, rect.bottom, x, rect.top,
            intArrayOf(ACCEPT, DEEP), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(x, rect.bottom - height * 0.52f, x + width, rect.bottom),
            width / 2f, width / 2f, paint,
        )
        paint.shader = null

        // The two thresholds the rep counter actually uses, as lines rather than as a legend: the
        // whole promise of the gauge is that the line you aim at is the line that judges you.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        listOf(0.56f to DEEP, 0.30f to ACCEPT).forEach { (fraction, color) ->
            paint.color = color
            val y = rect.bottom - height * fraction
            canvas.drawLine(x - 12f, y, x + width + 12f, y, paint)
        }
    }

    /** The largest size at or below [preferred] at which every line fits [maxWidth]. */
    private fun fit(lines: List<String>, maxWidth: Float, preferred: Float, face: Typeface): Float {
        val probe = Paint(Paint.ANTI_ALIAS_FLAG)
        probe.typeface = face
        var size = preferred
        lines.forEach { line ->
            probe.textSize = preferred
            var width = probe.measureText(line)
            while (width > maxWidth && probe.textSize > 12f) {
                probe.textSize *= (maxWidth / width).coerceAtMost(0.96f)
                width = probe.measureText(line)
            }
            size = minOf(size, probe.textSize)
        }
        return size
    }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        face: Typeface,
        spacing: Float = 0f,
        align: Paint.Align = Paint.Align.LEFT,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = size
        paint.color = color
        paint.typeface = face
        paint.letterSpacing = spacing
        paint.textAlign = align
        canvas.drawText(value, x, baseline, paint)
    }
}
