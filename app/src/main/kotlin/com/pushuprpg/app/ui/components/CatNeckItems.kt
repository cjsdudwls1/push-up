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

/** A collar's worth, just under the chin at [chinY]. */
internal fun DrawScope.drawNeckItem(item: CatItem, cx: Float, chinY: Float, head: Float) {
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
