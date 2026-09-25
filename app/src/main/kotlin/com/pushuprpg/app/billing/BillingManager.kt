package com.pushuprpg.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.random.Random

/** Where the connection to Play currently stands, for a paywall that wants to explain itself. */
enum class BillingAvailability {
    /** Nothing has been attempted yet. */
    UNKNOWN,

    /** Connecting, or reconnecting after a drop. Purchases may still be served from cache. */
    CONNECTING,

    READY,

    /** Play Billing will not work on this device or build: no store, a sideload, a dev error. */
    UNAVAILABLE,
}

/** One-shot things the paywall may want to react to. Never state — state lives in the flows. */
sealed interface BillingEvent {
    data object PurchaseCompleted : BillingEvent

    /** Bought with a payment that clears later: nothing is granted until Play says it has. */
    data object PurchasePending : BillingEvent

    data object PurchaseCancelled : BillingEvent
    data object AlreadyOwned : BillingEvent
    data class PurchaseFailed(val responseCode: Int) : BillingEvent
}

/** What 구매 복원 found, so the paywall can say so rather than look as if nothing happened. */
enum class RestoreOutcome {
    FOUND,

    /** Play answered, and this account holds no subscription. */
    NOTHING,

    /** Play could not be asked; nothing is known either way. */
    UNREACHABLE,
}

