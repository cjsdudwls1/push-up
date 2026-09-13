package com.pushuprpg.app.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.billingclient.api.Purchase
import com.pushuprpg.app.domain.Entitlement
import com.pushuprpg.app.domain.EntitlementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

private val Context.billingCacheStore: DataStore<Preferences> by preferencesDataStore(
    name = "billing_cache"
)

/**
 * [EntitlementRepository] on top of Google Play Billing, with a local cache so the app keeps
 * working on a subway.
 *
 * ## What a solo developer gets here, and what they do not
 *
 * There is no backend. That means:
 *
 *  - **Play is the source of truth.** `queryPurchasesAsync` returns purchases that the Play Store
 *    app has already signature-verified against Google's servers. We are not parsing a receipt
 *    ourselves and we are not trusting anything the client made up, so the *online* path is as
 *    trustworthy as a server-side check for practical purposes.
 *  - **The offline cache is forgeable.** A rooted device can write `billing_cache` by hand and
 *    claim a subscription. That is an accepted trade for v1: the thing being protected is extra
 *    dungeons in a solo fitness game, and the alternative — locking out a paying customer whose
 *    train went into a tunnel — is a far more expensive mistake than letting a determined
 *    attacker skip a few thousand won.
 *  - **Therefore: never trust the cache for anything irreversible.** No consumable grants, no
 *    server-side unlocks, no anything that cannot be taken back. It gates content rendering only.
 *    If this app ever gains a backend, entitlement checks move there and this cache becomes a
 *    display hint, nothing more.
 */
