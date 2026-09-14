@file:Suppress("unused")

package android.graphics

import java.awt.BasicStroke
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.OutputStream
import javax.imageio.ImageIO
import java.awt.Color as AwtColor

/**
 * Just enough of android.graphics to run [com.pushuprpg.app.share.ShareCardRenderer] on a JVM.
 *
 * This exists because :app cannot be compiled in every environment the repo is worked on, and the
 * share card is the one screen whose whole job is to look right to a stranger. Reviewing it by
 * reading coordinates does not work. Backed by Java2D, which shares enough of the model (device
 * pixels, ARGB, fill/stroke, gradients, baselined text) that the layout carries over; it is a
 * proof of the *composition*, not a pixel-exact emulator. Fonts in particular differ — Android
 * picks Noto Sans CJK KR and this picks whatever the machine has.
 */

class RectF(
    @JvmField var left: Float,
    @JvmField var top: Float,
    @JvmField var right: Float,
    @JvmField var bottom: Float,
) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f
    internal fun toRect(): Rectangle2D.Float =
        Rectangle2D.Float(left, top, width(), height())
}

open class Shader {
    enum class TileMode { CLAMP, REPEAT, MIRROR }
}

class LinearGradient(
    val x0: Float, val y0: Float, val x1: Float, val y1: Float,
    val colors: IntArray, val positions: FloatArray?, val tile: TileMode,
) : Shader()

class RadialGradient(
    val cx: Float, val cy: Float, val radius: Float,
    val colors: IntArray, val positions: FloatArray?, val tile: TileMode,
) : Shader()

class Typeface private constructor(val family: String, val style: Int) {
    companion object {
        const val NORMAL = 0
        const val BOLD = 1

        @JvmField val SANS_SERIF = Typeface(defaultFamily(), NORMAL)
        @JvmField val DEFAULT = SANS_SERIF

        fun create(family: Typeface, style: Int): Typeface = Typeface(family.family, style)

        /** Overridable because the only Hangul-capable family varies by machine. */
        private fun defaultFamily(): String =
            System.getenv("PUSHUP_PREVIEW_FONT") ?: "WenQuanYi Zen Hei"
    }
}

class Paint(@Suppress("UNUSED_PARAMETER") flags: Int = 0) {

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        private val METRICS_IMAGE = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        internal val METRICS_G: Graphics2D = METRICS_IMAGE.createGraphics().apply {
            setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
            )
        }
    }

    enum class Align { LEFT, CENTER, RIGHT }
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }

    var color: Int = 0xFF000000.toInt()
    var textSize: Float = 12f
    var typeface: Typeface? = null
    var textAlign: Align = Align.LEFT
    var letterSpacing: Float = 0f
    var style: Style = Style.FILL
    var strokeWidth: Float = 0f
    var strokeCap: Cap = Cap.BUTT
    var isSubpixelText: Boolean = false
    var shader: Shader? = null

    internal fun awtFont(): Font {
        val face = typeface ?: Typeface.SANS_SERIF
        val base = Font(face.family, if (face.style == Typeface.BOLD) Font.BOLD else Font.PLAIN, 1)
            .deriveFont(textSize)
        if (letterSpacing == 0f) return base
        return base.deriveFont(mapOf(TextAttribute.TRACKING to letterSpacing))
    }

    fun measureText(text: String): Float =
        METRICS_G.getFontMetrics(awtFont()).stringWidth(text).toFloat()

    fun ascent(): Float = -METRICS_G.getFontMetrics(awtFont()).ascent.toFloat()

    fun descent(): Float = METRICS_G.getFontMetrics(awtFont()).descent.toFloat()
}

class Path {
    enum class Direction { CW, CCW }

    internal val path = Path2D.Float()

    fun reset() = path.reset()
    fun moveTo(x: Float, y: Float) = path.moveTo(x, y)
    fun lineTo(x: Float, y: Float) = path.lineTo(x, y)
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float) = path.quadTo(cx, cy, x, y)
    fun close() = path.closePath()

    fun addRoundRect(rect: RectF, rx: Float, ry: Float, @Suppress("UNUSED_PARAMETER") dir: Direction) {
        path.append(
            RoundRectangle2D.Float(rect.left, rect.top, rect.width(), rect.height(), rx * 2, ry * 2),
            false,
        )
    }
}

