package com.pushuprpg.core.progression

import com.pushuprpg.core.detect.ExerciseType

/**
 * One movement's sessions, each as its total, oldest first: what the hub's graph draws.
 *
 * A session is one banked row — a 고양이 session of up to ten sets, its sets together — and its total
 * is [amount]: every rep of it, or every whole second held for a hold. By the owner's request
 * (2026-11-06) the graph shows a session that did less than the one before as a dip, not only the
 * ones that did more: it is there to see how the work is going, session by session, at a glance.
 * It is said plainly and never as a shortfall — the screen colours a rise and leaves a dip grey.
 *
 * A session with nothing in it is left out. It is a camera that never saw the body or a screen
 * opened and closed, and drawn as a drop to zero it would punish a tracking failure.
 */
data class SessionTrend(
    val exercise: ExerciseType,
    /** The last [Trends.SHOWN] sessions' totals at most, oldest first. */
    val points: List<Int>,
    /** Every session of the movement with anything in it, shown or not. */
    val sessions: Int,
) {
    val latest: Int? get() = points.lastOrNull()

    /** The latest session against the one before it; null with fewer than two. */
    val change: Int? get() = if (points.size < 2) null else points[points.size - 1] - points[points.size - 2]

    /** The most any shown session did: the top of the graph. */
    val peak: Int get() = points.maxOrNull() ?: 0

    val empty: Boolean get() = points.isEmpty()
}

object Trends {

    /** Sessions drawn: enough to see a direction, few enough that each point is a tap-sized dot. */
    const val SHOWN = 10

    /** [exercise]'s trend from every banked run. */
    fun of(facts: List<SessionFacts>, exercise: ExerciseType, shown: Int = SHOWN): SessionTrend {
        val totals = facts
            .filter { it.exercise == exercise }
            .sortedBy { it.startedAtMs }
            .map { it.amount() }
            .filter { it > 0 }
        return SessionTrend(exercise, totals.takeLast(shown), totals.size)
    }

    /** Every movement's trend, done or not, so the graph can switch to any of them in one tap. */
    fun all(facts: List<SessionFacts>, shown: Int = SHOWN): Map<ExerciseType, SessionTrend> =
        ExerciseType.entries.associateWith { of(facts, it, shown) }
}
