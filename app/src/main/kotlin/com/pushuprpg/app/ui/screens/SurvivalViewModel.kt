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
import com.pushuprpg.app.telemetry.TutorialEnd
import com.pushuprpg.app.trace.RunTraces
import com.pushuprpg.app.domain.SessionRecord
import com.pushuprpg.app.domain.SessionRepository
import com.pushuprpg.core.detect.DetectorFactory
import com.pushuprpg.app.domain.bestSurvivalScoreOf
import com.pushuprpg.app.domain.setBestsOf
import com.pushuprpg.app.domain.wearFound
import com.pushuprpg.app.domain.withSurvivalScore
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind
import com.pushuprpg.core.detect.Placement
import com.pushuprpg.core.detect.PlacementAdvice
import com.pushuprpg.core.detect.PlacementCoach
import com.pushuprpg.core.detect.PoseQuality
import com.pushuprpg.core.detect.PoseTick
import com.pushuprpg.core.detect.RepDetector
import com.pushuprpg.core.detect.RepEvent
import com.pushuprpg.core.detect.RenderSkeleton
import com.pushuprpg.core.detect.SkeletonMode
import com.pushuprpg.core.progression.RunGrowth
import com.pushuprpg.core.progression.SetBests
import com.pushuprpg.core.progression.SetOutcome
import com.pushuprpg.core.progression.SessionFacts
import com.pushuprpg.core.progression.Streak
import com.pushuprpg.core.progression.StreakState
import com.pushuprpg.core.run.TrackingDrops
import com.pushuprpg.core.survival.CatCompanion
import com.pushuprpg.core.survival.CatPhase
import com.pushuprpg.core.survival.CatSession
import com.pushuprpg.core.survival.CatSessionEvent
import com.pushuprpg.core.survival.CatSessionState
import com.pushuprpg.core.survival.CatView
import com.pushuprpg.core.survival.CeilingSurvival
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

    // Ten lives with a rest between them — ten sets — and the tutorial one life with none: it is
    // somebody's first minute, and what it measures is one go. Each life is worth what the movement
    // is worth everywhere else: a pull-up moves the ceiling about as far as three pushups. See
    // CeilingSurvival.forExercise and CatSession.
    private val session = CatSession(
        newLife = { CeilingSurvival.forExercise(exercise) },
        lives = if (tutorial) 1 else CatSession.LIVES,
        refunds = if (tutorial) 0 else CatSession.REFUNDS,
    )

    /** The rest between lives, from the settings; handed to the session on its own thread. */
    @Volatile
    private var restMs = CatSession.REST_MS

    // Through the factory, so a plank gets the hold detector rather than a rep state machine that
    // would count nothing. The overlay is off: the mode's whole appeal is that it looks like a toy,
    // and a joint diagram over the top would undo that immediately.
    private val detector: RepDetector =
        DetectorFactory.create(exercise).also { it.skeletonMode = SkeletonMode.OFF }

    private val _state = MutableStateFlow(session.state())
    val state: StateFlow<CatSessionState> = _state.asStateFlow()

    private val _bestScore = MutableStateFlow(0)
    val bestScore: StateFlow<Int> = _bestScore.asStateFlow()

    /**
     * This movement's best per set, as it stood before this session: what each life is played
     * against, on screen and by the cat. Moves on only when a session is banked, for the next one.
     */
    private val _setBests = MutableStateFlow(SetBests())
    val setBests: StateFlow<SetBests> = _setBests.asStateFlow()

    /** What the banked session did to the set bests, and the 츄르 it earned. Null until written. */
    private val _setOutcome = MutableStateFlow<SetOutcome?>(null)
    val setOutcome: StateFlow<SetOutcome?> = _setOutcome.asStateFlow()

    private val hold = Exercises.of(exercise).kind == MovementKind.HOLD

    /** What the banked session changed: a record, places passed. Null until it is written. */
    private val _growth = MutableStateFlow<RunGrowth?>(null)
    val growth: StateFlow<RunGrowth?> = _growth.asStateFlow()

    // Reps the detector saw and refused as not deep enough, for the tutorial's ending: a run with
    // none counted and some of these was a depth to find, not a phone to move.
    private val _nearMisses = MutableStateFlow(0)
    val nearMisses: StateFlow<Int> = _nearMisses.asStateFlow()

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
                restMs = it.catRestSeconds * 1_000L
            }
        }
        // One recording for the whole visit, restarts included: the run worth sending is often the
        // one before the retry. Replays with the defaults — survival starts from no calibration.
        traces.begin("mode=survival exercise=${exercise.name} profile=none")
        if (tutorial) telemetry.log(Event.TutorialStarted)
        // Scoped to the destination, so without loading it back the mode reported "최고 0점" every
        // time the user returned — in the one place the product is built around a score.
        viewModelScope.launch {
            val progress = progressRepository.current()
            _bestScore.value = progress.bestSurvivalScoreOf(exercise)
            // The tutorial is nobody's set to beat, and sets none.
            if (!tutorial) _setBests.value = progress.setBestsOf(exercise)
        }
    }

    // Counted on the pose thread; read on the main one when the user leaves mid-run.
    @Volatile private var maxCombo = 0

    /**
     * How often tracking lost the user once in position, for cat_session_finished: H2, whether the
     * tracker works on real phones. Fed on the pose thread, only while a life is on.
     */
    @Volatile private var tracking = TrackingDrops()

    /** Null, not OK, outside a life: see the quality_lost log in [onPoseFrame]. Pose thread. */
    private var lastReportedQuality: PoseQuality? = null

    /** When the first life started, by the phone's clock: the day the session is filed under. */
    @Volatile private var startedWallMs = 0L

    /** A session is banked by its end or by [leave], from two threads, and exactly once. */
    private val saved = AtomicBoolean(false)

    /** The tutorial ends once: skipped, or finished by its card, by back, or by the screen going. */
    private val tutorialEnded = AtomicBoolean(false)

    @Volatile
    private var restartRequested = false

    /** The rest card's 건너뛰기, asked on the main thread and done on the pose thread: see [skipRest]. */
    @Volatile
    private var skipRestRequested = false

    /** The screen is on its way out; see [stopPlaying]. */
    @Volatile
    private var left = false

    /**
     * Ended from the screen with lives still left: [endHere] has published the ending, and frames
     * are no longer played until a restart. Set on the main thread, read on the pose thread.
     */
    @Volatile
    private var endedHere = false

    fun onPoseFrame(frame: com.pushuprpg.core.pose.PoseFrame) {
        if (left) return
        traces.record(frame)
        if (restartRequested) {
            restartRequested = false
            applyRestart()
        }
        if (endedHere) return
        val tick: PoseTick = detector.onFrame(frame)
        _placement.value = coach.update(frame, tick)
        session.restMs = restMs

        // What is banked is what the detector counted while a life was on: its strikes, and nothing
        // it refused. Reps done while resting are not played into anything.
        val before = session.state()
        if (before.phase == CatPhase.PLAYING) {
            for (event in tick.events) {
                if (event is RepEvent.Strike) {
                    maxCombo = maxOf(maxCombo, event.combo)
                } else if (event is RepEvent.Shallow && before.life.alive) {
                    // Not after the ceiling came down: the ending on screen is the run's, and a rep
                    // tried in front of it must not change what it says.
                    _nearMisses.value = _nearMisses.value + 1
                }
            }
        }
        if (skipRestRequested) {
            skipRestRequested = false
            session.skipRest()
        }
        // Reps, near misses and holds, then the clock, which never stops once a life has started:
        // resting is not a pause. Between lives, the rest's own clock. See CatSession.onTick.
        val step = session.onTick(tick)
        val now = session.state()
        if (startedWallMs == 0L && now.started) startedWallMs = System.currentTimeMillis()
        reportTracking(before.phase, now, tick)

        // Straight from this thread: a push has to be heard as it lands. The cat decides what it
        // says about a rest as well as about a life.
        val best = if (now.phase == CatPhase.PLAYING) _setBests.value.at(now.ended.size) else 0
        audio.play(
            cat.update(now.life, step.life, tick.tMs, best = best, hold = hold) +
                cat.rest(now.restLeftMs, step.session, tick.tMs)
        )
        val catView = cat.view()
        // The cat's lines are heard as well as read: the bubble is small and the phone is far. Where
        // to stand is not said during a rest: stepping away for water is what a rest is for.
        val resting = now.phase == CatPhase.RESTING
        voice.announce(
            announcer.survival(catView.speech, _placement.value.advice.takeUnless { resting }, tick.tMs),
            exercise = exercise,
        )
        // Not over an ending published from the screen while this frame was in flight.
        if (endedHere) return
        _state.value = now
        _cat.value = catView
        val settingUp = now.phase == CatPhase.PLAYING && !now.life.started
        detector.skeletonMode = if (settingUp) SkeletonMode.FULL else SkeletonMode.OFF
        _setupSkeleton.value = if (settingUp) tick.render else null

        if (step.session.any { it is CatSessionEvent.Over }) {
            if (now.totalScore > _bestScore.value) _bestScore.value = now.totalScore
            save(now)
        }
    }

    /**
     * Tracking health for H2, read only while a life is on: a rest is for stepping away, and losing
     * the user then is not the tracker's doing. The end of a life is a changeover, as switching
     * movement once was — its last seconds are left out, and the next life has to arm again.
     *
     * Every drop is also sent on its own, with its reason: that is what says why. Only once tracking
     * has been OK, since a life starts with nobody in position yet. The tutorial sends neither, as it
     * sends no cat_session_finished.
     */
    private fun reportTracking(was: CatPhase, now: CatSessionState, tick: PoseTick) {
        if (tutorial) return
        if (now.phase != CatPhase.PLAYING) {
            if (was == CatPhase.PLAYING) tracking.onSwitch()
            lastReportedQuality = null
            return
        }
        tracking.onFrame(tick.tMs, tick.quality, tick.phase)
        if (tick.quality != PoseQuality.OK && lastReportedQuality == PoseQuality.OK) {
            telemetry.log(Event.QualityLost(tick.quality, now.life.reps, armed = tracking.armed))
        }
        lastReportedQuality = tick.quality
    }

    /**
     * Asks for a reset rather than performing one.
     *
     * The button is on the main thread while frames are arriving on MediaPipe's; both the session
     * and the detector are single-threaded and stateful by design, so an in-flight frame landing
     * inside a reset would resume a half-cleared game. Deferring it means exactly one thread ever
     * touches them.
     */
    fun restart() {
        restartRequested = true
        endedHere = false
    }

    /**
     * Ends the rest now, by the owner's decision (2026-10-02): the rest card's 건너뛰기. Asked rather
     * than done, as [restart] is, because the session belongs to the pose thread; the next frame ends
     * the rest the way running out would, so the cat asks again and the ceiling waits for the user.
     */
    fun skipRest() {
        skipRestRequested = true
    }

    /**
     * Ends the session here — the close button, or back — and shows its ending, rather than leaving
     * the screen: with ten lives most sessions end this way, and the ending is where a session says
     * what it did. Nothing played at all has nothing to show, so that leaves; see [leave].
     *
     * Published from the last state the screen had, without touching the session, which belongs to
     * the pose thread: the frames after this are not played.
     *
     * Returns whether there is an ending to show; false means the caller should leave.
     */
    fun endHere(): Boolean {
        val now = _state.value
        if (now.phase == CatPhase.OVER) return false
        if (!now.started) return false
        endedHere = true
        voice.stop()
        val over = now.copy(phase = CatPhase.OVER, ended = now.played, restLeftMs = 0L, lifeDeepReps = 0)
        if (over.totalScore > _bestScore.value) _bestScore.value = over.totalScore
        _state.value = over
        save(over)
        return true
    }

    private fun applyRestart() {
        session.reset()
        detector.reset()
        cat.reset()
        _cat.value = cat.view()
        coach.reset()
        announcer.reset()
        maxCombo = 0
        skipRestRequested = false
        tracking = TrackingDrops()
        lastReportedQuality = null
        startedWallMs = 0L
        _nearMisses.value = 0
        _growth.value = null
        _setOutcome.value = null
        saved.set(false)
        endedHere = false
        _state.value = session.state()
    }

    /**
     * Banks a session left before its lives ran out: the close button, the back gesture, or the
     * screen going any other way. Leaving keeps what was done, exactly as an ending does — the life
     * in progress included, as far as it got.
     *
     * There is no confirm to answer first. The ceiling never pauses, by the owner's decision, so it
     * would keep falling while the question was on screen.
     *
     * The tutorial, left once its run has started, is finished rather than only banked: see
     * [finishTutorial]. Left before, it is not skipped here — only [skipTutorial] skips it. Its card
     * and back finish it directly, so in the tutorial only the screen going gets here.
     */
    fun leave() {
        stopPlaying()
        val now = _state.value
        if (!now.started) return
        if (tutorial) finishTutorial(closed = true) else save(now)
    }

    /**
     * Every way out of the screen comes through here. It keeps feeding frames until its transition
     * is over, and the run played on behind the next screen: the ceiling kept falling and the cat
     * and the coach kept talking over the hub. Lines already queued go unsaid as well.
     */
    private fun stopPlaying() {
        left = true
        voice.stop()
    }

    override fun onCleared() {
        leave()
    }

    /**
     * Marks onboarding complete once the tutorial run has been played.
     *
     * The done card's button ends it, and so does back once the ceiling is moving; a run ended
     * before the ceiling came down is banked first, as an ending banks it. Once, however many ways
     * it is asked for: a double tap on the card once finished it twice.
     *
     * [closed] is the screen going rather than the user leaving it, for the ending H3 is read by.
     */
    fun finishTutorial(closed: Boolean = false) {
        stopPlaying()
        if (!tutorialEnded.compareAndSet(false, true)) return
        val now = _state.value
        if (now.started) save(now)
        val ended = when {
            now.phase == CatPhase.OVER -> TutorialEnd.CRUSHED
            closed -> TutorialEnd.CLOSED
            else -> TutorialEnd.BACK
        }
        telemetry.log(Event.TutorialCompleted(now.totalReps, now.life.elapsedMs, ended, _nearMisses.value))
        // The tap that calls this also leaves the screen; in its own scope the write could be
        // cancelled, and the tutorial would come back on the next launch.
        appScope.launch {
            progressRepository.update { it.copy(onboarded = true) }
        }
    }

    /**
     * Leaves the tutorial before its run has started: 건너뛰기, or back while the ceiling waits and
     * the question it asks answered.
     *
     * Someone who cannot get down on the floor, whose room will not fit the phone, or whose phone
     * cannot run the model was held on a camera screen with no way past it: back closed the app, and
     * opening it again opened the tutorial. Onboarding is complete, and nothing is banked.
     */
    fun skipTutorial() {
        stopPlaying()
        if (!tutorialEnded.compareAndSet(false, true)) return
        telemetry.log(Event.TutorialSkipped)
        // In the app's scope, as in finishTutorial: the tap that calls this also leaves the screen.
        appScope.launch {
            progressRepository.update { it.copy(onboarded = true) }
        }
    }

    /**
     * A session's reps count toward the lifetime total and the day's streak, and so do the
     * tutorial's, which is banked here too.
     *
     * They have to: the calories are counted from every rep, and a mode whose work did not count would
     * quietly break the app's central promise that every rep is kept. Left out of the streak, the
     * quickest mode to play could never start one.
     *
     * One row for the whole session, its lives together: a life is a set, and the session is the
     * workout. Its best set is its best life, which is what the records compare.
     */
    private fun save(snapshot: CatSessionState) {
        if (!saved.compareAndSet(false, true)) return
        val lives = snapshot.played
        if (lives.isEmpty()) return
        val repsDone = lives.sumOf { it.reps }
        val score = lives.sumOf { it.score }
        val playedMs = lives.sumOf { it.survivedMs }
        val deep = lives.sumOf { it.deepReps }
        val bestSet = maxOf(lives.maxOf { it.reps }, 0)
        val combo = maxCombo
        // Read now rather than inside the write, which runs later on a thread of its own.
        val summary = detector.sessionSummary()
        val plausibility = summary.plausibility
        val work = mapOf(exercise to Streak.amount(exercise, repsDone, summary.holdMs))
        // A hold's row is as long as it was held, which is what the records screen and the day's
        // streak bar read off it. Otherwise the time the lives were played: a rest is not activity.
        val durationMs = if (hold) summary.holdMs else playedMs
        val startedAt = startedWallMs.takeIf { it > 0L } ?: (System.currentTimeMillis() - playedMs)
        // Every life played out. Never the tutorial's one life: its row must not read as a session
        // finished, which is what the gifts count.
        val completed = !tutorial && snapshot.phase == CatPhase.OVER && snapshot.livesLeft == 0

        if (!tutorial) {
            telemetry.log(
                Event.CatSessionFinished(
                    exercise = exercise,
                    lives = lives.size,
                    reps = repsDone,
                    score = score,
                    durationMs = playedMs,
                    refunded = lives.any { it.refunded },
                    completed = completed,
                    plausibility = plausibility,
                    tracking = tracking.summary(),
                )
            )
        }

        appScope.launch {
            // Judged on the day the session started, with the rest of that day.
            val epochDay = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            val doneEarlier = sessionRepository.workOn(epochDay)
            val before = sessionRepository.factsNow()
            val facts = SessionFacts(exercise, epochDay, startedAt, repsDone, bestSet, deep, durationMs, fullSession = completed)
            sessionRepository.insert(
                SessionRecord(
                    startedAtMs = startedAt,
                    durationMs = durationMs,
                    exercise = exercise,
                    reps = repsDone,
                    maxCombo = if (hold) combo else bestSet,
                    deepReps = deep,
                    meanDepth = 0f,
                    cleared = completed,
                    plausibility = plausibility,
                )
            )
            // Only the day's work that meets its bar maintains the streak.
            var maintained: Int? = null
            var bestStreakBefore = 0
            var bestStreakAfter = 0
            var sets: SetOutcome? = null
            progressRepository.update { current ->
                val streak = Streak.advance(
                    StreakState(current.streakDays, current.lastActiveEpochDay),
                    epochDay,
                    Streak.sum(doneEarlier, work),
                )
                maintained = streak.days.takeIf { streak.lastActiveDay != current.lastActiveEpochDay }
                bestStreakBefore = current.bestStreakDays
                bestStreakAfter = maxOf(current.bestStreakDays, streak.days)
                // Against what is stored now, not what this session started from: a session banked
                // meanwhile from another screen must not have its records written over.
                val setsNow = if (tutorial) null else SetOutcome.of(current.setBestsOf(exercise), lives, hold)
                sets = setsNow
                current.withSurvivalScore(exercise, score).copy(
                    setBests = if (setsNow == null) current.setBests else current.setBests + (exercise to setsNow.bests.bests),
                    churu = current.churu + (setsNow?.churu ?: 0),
                    lifetimeReps = current.lifetimeReps + repsDone,
                    bestCombo = maxOf(current.bestCombo, bestSet),
                    totalActiveMs = current.totalActiveMs + playedMs,
                    streakDays = streak.days,
                    bestStreakDays = bestStreakAfter,
                    lastActiveEpochDay = streak.lastActiveDay,
                )
            }
            maintained?.let { telemetry.log(Event.StreakMaintained(it)) }
            // Once the streak is written, so a gift the streak brings comes with the session that
            // earned it; and put on, so the cat comes home wearing it.
            sets?.let {
                _setOutcome.value = it
                // The next session, a retry included, is played against these.
                _setBests.value = it.bests
            }
            val growth = RunGrowth.of(before, listOf(facts), bestStreakBefore, bestStreakAfter)
            _growth.value = growth
            settingsRepository.wearFound(growth)
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
