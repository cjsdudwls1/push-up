package com.pushuprpg.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pushuprpg.app.domain.PlayerProgress
import com.pushuprpg.app.domain.ProgressRepository
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.UserProfile
import com.pushuprpg.core.progression.Streak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.ZoneId

// A corrupt store is only half-visible without this: reads are masked because
    // CorruptionException extends IOException and the read path already swallows those, but every
    // write throws forever, so the app looks fine until the user changes something and then can
    // never change anything again. Replacing the file is the only recovery they could not perform
    // themselves.
private val Context.progressStore: DataStore<Preferences> by preferencesDataStore(
    name = "progress",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
private val Context.calibrationStore: DataStore<Preferences> by preferencesDataStore(
    name = "calibration",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Resolves a stored enum name, falling back to [default] when the name is absent or unknown.
 *
 * A build that removes or renames a constant must not take the user's save file down with it, so
 * an unreadable value degrades to the shipping default instead of throwing.
 */
internal inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    if (name == null) default else enumValues<T>().firstOrNull { it.name == name } ?: default

/** A read error on a preferences file is recoverable: treat it as "nothing saved yet". */
internal fun Flow<Preferences>.orEmptyOnIoError(): Flow<Preferences> =
    catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

private val PROGRESS_DEFAULT = PlayerProgress()

private val KEY_CLASS = stringPreferencesKey("player_class")
private val KEY_LEVEL = intPreferencesKey("level")
private val KEY_XP = intPreferencesKey("xp_into_level")
private val KEY_LIFETIME_REPS = intPreferencesKey("lifetime_reps")
private val KEY_BEST_COMBO = intPreferencesKey("best_combo")
private val KEY_TOTAL_ACTIVE_MS = longPreferencesKey("total_active_ms")
private val KEY_STREAK_DAYS = intPreferencesKey("streak_days")
private val KEY_BEST_STREAK_DAYS = intPreferencesKey("best_streak_days")
private val KEY_LAST_ACTIVE_DAY = longPreferencesKey("last_active_epoch_day")
private val KEY_HIGHEST_DUNGEON = intPreferencesKey("highest_dungeon_cleared")
private val KEY_CAPACITY_PUSHUP = floatPreferencesKey("capacity_pushup")
private val KEY_CAPACITY_SQUAT = floatPreferencesKey("capacity_squat")
private val KEY_CAPACITY_PLANK = floatPreferencesKey("capacity_plank_seconds")
private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")

private fun calibrationTopKey(e: ExerciseType) = floatPreferencesKey("cal_${e.name}_top")
private fun calibrationBotKey(e: ExerciseType) = floatPreferencesKey("cal_${e.name}_bot")
private fun calibrationSessionsKey(e: ExerciseType) = intPreferencesKey("cal_${e.name}_sessions")

private fun Preferences.toProgress(): PlayerProgress = PlayerProgress(
    playerClass = enumOrDefault(this[KEY_CLASS], PROGRESS_DEFAULT.playerClass),
    level = this[KEY_LEVEL] ?: PROGRESS_DEFAULT.level,
    xpIntoLevel = this[KEY_XP] ?: PROGRESS_DEFAULT.xpIntoLevel,
    lifetimeReps = this[KEY_LIFETIME_REPS] ?: PROGRESS_DEFAULT.lifetimeReps,
    bestCombo = this[KEY_BEST_COMBO] ?: PROGRESS_DEFAULT.bestCombo,
    totalActiveMs = this[KEY_TOTAL_ACTIVE_MS] ?: PROGRESS_DEFAULT.totalActiveMs,
    streakDays = this[KEY_STREAK_DAYS] ?: PROGRESS_DEFAULT.streakDays,
    bestStreakDays = this[KEY_BEST_STREAK_DAYS] ?: PROGRESS_DEFAULT.bestStreakDays,
    lastActiveEpochDay = this[KEY_LAST_ACTIVE_DAY] ?: PROGRESS_DEFAULT.lastActiveEpochDay,
    highestDungeonCleared = this[KEY_HIGHEST_DUNGEON] ?: PROGRESS_DEFAULT.highestDungeonCleared,
    capacityPushup = this[KEY_CAPACITY_PUSHUP] ?: PROGRESS_DEFAULT.capacityPushup,
    capacitySquat = this[KEY_CAPACITY_SQUAT] ?: PROGRESS_DEFAULT.capacitySquat,
    capacityPlankSeconds = this[KEY_CAPACITY_PLANK] ?: PROGRESS_DEFAULT.capacityPlankSeconds,
    bestSurvivalScore = this[KEY_BEST_SURVIVAL] ?: PROGRESS_DEFAULT.bestSurvivalScore,
    classChosen = this[KEY_CLASS_CHOSEN] ?: PROGRESS_DEFAULT.classChosen,
    onboarded = this[KEY_ONBOARDED] ?: PROGRESS_DEFAULT.onboarded,
)

private fun MutablePreferences.writeProgress(p: PlayerProgress) {
    this[KEY_CLASS] = p.playerClass.name
    this[KEY_LEVEL] = p.level
    this[KEY_XP] = p.xpIntoLevel
    this[KEY_LIFETIME_REPS] = p.lifetimeReps
    this[KEY_BEST_COMBO] = p.bestCombo
    this[KEY_TOTAL_ACTIVE_MS] = p.totalActiveMs
    this[KEY_STREAK_DAYS] = p.streakDays
    this[KEY_BEST_STREAK_DAYS] = p.bestStreakDays
    this[KEY_LAST_ACTIVE_DAY] = p.lastActiveEpochDay
    this[KEY_HIGHEST_DUNGEON] = p.highestDungeonCleared
    this[KEY_CAPACITY_PUSHUP] = p.capacityPushup
    this[KEY_CAPACITY_SQUAT] = p.capacitySquat
    this[KEY_CAPACITY_PLANK] = p.capacityPlankSeconds
    this[KEY_BEST_SURVIVAL] = p.bestSurvivalScore
    this[KEY_CLASS_CHOSEN] = p.classChosen
    this[KEY_ONBOARDED] = p.onboarded
}

private fun Preferences.toCalibration(exercise: ExerciseType): UserProfile {
    val sessions = this[calibrationSessionsKey(exercise)] ?: 0
    if (sessions <= 0) return UserProfile.empty()
    return UserProfile(
        topEwma = this[calibrationTopKey(exercise)] ?: 0f,
        botEwma = this[calibrationBotKey(exercise)] ?: 0f,
        sessionCount = sessions,
    )
}

/**
 * Pure streak transition, so the day arithmetic can be tested without a DataStore.
 *
 * Same day is a no-op, the next day increments, and a gap resumes from [Streak.afterBreak] of the
 * run that was broken plus the day being recorded — a missed week costs half a streak, not all of
 * it. A day earlier than the last recorded one (a backfill, or a user who moved their clock) is
 * ignored rather than allowed to rewind the streak. A first-ever day falls out of the gap branch,
 * since `afterBreak(0) + 1 == 1`.
 */
internal fun advanceStreak(p: PlayerProgress, epochDay: Long): PlayerProgress {
    if (epochDay <= p.lastActiveEpochDay) return p
    val days =
        if (epochDay == p.lastActiveEpochDay + 1) p.streakDays + 1
        else Streak.afterBreak(p.streakDays) + 1
    return p.copy(
        streakDays = days,
        bestStreakDays = maxOf(p.bestStreakDays, days),
        lastActiveEpochDay = epochDay,
    )
}

/**
 * Player progress and per-exercise calibration, in two DataStore files.
 *
 * They are separate because they change on very different rhythms: progress is rewritten after
 * every run, calibration only when the detector converges on a better range. [zone] is injectable
 * so the day boundary can be tested without touching the device clock.
 */
class DataStoreProgressRepository(
    context: Context,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ProgressRepository {

    private val store = context.applicationContext.progressStore
    private val calibration = context.applicationContext.calibrationStore

    override val progress: Flow<PlayerProgress> =
        store.data.orEmptyOnIoError()
            .map { it.toProgress() }
            .flowOn(Dispatchers.IO)

    override suspend fun current(): PlayerProgress = progress.first()

    override suspend fun update(transform: (PlayerProgress) -> PlayerProgress) {
        withContext(Dispatchers.IO) {
            store.edit { prefs -> prefs.writeProgress(transform(prefs.toProgress())) }
        }
    }

    /** Records that [epochDay] met the streak bar and returns the progress that resulted. */
    suspend fun recordActiveDay(epochDay: Long = CalendarDays.today(zone)): PlayerProgress =
        withContext(Dispatchers.IO) {
            store.edit { prefs ->
                prefs.writeProgress(advanceStreak(prefs.toProgress(), epochDay))
            }.toProgress()
        }

    override suspend fun calibrationProfile(exercise: ExerciseType): UserProfile =
        withContext(Dispatchers.IO) {
            calibration.data.orEmptyOnIoError().first().toCalibration(exercise)
        }

    override suspend fun saveCalibrationProfile(exercise: ExerciseType, profile: UserProfile) {
        withContext(Dispatchers.IO) {
            calibration.edit { prefs ->
                prefs[calibrationTopKey(exercise)] = profile.topEwma
                prefs[calibrationBotKey(exercise)] = profile.botEwma
                prefs[calibrationSessionsKey(exercise)] = profile.sessionCount
            }
        }
    }

    /** Observable variant, for a screen that shows which exercises are already calibrated. */
    fun calibrationProfiles(): Flow<Map<ExerciseType, UserProfile>> =
        calibration.data.orEmptyOnIoError()
            .map { prefs -> ExerciseType.entries.associateWith { prefs.toCalibration(it) } }
            .flowOn(Dispatchers.IO)
}
