package com.pushuprpg.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pushuprpg.app.domain.PlayerProgress
import com.pushuprpg.app.domain.ProgressRepository
import com.pushuprpg.core.detect.ExerciseType
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

// What the dungeons kept — the class, the level and its XP, the dungeons cleared, each movement's
// capacity — and each movement's calibration, which only the dungeons read, are no longer read or
// written. Nothing is deleted: a user who downgrades still finds them where the old build looks.
private val KEY_LIFETIME_REPS = intPreferencesKey("lifetime_reps")
private val KEY_BEST_COMBO = intPreferencesKey("best_combo")
private val KEY_TOTAL_ACTIVE_MS = longPreferencesKey("total_active_ms")
private val KEY_STREAK_DAYS = intPreferencesKey("streak_days")
private val KEY_BEST_STREAK_DAYS = intPreferencesKey("best_streak_days")
private val KEY_LAST_ACTIVE_DAY = longPreferencesKey("last_active_epoch_day")
/**
 * The best 고냥이 score, one key per movement. The single key before it ("best_survival_score") is
 * neither read nor deleted: nothing says which movement its score came from, so each movement's
 * best starts over rather than inheriting another's, and a downgrade still finds the old value.
 */
private fun bestSurvivalKey(e: ExerciseType) = intPreferencesKey("best_survival_score_${e.name}")
/**
 * Under the name it had when the first screen asked for a class, so nobody who has been through it
 * sees it again.
 */
private val KEY_INTRO_SEEN = booleanPreferencesKey("class_chosen")
private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
private val KEY_GIFTS_SEEN = stringSetPreferencesKey("gifts_seen")

private fun Preferences.toProgress(): PlayerProgress = PlayerProgress(
    lifetimeReps = this[KEY_LIFETIME_REPS] ?: PROGRESS_DEFAULT.lifetimeReps,
    bestCombo = this[KEY_BEST_COMBO] ?: PROGRESS_DEFAULT.bestCombo,
    totalActiveMs = this[KEY_TOTAL_ACTIVE_MS] ?: PROGRESS_DEFAULT.totalActiveMs,
    streakDays = this[KEY_STREAK_DAYS] ?: PROGRESS_DEFAULT.streakDays,
    bestStreakDays = this[KEY_BEST_STREAK_DAYS] ?: PROGRESS_DEFAULT.bestStreakDays,
    lastActiveEpochDay = this[KEY_LAST_ACTIVE_DAY] ?: PROGRESS_DEFAULT.lastActiveEpochDay,
    bestSurvivalScores = ExerciseType.entries.mapNotNull { e -> this[bestSurvivalKey(e)]?.let { e to it } }.toMap(),
    introSeen = this[KEY_INTRO_SEEN] ?: PROGRESS_DEFAULT.introSeen,
    onboarded = this[KEY_ONBOARDED] ?: PROGRESS_DEFAULT.onboarded,
    giftsSeen = this[KEY_GIFTS_SEEN] ?: PROGRESS_DEFAULT.giftsSeen,
)

private fun MutablePreferences.writeProgress(p: PlayerProgress) {
    this[KEY_LIFETIME_REPS] = p.lifetimeReps
    this[KEY_BEST_COMBO] = p.bestCombo
    this[KEY_TOTAL_ACTIVE_MS] = p.totalActiveMs
    this[KEY_STREAK_DAYS] = p.streakDays
    this[KEY_BEST_STREAK_DAYS] = p.bestStreakDays
    this[KEY_LAST_ACTIVE_DAY] = p.lastActiveEpochDay
    p.bestSurvivalScores.forEach { (exercise, score) -> this[bestSurvivalKey(exercise)] = score }
    this[KEY_INTRO_SEEN] = p.introSeen
    this[KEY_ONBOARDED] = p.onboarded
    this[KEY_GIFTS_SEEN] = p.giftsSeen
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
 * Player progress, in a DataStore file. [zone] is injectable so the day boundary can be tested
 * without touching the device clock.
 */
class DataStoreProgressRepository(
    context: Context,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ProgressRepository {

    private val store = context.applicationContext.progressStore

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
}
