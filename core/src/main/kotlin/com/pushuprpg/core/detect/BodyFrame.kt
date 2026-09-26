package com.pushuprpg.core.detect

import com.pushuprpg.core.math.Geometry
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.math.abs

/**
 * The body-local coordinate frame everything else is measured in.
 *
 * [nU], [nV] is the unit normal to the shoulder axis, always pointing from the shoulders toward
 * the wrists. Measuring along it rather than along screen-vertical makes the depth signal immune
 * to camera roll and to which way round the image is mirrored.
 *
 * [scale] is a slowly averaged projected shoulder width. It is the normalizer that makes the whole
 * pipeline work: see [BodyFrameTracker].
 */
data class BodyFrameState(
    val aspect: Float,
    val scale: Float,
    val axisU: Float,
    val axisV: Float,
    val nU: Float,
    val nV: Float,
    val shoulderU: Float,
    val shoulderV: Float,
    val instantScale: Float,
    val scaleReset: Boolean,
    val torsoRotated: Boolean,
    /** Read in the descriptor's [SideView], in the torso's frame. */
    val sideOn: Boolean = false,
    /** The view changed on this frame; [scaleReset] is set too. See [ExerciseDescriptor.sideView]. */
    val viewChanged: Boolean = false,
)

/**
 * Tracks the body frame across time.
 *
 * **Why shoulder width is the normalizer.** Under pinhole projection, a vertical world separation
 * `ΔY` images as `(f/Z)·ΔY` and the shoulder width `S` images as `(f/Z)·S`. In a pushup the hands
 * sit beside the chest, so wrists and shoulders are at essentially the same distance `Z` from the
 * lens, and the shoulder axis is very nearly perpendicular to the optical axis. Divide one by the
 * other and `f` and `Z` cancel *exactly*. The resulting ratio is invariant to how far away the
 * phone is, what lens it has, where in the frame the user is, and how the phone is rolled.
 *
 * That is what makes this the only viable choice here. The obvious alternative — normalizing by
 * torso length — is actively broken in this camera geometry: with the phone on the floor the torso
 * points away from the lens, so its projected length is both short *and changes during the rep*.
 * Dividing a moving signal by a moving normalizer destroys it.
 *
 * Side on it is the other way round — the shoulder line is what collapses and the torso lies in the
 * picture at its full length — so a movement with a [SideView] is read in the torso's frame there.
 * See [updateView].
 */
class BodyFrameTracker(private val config: DetectorConfig) {

    private var scaleEma = 0f
    private var initialized = false

    // A scale that has jumped, not yet believed. See [update].
    private var jumpFrames = 0
    private var jumpScale = 0f
    private var jumpSinceMs = 0L
    private var shrunkFrames = 0

    /** 0 until the normal's sign has been decided; only used when the descriptor latches it. */
    private var latchedSign = 0
    private var lastTMs = 0L

    // Which view a movement with a side view is read in, and the other view, not yet believed.
    private var sideOn = false
    private var viewDecided = false
    private var viewFrames = 0
    private var viewSinceMs = 0L
    private var viewLastMs = 0L
    private var viewChangePending = false

    fun reset() {
        scaleEma = 0f
        initialized = false
        lastTMs = 0L
        jumpFrames = 0
        shrunkFrames = 0
        sideOn = false
        viewDecided = false
        viewFrames = 0
        viewChangePending = false
    }

    /** The current averaged shoulder width, or 0 before it is known. */
    val scale: Float get() = scaleEma

    /**
     * A movement with a [SideView] has been seen in its other view and is being confirmed there:
     * the body is turning between the two, and the frames on the way are refused, not misread.
     */
    val changingView: Boolean get() = viewFrames > 0

