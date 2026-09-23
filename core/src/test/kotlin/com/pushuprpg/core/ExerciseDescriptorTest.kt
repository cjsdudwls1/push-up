package com.pushuprpg.core

import com.pushuprpg.core.detect.*
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.game.coefficient
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import com.pushuprpg.core.progression.Streak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The descriptor is the thing that makes an exercise a value rather than five edits, so what has to
 * be pinned is the property that adding a value cannot go quietly wrong.
 */
class ExerciseDescriptorTest {

    @Test
    fun `every exercise the app can select has a descriptor`() {
        // The registry's own init would have thrown by now if it did not, but asserting it here is
        // what turns "the app crashes on first use" into "the test suite says which one is missing".
        ExerciseType.entries.forEach { type ->
            assertEquals(type, Exercises.of(type).type, "descriptor missing or mismatched for $type")
        }
        assertEquals(ExerciseType.entries.size, Exercises.ALL.size)
    }

    @Test
    fun `every exercise declares the things the rest of the game reads off it`() {
        ExerciseType.entries.forEach { type ->
            val d = Exercises.of(type)
            assertEquals(type, d.config.exercise, "$type config disagrees with its descriptor")
            assertTrue(type.coefficient() > 0f, "$type deals no damage")
            assertTrue(d.defaultCapacity > 0f, "$type has no starting capacity")
            assertTrue(Streak.maintained(type, d.streakBar), "$type can never keep a streak")
            assertTrue(!Streak.maintained(type, d.streakBar - 1), "$type streak bar is not a bar")
        }
    }

    @Test
    fun `a streak kept across several movements adds each one's share of its own bar`() {
        // One movement: exactly the single-movement rule, at every bar.
        ExerciseType.entries.forEach { type ->
            val bar = Exercises.of(type).streakBar
            assertTrue(Streak.maintained(mapOf(type to bar)), "$type at its bar")
            assertTrue(!Streak.maintained(mapOf(type to bar - 1)), "$type one short of its bar")
        }
        val pushBar = Exercises.of(ExerciseType.PUSHUP).streakBar
        val squatBar = Exercises.of(ExerciseType.SQUAT).streakBar
        // Half of each is a whole day; half of one alone is not.
        assertTrue(Streak.maintained(mapOf(ExerciseType.PUSHUP to (pushBar + 1) / 2, ExerciseType.SQUAT to (squatBar + 1) / 2)))
        assertTrue(!Streak.maintained(mapOf(ExerciseType.PUSHUP to pushBar / 2)))
        assertTrue(!Streak.maintained(emptyMap()))
    }

    @Test
    fun `counted movements carry a signal and holds deliberately do not`() {
        ExerciseType.entries.forEach { type ->
            val d = Exercises.of(type)
            when (d.kind) {
                // A missing signal on a counted movement is exactly the "silently counts nothing"
                // failure, so it is a construction error rather than a runtime surprise.
                MovementKind.REP -> assertNotNull(d.signal, "$type would count nothing")
                MovementKind.HOLD -> assertNull(d.signal, "$type is held, not counted")
            }
        }
        assertEquals(MovementKind.HOLD, Exercises.PLANK.kind)
    }

    @Test
    fun `the factory picks the detector from the kind, not from the name`() {
        assertTrue(DetectorFactory.create(ExerciseType.PLANK) is PlankDetector)
        assertTrue(DetectorFactory.create(ExerciseType.PUSHUP) is RepDetectorImpl)
        assertTrue(DetectorFactory.create(ExerciseType.SQUAT) is RepDetectorImpl)
        assertTrue(DetectorFactory.create(ExerciseType.PULL_UP) is RepDetectorImpl)
    }

    @Test
    fun `a cross-check must be able to disagree, or be absent`() {
        // The whole point of the second opinion is that it watches something the primary signal
        // does not. A travel check whose endpoints are the primary pair would be the primary signal
        // plus a constant: it could never disagree, and would read as a passing check forever.
        ExerciseType.entries.mapNotNull { Exercises.of(it).signal }.forEach { signal ->
            val travel = signal.bodyTravel ?: return@forEach
            val watched = setOf(travel.from, travel.to)
            val primary = setOf<BodyPoint>(
                BodyPoint.Midpoint(signal.proximal),
                BodyPoint.Midpoint(signal.distal),
            )
            assertTrue(watched != primary, "a travel check on the primary pair tests nothing")
        }
    }

    @Test
    fun `the body normal is oriented toward a landmark that stays on one side of the shoulders`() {
        // If the pair the normal is forced toward crosses the shoulder line mid-rep, the normal
        // flips and the signal inverts half way through the movement.
        assertEquals(Lm.LEFT_WRIST, Exercises.PUSHUP.normalToward.left, "hands on the floor")
        assertEquals(Lm.LEFT_WRIST, Exercises.PULL_UP.normalToward.left, "hands on the bar")
        assertEquals(Lm.LEFT_HIP, Exercises.SQUAT.normalToward.left, "hips, never the wrists")
    }

