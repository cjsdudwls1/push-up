package com.pushuprpg.core

import com.pushuprpg.core.audio.Announcer
import com.pushuprpg.core.audio.VoiceStyle
import com.pushuprpg.core.detect.PlacementAdvice
import com.pushuprpg.core.survival.CatLine
import com.pushuprpg.core.survival.CatSpeech
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnouncerTest {

    @Test
    fun `placement advice is said when it changes and not repeated soon after`() {
        val a = Announcer()
        fun at(advice: PlacementAdvice?, t: Long) = a.survival(null, advice, t)

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
    fun `a line waits for a gap rather than talking over the last`() {
        val a = Announcer()
        a.survival(null, PlacementAdvice.COME_CLOSER, 0)
        // Half a second later the cat's line is dropped rather than queued behind.
        val saved = CatSpeech(CatLine.SAVED, serial = 0, atMs = 500)
        assertTrue(a.survival(saved, PlacementAdvice.COME_CLOSER, 500).isEmpty())
        val again = CatSpeech(CatLine.SAVED, serial = 1, atMs = 2_500)
        assertEquals(VoiceStyle.CAT, a.survival(again, PlacementAdvice.COME_CLOSER, 2_500).single().style)
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
