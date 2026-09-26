package com.pushuprpg.core.detect

import com.pushuprpg.core.filter.OneEuroFilter
import com.pushuprpg.core.math.Geometry
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
    /** The previous tracked frame's depth and time, for placing a line crossing between frames. */
    private var prevDepth = Float.NaN
    private var prevDepthMs = 0L
    private var depthSource = DepthSource.NONE

    private var tTopExit = 0L
    private var tBottom = 0L
    private var tCountExit = 0L
    /** Whether the signal has been in the top band since leaving the count line on this rep. */
    private var topReachedThisAscent = false
    private var tLastStrike = Long.MIN_VALUE
    private var tQualityOkSince = Long.MIN_VALUE
    /** Start of the current stretch of frames that are not OK; see [QUALITY_BLIP_MS]. */
    private var tQualityLostSince = Long.MIN_VALUE
    private var tLastGoodPose = Long.MIN_VALUE
    private var tFirstFrame = Long.MIN_VALUE
    private var tLastFrame = 0L
    private var tLastTracked = Long.MIN_VALUE

    private var hTopThisRep = Float.NEGATIVE_INFINITY
    private var hBotThisRep = Float.POSITIVE_INFINITY
    private var maxDepthThisRep = 0f
    /** The working joint's deepest reading on this rep, by its own 3-D angle; NaN when unseen. */
    private var maxJointThisRep = Float.NaN
    /** Where recent reps turned around short of the count line with the joint fully bent. */
    private val shallowBottoms = ArrayList<Float>()
    /** This rep reached the count line and was refused as [AbandonReason.TOO_FAST]. */
    private var refusedFastThisRep = false

    /** The working joint bent all the way on this rep, by a joint that can tell. */
    private fun jointConfirmedFull(): Boolean =
        config.descriptor.signal?.jointCheck?.confirmsFullDepth == true &&
            !maxJointThisRep.isNaN() && maxJointThisRep >= BOTTOM_WATCHDOG_JOINT_MIN
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
            if (body.viewChanged) {
                // Side on and from the head are two readings of the same body, not one: `h` in one
                // means nothing in the other. The range starts over from the stored profile and is
                // anchored at the rest this view shows, and the rep has to arm again in it.
                calibrator = RangeCalibrator(config, seedProfile)
                phase = RepPhase.IDLE
                tQualityOkSince = Long.MIN_VALUE
                resetWatchdog()
                shallowBottoms.clear()
            }
        }

        val newQuality = evaluateQuality(frame, body, sample, tMs)
        if (newQuality != quality) {
            quality = newQuality
            events += RepEvent.QualityChanged(tMs, newQuality)
        }

        if (newQuality == PoseQuality.OK && body != null && sample != null) {
            tLastGoodPose = tMs
            tQualityLostSince = Long.MIN_VALUE
            if (tQualityOkSince == Long.MIN_VALUE) tQualityOkSince = tMs

            // The slew cap is reasoned about in depth points per second, then expressed in the
            // units this filter actually sees, so recalibration cannot silently change how hard
            // it clamps.
            signalFilter.maxSlewPerSecond = (MAX_DEPTH_SLEW * calibrator.range / 100f).toDouble()

            val h = signalFilter.filter(sample.h.toDouble(), tMs).toFloat()
            if (signalFilter.hadDiscontinuity) abandonRep(tMs, AbandonReason.QUALITY_LOST, events)

            // Before mapping, and only until the first rep completes: put the range where this body
            // actually rests. A prior that is off by more than topEnter locks the user out of the
            // top band entirely, and because the calibrator only learns from completed reps, that
            // lockout can never resolve itself. Filtered h, not raw, so a single bad frame cannot
            // drag the anchor.
            calibrator.observeRest(h, tMs, exact = body.sideOn)

            prevDepth = if (tLastTracked == Long.MIN_VALUE) Float.NaN else depth
            prevDepthMs = tLastTracked
            tLastTracked = tMs
            depth = calibrator.map(h)
            depthVelocity = -100f * signalFilter.velocity.toFloat() / calibrator.range
            depthSource = sample.source

            confidenceSum += minOf(confidence[Lm.LEFT_SHOULDER], confidence[Lm.RIGHT_SHOULDER])
            confidenceSamples++
            depthSum += depth

            advance(tMs, h, sample, body, events)
            watchArming(h, sample)
        } else {
            // Never punish the user for a tracking failure: the gauge freezes rather than falling,
            // and the caller pauses the boss while quality is not OK.
            depthVelocity = 0f
            // A blip does not restart the wait to arm; losing the body does. Filmed from the head
            // the model swaps the shoulders for a frame or two, and at 14 fps those frames landed
            // inside the wait at the top often enough to cost the first rep of a set.
            if (tQualityLostSince == Long.MIN_VALUE) tQualityLostSince = tMs
            if (tMs - tQualityLostSince >= QUALITY_BLIP_MS) tQualityOkSince = Long.MIN_VALUE
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
            missing = config.descriptor.watchedLandmarks
                .filter { confidence[it] < config.minCoreConfidence }
                .sorted(),
        )
    }

    private fun advance(tMs: Long, h: Float, sample: DepthSample, body: BodyFrameState, events: MutableList<RepEvent>) {
        when (phase) {
            RepPhase.IDLE, RepPhase.LOST -> {
                val settled = tQualityOkSince != Long.MIN_VALUE && tMs - tQualityOkSince >= ARM_SETTLE_MS
                if (settled && depth <= config.topEnter) {
                    arm(h)
                } else if (settled && calibrator.completedReps > 0 && lockedOut(sample) && depth < config.countExit) {
                    // Back after a tracking gap, mid-set, arms straight: the top, whatever the
                    // range says. Not before the first rep — standing with the arms down is also
                    // straight arms, and the first rep's top is observeRest's to find.
                    if (depth > config.topEnter + JOINT_REANCHOR_MARGIN) calibrator.reanchorTop(h)
                    arm(h)
                }
            }

            RepPhase.READY_TOP -> {
                hTopThisRep = maxOf(hTopThisRep, h)
                // The witness's rest position: the LEAST it has travelled while the rep was armed,
                // not its value on the last frame before the descent. See [bodyDropAtTop].
                if (!sample.bodyDrop.isNaN()) bodyDropAtTop = minOf(bodyDropAtTop, sample.bodyDrop)
                // Side on, where the shoulders were in the picture at the top: the least deep armed frame.
                if (body.sideOn && !(depth > shouldersAtTopDepth)) {
                    shouldersAtTopDepth = depth
                    shouldersAtTopU = body.shoulderU
                    shouldersAtTopV = body.shoulderV
                }
                if (depth > config.topExit) {
                    phase = RepPhase.DESCENDING
                    tTopExit = crossedAt(config.topExit, tMs)
                    maxDepthThisRep = depth
                    hBotThisRep = h
                    maxJointThisRep = sample.jointDepth
                    refusedFastThisRep = false
                    asymmetryThisRep = sample.asymmetry
                    deepFiredThisRep = false
                    struckThisRep = false
                }
            }

            RepPhase.DESCENDING -> {
                maxDepthThisRep = maxOf(maxDepthThisRep, depth)
                hBotThisRep = minOf(hBotThisRep, h)
                asymmetryThisRep = maxOf(asymmetryThisRep, sample.asymmetry)
                if (!sample.jointDepth.isNaN() && !(sample.jointDepth <= maxJointThisRep)) maxJointThisRep = sample.jointDepth

                when {
                    depth >= calibrator.countEnter() -> {
                        val reason = strikeBlockedReason(tMs, sample, body)
                        phase = RepPhase.BOTTOM
                        tBottom = tMs
                        if (reason == null) {
                            strike(tMs, events)
                        } else {
                            events += RepEvent.Abandoned(tMs, reason)
                            refusedFastThisRep = reason == AbandonReason.TOO_FAST
                        }
                    }

                    depth <= config.topEnter -> {
                        // Came back up without reaching the line.
                        if (maxDepthThisRep >= config.topExit) {
                            shallowCount++
                            shallowConsecutive++
                            events += RepEvent.Shallow(tMs, maxDepthThisRep, shallowConsecutive)
                            watchBottom()
                        }
                        arm(h)
                    }

                    tMs - tTopExit > config.maxDescentMs -> {
                        events += RepEvent.Abandoned(tMs, AbandonReason.HOVERED)
                        phase = RepPhase.ASCENDING
                        tCountExit = tMs
                        topReachedThisAscent = false
                    }
                }
            }

            RepPhase.BOTTOM -> {
                maxDepthThisRep = maxOf(maxDepthThisRep, depth)
                hBotThisRep = minOf(hBotThisRep, h)
                if (!sample.jointDepth.isNaN() && !(sample.jointDepth <= maxJointThisRep)) maxJointThisRep = sample.jointDepth

                if (!deepFiredThisRep && struckThisRep && depth >= calibrator.deepEnter()) {
                    deepFiredThisRep = true
                    events += RepEvent.DeepUpgrade(tMs, repCount, depth, loweringMs(tMs))
                }

                when {
                    depth < config.countExit -> {
                        phase = RepPhase.ASCENDING
                        // Placed between frames like every other line: a brisk return crosses
                        // from the count line to the top band in two or three frames, and timed
                        // from the frame after the crossing it read too quick to be a rep.
                        tCountExit = crossedAt(config.countExit, tMs)
                        topReachedThisAscent = false
                    }

                    tMs - tBottom > config.maxBottomMs -> {
                        events += RepEvent.Abandoned(tMs, AbandonReason.STALLED_BOTTOM)
                        phase = RepPhase.LOST
                    }
                }
            }

            RepPhase.ASCENDING -> {
                if (depth <= config.topEnter) topReachedThisAscent = true
                when {
                    // Back in the top band — or, having reached it, still within the band's own
                    // hysteresis when the minimum ascent is up. Without that second part a rep that
                    // touched the top and went straight into the next one inside the minimum was
                    // left open, and the next rep with it: replayed at 20 fps, one pull-up in four.
                    (depth <= config.topEnter || (topReachedThisAscent && depth <= config.topExit)) &&
                        tMs - tCountExit >= config.minAscentMs -> {
                        complete(tMs, events)
                        arm(h)
                    }

                    // The joint says the rep is back at the top even though the range does not: the
                    // top of the range has drifted above this user's lockout. Filmed from the head,
                    // the ratio drifts as the body moves toward or away from the phone, and a
                    // lockout at 25-35 on the gauge lost every other rep. The elbow's 3-D angle does
                    // not drift, and a half rep cannot straighten it.
                    lockedOut(sample) && depth < config.countExit && tMs - tCountExit >= config.minAscentMs -> {
                        complete(tMs, events)
                        // Only a top that is clearly off is moved; one a few points over the band
                        // is noise, and moving the top moves the bottom with it.
                        if (depth > config.topEnter + JOINT_REANCHOR_MARGIN) calibrator.reanchorTop(h)
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
    private fun strikeBlockedReason(tMs: Long, sample: DepthSample, body: BodyFrameState): AbandonReason? {
        // Both ends of the band are placed where the signal crossed them, not on the frame that
        // first saw it across. Frame to frame, a brisk rep crosses the whole band in one or two
        // frames, and the frame times then undercount the descent by up to a frame: at 20-30 fps
        // that read a 1-second squat as 600-760 points a second and refused every rep as TOO_FAST
        // — more often the smoother the camera, since a lower frame rate happened to pad it.
        val descentMs = crossedAt(calibrator.countEnter(), tMs) - tTopExit
        if (descentMs <= 0L) return AbandonReason.TOO_FAST
        descentThisRepMs = descentMs.toInt()
        val bandSpeed = (calibrator.countEnter() - config.topExit) * 1000f / descentMs
        if (bandSpeed > config.maxDescentSpeed) return AbandonReason.TOO_FAST
        if (tLastStrike != Long.MIN_VALUE && tMs - tLastStrike < config.minRepPeriodMs) {
            return AbandonReason.TOO_FAST
        }
        if (depthVelocity <= config.minDescentVelocity) return AbandonReason.TOO_FAST

        // Two-signal agreement: the thing that stops someone waving an arm at the phone. The
        // primary signal can be fooled by moving the wrists alone; the joint angle and the watched
        // body part cannot be, because they describe the rest of the body.
        //
        // EVERY witness the descriptor declared and that is measurable this frame must agree. This
        // used to be a `when`, so the first available branch won and the rest were skipped — which
        // meant that whenever world landmarks were present the body-travel check was never
        // evaluated at all. A pushup could then be satisfied by elbow flexion with a completely
        // rigid head, which is waving at the phone with bent arms. It also made a pushup detector
        // accept a pull-up: the two share their primary signal exactly, and the only thing that
        // separates them is that a hanging body's head does not move relative to its shoulders.
        val signal = config.descriptor.signal

        if (!sample.jointDepth.isNaN()) {
            // One-sided. The fake this catches is a primary signal running ahead of the body —
            // the wrists moved, the elbow did not — so only a joint reading SHALLOWER than the
            // primary is a disagreement. A deeper one is a joint bent past its reference bottom:
            // the elbow scale tops out at 82 degrees and a pushup to the floor keeps going, so an
            // honest full-range rep reads 100 on the elbow while the primary is still at the count
            // line. Measured on the rig that gap was 34-37 against a limit of 35, and it refused
            // the pushups of exactly the people going deepest.
            //
            // Measured against the count line, not this frame's depth: the strike is a claim that
            // the rep reached the line, and a signal that overshoots it in one frame claims no more.
            // Filmed from behind, the wrists on the bar are lost as the head comes up to it and the
            // pull-up signal leaps 65 to 100 in a frame while the elbow reads 52 — an honest pull,
            // refused for how far the glitch went rather than for anything the body did.
            val claimed = minOf(depth, calibrator.countEnter())
            if (claimed - sample.jointDepth > config.maxSignalDisagreement) {
                return AbandonReason.INCONSISTENT
            }
        } else if (signal?.crossCheck == CrossCheckPolicy.JOINT_REQUIRED) {
            // A movement with no independently moving body part has nothing to fall back to, so it
            // refuses out loud rather than letting an unchecked rep through. The caller sees an
            // Abandoned event; it never looks like the detector simply counted nothing.
            return AbandonReason.INCONSISTENT
        }

        if (signal?.bodyTravel != null &&
            !sample.bodyDrop.isNaN() &&
            bodyDropAtTop != Float.POSITIVE_INFINITY
        ) {
            // A part of the body the primary signal does not watch: the head for a pushup, the
            // shoulders for a squat. It has to have genuinely travelled since this rep armed, which
            // is what waving at the phone does not do — and what a body hanging off a bar does not
            // do either.
            val descended = sample.bodyDrop - bodyDropAtTop
            val minTravel = signal.bodyTravel.minFraction ?: MIN_BODY_DROP_FRACTION
            if (descended < minTravel * calibrator.range) {
                return AbandonReason.INCONSISTENT
            }
        }

        // Side on, the shoulders themselves, in the picture: a pushup brings them down to the hands,
        // a wave brings the hands up to them. The phone is still, so the picture is the floor.
        val sideView = config.descriptor.sideView
        if (sideView != null && body.sideOn && shouldersAtTopDepth != Float.POSITIVE_INFINITY) {
            val came = Geometry.dot(
                body.shoulderU - shouldersAtTopU, body.shoulderV - shouldersAtTopV, body.nU, body.nV,
            ) / body.scale
            if (came < sideView.minShoulderTravel * calibrator.range) return AbandonReason.INCONSISTENT
        }

        // A split stance, for a movement that is one. The depth signal reads a squat exactly as a
        // lunge — hips over knees — so without this a squat counted. Unknown without world
        // landmarks, and unknown is not refused.
        signal?.stance?.let { stance ->
            if (!sample.stagger.isNaN() && sample.stagger < stance.minStaggerM) return AbandonReason.NOT_SPLIT
        }

        pruneStrikeWindow(tMs)
        if (strikeTimes.size >= config.maxRepsPer10s) return AbandonReason.TOO_FAST

        frontThisRep = sample.front
        return null
    }

    /** The leg in front on the rep being struck, for a split-stance movement. */
    private var frontThisRep: BodySide? = null

    /** Time from leaving the top band to crossing the count line, on the rep being struck. */
    private var descentThisRepMs = 0

    /** The working joint at its rest end — elbow straight, knee straight — by its 3-D angle. */
    private fun lockedOut(sample: DepthSample): Boolean =
        !sample.jointDepth.isNaN() && sample.jointDepth <= JOINT_LOCKOUT_MAX

    // --- the arming watchdog ---
    private var wdRising = true
    private var wdMax = Float.NaN
    private var wdMin = Float.NaN
    /** The straightest the working joint got on the way up to [wdMax], and the most bent on the way down. */
    private var wdJointStraightest = Float.NaN
    private var wdJointBentMost = Float.NaN
    private val wdPeaks = ArrayList<Float>()

    /**
     * The way out of a range whose top the body never reaches.
     *
     * A rep arms only in the top band, and only a completed rep teaches the calibrator anything, so
     * a top set too high — by a stale profile, a prior that does not fit this body, or a stray
     * frame — stops counting for good: every rep turns around at 40-60 instead of under 20, goes
     * deep, turns around again, and nothing ever changes. That is what two screen recordings from
     * one phone showed, a pushup set stuck at one rep and a pull-up set at none, with the tracker
     * reporting OK throughout.
     *
     * So the watchdog follows the body's own swings in `h`, not the gauge: a peak is a turnaround
     * that `h` then falls [WATCHDOG_SWING_OF_RMIN] of the movement's minimum range below, and a
     * valley one it rises as far above. Read off the gauge instead, through the count line of the
     * very range that is wrong, it caught the trap at the recording's own frame rate and missed it
     * at half and a third of it — the rates a phone actually runs at — because a hang reading 65-70
     * had to dip under a count line at 70 to register at all. After [WATCHDOG_TURNS] peaks in a row
     * short of the top band, at a consistent `h`, the top moves to where this user turns around.
     *
     * Half reps must not be able to do this, or bending the arms halfway would pull the top down to
     * meet them. So a peak only counts when the working joint straightened on the way up to it —
     * the elbow at the top of a pushup or the bottom of a hang — and bent on the way down from it,
     * by the joint's own 3-D angle, which the calibration cannot move. Unknown when world landmarks
     * are missing, and unknown is not refused: the alternative is a user who can never count.
     */
    private fun watchArming(h: Float, sample: DepthSample) {
        if (phase == RepPhase.READY_TOP) {
            // Arming works; nothing to rescue.
            resetWatchdog()
            return
        }
        val swing = WATCHDOG_SWING_OF_RMIN * config.rMin
        val joint = sample.jointDepth
        if (wdMax.isNaN()) {
            wdRising = true
            wdMax = h
            wdJointStraightest = joint
        }
        if (wdRising) {
            if (h > wdMax) wdMax = h
            if (!joint.isNaN() && !(joint >= wdJointStraightest)) wdJointStraightest = joint
            if (h < wdMax - swing) {
                wdRising = false
                wdMin = h
                wdJointBentMost = joint
            }
        } else {
            if (h < wdMin) wdMin = h
            if (!joint.isNaN() && !(joint <= wdJointBentMost)) wdJointBentMost = joint
            if (h > wdMin + swing) {
                onWatchdogPeak(wdMax, wdJointStraightest, wdJointBentMost)
                wdRising = true
                wdMax = h
                wdJointStraightest = joint
            }
        }
    }

    /** One full swing: up to [peak], then down at least a swing. Called once the valley after it is known. */
    private fun onWatchdogPeak(peak: Float, straightest: Float, bentMost: Float) {
        val straightened = straightest.isNaN() || straightest <= WATCHDOG_JOINT_REST_MAX
        val bent = bentMost.isNaN() || bentMost >= WATCHDOG_JOINT_BENT_MIN
        val short = calibrator.mapRaw(peak) > config.topEnter
        if (short && straightened && bent) wdPeaks += peak else wdPeaks.clear()
        if (wdPeaks.size < WATCHDOG_TURNS) return
        val spread = wdPeaks.max() - wdPeaks.min()
        if (spread <= WATCHDOG_SPREAD_OF_RANGE * calibrator.range) {
            // The least extended of them, so every one lands inside the top band. The median left
            // the others at 20-30 and took another two reps to fix.
            calibrator.reanchorTop(wdPeaks.min())
            wdPeaks.clear()
        } else {
            wdPeaks.removeAt(0)
        }
    }

    /**
     * The other end of [watchArming]: a range whose bottom the body never reaches.
     *
     * The count line is a fraction of this user's range, but the range's bottom starts as a guess
     * and only a counted rep moves it — so a guess too deep for how this phone sees this body is a
     * trap too. A lunge filmed from behind at a diagonal read 60 at the bottom against a line at 65
     * while the knee, by its own 3-D angle, was fully bent: every rep Shallow, none counted, forever.
     *
     * After [WATCHDOG_TURNS] Shallow reps in a row that turned around at a consistent `h` with the
     * working joint at least [BOTTOM_WATCHDOG_JOINT_MIN] of the way to a full bend, the bottom moves
     * up to the least deep of them. The joint is what keeps it honest: a half rep does not bend it,
     * and the calibration cannot move it — which is why only a joint that
     * [JointAngleCheck.confirmsFullDepth] is asked.
     */
    private fun watchBottom() {
        if (config.descriptor.signal?.jointCheck?.confirmsFullDepth != true) return
        if (!jointConfirmedFull() || hBotThisRep == Float.POSITIVE_INFINITY) {
            shallowBottoms.clear()
            return
        }
        shallowBottoms += hBotThisRep
        if (shallowBottoms.size < WATCHDOG_TURNS) return
        val spread = shallowBottoms.max() - shallowBottoms.min()
        if (spread <= WATCHDOG_SPREAD_OF_RANGE * calibrator.range) {
            calibrator.reanchorBottom(shallowBottoms.max())
            shallowBottoms.clear()
        } else {
            shallowBottoms.removeAt(0)
        }
    }

    private fun resetWatchdog() {
        wdRising = true
        wdMax = Float.NaN
        wdMin = Float.NaN
        wdJointStraightest = Float.NaN
        wdJointBentMost = Float.NaN
        wdPeaks.clear()
    }

    /** Top band to deep line on this rep, for [RepEvent.DeepUpgrade]. */
    private fun loweringMs(tMs: Long): Int =
        (crossedAt(calibrator.deepEnter(), tMs) - tTopExit).toInt().coerceAtLeast(0)

    /**
     * When the signal crossed [level] on its way to this frame's [depth], in either direction:
     * linear between the previous tracked frame and this one. This frame's own time when there is
     * no previous frame to interpolate from, or the signal was not moving across [level].
     */
    private fun crossedAt(level: Float, tMs: Long): Long {
        val before = prevDepth
        if (before.isNaN()) return tMs
        val deeper = depth > before && before < level
        val shallower = depth < before && before > level
        if (!deeper && !shallower) return tMs
        // Across a tracking gap there is no knowing when it crossed; this frame is the honest answer.
        if (tMs - prevDepthMs > MAX_INTERPOLATION_GAP_MS) return tMs
        val f = ((level - before) / (depth - before)).coerceIn(0f, 1f)
        return prevDepthMs + ((tMs - prevDepthMs) * f).toLong()
    }

    private fun strike(tMs: Long, events: MutableList<RepEvent>) {
        shallowBottoms.clear()
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
        events += RepEvent.Strike(tMs, repCount, grade, depth, combo, front = frontThisRep, descentMs = descentThisRepMs)
        if (grade == RepGrade.DEEP) {
            deepFiredThisRep = true
            events += RepEvent.DeepUpgrade(tMs, repCount, depth, loweringMs(tMs))
        }
    }

    private fun complete(tMs: Long, events: MutableList<RepEvent>) {
        if (!struckThisRep) {
            // Refused for speed, but down and back up with the working joint fully bent: a real
            // rep through a band too narrow for this body. Let it widen the range, and only widen.
            if (refusedFastThisRep && jointConfirmedFull() &&
                hTopThisRep != Float.NEGATIVE_INFINITY && hBotThisRep != Float.POSITIVE_INFINITY
            ) {
                calibrator.widen(hTopThisRep, hBotThisRep)
            }
            refusedFastThisRep = false
            return
        }

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

    /**
     * Where the travel witness sat at rest, for the rep in progress; +∞ until the first armed
     * frame reports it.
     *
     * This used to be overwritten on every armed frame, so it held the value from the LAST frame
     * before the descent began — depth already at topExit, a third of the way down — rather than
     * from the top. The witness was then asked to travel 30% of the range inside the remaining
     * third of the rep, which only a part geared faster than the primary signal can do. A curl
     * measured 0.3271 where 0.3277 was required and every rep after the first was refused; a lunge
     * 0.279 against 0.360; a dip 0.022 against 0.068. The check was meant to be "has this part
     * moved since the top", and taking the minimum over the armed frames is what makes it that.
     */
    private var bodyDropAtTop = Float.POSITIVE_INFINITY

    /** Side on: where the shoulders were in the picture at the top of this rep, and at what depth. */
    private var shouldersAtTopDepth = Float.POSITIVE_INFINITY
    private var shouldersAtTopU = 0f
    private var shouldersAtTopV = 0f

    private fun arm(h: Float) {
        phase = RepPhase.READY_TOP
        bodyDropAtTop = Float.POSITIVE_INFINITY
        shouldersAtTopDepth = Float.POSITIVE_INFINITY
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

        // From the side the far shoulder is regressed rather than seen, and its confidence never
        // clears the bar — a `min` across the pair would report LOW_CONFIDENCE for every frame of
        // a perfectly tracked set. Which rule applies is declared by the exercise.
        val core = when (if (body.sideOn) CoreConfidence.NEAR_SIDE else config.descriptor.coreConfidence) {
            CoreConfidence.BOTH_SHOULDERS ->
                minOf(confidence[Lm.LEFT_SHOULDER], confidence[Lm.RIGHT_SHOULDER])
            CoreConfidence.NEAR_SIDE ->
                maxOf(confidence[Lm.LEFT_SHOULDER], confidence[Lm.RIGHT_SHOULDER])
        }
        if (core < config.minCoreConfidence) return PoseQuality.LOW_CONFIDENCE

        // The landmarks that carry *this* movement, not the arms unconditionally. A squat used to
        // be gated on wrist confidence it never reads, so a squat with the hands out of frame
        // reported OUT_OF_FRAME while the hip-and-knee signal it actually uses was perfect.
        val signal = config.descriptor.signal
        if (signal != null) {
            val primary = maxOf(
                confidence[signal.proximal.left] * confidence[signal.distal.left],
                confidence[signal.proximal.right] * confidence[signal.distal.right],
            )
            if (primary < config.minSideWeight && sample.source != DepthSource.ELBOW_FALLBACK) {
                return PoseQuality.OUT_OF_FRAME
            }
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
        tQualityLostSince = Long.MIN_VALUE
        tLastGoodPose = Long.MIN_VALUE
        tFirstFrame = Long.MIN_VALUE
        tLastTracked = Long.MIN_VALUE
        prevDepth = Float.NaN
        resetWatchdog()
        shallowBottoms.clear()
        maxJointThisRep = Float.NaN
        refusedFastThisRep = false
        topReachedThisAscent = false
        bodyDropAtTop = Float.POSITIVE_INFINITY
        shouldersAtTopDepth = Float.POSITIVE_INFINITY
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
        /** A stretch of frames that are not OK shorter than this does not restart [ARM_SETTLE_MS]. */
        const val QUALITY_BLIP_MS = 200L

        const val RATE_WINDOW_MS = 10_000L

        /**
         * The working joint's own depth, 0-100, at or under which it counts as locked out. Real
         * pushups filmed from the head read 4-16 at the top and 70-95 at the bottom.
         */
        const val JOINT_LOCKOUT_MAX = 18f
        /** How far past the top band a joint-confirmed lockout must read before the top moves to it. */
        const val JOINT_REANCHOR_MARGIN = 10f

        /** Turnarounds short of the top band, in a row, before the top of the range moves to them. */
        const val WATCHDOG_TURNS = 2
        /** How close together those turnarounds must be, as a fraction of the calibrated range. */
        const val WATCHDOG_SPREAD_OF_RANGE = 0.35f
        /**
         * How far `h` must travel, as a fraction of the movement's minimum range, to be a swing
         * rather than a wobble. Real sets on a phone travelled 0.7 (pull-up) and 0.9 (pushup).
         */
        const val WATCHDOG_SWING_OF_RMIN = 0.5f
        /** The working joint's own depth, 0-100, at or under which it is at its rest end. */
        const val WATCHDOG_JOINT_REST_MAX = 35f
        /** And at or over which it bent: a rep, not someone shifting their weight. */
        const val WATCHDOG_JOINT_BENT_MIN = 45f
        /** The working joint's own depth at or over which a rep that fell short of the line was full. */
        const val BOTTOM_WATCHDOG_JOINT_MIN = 90f

        /** Longest frame gap a line crossing is interpolated across: three frames at 15 fps. */
        const val MAX_INTERPOLATION_GAP_MS = 200L

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
    /**
     * The detector for a movement, chosen by what kind of movement it is rather than by name.
     *
     * Adding a counted exercise therefore needs no edit here at all: it is a [MovementKind.REP]
     * value in [Exercises] and the rep state machine picks it up. A hold still needs a detector
     * written for it — [PlankDetector]'s scoring terms (hip height, shoulder stack, alignment) are
     * a plank's, not a wall sit's — so a second hold is a real piece of work, and this says so
     * instead of quietly handing it the plank scorer.
     */
    fun create(
        type: ExerciseType,
        config: DetectorConfig? = null,
        profile: UserProfile = UserProfile.empty(),
    ): RepDetector {
        val descriptor = Exercises.of(type)
        val cfg = config ?: descriptor.config
        return when (descriptor.kind) {
            MovementKind.REP -> RepDetectorImpl(cfg, profile)
            MovementKind.HOLD -> {
                require(type == ExerciseType.PLANK) {
                    "$type is a hold but has no scorer; PlankDetector measures a plank, not $type"
                }
                PlankDetector(cfg)
            }
        }
    }
}
