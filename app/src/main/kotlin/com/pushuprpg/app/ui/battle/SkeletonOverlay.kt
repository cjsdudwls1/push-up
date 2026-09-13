package com.pushuprpg.app.ui.battle

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pushuprpg.app.ui.theme.LocalGameColors
import com.pushuprpg.core.detect.RenderSkeleton
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.math.hypot

/**
 * Maps the detector's isotropic landmark coordinates onto the preview as it is actually displayed.
 *
 * The preview is centre-cropped to fill the screen, so the visible region is not the whole camera
 * image; getting this wrong slides the skeleton off the body by exactly the cropped margin, which
 * looks like a tracking bug rather than a layout bug.
 */
private data class PreviewTransform(
    val scaleX: Float,
    val scaleY: Float,
    val offsetX: Float,
    val offsetY: Float,
    val mirrored: Boolean,
) {
    fun map(u: Float, v: Float, imageAspect: Float): Offset {
        val nx = if (imageAspect > 0f) u / imageAspect else u
        val x = if (mirrored) 1f - nx else nx
        return Offset(offsetX + x * scaleX, offsetY + v * scaleY)
    }

    companion object {
        fun centreCrop(
            viewWidth: Float,
            viewHeight: Float,
            imageAspect: Float,
            mirrored: Boolean,
        ): PreviewTransform {
            if (imageAspect <= 0f || viewWidth <= 0f || viewHeight <= 0f) {
                return PreviewTransform(viewWidth, viewHeight, 0f, 0f, mirrored)
            }
            val viewAspect = viewWidth / viewHeight
            return if (viewAspect > imageAspect) {
                val drawnHeight = viewWidth / imageAspect
                PreviewTransform(viewWidth, drawnHeight, 0f, (viewHeight - drawnHeight) / 2f, mirrored)
            } else {
                val drawnWidth = viewHeight * imageAspect
                PreviewTransform(drawnWidth, viewHeight, (viewWidth - drawnWidth) / 2f, 0f, mirrored)
            }
        }
    }
}

/**
 * The pose overlay.
 *
 * The demo's version looked like a debug view — uniform lines, a dot on every joint, and stray
 * points hanging off legs the model was only guessing at. Three things fix that here:
 *
 *  - **`:core` decides what exists.** [RenderSkeleton] only ever contains bones whose endpoints are
 *    both confidently visible, and joints that anchor a drawn bone. This composable cannot draw a
 *    stray dot because it is never given one.
 *  - **Limbs are tapered ribbons, not strokes.** A limb narrows from shoulder to wrist the way a
 *    real one does, which is most of the difference between "diagram" and "drawing".
 *  - **Colour carries rep state.** The overlay is slate at the top of a rep, ramps to mint as the
 *    user crosses the count line and to gold past the deep line. It is information, not decoration.
 *
 * Alpha also drops as the user descends: the body is most worth seeing at the bottom of a rep,
 * which is exactly where an opaque overlay would cover it.
 */
