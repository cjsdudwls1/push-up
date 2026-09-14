package com.pushuprpg.core

import com.pushuprpg.core.audio.BattleAudio
import com.pushuprpg.core.audio.SoundCue
import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.RepDetectorImpl
import com.pushuprpg.core.fixtures.PoseFixtures
import com.pushuprpg.core.game.*
import com.pushuprpg.core.run.BattleEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BattleAudioTest {

    private fun engine() = BattleEngine(
        dungeon = Dungeons.FREE_DUNGEON,
        difficulty = Difficulty.STANDARD,
        capacity = 8f,
        initialPlayer = PlayerState.create(PlayerClass.KNIGHT, level = 1),
        detector = RepDetectorImpl(DetectorConfig.pushup()),
        resolver = CombatResolver(),
    )

    @Test
    fun `a combo is audible as a rising pitch, and its reset is audible too`() {
        // The point of the whole audio design: the game has to be playable by someone who cannot
        // look at the screen, which during a set is everyone.
        assertEquals(1f, BattleAudio.pitchForCombo(1))
        assertTrue(BattleAudio.pitchForCombo(5) > BattleAudio.pitchForCombo(2))
        assertTrue(BattleAudio.pitchForCombo(12) > BattleAudio.pitchForCombo(8))
    }

    @Test
    fun `the pitch stays inside what a sampler can actually play`() {
        // SoundPool tops out at 2.0, and past an octave it stops sounding like momentum anyway.
        for (combo in listOf(0, 1, 50, 500, 100_000)) {
            val rate = BattleAudio.pitchForCombo(combo)
            assertTrue(rate in 0.5f..2f, "combo $combo gave rate $rate")
        }
    }

    @Test
    fun `a counted rep plays one cue from each band`() {
        val e = engine()
        var firstRepSounds = emptyList<com.pushuprpg.core.audio.SoundRequest>()
        for (f in PoseFixtures.trace(count = 1, peakDepth = 0.80f)) {
            val s = e.onPoseFrame(f)
            if (s.sounds.isNotEmpty() && firstRepSounds.isEmpty()) firstRepSounds = s.sounds
        }
        val bands = firstRepSounds.map { it.cue.band }.toSet()
        assertTrue(SoundCue.Band.FORM in bands, "no form cue in $firstRepSounds")
        assertTrue(SoundCue.Band.COMBAT in bands, "no combat cue in $firstRepSounds")
    }

    @Test
    fun `a deep rep sounds different from a shallow-but-counted one`() {
        fun cuesFor(peak: Float): Set<SoundCue> {
            val e = engine()
            val cues = mutableSetOf<SoundCue>()
            for (f in PoseFixtures.trace(count = 3, peakDepth = peak)) {
                cues += e.onPoseFrame(f).sounds.map { it.cue }
            }
            return cues
        }
        assertTrue(SoundCue.REP_DEEP in cuesFor(0.97f))
        assertTrue(SoundCue.REP_DEEP !in cuesFor(0.78f))
    }

    @Test
    fun `the run's end is announced`() {
        val e = engine()
        val cues = mutableSetOf<SoundCue>()
        for (f in PoseFixtures.trace(count = 80, peakDepth = 0.95f, restMs = 250)) {
            val s = e.onPoseFrame(f)
            cues += s.sounds.map { it.cue }
            if (s.outcome != null) break
        }
        assertTrue(SoundCue.ENEMY_DOWN in cues, "no kill was ever heard")
        assertTrue(SoundCue.VICTORY in cues, "the clear was silent")
    }

    @Test
    fun `sounds do not repeat on the frames after the event`() {
        // They are per-frame requests, not state; leaking them would machine-gun the sampler.
        val e = engine()
        var framesWithSound = 0
        var totalFrames = 0
        for (f in PoseFixtures.trace(count = 4, peakDepth = 0.95f)) {
            val s = e.onPoseFrame(f)
            totalFrames++
            if (s.sounds.isNotEmpty()) framesWithSound++
        }
        assertTrue(framesWithSound in 1..20, "$framesWithSound of $totalFrames frames made noise")
    }

    @Test
    fun `every cue has a band`() {
        // The two-band split is the reason the form and combat cues can share a rep without
        // masking each other; a cue that skipped it would be the one that muddies everything.
        assertEquals(SoundCue.entries.size, SoundCue.entries.map { it.band }.size)
        assertTrue(SoundCue.entries.any { it.band == SoundCue.Band.FORM })
        assertTrue(SoundCue.entries.any { it.band == SoundCue.Band.COMBAT })
    }
}
