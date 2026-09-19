package com.pushuprpg.core.detect

import com.pushuprpg.core.pose.PoseFrame

/**
 * One frame's verdict from the router: which exercise the body is doing, and that detector's tick.
 *
 * [switched] is true on the frame the answer changed, which is the moment the UI should say so.
 */
data class RoutedTick(
    val exercise: ExerciseType,
    val tick: PoseTick,
    val committed: Boolean,
    val switched: Boolean,
)

/**
 * Works out which exercise is happening by running all of them, rather than by classifying the pose.
 *
 * The obvious design is a posture classifier: measure the body's shape, decide it is a pushup, then
 * load the pushup detector. Four independent attempts at that were designed and all four were
 * refuted on the same ground. Every candidate feature is a ratio of *projected* lengths, and a
 * projection depends on where the camera is — a segment at pitch α seen from elevation φ images as
 * cos(φ − α). The app supports the phone on the floor, propped at waist height, and anywhere
 * between, so φ is unknown and unobservable, and a threshold tuned at chest height is wrong on the
 * floor. Worse, a classifier's failure is silent: the wrong descriptor measures the wrong landmarks
 * against the wrong priors, the gauge sits still, and nothing reports an error. This project has
 * shipped that exact failure twice.
 *
 * So the premise is inverted. **Nothing about the pose is allowed to choose an exercise.** Every
 * candidate detector runs concurrently on the same frame, and the exercise is whichever one
 * actually produces a rep — a Strike that has already passed that descriptor's own depth range,
 * anti-cheat timing and cross-checks. Confirmation, not classification.
 *
 * This is cheap in the way that matters. The expensive work is the pose inference, which happens
 * once per frame upstream and is shared; a detector is a filter and a small state machine over 33
 * points, and nine of them cost far less than the model that feeds them.
 *
 * It also makes a superset work with no extra machinery. Alternating pull-ups and pushups simply
 * moves which detector is striking, and because calibration profiles are stored per exercise
 * already, switching restores a range that is warm rather than destroying one that was learned.
 */
