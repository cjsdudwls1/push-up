package com.pushuprpg.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Brush
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.WearSlot
import com.pushuprpg.core.survival.CatMood
import kotlin.math.PI
import kotlin.math.sin

/** The colours of one coat. Pattern colours are null for a coat that has none. */
data class CoatColors(
    val fur: Color,
    val earInner: Color,
    /** Nose, mouth and outline strokes — drawn on [muzzle] where there is one, else on [fur]. */
    val ink: Color,
    val whisker: Color,
    /** The eye: a dark eye on a light coat, a coloured iris on a dark one. */
    val eye: Color,
    /** A pupil inside [eye], for a coat whose eye is coloured. */
    val pupil: Color? = null,
    /** Tabby stripes. */
    val stripe: Color? = null,
    /** A lighter chin and cheeks — a tuxedo's white face, a tabby's pale muzzle. */
    val muzzle: Color? = null,
    /** A tuxedo's shirt front and socks. */
    val bib: Color? = null,
    /** Calico patches. */
    val patchA: Color? = null,
    val patchB: Color? = null,
    /**
     * A rim around the silhouette, for a coat that would otherwise vanish into the dark room the
     * cat is drawn in. Without it a tuxedo is two eyes and a shirt front.
     */
    val rim: Color? = null,
)

fun coatColors(coat: CatCoat): CoatColors = when (coat) {
    CatCoat.CREAM -> CoatColors(
        fur = Color(0xFFF3D9A8), earInner = Color(0xFFE0A88C), ink = Color(0xFF3A2A18),
        whisker = Color(0xFF3A2A18), eye = Color(0xFF3A2A18),
    )
    CatCoat.CHEESE -> CoatColors(
        fur = Color(0xFFF2B25C), earInner = Color(0xFFF0A08A), ink = Color(0xFF3A2A18),
        whisker = Color(0xFF3A2A18), eye = Color(0xFF3A2A18),
        stripe = Color(0xFFD27A26), muzzle = Color(0xFFFBE3BE),
    )
    CatCoat.MACKEREL -> CoatColors(
        fur = Color(0xFFABA79D), earInner = Color(0xFFD9A3A0), ink = Color(0xFF2A2724),
        whisker = Color(0xFF2A2724), eye = Color(0xFF2A2724),
        stripe = Color(0xFF55524C), muzzle = Color(0xFFE9E5DC),
    )
    CatCoat.TUXEDO -> CoatColors(
        fur = Color(0xFF2A2A2F), earInner = Color(0xFFE3A1A4), ink = Color(0xFF2A2724),
        whisker = Color(0xFFE8E4DA), eye = Color(0xFFC9D46A), pupil = Color(0xFF1A1A1A),
        muzzle = Color(0xFFF4F1EA), bib = Color(0xFFF4F1EA), rim = Color(0xFF6A6A74),
    )
    CatCoat.CALICO -> CoatColors(
        fur = Color(0xFFF7F2E8), earInner = Color(0xFFE7A3A6), ink = Color(0xFF3A2A18),
        whisker = Color(0xFF3A2A18), eye = Color(0xFF3A2A18),
        patchA = Color(0xFFE39A4C), patchB = Color(0xFF34302C),
    )
}

/**
 * 고양이.
 *
 * The same cat the share card draws, and that is the reason it is not just three circles: this is
 * the mascot, and the first thing anyone who has not installed the app ever sees. The parts that
 * make it read as a cat rather than a snowman — tall swept ears, whiskers, a tail — cost a handful
 * of draw calls and are worth every one.
 *
 * Its whole emotional read is carried by shape, so it survives greyscale and a glance:
 * - [mood] is the stage of fear [com.pushuprpg.core.survival.CatCompanion] decided — the tail stops
 *   swaying, the body crouches and trembles, the eyes go wide and then wet, and at the end the paws
 *   go up as if to hold the ceiling off.
 * - [alarm] is the continuous closeness, for what should move smoothly between stages: the ears.
 * - [cheer] is the moment after a push: eyes close into a smile and the cheeks flush.
 * - [phase] is a free-running 0..1 animation clock for the sway and the tremble; hold it at 0 to
 *   keep the cat still, which is what reduced motion does.
 * - [wear] is what it has on — the gifts it found — drawn with it so they move as it does. A scene
 *   is not worn; see [drawCatScene].
 * - [tail] false leaves the tail off, for a picture framed from the chest up: all of the tail it
 *   would show is its tip, poking in from the edge on its own.
 */
