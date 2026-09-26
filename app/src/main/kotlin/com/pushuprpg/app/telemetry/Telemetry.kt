package com.pushuprpg.app.telemetry

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.PoseQuality
import com.pushuprpg.core.game.Difficulty
import com.pushuprpg.core.run.TrackingDrops

/**
 * Crash reporting and product analytics.
 *
 * Inert unless Firebase has actually been initialised, which happens only when google-services.json
 * is present. That keeps the project buildable and runnable for anyone who clones it without a
 * Firebase project of their own, and it means every call site here is unconditional — no `if
 * (analytics != null)` scattered through the app.
 *
 * **What is and is not collected.** No camera frame, image, video or landmark ever passes through
 * here, and nothing in this file could carry one: the events are counts, durations and enum names.
 * That distinction has to survive contact with the privacy policy, which says video never leaves
 * the device — still true — while crash reports and these events do.
 */
class Telemetry(context: Context) {

    private val enabled: Boolean = runCatching {
        FirebaseApp.getApps(context).isNotEmpty()
    }.getOrDefault(false)

    private val analytics: FirebaseAnalytics? =
        if (enabled) runCatching { FirebaseAnalytics.getInstance(context) }.getOrNull() else null

    private val crashlytics: FirebaseCrashlytics? =
        if (enabled) runCatching { FirebaseCrashlytics.getInstance() }.getOrNull() else null

    fun log(event: Event) {
        if (!enabled) {
            if (BuildConfigBridge.debug) Log.d(TAG, "(inert) ${event.name} ${event.params}")
            return
        }
        val bundle = Bundle().apply {
            event.params.forEach { (k, v) ->
                when (v) {
                    is Int -> putLong(k, v.toLong())
                    is Long -> putLong(k, v)
                    is Float -> putDouble(k, v.toDouble())
                    is Double -> putDouble(k, v)
                    is Boolean -> putLong(k, if (v) 1L else 0L)
                    else -> putString(k, v.toString())
                }
            }
        }
        analytics?.logEvent(event.name, bundle)
    }

    /**
     * A caught problem worth knowing about.
     *
     * Used for the things that are recoverable but should not be common — a pose model that failed
     * to load, a billing client that never connected. Uncaught crashes need no help from here.
     */
    fun recordNonFatal(throwable: Throwable, context: String) {
        crashlytics?.apply {
            log(context)
            recordException(throwable)
        }
        Log.w(TAG, context, throwable)
    }

    /** Attached to every crash, so a report says which movement was being tracked. */
    fun setExercise(exercise: ExerciseType) {
        crashlytics?.setCustomKey("exercise", exercise.name)
    }

    fun setSubscriber(isSubscriber: Boolean) {
        crashlytics?.setCustomKey("subscriber", isSubscriber)
        analytics?.setUserProperty("subscriber", isSubscriber.toString())
    }

    private object BuildConfigBridge {
        val debug: Boolean get() = com.pushuprpg.app.BuildConfig.DEBUG
    }

    private companion object {
        const val TAG = "Telemetry"
    }
}

/**
 * The events worth having from day one.
 *
 * Deliberately short. The two questions that actually need answering are where the trial-to-paid
 * funnel leaks, which the onboarding and paywall events cover, and whether rep detection works on
 * hardware nobody here has ever held — which is what [RunFinished]'s tracking numbers and
 * plausibility, and [QualityLost]'s reasons, are for. Every detection constant in this app is
 * reasoned rather than measured, and without a signal from real phones there is no way to tune them.
 */
sealed class Event(val name: String, val params: Map<String, Any> = emptyMap()) {

    data object OnboardingStarted : Event("onboarding_started")

    data class ClassPicked(val playerClass: String) :
        Event("class_picked", mapOf("class" to playerClass))

    /** A later change, from the hub or settings. Kept apart from [ClassPicked] so onboarding's
     *  choice is not blurred by people trying the others. */
    data class ClassChanged(val from: String, val to: String) :
        Event("class_changed", mapOf("from" to from, "to" to to))

