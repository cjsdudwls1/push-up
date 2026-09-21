package com.pushuprpg.core.detect

/**
 * Every tunable in the detector, in one place.
 *
 * The UI reads [countEnter] and [deepEnter] to place the two marker lines on the 깊이 gauge, so the
 * gauge is the state machine rendered rather than a picture of it that can drift out of sync when
 * balance changes.
 */
data class DetectorConfig(
    val exercise: ExerciseType,

    // --- depth thresholds on the 0..100 scale, all hysteretic ---
    /** depth ≤ this re-arms the detector. */
    val topEnter: Float = 20f,
    /** depth > this starts a descent. */
    val topExit: Float = 32f,
    /** the 인정 line: the rep counts here. */
    val countEnter: Float = 70f,
    /** depth < this leaves the bottom. */
    val countExit: Float = 55f,
    /** the 깊게 line: bonus damage. */
    val deepEnter: Float = 88f,
    val deepExit: Float = 80f,

    // --- timing ---
    /**
     * Maximum average speed, in depth points per second, between leaving the top band and
     * crossing the count line. Anything faster than this is a bounce or a tracking glitch, not a
     * descent.
     *
     * This is a *speed* rather than a minimum elapsed time on purpose. A fixed time floor silently
     * depends on how far apart [topExit] and [countEnter] happen to be — and [countEnter] moves
     * during bootstrap — so relaxing the count line for a beginner would start rejecting their
     * perfectly normal reps as "too fast". A speed limit is independent of where the lines sit.
     *
     * 650 pt/s accepts a full descent down to roughly 225 ms, which is already at the edge of what
     * a body can do. Bouncing is caught by the 50 points of loop hysteresis and [minRepPeriodMs]
     * anyway; this is the backstop, not the main defence.
     */
    val maxDescentSpeed: Float = 650f,
    val minAscentMs: Int = 180,
    /** Hard cap of 100 reps/minute between strikes. */
    val minRepPeriodMs: Int = 600,
    val maxDescentMs: Int = 4000,
    val maxBottomMs: Int = 5000,
    val maxAscentMs: Int = 5000,
    val trackLostMs: Int = 500,
    val comboTimeoutMs: Int = 8000,

    // --- gating ---
    /** Depth must still be increasing at the crossing; frame-rate independent, unlike "N frames". */
    val minDescentVelocity: Float = 15f,
    val minCoreConfidence: Float = 0.50f,
    val minSideWeight: Float = 0.30f,
    val renderFadeLow: Float = 0.50f,
    val renderFadeHigh: Float = 0.75f,
    /** Primary and elbow depth must agree within this at the strike frame. */
    val maxSignalDisagreement: Float = 35f,

    // --- smoothing (applied to the raw ratio h, before the calibrated mapping) ---
    val signalMinCutoff: Float = 1.2f,
    val signalBeta: Float = 12f,
    val signalDCutoff: Float = 1.0f,
    val displayMinCutoff: Float = 1.0f,
    val displayBeta: Float = 3.0f,
    val displayDCutoff: Float = 1.0f,
    val scaleTauSec: Float = 2.0f,

    // --- calibration ---
    /**
     * How the range follows the rest position before any rep has completed.
     *
     * False scales: `h` is a body proportion (arm over shoulder width, say), so a longer-limbed user
     * reads proportionally higher at BOTH ends and the bottom moves by the same factor as the top.
     * True shifts: the top is near zero and set by where the camera is rather than by the body, so
     * the ratio means nothing and only the range is body-proportional. See [RangeCalibrator.observeRest].
     */
    val anchorByShift: Boolean = false,
    val hTopPrior: Float = 1.35f,
    val hBotPrior: Float = 0.70f,
    val rMin: Float = 0.35f,
    // Deliberately wide. These exist to stop a wild outlier capturing the calibration, not to
    // encode what a correct pushup looks like — the ratio shifts with how steeply the phone is
    // tilted, and a user whose living-room setup lands outside a tight clamp would find the accept
    // line permanently unreachable while every quality indicator said everything was fine. That
    // failure is silent and unrecoverable, which makes it far worse than a loose clamp.
    val topClampMin: Float = 0.70f,
    val topClampMax: Float = 2.40f,
    val botClampMin: Float = 0.10f,
    val botClampMax: Float = 1.80f,
    val alphaExpand: Float = 0.35f,
    val alphaTopContract: Float = 0.06f,
    val alphaBotContract: Float = 0.10f,
    /** A user may lose at most this fraction of their own demonstrated range to fatigue. */
    val fatigueDriftFraction: Float = 0.30f,
    /** Reps 1..this of a first-ever session use the relaxed bootstrap thresholds. */
    val bootstrapReps: Int = 5,
    val bootstrapCountEnter: Float = 62f,
    val bootstrapDeepEnter: Float = 92f,

    // --- anti-cheat ---
    val subjectJumpUnits: Float = 0.18f,
    val scaleJumpFraction: Float = 0.35f,
    val maxRepsPer10s: Int = 20,
    val minScale: Float = 0.08f,
    val maxScale: Float = 0.90f,
    /** Projected shoulder width below this fraction of its average means the torso has rolled. */
    val torsoRotatedFraction: Float = 0.80f,

    // --- coaching ---
    val hintMinIntervalMs: Int = 25_000,
    val hintMaxPerSession: Int = 3,
    val hintSuppressFirstReps: Int = 5,
    val hintRewardMultiplier: Float = 1.10f,
) {
    /**
     * What this config is measuring, as data.
     *
     * A getter rather than a constructor property on purpose: it keeps [DetectorConfig]'s identity,
     * `copy` and `equals` exactly what they were, and it means a config and its descriptor can
     * never disagree about which exercise they describe.
     */
    val descriptor: ExerciseDescriptor get() = Exercises.of(exercise)

    init {
        require(topEnter < topExit) { "topEnter must be below topExit" }
        require(topExit < countExit) { "topExit must be below countExit" }
        require(countExit < countEnter) { "countExit must be below countEnter (hysteresis)" }
        require(deepExit < deepEnter) { "deepExit must be below deepEnter (hysteresis)" }
        require(countEnter <= deepEnter) { "the 깊게 line cannot sit below the 인정 line" }
        require(rMin > 0f) { "rMin must be positive or the range can collapse" }
    }

    companion object {
        /**
         * The tuning for a movement.
         *
         * Every per-exercise value now lives beside its landmark pairs and cross-checks in
         * [Exercises], so a new movement is one value in one file rather than a new factory here, a
         * branch in [DepthSignal], a branch in [BodyFrameTracker] and a branch in [DetectorFactory].
         * The named helpers below are kept because half the codebase and every test calls them.
         */
        fun forExercise(type: ExerciseType): DetectorConfig = Exercises.of(type).config

        fun pushup(): DetectorConfig = Exercises.PUSHUP.config

        /**
         * A squat is the same state machine with a different signal and a slower cadence; the
         * thresholds move because the usable range of a squat (standing to parallel) is a smaller
         * fraction of the body than a pushup's.
         */
        fun squat(): DetectorConfig = Exercises.SQUAT.config

        /** A pull-up is a pushup upside down, paced far more slowly. */
        fun pullUp(): DetectorConfig = Exercises.PULL_UP.config

        fun plank(): DetectorConfig = Exercises.PLANK.config
    }
}

/**
 * Plank-specific tuning. A plank is scored continuously rather than counted, so none of the rep
 * thresholds apply.
 */
data class PlankConfig(
    val holdingScore: Float = 60f,
    val wobbleScore: Float = 40f,
    val breakGraceMs: Int = 1200,
    val recoveryWindowMs: Int = 3000,
    val recoveryChargeRetain: Float = 0.50f,
    val chargeSecondsAtPerfect: Float = 12f,
    /** > 1 so form beats endurance: a sloppy hold charges far slower than a clean one. */
    val chargeExponent: Float = 1.5f,
    val chargeDrainPerSec: Float = 25f,
    val dotTickHz: Float = 2f,
    val dotDamagePerTick: Float = 3.0f,
    val hipRefWindowMs: Int = 2000,
    val wAlignment: Float = 0.45f,
    val wHipHeight: Float = 0.30f,
    val wStability: Float = 0.15f,
    val wShoulderStack: Float = 0.10f,
)
