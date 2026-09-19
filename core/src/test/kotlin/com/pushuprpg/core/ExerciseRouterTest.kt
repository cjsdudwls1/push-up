package com.pushuprpg.core

import com.pushuprpg.core.detect.ExerciseRouter
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.detect.SkeletonMode
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.pose.PoseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The router picks the exercise by running all of them, so the only question worth asking is
 * whether the right one wins on a real movement — and, just as importantly, whether the wrong ones
 * stay quiet. A router that credits a squat for a pushup is worse than the settings menu it
 * replaces.
 */
class ExerciseRouterTest {

    private fun play(router: ExerciseRouter, frames: List<PoseFrame>): List<ExerciseType> =
        frames.map { router.route(it).exercise }

    private fun strikesPerExercise(frames: List<PoseFrame>): Map<ExerciseType, Int> {
        val router = ExerciseRouter()
        val counts = mutableMapOf<ExerciseType, Int>()
        frames.forEach { frame ->
            router.onFrame(frame)
            router.all.forEach { (type, detector) ->
                // Already advanced by onFrame above; read the detector's own session instead of
                // driving it twice, which would double every timestamp delta.
                counts[type] = detector.sessionSummary().repCount
            }
        }
        return counts
    }

    @Test
    fun `no loaded-barbell detector claims a bodyweight trace`() {
        // Six descriptors were added without a real body ever testing them, and the danger is not
        // that they fail to count — it is that one of them counts something else's reps. A curl
        // detector that strikes on a pushup would take the set off the movement the user is actually
        // doing, credit its capacity to the wrong exercise, and size the next boss from it.
        val loaded = setOf(
            ExerciseType.CURL,
            ExerciseType.OVERHEAD_PRESS,
            ExerciseType.LUNGE,
            ExerciseType.BENCH_PRESS,
            ExerciseType.HINGE,
        )
        val traces = mapOf(
            ExerciseType.PUSHUP to PoseFixtures.trace(count = 8, startMs = 3_600_000L),
            ExerciseType.PULL_UP to PoseFixtures.pullUpTrace(count = 8, startMs = 3_600_000L),
            ExerciseType.SQUAT to PoseFixtures.squatTrace(count = 8, startMs = 3_600_000L),
        )

        traces.forEach { (expected, frames) ->
            val struck = strikesPerExercise(frames).filterValues { it > 0 }.keys
            val intruders = struck.intersect(loaded)
            assertTrue(
                intruders.isEmpty(),
                "a $expected trace was also counted as $intruders",
            )
        }
    }

    @Test
    fun `only the credited detector builds a skeleton`() {
        // Nine skeletons a frame is nine allocations a frame for one that gets drawn. The saving is
        // only sound if the one on screen still arrives, so both halves are asserted here.
        val router = ExerciseRouter()
        router.skeletonMode = SkeletonMode.FULL
        val frames = PoseFixtures.trace(count = 6, startMs = 3_600_000L)

        var drawnFrames = 0
        frames.forEach { frame ->
            val routed = router.route(frame)
            if (routed.tick.render.bones.isNotEmpty()) drawnFrames++
            router.all.forEach { (type, detector) ->
                if (type != routed.exercise) {
                    assertEquals(
                        SkeletonMode.OFF, detector.skeletonMode,
                        "$type was still building a skeleton nobody draws",
                    )
                }
            }
        }

        assertTrue(drawnFrames > 0, "the overlay was never handed anything to draw")
    }

    @Test
    fun `a sustained plank is credited even though a hold never strikes`() {
        // A hold cannot win a race decided by strikes, so without a rule of its own the plank is
        // reachable only by turning auto-detection off — and it would fail silently, crediting
        // whatever the picker happened to be left on.
        val router = ExerciseRouter()
        val frames = PoseFixtures.plankTrace(durationMs = 25_000, startMs = 3_600_000L)
        play(router, frames)

        assertEquals(ExerciseType.PLANK, router.exercise,
            "a 25-second plank ended up credited as ${router.exercise}")
    }

