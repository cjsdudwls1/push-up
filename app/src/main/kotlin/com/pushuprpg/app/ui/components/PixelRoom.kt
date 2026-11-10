package com.pushuprpg.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.ui.theme.LocalReduceMotion
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.survival.CatView
import com.pushuprpg.core.survival.SurvivalState
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The pixel demo's 240 by 426 screen (2026-11-10, the look the owner chose) laid over a real one:
 * as large as fits, its floor 54 of its pixels over the bottom edge and its middle in the middle.
 * Anything wider or taller than the demo is more room, more wall above or to the sides, never a
 * stretched cat. [u] is one of the art's pixels on screen.
 */
internal class RoomGeometry(val width: Float, val height: Float) {
    val u = minOf(width / ART_W, height / ART_H)
    val floorY = height - (ART_H - FLOOR) * u
    val cx = width / 2f

    /** A demo x on screen. */
    fun x(demoX: Float) = cx + (demoX - CX) * u

    /** A demo y on screen. */
    fun y(demoY: Float) = floorY - (FLOOR - demoY) * u

    /** The demo's x at the screen's left and right edges, and its y at the top. */
    val leftX get() = CX - cx / u
    val rightX get() = CX + cx / u
    val topY get() = FLOOR - floorY / u

    /** The cat's scale for [drawCat]: four screen pixels of the art to a cell, as the demo's. */
    val catScale get() = CAT_CELL * u / 4.6f

    /** A rectangle in the demo's pixels, its edges snapped to whole screen pixels. */
    fun DrawScope.px(ax: Float, ay: Float, w: Float, h: Float, c: Color) {
        val l = x(ax).roundToInt().toFloat()
        val t = y(ay).roundToInt().toFloat()
        val r = x(ax + w).roundToInt().toFloat()
        val b = y(ay + h).roundToInt().toFloat()
        if (r > l && b > t) drawRect(c, topLeft = Offset(l, t), size = Size(r - l, b - t))
    }

    companion object {
        const val ART_W = 240f
        const val ART_H = 426f
        const val FLOOR = 372f
        const val CX = 120f
        const val CAT_CELL = 4f

        /** The ceiling's underside at the top, and at the end of a life, on the cat's ears. */
        const val CEIL_TOP = 70f
        const val CEIL_LOW = 284f
    }
}

/** Frame time in nanoseconds, every frame while composed. Read it in a draw lambda only. */
@Composable
internal fun rememberFrameClock(): () -> Long {
    val holder = remember { FrameClock() }
    LaunchedEffect(holder) {
        while (true) withFrameNanos { holder.now = it }
    }
    return holder::read
}

private class FrameClock {
    var now by mutableLongStateOf(0L)
    fun read() = now
}

/**
 * 고양이 지켜줘's scene as the pixel demo draws it: the dark room, the cat holding the ceiling up,
 * the brick ceiling with its teeth, and the red beat at the edges once the ceiling is close.
 *
 * [setUp] is the setting up before a set: the room is left out so the camera shows, which is what
 * lining up is done by, and the cat is faint under the framing guide. [holdUp] raises the cat's
 * paws to the ceiling, as in a set.
 */