class ExerciseRouter(
    candidates: List<ExerciseType> = ExerciseType.entries.toList(),
    /** One calibration per exercise, as the app already stores them. */
    profiles: Map<ExerciseType, UserProfile> = emptyMap(),
    /** The one to report before anybody has done anything yet. */
    private val initial: ExerciseType = ExerciseType.PUSHUP,
) : RepDetector {
    init {
        require(candidates.isNotEmpty()) { "a router with no candidates can never answer" }
        require(initial in candidates) { "the initial exercise must be one of the candidates" }
    }

    private val detectors: Map<ExerciseType, RepDetector> =
        candidates.associateWith {
            DetectorFactory.create(it, profile = profiles[it] ?: UserProfile.empty())
        }

    private var active: ExerciseType = initial
    private var committed = false

    /** Strike timestamps per exercise, pruned to [EVIDENCE_WINDOW_MS]. */
    private val recent = mutableMapOf<ExerciseType, ArrayDeque<Long>>()

    /** Unbroken hold time per hold exercise, as its own detector reports it. */
    private val held = mutableMapOf<ExerciseType, Long>()

    /** The exercise currently being credited. */
    val exercise: ExerciseType get() = active

    /** Every detector, so a caller can persist all their calibrations at the end of a session. */
    val all: Map<ExerciseType, RepDetector> get() = detectors

    /**
     * The mode the *active* detector draws in. The others are held at [SkeletonMode.OFF].
     *
     * Only one skeleton is ever shown, so building nine is nine allocations a frame — 270 a second
     * at 30fps — thrown away immediately. The cost of switching is that the frame a changeover lands
     * on carries the new detector's skeleton from before it was asked to build one, which is one
     * frame of no overlay at 33ms.
     */
    override var skeletonMode: SkeletonMode = SkeletonMode.FULL
        set(value) {
            field = value
            applySkeletonModes()
        }

    private fun applySkeletonModes() {
        detectors.forEach { (type, detector) ->
            detector.skeletonMode = if (type == active) skeletonMode else SkeletonMode.OFF
        }
    }

    init {
        applySkeletonModes()
    }

    /** The full verdict for the last frame, for a UI that wants to say which exercise it settled on. */
    var lastRouted: RoutedTick? = null
        private set

    // --- RepDetector, so the router drops in wherever a single detector used to go. Everything
    // below delegates to whichever exercise is currently being credited.

    override val config: DetectorConfig get() = detectors.getValue(active).config

    override fun snapshotCalibration(): CalibrationSnapshot =
        detectors.getValue(active).snapshotCalibration()

    override fun restoreCalibration(snapshot: CalibrationSnapshot) {
        detectors.getValue(active).restoreCalibration(snapshot)
    }

    override fun sessionSummary(): SessionSummary = detectors.getValue(active).sessionSummary()

    override fun updatedProfile(previous: UserProfile): UserProfile =
        detectors.getValue(active).updatedProfile(previous)

    override fun onFrame(frame: PoseFrame): PoseTick = route(frame).tick

    fun route(frame: PoseFrame): RoutedTick {
        // Every detector sees every frame. One that cannot read this framing — the phone on the
        // floor for a squat, a side-on descriptor when the shoulders are square to the lens —
        // simply never reaches OK quality and never strikes, so it eliminates itself without
        // anyone having to model the camera.
        val ticks = detectors.mapValues { (_, detector) -> detector.onFrame(frame) }
        val now = frame.timestampMs

        ticks.forEach { (type, tick) ->
            // A hold produces no strikes, so it cannot enter a race decided by them. Its evidence is
            // time: see [HOLD_COMMIT_MS].
            if (Exercises.of(type).kind != MovementKind.REP) {
                tick.events.filterIsInstance<RepEvent.HoldTick>().lastOrNull()?.let {
                    held[type] = it.holdMs
                }
                if (tick.events.any { it is RepEvent.HoldBroken }) held[type] = 0L
                return@forEach
            }
            if (tick.events.any { it is RepEvent.Strike }) {
                recent.getOrPut(type) { ArrayDeque() }.addLast(now)
            }
        }
        recent.values.forEach { times ->
            while (times.isNotEmpty() && now - times.first() > EVIDENCE_WINDOW_MS) times.removeFirst()
        }

        // Decided over a window rather than per frame. Two descriptors that both fit a movement do
        // not strike on the same frames — a pushup and a pull-up share their signal exactly, so
        // both fire, a few frames apart — and a per-frame rule just hands the set to whichever
        // struck last, flipping all the way through.
        val evidence = recent.filterValues { it.isNotEmpty() }
        val winner = evidence.keys.maxWithOrNull(
            compareBy<ExerciseType> { witnesses(it) }
                .thenBy { evidence.getValue(it).size }
                // The incumbent takes ties, so a set that genuinely fits two descriptions equally
                // well stays where it is instead of oscillating.
                .thenBy { if (it == active) 1 else 0 }
        )

        // A hold wins only when nothing is repping. The two are not symmetric: a rep set's top
        // position *is* a plank, so a plank detector ticks through every set — while a plank
        // genuinely held produces no strikes at all from anybody. Rep evidence therefore always
        // outranks hold time, and hold time is only consulted in its absence.
        val holding = if (evidence.isEmpty()) {
            held.entries.filter { it.value >= HOLD_COMMIT_MS }.maxByOrNull { it.value }?.key
        } else null

        val chosen = winner ?: holding
        val switched = chosen != null && (chosen != active || !committed)
        if (chosen != null) {
            active = chosen
            committed = true
            if (switched) applySkeletonModes()
        }

        return RoutedTick(
            exercise = active,
            tick = ticks.getValue(active),
            committed = committed,
            switched = switched,
        ).also { lastRouted = it }
    }

    /**
     * How many independent witnesses this exercise demanded before it would strike.
     *
     * A pushup and a pull-up share their primary signal exactly — `h` is the same shrinking
     * shoulder-to-wrist gap, which is why a pull-up needed no new state machine — so on a pushup
     * both detectors strike and something has to break the tie without new geometry.
     *
     * The asymmetry is already in the descriptors. A pushup additionally requires the head to drop
     * relative to the shoulders; a hanging body is rigid, so a pull-up cannot ask for that and does
     * not. A pull-up trace therefore fails the pushup's extra test and only one detector strikes,
     * while a pushup trace passes both — and the honest reading of that is not "it might be a
     * pull-up" but "the pushup explains more of what was observed".
     *
     * Counting witnesses, not strictness: JOINT_REQUIRED makes the joint check mandatory rather
     * than optional, which is a stronger demand on the same evidence, not a second opinion.
     */
    private fun witnesses(type: ExerciseType): Int {
        val signal = Exercises.of(type).signal ?: return 0
        var count = 0
        if (signal.bodyTravel != null) count++
        if (signal.jointCheck != null) count++
        return count
    }

    override fun reset() {
        detectors.values.forEach { it.reset() }
        lastRouted = null
        recent.clear()
        held.clear()
        active = initial
        committed = false
        applySkeletonModes()
    }

    private companion object {
        /**
         * How long a rep counts as evidence for its exercise.
         *
         * Long enough that a slow set of heavy reps keeps its identity between reps; short enough
         * that a superset moves within one changeover.
         */
        const val EVIDENCE_WINDOW_MS = 20_000L

        /**
         * How long a hold must be unbroken before it is believed.
         *
         * Far above the two or three seconds someone spends settling into position before their
         * first pushup, because that pause is a plank by every measure the detector has and a router
         * that believed it would announce 플랭크 at the start of every set. Well below a real plank,
         * which in this game is held for tens of seconds — and the hold time resets the moment the
         * body leaves the line, so nothing accumulates across a set of reps.
         */
        const val HOLD_COMMIT_MS = 12_000L
    }

    /**
     * Folds every detector's calibration into the profile.
     *
     * All of them, not just the winner, and each against its own previous profile: a detector only
     * teaches its calibrator from a *completed* rep, so one that never counted anything has nothing
     * to contribute and contributes nothing. Keeping them separate is what lets a superset switch
     * back to a range that is already warm.
     */
    fun updatedProfiles(previous: Map<ExerciseType, UserProfile>): Map<ExerciseType, UserProfile> =
        detectors.mapValues { (type, detector) ->
            detector.updatedProfile(previous[type] ?: UserProfile.empty())
        }
}
