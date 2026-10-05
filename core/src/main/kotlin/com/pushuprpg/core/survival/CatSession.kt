package com.pushuprpg.core.survival

import com.pushuprpg.core.detect.PoseTick

/** Where a 고양이 session is: a set being played, the rest after one, or the whole session over. */
enum class CatPhase { PLAYING, RESTING, OVER }

/** One set — one run of the ceiling, a life in [CeilingSurvival]'s words — as it ended. */
data class SetResult(
    val reps: Int,
    val score: Int,
    val survivedMs: Long,
    /** Reps of this set that went past the 깊게 line. */
    val deepReps: Int,
)

/** Everything the screen needs about a session, as one value. */
data class CatSessionState(
    val phase: CatPhase = CatPhase.PLAYING,
    /** The ceiling of the set being played; while resting, or once over, of the one that just ended. */
    val life: SurvivalState = SurvivalState(),
    /** Every set that has ended, in order. */
    val ended: List<SetResult> = emptyList(),
    /** Rest still to go, while [phase] is [CatPhase.RESTING]. */
    val restLeftMs: Long = 0,
    /** Reps of the set being played that went past the 깊게 line. */
    val setDeepReps: Int = 0,
) {
    /** The set being played is counted until it ends; after that it is one of [ended]. */
    private val live: Boolean get() = phase == CatPhase.PLAYING

    val totalScore: Int get() = ended.sumOf { it.score } + if (live) life.score else 0
    val totalReps: Int get() = ended.sumOf { it.reps } + if (live) life.reps else 0

    /** The most reps any one set of this session has made, the one in progress included. */
    val bestSetReps: Int get() = maxOf(ended.maxOfOrNull { it.reps } ?: 0, if (live) life.reps else 0)

    /** Whether anything at all has happened: a set started. What a session needs before it is worth banking. */
    val started: Boolean get() = ended.isNotEmpty() || life.started

    /**
     * Every set played so far, the one in progress included as far as it has got: what a session
     * banks, since a rep done is kept however the run ends.
     */
    val played: List<SetResult>
        get() = if (live && life.started) {
            ended + SetResult(life.reps, life.score, life.elapsedMs, setDeepReps)
        } else {
            ended
        }
}

/** What a session did on one frame, besides what its set did. */
sealed interface CatSessionEvent {
    val atMs: Long

    /** The ceiling won and a set ended. A rest follows it, except in the tutorial's one set. */
    data class SetOver(override val atMs: Long, val result: SetResult) : CatSessionEvent

    /** The rest is over. The next set's ceiling waits, as the first did, for the user to be in position. */
    data class RestOver(override val atMs: Long) : CatSessionEvent

    /** The session was ended from the screen, or the tutorial's one set ended. */
    data class Over(override val atMs: Long) : CatSessionEvent
}

/** One frame of a session: its set's events, then its own. */
data class CatStep(
    val life: List<SurvivalEvent> = emptyList(),
    val session: List<CatSessionEvent> = emptyList(),
)

/**
 * 고양이 지켜줘 as a workout: as many sets as the user wants, with a rest between them.
 *
 * A single run of [CeilingSurvival] is a test — as many as you can, until the ceiling wins — and a
 * test is not a workout. Several of them with a rest between is several sets to the edge of failure,
 * which is what builds muscle: several sets do clearly more than one, and a set taken close to its
 * limit is the one that counts. There used to be ten lives, ten sets, and the session was over when
 * they were; by the owner's decision (2026-10-04) there are no lives, and the session goes on until
 * the user ends it from the screen ([finish]). The rest between sets is the default between sets:
 * the ceiling does not come back until it is over, or until the user skips it ([skipRest]), which
 * the owner added on 2026-10-02.
 *
 * Each set is a fresh [CeilingSurvival] — a fresh ceiling and a fresh ramp, as a new set starts
 * fresh — and keeps that class's rule that nothing pauses it once it has started. With no lives to
 * lose, the ceiling winning while the camera had lost the user costs nothing but the set it ends,
 * whose reps are kept; there is nothing left to give back.
 *
 * Pure like the rest of `:core`: time is the frame's timestamp, and the rest is counted on it.
 */
