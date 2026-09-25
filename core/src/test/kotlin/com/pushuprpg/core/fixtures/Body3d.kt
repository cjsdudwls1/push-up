package com.pushuprpg.core.fixtures

import com.pushuprpg.core.pose.Landmark
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks as Lm
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * One body, in metres, projected through one pinhole camera.
 *
 * [PoseFixtures] places landmarks by hand in image space, per exercise, and that is how two of
 * its fixtures ended up describing bodies that cannot exist — an elbow left where the arm could
 * not put it, a witness that passed because it had been drawn to pass. This rig cannot do that.
 * Every movement is a set of joint angles applied to the same skeleton with real segment lengths;
 * the image landmarks are the pinhole projection of that skeleton and the world landmarks are the
 * same skeleton translated to the hip midpoint, exactly as MediaPipe reports them. The primary
 * signal, the joint-angle cross-check and the body-travel witness are therefore three views of
 * one body, and if a descriptor's claim about any of them is wrong, this is what shows it.
 *
 * It also makes the camera a parameter. `h` is invariant to distance by construction and to
 * nothing else; where the phone sits — on the floor tilted up, at waist height, at chest height —
 * changes the reading, and this is the only way to measure by how much.
 *
 * Coordinates: X to the subject's left (the viewer's right when they face the lens), Y up,
 * Z toward the camera. Floor at Y = 0. The subject stands at the origin facing +Z.
 */
object Body3d {

    // 175 cm male, from standard anthropometry. The same numbers ConfidenceEstimator's bone
    // limits are derived from.
    const val SHOULDER_WIDTH = 0.40f
    const val UPPER_ARM = 0.32f
    const val FOREARM = 0.26f
    const val TORSO = 0.50f
    const val HIP_WIDTH = 0.28f
    const val THIGH = 0.44f
    const val SHANK = 0.42f
    const val ANKLE_HEIGHT = 0.08f
    const val NOSE_ABOVE_SHOULDER = 0.24f
    const val NOSE_FORWARD = 0.09f
    /** The knee joint's height off the floor with the knee on it. */
    const val KNEE_ON_FLOOR = 0.06f

    const val STANDING_HIP_Y = ANKLE_HEIGHT + SHANK + THIGH
    const val STANDING_SHOULDER_Y = STANDING_HIP_Y + TORSO

    data class V3(val x: Float, val y: Float, val z: Float) {
        operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
        operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
        operator fun times(s: Float) = V3(x * s, y * s, z * s)
        fun dot(o: V3) = x * o.x + y * o.y + z * o.z
        val length get() = sqrt(x * x + y * y + z * z)
        fun unit(): V3 = (1f / length).let { V3(x * it, y * it, z * it) }
    }

    /** Thirteen tracked joints in metres; everything the detector reads. */
    class Skeleton {
        val joints = HashMap<Int, V3>()
        operator fun set(index: Int, p: V3) { joints[index] = p }
        operator fun get(index: Int): V3 = joints.getValue(index)
        fun mid(a: Int, b: Int): V3 = (this[a] + this[b]) * 0.5f
    }

