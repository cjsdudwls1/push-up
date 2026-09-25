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

    /** The largest held `h` seen before any rep completed — this user's actual rest position. */
    private var restAnchor = Float.NEGATIVE_INFINITY

    /** The last [REST_HELD_MS] of tracked `h`, for telling a held position from a stray frame. */
    private val restWindowT = ArrayDeque<Long>()
    private val restWindowH = ArrayDeque<Float>()

    /** How many times [observeRest] has moved the window this session, for diagnostics. */
    var reanchors: Int = 0
        private set

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
     * Moves the range to sit where this user actually rests, before any rep has completed.
     *
     * Without this the detector has a loop that closes on itself. Arming needs `depth <= topEnter`;
     * `depth` comes from a population prior; and the prior is only ever corrected by [onRepExtremes],
     * which only a completed rep reaches. A user whose rest position sits further from the prior
     * than `topEnter` therefore never arms, never completes a rep, and is never learned from — zero
     * reps forever, with the tracker reporting OK and no event to diagnose it by.
     *
     * `h` at rest is a body proportion — for a pushup, arm length over shoulder width — and it spans
     * roughly 1.0 to 1.8 across real builds against a prior of 1.35 and a tolerance of 0.13. Measured
     * before this existed: builds at 1.0, 1.1, 1.2, 1.6, 1.7 and 1.8 all counted nothing from ten
     * honest pushups. Only 1.3 to 1.5 worked.
     *
     * The window is **scaled, not shifted**, and that distinction is the whole of the fix. `h` is a
     * ratio of two body measurements, so a longer-limbed user reads proportionally higher at every
     * depth rather than offset by a constant: their lockout and their bottom both move, by the same
     * factor. Shifting instead of scaling was tried first and made it worse — it put the bottom of a
     * long-limbed user's range somewhere their elbow angle flatly disagreed with, and the rep was
     * thrown out as INCONSISTENT rather than counted.
     *
     * Scaling preserves the shape of the range, so the count line stays the same fraction of this
     * user's own travel. That is what buys arming without touching the anti-farming property that
     * half reps never count, which is asserted separately.
     *
     * Anchored to the largest `h` seen, because `h` is maximal at rest by construction. It only ever
     * grows, so a user who opens the app already at the bottom converges upward within a rep instead
     * of being pinned there. Once a rep completes, [onRepExtremes] owns the range and this stops.
     *
     * Largest **held** `h`: the median of a stretch of at least [REST_HELD_MS] that stays within
     * [REST_BAND_OF_RMIN] of the movement's minimum range. Taking the largest single value let one
     * stray frame — a landmark misplaced for a thirtieth of a second — set the top of the range
     * somewhere the body never went, and since the anchor only grows, nothing brought it back.
     * People pause at the top before they start; a glitch does not.
     */
    fun observeRest(h: Float, tMs: Long) {
        if (completedReps > 0 || h.isNaN()) return
        // A gap breaks the stretch: stillness has to be seen, not assumed across frames not seen.
        if (restWindowT.isNotEmpty() && tMs - restWindowT.last() > REST_MAX_GAP_MS) {
            restWindowT.clear()
            restWindowH.clear()
        }
        restWindowT.addLast(tMs)
        restWindowH.addLast(h)
        while (restWindowT.size > 1 && tMs - restWindowT.first() > REST_HELD_MS) {
            restWindowT.removeFirst()
            restWindowH.removeFirst()
        }
        if (tMs - restWindowT.first() < REST_HELD_MS * 3 / 4) return
        val held = restWindowH.maxOrNull()!! - restWindowH.minOrNull()!! <= REST_BAND_OF_RMIN * config.rMin
        if (!held) return
        val rest = restWindowH.sorted()[restWindowH.size / 2]

        if (rest <= restAnchor) return
        restAnchor = rest
        anchorTopAt(rest)
    }

    /**
     * Puts the top of the range at [hTop], scaling the bottom with it. Shared by [observeRest] and
     * [reanchorTop]; see the former for why scaling rather than shifting.
     */
    private fun anchorTopAt(hTop: Float) {
        val h = hTop

        // Only when the rest position genuinely maps somewhere other than the top of the gauge.
        // mapRaw rather than map, because the clamped version cannot see the opposite failure: a
        // longer-limbed user reads *below* zero, arms perfectly well, and then never reaches the
        // count line because their whole travel is compressed into the top of someone else's range.
        if (abs(mapRaw(h)) <= config.topEnter) return

        if (config.anchorByShift) {
            // The range is the body's; where it sits is the camera's. Keep one, move the other.
            val span = top - bottom
            top = h
            bottom = h - span
        } else {
            val shape = if (abs(top) > Geometry.EPSILON) bottom / top else 0f
            top = h
            bottom = h * shape
        }
        bottomBest = bottom
        applyGuards()
        bottomBest = min(bottomBest, bottom)
        reanchors++
    }

    /**
     * Moves the top of the range to where this user actually turns around at the top, after reps
     * have started. The way out of a range whose top the body never reaches: arming needs the top
     * band, and without arming no rep completes to correct the range. See the arming watchdog in
     * [RepDetectorImpl], which decides when this is called and guards it against half reps.
     */
    fun reanchorTop(hTop: Float) {
        if (hTop.isNaN()) return
        val topBefore = top
        anchorTopAt(hTop)
        // Guard B measures fatigue from the bottom this user demonstrated; a range that has just
        // moved wholesale has not demonstrated anything yet.
        if (top != topBefore) bottomBest = bottom
    }

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

        /** How long a position must be held to count as rest. */
        const val REST_HELD_MS = 300L
        /** How still, as a fraction of the movement's minimum range. */
        const val REST_BAND_OF_RMIN = 0.30f
        /** A longer gap between tracked frames starts the stretch over. */
        const val REST_MAX_GAP_MS = 150L
    }
}
