package com.pushuprpg.core

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.progression.Streak
import com.pushuprpg.core.progression.StreakState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreakTest {

    @Test
    fun `a streak survives on a token effort and a break only halves it`() {
        assertTrue(Streak.maintained(reps = 10))
        assertTrue(Streak.maintained(reps = 0, plankSeconds = 60))
        assertTrue(Streak.maintained(reps = 0, squats = 15))
        assertTrue(!Streak.maintained(reps = 3))
        assertEquals(50, Streak.afterBreak(100), "a missed week must not erase a year")
    }

    private val day = 20_000L

    @Test
    fun `a streak is kept by the day's work, not by one run's`() {
        val start = StreakState(days = 3, lastActiveDay = day - 1)
        val five = mapOf(ExerciseType.PUSHUP to 5)
        assertEquals(start, Streak.advance(start, day, five), "five pushups are half a day's bar")
        // The second run of five: the day so far, this run included, is ten.
        assertEquals(StreakState(4, day), Streak.advance(start, day, Streak.sum(five, five)))
        // And a third run that day changes nothing.
        val kept = Streak.advance(start, day, Streak.sum(five, five))
        assertEquals(kept, Streak.advance(kept, day, mapOf(ExerciseType.PUSHUP to 30)))
    }

    @Test
    fun `sessions of different movements add up to the day`() {
        val start = StreakState(days = 0, lastActiveDay = 0L)
        // Five pushups, then eight squats: half of ten and over half of fifteen. Either alone would
        // not keep it.
        val pushups = mapOf(ExerciseType.PUSHUP to 5)
        val squats = mapOf(ExerciseType.SQUAT to 8)
        assertEquals(start, Streak.advance(start, day, squats))
        assertEquals(StreakState(1, day), Streak.advance(start, day, Streak.sum(pushups, squats)))
        // A plank's seconds are its amount: a minute held keeps it on its own.
        val plank = Streak.amount(ExerciseType.PLANK, reps = 0, heldMs = 60_400L)
        assertEquals(60, plank)
        assertEquals(StreakState(1, day), Streak.advance(start, day, mapOf(ExerciseType.PLANK to plank)))
        assertEquals(12, Streak.amount(ExerciseType.SQUAT, reps = 12, heldMs = 0L))
    }

    @Test
    fun `a run is judged on the day it started, and a day never counts twice`() {
        val bar = mapOf(ExerciseType.PUSHUP to 10)
        // A set begun at 23:50 on day - 1 and banked after midnight: day - 1 is the day it earns.
        val evening = Streak.advance(StreakState(5, day - 2), day - 1, bar)
        assertEquals(StreakState(6, day - 1), evening)
        // The next day still has to be earned on its own, and adds one.
        assertEquals(StreakState(7, day), Streak.advance(evening, day, bar))
        // A day before the last one earned — a clock moved back — neither rewinds nor adds.
        val today = StreakState(7, day)
        assertEquals(today, Streak.advance(today, day - 1, bar))
        // The very first day starts it at one.
        assertEquals(StreakState(1, day), Streak.advance(StreakState(0, 0L), day, bar))
    }

    @Test
    fun `what is left of today's bar counts the day's work`() {
        val start = StreakState(days = 3, lastActiveDay = day - 1)
        assertEquals(15, Streak.leftOn(start, day, emptyMap(), ExerciseType.SQUAT))
        assertEquals(7, Streak.leftOn(start, day, mapOf(ExerciseType.SQUAT to 8), ExerciseType.SQUAT))
        assertEquals(24, Streak.leftOn(start, day, mapOf(ExerciseType.PLANK to 36), ExerciseType.PLANK))
        // Five pushups are half the day: half of fifteen squats, rounded up, is what is left.
        assertEquals(8, Streak.leftOn(start, day, mapOf(ExerciseType.PUSHUP to 5), ExerciseType.SQUAT))
        assertEquals(0, Streak.leftOn(start, day, mapOf(ExerciseType.PUSHUP to 10), ExerciseType.SQUAT))
        // A day already kept has nothing left, whatever its work says.
        val kept = Streak.advance(start, day, mapOf(ExerciseType.PUSHUP to 10))
        assertEquals(0, Streak.leftOn(kept, day, mapOf(ExerciseType.SQUAT to 8), ExerciseType.SQUAT))
        // Whatever is quoted is exactly enough, and one fewer is not.
        for (done in ExerciseType.entries) for (next in ExerciseType.entries) {
            val work = mapOf(done to Exercises.of(done).streakBar / 3)
            val left = Streak.leftOn(start, day, work, next)
            assertEquals(StreakState(4, day), Streak.advance(start, day, Streak.sum(work, mapOf(next to left))), "$done then $next")
            assertEquals(start, Streak.advance(start, day, Streak.sum(work, mapOf(next to left - 1))), "$done then $next")
        }
    }

    @Test
    fun `a streak left for a whole day reads as broken, at what it goes on from`() {
        val streak = StreakState(10, day - 1)
        // Yesterday's streak is alive all of today.
        assertTrue(!Streak.broken(streak, day))
        assertEquals(10, Streak.shown(streak, day))
        // A whole day missed has broken it; it shows the half that the next day adds to.
        val missed = StreakState(10, day - 2)
        assertTrue(Streak.broken(missed, day))
        assertEquals(5, Streak.shown(missed, day))
        assertEquals(Streak.shown(missed, day) + 1, Streak.advance(missed, day, mapOf(ExerciseType.PUSHUP to 10)).days)
        // Never started is not broken.
        assertTrue(!Streak.broken(StreakState(0, 0L), day))
    }
}
