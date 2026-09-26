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
     * How this movement is read when the camera sees it side on, where the shoulder line it is
     * otherwise read across collapses; null when it has no side view. See [SideView].
     */
    val sideView: SideView? = null,
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
     * set. The UI says so rather than pretending otherwise. True only for a movement a recording in
     * RealTraceTest replays and counts, which that test pins.
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
     * Anchored on a trained pushup session of ~150 reps: pull-up 45, dip 45, squat 100, lunge 130.
     * A hold is in seconds, not reps.
     */
    val sessionVolumeScale: Float,
    /** Starting capacity for a user who has never done this movement — reps, or seconds for a hold. */
    val defaultCapacity: Float,
    /** Reps (or seconds, for a hold) in one day that keep a streak alive. */
    val streakBar: Int,
) {
    /**
     * Every landmark this movement reads: the signal pair, the joint check's three points and the
     * travel witness's endpoints. The nose is left out — it is only ever a witness, and a witness
     * that cannot be seen is skipped rather than required.
     *
     * Two things are derived from this rather than listed by hand: which bones the minimal overlay
     * draws, and which body parts the user is told are out of shot. Both used to be "the arms",
     * fixed, which showed someone doing lunges a skeleton of their arms.
     */
    val watchedLandmarks: Set<Int> by lazy {
        val s = signal ?: return@lazy emptySet()
        val out = HashSet<Int>()
        fun add(p: LandmarkPair) { out += p.left; out += p.right }
        fun add(p: BodyPoint) {
            when (p) {
                is BodyPoint.Single -> out += p.index
                is BodyPoint.Midpoint -> add(p.pair)
            }
        }
        add(s.proximal); add(s.distal)
        s.jointCheck?.let { add(it.vertex); add(it.proximal); add(it.distal) }
        s.bodyTravel?.let { add(it.from); add(it.to) }
        out -= Lm.NOSE
        out
    }

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
        require(sideView == null || (kind == MovementKind.REP && axisSource == AxisSource.SHOULDER_PAIR)) {
            "$type has a side view, which only a movement read across the shoulder line needs"
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
    /**
     * Whether the angle keeps closing to the bottom of the movement, so that a fully bent joint
     * means a full rep. True for a lunge's knee, a dip's elbow and a pull-up's: on the rig they read
     * 60-70 at a half rep and 95-99 at a full one. False for a pushup's elbow and a squat's knee,
     * which reach their bottom angle by half depth (92 and 88 at a half rep) and cannot tell a half
     * rep from a whole one. Only a joint that can is trusted to move the bottom of the range.
     */
    val confirmsFullDepth: Boolean = false,
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
    /**
     * How far the witness must travel, as a fraction of the calibrated range, before a rep counts.
     *
     * Per-movement because the witness is not geared the same way in each. A dip's elbow is geared about 1:1
     * with it, and the reference is taken on the last frame of the top band rather than at lockout,
     * so the witness only ever sees the middle of the rep — against 0.30 an honest dip measured
     * 0.190 where 0.195 was required and every rep was refused, 0 of 8.
     *
     * Null keeps [RepDetectorImpl.MIN_BODY_DROP_FRACTION]. The pushup was once described here as
     * clearing that comfortably; that was a hand-placed fixture, and on a projected body it did
     * not — see [Exercises.PUSHUP].
     */
    val minFraction: Float? = null,
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
 * both hallucinated far landmarks out of the coordinate frame and out of the divisor. Scaled to
 * shoulder-width units like [TORSO]. No descriptor declares it; a [SideView] is read in it.
 */
enum class AxisSource {
    SHOULDER_PAIR,
    NEAR_SIDE_TORSO,

    /**
     * The spine itself, for a movement done upright — a pull-up, a dip. Depth is read ALONG the
     * line from the shoulders to the hips, and scaled by its length, rather than across a shoulder
     * line: the arms of a hanging or supported body move along the spine from every side, and the
     * spine is its full length in the picture from every side, where the shoulder line is not.
     * Scaled to shoulder-width units ([TORSO_TO_SHOULDER_WIDTH]) so a descriptor's priors read the
     * same as before.
     *
     * Found on the rig: at the three metres a pull-up needs to fit the bar and the feet, a shoulder
     * line square to the lens is barely over the detector's minimum scale; turned 45 degrees it
     * falls under it, and side on it is gone — "not counted from the front, the side or the back".
     */
    TORSO,
}

/** Shoulder width over torso length on an adult; what [AxisSource.TORSO] scales by. */
const val TORSO_TO_SHOULDER_WIDTH = 0.8f

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

/**
 * The second way to read a movement that is read across the shoulder line: side on, where that line
 * projects onto itself and the frame it defines has no scale.
 *
 * `BodyFrameTracker` switches to it when the shoulders are narrow against the torso, and back when
 * they open again, each only once the new view has held for a second — like a subject switch, and
 * for the same reason: filmed from the head, the lite model collapses the shoulder line for single
 * frames. A switch starts the range over and makes the rep arm again, because `h` side on is not
 * `h` from the head. The first view is taken as it is seen.
 *
 * Side on, three things change, all measured on the rig:
 *  - **The frame** is the torso of the side the camera sees better ([AxisSource.NEAR_SIDE_TORSO]):
 *    side on it lies in the picture at its full length, as the shoulder line does from the head.
 *  - **The reading** is how far apart [RepSignal.proximal] and [RepSignal.distal] are in the picture,
 *    not their separation along the normal. The movement is in the picture's own plane, and the
 *    normal to a torso that tilts through the rep tilts with it: along it, a knee pushup from the
 *    floor 70 degrees off the head read 22% of its range 40% of the way down. The distance reads
 *    35-42% there and from every other side-on placement.
 *  - **The witness** is the shoulders themselves. The head rides in line with the body, so the nose
 *    never moves across the torso, and the head-on witness would refuse every rep. What does move is
 *    the whole body against the floor — the shoulders come down to hands that stay put — and the
 *    phone, standing still, sees that directly. Arms waved at the lens move the hands and leave the
 *    shoulders where they were.
 *
 * The range is anchored wherever the body rests in this view, even close to the prior: the prior is
 * the head-on one. See [RangeCalibrator.observeRest].
 */
data class SideView(
    /**
     * How far the shoulders must have come down in the picture since the top, toward the hands, as
     * a fraction of the calibrated range, before a rep counts. By the count line they have come
     * 0.64-0.86 of it from every side-on placement on the rig, and none under a wave or a hang.
     */
    val minShoulderTravel: Float = 0.30f,
)

/**
 * The feet one in front of the other, measured in the model's 3-D skeleton: the ankles' distance
 * along the way the body faces, in metres.
 *
 * What makes a lunge a lunge rather than a squat, and the one thing the depth signal cannot see:
 * hips over knees reads the same whether the feet are split or side by side, so a squat counted as
 * a lunge — on the rig, six of six. A split squat's feet are 0.6-0.8 m apart front to back; a squat's
 * are side by side, a few centimetres at most.
 *
 * The same measurement says which leg is in front, which is how the game asks the user to swap.
 */
data class StanceCheck(val minStaggerM: Float = 0.30f)

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
    /** For a split-stance movement: how far apart front to back the feet must be. See [StanceCheck]. */
    val stance: StanceCheck? = null,
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
    /** Elbow locked out at the top of a dip; about 85 degrees at a bar-height bottom. */
    const val DIP_TOP_DEG = 172f
    const val DIP_BOTTOM_DEG = 85f

    const val HANG_TOP_DEG = 172f

    /** Elbow angle with the chin over the bar. */
    const val HANG_BOTTOM_DEG = 52f

    /** Knee angle standing. */
    const val KNEE_TOP_DEG = 172f

    /** Knee angle with the thigh parallel to the floor. */
    const val KNEE_BOTTOM_DEG = 88f

    /**
     * Shoulders descend toward fixed hands; `h` is the shrinking shoulder-to-wrist gap.
     *
     * Body travel: the head drops toward the floor *relative to the shoulders* as the chest goes
     * down, and stays put when someone waves an arm at the phone. Genuinely independent.
     *
     * Filmed from the side the shoulder line collapses, and the pushup is read in its [SideView]
     * instead: the shoulder-to-wrist distance along the torso's own frame, witnessed by the
     * shoulders coming down in the picture.
     */
    val PUSHUP = ExerciseDescriptor(
        type = ExerciseType.PUSHUP,
        kind = MovementKind.REP,
        sideView = SideView(),
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
                // Measured on the rig, not assumed. With the head held in line with the body —
                // which is how a pushup is taught — the nose travels 0.23-0.53 of the range by the
                // count line, depending on where the phone is; 0.30 refused every rep after the
                // first from two metres away, and from 15 degrees off-axis at any distance. The
                // fakes this exists for, arms waved at the lens and a body hanging from a bar, put
                // the nose nowhere relative to the shoulders at all.
                minFraction = 0.15f,
            ),
            crossCheck = CrossCheckPolicy.BEST_AVAILABLE,
            allowJointFallback = true,
        ),
        config = DetectorConfig(ExerciseType.PUSHUP),
        validatedOnDevice = true,
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
        // Read along the spine: counts from the front, the side, the back and between.
        axisSource = AxisSource.TORSO,
        coreConfidence = CoreConfidence.NEAR_SIDE,
        normalToward = WRISTS,
        signal = RepSignal(
            proximal = SHOULDERS,
            distal = WRISTS,
            scale = ScaleReference.SHOULDER_WIDTH,
            jointCheck = JointAngleCheck(ELBOWS, SHOULDERS, WRISTS, HANG_TOP_DEG, HANG_BOTTOM_DEG, confirmsFullDepth = true),
            bodyTravel = null,
            crossCheck = CrossCheckPolicy.JOINT_REQUIRED,
            allowJointFallback = true,
        ),
        config = DetectorConfig(
            exercise = ExerciseType.PULL_UP,
            // The phone is far away to fit the bar and the feet; the body is small in the picture.
            minScale = 0.05f,
            topEnter = 18f, topExit = 30f,
            countEnter = 70f, countExit = 55f,
            deepEnter = 88f, deepExit = 80f,
            // The rate cap matters more than for any other movement because kipping is the cheat,
            // and it is a cadence, not a shape — minRepPeriodMs holds that. The speed through the
            // band cannot: filmed from behind, the wrists on the bar are lost as the head comes up
            // to it and read as falling, and the pulls of a set recorded on a phone crossed the
            // band in 41-101 ms. At 400 that set counted none to two of its four armable pulls.
            maxDescentSpeed = 900f, minAscentMs = 250, minRepPeriodMs = 1000,
            maxDescentMs = 5000, maxBottomMs = 4000,
            signalMinCutoff = 1.0f, signalBeta = 15f,
            // Dead hang puts the wrists about one and a half shoulder widths above the acromion;
            // chin-over-bar closes that to roughly a half.
            hTopPrior = 1.40f, hBotPrior = 0.50f, rMin = 0.45f,
            topClampMin = 0.80f, topClampMax = 2.20f,
            botClampMin = 0.05f, botClampMax = 1.30f,
        ),
        validatedOnDevice = true,
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
        validatedOnDevice = true,
        damageCoefficient = 1.00f,
        sessionVolumeScale = 2.00f,
        defaultCapacity = 20f,
        streakBar = 60,
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
        // Read along the spine, like a pull-up: a lunge is filmed from the side or at an angle at
        // least as often as from the front, and across the shoulder line a body turned that far is
        // too narrow to measure — a real set filmed at an angle read LOW_CONFIDENCE on 161 frames
        // of 165 and counted nothing.
        axisSource = AxisSource.TORSO,
        coreConfidence = CoreConfidence.NEAR_SIDE,
        normalToward = HIPS,
        signal = RepSignal(
            proximal = HIPS,
            distal = KNEES,
            scale = ScaleReference.SHOULDER_WIDTH,
            jointCheck = JointAngleCheck(KNEES, HIPS, ANKLES, KNEE_TOP_DEG, KNEE_BOTTOM_DEG, confirmsFullDepth = true),
            bodyTravel = BodyTravelCheck(BodyPoint.Midpoint(SHOULDERS), BodyPoint.Midpoint(ANKLES), invert = true),
            sideCombiner = SideCombiner.DEEPER_SIDE,
            crossCheck = CrossCheckPolicy.BEST_AVAILABLE,
            allowJointFallback = false,
            stance = StanceCheck(),
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
            // Pacing is minRepPeriodMs's job. Read along the spine, the rig's brisk 1-second lunge
            // crossed the band too fast for a cap of 450 at 12 fps and counted none of ten.
            maxDescentSpeed = 600f, minAscentMs = 250, minRepPeriodMs = 900,
            maxDescentMs = 5000, maxBottomMs = 6000,
            signalMinCutoff = 1.0f, signalBeta = 18f,
            // Thigh over most of the spine. The rig stands at 0.9-1.2 depending on where the phone
            // is, but a person filmed on a phone stood at 0.76-0.90: the model puts the hips lower
            // and the shoulders higher than the rig's joints, so its spine is longer against the
            // leg. At 1.10 that person read 17 on the gauge standing, drifted past the top band
            // before the wait to arm was over, and the first lunge never counted. At 0.95 the rig,
            // standing 14 above the top, crossed the band in a frame at 12 fps and was refused as
            // too fast. 1.00 serves both.
            hTopPrior = 1.00f, hBotPrior = -0.10f, rMin = 0.50f,
            topClampMin = 0.60f, topClampMax = 1.60f,
            botClampMin = -0.60f, botClampMax = 0.70f,
        ),
        validatedOnDevice = true,
        damageCoefficient = 0.95f,
        sessionVolumeScale = 0.87f,
        defaultCapacity = 12f,
        streakBar = 15,
    )
    /**
     * A dip is a pull-up's mirror: the hands are fixed and the body travels PAST them rather than
     * toward them. At lockout the acromion sits an arm's length above the wrists; at the bottom it
     * is a hand's width above. So `h` is the same shrinking shoulder-to-wrist gap in the same pair
     * order, and it falls with effort with no swap — measured monotone across a rep with no sign
     * change, 1.505 at lockout down to 0.845 at an 85-degree bottom.
     *
     * **The normal points at the hips, not the wrists**, which is the one real decision here. Both
     * agree at lockout, but as the body sinks the wrist projection closes to about 0.85 of a width
     * while the hips keep a steady 1.3 — and a normal anchored on the closer pair is a normal whose
     * sign is decided by the noisiest landmark in the body on the frame the strike fires. The lean
     * of a dip rotates about the mediolateral axis, so the shoulder line stays fronto-parallel and
     * the hips stay squarely below it.
     *
     * **The elbow angle is the witness, and it is required.** There used to be a travel check on
     * the elbow as well — it rises relative to the shoulder line as the body sinks past the bar,
     * which is what separates a dip from a standing curl doing the same thing to the same landmarks.
     * It is a weak witness in the best case, geared well under 1:1 with the primary, and from a
     * phone on the floor it reverses: the elbow swings BACKWARD as it rises, and from below a point
     * moving away from the lens drops in the image faster than its rise lifts it. Measured on a
     * projected 3-D body: −0.71 at lockout to −0.80 at the bottom from the floor, the wrong way, 0
     * of 8. Since the movement is now declared on the way into the run rather than inferred, the
     * curl it defended against is no longer a race the detector has to win; and the elbow angle —
     * 172 to 85 degrees, in the frontal plane, from world landmarks — refuses a wave from any
     * camera position, which is the fake that matters.
     *
     * Bar dips only. Bench dips put the hands behind the hips, inside the body silhouette from the
     * front, and filming them from the side collapses the shoulder axis into the near-side-torso
     * territory, which this game does not film. That would be a second descriptor, not this one.
     *
     * Camera: level with the bar, front on. Placed on the floor a forward-leaning dip loses about a
     * quarter of its range to parallax and counted 0 of 6.
     */
    val DIP = ExerciseDescriptor(
        type = ExerciseType.DIP,
        kind = MovementKind.REP,
        // Read along the spine: counts from the front, the side, the back and between.
        axisSource = AxisSource.TORSO,
        coreConfidence = CoreConfidence.NEAR_SIDE,
        normalToward = HIPS,
        signal = RepSignal(
            proximal = SHOULDERS,
            distal = WRISTS,
            scale = ScaleReference.SHOULDER_WIDTH,
            jointCheck = JointAngleCheck(ELBOWS, SHOULDERS, WRISTS, DIP_TOP_DEG, DIP_BOTTOM_DEG, confirmsFullDepth = true),
            bodyTravel = null,
            crossCheck = CrossCheckPolicy.JOINT_REQUIRED,
            allowJointFallback = true,
        ),
        config = DetectorConfig(
            exercise = ExerciseType.DIP,
            // The phone is far away to fit the bar and the feet; the body is small in the picture.
            minScale = 0.05f,
            topEnter = 18f, topExit = 30f,
            countEnter = 70f, countExit = 55f,
            deepEnter = 88f, deepExit = 80f,
            // Pacing is minRepPeriodMs's job. Filmed from the front on a phone, honest dips crossed
            // the band at 470-500 points a second on the range the prior gives before any rep has
            // taught it — twice their speed on their own range — and at 420 the first were refused.
            maxDescentSpeed = 600f, minAscentMs = 250, minRepPeriodMs = 900,
            maxDescentMs = 5000, maxBottomMs = 4000,
            signalMinCutoff = 1.0f, signalBeta = 15f,
            // h at lockout IS arm length over shoulder width, so the prior has to span real builds:
            // 1.28 for broad shoulders and short arms, 1.80 for a lanky one. Seated mid-population
            // at 1.50 a broad build counted 0 of 6 with no events at all — a silent zero.
            hTopPrior = 1.35f, hBotPrior = 0.90f, rMin = 0.40f,
            topClampMin = 0.80f, topClampMax = 2.20f,
            botClampMin = 0.30f, botClampMax = 1.60f,
        ),
        validatedOnDevice = true,
        // Between a pushup and a pull-up, nearer the pull-up: a dip is near-max for most people but
        // the hands carry less than a full hang.
        damageCoefficient = 1.90f,
        sessionVolumeScale = 0.30f,
        defaultCapacity = 6f,
        streakBar = 8,
    )

    val ALL: List<ExerciseDescriptor> = listOf(
        PUSHUP, SQUAT, PULL_UP, PLANK, LUNGE, DIP,
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