/**
 * All Play Billing plumbing, and the only place in the app that imports `com.android.billingclient`
 * besides the product mapping in [BillingProducts].
 *
 * Threading: every Play callback can land on an arbitrary thread and can land more than once for
 * the same event. So all outward state is a [MutableStateFlow] (safe to write from anywhere),
 * every continuation is resumed behind an [AtomicBoolean] latch, and acknowledgement is
 * de-duplicated by purchase token. Nothing here assumes a callback is unique or ordered.
 */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    private val _availability = MutableStateFlow(BillingAvailability.UNKNOWN)
    val availability: StateFlow<BillingAvailability> = _availability.asStateFlow()

    /**
     * Active subscription purchases as Play last reported them.
     *
     * `null` means "we have never had an answer from Play", which is meaningfully different from
     * an empty list ("Play says this user owns nothing"). The entitlement layer leans on that
     * distinction to decide whether it is allowed to revoke access.
     */
    private val _activePurchases = MutableStateFlow<List<Purchase>?>(null)
    val activePurchases: StateFlow<List<Purchase>?> = _activePurchases.asStateFlow()

    private val _plans = MutableStateFlow<List<SubscriptionPlan>>(emptyList())
    val plans: StateFlow<List<SubscriptionPlan>> = _plans.asStateFlow()

    /**
     * True once a refresh has run to its end with still no plan to sell: Play unreachable, or
     * reachable and offering nothing — no network, signed out, a build Play will not sell from.
     * Until then an empty [plans] means "still asking", and the paywall can say which.
     */
    private val _plansUnavailable = MutableStateFlow(false)
    val plansUnavailable: StateFlow<Boolean> = _plansUnavailable.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    private val productDetails = ConcurrentHashMap<String, ProductDetails>()
    private val acknowledging = ConcurrentHashMap.newKeySet<String>()

    private val connectMutex = Mutex()
    private val refreshMutex = Mutex()

    /** Set when Play tells us this device can never transact, so we stop burning retries. */
    @Volatile
    private var permanentlyUnavailable = false

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                scope.launch {
                    val bought = purchases.orEmpty()
                    // Paid by a method that clears later is not paid yet, and saying it was would
                    // leave the user looking at a paywall that has just told them they are in.
                    val pending = bought.isNotEmpty() &&
                        bought.none { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    // Before the acknowledgement, which waits on Play's servers: the resume's own
                    // refresh can open the entitlement and close the paywall first, and the paywall
                    // is what logs purchase_completed.
                    _events.tryEmit(if (pending) BillingEvent.PurchasePending else BillingEvent.PurchaseCompleted)
                    bought.forEach { acknowledgeIfNeeded(it) }
                    // The callback carries only what just changed; re-query for the whole picture.
                    refresh()
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED ->
                _events.tryEmit(BillingEvent.PurchaseCancelled)

            // Usually a purchase made on another device, or a flow we already completed and missed
            // the callback for. Either way the fix is the same: go ask Play what is owned.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _events.tryEmit(BillingEvent.AlreadyOwned)
                scope.launch { refresh() }
            }

            else -> _events.tryEmit(BillingEvent.PurchaseFailed(result.responseCode))
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        // Required since PBL 8 even for a subscription-only app.
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        // The library then re-establishes a dropped binding itself; our own backoff below only has
        // to cover the initial connect and the "Play was never reachable" case.
        .enableAutoServiceReconnection()
        .build()

    /** Call once, at app start. */
    fun start() {
        scope.launch { refresh() }
    }

    /**
     * Call from the Activity's ON_RESUME. A subscription can be bought, cancelled, refunded or
     * restored entirely outside the app, and returning to the foreground is the only moment we are
     * guaranteed to notice.
     */
    fun onAppResume() {
        scope.launch { refresh() }
    }

    /**
     * Re-reads purchases and plan pricing from Play, and says whether Play answered for the
     * purchases.
     *
     * Leaves the last known values in place on failure rather than clearing them: an empty answer
     * we invented is indistinguishable, downstream, from Play saying the user owns nothing.
     */
    suspend fun refresh(): Boolean = refreshMutex.withLock {
        _plansUnavailable.value = false
        if (!ensureConnected()) {
            _plansUnavailable.value = _plans.value.isEmpty()
            return@withLock false
        }

        val purchases = queryPurchasesOnce()
        purchases?.let {
            it.forEach { purchase -> acknowledgeIfNeeded(purchase) }
            _activePurchases.value = it
        }

        queryPlansOnce().takeIf { it.isNotEmpty() }?.let { _plans.value = it }
        _plansUnavailable.value = _plans.value.isEmpty()
        purchases != null
    }

    /** 구매 복원: asks Play again what this account owns. */
    suspend fun restore(): RestoreOutcome {
        if (!refresh()) return RestoreOutcome.UNREACHABLE
        val owned = _activePurchases.value.orEmpty().any {
            it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                BillingProducts.grantsFullAccess(it.products)
        }
        return if (owned) RestoreOutcome.FOUND else RestoreOutcome.NOTHING
    }

    /**
     * Starts Play's purchase sheet. Must be called on the main thread with a started Activity.
     *
     * Returns whether the sheet opened; the actual outcome arrives later on [events].
     */
    fun launchPurchaseFlow(activity: Activity, plan: SubscriptionPlan): Boolean {
        if (!client.isReady) {
            scope.launch { refresh() }
            return false
        }

        val details = productDetails[plan.productId] ?: return false

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(plan.offerToken)
                        .build()
                )
            )
            .build()

        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "launchBillingFlow refused: ${result.responseCode} ${result.debugMessage}")
        }
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun dispose() {
        runCatching { client.endConnection() }
    }

    // --- connection ---------------------------------------------------------------------------

    private suspend fun ensureConnected(): Boolean {
        if (client.isReady) {
            _availability.value = BillingAvailability.READY
            return true
        }
        if (permanentlyUnavailable) return false

        connectMutex.withLock {
            if (client.isReady) {
                _availability.value = BillingAvailability.READY
                return true
            }
            if (permanentlyUnavailable) return false

            _availability.value = BillingAvailability.CONNECTING
            var backoffMs = INITIAL_BACKOFF_MS
            var attempt = 0

            while (attempt < MAX_CONNECT_ATTEMPTS) {
                val code = connectOnce()
                if (code == BillingClient.BillingResponseCode.OK) {
                    _availability.value = BillingAvailability.READY
                    return true
                }
                if (code == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
                    // Retrying in a loop will not help — but unlike the genuinely permanent codes,
                    // this one is usually the user's to fix: Play out of date, not signed in, a
                    // declined card, a managed-device restriction. Give up on this attempt without
                    // latching, so the next refresh or app resume can try again once they have.
                    _availability.value = BillingAvailability.UNAVAILABLE
                    Log.w(TAG, "billing unavailable, retryable on resume: $code")
                    return false
                }
                if (isPermanent(code)) {
                    permanentlyUnavailable = true
                    _availability.value = BillingAvailability.UNAVAILABLE
                    Log.w(TAG, "billing permanently unavailable: $code")
                    return false
                }
                attempt++
                if (attempt < MAX_CONNECT_ATTEMPTS) {
                    // Jitter so a whole user base coming back online after an outage does not
                    // arrive at Play in lockstep.
                    delay(backoffMs + Random.nextLong(JITTER_MS))
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
            }

            // Still down, but not fatally: stay CONNECTING so the UI says "reconnecting", and let
            // the next refresh() start a fresh round of attempts.
            _availability.value = BillingAvailability.CONNECTING
        }
        return false
    }

    private suspend fun connectOnce(): Int = suspendCancellableCoroutine { cont ->
        val resumed = AtomicBoolean(false)
        fun finish(code: Int) {
            if (resumed.compareAndSet(false, true)) cont.resume(code)
        }
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    finish(billingResult.responseCode)
                }

                override fun onBillingServiceDisconnected() {
                    if (_availability.value == BillingAvailability.READY) {
                        _availability.value = BillingAvailability.CONNECTING
                    }
                    finish(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                }
            })
        } catch (e: IllegalStateException) {
            // Thrown when a connection attempt is already in flight; treat it as a soft failure so
            // the caller backs off and tries again rather than crashing.
            Log.w(TAG, "startConnection rejected", e)
            finish(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
        }
    }

    /**
     * Codes worth latching on.
     *
     * Only the ones that cannot change without a new build or a different device. BILLING_UNAVAILABLE
     * deliberately is not here: it usually means something the user can fix, and latching would keep
     * them locked out for the rest of the process after they had.
     */
    private fun isPermanent(code: Int): Boolean = when (code) {
        BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
        BillingClient.BillingResponseCode.DEVELOPER_ERROR -> true

        else -> false
    }

    // --- queries ------------------------------------------------------------------------------

    /** `null` on failure, so callers can tell "no purchases" from "no answer". */
    private suspend fun queryPurchasesOnce(): List<Purchase>? =
        suspendCancellableCoroutine { cont ->
            // Suspended subscriptions (on hold after a failed payment) are deliberately not
            // requested: Play has stopped charging, so access should stop too.
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            val resumed = AtomicBoolean(false)
            client.queryPurchasesAsync(params) { result, purchases ->
                if (resumed.compareAndSet(false, true)) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        cont.resume(purchases)
                    } else {
                        Log.w(TAG, "queryPurchases failed: ${result.responseCode}")
                        cont.resume(null)
                    }
                }
            }
        }

    private suspend fun queryPlansOnce(): List<SubscriptionPlan> =
        suspendCancellableCoroutine { cont ->
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    BillingProducts.SUBSCRIPTION_IDS.map { id ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(id)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    }
                )
                .build()

            val resumed = AtomicBoolean(false)
            client.queryProductDetailsAsync(params) { result, queryResult ->
                if (resumed.compareAndSet(false, true)) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "queryProductDetails failed: ${result.responseCode}")
                        cont.resume(emptyList())
                    } else {
                        val details = queryResult.productDetailsList
                        details.forEach { productDetails[it.productId] = it }
                        for (unfetched in queryResult.unfetchedProductList) {
                            // Almost always a console/build mismatch: wrong product id, or an
                            // unsigned build that Play will not sell from.
                            Log.w(TAG, "product not fetched: $unfetched")
                        }
                        cont.resume(details.flatMap { it.toSubscriptionPlans() })
                    }
                }
            }
        }

    // --- acknowledgement ----------------------------------------------------------------------

    /**
     * Play auto-refunds a purchase that is not acknowledged within three days, so this has to run
     * on every purchase we ever see, not just on the one that came back from the buy flow.
     */
    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (purchase.isAcknowledged) return

        val token = purchase.purchaseToken
        // Duplicate callbacks for one purchase are normal; acknowledging twice is not.
        if (!acknowledging.add(token)) return

        val code = suspendCancellableCoroutine<Int> { cont ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(token)
                .build()
            val resumed = AtomicBoolean(false)
            client.acknowledgePurchase(params) { result ->
                if (resumed.compareAndSet(false, true)) cont.resume(result.responseCode)
            }
        }

        if (code != BillingClient.BillingResponseCode.OK) {
            // Let the next refresh try again — an unacknowledged purchase becomes a refund.
            acknowledging.remove(token)
            Log.w(TAG, "acknowledge failed: $code")
        }
    }

    private companion object {
        const val TAG = "BillingManager"
        const val MAX_CONNECT_ATTEMPTS = 5
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val JITTER_MS = 500L
    }
}
