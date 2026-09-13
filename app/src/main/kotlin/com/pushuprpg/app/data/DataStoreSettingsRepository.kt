package com.pushuprpg.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pushuprpg.app.domain.AppSettings
import com.pushuprpg.app.domain.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val SETTINGS_DEFAULT = AppSettings()

private val KEY_SKELETON_MODE = stringPreferencesKey("skeleton_mode")
private val KEY_GAUGE_ON_RIGHT = booleanPreferencesKey("gauge_on_right")
private val KEY_SHOW_GAUGE_NUMBER = booleanPreferencesKey("show_gauge_number")
private val KEY_SFX = booleanPreferencesKey("sfx_enabled")
private val KEY_MUSIC = booleanPreferencesKey("music_enabled")
private val KEY_VOICE = booleanPreferencesKey("voice_enabled")
private val KEY_CAPTIONS = booleanPreferencesKey("captions_enabled")
private val KEY_HAPTIC = stringPreferencesKey("haptic_strength")
private val KEY_COLOUR_BLIND_SAFE = booleanPreferencesKey("colour_blind_safe")
private val KEY_REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
private val KEY_AUDIO_ONLY = booleanPreferencesKey("audio_only")
private val KEY_LARGE_TEXT = booleanPreferencesKey("large_text")
private val KEY_EXERCISE = stringPreferencesKey("exercise")
private val KEY_DIFFICULTY = stringPreferencesKey("difficulty")

private fun Preferences.toSettings(): AppSettings = AppSettings(
    skeletonMode = enumOrDefault(this[KEY_SKELETON_MODE], SETTINGS_DEFAULT.skeletonMode),
    gaugeOnRight = this[KEY_GAUGE_ON_RIGHT] ?: SETTINGS_DEFAULT.gaugeOnRight,
    showGaugeNumber = this[KEY_SHOW_GAUGE_NUMBER] ?: SETTINGS_DEFAULT.showGaugeNumber,
    sfxEnabled = this[KEY_SFX] ?: SETTINGS_DEFAULT.sfxEnabled,
    musicEnabled = this[KEY_MUSIC] ?: SETTINGS_DEFAULT.musicEnabled,
    voiceEnabled = this[KEY_VOICE] ?: SETTINGS_DEFAULT.voiceEnabled,
    captionsEnabled = this[KEY_CAPTIONS] ?: SETTINGS_DEFAULT.captionsEnabled,
    hapticStrength = enumOrDefault(this[KEY_HAPTIC], SETTINGS_DEFAULT.hapticStrength),
    colourBlindSafe = this[KEY_COLOUR_BLIND_SAFE] ?: SETTINGS_DEFAULT.colourBlindSafe,
    reduceMotion = this[KEY_REDUCE_MOTION] ?: SETTINGS_DEFAULT.reduceMotion,
    audioOnly = this[KEY_AUDIO_ONLY] ?: SETTINGS_DEFAULT.audioOnly,
    largeText = this[KEY_LARGE_TEXT] ?: SETTINGS_DEFAULT.largeText,
    exercise = enumOrDefault(this[KEY_EXERCISE], SETTINGS_DEFAULT.exercise),
    difficulty = enumOrDefault(this[KEY_DIFFICULTY], SETTINGS_DEFAULT.difficulty),
)

private fun MutablePreferences.writeSettings(s: AppSettings) {
    this[KEY_SKELETON_MODE] = s.skeletonMode.name
    this[KEY_GAUGE_ON_RIGHT] = s.gaugeOnRight
    this[KEY_SHOW_GAUGE_NUMBER] = s.showGaugeNumber
    this[KEY_SFX] = s.sfxEnabled
    this[KEY_MUSIC] = s.musicEnabled
    this[KEY_VOICE] = s.voiceEnabled
    this[KEY_CAPTIONS] = s.captionsEnabled
    this[KEY_HAPTIC] = s.hapticStrength.name
    this[KEY_COLOUR_BLIND_SAFE] = s.colourBlindSafe
    this[KEY_REDUCE_MOTION] = s.reduceMotion
    this[KEY_AUDIO_ONLY] = s.audioOnly
    this[KEY_LARGE_TEXT] = s.largeText
    this[KEY_EXERCISE] = s.exercise.name
    this[KEY_DIFFICULTY] = s.difficulty.name
}

/**
 * User-facing settings, in their own DataStore file so wiping settings can never touch progress.
 *
 * Every enum is stored by name and read back through [enumOrDefault]: a name written by a newer
 * build resolves to the shipping default instead of crashing a downgraded install on launch.
 */
class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val store = context.applicationContext.settingsStore

    override val settings: Flow<AppSettings> =
        store.data.orEmptyOnIoError()
            .map { it.toSettings() }
            .flowOn(Dispatchers.IO)

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        withContext(Dispatchers.IO) {
            store.edit { prefs -> prefs.writeSettings(transform(prefs.toSettings())) }
        }
    }

    suspend fun current(): AppSettings = settings.first()

    /** Resets to the shipping defaults without disturbing progress or calibration. */
    suspend fun reset() {
        withContext(Dispatchers.IO) {
            store.edit { prefs -> prefs.clear() }
        }
    }
}
