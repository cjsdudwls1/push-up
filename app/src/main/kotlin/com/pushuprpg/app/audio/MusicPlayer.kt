package com.pushuprpg.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.MusicTrack

/**
 * Background music: one looping track at a time, under the game's sounds.
 *
 * MediaPlayer rather than SoundPool, because these are half-minute loops, not clips, and SoundPool
 * would hold every one of them decoded in memory. The files carry ANDROID_LOOP so the platform
 * loops them without a gap (tools/generate_music.py).
 *
 * Played well under the effects on purpose: a rep's chime and a hit's thump are the game telling
 * the user something, and the music must never be the thing that drowns it out.
 *
 * It gives way to everyone else's audio. It plays only while it holds audio focus: a ringing phone
 * or another app's short sound pauses it until they are done, and music someone starts elsewhere
 * stops it. Someone who opens the app over their own music keeps their music (see [play]).
 *
 * Main thread only — it is driven from composition, once, at the root of the app.
 */
class MusicPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val audio: AudioManager? = appContext.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var current: MusicTrack = MusicTrack.OFF

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var hasFocus = false

    /** Paused for a call or another app's short sound, and to come back when it is over. */
    private var pausedForTransientLoss = false

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(
            AudioManager.OnAudioFocusChangeListener { onFocusChange(it) },
            Handler(Looper.getMainLooper()),
        )
        .build()

    /**
     * Starts [track] looping, or keeps it going if it already is. [MusicTrack.OFF] stops.
     *
     * With [yieldToOtherMusic], nothing starts while another app's music is playing: the track the
     * app opens with passes it, so opening the game over a playlist does not put two songs on top of
     * each other. A track picked afterwards, in settings, was asked for and plays.
     */
    fun play(track: MusicTrack, yieldToOtherMusic: Boolean = false) {
        if (track == current && player != null) {
            resume()
            return
        }
        stop()
        val res = rawRes(track) ?: return
        val audio = audio ?: return
        if (yieldToOtherMusic && audio.isMusicActive) return
        if (!requestFocus()) return
        player = runCatching {
            MediaPlayer.create(appContext, res, attributes, audio.generateAudioSessionId())
        }.getOrNull()?.apply {
            isLooping = true
            setVolume(VOLUME, VOLUME)
            start()
        }
        current = if (player != null) track else MusicTrack.OFF
        if (player == null) abandonFocus()
    }

    /** Lowers the music under a spoken line, and brings it back after. */
    fun setDucked(ducked: Boolean) {
        val v = if (ducked) VOLUME * DUCK else VOLUME
        player?.setVolume(v, v)
    }

    /** Holds the place, for the app going to the background. */
    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        abandonFocus()
    }

    /**
     * Picks up where [pause] left off — unless the user put music on while the app was away, which
     * keeps playing, or the phone is in a call and focus is refused.
     */
    fun resume() {
        val p = player ?: return
        if (p.isPlaying) return
        if (audio?.isMusicActive == true) return
        if (requestFocus()) p.start()
    }

    fun stop() {
        player?.run {
            runCatching { stop() }
            release()
        }
        player = null
        current = MusicTrack.OFF
        abandonFocus()
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            // Someone else's music: it is theirs now, and the app does not take it back on its own.
            AudioManager.AUDIOFOCUS_LOSS -> {
                player?.takeIf { it.isPlaying }?.pause()
                abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player?.takeIf { it.isPlaying }?.let {
                it.pause()
                pausedForTransientLoss = true
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedForTransientLoss) {
                pausedForTransientLoss = false
                player?.start()
            }
            // A duck: another app's is done by the platform, the game's own voice's by setDucked.
            else -> Unit
        }
    }

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val audio = audio ?: return false
        hasFocus = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        pausedForTransientLoss = false
        if (!hasFocus) return
        hasFocus = false
        audio?.abandonAudioFocusRequest(focusRequest)
    }

    private fun rawRes(track: MusicTrack): Int? = when (track) {
        MusicTrack.OFF -> null
        MusicTrack.ADVENTURE -> R.raw.bgm_adventure
        MusicTrack.BATTLE -> R.raw.bgm_battle
        MusicTrack.FOCUS -> R.raw.bgm_focus
        MusicTrack.CALM -> R.raw.bgm_calm
    }

    private companion object {
        /** Well under the effects, which play at up to full scale. */
        const val VOLUME = 0.45f

        /** How far the music drops while the voice speaks. */
        const val DUCK = 0.3f
    }
}
