package com.pushuprpg.app.audio

import android.content.Context
import android.media.AudioAttributes
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
 * Main thread only — it is driven from composition.
 */
class MusicPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var current: MusicTrack = MusicTrack.OFF
    private val stopPreview = Runnable { stop() }

    /** Starts [track] looping, or keeps it going if it already is. [MusicTrack.OFF] stops. */
    fun play(track: MusicTrack) {
        main.removeCallbacks(stopPreview)
        if (track == current && player != null) {
            player?.takeIf { !it.isPlaying }?.start()
            return
        }
        stop()
        val res = rawRes(track) ?: return
        val audio = appContext.getSystemService(AudioManager::class.java) ?: return
        player = runCatching {
            MediaPlayer.create(
                appContext,
                res,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                audio.generateAudioSessionId(),
            )
        }.getOrNull()?.apply {
            isLooping = true
            setVolume(VOLUME, VOLUME)
            start()
        }
        current = if (player != null) track else MusicTrack.OFF
    }

    /** A few seconds of [track], for choosing one in settings. */
    fun preview(track: MusicTrack) {
        play(track)
        if (track != MusicTrack.OFF) main.postDelayed(stopPreview, PREVIEW_MS)
    }

    /** Holds the place, for the app going to the background. */
    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        player?.takeIf { !it.isPlaying }?.start()
    }

    fun stop() {
        main.removeCallbacks(stopPreview)
        player?.run {
            runCatching { stop() }
            release()
        }
        player = null
        current = MusicTrack.OFF
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
        const val PREVIEW_MS = 8_000L
    }
}
