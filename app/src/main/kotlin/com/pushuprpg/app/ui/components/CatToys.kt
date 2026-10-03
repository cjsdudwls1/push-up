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
import androidx.compose.ui.graphics.drawscope.rotate
import com.pushuprpg.core.progression.CatItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The cat's toys, on the floor at its left: not worn, but the cat's own.
 *
 * [x] is the toy's middle and [floorY] the floor it stands on; [head] is the cat's head radius, the
 * unit everything about the cat is drawn in. A toy keeps within a head either side of [x] and under
 * 2.4 heads tall, so it never reaches the cat or past the edge of a picture; the cat tower, which
 * stands taller than the cat, is the one thing allowed up to 4.4.
 *
 * Each toy is drawn in its own frame: `p(dx, up)` is the point [dx] heads right of [x] and [up] heads
 * above the floor, so a toy is designed standing up rather than in screen coordinates. Its outline is
 * drawn inside that, which is why `sit`, half the outline, lifts a toy's bottom edge: the outline
 * rests on the floor rather than sinking into it.
 */
internal fun DrawScope.drawCatToy(item: CatItem, x: Float, floorY: Float, head: Float) {
    when (item) {
        CatItem.YARN -> drawYarn(x, floorY, head)
        CatItem.FISH_TOY -> drawFishToy(x, floorY, head)
        CatItem.TROPHY -> drawTrophy(x, floorY, head)
        CatItem.BALL -> drawBouncyBall(x, floorY, head)
        CatItem.MOUSE_TOY -> drawMouseToy(x, floorY, head)
        CatItem.BOX -> drawDeliveryBox(x, floorY, head)
        CatItem.CAT_TOWER -> drawCatTower(x, floorY, head)
        else -> Unit
    }
}

