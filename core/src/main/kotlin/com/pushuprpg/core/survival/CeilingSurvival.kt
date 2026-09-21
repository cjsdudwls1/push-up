package com.pushuprpg.core.survival

import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.RepGrade
import kotlin.math.pow
import kotlin.math.roundToInt

/** What the survival run is doing right now. */
data class SurvivalState(
    /** 1.0 is safely at the top, 0.0 is the cat. */
    val height: Float = 1f,
    val score: Int = 0,
    /** Played time. Time spent setting up or untracked is not in here; the ceiling did not move then. */
    val elapsedMs: Long = 0,
    /** False until the first frame the user was in position; the ceiling has not moved yet. */
    val started: Boolean = false,
    val reps: Int = 0,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    val alive: Boolean = true,
    /** 0..1, for the UI to drive tension: colour, shake, music, the cat's expression. */
    val intensity: Float = 0f,
)

sealed interface SurvivalEvent {
    val atMs: Long

    data class Pushed(override val atMs: Long, val lift: Float, val deep: Boolean, val combo: Int) : SurvivalEvent

    /** A rep that did not go deep enough to lift anything. A near miss, not a punishment. */
    data class NearMiss(override val atMs: Long) : SurvivalEvent

    data class Milestone(override val atMs: Long, val seconds: Int) : SurvivalEvent

    data class GameOver(override val atMs: Long, val score: Int, val survivedMs: Long) : SurvivalEvent
}

/**
 * 고냥이 지켜줘 — the casual survival mode.
 *
 * A ceiling descends toward a cat and every pushup shoves it back up. The reason this works where
 * a more elaborate design would not is that the mapping is literal: the physical act of a pushup
 * *is* pushing something upward, so it teaches itself. Nobody needs to be told what the depth gauge
 * means, because there is no depth gauge — the ceiling is the gauge.
 *
 * Pure Kotlin and fully deterministic: time arrives as a parameter and nothing reads a clock, so a
 * run replays exactly from a recorded trace.
 *
 * ## The difficulty curve
 *
 * Descent accelerates with elapsed time, so every run ends no matter how well it is played:
 *
 *     descentPerSecond(t) = BASE_DESCENT * (1 + t / RAMP_SECONDS)
 *
 * At BASE_DESCENT 0.085 and RAMP_SECONDS 45 that is 0.085 height/s at the start, 0.198 at 60s,
 * 0.312 at 120s and 0.652 at 300s. A deep rep lifts 0.115, so sustaining the ceiling costs roughly
 * 0.7 reps/s at the start, 1.7/s at a minute, 2.7/s at two minutes and 5.7/s at five — which no
 * human sustains. A beginner managing about one rep every two seconds and starting to fade drowns
 * somewhere around 40-60s; someone strong holding a rep a second reaches the low hundreds; the
 * theoretical ceiling for a machine is a little over five minutes.
 */
