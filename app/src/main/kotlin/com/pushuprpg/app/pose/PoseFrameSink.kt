package com.pushuprpg.app.pose

import com.pushuprpg.core.pose.PoseFrame
import java.util.concurrent.atomic.AtomicReference

/**
 * Routes pose frames from MediaPipe's callback thread to whichever screen currently wants them.
 *
 * Deliberately not a Compose `mutableStateOf`. The consumer is swapped from the composition (the
 * main thread) but read from MediaPipe's result thread, and Compose snapshot state gives no
 * guarantees across threads — the reading thread could see a stale consumer indefinitely, or a
 * half-published one. An atomic reference is the whole fix.
 *
 * A frame that arrives with no consumer is dropped rather than queued. Nothing downstream wants a
 * backlog: a pose result the game reacts to late describes a position the user has already left.
 */
class PoseFrameSink {

    private val consumer = AtomicReference<((PoseFrame) -> Unit)?>(null)

    fun attach(block: (PoseFrame) -> Unit) {
        consumer.set(block)
    }

    /** Detaches only if [block] is still the active consumer, so a late teardown cannot unhook a
     *  screen that has already taken over. */
    fun detach(block: (PoseFrame) -> Unit) {
        consumer.compareAndSet(block, null)
    }

    fun emit(frame: PoseFrame) {
        consumer.get()?.invoke(frame)
    }
}
