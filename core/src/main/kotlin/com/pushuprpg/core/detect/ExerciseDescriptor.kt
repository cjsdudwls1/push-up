package com.pushuprpg.core.detect

import com.pushuprpg.core.pose.PoseLandmarks as Lm

/**
 * An exercise, as data.
 *
 * Every movement in this game is the same measurement:
 *
 * ```
 *   h = dot(distal − proximal, n̂) / scale
 * ```
 *
 * `n̂` is the unit normal to the shoulder axis, sign-forced toward [normalToward]; `scale` is the
 * projected shoulder width. Both the numerator and the denominator carry the same `f/Z` from the
 * pinhole projection, so focal length and camera distance cancel *exactly* and the reading is
 * invariant to how far the user is from the phone. That invariance is the whole design. A new
 * exercise that does not preserve it is not a new descriptor value — it is a new detector.
 *
 * Adding a movement is therefore adding one value to [Exercises], not editing five files.
 *
 * ## The one rule an author must get right
 *
 * **`h` must DECREASE as the effort increases.** 0 on the 0..100 scale is the rest position
 * (lockout, standing, a dead hang); 100 is the deepest calibrated position. Everything downstream
 * — the calibrator's `top > bottom` invariant, the hysteretic state machine, the gauge — assumes
 * it. If a movement naturally reads the other way (a deadlift's hands travel *away* from the
 * shoulders on the way down), swap [RepSignal.proximal] and [RepSignal.distal]: the order of the
 * pair is the sign.
 *
 * Twice in this project an exercise signal was defined plausibly and silently counted nothing,
 * because a sign was inverted or a cross-check could not move. [RepSignal.bodyTravel] carries the
 * reasoning for each movement in its doc comment for that reason.
 */
data class ExerciseDescriptor(
    val type: ExerciseType,
    val kind: MovementKind,
    /**
     * The landmark pair the body normal is sign-forced toward — the "far end" of the body for this
     * movement.
     *
     * Getting this wrong does not degrade the reading, it inverts it, so descending registers as
     * rising. It is deliberately separate from [RepSignal.distal]: a squat measures hip-above-knee
     * but orients toward the hips, and a curl must orient toward the hips too, because its wrists
     * cross the shoulder line mid-rep and would flip the normal half way through the movement.
     */
    val normalToward: LandmarkPair,
    /** Null exactly when [kind] is [MovementKind.HOLD] — a hold has no depth ratio to compute. */
    val signal: RepSignal?,
    /** Thresholds, priors and clamps. The detector reads nothing else. */
    val config: DetectorConfig,
    /** Damage per rep relative to a pushup. The balance knob; never an enemy HP number. */
    /** Where the body frame's axis and scale come from. See [AxisSource]. */
    val axisSource: AxisSource = AxisSource.SHOULDER_PAIR,
    /** How much of the core the quality gate insists on seeing. See [CoreConfidence]. */
    val coreConfidence: CoreConfidence = CoreConfidence.BOTH_SHOULDERS,
    /**
     * Fix n̂'s sign once per set instead of re-deciding it every frame.
     *
     * Re-deciding is right when the far pair is far: for a pushup the wrists are most of a
     * shoulder width away from the shoulder line all through the rep, so the dot product never
     * approaches zero. Filmed from the side at the hard end of a press the numerator falls to
     * about 0.17 of scale against a regressed far wrist, and a sign flip lands directly on the
     * strike frame. Latching takes the sign the first time the movement is unambiguous and holds
     * it until the subject changes.
     */
    val latchNormalSign: Boolean = false,
    /**
     * False while an exercise's signal has never been checked against a recorded trace of a real
     * set. The UI says so rather than pretending otherwise.
     */
    val validatedOnDevice: Boolean = false,
    val damageCoefficient: Float,
    /**
     * Reps of *this* movement that make up the same session as one rep of content authored in
     * pushups.
     *
     * Deliberately not [damageCoefficient], which is a per-rep damage ratio and answers a different
     * question. A pull-up is worth 2.6 pushups as one rep of effort; that does not make a pull-up
     * session 2.6 times shorter than a pushup session, it makes it about three times shorter,
     * because a near-max movement caps out on volume long before a bodyweight push does. Using the
     * damage coefficient for this gave 154 pull-ups and 471 bench reps for a 400-rep tier, neither
     * of which is a session that exists.
     *
     * Anchored on a trained pushup session of ~150 reps: pull-up 45, bench 30, overhead press 30,
     * curl 45, squat 100, lunge 130, hinge 37. A hold is in seconds, not reps.
     */
    val sessionVolumeScale: Float,
    /** Starting capacity for a user who has never done this movement — reps, or seconds for a hold. */
    val defaultCapacity: Float,
    /** Reps (or seconds, for a hold) in one day that keep a streak alive. */
    val streakBar: Int,
) {
    init {
        require(config.exercise == type) {
            "descriptor for $type carries a config for ${config.exercise}"
        }
        when (kind) {
            MovementKind.REP -> require(signal != null) {
                "$type is counted in reps but declares no signal — it would count nothing"
            }
            MovementKind.HOLD -> require(signal == null) {
                "$type is a hold; a depth ratio would never be read"
            }
        }
        require(damageCoefficient > 0f) { "$type must deal damage" }
        require(sessionVolumeScale > 0f) { "$type needs a session volume scale or a tier costs nothing" }
        require(defaultCapacity > 0f) { "$type needs a starting capacity" }
        require(streakBar > 0) { "$type needs a streak bar" }
    }
}

