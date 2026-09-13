package com.pushuprpg.core.game

/**
 * All randomness in `:core` flows through here.
 *
 * Injected rather than taken from a global so that a whole encounter is replayable: a balance test
 * that says "these thirty reps produce exactly this damage" is only meaningful if the crit rolls
 * are reproducible. Combat draws from it exactly once per rep, after every multiplier has been
 * computed, so adding a future roll cannot shift the results of existing tests.
 */
fun interface Rng {
    /** Uniform in [0, 1). */
    fun nextFloat(): Float
}

/** Deterministic default. Seeded per encounter so a replay of the same seed is identical. */
class SeededRng(seed: Long) : Rng {
    private val random = kotlin.random.Random(seed)
    override fun nextFloat(): Float = random.nextFloat()
}

/** Never crits. Useful for pinning damage numbers in tests. */
val NoCritRng = Rng { 1f }

/** Always crits. */
val AlwaysCritRng = Rng { 0f }