fun DrawScope.drawCat(
    centerX: Float,
    baseY: Float,
    scale: Float,
    coat: CatCoat = CatCoat.CREAM,
    mood: CatMood = CatMood.CALM,
    alarm: Float = 0f,
    cheer: Float = 0f,
    phase: Float = 0f,
    wear: Set<CatItem> = emptySet(),
    tail: Boolean = true,
) {
    val c = coatColors(coat)
    val tau = 2f * PI.toFloat()

    val tremble = when (mood) {
        CatMood.SCARED -> 1.2f
        CatMood.PANIC -> 2.6f
        else -> 0f
    } * scale * sin(phase * tau * 23f)
    val cx = centerX + tremble

    val crouch = crouchFor(mood)
    val body = 46f * scale
    val bodyH = body * 1.05f * crouch
    val head = 30f * scale
    val headY = baseY - bodyH - head * 0.72f
    val frightened = mood == CatMood.SCARED || mood == CatMood.PANIC

    // Tail first: the body edge hides where it joins. It sways while the cat is at ease, and puffs
    // up and stiffens once it is frightened.
    val rimWidth = head * 0.14f
    if (tail) {
        val sway = if (mood == CatMood.CALM) sin(phase * tau * 2f) * 0.16f else 0f
        val tailWidth = head * if (frightened) 0.52f else 0.30f
        val tailPath = Path().apply {
            moveTo(cx + body * 0.82f, baseY - body * 0.18f)
            cubicTo(
                cx + body * 1.62f, baseY - body * 0.30f,
                cx + body * (1.55f + sway * 0.5f), baseY - body * 0.74f,
                cx + body * (1.48f + sway), baseY - body * 0.98f,
            )
            cubicTo(
                cx + body * (1.36f + sway * 1.6f), baseY - body * 1.46f,
                cx + body * (1.16f + sway * 2f), baseY - body * 1.44f,
                cx + body * (0.98f + sway * 2f), baseY - body * (if (frightened) 1.62f else 1.40f),
            )
        }
        c.rim?.let { drawPath(tailPath, color = it, style = Stroke(width = tailWidth + rimWidth, cap = StrokeCap.Round)) }
        drawPath(tailPath, color = c.patchA ?: c.fur, style = Stroke(width = tailWidth, cap = StrokeCap.Round))
        c.stripe?.let { s ->
            // A ringed tail reads as a tabby from across the room.
            drawPath(
                tailPath,
                color = s,
                style = Stroke(
                    width = tailWidth,
                    cap = StrokeCap.Butt,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(head * 0.16f, head * 0.26f), head * 0.3f),
                ),
            )
        }
    }

    // Body.
    val bodyTop = baseY - bodyH
    val bodyPath = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = cx - body, top = bodyTop, right = cx + body, bottom = baseY,
                cornerRadius = CornerRadius(body * 0.62f),
            )
        )
    }
    c.rim?.let { drawPath(bodyPath, color = it, style = Stroke(width = rimWidth)) }
    drawPath(bodyPath, color = c.fur)
    clipPath(bodyPath) {
        c.patchA?.let { drawCircle(it, radius = body * 0.62f, center = Offset(cx - body * 0.72f, bodyTop + bodyH * 0.35f)) }
        c.patchB?.let { drawCircle(it, radius = body * 0.52f, center = Offset(cx + body * 0.78f, bodyTop + bodyH * 0.75f)) }
        c.stripe?.let { s ->
            listOf(-0.62f, -0.34f, 0.34f, 0.62f).forEach { x ->
                drawLine(
                    color = s,
                    start = Offset(cx + body * x, bodyTop - body * 0.05f),
                    end = Offset(cx + body * x * 1.12f, bodyTop + bodyH * 0.42f),
                    strokeWidth = body * 0.10f,
                    cap = StrokeCap.Round,
                )
            }
        }
        c.bib?.let { drawOval(it, topLeft = Offset(cx - body * 0.42f, bodyTop + bodyH * 0.08f), size = Size(body * 0.84f, bodyH * 1.2f)) }
    }

    // Front paws, tucked at the base — or, at the very end, raised over the head to hold the
    // ceiling off, which is the one pose that says "help" without a word.
    val pawColor = c.bib ?: c.muzzle ?: c.fur
    val pawR = head * 0.24f
    if (mood == CatMood.PANIC) {
        listOf(-1f, 1f).forEach { side ->
            val shoulder = Offset(cx + side * body * 0.55f, bodyTop + bodyH * 0.25f)
            val paw = Offset(cx + side * head * 1.18f, headY - head * 1.25f)
            c.rim?.let { drawLine(it, shoulder, paw, strokeWidth = head * 0.34f + rimWidth, cap = StrokeCap.Round) }
            drawLine(c.fur, shoulder, paw, strokeWidth = head * 0.34f, cap = StrokeCap.Round)
            drawCircle(pawColor, radius = pawR, center = paw)
        }
    } else {
        listOf(-1f, 1f).forEach { side ->
            drawOval(
                pawColor,
                topLeft = Offset(cx + side * body * 0.34f - pawR * 1.2f, baseY - pawR * 1.2f),
                size = Size(pawR * 2.4f, pawR * 1.5f),
            )
        }
    }

    // Ears before the head, so their bases vanish under it. Flattening is clamped so an alarmed cat
    // still has ears — a cat with none reads as a bug rather than as fear.
    val lift = 1f - 0.42f * alarm
    listOf(-1f, 1f).forEach { side ->
        val outer = if (side < 0) c.patchB ?: c.fur else c.patchA ?: c.fur
        val earPath = Path().apply {
            moveTo(cx + side * head * 0.16f, headY - head * 0.62f)
            lineTo(cx + side * head * (0.74f + 0.30f * alarm), headY - head * 1.58f * lift)
            lineTo(cx + side * head * 1.00f, headY - head * 0.34f)
            close()
        }
        c.rim?.let { drawPath(earPath, color = it, style = Stroke(width = rimWidth)) }
        drawPath(earPath, color = outer)
        drawPath(
            path = Path().apply {
                moveTo(cx + side * head * 0.36f, headY - head * 0.66f)
                lineTo(cx + side * head * (0.71f + 0.28f * alarm), headY - head * 1.28f * lift)
                lineTo(cx + side * head * 0.84f, headY - head * 0.52f)
                close()
            },
            color = c.earInner,
        )
    }

    // Head, with its markings clipped to it.
    val headCenter = Offset(cx, headY)
    val headPath = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center = headCenter, radius = head))
    }
    c.rim?.let { drawPath(headPath, color = it, style = Stroke(width = rimWidth)) }
    drawPath(headPath, color = c.fur)
    clipPath(headPath) {
        c.patchB?.let { drawCircle(it, radius = head * 0.62f, center = Offset(cx - head * 0.78f, headY - head * 0.62f)) }
        c.patchA?.let { drawCircle(it, radius = head * 0.58f, center = Offset(cx + head * 0.80f, headY - head * 0.55f)) }
        c.stripe?.let { s ->
            // The tabby's forehead M, as three short strokes.
            listOf(-0.22f, 0f, 0.22f).forEach { x ->
                drawLine(
                    color = s,
                    start = Offset(cx + head * x, headY - head * 0.98f),
                    end = Offset(cx + head * x * 0.8f, headY - head * (if (x == 0f) 0.50f else 0.62f)),
                    strokeWidth = head * 0.09f,
                    cap = StrokeCap.Round,
                )
            }
            listOf(-1f, 1f).forEach { side ->
                drawLine(s, Offset(cx + side * head * 0.98f, headY - head * 0.05f), Offset(cx + side * head * 0.70f, headY + head * 0.02f), strokeWidth = head * 0.08f, cap = StrokeCap.Round)
                drawLine(s, Offset(cx + side * head * 0.96f, headY + head * 0.18f), Offset(cx + side * head * 0.72f, headY + head * 0.20f), strokeWidth = head * 0.08f, cap = StrokeCap.Round)
            }
        }
        c.muzzle?.let {
            drawOval(it, topLeft = Offset(cx - head * 0.52f, headY + head * 0.02f), size = Size(head * 1.04f, head * 0.80f))
            if (c.bib != null) {
                // A tuxedo's white runs up between the eyes.
                drawPath(
                    Path().apply {
                        moveTo(cx - head * 0.16f, headY + head * 0.10f)
                        lineTo(cx, headY - head * 0.42f)
                        lineTo(cx + head * 0.16f, headY + head * 0.10f)
                        close()
                    },
                    color = it,
                )
            }
        }
    }

    val eyeY = headY - head * 0.10f
    val stroke = head * 0.055f
    val happy = cheer > 0.35f
    // Lines drawn straight onto the fur: dark on a light coat, light on a tuxedo's black, where a
    // dark smile would simply vanish.
    val onFur = if (c.bib != null) c.whisker else c.ink
    listOf(-1f, 1f).forEach { side ->
        val ex = cx + side * head * 0.36f
        when {
            happy -> {
                // ^ ^ — the smile is in the eyes.
                drawArc(
                    color = onFur,
                    startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(ex - head * 0.16f, eyeY - head * 0.08f),
                    size = Size(head * 0.32f, head * 0.26f),
                    style = Stroke(width = stroke * 1.5f, cap = StrokeCap.Round),
                )
            }
            else -> {
                val wide = frightened
                val eyeR = head * (0.15f + 0.07f * alarm) * (if (wide) 1.1f else 1f)
                // Looking up at the ceiling once it is worth watching.
                val look = if (mood == CatMood.CALM) 0f else -eyeR * 0.28f
                if (c.pupil != null || wide) {
                    // A ringed eye: a coloured iris, or the white of a frightened one.
                    drawCircle(if (c.pupil != null) c.eye else Color.White, radius = eyeR, center = Offset(ex, eyeY))
                    drawCircle(c.pupil ?: c.eye, radius = eyeR * (if (wide) 0.48f else 0.62f), center = Offset(ex, eyeY + look))
                } else {
                    drawCircle(c.eye, radius = eyeR, center = Offset(ex, eyeY + look))
                }
                // The glint that makes a dot an eye.
                drawCircle(Color.White, radius = eyeR * 0.32f, center = Offset(ex + eyeR * 0.30f, eyeY + look - eyeR * 0.34f))
            }
        }
    }

    // Worried brows once the ceiling is coming, on a coat where they would show.
    if (mood != CatMood.CALM && !happy) {
        listOf(-1f, 1f).forEach { side ->
            drawLine(
                onFur,
                start = Offset(cx + side * head * 0.20f, eyeY - head * 0.30f),
                end = Offset(cx + side * head * 0.50f, eyeY - head * 0.40f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }

    if (happy) {
        // Flushed cheeks.
        listOf(-1f, 1f).forEach { side ->
            drawOval(
                Color(0x99F28C8C),
                topLeft = Offset(cx + side * head * 0.58f - head * 0.17f, eyeY + head * 0.16f),
                size = Size(head * 0.34f, head * 0.18f),
            )
        }
    }

    val noseY = eyeY + head * 0.30f
    drawPath(
        path = Path().apply {
            moveTo(cx - head * 0.10f, noseY)
            lineTo(cx + head * 0.10f, noseY)
            lineTo(cx, noseY + head * 0.11f)
            close()
        },
        color = if (c.bib != null) Color(0xFFE3A1A4) else c.ink,
    )

    val mouthY = noseY + head * 0.12f
    when {
        happy -> {
            // ω
            listOf(-1f, 1f).forEach { side ->
                drawArc(
                    color = c.ink,
                    startAngle = 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(cx + (if (side < 0) -head * 0.22f else 0f), mouthY - head * 0.06f),
                    size = Size(head * 0.22f, head * 0.16f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        frightened -> {
            // An open mouth: a gasp, and at the end a cry.
            val open = if (mood == CatMood.PANIC) 0.26f else 0.15f
            drawOval(
                Color(0xFF7A2E2E),
                topLeft = Offset(cx - head * open * 0.55f, mouthY),
                size = Size(head * open * 1.1f, head * open),
            )
        }
        else -> {
            drawLine(c.ink, Offset(cx, mouthY), Offset(cx - head * 0.14f, mouthY + head * 0.12f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(c.ink, Offset(cx, mouthY), Offset(cx + head * 0.14f, mouthY + head * 0.12f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }

    listOf(-1f, 1f).forEach { side ->
        val from = cx + side * head * 0.42f
        drawLine(c.whisker, Offset(from, noseY - head * 0.04f), Offset(from + side * head * 0.72f, noseY - head * 0.22f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(c.whisker, Offset(from, noseY + head * 0.12f), Offset(from + side * head * 0.76f, noseY + head * 0.16f), strokeWidth = stroke, cap = StrokeCap.Round)
    }

    if (mood == CatMood.SCARED) {
        // A bead of sweat.
        val sx = cx + head * 0.92f
        val sy = headY - head * 0.62f
        drawPath(
            Path().apply {
                moveTo(sx, sy - head * 0.22f)
                quadraticBezierTo(sx + head * 0.14f, sy, sx, sy + head * 0.08f)
                quadraticBezierTo(sx - head * 0.14f, sy, sx, sy - head * 0.22f)
                close()
            },
            color = Color(0xFF9FD3F5),
        )
    }
    if (mood == CatMood.PANIC && !happy) {
        // Tears, falling on the clock so they run rather than sit.
        listOf(-1f, 1f).forEach { side ->
            val fall = (phase * 3f + if (side < 0) 0f else 0.5f) % 1f
            drawCircle(
                Color(0xCC9FD3F5),
                radius = head * 0.075f,
                center = Offset(cx + side * head * 0.40f, eyeY + head * (0.22f + 0.45f * fall)),
            )
        }
    }

    // What it has on, over everything, in the order a cat would put things on.
    wear.firstOrNull { it.slot == WearSlot.NECK }?.let { drawNeckItem(it, cx, headY + head * 0.98f, head) }
    wear.firstOrNull { it.slot == WearSlot.FACE }?.let { drawFaceItem(it, cx, eyeY, head) }
    wear.firstOrNull { it.slot == WearSlot.HEAD }?.let { drawHeadItem(it, cx, headY, head) }
}

private val KNIT_RED = Color(0xFFD9534F)
private val KNIT_RED_DARK = Color(0xFFB94340)
private val GOLD = Color(0xFFFFC53D)
private val GOLD_DARK = Color(0xFFC8932B)
private val PINK = Color(0xFFFF6F91)
private val PINK_DARK = Color(0xFFE0527A)
private val BRAND = Color(0xFF7C6BFF)
private val CREAM_WHITE = Color(0xFFF5F0E8)
private val FRAME = Color(0xFF6B4A2B)

/** A collar's worth, just under the chin at [chinY]. */
private fun DrawScope.drawNeckItem(item: CatItem, cx: Float, chinY: Float, head: Float) {
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
        else -> Unit
    }
}

/** Over the eyes, which sit at [eyeY], ±0.36 of a head apart. */
private fun DrawScope.drawFaceItem(item: CatItem, cx: Float, eyeY: Float, head: Float) {
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
        else -> Unit
    }
}

/** On the head, whose centre is at [headY]: the ears stand from 0.62 of a head above it. */
private fun DrawScope.drawHeadItem(item: CatItem, cx: Float, headY: Float, head: Float) {
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
                    center = Offset(c.x + head * 0.15f * kotlin.math.cos(a), c.y + head * 0.15f * sin(a)),
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

/**
 * A place for the cat to sit, drawn over the whole canvas behind it: one of the scenes the calories
 * burned bring. [floorY] is where the cat's paws are.
 */
fun DrawScope.drawCatScene(scene: CatItem, floorY: Float) {
    val w = size.width
    val h = size.height
    when (scene) {
        CatItem.CITY -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1E2A4A), Color(0xFF3B3F6B))))
            drawCircle(Color(0xFFFFF3C4), radius = h * 0.08f, center = Offset(w * 0.82f, h * 0.2f))
            val towers = listOf(0.02f to 0.42f, 0.14f to 0.58f, 0.27f to 0.36f, 0.62f to 0.50f, 0.75f to 0.66f, 0.88f to 0.40f)
            towers.forEach { (x, tall) ->
                val top = floorY - h * tall
                drawRect(Color(0xFF141B30), topLeft = Offset(w * x, top), size = Size(w * 0.11f, floorY - top))
                var y = top + h * 0.05f
                while (y < floorY - h * 0.06f) {
                    drawRect(Color(0xFFFFD66B), topLeft = Offset(w * x + w * 0.025f, y), size = Size(w * 0.02f, h * 0.025f))
                    drawRect(Color(0xFFFFD66B), topLeft = Offset(w * x + w * 0.065f, y), size = Size(w * 0.02f, h * 0.025f))
                    y += h * 0.07f
                }
            }
        }
        CatItem.MOUNTAIN -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF9ED8F5), Color(0xFFE8F6FF))))
            drawCircle(Color(0xFFFFE07A), radius = h * 0.07f, center = Offset(w * 0.18f, h * 0.2f))
            drawPath(
                Path().apply {
                    moveTo(0f, floorY)
                    lineTo(w * 0.5f, floorY - h * 0.55f)
                    lineTo(w, floorY)
                    close()
                },
                color = Color(0xFF7FB7A0),
            )
            drawPath(
                Path().apply {
                    moveTo(0f, floorY)
                    quadraticBezierTo(w * 0.25f, floorY - h * 0.28f, w * 0.55f, floorY - h * 0.12f)
                    quadraticBezierTo(w * 0.8f, floorY - h * 0.02f, w, floorY - h * 0.16f)
                    lineTo(w, floorY)
                    close()
                },
                color = Color(0xFF5E9E7A),
            )
        }
        CatItem.SNOW -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFBFE3FF), Color(0xFFF2FAFF))))
            listOf(0.28f to 0.62f, 0.70f to 0.78f).forEach { (x, tall) ->
                val peak = Offset(w * x, floorY - h * tall)
                val halfBase = w * 0.36f
                drawPath(
                    Path().apply {
                        moveTo(peak.x - halfBase, floorY)
                        lineTo(peak.x, peak.y)
                        lineTo(peak.x + halfBase, floorY)
                        close()
                    },
                    color = Color(0xFF8A9BB5),
                )
                drawPath(
                    Path().apply {
                        moveTo(peak.x - halfBase * 0.3f, peak.y + h * tall * 0.3f)
                        lineTo(peak.x, peak.y)
                        lineTo(peak.x + halfBase * 0.3f, peak.y + h * tall * 0.3f)
                        lineTo(peak.x + halfBase * 0.1f, peak.y + h * tall * 0.22f)
                        lineTo(peak.x - halfBase * 0.1f, peak.y + h * tall * 0.3f)
                        close()
                    },
                    color = Color.White,
                )
            }
        }
        CatItem.SPACE -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0E24), Color(0xFF1B1F4B))))
            STARS.forEach { (x, y) -> drawCircle(Color(0xCCFFFFFF), radius = h * 0.008f, center = Offset(w * x, h * y)) }
            val planet = Offset(w * 0.78f, h * 0.26f)
            drawCircle(Color(0xFFE8A06B), radius = h * 0.09f, center = planet)
            drawOval(
                Color(0xFFF3D9A8),
                topLeft = Offset(planet.x - h * 0.16f, planet.y - h * 0.035f),
                size = Size(h * 0.32f, h * 0.07f),
                style = Stroke(width = h * 0.012f),
            )
        }
        CatItem.BEACH -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF7FD3F7), Color(0xFFD9F3FF))))
            drawCircle(Color(0xFFFFE07A), radius = h * 0.08f, center = Offset(w * 0.80f, h * 0.18f))
            drawRect(Color(0xFF2F8FD8), topLeft = Offset(0f, floorY - h * 0.22f), size = Size(w, h * 0.12f))
            drawRect(Color(0xFFF2D7A0), topLeft = Offset(0f, floorY - h * 0.10f), size = Size(w, h * 0.10f + (h - floorY)))
            // A palm leaning in from the left.
            drawLine(Color(0xFF8B5E34), Offset(w * 0.12f, floorY - h * 0.04f), Offset(w * 0.20f, floorY - h * 0.52f), strokeWidth = w * 0.03f)
            listOf(-60f, -20f, 20f, 60f, 100f).forEach { a ->
                val r = Math.toRadians(a.toDouble())
                val top = Offset(w * 0.20f, floorY - h * 0.52f)
                drawLine(
                    Color(0xFF3E9E5A), top,
                    Offset(top.x + w * 0.14f * kotlin.math.cos(r).toFloat(), top.y + h * 0.08f * sin(r.toFloat()) + h * 0.03f),
                    strokeWidth = w * 0.025f, cap = StrokeCap.Round,
                )
            }
        }
        CatItem.GYM -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF3A3F4B), Color(0xFF555B69))))
            // A mirror strip and a rack of dumbbells against the wall.
            drawRect(Color(0x339ED8F5), topLeft = Offset(w * 0.05f, h * 0.12f), size = Size(w * 0.9f, h * 0.32f))
            drawRect(Color(0xFF22252C), topLeft = Offset(w * 0.06f, floorY - h * 0.26f), size = Size(w * 0.30f, h * 0.03f))
            listOf(0.10f, 0.20f, 0.30f).forEach { x ->
                drawCircle(Color(0xFF1A1C21), radius = h * 0.035f, center = Offset(w * x, floorY - h * 0.29f))
            }
            val bar = floorY - h * 0.12f
            drawLine(Color(0xFFBFC4CC), Offset(w * 0.56f, bar), Offset(w * 0.94f, bar), strokeWidth = h * 0.015f)
            listOf(0.58f, 0.92f).forEach { x ->
                drawRoundRect(Color(0xFF1A1C21), topLeft = Offset(w * x - w * 0.02f, bar - h * 0.08f), size = Size(w * 0.04f, h * 0.16f), cornerRadius = CornerRadius(w * 0.01f))
            }
            drawRect(Color(0xFF2A2D35), topLeft = Offset(0f, floorY), size = Size(w, h - floorY))
        }
        else -> Unit
    }
}

