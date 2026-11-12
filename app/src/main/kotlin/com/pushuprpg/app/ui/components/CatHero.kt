package com.pushuprpg.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.pushuprpg.app.ui.theme.PixelShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.ui.theme.LocalReduceMotion
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.app.ui.theme.Type
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.WearSlot

/**
 * 고양이 at rest, on a cushion: the hub's face.
 *
 * The same drawing the run uses, calm and bright-eyed, with the tail swaying on its own clock — still
 * under reduced motion. [cheer] is passed through; the eyes stay open and bright. It wears [wear], and
 * sits in the scene among them, if there is one, on a card of its own.
 */
@Composable
fun CatPortrait(
    coat: CatCoat,
    modifier: Modifier = Modifier,
    wear: Set<CatItem> = emptySet(),
    cheer: Float = 1f,
) {
    val scene = wear.firstOrNull { it.slot == WearSlot.SCENE }
    val reduceMotion = LocalReduceMotion.current
    // The tail sways in a sprite's frames, [TAIL_FRAMES] to a sway, rather than every vsync: the hub
    // sat redrawing a cat that had not visibly moved sixty times a second. Still under reduced motion.
    val frame = remember { mutableIntStateOf(0) }
    if (!reduceMotion) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(SWAY_MS / TAIL_FRAMES)
                frame.intValue = (frame.intValue + 1) % TAIL_FRAMES
            }
        }
    }
    Box(if (scene != null) modifier.clip(SCENE_SHAPE) else modifier) {
        if (scene != null) Canvas(Modifier.matchParentSize()) { drawCatPortraitScene(wear) }
        // The cat in a layer of its own, so a new tail frame does not redraw the scene with it, a
        // couple of hundred shapes that never move.
        Canvas(Modifier.matchParentSize().graphicsLayer()) {
            val phase = if (reduceMotion) 0f else frame.intValue / TAIL_FRAMES.toFloat()
            drawCatPortrait(coat = coat, wear = wear, cheer = cheer, phase = phase, scene = false)
        }
    }
}

private const val SWAY_MS = 4_000L
private const val TAIL_FRAMES = 16

private val SCENE_SHAPE = PixelShape(24.dp)

/**
 * What the cat says, in a comm panel with its name on top (the space look, 2026-11-11: a paper bubble
 * was the brightest thing on a dark screen, brighter than the button). A deep violet panel, the name
 * in the brand colour, and a tail of stepped pixels pointing down at the cat.
 */
@Composable
fun CatSpeechBubble(name: String, text: String, modifier: Modifier = Modifier) {
    val face = Palette.Bg3
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .background(face, PixelShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = name, style = Type.labelM, color = Palette.Brand400)
            Text(text = text, style = Type.bodyL, color = Palette.TextPrimary, textAlign = TextAlign.Center)
        }
        // Three rows of whole pixels, each narrower than the last: a pixel tail, not a smooth wedge.
        Canvas(Modifier.size(width = 18.dp, height = 9.dp)) {
            val step = size.height / 3f
            for (row in 0 until 3) {
                val inset = row * step
                drawRect(face, topLeft = Offset(inset, row * step), size = Size(size.width - 2 * inset, step))
            }
        }
    }
}