class Bitmap internal constructor(@JvmField val image: BufferedImage) {

    enum class Config { ARGB_8888 }
    enum class CompressFormat { PNG, JPEG }

    companion object {
        fun createBitmap(width: Int, height: Int, @Suppress("UNUSED_PARAMETER") config: Config): Bitmap =
            Bitmap(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB))
    }

    fun compress(format: CompressFormat, @Suppress("UNUSED_PARAMETER") quality: Int, out: OutputStream): Boolean =
        ImageIO.write(image, format.name.lowercase(), out)

    fun recycle() = Unit
}

class Canvas(bitmap: Bitmap) {

    private val g: Graphics2D = bitmap.image.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    }

    private val clips = ArrayDeque<java.awt.Shape?>()

    fun save(): Int {
        clips.addLast(g.clip)
        return clips.size
    }

    fun restore() {
        g.clip = clips.removeLastOrNull()
    }

    fun clipPath(path: Path) = g.clip(path.path)

    fun clipRect(rect: RectF) = g.clip(rect.toRect())

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) =
        draw(Rectangle2D.Float(left, top, right - left, bottom - top), paint)

    fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) = draw(
        RoundRectangle2D.Float(rect.left, rect.top, rect.width(), rect.height(), rx * 2, ry * 2),
        paint,
    )

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) =
        draw(Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2), paint)

    fun drawPath(path: Path, paint: Paint) = draw(path.path, paint)

    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) {
        apply(paint)
        g.stroke = stroke(paint)
        g.draw(Line2D.Float(x0, y0, x1, y1))
    }

    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        apply(paint)
        g.font = paint.awtFont()
        val width = paint.measureText(text)
        val left = when (paint.textAlign) {
            Paint.Align.LEFT -> x
            Paint.Align.CENTER -> x - width / 2f
            Paint.Align.RIGHT -> x - width
        }
        g.drawString(text, left, y)
    }

    private fun draw(shape: java.awt.Shape, paint: Paint) {
        apply(paint)
        when (paint.style) {
            Paint.Style.FILL -> g.fill(shape)
            Paint.Style.STROKE -> {
                g.stroke = stroke(paint)
                g.draw(shape)
            }
            Paint.Style.FILL_AND_STROKE -> {
                g.fill(shape)
                g.stroke = stroke(paint)
                g.draw(shape)
            }
        }
    }

    private fun stroke(paint: Paint) = BasicStroke(
        paint.strokeWidth.coerceAtLeast(0.01f),
        when (paint.strokeCap) {
            Paint.Cap.ROUND -> BasicStroke.CAP_ROUND
            Paint.Cap.SQUARE -> BasicStroke.CAP_SQUARE
            Paint.Cap.BUTT -> BasicStroke.CAP_BUTT
        },
        BasicStroke.JOIN_ROUND,
    )

    private fun apply(paint: Paint) {
        val shader = paint.shader
        g.paint = when (shader) {
            is LinearGradient -> LinearGradientPaint(
                Point2D.Float(shader.x0, shader.y0),
                Point2D.Float(shader.x1, shader.y1),
                shader.positions ?: evenly(shader.colors.size),
                shader.colors.map(::awtColor).toTypedArray(),
                MultipleGradientPaint.CycleMethod.NO_CYCLE,
            )
            is RadialGradient -> RadialGradientPaint(
                Point2D.Float(shader.cx, shader.cy),
                shader.radius,
                shader.positions ?: evenly(shader.colors.size),
                shader.colors.map(::awtColor).toTypedArray(),
                MultipleGradientPaint.CycleMethod.NO_CYCLE,
            )
            else -> awtColor(paint.color)
        }
    }

    private fun evenly(count: Int) = FloatArray(count) { it.toFloat() / (count - 1).coerceAtLeast(1) }

    private fun awtColor(argb: Int) = AwtColor(
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF,
        (argb ushr 24) and 0xFF,
    )
}