@Composable
internal fun PixelRoom(
    state: SurvivalState,
    cat: CatView,
    coat: CatCoat,
    wear: Set<CatItem>,
    fx: PixelFxState,
    setUp: Boolean,
    holdUp: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val now = rememberFrameClock()
    val danger = state.intensity.coerceIn(0f, 1f)
    val bottom = (CEIL_TOP_F + (RoomGeometry.CEIL_LOW - RoomGeometry.CEIL_TOP) * (1f - state.height.coerceIn(0f, 1f))).roundToInt().toFloat()

    Box(modifier) {
        if (!setUp) {
            Canvas(Modifier.fillMaxSize()) {
                val g = RoomGeometry(size.width, size.height)
                drawRoom(g, danger)
            }
        }
        Canvas(Modifier.fillMaxSize().alpha(if (setUp) SETUP_ALPHA else 1f)) {
            val g = RoomGeometry(size.width, size.height)
            val t = now()
            val scale = g.catScale
            if (setUp) with(g) { px(leftX - 4f, RoomGeometry.FLOOR, rightX - leftX + 8f, 4f, FLOOR_EDGE) }
            drawCat(
                centerX = g.cx,
                baseY = g.floorY,
                scale = scale,
                coat = coat,
                mood = cat.mood,
                alarm = danger,
                cheer = cat.cheer,
                phase = if (reduceMotion) 0f else ((t / 1_000_000L) % 4_000L) / 4_000f,
                wear = wear,
                holdUpTo = if (holdUp) g.y(bottom + 2f) else null,
                squash = if (reduceMotion) 0f else fx.squash(t),
            )
            drawHearts(
                centerX = g.cx,
                headTopY = catHeadTop(g.floorY, scale, cat.mood),
                scale = scale,
                count = cat.hearts,
                cheer = cat.cheer,
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val g = RoomGeometry(size.width, size.height)
            drawPixelCeiling(g, bottom, danger)
            if (!setUp && danger > 0.6f && !reduceMotion) {
                val bpm = 96f + 84f * danger
                val beat = (now() / 1_000_000L / (60_000f / bpm)).toLong()
                if (beat % 2L == 0L) {
                    val c = Color(255, 77, 94).copy(alpha = 0.25f + 0.35f * danger)
                    val b = (4f * g.u).roundToInt().toFloat()
                    drawRect(c, size = Size(size.width, b))
                    drawRect(c, topLeft = Offset(0f, size.height - b), size = Size(size.width, b))
                    drawRect(c, size = Size(b, size.height))
                    drawRect(c, topLeft = Offset(size.width - b, 0f), size = Size(b, size.height))
                }
            }
        }
    }
}

private const val CEIL_TOP_F = RoomGeometry.CEIL_TOP

/** How strongly the cat is drawn while setting up, under the framing guide. */
private const val SETUP_ALPHA = 0.4f

private val FLOOR_EDGE = Color(0xFF2A3140)

/** The room: the wall with its stripes, the floor's edge, and the boards below it. */
private fun DrawScope.drawRoom(g: RoomGeometry, danger: Float) {
    drawRect(if (danger > 0.6f) Color(0xFF1F1416) else Color(0xFF171C26))
    with(g) {
        val left = leftX
        val right = rightX
        val stripe = Color(0xFF1F2633)
        // Every 24 from x 8, as the demo's, carried on past its edges.
        var x = 8f + floor((left - 8f) / 24f) * 24f
        while (x < right) {
            px(x, topY - 4f, 2f, RoomGeometry.FLOOR - topY + 4f, stripe)
            x += 24f
        }
        px(left - 4f, RoomGeometry.FLOOR, right - left + 8f, 4f, FLOOR_EDGE)
        px(left - 4f, RoomGeometry.FLOOR + 4f, right - left + 8f, RoomGeometry.ART_H, Color(0xFF141922))
        val board = Color(0xFF1D2330)
        var y = RoomGeometry.FLOOR + 12f
        while (y < RoomGeometry.FLOOR + (size.height - floorY) / u) {
            px(left - 4f, y, right - left + 8f, 2f, board)
            y += 10f
        }
    }
}

/**
 * The ceiling as the demo draws it, its underside at the demo's [bottom]: bricks that redden with
 * [danger], a lit edge, and stepped teeth. Up to the top of the screen, however tall it is.
 */
private fun DrawScope.drawPixelCeiling(g: RoomGeometry, bottom: Float, danger: Float) {
    val r = (107 + 128 * danger).roundToInt().coerceIn(0, 255)
    val gg = (87 - 46 * danger).roundToInt().coerceIn(0, 255)
    val b = (71 - 41 * danger).roundToInt().coerceIn(0, 255)
    val base = Color(r, gg, b)
    val dark = Color((r * 0.7f).toInt(), (gg * 0.7f).toInt(), (b * 0.7f).toInt())
    val lite = Color(minOf(255, r + 40), minOf(255, gg + 30), minOf(255, b + 25))
    with(g) {
        val left = floor(leftX / 32f) * 32f - 32f
        val right = ceil(rightX) + 4f
        val top = topY - 4f
        px(left, top, right - left, bottom - top, base)
        var y = bottom - 12f
        var row = 0
        while (y > top - 12f) {
            px(left, y, right - left, 2f, dark)
            var x = left + (row % 2) * 16f
            while (x < right) {
                px(x, y, 2f, 12f, dark)
                x += 32f
            }
            y -= 12f
            row++
        }
        px(left, bottom - 4f, right - left, 2f, lite)
        var x = left
        while (x < right) {
            px(x + 2f, bottom, 12f, 4f, base)
            px(x + 4f, bottom + 4f, 8f, 4f, base)
            px(x + 6f, bottom + 8f, 4f, 3f, base)
            px(x + 6f, bottom + 8f, 4f, 1f, dark)
            x += 16f
        }
    }
}

private val LIFE_HEART = Color(0xFFFF6F91)

/**
 * The lives left as pixel hearts, one for each: a lost one is gone, not dimmed, by the owner's
 * request (2026-10-28). Seven of the art's pixels at two screen-art pixels each, [u] apart.
 */
@Composable
internal fun PixelLives(left: Int, u: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = 2f * u
        val step = 18f * u
        val start = size.width / 2f - (left - 1) * step / 2f
        for (i in 0 until left) {
            drawPixelHeart(Offset(start + i * step, size.height / 2f), unit, LIFE_HEART)
        }
    }
}
