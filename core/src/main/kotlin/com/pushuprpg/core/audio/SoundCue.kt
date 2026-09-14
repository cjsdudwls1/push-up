package com.pushuprpg.core.audio

import kotlin.math.pow

/**
 * The sound palette, in two deliberately separated bands.
 *
 * FORM cues are dry and high (1.5–4 kHz); COMBAT cues are wet and low (60–250 Hz). Keeping them
 * apart spectrally is what lets both play on the same rep without either masking the other — and a
 * counted rep does fire both, because they answer different questions: "did that one count?" and
 * "did it hurt the thing?".
 */
enum class SoundCue(val band: Band) {
    REP_ACCEPT(Band.FORM),
    REP_DEEP(Band.FORM),
    COUNTDOWN(Band.FORM),
    GO(Band.FORM),
    COMBO_UP(Band.FORM),
    COMBO_BREAK(Band.FORM),

    HIT(Band.COMBAT),
    HIT_HEAVY(Band.COMBAT),
    CRIT(Band.COMBAT),
    PLAYER_HURT(Band.COMBAT),
    TELEGRAPH(Band.COMBAT),
    ENEMY_DOWN(Band.COMBAT),
    VICTORY(Band.COMBAT),
    DEFEAT(Band.COMBAT),
    CEILING_PUSH(Band.COMBAT),
    ;

    enum class Band { FORM, COMBAT }
}

/** One sound to play. [rate] is a playback-speed multiplier, [volume] a 0..1 gain. */
data class SoundRequest(
    val cue: SoundCue,
    val rate: Float = 1f,
    val volume: Float = 1f,
)

object BattleAudio {

    /**
     * Playback rate for a hit at the given combo.
     *
     * The impact climbs a semitone per rep, which makes a combo *audible*: someone with their eyes
     * shut, or running the screen-off mode, hears the chain rising and hears it reset. That is the
     * single cheapest way to make the game playable without looking at it.
     *
     * Capped at an octave because SoundPool's rate tops out at 2.0, and because past that it stops
     * sounding like momentum and starts sounding like a fault.
     */
    fun pitchForCombo(combo: Int): Float {
        val semitones = (combo - 1).coerceIn(0, MAX_COMBO_SEMITONES)
        return 2f.pow(semitones / 12f)
    }

    const val MAX_COMBO_SEMITONES = 12
}