@Composable
fun SkeletonOverlay(
    skeleton: RenderSkeleton,
    depth: Float,
    countEnter: Float,
    deepEnter: Float,
    flare: Float,
    mirrored: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGameColors.current
    val density = LocalDensity.current

    val stateColor = when {
        depth >= deepEnter -> colors.deep
        depth >= countEnter -> colors.accept
        else -> {
            // Ramp continuously through the descent rather than switching at the line, so the user
            // can see the colour approaching the threshold and push a little further.
            val t = (depth / countEnter.coerceAtLeast(1f)).coerceIn(0f, 1f)
            lerp(colors.idle, colors.accept, t * t)
        }
    }
    val baseAlpha = (1f - 0.55f * (depth / 100f).coerceIn(0f, 1f)).coerceIn(0.45f, 1f)
    val alpha = (baseAlpha + flare * (1f - baseAlpha)).coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        if (skeleton.bones.isEmpty()) return@Canvas

        val transform = PreviewTransform.centreCrop(size.width, size.height, skeleton.aspect, mirrored)
        val points = skeleton.joints.associate { joint ->
            joint.index to transform.map(joint.x, joint.y, skeleton.aspect)
        }

        fun px(dp: Dp) = with(density) { dp.toPx() }

        // Three stacked passes: a wide faint halo, a medium one, then the limb itself. A real blur
        // would look marginally better but costs a RenderEffect pass per frame on hardware that is
        // already running a pose model, and this is indistinguishable in motion.
        val passes = listOf(
            px(10.dp) to 0.06f,
            px(5.dp) to 0.12f,
            0f to 1.0f,
        )

        for (bone in skeleton.bones) {
            val a = points[bone.a] ?: continue
            val b = points[bone.b] ?: continue
            val (proximal, distal) = boneWidths(bone.a, bone.b)

            for ((grow, passAlpha) in passes) {
                drawTaperedLimb(
                    from = a,
                    to = b,
                    fromWidth = px(proximal) + grow,
                    toWidth = px(distal) + grow,
                    color = stateColor.copy(alpha = alpha * bone.alpha * passAlpha),
                    blend = if (grow > 0f) BlendMode.Plus else BlendMode.SrcOver,
                )
            }
        }

        // Joints are drawn last and only for the elbows: they are the hinge the movement turns
        // around, so a marker there reads as anatomy. A dot on every point reads as a debug view.
        for (joint in skeleton.joints) {
            if (joint.index != Lm.LEFT_ELBOW && joint.index != Lm.RIGHT_ELBOW) continue
            val p = points[joint.index] ?: continue
            drawCircle(
                color = stateColor.copy(alpha = alpha * joint.alpha * 0.22f),
                radius = px(9.dp),
                center = p,
                blendMode = BlendMode.Plus,
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * joint.alpha * 0.92f),
                radius = px(3.5.dp),
                center = p,
            )
        }
    }
}

/** Limbs taper toward the extremity, the way an arm actually does. */
private fun boneWidths(a: Int, b: Int): Pair<Dp, Dp> = when {
    (a == Lm.LEFT_SHOULDER && b == Lm.RIGHT_SHOULDER) ||
        (a == Lm.RIGHT_SHOULDER && b == Lm.LEFT_SHOULDER) -> 6.5.dp to 6.5.dp
    b == Lm.LEFT_ELBOW || b == Lm.RIGHT_ELBOW -> 7.5.dp to 6.0.dp
    b == Lm.LEFT_WRIST || b == Lm.RIGHT_WRIST -> 6.0.dp to 4.5.dp
    b == Lm.LEFT_HIP || b == Lm.RIGHT_HIP -> 7.0.dp to 6.0.dp
    b == Lm.LEFT_KNEE || b == Lm.RIGHT_KNEE -> 6.5.dp to 5.5.dp
    else -> 5.5.dp to 4.5.dp
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTaperedLimb(
    from: Offset,
    to: Offset,
    fromWidth: Float,
    toWidth: Float,
    color: Color,
    blend: BlendMode,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy)
    if (length < 0.5f) return

    // Unit normal to the limb, so the ribbon widens perpendicular to its own direction rather than
    // to the screen — otherwise a limb pointing diagonally looks pinched.
    val nx = -dy / length
    val ny = dx / length
    val h1 = fromWidth / 2f
    val h2 = toWidth / 2f

    val path = Path().apply {
        moveTo(from.x + nx * h1, from.y + ny * h1)
        lineTo(to.x + nx * h2, to.y + ny * h2)
        lineTo(to.x - nx * h2, to.y - ny * h2)
        lineTo(from.x - nx * h1, from.y - ny * h1)
        close()
    }
    drawPath(path, color, blendMode = blend)

    // Round the ends so consecutive limbs join without a notch at the joint.
    drawCircle(color, radius = h1, center = from, blendMode = blend)
    drawCircle(color, radius = h2, center = to, blendMode = blend)
}
