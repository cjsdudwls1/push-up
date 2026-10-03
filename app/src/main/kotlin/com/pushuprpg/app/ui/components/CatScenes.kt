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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.pushuprpg.core.progression.CatItem
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
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
                    quadraticTo(w * 0.25f, floorY - h * 0.28f, w * 0.55f, floorY - h * 0.12f)
                    quadraticTo(w * 0.8f, floorY - h * 0.02f, w, floorY - h * 0.16f)
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
        CatItem.CHERRY_BLOSSOM -> {
            // 벚꽃길: a road under cherry trees in bloom, petals coming down.
            val hz = floorY - h * 0.20f
            val e = edgeUnit(w, h)
            drawRect(Brush.verticalGradient(listOf(Color(0xFFBBE2F6), Color(0xFFFFEFF3)), endY = hz))
            // Far blossom, a haze along the horizon.
            repeat(9) { i ->
                drawCircle(
                    Color(0xFFF9D6E3), radius = h * (0.10f + 0.03f * (i % 3)),
                    center = Offset(w * (0.04f + 0.115f * i), hz - h * 0.01f * (i % 2)),
                )
            }
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFFC4E8A2), Color(0xFF95D07E)), startY = hz, endY = h),
                topLeft = Offset(0f, hz), size = Size(w, h - hz),
            )
            // The road narrows to the horizon: a pink edge, then the pale surface.
            listOf(Triple(0.045f, 0.43f, Color(0xFFEFC6C4)), Triple(0.035f, 0.40f, Color(0xFFFCE9E1))).forEach { (top, bottom, color) ->
                drawPath(
                    Path().apply {
                        moveTo(w * (0.5f - top), hz)
                        lineTo(w * (0.5f + top), hz)
                        lineTo(w * (0.5f + bottom), h)
                        lineTo(w * (0.5f - bottom), h)
                        close()
                    },
                    color = color,
                )
            }
            // Petals already down on the road.
            ROAD_PETALS.forEachIndexed { i, (x, y) ->
                val r = h * (0.010f + 0.010f * y) * (1f + 0.3f * (i % 3))
                drawOval(
                    if (i % 2 == 0) Color(0xFFF8B9CC) else Color(0xFFFFFFFF),
                    topLeft = Offset(w * x - r, h * y - r * 0.4f), size = Size(r * 2f, r * 0.8f),
                )
            }
            // A second row of trees further down the road, then the near pair at the edges.
            blossomTree(w * 0.25f, hz + h * 0.07f, w * 0.25f, h * 0.42f, e * 0.26f, 1f)
            blossomTree(w * 0.75f, hz + h * 0.07f, w * 0.75f, h * 0.42f, e * 0.26f, -1f)
            blossomTree(e * 0.10f, floorY + h * 0.02f, e * 0.14f, h * 0.28f, e * 0.40f, 1f)
            blossomTree(w - e * 0.10f, floorY + h * 0.02f, w - e * 0.14f, h * 0.28f, e * 0.40f, -1f)
            // Petals still falling.
            PETALS.forEachIndexed { i, (x, y) ->
                val r = h * (0.012f + 0.004f * (i % 3))
                val c = Offset(w * x, h * y)
                rotate(20f + 37f * i, pivot = c) {
                    drawOval(
                        if (i % 2 == 0) Color(0xFFFFADC8) else Color(0xFFFFE3EC),
                        topLeft = Offset(c.x - r, c.y - r * 0.55f), size = Size(r * 2f, r * 1.1f),
                    )
                }
            }
        }
        CatItem.AURORA -> {
            // 오로라: green, teal and violet curtains arching over a snowy night, pines at the edges.
            val hz = floorY - h * 0.17f
            val e = edgeUnit(w, h)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF050B24), Color(0xFF0A2142), Color(0xFF14566A)), endY = hz))
            AURORA_STARS.forEach { (x, y, size) ->
                val c = Offset(w * x, h * y)
                if (size >= 2) {
                    // The big ones twinkle: a cross of two thin diamonds.
                    val r = h * 0.026f
                    drawPath(
                        Path().apply {
                            moveTo(c.x, c.y - r); lineTo(c.x + r * 0.22f, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.22f, c.y); close()
                            moveTo(c.x - r * 0.75f, c.y); lineTo(c.x, c.y - r * 0.16f); lineTo(c.x + r * 0.75f, c.y); lineTo(c.x, c.y + r * 0.16f); close()
                        },
                        color = Color(0xE6FFFFFF),
                    )
                } else {
                    drawCircle(Color(0xCCFFFFFF), radius = h * (if (size == 1) 0.009f else 0.006f), center = c)
                }
            }
            auroraCurtain(Color(0xFF9B73FF), mid = 0.08f, left = 0.30f, right = 0.44f, phase = 0.20f, thick = 0.30f, rays = 11)
            auroraCurtain(Color(0xFF2FC8D2), mid = 0.15f, left = 0.46f, right = 0.34f, phase = 0.55f, thick = 0.32f, rays = 13)
            auroraCurtain(Color(0xFF45E39B), mid = 0.20f, left = 0.56f, right = 0.50f, phase = 0.85f, thick = 0.30f, rays = 15)
            // Far hills, then nearer ones, black against the glow.
            drawPath(
                Path().apply {
                    moveTo(0f, hz)
                    lineTo(0f, hz - h * 0.20f)
                    lineTo(w * 0.10f, hz - h * 0.27f)
                    lineTo(w * 0.22f, hz - h * 0.14f)
                    lineTo(w * 0.34f, hz - h * 0.08f)
                    lineTo(w * 0.50f, hz - h * 0.03f)
                    lineTo(w * 0.66f, hz - h * 0.09f)
                    lineTo(w * 0.80f, hz - h * 0.22f)
                    lineTo(w * 0.92f, hz - h * 0.15f)
                    lineTo(w, hz - h * 0.23f)
                    lineTo(w, hz)
                    close()
                },
                color = Color(0xFF123150),
            )
            // Snow, lit a little green from above.
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFFCDE9E6), Color(0xFFA9C4E2)), startY = hz, endY = h),
                topLeft = Offset(0f, hz), size = Size(w, h - hz),
            )
            drawPath(
                Path().apply {
                    moveTo(0f, floorY - h * 0.10f)
                    quadraticTo(w * 0.25f, floorY - h * 0.17f, w * 0.52f, floorY - h * 0.06f)
                    quadraticTo(w * 0.80f, floorY + h * 0.01f, w, floorY - h * 0.12f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                },
                color = Color(0xFFE6F2FB),
            )
            // Pines at the edges, the lit side toward the middle.
            val pine = Color(0xFF0A1B2A)
            val lit = Color(0xFF14354C)
            pine(e * 0.36f, floorY - h * 0.10f, e * 0.52f, Color(0xFF0E2638), lit, 1f)
            pine(w - e * 0.34f, floorY - h * 0.10f, e * 0.46f, Color(0xFF0E2638), lit, -1f)
            pine(e * 0.12f, floorY - h * 0.02f, e * 0.80f, pine, lit, 1f)
            pine(w - e * 0.14f, floorY - h * 0.02f, e * 0.72f, pine, lit, -1f)
        }
        CatItem.UNDERSEA -> {
            // 바닷속: light slanting down from the surface, a reef and seaweed at the sides, a few fish.
            val e = edgeUnit(w, h)
            drawRect(Brush.verticalGradient(0f to Color(0xFF63D8F4), 0.5f to Color(0xFF2B96DC), 1f to Color(0xFF123F88)))
            // The surface, seen from below.
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w, h * 0.05f)
                    for (k in 6 downTo 1) {
                        quadraticTo(w * (k - 0.5f) / 6f, h * (0.05f + 0.035f * (if (k % 2 == 0) 1f else -1f)), w * (k - 1) / 6f, h * 0.05f)
                    }
                    close()
                },
                color = Color(0x40FFFFFF),
            )
            val beam = Brush.verticalGradient(listOf(Color(0x44FFFFFF), Color(0x00FFFFFF)), startY = 0f, endY = floorY)
            listOf(0.10f to 0.05f, 0.30f to 0.08f, 0.52f to 0.045f, 0.70f to 0.09f, 0.90f to 0.06f).forEach { (x, width) ->
                drawPath(
                    Path().apply {
                        moveTo(w * x, 0f)
                        lineTo(w * (x + width), 0f)
                        lineTo(w * (x + width * 2.2f) - h * 0.36f, floorY)
                        lineTo(w * x - h * 0.36f, floorY)
                        close()
                    },
                    brush = beam,
                )
            }
            // Seaweed behind the fish, darker and taller.
            val weedBack = Color(0xFF1E8A66)
            val weedFront = Color(0xFF39B87A)
            listOf(0.05f to 0.62f, 0.20f to 0.50f, 0.34f to 0.40f).forEach { (x, tall) ->
                weedBlade(e * x, floorY - h * 0.02f, h * tall, e * 0.018f, e * 0.05f, weedBack)
                weedBlade(w - e * x, floorY - h * 0.02f, h * tall * 0.92f, e * 0.018f, -e * 0.05f, weedBack)
            }
            // The sand.
            drawPath(
                Path().apply {
                    moveTo(0f, floorY - h * 0.08f)
                    quadraticTo(w * 0.25f, floorY - h * 0.14f, w * 0.52f, floorY - h * 0.05f)
                    quadraticTo(w * 0.80f, floorY + h * 0.01f, w, floorY - h * 0.10f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                },
                color = Color(0xFFF0D9A2),
            )
            drawRect(Color(0xFFE2C688), topLeft = Offset(0f, floorY + h * 0.03f), size = Size(w, h - floorY - h * 0.03f))
            // The reef at the foot of each side.
            coral(e * 0.16f, floorY - h * 0.03f, e * 0.34f, Color(0xFFFF7E8B), Color(0xFFD85A72))
            coral(e * 0.34f, floorY - h * 0.01f, e * 0.22f, Color(0xFFFFA45C), Color(0xFFD9803A))
            coral(w - e * 0.15f, floorY - h * 0.03f, e * 0.30f, Color(0xFFB98BFF), Color(0xFF8D63D8))
            coral(w - e * 0.32f, floorY - h * 0.01f, e * 0.20f, Color(0xFFFF7E8B), Color(0xFFD85A72))
            listOf(0.04f to 0.06f, 0.12f to 0.02f, 0.30f to 0.03f).forEach { (x, tall) ->
                weedBlade(e * x, floorY, h * (0.30f + tall), e * 0.022f, e * 0.04f, weedFront)
                weedBlade(w - e * x, floorY, h * (0.28f + tall), e * 0.022f, -e * 0.04f, weedFront)
            }
            // Fish.
            fish(w * 0.26f, h * 0.40f, h * 0.15f, Color(0xFFFF9F43), Color(0xFFE0762A), 1f)
            fish(w * 0.76f, h * 0.27f, h * 0.13f, Color(0xFFFFD84D), Color(0xFFE0A91F), -1f)
            fish(w * 0.64f, h * 0.15f, h * 0.07f, Color(0xFF7FE0D0), Color(0xFF3FB7B0), -1f)
            // Bubbles.
            BUBBLES.forEach { (x, y, size) ->
                val r = h * (0.012f + 0.010f * size)
                val c = Offset(w * x, h * y)
                drawCircle(Color(0x22FFFFFF), radius = r, center = c)
                drawCircle(Color(0x88FFFFFF), radius = r, center = c, style = Stroke(width = r * 0.22f))
                drawCircle(Color(0xCCFFFFFF), radius = r * 0.2f, center = Offset(c.x - r * 0.38f, c.y - r * 0.38f))
            }
        }
        CatItem.PARK -> {
            // 공원: a sunny lawn with a bench, round trees and a path.
            val hz = floorY - h * 0.22f
            val e = edgeUnit(w, h)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF79C6F3), Color(0xFFD9F1FF)), endY = hz))
            // Clear of the big right tree, which hides the corner in the wide card.
            drawCircle(Color(0x33FFE07A), radius = h * 0.12f, center = Offset(w * 0.68f, h * 0.13f))
            drawCircle(Color(0xFFFFE07A), radius = h * 0.075f, center = Offset(w * 0.68f, h * 0.13f))
            cloud(w * 0.20f, h * 0.22f, h * 0.36f)
            cloud(w * 0.66f, h * 0.33f, h * 0.26f)
            // Hills in the distance.
            drawPath(
                Path().apply {
                    moveTo(0f, hz)
                    lineTo(0f, hz - h * 0.10f)
                    quadraticTo(w * 0.22f, hz - h * 0.22f, w * 0.45f, hz - h * 0.06f)
                    quadraticTo(w * 0.75f, hz - h * 0.20f, w, hz - h * 0.08f)
                    lineTo(w, hz)
                    close()
                },
                color = Color(0xFFA7DE9F),
            )
            drawPath(
                Path().apply {
                    moveTo(0f, hz)
                    lineTo(0f, hz - h * 0.03f)
                    quadraticTo(w * 0.30f, hz - h * 0.10f, w * 0.60f, hz - h * 0.02f)
                    quadraticTo(w * 0.85f, hz - h * 0.08f, w, hz - h * 0.03f)
                    lineTo(w, hz)
                    close()
                },
                color = Color(0xFF8CD08A),
            )
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFF8FD968), Color(0xFF63BC54)), startY = hz, endY = h),
                topLeft = Offset(0f, hz), size = Size(w, h - hz),
            )
            // The path starts wide at the bottom and narrows to the horizon behind the cat. Grey, so a
            // cream tail does not run into it.
            val trail = Path().apply {
                moveTo(w * 0.07f, h)
                cubicTo(w * 0.20f, floorY - h * 0.04f, w * 0.45f, hz + h * 0.10f, w * 0.47f, hz)
                lineTo(w * 0.53f, hz)
                cubicTo(w * 0.55f, hz + h * 0.10f, w * 0.80f, floorY - h * 0.04f, w * 0.93f, h)
                close()
            }
            drawPath(trail, color = Color(0xFFD9D6D0))
            drawPath(trail, color = Color(0xFFB0ABA3), style = Stroke(width = h * 0.014f))
            // Trees at the sides, the bench in front of the left one.
            roundTree(e * 0.15f, floorY - h * 0.04f, h * 0.30f, e * 0.20f)
            roundTree(w - e * 0.14f, floorY - h * 0.04f, h * 0.34f, e * 0.21f)
            bench(e * 0.03f, floorY - h * 0.03f, e * 0.36f)
            // Flowers in the grass.
            FLOWERS.forEachIndexed { i, (x, y) ->
                val c = Offset(w * x, h * y)
                drawCircle(
                    listOf(Color(0xFFFFFFFF), Color(0xFFFFD84D), Color(0xFFFF8FB1), Color(0xFFFFB05C))[i % 4],
                    radius = h * 0.013f, center = c,
                )
                drawCircle(Color(0xFFE8A91F), radius = h * 0.005f, center = c)
            }
        }
        CatItem.STAGE -> {
            // 무대: velvet curtains tied back, a dark backdrop, the lights on the cat.
            val e = edgeUnit(w, h)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0E2E), Color(0xFF3B1C3E)), endY = floorY))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x66FFC878), Color(0x00FFC878)),
                    center = Offset(w * 0.5f, floorY - h * 0.28f), radius = h * 0.80f,
                ),
                radius = h * 0.80f, center = Offset(w * 0.5f, floorY - h * 0.28f),
            )
            // Two spotlights crossing on the middle of the stage.
            val beam = Brush.verticalGradient(listOf(Color(0x66FFE6B0), Color(0x16FFE6B0)), startY = h * 0.14f, endY = floorY)
            listOf(0.22f to 0.40f, 0.78f to 0.60f).forEach { (x, aim) ->
                drawPath(
                    Path().apply {
                        moveTo(w * x - h * 0.025f, h * 0.14f)
                        lineTo(w * x + h * 0.025f, h * 0.14f)
                        lineTo(w * aim + h * 0.30f, floorY)
                        lineTo(w * aim - h * 0.30f, floorY)
                        close()
                    },
                    brush = beam,
                )
                drawRoundRect(Color(0xFF2B2430), topLeft = Offset(w * x - h * 0.04f, h * 0.10f), size = Size(h * 0.08f, h * 0.05f), cornerRadius = CornerRadius(h * 0.012f))
                drawOval(Color(0xFFFFF0C0), topLeft = Offset(w * x - h * 0.028f, h * 0.135f), size = Size(h * 0.056f, h * 0.022f))
            }
            // The boards, and the pool of light on them.
            val plank = h * 0.034f
            drawRect(Color(0xFF9B6B3C), topLeft = Offset(0f, floorY), size = Size(w, h - floorY))
            var row = 0
            var y = floorY
            while (y < h) {
                if (row % 2 == 1) drawRect(Color(0xFF8A5B30), topLeft = Offset(0f, y), size = Size(w, minOf(plank, h - y)))
                drawRect(Color(0xFF5E3A1C), topLeft = Offset(0f, y), size = Size(w, h * 0.008f))
                var x = w * (0.09f + 0.07f * row)
                while (x < w) {
                    drawRect(Color(0xFF5E3A1C), topLeft = Offset(x, y), size = Size(h * 0.008f, minOf(plank, h - y)))
                    x += w * 0.21f
                }
                y += plank
                row++
            }
            listOf(0.46f to 0.075f, 0.34f to 0.055f, 0.22f to 0.035f).forEach { (rx, ry) ->
                drawOval(Color(0x2AFFE6B0), topLeft = Offset(w * 0.5f - h * rx, floorY - h * ry), size = Size(h * rx * 2f, h * ry * 2f))
            }
            // The curtains, drawn at the left and mirrored to the right.
            curtain(floorY, w * 0.21f)
            scale(-1f, 1f, pivot = Offset(w / 2f, 0f)) { curtain(floorY, w * 0.21f) }
            // The valance across the top, with a gold edge.
            val n = maxOf(3, (w / (h * 0.30f)).roundToInt())
            val swag = Path().apply {
                moveTo(w, h * 0.075f)
                for (k in n downTo 1) {
                    quadraticTo(w * (k - 0.5f) / n, h * 0.165f, w * (k - 1) / n, h * 0.075f)
                }
            }
            val valance = Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                addPath(swag)
                close()
            }
            drawPath(valance, color = Color(0xFF7C1020))
            translate(top = -h * 0.014f) { drawPath(valance, color = Color(0xFFB01E2E)) }
            drawPath(swag, GOLD, style = Stroke(width = h * 0.016f, cap = StrokeCap.Round))
            for (k in 0..n) drawCircle(GOLD, radius = h * 0.018f, center = Offset(w * k / n, h * 0.075f))
            // Footlights along the front of the stage.
            repeat(12) { i ->
                val c = Offset(w * (0.04f + 0.08f * i), h - h * 0.045f)
                val r = h * 0.024f
                drawCircle(Color(0x33FFE6A0), radius = r * 1.9f, center = c)
                drawArc(GOLD_DARK, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(c.x - r, c.y - r * 0.4f), size = Size(r * 2f, r * 1.6f))
                drawCircle(Color(0xFFFFF0B8), radius = r * 0.5f, center = Offset(c.x, c.y - r * 0.1f))
            }
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

