package com.pushuprpg.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.pushuprpg.core.progression.CatItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Over the eyes, which sit at [eyeY], ±0.36 of a head apart. */
internal fun DrawScope.drawFaceItem(item: CatItem, cx: Float, eyeY: Float, head: Float) {
    when (item) {
        CatItem.GLASSES -> {
            listOf(-1f, 1f).forEach { side ->
                drawCircle(
                    FRAME,
                    radius = head * 0.24f,
                    center = Offset(cx + side * head * 0.36f, eyeY),
                    style = Stroke(width = head * 0.06f),
                )
            }
            drawLine(FRAME, Offset(cx - head * 0.12f, eyeY - head * 0.03f), Offset(cx + head * 0.12f, eyeY - head * 0.03f), strokeWidth = head * 0.05f)
        }
        CatItem.SUNGLASSES -> {
            listOf(-1f, 1f).forEach { side ->
                // Tall enough to cover an eye fear has opened wide.
                val lens = Offset(cx + side * head * 0.36f - head * 0.28f, eyeY - head * 0.21f)
                drawRoundRect(
                    Color(0xF0202028),
                    topLeft = lens,
                    size = Size(head * 0.56f, head * 0.42f),
                    cornerRadius = CornerRadius(head * 0.15f),
                )
                drawLine(
                    Color(0x80FFFFFF),
                    Offset(lens.x + head * 0.10f, lens.y + head * 0.08f),
                    Offset(lens.x + head * 0.22f, lens.y + head * 0.08f),
                    strokeWidth = head * 0.04f,
                    cap = StrokeCap.Round,
                )
            }
            drawLine(Color(0xF0202028), Offset(cx - head * 0.12f, eyeY - head * 0.06f), Offset(cx + head * 0.12f, eyeY - head * 0.06f), strokeWidth = head * 0.06f)
        }
        CatItem.HEART_GLASSES -> {
            listOf(-1f, 1f).forEach { side ->
                val c = Offset(cx + side * head * 0.36f, eyeY)
                val r = head * 0.26f
                val heart = Path().apply {
                    moveTo(c.x, c.y + r * 0.9f)
                    cubicTo(c.x - r * 1.3f, c.y + r * 0.1f, c.x - r * 0.9f, c.y - r * 1.0f, c.x, c.y - r * 0.35f)
                    cubicTo(c.x + r * 0.9f, c.y - r * 1.0f, c.x + r * 1.3f, c.y + r * 0.1f, c.x, c.y + r * 0.9f)
                    close()
                }
                drawPath(heart, color = Color(0xD0FF6F91))
                drawPath(heart, color = PINK_DARK, style = Stroke(width = head * 0.05f))
            }
            drawLine(PINK_DARK, Offset(cx - head * 0.12f, eyeY - head * 0.04f), Offset(cx + head * 0.12f, eyeY - head * 0.04f), strokeWidth = head * 0.05f)
        }
        CatItem.GOGGLES -> {
            // Swimming goggles: a tinted cup over each eye, a short bridge, and a strap that wraps round the head.
            val frame = Color(0xFF2F9BE8)
            val frameDark = Color(0xFF1B64B0)
            clipPath(headOutline(cx, eyeY, head)) {
                listOf(-1f, 1f).forEach { side ->
                    val from = Offset(cx + side * head * 0.62f, eyeY - head * 0.02f)
                    val to = Offset(cx + side * head * 1.10f, eyeY - head * 0.20f)
                    drawLine(frameDark, from, to, strokeWidth = head * 0.15f)
                    drawLine(frame, from, to, strokeWidth = head * 0.08f)
                }
            }
            listOf(-1f, 1f).forEach { side ->
                val c = Offset(cx + side * head * 0.385f, eyeY)
                // Tall enough that an eye fear has opened wide still sits in the rim.
                val cupTopLeft = Offset(c.x - head * 0.29f, c.y - head * 0.26f)
                val cup = Size(head * 0.58f, head * 0.52f)
                val corner = CornerRadius(head * 0.24f)
                drawRoundRect(Color(0xA07FD2FF), topLeft = cupTopLeft, size = cup, cornerRadius = corner)
                drawRoundRect(
                    frameDark,
                    topLeft = Offset(cupTopLeft.x - head * 0.05f, cupTopLeft.y - head * 0.05f),
                    size = Size(cup.width + head * 0.10f, cup.height + head * 0.10f),
                    cornerRadius = CornerRadius(head * 0.29f),
                    style = Stroke(width = head * 0.035f),
                )
                drawRoundRect(frame, topLeft = cupTopLeft, size = cup, cornerRadius = corner, style = Stroke(width = head * 0.065f))
                drawArc(
                    Color(0xDDFFFFFF),
                    startAngle = 200f, sweepAngle = 50f, useCenter = false,
                    topLeft = Offset(c.x - head * 0.19f, c.y - head * 0.19f),
                    size = Size(head * 0.38f, head * 0.38f),
                    style = Stroke(width = head * 0.04f, cap = StrokeCap.Round),
                )
            }
            drawRoundRect(frame, topLeft = Offset(cx - head * 0.11f, eyeY - head * 0.10f), size = Size(head * 0.22f, head * 0.09f), cornerRadius = CornerRadius(head * 0.04f))
        }
        CatItem.BLUSH -> {
            // Strong pink cheeks under and outside the eyes, clear of the mouth, each with three shine strokes.
            listOf(-1f, 1f).forEach { side ->
                val c = Offset(cx + side * head * 0.63f, eyeY + head * 0.32f)
                drawOval(Color(0x66FF5F86), topLeft = Offset(c.x - head * 0.27f, c.y - head * 0.155f), size = Size(head * 0.54f, head * 0.31f))
                drawOval(Color(0xD0FF5F86), topLeft = Offset(c.x - head * 0.225f, c.y - head * 0.125f), size = Size(head * 0.45f, head * 0.25f))
                // Leaning out at the top, the way the cheeks of a drawn face are hatched.
                listOf(-0.10f, 0f, 0.10f).forEach { dx ->
                    drawLine(
                        Color(0xE6FFFFFF),
                        Offset(c.x + head * dx - side * head * 0.04f, c.y + head * 0.055f),
                        Offset(c.x + head * dx + side * head * 0.04f, c.y - head * 0.055f),
                        strokeWidth = head * 0.042f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        CatItem.MONOCLE -> {
            // A gold rim round one eye, glassy, with a chain that hangs down the cheek and round the side of the head.
            val c = Offset(cx + head * 0.36f, eyeY)
            val r = head * 0.30f
            val chain = Path().apply {
                moveTo(c.x + r * 0.55f, c.y + r * 0.84f)
                cubicTo(cx + head * 0.50f, eyeY + head * 0.58f, cx + head * 0.78f, eyeY + head * 0.64f, cx + head * 1.06f, eyeY + head * 0.46f)
            }
            clipPath(headOutline(cx, eyeY, head)) {
                drawPath(chain, GOLD_DARK, style = Stroke(width = head * 0.08f, cap = StrokeCap.Round))
                // Dashed, so it reads as links and not as a wire.
                drawPath(chain, GOLD, style = Stroke(width = head * 0.045f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(head * 0.075f, head * 0.04f))))
            }
            drawCircle(Color(0x28BFE8FF), radius = r, center = c)
            drawCircle(GOLD_DARK, radius = r, center = c, style = Stroke(width = head * 0.125f))
            drawCircle(GOLD, radius = r, center = c, style = Stroke(width = head * 0.055f))
            drawArc(
                Color(0xCCFFFFFF),
                startAngle = 200f, sweepAngle = 50f, useCenter = false,
                topLeft = Offset(c.x - head * 0.19f, c.y - head * 0.19f),
                size = Size(head * 0.38f, head * 0.38f),
                style = Stroke(width = head * 0.04f, cap = StrokeCap.Round),
            )
        }
        CatItem.MUSTACHE -> {
            // A dark handlebar under the nose, fat in the middle and swept up at the tips. A notch under the
            // nose leaves the top of a gaping mouth showing.
            val brown = Color(0xFF4E2D16)
            val brownDark = Color(0xFF2B160A)
            val shine = Color(0xFF8A5A33)
            listOf(-1f, 1f).forEach { side ->
                fun x(u: Float) = cx + side * head * u
                fun y(v: Float) = eyeY + head * v
                val half = Path().apply {
                    moveTo(x(0f), y(0.38f))
                    cubicTo(x(0.10f), y(0.345f), x(0.26f), y(0.37f), x(0.38f), y(0.385f))
                    cubicTo(x(0.50f), y(0.40f), x(0.62f), y(0.36f), x(0.66f), y(0.25f))
                    cubicTo(x(0.66f), y(0.36f), x(0.60f), y(0.50f), x(0.46f), y(0.55f))
                    cubicTo(x(0.36f), y(0.585f), x(0.22f), y(0.62f), x(0.12f), y(0.58f))
                    cubicTo(x(0.07f), y(0.56f), x(0.03f), y(0.51f), x(0f), y(0.47f))
                    close()
                }
                drawPath(half, brown)
                drawPath(half, brownDark, style = Stroke(width = head * 0.035f, join = StrokeJoin.Round))
                val gloss = Path().apply {
                    moveTo(x(0.12f), y(0.425f))
                    quadraticTo(x(0.28f), y(0.44f), x(0.42f), y(0.46f))
                }
                drawPath(gloss, shine, style = Stroke(width = head * 0.04f, cap = StrokeCap.Round))
            }
        }
        CatItem.STAR_GLASSES -> {
            // Party glasses: a gold star round each eye, the points of the two almost meeting at the bridge.
            listOf(-1f, 1f).forEach { side ->
                val c = Offset(cx + side * head * 0.40f, eyeY)
                val star = Path().apply {
                    repeat(10) { i ->
                        val a = (i * 36f - 90f) * (PI.toFloat() / 180f)
                        val r = head * if (i % 2 == 0) 0.40f else 0.26f
                        val px = c.x + r * cos(a)
                        val py = c.y + r * sin(a)
                        if (i == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                drawPath(star, GOLD_DARK, style = Stroke(width = head * 0.13f, join = StrokeJoin.Round))
                drawPath(star, GOLD, style = Stroke(width = head * 0.06f, join = StrokeJoin.Round))
            }
            drawLine(GOLD, Offset(cx - head * 0.05f, eyeY - head * 0.12f), Offset(cx + head * 0.05f, eyeY - head * 0.12f), strokeWidth = head * 0.06f)
        }
        else -> Unit
    }
}

/** The head's outline, to clip what wraps round it. The eyes sit 0.10 of a head above its centre. */
private fun headOutline(cx: Float, eyeY: Float, head: Float) = Path().apply {
    addOval(Rect(center = Offset(cx, eyeY + head * 0.10f), radius = head))
}
