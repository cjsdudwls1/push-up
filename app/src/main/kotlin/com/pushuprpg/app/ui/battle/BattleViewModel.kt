package com.pushuprpg.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pushuprpg.app.AppContainer
import com.pushuprpg.app.audio.GameAudio
import com.pushuprpg.app.domain.AppSettings
import com.pushuprpg.app.domain.PlayerProgress
import com.pushuprpg.app.domain.ProgressRepository
import com.pushuprpg.app.domain.SessionRecord
import com.pushuprpg.app.domain.SessionRepository
import com.pushuprpg.app.domain.SettingsRepository
import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.core.detect.RepDetector
import com.pushuprpg.core.detect.SkeletonMode
import com.pushuprpg.core.game.CombatResolver
import com.pushuprpg.core.game.Dungeons
import com.pushuprpg.core.game.PlayerState
import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.progression.Capacity
import com.pushuprpg.core.progression.Levels
import com.pushuprpg.core.progression.Streak
import com.pushuprpg.core.run.BattleEngine
import com.pushuprpg.core.run.BattleState
import com.pushuprpg.core.run.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Owns one dungeon run.
 *
 * The rules live in [BattleEngine] in `:core`; this exists to bridge them to Android — to hold the
 * coroutine scope, to seed the run from persisted progress, and to write the result back when it
 * ends. Pose frames arrive on MediaPipe's callback thread and are processed there, because the
 * engine's work is a few microseconds and hopping threads would add latency to the one number the
 * user is watching.
 */
