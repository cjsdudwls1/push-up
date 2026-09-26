package com.pushuprpg.core

import com.pushuprpg.core.detect.CalibrationSnapshot
import com.pushuprpg.core.detect.CalibrationState
import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.PoseQuality
import com.pushuprpg.core.detect.RepDetector
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.detect.UserProfile
import com.pushuprpg.core.game.CombatResolver
import com.pushuprpg.core.game.Difficulty
import com.pushuprpg.core.game.Dungeons
import com.pushuprpg.core.game.PlayerClass
import com.pushuprpg.core.game.PlayerState
import com.pushuprpg.core.run.AlertKey
import com.pushuprpg.core.run.BattleEngine
import com.pushuprpg.core.run.Stars
import kotlin.test.assertEquals
import com.pushuprpg.core.trace.PoseTrace
import com.pushuprpg.core.trace.TraceReplay
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Two sets filmed on a phone, as the phone saw them.
 *
 * The device report: pushups that had worked stopped at one rep, and pull-ups showed on the gauge
 * but never counted. Both screen recordings were run back through the app's own pose model
 * (pose_landmarker_lite, VIDEO mode, the app's thresholds) into the traces here.
 *
 * What they showed, and what these tests hold the detector to:
 * - **Pushups, filmed from the head, phone close.** The lite model put a shoulder on the neck for
 *   one frame. The scale jumped, the filter was reset — and the calibrator, which kept the largest
 *   `h` it had seen as the top, moved the top to 2.38 against a real lockout of 1.5-1.8. Every rep
 *   after the first then read 40-60 at the top, never re-armed, and never counted.
 * - **Pull-ups, filmed from behind.** A range from the rig, or from a profile learned in other
 *   units, put the dead hang at 55 on the gauge and the pull at 100: the gauge moved, the count did
 *   not. And as the head reached the bar the wrists were lost and read as falling, doubling the
 *   measured speed of an honest pull.
 *
 * Each set is replayed three ways: from nothing; from the calibration the phone was actually left
 * with (a top the body never reaches, one rep behind it); and from a stale profile. And at three
 * frame rates: the recording's own (about 40 fps) and every second and third frame of it (about 20
 * and 14), because a phone's pose model runs at 15-30 and the rescue that worked at 40 once missed
 * at 20.
 */
class RealTraceTest {

    private fun load(name: String): PoseTrace {
        val stream = checkNotNull(javaClass.getResourceAsStream("/traces/$name")) { "missing trace $name" }
        return GZIPInputStream(stream).use { PoseTrace.decode(it.readBytes().decodeToString()) }
    }

    private val pullUps = load("pullup-from-behind.json.gz")
    private val pushups = load("pushup-from-the-head.json.gz")

    /** Every [stride]th frame, as a slower phone would have seen the same set. */
    private fun PoseTrace.every(stride: Int) = copy(frames = frames.filterIndexed { i, _ -> i % stride == 0 })

    private data class Scenario(val name: String, val detector: () -> RepDetector)

    private fun scenarios(type: ExerciseType, stuck: CalibrationSnapshot, stale: UserProfile) = listOf(
        Scenario("fresh") { DetectorFactory.create(type) },
        Scenario("left stuck") { DetectorFactory.create(type).also { it.restoreCalibration(stuck) } },
        Scenario("stale profile") { DetectorFactory.create(type, profile = stale) },
    )

    private fun replay(trace: PoseTrace, detector: RepDetector): Pair<Int, List<RepEvent>> {
        val events = TraceReplay.frames(trace).flatMap { detector.onFrame(it).events }
        return detector.sessionSummary().repCount to events
    }

    /**
     * [least] reps at each stride, and never more than the set had. A replay that stops counting,
     * or starts counting reps that were not there, fails.
     */
    private fun assertCounts(
        what: String, trace: PoseTrace, done: Int, scenarios: List<Scenario>, least: Map<String, List<Int>>,
    ) {
        for (s in scenarios) for ((i, stride) in listOf(1, 2, 3).withIndex()) {
            val (reps, events) = replay(trace.every(stride), s.detector())
            val refused = events.filterIsInstance<RepEvent.Abandoned>().map { it.reason }
            val min = least.getValue(s.name)[i]
            assertTrue(reps in min..done, "$what, ${s.name}, every ${stride}: $reps reps, wanted $min-$done; refused $refused")
        }
    }

