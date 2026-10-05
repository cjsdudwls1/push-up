package com.pushuprpg.core.sensor

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.OutputStream
import kotlin.math.roundToLong

/**
 * A set recorded from the phone's motion sensors, for replaying through a sensor detector on any
 * machine — the sensor counterpart of [com.pushuprpg.core.trace.PoseTrace].
 *
 * [notes] carries what a test needs to judge a replay: the movement, and how many reps the person
 * says they actually did.
 */
@Serializable
data class SensorTrace(
    val version: Int = FORMAT_VERSION,
    /** Free-form, for the device and app build that produced it. */
    val device: String = "",
    val notes: String = "",
    /** In the order they arrived, the two sensors interleaved. */
    val samples: List<SensorSample>,
) {
    /**
     * The trace at the precision a sensor has rather than the one a float carries: a thousandth of
     * a m/s² or rad/s, well under either sensor's noise. A quantized replay counts what the original
     * counted.
     */
    fun quantized(): SensorTrace = copy(
        samples = samples.map { s ->
            s.copy(x = round(s.x), y = round(s.y), z = round(s.z))
        },
    )

    /** Hands every sample to [sink] in recorded order, as the phone's sensor thread did. */
    fun replay(sink: SensorSampleSink) = samples.forEach(sink::onSample)

    companion object {
        const val FORMAT_VERSION = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(trace: SensorTrace): String = json.encodeToString(trace)

        /** Streamed, as a pose trace is: ten minutes of samples is a few megabytes of text. */
        @OptIn(ExperimentalSerializationApi::class)
        fun encodeTo(trace: SensorTrace, out: OutputStream) = json.encodeToStream(trace, out)

        fun decode(text: String): SensorTrace = json.decodeFromString(text)

        private fun round(value: Float): Float = ((value * 1000.0).roundToLong() / 1000.0).toFloat()
    }
}

/**
 * Accumulates samples during a live set. Not thread-safe: the app takes a lock around it, since
 * samples arrive on the sensor thread and the trace is built on the main one.
 */
class SensorTraceRecorder(
    private val device: String = "",
    private val maxSamples: Int = DEFAULT_MAX_SAMPLES,
) : SensorSampleSink {
    private val samples = ArrayDeque<SensorSample>(4096)

    val sampleCount: Int get() = samples.size

    override fun onSample(sample: SensorSample) {
        // Bounded, as the pose recorder is: a recorder left running keeps the latest minutes.
        if (samples.size >= maxSamples) samples.removeFirst()
        samples.addLast(sample)
    }

    fun build(notes: String = ""): SensorTrace =
        SensorTrace(device = device, notes = notes, samples = samples.toList())

    companion object {
        /** About ten minutes of both sensors at the 50 Hz the app asks for. */
        const val DEFAULT_MAX_SAMPLES = 60_000
    }
}
