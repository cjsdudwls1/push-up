package com.pushuprpg.core.detect

/**
 * Which leg goes forward next, for a movement done one leg at a time.
 *
 * A lunge set is both legs in turn, and a set done all on one leg is half a set done twice. The
 * detector reports which foot was in front on each counted rep; this remembers the last one and
 * names the other. Doing the same leg twice still counts — it is the user's rep — but it is
 * noticed, so the game can say so.
 */
class LegAlternator {

    /** The leg to put forward on the next rep, or null before the first. */
    var next: BodySide? = null
        private set

    private var last: BodySide? = null

    /** Folds in a counted rep's front leg. True when it was the same leg as the rep before. */
    fun onRep(front: BodySide?): Boolean {
        if (front == null) return false
        val repeated = last == front
        last = front
        next = if (front == BodySide.LEFT) BodySide.RIGHT else BodySide.LEFT
        return repeated
    }

    fun reset() {
        next = null
        last = null
    }
}
