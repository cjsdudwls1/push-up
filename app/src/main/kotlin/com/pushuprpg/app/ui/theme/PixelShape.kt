package com.pushuprpg.app.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A corner cut in stairs rather than rounded: the pixel demo's look, which the owner chose for the
 * whole app (2026-11-10). Each corner is up to three steps of 3 dp, about as many as [radius] would
 * have rounded off; a corner of 0 stays square. Steps land on whole pixels so the edge stays crisp.
 */
class PixelShape(
    private val topStart: Dp,
    private val topEnd: Dp = topStart,
    private val bottomEnd: Dp = topStart,
    private val bottomStart: Dp = topStart,
) : Shape {
    constructor(radius: Dp) : this(radius, radius, radius, radius)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val half = minOf(w, h) / 2f
        val ltr = layoutDirection == LayoutDirection.Ltr
        fun corner(r: Dp): Pair<Int, Float> = with(density) {
            val rPx = r.toPx()
            if (rPx <= 0f || half <= 0f) return 0 to 0f
            if (rPx >= half) {
                // Round, as near as stairs go: an octagon's worth of steps, each bigger on a big shape.
                val s = maxOf(3.dp.toPx(), half / 6f).roundToInt().coerceAtLeast(1).toFloat()
                return floor(half * 0.6f / s).toInt().coerceIn(1, 4) to s
            }
            val s = 3.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
            val n = (rPx / 8.dp.toPx()).roundToInt().coerceIn(1, 3)
            minOf(n, floor(half / s).toInt()) to s
        }
        val tl = corner(if (ltr) topStart else topEnd)
        val tr = corner(if (ltr) topEnd else topStart)
        val br = corner(if (ltr) bottomEnd else bottomStart)
        val bl = corner(if (ltr) bottomStart else bottomEnd)

        val p = Path()
        // Clockwise from the top left, each corner as stairs of n steps of s.
        p.moveTo(tl.first * tl.second, 0f)
        run {
            val (n, s) = tr
            p.lineTo(w - n * s, 0f)
            for (k in n - 1 downTo 0) {
                p.lineTo(w - k * s, (n - 1 - k) * s)
                p.lineTo(w - k * s, (n - k) * s)
            }
        }
        run {
            val (n, s) = br
            p.lineTo(w, h - n * s)
            for (k in n - 1 downTo 0) {
                p.lineTo(w - (n - 1 - k) * s, h - k * s)
                p.lineTo(w - (n - k) * s, h - k * s)
            }
        }
        run {
            val (n, s) = bl
            p.lineTo(n * s, h)
            for (k in n - 1 downTo 0) {
                p.lineTo(k * s, h - (n - 1 - k) * s)
                p.lineTo(k * s, h - (n - k) * s)
            }
        }
        run {
            val (n, s) = tl
            p.lineTo(0f, n * s)
            for (k in n - 1 downTo 0) {
                p.lineTo((n - 1 - k) * s, k * s)
                p.lineTo((n - k) * s, k * s)
            }
        }
        p.close()
        return Outline.Generic(p)
    }
}

/** What was a circle: as round as stairs go. */
val PixelCircle = PixelShape(9999.dp)
