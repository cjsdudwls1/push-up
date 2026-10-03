package com.pushuprpg.app.domain

import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.progression.CatItem
import com.pushuprpg.core.progression.Gifts
import com.pushuprpg.core.progression.RunGrowth
import com.pushuprpg.core.progression.SessionFacts
import kotlinx.coroutines.flow.Flow
import com.pushuprpg.core.progression.Purse
import com.pushuprpg.core.progression.SetBests

/**
 * Everything the app persists about the player.
 *
 * Kept as one value rather than scattered preferences so a screen can render a consistent picture
 * without stitching four flows together mid-frame.
 */
data class PlayerProgress(
    val lifetimeReps: Int = 0,
    val bestCombo: Int = 0,
    val totalActiveMs: Long = 0,
    val streakDays: Int = 0,
    val bestStreakDays: Int = 0,
    /** Epoch day of the most recent day that met the streak bar. */
    val lastActiveEpochDay: Long = 0,
    /**
     * 고양이 지켜줘's best score per movement, by the owner's decision: a pull-up and a plank are
     * not the same effort, so one best across all of them only ever said what the easiest one
     * scored. Read it through [bestSurvivalScoreOf]; a movement never played is absent.
     */
    val bestSurvivalScores: Map<ExerciseType, Int> = emptyMap(),
    /** The first screen's 시작 has been tapped, so it is not shown again. */
    val introSeen: Boolean = false,
    /** Onboarding is complete: the tutorial run was played or skipped. */
    val onboarded: Boolean = false,
    /**
     * Gifts the user has already been shown, by name ([com.pushuprpg.core.progression.Gift]). Which
     * gifts are earned is never stored — it is worked out from the runs — so this only decides what
     * is still new.
     */
    val giftsSeen: Set<String> = emptySet(),
    /** Each movement's best per set (see [com.pushuprpg.core.progression.SetBests]); absent when never played. */
    val setBests: Map<ExerciseType, List<Int>> = emptyMap(),
    /** 츄르 in hand: earned by set records, spent in the shop, never otherwise lost. */
    val churu: Int = 0,
    /** Things bought in the shop, by [com.pushuprpg.core.progression.CatItem] name. Never taken back. */
    val bought: Set<String> = emptySet(),
)

/** [exercise]'s best per set. */
fun PlayerProgress.setBestsOf(exercise: ExerciseType): SetBests = SetBests(setBests[exercise].orEmpty())

/** What the shop sees. */
fun PlayerProgress.purse(): Purse = Purse(
    churu = churu,
    owned = bought.mapNotNullTo(mutableSetOf()) { name -> CatItem.entries.firstOrNull { it.name == name } },
)

/** The best 고양이 score with [exercise], or 0 before its first session. */
fun PlayerProgress.bestSurvivalScoreOf(exercise: ExerciseType): Int = bestSurvivalScores[exercise] ?: 0

/** [score] kept as [exercise]'s best if it beats the one there. */
fun PlayerProgress.withSurvivalScore(exercise: ExerciseType, score: Int): PlayerProgress =
    if (score <= bestSurvivalScoreOf(exercise)) this
    else copy(bestSurvivalScores = bestSurvivalScores + (exercise to score))

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
    /**
     * The dungeon a row was played in, for rows written while the app had dungeons; null for every
     * 고양이 session. Their reps still count everywhere, and [cleared] means something else on them.
     */
    val dungeonIndex: Int? = null,
    /** Every life played out; on a dungeon row, the dungeon cleared. */
    val cleared: Boolean,
    /** Below 0.85 the session still counts for the user but stays off any leaderboard. */
    val plausibility: Float,
)

data class DailyTotal(val epochDay: Long, val reps: Int, val activeMs: Long)

interface ProgressRepository {
    val progress: Flow<PlayerProgress>
    suspend fun current(): PlayerProgress
    suspend fun update(transform: (PlayerProgress) -> PlayerProgress)
}

interface SessionRepository {
    fun recent(limit: Int = 50): Flow<List<SessionRecord>>
    fun dailyTotals(days: Int = 90): Flow<List<DailyTotal>>
    suspend fun insert(record: SessionRecord): Long
    suspend fun lifetimeReps(): Int
    suspend fun repsOn(epochDay: Long): Int

    /**
     * What was done on [epochDay], every mode, per movement in the unit its streak bar is in:
     * reps, or seconds for a hold. No hold time is stored, so a hold's seconds are its rows' length.
     */
    suspend fun workOn(epochDay: Long): Map<ExerciseType, Int>

    /** Every run as the growth screens read it — the calories, the records, the week — oldest first. */
    fun facts(): Flow<List<SessionFacts>>

    /** [facts], read once: what a run is measured against before it is banked. */
    suspend fun factsNow(): List<SessionFacts>
}

/** User-facing settings. Defaults are the shipping defaults, not placeholders. */
data class AppSettings(
    val sfxEnabled: Boolean = true,
    /** The background music during a run, or [MusicTrack.OFF]. */
    val music: MusicTrack = MusicTrack.ADVENTURE,
    val voiceEnabled: Boolean = true,
    val captionsEnabled: Boolean = false,
    val hapticStrength: HapticStrength = HapticStrength.MEDIUM,
    val colourBlindSafe: Boolean = false,
    val reduceMotion: Boolean = false,
    val largeText: Boolean = false,
    /**
     * The movement picked last, which the hub's button starts and the picker opens on. It is a
     * default, not a global mode — a run's exercise is whatever it was started with.
     */
    val exercise: ExerciseType = ExerciseType.PUSHUP,
    /** The menus' look. The run is dark either way — it is drawn over the camera. */
    val themeMode: ThemeMode = ThemeMode.DARK,
    /**
     * Keep the latest run's landmarks so they can be sent as a bug report. Honoured only in debug
     * builds; see [com.pushuprpg.app.trace.RunTraces].
     */
    val recordTraces: Boolean = false,
    /**
     * What the user calls the cat in 고양이 지켜줘. Blank means the default, which is a string
     * resource rather than a literal here, so it follows the locale like every other word.
     */
    val catName: String = "",
    val catCoat: CatCoat = CatCoat.CREAM,
    /**
     * What the cat is wearing, by name ([com.pushuprpg.core.progression.CatItem]): one per slot, and
     * only what has been earned. A name a later build no longer knows is ignored.
     */
    val catWear: Set<String> = emptySet(),
    /**
     * The rest between two lives of 고양이 지켜줘, in seconds. A minute by the owner's decision;
     * the settings offer longer, since a set to failure recovers better with more.
     */
    val catRestSeconds: Int = 60,
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
    /** 잔잔 — soft marimba; 고양이's room. */
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

/** What the cat is wearing, as items: names this build does not know are left out. */
fun AppSettings.wearing(): Set<CatItem> =
    catWear.mapNotNullTo(mutableSetOf()) { name -> CatItem.entries.firstOrNull { it.name == name } }

fun AppSettings.withWear(items: Set<CatItem>): AppSettings =
    copy(catWear = items.mapTo(mutableSetOf()) { it.name })

/**
 * Puts on what [growth] found, each in its slot if the slot is empty, so the cat comes home wearing
 * its find. Something the user chose to wear is never taken off for it.
 */
suspend fun SettingsRepository.wearFound(growth: RunGrowth) {
    if (growth.gifts.isEmpty()) return
    update { it.withWear(Gifts.wearNew(it.wearing(), growth.gifts.map { gift -> gift.item })) }
}
