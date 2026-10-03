package com.pushuprpg.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.pushuprpg.core.progression.CatItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A collar's worth, just under the chin at [chinY]. */
internal fun DrawScope.drawNeckItem(item: CatItem, cx: Float, chinY: Float, head: Float) {
    when (item) {
        CatItem.BELL -> {
            drawRoundRect(
                KNIT_RED,
                topLeft = Offset(cx - head * 0.62f, chinY - head * 0.07f),
                size = Size(head * 1.24f, head * 0.14f),
                cornerRadius = CornerRadius(head * 0.07f),
            )
            drawCircle(GOLD, radius = head * 0.17f, center = Offset(cx, chinY + head * 0.15f))
            drawCircle(GOLD_DARK, radius = head * 0.17f, center = Offset(cx, chinY + head * 0.15f), style = Stroke(width = head * 0.035f))
            drawLine(GOLD_DARK, Offset(cx, chinY + head * 0.17f), Offset(cx, chinY + head * 0.30f), strokeWidth = head * 0.04f, cap = StrokeCap.Round)
            drawCircle(GOLD_DARK, radius = head * 0.04f, center = Offset(cx, chinY + head * 0.15f))
        }
        CatItem.BOW_TIE -> {
            val y = chinY + head * 0.08f
            listOf(-1f, 1f).forEach { side ->
                drawPath(
                    Path().apply {
                        moveTo(cx, y)
                        lineTo(cx + side * head * 0.36f, y - head * 0.19f)
                        lineTo(cx + side * head * 0.36f, y + head * 0.19f)
                        close()
                    },
                    color = BRAND,
                )
            }
            drawCircle(Color(0xFF5A4BD6), radius = head * 0.09f, center = Offset(cx, y))
        }
        CatItem.SCARF -> {
            val blue = Color(0xFF35A0E8)
            // The tail first, so the loop around the neck covers where it starts.
            drawRoundRect(
                blue,
                topLeft = Offset(cx + head * 0.16f, chinY),
                size = Size(head * 0.28f, head * 0.72f),
                cornerRadius = CornerRadius(head * 0.08f),
            )
            drawLine(Color.White, Offset(cx + head * 0.16f, chinY + head * 0.62f), Offset(cx + head * 0.44f, chinY + head * 0.62f), strokeWidth = head * 0.06f)
            drawRoundRect(
                blue,
                topLeft = Offset(cx - head * 0.72f, chinY - head * 0.13f),
                size = Size(head * 1.44f, head * 0.28f),
                cornerRadius = CornerRadius(head * 0.14f),
            )
            listOf(-0.4f, 0f, 0.4f).forEach { x ->
                drawLine(
                    Color.White,
                    Offset(cx + head * x - head * 0.05f, chinY - head * 0.12f),
                    Offset(cx + head * x + head * 0.05f, chinY + head * 0.14f),
                    strokeWidth = head * 0.06f,
                )
            }
        }
        CatItem.BANDANA -> {
            val red = Color(0xFFD93A3A)
            drawPath(
                Path().apply {
                    moveTo(cx - head * 0.66f, chinY - head * 0.08f)
                    lineTo(cx + head * 0.66f, chinY - head * 0.08f)
                    lineTo(cx, chinY + head * 0.52f)
                    close()
                },
                color = red,
            )
            listOf(-0.3f to 0.08f, 0.25f to 0.12f, 0f to 0.3f, -0.05f to 0.05f, 0.4f to 0.0f).forEach { (x, y) ->
                drawCircle(Color.White, radius = head * 0.04f, center = Offset(cx + head * x * 0.8f, chinY + head * y))
            }
        }
        CatItem.MEDAL -> {
            drawPath(
                Path().apply {
                    moveTo(cx - head * 0.42f, chinY - head * 0.08f)
                    lineTo(cx - head * 0.10f, chinY + head * 0.40f)
                    lineTo(cx + head * 0.10f, chinY + head * 0.40f)
                    lineTo(cx + head * 0.42f, chinY - head * 0.08f)
                    lineTo(cx + head * 0.24f, chinY - head * 0.08f)
                    lineTo(cx, chinY + head * 0.26f)
                    lineTo(cx - head * 0.24f, chinY - head * 0.08f)
                    close()
                },
                color = Color(0xFF35A0E8),
            )
            val c = Offset(cx, chinY + head * 0.56f)
            drawCircle(GOLD, radius = head * 0.20f, center = c)
            drawCircle(GOLD_DARK, radius = head * 0.20f, center = c, style = Stroke(width = head * 0.04f))
            drawCircle(GOLD_DARK, radius = head * 0.09f, center = c, style = Stroke(width = head * 0.03f))
        }
        CatItem.WHISTLE -> {
            // A coach's whistle on a green lanyard: the mouthpiece to the left, the ring at the top.
            val cord = Color(0xFF2DBE74)
            val cordDark = Color(0xFF1C8F55)
            val steel = Color(0xFFDDE2EA)
            val steelShade = Color(0xFFAEB8C7)
            val steelDark = Color(0xFF66728A)
            val lanyard = Path().apply {
                moveTo(cx - head * 0.66f, chinY - head * 0.10f)
                quadraticTo(cx, chinY + head * 0.34f, cx + head * 0.66f, chinY - head * 0.10f)
            }
            drawPath(lanyard, cordDark, style = Stroke(width = head * 0.13f, cap = StrokeCap.Round))
            drawPath(lanyard, cord, style = Stroke(width = head * 0.075f, cap = StrokeCap.Round))
            val w = Offset(cx + head * 0.07f, chinY + head * 0.34f)
            val mouth = Offset(w.x - head * 0.52f, w.y - head * 0.02f)
            val mouthSize = Size(head * 0.56f, head * 0.21f)
            drawRoundRect(steel, topLeft = mouth, size = mouthSize, cornerRadius = CornerRadius(head * 0.08f))
            drawRoundRect(steelShade, topLeft = Offset(mouth.x, mouth.y + head * 0.13f), size = Size(mouthSize.width, head * 0.08f), cornerRadius = CornerRadius(head * 0.04f))
            drawRoundRect(steelDark, topLeft = mouth, size = mouthSize, cornerRadius = CornerRadius(head * 0.08f), style = Stroke(width = head * 0.045f))
            drawCircle(steel, radius = head * 0.19f, center = w)
            drawArc(
                steelShade,
                startAngle = 10f, sweepAngle = 130f, useCenter = false,
                topLeft = Offset(w.x - head * 0.14f, w.y - head * 0.14f),
                size = Size(head * 0.28f, head * 0.28f),
                style = Stroke(width = head * 0.07f),
            )
            drawCircle(steelDark, radius = head * 0.19f, center = w, style = Stroke(width = head * 0.045f))
            // The slot the air comes out of, and the shine along the top.
            drawRoundRect(steelDark, topLeft = Offset(w.x - head * 0.19f, w.y - head * 0.17f), size = Size(head * 0.17f, head * 0.07f), cornerRadius = CornerRadius(head * 0.03f))
            drawArc(
                Color.White,
                startAngle = 205f, sweepAngle = 55f, useCenter = false,
                topLeft = Offset(w.x - head * 0.125f, w.y - head * 0.125f),
                size = Size(head * 0.25f, head * 0.25f),
                style = Stroke(width = head * 0.045f, cap = StrokeCap.Round),
            )
            drawLine(Color.White, Offset(mouth.x + head * 0.10f, mouth.y + head * 0.05f), Offset(mouth.x + head * 0.34f, mouth.y + head * 0.05f), strokeWidth = head * 0.045f, cap = StrokeCap.Round)
            drawCircle(steelDark, radius = head * 0.065f, center = Offset(cx, chinY + head * 0.12f), style = Stroke(width = head * 0.045f))
        }
        CatItem.NECKTIE -> {
            // A navy tie with pale diagonal stripes, the knot under the chin between two collar points.
            val navy = Color(0xFF2B3C7E)
            val navyDark = Color(0xFF1B2757)
            val stripe = Color(0xFF8FA6EA)
            val shirt = Color(0xFFFAF8F4)
            val shirtDark = Color(0xFFB8B2A6)
            listOf(-1f, 1f).forEach { side ->
                val collar = Path().apply {
                    moveTo(cx + side * head * 0.04f, chinY - head * 0.08f)
                    lineTo(cx + side * head * 0.52f, chinY - head * 0.08f)
                    lineTo(cx + side * head * 0.35f, chinY + head * 0.25f)
                    close()
                }
                drawPath(collar, shirt)
                drawPath(collar, shirtDark, style = Stroke(width = head * 0.035f, join = StrokeJoin.Round))
            }
            val blade = Path().apply {
                moveTo(cx - head * 0.09f, chinY + head * 0.14f)
                lineTo(cx + head * 0.09f, chinY + head * 0.14f)
                lineTo(cx + head * 0.21f, chinY + head * 0.62f)
                lineTo(cx, chinY + head * 0.78f)
                lineTo(cx - head * 0.21f, chinY + head * 0.62f)
                close()
            }
            drawPath(blade, navy)
            clipPath(blade) {
                listOf(0.22f, 0.38f, 0.54f, 0.70f).forEach { y ->
                    drawLine(stripe, Offset(cx - head * 0.24f, chinY + head * (y - 0.08f)), Offset(cx + head * 0.24f, chinY + head * (y + 0.08f)), strokeWidth = head * 0.055f)
                }
            }
            drawPath(blade, navyDark, style = Stroke(width = head * 0.035f, join = StrokeJoin.Round))
            val knot = Path().apply {
                moveTo(cx - head * 0.15f, chinY - head * 0.05f)
                lineTo(cx + head * 0.15f, chinY - head * 0.05f)
                lineTo(cx + head * 0.10f, chinY + head * 0.17f)
                lineTo(cx - head * 0.10f, chinY + head * 0.17f)
                close()
            }
            drawPath(knot, navy)
            drawPath(knot, navyDark, style = Stroke(width = head * 0.035f, join = StrokeJoin.Round))
            drawLine(stripe, Offset(cx - head * 0.07f, chinY + head * 0.03f), Offset(cx + head * 0.07f, chinY + head * 0.03f), strokeWidth = head * 0.04f, cap = StrokeCap.Round)
        }
        CatItem.PEARLS -> {
            // Nine pearls hung in an arc, the middle one a little larger.
            val shade = Color(0xFFB4A592)
            // The arc is part of a circle, so that equal steps round it are equal gaps between pearls.
            val top = Offset(cx, chinY - head * 0.35f)
            val rho = head * 0.65f
            val n = 9
            val order = listOf(0, 8, 1, 7, 2, 6, 3, 5, 4)
            order.forEach { i ->
                val a = (-67.4f + 134.8f * i / (n - 1)) * (PI.toFloat() / 180f)
                val p = Offset(top.x + rho * sin(a), top.y + rho * cos(a))
                val r = head * if (i == n / 2) 0.12f else 0.09f
                drawCircle(Color(0xFFFFFAF0), radius = r, center = p)
                drawCircle(shade, radius = r, center = p, style = Stroke(width = head * 0.035f))
                drawCircle(Color.White, radius = head * 0.032f, center = Offset(p.x - r * 0.33f, p.y - r * 0.33f))
            }
        }
        CatItem.SPORTS_TOWEL -> {
            // A white towel round the neck: a soft band, fatter at the sides and folded along its length, and two
            // ends hanging down the chest at different lengths and angles, each with a shaded edge, two red
            // stripes and a short fringe. The band is drawn over the ends so that they come out from under it.
            val white = Color(0xFFFBFCFF)
            val behind = Color(0xFFF1F4F9)
            val shade = Color(0x2E5A6C8C)
            val edge = Color(0xFFA9B3C4)
            val red = Color(0xFFE0443C)
            fun x(u: Float) = cx + head * u
            fun y(v: Float) = chinY + head * v
            // An end hangs [len] heads from the middle of its top edge, [deg] off straight down.
            fun end(top: Offset, deg: Float, len: Float, fill: Color) {
                val a = deg * (PI.toFloat() / 180f)
                val ax = sin(a)
                val ay = cos(a)
                val w = 0.215f
                val r = 0.07f
                // A point [along] the end and [across] it, to the right of its axis.
                fun at(along: Float, across: Float) = Offset(top.x + head * (ax * along + ay * across), top.y + head * (ay * along - ax * across))
                val shape = Path().apply {
                    val tl = at(0f, -w)
                    val tr = at(0f, w)
                    val rightKnee = at(len - r, w)
                    val rightCorner = at(len, w)
                    val rightFoot = at(len, w - r)
                    val leftFoot = at(len, -w + r)
                    val leftCorner = at(len, -w)
                    val leftKnee = at(len - r, -w)
                    moveTo(tl.x, tl.y)
                    lineTo(tr.x, tr.y)
                    lineTo(rightKnee.x, rightKnee.y)
                    quadraticTo(rightCorner.x, rightCorner.y, rightFoot.x, rightFoot.y)
                    lineTo(leftFoot.x, leftFoot.y)
                    quadraticTo(leftCorner.x, leftCorner.y, leftKnee.x, leftKnee.y)
                    close()
                }
                drawPath(shape, fill)
                clipPath(shape) {
                    listOf(len - 0.21f, len - 0.115f).forEach { s ->
                        drawLine(red, at(s, -w - 0.05f), at(s, w + 0.05f), strokeWidth = head * 0.055f)
                    }
                    // Light from the upper left leaves the right edge in shade.
                    drawLine(shade, at(-0.05f, w - 0.02f), at(len + 0.05f, w - 0.02f), strokeWidth = head * 0.12f)
                }
                drawPath(shape, edge, style = Stroke(width = head * 0.035f, join = StrokeJoin.Round))
                repeat(6) { i ->
                    val across = -w + 0.05f + i * (2f * w - 0.10f) / 5f
                    drawLine(edge, at(len - 0.02f, across), at(len + 0.075f, across), strokeWidth = head * 0.04f, cap = StrokeCap.Round)
                }
            }
            end(Offset(x(0.19f), y(0.05f)), 7f, 0.62f, behind)
            end(Offset(x(-0.18f), y(0.05f)), -4f, 0.50f, white)
            val band = Path().apply {
                moveTo(x(-0.60f), y(-0.20f))
                quadraticTo(x(0f), y(0.10f), x(0.60f), y(-0.20f))
                cubicTo(x(0.74f), y(-0.20f), x(0.82f), y(0.04f), x(0.62f), y(0.17f))
                quadraticTo(x(0f), y(0.23f), x(-0.62f), y(0.17f))
                cubicTo(x(-0.82f), y(0.04f), x(-0.74f), y(-0.20f), x(-0.60f), y(-0.20f))
                close()
            }
            drawPath(band, white)
            clipPath(band) {
                val fold = Path().apply {
                    moveTo(x(-0.80f), y(-0.05f))
                    quadraticTo(x(0f), y(0.21f), x(0.80f), y(-0.05f))
                }
                val under = Path().apply {
                    addPath(fold)
                    lineTo(x(0.80f), y(0.40f))
                    lineTo(x(-0.80f), y(0.40f))
                    close()
                }
                drawPath(under, shade)
                drawPath(fold, edge, style = Stroke(width = head * 0.035f, cap = StrokeCap.Round))
            }
            drawPath(band, edge, style = Stroke(width = head * 0.035f, join = StrokeJoin.Round))
        }
        CatItem.LEI -> {
            // A Hawaiian lei: pink, orange, yellow and white flowers on a strand of leaves.
            val leaf = Color(0xFF3CA95A)
            val leafDark = Color(0xFF1F7A40)
            val top = Offset(cx, chinY - head * 0.4525f)
            val rho = head * 0.8125f
            val n = 9
            fun at(i: Int, r: Float): Offset {
                val a = (-59.5f + 119f * i / (n - 1f)) * (PI.toFloat() / 180f)
                return Offset(top.x + r * sin(a), top.y + r * cos(a))
            }
            drawArc(
                leaf,
                startAngle = 90f - 59.5f, sweepAngle = 119f, useCenter = false,
                topLeft = Offset(top.x - rho, top.y - rho), size = Size(rho * 2f, rho * 2f),
                style = Stroke(width = head * 0.16f, cap = StrokeCap.Round),
            )
            // Leaves between the flowers, pointing out and down.
            for (i in 0 until n - 1) {
                val a = (-59.5f + 119f * (i + 0.5f) / (n - 1f)) * (PI.toFloat() / 180f)
                val dx = sin(a)
                val dy = cos(a)
                val base = Offset(top.x + rho * 0.97f * dx, top.y + rho * 0.97f * dy)
                val tip = Offset(base.x + head * 0.27f * dx + head * 0.06f * dy, base.y + head * 0.27f * dy - head * 0.06f * dx)
                val leafPath = Path().apply {
                    moveTo(base.x, base.y)
                    quadraticTo((base.x + tip.x) / 2f + head * 0.09f * dy, (base.y + tip.y) / 2f - head * 0.09f * dx, tip.x, tip.y)
                    quadraticTo((base.x + tip.x) / 2f - head * 0.09f * dy, (base.y + tip.y) / 2f + head * 0.09f * dx, base.x, base.y)
                    close()
                }
                drawPath(leafPath, leaf)
                drawPath(leafPath, leafDark, style = Stroke(width = head * 0.03f, join = StrokeJoin.Round))
            }
            val petals = listOf(Color(0xFFFFB3C7), Color(0xFFFF9A3C), GOLD, CREAM_WHITE)
            val edges = listOf(PINK_DARK, Color(0xFFD9701A), GOLD_DARK, Color(0xFFC9BFAE))
            val order = listOf(0, 8, 1, 7, 2, 6, 3, 5, 4)
            order.forEach { i ->
                val kind = listOf(2, 0, 3, 1, 0, 1, 3, 0, 2)[i]
                val c = at(i, rho)
                repeat(5) { k ->
                    val a = (k * 72f - 90f) * (PI.toFloat() / 180f)
                    drawCircle(edges[kind], radius = head * 0.085f, center = Offset(c.x + head * 0.085f * cos(a), c.y + head * 0.085f * sin(a)))
                }
                repeat(5) { k ->
                    val a = (k * 72f - 90f) * (PI.toFloat() / 180f)
                    drawCircle(petals[kind], radius = head * 0.065f, center = Offset(c.x + head * 0.085f * cos(a), c.y + head * 0.085f * sin(a)))
                }
                drawCircle(if (kind == 2 || kind == 3) PINK else GOLD, radius = head * 0.05f, center = c)
            }
        }
        else -> Unit
    }
}
