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
 */
class BodyFrameTracker(private val config: DetectorConfig) {

    private var scaleEma = 0f
    private var initialized = false

    /** 0 until the normal's sign has been decided; only used when the descriptor latches it. */
    private var latchedSign = 0
    private var lastTMs = 0L

    fun reset() {
        scaleEma = 0f
        initialized = false
        lastTMs = 0L
    }

    /** The current averaged shoulder width, or 0 before it is known. */
    val scale: Float get() = scaleEma

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
    }

    fun update(frame: PoseFrame, confidence: FloatArray): BodyFrameState? {
        if (!frame.hasPose) return null

        val descriptor = config.descriptor
        val nearSide = descriptor.coreConfidence == CoreConfidence.NEAR_SIDE
        val confL = confidence[Lm.LEFT_SHOULDER]
        val confR = confidence[Lm.RIGHT_SHOULDER]
        // Filmed from the side the far shoulder is a regression, not an observation, so demanding
        // both would refuse every frame of an otherwise perfect set.
        val coreConf = if (nearSide) maxOf(confL, confR) else minOf(confL, confR)
        if (coreConf < config.minCoreConfidence) return null

        val aspect = frame.aspect
        val left = confL >= confR
        val shoulder = if (left) Lm.LEFT_SHOULDER else Lm.RIGHT_SHOULDER
        val hip = if (left) Lm.LEFT_HIP else Lm.RIGHT_HIP

        // The origin the depth ratio is measured from: the shoulder midpoint when the user faces
        // the lens, the near shoulder alone when they do not.
        val originU: Float
        val originV: Float
        var axU: Float
        var axV: Float
        when (descriptor.axisSource) {
            AxisSource.SHOULDER_PAIR -> {
                originU = (frame.u(Lm.LEFT_SHOULDER) + frame.u(Lm.RIGHT_SHOULDER)) / 2f
                originV = (frame.v(Lm.LEFT_SHOULDER) + frame.v(Lm.RIGHT_SHOULDER)) / 2f
                axU = frame.u(Lm.LEFT_SHOULDER) - frame.u(Lm.RIGHT_SHOULDER)
                axV = frame.v(Lm.LEFT_SHOULDER) - frame.v(Lm.RIGHT_SHOULDER)
            }
            AxisSource.NEAR_SIDE_TORSO -> {
                if (confidence[hip] < config.minCoreConfidence) return null
                originU = frame.u(shoulder)
                originV = frame.v(shoulder)
                axU = frame.u(hip) - frame.u(shoulder)
                axV = frame.v(hip) - frame.v(shoulder)
            }
        }

        val axisLen = Geometry.norm(axU, axV)
        if (axisLen < Geometry.EPSILON) return null
        axU /= axisLen
        axV /= axisLen

        val instantScale = axisLen
        if (instantScale < config.minScale || instantScale > config.maxScale) return null

        // Scale is averaged slowly: it is a property of the person and the camera placement, not
        // of the rep. Letting it track quickly would let it absorb the very motion we measure.
        var scaleReset = false
        if (!initialized) {
            scaleEma = instantScale
            initialized = true
        } else {
            val dtMs = (frame.timestampMs - lastTMs).coerceAtLeast(0L)
            if (abs(instantScale - scaleEma) / scaleEma > config.scaleJumpFraction) {
                // A jump this large is not a person moving; it is the model switching subjects or
                // the user repositioning entirely. Re-seed rather than average across the change.
                scaleEma = instantScale
                scaleReset = true
            } else {
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
        val torsoRotated = scaleEma > 0f &&
            instantScale / scaleEma < config.torsoRotatedFraction

        return BodyFrameState(
            aspect = aspect,
            scale = scaleEma,
            axisU = axU,
            axisV = axV,
            nU = nU,
            nV = nV,
            shoulderU = shoulderU,
            shoulderV = shoulderV,
            instantScale = instantScale,
            scaleReset = scaleReset,
            torsoRotated = torsoRotated,
        )
    }
}