private const val TAU = 6.2831855f

/** What the things at the edges are sized by: the height, and a little more in the wide card, where they have the room. */
private fun edgeUnit(w: Float, h: Float): Float = h * (1f + 0.35f * ((w / h - 1f) / 0.9f).coerceIn(0f, 1f))

/** A smooth line through [points]: a quadratic between their midpoints. */
private fun Path.curveThrough(points: List<Offset>, move: Boolean) {
    if (move) moveTo(points.first().x, points.first().y) else lineTo(points.first().x, points.first().y)
    for (i in 1 until points.size - 1) {
        quadraticTo(points[i].x, points[i].y, (points[i].x + points[i + 1].x) / 2f, (points[i].y + points[i + 1].y) / 2f)
    }
    lineTo(points.last().x, points.last().y)
}

// --- 벚꽃길 ---

private val BLOSSOM_TRUNK = Color(0xFF5E3F42)
private val BLOSSOM_TRUNK_DARK = Color(0xFF3F2A2E)

/** The puffs of one canopy: across, down and radius, in tree units. */
private val BLOSSOM_PUFFS = listOf(
    Triple(-0.30f, 0.04f, 0.20f), Triple(-0.10f, -0.10f, 0.23f), Triple(0.14f, -0.12f, 0.22f),
    Triple(0.36f, 0.00f, 0.20f), Triple(0.50f, 0.18f, 0.14f), Triple(-0.45f, 0.20f, 0.13f),
    Triple(-0.18f, 0.16f, 0.19f), Triple(0.10f, 0.14f, 0.20f), Triple(0.32f, 0.22f, 0.15f),
    Triple(0.00f, 0.30f, 0.11f),
)

