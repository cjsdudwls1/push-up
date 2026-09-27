package com.pushuprpg.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.domain.CatCoat
import com.pushuprpg.app.ui.theme.LocalReduceMotion
import com.pushuprpg.app.ui.theme.Type

/**
 * 고냥이 at rest, on a cushion: the hub's face.
 *
 * The same drawing the run uses, calm and smiling, with the tail swaying on its own clock — still
 * under reduced motion. [cheer] above a third closes the eyes into a smile.
 */
@Composable
fun CatPortrait(
    coat: CatCoat,
    modifier: Modifier = Modifier,
    cheer: Float = 1f,
) {
    val reduceMotion = LocalReduceMotion.current
    val clock = rememberInfiniteTransition(label = "portrait")
    val phase by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 4_000, easing = LinearEasing)),
        label = "portrait-phase",
    )
    Canvas(modifier) {
        val baseY = size.height * 0.90f
        // The cat is 1.95 heads of 30 units tall from its paws to its ear tips, plus room for the tail.
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
            phase = if (reduceMotion) 0f else phase,
        )
    }
}

private val CUSHION = Color(0xFFE58A7B)

/**
 * What the cat says, in a paper bubble with its name on top: the same bubble the run draws over
 * the camera, so the hub's cat and the run's are plainly one cat.
 */
@Composable
fun CatSpeechBubble(name: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .background(PAPER, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = name, style = Type.labelM, color = NAME_TAG)
            Text(text = text, style = Type.bodyL, color = INK, textAlign = TextAlign.Center)
        }
        Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
            drawPath(
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                },
                color = PAPER,
            )
        }
    }
}

private val PAPER = Color(0xFFFFF8EC)
private val NAME_TAG = Color(0xFFB0662A)
private val INK = Color(0xFF3A2A18)
