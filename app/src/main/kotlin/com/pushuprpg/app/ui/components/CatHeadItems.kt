package com.pushuprpg.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.pushuprpg.core.progression.CatItem
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

/** On the head, whose centre is at [headY]: the ears stand from 0.62 of a head above it. */
internal fun DrawScope.drawHeadItem(item: CatItem, cx: Float, headY: Float, head: Float) {
    when (item) {
        CatItem.RIBBON -> {
            val c = Offset(cx - head * 0.52f, headY - head * 0.80f)
            drawOval(PINK, topLeft = Offset(c.x - head * 0.44f, c.y - head * 0.15f), size = Size(head * 0.42f, head * 0.30f))
            drawOval(PINK, topLeft = Offset(c.x + head * 0.02f, c.y - head * 0.15f), size = Size(head * 0.42f, head * 0.30f))
            drawCircle(PINK_DARK, radius = head * 0.10f, center = c)
        }
        CatItem.FLOWER -> {
            val c = Offset(cx + head * 0.58f, headY - head * 0.74f)
            repeat(5) { i ->
                val a = (i * 72f - 90f) * (PI.toFloat() / 180f)
                drawCircle(
                    Color(0xFFFFB3C7),
                    radius = head * 0.13f,
                    center = Offset(c.x + head * 0.15f * cos(a), c.y + head * 0.15f * sin(a)),
                )
            }
            drawCircle(GOLD, radius = head * 0.09f, center = c)
        }
        CatItem.BEANIE -> {
            drawArc(
                KNIT_RED,
                startAngle = 180f, sweepAngle = 180f, useCenter = true,
                topLeft = Offset(cx - head * 1.0f, headY - head * 1.34f),
                size = Size(head * 2.0f, head * 1.80f),
            )
            // The brim stops above the eyes even when fear has opened them wide (0.34 of a head up).
            drawRoundRect(
                KNIT_RED_DARK,
                topLeft = Offset(cx - head * 1.02f, headY - head * 0.58f),
                size = Size(head * 2.04f, head * 0.22f),
                cornerRadius = CornerRadius(head * 0.10f),
            )
            drawCircle(CREAM_WHITE, radius = head * 0.20f, center = Offset(cx, headY - head * 1.36f))
        }
        CatItem.STRAW_HAT -> {
            val straw = Color(0xFFE8C170)
            drawArc(
                straw,
                startAngle = 180f, sweepAngle = 180f, useCenter = true,
                topLeft = Offset(cx - head * 0.62f, headY - head * 1.34f),
                size = Size(head * 1.24f, head * 1.24f),
            )
            drawRect(KNIT_RED, topLeft = Offset(cx - head * 0.62f, headY - head * 0.86f), size = Size(head * 1.24f, head * 0.14f))
            drawOval(straw, topLeft = Offset(cx - head * 1.30f, headY - head * 0.92f), size = Size(head * 2.60f, head * 0.44f))
            drawOval(Color(0xFFCFA553), topLeft = Offset(cx - head * 1.30f, headY - head * 0.92f), size = Size(head * 2.60f, head * 0.44f), style = Stroke(width = head * 0.04f))
        }
        CatItem.PARTY_HAT -> {
            val base = headY - head * 0.86f
            val apex = Offset(cx + head * 0.10f, headY - head * 1.74f)
            val cone = Path().apply {
                moveTo(cx - head * 0.34f, base)
                lineTo(cx + head * 0.34f, base)
                lineTo(apex.x, apex.y)
                close()
            }
            drawPath(cone, color = BRAND)
            clipPath(cone) {
                listOf(0.25f, 0.55f).forEach { t ->
                    val y = base + (apex.y - base) * t
                    drawLine(GOLD, Offset(cx - head * 0.5f, y + head * 0.08f), Offset(cx + head * 0.5f, y - head * 0.08f), strokeWidth = head * 0.10f)
                }
            }
            drawCircle(PINK, radius = head * 0.13f, center = apex)
        }
        CatItem.CROWN -> {
            val base = headY - head * 0.84f
            val crown = Path().apply {
                moveTo(cx - head * 0.50f, base)
                lineTo(cx - head * 0.50f, base - head * 0.34f)
                lineTo(cx - head * 0.25f, base - head * 0.14f)
                lineTo(cx, base - head * 0.44f)
                lineTo(cx + head * 0.25f, base - head * 0.14f)
                lineTo(cx + head * 0.50f, base - head * 0.34f)
                lineTo(cx + head * 0.50f, base)
                close()
            }
            drawPath(crown, color = GOLD)
            drawPath(crown, color = GOLD_DARK, style = Stroke(width = head * 0.04f))
            drawCircle(PINK_DARK, radius = head * 0.07f, center = Offset(cx, base - head * 0.14f))
            drawCircle(Color(0xFF35A0E8), radius = head * 0.05f, center = Offset(cx - head * 0.30f, base - head * 0.07f))
            drawCircle(Color(0xFF35A0E8), radius = head * 0.05f, center = Offset(cx + head * 0.30f, base - head * 0.07f))
        }
        CatItem.HEADBAND -> {
            // Across the forehead, above eyes fear has opened wide, like a set's sweatband.
            drawRoundRect(
                Color(0xFF2EC27E),
                topLeft = Offset(cx - head * 0.98f, headY - head * 0.62f),
                size = Size(head * 1.96f, head * 0.22f),
                cornerRadius = CornerRadius(head * 0.10f),
            )
            drawLine(Color.White, Offset(cx - head * 0.9f, headY - head * 0.51f), Offset(cx + head * 0.9f, headY - head * 0.51f), strokeWidth = head * 0.05f)
        }
        CatItem.CHEF_HAT -> {
            val white = CREAM_WHITE
            drawRoundRect(
                white,
                topLeft = Offset(cx - head * 0.50f, headY - head * 1.12f),
                size = Size(head * 1.0f, head * 0.40f),
                cornerRadius = CornerRadius(head * 0.06f),
            )
            listOf(-0.36f, 0f, 0.36f).forEach { x ->
                drawCircle(white, radius = head * 0.30f, center = Offset(cx + head * x, headY - head * 1.30f))
            }
            drawLine(Color(0xFFD8D2C8), Offset(cx - head * 0.48f, headY - head * 0.84f), Offset(cx + head * 0.48f, headY - head * 0.84f), strokeWidth = head * 0.04f)
        }
        CatItem.WITCH_HAT -> {
            val purple = Color(0xFF5B3A8C)
            val base = headY - head * 0.80f
            drawPath(
                Path().apply {
                    moveTo(cx - head * 0.46f, base)
                    lineTo(cx + head * 0.46f, base)
                    quadraticBezierTo(cx + head * 0.20f, base - head * 0.60f, cx + head * 0.55f, base - head * 1.05f)
                    quadraticBezierTo(cx - head * 0.10f, base - head * 0.70f, cx - head * 0.46f, base)
                    close()
                },
                color = purple,
            )
            drawOval(purple, topLeft = Offset(cx - head * 1.0f, base - head * 0.10f), size = Size(head * 2.0f, head * 0.26f))
            drawRect(GOLD, topLeft = Offset(cx - head * 0.44f, base - head * 0.20f), size = Size(head * 0.88f, head * 0.10f))
        }
        else -> Unit
    }
}