/** Single blossoms on the canopy, in the same units. */
private val BLOSSOM_DOTS = listOf(
    -0.36f to 0.02f, -0.16f to -0.16f, 0.06f to -0.22f, 0.26f to -0.12f, 0.42f to 0.04f, -0.28f to 0.20f,
    -0.04f to 0.04f, 0.18f to 0.08f, 0.40f to 0.24f, 0.10f to 0.28f, -0.08f to 0.24f, 0.52f to 0.16f,
)

/** Petals in the air, as fractions of the canvas. */
private val PETALS = listOf(
    0.06f to 0.10f, 0.15f to 0.38f, 0.22f to 0.62f, 0.10f to 0.74f, 0.30f to 0.20f,
    0.34f to 0.50f, 0.27f to 0.82f, 0.43f to 0.12f, 0.46f to 0.34f, 0.58f to 0.08f,
    0.62f to 0.44f, 0.55f to 0.64f, 0.70f to 0.26f, 0.74f to 0.70f, 0.80f to 0.14f,
    0.86f to 0.42f, 0.92f to 0.60f, 0.94f to 0.24f, 0.88f to 0.80f, 0.66f to 0.86f,
    0.38f to 0.78f, 0.50f to 0.90f, 0.03f to 0.52f, 0.97f to 0.90f, 0.78f to 0.52f,
)

