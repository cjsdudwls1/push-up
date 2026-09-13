package com.pushuprpg.core

import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.trace.PoseTrace
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
}
