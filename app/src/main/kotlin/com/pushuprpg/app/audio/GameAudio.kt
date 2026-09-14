package com.pushuprpg.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pushuprpg.app.R
import com.pushuprpg.app.domain.HapticStrength
import com.pushuprpg.core.audio.SoundCue
import com.pushuprpg.core.audio.SoundRequest
import java.util.concurrent.ConcurrentHashMap

/**
 * Sound and vibration.
 *
 * Played through SoundPool from whatever thread the game loop is on — which is MediaPipe's callback
 * thread — rather than being routed through a recomposition. The budget from a rep reaching the
 * bottom to the sound arriving is about 90 ms, and a trip through Compose spends most of that for
 * nothing. SoundPool is the right tool precisely because it keeps short clips decoded in memory.
 *
 * Every sample is loaded at construction and the class is inert until they are ready; a cue that
 * arrives before its sample is simply dropped. Silence for the first second beats a stutter, and
 * beats holding the game loop while a decoder runs.
 */
class GameAudio(context: Context) {

    private val appContext = context.applicationContext

    private val pool: SoundPool = SoundPool.Builder()
        // Enough voices for a deep rep landing on a crit while the boss is winding up.
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val sampleIds = ConcurrentHashMap<SoundCue, Int>()
    private val ready = ConcurrentHashMap<Int, Boolean>()

    @Volatile
    var soundEnabled: Boolean = true

    @Volatile
    var hapticStrength: HapticStrength = HapticStrength.MEDIUM

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            ready[sampleId] = status == 0
        }
        RESOURCES.forEach { (cue, resId) ->
            val id = pool.load(appContext, resId, 1)
            sampleIds[cue] = id
            ready[id] = false
        }
    }

    fun play(request: SoundRequest) {
        if (!soundEnabled) return
        val id = sampleIds[request.cue] ?: return
        if (ready[id] != true) return
        pool.play(
            id,
            request.volume.coerceIn(0f, 1f),
            request.volume.coerceIn(0f, 1f),
            // Form cues confirm the rep counted; losing one to voice-stealing is worse than losing
            // an impact, which the user can already see.
            if (request.cue.band == SoundCue.Band.FORM) 1 else 0,
            0,
            request.rate.coerceIn(0.5f, 2.0f),
        )
    }

    fun play(requests: List<SoundRequest>) {
        if (requests.isEmpty()) return
        requests.forEach(::play)
        requests.firstOrNull { it.cue.band == SoundCue.Band.COMBAT }?.let { vibrateFor(it.cue) }
    }

    private fun vibrateFor(cue: SoundCue) {
        val v = vibrator ?: return
        if (hapticStrength == HapticStrength.OFF) return
        if (!v.hasVibrator()) return

        val amplitude = when (hapticStrength) {
            HapticStrength.OFF -> return
            HapticStrength.LIGHT -> 70
            HapticStrength.MEDIUM -> 150
            HapticStrength.STRONG -> 255
        }
        val durationMs = when (cue) {
            SoundCue.CRIT -> 55L
            SoundCue.HIT_HEAVY -> 40L
            SoundCue.PLAYER_HURT -> 80L
            SoundCue.ENEMY_DOWN -> 90L
            else -> 22L
        }
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        }
    }

    fun release() {
        pool.release()
        sampleIds.clear()
        ready.clear()
    }

    private companion object {
        val RESOURCES: Map<SoundCue, Int> = mapOf(
            SoundCue.REP_ACCEPT to R.raw.sfx_rep_accept,
            SoundCue.REP_DEEP to R.raw.sfx_rep_deep,
            SoundCue.HIT to R.raw.sfx_hit,
            SoundCue.HIT_HEAVY to R.raw.sfx_hit_heavy,
            SoundCue.CRIT to R.raw.sfx_crit,
            SoundCue.PLAYER_HURT to R.raw.sfx_player_hurt,
            SoundCue.TELEGRAPH to R.raw.sfx_telegraph,
            SoundCue.COMBO_UP to R.raw.sfx_combo_up,
            SoundCue.COMBO_BREAK to R.raw.sfx_combo_break,
            SoundCue.ENEMY_DOWN to R.raw.sfx_enemy_down,
            SoundCue.VICTORY to R.raw.sfx_victory,
            SoundCue.DEFEAT to R.raw.sfx_defeat,
            SoundCue.COUNTDOWN to R.raw.sfx_countdown,
            SoundCue.GO to R.raw.sfx_go,
            SoundCue.CEILING_PUSH to R.raw.sfx_ceiling_push,
        )
    }
}