/** Petals on the road. */
private val ROAD_PETALS = listOf(
    0.30f to 0.82f, 0.36f to 0.96f, 0.43f to 0.88f, 0.52f to 0.97f, 0.58f to 0.84f, 0.66f to 0.93f,
    0.72f to 0.80f, 0.47f to 0.78f, 0.62f to 0.76f, 0.40f to 0.75f, 0.24f to 0.92f, 0.78f to 0.90f,
)

/**
 * A cherry tree in bloom: its trunk rises from [baseX], [baseY] to a canopy of pink puffs about [cx],
 * [cy], [u] across. [s] is 1 for a canopy that leans right, -1 for one that leans left.
 */
private fun DrawScope.blossomTree(baseX: Float, baseY: Float, cx: Float, cy: Float, u: Float, s: Float) {
    val topY = cy + 0.18f * u
    val rise = baseY - topY
    val trunk = Path().apply {
        moveTo(baseX - 0.075f * u, baseY)
        cubicTo(baseX - 0.03f * u, baseY - rise * 0.45f, cx - 0.07f * u, topY + rise * 0.30f, cx - 0.04f * u, topY)
        lineTo(cx + 0.04f * u, topY)
        cubicTo(cx + 0.07f * u, topY + rise * 0.30f, baseX + 0.03f * u, baseY - rise * 0.45f, baseX + 0.075f * u, baseY)
        close()
    }
    drawPath(trunk, BLOSSOM_TRUNK_DARK)
    clipPath(trunk) { translate(left = -0.045f * u) { drawPath(trunk, BLOSSOM_TRUNK) } }
    drawLine(BLOSSOM_TRUNK, Offset(cx, topY + 0.05f * u), Offset(cx + s * 0.36f * u, cy - 0.02f * u), strokeWidth = 0.04f * u, cap = StrokeCap.Round)
    drawLine(BLOSSOM_TRUNK, Offset(cx, topY + 0.05f * u), Offset(cx - s * 0.32f * u, cy + 0.04f * u), strokeWidth = 0.035f * u, cap = StrokeCap.Round)
    // The shaded underside of every puff first, then the pale tops over it.
    BLOSSOM_PUFFS.forEach { (dx, dy, r) -> drawCircle(Color(0xFFF58FB2), r * u, Offset(cx + s * dx * u, cy + (dy + 0.06f) * u)) }
    BLOSSOM_PUFFS.forEach { (dx, dy, r) -> drawCircle(Color(0xFFFFB9CF), r * u, Offset(cx + s * dx * u, cy + dy * u)) }
    BLOSSOM_PUFFS.take(5).forEach { (dx, dy, r) ->
        drawCircle(Color(0xFFFFD9E5), r * 0.55f * u, Offset(cx + s * dx * u - r * 0.18f * u, cy + dy * u - r * 0.25f * u))
    }
    BLOSSOM_DOTS.forEachIndexed { i, (dx, dy) ->
        drawCircle(if (i % 3 == 0) Color(0xFFFFF4F7) else Color(0xFFF58FB2), 0.022f * u, Offset(cx + s * dx * u, cy + dy * u))
    }
}