    /**
     * Returns null when the shoulders are not reliable enough to define a frame at all, which is
     * the honest answer — every downstream quantity divides by [BodyFrameState.scale].
     */
    private companion object {
        /**
         * How far the far pair must project along the normal before its sign is trusted.
         *
         * Below this the projection is small enough that landmark noise decides it.
         */
        const val SIGN_LATCH_FRACTION = 0.40f

        /**
         * A jumped scale is a new subject or a new placement only once it has held this long, and
         * this many frames, at a level consistent with itself.
         *
         * Before, one frame was enough, and one frame is what the lite model gets wrong: a shoulder
         * put on the neck for a single frame, filmed from the head. On a phone that frame reset the
         * filter, abandoned the rep, and — through the calibrator, which kept the largest value it
         * had seen — moved the top of the range somewhere the body never returned to. Every rep
         * after the first then read 40-60 at lockout and never re-armed.
         */
        const val SWITCH_CONFIRM_FRAMES = 3
        const val SWITCH_CONFIRM_MS = 100L

        /** Frames the shoulder line must stay short before it is a turned torso, not a glitch. */
        const val ROTATED_CONFIRM_FRAMES = 2

        /**
         * Shoulder width against the longer shoulder-to-hip line in the picture under which a
         * movement with a side view is side on. On the rig, from the floor, a pushup 70 degrees or
         * more off the head reads 0.48 at most, 60 degrees off 0.46-0.58 and 50 off 0.65 at least;
         * filmed side on by a phone, a plank read 0.00-0.05. Square to the head it is above 1.
         *
         * It was 0.42, and from the floor 61-70 degrees off fell between the two views: the shoulder
         * line there is too short for the far shoulder to be believed at the bottom of a rep, so
         * the head-on reading lost it, and the ratio went under 0.42 only at the bottom, never for
         * the second a switch needs. 1.3 m away a set 63 degrees off counted one rep in eight, 1 m
         * away 62-64 off one or none, and around them the first rep was lost. At 0.55 the view
         * turns 55-60 degrees off, where from a metre and further the head-on reading still counts
         * every rep; 0.8 m away, 61-62 degrees off, the turn still costs the rep it lands in.
         */
        const val SIDE_ON_RATIO = 0.55f

        /**
         * Under this ratio, with the shoulder line also too short for the detector to measure at
         * all, the side view is the only reading there is. From chest height 70 degrees off the head
         * reads up to 0.45 at the bottom, and from waist or chest height 60 degrees off 0.48-0.58 —
         * no different from 60 off from the floor, except that the shoulder line is under the
         * minimum scale there from the top of the rep.
         */
        const val SIDE_ON_UNMEASURABLE_RATIO = 0.60f

        /** Over this, with a measurable shoulder line, the shoulders face the lens again. */
        const val FACING_RATIO = 0.70f

        /**
         * A view is believed once it has held this long and this many frames, with no gap longer
         * than [VIEW_MAX_GAP_MS]. Filmed from the head, the lite model collapsed the shoulder line
         * for single frames through the set, and as the user got up at the end for six frames over
         * 470 ms with frames lost between; from the floor 2 m away at 60 degrees off the head, the
         * shoulder line stays under the minimum scale for 530 ms at the bottom of every rep.
         */
        const val VIEW_CONFIRM_FRAMES = 4
        const val VIEW_CONFIRM_MS = 1000L
        const val VIEW_MAX_GAP_MS = 150L
    }