/** Counted, or held. The two have different detectors because they are different questions. */
enum class MovementKind { REP, HOLD }

/** A left/right landmark index pair, so every measurement can be taken per side. */
data class LandmarkPair(val left: Int, val right: Int) {
    init {
        require(left in 0 until Lm.COUNT && right in 0 until Lm.COUNT) {
            "landmark indices out of range: $left, $right"
        }
        require(left != right) { "a pair needs two distinct landmarks" }
    }
}

/** A point on the body one of the cross-checks measures from or to. */
sealed interface BodyPoint {
    /** A single landmark, for something that has no left and right — the nose. */
    data class Single(val index: Int) : BodyPoint

    /** The unweighted midpoint of a pair, which is what the shoulder anchor already is. */
    data class Midpoint(val pair: LandmarkPair) : BodyPoint
}

/**
 * The 3-D joint-angle cross-check, read from MediaPipe world landmarks.
 *
 * It must come from world landmarks, never from the projected 2-D angle: in this camera geometry
 * the working limb usually swings away from the lens, so the projected angle can barely move across
 * a full-depth rep. Useful as a cross-check, unusable as the gauge.
 */
data class JointAngleCheck(
    val vertex: LandmarkPair,
    val proximal: LandmarkPair,
    val distal: LandmarkPair,
    /** The angle at the rest position — depth 0. */
    val topDeg: Float,
    /** The angle at a standard full-depth position — depth 100. */
    val bottomDeg: Float,
) {
    init {
        // Deliberately not "the joint must close as the user descends". It does for a pushup, a
        // squat, a curl and a hinge, and it does not for an overhead press: the elbow OPENS from
        // 62 degrees racked to 170 locked out overhead, because "depth" here means effort, not
        // downward. The mapping is an inverseLerp between these two, which handles either
        // direction; all that matters is that they are not the same number.
        require(topDeg != bottomDeg) { "a joint check needs two distinct anchor angles" }
    }
}

/**
 * How far an **independent** part of the body has travelled along the body axis since the top.
 *
 * Defined so it **increases as the user descends**, for every exercise, which is what [invert] is
 * for: set it when the measured extent naturally shrinks with depth.
 *
 * The point is to watch something the primary signal does not. Two ways to get this wrong, both of
 * which have already happened here:
 *
 *  - **A sign inversion** makes an honest rep look like the user rising, and every rep is rejected.
 *  - **A part that cannot move** relative to the anchor is worse: it never disagrees, so it reads
 *    as a passing check while testing nothing — or, if it drifts the wrong way, rejects everything.
 *    Nose-vs-shoulders is a real check for a pushup (the head drops toward the floor) and a dead
 *    one for a squat (the neck is rigid), which is why the squat watches the shoulders against the
 *    ankles instead.
 *
 * When no part of the body moves independently, this must be null and the descriptor must require
 * the joint angle instead. Inventing a check that cannot disagree is the failure mode, not the fix.
 */
data class BodyTravelCheck(
    val from: BodyPoint,
    val to: BodyPoint,
    /** True when `dot(to − from, n̂)` shrinks with depth and must be negated to grow with it. */
    val invert: Boolean,
)

/** What must hold before a strike is allowed. */
enum class CrossCheckPolicy {
    /**
     * Use the joint angle when world landmarks provide it, otherwise the body-travel check,
     * otherwise let the rep through on the primary signal alone.
     */
    BEST_AVAILABLE,

    /**
     * The joint angle must be present and must agree. For a movement with no independently moving
     * body part there is no second opinion to fall back to, and waving the rep through would mean
     * the primary signal is unchecked. A rejected rep surfaces as [AbandonReason.INCONSISTENT],
     * so this refuses out loud rather than counting nothing in silence.
     */
    JOINT_REQUIRED,
}

