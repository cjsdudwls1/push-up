package com.pushuprpg.app.billing

import com.android.billingclient.api.ProductDetails
import com.pushuprpg.app.domain.FreeTier

/**
 * The Play Console catalogue, mirrored as constants.
 *
 * One subscription product with two base plans, rather than two products: Play only lets a user
 * hold one purchase per subscription product, so month -> year is then an ordinary plan change
 * inside Play's own UI instead of a cancel-and-rebuy we would have to orchestrate ourselves.
 *
 * Prices are deliberately absent. Korean pricing, the annual discount and any regional price are
 * set in the Play Console and read back off [ProductDetails]; hardcoding them here would mean a
 * store-listing change could only ship as an app update.
 */
object BillingProducts {

    const val PRO_SUBSCRIPTION_ID = "pushup_rpg_pro"

    const val BASE_PLAN_MONTHLY = "pro-monthly"
    const val BASE_PLAN_ANNUAL = "pro-annual"

    const val OFFER_TRIAL_MONTHLY = "trial-7d-monthly"
    const val OFFER_TRIAL_ANNUAL = "trial-7d-annual"

    /** Used only when Play has not told us the real trial length yet (first run, offline). */
    const val DEFAULT_TRIAL_DAYS = FreeTier.TRIAL_DAYS

    val SUBSCRIPTION_IDS: List<String> = listOf(PRO_SUBSCRIPTION_ID)

    fun grantsFullAccess(productIds: List<String>): Boolean =
        productIds.any { it == PRO_SUBSCRIPTION_ID }
}

enum class PlanPeriod { MONTHLY, ANNUAL }

/**
 * One purchasable base plan, already flattened out of [ProductDetails] so nothing above this layer
 * has to know the Play Billing types.
 */
data class SubscriptionPlan(
    val productId: String,
    val basePlanId: String,
    val offerId: String?,
    /** Opaque token identifying the exact offer to buy; required by the billing flow. */
    val offerToken: String,
    val period: PlanPeriod,
    /** Localised and currency-formatted by Play, e.g. "₩4,900". Never build this yourself. */
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    /** ISO-8601 recurrence of the paid phase, e.g. "P1M". */
    val billingPeriod: String,
    val hasFreeTrial: Boolean,
    val freeTrialDays: Int,
) {
    /** Lets a paywall put the annual plan on the same axis as the monthly one. */
    val monthlyEquivalentMicros: Long
        get() = if (period == PlanPeriod.ANNUAL) priceAmountMicros / 12 else priceAmountMicros
}

/**
 * Collapses Play's offer list into one entry per base plan.
 *
 * Play returns a separate `SubscriptionOfferDetails` for every offer the user is *eligible* for,
 * including the bare base plan with no offer attached. We prefer the free-trial offer when it is
 * present and fall back to the bare plan when it is not — a user who has already burned their trial
 * simply stops being eligible for it, and must still be able to subscribe.
 */
fun ProductDetails.toSubscriptionPlans(): List<SubscriptionPlan> {
    val offers = subscriptionOfferDetails ?: return emptyList()

    return offers.groupBy { it.basePlanId }.mapNotNull { (basePlanId, forPlan) ->
        val chosen = forPlan.firstOrNull { it.freeTrialPhase() != null }
            ?: forPlan.firstOrNull()
            ?: return@mapNotNull null

        val phases = chosen.pricingPhases.pricingPhaseList
        // The list is time-ordered, so the ongoing recurring price is the last phase; anything
        // before it is an introductory or free period.
        val recurring = phases.lastOrNull() ?: return@mapNotNull null
        val trial = chosen.freeTrialPhase()

        SubscriptionPlan(
            productId = productId,
            basePlanId = basePlanId,
            offerId = chosen.offerId,
            offerToken = chosen.offerToken,
            period = periodOf(basePlanId, recurring.billingPeriod),
            formattedPrice = recurring.formattedPrice,
            priceAmountMicros = recurring.priceAmountMicros,
            currencyCode = recurring.priceCurrencyCode,
            billingPeriod = recurring.billingPeriod,
            hasFreeTrial = trial != null,
            freeTrialDays = trial?.let { iso8601PeriodToDays(it.billingPeriod) } ?: 0,
        )
    }.sortedBy { it.period.ordinal }
}

private fun ProductDetails.SubscriptionOfferDetails.freeTrialPhase(): ProductDetails.PricingPhase? {
    val phases = pricingPhases.pricingPhaseList
    if (phases.size < 2) return null
    return phases.firstOrNull { it.priceAmountMicros == 0L }
}

private fun periodOf(basePlanId: String, billingPeriod: String): PlanPeriod = when {
    basePlanId == BillingProducts.BASE_PLAN_ANNUAL -> PlanPeriod.ANNUAL
    basePlanId == BillingProducts.BASE_PLAN_MONTHLY -> PlanPeriod.MONTHLY
    // A base plan renamed in the console should still render sanely rather than vanish.
    iso8601PeriodToDays(billingPeriod) >= 180 -> PlanPeriod.ANNUAL
    else -> PlanPeriod.MONTHLY
}

/**
 * Minimal ISO-8601 duration reader for the subset Play emits for billing periods: P1W, P7D, P1M,
 * P3M, P6M, P1Y. Months and years are approximated, which is fine — the only consumer is a
 * "trial ends in N days" label and a monthly/annual classification.
 */
internal fun iso8601PeriodToDays(period: String?): Int {
    if (period.isNullOrEmpty() || period[0] != 'P') return 0
    var days = 0
    var number = 0
    var sawDigit = false
    for (i in 1 until period.length) {
        val c = period[i]
        when {
            c in '0'..'9' -> {
                number = number * 10 + (c - '0')
                sawDigit = true
            }
            // Sub-day components cannot express a trial length we would ever show.
            c == 'T' -> return days
            else -> {
                if (!sawDigit) return days
                days += when (c) {
                    'D' -> number
                    'W' -> number * 7
                    'M' -> number * 30
                    'Y' -> number * 365
                    else -> 0
                }
                number = 0
                sawDigit = false
            }
        }
    }
    return days
}
