package com.pushuprpg.core.detect

import com.pushuprpg.core.math.Geometry
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.math.abs
import kotlin.math.pow

/**
 * A plank is a hold, not a rep, so it is scored continuously rather than counted.
 *
 * The score is a weighted mean over the components that are actually **available this frame**, with
 * the weights renormalised across them. That matters more here than anywhere else in the detector:
 * with a phone on the floor the hips are often visible and the ankles almost never are, so a fixed
 * weighting would either need a default value for a missing component — which is inventing data —
 * or would permanently cap the achievable score for most users in most rooms. Renormalising means a
 * user whose legs are out of frame is judged fairly on what can be seen.
 *
 * The hold pays out two ways at once. Damage ticks give continuous feedback so the effort visibly
 * does something, and a charge meter builds toward a payoff so a long hold has a shape. Charge
 * grows with score raised above one, so good form beats mere endurance.
 */
class PlankDetector(
    override val config: DetectorConfig = DetectorConfig.plank(),
    private val plank: PlankConfig = PlankConfig(),
    override var skeletonMode: SkeletonMode = SkeletonMode.MINIMAL,
) : RepDetector {

    private val confidenceEstimator = ConfidenceEstimator()
    private val bodyTracker = BodyFrameTracker(config)
    private val skeletonBuilder = SkeletonBuilder(config)
    private val confidence = FloatArray(Lm.COUNT)

    private var quality: PoseQuality? = null
    private var holding = false
    private var holdStartMs = 0L
    private var brokenSinceMs = Long.MIN_VALUE
    private var lastFrameMs = 0L
    private var firstFrameMs = Long.MIN_VALUE
    private var lastTickMs = Long.MIN_VALUE
    private var charge = 0f
    private var chargeIndex = 0
    private var score = 0f

    private var totalHoldMs = 0L
    private var longestUnbrokenMs = 0L
    private var qualitySum = 0f
    private var qualitySamples = 0

    /** Recent shoulder positions, for the stability term. */
    private val recentShoulderU = ArrayDeque<Float>()
    private val recentShoulderV = ArrayDeque<Float>()

    /** Hip height is judged against where the user started, not against an absolute. */
    private var hipReference = Float.NaN
    private var hipReferenceSamples = 0

    override fun onFrame(frame: PoseFrame): PoseTick {
        val events = mutableListOf<RepEvent>()
        val tMs = frame.timestampMs
        if (firstFrameMs == Long.MIN_VALUE) firstFrameMs = tMs
        val dtMs = if (lastFrameMs == 0L) 0L else (tMs - lastFrameMs).coerceIn(0L, MAX_STEP_MS)
        lastFrameMs = tMs

        confidenceEstimator.compute(frame, bodyTracker.scale, confidence)
        val body = bodyTracker.update(frame, confidence)

        val newQuality = when {
            !frame.hasPose -> PoseQuality.NO_SUBJECT
            body == null -> PoseQuality.LOW_CONFIDENCE
            else -> PoseQuality.OK
        }
        if (newQuality != quality) {
            quality = newQuality
            events += RepEvent.QualityChanged(tMs, newQuality)
        }

        if (newQuality == PoseQuality.OK && body != null) {
            score = scoreFrame(frame, body)
            qualitySum += score
            qualitySamples++
            advance(tMs, dtMs, events)
        } else if (holding) {
            breakHold(tMs, events)
        }

        return PoseTick(
            tMs = tMs,
            depth = score,
            depthVelocity = 0f,
            depthSource = if (newQuality == PoseQuality.OK) DepthSource.PRIMARY else DepthSource.NONE,
            phase = if (holding) RepPhase.BOTTOM else RepPhase.IDLE,
            quality = newQuality,
            repCount = (totalHoldMs / 1000).toInt(),
            combo = 0,
            maxCombo = 0,
            calibration = CalibrationSnapshot(
                exercise = ExerciseType.PLANK,
                top = 0f, bottom = 0f, bottomBest = 0f,
                state = CalibrationState.CONVERGED,
                completedReps = 0,
            ),
            render = skeletonBuilder.build(frame, confidence, skeletonMode, holding),
            events = events,
        )
    }

    private fun advance(tMs: Long, dtMs: Long, events: MutableList<RepEvent>) {
        val goodEnough = score >= plank.holdingScore

        if (!holding) {
            if (goodEnough) {
                holding = true
                holdStartMs = tMs
                lastTickMs = tMs
                // Resuming inside the recovery window keeps most of the charge: a plank that
                // wobbles and is saved should feel like a save, not like starting over.
                if (brokenSinceMs != Long.MIN_VALUE && tMs - brokenSinceMs <= plank.recoveryWindowMs) {
                    charge *= plank.recoveryChargeRetain
                } else {
                    charge = 0f
                }
                brokenSinceMs = Long.MIN_VALUE
            }
            return
        }

        if (!goodEnough) {
            // A brief wobble is not a broken plank. Without this grace the meter would flicker off
            // on every shiver, which is both wrong and demoralising.
            if (brokenSinceMs == Long.MIN_VALUE) brokenSinceMs = tMs
            if (tMs - brokenSinceMs >= plank.breakGraceMs) breakHold(tMs, events)
            return
        }
        brokenSinceMs = Long.MIN_VALUE

        totalHoldMs += dtMs
        val unbroken = tMs - holdStartMs
        longestUnbrokenMs = maxOf(longestUnbrokenMs, unbroken)

        val normalised = (score / 100f).coerceIn(0f, 1f)
        charge = (charge + 100f * normalised.pow(plank.chargeExponent) *
            (dtMs / 1000f) / plank.chargeSecondsAtPerfect).coerceAtMost(100f)

        val tickInterval = (1000f / plank.dotTickHz).toLong()
        if (lastTickMs == Long.MIN_VALUE || tMs - lastTickMs >= tickInterval) {
            lastTickMs = tMs
            events += RepEvent.HoldTick(
                tMs = tMs,
                score = score,
                holdMs = unbroken,
                charge = charge,
                damage = plank.dotDamagePerTick * normalised,
            )
        }

        if (charge >= 100f) {
            chargeIndex++
            charge = 0f
            events += RepEvent.ChargeFull(tMs, chargeIndex)
        }
    }

    private fun breakHold(tMs: Long, events: MutableList<RepEvent>) {
        val held = tMs - holdStartMs
        holding = false
        brokenSinceMs = tMs
        events += RepEvent.HoldBroken(
            tMs = tMs,
            holdMs = held,
            qualityAvg = if (qualitySamples == 0) 0f else qualitySum / qualitySamples,
            longestUnbrokenMs = longestUnbrokenMs,
        )
    }

    /**
     * Weighted mean over available components, renormalised.
     *
     * A component that cannot be measured is dropped from both the numerator and the denominator.
     * Substituting a neutral value would quietly reward a user for a body part the camera cannot
     * see, and substituting zero would punish them for the same thing.
     */
    private fun scoreFrame(frame: PoseFrame, body: BodyFrameState): Float {
        var weighted = 0f
        var weight = 0f

        alignment(frame, body)?.let { weighted += it * plank.wAlignment; weight += plank.wAlignment }
        hipHeight(frame, body)?.let { weighted += it * plank.wHipHeight; weight += plank.wHipHeight }
        stability(body)?.let { weighted += it * plank.wStability; weight += plank.wStability }
        shoulderStack(frame, body)?.let { weighted += it * plank.wShoulderStack; weight += plank.wShoulderStack }

        if (weight <= 0f) return 0f
        return (100f * weighted / weight).coerceIn(0f, 100f)
    }

    /** How straight the shoulder-hip-knee line is. The core of what a plank is. */
    private fun alignment(frame: PoseFrame, body: BodyFrameState): Float? {
        val shoulderConf = minOf(confidence[Lm.LEFT_SHOULDER], confidence[Lm.RIGHT_SHOULDER])
        val hipConf = minOf(confidence[Lm.LEFT_HIP], confidence[Lm.RIGHT_HIP])
        val kneeConf = minOf(confidence[Lm.LEFT_KNEE], confidence[Lm.RIGHT_KNEE])
        if (shoulderConf < config.minCoreConfidence || hipConf < config.minCoreConfidence) return null
        if (kneeConf < config.minCoreConfidence) return null

        val shoulder = frame.midpoint2(Lm.LEFT_SHOULDER, Lm.RIGHT_SHOULDER)
        val hip = frame.midpoint2(Lm.LEFT_HIP, Lm.RIGHT_HIP)
        val knee = frame.midpoint2(Lm.LEFT_KNEE, Lm.RIGHT_KNEE)

        val angle = Geometry.angleDeg(
            shoulder.first, shoulder.second,
            hip.first, hip.second,
            knee.first, knee.second,
        )
        if (angle.isNaN()) return null
        // 180 degrees is a straight line; every degree away from it is sag or pike.
        return (1f - abs(180f - angle) / ALIGNMENT_TOLERANCE_DEG).coerceIn(0f, 1f)
    }

    /**
     * How far the hips have drifted from where the user set them.
     *
     * Measured against their own starting position rather than an absolute, because what counts as
     * a level hip line depends entirely on how the phone is propped.
     */
    private fun hipHeight(frame: PoseFrame, body: BodyFrameState): Float? {
        val hipConf = minOf(confidence[Lm.LEFT_HIP], confidence[Lm.RIGHT_HIP])
        if (hipConf < config.minCoreConfidence || body.scale <= 0f) return null

        val hip = frame.midpoint2(Lm.LEFT_HIP, Lm.RIGHT_HIP)
        val offset = Geometry.dot(
            hip.first - body.shoulderU, hip.second - body.shoulderV,
            body.nU, body.nV,
        ) / body.scale

        if (hipReferenceSamples < HIP_REFERENCE_SAMPLES) {
            hipReference = if (hipReference.isNaN()) offset else (hipReference + offset) / 2f
            hipReferenceSamples++
            return 1f
        }
        return (1f - abs(offset - hipReference) / HIP_DRIFT_TOLERANCE).coerceIn(0f, 1f)
    }

    /** Whether the body is still. A plank that is wandering is not being held. */
    private fun stability(body: BodyFrameState): Float? {
        recentShoulderU.addLast(body.shoulderU)
        recentShoulderV.addLast(body.shoulderV)
        while (recentShoulderU.size > STABILITY_WINDOW) {
            recentShoulderU.removeFirst()
            recentShoulderV.removeFirst()
        }
        if (recentShoulderU.size < STABILITY_WINDOW / 2) return null
        if (body.scale <= 0f) return null

        val meanU = recentShoulderU.average().toFloat()
        val meanV = recentShoulderV.average().toFloat()
        var spread = 0f
        for (i in recentShoulderU.indices) {
            spread = maxOf(spread, Geometry.norm(recentShoulderU[i] - meanU, recentShoulderV[i] - meanV))
        }
        return (1f - (spread / body.scale) / STABILITY_TOLERANCE).coerceIn(0f, 1f)
    }

    /** Wrists stacked under the shoulders — the difference between a plank and a hover. */
    private fun shoulderStack(frame: PoseFrame, body: BodyFrameState): Float? {
        val wristConf = maxOf(confidence[Lm.LEFT_WRIST], confidence[Lm.RIGHT_WRIST])
        if (wristConf < config.minSideWeight || body.scale <= 0f) return null

        val wrist = frame.midpoint2(Lm.LEFT_WRIST, Lm.RIGHT_WRIST)
        // Displacement along the shoulder axis, i.e. sideways rather than toward the floor.
        val lateral = abs(
            Geometry.dot(
                wrist.first - body.shoulderU, wrist.second - body.shoulderV,
                body.axisU, body.axisV,
            )
        ) / body.scale
        return (1f - lateral / STACK_TOLERANCE).coerceIn(0f, 1f)
    }

    override fun reset() {
        confidenceEstimator.reset()
        bodyTracker.reset()
        quality = null
        holding = false
        holdStartMs = 0L
        brokenSinceMs = Long.MIN_VALUE
        lastFrameMs = 0L
        firstFrameMs = Long.MIN_VALUE
        lastTickMs = Long.MIN_VALUE
        charge = 0f
        chargeIndex = 0
        score = 0f
        totalHoldMs = 0L
        longestUnbrokenMs = 0L
        qualitySum = 0f
        qualitySamples = 0
        recentShoulderU.clear()
        recentShoulderV.clear()
        hipReference = Float.NaN
        hipReferenceSamples = 0
    }

    override fun snapshotCalibration(): CalibrationSnapshot = CalibrationSnapshot(
        ExerciseType.PLANK, 0f, 0f, 0f, CalibrationState.CONVERGED, 0
    )

    override fun restoreCalibration(snapshot: CalibrationSnapshot) = Unit

    override fun updatedProfile(previous: UserProfile): UserProfile = previous

    override fun sessionSummary(): SessionSummary = SessionSummary(
        exercise = ExerciseType.PLANK,
        // A plank's "reps" are seconds held, so time under tension lands in the same stats as reps.
        repCount = (totalHoldMs / 1000).toInt(),
        maxCombo = 0,
        shallowCount = 0,
        durationMs = if (firstFrameMs == Long.MIN_VALUE) 0L else lastFrameMs - firstFrameMs,
        meanDepth = if (qualitySamples == 0) 0f else qualitySum / qualitySamples,
        plausibility = 1f,
        records = emptyList(),
        holdMs = totalHoldMs,
        longestUnbrokenMs = longestUnbrokenMs,
        qualityAvg = if (qualitySamples == 0) 0f else qualitySum / qualitySamples,
    )

    companion object {
        /** Degrees away from straight at which the alignment term reaches zero. */
        const val ALIGNMENT_TOLERANCE_DEG = 35f

        /** Hip drift, in shoulder widths, at which that term reaches zero. */
        const val HIP_DRIFT_TOLERANCE = 0.45f

        const val STABILITY_WINDOW = 12
        const val STABILITY_TOLERANCE = 0.28f
        const val STACK_TOLERANCE = 0.90f

        const val HIP_REFERENCE_SAMPLES = 15
        const val MAX_STEP_MS = 250L
    }
}

/** Midpoint of two landmarks in isotropic units. */
private fun PoseFrame.midpoint2(a: Int, b: Int): Pair<Float, Float> =
    (u(a) + u(b)) / 2f to (v(a) + v(b)) / 2f
