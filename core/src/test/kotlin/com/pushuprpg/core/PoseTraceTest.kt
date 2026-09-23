package com.pushuprpg.core

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.fixtures.Body3d
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.trace.PoseTrace
import com.pushuprpg.core.trace.TraceFrame
import com.pushuprpg.core.trace.TraceRecorder
import com.pushuprpg.core.trace.TraceReplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoseTraceTest {

    private fun recordTrace(reps: Int = 6): PoseTrace {
        val recorder = TraceRecorder(PoseFixtures.WIDTH, PoseFixtures.HEIGHT, device = "test")
        PoseFixtures.trace(count = reps, peakDepth = 0.95f).forEach(recorder::record)
        return recorder.build(notes = "$reps synthetic reps")
    }

    @Test
    fun `a trace round-trips through json unchanged`() {
        val original = recordTrace()
        val decoded = PoseTrace.decode(PoseTrace.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `replaying a trace reproduces the original run exactly`() {
        // The whole point of the format: a bug report becomes a regression test. If a replay could
        // drift from the live run it would be worthless for that.
        val trace = recordTrace(reps = 8)

        val first = TraceReplay.run(trace)
        val second = TraceReplay.run(trace)

        assertEquals(first.repCount, second.repCount)
        assertEquals(first.events.size, second.events.size)
        assertEquals(
            first.events.filterIsInstance<RepEvent.Strike>().map { it.tMs to it.depth },
            second.events.filterIsInstance<RepEvent.Strike>().map { it.tMs to it.depth },
        )
    }

    @Test
    fun `a replayed trace counts what the live detector counted`() {
        val detector = com.pushuprpg.core.detect.RepDetectorImpl(
            com.pushuprpg.core.detect.DetectorConfig.pushup()
        )
        val recorder = TraceRecorder(PoseFixtures.WIDTH, PoseFixtures.HEIGHT)
        PoseFixtures.trace(count = 9, peakDepth = 0.95f).forEach { frame ->
            recorder.record(frame)
            detector.onFrame(frame)
        }

        val replayed = TraceReplay.run(recorder.build())
        assertEquals(detector.sessionSummary().repCount, replayed.repCount)
    }

    @Test
    fun `frames with no pose survive the round trip`() {
        val recorder = TraceRecorder(640, 480)
        recorder.record(com.pushuprpg.core.pose.PoseFrame.empty(0L, 640, 480))
        recorder.record(PoseFixtures.frame(33L, 0f))
        val decoded = PoseTrace.decode(PoseTrace.encode(recorder.build()))
        val frames = TraceReplay.frames(decoded)

        assertEquals(2, frames.size)
        assertTrue(!frames[0].hasPose, "an empty frame must decode back to an empty frame")
        assertTrue(frames[1].hasPose)
        assertTrue(frames[1].hasWorld)
    }

    @Test
    fun `the recorder is bounded so a forgotten recording cannot grow forever`() {
        val recorder = TraceRecorder(640, 480, maxFrames = 50)
        repeat(500) { i -> recorder.record(PoseFixtures.frame(i * 33L, 0f)) }
        assertEquals(50, recorder.frameCount)
        // It keeps the most recent window, which is the part a bug report is about.
        assertEquals(499 * 33L, recorder.build().frames.last().t)
    }

    /**
     * What gets sent from a phone is the quantized trace, so it is the one that has to replay
     * faithfully. A projected pushup rather than a hand-placed fixture: it carries world landmarks
     * and model confidence, which are exactly the fields quantization touches.
     */
    @Test
    fun `a quantized trace replays to the same reps at the same moments`() {
        val recorder = TraceRecorder(480, 640, device = "rig")
        Body3d.trace({ Body3d.pushup(it, Body3d.V3(0f, 0f, 1f), Body3d.V3(0f, 0f, 0f)) },
            Body3d.Camera.onFloor(1.3f, 12f), count = 8).forEach(recorder::record)
        val original = recorder.build()
        val quantized = original.quantized()

        val config = Exercises.of(ExerciseType.PUSHUP).config
        val a = TraceReplay.run(original, config)
        val b = TraceReplay.run(quantized, config)
        assertEquals(8, a.repCount)
        assertEquals(a.repCount, b.repCount)
        assertEquals(
            a.events.filterIsInstance<RepEvent.Strike>().map { it.tMs },
            b.events.filterIsInstance<RepEvent.Strike>().map { it.tMs },
        )
        assertEquals(quantized, PoseTrace.decode(PoseTrace.encode(quantized)))
    }

    /**
     * The rig leaves twenty landmarks at zero, which already prints short, so the size claim is
     * measured on frames filled the way the model fills them: every landmark, every field.
     */
    @Test
    fun `quantizing takes about half off a trace the model actually produced`() {
        val random = java.util.Random(7)
        val frames = List(300) { n ->
            TraceFrame(
                t = n * 33L,
                lm = FloatArray(33 * TraceRecorder.STRIDE) { i ->
                    if (i % TraceRecorder.STRIDE >= 3) 0.5f + 0.5f * random.nextFloat() else random.nextFloat()
                },
                world = FloatArray(33 * TraceRecorder.WORLD_STRIDE) { random.nextFloat() - 0.5f },
            )
        }
        val trace = PoseTrace(imageWidth = 480, imageHeight = 640, frames = frames)
        val full = PoseTrace.encode(trace).length
        val small = java.io.ByteArrayOutputStream().also { PoseTrace.encodeTo(trace.quantized(), it) }.size()
        assertTrue(small < full * 0.65, "quantized $small bytes against $full")
    }
}
