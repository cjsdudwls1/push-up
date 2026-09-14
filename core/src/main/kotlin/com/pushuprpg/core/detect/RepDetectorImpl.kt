package com.pushuprpg.core.detect

import com.pushuprpg.core.filter.OneEuroFilter
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.math.abs

/**
 * The rep state machine, shared by pushups and squats — they differ only in [DetectorConfig].
 *
 * ## Why the rep counts at the bottom
 *
 * The strike fires the moment depth crosses the 인정 line, not when the user returns to the top.
 * The hit has to land when the effort is *felt*; firing at lockout would put the damage number
 * several hundred milliseconds after the exertion, and the game would read as laggy and
 * disconnected from the body — the exact failure that makes a fitness game feel like a fitness app.
 *
 * The usual objection is that this lets someone pump the bottom and farm reps. It does not, for
 * three independent reasons:
 *
 *  1. A strike is only reachable from [RepPhase.READY_TOP], so a second rep requires passing
 *     through a genuine lockout. The top-return requirement is structural, not a validation check
 *     that could be worked around.
 *  2. [DetectorConfig.minRepPeriodMs] caps the rate regardless.
 *  3. Only *completed* reps feed calibration, so a user who never returns to the top never moves
 *     their own bar.
 *
 * The one case this permits is descending once and giving up at the bottom, which awards exactly
 * one rep for one genuinely performed descent. That is correct behaviour, not a cheat.
 */
