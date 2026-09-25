package com.pushuprpg.app

import android.content.Context
import android.os.Build
import com.pushuprpg.app.audio.GameAudio
import com.pushuprpg.app.audio.GameVoice
import com.pushuprpg.app.audio.MusicPlayer
import com.pushuprpg.app.billing.PlayEntitlementRepository
import com.pushuprpg.app.telemetry.Telemetry
import com.pushuprpg.app.data.AppDatabase
import com.pushuprpg.app.data.DataStoreProgressRepository
import com.pushuprpg.app.data.DataStoreSettingsRepository
import com.pushuprpg.app.data.RoomSessionRepository
import com.pushuprpg.app.domain.EntitlementRepository
import com.pushuprpg.app.domain.ProgressRepository
import com.pushuprpg.app.domain.SessionRepository
import com.pushuprpg.app.domain.SettingsRepository
import com.pushuprpg.app.trace.RunTraces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manual dependency injection.
 *
 * An app this size does not need a DI framework, and skipping one removes an annotation processor,
 * a compiler-plugin version to keep in step with Kotlin and AGP, and a layer of generated code
 * between a crash and its cause. One object constructed at startup is enough.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Outlives any screen. Billing has to keep listening for purchases that complete while the
     * user is somewhere else entirely — including in the Play Store app.
     *
     * A finished run is written here too. The screen that ended it is popped straight away, and a
     * write in that screen's own scope could be cancelled between the record row and the XP.
     */
    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val progressRepository: ProgressRepository by lazy {
        DataStoreProgressRepository(appContext)
    }

    val sessionRepository: SessionRepository by lazy {
        RoomSessionRepository(AppDatabase.get(appContext).sessionDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(appContext)
    }

    val entitlementRepository: EntitlementRepository by lazy {
        PlayEntitlementRepository(appContext, appScope)
    }

    /**
     * Shared across screens rather than created per battle: loading fifteen samples takes long
     * enough to be audible as a gap on the first rep of every run.
     */
    val audio: GameAudio by lazy { GameAudio(appContext) }

    /** One player for the whole app, so leaving one run and entering the next never plays two. */
    val music: MusicPlayer by lazy { MusicPlayer(appContext) }

    /** The spoken lines. One engine for the app: binding the TTS service takes a moment. */
    val voice: GameVoice by lazy { GameVoice(appContext, music) }

    val telemetry: Telemetry by lazy { Telemetry(appContext) }

    /**
     * The latest run's landmarks, for a bug report. Follows the setting, and only in a debug build:
     * the Play build must keep the privacy policy's promise that pose data is never stored.
     */
    val traces: RunTraces by lazy {
        RunTraces(
            device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, " +
                BuildConfig.VERSION_NAME,
        ).also { recorder ->
            if (BuildConfig.DEBUG) {
                appScope.launch { settingsRepository.settings.collect { recorder.enabled = it.recordTraces } }
            }
        }
    }

    /** The billing client, for the paywall's purchase flow. */
    val billing get() = (entitlementRepository as PlayEntitlementRepository).billing

    fun onAppStart() {
        billing.start()
    }

    fun onAppResume() {
        billing.onAppResume()
    }
}