    data class PermissionResolved(val granted: Boolean) :
        Event("camera_permission", mapOf("granted" to granted))

    /**
     * The tutorial's screen opened: H3's denominator. Started minus completed minus skipped is
     * everyone who closed the app on the camera screen without either.
     */
    data object TutorialStarted : Event("tutorial_started")

    /**
     * The tutorial run, which is also the calibration set. Sent however it ended once the ceiling
     * moved, so [ended] and [nearMisses] are what tell a first minute that worked from one that did
     * not: a run the camera counted nothing in is a completion too.
     */
    data class TutorialCompleted(
        val reps: Int,
        val durationMs: Long,
        val ended: TutorialEnd,
        /** Reps the detector saw and refused as not deep enough while the ceiling was up. */
        val nearMisses: Int,
    ) : Event(
        "tutorial_completed",
        mapOf(
            "reps" to reps,
            "duration_ms" to durationMs,
            "ended" to ended.name,
            "near_misses" to nearMisses,
        ),
    )

    /** Left before its run started, so nothing was measured: how many never got a first rep in. */
    data object TutorialSkipped : Event("tutorial_skipped")

    data class RunStarted(
        val dungeon: Int,
        val difficulty: Difficulty,
        val exercise: ExerciseType,
    ) : Event(
        "run_started",
        mapOf(
            "dungeon" to dungeon,
            "difficulty" to difficulty.name,
            "exercise" to exercise.name,
        ),
    )

    data class RunFinished(
        val dungeon: Int,
        val cleared: Boolean,
        val reps: Int,
        val durationMs: Long,
        val plausibility: Float,
        /** How often tracking lost the user once they were in position; see [TrackingDrops]. */
        val tracking: TrackingDrops.Summary,
    ) : Event(
        "run_finished",
        mapOf(
            "dungeon" to dungeon,
            "cleared" to cleared,
            "reps" to reps,
            "duration_ms" to durationMs,
            // Below 0.85 the detector itself flagged the session. A population of these is the
            // first hint that the thresholds are wrong for some device or some body.
            "plausibility" to plausibility,
            // H2, per run: whether the user was ever in position, and after that how often the
            // tracker lost them, how often it found them again and for how long in all. The run's
            // last seconds are left out: that is the user getting up to end it.
            "armed" to tracking.armed,
            "lost_count" to tracking.drops,
            "lost_recovered" to tracking.recovered,
            "lost_ms" to tracking.lostMs,
        ),
    )

    /**
     * Tracking dropped mid-run.
     *
     * How "it stopped counting my last three reps" turns into something anyone can act on, broken
     * down by reason and by device. It is one event per drop with no run to sum it over, and it
     * fires as readily for the user getting up to quit, so how often runs lose the user is read from
     * [RunFinished]; this is for why. [armed] says whether the movement had armed before it.
     */
    data class QualityLost(val quality: PoseQuality, val atRep: Int, val armed: Boolean) :
        Event("quality_lost", mapOf("reason" to quality.name, "at_rep" to atRep, "armed" to armed))

    data class PaywallShown(val source: String) :
        Event("paywall_shown", mapOf("source" to source))

    data class PurchaseStarted(val plan: String) :
        Event("purchase_started", mapOf("plan" to plan))

    data class PurchaseCompleted(val plan: String) :
        Event("purchase_completed", mapOf("plan" to plan))

    /**
     * A run met the day's streak bar, and the streak is now [days] long. Sent once for each day
     * that counts, so a second run on a day already kept sends nothing.
     */
    data class StreakMaintained(val days: Int) :
        Event("streak_maintained", mapOf("days" to days))
}

/** How the tutorial ended, once its ceiling had started to move. */
enum class TutorialEnd {
    /** The ceiling came down and the done card was shown, however the card was then left. */
    CRUSHED,

    /** Back, while the ceiling was still coming. */
    BACK,

    /** The screen went while the ceiling was still coming: the app closed from recents, say. */
    CLOSED,
}
