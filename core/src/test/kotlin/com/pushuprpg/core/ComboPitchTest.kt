package com.pushuprpg.core

import com.pushuprpg.core.audio.ComboPitch
import com.pushuprpg.core.audio.SoundCue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComboPitchTest {

    @Test
    fun `a combo is audible as a rising pitch, and its reset is audible too`() {
        // The point of the whole audio design: the game has to be playable by someone who cannot
        // look at the screen, which during a set is everyone.
        assertEquals(1f, ComboPitch.of(1))
        assertTrue(ComboPitch.of(5) > ComboPitch.of(2))
        assertTrue(ComboPitch.of(12) > ComboPitch.of(8))
    }

    @Test
    fun `the pitch stays inside what a sampler can actually play`() {
        // SoundPool tops out at 2.0, and past an octave it stops sounding like momentum anyway.
        for (combo in listOf(0, 1, 50, 500, 100_000)) {
            val rate = ComboPitch.of(combo)
            assertTrue(rate in 0.5f..2f, "combo $combo gave rate $rate")
        }
    }

    @Test
    fun `every band has a cue`() {
        // The split is the reason the form and impact cues can share a rep without masking each
        // other; a band left empty would mean a cue had wandered into the wrong one.
        for (band in SoundCue.Band.entries) {
            assertTrue(SoundCue.entries.any { it.band == band }, "no cue in $band")
        }
    }
}
