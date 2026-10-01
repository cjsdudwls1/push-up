package com.pushuprpg.core.progression

import com.pushuprpg.core.survival.LifeResult

/**
 * Each life's best, by its place in the session: the most a first set has ever done, the most a
 * second has, and so on, for one movement. Reps, or whole seconds for a hold.
 *
 * By the owner's decision a set is played against its own past best — 3세트 against every 3세트
 * before it — with the number on screen while it is played, and breaking it is paid in 츄르, which
 * buys things for the cat ([Shop]). A third set is not a first one: the sets fade across a session,
 * and a single best for all of them would be beaten in the first set or not at all.
 *
 * Only grows. A set short of its best changes nothing and costs nothing.
 */
data class SetBests(
    /** Index 0 is the first life. Missing or 0 is a set never played. */
    val bests: List<Int> = emptyList(),
) {
    /** The best for the life at [index], or 0 when there is none to beat yet. */
    fun at(index: Int): Int = bests.getOrNull(index) ?: 0

    companion object {
        /** A set played for the first time sets its best: worth a little, so the first session pays. */
        const val CHURU_FIRST = 1

        /** A set that beat its best. */
        const val CHURU_BROKEN = 3

        /** What a life counts for its best: its reps, or for a hold the whole seconds it lasted. */
        fun valueOf(life: LifeResult, hold: Boolean): Int =
            if (hold) (life.survivedMs / 1000L).toInt() else life.reps
    }
}

/** What a session did to its movement's [SetBests]. */
data class SetOutcome(
    val bests: SetBests,
    /** Lives, by index, that beat a best there was. */
    val broken: List<Int>,
    /** Lives, by index, that set a best where there was none. */
    val firsts: List<Int>,
) {
    /** 츄르 earned. */
    val churu: Int get() = broken.size * SetBests.CHURU_BROKEN + firsts.size * SetBests.CHURU_FIRST

    companion object {
        /** [lives] played against [previous]. A life that did nothing sets nothing. */
        fun of(previous: SetBests, lives: List<LifeResult>, hold: Boolean): SetOutcome {
            val size = maxOf(previous.bests.size, lives.size)
            val out = MutableList(size) { previous.at(it) }
            val broken = mutableListOf<Int>()
            val firsts = mutableListOf<Int>()
            lives.forEachIndexed { i, life ->
                val value = SetBests.valueOf(life, hold)
                val before = previous.at(i)
                if (value <= 0 || value <= before) return@forEachIndexed
                out[i] = value
                if (before == 0) firsts += i else broken += i
            }
            return SetOutcome(SetBests(out), broken, firsts)
        }
    }
}
