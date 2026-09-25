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

        confidenceEstimator.compute(frame, boneScale(frame), confidence)
        val body = bodyTracker.update(frame, confidence)
        // From world landmarks when the model gives them, which on a phone is always: the only
        // reading of a plank that does not depend on where the phone is. See [posture].
        val posture = if (frame.hasPose && frame.hasWorld) posture(frame) else null

        val newQuality = when {
            !frame.hasPose -> PoseQuality.NO_SUBJECT
            frame.hasWorld -> if (posture != null) PoseQuality.OK else PoseQuality.LOW_CONFIDENCE
            body == null -> PoseQuality.LOW_CONFIDENCE
            else -> PoseQuality.OK
        }
        if (newQuality != quality) {
            quality = newQuality
            events += RepEvent.QualityChanged(tMs, newQuality)
        }

        if (newQuality == PoseQuality.OK && (posture != null || body != null)) {
            score = if (posture != null) scorePosture(frame, posture) else scoreFrame(frame, body!!)
            qualitySum += score
            qualitySamples++
            advance(tMs, dtMs, events)
        } else if (holding) {
            // The tracker blinking is not the plank breaking: the same grace as a wobble, with no
            // time counted, so a frame the model loses the shoulders on does not halve the charge.
            if (brokenSinceMs == Long.MIN_VALUE) brokenSinceMs = tMs
            if (tMs - brokenSinceMs >= plank.breakGraceMs) breakHold(tMs, events)
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
            calibration = snapshotCalibration(),
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
     * The body as a shape in space, from world landmarks: how straight the line from shoulder to
     * knee is, how straight the legs are, and how far the torso leans from upright.
     *
     * This replaced reading those angles off the picture, which is what let someone on all fours —
     * or simply standing there — hold a plank. Filmed from the head, shoulder, hip and knee of any
     * of those poses line up down the image, and a straight line in the image scored as a straight
     * body. And the picture-based version needed the two shoulders apart in the image to measure
     * anything at all, so side on, the view a plank is most naturally filmed from, it never held.
     * World landmarks are the model's own 3-D skeleton and read the same from every side.
     *
     * Each point is taken from the side the camera actually sees better: side on, the far limb is
     * the model's guess. Null when the shoulders or the hips cannot be seen on either side.
     *
     * The knees and ankles are taken whether the camera sees them or not. Requiring them is what
     * kept a real plank from ever holding on a phone: from the head they are behind the body
     * (visibility 0.05-0.3 at the top of every pushup in a device recording), and from the side at
     * the distance people put a phone they are past the edge of the picture. The model still places
     * them in its 3-D skeleton, and at those pushup tops it put the knee at 149-178 degrees — a
     * straight leg, read a little bent — which is what the leg gate below is set against. Unseen
     * legs still gate the pose, so a knee the model folds is still not a plank, but they are left
     * out of the form score, because a user should not be marked down for what the camera cannot
     * see.
     */
    private fun posture(frame: PoseFrame): Posture? {
        val w = frame.worldLandmarks
        fun seen(left: Int, right: Int) = maxOf(confidence[left], confidence[right]) >= config.minCoreConfidence
        fun point(left: Int, right: Int): FloatArray {
            // Mostly the better-seen side, so a regressed far limb cannot bend the line. When
            // neither side is seen, both guesses count about the same.
            val wl = maxOf(confidence[left], UNSEEN_WEIGHT).let { it * it }
            val wr = maxOf(confidence[right], UNSEEN_WEIGHT).let { it * it }
            val a = w[left]
            val b = w[right]
            return floatArrayOf(
                (a.x * wl + b.x * wr) / (wl + wr),
                (a.y * wl + b.y * wr) / (wl + wr),
                (a.z * wl + b.z * wr) / (wl + wr),
            )
        }
        if (!seen(Lm.LEFT_SHOULDER, Lm.RIGHT_SHOULDER) || !seen(Lm.LEFT_HIP, Lm.RIGHT_HIP)) return null
        val shoulder = point(Lm.LEFT_SHOULDER, Lm.RIGHT_SHOULDER)
        val hip = point(Lm.LEFT_HIP, Lm.RIGHT_HIP)
        val knee = point(Lm.LEFT_KNEE, Lm.RIGHT_KNEE)
        val ankle = point(Lm.LEFT_ANKLE, Lm.RIGHT_ANKLE)
        val elbow = if (seen(Lm.LEFT_ELBOW, Lm.RIGHT_ELBOW)) point(Lm.LEFT_ELBOW, Lm.RIGHT_ELBOW) else null
        val kneesSeen = seen(Lm.LEFT_KNEE, Lm.RIGHT_KNEE)
        val legsSeen = kneesSeen && seen(Lm.LEFT_ANKLE, Lm.RIGHT_ANKLE)
        val wrist = if (seen(Lm.LEFT_WRIST, Lm.RIGHT_WRIST)) point(Lm.LEFT_WRIST, Lm.RIGHT_WRIST) else null

        val torso = floatArrayOf(hip[0] - shoulder[0], hip[1] - shoulder[1], hip[2] - shoulder[2])
        val torsoLen = kotlin.math.sqrt(torso[0] * torso[0] + torso[1] * torso[1] + torso[2] * torso[2])
        if (torsoLen < 1e-4f) return null
        // World y is the camera's down. A phone propped on the floor is tilted up a little, so a
        // level body reads a little off level; the gate below leaves room for that.
        val fromVertical = Math.toDegrees(kotlin.math.acos((abs(torso[1]) / torsoLen).toDouble())).toFloat()

        return Posture(
            bodyLine = angle3(shoulder, hip, knee),
            legs = angle3(hip, knee, ankle),
            legsSeen = legsSeen,
            kneesSeen = kneesSeen,
            fromVertical = fromVertical,
            arm = elbow?.let { angle3(hip, shoulder, it) },
            kneeLift = elbow?.let { kneeLift(shoulder, it, wrist, knee) },
        )
    }

    /**
     * How far the knees are off the floor, as a fraction of how far the shoulders are: 0 with the
     * knees down, 0.4 in a forearm plank on the rig and 0.6-0.9 in one filmed on a phone.
     *
     * In a plank the arm that holds the body up is vertical whatever the phone sees, so elbow to
     * shoulder is up; and whichever of the elbow and the wrist is lower is on the floor — the elbow
     * on the forearms, the wrist on the hands. That reads the one thing a knee plank changes, where
     * the knees are, from joints the camera sees side on even when the feet are past the edge of
     * the picture and the knee angle is the model's guess at a shin it cannot see.
     */
    private fun kneeLift(shoulder: FloatArray, elbow: FloatArray, wrist: FloatArray?, knee: FloatArray): Float? {
        val ux = shoulder[0] - elbow[0]; val uy = shoulder[1] - elbow[1]; val uz = shoulder[2] - elbow[2]
        val len = kotlin.math.sqrt(ux * ux + uy * uy + uz * uz)
        if (len < 1e-4f) return null
        fun up(p: FloatArray) = ((p[0] - shoulder[0]) * ux + (p[1] - shoulder[1]) * uy + (p[2] - shoulder[2]) * uz) / len
        val floor = minOf(up(elbow), wrist?.let { up(it) } ?: 0f)
        if (floor > -1e-3f) return null
        return (up(knee) - floor) / -floor
    }

    /**
     * Form, 0-100, from [posture] and how still the shoulders are. A pose that is not a plank at
     * all — upright, folded at the hips, or down on the knees — is capped under the holding line
     * however still it is.
     */
    private fun scorePosture(frame: PoseFrame, p: Posture): Float {
        val line = (1f - abs(180f - p.bodyLine) / LINE_TOLERANCE_DEG).coerceIn(0f, 1f)
        val legs = (1f - (STRAIGHT_LEGS_DEG - p.legs).coerceAtLeast(0f) / LEGS_TOLERANCE_DEG).coerceIn(0f, 1f)
        val level = ((p.fromVertical - UPRIGHT_MAX_DEG) / (LEVEL_DEG - UPRIGHT_MAX_DEG)).coerceIn(0f, 1f)

        var weighted = line * W_LINE + level * W_LEVEL
        var weight = W_LINE + W_LEVEL
        if (p.legsSeen) { weighted += legs * W_LEGS; weight += W_LEGS }
        imageStability(frame)?.let { weighted += it * W_STILL; weight += W_STILL }
        val score = 100f * weighted / weight

        // The legs, from whatever of them the camera sees. With the feet past the edge of the
        // picture the knee angle is the model's guess at a shin it cannot see — a real side-on plank
        // read 139-145, a knee plank's angle — so the knees' height off the floor can say it too.
        // Either will do: the height leans on the arm being upright, and hands a little ahead of the
        // shoulders read a plank on the rig as 0.16.
        val legsStraight = p.legs >= MIN_LEGS_DEG ||
            (!p.legsSeen && p.kneesSeen && p.kneeLift != null && p.kneeLift >= MIN_KNEE_LIFT)
        val isPlank = p.fromVertical >= UPRIGHT_MAX_DEG &&
            p.bodyLine >= MIN_BODY_LINE_DEG &&
            legsStraight &&
            // Arms reaching down to the floor, not hanging at the sides: the second thing that
            // tells a plank from standing still, and one the camera's tilt cannot touch. Not asked
            // of a torso past level, which no one standing has: from the head on the forearms the
            // model put the elbow behind the shoulder in half the frames and read 10-35.
            (p.arm == null || p.arm >= MIN_ARM_DEG || p.fromVertical >= LEVEL_DEG)
        return if (isPlank) score else minOf(score, NOT_A_PLANK_CAP)
    }

    /**
     * Shoulder jitter in the picture, against the body's size in the picture: the torso's length
     * or the shoulders' width, whichever is larger. Filmed from the head the torso points at the
     * lens and is a sliver of the picture, and against it alone a plank held still read as shaking.
     */
    private fun imageStability(frame: PoseFrame): Float? {
        val sc = maxOf(confidence[Lm.LEFT_SHOULDER], confidence[Lm.RIGHT_SHOULDER])
        val hc = maxOf(confidence[Lm.LEFT_HIP], confidence[Lm.RIGHT_HIP])
        if (sc < config.minCoreConfidence || hc < config.minCoreConfidence) return null
        val shoulder = frame.midpoint2(Lm.LEFT_SHOULDER, Lm.RIGHT_SHOULDER)
        val hip = frame.midpoint2(Lm.LEFT_HIP, Lm.RIGHT_HIP)
        val torso = maxOf(
            Geometry.norm(hip.first - shoulder.first, hip.second - shoulder.second),
            Geometry.norm(frame.u(Lm.LEFT_SHOULDER) - frame.u(Lm.RIGHT_SHOULDER), frame.v(Lm.LEFT_SHOULDER) - frame.v(Lm.RIGHT_SHOULDER)),
        )
        if (torso <= 1e-4f) return null
        recentShoulderU.addLast(shoulder.first)
        recentShoulderV.addLast(shoulder.second)
        while (recentShoulderU.size > STABILITY_WINDOW) {
            recentShoulderU.removeFirst()
            recentShoulderV.removeFirst()
        }
        if (recentShoulderU.size < STABILITY_WINDOW / 2) return null
        val meanU = recentShoulderU.average().toFloat()
        val meanV = recentShoulderV.average().toFloat()
        var spread = 0f
        for (i in recentShoulderU.indices) {
            spread = maxOf(spread, Geometry.norm(recentShoulderU[i] - meanU, recentShoulderV[i] - meanV))
        }
        return (1f - (spread / torso) / STABILITY_TOLERANCE).coerceIn(0f, 1f)
    }

    /**
     * The scale the bone-length test measures limbs against: the larger of the shoulder width and
     * most of the torso, both as seen. Side on the shoulder width alone is nearly zero, and every
     * limb would look impossibly long against it and be thrown away.
     */
    private fun boneScale(frame: PoseFrame): Float {
        if (!frame.hasPose) return 0f
        val shoulders = Geometry.norm(
            frame.u(Lm.LEFT_SHOULDER) - frame.u(Lm.RIGHT_SHOULDER),
            frame.v(Lm.LEFT_SHOULDER) - frame.v(Lm.RIGHT_SHOULDER),
        )
        val s = frame.midpoint2(Lm.LEFT_SHOULDER, Lm.RIGHT_SHOULDER)
        val h = frame.midpoint2(Lm.LEFT_HIP, Lm.RIGHT_HIP)
        val torso = Geometry.norm(h.first - s.first, h.second - s.second)
        return maxOf(bodyTracker.scale, shoulders, torso * SHOULDER_PER_TORSO)
    }

    private fun angle3(a: FloatArray, vertex: FloatArray, b: FloatArray): Float {
        val ux = a[0] - vertex[0]; val uy = a[1] - vertex[1]; val uz = a[2] - vertex[2]
        val vx = b[0] - vertex[0]; val vy = b[1] - vertex[1]; val vz = b[2] - vertex[2]
        val nu = kotlin.math.sqrt(ux * ux + uy * uy + uz * uz)
        val nv = kotlin.math.sqrt(vx * vx + vy * vy + vz * vz)
        if (nu < 1e-5f || nv < 1e-5f) return 0f
        val cos = ((ux * vx + uy * vy + uz * vz) / (nu * nv)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cos.toDouble())).toFloat()
    }

    /**
     * [arm] is the hip-shoulder-elbow angle, or null when neither elbow is seen. [legs] is the knee
     * angle whether or not [legsSeen]; unseen, it is the model's estimate.
     */
    private data class Posture(
        val bodyLine: Float,
        val legs: Float,
        val legsSeen: Boolean,
        val kneesSeen: Boolean,
        val fromVertical: Float,
        val arm: Float?,
        /** See [kneeLift]; null when the elbow is not seen. */
        val kneeLift: Float?,
    )

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
        ExerciseType.PLANK, 0f, 0f, 0f, CalibrationState.CONVERGED, 0, config.countEnter, config.deepEnter,
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

        // --- the world-landmark plank ---

        /** A straight body is 180 degrees shoulder-hip-knee; this far off it scores nothing. */
        const val LINE_TOLERANCE_DEG = 35f
        /** Hips this far out of line — a sag, or a pike — is not a plank at all. */
        const val MIN_BODY_LINE_DEG = 145f
        /** Straight legs. On the knees the shin folds back and the knee closes to 90-145 degrees. */
        const val STRAIGHT_LEGS_DEG = 172f
        const val LEGS_TOLERANCE_DEG = 30f
        /**
         * Under this the knee is bent: a knee plank with the shins flat is 143 on the rig. Over
         * the pushup tops of a device recording filmed from the head, the model put the unseen
         * knee at 149-178 degrees, 4% of frames under 150 and 29% under 155 — so 155 refused
         * three frames in ten of a real plank there.
         */
        const val MIN_LEGS_DEG = 150f
        /** How much a side neither camera nor model is sure of still counts toward the average. */
        const val UNSEEN_WEIGHT = 0.1f
        /**
         * Knees this far off the floor, as a fraction of the shoulders' height, are not resting on
         * it. A knee plank and all fours on the rig read 0.03; a forearm plank 0.40 on the rig and
         * 0.61-0.92 filmed side on by a phone.
         */
        const val MIN_KNEE_LIFT = 0.25f
        /**
         * A torso within this of upright is standing, not planking. World y is the camera's down,
         * not gravity's, so the phone's own tilt is in the reading: a phone on the floor tilted up
         * 30 degrees shows a standing body 30 degrees off upright, and a plank on the hands — which
         * slopes about 23 degrees from level — filmed from its head reads as little as 37.
         */
        const val UPRIGHT_MAX_DEG = 34f
        /** At or past this lean the torso counts as level. */
        const val LEVEL_DEG = 60f
        /**
         * Upper arm against the torso. On the hands or the forearms it is 60-80 degrees; standing
         * with the arms down, 5-20.
         */
        const val MIN_ARM_DEG = 40f
        /** What a pose that is not a plank can score at most: under the holding line. */
        const val NOT_A_PLANK_CAP = 40f
        const val W_LINE = 0.40f
        const val W_LEGS = 0.25f
        const val W_LEVEL = 0.15f
        const val W_STILL = 0.20f
        /** Shoulder width is about 0.8 of the torso's length on an adult. */
        const val SHOULDER_PER_TORSO = 0.8f
        const val MAX_STEP_MS = 250L
    }
}

/** Midpoint of two landmarks in isotropic units. */
private fun PoseFrame.midpoint2(a: Int, b: Int): Pair<Float, Float> =
    (u(a) + u(b)) / 2f to (v(a) + v(b)) / 2f