// --- 오로라 ---

/** Stars with a size, 0 small to 2 twinkling, as fractions of the canvas. */
private val AURORA_STARS = listOf(
    Triple(0.04f, 0.10f, 1), Triple(0.09f, 0.26f, 0), Triple(0.14f, 0.06f, 2), Triple(0.18f, 0.40f, 0),
    Triple(0.24f, 0.16f, 0), Triple(0.30f, 0.34f, 1), Triple(0.36f, 0.05f, 0), Triple(0.41f, 0.22f, 0),
    Triple(0.47f, 0.09f, 1), Triple(0.52f, 0.30f, 0), Triple(0.57f, 0.14f, 2), Triple(0.63f, 0.04f, 0),
    Triple(0.68f, 0.24f, 0), Triple(0.74f, 0.12f, 1), Triple(0.79f, 0.36f, 0), Triple(0.84f, 0.07f, 0),
    Triple(0.89f, 0.22f, 2), Triple(0.94f, 0.12f, 0), Triple(0.97f, 0.34f, 1), Triple(0.02f, 0.48f, 0),
    Triple(0.11f, 0.52f, 0), Triple(0.92f, 0.52f, 0), Triple(0.33f, 0.46f, 0), Triple(0.70f, 0.48f, 1),
)

/**
 * One curtain of the aurora, arching over the picture. Its bright lower edge sits at [mid] of the
 * height in the middle and at [left] and [right] at the sides, [thick] of the height deep, fading up
 * into [rays] streaks. A few translucent layers, the lowest the brightest, give it the glow.
 */