class RepDetectorImpl(
    override val config: DetectorConfig,
    profile: UserProfile = UserProfile.empty(),
    override var skeletonMode: SkeletonMode = SkeletonMode.MINIMAL,
) : RepDetector {

    private val confidenceEstimator = ConfidenceEstimator()
    private val bodyTracker = BodyFrameTracker(config)
    private val skeletonBuilder = SkeletonBuilder(config)
    private var calibrator = RangeCalibrator(config, profile)
    private val seedProfile = profile

    private val signalFilter = OneEuroFilter(
        minCutoff = config.signalMinCutoff.toDouble(),
        beta = config.signalBeta.toDouble(),
        dCutoff = config.signalDCutoff.toDouble(),
    )

    private val confidence = FloatArray(Lm.COUNT)

    // --- state machine ---
    private var phase: RepPhase = RepPhase.IDLE
    /** Null until the first frame, so the very first quality is always reported as a change. */
    private var quality: PoseQuality? = null
    private var depth = 0f
    private var depthVelocity = 0f
    private var depthSource = DepthSource.NONE

    private var tTopExit = 0L
    private var tBottom = 0L
    private var tCountExit = 0L
    private var tLastStrike = Long.MIN_VALUE
    private var tQualityOkSince = Long.MIN_VALUE
    private var tLastGoodPose = Long.MIN_VALUE
    private var tFirstFrame = Long.MIN_VALUE
    private var tLastFrame = 0L

    private var hTopThisRep = Float.NEGATIVE_INFINITY
    private var hBotThisRep = Float.POSITIVE_INFINITY
    private var maxDepthThisRep = 0f
    private var deepFiredThisRep = false
    private var struckThisRep = false
    private var asymmetryThisRep = 0f
    private var confidenceSum = 0f
    private var confidenceSamples = 0

    private var repCount = 0
    private var combo = 0
    private var maxCombo = 0
    private var shallowCount = 0
    private var shallowConsecutive = 0
    private var depthSum = 0f

    private val records = ArrayList<RepRecord>()
    private val strikeTimes = ArrayDeque<Long>()
    private var pendingFlags = mutableSetOf<PoseQuality>()

    override fun onFrame(frame: PoseFrame): PoseTick {
        val events = mutableListOf<RepEvent>()
        val tMs = frame.timestampMs
        if (tFirstFrame == Long.MIN_VALUE) tFirstFrame = tMs
        tLastFrame = tMs

        confidenceEstimator.compute(frame, bodyTracker.scale, confidence)
        val body = bodyTracker.update(frame, confidence)
        val sample = if (body == null) null else DepthSignal.compute(frame, body, confidence, config)

        // Handled before the quality gate: a scale reset also *reports* as SUBJECT_SWITCH, so
        // doing this inside the OK branch below would mean it never ran at all.
        if (body != null && body.scaleReset) {
            calibrator.revalidate()
            signalFilter.reset()
            abandonRep(tMs, AbandonReason.QUALITY_LOST, events)
        }

        val newQuality = evaluateQuality(frame, body, sample, tMs)
        if (newQuality != quality) {
            quality = newQuality
            events += RepEvent.QualityChanged(tMs, newQuality)
        }

        if (newQuality == PoseQuality.OK && body != null && sample != null) {
            tLastGoodPose = tMs
            if (tQualityOkSince == Long.MIN_VALUE) tQualityOkSince = tMs

            // The slew cap is reasoned about in depth points per second, then expressed in the
            // units this filter actually sees, so recalibration cannot silently change how hard
            // it clamps.
            signalFilter.maxSlewPerSecond = (MAX_DEPTH_SLEW * calibrator.range / 100f).toDouble()

            val h = signalFilter.filter(sample.h.toDouble(), tMs).toFloat()
            if (signalFilter.hadDiscontinuity) abandonRep(tMs, AbandonReason.QUALITY_LOST, events)

            depth = calibrator.map(h)
            depthVelocity = -100f * signalFilter.velocity.toFloat() / calibrator.range
            depthSource = sample.source

            confidenceSum += minOf(confidence[Lm.LEFT_SHOULDER], confidence[Lm.RIGHT_SHOULDER])
            confidenceSamples++
            depthSum += depth

            advance(tMs, h, sample, events)
        } else {
            // Never punish the user for a tracking failure: the gauge freezes rather than falling,
            // and the caller pauses the boss while quality is not OK.
            depthVelocity = 0f
            tQualityOkSince = Long.MIN_VALUE
            if (phase != RepPhase.IDLE && phase != RepPhase.LOST &&
                tLastGoodPose != Long.MIN_VALUE && tMs - tLastGoodPose >= config.trackLostMs
            ) {
                abandonRep(tMs, AbandonReason.QUALITY_LOST, events)
                phase = RepPhase.LOST
            }
        }

        if (combo > 0 && tLastStrike != Long.MIN_VALUE && tMs - tLastStrike > config.comboTimeoutMs) {
            events += RepEvent.ComboBroken(tMs, combo)
            combo = 0
        }

        val render = skeletonBuilder.build(
            frame, confidence, skeletonMode,
            highlight = phase == RepPhase.BOTTOM && maxDepthThisRep >= calibrator.deepEnter(),
        )

        return PoseTick(
            tMs = tMs,
            depth = depth,
            depthVelocity = depthVelocity,
            depthSource = depthSource,
            phase = phase,
            quality = quality ?: PoseQuality.NO_SUBJECT,
            repCount = repCount,
            combo = combo,
            maxCombo = maxCombo,
            calibration = calibrator.snapshot(),
            render = render,
            events = events,
        )
    }

    private fun advance(tMs: Long, h: Float, sample: DepthSample, events: MutableList<RepEvent>) {
        when (phase) {
            RepPhase.IDLE, RepPhase.LOST -> {
                val settled = tQualityOkSince != Long.MIN_VALUE && tMs - tQualityOkSince >= ARM_SETTLE_MS
                if (settled && depth <= config.topEnter) {
                    arm(h)
                }
            }

            RepPhase.READY_TOP -> {
                hTopThisRep = maxOf(hTopThisRep, h)
                if (!sample.bodyDrop.isNaN()) bodyDropAtTop = sample.bodyDrop
                if (depth > config.topExit) {
                    phase = RepPhase.DESCENDING
                    tTopExit = tMs
                    maxDepthThisRep = depth
                    hBotThisRep = h
                    asymmetryThisRep = sample.asymmetry
                    deepFiredThisRep = false
                    struckThisRep = false
                }
            }

            RepPhase.DESCENDING -> {
                maxDepthThisRep = maxOf(maxDepthThisRep, depth)
                hBotThisRep = minOf(hBotThisRep, h)
                asymmetryThisRep = maxOf(asymmetryThisRep, sample.asymmetry)

                when {
                    depth >= calibrator.countEnter() -> {
                        val reason = strikeBlockedReason(tMs, sample)
                        phase = RepPhase.BOTTOM
                        tBottom = tMs
                        if (reason == null) {
                            strike(tMs, events)
                        } else {
                            events += RepEvent.Abandoned(tMs, reason)
                        }
                    }

                    depth <= config.topEnter -> {
                        // Came back up without reaching the line.
                        if (maxDepthThisRep >= config.topExit) {
                            shallowCount++
                            shallowConsecutive++
                            events += RepEvent.Shallow(tMs, maxDepthThisRep, shallowConsecutive)
                        }
                        arm(h)
                    }

                    tMs - tTopExit > config.maxDescentMs -> {
                        events += RepEvent.Abandoned(tMs, AbandonReason.HOVERED)
                        phase = RepPhase.ASCENDING
                        tCountExit = tMs
                    }
                }
            }

            RepPhase.BOTTOM -> {
                maxDepthThisRep = maxOf(maxDepthThisRep, depth)
                hBotThisRep = minOf(hBotThisRep, h)

                if (!deepFiredThisRep && struckThisRep && depth >= calibrator.deepEnter()) {
                    deepFiredThisRep = true
                    events += RepEvent.DeepUpgrade(tMs, repCount, depth)
                }

                when {
                    depth < config.countExit -> {
                        phase = RepPhase.ASCENDING
                        tCountExit = tMs
                    }

                    tMs - tBottom > config.maxBottomMs -> {
                        events += RepEvent.Abandoned(tMs, AbandonReason.STALLED_BOTTOM)
                        phase = RepPhase.LOST
                    }
                }
            }

            RepPhase.ASCENDING -> {
                when {
                    depth <= config.topEnter && tMs - tCountExit >= config.minAscentMs -> {
                        complete(tMs, events)
                        arm(h)
                    }

                    // Dipped back down without re-arming. No second strike is possible from here:
                    // the arming lock and the minimum rep period both block it.
                    depth >= calibrator.countEnter() -> {
                        phase = RepPhase.BOTTOM
                        tBottom = tMs
                    }

                    tMs - tCountExit > config.maxAscentMs -> {
                        // The count stands — the descent was real — but it earns no combo and does
                        // not teach the calibrator anything.
                        events += RepEvent.Abandoned(tMs, AbandonReason.SLOW_ASCENT)
                        struckThisRep = false
                        if (depth <= config.topEnter) arm(h)
                    }
                }
            }
        }
    }

    /** Returns null when the strike is allowed, or the reason it is not. */
    private fun strikeBlockedReason(tMs: Long, sample: DepthSample): AbandonReason? {
        val descentMs = tMs - tTopExit
        if (descentMs <= 0L) return AbandonReason.TOO_FAST
        val bandSpeed = (calibrator.countEnter() - config.topExit) * 1000f / descentMs
        if (bandSpeed > config.maxDescentSpeed) return AbandonReason.TOO_FAST
        if (tLastStrike != Long.MIN_VALUE && tMs - tLastStrike < config.minRepPeriodMs) {
            return AbandonReason.TOO_FAST
        }
        if (depthVelocity <= config.minDescentVelocity) return AbandonReason.TOO_FAST

        // Two-signal agreement: the thing that stops someone waving an arm at the phone. The
        // primary signal can be fooled by moving the wrists alone; the elbow angle and the nose
        // cannot be, because they describe the rest of the body.
        if (!sample.jointDepth.isNaN()) {
            if (abs(depth - sample.jointDepth) > config.maxSignalDisagreement) {
                return AbandonReason.INCONSISTENT
            }
        } else if (!sample.bodyDrop.isNaN() && bodyDropAtTop != Float.NEGATIVE_INFINITY) {
            // No world landmarks, so watch a part of the body the primary signal does not: the head
            // for a pushup, the shoulders for a squat. Either way it has to have genuinely
            // travelled since this rep armed, which is what waving at the phone does not do.
            val descended = sample.bodyDrop - bodyDropAtTop
            if (descended < MIN_BODY_DROP_FRACTION * calibrator.range) {
                return AbandonReason.INCONSISTENT
            }
        }

        pruneStrikeWindow(tMs)
        if (strikeTimes.size >= config.maxRepsPer10s) return AbandonReason.TOO_FAST

        return null
    }

    private fun strike(tMs: Long, events: MutableList<RepEvent>) {
        repCount++
        struckThisRep = true
        tLastStrike = tMs
        shallowConsecutive = 0
        strikeTimes.addLast(tMs)
        pruneStrikeWindow(tMs)

        // Combo increments here rather than at lockout so the combo the player sees always matches
        // the rep count they see; both surface at the same instant.
        combo++
        maxCombo = maxOf(maxCombo, combo)

        val grade = if (maxDepthThisRep >= calibrator.deepEnter()) RepGrade.DEEP else RepGrade.COUNTED
        events += RepEvent.Strike(tMs, repCount, grade, depth, combo)
        if (grade == RepGrade.DEEP) {
            deepFiredThisRep = true
            events += RepEvent.DeepUpgrade(tMs, repCount, depth)
        }
    }

    private fun complete(tMs: Long, events: MutableList<RepEvent>) {
        if (!struckThisRep) return

        val grade = if (maxDepthThisRep >= calibrator.deepEnter()) RepGrade.DEEP else RepGrade.COUNTED
        val record = RepRecord(
            repIndex = repCount,
            grade = grade,
            maxDepth = maxDepthThisRep,
            descentMs = (tBottom - tTopExit).toInt().coerceAtLeast(0),
            bottomMs = (tCountExit - tBottom).toInt().coerceAtLeast(0),
            ascentMs = (tMs - tCountExit).toInt().coerceAtLeast(0),
            asymmetry = asymmetryThisRep * 100f / calibrator.range,
            meanConfidence = if (confidenceSamples > 0) confidenceSum / confidenceSamples else 0f,
            depthSource = depthSource,
            qualityFlags = pendingFlags.toSet(),
        )
        records += record
        events += RepEvent.Completed(tMs, repCount, record)

        // Only a completed rep teaches the calibrator. A user who never returns to the top can
        // never move their own bar downward.
        if (hTopThisRep != Float.NEGATIVE_INFINITY && hBotThisRep != Float.POSITIVE_INFINITY) {
            calibrator.onRepExtremes(hTopThisRep, hBotThisRep)
        }
        pendingFlags = mutableSetOf()
        struckThisRep = false
    }

    private var bodyDropAtTop = Float.NEGATIVE_INFINITY

    private fun arm(h: Float) {
        phase = RepPhase.READY_TOP
        hTopThisRep = h
        hBotThisRep = Float.POSITIVE_INFINITY
        maxDepthThisRep = 0f
        deepFiredThisRep = false
        asymmetryThisRep = 0f
    }

    private fun abandonRep(tMs: Long, reason: AbandonReason, events: MutableList<RepEvent>) {
        if (phase == RepPhase.DESCENDING || phase == RepPhase.BOTTOM || phase == RepPhase.ASCENDING) {
            events += RepEvent.Abandoned(tMs, reason)
        }
        struckThisRep = false
        maxDepthThisRep = 0f
        hTopThisRep = Float.NEGATIVE_INFINITY
        hBotThisRep = Float.POSITIVE_INFINITY
    }

    private fun pruneStrikeWindow(tMs: Long) {
        while (strikeTimes.isNotEmpty() && tMs - strikeTimes.first() > RATE_WINDOW_MS) {
            strikeTimes.removeFirst()
        }
    }

    private fun evaluateQuality(
        frame: PoseFrame,
        body: BodyFrameState?,
        sample: DepthSample?,
        tMs: Long,
    ): PoseQuality {
        if (!frame.hasPose) return PoseQuality.NO_SUBJECT
        if (body == null) return PoseQuality.LOW_CONFIDENCE
        if (sample == null) return PoseQuality.LOW_CONFIDENCE
        if (body.torsoRotated) {
            pendingFlags += PoseQuality.TORSO_ROTATED
            return PoseQuality.TORSO_ROTATED
        }
        if (body.scaleReset) return PoseQuality.SUBJECT_SWITCH

        pruneStrikeWindow(tMs)
        if (strikeTimes.size >= config.maxRepsPer10s) {
            pendingFlags += PoseQuality.IMPLAUSIBLE_RATE
            return PoseQuality.IMPLAUSIBLE_RATE
        }

        val core = minOf(confidence[Lm.LEFT_SHOULDER], confidence[Lm.RIGHT_SHOULDER])
        if (core < config.minCoreConfidence) return PoseQuality.LOW_CONFIDENCE

        val arms = maxOf(
            confidence[Lm.LEFT_SHOULDER] * confidence[Lm.LEFT_WRIST],
            confidence[Lm.RIGHT_SHOULDER] * confidence[Lm.RIGHT_WRIST],
        )
        if (arms < config.minSideWeight && sample.source != DepthSource.ELBOW_FALLBACK) {
            return PoseQuality.OUT_OF_FRAME
        }

        return PoseQuality.OK
    }

    override fun reset() {
        confidenceEstimator.reset()
        bodyTracker.reset()
        signalFilter.reset()
        calibrator = RangeCalibrator(config, seedProfile)
        phase = RepPhase.IDLE
        quality = null
        depth = 0f
        depthVelocity = 0f
        depthSource = DepthSource.NONE
        tLastStrike = Long.MIN_VALUE
        tQualityOkSince = Long.MIN_VALUE
        tLastGoodPose = Long.MIN_VALUE
        tFirstFrame = Long.MIN_VALUE
        bodyDropAtTop = Float.NEGATIVE_INFINITY
        repCount = 0
        combo = 0
        maxCombo = 0
        shallowCount = 0
        shallowConsecutive = 0
        depthSum = 0f
        confidenceSum = 0f
        confidenceSamples = 0
        records.clear()
        strikeTimes.clear()
        pendingFlags = mutableSetOf()
    }

    override fun snapshotCalibration(): CalibrationSnapshot = calibrator.snapshot()

    override fun restoreCalibration(snapshot: CalibrationSnapshot) = calibrator.restore(snapshot)

    override fun updatedProfile(previous: UserProfile): UserProfile = calibrator.toProfile(previous)

    override fun sessionSummary(): SessionSummary {
        val flagged = records.count { it.qualityFlags.isNotEmpty() }
        return SessionSummary(
            exercise = config.exercise,
            repCount = repCount,
            maxCombo = maxCombo,
            shallowCount = shallowCount,
            durationMs = if (tFirstFrame == Long.MIN_VALUE) 0L else tLastFrame - tFirstFrame,
            meanDepth = if (records.isEmpty()) 0f else records.map { it.maxDepth }.average().toFloat(),
            plausibility = if (repCount == 0) 1f else 1f - flagged.toFloat() / repCount,
            records = records.toList(),
        )
    }

    companion object {
        /** Quality must hold for this long before the detector will arm. */
        const val ARM_SETTLE_MS = 300L

        const val RATE_WINDOW_MS = 10_000L

        /**
         * Depth points per second the signal is allowed to move; see OneEuroFilter's slew limit.
         *
         * Set above [DetectorConfig.maxDescentSpeed] on purpose. That limit is on the *average*
         * speed across the descent band, whereas a real rep eases in and out, so its instantaneous
         * peak is roughly half again as fast. Clamping at the average would shave the middle out
         * of every brisk rep.
         */
        const val MAX_DEPTH_SLEW = 1000f

        /** How much of the calibrated range the cross-checked body part must have travelled. */
        const val MIN_BODY_DROP_FRACTION = 0.30f
    }
}

object DetectorFactory {
    fun create(
        type: ExerciseType,
        config: DetectorConfig? = null,
        profile: UserProfile = UserProfile.empty(),
    ): RepDetector = when (type) {
        ExerciseType.PUSHUP -> RepDetectorImpl(config ?: DetectorConfig.pushup(), profile)
        ExerciseType.SQUAT -> RepDetectorImpl(config ?: DetectorConfig.squat(), profile)
        // A plank is a hold rather than a rep, so it needs its own detector entirely — the rep
        // state machine has nothing to say about a position that is simply maintained.
        ExerciseType.PLANK -> PlankDetector(config ?: DetectorConfig.plank())
    }
}
