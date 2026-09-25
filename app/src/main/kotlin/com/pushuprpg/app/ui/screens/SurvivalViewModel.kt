package com.pushuprpg.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pushuprpg.app.AppContainer
import com.pushuprpg.app.audio.GameAudio
import com.pushuprpg.app.audio.GameVoice
import com.pushuprpg.core.audio.Announcer
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
import com.pushuprpg.core.detect.PoseTick
import com.pushuprpg.core.detect.RepDetector
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.detect.RenderSkeleton
import com.pushuprpg.core.detect.SkeletonMode
import com.pushuprpg.core.progression.Capacity
import com.pushuprpg.core.progression.Streak
import com.pushuprpg.core.progression.StreakState
import com.pushuprpg.core.survival.CatCompanion
import com.pushuprpg.core.survival.CatView
import com.pushuprpg.core.survival.CeilingSurvival
import com.pushuprpg.core.survival.SurvivalEvent
import com.pushuprpg.core.survival.SurvivalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

class SurvivalViewModel(
    private val progressRepository: ProgressRepository,
    private val sessionRepository: SessionRepository,
    private val telemetry: Telemetry,
    private val traces: RunTraces,
    private val settingsRepository: SettingsRepository,
    private val audio: GameAudio,
    private val voice: GameVoice,
    /** Chosen on the way in; always pushups for the tutorial (see Routes.survival). */
    private val exercise: ExerciseType,
    /** The first run, skipped or finished rather than left; see [skipTutorial] and [finishTutorial]. */
    private val tutorial: Boolean,
    /** Where the run is banked: it outlives this screen, which may be popped before a write lands. */
    private val appScope: CoroutineScope,
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

    private val announcer = Announcer()

    // The skeleton, only while setting up: the mode hides it on purpose, but lining up with the
    // framing guide is done by watching the lines.
    private val _setupSkeleton = MutableStateFlow<RenderSkeleton?>(null)
    val setupSkeleton: StateFlow<RenderSkeleton?> = _setupSkeleton.asStateFlow()

    // The cat's face, words and voice. It reads the run and changes nothing in it.
    private val cat = CatCompanion()
    private val _cat = MutableStateFlow(cat.view())
    val catView: StateFlow<CatView> = _cat.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect {
                audio.soundEnabled = it.sfxEnabled
                audio.hapticStrength = it.hapticStrength
                voice.enabled = it.voiceEnabled
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
    // Counted on the pose thread; read on the main one when the user leaves mid-run.
    @Volatile private var reps = 0
    @Volatile private var maxCombo = 0

    /** A run is banked by its game over or by [leave], from two threads, and exactly once. */
    private val saved = AtomicBoolean(false)

    /** The tutorial ends once: skipped, or finished by its card, by back, or by the screen going. */
    private val tutorialEnded = AtomicBoolean(false)

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

        // What is banked is what the detector counted: its strikes, and nothing it refused.
        for (event in tick.events) {
            if (event is RepEvent.Strike) {
                reps++
                maxCombo = maxOf(maxCombo, event.combo)
            }
        }
        // Reps, near misses and holds, then the clock, which never stops once the run has started:
        // resting is not a pause. See CeilingSurvival.onTick.
        val events = game.onTick(tick)
        handle(events)

        val now = game.state()
        // Straight from this thread, like the dungeon's: a push has to be heard as it lands.
        audio.play(cat.update(now, events, tick.tMs))
        val catView = cat.view()
        // The cat's lines are heard as well as read: the bubble is small and the phone is far.
        voice.announce(
            announcer.survival(catView.speech, _placement.value.advice, tick.tMs),
            exercise = exercise,
        )
        _state.value = now
        _cat.value = catView
        detector.skeletonMode = if (now.started) SkeletonMode.OFF else SkeletonMode.FULL
        _setupSkeleton.value = if (now.started) null else tick.render
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
        announcer.reset()
        reps = 0
        maxCombo = 0
        startedAtMs = 0L
        saved.set(false)
        _state.value = game.state()
    }

    private fun handle(events: List<SurvivalEvent>) {
        events.filterIsInstance<SurvivalEvent.GameOver>().firstOrNull()?.let { over ->
            if (over.score > _bestScore.value) _bestScore.value = over.score
            save(score = over.score, survivedMs = over.survivedMs)
        }
    }

    /**
     * Banks a run left before the ceiling came down: the close button, the back gesture, or the
     * screen going any other way. Leaving keeps what was done, exactly as a game over does.
     *
     * There is no confirm to answer first. The ceiling never pauses, by the owner's decision, so it
     * would keep falling while the question was on screen.
     *
     * The tutorial, left once its run has started, is finished rather than only banked: see
     * [finishTutorial]. Left before, it is not skipped here — only [skipTutorial] skips it.
     */
    fun leave() {
        val now = _state.value
        if (!now.started) return
        if (tutorial) finishTutorial() else save(score = now.score, survivedMs = now.elapsedMs)
    }

    override fun onCleared() {
        leave()
    }

    /**
     * Seeds the player's capacity from the tutorial run and marks onboarding complete.
     *
     * The first survival run is the calibration set: it is the only moment the app can ask someone
     * to do as many as they can without it feeling like a test, because they are busy protecting a
     * cat. The difficulty the dungeon list recommends is read from this number; what a dungeon
     * asks is not — that is its rep cost, which the done card quotes.
     *
     * The done card's button ends it, and so does back once the ceiling is moving; a run ended
     * before the ceiling came down is banked first, as a game over banks it. Once, however many
     * ways it is asked for: a double tap on the card applied the capacity twice.
     */
    fun finishTutorial() {
        if (!tutorialEnded.compareAndSet(false, true)) return
        val now = _state.value
        if (now.started) save(score = now.score, survivedMs = now.elapsedMs)
        val observed = maxCombo
        telemetry.log(Event.TutorialCompleted(reps, now.elapsedMs))
        // The tap that calls this also leaves the screen; in its own scope the write could be
        // cancelled, and the tutorial would come back on the next launch.
        appScope.launch {
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

    /**
     * Leaves the tutorial before its run has started: 건너뛰기, or back while the ceiling waits.
     *
     * Someone who cannot get down on the floor, whose room will not fit the phone, or whose phone
     * cannot run the model was held on a camera screen with no way past it: back closed the app, and
     * opening it again opened the tutorial. Onboarding is complete and nothing is measured — the
     * capacity keeps what it was, which is what a skipped calibration should mean.
     */
    fun skipTutorial() {
        if (!tutorialEnded.compareAndSet(false, true)) return
        telemetry.log(Event.TutorialSkipped)
        // In the app's scope, as in finishTutorial: the tap that calls this also leaves the screen.
        appScope.launch {
            progressRepository.update { it.copy(onboarded = true) }
        }
    }

    /**
     * Survival reps count toward the lifetime total and the day's streak exactly like dungeon reps,
     * and so do the tutorial's, which is banked here too.
     *
     * They have to: rank is built from lifetime reps, and a free mode whose work did not count
     * would quietly make the app's central promise conditional on paying. Left out of the streak,
     * the mode a free player can always play could never start one.
     */
    private fun save(score: Int, survivedMs: Long) {
        if (!saved.compareAndSet(false, true)) return
        val repsDone = reps
        val combo = maxCombo
        // Read now rather than inside the write, which runs later on a thread of its own.
        val summary = detector.sessionSummary()
        val plausibility = summary.plausibility
        val work = mapOf(exercise to Streak.amount(exercise, repsDone, summary.holdMs))

        appScope.launch {
            // Judged as a dungeon run is: on the day the run started, with the rest of that day.
            val startedAt = System.currentTimeMillis() - survivedMs
            val epochDay = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            val doneEarlier = sessionRepository.workOn(epochDay)
            sessionRepository.insert(
                SessionRecord(
                    startedAtMs = startedAt,
                    durationMs = survivedMs,
                    exercise = exercise,
                    reps = repsDone,
                    maxCombo = combo,
                    deepReps = 0,
                    meanDepth = 0f,
                    dungeonIndex = null,
                    cleared = false,
                    xpEarned = 0,
                    plausibility = plausibility,
                )
            )
            progressRepository.update { current ->
                val streak = Streak.advance(
                    StreakState(current.streakDays, current.lastActiveEpochDay),
                    epochDay,
                    Streak.sum(doneEarlier, work),
                )
                current.copy(
                    lifetimeReps = current.lifetimeReps + repsDone,
                    bestCombo = maxOf(current.bestCombo, combo),
                    totalActiveMs = current.totalActiveMs + survivedMs,
                    bestSurvivalScore = maxOf(current.bestSurvivalScore, score),
                    streakDays = streak.days,
                    bestStreakDays = maxOf(current.bestStreakDays, streak.days),
                    lastActiveEpochDay = streak.lastActiveDay,
                )
            }
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            exercise: ExerciseType,
            tutorial: Boolean,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SurvivalViewModel(
                    container.progressRepository,
                    container.sessionRepository,
                    container.telemetry,
                    container.traces,
                    container.settingsRepository,
                    container.audio,
                    container.voice,
                    exercise,
                    tutorial,
                    container.appScope,
                )
            }
        }
    }
}
