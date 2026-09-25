package com.pushuprpg.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.pushuprpg.app.R
import com.pushuprpg.core.audio.Announcement
import com.pushuprpg.core.audio.VoiceStyle
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.PlacementAdvice
import com.pushuprpg.core.game.PlayerClass
import com.pushuprpg.core.run.AlertKey
import com.pushuprpg.core.survival.CatLine
import java.security.MessageDigest
import java.util.Locale

/**
 * The game's voice: what [com.pushuprpg.core.audio.Announcer] decides to say, spoken aloud.
 *
 * A line is played from a pre-rendered clip when the app ships one for its exact text —
 * `assets/voice/<id>.ogg`, where the id is the first 12 hex digits of the SHA-1 of the text — and
 * otherwise spoken by the phone's own text-to-speech engine, which is free, offline, and on any
 * recent Android a neural Korean voice. `tools/voice_lines.py` lists every line with its file name
 * and delivery. Keying on the text means a reworded line stops matching its old clip rather than
 * being played in words the app no longer uses, and a line nobody recorded is still said.
 *
 * Urgency is carried by delivery: urgent lines are faster and higher (in the clip, or by the
 * engine's rate and pitch) and cut off anything being said. Everything else queues behind the line
 * in progress, one at a time, whichever of the two is saying it. The music ducks under every line,
 * so the words are never the thing that gets lost — and so does anyone else's: each run of lines
 * holds transient, may-duck audio focus, so the user's own playlist drops under 필살기 와요 and comes
 * back when the queue is empty. When focus is refused, as it is in a phone call, nothing is said.
 *
 * Called from the pose thread; every call is posted to the main thread, which is where the engine,
 * the clip player and the music player are touched.
 */
