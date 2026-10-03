package com.pushuprpg.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.pushuprpg.core.progression.CatItem
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.cos

/** On the head, whose centre is at [headY]: the ears stand from 0.62 of a head above it. */
internal fun DrawScope.drawHeadItem(item: CatItem, cx: Float, headY: Float, head: Float) {
    // A place in heads: [x] across from the middle, [up] above the head's centre.
    fun px(x: Float) = cx + head * x
    fun py(up: Float) = headY - head * up
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
                    quadraticTo(cx + head * 0.20f, base - head * 0.60f, cx + head * 0.55f, base - head * 1.05f)
                    quadraticTo(cx - head * 0.10f, base - head * 0.70f, cx - head * 0.46f, base)
                    close()
                },
                color = purple,
            )
            drawOval(purple, topLeft = Offset(cx - head * 1.0f, base - head * 0.10f), size = Size(head * 2.0f, head * 0.26f))
            drawRect(GOLD, topLeft = Offset(cx - head * 0.44f, base - head * 0.20f), size = Size(head * 0.88f, head * 0.10f))
        }
        CatItem.CAP -> {
            // A ball cap turned sideways, so its bill shows in profile on a face-on head: out to the left.
            drawPath(
                Path().apply {
                    moveTo(px(-0.62f), py(0.66f))
                    cubicTo(px(-0.98f), py(0.68f), px(-1.32f), py(0.62f), px(-1.54f), py(0.46f))
                    quadraticTo(px(-1.60f), py(0.34f), px(-1.46f), py(0.33f))
                    cubicTo(px(-1.20f), py(0.36f), px(-0.90f), py(0.40f), px(-0.62f), py(0.48f))
                    close()
                },
                color = CAP_BLUE_DARK,
            )
            val crown = Path().apply {
                moveTo(px(-0.90f), py(0.52f))
                cubicTo(px(-0.92f), py(1.02f), px(-0.48f), py(1.32f), px(0.02f), py(1.32f))
                cubicTo(px(0.54f), py(1.32f), px(0.92f), py(1.02f), px(0.90f), py(0.52f))
                quadraticTo(px(0f), py(0.38f), px(-0.90f), py(0.52f))
                close()
            }
            drawPath(crown, color = CAP_BLUE)
            clipPath(crown) {
                // The front panel, where a team puts its mark.
                drawPath(
                    Path().apply {
                        moveTo(px(0.02f), py(1.30f))
                        quadraticTo(px(-0.34f), py(1.00f), px(-0.36f), py(0.40f))
                        lineTo(px(-1.0f), py(0.40f))
                        lineTo(px(-1.0f), py(1.40f))
                        close()
                    },
                    color = CREAM_WHITE,
                )
            }
            val seam = Stroke(width = head * 0.04f)
            drawPath(Path().apply { moveTo(px(0.02f), py(1.30f)); quadraticTo(px(-0.34f), py(1.00f), px(-0.36f), py(0.45f)) }, color = CAP_BLUE_DARK, style = seam)
            drawPath(Path().apply { moveTo(px(0.02f), py(1.30f)); quadraticTo(px(0.38f), py(1.00f), px(0.42f), py(0.45f)) }, color = CAP_BLUE_DARK, style = seam)
            drawPath(Path().apply { moveTo(px(0.30f), py(1.21f)); quadraticTo(px(0.64f), py(1.12f), px(0.76f), py(0.86f)) }, color = CAP_SHEEN, style = Stroke(width = head * 0.05f, cap = StrokeCap.Round))
            drawPath(crown, color = CAP_BLUE_DARK, style = Stroke(width = head * 0.045f, join = StrokeJoin.Round))
            drawStar(Offset(px(-0.62f), py(0.87f)), head * 0.18f, CAP_BLUE_DARK)
            drawCircle(CAP_BLUE_DARK, radius = head * 0.08f, center = Offset(px(0.02f), py(1.32f)))
        }
        CatItem.PIRATE_HAT -> {
            // A black bicorne turned up at both sides, its brim folded up in front and trimmed in gold,
            // with a skull over crossbones above it.
            val hat = Path().apply {
                moveTo(px(-1.46f), py(0.98f))
                cubicTo(px(-1.28f), py(1.36f), px(-0.66f), py(1.42f), px(0f), py(1.42f))
                cubicTo(px(0.66f), py(1.42f), px(1.28f), py(1.36f), px(1.46f), py(0.98f))
                cubicTo(px(1.12f), py(0.62f), px(0.58f), py(0.44f), px(0f), py(0.44f))
                cubicTo(px(-0.58f), py(0.44f), px(-1.12f), py(0.62f), px(-1.46f), py(0.98f))
                close()
            }
            drawPath(hat, color = PIRATE_BLACK)
            val fold = Path().apply {
                moveTo(px(-1.46f), py(0.98f))
                cubicTo(px(-1.10f), py(0.86f), px(-0.56f), py(0.70f), px(0f), py(0.70f))
                cubicTo(px(0.56f), py(0.70f), px(1.10f), py(0.86f), px(1.46f), py(0.98f))
            }
            clipPath(hat) {
                drawPath(
                    Path().apply {
                        moveTo(px(-1.5f), py(0.98f))
                        cubicTo(px(-1.10f), py(0.86f), px(-0.56f), py(0.70f), px(0f), py(0.70f))
                        cubicTo(px(0.56f), py(0.70f), px(1.10f), py(0.86f), px(1.5f), py(0.98f))
                        lineTo(px(1.5f), py(0.2f))
                        lineTo(px(-1.5f), py(0.2f))
                        close()
                    },
                    color = PIRATE_BRIM,
                )
            }
            drawPath(fold, color = GOLD, style = Stroke(width = head * 0.04f))
            drawPath(hat, color = GOLD, style = Stroke(width = head * 0.07f, join = StrokeJoin.Round))
            listOf(-1f, 1f).forEach { s ->
                drawLine(CREAM_WHITE, Offset(px(-0.38f * s), py(1.24f)), Offset(px(0.38f * s), py(0.86f)), strokeWidth = head * 0.09f, cap = StrokeCap.Round)
                drawCircle(CREAM_WHITE, radius = head * 0.075f, center = Offset(px(-0.38f * s), py(1.24f)))
                drawCircle(CREAM_WHITE, radius = head * 0.075f, center = Offset(px(0.38f * s), py(0.86f)))
            }
            drawCircle(CREAM_WHITE, radius = head * 0.22f, center = Offset(px(0f), py(1.10f)))
            drawRoundRect(CREAM_WHITE, topLeft = Offset(px(-0.12f), py(0.98f)), size = Size(head * 0.24f, head * 0.20f), cornerRadius = CornerRadius(head * 0.05f))
            drawCircle(PIRATE_BLACK, radius = head * 0.06f, center = Offset(px(-0.09f), py(1.11f)))
            drawCircle(PIRATE_BLACK, radius = head * 0.06f, center = Offset(px(0.09f), py(1.11f)))
        }
        CatItem.FLOWER_CROWN -> {
            // A vine over the top of the forehead with flowers on it; the ears come up behind.
            val a = Offset(px(-0.84f), py(0.52f))
            val b = Offset(px(0.84f), py(0.52f))
            val ctrl = Offset(px(0f), py(1.40f))
            fun vine(t: Float): Offset {
                val u = 1f - t
                return Offset(
                    u * u * a.x + 2f * u * t * ctrl.x + t * t * b.x,
                    u * u * a.y + 2f * u * t * ctrl.y + t * t * b.y,
                )
            }
            drawPath(Path().apply { moveTo(a.x, a.y); quadraticTo(ctrl.x, ctrl.y, b.x, b.y) }, color = LEAF_DARK, style = Stroke(width = head * 0.12f, cap = StrokeCap.Round))
            // A pair of leaves in each gap, one pointing out over the head and one in.
            listOf(0.125f, 0.375f, 0.625f, 0.875f).forEach { t ->
                val from = vine(t)
                val ahead = vine(t + 0.02f)
                val behind = vine(t - 0.02f)
                val along = atan2(ahead.y - behind.y, ahead.x - behind.x) * (180f / PI.toFloat())
                drawLeaf(from, along - 50f, head * 0.46f, LEAF)
                drawLeaf(from, along + 50f, head * 0.46f, LEAF)
            }
            // (where on the vine, how big, petals)
            listOf(
                Triple(0.00f, 0.17f, CREAM_WHITE),
                Triple(0.25f, 0.21f, BLOSSOM_PINK),
                Triple(0.50f, 0.25f, BLOSSOM_YELLOW),
                Triple(0.75f, 0.21f, BLOSSOM_PINK),
                Triple(1.00f, 0.17f, CREAM_WHITE),
            ).forEach { (t, r, petal) ->
                val heart = if (petal == BLOSSOM_YELLOW) PINK else GOLD
                drawBlossom(vine(t), head * r, petal, heart, edge = head * 0.035f)
            }
        }
        CatItem.GRAD_CAP -> {
            // A mortarboard seen from a little above, on a skull-cap, with a gold tassel hanging at the right.
            val skull = Path().apply {
                moveTo(px(-0.84f), py(1.0f))
                lineTo(px(-0.80f), py(0.62f))
                quadraticTo(px(0f), py(0.36f), px(0.80f), py(0.62f))
                lineTo(px(0.84f), py(1.0f))
                close()
            }
            drawPath(skull, color = GRAD_BLACK)
            drawPath(skull, color = GRAD_RIM, style = Stroke(width = head * 0.035f, join = StrokeJoin.Round))
            fun board(drop: Float) = Path().apply {
                moveTo(px(-1.24f), py(1.02f - drop))
                lineTo(px(0.22f), py(1.42f - drop))
                lineTo(px(1.24f), py(1.20f - drop))
                lineTo(px(-0.22f), py(0.80f - drop))
                close()
            }
            drawPath(board(-0.12f), color = GRAD_EDGE)
            drawPath(board(0f), color = GRAD_BLACK)
            clipPath(board(0f)) {
                // The far half catches the light.
                drawPath(
                    Path().apply {
                        moveTo(px(-1.3f), py(1.0f))
                        lineTo(px(0.22f), py(1.5f))
                        lineTo(px(1.3f), py(1.22f))
                        close()
                    },
                    color = GRAD_SHEEN,
                )
            }
            drawPath(board(0f), color = GRAD_RIM, style = Stroke(width = head * 0.04f, join = StrokeJoin.Round))
            // The cord runs from the button over the board to its edge, where the tassel hangs.
            val edge = Offset(px(0.95f), py(1.12f))
            drawPath(
                Path().apply { moveTo(px(0f), py(1.11f)); quadraticTo(px(0.50f), py(1.02f), edge.x, edge.y) },
                color = GOLD,
                style = Stroke(width = head * 0.05f, cap = StrokeCap.Round),
            )
            drawLine(GOLD, edge, Offset(edge.x, py(0.88f)), strokeWidth = head * 0.055f, cap = StrokeCap.Round)
            val tassel = Path().apply {
                moveTo(px(0.89f), py(0.82f))
                lineTo(px(1.01f), py(0.82f))
                lineTo(px(1.09f), py(0.50f))
                quadraticTo(px(0.95f), py(0.42f), px(0.81f), py(0.50f))
                close()
            }
            drawPath(tassel, color = GOLD)
            drawPath(tassel, color = GOLD_DARK, style = Stroke(width = head * 0.035f, join = StrokeJoin.Round))
            drawLine(GOLD_DARK, Offset(px(0.915f), py(0.76f)), Offset(px(0.885f), py(0.52f)), strokeWidth = head * 0.035f)
            drawLine(GOLD_DARK, Offset(px(0.985f), py(0.76f)), Offset(px(1.015f), py(0.52f)), strokeWidth = head * 0.035f)
            drawCircle(GOLD, radius = head * 0.075f, center = Offset(edge.x, py(0.86f)))
            drawCircle(GOLD_DARK, radius = head * 0.075f, center = Offset(edge.x, py(0.86f)), style = Stroke(width = head * 0.035f))
            drawCircle(GOLD, radius = head * 0.08f, center = Offset(px(0f), py(1.11f)))
            drawCircle(GOLD_DARK, radius = head * 0.08f, center = Offset(px(0f), py(1.11f)), style = Stroke(width = head * 0.035f))
        }
        CatItem.HALO -> {
            // A golden ring floating between the ear tips; its light spills round it but not into the hole.
            val c = Offset(px(0f), py(1.45f))
            val rx = head * 0.46f
            val ry = head * 0.18f
            fun ring(width: Float, color: Color) =
                drawOval(color, topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2f, ry * 2f), style = Stroke(width = head * width))
            // The glow is one soft disc of light, flattened to the ring's tilt.
            scale(1f, 0.55f, pivot = c) {
                drawCircle(
                    Brush.radialGradient(
                        0.00f to GLOW.copy(alpha = 0f),
                        0.28f to GLOW.copy(alpha = 0.12f),
                        0.50f to GLOW.copy(alpha = 0.62f),
                        0.68f to GLOW.copy(alpha = 0.28f),
                        1.00f to GLOW.copy(alpha = 0f),
                        center = c, radius = head * 0.92f,
                    ),
                    radius = head * 0.92f,
                    center = c,
                )
            }
            ring(0.16f, GOLD_DARK)
            ring(0.09f, GOLD)
            drawArc(
                GLOW_LIGHT,
                startAngle = 115f, sweepAngle = 50f, useCenter = false,
                topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = head * 0.04f, cap = StrokeCap.Round),
            )
            drawTwinkle(Offset(px(-0.98f), py(1.60f)), head * 0.17f, GLOW_LIGHT)
            drawTwinkle(Offset(px(1.02f), py(1.74f)), head * 0.12f, GLOW_LIGHT)
            drawTwinkle(Offset(px(-0.64f), py(1.80f)), head * 0.08f, GLOW_LIGHT)
        }
        CatItem.FROG_HAT -> {
            // A knit frog hat: the beanie's dome and band, with the eyes up on top where the ears are.
            drawArc(
                FROG_GREEN,
                startAngle = 180f, sweepAngle = 180f, useCenter = true,
                topLeft = Offset(px(-1.0f), py(1.30f)),
                size = Size(head * 2.0f, head * 1.72f),
            )
            drawRoundRect(
                FROG_GREEN_DARK,
                topLeft = Offset(px(-1.02f), py(0.58f)),
                size = Size(head * 2.04f, head * 0.22f),
                cornerRadius = CornerRadius(head * 0.10f),
            )
            // The ribbing of the brim, as knit stitches.
            for (i in -5..5) {
                drawLine(FROG_GREEN, Offset(px(i * 0.17f), py(0.54f)), Offset(px(i * 0.17f), py(0.41f)), strokeWidth = head * 0.04f, cap = StrokeCap.Round)
            }
            listOf(-1f, 1f).forEach { s ->
                drawCircle(FROG_GREEN_DARK, radius = head * 0.045f, center = Offset(px(0.11f * s), py(1.02f)))
                drawOval(FROG_CHEEK, topLeft = Offset(px(0.64f * s - 0.14f), py(0.86f)), size = Size(head * 0.28f, head * 0.16f))
                val e = Offset(px(0.50f * s), py(1.30f))
                drawCircle(FROG_GREEN, radius = head * 0.40f, center = e)
                drawCircle(FROG_GREEN_DARK, radius = head * 0.40f, center = e, style = Stroke(width = head * 0.045f))
                drawCircle(Color.White, radius = head * 0.29f, center = Offset(e.x, e.y + head * 0.02f))
                val pupil = Offset(e.x - s * head * 0.04f, e.y - head * 0.02f)
                drawCircle(PIRATE_BLACK, radius = head * 0.15f, center = pupil)
                drawCircle(Color.White, radius = head * 0.055f, center = Offset(pupil.x + head * 0.05f, pupil.y - head * 0.06f))
            }
        }
        CatItem.HEADPHONES -> {
            // A band resting on the head, in front of the ears' feet so their tips stay up, and a big cup each side.
            val topLeft = Offset(px(-1.07f), py(1.07f))
            val size = Size(head * 2.14f, head * 2.14f)
            drawArc(VIOLET_DARK, startAngle = 201f, sweepAngle = 138f, useCenter = false, topLeft = topLeft, size = size, style = Stroke(width = head * 0.20f))
            drawArc(BRAND, startAngle = 201f, sweepAngle = 138f, useCenter = false, topLeft = topLeft, size = size, style = Stroke(width = head * 0.12f))
            drawArc(
                VIOLET_LIGHT,
                startAngle = 232f, sweepAngle = 40f, useCenter = false,
                topLeft = Offset(px(-1.10f), py(1.10f)), size = Size(head * 2.20f, head * 2.20f),
                style = Stroke(width = head * 0.04f, cap = StrokeCap.Round),
            )
            listOf(-1f, 1f).forEach { s ->
                val cup = Offset(px(1.08f * s), py(0.16f))
                drawCircle(VIOLET_DARK, radius = head * 0.33f, center = Offset(px(1.00f * s), py(0.16f)))
                drawCircle(BRAND, radius = head * 0.29f, center = cup)
                drawCircle(VIOLET_DARK, radius = head * 0.29f, center = cup, style = Stroke(width = head * 0.04f))
                drawCircle(VIOLET_LIGHT, radius = head * 0.12f, center = cup)
            }
        }
        else -> Unit
    }
}