    /**
     * A phone at [position] aimed at [target], portrait, with the given vertical field of view.
     *
     * 480x640 because that is what the app captures: 640x480 rotated upright.
     */
    data class Camera(
        val position: V3,
        val target: V3,
        val vfovDeg: Float = 62f,
        val width: Int = 480,
        val height: Int = 640,
    ) {
        val forward = (target - position).unit()
        // forward × world-up: horizontal, and to the viewer's right. It used to be the negation of
        // this, which rotated every image 180 degrees. Nothing that measures relative geometry
        // could tell; the placement coach, which reads which edge a foot left by, could.
        val right = V3(-forward.z, 0f, forward.x).unit()
        val up = V3(
            right.y * forward.z - right.z * forward.y,
            right.z * forward.x - right.x * forward.z,
            right.x * forward.y - right.y * forward.x,
        ).unit()
        private val tanHalfV = tan(vfovDeg / 2f * PI.toFloat() / 180f)
        val aspect: Float get() = width.toFloat() / height.toFloat()

        /** Normalised image coordinates, MediaPipe convention: x by width, y by height, top-left origin. */
        fun project(p: V3): Pair<Float, Float> {
            val d = p - position
            val z = d.dot(forward)
            val xc = d.dot(right) / z
            val yc = d.dot(up) / z
            // The image x axis runs to the viewer's right. A subject facing the lens has their own
            // left on the viewer's right, so +X (subject's left) lands at larger image x when the
            // preview is not mirrored — which is the analysis frame the detector reads.
            val xn = 0.5f + xc / (2f * tanHalfV * aspect)
            val yn = 0.5f - yc / (2f * tanHalfV)
            return xn to yn
        }

        companion object {
            /** Propped on the floor [distance] metres away, tilted up by [tiltDeg]. */
            fun onFloor(distance: Float, tiltDeg: Float): Camera {
                val rad = tiltDeg * PI.toFloat() / 180f
                return Camera(
                    position = V3(0f, 0.10f, distance),
                    target = V3(0f, 0.10f + distance * tan(rad), 0f),
                )
            }

            /** Standing on something [height] metres up, level, [distance] metres away. */
            fun level(distance: Float, height: Float): Camera =
                Camera(position = V3(0f, height, distance), target = V3(0f, height, 0f))
        }
    }

    /**
     * Projects a skeleton through a camera into the frame the detector reads.
     *
     * Confidence is 1 for every joint inside the image and 0 outside it, which is exactly what
     * ConfidenceEstimator's bounds test does to a landmark the model extrapolated off the edge.
     * World landmarks are the skeleton with the hip midpoint subtracted, as MediaPipe defines them.
     */
    fun frame(tMs: Long, skeleton: Skeleton, camera: Camera, world: Boolean = true): PoseFrame {
        val lm = MutableList(Lm.COUNT) { Landmark.ZERO }
        val hipMid = skeleton.mid(Lm.LEFT_HIP, Lm.RIGHT_HIP)
        val worldLm = MutableList(Lm.COUNT) { Landmark.ZERO }
        for ((index, p) in skeleton.joints) {
            val (x, y) = camera.project(p)
            val inside = x in 0f..1f && y in 0f..1f
            val conf = if (inside) 1f else 0f
            lm[index] = Landmark(x, y, 0f, conf, conf)
            val w = p - hipMid
            // MediaPipe world space is hip-centred and camera-aligned — x right, y down, z away, in
            // the phone's own frame, not gravity's; the model has no idea which way is down except
            // through the picture. So a phone tilted up 30 degrees reports a level body tilted 30
            // degrees, and a rule that reads "horizontal" from world landmarks has to survive that.
            worldLm[index] = Landmark(w.dot(camera.right), -w.dot(camera.up), w.dot(camera.forward))
        }
        return PoseFrame(tMs, camera.width, camera.height, lm, if (world) worldLm else emptyList())
    }

    // ------------------------------------------------------------------ poses

    private fun deg(d: Float) = d * PI.toFloat() / 180f

    /**
     * Second joint of a two-link chain from [root] to [end], links [a] then [b], with the middle
     * joint pushed toward [bendToward]. What a knee or an elbow does given where the hip and the
     * ankle, or the shoulder and the wrist, are.
     */
    private fun midJoint(root: V3, end: V3, a: Float, b: Float, bendToward: V3): V3 {
        val d = end - root
        val dist = d.length.coerceAtMost(a + b - 1e-4f)
        val dir = d.unit()
        val along = (dist * dist + a * a - b * b) / (2f * dist)
        val off = sqrt((a * a - along * along).coerceAtLeast(0f))
        // Perpendicular to the chain, in the direction of the bend.
        val perp = (bendToward - dir * bendToward.dot(dir)).let { if (it.length < 1e-6f) V3(0f, 0f, 1f) else it.unit() }
        return root + dir * along + perp * off
    }

    private fun standingLegs(s: Skeleton) {
        for ((side, hip, knee, ankle) in SIDES_LEG) {
            val x = side * HIP_WIDTH / 2f
            s[hip] = V3(x, STANDING_HIP_Y, 0f)
            s[knee] = V3(x, ANKLE_HEIGHT + SHANK, 0.02f)
            s[ankle] = V3(x, ANKLE_HEIGHT, 0f)
        }
    }