/** What the depth ratio is normalized by. */
/**
 * Where the body frame's axis and scale come from.
 *
 * [SHOULDER_PAIR] is the default and the better one: the shoulder line is physically constant and
 * close to perpendicular to the lens for anyone facing it, so `f` and `Z` cancel in the depth
 * ratio and distance stops mattering.
 *
 * [NEAR_SIDE_TORSO] exists for the exercises filmed from the side. There the shoulder pair projects
 * almost onto itself — separation falls to roughly 0.02-0.05 of the frame — so `instantScale` sits
 * under [DetectorConfig.minScale], `BodyFrameTracker.update` returns null on every frame, and the
 * user gets a frozen gauge and no explanation. Taking the near side's shoulder-to-hip instead keeps
 * both hallucinated far landmarks out of the coordinate frame and out of the divisor.
 */
enum class AxisSource { SHOULDER_PAIR, NEAR_SIDE_TORSO }

/**
 * How the two sides' readings become one number.
 *
 * [CONFIDENCE_WEIGHTED] is right for a symmetric movement: both sides do the same thing, so the
 * better-seen one should dominate and the difference between them is a usable asymmetry signal.
 *
 * [DEEPER_SIDE] is for a split stance. In a lunge only the front leg bends; averaging it with a
 * trailing leg that barely moves halves the reading and no honest rep ever reaches the line. Taking
 * the deeper side tracks whichever leg is working, and asymmetry stops meaning anything — a lunge is
 * asymmetric by definition, so it is reported as zero rather than as a fault.
 */
enum class SideCombiner { CONFIDENCE_WEIGHTED, DEEPER_SIDE }

/**
 * How much of the core the quality gate insists on seeing.
 *
 * [BOTH_SHOULDERS] is right when the user faces the lens. Filmed from the side the far shoulder is
 * a guess, its confidence sits below [DetectorConfig.minCoreConfidence] permanently, and a `min`
 * across the pair reports LOW_CONFIDENCE forever — the same silent zero as above, from a different
 * direction. [NEAR_SIDE] takes the better of the two instead.
 */
enum class CoreConfidence { BOTH_SHOULDERS, NEAR_SIDE }

enum class ScaleReference {
    /**
     * Slowly averaged projected biacromial width. The only normaliser implemented, and the only one
     * that cancels `f/Z` for a subject facing the lens.
     *
     * A movement filmed from the side — a bench press, most barbell work — projects the shoulder
     * axis onto nearly nothing, so this collapses and the reading inflates without bound. That is a
     * [BodyFrameTracker] change, not a descriptor value.
     */
    SHOULDER_WIDTH,
}

/** Everything needed to turn one frame into a depth reading and to sanity-check it. */
data class RepSignal(
    /** The end of the measured segment that stays put relative to the body frame. */
    val proximal: LandmarkPair,
    /** The end that moves. `h = dot(distal − proximal, n̂) / scale`, and must fall with depth. */
    val distal: LandmarkPair,
    val scale: ScaleReference,
    val jointCheck: JointAngleCheck?,
    val bodyTravel: BodyTravelCheck?,
    /** How the two sides' readings become one. See [SideCombiner]. */
    val sideCombiner: SideCombiner = SideCombiner.CONFIDENCE_WEIGHTED,
    val crossCheck: CrossCheckPolicy,
    /**
     * Whether a reading may still be produced from the joint angle alone when the primary
     * landmarks drop below [DetectorConfig.minSideWeight] — a pushup with the wrists at the edge of
     * frame. False means "no primary landmarks, no reading", which is the honest answer for a
     * movement whose joint angle is a weak proxy.
     */
    val allowJointFallback: Boolean,
) {
    init {
        require(scale == ScaleReference.SHOULDER_WIDTH) { "only shoulder width is implemented" }
        require(jointCheck != null || crossCheck != CrossCheckPolicy.JOINT_REQUIRED) {
            "a signal cannot require a joint check it does not define"
        }
        require(!allowJointFallback || jointCheck != null) {
            "there is nothing to fall back to without a joint check"
        }
    }
}

/**
 * Every movement the game knows, as values.
 *
 * The registry is exhaustive by construction: the init block below fails loudly if an
 * [ExerciseType] is added without a descriptor, because the alternative is the failure this
 * project has already shipped twice — an exercise that looks configured and counts nothing.
 */
object Exercises {

    private val SHOULDERS = LandmarkPair(Lm.LEFT_SHOULDER, Lm.RIGHT_SHOULDER)
    private val ELBOWS = LandmarkPair(Lm.LEFT_ELBOW, Lm.RIGHT_ELBOW)
    private val WRISTS = LandmarkPair(Lm.LEFT_WRIST, Lm.RIGHT_WRIST)
    private val HIPS = LandmarkPair(Lm.LEFT_HIP, Lm.RIGHT_HIP)
    private val KNEES = LandmarkPair(Lm.LEFT_KNEE, Lm.RIGHT_KNEE)
    private val ANKLES = LandmarkPair(Lm.LEFT_ANKLE, Lm.RIGHT_ANKLE)

