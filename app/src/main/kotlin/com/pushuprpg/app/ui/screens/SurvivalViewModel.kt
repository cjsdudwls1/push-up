package com.pushuprpg.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pushuprpg.app.AppContainer
import com.pushuprpg.app.domain.ProgressRepository
import com.pushuprpg.app.telemetry.Event
import com.pushuprpg.app.telemetry.Telemetry
import com.pushuprpg.app.domain.SessionRecord
import com.pushuprpg.app.domain.SessionRepository
import com.pushuprpg.core.detect.DetectorConfig
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.PoseTick
import com.pushuprpg.core.detect.RepDetectorImpl
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.detect.SkeletonMode
import com.pushuprpg.core.progression.Capacity
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
) : ViewModel() {

    private val game = CeilingSurvival()

    // The overlay is off by default here: the mode's whole appeal is that it looks like a toy, and
    // a joint diagram over the top would undo that immediately.
    private val detector = RepDetectorImpl(DetectorConfig.pushup(), skeletonMode = SkeletonMode.OFF)

    private val _state = MutableStateFlow(game.state())
    val state: StateFlow<SurvivalState> = _state.asStateFlow()

    private val _bestScore = MutableStateFlow(0)
    val bestScore: StateFlow<Int> = _bestScore.asStateFlow()

    init {
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
        if (restartRequested) {
            restartRequested = false
            applyRestart()
        }
        val tick: PoseTick = detector.onFrame(frame)
        if (startedAtMs == 0L) startedAtMs = tick.tMs

        tick.events.filterIsInstance<RepEvent.Strike>().forEach { strike ->
            reps++
            maxCombo = maxOf(maxCombo, strike.combo)
            handle(game.onRep(strike.depth, strike.tMs))
        }
        handle(game.update(tick.tMs))
        _state.value = game.state()
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
                current.copy(
                    capacityPushup = Capacity.update(current.capacityPushup, observed),
                    onboarded = true,
                )
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
                    exercise = ExerciseType.PUSHUP,
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
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SurvivalViewModel(
                    container.progressRepository,
                    container.sessionRepository,
                    container.telemetry,
                )
            }
        }
    }
}