class BattleViewModel(
    private val progressRepository: ProgressRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val audio: GameAudio,
) : ViewModel() {

    private val _state = MutableStateFlow(BattleState())
    val state: StateFlow<BattleState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // All of these are written from the main thread in start() and read from MediaPipe's callback
    // thread in onPoseFrame, so the pose thread needs a guarantee it will actually see the
    // publication — and that it cannot observe a half-constructed engine.
    @Volatile private var engine: BattleEngine? = null
    @Volatile private var detector: RepDetector? = null
    @Volatile private var progress: PlayerProgress = PlayerProgress()
    @Volatile private var exercise: ExerciseType = ExerciseType.PUSHUP
    @Volatile private var dungeonIndex: Int = 1
    @Volatile private var sessionBestDepth: Float = 0f

    /** finish() is reachable from both the pose thread and quit(); the run must bank exactly once. */
    private val saved = AtomicBoolean(false)

    private val _levelsGained = MutableStateFlow(0)
    val levelsGained: StateFlow<Int> = _levelsGained.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect {
                _settings.value = it
                audio.soundEnabled = it.sfxEnabled
                audio.hapticStrength = it.hapticStrength
            }
        }
    }

    fun start(dungeonIndex: Int) {
        this.dungeonIndex = dungeonIndex
        saved.set(false)
        sessionBestDepth = 0f
        _levelsGained.value = 0

        viewModelScope.launch {
            progress = progressRepository.current()
            val settings = settingsRepository.settings.first()
            exercise = settings.exercise

            val config = when (exercise) {
                ExerciseType.PUSHUP -> DetectorConfig.pushup()
                ExerciseType.SQUAT -> DetectorConfig.squat()
                ExerciseType.PLANK -> DetectorConfig.plank()
            }
            val profile = progressRepository.calibrationProfile(exercise)
            // Through the factory, not a direct RepDetectorImpl: a plank needs a different detector
            // entirely, and constructing the rep state machine for it would silently count nothing.
            val det = DetectorFactory.create(exercise, config, profile)
            det.skeletonMode = settings.skeletonMode
            detector = det

            val player = PlayerState.create(
                playerClass = progress.playerClass,
                level = progress.level,
                streakBonus = Streak.hpBonus(progress.streakDays),
            )
            val dungeon = Dungeons.byIndex(dungeonIndex) ?: Dungeons.FREE_DUNGEON

            engine = BattleEngine(
                dungeon = dungeon,
                difficulty = settings.difficulty,
                capacity = capacityFor(exercise),
                initialPlayer = player,
                detector = det,
                resolver = CombatResolver(config),
                // Seeded from the dungeon and the player's level rather than a clock, so a run is
                // reproducible from a recorded trace when diagnosing a report.
                rngSeed = dungeonIndex * 1_000L + progress.level,
            )
            _state.value = engine!!.currentState()
        }
    }

    /** Called on the pose callback thread. */
    fun onPoseFrame(frame: PoseFrame) {
        val e = engine ?: return
        val next = e.onPoseFrame(frame)
        // Fired straight from this thread: routing it through a recomposition would spend most of
        // the ~90ms budget between the rep bottoming out and the user hearing it.
        audio.play(next.sounds)
        sessionBestDepth = maxOf(sessionBestDepth, next.depth)
        _state.value = next
        next.outcome?.let { finish(it) }
    }

    fun currentSessionBestDepth(): Float = sessionBestDepth

    fun setSkeletonMode(mode: SkeletonMode) {
        detector?.skeletonMode = mode
        viewModelScope.launch { settingsRepository.update { it.copy(skeletonMode = mode) } }
    }

    /** Returns the banked outcome so the caller can hand it to the result screen. */
    fun quit(): Outcome? {
        val e = engine ?: return null
        val outcome = e.quit()
        finish(outcome)
        return outcome
    }

    /**
     * Banks the run.
     *
     * Everything here happens whether the run was won or lost. The app tells the user their reps
     * survive a defeat, and this is the code that has to be true for that to be true — so the only
     * thing `cleared` changes is the dungeon-unlock line and the completion bonus that was already
     * folded into [Outcome.xpEarned].
     */
    private fun finish(outcome: Outcome) {
        if (!saved.compareAndSet(false, true)) return

        val summary = detector?.sessionSummary()
        val plankSeconds = ((summary?.holdMs ?: 0L) / 1000L).toInt()

        viewModelScope.launch {
            val epochDay = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

            sessionRepository.insert(
                SessionRecord(
                    startedAtMs = System.currentTimeMillis() - outcome.durationMs,
                    durationMs = outcome.durationMs,
                    exercise = exercise,
                    reps = outcome.reps,
                    maxCombo = outcome.maxCombo,
                    deepReps = outcome.deepReps,
                    meanDepth = outcome.meanDepth,
                    dungeonIndex = dungeonIndex,
                    cleared = outcome.cleared,
                    xpEarned = outcome.xpEarned,
                    plausibility = outcome.plausibility,
                )
            )

            summary?.let {
                detector?.let { det ->
                    val previous = progressRepository.calibrationProfile(exercise)
                    progressRepository.saveCalibrationProfile(exercise, det.updatedProfile(previous))
                }
            }

            progressRepository.update { current ->
                val levelled = Levels.apply(current.level, current.xpIntoLevel, outcome.xpEarned)
                _levelsGained.value = levelled.levelsGained
                val streak = advanceStreak(current, epochDay, outcome.reps, plankSeconds)
                current.copy(
                    level = levelled.level,
                    xpIntoLevel = levelled.xpIntoLevel,
                    lifetimeReps = current.lifetimeReps + outcome.reps,
                    bestCombo = maxOf(current.bestCombo, outcome.maxCombo),
                    totalActiveMs = current.totalActiveMs + outcome.durationMs,
                    streakDays = streak.first,
                    bestStreakDays = maxOf(current.bestStreakDays, streak.first),
                    lastActiveEpochDay = streak.second,
                    highestDungeonCleared = if (outcome.cleared) {
                        maxOf(current.highestDungeonCleared, dungeonIndex)
                    } else current.highestDungeonCleared,
                    capacityPushup = if (exercise == ExerciseType.PUSHUP) {
                        Capacity.update(current.capacityPushup, outcome.maxCombo)
                    } else current.capacityPushup,
                    capacitySquat = if (exercise == ExerciseType.SQUAT) {
                        Capacity.update(current.capacitySquat, outcome.maxCombo)
                    } else current.capacitySquat,
                    capacityPlankSeconds = if (exercise == ExerciseType.PLANK) {
                        maxOf(current.capacityPlankSeconds, plankSeconds.toFloat())
                    } else current.capacityPlankSeconds,
                )
            }
        }
    }

    /**
     * Returns the new streak and the day it was last earned.
     *
     * A streak that survives on ten reps is the point: its job is to get someone to open the app on
     * a bad day, not to extract a workout from them. A genuine break halves it rather than zeroing
     * it, so one missed week does not erase a year of work.
     */
    private fun advanceStreak(
        current: PlayerProgress,
        epochDay: Long,
        reps: Int,
        plankSeconds: Int,
    ): Pair<Int, Long> {
        // The count has to go into the slot for the movement actually performed. Passing it as
        // pushups regardless meant a five-minute plank — which reports zero reps by construction —
        // lost the user their streak, and twelve squats kept it when fifteen are the bar.
        val maintained = when (exercise) {
            ExerciseType.PUSHUP -> Streak.maintained(reps = reps)
            ExerciseType.SQUAT -> Streak.maintained(reps = 0, squats = reps)
            ExerciseType.PLANK -> Streak.maintained(reps = 0, plankSeconds = plankSeconds)
        }
        if (!maintained) return current.streakDays to current.lastActiveEpochDay
        return when (epochDay - current.lastActiveEpochDay) {
            0L -> current.streakDays.coerceAtLeast(1) to epochDay
            1L -> (current.streakDays + 1) to epochDay
            else -> (Streak.afterBreak(current.streakDays) + 1) to epochDay
        }
    }

    private fun capacityFor(exercise: ExerciseType): Float = when (exercise) {
        ExerciseType.PUSHUP -> progress.capacityPushup
        ExerciseType.SQUAT -> progress.capacitySquat
        ExerciseType.PLANK -> progress.capacityPlankSeconds
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BattleViewModel(
                    container.progressRepository,
                    container.sessionRepository,
                    container.settingsRepository,
                    container.audio,
                )
            }
        }
    }
}
