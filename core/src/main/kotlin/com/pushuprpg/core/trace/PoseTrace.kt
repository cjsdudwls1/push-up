package com.pushuprpg.core.trace

import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.PoseTick
import com.pushuprpg.core.detect.RepDetector
import com.pushuprpg.core.detect.RepDetectorImpl
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.detect.SessionSummary
import com.pushuprpg.core.detect.UserProfile
import com.pushuprpg.core.pose.Landmark
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.pose.PoseLandmarks
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A recorded session, as landmarks rather than video.
 *
 * Every constant in the detector is currently reasoned from anthropometry and projection geometry
 * and validated against synthetic traces. That is a defensible starting point and nothing more:
 * until the thresholds have been run against real people, in real rooms, on real phones, they are
 * hypotheses. This is the format that turns a bug report — "it stopped counting my last three
 * reps" — into a regression test that runs in twenty seconds on any machine.
 *
 * Landmarks and not video, deliberately. A trace is a few hundred kilobytes of numbers with no
 * image in it, which makes it something a user can actually be asked to send: there is nothing
 * recognisable in it, and nothing that needs handling as a recording of somebody's home.
 */
@Serializable
data class PoseTrace(
    val version: Int = FORMAT_VERSION,
    val imageWidth: Int,
    val imageHeight: Int,
    /** Free-form, for the device and app build that produced it. */
    val device: String = "",
    val notes: String = "",
    val frames: List<TraceFrame>,
) {
    companion object {
        const val FORMAT_VERSION = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(trace: PoseTrace): String = json.encodeToString(trace)

        fun decode(text: String): PoseTrace = json.decodeFromString(text)
    }
}

/**
 * One frame.
 *
 * Landmarks are stored as flat float arrays rather than as objects: a two-minute session at 30fps
 * is 3,600 frames, and the object form costs several times the bytes for nothing a reader needs.
 */
@Serializable
data class TraceFrame(
    val t: Long,
    /** `[x, y, z, visibility, presence] * 33`, or empty when no pose was detected. */
    val lm: FloatArray = FloatArray(0),
    /** `[x, y, z] * 33`, or empty. */
    val world: FloatArray = FloatArray(0),
) {
    // Data classes with array members need these written out; the generated ones compare by
    // identity, which would make two identical frames unequal and break any test that compares
    // a decoded trace with the one it was encoded from.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TraceFrame) return false
        return t == other.t && lm.contentEquals(other.lm) && world.contentEquals(other.world)
    }

    override fun hashCode(): Int {
        var result = t.hashCode()
        result = 31 * result + lm.contentHashCode()
        result = 31 * result + world.contentHashCode()
        return result
    }
}

/** Accumulates frames during a live session. Cheap enough to leave running in a debug build. */
class TraceRecorder(
    private val imageWidth: Int,
    private val imageHeight: Int,
    private val device: String = "",
    private val maxFrames: Int = DEFAULT_MAX_FRAMES,
) {
    private val frames = ArrayList<TraceFrame>(1024)

    val frameCount: Int get() = frames.size

    fun record(frame: PoseFrame) {
        // A bounded buffer rather than an unbounded one: a forgotten recorder on a long session
        // should degrade into keeping the most recent few minutes, not into an OOM.
        if (frames.size >= maxFrames) frames.removeAt(0)
        frames.add(
            TraceFrame(
                t = frame.timestampMs,
                lm = flatten(frame.landmarks, STRIDE),
                world = flatten(frame.worldLandmarks, WORLD_STRIDE),
            )
        )
    }

    fun build(notes: String = ""): PoseTrace = PoseTrace(
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        device = device,
        notes = notes,
        frames = frames.toList(),
    )

    fun clear() = frames.clear()

    private fun flatten(landmarks: List<Landmark>, stride: Int): FloatArray {
        if (landmarks.isEmpty()) return FloatArray(0)
        val out = FloatArray(landmarks.size * stride)
        landmarks.forEachIndexed { i, lm ->
            val o = i * stride
            out[o] = lm.x
            out[o + 1] = lm.y
            out[o + 2] = lm.z
            if (stride == STRIDE) {
                out[o + 3] = lm.visibility
                out[o + 4] = lm.presence
            }
        }
        return out
    }

    companion object {
        const val STRIDE = 5
        const val WORLD_STRIDE = 3

        /** About four minutes at 30fps. */
        const val DEFAULT_MAX_FRAMES = 7_200
    }
}

/** The result of running a trace back through the detector. */
data class ReplayResult(
    val summary: SessionSummary,
    val events: List<RepEvent>,
    val ticks: List<PoseTick>,
) {
    val repCount: Int get() = summary.repCount
}

object TraceReplay {

    fun frames(trace: PoseTrace): List<PoseFrame> = trace.frames.map { f ->
        if (f.lm.isEmpty()) {
            PoseFrame.empty(f.t, trace.imageWidth, trace.imageHeight)
        } else {
            PoseFrame.fromFlatArray(
                values = f.lm,
                world = f.world.takeIf { it.isNotEmpty() },
                timestampMs = f.t,
                imageWidth = trace.imageWidth,
                imageHeight = trace.imageHeight,
            )
        }
    }

    /**
     * Replays a trace through a fresh detector.
     *
     * [keepTicks] is off by default because a long trace produces thousands of ticks, each holding
     * a render skeleton, and a test that only wants the rep count should not pay for that.
     */
    fun run(
        trace: PoseTrace,
        config: DetectorConfig = DetectorConfig.pushup(),
        profile: UserProfile = UserProfile.empty(),
        keepTicks: Boolean = false,
    ): ReplayResult = run(RepDetectorImpl(config, profile), trace, keepTicks)

    fun run(detector: RepDetector, trace: PoseTrace, keepTicks: Boolean = false): ReplayResult {
        val events = mutableListOf<RepEvent>()
        val ticks = if (keepTicks) mutableListOf<PoseTick>() else null
        for (frame in frames(trace)) {
            val tick = detector.onFrame(frame)
            events += tick.events
            ticks?.add(tick)
        }
        return ReplayResult(detector.sessionSummary(), events, ticks ?: emptyList())
    }
}