    private fun uprightTorso(s: Skeleton, hipMid: V3) {
        for ((side, shoulder) in SIDES_SHOULDER) {
            s[shoulder] = hipMid + V3(side * SHOULDER_WIDTH / 2f, TORSO, 0f)
        }
        s[Lm.NOSE] = hipMid + V3(0f, TORSO + NOSE_ABOVE_SHOULDER, NOSE_FORWARD)
    }

    private fun hangingArms(s: Skeleton, forwardTiltDeg: Float = 5f) {
        for ((side, shoulder, elbow, wrist) in SIDES_ARM) {
            val dir = V3(0f, -cos(deg(forwardTiltDeg)), sin(deg(forwardTiltDeg)))
            s[elbow] = s[shoulder] + dir * UPPER_ARM
            s[wrist] = s[elbow] + dir * FOREARM
        }
    }

    /**
     * A split squat — the lunge as it is actually repeated: feet stay split, the body goes down and
     * up. Left leg forward, toward the camera. [depth] 0 is standing tall in the split, 1 is both
     * knees at about ninety degrees.
     */
    fun lunge(depth: Float, leftForward: Boolean = true): Skeleton {
        val s = Skeleton()
        val split = 0.36f
        val hipY = STANDING_HIP_Y - 0.40f * depth
        val hipMid = V3(0f, hipY, 0f)
        for ((side, hip, knee, ankle) in SIDES_LEG) {
            val x = side * HIP_WIDTH / 2f
            val front = (side > 0) == leftForward
            val ankleZ = if (front) split else -split
            s[hip] = V3(x, hipY, 0f)
            s[ankle] = V3(x, ANKLE_HEIGHT, ankleZ)
            // Both knees bend forward of the hip-ankle line: the front shin stays near vertical, the
            // back knee drops toward the floor under the hip.
            s[knee] = midJoint(s[hip], s[ankle], THIGH, SHANK, V3(0f, 0f, 1f))
        }
        uprightTorso(s, hipMid)
        hangingArms(s)
        return s
    }

    /**
     * A bar dip. Hands fixed on bars [barY] up, [barHalfWidth] apart. [depth] 0 is lockout with the
     * elbow at 172; 1 is an 85-degree bottom. The torso leans forward with depth by [leanDeg]
     * degrees, and the legs hang with the knees bent.
     */
    fun dip(depth: Float, barY: Float = 1.20f, leanDeg: Float = 20f, barHalfWidth: Float = 0.27f): Skeleton {
        val s = Skeleton()
        val theta = deg(172f - depth * (172f - 85f))
        // Shoulder-to-wrist distance for a two-link arm at this elbow angle.
        val reach = sqrt(UPPER_ARM * UPPER_ARM + FOREARM * FOREARM - 2f * UPPER_ARM * FOREARM * cos(theta))
        val lean = deg(leanDeg * depth)
        val shoulderMid = V3(0f, barY + reach * cos(lean), reach * sin(lean) * 0.3f)
        for ((side, shoulder, elbow, wrist) in SIDES_ARM) {
            s[wrist] = V3(side * barHalfWidth, barY, 0f)
            s[shoulder] = shoulderMid + V3(side * SHOULDER_WIDTH / 2f, 0f, 0f)
            // The elbow points backward, away from the lens, as the body sinks past the hands.
            s[elbow] = midJoint(s[shoulder], s[wrist], UPPER_ARM, FOREARM, V3(0f, 0f, -1f))
        }
        val torsoDir = V3(0f, -cos(lean), -sin(lean))
        val hipMid = shoulderMid + torsoDir * TORSO
        for ((side, hip, knee, ankle) in SIDES_LEG) {
            val x = side * HIP_WIDTH / 2f
            s[hip] = hipMid + V3(x, 0f, 0f)
            s[knee] = s[hip] + V3(0f, -THIGH * 0.94f, -THIGH * 0.34f)
            s[ankle] = s[knee] + V3(0f, -SHANK * 0.80f, -SHANK * 0.60f)
        }
        s[Lm.NOSE] = shoulderMid + V3(0f, NOSE_ABOVE_SHOULDER * cos(lean), NOSE_FORWARD + NOSE_ABOVE_SHOULDER * sin(lean))
        return s
    }

