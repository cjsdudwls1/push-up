package com.pushuprpg.core.progression

/**
 * How often ads may appear, counted in epoch days the caller supplies. By the owner's decision
 * (2026-11-08): the shop's 츄르 ad three times a day, the interstitial once a day and not in the
 * first three days. A day earlier than the one stored (a moved clock) counts as the stored day, so
 * turning the clock back does not refill anything.
 */
data class AdLedger(
    /** The first day ads were counted; 0 before that. */
    val firstDay: Long = 0,
    val shopDay: Long = 0,
    val shopCount: Int = 0,
    val interstitialDay: Long = 0,
)

object AdRules {
    const val SHOP_ADS_PER_DAY = 3
    const val SHOP_AD_CHURU = 2
    const val INTERSTITIAL_FREE_DAYS = 3

    fun started(ledger: AdLedger, today: Long): AdLedger =
        if (ledger.firstDay == 0L) ledger.copy(firstDay = today) else ledger

    fun shopAdsLeft(ledger: AdLedger, today: Long): Int =
        if (today > ledger.shopDay) SHOP_ADS_PER_DAY else (SHOP_ADS_PER_DAY - ledger.shopCount).coerceAtLeast(0)

    /** The ledger after a shop ad paid out, or null when none was left today. */
    fun shopAdWatched(ledger: AdLedger, today: Long): AdLedger? {
        val left = shopAdsLeft(ledger, today)
        if (left == 0) return null
        return ledger.copy(shopDay = maxOf(today, ledger.shopDay), shopCount = SHOP_ADS_PER_DAY - left + 1)
    }

    fun interstitialDue(ledger: AdLedger, today: Long): Boolean =
        ledger.firstDay != 0L &&
            today - ledger.firstDay >= INTERSTITIAL_FREE_DAYS &&
            today > ledger.interstitialDay

    fun interstitialShown(ledger: AdLedger, today: Long): AdLedger =
        ledger.copy(interstitialDay = maxOf(today, ledger.interstitialDay))
}