class CatSession(
    /** A fresh set's ceiling. The tutorial's session and a run's make them the same way. */
    private val newLife: () -> CeilingSurvival,
    /** The tutorial's session: one set, no rest, and over when the ceiling wins. */
    val oneSet: Boolean = false,
) {
    /** The rest between sets. Read as each rest starts, so a change applies from the next one. */
    var restMs: Long = REST_MS

    private var game = newLife()
    private var phase = CatPhase.PLAYING
    private val ended = mutableListOf<SetResult>()

    private var restStartedMs = Long.MIN_VALUE
    private var restLengthMs = 0L
    private var restLeftMs = 0L

    private var setDeep = 0

    fun state(): CatSessionState = CatSessionState(
        phase = phase,
        life = game.state(),
        ended = ended.toList(),
        restLeftMs = if (phase == CatPhase.RESTING) restLeftMs else 0L,
        setDeepReps = if (phase == CatPhase.PLAYING) setDeep else 0,
    )

    /**
     * One frame of the detector, played into the session.
     *
     * While a set is on, it goes to its ceiling as [CeilingSurvival.onTick] takes it. While resting,
     * the frame only moves the rest's clock: reps done during a rest are not played into anything.
     */
    fun onTick(tick: PoseTick): CatStep = when (phase) {
        CatPhase.PLAYING -> play(tick)
        CatPhase.RESTING -> CatStep(session = rest(tick.tMs))
        CatPhase.OVER -> CatStep()
    }

    /**
     * Ends the session where it stands: the close button, back, or the screen going — the only way a
     * session ends, the tutorial's aside. A set in progress counts as far as it got, as every run in
     * this app does.
     */
    fun finish(atMs: Long): List<CatSessionEvent> {
        if (phase == CatPhase.OVER) return emptyList()
        if (phase == CatPhase.PLAYING && game.state().started) ended += resultOf(game.state())
        phase = CatPhase.OVER
        return listOf(CatSessionEvent.Over(atMs))
    }

    /**
     * Ends the rest now. The next set comes on the next frame, through [onTick], exactly as when the
     * rest runs out: the cat asks again, and the ceiling waits for the user to be in position. Asked
     * while a set is on or once the session is over, it does nothing, and leaves nothing behind to
     * cut the next rest short.
     */
    fun skipRest() {
        if (phase == CatPhase.RESTING) restLengthMs = 0L
    }

    /** Back to the first set, as a new session. */
    fun reset() {
        game = newLife()
        phase = CatPhase.PLAYING
        ended.clear()
        restStartedMs = Long.MIN_VALUE
        restLengthMs = 0L
        restLeftMs = 0L
        setDeep = 0
    }

    private fun play(tick: PoseTick): CatStep {
        val events = game.onTick(tick)
        for (e in events) {
            if (e is SurvivalEvent.Deepened || (e is SurvivalEvent.Pushed && e.deep && !e.hold)) setDeep++
        }
        val over = events.filterIsInstance<SurvivalEvent.GameOver>().firstOrNull()
            ?: return CatStep(life = events)
        return CatStep(life = events, session = endSet(over.atMs))
    }

    private fun endSet(atMs: Long): List<CatSessionEvent> {
        val result = resultOf(game.state())
        ended += result
        val out = mutableListOf<CatSessionEvent>(CatSessionEvent.SetOver(atMs, result))
        if (oneSet) {
            phase = CatPhase.OVER
            out += CatSessionEvent.Over(atMs)
        } else {
            phase = CatPhase.RESTING
            restStartedMs = atMs
            restLengthMs = restMs.coerceAtLeast(0L)
            restLeftMs = restLengthMs
        }
        return out
    }

    private fun rest(nowMs: Long): List<CatSessionEvent> {
        restLeftMs = (restLengthMs - (nowMs - restStartedMs)).coerceIn(0L, restLengthMs)
        if (restLeftMs > 0L) return emptyList()
        game = newLife()
        phase = CatPhase.PLAYING
        setDeep = 0
        return listOf(CatSessionEvent.RestOver(nowMs))
    }

    private fun resultOf(s: SurvivalState) = SetResult(
        reps = s.reps,
        score = s.score,
        survivedMs = s.elapsedMs,
        deepReps = setDeep,
    )

    companion object {
        /** A minute between sets, by the owner's decision; the settings offer longer. */
        const val REST_MS = 60_000L
    }
}