    /**
     * A pushup, on the hands and toes. [depth] 0 is lockout, arms straight; 1 is the chest about a
     * fist off the floor, shoulders 0.26 m up, with the elbow near 55 degrees. The body is one
     * rigid plank pivoting at the feet. The hands stay planted a little wider than the shoulders,
     * the shoulders travel slightly forward of them on the way down as they do in a real pushup, and
     * the elbows go back toward the feet and out — the two-link arm puts them wherever the
     * shoulders' height requires.
     *
     * [heading] is the horizontal direction the head points and [shoulderAt] the point on the floor
     * the shoulders sit over. The default lies across the lens with the head to the viewer's left:
     * the side view the placement line asks for.
     *
     * [onKnees] is the knee pushup the game offers when pushups get hard: the same arms, and the
     * body one straight line from the shoulders to the knees on the floor, hips extended, pivoting
     * there, with the shins flat on the floor behind.
     */
    fun pushup(
        depth: Float,
        heading: V3 = V3(-1f, 0f, 0f),
        shoulderAt: V3 = V3(-0.55f, 0f, 0f),
        onKnees: Boolean = false,
    ): Skeleton {
        val s = Skeleton()
        val h = heading.unit()
        val down = V3(0f, -1f, 0f)
        // The subject's left, for a body facing the floor: head x facing, as for a standing body.
        val left = V3(h.y * down.z - h.z * down.y, h.z * down.x - h.x * down.z, h.x * down.y - h.y * down.x)
        val handY = 0.04f
        val handHalfWidth = 0.25f
        val lateral = handHalfWidth - SHOULDER_WIDTH / 2f
        val reachTop = sqrt(UPPER_ARM * UPPER_ARM + FOREARM * FOREARM - 2f * UPPER_ARM * FOREARM * cos(deg(172f)))
        val forwardTop = 0.05f
        val topY = handY + sqrt(reachTop * reachTop - lateral * lateral - forwardTop * forwardTop)
        val shoulderY = topY + (0.26f - topY) * depth
        val forward = forwardTop + (0.12f - forwardTop) * depth
        val hands = V3(shoulderAt.x, handY, shoulderAt.z)
        val shoulderMid = hands + h * forward + V3(0f, shoulderY - handY, 0f)
        for ((side, shoulder, elbow, wrist) in SIDES_ARM) {
            s[wrist] = hands + left * (side * handHalfWidth)
            s[shoulder] = shoulderMid + left * (side * SHOULDER_WIDTH / 2f)
            s[elbow] = midJoint(s[shoulder], s[wrist], UPPER_ARM, FOREARM, h * -1f + left * (side * 0.8f))
        }
        val bodyLength = TORSO + THIGH + if (onKnees) 0f else SHANK
        val drop = shoulderY - if (onKnees) KNEE_ON_FLOOR else ANKLE_HEIGHT
        val toFeet = (h * -sqrt(bodyLength * bodyLength - drop * drop) + V3(0f, -drop, 0f)).unit()
        val hipMid = shoulderMid + toFeet * TORSO
        for ((side, hip, knee, ankle) in SIDES_LEG) {
            val x = left * (side * HIP_WIDTH / 2f)
            s[hip] = hipMid + x
            s[knee] = hipMid + toFeet * THIGH + x
            s[ankle] = if (onKnees) s[knee] - h * SHANK else hipMid + toFeet * (THIGH + SHANK) + x
        }
        // The head in line with the body, face to the floor.
        val faceDown = (down - toFeet * down.dot(toFeet)).unit()
        s[Lm.NOSE] = shoulderMid - toFeet * NOSE_ABOVE_SHOULDER + faceDown * NOSE_FORWARD
        return s
    }

