package preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.pushuprpg.app.share.ShareCardRenderer

/**
 * The 1024x500 banner Play shows at the top of the listing.
 *
 * Generated rather than designed because it is a hard requirement for publishing and there is no
 * designer on this project. Its picture is the share card's own ceiling and cat, drawn by the same
 * code ([ShareCardRenderer.drawCeilingAndCat]), so the store shows exactly the cat the app does.
 *
 * Play crops this asset differently across surfaces and overlays a play button on some of them, so
 * nothing load-bearing goes in the centre or within 10% of any edge.
 */
object FeatureGraphic {

    const val WIDTH = 1024
    const val HEIGHT = 500

    // 고냥이's warm set, as on the share card.
    private const val CAT_BG = 0xFF1A1208.toInt()
    private const val CAT_BG_LOW = 0xFF120C05.toInt()
    private const val CAT_PANEL = 0xFF241809.toInt()
    private const val STROKE = 0xFF3A2A18.toInt()
    private const val TEXT = 0xFFF2F5FA.toInt()
    private const val TEXT_2 = 0xFFC9B79C.toInt()
    private const val DEEP = 0xFFFFC53D.toInt()

    /** Where the text column ends and the picture begins. */
    private const val TEXT_RIGHT = 532f

    fun render(): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(
            0f, 0f, 0f, HEIGHT.toFloat(),
            intArrayOf(CAT_BG, CAT_BG_LOW), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)

        // A warm glow behind the picture, as on the card.
        paint.shader = RadialGradient(
            WIDTH * 0.74f, HEIGHT * 0.50f, WIDTH * 0.42f,
            intArrayOf((DEEP and 0x00FFFFFF) or 0x33000000, (DEEP and 0x00FFFFFF)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null

        val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val left = 72f

        // Sized to the text column rather than by eye, so a long line can never run into the picture.
        val lines = listOf("팔굽혀펴기 한 번에", "천장이 올라가요")
        val headline = fit(lines, TEXT_RIGHT - left, 64f, bold)

        text(canvas, "고냥이 지켜줘", left, 150f, 40f, DEEP, bold, spacing = 0.04f)
        text(canvas, lines[0], left, 246f, headline, TEXT, bold)
        text(canvas, lines[1], left, 246f + headline * 1.24f, headline, TEXT, bold)
        text(canvas, "카메라가 세는 홈트 · 영상은 저장되지 않아요", left, 404f, 24f, TEXT_2, Typeface.SANS_SERIF)

        // Kept clear of the right 10%: Play crops this asset differently across its surfaces. The cat
        // is sized from the panel's width, so the panel is wide rather than tall: a narrow one left a
        // small cat under a slab that filled half the picture.
        val panel = RectF(560f, 110f, 912f, 390f)
        paint.color = CAT_PANEL
        canvas.drawRoundRect(panel, 44f, 44f, paint)
        ShareCardRenderer.drawCeilingAndCat(canvas, panel)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = STROKE
        canvas.drawRoundRect(panel, 44f, 44f, paint)

        return bitmap
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
