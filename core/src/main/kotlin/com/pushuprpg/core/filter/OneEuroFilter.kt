package com.pushuprpg.core.filter

import kotlin.math.abs
import kotlin.math.PI

/**
 * A first-order low-pass filter with an adaptive cutoff — the "One Euro" filter
 * (Casiez, Roussel & Vogel, CHI 2012).
 *
 * Why this and not a moving average: a fixed filter forces a choice between jitter at rest and lag
 * during motion, and this app needs *both* ends. A user holding the top of a pushup must not see
 * the depth gauge shimmer, while the descent must register the instant it happens or the attack
 * lands late and the game feels unresponsive. One Euro solves exactly that by widening its cutoff
 * in proportion to the signal's own speed.
 *
 * Tuned for the app's 0..100 depth scale at ~30 fps. Note the residual jitter measured below
 * (~4 depth units): every threshold the rep detector applies to this signal needs hysteresis
 * comfortably wider than that, or a user holding still at the bottom will machine-gun reps.
 *
 *  - [minCutoff] 1.0 Hz — the jitter floor while the user holds a position.
 *  - [beta] 0.2 — how fast the cutoff opens with speed. The published examples use values near
 *    0.007, but those filter signals in a 0..1 range; ours spans 0..100, so the same responsiveness
 *    needs a beta scaled to match.
 *
 *    Measured on this scale at 30 fps, against a sinusoidal rep and correlated landmark noise
 *    (peak tracking error in depth units / residual jitter in depth units):
 *
 *        beta    lag @1 rep/s   lag @0.5 rep/s   jitter
 *        0.007       17.4            8.9           3.1
 *        0.03        10.2            4.9           3.6
 *        0.2          4.3            2.5           4.2
 *        0.6          2.0            1.6           4.4
 *
 *    Jitter is nearly flat across that range — most of what survives is slow drift, which no
 *    low-pass can remove without also eating real motion — while lag improves fourfold. 0.2 takes
 *    the cheap end of that trade; beyond it lag gains shrink and noise keeps creeping up.
 *  - [dCutoff] 1.0 Hz — smoothing applied to the speed estimate itself.
 */
class OneEuroFilter(
    private val minCutoff: Double = DEFAULT_MIN_CUTOFF,
    private val beta: Double = DEFAULT_BETA,
    private val dCutoff: Double = DEFAULT_D_CUTOFF,
    private val maxSlewPerSecond: Double = DEFAULT_MAX_SLEW,
) {
    private var initialized = false
    private var xPrev = 0.0
    private var dxPrev = 0.0
    private var tPrevMs = 0L

    /**
     * True when the last [filter] call re-seeded the filter because too much time had passed.
     *
     * The rep detector must watch this: a gap that long means the camera stalled or the app was
     * backgrounded mid-set, so any in-flight rep is no longer trustworthy and must be abandoned
     * rather than completed against a stale bottom position.
     */
    var hadDiscontinuity: Boolean = false
        private set

    /**
     * Feeds one sample and returns the filtered value.
     *
     * [timestampMs] must be the camera frame timestamp, not the time the result was delivered.
     * Duplicate and out-of-order timestamps are tolerated — MediaPipe's live-stream callbacks can
     * reorder under load — and return the previous output unchanged.
     */
    fun filter(value: Double, timestampMs: Long): Double {
        hadDiscontinuity = false

        if (!initialized) {
            seed(value, timestampMs)
            return value
        }

        val dtMs = timestampMs - tPrevMs
        if (dtMs <= 0L) return xPrev

        if (dtMs > GAP_RESET_MS) {
            // Blending across a gap this long is meaningless — and actively harmful, because the
            // adaptive cutoff reads the huge apparent velocity and opens right up, letting one
            // stale sample yank the output. Re-seed instead and tell the caller.
            seed(value, timestampMs)
            hadDiscontinuity = true
            return value
        }

        val rate = (1000.0 / dtMs).coerceIn(MIN_RATE_HZ, MAX_RATE_HZ)

        // Slew limit before the filter proper.
        //
        // A responsive beta buys low lag at the cost of trusting fast change, and a single
        // mis-detected frame is fast change: unguarded, one bad landmark drags the depth signal
        // most of the way across its range in one frame, which the state machine reads as a rep
        // the user never did. Nothing erodes trust in a rep counter faster.
        //
        // So changes are capped at a speed no human body produces. Real motion never touches this
        // limit, impulse noise always does, and unlike a median prefilter it costs no latency.
        val maxDelta = maxSlewPerSecond * (dtMs / 1000.0)
        val sample = value.coerceIn(xPrev - maxDelta, xPrev + maxDelta)

        val dx = (sample - xPrev) * rate
        val dxHat = lowPass(dx, dxPrev, alpha(dCutoff, rate))

        val cutoff = minCutoff + beta * abs(dxHat)
        val xHat = lowPass(sample, xPrev, alpha(cutoff, rate))

        xPrev = xHat
        dxPrev = dxHat
        tPrevMs = timestampMs
        return xHat
    }

    /** Current filtered value, or 0.0 before the first sample. */
    val value: Double get() = if (initialized) xPrev else 0.0

    /** Current smoothed rate of change, in units per second. */
    val velocity: Double get() = if (initialized) dxPrev else 0.0

    fun reset() {
        initialized = false
        xPrev = 0.0
        dxPrev = 0.0
        tPrevMs = 0L
        hadDiscontinuity = false
    }

    private fun seed(value: Double, timestampMs: Long) {
        initialized = true
        xPrev = value
        dxPrev = 0.0
        tPrevMs = timestampMs
    }

    private fun alpha(cutoff: Double, rate: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        val te = 1.0 / rate
        return 1.0 / (1.0 + tau / te)
    }

    private fun lowPass(x: Double, xPrev: Double, alpha: Double): Double =
        alpha * x + (1.0 - alpha) * xPrev

    companion object {
        const val DEFAULT_MIN_CUTOFF = 1.0
        const val DEFAULT_BETA = 0.2
        const val DEFAULT_D_CUTOFF = 1.0

        /**
         * Beyond this gap the filter re-seeds rather than interpolates. 400 ms is about a dozen
         * dropped frames — well past any hiccup a healthy pipeline produces, and far shorter than
         * the fastest plausible rep, so a real descent can never be mistaken for a discontinuity.
         */
        const val GAP_RESET_MS = 400L

        val MIN_RATE_HZ = 1000.0 / GAP_RESET_MS
        const val MAX_RATE_HZ = 120.0

        /**
         * Maximum believable rate of change, in depth units per second.
         *
         * 400 u/s crosses the whole 0..100 depth range in 250 ms. The fastest real pushup descent
         * is comfortably slower than that, so genuine motion is never clipped, while a one-frame
         * landmark glitch — which implies thousands of units per second — is cut down to something
         * the filter can absorb.
         */
        const val DEFAULT_MAX_SLEW = 400.0
    }
}