    /** Elbow angle at lockout. */
    const val ELBOW_TOP_DEG = 172f

    /** Elbow angle with the upper arm parallel to the floor — a standard pushup bottom. */
    const val ELBOW_BOTTOM_DEG = 82f

    /** Elbow angle at a dead hang. */
    const val HANG_TOP_DEG = 172f

    /** Elbow angle with the chin over the bar. */
    const val HANG_BOTTOM_DEG = 52f

    /** Knee angle standing. */
    const val KNEE_TOP_DEG = 172f

    /** Knee angle with the thigh parallel to the floor. */
    const val KNEE_BOTTOM_DEG = 88f

    /** Elbow at the hang of a curl, and at peak contraction. */
    const val CURL_TOP_DEG = 168f
    const val CURL_BOTTOM_DEG = 42f

    /** Elbow racked at the shoulder, and locked out overhead. */
    const val PRESS_TOP_DEG = 62f
    const val PRESS_BOTTOM_DEG = 170f

    /** Hip angle standing tall, and at the bottom of a hinge. */
    const val HIP_TOP_DEG = 175f
    const val HIP_BOTTOM_DEG = 75f

    /**
     * Shoulders descend toward fixed hands; `h` is the shrinking shoulder-to-wrist gap.
     *
     * Body travel: the head drops toward the floor *relative to the shoulders* as the chest goes
     * down, and stays put when someone waves an arm at the phone. Genuinely independent.
     */
    val PUSHUP = ExerciseDescriptor(
        type = ExerciseType.PUSHUP,
        kind = MovementKind.REP,
        normalToward = WRISTS,
        signal = RepSignal(
            proximal = SHOULDERS,
            distal = WRISTS,
            scale = ScaleReference.SHOULDER_WIDTH,
            jointCheck = JointAngleCheck(ELBOWS, SHOULDERS, WRISTS, ELBOW_TOP_DEG, ELBOW_BOTTOM_DEG),
            bodyTravel = BodyTravelCheck(
                from = BodyPoint.Midpoint(SHOULDERS),
                to = BodyPoint.Single(Lm.NOSE),
                // n̂ points at the floor, the nose moves toward the floor: already grows with depth.
                invert = false,
            ),
            crossCheck = CrossCheckPolicy.BEST_AVAILABLE,
            allowJointFallback = true,
        ),
        config = DetectorConfig(ExerciseType.PUSHUP),
        damageCoefficient = 1.00f,
        sessionVolumeScale = 1.00f,
        defaultCapacity = 8f,
        streakBar = 10,
    )

    /**
     * Hip-above-knee along the body axis, in shoulder widths — the anatomical definition of squat
     * depth, expressed directly. About +1.05 standing, 0 at parallel, negative below it.
     *
     * Body travel: nose-vs-shoulders is dead here (the neck is rigid, so it never moves and would
     * reject every rep), so the check watches the shoulders coming down toward the ankles instead.
     * That extent *shrinks* with depth, hence [BodyTravelCheck.invert].
     */
    val SQUAT = ExerciseDescriptor(
        type = ExerciseType.SQUAT,
        kind = MovementKind.REP,
        normalToward = HIPS,
        signal = RepSignal(
            proximal = HIPS,
            distal = KNEES,
            scale = ScaleReference.SHOULDER_WIDTH,
            jointCheck = JointAngleCheck(KNEES, HIPS, ANKLES, KNEE_TOP_DEG, KNEE_BOTTOM_DEG),
            bodyTravel = BodyTravelCheck(
                from = BodyPoint.Midpoint(SHOULDERS),
                to = BodyPoint.Midpoint(ANKLES),
                invert = true,
            ),
            crossCheck = CrossCheckPolicy.BEST_AVAILABLE,
            // Hip-above-knee needs the knee. Without it there is no squat signal at all, and the
            // knee angle is far too weak a proxy to invent one from.
            allowJointFallback = false,
        ),
        config = DetectorConfig(
            exercise = ExerciseType.SQUAT,
            topEnter = 15f, topExit = 30f,
            countEnter = 68f, countExit = 52f,
            deepEnter = 85f, deepExit = 78f,
            maxDescentSpeed = 450f, minAscentMs = 250, minRepPeriodMs = 900,
            maxDescentMs = 5000, maxBottomMs = 6000,
            signalMinCutoff = 1.0f, signalBeta = 18f,
            // The bottom clamp has to allow negative values — a deep squat genuinely puts the hip
            // crease under the knee, and clamping at zero would cap a full-depth user at the same
            // reading as a parallel one.
            hTopPrior = 1.05f, hBotPrior = 0.05f, rMin = 0.45f,
            topClampMin = 0.70f, topClampMax = 1.60f,
            botClampMin = -0.50f, botClampMax = 0.80f,
        ),
        damageCoefficient = 0.85f,
        sessionVolumeScale = 0.67f,
        defaultCapacity = 12f,
        streakBar = 15,
    )

