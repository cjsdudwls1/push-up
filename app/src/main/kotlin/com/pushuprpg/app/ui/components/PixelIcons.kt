package com.pushuprpg.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.floor

/**
 * A one-colour icon drawn as whole pixels on a square grid: the pixel look's answer to Material's
 * rounded icons, which were the last smooth curves left on the hub (the review of 2026-11-11).
 *
 * Rows are strings, `#` for a lit pixel and anything else for none, so a sprite reads as itself in
 * the source.
 */
@Immutable
class PixelSprite(rows: List<String>) {
    val n: Int = rows.size
    internal val cells: List<Pair<Int, Int>> =
        rows.flatMapIndexed { y, row -> row.mapIndexedNotNull { x, c -> if (c == '#') x to y else null } }

    init {
        require(rows.all { it.length == n }) { "a sprite is square" }
    }
}

/**
 * [sprite] in [tint], its pixels snapped to whole device pixels and centred, so it stays crisp at any
 * size. Read aloud as [contentDescription] if there is one; otherwise it is decoration.
 */
@Composable
fun PixelIcon(sprite: PixelSprite, tint: Color, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(described)) {
        val cell = floor(size.minDimension / sprite.n).coerceAtLeast(1f)
        val ox = floor((size.width - cell * sprite.n) / 2f)
        val oy = floor((size.height - cell * sprite.n) / 2f)
        for ((x, y) in sprite.cells) {
            drawRect(tint, topLeft = Offset(ox + x * cell, oy + y * cell), size = Size(cell, cell))
        }
    }
}

/** The app's icons. Twelve pixels square, so they sit on one grid with each other. */
object PixelIcons {
    val Flame = PixelSprite(
        listOf(
            ".....#......",
            ".....##.....",
            "....###.....",
            "....####..#.",
            "...#####.##.",
            "..#########.",
            ".##########.",
            ".####.#####.",
            ".###...####.",
            ".###...####.",
            "..###.####..",
            "...######...",
        ),
    )
    val Sun = PixelSprite(
        listOf(
            ".....##.....",
            ".#...##...#.",
            "..#......#..",
            "....####....",
            "...######...",
            "##.######.##",
            "##.######.##",
            "...######...",
            "....####....",
            "..#......#..",
            ".#...##...#.",
            ".....##.....",
        ),
    )
    val Moon = PixelSprite(
        listOf(
            "....#####...",
            "..####......",
            ".###........",
            ".###........",
            "###.........",
            "###.........",
            "###.........",
            "###.........",
            ".###......#.",
            ".#####..###.",
            "..########..",
            "....####....",
        ),
    )
    val Gear = PixelSprite(
        listOf(
            ".....##.....",
            "..#..##..#..",
            ".##########.",
            "..###..###..",
            ".##......##.",
            "####....####",
            "####....####",
            ".##......##.",
            "..###..###..",
            ".##########.",
            "..#..##..#..",
            ".....##.....",
        ),
    )
    val Swap = PixelSprite(
        listOf(
            "............",
            "........#...",
            "........##..",
            ".##########.",
            "........##..",
            "........#...",
            "...#........",
            "..##........",
            ".##########.",
            "..##........",
            "...#........",
            "............",
        ),
    )
    val Bars = PixelSprite(
        listOf(
            "............",
            ".........##.",
            ".........##.",
            ".....##..##.",
            ".....##..##.",
            ".##..##..##.",
            ".##..##..##.",
            ".##..##..##.",
            ".##..##..##.",
            ".##..##..##.",
            "............",
            "############",
        ),
    )
    val Play = PixelSprite(
        listOf(
            "...#........",
            "...##.......",
            "...###......",
            "...####.....",
            "...#####....",
            "...######...",
            "...######...",
            "...#####....",
            "...####.....",
            "...###......",
            "...##.......",
            "...#........",
        ),
    )
    val Close = PixelSprite(
        listOf(
            "##........##",
            "###......###",
            ".###....###.",
            "..###..###..",
            "...######...",
            "....####....",
            "....####....",
            "...######...",
            "..###..###..",
            ".###....###.",
            "###......###",
            "##........##",
        ),
    )
}
