package com.pushuprpg.core

import com.pushuprpg.core.detect.CrossCheckPolicy
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The invariants that, when broken, make an exercise count exactly nothing and say nothing.
 *
 * This has happened twice in this project. A squat's nose cross-check could not move, so it
 * rejected every rep; a sign was inverted, so descending read as rising. Neither produced an error
 * — both produced a gauge that sat still while the user did the work. These are cheap assertions
 * against a whole class of silent failure.
 */
class ExerciseDirectionTest {

    private val reps = Exercises.ALL.filter { it.kind == MovementKind.REP }

    @Test
    fun `every exercise type has a descriptor`() {
        val covered = Exercises.ALL.map { it.type }.toSet()
        assertEquals(ExerciseType.entries.toSet(), covered,
            "an ExerciseType with no descriptor resolves to nothing at runtime")
    }

    @Test
    fun `a rep exercise measures two different points`() {
        reps.forEach { d ->
            val s = assertNotNull(d.signal, "${d.type} is a REP with no signal")
            assertTrue(s.proximal != s.distal,
                "${d.type} measures a point against itself; h can never move")
        }
    }

    @Test
    fun `the depth bands are ordered and hysteretic`() {
        reps.forEach { d ->
            val c = d.config
            assertTrue(c.topEnter < c.topExit, "${d.type}: top band has no hysteresis")
            assertTrue(c.countExit < c.countEnter, "${d.type}: count band has no hysteresis")
            assertTrue(c.deepExit < c.deepEnter, "${d.type}: deep band has no hysteresis")
            assertTrue(c.countEnter <= c.deepEnter, "${d.type}: 깊게 sits below 인정")
        }
    }

    /**
     * The calibrator's range is `top - bottom` and it divides by it. A prior pair the wrong way
     * round gives a negative range, and every mapped depth comes out inverted — the user descends
     * and the gauge falls.
     */
    @Test
    fun `h falls as effort rises, for every exercise`() {
        reps.forEach { d ->
            val c = d.config
            assertTrue(c.hTopPrior > c.hBotPrior,
                "${d.type}: hTopPrior ${c.hTopPrior} is not above hBotPrior ${c.hBotPrior}, " +
                    "so the signal rises with effort and nothing will ever count")
            assertTrue(c.hTopPrior - c.hBotPrior >= c.rMin,
                "${d.type}: the prior range is already narrower than rMin")
        }
    }

    /** A prior outside its own clamps is a range the calibrator immediately contracts away from. */
    @Test
    fun `the priors sit inside their clamps`() {
        reps.forEach { d ->
            val c = d.config
            assertTrue(c.hTopPrior in c.topClampMin..c.topClampMax,
                "${d.type}: hTopPrior ${c.hTopPrior} outside [${c.topClampMin}, ${c.topClampMax}]")
            assertTrue(c.hBotPrior in c.botClampMin..c.botClampMax,
                "${d.type}: hBotPrior ${c.hBotPrior} outside [${c.botClampMin}, ${c.botClampMax}]")
        }
    }

    /**
     * An exercise with neither cross-check is a signal nobody is watching — which is how a pushup
     * gets faked by waving the wrists.
     */
    @Test
    fun `every rep exercise keeps at least one cross-check`() {
        reps.forEach { d ->
            val s = assertNotNull(d.signal)
            assertTrue(s.jointCheck != null || s.bodyTravel != null,
                "${d.type} has no independent witness at all")
            if (s.bodyTravel == null) {
                assertEquals(CrossCheckPolicy.JOINT_REQUIRED, s.crossCheck,
                    "${d.type} has only a joint check, so it must be mandatory rather than optional")
            }
        }
    }

    /** A travel check whose two points cannot move relative to each other is the squat's dead nose. */
    @Test
    fun `a travel check watches something the primary signal does not`() {
        reps.forEach { d ->
            val travel = d.signal?.bodyTravel ?: return@forEach
            assertTrue(travel.from != travel.to,
                "${d.type}: the travel check measures a point against itself")
        }
    }
}