    /**
     * A pull-up is a pushup with the hands above instead of below: the hands are fixed, the
     * shoulders travel toward them, and `h` is the same shrinking shoulder-to-wrist gap. The normal
     * is sign-forced toward the wrists exactly as for a pushup — it simply points up the image
     * instead of down, and the reading falls with effort either way. Nothing in the state machine
     * or the calibrator needed a special case.
     *
     * **There is deliberately no body-travel check, and the joint angle is mandatory.** A hanging
     * body is rigid below the shoulders, so every candidate is degenerate:
     *
     *  - nose-vs-shoulders never moves — the squat's dead cross-check, again;
     *  - ankles-vs-wrists is the primary signal plus a constant for a strict rep, so it can never
     *    disagree, and for anyone hanging with bent knees it drifts and rejects honest reps.
     *
     * The elbow is the one thing that genuinely moves independently, and for a hanging subject it
     * flexes almost in the image plane and through a huge range, which makes it a strong check
     * rather than the marginal one it is for a squat. So it is required: no world landmarks, no
     * strike — surfaced as [AbandonReason.INCONSISTENT] rather than silently counted as nothing.
     */
    val PULL_UP = ExerciseDescriptor(
        type = ExerciseType.PULL_UP,
        kind = MovementKind.REP,
        normalToward = WRISTS,
        signal = RepSignal(
            proximal = SHOULDERS,
            distal = WRISTS,
            scale = ScaleReference.SHOULDER_WIDTH,
            jointCheck = JointAngleCheck(ELBOWS, SHOULDERS, WRISTS, HANG_TOP_DEG, HANG_BOTTOM_DEG),
            bodyTravel = null,
            crossCheck = CrossCheckPolicy.JOINT_REQUIRED,
            allowJointFallback = true,
        ),
        config = DetectorConfig(
            exercise = ExerciseType.PULL_UP,
            topEnter = 18f, topExit = 30f,
            countEnter = 70f, countExit = 55f,
            deepEnter = 88f, deepExit = 80f,
            // A pull-up is slow and there are few of them. The rate cap matters more than for any
            // other movement because kipping is the cheat, and it is a cadence, not a shape.
            maxDescentSpeed = 400f, minAscentMs = 250, minRepPeriodMs = 1000,
            maxDescentMs = 5000, maxBottomMs = 4000,
            signalMinCutoff = 1.0f, signalBeta = 15f,
            // Dead hang puts the wrists about one and a half shoulder widths above the acromion;
            // chin-over-bar closes that to roughly a half.
            hTopPrior = 1.40f, hBotPrior = 0.50f, rMin = 0.45f,
            topClampMin = 0.80f, topClampMax = 2.20f,
            botClampMin = 0.05f, botClampMax = 1.30f,
        ),
        // A pull-up is worth roughly two and a half pushups: typical untrained maxima are about
        // twenty and seven. This coefficient is the knob to turn if a floor feels wrong — never an
        // enemy HP number, because there isn't one.
        damageCoefficient = 2.60f,
        sessionVolumeScale = 0.30f,
        defaultCapacity = 4f,
        streakBar = 5,
    )

    /**
     * A hold, not a rep. It still needs a body frame — [PlankDetector] measures alignment in the
     * same normal — so it declares [normalToward], and declares no [RepSignal] at all so that the
     * rep state machine can never be constructed for it by accident.
     */
    val PLANK = ExerciseDescriptor(
        type = ExerciseType.PLANK,
        kind = MovementKind.HOLD,
        normalToward = WRISTS,
        signal = null,
        config = DetectorConfig(ExerciseType.PLANK),
        damageCoefficient = 1.00f,
        sessionVolumeScale = 2.00f,
        defaultCapacity = 20f,
        streakBar = 60,
    )


