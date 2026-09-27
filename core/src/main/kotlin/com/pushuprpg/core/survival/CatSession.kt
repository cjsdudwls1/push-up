package com.pushuprpg.core.survival

import com.pushuprpg.core.detect.PoseQuality
import com.pushuprpg.core.detect.PoseTick

/** Where a 고냥이 session is: a life being played, the rest after one, or the whole session over. */
enum class CatPhase { PLAYING, RESTING, OVER }

/** One life, as it ended. */
data class LifeResult(
    val reps: Int,
    val score: Int,
    val survivedMs: Long,
    /** Reps of this life that went past the 깊게 line. */
    val deepReps: Int,
    /**
     * Given back: the ceiling came down while the camera had lost the user, which is the tracker's
     * doing rather than theirs. Its reps and points still count; it cost no life.
     */
    val refunded: Boolean = false,
)

/** Everything the screen needs about a session, as one value. */
data class CatSessionState(
    val phase: CatPhase = CatPhase.PLAYING,
    /** The life being played; while resting, or once over, the one that just ended. */
    val life: SurvivalState = SurvivalState(),
    /** Lives the session started with. */
    val lives: Int = CatSession.LIVES,
    /** Lives not yet lost, the one being played included. */
    val livesLeft: Int = lives,
    /** Every life that has ended, in order, refunded ones included. */
    val ended: List<LifeResult> = emptyList(),
    /** Rest still to go, while [phase] is [CatPhase.RESTING]. */
    val restLeftMs: Long = 0,
    /** Reps of the life being played that went past the 깊게 line. */
    val lifeDeepReps: Int = 0,
) {
    /** The life being played is counted until it ends; after that it is one of [ended]. */
    private val live: Boolean get() = phase == CatPhase.PLAYING

    val totalScore: Int get() = ended.sumOf { it.score } + if (live) life.score else 0
    val totalReps: Int get() = ended.sumOf { it.reps } + if (live) life.reps else 0

    /** The most reps any one life of this session has made, the one in progress included. */
    val bestLifeReps: Int get() = maxOf(ended.maxOfOrNull { it.reps } ?: 0, if (live) life.reps else 0)

    /** Whether anything at all has happened: a life started. What a session needs before it is worth banking. */
    val started: Boolean get() = ended.isNotEmpty() || life.started

    /**
     * Every life played so far, the one in progress included as far as it has got: what a session
     * left part way banks, since a rep done is kept however the run ends.
     */
    val played: List<LifeResult>
        get() = if (live && life.started) {
            ended + LifeResult(life.reps, life.score, life.elapsedMs, lifeDeepReps)
        } else {
            ended
        }
}

/** What a session did on one frame, besides what its life did. */
sealed interface CatSessionEvent {
    val atMs: Long

    /** A life ended; [livesLeft] after it. A refunded life leaves them as they were. */
    data class LifeLost(override val atMs: Long, val result: LifeResult, val livesLeft: Int) : CatSessionEvent

    /** The rest is over. The next life's ceiling waits, as the first did, for the user to be in position. */
    data class RestOver(override val atMs: Long) : CatSessionEvent

    /** No lives left, or the session was ended from the screen. */
    data class Over(override val atMs: Long) : CatSessionEvent
}

/** One frame of a session: its life's events, then its own. */
data class CatStep(
    val life: List<SurvivalEvent> = emptyList(),
    val session: List<CatSessionEvent> = emptyList(),
)

/**
 * 고냥이 지켜줘 as a workout: [lives] lives, each one a set, with a rest between them.
 *
 * A single run of [CeilingSurvival] is a test — as many as you can, until the ceiling wins — and a
 * test is not a workout. Several of them with a rest between is several sets to the edge of failure,
 * which is what builds muscle: several sets do clearly more than one, and a set taken close to its
 * limit is the one that counts. By the owner's decision there are ten lives — ten sets, as in the
 * old ten-by-ten volume programmes — and the rest between them is not optional: the ceiling does
 * not come back until it is over. The session can be ended after any of them, and keeps what was
 * done.
 *
 * Each life is a fresh [CeilingSurvival] — a fresh ceiling and a fresh ramp, as a new set starts
 * fresh — and keeps that class's rule that nothing pauses it once it has started. The one exception
 * is the camera's own failure: a life that ends while the tracker has lost the user for
 * [REFUND_LOST_MS] is given back, [refunds] times a session, because the rule this app keeps
 * everywhere else is that a tracking failure never costs the user anything. Only one is given, so
 * stepping out of view is not a way to live forever.
 *
 * Pure like the rest of `:core`: time is the frame's timestamp, and the rest is counted on it.
 */
