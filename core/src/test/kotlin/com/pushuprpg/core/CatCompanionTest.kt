package com.pushuprpg.core

import com.pushuprpg.core.audio.SoundCue
import com.pushuprpg.core.audio.SoundRequest
import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.RepGrade
import com.pushuprpg.core.survival.CatCompanion
import com.pushuprpg.core.survival.CatLine
import com.pushuprpg.core.survival.CatMood
import com.pushuprpg.core.survival.CatSpeech
import com.pushuprpg.core.survival.CatView
import com.pushuprpg.core.survival.CeilingSurvival
import com.pushuprpg.core.survival.SurvivalEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatCompanionTest {

    /** A survival run with the cat watching it, one 30 fps frame at a time. */
    private class Run {
        val config = DetectorConfig.pushup()
        val game = CeilingSurvival(config)
        val cat = CatCompanion()
        var t = 0L
        val sounds = mutableListOf<Pair<Long, SoundRequest>>()
        val lines = mutableListOf<CatSpeech>()
        val moods = mutableListOf<CatMood>()

        fun frame(inPosition: Boolean = true, rep: RepGrade? = null, hold: Boolean = false) {
            t += 33
            val events = buildList {
                if (rep != null) addAll(game.onRep(rep, depthFor(rep), t))
                if (hold) addAll(game.onHold(formScore = 80f, seconds = 0.5f, atMs = t))
                addAll(game.update(t, inPosition))
            }
            cat.update(game.state(), events, t).forEach { sounds += t to it }
            val view = cat.view()
            view.speech?.let { if (lines.lastOrNull() != it) lines += it }
            if (moods.lastOrNull() != view.mood) moods += view.mood
        }

        /** A depth the detector would have given [grade]; a shallow one still tried, so it still lifts. */
        private fun depthFor(grade: RepGrade): Float = when (grade) {
            RepGrade.DEEP -> config.deepEnter
            RepGrade.COUNTED -> config.countEnter
            RepGrade.SHALLOW -> config.countEnter * 0.8f
        }

        fun framesFor(ms: Long, inPosition: Boolean = true) {
            val end = t + ms
            while (t < end && game.state().alive) frame(inPosition)
        }

        fun until(limitMs: Long = 120_000, condition: (CatView) -> Boolean) {
            val end = t + limitMs
            while (!condition(cat.view()) && t < end && game.state().alive) frame()
            check(condition(cat.view())) { "never reached the condition by ${t}ms" }
        }

        fun played(cue: SoundCue): List<Pair<Long, SoundRequest>> = sounds.filter { it.second.cue == cue }
    }

    @Test
    fun `before the run the cat asks for help and keeps asking until it starts`() {
        val run = Run()
        run.framesFor(60_000, inPosition = false)
        assertEquals(CatLine.WAITING, run.cat.view().speech?.line, "the waiting bubble timed out")
        assertEquals(1, run.lines.size, "the waiting line was said more than once: ${run.lines}")
        assertTrue(run.played(SoundCue.CAT_PURR).size >= 10, "no purr while waiting")

        run.frame(inPosition = true)
        assertEquals(CatLine.HELLO, run.cat.view().speech?.line)
        assertTrue(run.played(SoundCue.GO).isNotEmpty())
    }

    @Test
    fun `left alone, the cat goes through every stage of fear once, in order, crying louder`() {
        val run = Run()
        run.frame()
        while (run.game.state().alive) run.frame()

        assertEquals(listOf(CatMood.CALM, CatMood.UNEASY, CatMood.SCARED, CatMood.PANIC), run.moods)
        val said = run.lines.map { it.line }.filter { it != CatLine.HELLO }
        assertEquals(listOf(CatLine.UNEASY, CatLine.SCARED, CatLine.PANIC), said)

        assertEquals(1, run.played(SoundCue.CAT_MEOW).size)
        val cries = run.played(SoundCue.CAT_CRY).map { it.second }
        assertEquals(2, cries.size)
        assertTrue(cries[1].rate > cries[0].rate, "panic should cry higher than fear: $cries")
    }

    @Test
    fun `a ceiling sitting on a threshold does not flick the face back and forth`() {
        var mood = CatMood.CALM
        val seen = mutableListOf(mood)
        // Wobbling a hair either side of the scared line, as a ceiling does when the reps just
        // keep pace with it.
        for (i in 0 until 200) {
            val h = CatCompanion.SCARED_BELOW + if (i % 2 == 0) -0.01f else 0.02f
            mood = CatCompanion.moodFor(h, mood)
            if (seen.last() != mood) seen += mood
        }
        assertEquals(listOf(CatMood.CALM, CatMood.SCARED), seen)

        // And a clear lift does calm it.
        assertEquals(CatMood.UNEASY, CatCompanion.moodFor(CatCompanion.SCARED_BELOW + CatCompanion.CALM_MARGIN + 0.01f, CatMood.SCARED))
    }

    @Test
    fun `a push that lifts the ceiling out of danger is the cat being saved`() {
        val run = Run()
        run.until { it.mood == CatMood.SCARED }
        run.frame(rep = RepGrade.DEEP)

        val view = run.cat.view()
        assertTrue(view.mood < CatMood.SCARED, "one deep rep from the edge of fear should calm it: ${view.mood}")
        assertEquals(CatLine.SAVED, view.speech?.line)
        assertEquals(1, run.played(SoundCue.CAT_HAPPY).size)
    }

    @Test
    fun `panic easing into fear is not called saved`() {
        val run = Run()
        run.until { it.mood == CatMood.PANIC }
        // Shallow reps: together enough to leave panic, not enough to leave fear.
        repeat(4) { run.frame(rep = RepGrade.SHALLOW) }
        assertEquals(CatMood.SCARED, run.cat.view().mood)
        assertTrue(run.lines.none { it.line == CatLine.SAVED }, "said saved while still frightened: ${run.lines}")
    }

    @Test
    fun `every push brightens the cat for a moment, more for a deeper one`() {
        val run = Run()
        run.framesFor(1_000)
        assertEquals(0f, run.cat.view().cheer)

        run.frame(rep = RepGrade.DEEP)
        assertEquals(3, run.cat.view().hearts)
        assertTrue(run.cat.view().cheer > 0.9f)

        run.framesFor(CatCompanion.CHEER_MS + 100)
        assertEquals(0f, run.cat.view().cheer)
        assertEquals(0, run.cat.view().hearts)

        run.frame(rep = RepGrade.SHALLOW)
        assertEquals(1, run.cat.view().hearts)
    }

    @Test
    fun `the heartbeat starts with fear and quickens as the ceiling closes`() {
        val run = Run()
        run.frame()
        while (run.game.state().alive) run.frame()

        val beats = run.played(SoundCue.HEARTBEAT)
        assertTrue(beats.size >= 4, "barely any heartbeat: $beats")
        val gaps = beats.zipWithNext { a, b -> b.first - a.first }
        assertTrue(gaps.last() < gaps.first(), "the heartbeat did not quicken: $gaps")
        assertTrue(beats.last().second.volume > beats.first().second.volume)

        // And none before the cat was frightened.
        val firstScaredLine = run.lines.first { it.line == CatLine.SCARED }.atMs
        assertTrue(beats.first().first >= firstScaredLine)
    }

    @Test
    fun `the creak comes faster and louder as the ceiling closes`() {
        val run = Run()
        run.frame()
        while (run.game.state().alive) run.frame()
        val creaks = run.played(SoundCue.CEILING_CREAK)
        assertTrue(creaks.size >= 3, "creaks: $creaks")
        assertTrue(creaks.last().second.volume > creaks.first().second.volume)
        val gaps = creaks.zipWithNext { a, b -> b.first - a.first }
        assertTrue(gaps.last() < gaps.first(), "the creak did not quicken: $gaps")
    }

    @Test
    fun `the cat goes quiet when the run ends, and the result card speaks instead`() {
        val run = Run()
        run.frame()
        while (run.game.state().alive) run.frame()
        assertNull(run.cat.view().speech)
        assertEquals(1, run.played(SoundCue.DEFEAT).size)

        val soundsAtEnd = run.sounds.size
        repeat(100) {
            run.t += 33
            run.cat.update(run.game.state(), run.game.update(run.t), run.t).forEach { s -> run.sounds += run.t to s }
        }
        assertEquals(soundsAtEnd, run.sounds.size, "the cat kept making noise after the run ended")
    }

    @Test
    fun `a plank pushes the ceiling twice a second but thumps once`() {
        val run = Run()
        run.frame()
        repeat(40) { i -> run.frame(hold = i % 15 == 0) }
        val start = run.t
        repeat(300) { i -> run.frame(hold = i % 15 == 0) } // every ~0.5 s, for ten seconds
        val thumps = run.played(SoundCue.CEILING_PUSH).filter { it.first > start }
        assertTrue(thumps.size in 8..11, "expected about one push sound a second, got ${thumps.size}")
    }

    @Test
    fun `a streak of ten is called out`() {
        val run = Run()
        run.frame()
        repeat(10) {
            run.frame(rep = RepGrade.COUNTED)
            run.framesFor(400)
        }
        val combo = run.lines.single { it.line == CatLine.COMBO }
        assertEquals(10, combo.arg)
        assertTrue(run.played(SoundCue.COMBO_UP).isNotEmpty())
    }

    @Test
    fun `the same run gives the same cat`() {
        fun play(): List<Pair<Long, SoundRequest>> {
            val run = Run()
            run.frame()
            var i = 0
            while (run.game.state().alive && run.t < 90_000) {
                run.frame(rep = if (i++ % 45 == 0) RepGrade.DEEP else null)
            }
            return run.sounds
        }
        assertEquals(play(), play())
    }

    @Test
    fun `a retry starts the cat over but not its wording`() {
        val run = Run()
        run.frame()
        run.until { it.speech?.line == CatLine.UNEASY }
        val firstUneasy = run.cat.view().speech!!.serial

        run.game.reset()
        run.cat.reset()
        assertEquals(CatMood.CALM, run.cat.view().mood)
        run.frame()
        run.until { it.speech?.line == CatLine.UNEASY }
        assertEquals(firstUneasy + 1, run.cat.view().speech!!.serial, "the retry reused the first wording")
    }

    @Test
    fun `hearts belong to accepted pushes only, and a near miss is encouraged rather than scolded`() {
        val run = Run()
        run.frame()
        // Far short of the line: the game calls it a near miss and lifts nothing.
        run.t += 33
        val events = run.game.onRep(RepGrade.SHALLOW, depth = 0.05f, atMs = run.t) + run.game.update(run.t)
        run.cat.update(run.game.state(), events, run.t)
        assertEquals(CatLine.NEAR_MISS, run.cat.view().speech?.line)
        assertEquals(0, run.cat.view().hearts)
        assertTrue(events.none { it is SurvivalEvent.Pushed })
    }
}