// 털실 뭉치: a ball of pink yarn wound with crossing strands, and a loose end trailing on the floor.
private fun DrawScope.drawYarn(x: Float, floorY: Float, head: Float) {
    val sit = 0.025f
    fun p(dx: Float, up: Float) = Offset(x + head * dx, floorY - head * (up + sit))
    val r = head * 0.70f
    val c = p(0.16f, 0.70f)

    // The loose end first, so the ball covers where it starts.
    val strand = Path().apply {
        moveTo(p(-0.38f, 0.25f))
        cubicTo(p(-0.44f, 0.13f), p(-0.50f, 0.055f), p(-0.62f, 0.055f))
        cubicTo(p(-0.74f, 0.055f), p(-0.76f, 0.18f), p(-0.82f, 0.18f))
        cubicTo(p(-0.88f, 0.18f), p(-0.86f, 0.055f), p(-0.91f, 0.055f))
    }
    drawPath(strand, color = PINK_DARK, style = Stroke(width = head * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(strand, color = PINK, style = Stroke(width = head * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    val ball = Path().apply { addOval(Rect(center = c, radius = r)) }
    drawPath(ball, color = YARN_SHADE)
    clipPath(ball) {
        // The lit side, then two sets of strands wound across each other.
        drawCircle(PINK, radius = r, center = Offset(c.x - r * 0.10f, c.y - r * 0.12f))
        listOf(-38f to PINK_DARK, 58f to YARN_LIGHT).forEach { (angle, color) ->
            rotate(angle, pivot = c) {
                listOf(-0.66f, -0.26f, 0.14f, 0.54f).forEach { k ->
                    val y = c.y + r * k
                    drawPath(
                        Path().apply {
                            moveTo(c.x - r * 1.1f, y)
                            quadraticTo(c.x, y + r * 0.7f, c.x + r * 1.1f, y)
                        },
                        color = color,
                        style = Stroke(width = head * 0.06f, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
    drawCircle(PINK_DARK, radius = r, center = c, style = Stroke(width = head * 0.05f))
    rotate(-40f, pivot = p(-0.20f, 1.12f)) {
        drawOval(Color(0xB3FFFFFF), topLeft = p(-0.31f, 1.19f), size = Size(head * 0.22f, head * 0.14f))
    }
}

// 물고기 인형: a blue plush fish lying on its side, nose to the cat, with a big eye, a smile and stitching.
private fun DrawScope.drawFishToy(x: Float, floorY: Float, head: Float) {
    fun p(dx: Float, up: Float) = Offset(x + head * dx, floorY - head * up)
    val line = head * 0.05f

    // The tail: two plump lobes off the same joint. Their outline goes down first, so the pair has one.
    val joint = p(-0.38f, 0.48f)
    fun lobes(draw: () -> Unit) = listOf(34f, -34f).forEach { angle -> rotate(angle, pivot = joint) { draw() } }
    lobes {
        drawOval(FISH_BLUE_DARK, topLeft = p(-1.00f, 0.65f), size = Size(head * 0.62f, head * 0.34f))
        drawOval(FISH_BLUE_DARK, topLeft = p(-1.00f, 0.65f), size = Size(head * 0.62f, head * 0.34f), style = Stroke(width = line * 2f))
    }
    lobes { drawOval(FISH_FIN, topLeft = p(-1.00f, 0.65f), size = Size(head * 0.62f, head * 0.34f)) }

    val topFin = Path().apply {
        moveTo(p(-0.05f, 0.88f))
        cubicTo(p(0.00f, 1.12f), p(0.12f, 1.24f), p(0.30f, 1.14f))
        cubicTo(p(0.32f, 1.06f), p(0.34f, 1.00f), p(0.36f, 0.90f))
        close()
    }
    drawPath(topFin, color = FISH_FIN)
    drawPath(topFin, color = FISH_BLUE_DARK, style = Stroke(width = line, join = StrokeJoin.Round))

    val body = Path().apply {
        moveTo(p(-0.46f, 0.46f))
        cubicTo(p(-0.28f, 0.78f), p(0.05f, 0.99f), p(0.40f, 0.99f))
        cubicTo(p(0.70f, 0.99f), p(0.92f, 0.80f), p(0.92f, 0.52f))
        cubicTo(p(0.92f, 0.24f), p(0.78f, 0.03f), p(0.53f, 0.03f))
        lineTo(p(0.22f, 0.03f))
        cubicTo(p(-0.08f, 0.03f), p(-0.30f, 0.20f), p(-0.46f, 0.46f))
        close()
    }
    drawPath(body, color = FISH_BLUE)
    clipPath(body) {
        // The pale belly.
        drawOval(FISH_BELLY, topLeft = p(-0.35f, 0.34f), size = Size(head * 1.7f, head * 0.80f))
    }
    drawPath(body, color = FISH_BLUE_DARK, style = Stroke(width = line, join = StrokeJoin.Round))

    // The stitched seam behind the head, and down each lobe of the tail.
    val seam = Stroke(width = head * 0.05f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(head * 0.10f, head * 0.10f)))
    drawPath(
        Path().apply {
            moveTo(p(0.24f, 0.90f))
            cubicTo(p(0.08f, 0.66f), p(0.08f, 0.34f), p(0.24f, 0.10f))
        },
        color = FISH_BLUE_DARK,
        style = seam,
    )
    lobes { drawLine(FISH_BLUE_DARK, p(-0.56f, 0.48f), p(-0.92f, 0.48f), strokeWidth = seam.width, cap = StrokeCap.Round, pathEffect = seam.pathEffect) }

    // A side fin.
    val fin = Path().apply {
        moveTo(p(0.10f, 0.42f))
        cubicTo(p(-0.10f, 0.50f), p(-0.26f, 0.34f), p(-0.28f, 0.20f))
        cubicTo(p(-0.10f, 0.18f), p(0.06f, 0.24f), p(0.10f, 0.42f))
        close()
    }
    drawPath(fin, color = FISH_FIN)
    drawPath(fin, color = FISH_BLUE_DARK, style = Stroke(width = line, join = StrokeJoin.Round))

    // The face: one big eye with a glint, a blush, and a smile.
    val eye = p(0.60f, 0.62f)
    drawCircle(Color.White, radius = head * 0.21f, center = eye)
    drawCircle(FISH_BLUE_DARK, radius = head * 0.21f, center = eye, style = Stroke(width = head * 0.035f))
    drawCircle(FISH_INK, radius = head * 0.13f, center = p(0.63f, 0.60f))
    drawCircle(Color.White, radius = head * 0.045f, center = p(0.67f, 0.65f))
    drawOval(FISH_BLUSH, topLeft = p(0.38f, 0.40f), size = Size(head * 0.20f, head * 0.12f))
    drawPath(
        Path().apply {
            moveTo(p(0.60f, 0.33f))
            quadraticTo(p(0.74f, 0.20f), p(0.87f, 0.38f))
        },
        color = FISH_INK,
        style = Stroke(width = head * 0.05f, cap = StrokeCap.Round),
    )
}

// 트로피: a golden cup with two handles and a star, on a stem and a dark base with a gold plate.
private fun DrawScope.drawTrophy(x: Float, floorY: Float, head: Float) {
    val line = head * 0.045f
    val sit = 0.0225f
    fun p(dx: Float, up: Float) = Offset(x + head * dx, floorY - head * (up + sit))

    // Base: two blocks, and the plate.
    fun block(left: Float, top: Float, width: Float, height: Float) {
        drawRoundRect(TROPHY_BASE, topLeft = p(left, top), size = Size(head * width, head * height), cornerRadius = CornerRadius(head * 0.05f))
        drawRoundRect(TROPHY_BASE_DARK, topLeft = p(left, top), size = Size(head * width, head * height), cornerRadius = CornerRadius(head * 0.05f), style = Stroke(width = line))
    }
    block(-0.54f, 0.22f, 1.08f, 0.22f)
    block(-0.40f, 0.42f, 0.80f, 0.20f)
    drawRoundRect(GOLD, topLeft = p(-0.20f, 0.17f), size = Size(head * 0.40f, head * 0.12f), cornerRadius = CornerRadius(head * 0.03f))

    // Stem, with its knob.
    drawRect(GOLD, topLeft = p(-0.10f, 0.82f), size = Size(head * 0.20f, head * 0.40f))
    drawRect(GOLD_DARK, topLeft = p(-0.10f, 0.82f), size = Size(head * 0.20f, head * 0.40f), style = Stroke(width = line))
    drawOval(GOLD, topLeft = p(-0.19f, 0.73f), size = Size(head * 0.38f, head * 0.14f))
    drawOval(GOLD_DARK, topLeft = p(-0.19f, 0.73f), size = Size(head * 0.38f, head * 0.14f), style = Stroke(width = line))

    // Handles, behind the cup: an outline tube, then the gold over it.
    listOf(-1f, 1f).forEach { side ->
        val handle = Path().apply {
            moveTo(p(side * 0.52f, 1.84f))
            cubicTo(p(side * 1.06f, 1.92f), p(side * 1.02f, 1.20f), p(side * 0.40f, 1.16f))
        }
        drawPath(handle, color = GOLD_DARK, style = Stroke(width = head * 0.17f, cap = StrokeCap.Round))
        drawPath(handle, color = GOLD, style = Stroke(width = head * 0.09f, cap = StrokeCap.Round))
    }

    // The cup, with a darker band down its far side.
    val cup = Path().apply {
        moveTo(p(-0.60f, 2.04f))
        cubicTo(p(-0.60f, 1.28f), p(-0.36f, 0.98f), p(-0.14f, 0.84f))
        lineTo(p(0.14f, 0.84f))
        cubicTo(p(0.36f, 0.98f), p(0.60f, 1.28f), p(0.60f, 2.04f))
        close()
    }
    drawPath(cup, color = GOLD)
    clipPath(cup) {
        drawPath(
            Path().apply {
                moveTo(p(0.34f, 2.08f))
                cubicTo(p(0.38f, 1.58f), p(0.30f, 1.18f), p(0.12f, 0.82f))
                lineTo(p(0.70f, 0.82f))
                lineTo(p(0.70f, 2.08f))
                close()
            },
            color = TROPHY_SHADE,
        )
    }
    drawPath(cup, color = GOLD_DARK, style = Stroke(width = line, join = StrokeJoin.Round))
    // The rim's mouth.
    drawOval(GOLD_DARK, topLeft = p(-0.62f, 2.15f), size = Size(head * 1.24f, head * 0.22f))
    drawOval(GOLD, topLeft = p(-0.62f, 2.15f), size = Size(head * 1.24f, head * 0.22f), style = Stroke(width = line * 1.2f))
    // The star, and a glint down the near side.
    val star = toyStar(p(-0.02f, 1.48f), head * 0.33f, head * 0.145f)
    drawPath(star, color = CREAM_WHITE)
    drawPath(star, color = GOLD_DARK, style = Stroke(width = head * 0.035f, join = StrokeJoin.Round))
    drawPath(
        Path().apply {
            moveTo(p(-0.46f, 1.92f))
            quadraticTo(p(-0.50f, 1.52f), p(-0.34f, 1.20f))
        },
        color = TROPHY_GLINT,
        style = Stroke(width = head * 0.08f, cap = StrokeCap.Round),
    )
}

// 탱탱볼: a violet rubber ball with a big yellow star and a bright highlight.
private fun DrawScope.drawBouncyBall(x: Float, floorY: Float, head: Float) {
    val sit = 0.025f
    fun p(dx: Float, up: Float) = Offset(x + head * dx, floorY - head * (up + sit))
    val r = head * 0.62f
    val c = p(0f, 0.62f)
    val ball = Path().apply { addOval(Rect(center = c, radius = r)) }
    drawPath(ball, color = BALL_VIOLET_DARK)
    clipPath(ball) {
        // The lit side.
        drawCircle(BRAND, radius = r, center = Offset(c.x - r * 0.10f, c.y - r * 0.12f))
    }
    drawCircle(BALL_VIOLET_DARK, radius = r, center = c, style = Stroke(width = head * 0.05f))
    rotate(-14f, pivot = c) {
        val star = toyStar(Offset(c.x + r * 0.04f, c.y + r * 0.08f), r * 0.56f, r * 0.26f)
        drawPath(star, color = GOLD)
        drawPath(star, color = GOLD_DARK, style = Stroke(width = head * 0.04f, join = StrokeJoin.Round))
    }
    rotate(-38f, pivot = p(-0.30f, 0.95f)) {
        drawOval(Color(0xB3FFFFFF), topLeft = p(-0.43f, 1.025f), size = Size(head * 0.26f, head * 0.15f))
    }
}

// 쥐돌이: a grey toy mouse facing the cat, with big pink ears, a pink nose and a long curly tail.
private fun DrawScope.drawMouseToy(x: Float, floorY: Float, head: Float) {
    val line = head * 0.05f
    val sit = 0.025f
    fun p(dx: Float, up: Float) = Offset(x + head * dx, floorY - head * (up + sit))

    // The tail first, curling up behind the rump.
    val tail = Path().apply {
        moveTo(p(-0.50f, 0.28f))
        cubicTo(p(-0.76f, 0.22f), p(-0.94f, 0.40f), p(-0.86f, 0.62f))
        cubicTo(p(-0.78f, 0.80f), p(-0.60f, 0.80f), p(-0.58f, 0.64f))
        cubicTo(p(-0.57f, 0.52f), p(-0.70f, 0.50f), p(-0.72f, 0.60f))
    }
    drawPath(tail, color = MOUSE_PINK_DARK, style = Stroke(width = head * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(tail, color = MOUSE_PINK, style = Stroke(width = head * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // The far ear, behind the body.
    drawCircle(MOUSE_GREY_DARK, radius = head * 0.29f, center = p(-0.06f, 1.00f))
    drawCircle(MOUSE_PINK, radius = head * 0.18f, center = p(-0.06f, 1.00f))

    val body = Path().apply {
        moveTo(p(0.88f, 0.20f))
        cubicTo(p(0.72f, 0.52f), p(0.42f, 0.96f), p(-0.08f, 0.96f))
        cubicTo(p(-0.38f, 0.96f), p(-0.56f, 0.74f), p(-0.56f, 0.48f))
        cubicTo(p(-0.56f, 0.20f), p(-0.36f, 0.00f), p(-0.10f, 0.00f))
        lineTo(p(0.62f, 0.00f))
        cubicTo(p(0.78f, 0.00f), p(0.86f, 0.08f), p(0.88f, 0.20f))
        close()
    }
    drawPath(body, color = MOUSE_GREY)
    drawPath(body, color = MOUSE_GREY_DARK, style = Stroke(width = line, join = StrokeJoin.Round))

    // The near ear, over the body.
    drawCircle(MOUSE_GREY, radius = head * 0.30f, center = p(0.30f, 0.92f))
    drawCircle(MOUSE_GREY_DARK, radius = head * 0.30f, center = p(0.30f, 0.92f), style = Stroke(width = line))
    drawCircle(MOUSE_PINK, radius = head * 0.19f, center = p(0.30f, 0.92f))

    // Face: whiskers swept back along the cheek, a big dot of an eye with its glint, the pink nose.
    drawLine(MOUSE_GREY_DARK, p(0.76f, 0.28f), p(0.52f, 0.34f), strokeWidth = head * 0.04f, cap = StrokeCap.Round)
    drawLine(MOUSE_GREY_DARK, p(0.76f, 0.22f), p(0.54f, 0.22f), strokeWidth = head * 0.04f, cap = StrokeCap.Round)
    drawCircle(MOUSE_INK, radius = head * 0.10f, center = p(0.58f, 0.52f))
    drawCircle(Color.White, radius = head * 0.035f, center = p(0.61f, 0.56f))
    drawCircle(MOUSE_PINK_DARK, radius = head * 0.115f, center = p(0.88f, 0.23f))
    drawCircle(MOUSE_PINK, radius = head * 0.085f, center = p(0.88f, 0.23f))
}

// 택배 상자: an open cardboard box, flaps flung wide, dark inside, taped, marked this way up, with a paw print.
private fun DrawScope.drawDeliveryBox(x: Float, floorY: Float, head: Float) {
    val line = head * 0.045f
    val sit = 0.0225f
    fun p(dx: Float, up: Float) = Offset(x + head * dx, floorY - head * (up + sit))

    fun panel(color: Color, vararg corners: Pair<Float, Float>) {
        val path = Path().apply {
            corners.forEachIndexed { i, (dx, up) -> if (i == 0) moveTo(p(dx, up)) else lineTo(p(dx, up)) }
            close()
        }
        drawPath(path, color = color)
        drawPath(path, color = BOX_DARK, style = Stroke(width = line, join = StrokeJoin.Round))
    }
    // Back flap, then the two at the sides, then the dark mouth between them.
    panel(BOX_FLAP, -0.58f to 1.30f, 0.58f to 1.30f, 0.50f to 1.55f, -0.50f to 1.55f)
    panel(BOX_FLAP, -0.74f to 0.90f, -0.60f to 1.30f, -0.84f to 1.52f, -0.97f to 1.14f)
    panel(BOX_FLAP, 0.74f to 0.90f, 0.60f to 1.30f, 0.84f to 1.52f, 0.97f to 1.14f)
    panel(BOX_INSIDE, -0.74f to 0.90f, 0.74f to 0.90f, 0.60f to 1.30f, -0.60f to 1.30f)

    // The front, with its own flap folded down over the top of it.
    drawRoundRect(BOX_KRAFT, topLeft = p(-0.74f, 0.90f), size = Size(head * 1.48f, head * 0.90f), cornerRadius = CornerRadius(head * 0.08f))
    drawRoundRect(BOX_DARK, topLeft = p(-0.74f, 0.90f), size = Size(head * 1.48f, head * 0.90f), cornerRadius = CornerRadius(head * 0.08f), style = Stroke(width = line))
    panel(BOX_FLAP, -0.74f to 0.90f, 0.74f to 0.90f, 0.78f to 0.66f, -0.78f to 0.66f)
    // The strip of tape down the middle, over the fold and torn off on a slant.
    val tape = Path().apply {
        moveTo(p(-0.11f, 0.90f))
        lineTo(p(0.11f, 0.90f))
        lineTo(p(0.11f, 0.46f))
        lineTo(p(-0.11f, 0.38f))
        close()
    }
    drawPath(tape, color = BOX_TAPE)
    drawPath(tape, color = BOX_TAPE_EDGE, style = Stroke(width = head * 0.03f, join = StrokeJoin.Round))
    // This way up: an arrow and its bar.
    drawLine(BOX_PRINT, p(-0.46f, 0.18f), p(-0.46f, 0.38f), strokeWidth = head * 0.10f)
    drawPath(
        Path().apply {
            moveTo(p(-0.46f, 0.56f))
            lineTo(p(-0.64f, 0.34f))
            lineTo(p(-0.28f, 0.34f))
            close()
        },
        color = BOX_PRINT,
    )
    drawLine(BOX_PRINT, p(-0.64f, 0.10f), p(-0.28f, 0.10f), strokeWidth = head * 0.07f, cap = StrokeCap.Round)
    // A paw print.
    drawOval(BOX_PRINT, topLeft = p(0.35f, 0.33f), size = Size(head * 0.30f, head * 0.22f))
    listOf(0.33f to 0.44f, 0.42f to 0.53f, 0.56f to 0.53f, 0.65f to 0.44f).forEach { (dx, up) ->
        drawCircle(BOX_PRINT, radius = head * 0.065f, center = p(dx, up))
    }
}

// 캣타워: a sisal-wrapped post on a carpeted base, two teal pads at different heights, a pompom on a string.
private fun DrawScope.drawCatTower(x: Float, floorY: Float, head: Float) {
    fun p(dx: Float, up: Float) = Offset(x + head * dx, floorY - head * up)
    val line = head * 0.045f

    // A stretch of the post, from [from] up to [to] heads above the floor: sisal rope, wrapped, with
    // a round foot where it stands on a pad.
    fun post(from: Float, to: Float) {
        val rope = Path().apply {
            addRect(Rect(p(-0.21f, to), p(0.21f, from)))
            addOval(Rect(p(-0.21f, from + 0.07f), p(0.21f, from - 0.07f)))
        }
        drawPath(rope, color = TOWER_SISAL)
        clipPath(rope) {
            var up = from + 0.02f
            while (up < to) {
                drawLine(TOWER_SISAL_DARK, p(-0.21f, up), p(0.21f, up + 0.09f), strokeWidth = head * 0.05f)
                up += 0.19f
            }
        }
        drawLine(TOWER_SISAL_DARK, p(-0.21f, from), p(-0.21f, to), strokeWidth = line)
        drawLine(TOWER_SISAL_DARK, p(0.21f, from), p(0.21f, to), strokeWidth = line)
        drawArc(
            TOWER_SISAL_DARK,
            startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = p(-0.21f, from + 0.07f), size = Size(head * 0.42f, head * 0.14f),
            style = Stroke(width = line),
        )
    }
    // A pad: a round slab seen from a little above, its side first and then its soft top.
    fun pad(cx: Float, cy: Float, rx: Float, ry: Float, thick: Float, face: Color, side: Color, edge: Color, sheen: Color) {
        val slab = Path().apply {
            addOval(Rect(p(cx - rx, cy - thick + ry), p(cx + rx, cy - thick - ry)))
            addRect(Rect(p(cx - rx, cy), p(cx + rx, cy - thick)))
            addOval(Rect(p(cx - rx, cy + ry), p(cx + rx, cy - ry)))
        }
        drawPath(slab, color = edge, style = Stroke(width = line * 2f, join = StrokeJoin.Round))
        drawPath(slab, color = side)
        val top = Rect(p(cx - rx, cy + ry), p(cx + rx, cy - ry))
        drawOval(face, topLeft = top.topLeft, size = top.size)
        drawArc(edge, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = top.topLeft, size = top.size, style = Stroke(width = line, cap = StrokeCap.Round))
        drawOval(sheen, topLeft = p(cx - rx * 0.74f, cy + ry * 0.55f), size = Size(head * rx * 1.48f, head * ry * 1.1f))
    }
    fun carpet(cx: Float, cy: Float, rx: Float, ry: Float, thick: Float) =
        pad(cx, cy, rx, ry, thick, TOWER_TEAL, TOWER_TEAL_DARK, TOWER_TEAL_EDGE, TOWER_TEAL_LIGHT)

    pad(0f, 0.485f, 0.95f, 0.20f, 0.24f, TOWER_BASE_FACE, TOWER_BASE, TOWER_BASE_DARK, TOWER_BASE_LIGHT)
    post(0.485f, 1.90f)
    carpet(-0.25f, 2.06f, 0.70f, 0.17f, 0.20f)
    post(2.06f, 4.05f)
    // The pompom hangs under the high pad, out past the low one.
    drawLine(TOWER_SISAL, p(0.62f, 3.95f), p(0.62f, 3.30f), strokeWidth = head * 0.045f)
    repeat(8) { i ->
        val a = i * (PI.toFloat() / 4f)
        drawCircle(PINK, radius = head * 0.09f, center = p(0.62f + 0.13f * cos(a), 3.14f + 0.13f * sin(a)))
    }
    drawCircle(PINK, radius = head * 0.17f, center = p(0.62f, 3.14f))
    drawCircle(YARN_LIGHT, radius = head * 0.05f, center = p(0.57f, 3.20f))
    carpet(0.22f, 4.18f, 0.73f, 0.17f, 0.20f)
}

/** A five-pointed star. */
private fun toyStar(c: Offset, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val a = (-90f + i * 36f) * (PI.toFloat() / 180f)
        val r = if (i % 2 == 0) outer else inner
        val point = Offset(c.x + r * cos(a), c.y + r * sin(a))
        if (i == 0) moveTo(point) else lineTo(point)
    }
    close()
}

private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineTo(o: Offset) = lineTo(o.x, o.y)
private fun Path.quadraticTo(c: Offset, o: Offset) = quadraticTo(c.x, c.y, o.x, o.y)
private fun Path.cubicTo(c1: Offset, c2: Offset, o: Offset) = cubicTo(c1.x, c1.y, c2.x, c2.y, o.x, o.y)

private val YARN_SHADE = Color(0xFFF2608A)
private val YARN_LIGHT = Color(0xFFFFB3C7)

private val FISH_BLUE = Color(0xFF3FA2EB)
private val FISH_BLUE_DARK = Color(0xFF2275B8)
private val FISH_FIN = Color(0xFF2F8CD6)
private val FISH_BELLY = Color(0xFFBFE4FA)
private val FISH_INK = Color(0xFF1F2A44)
private val FISH_BLUSH = Color(0xAAFF8FA8)

private val TROPHY_BASE = Color(0xFF5B3F33)
private val TROPHY_BASE_DARK = Color(0xFF3A271F)
private val TROPHY_SHADE = Color(0xFFE9A92F)
private val TROPHY_GLINT = Color(0xFFFFE9A0)

private val BALL_VIOLET_DARK = Color(0xFF5A4BD6)

private val MOUSE_GREY = Color(0xFFC4C8D3)
private val MOUSE_GREY_DARK = Color(0xFF7E8396)
private val MOUSE_PINK = Color(0xFFFFB3C7)
private val MOUSE_PINK_DARK = Color(0xFFE0527A)
private val MOUSE_INK = Color(0xFF2A2A33)

private val BOX_KRAFT = Color(0xFFD9A864)
private val BOX_FLAP = Color(0xFFC79651)
private val BOX_DARK = Color(0xFF8C5E2D)
private val BOX_INSIDE = Color(0xFF6B4524)
private val BOX_TAPE = Color(0xFFF3E1B6)
private val BOX_TAPE_EDGE = Color(0xFFC9A468)
private val BOX_PRINT = Color(0xFF8C5E2D)

private val TOWER_SISAL = Color(0xFFE9D3A2)
private val TOWER_SISAL_DARK = Color(0xFFB8975A)
private val TOWER_TEAL = Color(0xFF3FBFB4)
private val TOWER_TEAL_DARK = Color(0xFF2A9188)
private val TOWER_TEAL_EDGE = Color(0xFF1F7A72)
private val TOWER_TEAL_LIGHT = Color(0xFF6FD9CF)
private val TOWER_BASE_FACE = Color(0xFFCDBFB2)
private val TOWER_BASE = Color(0xFFB5A69A)
private val TOWER_BASE_DARK = Color(0xFF857569)
private val TOWER_BASE_LIGHT = Color(0xFFDDD1C6)
