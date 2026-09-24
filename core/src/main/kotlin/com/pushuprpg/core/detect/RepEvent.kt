package com.pushuprpg.core.detect

/**
 * Everything the game layer reacts to, stamped with the *pose* timestamp it happened at.
 *
 * Carrying the timestamp rather than relying on delivery order is what lets the render loop run at
 * its own rate and replay these at the moment they occurred, instead of bunching them up whenever
 * a slow inference finally lands.
 */
sealed interface RepEvent {
    val tMs: Long

    /**
     * The rep counted — fired at the *bottom*, not on the way back up.
     *
     * The hit has to land when the user feels the effort. Waiting for the lockout would put the
     * damage number 400–700 ms after the exertion, far outside the window in which the two read as
     * one event, and the game would feel disconnected from the body.
     */
    data class Strike(
        override val tMs: Long,
        val repIndex: Int,
        val grade: RepGrade,
        val depth: Float,
        val combo: Int,
        /** For a split-stance movement, the leg in front on this rep; null for everything else. */
        val front: BodySide? = null,
        /**
         * How long this rep took from leaving the top band to crossing the count line — the
         * controlled part of the way down, placed between frames. What a 기사's tempo is read from.
         */
        val descentMs: Int = 0,
    ) : RepEvent

    /**
     * The same rep went deeper than [DetectorConfig.deepEnter] after it had already counted.
     *
     * A separate event rather than a better grade on [Strike], because at the moment of the strike
     * we cannot know how deep the user will go, and delaying the hit to find out is exactly what
     * must not happen. Going deeper is rewarded *while* the user is still going deeper.
     */
    data class DeepUpgrade(
        override val tMs: Long,
        val repIndex: Int,
        val depth: Float,
        /**
         * How long the way down took, from leaving the top band to crossing the deep line, placed
         * between frames. Most of a full lowering, which is what a 기사's tempo is read from: the
         * count line alone sits in the fast middle of the rep and moves while calibrating.
         */
        val loweringMs: Int = 0,
    ) : RepEvent

    /** Bottom reached and top regained. Only these feed calibration. */
    data class Completed(override val tMs: Long, val repIndex: Int, val record: RepRecord) : RepEvent

    /** Went down, came back without reaching the 인정 line. */
    data class Shallow(override val tMs: Long, val maxDepth: Float, val consecutive: Int) : RepEvent

    data class Abandoned(override val tMs: Long, val reason: AbandonReason) : RepEvent

    data class QualityChanged(override val tMs: Long, val quality: PoseQuality) : RepEvent

    /** A form tip, framed as a buff the engine actually grants. */
    data class Coach(override val tMs: Long, val hint: FormHint, val rewardMultiplier: Float) : RepEvent

    data class ComboBroken(override val tMs: Long, val finalCombo: Int) : RepEvent

    // ---- plank (a hold, not a rep) ----

    data class HoldTick(
        override val tMs: Long,
        val score: Float,
        val holdMs: Long,
        val charge: Float,
        val damage: Float,
    ) : RepEvent

    data class HoldBroken(
        override val tMs: Long,
        val holdMs: Long,
        val qualityAvg: Float,
        val longestUnbrokenMs: Long,
    ) : RepEvent

    data class ChargeFull(override val tMs: Long, val chargeIndex: Int) : RepEvent
}
