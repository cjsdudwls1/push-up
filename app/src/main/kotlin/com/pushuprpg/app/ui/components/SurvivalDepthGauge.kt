package com.pushuprpg.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.Galmuri
import com.pushuprpg.app.ui.theme.LocalReduceMotion
import kotlin.math.roundToInt

/**
 * 고양이 지켜줘's depth, as the pixel demo draws it: a bar that fills downward as the user goes down,
 * with the line a rep counts at (1회) and the line a deep one does (깊게), blinking at its foot past
 * the deep line and marking where the last rep turned for a moment after. By the owner's request
 * (2026-11-10) — the ceiling alone did not say how far was far enough.
 *
 * [u] is one of the art's pixels on screen ([RoomGeometry.u]); the gauge is 48 by 230 of them.
 * [depth] is read in the draw phase only. Depths are the detector's 0..100; [countAt] and [deepAt]
 * are its thresholds on that scale.
 */
@Composable
fun SurvivalDepthGauge(
    depth: () -> Float,
    countAt: Float,
    deepAt: Float,
    u: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val now = rememberFrameClock()
    val calm = LocalReduceMotion.current
    // The deepest the rep under way has gone, and the last finished rep's, with when it finished.
    val peak = remember { FloatArray(2) { -1f } }
    val peakAt = remember { LongArray(1) }
    val labelStyle = remember(u, density) {
        TextStyle(
            fontFamily = Galmuri,
            fontWeight = FontWeight.Bold,
            fontSize = with(density) { (11f * u).toSp() },
            lineHeight = with(density) { (12f * u).toSp() },
        )
    }
    Box(modifier.size(with(density) { (48f * u).toDp() }, with(density) { (230f * u).toDp() })) {
        Canvas(Modifier.fillMaxSize()) {
            val t = now()
            fun px(x: Float, y: Float, w: Float, h: Float, c: Color) {
                val l = (x * u).roundToInt().toFloat()
                val tp = (y * u).roundToInt().toFloat()
                val r = ((x + w) * u).roundToInt().toFloat()
                val b = ((y + h) * u).roundToInt().toFloat()
                if (r > l && b > tp) drawRect(c, topLeft = Offset(l, tp), size = Size(r - l, b - tp))
            }
            val x = 30f
            val y0 = 16f
            val gh = 200f
            val w = 12f
            val d = depth().coerceIn(0f, 100f)
            // A rep is over once it has come back up past half the count line.
            if (d > peak[0]) peak[0] = d
            if (d < countAt * 0.5f && peak[0] >= countAt) {
                peak[1] = peak[0]
                peakAt[0] = t
                peak[0] = d
            } else if (d < countAt * 0.5f) {
                peak[0] = d
            }

            px(0f, 0f, 48f, gh + 30f, Color(0xC70B0E14))
            px(x - 2f, y0 - 2f, w + 4f, gh + 4f, Color(0xFF2C3340))
            px(x, y0, w, gh, Color(0xFF0B0E14))
            val fillH = (d / 100f * gh / 2f).roundToInt() * 2f
            val col = when {
                d >= deepAt -> Color(0xFFFFC53D)
                d >= countAt -> Color(0xFFFF9F43)
                else -> Color(0xFF8FA3BF)
            }
            px(x, y0, w, fillH, col)
            if (d >= deepAt && !calm && (t / 80_000_000L) % 2L == 1L) px(x, y0 + fillH - 4f, w, 4f, Color(0xFFFFF4BC))
            var yy = y0 + 4f
            while (yy < y0 + fillH) {
                px(x, yy, w, 2f, Color(0x2E000000))
                yy += 6f
            }
            for ((at, c) in listOf(countAt to Color(0xFFFF9F43), deepAt to Color(0xFFFFC53D))) {
                px(x - 6f, y0 + (at / 100f * gh).roundToInt(), w + 10f, 2f, c)
            }
            if (peak[1] >= 0f && t - peakAt[0] < 1_400_000_000L) {
                val py = y0 + (peak[1] / 100f * gh).roundToInt()
                val c = if (calm || (t / 100_000_000L) % 2L == 1L) Color.White else Color(0xFFC8D0DC)
                px(x - 3f, py - 1f, w + 6f, 3f, c)
            }
        }
        // The labels to the bar's left, sitting on their lines.
        for ((at, label, c) in listOf(
            Triple(countAt, R.string.survival_depth_count, Color(0xFFFF9F43)),
            Triple(deepAt, R.string.survival_depth_deep, Color(0xFFFFC53D)),
        )) {
            val top = (16f + (at / 100f * 200f).roundToInt() - 12f) * u
            Text(
                text = stringResource(label),
                style = labelStyle,
                color = c,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset(x = with(density) { (2f * u).toDp() }, y = with(density) { top.toDp() }),
            )
        }
    }
}
