package com.pushuprpg.core.survival

import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.PlankConfig
import com.pushuprpg.core.detect.RepGrade
import kotlin.math.pow
import kotlin.math.roundToInt

/** What the survival run is doing right now. */
data class SurvivalState(
    /** 1.0 is safely at the top, 0.0 is the cat. */
    val height: Float = 1f,
    val score: Int = 0,
    /** Played time, from the first frame in position. Setting up is not in here. */
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

    /** [hold] marks a stretch of a hold rather than a rep: it arrives twice a second, not once a rep. */
    data class Pushed(
        override val atMs: Long,
        val lift: Float,
        val deep: Boolean,
        val combo: Int,
        val hold: Boolean = false,
    ) : SurvivalEvent

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
 *
 * ## Other movements
 *
 * The curve above is written in pushups, and every other movement is converted into them rather
 * than given a curve of its own: a rep is worth [pushupsPerRep] pushups of lift and score, and a
 * second of a hold the same. The number is the movement's own
 * [com.pushuprpg.core.detect.ExerciseDescriptor.sessionVolumeScale] — the one a dungeon's rep cost
 * is converted by — so a pull-up, which a person manages about a third as many of, moves the ceiling
 * about three times as far, and a movement is worth the same here as everywhere else in the game.
 */
class CeilingSurvival(
    private val config: DetectorConfig = DetectorConfig.pushup(),
    private val baseDescent: Float = BASE_DESCENT,
    private val rampSeconds: Float = RAMP_SECONDS,
    /** Pushups one rep of this movement is worth — or, for a hold, one second of it. */
    private val pushupsPerRep: Float = 1f,
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
     * [inPosition] — the detector is armed or mid-rep with good tracking — only starts the run.
     * Until the first such frame nothing moves: the camera binds while the user is still walking
     * back to the mat, and at the starting rate the ceiling would reach the cat about twenty seconds
     * later, before a beginner has found the floor. Setting up is not playing.
     *
     * Once started, the ceiling never stops. Resting, standing up, stepping out of view — it keeps
     * coming, and the only thing that holds it off is the next rep. This mode used to freeze
     * whenever the user was out of position, by the same rule the dungeon keeps (never punish a
     * tracking failure), and that made it something you could pause by sitting up. The mode is a
     * sprint for a cat, and a sprint you can pause is not one; this is the deliberate exception to
     * that rule, and CLAUDE.md says so.
     */
    fun update(nowMs: Long, inPosition: Boolean = true): List<SurvivalEvent> {
        if (!alive) return emptyList()
        if (startedAtMs == Long.MIN_VALUE && !inPosition) {
            // Not started: hold everything, so the wait never turns into descent.
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
        score += SCORE_PER_REP * pushupsPerRep * (if (isDeep) DEEP_SCORE_MULTIPLIER else 1f) * comboBonus

        height = (height + lift * pushupsPerRep).coerceAtMost(1f)

        return if (counted) {
            listOf(SurvivalEvent.Pushed(atMs, lift * pushupsPerRep, isDeep, combo))
        } else {
            listOf(SurvivalEvent.Pushed(atMs, lift * pushupsPerRep, false, combo), SurvivalEvent.NearMiss(atMs))
        }
    }

    /**
     * A stretch of a hold the detector has scored — a plank, where there is no rep to count.
     *
     * [formScore] is the detector's own 0-100 score for the stretch and [seconds] how long it was.
     * Form at the detector's holding line pushes like a counted rep and perfect form like a deep
     * one, in proportion to the time held. The detector reports a stretch only while the hold is
     * on, so a hold that has broken pushes nothing without this needing to know why — the same
     * rule as a rep: one component decides whether it counts.
     */
    fun onHold(formScore: Float, seconds: Float, atMs: Long): List<SurvivalEvent> {
        if (!alive || seconds <= 0f) return emptyList()
        if (startedAtMs == Long.MIN_VALUE) {
            startedAtMs = atMs
            lastUpdateMs = atMs
        }
        val quality = ((formScore - HOLD_LINE) / (100f - HOLD_LINE)).coerceIn(0f, 1f)
        val pushups = seconds * pushupsPerRep
        val lift = (LIFT + (DEEP_LIFT - LIFT) * quality) * pushups
        height = (height + lift).coerceAtMost(1f)
        score += SCORE_PER_REP * pushups * (1f + (DEEP_SCORE_MULTIPLIER - 1f) * quality)
        return listOf(SurvivalEvent.Pushed(atMs, lift, deep = quality >= 1f, combo = 0, hold = true))
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
        /** The survival run for [type], worth what that movement is worth everywhere else. */
        fun forExercise(type: ExerciseType): CeilingSurvival {
            val descriptor = Exercises.of(type)
            return CeilingSurvival(config = descriptor.config, pushupsPerRep = 1f / descriptor.sessionVolumeScale)
        }

        /** The form score a plank must hold to count as holding at all; see [PlankConfig]. */
        private val HOLD_LINE = PlankConfig().holdingScore

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
