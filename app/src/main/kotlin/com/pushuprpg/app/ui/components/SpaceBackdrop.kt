package com.pushuprpg.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import com.pushuprpg.app.ui.theme.Palette
import kotlin.math.floor

/**
 * The menus' ground: deep space, by the owner's request (2026-11-11, "우주 슈팅 게임처럼").
 *
 * One still gradient from the top of the screen to the bottom: the deepest black-navy above, easing
 * into navy and then a violet haze at the foot. Drawn in [BANDS] flat steps rather than a smooth
 * blend, so it reads as pixel art. It used to carry drifting stars; the owner found them busy
 * ("정신사나워", 2026-11-11), so it holds still and costs one draw.
 */
@Composable
fun SpaceBackdrop(modifier: Modifier = Modifier) {
    val top = Palette.Bg0
    val ground = Palette.Bg1
    val haze = Palette.Nebula
    Spacer(
        modifier.drawWithCache {
            val colors = List(BANDS) { b ->
                val t = b / (BANDS - 1f)
                if (t < 0.5f) lerp(top, ground, t / 0.5f) else lerp(ground, haze, (t - 0.5f) / 0.5f * 0.7f)
            }
            onDrawBehind {
                for (b in 0 until BANDS) {
                    val y0 = floor(size.height * b / BANDS)
                    val y1 = floor(size.height * (b + 1) / BANDS)
                    drawRect(colors[b], topLeft = Offset(0f, y0), size = Size(size.width, y1 - y0))
                }
            }
        },
    )
}

private const val BANDS = 12