    /**
     * A pull-up on a bar at [barY], hands [gripHalfWidth] either side of centre. [depth] 0 is a dead
     * hang, arms straight overhead; 1 is the chin over the bar, elbows closed to about 50 degrees
     * and pointing down and a little forward. The body hangs straight under the shoulders with the
     * knees slightly bent, and [facing] is the direction the chest faces: toward the lens (+Z) is
     * the front view, away from it the back.
     */
    fun pullUp(
        depth: Float,
        facing: V3 = V3(0f, 0f, 1f),
        barY: Float = 2.15f,
        gripHalfWidth: Float = 0.30f,
    ): Skeleton {
        val s = Skeleton()
        val f = facing.unit()
        val left = V3(f.z, 0f, -f.x) // the subject's left, for a body upright facing f
        val theta = deg(172f - depth * (172f - 52f))
        val lateral = gripHalfWidth - SHOULDER_WIDTH / 2f
        val reach = sqrt(UPPER_ARM * UPPER_ARM + FOREARM * FOREARM - 2f * UPPER_ARM * FOREARM * cos(theta))
        val drop = sqrt((reach * reach - lateral * lateral).coerceAtLeast(0.01f))
        val shoulderMid = V3(0f, barY - drop, 0f)
        for ((side, shoulder, elbow, wrist) in SIDES_ARM) {
            s[wrist] = V3(0f, barY, 0f) + left * (side * gripHalfWidth)
            s[shoulder] = shoulderMid + left * (side * SHOULDER_WIDTH / 2f)
            // Elbows drive down and slightly forward, as they do.
            s[elbow] = midJoint(s[shoulder], s[wrist], UPPER_ARM, FOREARM, left * side + f * 0.3f)
        }
        val hipMid = shoulderMid + V3(0f, -TORSO, 0f) + f * 0.03f
        for ((side, hip, knee, ankle) in SIDES_LEG) {
            s[hip] = hipMid + left * (side * HIP_WIDTH / 2f)
            s[knee] = s[hip] + V3(0f, -THIGH * 0.98f, 0f) + f * (THIGH * 0.2f)
            s[ankle] = s[knee] + V3(0f, -SHANK * 0.94f, 0f) - f * (SHANK * 0.34f)
        }
        s[Lm.NOSE] = shoulderMid + V3(0f, NOSE_ABOVE_SHOULDER, 0f) + f * NOSE_FORWARD
        return s
    }

    /** A dip seen from any side: [dip], turned so the chest faces [facing]. */
    fun dipFacing(depth: Float, facing: V3): Skeleton = rotated(dip(depth), facing)

    /** [s] turned about the vertical axis so that what faced +Z faces [facing]. */
    fun rotated(s: Skeleton, facing: V3): Skeleton {
        val f = facing.unit()
        // +Z maps to f; +X (the subject's left when facing +Z) maps to the left of f.
        val left = V3(f.z, 0f, -f.x)
        val out = Skeleton()
        for ((i, p) in s.joints) out[i] = left * p.x + V3(0f, p.y, 0f) + f * p.z
        return out
    }

    /**
     * A bodyweight squat, facing +Z: feet a little wider than the hips and side by side, the hips
     * sitting back and down, the knees travelling forward over the toes, the chest leaning in and
     * the arms reaching forward for balance. [depth] 1 is thighs about parallel.
     */
    fun squat(depth: Float): Skeleton {
        val s = Skeleton()
        val hipY = STANDING_HIP_Y - 0.45f * depth
        val hipZ = -0.22f * depth
        val hipMid = V3(0f, hipY, hipZ)
        for ((side, hip, knee, ankle) in SIDES_LEG) {
            val x = side * 0.17f
            s[hip] = V3(side * HIP_WIDTH / 2f, hipY, hipZ)
            s[ankle] = V3(x, ANKLE_HEIGHT, 0.02f)
            s[knee] = midJoint(s[hip], s[ankle], THIGH, SHANK, V3(side * 0.3f, 0f, 1f))
        }
        val lean = deg(35f * depth)
        val up = V3(0f, cos(lean), sin(lean))
        for ((side, shoulder) in SIDES_SHOULDER) {
            s[shoulder] = hipMid + up * TORSO + V3(side * SHOULDER_WIDTH / 2f, 0f, 0f)
        }
        s[Lm.NOSE] = hipMid + up * (TORSO + NOSE_ABOVE_SHOULDER) + V3(0f, 0f, NOSE_FORWARD)
        for ((side, shoulder, elbow, wrist) in SIDES_ARM) {
            val reach = deg(10f + 70f * depth)
            val dir = V3(0f, -cos(reach), sin(reach))
            s[elbow] = s[shoulder] + dir * UPPER_ARM
            s[wrist] = s[elbow] + dir * FOREARM
        }
        return s
    }

