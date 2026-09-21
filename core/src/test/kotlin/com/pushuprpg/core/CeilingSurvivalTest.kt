package com.pushuprpg.core

import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.RepGrade
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
        repeat(600) { t += 33; game.update(t, active = false) }
        assertEquals(1f, game.state().height, "the ceiling moved while nobody was in position")
        assertEquals(0L, game.state().elapsedMs)
        assertTrue(!game.state().started)
    }

    @Test
    fun `an untracked gap is frozen time, not lost time`() {
        val game = CeilingSurvival()
        var t = 0L
        repeat(90) { t += 33; game.update(t, active = true) }
        val heightBefore = game.state().height
        val elapsedBefore = game.state().elapsedMs
        val scoreBefore = game.state().score

        // Five seconds looking away from the phone.
        repeat(150) { t += 33; game.update(t, active = false) }
        assertEquals(heightBefore, game.state().height, "the ceiling fell during a tracking gap")
        assertEquals(elapsedBefore, game.state().elapsedMs, "the difficulty ramp advanced during a gap")
        assertEquals(scoreBefore, game.state().score)

        // Resuming integrates from the resume frame, not across the gap.
        t += 33
        game.update(t, active = true)
        assertTrue(
            heightBefore - game.state().height < 0.01f,
            "the frozen interval was integrated as descent on resume",
        )
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

    @Test
    fun `a rep that falls short still helps, and says so`() {
        val game = CeilingSurvival()
        game.update(0)
        game.update(1000)
        val before = game.state().height
        val events = game.onRep(graded(50f), 50f, 1000)
        assertTrue(game.state().height > before, "a genuine attempt should lift something")
        assertTrue(events.any { it is SurvivalEvent.NearMiss }, "and should be reported as a near miss")
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
}
