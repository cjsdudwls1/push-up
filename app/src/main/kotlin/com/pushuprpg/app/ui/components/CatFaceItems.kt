package com.pushuprpg.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pushuprpg.core.progression.CatItem

/** Over the eyes, which sit at [eyeY], ±0.36 of a head apart. */
internal fun DrawScope.drawFaceItem(item: CatItem, cx: Float, eyeY: Float, head: Float) {
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
