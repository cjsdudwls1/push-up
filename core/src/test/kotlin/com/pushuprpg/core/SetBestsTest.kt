package com.pushuprpg.core

import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.Purse
import com.pushuprpg.core.progression.SetBests
import com.pushuprpg.core.progression.SetOutcome
import com.pushuprpg.core.progression.Shop
import com.pushuprpg.core.progression.ShopItem
import com.pushuprpg.core.survival.LifeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetBestsTest {

    private fun life(reps: Int, ms: Long = 30_000L) = LifeResult(reps = reps, score = reps * 10, survivedMs = ms, deepReps = 0)

    @Test
    fun `a first session sets every set's best and pays for each`() {
        val out = SetOutcome.of(SetBests(), listOf(life(12), life(10), life(8)), hold = false)
        assertEquals(listOf(12, 10, 8), out.bests.bests)
        assertEquals(listOf(0, 1, 2), out.firsts)
        assertEquals(emptyList(), out.broken)
        assertEquals(3 * SetBests.CHURU_FIRST, out.churu)
    }

    @Test
    fun `each set is played against its own best, and only beating it counts`() {
        val before = SetBests(listOf(12, 10, 8))
        // The first set ties, the second falls short, the third beats 8.
        val out = SetOutcome.of(before, listOf(life(12), life(7), life(9), life(4)), hold = false)
        assertEquals(listOf(12, 10, 9, 4), out.bests.bests)
        assertEquals(listOf(2), out.broken)
        assertEquals(listOf(3), out.firsts)
        assertEquals(SetBests.CHURU_BROKEN + SetBests.CHURU_FIRST, out.churu)
    }

    @Test
    fun `a short session never lowers a best it did not reach`() {
        val before = SetBests(listOf(12, 10, 8))
        val out = SetOutcome.of(before, listOf(life(3)), hold = false)
        assertEquals(listOf(12, 10, 8), out.bests.bests)
        assertEquals(0, out.churu)
    }

    @Test
    fun `a life that did nothing sets nothing`() {
        val out = SetOutcome.of(SetBests(), listOf(life(0)), hold = false)
        assertEquals(listOf(0), out.bests.bests)
        assertEquals(0, out.churu)
    }

    @Test
    fun `a hold's best is its whole seconds`() {
        val out = SetOutcome.of(SetBests(listOf(40)), listOf(life(0, ms = 45_900L)), hold = true)
        assertEquals(listOf(45), out.bests.bests)
        assertEquals(listOf(0), out.broken)
    }

    @Test
    fun `the shop sells each thing once, for what it costs`() {
        val purse = Purse(churu = 10, owned = emptySet())
        assertNull(Shop.buy(purse, ShopItem.HEART_GLASSES), "12 bought with 10")
        val after = Shop.buy(purse, ShopItem.BANDANA)!!
        assertEquals(2, after.churu)
        assertTrue(CatItem.BANDANA in after.owned)
        assertFalse(Shop.canBuy(after.copy(churu = 100), ShopItem.BANDANA), "bought twice")
    }

    @Test
    fun `the shelf is cheapest first and sells nothing a gift brings`() {
        val prices = ShopItem.entries.map { it.price }
        assertEquals(prices.sorted(), prices)
        val gifted = com.pushuprpg.core.progression.Gift.entries.map { it.item }.toSet()
        assertTrue(ShopItem.entries.none { it.item in gifted })
    }
}