    /**
     * A curl is the best-conditioned signal in the app and the worst-conditioned incentive.
     *
     * Best-conditioned because the movement happens in the frontal plane, perpendicular to the
     * lens — the opposite of the pushup, whose forearm swings away from it — and because the
     * elbow angle finally moves through its whole range in the image plane, so the cross-check is
     * a real second opinion rather than a formality. Normalised by shoulder width the hand travels
     * about 1.20 shoulder widths, a wider range than the pushup's 0.65, so the "small movement,
     * more jitter" worry is backwards in the units this pipeline actually uses.
     *
     * Worst-conditioned because of how it fails. A pushup degrades into a shallow pushup and a
     * squat into a quarter squat — in both cases the thing that got worse is the thing `h`
     * measures, so the gauge sees it and grades it SHALLOW. A curl degrades into hip drive, and
     * the wrist still travels from thigh to shoulder, so the primary signal reads a textbook rep.
     * The cheat is invisible in the one quantity the detector is built on. The forearm-rise travel
     * check below is the only thing watching for it, and it is a weak witness.
     *
     * Two arms at once. A scalar `h` cannot express alternating arms, and pretending otherwise
     * with a min or a max over sides would count half-reps as whole ones.
     */
    val CURL = ExerciseDescriptor(
        type = ExerciseType.CURL,
        kind = MovementKind.REP,
        // Toward the hips: the wrist pair's sign margin collapses near peak contraction, on the
        // noisiest landmark in the body, while the hips stay rigid against the shoulders all rep.
        normalToward = HIPS,
        signal = RepSignal(
            proximal = SHOULDERS,
            distal = WRISTS,
            scale = ScaleReference.SHOULDER_WIDTH,
            jointCheck = JointAngleCheck(ELBOWS, SHOULDERS, WRISTS, CURL_TOP_DEG, CURL_BOTTOM_DEG),
            // The forearm shortening against the body axis. It moves only if the elbow stays put,
            // which is exactly the thing a cheat curl stops doing.
            bodyTravel = BodyTravelCheck(BodyPoint.Midpoint(ELBOWS), BodyPoint.Midpoint(WRISTS), invert = true),
            crossCheck = CrossCheckPolicy.BEST_AVAILABLE,
            allowJointFallback = false,
        ),
        config = DetectorConfig(
            exercise = ExerciseType.CURL,
            // Tighter at the top than a pushup: full extension at the hang is a gravity-assisted
            // mechanical stop that costs nothing, and the half-rep people actually skip is the
            // bottom of the curl, not the top.
            topEnter = 15f, topExit = 28f,
            countEnter = 72f, countExit = 56f,
            deepEnter = 90f, deepExit = 82f,
            maxDescentSpeed = 380f, minAscentMs = 250, minRepPeriodMs = 900,
            maxDescentMs = 4000, maxBottomMs = 4000,
            signalMinCutoff = 1.0f, signalBeta = 16f,
            hTopPrior = 1.50f, hBotPrior = 0.30f, rMin = 0.55f,
            topClampMin = 1.10f, topClampMax = 1.90f,
            botClampMin = -0.20f, botClampMax = 0.90f,
        ),
        // A curl moves one limb's worth of load through a short path. Against a pushup, which
        // moves most of a bodyweight, the honest number is small.
        damageCoefficient = 0.35f,
        sessionVolumeScale = 0.30f,
        defaultCapacity = 12f,
        streakBar = 15,
    )

    /**
     * An overhead press, measured at the elbow rather than the wrist.
     *
     * The wrist is the highest point on the body at lockout and the first thing to leave the top of
     * the frame with a phone on the floor — a signal that vanishes at exactly the moment it counts.
     * The elbow travels the same arc and stays in shot.
     *
     * Note the orientation: in this pipeline depth 0 is the easy, re-arming end, which for a press
     * is the rack at the shoulder, and depth 100 is the lockout overhead. So the numbers here run
     * the opposite way round from every other exercise in physical space while behaving identically
     * in `h`, which is the whole point of the abstraction.
     */
    val OVERHEAD_PRESS = ExerciseDescriptor(
        type = ExerciseType.OVERHEAD_PRESS,
        kind = MovementKind.REP,
        normalToward = HIPS,
        signal = RepSignal(
            proximal = SHOULDERS,
            distal = ELBOWS,
            scale = ScaleReference.SHOULDER_WIDTH,
            jointCheck = JointAngleCheck(ELBOWS, SHOULDERS, WRISTS, PRESS_TOP_DEG, PRESS_BOTTOM_DEG),
            // The wrists rising past the shoulder line. Independent of the elbow the primary reads.
            bodyTravel = BodyTravelCheck(BodyPoint.Midpoint(SHOULDERS), BodyPoint.Midpoint(WRISTS), invert = true),
            crossCheck = CrossCheckPolicy.BEST_AVAILABLE,
            allowJointFallback = false,
        ),
        config = DetectorConfig(
            exercise = ExerciseType.OVERHEAD_PRESS,
            topEnter = 18f, topExit = 32f,
            // Higher than a pushup: the overhead lockout is a real mechanical stop, so asking for
            // it costs an honest lifter nothing.
            countEnter = 74f, countExit = 58f,
            deepEnter = 90f, deepExit = 82f,
            maxDescentSpeed = 400f, minAscentMs = 300, minRepPeriodMs = 1000,
            maxDescentMs = 4000, maxBottomMs = 4000,
            signalMinCutoff = 1.0f, signalBeta = 14f,
            hTopPrior = 0.70f, hBotPrior = -0.75f, rMin = 0.70f,
            topClampMin = 0.35f, topClampMax = 1.10f,
            botClampMin = -1.20f, botClampMax = -0.20f,
        ),
        damageCoefficient = 1.15f,
        sessionVolumeScale = 0.20f,
        defaultCapacity = 8f,
        streakBar = 10,
    )

