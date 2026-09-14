package com.pushuprpg.core

import com.pushuprpg.core.anim.AnimClip
import com.pushuprpg.core.anim.FighterAnimator
import com.pushuprpg.core.game.PlayerClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FighterAnimatorTest {

    private fun animator() = FighterAnimator(PlayerClass.KNIGHT)

    @Test
    fun `consecutive normal reps never repeat the same motion`() {
        // The whole answer to "it only has one attack animation" is here: variety from data rather
        // than from more art.
        val a = animator()
        val clips = (0 until 6).map { i ->
            val t = i * 1_000L
            a.onStrike(t, deep = false, crit = false)
            a.update(t, depth = 90f).clip
        }
        clips.zipWithNext().forEach { (first, second) ->
            assertTrue(first != second, "repeated $first back to back in $clips")
        }
        assertEquals(FighterAnimator.LIGHT_STRING.size, clips.toSet().size)
    }

    @Test
    fun `a deep rep and a crit get their own motions`() {
        val a = animator()
        a.onStrike(0, deep = true, crit = false)
        assertEquals(AnimClip.HEAVY, a.update(0, 95f).clip)

        a.onStrike(1_000, deep = true, crit = true)
        assertEquals(AnimClip.CRIT, a.update(1_000, 95f).clip)
    }

    @Test
    fun `the avatar winds up with the user rather than only reacting after the fact`() {
        val a = animator()
        assertEquals(0f, a.update(0, depth = 0f).windup)
        assertTrue(a.update(100, depth = 50f).windup > 0.4f)
        assertEquals(1f, a.update(200, depth = 100f).windup)
    }

    @Test
    fun `the wind-up yields while a swing is playing`() {
        // Otherwise the body blend and the clip pull the same limbs in different directions.
        val a = animator()
        a.onStrike(0, deep = false, crit = false)
        assertEquals(0f, a.update(50, depth = 95f).windup)
    }

    @Test
    fun `a clip returns to idle on its own`() {
        val a = animator()
        a.onStrike(0, deep = false, crit = false)
        val clip = a.update(0, 90f).clip
        assertTrue(clip.isAttack)
        assertEquals(AnimClip.IDLE, a.update(clip.durationMs + 50L, 0f).clip)
    }

    @Test
    fun `impact spikes on a hit and decays`() {
        val a = animator()
        a.onStrike(0, deep = false, crit = false)
        val onHit = a.update(0, 90f).impact
        val later = a.update(FighterAnimator.IMPACT_MS / 2, 90f).impact
        val gone = a.update(FighterAnimator.IMPACT_MS + 100, 0f).impact

        assertTrue(onHit > 0.9f)
        assertTrue(later in 0.01f..onHit)
        assertEquals(0f, gone)
    }

    @Test
    fun `being hit interrupts a swing`() {
        val a = animator()
        a.onStrike(0, deep = false, crit = false)
        a.onHurt(100)
        assertEquals(AnimClip.HURT, a.update(120, 0f).clip)
    }

    @Test
    fun `the end of a run locks out later events`() {
        // A combat event and the run ending can land on the same frame; the result must win.
        val a = animator()
        a.onRunEnded(1_000, cleared = true)
        a.onStrike(1_010, deep = true, crit = true)
        a.onHurt(1_020)
        assertEquals(AnimClip.VICTORY, a.update(1_100, 0f).clip)
    }

    @Test
    fun `a defeat animates as a defeat and holds`() {
        val a = animator()
        a.onRunEnded(0, cleared = false)
        assertEquals(AnimClip.DEFEAT, a.update(AnimClip.DEFEAT.durationMs + 500L, 0f).clip)
    }

    @Test
    fun `reset returns it to idle for the next run`() {
        val a = animator()
        a.onRunEnded(0, cleared = false)
        a.reset()
        assertEquals(AnimClip.IDLE, a.update(100, 0f).clip)
        a.onStrike(200, deep = false, crit = false)
        assertEquals(AnimClip.LIGHT_1, a.update(200, 90f).clip)
    }
}
