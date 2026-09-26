package com.pushuprpg.core.detect

import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.math.hypot

/**
 * What to tell the user about where the phone is and where they are, in the order worth fixing.
 *
 * Every one of these is something the user can *do*: move, turn, move the phone. The detector's
 * own [PoseQuality] says why it is not counting; this says what to change so that it will.
 */
enum class PlacementAdvice {
    /** Nobody in the picture. */
    STEP_INTO_VIEW,
    /** The body is too small in the picture for the detector to measure. */
    COME_CLOSER,
    /** The body overflows the picture, or is too big in it. */
    MOVE_PHONE_BACK,
    /** Something the movement needs is below the bottom edge — feet, knees, hands on the floor. */
    SHOW_BELOW,
    /** Something the movement needs is above the top edge — the head, hands on a bar. */
    SHOW_ABOVE,
    /** Cut off at a side. */
    CENTER,
    /** Side on, or turned away: the shoulders overlap and there is no frame to measure in. */
    FACE_CAMERA,
    /** Everything is in the picture and the right size, and the tracker still cannot see it well. */
    CLEARER,
    HOLD_PHONE_STILL,
    /**
     * The detector is re-reading the user after a big change — another subject, or a pushup turned
     * between the head and the side; nothing to do but wait.
     */
    SETTLING,
    SLOW_DOWN,
    /** Seen well, but not in the starting position yet. */
    GET_IN_POSITION,
    /** Seen, measured and armed. Said once, briefly, then the coach goes quiet. */
    READY,
}

/**
 * What the coach is saying right now: [advice] is null when there is nothing to say. [offFrame] is
 * the landmarks it is talking about, for a line that names the body part.
 */
data class Placement(
    val advice: PlacementAdvice? = null,
    val offFrame: List<Int> = emptyList(),
)

/**
 * Talks the user into a placement that counts, live, from the camera — so the exercise picker
 * does not have to explain every placement in a paragraph nobody reads with the phone in their
 * hand.
 *
 * It agrees with the detector by construction: it never says [PlacementAdvice.READY] unless the
 * detector's own [PoseQuality] is OK and it has armed, and every other piece of advice is
 * geometry measured on the same frame the detector refused. A coach with its own idea of "good"
 * would say 좋아요 over a set that counts nothing — the failure this exists to end.
 *
 * The geometry, measured on the projected rig body in the core tests rather than assumed:
 * - The body frame is the shoulder pair. Side on, it collapses — a pushup from the side projected
 *   shoulders 0.04 of the image height apart against a torso of 0.21, under the detector's minimum
 *   scale. A shoulder width under [SIDE_ON_RATIO] of the torso is that, not distance, and for a
 *   movement read only across it moving closer would not fix it; turning would. A movement with a
 *   [SideView] — the pushup — is read along the torso there instead, so it is never told to turn:
 *   side on, its size is the torso's and the hips are among what it needs in the picture.
 * - Too far is the same minimum scale with the shoulders square to the lens.
 * - A needed landmark off an edge is named by the edge it left through, because "move the phone
 *   back" and "tilt it down" fix different edges.
 *
 * Advice changes only after the new advice has held for [SWITCH_MS], so a body on a threshold
 * does not flick between two sentences. Time is the frame's timestamp, like everything in `:core`.
 */
