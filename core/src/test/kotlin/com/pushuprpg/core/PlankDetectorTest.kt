package com.pushuprpg.core

import com.pushuprpg.core.detect.*
import com.pushuprpg.core.fixtures.PoseFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlankDetectorTest {

    private fun run(frames: List<com.pushuprpg.core.pose.PoseFrame>, detector: PlankDetector = PlankDetector()):
        Pair<PlankDetector, List<RepEvent>> {
        val events = mutableListOf<RepEvent>()
        frames.forEach { events += detector.onFrame(it).events }
        return detector to events
    }

    private inline fun <reified T : RepEvent> List<RepEvent>.of(): List<T> = filterIsInstance<T>()

    @Test
    fun `a clean plank is recognised and ticks damage`() {
        val (detector, events) = run(PoseFixtures.plankTrace(durationMs = 10_000))
        assertTrue(events.of<RepEvent.HoldTick>().isNotEmpty(), "a good plank should tick")
        assertTrue(detector.sessionSummary().holdMs > 8_000, "held ${detector.sessionSummary().holdMs}ms of 10s")
        assertTrue(events.of<RepEvent.HoldTick>().all { it.damage > 0f })
    }

    @Test
    fun `a badly sagging plank does not count as a hold`() {
        val (detector, events) = run(
            PoseFixtures.plankTrace(durationMs = 10_000) { PoseFixtures.plankFrame(it, sagDegrees = 45f) }
        )
        assertTrue(events.of<RepEvent.HoldTick>().isEmpty(), "a collapsed plank must not score as one")
        assertEquals(0L, detector.sessionSummary().holdMs)
    }

    @Test
    fun `better form scores higher than worse form`() {
        fun meanScore(sag: Float): Float {
            val d = PlankDetector()
            var sum = 0f
            var n = 0
            PoseFixtures.plankTrace(5_000) { PoseFixtures.plankFrame(it, sagDegrees = sag) }
                .forEach { sum += d.onFrame(it).depth; n++ }
            return sum / n
        }
        val straight = meanScore(0f)
        val slight = meanScore(12f)
        val bad = meanScore(30f)
        assertTrue(straight > slight && slight > bad, "straight=$straight slight=$slight bad=$bad")
    }

    @Test
    fun `the charge meter fills and resets`() {
        val (_, events) = run(PoseFixtures.plankTrace(durationMs = 30_000))
        val full = events.of<RepEvent.ChargeFull>()
        assertTrue(full.isNotEmpty(), "30s of a perfect plank should fill the charge at least once")
        assertEquals(full.map { it.chargeIndex }.sorted(), full.map { it.chargeIndex })

        val ticks = events.of<RepEvent.HoldTick>()
        assertTrue(ticks.any { it.charge > 50f }, "charge should visibly build")
    }

    @Test
    fun `a brief wobble does not break the hold`() {
        val frames = mutableListOf<com.pushuprpg.core.pose.PoseFrame>()
        var t = 0L
        repeat(120) { frames += PoseFixtures.plankFrame(t); t += 33 }
        // Half a second of collapse, inside the grace window.
        repeat(15) { frames += PoseFixtures.plankFrame(t, sagDegrees = 50f); t += 33 }
        repeat(120) { frames += PoseFixtures.plankFrame(t); t += 33 }

        val (_, events) = run(frames)
        assertTrue(events.of<RepEvent.HoldBroken>().isEmpty(),
            "a half-second shiver should not end the plank")
    }

    @Test
    fun `a real collapse breaks the hold and reports how long it lasted`() {
        val frames = mutableListOf<com.pushuprpg.core.pose.PoseFrame>()
        var t = 0L
        repeat(200) { frames += PoseFixtures.plankFrame(t); t += 33 }
        repeat(150) { frames += PoseFixtures.plankFrame(t, sagDegrees = 55f); t += 33 }

        val (_, events) = run(frames)
        val broken = events.of<RepEvent.HoldBroken>().firstOrNull()
        assertTrue(broken != null, "a five-second collapse should end the plank")
        assertTrue(broken!!.longestUnbrokenMs > 5_000, "held ${broken.longestUnbrokenMs}ms")
    }

    @Test
    fun `losing the user ends the hold rather than counting time they were not there`() {
        val frames = mutableListOf<com.pushuprpg.core.pose.PoseFrame>()
        var t = 0L
        repeat(150) { frames += PoseFixtures.plankFrame(t); t += 33 }
        repeat(60) { frames += com.pushuprpg.core.pose.PoseFrame.empty(t, PoseFixtures.WIDTH, PoseFixtures.HEIGHT); t += 33 }

        val (detector, events) = run(frames)
        assertTrue(events.of<RepEvent.HoldBroken>().isNotEmpty())
        assertTrue(detector.sessionSummary().holdMs < 6_000, "counted ${detector.sessionSummary().holdMs}ms")
    }

    @Test
    fun `a plank still scores when the legs are out of frame`() {
        // The realistic case with a phone on the floor. Weights renormalise over what is visible
        // rather than defaulting the missing component, so the user is judged on what can be seen.
        val (detector, events) = run(
            PoseFixtures.plankTrace(8_000) { PoseFixtures.plankFrame(it, legConfidence = 0.1f) }
        )
        assertTrue(events.of<RepEvent.HoldTick>().isNotEmpty(),
            "a legs-out-of-frame plank must still be holdable")
        assertTrue(detector.sessionSummary().holdMs > 5_000)
    }

    @Test
    fun `hip drift away from the starting position lowers the score`() {
        fun meanScore(drift: Float): Float {
            val d = PlankDetector()
            // Let the reference settle at a neutral position first.
            PoseFixtures.plankTrace(2_000).forEach { d.onFrame(it) }
            var sum = 0f
            var n = 0
            PoseFixtures.plankTrace(3_000, startMs = 2_000) { PoseFixtures.plankFrame(it, drift = drift) }
                .forEach { sum += d.onFrame(it).depth; n++ }
            return sum / n
        }
        assertTrue(meanScore(0f) > meanScore(0.35f), "drifting hips should cost score")
    }

    @Test
    fun `plank time lands in the session summary as seconds`() {
        val (detector, _) = run(PoseFixtures.plankTrace(durationMs = 12_000))
        val summary = detector.sessionSummary()
        assertEquals(ExerciseType.PLANK, summary.exercise)
        // A plank's "reps" are seconds held, so time under tension reaches the same stats as reps.
        assertTrue(summary.repCount in 8..12, "reported ${summary.repCount} seconds")
        assertTrue(summary.qualityAvg > 60f)
    }

    @Test
    fun `the factory hands back a plank detector for plank`() {
        assertTrue(DetectorFactory.create(ExerciseType.PLANK) is PlankDetector)
        assertTrue(DetectorFactory.create(ExerciseType.PUSHUP) is RepDetectorImpl)
    }
}
