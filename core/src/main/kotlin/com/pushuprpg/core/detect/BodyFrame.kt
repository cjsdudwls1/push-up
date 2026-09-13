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
    fun update(frame: PoseFrame, confidence: FloatArray): BodyFrameState? {
        if (!frame.hasPose) return null
        if (confidence[Lm.LEFT_SHOULDER] < config.minCoreConfidence ||
            confidence[Lm.RIGHT_SHOULDER] < config.minCoreConfidence
        ) return null

        val aspect = frame.aspect
        val slU = frame.u(Lm.LEFT_SHOULDER)
        val slV = frame.v(Lm.LEFT_SHOULDER)
        val srU = frame.u(Lm.RIGHT_SHOULDER)
        val srV = frame.v(Lm.RIGHT_SHOULDER)

        var axU = slU - srU
        var axV = slV - srV
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

        // Normal to the shoulder axis, sign forced toward the wrists so the signal is unaffected
        // by camera roll or by the preview being mirrored.
        var (nU, nV) = Geometry.rot90(axU, axV)

        val wL = confidence[Lm.LEFT_WRIST]
        val wR = confidence[Lm.RIGHT_WRIST]
        val shoulderU = (slU + srU) / 2f
        val shoulderV = (slV + srV) / 2f

        if (wL + wR > 0f) {
            val wristU = (frame.u(Lm.LEFT_WRIST) * wL + frame.u(Lm.RIGHT_WRIST) * wR) / (wL + wR)
            val wristV = (frame.v(Lm.LEFT_WRIST) * wL + frame.v(Lm.RIGHT_WRIST) * wR) / (wL + wR)
            if (Geometry.dot(wristU - shoulderU, wristV - shoulderV, nU, nV) < 0f) {
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
