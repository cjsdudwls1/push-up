package com.pushuprpg.core

import com.pushuprpg.core.game.Dungeons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every dungeon is free, and each opens by clearing the one before it. [Dungeons.isUnlocked] is the
 * only place that says so: the dungeon list asks it, and the hub and the clear screen offer only the
 * dungeon after a clear, which it opens.
 */
class DungeonUnlockTest {

    @Test
    fun `the rule walks the table, which runs 1 to its size without a gap`() {
        assertEquals((1..Dungeons.ALL.size).toList(), Dungeons.ALL.map { it.index })
    }

    @Test
    fun `the first dungeon is open before anything is cleared`() {
        assertTrue(Dungeons.isUnlocked(1, highestCleared = 0))
        assertTrue(Dungeons.isUnlocked(Dungeons.FREE_DUNGEON.index, highestCleared = 0))
        assertFalse(Dungeons.isUnlocked(2, highestCleared = 0))
    }

    @Test
    fun `a clear opens the next one and nothing past it`() {
        for (cleared in 0 until Dungeons.ALL.size) {
            assertTrue(Dungeons.isUnlocked(cleared + 1, cleared), "after $cleared, ${cleared + 1} is open")
            for (index in cleared + 2..Dungeons.ALL.size) {
                assertFalse(Dungeons.isUnlocked(index, cleared), "after $cleared, $index is still shut")
            }
        }
    }

    @Test
    fun `a dungeon once open stays open`() {
        val last = Dungeons.ALL.size
        for (index in 1..last) {
            assertTrue(Dungeons.isUnlocked(index, highestCleared = last), "all cleared: $index is open")
        }
        assertTrue(Dungeons.isUnlocked(1, highestCleared = 5))
        assertTrue(Dungeons.isUnlocked(4, highestCleared = 5))
    }

    @Test
    fun `an index outside the table is never open`() {
        val last = Dungeons.ALL.size
        assertFalse(Dungeons.isUnlocked(0, highestCleared = last))
        assertFalse(Dungeons.isUnlocked(-1, highestCleared = last))
        assertFalse(Dungeons.isUnlocked(last + 1, highestCleared = last))
        assertFalse(Dungeons.isUnlocked(last + 1, highestCleared = last + 5))
    }
}