    /**
     * Keeps [sideOn] current for a movement with a [SideView], and returns true on the frame the
     * view changes. The first view is taken as seen; after that, a change must hold (see
     * [VIEW_CONFIRM_MS]) — one stray frame never moves it.
     */
    private fun updateView(frame: PoseFrame, confidence: FloatArray): Boolean {
        val shoulderSeen = maxOf(confidence[Lm.LEFT_SHOULDER], confidence[Lm.RIGHT_SHOULDER]) >= config.minCoreConfidence
        val hipSeen = maxOf(confidence[Lm.LEFT_HIP], confidence[Lm.RIGHT_HIP]) >= config.minCoreConfidence
        val shoulders = Geometry.norm(
            frame.u(Lm.LEFT_SHOULDER) - frame.u(Lm.RIGHT_SHOULDER),
            frame.v(Lm.LEFT_SHOULDER) - frame.v(Lm.RIGHT_SHOULDER),
        )
        val torso = maxOf(
            Geometry.norm(frame.u(Lm.LEFT_HIP) - frame.u(Lm.LEFT_SHOULDER), frame.v(Lm.LEFT_HIP) - frame.v(Lm.LEFT_SHOULDER)),
            Geometry.norm(frame.u(Lm.RIGHT_HIP) - frame.u(Lm.RIGHT_SHOULDER), frame.v(Lm.RIGHT_HIP) - frame.v(Lm.RIGHT_SHOULDER)),
        )
        if (!shoulderSeen || !hipSeen || torso < Geometry.EPSILON) {
            // Nothing to say either way; the run has to be seen whole.
            viewFrames = 0
            return false
        }
        val side = shoulders < SIDE_ON_RATIO * torso ||
            (shoulders < config.minScale && shoulders < SIDE_ON_UNMEASURABLE_RATIO * torso)
        val facing = shoulders > FACING_RATIO * torso && shoulders >= config.minScale
        if (!viewDecided) {
            viewDecided = true
            sideOn = side
            return false
        }
        val t = frame.timestampMs
        if (!(if (sideOn) facing else side)) {
            viewFrames = 0
            return false
        }
        if (viewFrames == 0 || t - viewLastMs > VIEW_MAX_GAP_MS) {
            viewFrames = 0
            viewSinceMs = t
        }
        viewFrames++
        viewLastMs = t
        if (viewFrames < VIEW_CONFIRM_FRAMES || t - viewSinceMs < VIEW_CONFIRM_MS) return false
        sideOn = !sideOn
        viewFrames = 0
        return true
    }