/** Where the stars are, as fractions of the canvas: fixed, so the sky does not twinkle between frames. */
private val STARS = listOf(
    0.08f to 0.12f, 0.22f to 0.30f, 0.35f to 0.08f, 0.48f to 0.22f, 0.12f to 0.48f,
    0.58f to 0.40f, 0.66f to 0.10f, 0.92f to 0.14f, 0.88f to 0.46f, 0.30f to 0.55f,
    0.52f to 0.60f, 0.04f to 0.70f, 0.95f to 0.64f, 0.42f to 0.36f, 0.74f to 0.52f,
)

/**
 * Hearts after a push: [count] of them, drifting up and out from beside the ears and fading as
 * [cheer] runs down. Out to the sides rather than straight up, because straight up is where the
 * speech bubble is.
 */
fun DrawScope.drawHearts(centerX: Float, headTopY: Float, scale: Float, count: Int, cheer: Float) {
    if (count <= 0 || cheer <= 0f) return
    val head = 30f * scale
    val rise = (1f - cheer) * 55f * scale
    val color = Color(0xFFFF6F91).copy(alpha = cheer.coerceIn(0f, 1f))
    // (side, how far out in heads, a head start upward, size)
    val spots = listOf(
        listOf(1f, 1.05f, 0f, 15f),
        listOf(-1f, 1.05f, 6f, 13f),
        listOf(1f, 1.65f, 18f, 11f),
    )
    for (spot in spots.take(count)) {
        val (side, out, lead, size) = spot
        val x = centerX + side * (head * out + rise * 0.45f)
        val y = headTopY + head * 0.45f - rise - lead * scale
        drawHeart(Offset(x, y), size = size * scale, color = color)
    }
}

private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
    val r = size * 0.5f
    drawCircle(color, radius = r * 0.62f, center = Offset(center.x - r * 0.5f, center.y - r * 0.2f))
    drawCircle(color, radius = r * 0.62f, center = Offset(center.x + r * 0.5f, center.y - r * 0.2f))
    drawPath(
        Path().apply {
            moveTo(center.x - r * 1.08f, center.y - r * 0.02f)
            lineTo(center.x + r * 1.08f, center.y - r * 0.02f)
            lineTo(center.x, center.y + r * 1.05f)
            close()
        },
        color = color,
    )
}

/** Where the top of the cat's head is, for placing what floats above it. */
fun catHeadTop(baseY: Float, scale: Float, mood: CatMood = CatMood.CALM): Float {
    val head = 30f * scale
    return baseY - 46f * scale * 1.05f * crouchFor(mood) - head * 0.72f - head
}

/** How low a frightened cat makes itself. */
private fun crouchFor(mood: CatMood): Float = when (mood) {
    CatMood.CALM -> 1f
    CatMood.UNEASY -> 0.97f
    CatMood.SCARED -> 0.88f
    CatMood.PANIC -> 0.82f
}
