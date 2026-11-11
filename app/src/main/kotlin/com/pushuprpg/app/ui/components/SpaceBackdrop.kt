package com.pushuprpg.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.ui.theme.LocalReduceMotion
import com.pushuprpg.app.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.floor

/**
 * The menus' ground: deep space, by the owner's request (2026-11-11, "우주 슈팅 게임처럼").
 *
 * Navy going to black, a violet haze banded in steps at the bottom, and three layers of pixel stars
 * that drift down past the screen at three speeds, as a shooter's field scrolls under its ship. They
 * move a whole pixel at a time, [TICK_MS] apart, so the screen redraws ten times a second rather than
 * every vsync; under reduced motion they hold still.
 */
@Composable
fun SpaceBackdrop(modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val tick = remember { mutableIntStateOf(0) }
    if (!reduceMotion) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(TICK_MS)
                tick.intValue++
            }
        }
    }
    val top = Palette.Bg0
    val ground = Palette.Bg1
    val haze = Palette.Nebula
    val star = Palette.Star
    // A sibling under the screens, not their parent: a new frame of stars redraws this and nothing else.
    Spacer(
        modifier.drawWithCache {
            val cell = floor(2.dp.toPx()).coerceAtLeast(1f)
            val cols = (size.width / cell).toInt() + 1
            val rows = (size.height / cell).toInt() + 1
            // A fixed field of stars per layer, from a hash of the cell, so it is the same sky
            // every time the screen opens.
            val layers = List(LAYERS) { layer ->
                buildList {
                    for (j in 0 until rows step 6) for (i in 0 until cols step 6) {
                        val h = starHash(i + layer * 7_919, j)
                        if (h % DENSITY[layer] == 0) add(Offset((i + h % 6) * cell, (j + h / 6 % 6) * cell))
                    }
                }
            }
            val bands = 8
            onDrawBehind {
                // The ground, stepped from black at the top to navy, then the haze in bands.
                for (b in 0 until bands) {
                    val y0 = floor(size.height * b / bands)
                    val y1 = floor(size.height * (b + 1) / bands)
                    val t = b / (bands - 1f)
                    val color = if (t < 0.6f) lerp(top, ground, t / 0.6f) else lerp(ground, haze, (t - 0.6f) / 0.4f * 0.55f)
                    drawRect(color, topLeft = Offset(0f, y0), size = Size(size.width, y1 - y0))
                }
                val shift = if (reduceMotion) 0 else tick.intValue
                layers.forEachIndexed { layer, stars ->
                    val dy = (shift * SPEED[layer] / 4) % rows * cell
                    val px = cell * SIZE[layer]
                    val tint = star.copy(alpha = ALPHA[layer])
                    for (s in stars) {
                        var y = s.y + dy
                        if (y >= rows * cell) y -= rows * cell
                        drawRect(tint, topLeft = Offset(s.x, y), size = Size(px, px))
                    }
                }
            }
        },
    )
}

private const val TICK_MS = 100L
private const val LAYERS = 3

/** Far to near: one cell in so many has a star, how many cells it falls in four ticks, how big and bright it is. */
private val DENSITY = intArrayOf(9, 17, 41)
private val SPEED = intArrayOf(1, 2, 4)
private val SIZE = intArrayOf(1, 1, 2)
private val ALPHA = floatArrayOf(0.35f, 0.6f, 0.9f)