    /** Standing tall, arms by the sides. The thing a plank or a lunge must not be mistaken for. */
    fun standing(): Skeleton {
        val s = Skeleton()
        standingLegs(s)
        uprightTorso(s, V3(0f, STANDING_HIP_Y, 0f))
        hangingArms(s)
        return s
    }

    /**
     * A body on the floor, described as a side profile: for each joint, how far along [heading] it
     * is from the shoulders' floor point and how high. Left and right are the same profile moved
     * half a joint-width to either side. What the floor holds — a plank, all fours — are all this.
     */
    private fun floorPose(
        profile: Map<String, Pair<Float, Float>>,
        heading: V3,
        shoulderAt: V3,
    ): Skeleton {
        val s = Skeleton()
        val h = heading.unit()
        val left = V3(h.z, 0f, -h.x) // the subject's left when face-down with the head along h
        fun at(name: String, halfWidth: Float, side: Float): V3 {
            val (along, y) = profile.getValue(name)
            return V3(shoulderAt.x, y, shoulderAt.z) + h * along + left * (side * halfWidth)
        }
        for ((side, shoulder, elbow, wrist) in SIDES_ARM) {
            s[shoulder] = at("shoulder", SHOULDER_WIDTH / 2f, side)
            s[elbow] = at("elbow", 0.22f, side)
            s[wrist] = at("wrist", 0.24f, side)
        }
        for ((side, hip, knee, ankle) in SIDES_LEG) {
            s[hip] = at("hip", HIP_WIDTH / 2f, side)
            s[knee] = at("knee", HIP_WIDTH / 2f, side)
            s[ankle] = at("ankle", HIP_WIDTH * 0.4f, side)
        }
        s[Lm.NOSE] = at("nose", 0f, 0f)
        return s
    }

    /** Joints along a straight line from [from] toward [toward], at the body's segment lengths. */
    private fun straightBody(from: Pair<Float, Float>, toward: Pair<Float, Float>): Map<String, Pair<Float, Float>> {
        val dx = toward.first - from.first
        val dy = toward.second - from.second
        val len = sqrt(dx * dx + dy * dy)
        fun along(d: Float) = (from.first + dx / len * d) to (from.second + dy / len * d)
        return mapOf("hip" to along(TORSO), "knee" to along(TORSO + THIGH), "ankle" to along(TORSO + THIGH + SHANK))
    }

    /** A forearm plank: elbows under the shoulders, forearms flat, one straight line to the heels. */
    fun forearmPlank(heading: V3 = V3(0f, 0f, 1f), shoulderAt: V3 = V3(0f, 0f, 0f), sagDeg: Float = 0f): Skeleton {
        val shoulderY = 0.04f + UPPER_ARM
        val bodyLength = TORSO + THIGH + SHANK
        val drop = shoulderY - ANKLE_HEIGHT
        val run = sqrt(bodyLength * bodyLength - drop * drop)
        val body = straightBody(0f to shoulderY, -run to ANKLE_HEIGHT).toMutableMap()
        if (sagDeg != 0f) {
            // The hips drop out of the line: the legs pivot at the toes.
            val (hx, hy) = body.getValue("hip")
            body["hip"] = hx to hy - TORSO * sin(deg(sagDeg))
        }
        return floorPose(
            body + mapOf(
                "shoulder" to (0f to shoulderY),
                "elbow" to (0f to 0.04f),
                "wrist" to (FOREARM to 0.04f),
                "nose" to (NOSE_ABOVE_SHOULDER to shoulderY - NOSE_FORWARD),
            ),
            heading, shoulderAt,
        )
    }