private fun DrawScope.auroraCurtain(color: Color, mid: Float, left: Float, right: Float, phase: Float, thick: Float, rays: Int) {
    val w = size.width
    val h = size.height
    val steps = 28
    fun edgeY(t: Float): Float {
        val arch = abs(2f * t - 1f).pow(1.7f)
        return h * (mid + ((if (t < 0.5f) left else right) - mid) * arch + 0.03f * sin((t * 3.2f + phase) * TAU))
    }
    fun depth(t: Float): Float = h * thick * (0.8f + 0.2f * sin((t * 1.6f + phase * 2f) * TAU))
    val lower = List(steps + 1) { i -> Offset(w * i / steps, edgeY(i / steps.toFloat())) }
    listOf(1f to 0.10f, 0.62f to 0.13f, 0.34f to 0.17f, 0.14f to 0.24f).forEach { (part, alpha) ->
        val upper = List(steps + 1) { i -> Offset(w * i / steps, edgeY(i / steps.toFloat()) - part * depth(i / steps.toFloat())) }
        drawPath(
            Path().apply {
                curveThrough(lower, move = true)
                curveThrough(upper.reversed(), move = false)
                close()
            },
            color = color.copy(alpha = alpha),
        )
    }
    val yMax = lower.maxOf { it.y }
    val yMin = lower.minOf { it.y } - h * thick
    val streaks = Path()
    repeat(rays) { i ->
        val t = (i + 0.5f) / rays + 0.25f / rays * sin(i * 2.4f)
        val base = edgeY(t)
        val tall = depth(t) * (0.55f + 0.45f * abs(sin(i * 1.7f + phase * 5f)))
        val x = w * t
        val lean = h * 0.03f * sin(i * 1.3f)
        streaks.moveTo(x - h * 0.011f, base)
        streaks.lineTo(x + h * 0.011f, base)
        streaks.lineTo(x + lean, base - tall)
        streaks.close()
    }
    drawPath(streaks, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 0.5f)), startY = yMin, endY = yMax))
    val edge = Path().apply { curveThrough(lower, move = true) }
    drawPath(edge, color.copy(alpha = 0.22f), style = Stroke(width = h * 0.045f, cap = StrokeCap.Round))
    drawPath(edge, color.copy(alpha = 0.65f), style = Stroke(width = h * 0.012f, cap = StrokeCap.Round))
}

/** A pine [tall] high standing at [x], [baseY], the side of its tiers that faces [toward] (1 or -1) [lit]. */
private fun DrawScope.pine(x: Float, baseY: Float, tall: Float, dark: Color, lit: Color, toward: Float) {
    drawRect(dark, topLeft = Offset(x - tall * 0.035f, baseY - tall * 0.16f), size = Size(tall * 0.07f, tall * 0.16f))
    repeat(4) { i ->
        val base = baseY - tall * (0.10f + 0.19f * i)
        val apex = base - tall * 0.36f
        val half = tall * 0.30f * (1f - 0.20f * i)
        val tier = Path().apply {
            moveTo(x - half, base)
            quadraticTo(x - half * 0.40f, base - (base - apex) * 0.50f, x, apex)
            quadraticTo(x + half * 0.40f, base - (base - apex) * 0.50f, x + half, base)
            quadraticTo(x, base - tall * 0.05f, x - half, base)
            close()
        }
        drawPath(tier, dark)
        clipPath(tier) { drawRect(lit, topLeft = Offset(if (toward > 0f) x else x - half, apex), size = Size(half, base - apex)) }
    }
}

// --- 바닷속 ---

