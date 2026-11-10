package com.pushuprpg.core.progression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdRulesTest {
    private val day = 20_000L

    @Test
    fun `the shop pays three times a day and refills the next day`() {
        var ledger = AdLedger()
        repeat(3) { ledger = AdRules.shopAdWatched(ledger, day)!! }
        assertEquals(0, AdRules.shopAdsLeft(ledger, day))
        assertNull(AdRules.shopAdWatched(ledger, day))
        assertEquals(3, AdRules.shopAdsLeft(ledger, day + 1))
        assertEquals(1, AdRules.shopAdWatched(ledger, day + 1)!!.shopCount)
    }

    @Test
    fun `turning the clock back refills nothing`() {
        var ledger = AdLedger()
        repeat(3) { ledger = AdRules.shopAdWatched(ledger, day)!! }
        assertEquals(0, AdRules.shopAdsLeft(ledger, day - 5))
        assertFalse(AdRules.interstitialDue(AdRules.interstitialShown(AdLedger(firstDay = 1), day), day - 5))
    }

    @Test
    fun `no interstitial in the first three days or before counting began`() {
        val ledger = AdRules.started(AdLedger(), day)
        assertFalse(AdRules.interstitialDue(AdLedger(), day))
        assertFalse(AdRules.interstitialDue(ledger, day + 2))
        assertTrue(AdRules.interstitialDue(ledger, day + 3))
    }

    @Test
    fun `one interstitial a day, and starting twice keeps the first day`() {
        val ledger = AdRules.started(AdRules.started(AdLedger(), day), day + 9)
        assertEquals(day, ledger.firstDay)
        val shown = AdRules.interstitialShown(ledger, day + 9)
        assertFalse(AdRules.interstitialDue(shown, day + 9))
        assertTrue(AdRules.interstitialDue(shown, day + 10))
    }
}
