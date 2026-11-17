package com.pushuprpg.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/**
 * 고양이 지켜줘's music: the pixel demo's chiptune, played live rather than from a file, because it
 * follows the ceiling — faster, busier and an octave up as [intensity] goes from 0 to 1.
 *
 * Four bars over A, F, C and G: a triangle bass on the beat (and its octave between them once the
 * ceiling is low), a square arpeggio on every sixteenth, and hi-hat noise on the off ones, with a
 * crash on the beat near the end. 96 bpm calm, 180 at the cat's head. The numbers are the pixel
 * demo's (2026-11-10, the look the owner chose), voice for voice.
 *
 * One thread writes a stream to an [AudioTrack]; [start] and [stop] may be called from anywhere.
 */
internal class Chiptune(private val attributes: AudioAttributes) {

    /** 0 calm to 1 the ceiling on the cat's head. */
    @Volatile var intensity = 0f

    /** Overall level, for ducking under the voice. */
    @Volatile var gain = 1f

    @Volatile private var thread: Thread? = null

    val playing: Boolean get() = thread != null

    fun start() {
        if (thread != null) return
        val t = Thread({ run(Thread.currentThread()) }, "chiptune")
        thread = t
        t.start()
    }

    /** Lets the thread finish its chunk and release the track; does not wait for it. */
    fun stop() {
        thread = null
    }

    private class Voice(val kind: Int, val freq: Double, val start: Long, val length: Long, val vol: Double) {
        var phase = 0.0
        var lp = 0.0
        val decay = ln(0.0001 / vol) / length
    }

    private fun run(self: Thread) {
        val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SR)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBuf, CHUNK * 4))
                .build()
        }.getOrNull()
        if (track == null) {
            if (thread === self) thread = null
            return
        }
        val buf = ShortArray(CHUNK)
        val voices = ArrayList<Voice>()
        val random = Random(20261110)
        val hpA = Math.exp(-2.0 * Math.PI * 6000.0 / SR)
        var n = 0L
        var step = 0L
        var nextStep = 0L
        try {
            track.play()
            while (thread === self) {
                val i = intensity.coerceIn(0f, 1f)
                val g = MASTER * gain
                for (k in 0 until CHUNK) {
                    if (n >= nextStep) {
                        val bpm = 96.0 + 84.0 * i
                        val sp = (60.0 / bpm / 4.0 * SR).toLong()
                        schedule(voices, step, n, sp, i)
                        step++
                        nextStep = n + sp
                    }
                    var out = 0.0
                    val it = voices.iterator()
                    while (it.hasNext()) {
                        val v = it.next()
                        val age = n - v.start
                        if (age >= v.length) { it.remove(); continue }
                        val env = v.vol * Math.exp(v.decay * age)
                        val s = when (v.kind) {
                            TRI -> {
                                v.phase = (v.phase + v.freq / SR) % 1.0
                                4.0 * Math.abs(v.phase - 0.5) - 1.0
                            }
                            SQUARE -> {
                                v.phase = (v.phase + v.freq / SR) % 1.0
                                if (v.phase < 0.5) 1.0 else -1.0
                            }
                            else -> {
                                // White noise through a one-pole high-pass at 6 kHz.
                                val w = random.nextDouble() * 2.0 - 1.0
                                v.lp = (1 - hpA) * w + hpA * v.lp
                                w - v.lp
                            }
                        }
                        out += s * env
                    }
                    buf[k] = (out * g * 32767.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
                    n++
                }
                if (track.write(buf, 0, CHUNK) < 0) break
            }
        } catch (_: Throwable) {
            // A track the system took away: the run goes on without music.
        } finally {
            runCatching { track.stop() }
            track.release()
            if (thread === self) thread = null
        }
    }

    private fun schedule(voices: MutableList<Voice>, step: Long, at: Long, sp: Long, i: Float) {
        val bar = ((step / 16) % 4).toInt()
        val s = (step % 16).toInt()
        val root = ROOTS[bar]
        if (s % 4 == 0) voices += Voice(TRI, root, at, sp * 3, 0.22)
        if (i > 0.55f && s % 4 == 2) voices += Voice(TRI, root * 2, at, sp, 0.12)
        val up = if (i > 0.75f && s % 8 >= 4) 12 else 0
        val semis = ARP[bar][s % 4] + up
        voices += Voice(SQUARE, root * 4 * 2.0.pow(semis / 12.0), at, (sp * 0.9).toLong(), 0.045)
        if (s % 2 == 1) voices += Voice(NOISE, 0.0, at, secs(0.03), if (i > 0.35f) 0.06 else 0.025)
        if (i > 0.8f && s % 4 == 0) voices += Voice(NOISE, 0.0, at, secs(0.08), 0.09)
    }

    private fun secs(s: Double): Long = (s * SR).toLong()

    private companion object {
        const val SR = 44_100
        const val CHUNK = 512
        const val MASTER = 0.9
        const val TRI = 0
        const val SQUARE = 1
        const val NOISE = 2
        val ROOTS = doubleArrayOf(110.0, 87.31, 130.81, 98.0)
        val ARP = arrayOf(intArrayOf(0, 3, 7, 12), intArrayOf(0, 4, 7, 12), intArrayOf(0, 4, 7, 12), intArrayOf(0, 4, 7, 12))
    }
}