/** Bubbles with a size, 0 small to 2 big, as fractions of the canvas. */
private val BUBBLES = listOf(
    Triple(0.07f, 0.55f, 1), Triple(0.11f, 0.40f, 0), Triple(0.05f, 0.28f, 2), Triple(0.15f, 0.18f, 0),
    Triple(0.20f, 0.64f, 0), Triple(0.26f, 0.24f, 1), Triple(0.09f, 0.72f, 0),
    Triple(0.93f, 0.50f, 1), Triple(0.88f, 0.34f, 0), Triple(0.95f, 0.22f, 2), Triple(0.85f, 0.14f, 0),
    Triple(0.80f, 0.60f, 0), Triple(0.74f, 0.20f, 1), Triple(0.91f, 0.70f, 0),
    Triple(0.44f, 0.12f, 0), Triple(0.58f, 0.09f, 1), Triple(0.50f, 0.20f, 0),
)

/** A blade of seaweed [len] tall at [x], [baseY], [half] wide at the foot, swaying [sway] to one side and back, ending in a point. */
private fun DrawScope.weedBlade(x: Float, baseY: Float, len: Float, half: Float, sway: Float, color: Color) {
    drawPath(
        Path().apply {
            moveTo(x - half, baseY)
            cubicTo(x - half + sway, baseY - len * 0.35f, x - sway * 0.8f, baseY - len * 0.65f, x + sway * 0.3f, baseY - len)
            cubicTo(x + half * 0.6f - sway * 0.6f, baseY - len * 0.62f, x + half + sway * 0.7f, baseY - len * 0.32f, x + half, baseY)
            close()
        },
        color = color,
    )
}

