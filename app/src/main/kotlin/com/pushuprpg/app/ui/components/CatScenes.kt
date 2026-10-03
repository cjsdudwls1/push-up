package com.pushuprpg.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pushuprpg.core.progression.CatItem
import kotlin.math.sin
import kotlin.math.cos

/**
 * A place for the cat to sit, drawn over the whole canvas behind it: one of the scenes the calories
 * burned bring. [floorY] is where the cat's paws are.
 */
fun DrawScope.drawCatScene(scene: CatItem, floorY: Float) {
    val w = size.width
    val h = size.height
    when (scene) {
        CatItem.CITY -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1E2A4A), Color(0xFF3B3F6B))))
            drawCircle(Color(0xFFFFF3C4), radius = h * 0.08f, center = Offset(w * 0.82f, h * 0.2f))
            val towers = listOf(0.02f to 0.42f, 0.14f to 0.58f, 0.27f to 0.36f, 0.62f to 0.50f, 0.75f to 0.66f, 0.88f to 0.40f)
            towers.forEach { (x, tall) ->
                val top = floorY - h * tall
                drawRect(Color(0xFF141B30), topLeft = Offset(w * x, top), size = Size(w * 0.11f, floorY - top))
                var y = top + h * 0.05f
                while (y < floorY - h * 0.06f) {
                    drawRect(Color(0xFFFFD66B), topLeft = Offset(w * x + w * 0.025f, y), size = Size(w * 0.02f, h * 0.025f))
                    drawRect(Color(0xFFFFD66B), topLeft = Offset(w * x + w * 0.065f, y), size = Size(w * 0.02f, h * 0.025f))
                    y += h * 0.07f
                }
            }
        }
        CatItem.MOUNTAIN -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF9ED8F5), Color(0xFFE8F6FF))))
            drawCircle(Color(0xFFFFE07A), radius = h * 0.07f, center = Offset(w * 0.18f, h * 0.2f))
            drawPath(
                Path().apply {
                    moveTo(0f, floorY)
                    lineTo(w * 0.5f, floorY - h * 0.55f)
                    lineTo(w, floorY)
                    close()
                },
                color = Color(0xFF7FB7A0),
            )
            drawPath(
                Path().apply {
                    moveTo(0f, floorY)
                    quadraticBezierTo(w * 0.25f, floorY - h * 0.28f, w * 0.55f, floorY - h * 0.12f)
                    quadraticBezierTo(w * 0.8f, floorY - h * 0.02f, w, floorY - h * 0.16f)
                    lineTo(w, floorY)
                    close()
                },
                color = Color(0xFF5E9E7A),
            )
        }
        CatItem.SNOW -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFBFE3FF), Color(0xFFF2FAFF))))
            listOf(0.28f to 0.62f, 0.70f to 0.78f).forEach { (x, tall) ->
                val peak = Offset(w * x, floorY - h * tall)
                val halfBase = w * 0.36f
                drawPath(
                    Path().apply {
                        moveTo(peak.x - halfBase, floorY)
                        lineTo(peak.x, peak.y)
                        lineTo(peak.x + halfBase, floorY)
                        close()
                    },
                    color = Color(0xFF8A9BB5),
                )
                drawPath(
                    Path().apply {
                        moveTo(peak.x - halfBase * 0.3f, peak.y + h * tall * 0.3f)
                        lineTo(peak.x, peak.y)
                        lineTo(peak.x + halfBase * 0.3f, peak.y + h * tall * 0.3f)
                        lineTo(peak.x + halfBase * 0.1f, peak.y + h * tall * 0.22f)
                        lineTo(peak.x - halfBase * 0.1f, peak.y + h * tall * 0.3f)
                        close()
                    },
                    color = Color.White,
                )
            }
        }
        CatItem.SPACE -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0E24), Color(0xFF1B1F4B))))
            STARS.forEach { (x, y) -> drawCircle(Color(0xCCFFFFFF), radius = h * 0.008f, center = Offset(w * x, h * y)) }
            val planet = Offset(w * 0.78f, h * 0.26f)
            drawCircle(Color(0xFFE8A06B), radius = h * 0.09f, center = planet)
            drawOval(
                Color(0xFFF3D9A8),
                topLeft = Offset(planet.x - h * 0.16f, planet.y - h * 0.035f),
                size = Size(h * 0.32f, h * 0.07f),
                style = Stroke(width = h * 0.012f),
            )
        }
        CatItem.BEACH -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF7FD3F7), Color(0xFFD9F3FF))))
            drawCircle(Color(0xFFFFE07A), radius = h * 0.08f, center = Offset(w * 0.80f, h * 0.18f))
            drawRect(Color(0xFF2F8FD8), topLeft = Offset(0f, floorY - h * 0.22f), size = Size(w, h * 0.12f))
            drawRect(Color(0xFFF2D7A0), topLeft = Offset(0f, floorY - h * 0.10f), size = Size(w, h * 0.10f + (h - floorY)))
            // A palm leaning in from the left.
            drawLine(Color(0xFF8B5E34), Offset(w * 0.12f, floorY - h * 0.04f), Offset(w * 0.20f, floorY - h * 0.52f), strokeWidth = w * 0.03f)
            listOf(-60f, -20f, 20f, 60f, 100f).forEach { a ->
                val r = Math.toRadians(a.toDouble())
                val top = Offset(w * 0.20f, floorY - h * 0.52f)
                drawLine(
                    Color(0xFF3E9E5A), top,
                    Offset(top.x + w * 0.14f * cos(r).toFloat(), top.y + h * 0.08f * sin(r.toFloat()) + h * 0.03f),
                    strokeWidth = w * 0.025f, cap = StrokeCap.Round,
                )
            }
        }
        CatItem.GYM -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF3A3F4B), Color(0xFF555B69))))
            // A mirror strip and a rack of dumbbells against the wall.
            drawRect(Color(0x339ED8F5), topLeft = Offset(w * 0.05f, h * 0.12f), size = Size(w * 0.9f, h * 0.32f))
            drawRect(Color(0xFF22252C), topLeft = Offset(w * 0.06f, floorY - h * 0.26f), size = Size(w * 0.30f, h * 0.03f))
            listOf(0.10f, 0.20f, 0.30f).forEach { x ->
                drawCircle(Color(0xFF1A1C21), radius = h * 0.035f, center = Offset(w * x, floorY - h * 0.29f))
            }
            val bar = floorY - h * 0.12f
            drawLine(Color(0xFFBFC4CC), Offset(w * 0.56f, bar), Offset(w * 0.94f, bar), strokeWidth = h * 0.015f)
            listOf(0.58f, 0.92f).forEach { x ->
                drawRoundRect(Color(0xFF1A1C21), topLeft = Offset(w * x - w * 0.02f, bar - h * 0.08f), size = Size(w * 0.04f, h * 0.16f), cornerRadius = CornerRadius(w * 0.01f))
            }
            drawRect(Color(0xFF2A2D35), topLeft = Offset(0f, floorY), size = Size(w, h - floorY))
        }
        else -> Unit
    }
}

/** Where the stars are, as fractions of the canvas: fixed, so the sky does not twinkle between frames. */
private val STARS = listOf(
    0.08f to 0.12f, 0.22f to 0.30f, 0.35f to 0.08f, 0.48f to 0.22f, 0.12f to 0.48f,
    0.58f to 0.40f, 0.66f to 0.10f, 0.92f to 0.14f, 0.88f to 0.46f, 0.30f to 0.55f,
    0.52f to 0.60f, 0.04f to 0.70f, 0.95f to 0.64f, 0.42f to 0.36f, 0.74f to 0.52f,
)
