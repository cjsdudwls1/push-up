package com.pushuprpg.core.detect

import com.pushuprpg.core.math.Geometry
import kotlin.math.abs
import kotlin.math.min

/**
 * Learns each user's personal top and bottom of the movement and maps the raw ratio `h` onto the
 * 0..100 depth scale the gauge and the state machine use.
 *
 * There is no universal "correct" depth. A tall user, a short user, someone doing knee pushups and
 * someone doing incline pushups all produce different ratios for the same honest effort, so the
 * detector calibrates instead of judging. That is also why the detector needs no special cases for
 * exercise variants: they simply shift [top] and [bottom].
 *
 * The hard part is stopping the range from collapsing onto whatever the user is doing right now,
 * because then every rep counts forever and the gauge becomes decoration. Three guards prevent it,
 * applied in order on every update — see [onRepExtremes].
 */
class RangeCalibrator(
    private val config: DetectorConfig,
    profile: UserProfile = UserProfile.empty(),
) {
    var top: Float = 0f
        private set
    var bottom: Float = 0f
        private set

    /** The deepest bottom actually demonstrated this session; the anchor for the fatigue cap. */
    var bottomBest: Float = 0f
        private set

    var state: CalibrationState = CalibrationState.BOOTSTRAP
        private set

    var completedReps: Int = 0
        private set

    private var consecutiveConsistent = 0
    private var sessionTop = Float.NEGATIVE_INFINITY
    private var sessionBot = Float.POSITIVE_INFINITY
    private val startedFromProfile: Boolean = !profile.isEmpty

    init {
        // Seeding from the stored profile is what makes the gauge trustworthy on rep 1 of session
        // 2 rather than rep 4. The prior is still blended in so one unusual session cannot capture
        // the profile permanently.
        top = if (profile.isEmpty) config.hTopPrior
        else 0.75f * profile.topEwma + 0.25f * config.hTopPrior
        bottom = if (profile.isEmpty) config.hBotPrior
        else 0.75f * profile.botEwma + 0.25f * config.hBotPrior
        // Seed bottomBest *before* the guards run: Guard B measures drift against it, and against
        // an unset zero it would clamp the starting range down to a third of itself.
        bottomBest = bottom
        applyGuards()
        bottomBest = min(bottomBest, bottom)
        state = if (profile.isEmpty) CalibrationState.BOOTSTRAP else CalibrationState.CONVERGED
    }

    /** 0 at lockout, 100 at the deepest calibrated position. */
    fun map(h: Float): Float = 100f * Geometry.inverseLerp(top, bottom, h)

    /**
     * Unclamped version, for diagnostics and for noticing that a user has gone beyond their known
     * range (which is what expands it).
     */
    fun mapRaw(h: Float): Float {
        val span = top - bottom
        if (abs(span) < Geometry.EPSILON) return 0f
        return 100f * (top - h) / span
    }

    /** Current range in `h` units; the slew limit is scaled by this. */
    val range: Float get() = (top - bottom).coerceAtLeast(config.rMin)

    /** The count line, relaxed while the very first session is still finding its feet. */
    fun countEnter(): Float =
        if (state == CalibrationState.BOOTSTRAP) config.bootstrapCountEnter else config.countEnter

    /**
     * The deep line. Note it moves the *other* way during bootstrap: accept a beginner's rep
     * generously, but award "깊은 타격" stingily. Wrongly rejecting a real rep is far more damaging
     * than under-awarding a deep one.
     */
    fun deepEnter(): Float =
        if (state == CalibrationState.BOOTSTRAP) config.bootstrapDeepEnter else config.deepEnter

    /**
     * Feeds one **completed** rep's extremes. Incomplete reps must never reach here: a user who
     * never returns to the top would otherwise move their own bar downward for free.
     */
    fun onRepExtremes(hTop: Float, hBottom: Float) {
        completedReps++
        sessionTop = maxOf(sessionTop, hTop)
        sessionBot = min(sessionBot, hBottom)

        // Expand fast, contract slowly. Going further than before is information; falling short is
        // usually fatigue, and should move the bar only gradually.
        val alphaTop = if (hTop > top) config.alphaExpand else config.alphaTopContract
        top += alphaTop * (hTop - top)

        val alphaBot = if (hBottom < bottom) config.alphaExpand else config.alphaBotContract
        bottom += alphaBot * (hBottom - bottom)

        bottomBest = min(bottomBest, bottom)
        applyGuards()

        if (state == CalibrationState.BOOTSTRAP || state == CalibrationState.REVALIDATING) {
            if (abs(hBottom - bottom) < CONVERGENCE_TOLERANCE) consecutiveConsistent++ else consecutiveConsistent = 0
            if (consecutiveConsistent >= 2 || completedReps >= config.bootstrapReps) {
                state = CalibrationState.CONVERGED
            }
        }
    }

    /**
     * Widens the learning rate for a couple of reps after something invalidated the range —
     * the user repositioned, the exercise changed, or tracking swapped to a different person.
     */
    fun revalidate() {
        state = CalibrationState.REVALIDATING
        consecutiveConsistent = 0
    }

    fun snapshot(): CalibrationSnapshot = CalibrationSnapshot(
        exercise = config.exercise,
        top = top,
        bottom = bottom,
        bottomBest = bottomBest,
        state = state,
        completedReps = completedReps,
    )

    fun restore(s: CalibrationSnapshot) {
        top = s.top
        bottom = s.bottom
        bottomBest = s.bottomBest
        state = s.state
        completedReps = s.completedReps
        applyGuards()
    }

    /** Folds this session into the persisted profile. Only worth doing with enough real reps. */
    fun toProfile(previous: UserProfile): UserProfile {
        if (completedReps < MIN_REPS_TO_LEARN ||
            sessionTop == Float.NEGATIVE_INFINITY ||
            sessionBot == Float.POSITIVE_INFINITY
        ) return previous

        return if (previous.isEmpty) {
            UserProfile(sessionTop, sessionBot, 1)
        } else {
            UserProfile(
                topEwma = 0.70f * previous.topEwma + 0.30f * sessionTop,
                botEwma = 0.70f * previous.botEwma + 0.30f * sessionBot,
                sessionCount = previous.sessionCount + 1,
            )
        }
    }

    private fun applyGuards() {
        // Guard A — minimum range, anchored at the TOP.
        // Not a symmetric spread: lockout is a mechanical hard stop and the most repeatable event
        // in the movement, so it is the trustworthy end to pin. The bottom is the variable one.
        if (top - bottom < config.rMin) bottom = top - config.rMin

        // Guard B — the anti-farming rule. Fatigue is real and the bottom is allowed to drift up,
        // but only so far: a user may lose at most a fixed fraction of the range they themselves
        // demonstrated. Past that, reps stop counting and the 인정 line shows as unreachable, which
        // is the honest answer rather than quietly lowering the bar to meet them.
        val rBest = top - bottomBest
        if (rBest > 0f) {
            bottom = min(bottom, bottomBest + config.fatigueDriftFraction * rBest)
        }

        // Guard C — absolute anthropometric clamps, then re-apply A in case C moved an end.
        top = top.coerceIn(config.topClampMin, config.topClampMax)
        bottom = bottom.coerceIn(config.botClampMin, config.botClampMax)
        if (top - bottom < config.rMin) bottom = top - config.rMin
        bottom = bottom.coerceIn(config.botClampMin, config.botClampMax)
    }

    companion object {
        const val CONVERGENCE_TOLERANCE = 0.10f
        const val MIN_REPS_TO_LEARN = 5
    }
}
