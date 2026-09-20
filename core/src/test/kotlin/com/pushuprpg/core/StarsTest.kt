package com.pushuprpg.core

import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.run.Stars
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stars are the axis the volume model left empty.
 *
 * Every accepted rep is worth exactly one, so depth cannot buy a shorter run — it buys a better
 * one. What matters is that the grade is read off the same two lines the user is aiming at, for
 * every movement, rather than off numbers of its own.
 */
class StarsTest {

    @Test
    fun `the grade is read off each movement's own lines, not a hardcoded pair`() {
        ExerciseType.entries.forEach { exercise ->
            val c = DetectorConfig.forExercise(exercise)

            // Just over the accept line is one star, however generous that line happens to be.
            assertEquals(
                Stars.ONE, Stars.of(c.countEnter + 0.1f, c.countEnter, c.deepEnter),
                "$exercise: scraping 인정 should be one star",
            )
            // At or past the deep line is three, for every movement.
            assertEquals(
                Stars.THREE, Stars.of(c.deepEnter, c.countEnter, c.deepEnter),
                "$exercise: reaching 깊게 should be three stars",
            )
            assertEquals(
                Stars.TWO, Stars.of((c.countEnter + c.deepEnter) / 2f, c.countEnter, c.deepEnter),
                "$exercise: halfway between the lines should be two stars",
            )
        }
    }

    @Test
    fun `a deeper run never scores worse`() {
        val c = DetectorConfig.pushup()
        var previous = 0
        (50..100).forEach { d ->
            val stars = Stars.of(d.toFloat(), c.countEnter, c.deepEnter).count
            assertTrue(stars >= previous, "depth $d scored $stars after $previous")
            previous = stars
        }
        assertEquals(3, Stars.of(100f, c.countEnter, c.deepEnter).count)
    }

    @Test
    fun `depth cannot shorten a run, only grade it`() {
        // The property that keeps the entry screen's promise honest. Two runs of the same dungeon,
        // one scraping the line and one well past it, must cost the same number of reps.
        val shallow = Stars.of(71f, 70f, 88f)
        val deep = Stars.of(95f, 70f, 88f)
        assertTrue(deep.count > shallow.count, "depth should still be worth something")
        // And the grade is presentation only — it is not an input to any cost.
        assertEquals(1, shallow.count)
        assertEquals(3, deep.count)
    }
}