class CatSession(
    /** A fresh life. The tutorial's session and a run's make them the same way. */
    private val newLife: () -> CeilingSurvival,
    val lives: Int = LIVES,
    private val refunds: Int = REFUNDS,
) {
    init {
        require(lives >= 1) { "a session needs a life" }
    }

    /** The rest between lives. Read as each rest starts, so a change applies from the next one. */
    var restMs: Long = REST_MS

    private var game = newLife()
    private var phase = CatPhase.PLAYING
    private var livesLeft = lives
    private val ended = mutableListOf<LifeResult>()
    private var refundsUsed = 0

    private var restStartedMs = Long.MIN_VALUE
    private var restLengthMs = 0L
    private var restLeftMs = 0L

    /** When the camera lost the user, for the stretch it has not found them since; MIN_VALUE while it sees them. */
    private var lostSinceMs = Long.MIN_VALUE
    private var lifeDeep = 0

    fun state(): CatSessionState = CatSessionState(
        phase = phase,
        life = game.state(),
        lives = lives,
        livesLeft = livesLeft,
        ended = ended.toList(),
        restLeftMs = if (phase == CatPhase.RESTING) restLeftMs else 0L,
        lifeDeepReps = if (phase == CatPhase.PLAYING) lifeDeep else 0,
    )

    /**
     * One frame of the detector, played into the session.
     *
     * While a life is on, it goes to the life as [CeilingSurvival.onTick] takes it. While resting,
     * the frame only moves the rest's clock: reps done during a rest are not played into anything.
     */
    fun onTick(tick: PoseTick): CatStep = when (phase) {
        CatPhase.PLAYING -> play(tick)
        CatPhase.RESTING -> CatStep(session = rest(tick.tMs))
        CatPhase.OVER -> CatStep()
    }

    /**
     * Ends the session where it stands: the close button, or the screen going. A life in progress
     * counts as far as it got, as every run in this app does.
     */
    fun finish(atMs: Long): List<CatSessionEvent> {
        if (phase == CatPhase.OVER) return emptyList()
        if (phase == CatPhase.PLAYING && game.state().started) ended += resultOf(game.state(), refunded = false)
        phase = CatPhase.OVER
        return listOf(CatSessionEvent.Over(atMs))
    }

    /** Back to the first life, as a new session. */
    fun reset() {
        game = newLife()
        phase = CatPhase.PLAYING
        livesLeft = lives
        ended.clear()
        refundsUsed = 0
        restStartedMs = Long.MIN_VALUE
        restLengthMs = 0L
        restLeftMs = 0L
        lostSinceMs = Long.MIN_VALUE
        lifeDeep = 0
    }

    private fun play(tick: PoseTick): CatStep {
        lostSinceMs = when {
            tick.quality == PoseQuality.OK -> Long.MIN_VALUE
            lostSinceMs == Long.MIN_VALUE -> tick.tMs
            else -> lostSinceMs
        }
        val events = game.onTick(tick)
        for (e in events) {
            if (e is SurvivalEvent.Deepened || (e is SurvivalEvent.Pushed && e.deep && !e.hold)) lifeDeep++
        }
        val over = events.filterIsInstance<SurvivalEvent.GameOver>().firstOrNull()
            ?: return CatStep(life = events)
        return CatStep(life = events, session = endLife(over.atMs))
    }

    private fun endLife(atMs: Long): List<CatSessionEvent> {
        val trackerLost = lostSinceMs != Long.MIN_VALUE && atMs - lostSinceMs >= REFUND_LOST_MS
        val refunded = trackerLost && refundsUsed < refunds
        if (refunded) refundsUsed++ else livesLeft--
        val result = resultOf(game.state(), refunded)
        ended += result
        val out = mutableListOf<CatSessionEvent>(CatSessionEvent.LifeLost(atMs, result, livesLeft))
        if (livesLeft <= 0) {
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
        lostSinceMs = Long.MIN_VALUE
        lifeDeep = 0
        return listOf(CatSessionEvent.RestOver(nowMs))
    }

    private fun resultOf(s: SurvivalState, refunded: Boolean) = LifeResult(
        reps = s.reps,
        score = s.score,
        survivedMs = s.elapsedMs,
        deepReps = lifeDeep,
        refunded = refunded,
    )

    companion object {
        /** Ten lives, ten sets: the owner's number. */
        const val LIVES = 10

        /** A minute between sets, by the owner's decision; the settings offer longer. */
        const val REST_MS = 60_000L

        /** Lives a session gives back for the camera's failures. */
        const val REFUNDS = 1

        /** How long the camera must have lost the user, up to the life ending, for it to be the camera's doing. */
        const val REFUND_LOST_MS = 2_000L
    }
}
