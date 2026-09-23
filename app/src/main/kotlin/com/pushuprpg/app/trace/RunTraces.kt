package com.pushuprpg.app.trace

import com.pushuprpg.core.pose.PoseFrame
import com.pushuprpg.core.trace.PoseTrace
import com.pushuprpg.core.trace.TraceRecorder

/**
 * The landmarks of the latest run, so that a report of "it stopped counting" can arrive as a file
 * that replays through the detector on any machine, instead of as a description to be guessed at.
 *
 * Off unless [enabled], which only a debug build ever sets — the Play build keeps the privacy
 * policy's promise that pose data is never stored. When on, one run is held in memory and replaced
 * by the next; nothing is written anywhere until the user asks to send it, and what is written is
 * joint positions, never an image.
 *
 * [record] is called on MediaPipe's result thread and everything else on the main thread, hence
 * the lock. It is taken once per frame and held for an array copy.
 */
class RunTraces(private val device: String) {

    @Volatile
    var enabled: Boolean = false

    private val lock = Any()
    private var recorder: TraceRecorder? = null
    private var notes: String = ""

    /**
     * Starts a new recording, dropping the last one. [notes] must say what is needed to replay it:
     * the mode, the movement, and the calibration the detector started from.
     */
    fun begin(notes: String) {
        synchronized(lock) {
            // Created on the first frame, which is the first moment the image size is known.
            recorder = null
            this.notes = notes
        }
    }

    /** Adds to the current recording's notes — a movement switch, say — so a replay can follow it. */
    fun mark(note: String) {
        synchronized(lock) { notes = "$notes $note" }
    }

    fun record(frame: PoseFrame) {
        if (!enabled) return
        synchronized(lock) {
            val r = recorder
                ?: TraceRecorder(frame.imageWidth, frame.imageHeight, device).also { recorder = it }
            r.record(frame)
        }
    }

    /** The latest run so far, or null when nothing was recorded. */
    fun latest(): PoseTrace? = synchronized(lock) {
        recorder?.takeIf { it.frameCount > 0 }?.build(notes)
    }
}
