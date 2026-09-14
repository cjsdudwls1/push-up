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
 * hardware nobody here has ever held — which is what [QualityLost] and [RunFinished]'s plausibility
 * are for. Every detection constant in this app is reasoned rather than measured, and without a
 * signal from real phones there is no way to tune them.
 */
sealed class Event(val name: String, val params: Map<String, Any> = emptyMap()) {

    data object OnboardingStarted : Event("onboarding_started")

    data class ClassPicked(val playerClass: String) :
        Event("class_picked", mapOf("class" to playerClass))

    data class PermissionResolved(val granted: Boolean) :
        Event("camera_permission", mapOf("granted" to granted))

    /** The tutorial run, which is also the calibration set. */
    data class TutorialCompleted(val reps: Int, val durationMs: Long) :
        Event("tutorial_completed", mapOf("reps" to reps, "duration_ms" to durationMs))

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
        ),
    )

    /**
     * Tracking dropped mid-run.
     *
     * The single most useful signal in this list: it is how "it stopped counting my last three
     * reps" turns into something anyone can act on, broken down by reason and by device.
     */
    data class QualityLost(val quality: PoseQuality, val atRep: Int) :
        Event("quality_lost", mapOf("reason" to quality.name, "at_rep" to atRep))

    data class PaywallShown(val source: String) :
        Event("paywall_shown", mapOf("source" to source))

    data class PurchaseStarted(val plan: String) :
        Event("purchase_started", mapOf("plan" to plan))

    data class PurchaseCompleted(val plan: String) :
        Event("purchase_completed", mapOf("plan" to plan))

    data class StreakMaintained(val days: Int) :
        Event("streak_maintained", mapOf("days" to days))
}