private val CAP_BLUE = Color(0xFF3A86E8)
private val CAP_BLUE_DARK = Color(0xFF2559B0)
private val CAP_SHEEN = Color(0xFF7DB6F5)
private val PIRATE_BLACK = Color(0xFF2B2B36)
private val PIRATE_BRIM = Color(0xFF42425A)
private val LEAF = Color(0xFF58B85A)
private val LEAF_DARK = Color(0xFF3A8F45)
private val BLOSSOM_PINK = Color(0xFFFF8FB1)
private val BLOSSOM_YELLOW = Color(0xFFFFD84D)
private val GRAD_BLACK = Color(0xFF33343F)
private val GRAD_EDGE = Color(0xFF1B1C24)
private val GRAD_SHEEN = Color(0xFF3F4050)
private val GRAD_RIM = Color(0xFF6C6E82)
private val GLOW = Color(0xFFFFE066)
private val GLOW_LIGHT = Color(0xFFFFF6C8)
private val FROG_GREEN = Color(0xFF6CC24A)
private val FROG_GREEN_DARK = Color(0xFF449A3A)
private val FROG_CHEEK = Color(0xFFFF9DB5)
private val VIOLET_DARK = Color(0xFF4B3FB5)
private val VIOLET_LIGHT = Color(0xFFA79CFF)

