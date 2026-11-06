package com.pushuprpg.core

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.progression.SessionFacts
import com.pushuprpg.core.progression.Trends
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrendsTest {

    private var clock = 0L

    private fun run(exercise: ExerciseType = ExerciseType.PUSHUP, reps: Int = 0, heldMs: Long = 60_000) =
        SessionFacts(exercise, epochDay = 20_717, startedAtMs = ++clock * 1_000L, reps, reps, 0, heldMs)

    @Test
    fun `each session is its whole total, oldest first, and a dip is a dip`() {
        val facts = listOf(run(reps = 30), run(reps = 42), run(reps = 38))
        val trend = Trends.of(facts, ExerciseType.PUSHUP)
        assertEquals(listOf(30, 42, 38), trend.points)
        assertEquals(38, trend.latest)
        assertEquals(-4, trend.change)
        assertEquals(42, trend.peak)
    }

    @Test
    fun `only the last ten are drawn, out of every session`() {
        val facts = (1..14).map { run(reps = it) }
        val trend = Trends.of(facts, ExerciseType.PUSHUP)
        assertEquals((5..14).toList(), trend.points)
        assertEquals(14, trend.sessions)
        assertEquals(1, trend.change)
    }

    @Test
    fun `movements are kept apart, and read in the order they were played whatever the list's order`() {
        val first = run(reps = 10)
        val squat = run(ExerciseType.SQUAT, reps = 50)
        val second = run(reps = 20)
        val trend = Trends.of(listOf(second, squat, first), ExerciseType.PUSHUP)
        assertEquals(listOf(10, 20), trend.points)
        assertEquals(listOf(50), Trends.of(listOf(second, squat, first), ExerciseType.SQUAT).points)
    }

    @Test
    fun `a hold is read in seconds held`() {
        val facts = listOf(run(ExerciseType.PLANK, heldMs = 45_400), run(ExerciseType.PLANK, heldMs = 61_000))
        assertEquals(listOf(45, 61), Trends.of(facts, ExerciseType.PLANK).points)
    }

    @Test
    fun `a session with nothing in it is not drawn as a drop to zero`() {
        val facts = listOf(run(reps = 25), run(reps = 0), run(reps = 27))
        val trend = Trends.of(facts, ExerciseType.PUSHUP)
        assertEquals(listOf(25, 27), trend.points)
        assertEquals(2, trend.change)
    }

    @Test
    fun `every movement has a trend, so the graph can switch to one never done`() {
        val all = Trends.all(listOf(run(reps = 5)))
        assertEquals(ExerciseType.entries.toSet(), all.keys)
        val dip = all.getValue(ExerciseType.DIP)
        assertTrue(dip.empty)
        assertNull(dip.latest)
        assertNull(dip.change)
        assertNull(all.getValue(ExerciseType.PUSHUP).change, "one session has nothing to compare with")
    }
}
