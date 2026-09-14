package com.pushuprpg.app.ui.battle

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * How the enemy should look and behave this frame.
 *
 * [hurt] and [telegraph] decay in the caller so this stays a pure drawing routine.
 */
data class MonsterVisual(
    val id: String,
    val isBoss: Boolean,
    /** 0..1, flashes on a hit. */
    val hurt: Float = 0f,
    /** 0..1, rises while the ultimate is winding up. */
    val telegraph: Float = 0f,
    /** 0..1 through the death animation; 1 is gone. */
    val death: Float = 0f,
    /** Drives idle breathing; the pose timestamp in milliseconds. */
    val timeMs: Long = 0L,
)

/**
 * Enemies drawn from shapes, with their look derived from their id.
 *
 * Forty enemies would otherwise be forty art tasks. Hashing the id into a palette, a silhouette
 * variant and a horn count means every one of them looks like itself, consistently, for no
 * additional work — and a new enemy added to the dungeon table arrives already drawn.
 */
@Composable
fun Monster(visual: MonsterVisual, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawMonster(visual) }
}

private fun DrawScope.drawMonster(visual: MonsterVisual) {
    if (visual.death >= 1f) return

    val seed = visual.id.hashCode()
    val palette = monsterPalette(seed)
    val horns = 1 + (abs(seed / 7) % 3)
    val stout = (abs(seed / 31) % 100) / 100f

    val h = size.height * (if (visual.isBoss) 0.92f else 0.76f)
    val cx = size.width / 2f
    val groundY = size.height * 0.95f

    // Idle breathing, so a live enemy never looks like a sticker.
    val breath = sin(visual.timeMs / 900f) * h * 0.012f
    val menace = visual.telegraph
    val bodyW = h * (0.52f + 0.16f * stout) * (1f + 0.05f * menace)
    val bodyH = h * 0.52f + breath

    val bodyTop = groundY - bodyH
    val headR = h * (0.20f + 0.03f * stout)
    val headY = bodyTop - headR * 0.62f

    // A defeated enemy breaks apart rather than fading: the demo shattered its boss and it read as
    // a kill in a way a dissolve never does.
    if (visual.death > 0f) {
        drawShatter(cx, groundY - bodyH * 0.6f, h, palette.body, visual.death, seed)
        return
    }

    val tint = lerp(palette.body, Color.White, visual.hurt * 0.65f)
    val dark = lerp(palette.shade, Color.White, visual.hurt * 0.5f)

    drawOval(
        color = Color.Black.copy(alpha = 0.34f),
        topLeft = Offset(cx - bodyW * 0.55f, groundY - h * 0.02f),
        size = Size(bodyW * 1.1f, h * 0.05f),
    )

    // Rage glow while the ultimate winds up: the warning has to be visible peripherally, because
    // the user is mid-rep and not reading the screen.
    if (menace > 0.01f) {
        drawCircle(
            color = Color(0xFFFF4D5E).copy(alpha = 0.26f * menace),
            radius = h * (0.48f + 0.10f * menace),
            center = Offset(cx, groundY - bodyH * 0.55f),
            blendMode = BlendMode.Plus,
        )
    }

    // Legs.
    val legW = h * 0.10f
    listOf(-1f, 1f).forEach { side ->
        drawRoundRect(
            color = dark,
            topLeft = Offset(cx + side * bodyW * 0.26f - legW / 2f, groundY - h * 0.18f),
            size = Size(legW, h * 0.18f),
            cornerRadius = CornerRadius(legW * 0.4f),
        )
    }

    // Body.
    drawRoundRect(
        color = tint,
        topLeft = Offset(cx - bodyW / 2f, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(bodyW * 0.34f),
    )
    drawOval(
        color = dark.copy(alpha = 0.55f),
        topLeft = Offset(cx - bodyW * 0.26f, bodyTop + bodyH * 0.34f),
        size = Size(bodyW * 0.52f, bodyH * 0.46f),
    )

    // Arms.
    val armDrop = h * 0.03f + menace * h * 0.05f
    listOf(-1f, 1f).forEach { side ->
        val shoulder = Offset(cx + side * bodyW * 0.46f, bodyTop + bodyH * 0.22f)
        val hand = Offset(cx + side * bodyW * (0.66f + 0.08f * menace), bodyTop + bodyH * 0.72f - armDrop)
        drawLine(tint, shoulder, hand, strokeWidth = h * 0.075f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(dark, h * 0.05f, hand)
    }

    // Head.
    drawCircle(tint, headR, Offset(cx, headY))

    repeat(horns) { i ->
        val spread = (i - (horns - 1) / 2f) * headR * 0.62f
        drawPath(
            Path().apply {
                moveTo(cx + spread - headR * 0.16f, headY - headR * 0.72f)
                lineTo(cx + spread, headY - headR * (1.35f + 0.25f * menace))
                lineTo(cx + spread + headR * 0.16f, headY - headR * 0.72f)
                close()
            },
            palette.horn,
        )
    }

    // Eyes narrow as the ultimate charges.
    val eyeR = headR * (0.20f - 0.06f * menace)
    val eyeColor = lerp(palette.eye, Color(0xFFFF4D5E), menace)
    listOf(-1f, 1f).forEach { side ->
        drawCircle(Color(0xFF12161F), eyeR * 1.5f, Offset(cx + side * headR * 0.38f, headY - headR * 0.08f))
        drawCircle(eyeColor, eyeR, Offset(cx + side * headR * 0.38f, headY - headR * 0.08f))
        if (menace > 0.3f) {
            drawCircle(
                eyeColor.copy(alpha = 0.5f * menace),
                eyeR * 2.6f,
                Offset(cx + side * headR * 0.38f, headY - headR * 0.08f),
                blendMode = BlendMode.Plus,
            )
        }
    }

    // A crown marks a boss without needing a different silhouette.
    if (visual.isBoss) {
        val cw = headR * 1.25f
        drawPath(
            Path().apply {
                moveTo(cx - cw / 2f, headY - headR * 0.95f)
                lineTo(cx - cw / 2f, headY - headR * 1.45f)
                lineTo(cx - cw * 0.18f, headY - headR * 1.12f)
                lineTo(cx, headY - headR * 1.55f)
                lineTo(cx + cw * 0.18f, headY - headR * 1.12f)
                lineTo(cx + cw / 2f, headY - headR * 1.45f)
                lineTo(cx + cw / 2f, headY - headR * 0.95f)
                close()
            },
            Color(0xFFFFC53D),
        )
    }

    if (visual.hurt > 0.02f) {
        drawCircle(
            color = Color.White.copy(alpha = 0.20f * visual.hurt),
            radius = h * 0.42f,
            center = Offset(cx, groundY - bodyH * 0.55f),
            blendMode = BlendMode.Plus,
        )
    }
}

/** The kill: fragments thrown outward and down, fading as they go. */
private fun DrawScope.drawShatter(
    cx: Float,
    cy: Float,
    h: Float,
    color: Color,
    progress: Float,
    seed: Int,
) {
    val pieces = 11
    val fade = (1f - progress).coerceIn(0f, 1f)
    repeat(pieces) { i ->
        // Deterministic from the seed, so a replay of the same fight shatters the same way.
        val angle = (i * 137 + abs(seed % 90)) * 0.0174533f
        val speed = h * (0.30f + 0.28f * ((abs(seed / (i + 3)) % 100) / 100f))
        val x = cx + cos(angle) * speed * progress
        val y = cy + sin(angle) * speed * progress + h * 0.85f * progress * progress
        val r = h * 0.055f * fade
        if (r <= 0.5f) return@repeat
        drawRoundRect(
            color = color.copy(alpha = fade),
            topLeft = Offset(x - r, y - r),
            size = Size(r * 2f, r * 2f),
            cornerRadius = CornerRadius(r * 0.35f),
        )
    }
}

private data class MonsterPalette(val body: Color, val shade: Color, val horn: Color, val eye: Color)

private fun monsterPalette(seed: Int): MonsterPalette {
    val options = listOf(
        MonsterPalette(Color(0xFF6FAF52), Color(0xFF3F7331), Color(0xFFE8DCC0), Color(0xFFFFE066)),
        MonsterPalette(Color(0xFF7E6BB5), Color(0xFF4E3F7D), Color(0xFFD8CFF0), Color(0xFF9BE7FF)),
        MonsterPalette(Color(0xFFB5654B), Color(0xFF7A3E2C), Color(0xFFF0D8B0), Color(0xFFFFB020)),
        MonsterPalette(Color(0xFF4F8FB5), Color(0xFF2F5E7D), Color(0xFFD5ECF5), Color(0xFFB8FFF0)),
        MonsterPalette(Color(0xFF8C8F99), Color(0xFF5A5D66), Color(0xFFE4E6EC), Color(0xFFFF8A5C)),
    )
    return options[abs(seed) % options.size]
}
