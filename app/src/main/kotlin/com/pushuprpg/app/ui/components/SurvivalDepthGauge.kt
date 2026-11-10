package com.pushuprpg.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.R
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type

/**
 * 고양이 지켜줘's depth: a bar that fills downward as the user goes down, with the line a rep
 * counts at (1회) and the line a deep one does (깊게). By the owner's request (2026-11-10) — the
 * other modes show depth on the skeleton, and the ceiling alone did not say how far was far enough.
 *
 * [depth] is read in the draw phase only, so a frame's depth repaints the bar and nothing else.
 * Depths are the detector's 0..100; [countAt] and [deepAt] are its thresholds on that scale.
 */
@Composable
fun SurvivalDepthGauge(
    depth: () -> Float,
    countAt: Float,
    deepAt: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.width(64.dp).height(220.dp)) {
        val barW = 14.dp
        val h = maxHeight
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(barW)
                .fillMaxHeight()
                .drawBehind {
                    val d = depth().coerceIn(0f, 100f)
                    val r = CornerRadius(4.dp.toPx())
                    drawRoundRect(Palette.ScrimPanelHigh, cornerRadius = r)
                    val fill = d / 100f * size.height
                    val colour = when {
                        d >= deepAt -> Palette.Deep
                        d >= countAt -> Palette.Combo
                        else -> Palette.Idle
                    }
                    if (fill > 0f) drawRoundRect(colour, size = Size(size.width, fill), cornerRadius = r)
                    for ((at, c) in listOf(countAt to Palette.Combo, deepAt to Palette.Deep)) {
                        val y = at / 100f * size.height
                        drawLine(c, Offset(-4.dp.toPx(), y), Offset(size.width + 4.dp.toPx(), y), strokeWidth = 2.dp.toPx())
                    }
                },
        )
        // The labels sit on their lines, to the bar's left. bodyM, as all text over the camera is.
        for ((at, label, c) in listOf(
            Triple(countAt, R.string.survival_depth_count, Palette.Combo),
            Triple(deepAt, R.string.survival_depth_deep, Palette.Deep),
        )) {
            CameraText(
                text = stringResource(label),
                style = Type.bodyM,
                color = c,
                singleLine = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -(barW + 8.dp), y = h * (at / 100f) - 11.dp),
            )
        }
    }
}
