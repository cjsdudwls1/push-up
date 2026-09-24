package com.pushuprpg.core.game

import com.pushuprpg.core.detect.ExerciseType

/** Why a rep was worth half: what to tell the user so the next one is whole. */
enum class StyleMiss {
    /** 기사: came down faster than a controlled lowering. */
    TOO_QUICK,

    /** 기사: counted, but never reached the 깊게 line. */
    NOT_FULL,

    /** 궁수: lagged behind the last rep inside a set. */
    LAGGING,
}

/**
 * What each class counts as a rep done its way — a whole rep off the monster — and what it counts
 * as half.
 *
 * The thresholds were read off the projected body ([com.pushuprpg.core.fixtures.Body3d] in the
 * tests), not guessed: see `ClassStyleRigTest`.
 */
object ClassStyle {

    /**
     * A 기사's lowering, from leaving the top band to crossing the 깊게 line, must take this long.
     *
     * On the rig that stretch is 35-47% of the whole lowering for every movement, and it holds from
     * the first rep while the lines are still calibrating: 550 ms is a lowering of about 1.5 s. A
     * deliberate two-second lowering clears it with room; an ordinary one-second rep reads 330-470
     * ms and does not. Measured to the deep line rather than the count line because the count line
     * sits in the fast middle of the rep and moves during calibration.
     */
    const val SLOW_LOWERING_MS = 550

    /**
     * A 궁수's rep is whole when it follows the last one within this long, strike to strike.
     *
     * Generous on purpose, and well clear of what the detector can count: on the rig a pushup
     * still counts at 0.8 s a rep, a squat, lunge or pull-up at 1.0 s and a dip at 1.2 s, at 12-30
     * fps (`FastRepRigTest`). Anything tighter would be asking the camera, not the body.
     */
    fun briskCycleMs(exercise: ExerciseType): Int = when (exercise) {
        ExerciseType.PUSHUP -> 1_500
        ExerciseType.SQUAT -> 1_600
        ExerciseType.LUNGE -> 1_800
        ExerciseType.DIP -> 1_800
        ExerciseType.PULL_UP -> 2_200
        ExerciseType.PLANK -> Int.MAX_VALUE
    }

    /**
     * What a 궁수's rep is worth at the strike, in half-reps. [startsSet] is the first rep after a
     * rest long enough to break the chain: always whole, so resting between sets costs nothing.
     */
    fun archerHalves(exercise: ExerciseType, cycleMs: Int, startsSet: Boolean): Int =
        if (startsSet || cycleMs <= briskCycleMs(exercise)) 2 else 1

    /** Whether a 기사's lowering, top band to deep line, was slow enough to make the rep whole. */
    fun knightSlowEnough(loweringMs: Int): Boolean = loweringMs >= SLOW_LOWERING_MS
}