    @Test
    fun `pull-ups filmed from behind count, however the range was left`() = assertCounts(
        "pull-ups from behind", pullUps,
        // Five pulls. The first starts 0.4 s in, before a hang has been seen, so it cannot arm.
        done = 5,
        scenarios = scenarios(
            ExerciseType.PULL_UP,
            stuck = CalibrationSnapshot(ExerciseType.PULL_UP, 1.60f, 0.50f, 0.50f, CalibrationState.CONVERGED, 1, 70f, 88f),
            stale = UserProfile(1.6f, 0.5f, 3),
        ),
        least = mapOf(
            "fresh" to listOf(4, 4, 4),
            // The trap is found by two hangs short of the top band, and the pull between them is lost.
            "left stuck" to listOf(3, 3, 3),
            "stale profile" to listOf(4, 4, 4),
        ),
    )

    @Test
    fun `pushups filmed from the head keep counting past a stray frame`() = assertCounts(
        "pushups from the head", pushups,
        // Four pushups. The first starts 0.25 s in, before the top has been held long enough to
        // anchor the range; at a third of the frame rate that also leaves the second on the prior's
        // range, where a close phone's travel reads too fast.
        done = 4,
        scenarios = scenarios(
            ExerciseType.PUSHUP,
            stuck = CalibrationSnapshot(ExerciseType.PUSHUP, 2.38f, 1.23f, 1.23f, CalibrationState.CONVERGED, 1, 70f, 88f),
            stale = UserProfile(2.4f, 1.2f, 3),
        ),
        least = mapOf(
            "fresh" to listOf(3, 3, 2),
            "left stuck" to listOf(3, 3, 2),
            "stale profile" to listOf(3, 3, 2),
        ),
    )

    /**
     * From the head the tracker loses the body for a frame or two about once a second, and every
     * drop put 추적이 끊긴 동안에는 보스도 멈춰 있어요 over whatever the alert slot was saying. A
     * 기사's 천천히 해야 1개로 쳐요 — nearly the only place the class's rule is written — was gone in
     * under a second. Lost and found now wait for the slot, and a drop from one reason to another is
     * not a second loss.
     */
    @Test
    fun `a coaching line stays up its whole time while the tracker blinks`() {
        val tracking = setOf(AlertKey.QUALITY_LOST, AlertKey.QUALITY_RECOVERED)
        for (stride in listOf(1, 2, 3)) {
            val engine = BattleEngine(
                dungeon = Dungeons.byIndex(3)!!,
                difficulty = Difficulty.STANDARD,
                capacity = 8f,
                initialPlayer = PlayerState.create(PlayerClass.KNIGHT, level = 1),
                detector = DetectorFactory.create(ExerciseType.PUSHUP),
                resolver = CombatResolver(),
            )
            val states = TraceReplay.frames(pushups.every(stride)).map { it.timestampMs to engine.onPoseFrame(it) }
            val lines = states.mapNotNull { it.second.alert }.filter { it.textKey !in tracking }.distinct()
            assertTrue(lines.any { it.textKey == AlertKey.STYLE_TOO_QUICK }, "every $stride: no 기사 line to keep up: $lines")
            for (line in lines) {
                val over = states.firstOrNull { (t, s) ->
                    t >= line.atMs && t - line.atMs < BattleEngine.ALERT_LIFETIME_MS && s.alert?.textKey in tracking
                }
                assertTrue(over == null, "every $stride: ${line.textKey} was covered by ${over?.second?.alert?.textKey} after ${over?.let { it.first - line.atMs }}ms")
            }
            val lost = states.mapNotNull { it.second.alert }.filter { it.textKey == AlertKey.QUALITY_LOST }.distinct().size
            val drops = states.zipWithNext().count { (a, b) -> a.second.quality == PoseQuality.OK && b.second.quality != PoseQuality.OK }
            assertTrue(lost <= drops, "every $stride: $lost tracking-lost toasts for $drops times tracking was lost")
        }
    }

    @Test
    fun `the tops of those pushups hold as a plank, though the camera never sees the legs`() {
        // From the head the ankles are behind the body — visibility 0.05-0.3 — and the plank used to
        // refuse to judge anything without them, so from here it never held at all.
        for (stride in listOf(1, 2, 3)) {
            val (_, events) = replay(pushups.every(stride), DetectorFactory.create(ExerciseType.PLANK))
            val ticks = events.count { it is RepEvent.HoldTick }
            assertTrue(ticks > 0, "every $stride: the pushup tops never held as a plank")
        }
    }

