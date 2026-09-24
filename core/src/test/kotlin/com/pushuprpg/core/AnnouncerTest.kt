package com.pushuprpg.core

import com.pushuprpg.core.audio.Announcer
import com.pushuprpg.core.audio.VoiceStyle
import com.pushuprpg.core.detect.Placement
import com.pushuprpg.core.detect.PlacementAdvice
import com.pushuprpg.core.game.Encounter
import com.pushuprpg.core.run.AlertKey
import com.pushuprpg.core.run.BattleState
import com.pushuprpg.core.run.Toast
import com.pushuprpg.core.survival.CatLine
import com.pushuprpg.core.survival.CatSpeech
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnouncerTest {

    private val quiet = BattleState(placement = Placement())

    @Test
    fun `an ultimate is announced once, urgently, then counted down as answers land`() {
        val a = Announcer()
        val incoming = quiet.copy(ultimateIncoming = true, ultimateRepsLeft = 5)
        val first = a.battle(incoming, 1_000)
        assertEquals(1, first.size)
        assertEquals(VoiceStyle.URGENT, first[0].style)
        assertEquals(AlertKey.ULTIMATE_INCOMING, first[0].alert)
        assertEquals(Encounter.ANSWERS_TO_BLOCK, first[0].arg)

        // Frames with nothing new say nothing.
        assertTrue(a.battle(incoming, 1_033).isEmpty())

        val oneIn = a.battle(incoming.copy(ultimateAnswers = 1, ultimateRepsLeft = 4), 2_000)
        assertEquals(Encounter.ANSWERS_TO_BLOCK - 1, oneIn.single().answersLeft)
        val twoIn = a.battle(incoming.copy(ultimateAnswers = 2, ultimateRepsLeft = 3), 2_400)
        assertEquals(Encounter.ANSWERS_TO_BLOCK - 2, twoIn.single().answersLeft)
    }

    @Test
    fun `the outcome of an ultimate is said, and not twice for one toast`() {
        val a = Announcer()
        val blocked = quiet.copy(alert = Toast(AlertKey.ULTIMATE_BLOCKED, atMs = 5_000))
        assertEquals(AlertKey.ULTIMATE_BLOCKED, a.battle(blocked, 5_000).single().alert)
        assertTrue(a.battle(blocked, 5_033).isEmpty())
    }

    @Test
    fun `placement advice is said when it changes and not repeated soon after`() {
        val a = Announcer()
        fun at(advice: PlacementAdvice?, t: Long) = a.battle(quiet.copy(placement = Placement(advice)), t)

        assertEquals(PlacementAdvice.COME_CLOSER, at(PlacementAdvice.COME_CLOSER, 0).single().placement)
        assertTrue(at(PlacementAdvice.COME_CLOSER, 500).isEmpty(), "repeated while unchanged")
        assertEquals(PlacementAdvice.READY, at(PlacementAdvice.READY, 3_000).single().placement)
        at(null, 4_000)
        // Back to the same advice within the repeat window: the banner carries it.
        assertTrue(at(PlacementAdvice.COME_CLOSER, 6_000).isEmpty())
        at(null, 7_000)
        assertEquals(PlacementAdvice.COME_CLOSER, at(PlacementAdvice.COME_CLOSER, 20_000).single().placement)
    }

    @Test
    fun `calm lines wait for a gap, urgent ones cut in`() {
        val a = Announcer()
        a.battle(quiet.copy(placement = Placement(PlacementAdvice.COME_CLOSER)), 0)
        // A calm alert half a second later is dropped rather than queued behind.
        val low = quiet.copy(placement = Placement(PlacementAdvice.COME_CLOSER), alert = Toast(AlertKey.BOSS_LOW_HP, atMs = 500))
        assertTrue(a.battle(low, 500).isEmpty())
        // The ultimate does not wait.
        val urgent = low.copy(ultimateIncoming = true)
        assertEquals(VoiceStyle.URGENT, a.battle(urgent, 600).single().style)
    }

    @Test
    fun `frequent alerts stay silent`() {
        val a = Announcer()
        val deep = quiet.copy(alert = Toast(AlertKey.DEEP_STRIKE, atMs = 100))
        assertTrue(a.battle(deep, 100).isEmpty())
    }

    @Test
    fun `survival speaks each new cat line once, in the cat's voice`() {
        val a = Announcer()
        val saved = CatSpeech(CatLine.SAVED, serial = 0, atMs = 1_000)
        assertEquals(VoiceStyle.CAT, a.survival(saved, null, 1_000).single().style)
        assertTrue(a.survival(saved, null, 1_033).isEmpty())
        assertNull(a.survival(null, null, 2_000).firstOrNull())
    }
}
