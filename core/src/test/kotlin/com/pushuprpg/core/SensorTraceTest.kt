package com.pushuprpg.core

import com.pushuprpg.core.sensor.SensorKind
import com.pushuprpg.core.sensor.SensorSample
import com.pushuprpg.core.sensor.SensorTrace
import com.pushuprpg.core.sensor.SensorTraceRecorder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SensorTraceTest {

    /** Two seconds of a phone bobbing at 50 Hz: accelerometer and gyroscope interleaved. */
    private fun samples(): List<SensorSample> = (0 until 100).flatMap { i ->
        val t = 1_000_000L + i * 20L
        val phase = (2 * PI * i / 50).toFloat()
        listOf(
            SensorSample(t, SensorKind.ACCEL, 0.1234567f, 9.81f + 2f * sin(phase), 0.5f),
            SensorSample(t + 1, SensorKind.GYRO, 0.01f * sin(phase), -0.002f, 0.3333333f),
        )
    }

    @Test
    fun `a trace round-trips through json unchanged`() {
        val recorder = SensorTraceRecorder(device = "test")
        samples().forEach(recorder::onSample)
        val original = recorder.build(notes = "movement=PUSHUP reps=1")

        assertEquals(original, SensorTrace.decode(SensorTrace.encode(original)))
    }

    @Test
    fun `quantizing keeps the time and kind exactly and the values to a thousandth`() {
        val original = SensorTrace(samples = samples())
        val quantized = SensorTrace.decode(SensorTrace.encode(original.quantized()))

        assertEquals(original.samples.map { it.timestampMs to it.kind }, quantized.samples.map { it.timestampMs to it.kind })
        original.samples.zip(quantized.samples).forEach { (a, b) ->
            assertTrue(kotlin.math.abs(a.x - b.x) <= 0.0005f)
            assertTrue(kotlin.math.abs(a.y - b.y) <= 0.0005f)
            assertTrue(kotlin.math.abs(a.z - b.z) <= 0.0005f)
        }
    }

    @Test
    fun `a replay hands over every sample in recorded order`() {
        val trace = SensorTrace(samples = samples())
        val seen = mutableListOf<SensorSample>()
        trace.replay { seen += it }
        assertEquals(trace.samples, seen)
    }

    @Test
    fun `a full recorder keeps the latest samples`() {
        val recorder = SensorTraceRecorder(maxSamples = 10)
        val all = samples()
        all.forEach(recorder::onSample)

        val kept = recorder.build().samples
        assertEquals(10, kept.size)
        assertEquals(all.takeLast(10), kept)
    }

    @Test
    fun `a trace written by a newer build with more fields still reads`() {
        val text = """{"version":2,"device":"x","notes":"","extra":1,
            "samples":[{"timestampMs":5,"kind":"GYRO","x":1.0,"y":2.0,"z":3.0,"accuracy":3}]}"""
        assertEquals(SensorSample(5, SensorKind.GYRO, 1f, 2f, 3f), SensorTrace.decode(text).samples.single())
    }
}