    // --- the second round: six sets filmed with the phone's own camera, 24-30 fps ---
    //
    // The device report: pushups graded 다음엔 더 깊게 with the chest on the floor, lunges filmed at
    // an angle never counted, the plank held neither from the head on the forearms nor side on with
    // the feet out of the picture, and dips were refused as too fast. Each is replayed at the
    // recording's rate and at every second frame, 12-15 fps.

    private class Set(val file: String, val type: ExerciseType, val least: Int, val done: Int, val why: String)

    private val camera = listOf(
        Set("pushup-head-camera.json.gz", ExerciseType.PUSHUP, 5, 5, "five pushups"),
        Set("lunge-angled-a.json.gz", ExerciseType.LUNGE, 3, 3, "three lunges filmed at an angle"),
        // The first lunge is at the bottom when the recording starts.
        Set("lunge-angled-b.json.gz", ExerciseType.LUNGE, 2, 3, "lunges filmed at the other angle"),
        // The first pull is under way when the recording starts.
        Set("pullup-behind-camera.json.gz", ExerciseType.PULL_UP, 2, 3, "pull-ups from behind"),
        // The first dip starts 0.3 s in, inside the wait to arm; the last is cut off by the end.
        Set("dip-front.json.gz", ExerciseType.DIP, 2, 4, "dips from the front"),
    )

    @Test
    fun `every set filmed with the camera counts the reps it shows`() {
        for (set in camera) {
            val trace = load(set.file)
            for (stride in listOf(1, 2)) {
                val (reps, events) = replay(trace.every(stride), DetectorFactory.create(set.type))
                val refused = events.filterIsInstance<RepEvent.Abandoned>().map { it.reason }
                assertTrue(reps in set.least..set.done, "${set.why}, every $stride: $reps reps, wanted ${set.least}-${set.done}; refused $refused")
            }
        }
    }

    /**
     * 시험 중 on the picker is the honest word for a movement no real set has been counted for. Every
     * movement a recording here replays and counts is marked validated, and no other.
     */
    @Test
    fun `the movements marked validated are the ones recorded here`() {
        val recorded = setOf(ExerciseType.PUSHUP, ExerciseType.PULL_UP, ExerciseType.PLANK) + camera.map { it.type }
        assertEquals(recorded, Exercises.ALL.filter { it.validatedOnDevice }.map { it.type }.toSet())
    }

    @Test
    fun `pushups with the chest on the floor grade three stars, every one 깊게`() {
        val engine = BattleEngine(
            dungeon = Dungeons.byIndex(3)!!,
            difficulty = Difficulty.STANDARD,
            capacity = 8f,
            initialPlayer = PlayerState.create(PlayerClass.KNIGHT, level = 1),
            detector = DetectorFactory.create(ExerciseType.PUSHUP),
            resolver = CombatResolver(),
        )
        TraceReplay.frames(load("pushup-head-camera.json.gz")).forEach { engine.onPoseFrame(it) }
        val outcome = engine.quit()
        assertEquals(5, outcome.reps)
        assertEquals(5, outcome.deepReps, "reps that went to the floor were not counted 깊게")
        assertEquals(Stars.THREE, outcome.stars, "mean depth ${outcome.meanDepth}: the result screen said 다음엔 더 깊게")
    }

    @Test
    fun `the plank holds on the forearms from the head and side on, and not on all fours between`() {
        val trace = load("plank-front-then-side.json.gz")
        for (stride in listOf(1, 2)) {
            val detector = DetectorFactory.create(ExerciseType.PLANK)
            val (_, events) = replay(trace.every(stride), detector)
            val ticks = events.filterIsInstance<RepEvent.HoldTick>().map { (it.tMs - trace.frames.first().t) / 1000f }
            assertTrue(ticks.any { it < 2.5f }, "every $stride: the forearm plank from the head never held: $ticks")
            assertTrue(ticks.count { it > 8.5f } >= 5, "every $stride: the side-on plank with the feet out of frame held for ${ticks.count { it > 8.5f }} ticks")
            assertTrue(ticks.none { it in 3.0f..8.5f }, "every $stride: all fours and turning held as a plank at $ticks")
        }
    }
}
