package com.pushuprpg.app.telemetry

import android.os.SystemClock

/**
 * Which screen people are on, how long they stay and which one they leave the app from. Main thread
 * only. Time with the app out of sight is not counted towards a screen.
 */
class ScreenTracker(private val telemetry: Telemetry) {
    private var screen: String? = null
    private var since = 0L

    val current: String get() = screen.orEmpty()

    fun entered(route: String?) {
        val next = route?.substringBefore('/') ?: return
        if (next == screen) return
        screen?.let { telemetry.log(Event.ScreenExit(it, next, elapsed())) }
        screen = next
        since = SystemClock.elapsedRealtime()
        telemetry.log(Event.ScreenView(next))
    }

    fun appHidden() {
        screen?.let { telemetry.log(Event.AppLeft(it, elapsed())) }
    }

    fun appShown() {
        since = SystemClock.elapsedRealtime()
    }

    private fun elapsed() = SystemClock.elapsedRealtime() - since
}
