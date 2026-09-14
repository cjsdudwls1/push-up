package com.pushuprpg.app

import android.content.Context
import com.pushuprpg.app.audio.GameAudio
import com.pushuprpg.app.billing.PlayEntitlementRepository
import com.pushuprpg.app.data.AppDatabase
import com.pushuprpg.app.data.DataStoreProgressRepository
import com.pushuprpg.app.data.DataStoreSettingsRepository
import com.pushuprpg.app.data.RoomSessionRepository
import com.pushuprpg.app.domain.EntitlementRepository
import com.pushuprpg.app.domain.ProgressRepository
import com.pushuprpg.app.domain.SessionRepository
import com.pushuprpg.app.domain.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

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
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    /** The billing client, for the paywall's purchase flow. */
    val billing get() = (entitlementRepository as PlayEntitlementRepository).billing

    fun onAppStart() {
        billing.start()
    }

    fun onAppResume() {
        billing.onAppResume()
    }
}
