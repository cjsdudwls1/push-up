package com.pushuprpg.core

import com.pushuprpg.core.detect.*
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The overlay was the most-cited complaint about the demo: debug-looking lines, and stray dots
 * hanging off the legs. These tests pin the two structural rules that fix it.
 */
class SkeletonBuilderTest {

    private val builder = SkeletonBuilder(DetectorConfig.pushup())
    private val estimator = ConfidenceEstimator()

    private fun confidenceFor(frame: com.pushuprpg.core.pose.PoseFrame): FloatArray {
        val c = FloatArray(Lm.COUNT)
        estimator.compute(frame, PoseFixtures.SHOULDER_WIDTH, c)
        return c
    }

    private val legIndices = setOf(
        Lm.LEFT_HIP, Lm.RIGHT_HIP, Lm.LEFT_KNEE, Lm.RIGHT_KNEE, Lm.LEFT_ANKLE, Lm.RIGHT_ANKLE,
    )

    @Test
    fun `minimal mode never draws lower body, however confident the model is`() {
        // Even with the legs reported at full confidence — the case where a threshold alone would
        // let them through — the default mode excludes them by allowlist.
        val frame = PoseFixtures.frame(0L, 0.5f, legConfidence = 0.99f)
        val skeleton = builder.build(frame, confidenceFor(frame), SkeletonMode.MINIMAL, false)

        assertTrue(skeleton.joints.none { it.index in legIndices }, "no lower-body joints")
        assertTrue(skeleton.bones.none { it.a in legIndices || it.b in legIndices }, "no lower-body bones")
        assertTrue(skeleton.joints.any { it.index == Lm.LEFT_SHOULDER }, "arms are still drawn")
    }

    @Test
    fun `full mode drops legs the model is only guessing at`() {
        // The realistic case: a phone on the floor, legs far away and partly out of frame.
        val frame = PoseFixtures.frame(0L, 0.5f, legConfidence = 0.25f)
        val skeleton = builder.build(frame, confidenceFor(frame), SkeletonMode.FULL, false)

        assertTrue(
            skeleton.joints.none { it.index in legIndices },
            "low-confidence legs produced ${skeleton.joints.filter { it.index in legIndices }}",
        )
    }

    @Test
    fun `full mode draws legs that really are visible`() {
        val frame = PoseFixtures.frame(0L, 0.5f, legConfidence = 0.95f)
        val skeleton = builder.build(frame, confidenceFor(frame), SkeletonMode.FULL, false)
        assertTrue(skeleton.joints.any { it.index == Lm.LEFT_KNEE }, "a visible knee should be drawn")
    }

    @Test
    fun `a joint is never drawn without a bone to anchor it`() {
        // The stray-dot failure mode: one endpoint confident, the other not. Neither should appear.
        val frame = PoseFixtures.frame(0L, 0.5f, legConfidence = 0.99f, armsVisible = false)
        val skeleton = builder.build(frame, confidenceFor(frame), SkeletonMode.MINIMAL, false)

        val anchored = skeleton.bones.flatMap { listOf(it.a, it.b) }.toSet()
        assertEquals(anchored, skeleton.joints.map { it.index }.toSet())
        assertTrue(skeleton.joints.none { it.index == Lm.LEFT_WRIST }, "an unseen wrist must not appear")
    }

    @Test
    fun `off mode draws nothing at all`() {
        val frame = PoseFixtures.frame(0L, 0.5f, legConfidence = 0.99f)
        val skeleton = builder.build(frame, confidenceFor(frame), SkeletonMode.OFF, false)
        assertTrue(skeleton.joints.isEmpty() && skeleton.bones.isEmpty())
    }

    @Test
    fun `confidence fades alpha instead of switching joints on and off`() {
        // A joint hovering at the edge of detectability should dissolve, not strobe frame to frame.
        val alphas = listOf(0.55f, 0.62f, 0.70f).map { conf ->
            val frame = PoseFixtures.frame(0L, 0.5f, legConfidence = conf)
            val c = FloatArray(Lm.COUNT) { conf }
            builder.build(frame, c, SkeletonMode.MINIMAL, false).bones.first().alpha
        }
        assertTrue(alphas[0] < alphas[1] && alphas[1] < alphas[2], "alpha should ramp: $alphas")
        assertTrue(alphas.all { it > 0f && it < 1f }, "mid-confidence should be partly transparent")
    }

    @Test
    fun `a landmark outside the frame is never drawn`() {
        // MediaPipe extrapolates landmarks past the image edge; those are invented, not observed.
        val frame = PoseFixtures.frame(0L, 0.5f, legConfidence = 0.99f, centerU = 3.0f)
        val skeleton = builder.build(frame, confidenceFor(frame), SkeletonMode.FULL, false)
        assertTrue(skeleton.joints.isEmpty(), "off-frame joints leaked: ${skeleton.joints}")
    }
}