class CeilingSurvival(
    private val config: DetectorConfig = DetectorConfig.pushup(),
    private val baseDescent: Float = BASE_DESCENT,
    private val rampSeconds: Float = RAMP_SECONDS,
) {
    private var height = 1f
    private var score = 0f
    private var startedAtMs = Long.MIN_VALUE
    private var lastUpdateMs = Long.MIN_VALUE
    /** Time the run has actually been played, which is what the difficulty ramp runs on. */
    private var activeMs = 0L
    private var reps = 0
    private var combo = 0
    private var bestCombo = 0
    private var alive = true
    private var lastMilestone = 0

    fun state(): SurvivalState = SurvivalState(
        height = height,
        score = score.roundToInt(),
        elapsedMs = activeMs,
        started = startedAtMs != Long.MIN_VALUE,
        reps = reps,
        combo = combo,
        bestCombo = bestCombo,
        alive = alive,
        intensity = (1f - height).coerceIn(0f, 1f).pow(0.7f),
    )

    /**
     * Advances the ceiling. Call every frame with the current pose timestamp.
     *
     * [active] is whether the user is in position and being tracked — the detector is armed or
     * mid-rep, and its quality is OK. While it is false the ceiling holds still, the score holds
     * still and the difficulty ramp does not advance: the run is frozen, not lost.
     *
     * It used to fall unconditionally from the first pose frame. The camera binds while the user
     * is still walking back to the mat, and at the starting rate the ceiling reaches the cat about
     * twenty seconds after that — before a beginner has found the floor, before the tutorial card
     * has been read, and with zero reps counted. Then it kept falling through every tracking gap.
     * The rule this mode broke is the same one the dungeon keeps: a user must never lose for a
     * tracking failure, and setting up is not playing.
     */
    fun update(nowMs: Long, active: Boolean = true): List<SurvivalEvent> {
        if (!alive) return emptyList()
        if (!active) {
            // Hold everything where it is. The next active frame integrates from here, so the
            // frozen interval never turns into descent.
            lastUpdateMs = nowMs
            return emptyList()
        }
        if (startedAtMs == Long.MIN_VALUE) {
            startedAtMs = nowMs
            lastUpdateMs = nowMs
            return emptyList()
        }

        val dtMs = (nowMs - lastUpdateMs).coerceIn(0L, MAX_STEP_MS)
        lastUpdateMs = nowMs
        if (dtMs == 0L) return emptyList()

        val dt = dtMs / 1000f
        activeMs += dtMs
        val elapsedSec = activeMs / 1000f

        height = (height - descentPerSecond(elapsedSec) * dt).coerceIn(0f, 1f)
        // Surviving is itself worth points, so a cautious player still climbs the board.
        score += SCORE_PER_SECOND * dt

        val events = mutableListOf<SurvivalEvent>()

        val seconds = elapsedSec.toInt()
        if (seconds >= lastMilestone + MILESTONE_SECONDS) {
            lastMilestone = seconds - seconds % MILESTONE_SECONDS
            events += SurvivalEvent.Milestone(nowMs, lastMilestone)
        }

        if (height <= 0f) {
            alive = false
            events += SurvivalEvent.GameOver(nowMs, score.roundToInt(), activeMs)
        }
        return events
    }

    /**
     * A rep the detector has already judged.
     *
     * [grade] is the detector's verdict and this function does not second-guess it. It used to:
     * it re-tested [depth] against `config.countEnter`, which is the *converged* line, while a
     * first-time user is still in bootstrap and being counted against a deliberately more
     * forgiving one. Every early rep therefore came back a near miss — a barely-moving ceiling and
     * a combo stuck at zero — in the one mode whose entire job is to be somebody's first sixty
     * seconds. That is the same defect that once let the dungeon counter tick up while dealing no
     * damage, and the rule it breaks is the same: one component owns whether a rep counts.
     *
     * [depth] survives only to scale the lift between the accepted and deep bands.
     *
     * A rep the detector rejected still gives a little, and is reported as a near miss rather than
     * ignored. This mode is the on-ramp for people who have never used the app; making a shallow
     * rep feel like nothing happened is how you teach someone that they are bad at it.
     */
    fun onRep(grade: RepGrade, depth: Float, atMs: Long): List<SurvivalEvent> {
        if (!alive) return emptyList()
        if (startedAtMs == Long.MIN_VALUE) {
            startedAtMs = atMs
            lastUpdateMs = atMs
        }

        val counted = grade != RepGrade.SHALLOW
        val isDeep = grade == RepGrade.DEEP

        // Far short of even the forgiving line: acknowledged, but it moves nothing.
        if (!counted && depth < config.countEnter * SHALLOW_CREDIT_FLOOR) {
            return listOf(SurvivalEvent.NearMiss(atMs))
        }

        val accept = config.countEnter
        val deep = config.deepEnter
        val lift = when {
            isDeep -> DEEP_LIFT
            counted -> LIFT + (DEEP_LIFT - LIFT) * ((depth - accept) / (deep - accept)).coerceIn(0f, 1f)
            // Short of the line but genuinely tried: a fraction of the push, and a near miss.
            else -> LIFT * SHALLOW_LIFT_FRACTION
        }

        reps++
        if (counted) {
            combo++
            bestCombo = maxOf(bestCombo, combo)
        } else {
            combo = 0
        }

        // Consecutive deep reps are worth compounding, which is what makes a good run feel good
        // rather than merely long.
        val comboBonus = 1f + COMBO_SCORE_BONUS * (combo - 1).coerceAtLeast(0)
        score += SCORE_PER_REP * (if (isDeep) DEEP_SCORE_MULTIPLIER else 1f) * comboBonus

        height = (height + lift).coerceAtMost(1f)

        return if (counted) {
            listOf(SurvivalEvent.Pushed(atMs, lift, isDeep, combo))
        } else {
            listOf(SurvivalEvent.Pushed(atMs, lift, false, combo), SurvivalEvent.NearMiss(atMs))
        }
    }

    fun reset() {
        height = 1f
        score = 0f
        startedAtMs = Long.MIN_VALUE
        lastUpdateMs = Long.MIN_VALUE
        activeMs = 0L
        reps = 0
        combo = 0
        bestCombo = 0
        alive = true
        lastMilestone = 0
    }

    private fun descentPerSecond(elapsedSec: Float): Float =
        baseDescent * (1f + elapsedSec / rampSeconds)

    companion object {
        /** Height units per second at the very start of a run. */
        const val BASE_DESCENT = 0.040f

        /** Seconds for the descent rate to double. */
        const val RAMP_SECONDS = 45f

        const val LIFT = 0.075f
        const val DEEP_LIFT = 0.115f
        const val SHALLOW_LIFT_FRACTION = 0.35f

        /** Below this fraction of the accept line, nothing meaningful happened. */
        const val SHALLOW_CREDIT_FLOOR = 0.55f

        const val SCORE_PER_SECOND = 10f
        const val SCORE_PER_REP = 25f
        const val DEEP_SCORE_MULTIPLIER = 1.6f
        const val COMBO_SCORE_BONUS = 0.06f

        const val MILESTONE_SECONDS = 30

        /** A frame gap longer than this is a stall, not time the ceiling should fall through. */
        const val MAX_STEP_MS = 250L
    }
}
