package com.pushuprpg.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.roundToInt
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.WearSlot

// The pictures the screens frame the cat in, kept as plain drawing rather than inside the composables
// so that scripts/render-cat.sh renders on a plain JVM exactly what the app draws.

/**
 * The hub's cat at rest on its cushion, wearing [wear], in the scene among them if there is one:
 * [CatPortrait]'s picture. [scene] false leaves the scene to [drawCatPortraitScene], for a screen that
 * draws it in a layer of its own.
 */
fun DrawScope.drawCatPortrait(coat: CatCoat, wear: Set<CatItem>, cheer: Float, phase: Float, scene: Boolean = true) {
    val baseY = size.height * PORTRAIT_FLOOR
    if (scene) drawCatPortraitScene(wear)
    // The cat is 1.95 heads of 30 units tall from its paws to its ear tips, plus room for the tail.
    // A party hat's pompom, the tallest thing it wears, is under a third of a head above them; the cat
    // tower, the tallest toy, stands 4.4 heads from the floor. Its toy reaches 3.4 heads to the left.
    val scale = minOf(size.height / 175f, size.width / 260f)
    drawPixelCushion(size.width / 2f, baseY, catCell(scale))
    drawCat(
        centerX = size.width / 2f - 8f * scale,
        baseY = baseY,
        scale = scale,
        coat = coat,
        cheer = cheer,
        phase = phase,
        wear = wear,
    )
}

/**
 * The scene among [wear], if there is one, behind the hub's cat. It never moves, so the hub draws it
 * apart from the cat, whose tail sways on every frame: the richer scenes are a couple of hundred
 * shapes, and drawn with the cat they were drawn again sixty times a second.
 */
fun DrawScope.drawCatPortraitScene(wear: Set<CatItem>) {
    val scale = minOf(size.height / 175f, size.width / 260f)
    wear.firstOrNull { it.slot == WearSlot.SCENE }?.let {
        drawPixelScene(it, floorY = size.height * PORTRAIT_FLOOR, pixel = catCell(scale) / 2f)
    }
}

/**
 * A scene as pixel art, to match the cat (2026-11-10, the pixel look the owner chose): drawn smooth
 * into a picture [pixel] times smaller, then laid over the canvas pixel for pixel, unsmoothed. Kept,
 * since a scene never moves; one per scene and size.
 */
fun DrawScope.drawPixelScene(scene: CatItem, floorY: Float, pixel: Float) {
    val p = pixel.coerceAtLeast(1f)
    val cols = ceil(size.width / p).toInt().coerceAtLeast(1)
    val rows = ceil(size.height / p).toInt().coerceAtLeast(1)
    val key = listOf(scene, cols, rows, (floorY / p * 4f).roundToInt())
    val bitmap = sceneBitmaps.getOrPut(key) {
        if (sceneBitmaps.size > 12) sceneBitmaps.clear()
        val b = ImageBitmap(cols, rows)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(b), Size(cols.toFloat(), rows.toFloat()),
        ) { drawCatScene(scene, floorY = floorY / p) }
        b
    }
    drawImage(
        bitmap,
        dstOffset = IntOffset.Zero,
        dstSize = IntSize((cols * p).roundToInt(), (rows * p).roundToInt()),
        filterQuality = FilterQuality.None,
    )
}

private val sceneBitmaps = HashMap<Any, ImageBitmap>()

/** The cushion as stepped pixel rows, in cells [s] of the cat, its top at the cat's paws. */
private fun DrawScope.drawPixelCushion(cx: Float, baseY: Float, s: Float) {
    fun row(halfCells: Float, topCells: Float, hCells: Float, c: Color) {
        val l = (cx - halfCells * s).roundToInt().toFloat()
        val t = (baseY + topCells * s).roundToInt().toFloat()
        val r = (cx + halfCells * s).roundToInt().toFloat()
        val b = (baseY + (topCells + hCells) * s).roundToInt().toFloat()
        drawRect(c, topLeft = Offset(l, t), size = Size(r - l, b - t))
    }
    row(15f, -2f, 1f, CUSHION_EDGE)
    row(16f, -1f, 3f, CUSHION_EDGE)
    row(15f, -1f, 2f, CUSHION)
    row(15f, 1f, 1f, CUSHION_DARK)
    row(15f, 2f, 1f, CUSHION_EDGE)
}

/** Where the hub's cat sits, as a fraction of the picture's height. */
private const val PORTRAIT_FLOOR = 0.90f

private val CUSHION = Color(0xFFE58A7B)
private val CUSHION_DARK = Color(0xFFC46B5E)
private val CUSHION_EDGE = Color(0xFF2B1A12)

/**
 * One of the cat's things as the wardrobe shows it, on the user's own cat: a scene with the cat small
 * in it, a toy on the floor beside it, anything worn on the cat from the chest up, so a ribbon is big
 * enough to see. Square, and clipped by the tile.
 */
fun DrawScope.drawItemPicture(item: CatItem, coat: CatCoat) {
    when (item.slot) {
        WearSlot.SCENE -> {
            val floor = size.height * 0.92f
            val scale = size.minDimension / 160f
            drawPixelScene(item, floorY = floor, pixel = catCell(scale) / 2f)
            drawCat(centerX = size.width * 0.45f, baseY = floor, scale = scale, coat = coat)
        }
        WearSlot.TOY -> {
            // The toy reaches 3.4 heads left of the cat's middle and the cat 1.55 to the right, with the
            // tail left off: about 5.4 heads across, so a head is a 5.4th of the tile, and 30 units.
            drawCat(
                centerX = size.width * 0.66f,
                baseY = size.height * 0.90f,
                scale = size.minDimension / 162f,
                coat = coat,
                wear = setOf(item),
                tail = false,
            )
        }
        else -> {
            // Paws below the frame; a party hat's pompom, the tallest thing worn, just inside the top.
            drawCat(
                centerX = size.width * 0.5f,
                baseY = size.height * 1.22f,
                scale = size.minDimension / 105f,
                coat = coat,
                wear = setOf(item),
                tail = false,
            )
        }
    }
}
