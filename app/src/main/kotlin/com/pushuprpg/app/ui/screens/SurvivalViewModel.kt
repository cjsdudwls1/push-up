package com.pushuprpg.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pushuprpg.app.AppContainer
import com.pushuprpg.app.audio.GameAudio
import com.pushuprpg.app.domain.SettingsRepository
import com.pushuprpg.app.domain.ProgressRepository
import com.pushuprpg.app.telemetry.Event
import com.pushuprpg.app.telemetry.Telemetry
import com.pushuprpg.app.trace.RunTraces
import com.pushuprpg.app.domain.SessionRecord
import com.pushuprpg.app.domain.SessionRepository
import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.app.domain.capacityOf
import com.pushuprpg.app.domain.withCapacity
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Placement
import com.pushuprpg.core.detect.PlacementAdvice
import com.pushuprpg.core.detect.PlacementCoach
import com.pushuprpg.core.detect.PlankConfig
import com.pushuprpg.core.detect.PoseQuality
import com.pushuprpg.core.detect.PoseTick
import com.pushuprpg.core.detect.RepDetector
import com.pushuprpg.core.detect.RepPhase
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.detect.SkeletonMode
import com.pushuprpg.core.progression.Capacity
import com.pushuprpg.core.survival.CatCompanion
import com.pushuprpg.core.survival.CatView
import com.pushuprpg.core.survival.CeilingSurvival
import com.pushuprpg.core.survival.SurvivalEvent
import com.pushuprpg.core.survival.SurvivalState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SurvivalViewModel(
    private val progressRepository: ProgressRepository,
    private val sessionRepository: SessionRepository,
    private val telemetry: Telemetry,
    private val traces: RunTraces,
    private val settingsRepository: SettingsRepository,
    private val audio: GameAudio,
    /** Chosen on the way in; always pushups for the tutorial (see Routes.survival). */
    private val exercise: ExerciseType,
) : ViewModel() {

    // Any movement, each worth what it is worth everywhere else: a pull-up moves the ceiling about
    // as far as three pushups. See CeilingSurvival.forExercise.
    private val game = CeilingSurvival.forExercise(exercise)

    // Through the factory, like a dungeon run, so a plank gets the hold detector rather than a rep
    // state machine that would count nothing. The overlay is off: the mode's whole appeal is that
    // it looks like a toy, and a joint diagram over the top would undo that immediately.
    private val detector: RepDetector =
        DetectorFactory.create(exercise).also { it.skeletonMode = SkeletonMode.OFF }

    private val _state = MutableStateFlow(game.state())
    val state: StateFlow<SurvivalState> = _state.asStateFlow()

    private val _bestScore = MutableStateFlow(0)
    val bestScore: StateFlow<Int> = _bestScore.asStateFlow()

    // Talks the user into a placement that counts, before the first rep and whenever it is lost.
    private val coach = PlacementCoach(exercise, detector.config)
    private val _placement = MutableStateFlow(Placement(PlacementAdvice.STEP_INTO_VIEW))
    val placement: StateFlow<Placement> = _placement.asStateFlow()

    // The cat's face, words and voice. It reads the run and changes nothing in it.
    private val cat = CatCompanion()
    private val _cat = MutableStateFlow(cat.view())
    val catView: StateFlow<CatView> = _cat.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect {
                audio.soundEnabled = it.sfxEnabled
                audio.hapticStrength = it.hapticStrength
            }
        }
        // One recording for the whole visit, restarts included: the run worth sending is often the
        // one before the retry. Replays with the defaults — survival starts from no calibration.
        traces.begin("mode=survival exercise=${exercise.name} profile=none")
        // Scoped to the destination, so without loading it back the mode reported "최고 0점" every
        // time the user returned — in the one place the product is built around a score.
        viewModelScope.launch { _bestScore.value = progressRepository.current().bestSurvivalScore }
    }

    private var startedAtMs = 0L
    private var reps = 0
    private var maxCombo = 0
    private var saved = false

    @Volatile
    private var restartRequested = false

    fun onPoseFrame(frame: com.pushuprpg.core.pose.PoseFrame) {
        traces.record(frame)
        if (restartRequested) {
            restartRequested = false
            applyRestart()
        }
        val tick: PoseTick = detector.onFrame(frame)
        if (startedAtMs == 0L) startedAtMs = tick.tMs
        _placement.value = coach.update(frame, tick)

        val events = mutableListOf<SurvivalEvent>()
        for (event in tick.events) {
            when (event) {
                is RepEvent.Strike -> {
                    reps++
                    maxCombo = maxOf(maxCombo, event.combo)
                    events += game.onRep(event.grade, event.depth, event.tMs)
                }
                // A hold pushes for as long as it is held, in the detector's own tick steps.
                is RepEvent.HoldTick -> events += game.onHold(event.score, HOLD_TICK_SECONDS, event.tMs)
                else -> Unit
            }
        }
        // Being in position — seen, and armed at the top or inside a rep — starts the run. After
        // that the ceiling never stops: resting is not a pause. See CeilingSurvival.update.
        val inPosition = tick.quality == PoseQuality.OK &&
            tick.phase != RepPhase.IDLE && tick.phase != RepPhase.LOST
        events += game.update(tick.tMs, inPosition = inPosition)
        handle(events)

        val now = game.state()
        // Straight from this thread, like the dungeon's: a push has to be heard as it lands.
        audio.play(cat.update(now, events, tick.tMs))
        _state.value = now
        _cat.value = cat.view()
    }

    /**
     * Asks for a reset rather than performing one.
     *
     * The button is on the main thread while frames are arriving on MediaPipe's; both
     * [CeilingSurvival] and the detector are single-threaded and stateful by design, so an in-flight
     * frame landing inside a reset would resume a half-cleared game. Deferring it means exactly one
     * thread ever touches them.
     */
    fun restart() {
        restartRequested = true
    }

    private fun applyRestart() {
        game.reset()
        detector.reset()
        cat.reset()
        _cat.value = cat.view()
        coach.reset()
        reps = 0
        maxCombo = 0
        startedAtMs = 0L
        saved = false
        _state.value = game.state()
    }

    private fun handle(events: List<SurvivalEvent>) {
        events.filterIsInstance<SurvivalEvent.GameOver>().firstOrNull()?.let { over ->
            if (over.score > _bestScore.value) _bestScore.value = over.score
            save(over)
        }
    }

    /**
     * Survival reps count toward the lifetime total exactly like dungeon reps.
     *
     * They have to: rank is built from lifetime reps, and a free mode whose work did not count
     * would quietly make the app's central promise conditional on paying.
     */
    /**
     * Seeds the player's capacity from the tutorial run and marks onboarding complete.
     *
     * The first survival run is the calibration set: it is the only moment the app can ask someone
     * to do as many as they can without it feeling like a test, because they are busy protecting a
     * cat. Every dungeon from then on is sized from this number.
     */
    fun finishTutorial() {
        val observed = maxCombo
        telemetry.log(Event.TutorialCompleted(reps, _state.value.elapsedMs))
        viewModelScope.launch {
            progressRepository.update { current ->
                current
                    .withCapacity(
                        exercise,
                        Capacity.update(current.capacityOf(exercise), observed),
                    )
                    .copy(onboarded = true)
            }
        }
    }

    private fun save(over: SurvivalEvent.GameOver) {
        if (saved) return
        saved = true
        val repsDone = reps
        val combo = maxCombo

        viewModelScope.launch {
            sessionRepository.insert(
                SessionRecord(
                    startedAtMs = System.currentTimeMillis() - over.survivedMs,
                    durationMs = over.survivedMs,
                    exercise = exercise,
                    reps = repsDone,
                    maxCombo = combo,
                    deepReps = 0,
                    meanDepth = 0f,
                    dungeonIndex = null,
                    cleared = false,
                    xpEarned = 0,
                    plausibility = detector.sessionSummary().plausibility,
                )
            )
            progressRepository.update { current ->
                current.copy(
                    lifetimeReps = current.lifetimeReps + repsDone,
                    bestCombo = maxOf(current.bestCombo, combo),
                    totalActiveMs = current.totalActiveMs + over.survivedMs,
                    bestSurvivalScore = maxOf(current.bestSurvivalScore, over.score),
                )
            }
        }
    }

    companion object {
        private val HOLD_TICK_SECONDS = 1f / PlankConfig().dotTickHz

        fun factory(container: AppContainer, exercise: ExerciseType): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SurvivalViewModel(
                    container.progressRepository,
                    container.sessionRepository,
                    container.telemetry,
                    container.traces,
                    container.settingsRepository,
                    container.audio,
                    exercise,
                )
            }
        }
    }
}