class PlacementCoach(
    private val exercise: ExerciseType,
    private val config: DetectorConfig = Exercises.of(exercise).config,
) {
    private val required: List<Int> = requiredLandmarks(exercise)

    /**
     * Whether turning side on can stop this movement counting. Only for one read across the
     * shoulder line with no side view; a pull-up, a dip and a plank are read along the spine or in
     * 3-D, and a pushup has a [SideView], so all of them count from the side and telling their
     * users to turn would be telling them something false.
     */
    private val sideOnMatters: Boolean = Exercises.of(exercise).let {
        it.kind == MovementKind.REP && it.axisSource == AxisSource.SHOULDER_PAIR && it.sideView == null
    }

    /** Whether side on this movement is read along the torso, which then has to be in the picture. */
    private val hasSideView: Boolean = Exercises.of(exercise).sideView != null

    private var shown: Placement = Placement()
    private var shownSinceMs = Long.MIN_VALUE
    private var candidate: PlacementAdvice? = null
    private var candidateSinceMs = Long.MIN_VALUE
    private var armedOnce = false

    /** Folds in one frame and the detector's verdict on it. */
    fun update(frame: PoseFrame, tick: PoseTick): Placement {
        val t = frame.timestampMs
        val (raw, off) = judge(frame, tick)

        // The starting-position nudge is for getting going. Once the user has been armed, standing
        // up to rest is resting, and the coach does not nag about it.
        if (raw == PlacementAdvice.READY) armedOnce = true
        val advice = if (raw == PlacementAdvice.GET_IN_POSITION && armedOnce) null else raw

        if (advice != candidate) {
            candidate = advice
            candidateSinceMs = t
        }
        val settled = shownSinceMs == Long.MIN_VALUE || t - candidateSinceMs >= SWITCH_MS
        if (candidate == shown.advice) {
            // Same advice: keep it, but let the parts it names follow the body.
            shown = shown.copy(offFrame = off)
        } else if (settled) {
            shown = Placement(candidate, off)
            shownSinceMs = t
        }

        // Ready is a moment, not a state: said, then gone.
        if (shown.advice == PlacementAdvice.READY && t - shownSinceMs >= READY_SHOW_MS) {
            return Placement()
        }
        return shown
    }

    fun reset() {
        shown = Placement()
        shownSinceMs = Long.MIN_VALUE
        candidate = null
        candidateSinceMs = Long.MIN_VALUE
        armedOnce = false
    }

    private fun judge(frame: PoseFrame, tick: PoseTick): Pair<PlacementAdvice, List<Int>> {
        if (!frame.hasPose) return PlacementAdvice.STEP_INTO_VIEW to emptyList()
        when (tick.quality) {
            PoseQuality.UNSTABLE_CAMERA -> return PlacementAdvice.HOLD_PHONE_STILL to emptyList()
            PoseQuality.SUBJECT_SWITCH -> return PlacementAdvice.SETTLING to emptyList()
            PoseQuality.IMPLAUSIBLE_RATE -> return PlacementAdvice.SLOW_DOWN to emptyList()
            else -> Unit
        }

        val shoulderWidth = hypot(
            frame.u(Lm.LEFT_SHOULDER) - frame.u(Lm.RIGHT_SHOULDER),
            frame.v(Lm.LEFT_SHOULDER) - frame.v(Lm.RIGHT_SHOULDER),
        )
        val torso = hypot(
            (frame.u(Lm.LEFT_SHOULDER) + frame.u(Lm.RIGHT_SHOULDER) - frame.u(Lm.LEFT_HIP) - frame.u(Lm.RIGHT_HIP)) / 2f,
            (frame.v(Lm.LEFT_SHOULDER) + frame.v(Lm.RIGHT_SHOULDER) - frame.v(Lm.LEFT_HIP) - frame.v(Lm.RIGHT_HIP)) / 2f,
        )
        // Side on, a movement with a side view is measured along the torso: its size is the
        // torso's, in shoulder widths as the detector takes it, and it needs the hips too.
        val alongTorso = hasSideView && torso > 0f && shoulderWidth < SIDE_ON_RATIO * torso
        val size = if (alongTorso) torso * TORSO_TO_SHOULDER_WIDTH else shoulderWidth

        val below = ArrayList<Int>()
        val above = ArrayList<Int>()
        val side = ArrayList<Int>()
        for (i in if (alongTorso) required + SIDE_VIEW_LANDMARKS else required) {
            val lm = frame[i]
            // Off the edge outright, as the model extrapolates a point it cannot see — or pressed
            // against an edge with no confidence, as it also does.
            val faint = lm.hasModelConfidence && lm.modelConfidence < config.minCoreConfidence
            when {
                lm.y > 1f || (faint && lm.y > 1f - EDGE) -> below += i
                lm.y < 0f || (faint && lm.y < EDGE) -> above += i
                lm.x < 0f || lm.x > 1f || (faint && (lm.x < EDGE || lm.x > 1f - EDGE)) -> side += i
            }
        }
        val off = (below + above + side).sorted()
        val counting = tick.quality == PoseQuality.OK

        val advice = when {
            // Overflowing in two directions at once is one problem: the phone is too close.
            below.isNotEmpty() && above.isNotEmpty() -> PlacementAdvice.MOVE_PHONE_BACK
            side.isNotEmpty() && (below.isNotEmpty() || above.isNotEmpty()) -> PlacementAdvice.MOVE_PHONE_BACK
            size > config.maxScale -> PlacementAdvice.MOVE_PHONE_BACK
            below.isNotEmpty() -> PlacementAdvice.SHOW_BELOW
            above.isNotEmpty() -> PlacementAdvice.SHOW_ABOVE
            side.isNotEmpty() -> PlacementAdvice.CENTER
            sideOnMatters && !counting && torso > 0f && shoulderWidth < SIDE_ON_RATIO * torso -> PlacementAdvice.FACE_CAMERA
            // Turning between the two views a movement with a side view is read in: the shoulder
            // line narrows or opens on the way, and the detector refuses those frames while it
            // confirms the new view. Nothing to fix — and not the light, which is what it was told.
            !counting && (tick.changingView || (hasSideView && tick.quality == PoseQuality.TORSO_ROTATED)) ->
                PlacementAdvice.SETTLING
            !counting && size < config.minScale * SMALL_MARGIN -> PlacementAdvice.COME_CLOSER
            sideOnMatters && tick.quality == PoseQuality.TORSO_ROTATED -> PlacementAdvice.FACE_CAMERA
            !counting -> PlacementAdvice.CLEARER
            tick.phase == RepPhase.IDLE || tick.phase == RepPhase.LOST -> PlacementAdvice.GET_IN_POSITION
            else -> PlacementAdvice.READY
        }
        return advice to off
    }

    companion object {
        /** How long a new piece of advice must hold before it replaces the one on screen. */
        const val SWITCH_MS = 600L

        /** How long 좋아요 stays up once everything is right. */
        const val READY_SHOW_MS = 1_500L

        /**
         * Shoulder width against torso length below which the user is side on. Square to the lens
         * the ratio is about 0.8 standing and above 1.2 for a floor movement filmed from the head;
         * side on it is 0.2-0.5.
         */
        const val SIDE_ON_RATIO = 0.6f

        /** Just over the detector's minimum scale, so the advice starts before the refusals do. */
        const val SMALL_MARGIN = 1.1f

        /** How near an edge a point with no confidence counts as having left through it. */
        const val EDGE = 0.03f

        /** What a movement read in its [SideView] needs in the picture besides its own landmarks. */
        private val SIDE_VIEW_LANDMARKS = listOf(Lm.LEFT_HIP, Lm.RIGHT_HIP)

        /** What must be in the picture for [type] to be measured at all. */
        fun requiredLandmarks(type: ExerciseType): List<Int> {
            val descriptor = Exercises.of(type)
            val out = sortedSetOf(Lm.LEFT_SHOULDER, Lm.RIGHT_SHOULDER)
            if (descriptor.kind == MovementKind.HOLD) {
                // The plank needs the shoulders and hips seen; the legs it takes from the model's
                // 3-D skeleton whether the camera sees them or not, so feet past the edge of the
                // picture do not stop it and the coach must not say they do.
                out += listOf(Lm.LEFT_HIP, Lm.RIGHT_HIP)
            } else {
                out += descriptor.watchedLandmarks
            }
            return out.toList()
        }
    }
}
