package com.pushuprpg.app.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.WearSlot

// The pictures the screens frame the cat in, kept as plain drawing rather than inside the composables
// so that scripts/render-cat.sh renders on a plain JVM exactly what the app draws.

/**
 * The hub's cat at rest on its cushion, wearing [wear], in the scene among them if there is one:
 * [CatPortrait]'s picture.
 */
fun DrawScope.drawCatPortrait(coat: CatCoat, wear: Set<CatItem>, cheer: Float, phase: Float) {
    val baseY = size.height * 0.90f
    wear.firstOrNull { it.slot == WearSlot.SCENE }?.let { drawCatScene(it, floorY = baseY) }
    // The cat is 1.95 heads of 30 units tall from its paws to its ear tips, plus room for the tail.
    // A party hat's pompom, the tallest thing it wears, is under a third of a head above them; the cat
    // tower, the tallest toy, stands 4.4 heads from the floor. Its toy reaches 3.4 heads to the left.
    val scale = minOf(size.height / 175f, size.width / 260f)
    val cushionW = 150f * scale
    drawRoundRect(
        color = CUSHION,
        topLeft = Offset(size.width / 2f - cushionW / 2f, baseY - 10f * scale),
        size = Size(cushionW, 22f * scale),
        cornerRadius = CornerRadius(11f * scale),
    )
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

private val CUSHION = Color(0xFFE58A7B)

/**
 * One of the cat's things as the wardrobe shows it, on the user's own cat: a scene with the cat small
 * in it, a toy on the floor beside it, anything worn on the cat from the chest up, so a ribbon is big
 * enough to see. Square, and clipped by the tile.
 */
fun DrawScope.drawItemPicture(item: CatItem, coat: CatCoat) {
    when (item.slot) {
        WearSlot.SCENE -> {
            val floor = size.height * 0.92f
            drawCatScene(item, floorY = floor)
            drawCat(centerX = size.width * 0.45f, baseY = floor, scale = size.minDimension / 160f, coat = coat)
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
