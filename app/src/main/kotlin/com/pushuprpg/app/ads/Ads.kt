package com.pushuprpg.app.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.pushuprpg.app.BuildConfig
import com.pushuprpg.app.telemetry.Event
import com.pushuprpg.app.telemetry.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Where an ad is shown, as analytics names it. */
object AdPlacement {
    const val SHOP = "shop_reward"
    const val DOUBLE = "double_churu"
    const val HOME = "home_interstitial"
    const val RECORDS = "records_banner"
}

/**
 * AdMob, kept to one object. Loads start only from screens without the camera, except the 츄르 2배
 * button, which turns the camera off first. All methods are called on the main thread; the SDK's
 * start-up is the one thing moved off it.
 */
class Ads(context: Context, private val telemetry: Telemetry, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val started = AtomicBoolean(false)
    private var consentAsked = false

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val _ready = MutableStateFlow(false)
    /** Consent is settled and the SDK is up. False means no ad surface should be offered. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _rewardedReady = MutableStateFlow(false)
    val rewardedReady: StateFlow<Boolean> = _rewardedReady.asStateFlow()

    /** A full-screen ad is up, so MainActivity's onStop is not the user leaving the app. */
    @Volatile var fullScreenOpen = false
        private set

    private var rewarded: RewardedAd? = null
    private var rewardedAt = 0L
    private var rewardedLoading = false
    private var waiting: PendingShow? = null

    private var interstitial: InterstitialAd? = null
    private var interstitialAt = 0L
    private var interstitialLoading = false

    private class PendingShow(
        val activity: ComponentActivity,
        val placement: String,
        val onReward: () -> Unit,
        val onDone: (shown: Boolean) -> Unit,
    )

    /** Once a process: the Activity is recreated when a run's screen turns with the phone. */
    fun start(activity: Activity) {
        if (consentAsked) return
        consentAsked = true
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    settleConsent(consent)
                }
            },
            { settleConsent(consent) },
        )
        if (consent.canRequestAds()) initialise()
    }

    private fun settleConsent(consent: ConsentInformation) {
        _privacyOptionsRequired.value = consent.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (consent.canRequestAds()) initialise()
    }

    /** Lets someone in a region that requires it change their answer; see settings. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    private fun initialise() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { MobileAds.initialize(app) { _ready.value = true } }
    }

    fun loadRewarded(placement: String) {
        if (!_ready.value || rewardedLoading || freshRewarded() != null) return
        rewardedLoading = true
        log(placement, "load")
        RewardedAd.load(
            app,
            BuildConfig.ADMOB_REWARDED,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    rewarded = ad
                    rewardedAt = SystemClock.elapsedRealtime()
                    _rewardedReady.value = true
                    waiting?.let { pending ->
                        waiting = null
                        present(pending)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                    log(placement, "load_error", error.code)
                    waiting?.let { pending ->
                        waiting = null
                        pending.onDone(false)
                    }
                }
            },
        )
    }

    /** Shows a rewarded ad, loading one first if none is waiting. [onDone] always runs once. */
    fun showRewarded(
        activity: ComponentActivity,
        placement: String,
        onReward: () -> Unit,
        onDone: (shown: Boolean) -> Unit,
    ) {
        val pending = PendingShow(activity, placement, onReward, onDone)
        if (freshRewarded() != null) {
            present(pending)
        } else if (!_ready.value) {
            onDone(false)
        } else {
            waiting = pending
            loadRewarded(placement)
        }
    }

    /** The screen that asked is gone: an ad arriving now would land on a screen nobody asked it on. */
    fun cancelWaiting() {
        waiting?.let { pending ->
            waiting = null
            pending.onDone(false)
        }
    }

    private fun present(pending: PendingShow) {
        val ad = freshRewarded()
        if (ad == null || !pending.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            pending.onDone(false)
            return
        }
        rewarded = null
        _rewardedReady.value = false
        fullScreenOpen = true
        ad.fullScreenContentCallback = callback(pending.placement) { shown -> pending.onDone(shown) }
        ad.show(pending.activity) {
            log(pending.placement, "reward")
            pending.onReward()
        }
    }

    fun preloadInterstitial() {
        if (!_ready.value || interstitialLoading || freshInterstitial() != null) return
        interstitialLoading = true
        log(AdPlacement.HOME, "load")
        InterstitialAd.load(
            app,
            BuildConfig.ADMOB_INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    interstitial = ad
                    interstitialAt = SystemClock.elapsedRealtime()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    log(AdPlacement.HOME, "load_error", error.code)
                }
            },
        )
    }

    /** Only what is already loaded: an interstitial that arrives late lands under someone's thumb. */
    fun showInterstitial(activity: ComponentActivity, onShown: () -> Unit) {
        val ad = freshInterstitial() ?: return
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        interstitial = null
        fullScreenOpen = true
        ad.fullScreenContentCallback = callback(AdPlacement.HOME) { shown -> if (shown) onShown() }
        ad.show(activity)
    }

    private fun callback(placement: String, onDone: (Boolean) -> Unit) = object : FullScreenContentCallback() {
        private var shown = false

        override fun onAdShowedFullScreenContent() {
            shown = true
            log(placement, "show")
        }

        override fun onAdClicked() = log(placement, "click")

        override fun onAdDismissedFullScreenContent() {
            fullScreenOpen = false
            log(placement, "close")
            onDone(shown)
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            fullScreenOpen = false
            log(placement, "show_error", error.code)
            onDone(false)
        }
    }

    fun log(placement: String, action: String, code: Int? = null) {
        telemetry.log(Event.AdEvent(placement, action, code))
    }

    private fun freshRewarded(): RewardedAd? {
        if (rewarded != null && SystemClock.elapsedRealtime() - rewardedAt > MAX_AGE_MS) {
            rewarded = null
            _rewardedReady.value = false
        }
        return rewarded
    }

    private fun freshInterstitial(): InterstitialAd? {
        if (interstitial != null && SystemClock.elapsedRealtime() - interstitialAt > MAX_AGE_MS) interstitial = null
        return interstitial
    }

    private companion object {
        /** A loaded full-screen ad stops paying after about an hour. */
        const val MAX_AGE_MS = 55 * 60 * 1000L
    }
}
