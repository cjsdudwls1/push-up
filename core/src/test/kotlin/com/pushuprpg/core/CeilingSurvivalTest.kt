package com.pushuprpg.core

import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.RepGrade
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.survival.CatCompanion
import com.pushuprpg.core.survival.CatLine
import com.pushuprpg.core.survival.CeilingSurvival
import com.pushuprpg.core.survival.SurvivalEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CeilingSurvivalTest {

    /**
     * The verdict a converged detector would return for [depth].
     *
     * These tests were all written in depths, against the converged lines, so routing them through
     * this keeps every existing assertion saying what it always said — while the production path
     * now takes the detector's grade directly rather than re-deriving one.
     */
    private fun graded(depth: Float, config: DetectorConfig = DetectorConfig.pushup()): RepGrade = when {
        depth >= config.deepEnter -> RepGrade.DEEP
        depth >= config.countEnter -> RepGrade.COUNTED
        else -> RepGrade.SHALLOW
    }

    /** Plays at a fixed cadence and returns how long the run lasted. */
    private fun playAtCadence(repsPerSecond: Float, depth: Float, limitMs: Long = 600_000): Long {
        val game = CeilingSurvival()
        var t = 0L
        val repInterval = (1000f / repsPerSecond).toLong().coerceAtLeast(1L)
        var nextRep = repInterval
        while (game.state().alive && t < limitMs) {
            t += 33
            game.update(t)
            if (t >= nextRep) {
                game.onRep(graded(depth), depth, t)
                nextRep += repInterval
            }
        }
        return game.state().elapsedMs
    }

    @Test
    fun `the ceiling holds still until the user is in position`() {
        // Twenty seconds is longer than the whole starting run. The camera binds while the user is
        // still walking back to the mat; nothing may happen until they are seen at the top.
        val game = CeilingSurvival()
        var t = 0L
        repeat(600) { t += 33; game.update(t, inPosition = false) }
        assertEquals(1f, game.state().height, "the ceiling moved while nobody was in position")
        assertEquals(0L, game.state().elapsedMs)
        assertTrue(!game.state().started)
    }

    /**
     * The owner's rule for this mode: once it has started it never pauses. Sitting up, standing,
     * walking out of view — the ceiling keeps coming, and only the next rep holds it off.
     */
    @Test
    fun `once started, the ceiling keeps coming when the user stops`() {
        val game = CeilingSurvival()
        var t = 0L
        repeat(90) { t += 33; game.update(t, inPosition = true) }
        val heightBefore = game.state().height
        val elapsedBefore = game.state().elapsedMs

        // Five seconds sitting up, out of position.
        repeat(150) { t += 33; game.update(t, inPosition = false) }
        assertTrue(heightBefore - game.state().height > 0.15f, "the ceiling paused while the user rested")
        assertTrue(game.state().elapsedMs - elapsedBefore >= 4_900, "the difficulty ramp paused while the user rested")
    }

    @Test
    fun `resting out of position loses the run`() {
        val game = CeilingSurvival()
        var t = 0L
        game.update(t, inPosition = true)
        while (game.state().alive && t < 120_000) { t += 33; game.update(t, inPosition = false) }
        assertTrue(!game.state().alive, "a run with no reps and nobody in position never ended")
    }

    @Test
    fun `the ceiling falls on its own`() {
        val game = CeilingSurvival()
        var t = 0L
        repeat(60) { t += 33; game.update(t) }
        assertTrue(game.state().height < 1f, "the ceiling should be descending")
    }

    @Test
    fun `doing nothing ends the run, and not instantly`() {
        val game = CeilingSurvival()
        var t = 0L
        var over: SurvivalEvent.GameOver? = null
        while (over == null && t < 60_000) {
            t += 33
            over = game.update(t).filterIsInstance<SurvivalEvent.GameOver>().firstOrNull()
        }
        assertTrue(over != null, "an idle run must end")
        assertTrue(over!!.survivedMs > 5_000, "ended after only ${over!!.survivedMs}ms")
    }

    @Test
    fun `a deep rep buys more time than a shallow one`() {
        val deep = playAtCadence(repsPerSecond = 1f, depth = 95f)
        val shallow = playAtCadence(repsPerSecond = 1f, depth = 60f)
        assertTrue(deep > shallow, "deep survived ${deep}ms vs shallow ${shallow}ms")
    }

    @Test
    fun `the run always ends, even played perfectly at a superhuman cadence`() {
        // The acceleration has to outrun any human, or the mode has no ending and no score.
        val survived = playAtCadence(repsPerSecond = 3f, depth = 100f, limitMs = 900_000)
        assertTrue(survived < 600_000, "a 3 rep/second machine survived ${survived}ms")
    }

    @Test
    fun `a beginner gets a run worth playing and an athlete gets a longer one`() {
        val beginner = playAtCadence(repsPerSecond = 0.5f, depth = 80f)
        val strong = playAtCadence(repsPerSecond = 1.2f, depth = 95f)

        // Long enough to be a real go, short enough to want another one immediately.
        assertTrue(beginner in 35_000..80_000, "beginner run was ${beginner}ms")
        assertTrue(strong > beginner, "strong run ${strong}ms did not beat beginner ${beginner}ms")
    }

    /**
     * The mode's whole job is somebody's first sixty seconds, and a first-time user is judged by a
     * detector still in bootstrap — against a deliberately more forgiving line than the converged
     * one. This used to re-test the depth against the converged line and hand back a near miss for
     * every rep the detector had accepted: a ceiling that barely moved and a combo stuck at zero,
     * which reads as "it isn't seeing me".
     */
    @Test
    fun `a rep the detector accepted counts, even below the converged line`() {
        val game = CeilingSurvival()
        game.update(0)
        game.update(1000)

        // 63 is under DetectorConfig.countEnter (70) but over bootstrapCountEnter: exactly the
        // band a beginner's first reps land in.
        val events = game.onRep(RepGrade.COUNTED, depth = 63f, atMs = 1000)

        assertTrue(events.none { it is SurvivalEvent.NearMiss },
            "the detector accepted this rep; the ceiling must not call it a near miss")
        assertEquals(1, game.state().combo, "an accepted rep builds combo")
        // Asserted on the event rather than the height, which the ceiling clamps at 1.
        val pushed = events.filterIsInstance<SurvivalEvent.Pushed>().single()
        assertTrue(pushed.lift >= CeilingSurvival.LIFT,
            "an accepted rep gets a full push, not a consolation nudge: lift=${pushed.lift}")
    }

    /**
     * The detector decides what counts. A rep it refused is a near miss, and nothing else: no push,
     * no points, and no rep on the card that tells the tutorial how many were done.
     */
    @Test
    fun `a rep that falls short moves nothing and counts nothing, and says so`() {
        val game = CeilingSurvival()
        game.update(0)
        game.update(1000)
        val before = game.state()
        val events = game.onRep(graded(60f), 60f, 1000)
        assertEquals(listOf<SurvivalEvent>(SurvivalEvent.NearMiss(1000)), events)
        assertEquals(before.height, game.state().height, "a refused rep lifted the ceiling")
        assertEquals(before.score, game.state().score, "a refused rep scored")
        assertEquals(0, game.state().reps, "a refused rep was counted")
    }

    @Test
    fun `flailing at the camera lifts nothing`() {
        val game = CeilingSurvival()
        game.update(0)
        game.update(1000)
        val before = game.state().height
        val events = game.onRep(graded(5f), 5f, 1000)
        assertEquals(before, game.state().height)
        assertTrue(events.all { it is SurvivalEvent.NearMiss })
    }

    @Test
    fun `score only ever rises`() {
        val game = CeilingSurvival()
        var t = 0L
        var previous = 0
        while (game.state().alive && t < 120_000) {
            t += 33
            game.update(t)
            if (t % 900 < 33) game.onRep(graded(92f), 92f, t)
            val now = game.state().score
            assertTrue(now >= previous, "score fell from $previous to $now")
            previous = now
        }
        assertTrue(previous > 0)
    }

    @Test
    fun `the run is deterministic`() {
        fun run(): List<Int> {
            val game = CeilingSurvival()
            val samples = mutableListOf<Int>()
            var t = 0L
            while (game.state().alive && t < 90_000) {
                t += 33
                game.update(t)
                if (t % 1200 < 33) game.onRep(graded(90f), 90f, t)
                if (t % 3000 < 33) samples += game.state().score
            }
            return samples
        }
        assertEquals(run(), run())
    }

    @Test
    fun `a long stall does not drop the ceiling through the floor`() {
        val game = CeilingSurvival()
        game.update(0)
        game.update(33)
        // The app was backgrounded for half a minute; that time did not happen for the ceiling.
        game.update(30_000)
        assertTrue(game.state().height > 0.9f, "height fell to ${game.state().height} across a stall")
        assertTrue(game.state().alive)
    }

    @Test
    fun `milestones are announced as the run goes on`() {
        val game = CeilingSurvival()
        var t = 0L
        val milestones = mutableListOf<Int>()
        while (game.state().alive && t < 200_000) {
            t += 33
            milestones += game.update(t).filterIsInstance<SurvivalEvent.Milestone>().map { it.seconds }
            if (t % 700 < 33) game.onRep(graded(95f), 95f, t)
        }
        assertTrue(milestones.isNotEmpty(), "a run of ${game.state().elapsedMs}ms announced nothing")
        assertEquals(milestones.sorted(), milestones)
    }

    // ------------------------------------------------------------ played from a real detector

    /** Plays [frames] through the detector and the run the way the app does, with the cat listening. */
    private class Played(frames: List<PoseFrame>) {
        val game = CeilingSurvival.forExercise(ExerciseType.PUSHUP)
        val events = mutableListOf<SurvivalEvent>()
        val lines = mutableListOf<CatLine>()

        init {
            val detector = DetectorFactory.create(ExerciseType.PUSHUP)
            val cat = CatCompanion()
            for (frame in frames) {
                val tick = detector.onFrame(frame)
                val now = game.onTick(tick)
                events += now
                cat.update(game.state(), now, tick.tMs)
                cat.view().speech?.line?.let { if (lines.lastOrNull() != it) lines += it }
            }
        }
    }

    /**
     * A beginner's half reps, from the detector itself: each reaches the cat as a near miss and
     * none reaches the count. RepEvent.Shallow used to be dropped on the way, so half reps did
     * nothing at all while the ceiling came down — which reads as a camera that cannot see you.
     */
    @Test
    fun `half reps the detector refused are near misses the cat answers, never reps`() {
        // Past the top band, nowhere near the 인정 line.
        val played = Played(PoseFixtures.trace(count = 6, peakDepth = 0.50f))
        assertTrue(played.game.state().started, "the half reps were done in position; the run should have started")
        assertEquals(6, played.events.count { it is SurvivalEvent.NearMiss }, "every half rep is a near miss")
        assertTrue(played.events.none { it is SurvivalEvent.Pushed }, "a half rep pushed the ceiling")
        assertEquals(0, played.game.state().reps, "a half rep was counted")
        assertTrue(CatLine.NEAR_MISS in played.lines, "the cat never said to go deeper: ${played.lines}")
    }

    @Test
    fun `whole reps from the detector push the ceiling and count`() {
        val played = Played(PoseFixtures.trace(count = 6))
        assertEquals(6, played.events.count { it is SurvivalEvent.Pushed })
        assertTrue(played.events.none { it is SurvivalEvent.NearMiss })
        assertEquals(6, played.game.state().reps)
    }

    // ------------------------------------------------------------ every movement, not only pushups

    /** How far one counted rep of [type] lifts a ceiling that has room to rise. */
    private fun liftOfOneRep(type: ExerciseType): Float {
        val game = CeilingSurvival.forExercise(type)
        var t = 0L
        repeat(400) { t += 33; game.update(t) } // down to about 0.4, so no lift is capped at the top
        val before = game.state().height
        val depth = Exercises.of(type).config.countEnter
        game.onRep(RepGrade.COUNTED, depth, t)
        return game.state().height - before
    }

    @Test
    fun `a pull-up moves the ceiling as far as the pushups it is worth`() {
        // A person manages about a third as many pull-ups as pushups; the ceiling is written in
        // pushups, so one pull-up has to move it about three times as far or the mode is unwinnable.
        val pushup = liftOfOneRep(ExerciseType.PUSHUP)
        val pullUp = liftOfOneRep(ExerciseType.PULL_UP)
        val worth = 1f / Exercises.of(ExerciseType.PULL_UP).sessionVolumeScale
        assertEquals(pushup * worth, pullUp, 0.001f)
    }

    @Test
    fun `a pushup run is exactly what it was before movements were added`() {
        assertEquals(liftOfOneRep(ExerciseType.PUSHUP), run {
            val game = CeilingSurvival()
            var t = 0L
            repeat(400) { t += 33; game.update(t) }
            val before = game.state().height
            game.onRep(RepGrade.COUNTED, DetectorConfig.pushup().countEnter, t)
            game.state().height - before
        }, 0.0001f)
    }

    @Test
    fun `a held plank pushes the ceiling, and good form pushes harder`() {
        fun liftOf(formScore: Float): Float {
            val game = CeilingSurvival.forExercise(ExerciseType.PLANK)
            var t = 0L
            repeat(150) { t += 33; game.update(t) }
            val before = game.state().height
            game.onHold(formScore, 0.5f, t)
            return game.state().height - before
        }
        val passable = liftOf(60f)
        val perfect = liftOf(100f)
        assertTrue(passable > 0f, "a plank held at the holding line pushed nothing")
        assertTrue(perfect > passable, "perfect form $perfect did not beat passable $passable")
    }

    /**
     * A plank has no cadence to speed up, so the ramp alone has to end it: held perfectly it
     * should outlast the opening — a good hold is winning at first — and still lose inside a
     * few minutes, like every other run.
     */
    @Test
    fun `a perfect plank outlasts the opening and still ends`() {
        val game = CeilingSurvival.forExercise(ExerciseType.PLANK)
        var t = 0L
        while (game.state().alive && t < 600_000) {
            t += 33
            game.update(t)
            if (t % 500 < 33) game.onHold(100f, 0.5f, t)
        }
        val survivedS = game.state().elapsedMs / 1000
        assertTrue(!game.state().alive, "a plank survived ten minutes")
        assertTrue(survivedS in 40..180, "a perfect plank lasted ${survivedS}s")
        assertEquals(0, game.state().reps, "plank seconds are not reps, here or in a dungeon")
    }
}
