package com.pushuprpg.app.domain

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.SkeletonMode
import com.pushuprpg.core.detect.UserProfile
import com.pushuprpg.core.game.Difficulty
import com.pushuprpg.core.game.PlayerClass
import kotlinx.coroutines.flow.Flow

/**
 * Everything the app persists about the player.
 *
 * Kept as one value rather than scattered preferences so a screen can render a consistent picture
 * without stitching four flows together mid-frame.
 */
data class PlayerProgress(
    val playerClass: PlayerClass = PlayerClass.KNIGHT,
    val level: Int = 1,
    val xpIntoLevel: Int = 0,
    val lifetimeReps: Int = 0,
    val bestCombo: Int = 0,
    val totalActiveMs: Long = 0,
    val streakDays: Int = 0,
    val bestStreakDays: Int = 0,
    /** Epoch day of the most recent day that met the streak bar. */
    val lastActiveEpochDay: Long = 0,
    val highestDungeonCleared: Int = 0,
    /**
     * Measured working capacity per movement — reps for a counted exercise, seconds for a hold.
     *
     * A map rather than a field per exercise, so adding a movement is adding a descriptor value and
     * not a field here, a preference key in the repository, a branch in the battle view model and a
     * branch in the survival one. A movement the user has never done is simply absent; read it
     * through [capacityOf] so it falls back to the descriptor's own starting value.
     */
    val capacity: Map<ExerciseType, Float> = emptyMap(),
    val bestSurvivalScore: Int = 0,
    /** The player has chosen a class. Separate from [onboarded] because the default class is a
     *  real class, so it cannot be used to infer whether anyone picked it. */
    val classChosen: Boolean = false,
    /** Onboarding is complete, which means the tutorial run has measured a starting capacity. */
    val onboarded: Boolean = false,
)

/** This player's capacity for a movement, or the movement's own starting value. */
fun PlayerProgress.capacityOf(exercise: ExerciseType): Float =
    capacity[exercise] ?: Exercises.of(exercise).defaultCapacity

fun PlayerProgress.withCapacity(exercise: ExerciseType, value: Float): PlayerProgress =
    copy(capacity = capacity + (exercise to value))

/** One finished run, win or lose. Losses are recorded exactly like wins — that is the point. */
data class SessionRecord(
    val id: Long = 0,
    val startedAtMs: Long,
    val durationMs: Long,
    /**
     * The movement actually credited, which under auto-detection is what the router settled on
     * rather than what was selected before the run.
     */
    val exercise: ExerciseType,
    val reps: Int,
    val maxCombo: Int,
    val deepReps: Int,
    val meanDepth: Float,
    val dungeonIndex: Int?,
    val cleared: Boolean,
    val xpEarned: Int,
    /** Below 0.85 the session still counts for the user but stays off any leaderboard. */
    val plausibility: Float,
)

data class DailyTotal(val epochDay: Long, val reps: Int, val activeMs: Long)

interface ProgressRepository {
    val progress: Flow<PlayerProgress>
    suspend fun current(): PlayerProgress
    suspend fun update(transform: (PlayerProgress) -> PlayerProgress)

    /** Per-exercise calibrated range, so session two starts accurate instead of relearning. */
    suspend fun calibrationProfile(exercise: ExerciseType): UserProfile
    suspend fun saveCalibrationProfile(exercise: ExerciseType, profile: UserProfile)
}

interface SessionRepository {
    fun recent(limit: Int = 50): Flow<List<SessionRecord>>
    fun dailyTotals(days: Int = 90): Flow<List<DailyTotal>>
    suspend fun insert(record: SessionRecord): Long
    suspend fun lifetimeReps(): Int
    suspend fun repsOn(epochDay: Long): Int
}