/** A pointed leaf: from [base], [len] long, [deg] clockwise from pointing right. */
private fun DrawScope.drawLeaf(base: Offset, deg: Float, len: Float, color: Color) {
    rotate(deg, pivot = base) {
        drawPath(
            Path().apply {
                moveTo(base.x, base.y)
                quadraticTo(base.x + len * 0.5f, base.y - len * 0.30f, base.x + len, base.y)
                quadraticTo(base.x + len * 0.5f, base.y + len * 0.30f, base.x, base.y)
                close()
            },
            color = color,
        )
    }
}

/** A five-petalled flower of radius [r], each petal edged by [edge] in a shade darker than itself. */
private fun DrawScope.drawBlossom(c: Offset, r: Float, petal: Color, heart: Color, edge: Float) {
    val pr = r * 0.46f
    val reach = r - pr
    val rim = Color(
        red = petal.red * 0.80f, green = petal.green * 0.78f, blue = petal.blue * 0.80f, alpha = 1f,
    )
    repeat(5) { i ->
        val a = (i * 72f - 90f) * (PI.toFloat() / 180f)
        drawCircle(rim, radius = pr + edge, center = Offset(c.x + reach * cos(a), c.y + reach * sin(a)))
    }
    repeat(5) { i ->
        val a = (i * 72f - 90f) * (PI.toFloat() / 180f)
        drawCircle(petal, radius = pr, center = Offset(c.x + reach * cos(a), c.y + reach * sin(a)))
    }
    drawCircle(heart, radius = r * 0.30f, center = c)
}

/** A five-pointed star of outer radius [r], one point up. */
private fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    drawPath(
        Path().apply {
            repeat(10) { i ->
                val a = (i * 36f - 90f) * (PI.toFloat() / 180f)
                val d = if (i % 2 == 0) r else r * 0.44f
                if (i == 0) moveTo(c.x + d * cos(a), c.y + d * sin(a)) else lineTo(c.x + d * cos(a), c.y + d * sin(a))
            }
            close()
        },
        color = color,
    )
}

/** A four-pointed sparkle of radius [r]. */
private fun DrawScope.drawTwinkle(c: Offset, r: Float, color: Color) {
    drawPath(
        Path().apply {
            moveTo(c.x, c.y - r)
            quadraticTo(c.x, c.y, c.x + r, c.y)
            quadraticTo(c.x, c.y, c.x, c.y + r)
            quadraticTo(c.x, c.y, c.x - r, c.y)
            quadraticTo(c.x, c.y, c.x, c.y - r)
            close()
        },
        color = color,
    )
}
