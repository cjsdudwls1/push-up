package com.pushuprpg.core

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.Gift
import com.pushuprpg.core.progression.Gifts
import com.pushuprpg.core.progression.RunGrowth
import com.pushuprpg.core.progression.SessionFacts
import com.pushuprpg.core.progression.WearSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GiftsTest {

    private var clock = 0L

    private fun run(
        day: Long,
        reps: Int = 10,
        bestSet: Int = reps,
        exercise: ExerciseType = ExerciseType.PUSHUP,
        full: Boolean = false,
    ) = SessionFacts(exercise, day, ++clock, reps, bestSet, deepReps = 0, durationMs = 60_000, fullSession = full)

    @Test
    fun `the first run brings the first gift`() {
        assertTrue(Gifts.earned(emptyList(), bestStreak = 0).isEmpty())
        assertEquals(setOf(Gift.FIRST_RUN), Gifts.earned(listOf(run(1, reps = 5)), bestStreak = 1))
    }

    @Test
    fun `a best set brings a gift at 10, 15, 20, 25, 30, 40, 50 and 70`() {
        fun at(best: Int) = Gifts.earned(listOf(run(1, reps = best)), 0)
        fun sets(best: Int) = at(best).filter { it.kind == Gift.Kind.SET }.map { it.count }.toSet()
        assertFalse(Gift.SET_10 in at(9))
        assertTrue(Gift.SET_10 in at(10))
        assertEquals(setOf(10, 15, 20, 25), sets(29))
        assertEquals(setOf(10, 15, 20, 25, 30, 40, 50), sets(69))
        assertTrue(Gift.SET_70 in at(70))
    }

    @Test
    fun `a hold's seconds are not a set`() {
        val plank = listOf(run(1, reps = 0, bestSet = 60, exercise = ExerciseType.PLANK))
        assertTrue(Gifts.earned(plank, 0).none { it.name.startsWith("SET_") })
        assertTrue(Gift.FIRST_RUN in Gifts.earned(plank, 0))
    }

    @Test
    fun `a streak's gifts come from the longest one, so a break never takes them back`() {
        val facts = listOf(run(1))
        assertTrue(Gift.STREAK_7 in Gifts.earned(facts, bestStreak = 7))
        assertFalse(Gift.STREAK_30 in Gifts.earned(facts, bestStreak = 29))
    }

    @Test
    fun `coming back after three days away is a gift, and two days is not`() {
        assertEquals(0, Gifts.comebacks(listOf(run(1), run(2), run(4))))
        assertEquals(1, Gifts.comebacks(listOf(run(1), run(2), run(5))))
        assertTrue(Gift.COMEBACK in Gifts.earned(listOf(run(1), run(5)), 1))
        // Two runs on the same day are one day.
        assertEquals(0, Gifts.comebacks(listOf(run(3), run(3))))
    }

    @Test
    fun `a session played to its last life brings the scarf`() {
        assertFalse(Gift.FULL_SESSION in Gifts.earned(listOf(run(1)), 1))
        assertTrue(Gift.FULL_SESSION in Gifts.earned(listOf(run(1, full = true)), 1))
    }

    @Test
    fun `runs with something done in them bring gifts at five, twenty-five and a hundred`() {
        val four = (1L..4L).map { run(it) }
        assertFalse(Gift.RUNS_5 in Gifts.earned(four, 1))
        assertTrue(Gift.RUNS_5 in Gifts.earned(four + run(5), 1))
        // Played with no rep while the ceiling came down: banked, but nothing was done in it.
        assertFalse(Gift.RUNS_5 in Gifts.earned(four + run(5, reps = 0, bestSet = 0), 1))
        // A hold counts by the seconds held.
        assertTrue(Gift.RUNS_5 in Gifts.earned(four + run(5, reps = 0, bestSet = 30, exercise = ExerciseType.PLANK), 1))
        val many = (1L..100L).map { run(it) }
        assertTrue(Gift.RUNS_25 in Gifts.earned(many.take(25), 1))
        assertFalse(Gift.RUNS_100 in Gifts.earned(many.dropLast(1), 1))
        assertTrue(Gift.RUNS_100 in Gifts.earned(many, 1))
    }

    @Test
    fun `three different movements bring the whistle`() {
        val two = listOf(run(1), run(2, exercise = ExerciseType.SQUAT), run(3, exercise = ExerciseType.SQUAT))
        assertFalse(Gift.MOVEMENTS_3 in Gifts.earned(two, 1))
        // Opened and left is not tried.
        assertFalse(Gift.MOVEMENTS_3 in Gifts.earned(two + run(4, reps = 0, bestSet = 0, exercise = ExerciseType.LUNGE), 1))
        assertTrue(Gift.MOVEMENTS_3 in Gifts.earned(two + run(4, exercise = ExerciseType.LUNGE), 1))
    }

    @Test
    fun `three records broken bring the glasses`() {
        // 10 is a first go; 11, 12 and 13 each beat the best before them.
        val three = listOf(run(1, bestSet = 10), run(2, bestSet = 11), run(3, bestSet = 12), run(4, bestSet = 13))
        assertTrue(Gift.RECORDS_3 in Gifts.earned(three, 1))
        assertFalse(Gift.RECORDS_3 in Gifts.earned(three.dropLast(1), 1))
    }

    @Test
    fun `the calorie gifts wait for their foods`() {
        // 1,108 pushups is 300 kcal: a bowl of rice, and not yet a chicken.
        val facts = listOf(run(1, reps = 1_108, bestSet = 30))
        assertTrue(Gift.BURN_RICE in Gifts.earned(facts, 1))
        assertFalse(Gift.BURN_CHICKEN in Gifts.earned(facts, 1))
        assertFalse(Gift.BURN_RICE in Gifts.earned(listOf(run(1, reps = 1_100, bestSet = 30)), 1))
    }

    @Test
    fun `a gift once earned is never lost as more is done`() {
        var facts = emptyList<SessionFacts>()
        var had = emptySet<Gift>()
        for (day in 1L..60L) {
            if (day % 5 == 0L) continue
            facts = facts + run(day, reps = (day % 7).toInt() + 1)
            val now = Gifts.earned(facts, bestStreak = 4)
            assertTrue(now.containsAll(had), "day $day took back ${had - now}")
            had = now
        }
    }

    @Test
    fun `a run reports only the gifts it brought`() {
        val before = listOf(run(1, reps = 12))
        val growth = RunGrowth.of(before, listOf(run(2, reps = 21)), bestStreakBefore = 1, bestStreakAfter = 2)
        assertEquals(listOf(Gift.SET_15, Gift.SET_20), growth.gifts)
        val streak = RunGrowth.of(before, listOf(run(2, reps = 5)), bestStreakBefore = 2, bestStreakAfter = 3)
        assertEquals(listOf(Gift.STREAK_3), streak.gifts)
    }

    @Test
    fun `a new find goes on only where nothing is worn`() {
        val wearing = setOf(CatItem.RIBBON)
        val after = Gifts.wearNew(wearing, listOf(CatItem.BEANIE, CatItem.BELL, CatItem.CITY))
        assertEquals(setOf(CatItem.RIBBON, CatItem.BELL, CatItem.CITY), after)
    }

    @Test
    fun `one thing per slot`() {
        val wearing = setOf(CatItem.RIBBON, CatItem.BELL)
        assertEquals(setOf(CatItem.CROWN, CatItem.BELL), Gifts.wear(wearing, CatItem.CROWN))
        assertEquals(setOf(CatItem.BELL), Gifts.takeOff(wearing, WearSlot.HEAD))
        // Every slot has something to put in it.
        assertEquals(WearSlot.entries.toSet(), CatItem.entries.map { it.slot }.toSet())
        // And every item is some gift's or on the shop's shelf, never both.
        val sold = com.pushuprpg.core.progression.ShopItem.entries.map { it.item }.toSet()
        assertEquals(CatItem.entries.toSet(), Gift.entries.map { it.item }.toSet() + sold)
    }

    @Test
    fun `every gift brings a thing of its own, and there are twice as many as there were`() {
        assertEquals(Gift.entries.size, Gift.entries.map { it.item }.distinct().size)
        // Fifteen gifts and eight on the shelf, until the owner asked for at least twice as many.
        assertTrue(Gift.entries.size >= 30, "${Gift.entries.size} gifts")
        assertTrue(com.pushuprpg.core.progression.ShopItem.entries.size >= 16, "${com.pushuprpg.core.progression.ShopItem.entries.size} on the shelf")
    }

    @Test
    fun `every gift says what it waits for`() {
        for (gift in Gift.entries) {
            when (gift.kind) {
                Gift.Kind.SET, Gift.Kind.STREAK, Gift.Kind.RECORDS, Gift.Kind.RUNS, Gift.Kind.MOVEMENTS ->
                    assertTrue(gift.count > 0, "$gift")
                Gift.Kind.BURN -> assertTrue(gift.food != null, "$gift")
                else -> Unit
            }
        }
    }
}
