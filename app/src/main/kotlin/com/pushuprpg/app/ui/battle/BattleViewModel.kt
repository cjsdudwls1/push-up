package com.pushuprpg.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pushuprpg.app.AppContainer
import com.pushuprpg.app.audio.GameAudio
import com.pushuprpg.app.audio.GameVoice
import com.pushuprpg.core.audio.Announcer
import com.pushuprpg.app.telemetry.Event
import com.pushuprpg.app.telemetry.Telemetry
import com.pushuprpg.app.trace.RunTraces
import com.pushuprpg.app.domain.AppSettings
import com.pushuprpg.app.domain.PlayerProgress
import com.pushuprpg.app.domain.ProgressRepository
import com.pushuprpg.app.domain.SessionRecord
import com.pushuprpg.app.domain.SessionRepository
import com.pushuprpg.app.domain.SettingsRepository
import com.pushuprpg.app.domain.capacityOf
import com.pushuprpg.app.domain.withCapacity
import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.detect.PlacementAdvice
import com.pushuprpg.core.detect.PoseQuality
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
import com.pushuprpg.core.progression.StreakState
import com.pushuprpg.core.run.BattleEngine
import com.pushuprpg.core.run.BattleState
import com.pushuprpg.core.run.ExerciseSegment
import com.pushuprpg.core.run.Outcome
import kotlinx.coroutines.CoroutineScope
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
    private val telemetry: Telemetry,
    private val traces: RunTraces,
    private val voice: GameVoice,
    /** Where the run is banked: it outlives this screen, which is popped as the run ends. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    // What is said aloud: the phone is two metres away and the banners cannot be read from there.
    // Only touched on the pose thread, like the engine.
    private val announcer = Announcer()

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
    /** Null, not OK, before the first frame: see the quality_lost log in [onPoseFrame]. */
    @Volatile private var lastReportedQuality: PoseQuality? = null

    /** finish() is reachable from both the pose thread and quit(); the run must bank exactly once. */
    private val saved = AtomicBoolean(false)

    /** One run per model: see [start]. Main thread only. */
    private var started = false

    /**
     * A movement switch, built on the main thread and waiting to be applied on the pose thread.
     *
     * The engine and the detector are single-threaded by design; swapping the detector while a
     * frame is inside it would hand half a rep to the new movement. So the switch is posted and the
     * pose thread takes it before its next frame, exactly as survival's restart does.
     */
    @Volatile private var pendingSwitch: RepDetector? = null
    /** The starting calibration of [pendingSwitch], for the trace; written before it is. */
    @Volatile private var pendingProfileNote: String = ""

    private val _levelsGained = MutableStateFlow(0)
    val levelsGained: StateFlow<Int> = _levelsGained.asStateFlow()

    /** The level the run ends on, for the result screen's 레벨 N 달성; set with [levelsGained]. */
    private val _levelReached = MutableStateFlow(1)
    val levelReached: StateFlow<Int> = _levelReached.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect {
                _settings.value = it
                audio.soundEnabled = it.sfxEnabled
                audio.hapticStrength = it.hapticStrength
                voice.enabled = it.voiceEnabled
            }
        }
    }

    fun start(dungeonIndex: Int) {
        // Called again when the activity is recreated around a live run — split screen, a fold
        // opening, a font size change — and this model outlives that. Starting over threw the
        // live engine away unbanked and put the counter back to zero.
        if (started) return
        started = true
        this.dungeonIndex = dungeonIndex
        saved.set(false)
        sessionBestDepth = 0f
        _levelsGained.value = 0

        viewModelScope.launch {
            progress = progressRepository.current()
            val settings = settingsRepository.settings.first()
            exercise = settings.exercise

            // One detector for the whole run, for the movement the user chose on the way in.
            //
            // An earlier build raced all nine detectors and credited whichever produced a verified
            // rep. It identified the exercise correctly and was still wrong: the loser's strikes are
            // discarded, so every rep done before the race resolved simply vanished — measured at 0
            // of 6 pull-ups when someone moved straight from the floor to the bar. A run has one
            // movement, and it is chosen rather than guessed.
            val config = DetectorConfig.forExercise(exercise)
            val profile = progressRepository.calibrationProfile(exercise)
            // Through the factory, not a direct RepDetectorImpl: a plank needs a different detector
            // entirely, and constructing the rep state machine for it would silently count nothing.
            val det: RepDetector = DetectorFactory.create(exercise, config, profile)
            det.skeletonMode = settings.skeletonMode
            detector = det

            val player = PlayerState.create(
                playerClass = progress.playerClass,
                level = progress.level,
                streakBonus = Streak.hpBonus(progress.streakDays),
            )
            val dungeon = Dungeons.byIndex(dungeonIndex) ?: Dungeons.FREE_DUNGEON

            // Before the engine exists, so no frame of this run lands in the last one's recording.
            // The profile is what the detector starts from, and a replay needs the same start.
            traces.begin(
                "mode=battle dungeon=$dungeonIndex exercise=${exercise.name} " +
                    "difficulty=${settings.difficulty.name} " +
                    "profile=${profile.topEwma}/${profile.botEwma}/${profile.sessionCount}"
            )
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
            telemetry.setExercise(exercise)
            telemetry.log(Event.RunStarted(dungeonIndex, settings.difficulty, exercise))
        }
    }

    /**
     * Carries the run on with [to] — as often as the user likes, because a session is pushups for a
     * while, then pull-ups, then squats. The new movement starts from its own stored calibration.
     */
    fun switchExercise(to: ExerciseType) {
        if (engine == null || to == exercise) return
        viewModelScope.launch {
            val profile = progressRepository.calibrationProfile(to)
            val next = DetectorFactory.create(to, DetectorConfig.forExercise(to), profile)
            next.skeletonMode = _settings.value.skeletonMode
            pendingProfileNote = "${profile.topEwma}/${profile.botEwma}/${profile.sessionCount}"
            pendingSwitch = next
            // Remembered for the next entry picker too, as a choice made on the way in would be.
            settingsRepository.update { it.copy(exercise = to) }
        }
    }

    /** Called on the pose callback thread. */
    fun onPoseFrame(frame: PoseFrame) {
        val e = engine ?: return
        pendingSwitch?.let { next ->
            pendingSwitch = null
            val from = exercise
            val retired = e.switchExercise(next)
            detector = next
            exercise = next.config.exercise
            telemetry.setExercise(exercise)
            traces.mark("switch=${frame.timestampMs}:${exercise.name}:$pendingProfileNote")
            // Banked now rather than at the end: the retired detector is no longer fed frames, so
            // it is safe to read here, and a run that is killed later keeps what it learned.
            appScope.launch {
                val previous = progressRepository.calibrationProfile(from)
                progressRepository.saveCalibrationProfile(from, retired.updatedProfile(previous))
            }
        }
        traces.record(frame)
        val next = e.onPoseFrame(frame)
        // The whole skeleton while setting up, whatever the overlay setting: the lines are how the
        // user lines themselves up with the framing guide. Back to their choice once armed.
        val settingUp = next.reps == 0 && next.placement.advice.let { it != null && it != PlacementAdvice.READY }
        detector?.skeletonMode = if (settingUp) SkeletonMode.FULL else _settings.value.skeletonMode
        // Fired straight from this thread: routing it through a recomposition would spend most of
        // the ~90ms budget between the rep bottoming out and the user hearing it.
        audio.play(next.sounds)
        voice.announce(announcer.battle(next, frame.timestampMs), progress.playerClass, next.exercise)

        // The most useful signal the app collects: how often tracking drops, for what reason, and
        // on which device. Every detection constant here is reasoned rather than measured, so
        // without this there is no way to learn that a threshold is wrong for a phone nobody here
        // has held.
        //
        // Only once tracking has been OK: a run starts with nobody in position yet, and counting
        // that first frame as a drop put nearly every session into H2's quality_lost rate.
        if (next.quality != PoseQuality.OK && lastReportedQuality == PoseQuality.OK) {
            telemetry.log(Event.QualityLost(next.quality, next.reps))
        }
        lastReportedQuality = next.quality

        sessionBestDepth = maxOf(sessionBestDepth, next.depth)
        // Banked before it is published: the screen opens the result the moment it sees an
        // outcome, and reads the level-up as it does.
        next.outcome?.let { finish(it) }
        _state.value = next
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
     * The screen went without the run ending or being quit — the app closed from recents, or any
     * way out that skipped the X. Whatever was done is banked all the same, in the app's scope,
     * because this one is already cancelled.
     */
    override fun onCleared() {
        // Not the engine at all once banked: the popped screen can still be feeding it frames.
        if (!saved.get()) quit()
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

        // Worked out here, from the progress the run started with, not inside the write below:
        // the result screen reads it as it opens, long before any write has landed, and so never
        // said 레벨 N 달성. Nothing else earns XP during a run, so the two agree.
        val reached = Levels.apply(progress.level, progress.xpIntoLevel, outcome.xpEarned)
        _levelReached.value = reached.level
        _levelsGained.value = reached.levelsGained

        // One entry per movement, in order. A run that never switched is one entry and banks
        // exactly as a run always did; the segment list only matters once there is more than one.
        val segments = outcome.segments.ifEmpty {
            listOf(
                ExerciseSegment(
                    exercise = exercise, reps = outcome.reps, maxCombo = outcome.maxCombo,
                    deepReps = outcome.deepReps, meanDepth = outcome.meanDepth,
                    holdMs = detector?.sessionSummary()?.holdMs ?: 0L,
                    durationMs = outcome.durationMs, plausibility = outcome.plausibility,
                )
            )
        }
        val single = segments.size == 1
        // A stretch with nothing in it — switched away before a rep — is not worth a record row.
        val worked = segments.filter { it.reps > 0 || it.holdMs >= 1_000L }.ifEmpty { listOf(segments.last()) }

        telemetry.log(
            Event.RunFinished(
                dungeon = dungeonIndex,
                cleared = outcome.cleared,
                reps = outcome.reps,
                durationMs = outcome.durationMs,
                plausibility = outcome.plausibility,
            )
        )

        // Not viewModelScope: the result screen pops this one as soon as the outcome lands, and a
        // write cancelled halfway would keep the record row and lose the XP, the streak and the
        // unlock.
        appScope.launch {
            // The streak is judged on the day the run started, the day its rows are filed under,
            // against everything already done that day — read before this run's rows go in.
            val runStartedAt = System.currentTimeMillis() - outcome.durationMs
            val epochDay = Instant.ofEpochMilli(runStartedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            val doneEarlier = sessionRepository.workOn(epochDay)

            // Each movement gets its own row, so the records screen says what was actually done.
            // The run's clear and its XP belong to the run, so they ride on the last row only.
            var startedAt = runStartedAt
            worked.forEachIndexed { i, seg ->
                val last = i == worked.lastIndex
                sessionRepository.insert(
                    SessionRecord(
                        startedAtMs = startedAt,
                        durationMs = if (single) outcome.durationMs else seg.durationMs,
                        exercise = seg.exercise,
                        reps = seg.reps,
                        maxCombo = if (single) outcome.maxCombo else seg.maxCombo,
                        deepReps = seg.deepReps,
                        meanDepth = seg.meanDepth,
                        dungeonIndex = dungeonIndex,
                        cleared = outcome.cleared && last,
                        xpEarned = if (last) outcome.xpEarned else 0,
                        plausibility = seg.plausibility,
                    )
                )
                startedAt += seg.durationMs
            }

            // The movement in progress at the end; each earlier one was banked when it was left.
            detector?.let { det ->
                val previous = progressRepository.calibrationProfile(exercise)
                progressRepository.saveCalibrationProfile(exercise, det.updatedProfile(previous))
            }

            progressRepository.update { current ->
                val levelled = Levels.apply(current.level, current.xpIntoLevel, outcome.xpEarned)
                val streak = Streak.advance(
                    StreakState(current.streakDays, current.lastActiveEpochDay),
                    epochDay,
                    Streak.sum(doneEarlier, runWork(segments)),
                )
                // Capacity is measured in each movement's own unit: reps for a counted exercise,
                // seconds for a hold, and a hold's best is its longest, not an average. A movement
                // done twice in one run is judged by its better stretch.
                val measured = (if (single) segments else worked).groupBy { it.exercise }
                    .mapValues { (type, segs) ->
                        if (Exercises.of(type).kind == MovementKind.HOLD) {
                            maxOf(current.capacityOf(type), segs.maxOf { it.holdMs / 1000L }.toFloat())
                        } else {
                            val best = if (single) outcome.maxCombo else segs.maxOf { it.maxCombo }
                            Capacity.update(current.capacityOf(type), best)
                        }
                    }
                measured.entries.fold(current) { p, (type, value) -> p.withCapacity(type, value) }.copy(
                    level = levelled.level,
                    xpIntoLevel = levelled.xpIntoLevel,
                    lifetimeReps = current.lifetimeReps + outcome.reps,
                    bestCombo = maxOf(current.bestCombo, outcome.maxCombo),
                    totalActiveMs = current.totalActiveMs + outcome.durationMs,
                    streakDays = streak.days,
                    bestStreakDays = maxOf(current.bestStreakDays, streak.days),
                    lastActiveEpochDay = streak.lastActiveDay,
                    highestDungeonCleared = if (outcome.cleared) {
                        maxOf(current.highestDungeonCleared, dungeonIndex)
                    } else current.highestDungeonCleared,
                )
            }
        }
    }

    /**
     * This run's work toward the day's streak bar, per movement.
     *
     * The count has to be judged against the bar for the movement actually performed. Passing it
     * as pushups regardless meant a five-minute plank — which reports zero reps by construction —
     * lost the user their streak, and twelve squats kept it when fifteen are the bar. The bar
     * travels with each movement, and a mixed run adds each one's share of its own bar; the day's
     * other runs add theirs in [Streak.advance].
     */
    private fun runWork(segments: List<ExerciseSegment>): Map<ExerciseType, Int> =
        segments.groupBy { it.exercise }.mapValues { (type, segs) ->
            Streak.amount(type, reps = segs.sumOf { it.reps }, heldMs = segs.sumOf { it.holdMs })
        }

    private fun capacityFor(exercise: ExerciseType): Float = progress.capacityOf(exercise)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BattleViewModel(
                    container.progressRepository,
                    container.sessionRepository,
                    container.settingsRepository,
                    container.audio,
                    container.telemetry,
                    container.traces,
                    container.voice,
                    container.appScope,
                )
            }
        }
    }
}
