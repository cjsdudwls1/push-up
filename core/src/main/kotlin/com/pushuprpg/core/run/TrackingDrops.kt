package com.pushuprpg.core.run

import com.pushuprpg.core.detect.PoseQuality
import com.pushuprpg.core.detect.RepPhase

/**
 * How often one run lost the user, summed over the run for the launch metric that asks whether
 * tracking works on real phones (H2 in docs/LAUNCH.md).
 *
 * The per-drop event cannot answer that on its own: it has no run to be summed over, and it fires as
 * readily for the user standing up to walk to the phone and end the run as for the tracker losing
 * someone mid-set. This folds a run's frames into what run_finished carries:
 *
 * - [Summary.armed]: the detector armed at some point — quality OK and out of IDLE and LOST, the
 *   moment the placement coach says 좋아요. Before that the user is still getting into position, and
 *   nothing counts as lost.
 * - [Summary.drops]: steps from OK to anything else while armed. A change from one reason to another
 *   while lost is the same drop.
 * - [Summary.recovered]: those that came back to OK — the tracker lost someone who was still there.
 * - [Summary.lostMs]: how long the recovered ones lasted, which is time the game stood paused. A drop
 *   that never comes back is usually the user leaving, and its length is theirs, not the tracker's.
 *
 * A drop that starts within [TAIL_MS] of the latest frame is left out, recovered or not: that is
 * getting up, walking to the phone and answering the quit question. A movement switch is treated the
 * same way — the changeover before it is left out — and the new movement has to arm before anything
 * counts again.
 *
 * Time is the frames' own, like everything in `:core`. Fed from the pose thread; [summary] reads only
 * counters, so reading it from another thread can at worst miss the latest frame.
 */
class TrackingDrops(
    /** Only a test that replays a set shorter than [TAIL_MS] has reason to change it. */
    private val tailMs: Long = TAIL_MS,
) {

    data class Summary(val armed: Boolean, val drops: Int, val recovered: Int, val lostMs: Long)

    private class Drop(val startMs: Long) {
        var endMs = -1L
        /** Older than the tail, and so counted. */
        var settled = false
    }

    /** Whether the movement being done has armed, so that losing the user now would count. */
    var armed = false
        private set

    private var everArmed = false
    private var lastOk = false
    private var open: Drop? = null
    /** Drops that started within [tailMs] of the latest frame, oldest first; not counted yet. */
    private val recent = ArrayDeque<Drop>()
    private var drops = 0
    private var recovered = 0
    private var lostMs = 0L

    fun onFrame(atMs: Long, quality: PoseQuality, phase: RepPhase) {
        val ok = quality == PoseQuality.OK
        if (!armed) {
            if (ok && phase != RepPhase.IDLE && phase != RepPhase.LOST) {
                armed = true
                everArmed = true
                lastOk = true
            }
            return
        }
        if (lastOk && !ok) {
            val drop = Drop(atMs)
            open = drop
            recent.addLast(drop)
        } else if (!lastOk && ok) {
            open?.let { drop ->
                drop.endMs = atMs
                if (drop.settled) recover(drop)
            }
            open = null
        }
        lastOk = ok
        while (recent.isNotEmpty() && atMs - recent.first().startMs > tailMs) {
            val drop = recent.removeFirst()
            drop.settled = true
            drops++
            if (drop.endMs >= 0) recover(drop)
        }
    }

    /**
     * The movement changed. The tail before it is the user changing over, not the tracker, and the
     * new movement's detector has not armed yet.
     */
    fun onSwitch() {
        recent.clear()
        open = null
        armed = false
        lastOk = false
    }

    fun summary(): Summary = Summary(everArmed, drops, recovered, lostMs)

    private fun recover(drop: Drop) {
        recovered++
        lostMs += drop.endMs - drop.startMs
    }

    companion object {
        /** The end of a run left out of it: long enough to stand, reach the phone and confirm the quit. */
        const val TAIL_MS = 10_000L
    }
}