class PlayEntitlementRepository(
    context: Context,
    private val scope: CoroutineScope,
    val billing: BillingManager = BillingManager(context, scope),
) : EntitlementRepository {

    private val store = context.applicationContext.billingCacheStore

    private val cached: Flow<CachedEntitlement> = store.data
        // A corrupt or unreadable cache must degrade to "no cached answer", not crash the app on
        // launch. Anything other than IO is a real bug and should still blow up loudly.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toCachedEntitlement() }

    override val entitlement: StateFlow<Entitlement> =
        combine(billing.activePurchases, billing.plans, cached) { purchases, plans, cache ->
            derive(purchases, plans, cache, System.currentTimeMillis())
        }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, Entitlement())

    init {
        // Persisting is a side effect of hearing from Play, not of somebody observing the flow —
        // keeping it out of the combine above stops a write from re-triggering its own derivation.
        billing.activePurchases
            .filterNotNull()
            .onEach { persist(it) }
            .launchIn(scope)
        billing.start()
    }

    override suspend fun refresh() {
        billing.refresh()
    }

    /** Forward the Activity's ON_RESUME so an out-of-app purchase or cancellation is picked up. */
    fun onAppResume() {
        billing.onAppResume()
    }

    // --- derivation ---------------------------------------------------------------------------

    private fun derive(
        purchases: List<Purchase>?,
        plans: List<SubscriptionPlan>,
        cache: CachedEntitlement,
        nowMs: Long,
    ): Entitlement {
        if (purchases != null) {
            // Play answered. Whatever it said is the truth, including "you own nothing".
            val active = activeSubscription(purchases)
                ?: return Entitlement(verified = true)

            val trialEndsAtMs = active.purchaseTime + trialLengthMs(plans)
            val inTrial = !cache.trialConsumed && nowMs < trialEndsAtMs
            return Entitlement(
                isSubscriber = true,
                inTrial = inTrial,
                trialDaysRemaining = if (inTrial) daysUntil(trialEndsAtMs, nowMs) else 0,
                // Play Billing does not expose a subscription's expiry to the client; only the
                // Play Developer API does, and that needs a server. Renewal is Play's problem.
                expiresAtMs = null,
                verified = true,
            )
        }

        // Play did not answer: offline, Play Store updating, service down. Serve the last known
        // answer rather than revoking anything.
        if (cache.lastVerifiedAtMs == 0L) return Entitlement()

        // ...but a cache cannot live forever, or a cancelled subscriber who simply never goes
        // online again keeps full access indefinitely. Fourteen days is well past any plausible
        // offline stretch and well inside a monthly billing cycle, so a real subscriber never
        // notices it and a lapsed one loses access within one cycle.
        if (nowMs - cache.lastVerifiedAtMs > CACHE_MAX_AGE_MS) return Entitlement()

        val inTrial = cache.inTrial && nowMs < cache.trialEndsAtMs
        return Entitlement(
            isSubscriber = cache.isSubscriber,
            inTrial = inTrial,
            trialDaysRemaining = if (inTrial) daysUntil(cache.trialEndsAtMs, nowMs) else 0,
            expiresAtMs = null,
            verified = false,
        )
    }

    private suspend fun persist(purchases: List<Purchase>) {
        val nowMs = System.currentTimeMillis()
        val active = activeSubscription(purchases)
        val trialEndsAtMs = active?.let { it.purchaseTime + trialLengthMs(billing.plans.value) } ?: 0L

        store.edit { prefs ->
            val alreadyConsumed = prefs[KEY_TRIAL_CONSUMED] ?: false
            prefs[KEY_IS_SUBSCRIBER] = active != null
            prefs[KEY_TRIAL_ENDS_AT] = trialEndsAtMs
            // Once the trial window has demonstrably elapsed on a live subscription, latch it shut.
            // Renewals can move `purchaseTime` forward, and without this latch a renewing
            // subscriber would be told they are back on a free trial every billing cycle.
            val consumed = alreadyConsumed || (active != null && nowMs >= trialEndsAtMs)
            prefs[KEY_TRIAL_CONSUMED] = consumed
            prefs[KEY_IN_TRIAL] = active != null && !consumed && nowMs < trialEndsAtMs
            prefs[KEY_LAST_VERIFIED_AT] = nowMs
        }
    }

    private fun activeSubscription(purchases: List<Purchase>): Purchase? = purchases
        .firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                BillingProducts.grantsFullAccess(it.products)
        }

    /** Play's real trial length when we have it; the catalogue default when we do not. */
    private fun trialLengthMs(plans: List<SubscriptionPlan>): Long {
        val days = plans.firstOrNull { it.hasFreeTrial && it.freeTrialDays > 0 }?.freeTrialDays
            ?: BillingProducts.DEFAULT_TRIAL_DAYS
        return days * DAY_MS
    }

    private fun daysUntil(endMs: Long, nowMs: Long): Int {
        val remaining = endMs - nowMs
        if (remaining <= 0L) return 0
        // Round up: with nine hours left the honest thing to say is "1일 남음", not "0일 남음".
        return ((remaining + DAY_MS - 1) / DAY_MS).toInt()
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val CACHE_MAX_AGE_MS = 14L * DAY_MS

        val KEY_IS_SUBSCRIBER = booleanPreferencesKey("is_subscriber")
        val KEY_IN_TRIAL = booleanPreferencesKey("in_trial")
        val KEY_TRIAL_CONSUMED = booleanPreferencesKey("trial_consumed")
        val KEY_TRIAL_ENDS_AT = longPreferencesKey("trial_ends_at")
        val KEY_LAST_VERIFIED_AT = longPreferencesKey("last_verified_at")
    }

    private fun Preferences.toCachedEntitlement() = CachedEntitlement(
        isSubscriber = this[KEY_IS_SUBSCRIBER] ?: false,
        inTrial = this[KEY_IN_TRIAL] ?: false,
        trialConsumed = this[KEY_TRIAL_CONSUMED] ?: false,
        trialEndsAtMs = this[KEY_TRIAL_ENDS_AT] ?: 0L,
        lastVerifiedAtMs = this[KEY_LAST_VERIFIED_AT] ?: 0L,
    )
}

private data class CachedEntitlement(
    val isSubscriber: Boolean,
    val inTrial: Boolean,
    val trialConsumed: Boolean,
    val trialEndsAtMs: Long,
    /** 0 means the cache has never been written; distinct from "written, and it said free". */
    val lastVerifiedAtMs: Long,
)