/** Branching coral [s] tall standing at [x], [baseY]: a dark outline first, the bright stems over it. */
private fun DrawScope.coral(x: Float, baseY: Float, s: Float, color: Color, dark: Color) {
    val stems = listOf(
        listOf(0f to 0f, 0f to -0.55f, 0f to -0.95f),
        listOf(0f to -0.25f, -0.30f to -0.55f, -0.34f to -0.85f),
        listOf(0f to -0.30f, 0.28f to -0.55f, 0.36f to -0.80f),
    )
    listOf(dark to 1f, color to 0.62f).forEach { (c, thin) ->
        stems.forEach { stem ->
            for (i in 0 until stem.size - 1) {
                drawLine(
                    c,
                    Offset(x + stem[i].first * s, baseY + stem[i].second * s),
                    Offset(x + stem[i + 1].first * s, baseY + stem[i + 1].second * s),
                    strokeWidth = s * 0.17f * thin, cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** A small fish [len] long at [cx], [cy], its nose toward [dir] (1 is right, -1 left). */
private fun DrawScope.fish(cx: Float, cy: Float, len: Float, body: Color, fin: Color, dir: Float) {
    val tailX = cx - dir * len * 0.40f
    drawPath(
        Path().apply {
            moveTo(tailX, cy)
            lineTo(tailX - dir * len * 0.34f, cy - len * 0.26f)
            lineTo(tailX - dir * len * 0.34f, cy + len * 0.26f)
            close()
        },
        color = fin,
    )
    drawPath(
        Path().apply {
            moveTo(cx - dir * len * 0.05f, cy - len * 0.20f)
            lineTo(cx - dir * len * 0.22f, cy - len * 0.40f)
            lineTo(cx - dir * len * 0.26f, cy - len * 0.16f)
            close()
        },
        color = fin,
    )
    drawOval(body, topLeft = Offset(cx - len * 0.5f, cy - len * 0.26f), size = Size(len, len * 0.52f))
    val eye = Offset(cx + dir * len * 0.26f, cy - len * 0.05f)
    drawCircle(Color.White, radius = len * 0.085f, center = eye)
    drawCircle(Color(0xFF1B2A4A), radius = len * 0.045f, center = Offset(eye.x + dir * len * 0.012f, eye.y))
}

// --- 공원 ---

/** Flowers in the grass, as fractions of the canvas. */
private val FLOWERS = listOf(
    0.04f to 0.93f, 0.04f to 0.975f, 0.10f to 0.915f, 0.13f to 0.885f, 0.07f to 0.88f,
    0.80f to 0.84f, 0.91f to 0.93f, 0.93f to 0.86f, 0.955f to 0.97f, 0.97f to 0.80f, 0.90f to 0.895f,
)

/** A cloud [s] wide, its middle at [cx], [cy]: a flat base with three bumps, and a pale blue underside. */
private fun DrawScope.cloud(cx: Float, cy: Float, s: Float) {
    drawRoundRect(Color(0xFFD3E8F8), topLeft = Offset(cx - s * 0.5f, cy - s * 0.04f), size = Size(s, s * 0.22f), cornerRadius = CornerRadius(s * 0.11f))
    drawRoundRect(Color.White, topLeft = Offset(cx - s * 0.5f, cy - s * 0.08f), size = Size(s, s * 0.22f), cornerRadius = CornerRadius(s * 0.11f))
    drawCircle(Color.White, radius = s * 0.17f, center = Offset(cx - s * 0.20f, cy - s * 0.12f))
    drawCircle(Color.White, radius = s * 0.23f, center = Offset(cx + s * 0.04f, cy - s * 0.19f))
    drawCircle(Color.White, radius = s * 0.15f, center = Offset(cx + s * 0.27f, cy - s * 0.08f))
}

/** A round tree: a trunk from [baseY] up to a canopy of radius [r] centred at [x], [cy]. */
private fun DrawScope.roundTree(x: Float, baseY: Float, cy: Float, r: Float) {
    val trunkW = r * 0.28f
    drawRect(Color(0xFF6E4A28), topLeft = Offset(x - trunkW / 2f, cy), size = Size(trunkW, baseY - cy))
    drawRect(Color(0xFF8B5E34), topLeft = Offset(x - trunkW / 2f, cy), size = Size(trunkW * 0.6f, baseY - cy))
    drawCircle(Color(0xFF379A4B), radius = r, center = Offset(x, cy))
    drawCircle(Color(0xFF48B35A), radius = r * 0.94f, center = Offset(x - r * 0.05f, cy - r * 0.06f))
    drawCircle(Color(0xFF6BC970), radius = r * 0.32f, center = Offset(x - r * 0.38f, cy - r * 0.40f))
}

/** A wooden bench seen from the front, [bw] wide, its feet at [baseY] and its left end at [x]. */
private fun DrawScope.bench(x: Float, baseY: Float, bw: Float) {
    val bh = bw * 0.55f
    val iron = Color(0xFF4B3A34)
    val wood = Color(0xFFC98B4E)
    val woodDark = Color(0xFF9A6330)
    drawRect(iron, topLeft = Offset(x + bw * 0.06f, baseY - bh), size = Size(bw * 0.05f, bh))
    drawRect(iron, topLeft = Offset(x + bw * 0.89f, baseY - bh), size = Size(bw * 0.05f, bh))
    listOf(0.98f, 0.76f).forEach { top ->
        drawRoundRect(woodDark, topLeft = Offset(x, baseY - bh * top + bh * 0.03f), size = Size(bw, bh * 0.16f), cornerRadius = CornerRadius(bh * 0.04f))
        drawRoundRect(wood, topLeft = Offset(x, baseY - bh * top), size = Size(bw, bh * 0.14f), cornerRadius = CornerRadius(bh * 0.04f))
    }
    drawRoundRect(woodDark, topLeft = Offset(x - bw * 0.02f, baseY - bh * 0.49f + bh * 0.04f), size = Size(bw * 1.04f, bh * 0.15f), cornerRadius = CornerRadius(bh * 0.04f))
    drawRoundRect(wood, topLeft = Offset(x - bw * 0.02f, baseY - bh * 0.49f), size = Size(bw * 1.04f, bh * 0.13f), cornerRadius = CornerRadius(bh * 0.04f))
    drawRect(iron, topLeft = Offset(x + bw * 0.09f, baseY - bh * 0.34f), size = Size(bw * 0.05f, bh * 0.34f))
    drawRect(iron, topLeft = Offset(x + bw * 0.86f, baseY - bh * 0.34f), size = Size(bw * 0.05f, bh * 0.34f))
}

// --- 무대 ---

/**
 * A velvet curtain hung at the left edge, [cw] wide at its fullest, drawn to [floorY]: pulled in by a
 * gold tie-back at the waist and flaring out again below it. The folds follow the same curve as the
 * inner edge, each a fraction of it.
 */
private fun DrawScope.curtain(floorY: Float, cw: Float) {
    val h = size.height
    val tieY = floorY - h * 0.38f
    fun along(f: Float, path: Path) {
        path.moveTo(cw * 0.80f * f, 0f)
        path.cubicTo(cw * 1.00f * f, h * 0.22f, cw * 0.62f * f, h * 0.42f, cw * 0.58f * f, tieY)
        path.cubicTo(cw * 0.56f * f, h * 0.74f, cw * 1.12f * f, h * 0.84f, cw * 1.02f * f, floorY)
    }
    val shape = Path().apply {
        along(1f, this)
        lineTo(0f, floorY)
        lineTo(0f, 0f)
        close()
    }
    drawPath(shape, Color(0xFFB01E2E))
    clipPath(shape) {
        listOf(0.16f, 0.38f, 0.60f, 0.82f).forEach { f ->
            val fold = Path().apply { along(f, this) }
            drawPath(fold, Color(0xFF7C1020), style = Stroke(width = cw * 0.11f))
            translate(left = cw * 0.07f) { drawPath(fold, Color(0xFFDA4757), style = Stroke(width = cw * 0.035f)) }
        }
    }
    rotate(-6f, pivot = Offset(cw * 0.3f, tieY)) {
        drawRoundRect(GOLD_DARK, topLeft = Offset(-cw * 0.05f, tieY - h * 0.022f), size = Size(cw * 0.74f, h * 0.05f), cornerRadius = CornerRadius(h * 0.02f))
        drawRoundRect(GOLD, topLeft = Offset(-cw * 0.05f, tieY - h * 0.022f), size = Size(cw * 0.74f, h * 0.036f), cornerRadius = CornerRadius(h * 0.018f))
    }
    val tassel = Offset(cw * 0.62f, tieY + h * 0.03f)
    drawPath(
        Path().apply {
            moveTo(tassel.x - h * 0.016f, tassel.y + h * 0.012f)
            lineTo(tassel.x + h * 0.016f, tassel.y + h * 0.012f)
            lineTo(tassel.x + h * 0.030f, tassel.y + h * 0.10f)
            lineTo(tassel.x - h * 0.030f, tassel.y + h * 0.10f)
            close()
        },
        color = GOLD,
    )
    drawCircle(GOLD, radius = h * 0.022f, center = tassel)
}
