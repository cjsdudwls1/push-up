package com.pushuprpg.app.ui.screens

/**
 * How many more reps (or, for a hold, seconds) beat the set's [best] from [now], once it is close:
 * within [NEAR_REPS] reps or [NEAR_SECONDS] seconds. Null with no best to beat, while still far from
 * it, and once it is beaten — the record's own burst says that.
 */
internal fun recordToGo(now: Int, best: Int, hold: Boolean): Int? {
    if (best <= 0) return null
    val toGo = best - now + 1
    val window = if (hold) NEAR_SECONDS else NEAR_REPS
    return toGo.takeIf { it in 1..window }
}

private const val NEAR_REPS = 3
private const val NEAR_SECONDS = 5
