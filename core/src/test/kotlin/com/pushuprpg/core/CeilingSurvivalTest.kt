package com.pushuprpg.core

import com.pushuprpg.core.survival.CeilingSurvival
import com.pushuprpg.core.survival.SurvivalEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CeilingSurvivalTest {

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
                game.onRep(depth, t)
                nextRep += repInterval
            }
        }
        return game.state().elapsedMs
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

    @Test
    fun `a rep that falls short still helps, and says so`() {
        val game = CeilingSurvival()
        game.update(0)
        game.update(1000)
        val before = game.state().height
        val events = game.onRep(50f, 1000)
        assertTrue(game.state().height > before, "a genuine attempt should lift something")
        assertTrue(events.any { it is SurvivalEvent.NearMiss }, "and should be reported as a near miss")
    }

    @Test
    fun `flailing at the camera lifts nothing`() {
        val game = CeilingSurvival()
        game.update(0)
        game.update(1000)
        val before = game.state().height
        val events = game.onRep(5f, 1000)
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
            if (t % 900 < 33) game.onRep(92f, t)
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
                if (t % 1200 < 33) game.onRep(90f, t)
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
            if (t % 700 < 33) game.onRep(95f, t)
        }
        assertTrue(milestones.isNotEmpty(), "a run of ${game.state().elapsedMs}ms announced nothing")
        assertEquals(milestones.sorted(), milestones)
    }
}
