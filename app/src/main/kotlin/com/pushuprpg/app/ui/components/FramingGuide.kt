package com.pushuprpg.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.ui.theme.Palette
import com.pushuprpg.core.detect.ExerciseType

/**
 * Where to be in the picture, drawn on it: a frame to fill and a ghost of the starting pose, over
 * the live skeleton — so setting up is lining the lines up with the ghost, not reading advice.
 *
 * Shown only while setting up. The ghost is the pose as the phone should see it for [exercise]:
 * standing square for a squat or a lunge, hanging for a pull-up, on the bars for a dip, and for a
 * pushup or a plank the body from the head end, receding up the picture.
 */
@Composable
fun FramingGuide(exercise: ExerciseType, modifier: Modifier = Modifier) {
    // Read here, not inside the draw lambda: the palette is theme-scoped.
    val frameColor = Palette.Brand400
    // No caption: the top of the screen is the HUD's, the bottom the placement line's, and that
    // line — spoken as well as shown — already says what to do. The frame and the ghost are the how.
    Canvas(modifier.fillMaxSize()) {
        val box = Size(size.width * 0.72f, size.height * 0.80f)
        val topLeft = Offset((size.width - box.width) / 2f, size.height * 0.10f)
        drawRoundRect(
            color = frameColor.copy(alpha = 0.85f),
            topLeft = topLeft,
            size = box,
            cornerRadius = CornerRadius(28.dp.toPx()),
            style = Stroke(
                width = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18.dp.toPx(), 12.dp.toPx())),
            ),
        )
        drawGhost(exercise, topLeft, box)
    }
}

/** The starting pose, as a stick figure in the guide box: fractions of its width and height. */
private fun DrawScope.drawGhost(exercise: ExerciseType, topLeft: Offset, box: Size) {
    val ink = Color.White.copy(alpha = 0.55f)
    val stroke = box.width * 0.035f
    fun p(x: Float, y: Float) = Offset(topLeft.x + box.width * x, topLeft.y + box.height * y)
    fun bone(a: Offset, b: Offset) = drawLine(ink, a, b, strokeWidth = stroke, cap = StrokeCap.Round)
    fun head(c: Offset, r: Float) = drawCircle(ink, radius = box.width * r, center = c, style = Stroke(stroke))

    when (exercise) {
        ExerciseType.PUSHUP, ExerciseType.PLANK -> {
            // From the head end: the head nearest, the shoulders wide, the body running away up the
            // picture to small feet.
            head(p(0.5f, 0.74f), 0.07f)
            val ls = p(0.30f, 0.64f); val rs = p(0.70f, 0.64f)
            bone(ls, rs)
            bone(ls, p(0.26f, 0.86f)); bone(rs, p(0.74f, 0.86f))
            val hip = p(0.5f, 0.42f)
            bone(p(0.5f, 0.64f), hip)
            bone(p(0.44f, 0.42f), p(0.47f, 0.24f)); bone(p(0.56f, 0.42f), p(0.53f, 0.24f))
        }
        ExerciseType.PULL_UP -> {
            bone(p(0.18f, 0.03f), p(0.82f, 0.03f)) // the bar
            head(p(0.5f, 0.26f), 0.07f)
            val ls = p(0.36f, 0.34f); val rs = p(0.64f, 0.34f)
            bone(ls, rs)
            bone(ls, p(0.30f, 0.03f)); bone(rs, p(0.70f, 0.03f))
            val hip = p(0.5f, 0.60f)
            bone(p(0.5f, 0.34f), hip)
            bone(p(0.44f, 0.60f), p(0.44f, 0.97f)); bone(p(0.56f, 0.60f), p(0.56f, 0.97f))
        }
        ExerciseType.DIP -> {
            head(p(0.5f, 0.10f), 0.07f)
            val ls = p(0.34f, 0.20f); val rs = p(0.66f, 0.20f)
            bone(ls, rs)
            bone(ls, p(0.30f, 0.45f)); bone(rs, p(0.70f, 0.45f))
            bone(p(0.18f, 0.45f), p(0.36f, 0.45f)); bone(p(0.64f, 0.45f), p(0.82f, 0.45f)) // the bars
            val hip = p(0.5f, 0.52f)
            bone(p(0.5f, 0.20f), hip)
            bone(p(0.44f, 0.52f), p(0.44f, 0.80f)); bone(p(0.44f, 0.80f), p(0.40f, 0.97f))
            bone(p(0.56f, 0.52f), p(0.56f, 0.80f)); bone(p(0.56f, 0.80f), p(0.60f, 0.97f))
        }
        ExerciseType.SQUAT, ExerciseType.LUNGE -> {
            head(p(0.5f, 0.08f), 0.075f)
            val ls = p(0.33f, 0.20f); val rs = p(0.67f, 0.20f)
            bone(ls, rs)
            bone(ls, p(0.30f, 0.52f)); bone(rs, p(0.70f, 0.52f))
            val hip = p(0.5f, 0.52f)
            bone(p(0.5f, 0.20f), hip)
            if (exercise == ExerciseType.LUNGE) {
                // One foot forward — nearer the phone, so lower and larger in the picture.
                bone(p(0.42f, 0.52f), p(0.38f, 0.76f)); bone(p(0.38f, 0.76f), p(0.36f, 0.99f))
                bone(p(0.58f, 0.52f), p(0.60f, 0.72f)); bone(p(0.60f, 0.72f), p(0.61f, 0.90f))
            } else {
                bone(p(0.42f, 0.52f), p(0.40f, 0.76f)); bone(p(0.40f, 0.76f), p(0.39f, 0.97f))
                bone(p(0.58f, 0.52f), p(0.60f, 0.76f)); bone(p(0.60f, 0.76f), p(0.61f, 0.97f))
            }
        }
    }
}