    /**
     * A lunge is a squat whose two sides disagree on purpose.
     *
     * Same hip-above-knee signal, but combined by taking the working leg rather than averaging:
     * a trailing leg that barely bends would otherwise halve the reading and no honest rep would
     * ever reach the line.
     *
     * The honest weakness, stated because it is the cheat that defeats both the signal and its
     * cross-check at once: standing on one leg and lifting the other knee produces the same
     * hip-above-knee reading as a lunge. Pushup and squat have no equivalent.
     */
    val LUNGE = ExerciseDescriptor(
        type = ExerciseType.LUNGE,
        kind = MovementKind.REP,
        normalToward = HIPS,
        signal = RepSignal(
            proximal = HIPS,
            distal = KNEES,
            scale = ScaleReference.SHOULDER_WIDTH,
            jointCheck = JointAngleCheck(KNEES, HIPS, ANKLES, KNEE_TOP_DEG, KNEE_BOTTOM_DEG),
            bodyTravel = BodyTravelCheck(BodyPoint.Midpoint(SHOULDERS), BodyPoint.Midpoint(ANKLES), invert = true),
            sideCombiner = SideCombiner.DEEPER_SIDE,
            crossCheck = CrossCheckPolicy.BEST_AVAILABLE,
            allowJointFallback = false,
        ),
        config = DetectorConfig(
            exercise = ExerciseType.LUNGE,
            // Wider than a squat's band: the min combiner biases the top down, and a fatigued user
            // very commonly stands between reps with a slight knee bend.
            topEnter = 18f, topExit = 34f,
            // Lower than a squat's: a lunge bottom has no felt hard cue the way "parallel" does,
            // and the reading shifts with step length, so a strict line rejects honest reps.
            countEnter = 65f, countExit = 50f,
            deepEnter = 88f, deepExit = 80f,
            maxDescentSpeed = 450f, minAscentMs = 250, minRepPeriodMs = 900,
            maxDescentMs = 5000, maxBottomMs = 6000,
            signalMinCutoff = 1.0f, signalBeta = 18f,
            hTopPrior = 1.10f, hBotPrior = -0.10f, rMin = 0.50f,
            topClampMin = 0.75f, topClampMax = 1.60f,
            botClampMin = -0.60f, botClampMax = 0.70f,
        ),
        damageCoefficient = 0.95f,
        sessionVolumeScale = 0.87f,
        defaultCapacity = 12f,
        streakBar = 15,
    )

