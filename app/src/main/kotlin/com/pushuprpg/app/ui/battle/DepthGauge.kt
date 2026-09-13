package com.pushuprpg.app.ui.battle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.drawText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type

/**
 * The 깊이 gauge.
 *
 * Two properties matter more here than anything decorative.
 *
 * It must be readable from a metre away by someone lying on the floor mid-rep, which is why the
 * zones are filled bands rather than hairlines and why the thumb overhangs the track — a marker
 * sitting inside a 22dp groove is invisible at that distance, and this is the one element the user
 * watches continuously.
 *
 * And its thresholds are passed in from the detector's own configuration rather than drawn at
 * hardcoded positions, so the line the user is aiming at is by definition the line the rep counter
 * uses. A balance change can never leave the picture disagreeing with the rules.
 */
@Composable
fun DepthGauge(
    depth: Float,
    countEnter: Float,
    deepEnter: Float,
    sessionBest: Float,
    showNumber: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "깊이",
            style = Type.labelM,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )

        Canvas(
            modifier = Modifier
                .padding(top = 6.dp)
                .width(72.dp)
                .weight(1f),
        ) {
            val trackWidth = density.run { 22.dp.toPx() }
            val trackLeft = size.width - trackWidth - density.run { 6.dp.toPx() }
            val top = density.run { 12.dp.toPx() }
            val bottom = size.height - density.run { 12.dp.toPx() }
            val span = (bottom - top).coerceAtLeast(1f)

            // Depth grows downward, matching the direction the body actually travels.
            fun yFor(value: Float) = top + span * (value / 100f).coerceIn(0f, 1f)

            val corner = CornerRadius(trackWidth / 2f)

            drawRoundRect(
                color = Palette.Bg0.copy(alpha = 0.72f),
                topLeft = Offset(trackLeft, top),
                size = Size(trackWidth, span),
                cornerRadius = corner,
            )

            // Bands rather than boundaries: the space between 인정 and 깊게 is a place to be in,
            // and drawing it as an area is what makes that legible without reading anything.
            drawRect(
                color = colors.acceptDim,
                topLeft = Offset(trackLeft, yFor(countEnter)),
                size = Size(trackWidth, yFor(deepEnter) - yFor(countEnter)),
            )
            drawRect(
                color = colors.deepDim,
                topLeft = Offset(trackLeft, yFor(deepEnter)),
                size = Size(trackWidth, yFor(100f) - yFor(deepEnter)),
            )

            val stateColor = when {
                depth >= deepEnter -> colors.deep
                depth >= countEnter -> colors.accept
                else -> colors.idle
            }

            drawRoundRect(
                color = stateColor.copy(alpha = 0.32f),
                topLeft = Offset(trackLeft, top),
                size = Size(trackWidth, (yFor(depth) - top).coerceAtLeast(0f)),
                cornerRadius = corner,
            )

            val markOverhang = density.run { 7.dp.toPx() }
            val markThickness = density.run { 3.dp.toPx() }
            drawRect(
                color = colors.accept,
                topLeft = Offset(trackLeft - markOverhang, yFor(countEnter) - markThickness / 2f),
                size = Size(trackWidth + markOverhang * 2, markThickness),
            )
            drawRect(
                color = colors.deep,
                topLeft = Offset(trackLeft - markOverhang, yFor(deepEnter) - markThickness / 2f),
                size = Size(trackWidth + markOverhang * 2, markThickness),
            )

            // Today's deepest, so there is something to beat even on a single floor.
            if (sessionBest > 1f) {
                val dash = density.run { 4.dp.toPx() }
                drawLine(
                    color = Color.White.copy(alpha = 0.55f),
                    start = Offset(trackLeft - markOverhang, yFor(sessionBest)),
                    end = Offset(trackLeft + trackWidth + markOverhang, yFor(sessionBest)),
                    strokeWidth = density.run { 1.5.dp.toPx() },
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                )
            }

            val thumbY = yFor(depth)
            val thumbWidth = density.run { 44.dp.toPx() }
            val thumbHeight = density.run { 12.dp.toPx() }
            val thumbLeft = trackLeft + trackWidth / 2f - thumbWidth / 2f

            if (depth >= countEnter) {
                drawRoundRect(
                    color = stateColor.copy(alpha = 0.32f),
                    topLeft = Offset(thumbLeft - 8f, thumbY - thumbHeight),
                    size = Size(thumbWidth + 16f, thumbHeight * 2f),
                    cornerRadius = CornerRadius(thumbHeight),
                )
            }
            drawRoundRect(
                color = Palette.Bg0.copy(alpha = 0.85f),
                topLeft = Offset(thumbLeft - 2f, thumbY - thumbHeight / 2f - 2f),
                size = Size(thumbWidth + 4f, thumbHeight + 4f),
                cornerRadius = CornerRadius(thumbHeight),
            )
            drawRoundRect(
                color = stateColor,
                topLeft = Offset(thumbLeft, thumbY - thumbHeight / 2f),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbHeight),
            )

            drawMarkerLabel(textMeasurer, "인정", colors.accept, trackLeft - markOverhang, yFor(countEnter))
            drawMarkerLabel(textMeasurer, "깊게", colors.deep, trackLeft - markOverhang, yFor(deepEnter))
        }

        if (showNumber) {
            Text(
                text = depth.toInt().toString(),
                style = Type.numeralM,
                color = Palette.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Threshold labels, drawn inside the canvas so they land exactly on their own lines.
 *
 * They stay horizontal. Rotating Korean text to run along the track would make it unreadable at
 * the glance this gauge exists for, and these two words are recognised by position and colour far
 * more than they are actually read.
 */
private fun DrawScope.drawMarkerLabel(
    measurer: TextMeasurer,
    text: String,
    color: Color,
    rightEdge: Float,
    y: Float,
) {
    val layout = measurer.measure(AnnotatedString(text), style = Type.labelS)
    val padX = 5f
    val padY = 2f
    val boxWidth = layout.size.width + padX * 2
    val boxHeight = layout.size.height + padY * 2
    val left = (rightEdge - boxWidth - 4f).coerceAtLeast(0f)
    val top = y - boxHeight / 2f

    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(4f),
    )
    drawText(
        textLayoutResult = layout,
        color = Palette.TextOnAccent,
        topLeft = Offset(left + padX, top + padY),
    )
}