class GameVoice(context: Context, private val music: MusicPlayer) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var seq = 0

    /** Lines waiting for the one in progress, oldest first. Main thread only. */
    private val pending = ArrayDeque<Pair<String, VoiceStyle>>()
    /** The utterance or clip in progress; a callback for anything else is stale and ignored. */
    private var current: String? = null
    private var clip: MediaPlayer? = null
    /** File names under assets/voice, read once. */
    private var clips: Set<String> = emptySet()

    @Volatile
    var enabled: Boolean = true

    private val speech: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val audioManager: AudioManager? = appContext.getSystemService(AudioManager::class.java)
    /** Held from the first line of a run to the moment the queue empties. Main thread only. */
    private var hasFocus = false
    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speech)
            // A call coming in, or something else that will not share: stop talking over it.
            .setOnAudioFocusChangeListener(
                AudioManager.OnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pending.clear()
                        interrupt()
                        releaseFocus()
                    }
                },
                main,
            )
            .build()

    init {
        main.post {
            clips = runCatching { appContext.assets.list(CLIP_DIR)?.toSet() }.getOrNull().orEmpty()
            tts = TextToSpeech(appContext) { status ->
                val engine = tts ?: return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) return@TextToSpeech
                val lang = engine.setLanguage(Locale.KOREAN)
                ready = lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED
                engine.setAudioAttributes(speech)
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        main.post { finished(utteranceId) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post { finished(utteranceId) }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        main.post { finished(utteranceId) }
                    }
                })
            }
        }
    }

    /** Says [announcements], in the context the words depend on. */
    fun announce(
        announcements: List<Announcement>,
        playerClass: PlayerClass = PlayerClass.KNIGHT,
        exercise: ExerciseType = ExerciseType.PUSHUP,
    ) {
        if (!enabled || announcements.isEmpty()) return
        announcements.forEach { a -> textFor(a, playerClass, exercise)?.let { say(it, a.style) } }
    }

    /** Says a fixed line — the rest countdown, say — outside the announcer. */
    fun say(text: String, style: VoiceStyle = VoiceStyle.COACH) {
        if (!enabled) return
        main.post {
            if (style == VoiceStyle.URGENT) {
                pending.clear()
                interrupt()
            }
            pending.addLast(text to style)
            if (current == null) next()
        }
    }

    fun stop() {
        main.post {
            pending.clear()
            interrupt()
            releaseFocus()
        }
    }

    /** Starts the oldest waiting line, or lets the music back up when there is none. */
    private fun next() {
        while (true) {
            val (text, style) = pending.removeFirstOrNull() ?: run {
                current = null
                music.setDucked(false)
                releaseFocus()
                return
            }
            if (!takeFocus()) {
                pending.clear()
                current = null
                music.setDucked(false)
                return
            }
            val id = "line-${seq++}"
            current = id
            music.setDucked(true)
            if (playClip(clipName(text), id) || speak(text, style, id)) return
        }
    }

    private fun finished(id: String?) {
        if (id == null || id != current) return
        clip?.release()
        clip = null
        next()
    }

    private fun takeFocus(): Boolean {
        if (hasFocus) return true
        val audio = audioManager ?: return true
        hasFocus = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun releaseFocus() {
        if (!hasFocus) return
        hasFocus = false
        audioManager?.abandonAudioFocusRequest(focusRequest)
    }

    /** Stops whatever is being said, without starting the next line. */
    private fun interrupt() {
        current = null
        clip?.run { runCatching { stop() }; release() }
        clip = null
        tts?.stop()
        music.setDucked(false)
    }

    private fun playClip(name: String, id: String): Boolean {
        if (name !in clips) return false
        return runCatching {
            appContext.assets.openFd("$CLIP_DIR/$name").use { fd ->
                val player = MediaPlayer()
                player.setAudioAttributes(speech)
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                player.setOnCompletionListener { finished(id) }
                player.setOnErrorListener { _, _, _ -> finished(id); true }
                player.prepare()
                clip = player
                player.start()
            }
        }.onFailure {
            clip?.release()
            clip = null
        }.isSuccess
    }

    private fun speak(text: String, style: VoiceStyle, id: String): Boolean {
        val engine = tts ?: return false
        if (!ready) return false
        val (rate, pitch) = when (style) {
            VoiceStyle.URGENT -> 1.3f to 1.15f
            VoiceStyle.COACH -> 1.1f to 1.0f
            VoiceStyle.CAT -> 1.15f to 1.6f
        }
        engine.setSpeechRate(rate)
        engine.setPitch(pitch)
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
    }

    private fun textFor(a: Announcement, playerClass: PlayerClass, exercise: ExerciseType): String? {
        val res = appContext.resources
        a.answersLeft?.let { left ->
            return res.getString(R.string.voice_answers_left, times(left))
        }
        a.placement?.let { return placementText(it, exercise) }
        a.cat?.let { return catText(it.line, it.serial, it.arg) }
        return when (a.alert) {
            AlertKey.ULTIMATE_INCOMING -> when {
                exercise == ExerciseType.PLANK -> res.getString(R.string.voice_ultimate_hold)
                playerClass == PlayerClass.ARCHER -> res.getString(R.string.voice_ultimate_archer, times(a.arg))
                else -> res.getString(R.string.voice_ultimate_knight, times(a.arg))
            }
            AlertKey.ULTIMATE_BLOCKED -> res.getString(R.string.voice_ultimate_blocked)
            AlertKey.ULTIMATE_HIT -> res.getString(R.string.voice_ultimate_hit)
            AlertKey.BOSS_LOW_HP -> res.getString(R.string.voice_boss_low)
            AlertKey.COMBO_MILESTONE -> res.getString(R.string.voice_combo, a.arg)
            AlertKey.SHALLOW_TWICE, AlertKey.SHALLOW_PULL -> res.getString(R.string.voice_shallow)
            AlertKey.NOT_SPLIT -> res.getString(R.string.battle_not_split)
            AlertKey.STYLE_TOO_QUICK -> res.getString(R.string.voice_style_too_quick)
            AlertKey.STYLE_NOT_FULL -> res.getString(R.string.voice_style_not_full)
            AlertKey.STYLE_LAGGING -> res.getString(R.string.voice_style_lagging)
            else -> null
        }
    }

    /** "세 번", not "3번": the engine reads a digit as 삼. */
    private fun times(n: Int): String = appContext.getString(
        when (n) {
            1 -> R.string.voice_times_1
            2 -> R.string.voice_times_2
            3 -> R.string.voice_times_3
            4 -> R.string.voice_times_4
            else -> R.string.voice_times_5
        }
    )

    private fun placementText(advice: PlacementAdvice, exercise: ExerciseType): String = appContext.getString(
        when (advice) {
            PlacementAdvice.STEP_INTO_VIEW -> R.string.placement_step_into_view
            PlacementAdvice.COME_CLOSER -> R.string.placement_come_closer
            PlacementAdvice.MOVE_PHONE_BACK -> R.string.placement_move_back
            PlacementAdvice.SHOW_BELOW -> R.string.placement_show_below
            PlacementAdvice.SHOW_ABOVE -> R.string.placement_show_above
            PlacementAdvice.CENTER -> R.string.placement_center
            PlacementAdvice.FACE_CAMERA -> when (exercise) {
                ExerciseType.PUSHUP, ExerciseType.PLANK -> R.string.placement_face_floor
                else -> R.string.placement_face_standing
            }
            PlacementAdvice.CLEARER -> R.string.placement_clearer
            PlacementAdvice.HOLD_PHONE_STILL -> R.string.quality_unstable_camera
            PlacementAdvice.SETTLING -> R.string.quality_subject_switch
            PlacementAdvice.SLOW_DOWN -> R.string.quality_implausible_rate
            PlacementAdvice.GET_IN_POSITION -> when (exercise) {
                ExerciseType.PUSHUP -> R.string.placement_start_pushup
                ExerciseType.PLANK -> R.string.placement_start_plank
                ExerciseType.SQUAT -> R.string.placement_start_squat
                ExerciseType.LUNGE -> R.string.placement_start_lunge
                ExerciseType.PULL_UP -> R.string.placement_start_pull_up
                ExerciseType.DIP -> R.string.placement_start_dip
            }
            PlacementAdvice.READY -> R.string.voice_ready
        }
    )

    private fun catText(line: CatLine, serial: Int, arg: Int): String {
        val wordings = when (line) {
            CatLine.WAITING -> listOf(R.string.cat_line_waiting_1, R.string.cat_line_waiting_2)
            CatLine.HELLO -> listOf(R.string.cat_line_hello_1, R.string.cat_line_hello_2)
            CatLine.CALM -> listOf(R.string.cat_line_calm_1, R.string.cat_line_calm_2, R.string.cat_line_calm_3)
            CatLine.NEAR_MISS -> listOf(R.string.cat_line_near_miss_1, R.string.cat_line_near_miss_2)
            CatLine.UNEASY -> listOf(R.string.cat_line_uneasy_1, R.string.cat_line_uneasy_2)
            CatLine.MILESTONE -> listOf(R.string.cat_line_milestone_1, R.string.cat_line_milestone_2)
            CatLine.COMBO -> listOf(R.string.cat_line_combo_1, R.string.cat_line_combo_2)
            CatLine.SCARED -> listOf(R.string.cat_line_scared_1, R.string.cat_line_scared_2)
            CatLine.PANIC -> listOf(R.string.cat_line_panic_1, R.string.cat_line_panic_2)
            CatLine.SAVED -> listOf(R.string.cat_line_saved_1, R.string.cat_line_saved_2, R.string.cat_line_saved_3)
        }
        val id = wordings[serial % wordings.size]
        return if (line == CatLine.MILESTONE || line == CatLine.COMBO) appContext.getString(id, arg)
        else appContext.getString(id)
    }

    companion object {
        private const val CLIP_DIR = "voice"

        /** The clip file for [text]: see `tools/voice_lines.py`, which must agree. */
        fun clipName(text: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(12) + ".ogg"
        }
    }
}
