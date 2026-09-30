package com.pushuprpg.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.ui.theme.Palette

/**
 * Text drawn over the camera image.
 *
 * A live camera frame can be any colour at any moment, so nothing readable can rely on contrast
 * with it. Every string over the preview goes through here and gets either a scrim behind it or an
 * ink outline around it — which is why this exists as a component rather than as a convention
 * people are asked to remember.
 */
@Composable
fun CameraText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Palette.TextPrimary,
    legibility: Legibility = Legibility.OUTLINE,
    /** Laid out on one line however wide it is, for a caller that fits it with [onTextLayout]. */
    singleLine: Boolean = false,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val maxLines = if (singleLine) 1 else Int.MAX_VALUE
    when (legibility) {
        Legibility.OUTLINE -> Box(modifier) {
            // A real stroke, offset in four directions, rather than a drop shadow: a shadow fails
            // over a bright background, which on a camera feed is half the time.
            val ink = Palette.OutlineInk
            val offsets = listOf(-2 to 0, 2 to 0, 0 to -2, 0 to 2, -2 to -2, 2 to 2, -2 to 2, 2 to -2)
            offsets.forEach { (dx, dy) ->
                Text(
                    text = text,
                    style = style,
                    color = ink,
                    softWrap = !singleLine,
                    maxLines = maxLines,
                    modifier = Modifier.offset(x = dx.dp, y = dy.dp),
                )
            }
            Text(
                text = text,
                style = style,
                color = color,
                softWrap = !singleLine,
                maxLines = maxLines,
                onTextLayout = onTextLayout,
            )
        }

        Legibility.PANEL -> Text(
            text = text,
            style = style,
            color = color,
            softWrap = !singleLine,
            maxLines = maxLines,
            onTextLayout = onTextLayout,
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.ScrimPanel)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        Legibility.SCRIM -> Text(
            text = text,
            style = style,
            color = color,
            softWrap = !singleLine,
            maxLines = maxLines,
            onTextLayout = onTextLayout,
            modifier = modifier,
        )
    }
}

enum class Legibility { OUTLINE, PANEL, SCRIM }
