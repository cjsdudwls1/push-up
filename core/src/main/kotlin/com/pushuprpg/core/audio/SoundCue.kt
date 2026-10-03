package com.pushuprpg.core.audio

import kotlin.math.pow

/**
 * The sound palette, in deliberately separated bands.
 *
 * FORM cues are dry and high (1.5–4 kHz); IMPACT cues are wet and low (60–250 Hz). Keeping them
 * apart spectrally is what lets both play on the same rep without either masking the other — and a
 * counted rep does fire both, because they answer different questions: "did that one count?" and
 * "did it push the ceiling?".
 *
 * CAT is 고양이 and its room, in the gap between them (roughly 300 Hz–1.4 kHz): a voice sits there
 * naturally, and it is the one band that never vibrates — a phone buzzing every time the cat purrs
 * would teach the user to ignore the buzz.
 */
enum class SoundCue(val band: Band) {
    REP_ACCEPT(Band.FORM),
    REP_DEEP(Band.FORM),
    GO(Band.FORM),
    COMBO_UP(Band.FORM),

    DEFEAT(Band.IMPACT),
    CEILING_PUSH(Band.IMPACT),
    /** Felt as well as heard: the one survival sound worth a buzz, because it is a pulse. */
    HEARTBEAT(Band.IMPACT),

    CAT_PURR(Band.CAT),
    CAT_MEOW(Band.CAT),
    CAT_CRY(Band.CAT),
    CAT_HAPPY(Band.CAT),
    CEILING_CREAK(Band.CAT),
    ;

    enum class Band { FORM, IMPACT, CAT }
}

/** One sound to play. [rate] is a playback-speed multiplier, [volume] a 0..1 gain. */
data class SoundRequest(
    val cue: SoundCue,
    val rate: Float = 1f,
    val volume: Float = 1f,
)

object ComboPitch {

    /**
     * Playback rate for a push at the given combo.
     *
     * The thump climbs a semitone per rep, which makes a combo *audible*: someone with their eyes
     * on the floor, which during a set is everyone, hears the chain rising and hears it reset.
     *
     * Capped at an octave because SoundPool's rate tops out at 2.0, and because past that it stops
     * sounding like momentum and starts sounding like a fault.
     */
    fun of(combo: Int): Float {
        val semitones = (combo - 1).coerceIn(0, MAX_SEMITONES)
        return 2f.pow(semitones / 12f)
    }

    const val MAX_SEMITONES = 12
}