    /**
     * Bench press, filmed from the side — the only exercise here that needed the frame itself
     * rebuilt.
     *
     * Three things would have made it count exactly zero, none of them visible from the signal
     * definition. The shoulder pair projects almost onto itself from the side, so the frame's
     * scale fell under [DetectorConfig.minScale] and every frame was refused; the far shoulder's
     * confidence never cleared the core gate, so a `min` across the pair reported LOW_CONFIDENCE
     * forever; and the normal's sign, re-decided every frame, flips near the hard end where the
     * projection approaches zero — which is the strike frame. Hence [AxisSource.NEAR_SIDE_TORSO],
     * [CoreConfidence.NEAR_SIDE] and [ExerciseDescriptor.latchNormalSign].
     *
     * Even so this is the least trustworthy signal in the app, and it is marked as such: the bar
     * and plates cross the frame at wrist height and occlude the wrists at the top of every rep,
     * the bench and thigh occlude the near hip that now carries the scale, and BlazePose's weakest
     * regime is a supine subject. The maths is sound; the landmarks are not reliably there.
     */
    val BENCH_PRESS = ExerciseDescriptor(
        type = ExerciseType.BENCH_PRESS,
        kind = MovementKind.REP,
        normalToward = WRISTS,
        axisSource = AxisSource.NEAR_SIDE_TORSO,
        coreConfidence = CoreConfidence.NEAR_SIDE,
        latchNormalSign = true,
        signal = RepSignal(
            proximal = SHOULDERS,
            distal = WRISTS,
            scale = ScaleReference.SHOULDER_WIDTH,
            jointCheck = JointAngleCheck(ELBOWS, SHOULDERS, WRISTS, ELBOW_TOP_DEG, ELBOW_BOTTOM_DEG),
            // Nothing on a benched body moves independently of the arms; a travel check here would
            // be the squat's dead nose cross-check all over again.
            bodyTravel = null,
            crossCheck = CrossCheckPolicy.JOINT_REQUIRED,
            allowJointFallback = true,
        ),
        config = DetectorConfig(
            exercise = ExerciseType.BENCH_PRESS,
            topEnter = 20f, topExit = 34f,
            countEnter = 70f, countExit = 55f,
            deepEnter = 88f, deepExit = 80f,
            maxDescentSpeed = 400f, minAscentMs = 300, minRepPeriodMs = 1000,
            maxDescentMs = 5000, maxBottomMs = 4000,
            signalMinCutoff = 1.0f, signalBeta = 14f,
            hTopPrior = 1.00f, hBotPrior = 0.17f, rMin = 0.45f,
            topClampMin = 0.60f, topClampMax = 1.50f,
            botClampMin = 0.00f, botClampMax = 0.70f,
        ),
        damageCoefficient = 0.85f,
        sessionVolumeScale = 0.20f,
        defaultCapacity = 8f,
        streakBar = 10,
    )

    /**
     * A hip hinge — Romanian deadlift with a dumbbell, kettlebell or an unloaded bar.
     *
     * Deliberately not the floor barbell pull. Forty-five centimetre plates occlude the shins,
     * ankles and part of the near knee at exactly the bottom position where the detector re-arms,
     * and the thing that fails in a fatigued deadlift is the spine — where BlazePose has no
     * landmark at all between the shoulders and the hips. A rounding back produces the same trace
     * as a clean pull, so the signal is blind precisely where the risk is. The hinge keeps the
     * movement pattern and drops the part the camera cannot see.
     *
     * Filmed from the front or slightly off it. A hinge rotates about the mediolateral axis, so the
     * shoulder line stays fronto-parallel at any yaw including zero — a true side view would
     * collapse it and refuse every frame.
     */
    val HINGE = ExerciseDescriptor(
        type = ExerciseType.HINGE,
        kind = MovementKind.REP,
        normalToward = HIPS,
        signal = RepSignal(
            proximal = KNEES,
            distal = WRISTS,
            scale = ScaleReference.SHOULDER_WIDTH,
            jointCheck = JointAngleCheck(HIPS, SHOULDERS, KNEES, HIP_TOP_DEG, HIP_BOTTOM_DEG),
            bodyTravel = BodyTravelCheck(BodyPoint.Midpoint(SHOULDERS), BodyPoint.Midpoint(ANKLES), invert = false),
            crossCheck = CrossCheckPolicy.BEST_AVAILABLE,
            allowJointFallback = false,
        ),
        config = DetectorConfig(
            exercise = ExerciseType.HINGE,
            topEnter = 18f, topExit = 32f,
            countEnter = 68f, countExit = 52f,
            deepEnter = 88f, deepExit = 80f,
            maxDescentSpeed = 420f, minAscentMs = 280, minRepPeriodMs = 1000,
            maxDescentMs = 5000, maxBottomMs = 5000,
            signalMinCutoff = 1.0f, signalBeta = 16f,
            hTopPrior = 1.40f, hBotPrior = -0.35f, rMin = 0.70f,
            topClampMin = 0.90f, topClampMax = 2.00f,
            botClampMin = -0.90f, botClampMax = 0.60f,
        ),
        damageCoefficient = 1.00f,
        sessionVolumeScale = 0.25f,
        defaultCapacity = 10f,
        streakBar = 12,
    )

    val ALL: List<ExerciseDescriptor> = listOf(
        PUSHUP, SQUAT, PULL_UP, PLANK, CURL, OVERHEAD_PRESS, LUNGE, BENCH_PRESS, HINGE,
    )

    private val byType: Map<ExerciseType, ExerciseDescriptor> = ALL.associateBy { it.type }

    init {
        val missing = ExerciseType.entries.filterNot { byType.containsKey(it) }
        require(missing.isEmpty()) {
            "no ExerciseDescriptor for $missing — it would be selectable and count nothing"
        }
        require(byType.size == ALL.size) { "duplicate descriptor in Exercises.ALL" }
    }

    fun of(type: ExerciseType): ExerciseDescriptor = byType.getValue(type)
}