    @Test
    fun `pushup and squat tuning is exactly what it was before the refactor`() {
        // These are the numbers the two shipped exercises were balanced on. The refactor moved
        // where they are written down; it must not have moved what they are.
        val pushup = DetectorConfig.pushup()
        assertEquals(DetectorConfig(ExerciseType.PUSHUP), pushup, "pushup is still all defaults")
        assertEquals(20f, pushup.topEnter)
        assertEquals(70f, pushup.countEnter)
        assertEquals(88f, pushup.deepEnter)
        assertEquals(1.35f, pushup.hTopPrior)
        assertEquals(0.70f, pushup.hBotPrior)

        val squat = DetectorConfig.squat()
        assertEquals(15f, squat.topEnter)
        assertEquals(68f, squat.countEnter)
        assertEquals(85f, squat.deepEnter)
        assertEquals(450f, squat.maxDescentSpeed)
        assertEquals(900, squat.minRepPeriodMs)
        assertEquals(1.05f, squat.hTopPrior)
        assertEquals(0.05f, squat.hBotPrior)
        assertEquals(-0.50f, squat.botClampMin)

        assertEquals(1.00f, ExerciseType.PUSHUP.coefficient())
        assertEquals(0.85f, ExerciseType.SQUAT.coefficient())
        assertEquals(1.00f, ExerciseType.PLANK.coefficient())
    }

    @Test
    fun `the generic signal reproduces the hand-written pushup and squat formulas exactly`() {
        // The refactor replaced two hand-written computations with one driven by descriptor values.
        // "The tests still pass" is necessary but not sufficient, because the tests are tolerant by
        // design; this recomputes the pre-refactor formulas inline and demands bit equality.
        fun check(config: DetectorConfig, frame: com.pushuprpg.core.pose.PoseFrame, hand: (BodyFrameState, FloatArray) -> Triple<Float, Float, Float>) {
            val tracker = BodyFrameTracker(config)
            val estimator = ConfidenceEstimator()
            val c = FloatArray(Lm.COUNT)
            estimator.compute(frame, 0f, c)
            val body = assertNotNull(tracker.update(frame, c))
            val sample = assertNotNull(DepthSignal.compute(frame, body, c, config))
            val (h, asym, drop) = hand(body, c)
            assertEquals(h, sample.h, "h for ${config.exercise}")
            assertEquals(asym, sample.asymmetry, "asymmetry for ${config.exercise}")
            assertEquals(drop, sample.bodyDrop, "bodyDrop for ${config.exercise}")
        }

        fun dot(frame: com.pushuprpg.core.pose.PoseFrame, b: BodyFrameState, from: Int, to: Int) =
            ((frame.u(to) - frame.u(from)) * b.nU + (frame.v(to) - frame.v(from)) * b.nV) / b.scale

        for (depth in listOf(0f, 0.4f, 0.95f)) {
            val push = PoseFixtures.frame(0L, depth)
            check(DetectorConfig.pushup(), push) { b, c ->
                // h = dot(wrist − shoulder, n̂) / shoulderWidth, blended by side weight.
                val hL = dot(push, b, Lm.LEFT_SHOULDER, Lm.LEFT_WRIST)
                val hR = dot(push, b, Lm.RIGHT_SHOULDER, Lm.RIGHT_WRIST)
                val wL = c[Lm.LEFT_SHOULDER] * c[Lm.LEFT_WRIST]
                val wR = c[Lm.RIGHT_SHOULDER] * c[Lm.RIGHT_WRIST]
                // bodyDrop was noseDrop: the nose below the shoulder line, along n̂.
                val noseDrop = ((push.u(Lm.NOSE) - b.shoulderU) * b.nU +
                    (push.v(Lm.NOSE) - b.shoulderV) * b.nV) / b.scale
                Triple((hL * wL + hR * wR) / (wL + wR), kotlin.math.abs(hL - hR), noseDrop)
            }

            val squat = PoseFixtures.squatFrame(0L, depth)
            check(DetectorConfig.squat(), squat) { b, c ->
                val hL = dot(squat, b, Lm.LEFT_HIP, Lm.LEFT_KNEE)
                val hR = dot(squat, b, Lm.RIGHT_HIP, Lm.RIGHT_KNEE)
                val wL = c[Lm.LEFT_HIP] * c[Lm.LEFT_KNEE]
                val wR = c[Lm.RIGHT_HIP] * c[Lm.RIGHT_KNEE]
                // bodyDrop was shoulderDropOverAnkles: the shoulder-to-ankle extent, NEGATED so it
                // grows with depth like every other exercise's.
                val ankleU = (squat.u(Lm.LEFT_ANKLE) + squat.u(Lm.RIGHT_ANKLE)) / 2f
                val ankleV = (squat.v(Lm.LEFT_ANKLE) + squat.v(Lm.RIGHT_ANKLE)) / 2f
                val extent = ((ankleU - b.shoulderU) * b.nU + (ankleV - b.shoulderV) * b.nV) / b.scale
                Triple((hL * wL + hR * wR) / (wL + wR), kotlin.math.abs(hL - hR), -extent)
            }
        }
    }

    @Test
    fun `the depth signal has no exercise branches left to fall through`() {
        // One implementation, driven by values: the same frames read through two descriptors must
        // produce two different readings, and neither may silently produce none.
        val config = DetectorConfig.pushup()
        val tracker = BodyFrameTracker(config)
        val estimator = ConfidenceEstimator()
        val confidence = FloatArray(Lm.COUNT)
        val frame = PoseFixtures.frame(0L, 0.5f)
        estimator.compute(frame, 0f, confidence)
        val body = assertNotNull(tracker.update(frame, confidence))

        val asPushup = DepthSignal.compute(frame, body, confidence, config)
        assertNotNull(asPushup, "the pushup descriptor must read a pushup frame")
        assertTrue(!asPushup.bodyDrop.isNaN(), "the nose check must be live for a pushup")

        // A plank declares no signal at all, so the rep pipeline cannot be fed one by accident.
        assertNull(
            DepthSignal.compute(frame, body, confidence, DetectorConfig.plank()),
            "a hold has no depth ratio",
        )
    }
}