    @Test
    fun `settling into position before the first pushup is not announced as a plank`() {
        // The top of a pushup is a plank by every measure the detector has, so the seconds spent
        // getting into position tick hold time. Believing that would put 플랭크 on screen at the
        // start of every set.
        val router = ExerciseRouter()
        val settle = PoseFixtures.plankTrace(durationMs = 4_000, startMs = 3_600_000L)
        play(router, settle)

        assertTrue(
            router.lastRouted?.committed != true,
            "four seconds of holding still committed to ${router.exercise}",
        )

        val set = PoseFixtures.trace(count = 8, startMs = 3_604_000L)
        play(router, set)
        assertEquals(ExerciseType.PUSHUP, router.exercise,
            "the set that followed was credited as ${router.exercise}")
    }

    @Test
    fun `a pushup set routes to the pushup detector`() {
        val router = ExerciseRouter()
        val frames = PoseFixtures.trace(count = 8, startMs = 3_600_000L)
        val seen = play(router, frames).toSet()

        assertEquals(ExerciseType.PUSHUP, router.exercise,
            "a pushup trace ended up credited as ${router.exercise}")
        assertTrue(ExerciseType.PUSHUP in seen)
    }

    @Test
    fun `a squat set routes to the squat detector`() {
        val router = ExerciseRouter()
        val frames = PoseFixtures.squatTrace(count = 8, startMs = 3_600_000L)
        play(router, frames)

        assertEquals(ExerciseType.SQUAT, router.exercise,
            "a squat trace ended up credited as ${router.exercise}")
    }

    @Test
    fun `a pull-up set routes to the pull-up detector`() {
        val router = ExerciseRouter()
        val frames = PoseFixtures.pullUpTrace(count = 6, startMs = 3_600_000L)
        play(router, frames)

        assertEquals(ExerciseType.PULL_UP, router.exercise,
            "a pull-up trace ended up credited as ${router.exercise}")
    }

    /**
     * The user's own case: pull-ups and pushups alternated. Locking the exercise for a whole run
     * would have made this impossible, and the per-exercise calibration profiles mean switching
     * restores a range that is already warm rather than destroying one.
     */
    @Test
    fun `a superset follows the body from one exercise to the other`() {
        val router = ExerciseRouter()

        val pushups = PoseFixtures.trace(count = 6, startMs = 3_600_000L)
        play(router, pushups)
        val afterPushups = router.exercise

        val pullUps = PoseFixtures.pullUpTrace(
            count = 6,
            startMs = pushups.last().timestampMs + 2_000L,
        )
        play(router, pullUps)
        val afterPullUps = router.exercise

        assertEquals(ExerciseType.PUSHUP, afterPushups, "the pushups were not credited as pushups")
        assertEquals(ExerciseType.PULL_UP, afterPullUps,
            "the set moved to the bar and the router stayed on $afterPushups")
    }

    /**
     * The cheat the body-travel witness exists to stop, and which it was silently not stopping.
     *
     * strikeBlockedReason used to pick ONE cross-check — the first available — so whenever world
     * landmarks were present the declared body-travel check was skipped entirely. A pull-up is the
     * clean demonstration: a hanging body's head does not move relative to its shoulders, so the
     * pushup's witness is exactly the thing that should refuse it, and it was not being asked.
     */
    @Test
    fun `a pushup is not credited for a pull-up`() {
        val router = ExerciseRouter()
        router.all.getValue(ExerciseType.PUSHUP).let { pushupDetector ->
            PoseFixtures.pullUpTrace(count = 6, startMs = 3_600_000L).forEach(router::onFrame)
            assertEquals(0, pushupDetector.sessionSummary().repCount,
                "the pushup detector counted a pull-up: its head-drop witness was not consulted")
        }
    }

    /** Nobody in shot, nothing done: the router must not invent an answer. */
    @Test
    fun `an empty frame commits to nothing`() {
        val router = ExerciseRouter()
        val routed = (0 until 120).map { router.route(PoseFrame.empty(3_600_000L + it * 33L)) }

        assertTrue(routed.none { it.committed },
            "the router committed to an exercise with nobody in frame")
    }

    @Test
    fun `a reset forgets which exercise was being done`() {
        val router = ExerciseRouter()
        play(router, PoseFixtures.pullUpTrace(count = 6, startMs = 3_600_000L))
        router.reset()

        assertEquals(ExerciseType.PUSHUP, router.exercise, "reset did not return to the initial guess")
    }
}