/** User-facing settings. Defaults are the shipping defaults, not placeholders. */
data class AppSettings(
    val skeletonMode: SkeletonMode = SkeletonMode.MINIMAL,
    val gaugeOnRight: Boolean = true,
    val showGaugeNumber: Boolean = false,
    val sfxEnabled: Boolean = true,
    /** The background music during a run, or [MusicTrack.OFF]. */
    val music: MusicTrack = MusicTrack.ADVENTURE,
    val voiceEnabled: Boolean = true,
    val captionsEnabled: Boolean = false,
    val hapticStrength: HapticStrength = HapticStrength.MEDIUM,
    val colourBlindSafe: Boolean = false,
    val reduceMotion: Boolean = false,
    /** Screen dark, audio only. The battery fix and the accessibility mode are the same feature. */
    val audioOnly: Boolean = false,
    val largeText: Boolean = false,
    /**
     * The movement picked on the way into the last dungeon, remembered so the entry picker opens on
     * it. It is a default for that screen, not a global mode — the run's exercise is whatever was
     * chosen at entry.
     */
    val exercise: ExerciseType = ExerciseType.PUSHUP,
    val difficulty: Difficulty = Difficulty.STANDARD,
    /** The menus' look. The run is dark either way — it is drawn over the camera. */
    val themeMode: ThemeMode = ThemeMode.DARK,
    /**
     * Keep the latest run's landmarks so they can be sent as a bug report. Honoured only in debug
     * builds; see [com.pushuprpg.app.trace.RunTraces].
     */
    val recordTraces: Boolean = false,
    /**
     * What the user calls the cat in 고냥이 지켜줘. Blank means the default, which is a string
     * resource rather than a literal here, so it follows the locale like every other word.
     */
    val catName: String = "",
    val catCoat: CatCoat = CatCoat.CREAM,
    /**
     * Seconds to rest after a cleared dungeon before the next one starts by itself, or 0 for off.
     * Without it a session ended at every clear screen: the next dungeon was a tap away, and a rest
     * with no end is not a rest.
     */
    val autoNextRestSeconds: Int = 0,
)

enum class HapticStrength { OFF, LIGHT, MEDIUM, STRONG }

/** Background music for a run: one of the synthesised loops (tools/generate_music.py), or none. */
enum class MusicTrack {
    OFF,
    /** 모험 — bright and bouncing; the default. */
    ADVENTURE,
    /** 전투 — minor key, four on the floor. */
    BATTLE,
    /** 집중 — lo-fi, for a long steady set. */
    FOCUS,
    /** 잔잔 — soft marimba; 고냥이's room. */
    CALM,
}

/**
 * The cat's coat. A cat someone has named and coloured like their own is a cat they protect
 * harder, which is the whole of this mode — so it is a setting, not a reward to be unlocked.
 */
enum class CatCoat {
    /** The original, and the share card's default. */
    CREAM,
    /** 치즈 — orange tabby. */
    CHEESE,
    /** 고등어 — grey mackerel tabby. */
    MACKEREL,
    /** 턱시도 — black with a white bib and socks. */
    TUXEDO,
    /** 삼색 — white with orange and black patches. */
    CALICO,
}

enum class ThemeMode {
    DARK,
    LIGHT;

    fun toggled(): ThemeMode = if (this == DARK) LIGHT else DARK
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/**
 * Subscription state.
 *
 * Exercise itself is never gated. The free tier always includes a full daily workout — the first
 * dungeon and the survival mode — because paywalling the exercise would be both wrong and, for a
 * habit product, commercially self-defeating. What a subscription buys is more *game*.
 */
data class Entitlement(
    val isSubscriber: Boolean = false,
    val inTrial: Boolean = false,
    val trialDaysRemaining: Int = 0,
    val expiresAtMs: Long? = null,
    /** True when Play said so recently; false means we are running on a cached answer. */
    val verified: Boolean = false,
) {
    val hasFullAccess: Boolean get() = isSubscriber || inTrial
}

interface EntitlementRepository {
    val entitlement: Flow<Entitlement>
    suspend fun refresh()
}

/** What the free tier can reach. Centralised so the boundary is one decision, not many. */
object FreeTier {
    /** 부서진 문 — always playable, forever, subscription or not. */
    const val FREE_DUNGEON_INDEX = 1

    const val TRIAL_DAYS = 7

    fun canPlayDungeon(index: Int, entitlement: Entitlement): Boolean =
        index <= FREE_DUNGEON_INDEX || entitlement.hasFullAccess

    /** Survival mode is free forever: it is the on-ramp, not the product. */
    fun canPlaySurvival(): Boolean = true
}
