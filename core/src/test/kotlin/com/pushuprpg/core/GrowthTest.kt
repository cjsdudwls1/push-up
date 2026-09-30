package com.pushuprpg.core

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.progression.Calories
import com.pushuprpg.core.progression.DayTotal
import com.pushuprpg.core.progression.Food
import com.pushuprpg.core.progression.Records
import com.pushuprpg.core.progression.RunGrowth
import com.pushuprpg.core.progression.SessionFacts
import com.pushuprpg.core.progression.Welcome
import com.pushuprpg.core.progression.Weeks
import com.pushuprpg.core.progression.recap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GrowthTest {

    private fun run(
        exercise: ExerciseType = ExerciseType.PUSHUP,
        day: Long = 20_717,
        at: Long = day * 86_400_000L,
        reps: Int = 0,
        bestSet: Int = reps,
        deep: Int = 0,
        durationMs: Long = 60_000,
    ) = SessionFacts(exercise, day, at, reps, bestSet, deep, durationMs)

    @Test
    fun `a rep's calories are its MET, the reference weight and the time it takes`() {
        // 7.5 MET × 65 kg × 2 s: 0.27 kcal a pushup.
        assertEquals(0.2708f, Calories.perUnit(ExerciseType.PUSHUP), 0.0001f)
        // A pull-up is the same effort for longer; a second of plank is light work.
        assertEquals(0.4063f, Calories.perUnit(ExerciseType.PULL_UP), 0.0001f)
        assertEquals(0.2934f, Calories.perUnit(ExerciseType.SQUAT), 0.0001f)
        assertEquals(0.0506f, Calories.perUnit(ExerciseType.PLANK), 0.0001f)
        // Every movement has a figure.
        ExerciseType.entries.forEach { assertTrue(Calories.perUnit(it) > 0f, "$it") }
    }

    @Test
    fun `the first session already burns something off`() {
        // The tutorial is a handful of pushups; the first food is within them.
        val p = Calories.progress(Calories.of(mapOf(ExerciseType.PUSHUP to 7)))
        assertEquals(Food.BLUEBERRY, p.reached)
        assertEquals(Food.CHERRY_TOMATO, p.next)
    }

    @Test
    fun `nothing done is nothing burned, with the first food ahead`() {
        val p = Calories.progress(0f)
        assertNull(p.reached)
        assertEquals(Food.BLUEBERRY, p.next)
        assertEquals(0f, p.fraction)
    }

    @Test
    fun `every movement burns, a hold by the second`() {
        val mixed = Calories.of(
            mapOf(ExerciseType.PUSHUP to 100, ExerciseType.SQUAT to 50, ExerciseType.PLANK to 60)
        )
        // 27.1 kcal of pushups, 14.7 of squats and 3.0 for a minute of plank.
        assertEquals(44.79f, mixed, 0.01f)
    }

    @Test
    fun `the foods come in order and never run out`() {
        val kcal = Food.entries.map { it.kcal }
        assertEquals(kcal.sorted(), kcal)
        assertEquals(kcal.distinct(), kcal)
        val beyond = Calories.progress(1_000_000f)
        assertNull(beyond.next)
        assertEquals(1f, beyond.fraction)
        assertEquals(Food.RICE_BALE, beyond.reached)
    }

    @Test
    fun `the count to the next food is whole reps, rounded up`() {
        val p = Calories.progress(Calories.of(mapOf(ExerciseType.PUSHUP to 7)))
        // 1.9 kcal of 3: 1.1 kcal, 5 pushups.
        assertEquals(5, Calories.toNext(p, ExerciseType.PUSHUP))
        assertTrue(Calories.toNext(p, ExerciseType.PULL_UP) < Calories.toNext(p, ExerciseType.PUSHUP))
    }

    @Test
    fun `burning a food off is said once, on the run that did it`() {
        assertEquals(listOf(Food.BLUEBERRY), Calories.passed(0f, 1.5f))
        assertEquals(emptyList(), Calories.passed(1.5f, 2f))
        assertEquals(listOf(Food.CHERRY_TOMATO, Food.CANDY), Calories.passed(2f, 25f))
    }

    @Test
    fun `a movement's records are its best go, its first and its total`() {
        val facts = listOf(
            run(day = 1, at = 1, reps = 12, bestSet = 8),
            run(day = 2, at = 2, reps = 30, bestSet = 14),
            run(day = 2, at = 3, reps = 9, bestSet = 9),
            run(ExerciseType.SQUAT, day = 3, at = 4, reps = 20, bestSet = 20),
        )
        val r = Records.of(facts).getValue(ExerciseType.PUSHUP)
        assertEquals(14, r.best)
        assertEquals(8, r.first)
        assertEquals(6, r.gain)
        assertEquals(51, r.total)
        assertEquals(2, r.days)
        assertEquals(20, Records.of(facts).getValue(ExerciseType.SQUAT).best)
    }

    @Test
    fun `a hold's records are in seconds`() {
        val facts = listOf(
            run(ExerciseType.PLANK, at = 1, durationMs = 30_400),
            run(ExerciseType.PLANK, at = 2, durationMs = 75_900),
        )
        val r = Records.of(facts).getValue(ExerciseType.PLANK)
        assertEquals(75, r.best)
        assertEquals(30, r.first)
        assertEquals(105, r.total)
    }

    @Test
    fun `the first go is the one that counted something`() {
        // A run the camera counted nothing in is not where the user started.
        val facts = listOf(run(at = 1, reps = 0, bestSet = 0), run(at = 2, reps = 10, bestSet = 10))
        assertEquals(10, Records.of(facts).getValue(ExerciseType.PUSHUP).first)
    }

    @Test
    fun `a first go is not a new record, and neither is a tie`() {
        assertFalse(Records.isNew(previousBest = null, now = 12))
        assertFalse(Records.isNew(previousBest = 0, now = 12))
        assertFalse(Records.isNew(previousBest = 12, now = 12))
        assertTrue(Records.isNew(previousBest = 12, now = 13))
    }

    @Test
    fun `a run reports the records it broke and the foods it burned off`() {
        val before = listOf(run(at = 1, reps = 40, bestSet = 15))
        val beat = RunGrowth.of(before, listOf(run(at = 2, reps = 40, bestSet = 18)))
        assertEquals(15, beat.records.single().previous)
        assertEquals(18, beat.records.single().now)
        // 10.8 kcal, then 21.7: past a candy.
        assertEquals(listOf(Food.CANDY), beat.passed)
        assertEquals(10.83f, beat.burned, 0.01f)

        val tie = RunGrowth.of(before, listOf(run(at = 2, reps = 15, bestSet = 15)))
        assertTrue(tie.records.isEmpty())
        // The first run of a movement sets its record rather than breaking one.
        val first = RunGrowth.of(before, listOf(run(ExerciseType.SQUAT, at = 2, reps = 20, bestSet = 20)))
        assertTrue(first.records.isEmpty())
    }

    @Test
    fun `the welcome goes by the days since the last workout`() {
        assertEquals(Welcome.First, Welcome.of(lastWorkoutDay = null, today = 100))
        assertEquals(Welcome.Today, Welcome.of(100, 100))
        assertEquals(Welcome.Yesterday, Welcome.of(99, 100))
        assertEquals(Welcome.Back(3), Welcome.of(97, 100))
        // A clock moved back is today, not a negative absence.
        assertEquals(Welcome.Today, Welcome.of(101, 100))
    }

    @Test
    fun `a week runs Monday to Sunday`() {
        val sunday = 20_723L // 2026-09-27
        val monday = 20_717L // 2026-09-21
        assertEquals(monday, Weeks.mondayOf(sunday))
        assertEquals(monday, Weeks.mondayOf(monday))
        assertEquals(20_724L, Weeks.mondayOf(20_724L)) // 2026-09-28, the next Monday
        assertEquals(-3L, Weeks.mondayOf(0L)) // 1970-01-01 was a Thursday
    }

    @Test
    fun `a week's recap is its days, its calories and the records broken in it`() {
        val monday = 20_710L // 2026-09-14
        val facts = listOf(
            run(day = monday - 3, at = 1, reps = 20, bestSet = 10), // the week before: a first go
            run(day = monday, at = 2, reps = 30, bestSet = 12), // a record, beating 10
            run(day = monday + 2, at = 3, reps = 10, bestSet = 11), // not one
            run(day = monday + 4, at = 4, reps = 40, bestSet = 15), // a record again
            run(ExerciseType.SQUAT, day = monday + 4, at = 5, reps = 20, bestSet = 20), // a first go
            run(day = monday + 8, at = 6, reps = 50, bestSet = 30), // the next week
        )
        val recap = Weeks.recap(facts, monday)
        assertEquals(listOf(true, false, true, false, true, false, false), recap.summary.days)
        assertEquals(100, recap.summary.reps)
        assertEquals(2, recap.records)
        // 80 pushups and 20 squats.
        assertEquals(27.54f, recap.kcal, 0.01f)
        assertFalse(recap.empty)
        assertTrue(Weeks.recap(facts, monday - 14).empty)
    }

    @Test
    fun `this week and last, lit by the days that had work`() {
        val today = 20_719L // Wednesday 2026-09-23
        val totals = listOf(
            DayTotal(20_710, reps = 40, activeMs = 0), // last Monday
            DayTotal(20_716, reps = 10, activeMs = 0), // last Sunday
            DayTotal(20_717, reps = 25, activeMs = 0), // this Monday
            DayTotal(20_719, reps = 0, activeMs = 90_000), // today: a plank, no reps
        )
        val (thisWeek, lastWeek) = Weeks.thisAndLast(totals, today)
        assertEquals(listOf(true, false, true, false, false, false, false), thisWeek.days)
        assertEquals(2, thisWeek.activeDays)
        assertEquals(25, thisWeek.reps)
        assertEquals(2, lastWeek.activeDays)
        assertEquals(50, lastWeek.reps)
    }
}
