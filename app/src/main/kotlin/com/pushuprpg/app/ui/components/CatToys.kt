package com.pushuprpg.app.ui.components

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pushuprpg.core.progression.CatItem

/**
 * The cat's toys, on the floor at its left: not worn, but the cat's own.
 *
 * [x] is the toy's middle and [floorY] the floor it stands on; [head] is the cat's head radius, the
 * unit everything about the cat is drawn in. A toy keeps within a head either side of [x] and under
 * 2.4 heads tall, so it never reaches the cat or past the edge of a picture; the cat tower, which
 * stands taller than the cat, is the one thing allowed up to 4.4.
 */
internal fun DrawScope.drawCatToy(item: CatItem, x: Float, floorY: Float, head: Float) {
    when (item) {
        else -> Unit
    }
}