    fun update(frame: PoseFrame, confidence: FloatArray): BodyFrameState? {
        if (!frame.hasPose) return null

        val descriptor = config.descriptor
        if (descriptor.sideView != null && updateView(frame, confidence)) {
            // A new view is a new frame: re-seeded like a new subject, on the first frame it measures.
            initialized = false
            viewChangePending = true
        }
        val nearSide = descriptor.coreConfidence == CoreConfidence.NEAR_SIDE || sideOn
        val confL = confidence[Lm.LEFT_SHOULDER]
        val confR = confidence[Lm.RIGHT_SHOULDER]
        // Filmed from the side the far shoulder is a regression, not an observation, so demanding
        // both would refuse every frame of an otherwise perfect set.
        val coreConf = if (nearSide) maxOf(confL, confR) else minOf(confL, confR)
        if (coreConf < config.minCoreConfidence) return null

        val aspect = frame.aspect

        // The origin the depth ratio is measured from: the shoulder midpoint when the user faces
        // the lens, mostly the near shoulder when they do not.
        val originU: Float
        val originV: Float
        var axU: Float
        var axV: Float
        val axisSource = if (sideOn) AxisSource.NEAR_SIDE_TORSO else descriptor.axisSource
        when (axisSource) {
            AxisSource.SHOULDER_PAIR -> {
                originU = (frame.u(Lm.LEFT_SHOULDER) + frame.u(Lm.RIGHT_SHOULDER)) / 2f
                originV = (frame.v(Lm.LEFT_SHOULDER) + frame.v(Lm.RIGHT_SHOULDER)) / 2f
                axU = frame.u(Lm.LEFT_SHOULDER) - frame.u(Lm.RIGHT_SHOULDER)
                axV = frame.v(Lm.LEFT_SHOULDER) - frame.v(Lm.RIGHT_SHOULDER)
            }
            AxisSource.NEAR_SIDE_TORSO -> {
                if (maxOf(confidence[Lm.LEFT_HIP], confidence[Lm.RIGHT_HIP]) < config.minCoreConfidence) return null
                // The side the camera sees better, by weight rather than by choice. Side on the far
                // side weighs next to nothing; 60-75 degrees off the head both are seen about as
                // well, and choosing one flipped the frame between two torsos of different lengths
                // in the picture whenever their confidences crossed.
                val wL = minOf(confL, confidence[Lm.LEFT_HIP]).let { it * it }
                val wR = minOf(confR, confidence[Lm.RIGHT_HIP]).let { it * it }
                if (wL + wR <= 0f) return null
                originU = (frame.u(Lm.LEFT_SHOULDER) * wL + frame.u(Lm.RIGHT_SHOULDER) * wR) / (wL + wR)
                originV = (frame.v(Lm.LEFT_SHOULDER) * wL + frame.v(Lm.RIGHT_SHOULDER) * wR) / (wL + wR)
                axU = (frame.u(Lm.LEFT_HIP) * wL + frame.u(Lm.RIGHT_HIP) * wR) / (wL + wR) - originU
                axV = (frame.v(Lm.LEFT_HIP) * wL + frame.v(Lm.RIGHT_HIP) * wR) / (wL + wR) - originV
            }
            AxisSource.TORSO -> {
                if (maxOf(confidence[Lm.LEFT_HIP], confidence[Lm.RIGHT_HIP]) < config.minCoreConfidence) return null
                originU = (frame.u(Lm.LEFT_SHOULDER) + frame.u(Lm.RIGHT_SHOULDER)) / 2f
                originV = (frame.v(Lm.LEFT_SHOULDER) + frame.v(Lm.RIGHT_SHOULDER)) / 2f
                val spineU = (frame.u(Lm.LEFT_HIP) + frame.u(Lm.RIGHT_HIP)) / 2f - originU
                val spineV = (frame.v(Lm.LEFT_HIP) + frame.v(Lm.RIGHT_HIP)) / 2f - originV
                // A virtual shoulder line square to the spine, so the normal the depth is read
                // along — its perpendicular — is the spine itself; and its length is the spine's.
                axU = -spineV
                axV = spineU
            }
        }

        val axisLen = Geometry.norm(axU, axV)
        if (axisLen < Geometry.EPSILON) return null
        axU /= axisLen
        axV /= axisLen

        // In shoulder widths whatever the axis, so a descriptor's priors mean the same thing.
        val instantScale = if (axisSource == AxisSource.SHOULDER_PAIR) axisLen else axisLen * TORSO_TO_SHOULDER_WIDTH
        if (instantScale < config.minScale || instantScale > config.maxScale) return null

        // Scale is averaged slowly: it is a property of the person and the camera placement, not
        // of the rep. Letting it track quickly would let it absorb the very motion we measure.
        var scaleReset = false
        var viewChanged = false
        if (!initialized) {
            scaleEma = instantScale
            initialized = true
            jumpFrames = 0
            if (viewChangePending) {
                viewChangePending = false
                scaleReset = true
                viewChanged = true
            }
        } else {
            val dtMs = (frame.timestampMs - lastTMs).coerceAtLeast(0L)
            if (abs(instantScale - scaleEma) / scaleEma > config.scaleJumpFraction) {
                // A jump this large is not a person moving: it is the model switching subjects, the
                // user repositioning entirely — or the model misplacing a landmark for a frame. The
                // first two persist and the last does not, so the frame is set aside until the new
                // scale has held (see SWITCH_CONFIRM_FRAMES), and only then re-seeded.
                val consistent = jumpFrames > 0 &&
                    abs(instantScale - jumpScale) / jumpScale <= config.scaleJumpFraction / 2f
                if (!consistent) {
                    jumpFrames = 1
                    jumpScale = instantScale
                    jumpSinceMs = frame.timestampMs
                } else {
                    jumpFrames++
                    jumpScale += 0.5f * (instantScale - jumpScale)
                }
                if (jumpFrames < SWITCH_CONFIRM_FRAMES || frame.timestampMs - jumpSinceMs < SWITCH_CONFIRM_MS) {
                    return null
                }
                scaleEma = instantScale
                scaleReset = true
                jumpFrames = 0
            } else {
                jumpFrames = 0
                val tauMs = config.scaleTauSec * 1000f
                val alpha = (dtMs / (tauMs + dtMs)).coerceIn(0f, 1f)
                scaleEma += alpha * (instantScale - scaleEma)
            }
        }
        lastTMs = frame.timestampMs

        // Normal to the shoulder axis, with its sign forced toward the far end of the body so the
        // signal is unaffected by camera roll or by the preview being mirrored.
        //
        // Which end counts as "far" depends on the movement: a pushup measures toward the hands on
        // the floor, a squat toward the hips and legs, a pull-up toward the hands on the bar above.
        // Getting this wrong does not produce a slightly worse reading — it inverts the signal, so
        // descending would register as rising. It is therefore declared per exercise, as data.
        var (nU, nV) = Geometry.rot90(axU, axV)

        val shoulderU = originU
        val shoulderV = originV

        val far = descriptor.normalToward
        val wL = confidence[far.left]
        val wR = confidence[far.right]

        if (scaleReset) latchedSign = 0

        if (wL + wR > 0f) {
            val farU = (frame.u(far.left) * wL + frame.u(far.right) * wR) / (wL + wR)
            val farV = (frame.v(far.left) * wL + frame.v(far.right) * wR) / (wL + wR)
            val projection = Geometry.dot(farU - shoulderU, farV - shoulderV, nU, nV)
            val sign = if (descriptor.latchNormalSign) {
                // Take the sign the first time the movement is unambiguous, then hold it. Near the
                // hard end of a side-on press the projection approaches zero, and re-deciding there
                // puts a flip on the strike frame itself.
                if (latchedSign == 0 && abs(projection) > SIGN_LATCH_FRACTION * instantScale) {
                    latchedSign = if (projection < 0f) -1 else 1
                }
                latchedSign
            } else {
                if (projection < 0f) -1 else 1
            }
            if (sign < 0) {
                nU = -nU
                nV = -nV
            }
        }

        // A rolled torso foreshortens the projected shoulder width, which would inflate the depth
        // ratio and hand out depth for a twist. Flagging it lets the state machine refuse to count.
        // Read along the spine there is no shoulder width to foreshorten, and turning is allowed.
        val spine = axisSource == AxisSource.TORSO
        val shrunk = !spine && scaleEma > 0f && instantScale / scaleEma < config.torsoRotatedFraction
        shrunkFrames = if (shrunk) shrunkFrames + 1 else 0
        // One short frame is the model misplacing a shoulder; a turned torso stays turned. The
        // single frame is set aside rather than read — its h would be inflated by the same factor.
        if (shrunk && shrunkFrames < ROTATED_CONFIRM_FRAMES) return null
        val torsoRotated = shrunk

        return BodyFrameState(
            aspect = aspect,
            // Along the spine the divisor is this frame's own spine length, not a slow average: the
            // spine leans through a dip and the phone looks up at it, so its projection changes
            // within a rep — by 18 percent from the floor at 35 degrees — and the arm, lying along
            // it, is foreshortened with it in the same frame. An averaged divisor lagged that and
            // drifted the top of the rep out of reach after three.
            scale = if (spine) instantScale else scaleEma,
            axisU = axU,
            axisV = axV,
            nU = nU,
            nV = nV,
            shoulderU = shoulderU,
            shoulderV = shoulderV,
            instantScale = instantScale,
            scaleReset = scaleReset,
            torsoRotated = torsoRotated,
            sideOn = sideOn,
            viewChanged = viewChanged,
        )
    }
}
