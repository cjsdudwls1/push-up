package com.pushuprpg.core.anim

import com.pushuprpg.core.game.PlayerClass
import kotlin.math.pow

/** One motion. Which one plays is decided here; what it looks like is the renderer's problem. */
enum class AnimClip(val durationMs: Int) {
    IDLE(1_200),
    LIGHT_1(360),
    LIGHT_2(360),
    LIGHT_3(420),
    HEAVY(520),
    CRIT(620),
    SKILL(760),
    HURT(340),
    VICTORY(1_400),
    DEFEAT(1_200),
    ;

    val isAttack: Boolean
        get() = this == LIGHT_1 || this == LIGHT_2 || this == LIGHT_3 ||
            this == HEAVY || this == CRIT || this == SKILL
}

/** What the renderer needs this frame. */
data class AnimState(
    val clip: AnimClip,
    /** 0..1 through the current clip. */
    val progress: Float,
    /**
     * 0..1, driven continuously by the user's own depth.
     *
     * This is the part that actually answers the complaint about a single generic motion. Playing
     * more attack clips only adds variety between reps; blending the avatar through a wind-up in
     * step with the descent makes it do the rep *with* the user, every frame, for the cost of one
     * extra authored pose.
     */
    val windup: Float,
    /** 0..1, decays after a hit; drives weapon trails and impact flashes. */
    val impact: Float,
    val facingRight: Boolean = true,
)

/**
 * Picks the motion.
 *
 * Attack variety comes from data rather than from more art: a three-hit light string cycles so
 * consecutive reps never repeat, a deep rep swaps to a heavy variant, and a crit to its own. Three
 * classes times that set is a linear asset count, not a combinatorial one.
 *
 * Reads no clock — every method takes the pose timestamp — so a whole fight's animation is
 * reproducible from a recorded trace, like everything else in this module.
 */
class FighterAnimator(private val playerClass: PlayerClass) {

    private var clip: AnimClip = AnimClip.IDLE
    private var clipStartedMs: Long = Long.MIN_VALUE
    private var impactAtMs: Long = Long.MIN_VALUE
    private var lightIndex: Int = 0
    private var locked: Boolean = false

    /** A counted rep landed. [deep] and [crit] pick the heavier variants. */
    fun onStrike(atMs: Long, deep: Boolean, crit: Boolean) {
        if (locked) return
        val next = when {
            crit -> AnimClip.CRIT
            deep -> AnimClip.HEAVY
            else -> {
                val clips = LIGHT_STRING
                val pick = clips[lightIndex % clips.size]
                lightIndex++
                pick
            }
        }
        play(next, atMs)
        impactAtMs = atMs
    }

    /** A class skill fired. */
    fun onSkill(atMs: Long) {
        if (locked) return
        play(AnimClip.SKILL, atMs)
        impactAtMs = atMs
    }

    /** The player took damage. Interrupts an attack: being hit mid-swing should read as being hit. */
    fun onHurt(atMs: Long) {
        if (locked) return
        play(AnimClip.HURT, atMs)
    }

    /**
     * The run ended. Locks the animator so a late event cannot animate over the result — combat
     * events and the run ending can arrive on the same frame.
     */
    fun onRunEnded(atMs: Long, cleared: Boolean) {
        play(if (cleared) AnimClip.VICTORY else AnimClip.DEFEAT, atMs)
        locked = true
    }

    fun reset() {
        clip = AnimClip.IDLE
        clipStartedMs = Long.MIN_VALUE
        impactAtMs = Long.MIN_VALUE
        lightIndex = 0
        locked = false
    }

    /** [depth] is the live 0..100 gauge value. */
    fun update(nowMs: Long, depth: Float): AnimState {
        if (clipStartedMs == Long.MIN_VALUE) clipStartedMs = nowMs

        val elapsed = (nowMs - clipStartedMs).coerceAtLeast(0L)
        var progress = (elapsed.toFloat() / clip.durationMs).coerceIn(0f, 1f)

        if (progress >= 1f && clip != AnimClip.IDLE && !locked) {
            clip = AnimClip.IDLE
            clipStartedMs = nowMs
            progress = 0f
        }

        val impactElapsed = if (impactAtMs == Long.MIN_VALUE) Long.MAX_VALUE else nowMs - impactAtMs
        val impact = if (impactElapsed >= IMPACT_MS) {
            0f
        } else {
            (1f - impactElapsed.toFloat() / IMPACT_MS).coerceIn(0f, 1f).pow(1.6f)
        }

        // The wind-up only tracks the body while the avatar is idling. During a swing the clip owns
        // the pose, or the character would visibly fight itself.
        val windup = if (clip == AnimClip.IDLE) (depth / 100f).coerceIn(0f, 1f) else 0f

        return AnimState(clip = clip, progress = progress, windup = windup, impact = impact)
    }

    private fun play(next: AnimClip, atMs: Long) {
        clip = next
        clipStartedMs = atMs
    }

    companion object {
        val LIGHT_STRING = listOf(AnimClip.LIGHT_1, AnimClip.LIGHT_2, AnimClip.LIGHT_3)
        const val IMPACT_MS = 260L
    }
}