    /** On all fours: hands under the shoulders, knees under the hips, shins on the floor. */
    fun allFours(heading: V3 = V3(0f, 0f, 1f), shoulderAt: V3 = V3(0f, 0f, 0f)): Skeleton {
        val shoulderY = 0.04f + UPPER_ARM + FOREARM
        val kneeY = 0.06f
        val hipY = kneeY + THIGH
        val back = sqrt(TORSO * TORSO - (shoulderY - hipY) * (shoulderY - hipY))
        return floorPose(
            mapOf(
                "shoulder" to (0f to shoulderY),
                "elbow" to (0f to 0.04f + FOREARM),
                "wrist" to (0f to 0.04f),
                "hip" to (-back to hipY),
                "knee" to (-back to kneeY),
                "ankle" to (-back - SHANK to kneeY),
                "nose" to (NOSE_ABOVE_SHOULDER to shoulderY),
            ),
            heading, shoulderAt,
        )
    }

    /** A plank from the knees: straight from knee to shoulder, shins on the floor. */
    fun kneePlank(heading: V3 = V3(0f, 0f, 1f), shoulderAt: V3 = V3(0f, 0f, 0f)): Skeleton {
        val shoulderY = 0.04f + UPPER_ARM + FOREARM
        val kneeY = 0.06f
        val run = sqrt((TORSO + THIGH) * (TORSO + THIGH) - (shoulderY - kneeY) * (shoulderY - kneeY))
        val line = straightBody(0f to shoulderY, -run to kneeY)
        val knee = line.getValue("knee")
        return floorPose(
            mapOf(
                "shoulder" to (0f to shoulderY),
                "elbow" to (0f to 0.04f + FOREARM),
                "wrist" to (0f to 0.04f),
                "hip" to line.getValue("hip"),
                "knee" to knee,
                "ankle" to (knee.first - SHANK to kneeY),
                "nose" to (NOSE_ABOVE_SHOULDER to shoulderY),
            ),
            heading, shoulderAt,
        )
    }

    private data class Leg(val side: Float, val hip: Int, val knee: Int, val ankle: Int)
    private data class Arm(val side: Float, val shoulder: Int, val elbow: Int, val wrist: Int)
    private data class Shoulder(val side: Float, val shoulder: Int)

    private val SIDES_LEG = listOf(
        Leg(1f, Lm.LEFT_HIP, Lm.LEFT_KNEE, Lm.LEFT_ANKLE),
        Leg(-1f, Lm.RIGHT_HIP, Lm.RIGHT_KNEE, Lm.RIGHT_ANKLE),
    )
    private val SIDES_ARM = listOf(
        Arm(1f, Lm.LEFT_SHOULDER, Lm.LEFT_ELBOW, Lm.LEFT_WRIST),
        Arm(-1f, Lm.RIGHT_SHOULDER, Lm.RIGHT_ELBOW, Lm.RIGHT_WRIST),
    )
    private val SIDES_SHOULDER = listOf(
        Shoulder(1f, Lm.LEFT_SHOULDER),
        Shoulder(-1f, Lm.RIGHT_SHOULDER),
    )

    /**
     * [count] reps of [pose] through [camera], with the same eased phase profile as
     * [PoseFixtures.trace] so the two families of fixture are directly comparable.
     */
    fun trace(
        pose: (Float) -> Skeleton,
        camera: Camera,
        count: Int,
        startMs: Long = 3_600_000L,
        peakDepth: Float = 0.95f,
        descentMs: Int = 1000,
        bottomMs: Int = 200,
        ascentMs: Int = 1000,
        restMs: Int = 400,
        fps: Int = 30,
        settleMs: Int = 800,
        world: Boolean = true,
    ): List<PoseFrame> = PoseFixtures.trace(
        count = count, startMs = startMs, peakDepth = peakDepth,
        descentMs = descentMs, bottomMs = bottomMs, ascentMs = ascentMs,
        restMs = restMs, fps = fps, settleMs = settleMs,
        frameOf = { t, d -> frame(t, pose(d), camera, world) },
    )
}
