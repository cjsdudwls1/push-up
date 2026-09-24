package com.pushuprpg.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.pushuprpg.app.R
import com.pushuprpg.core.audio.Announcement
import com.pushuprpg.core.audio.VoiceStyle
import com.pushuprpg.core.detect.BodySide
import com.pushuprpg.core.detect.ExerciseType
import com.pushuprpg.core.detect.PlacementAdvice
import com.pushuprpg.core.game.PlayerClass
import com.pushuprpg.core.run.AlertKey
import com.pushuprpg.core.survival.CatLine
import java.util.Locale

/**
 * The game's voice: what [com.pushuprpg.core.audio.Announcer] decides to say, spoken by the
 * phone's own text-to-speech engine.
 *
 * On-device rather than recorded, for three reasons. It is free and works offline. It is the
 * phone's neural Korean voice on any recent Android, not a robot. And the lines carry numbers and
 * names — how many answers are left, which leg goes next — which a folder of recordings could only
 * cover by recording every combination.
 *
 * Urgency is carried by delivery: urgent lines are faster and higher and cut off anything being
 * said. The music ducks under every line, so the words are never the thing that gets lost.
 *
 * Called from the pose thread; every call is posted to the main thread, which is where the engine
 * and the music player are touched.
 */
class GameVoice(context: Context, private val music: MusicPlayer) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var speaking = 0
    private var seq = 0

    @Volatile
    var enabled: Boolean = true

    init {
        main.post {
            tts = TextToSpeech(appContext) { status ->
                val engine = tts ?: return@TextToSpeech
                if (status != TextToSpeech.SUCCESS) return@TextToSpeech
                val lang = engine.setLanguage(Locale.KOREAN)
                ready = lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        main.post { duck(+1) }
                    }

                    override fun onDone(utteranceId: String?) {
                        main.post { duck(-1) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post { duck(-1) }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        main.post { duck(-1) }
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
            val engine = tts ?: return@post
            if (!ready) return@post
            val (rate, pitch) = when (style) {
                VoiceStyle.URGENT -> 1.3f to 1.15f
                VoiceStyle.COACH -> 1.1f to 1.0f
                VoiceStyle.CAT -> 1.15f to 1.6f
            }
            engine.setSpeechRate(rate)
            engine.setPitch(pitch)
            val mode = if (style == VoiceStyle.URGENT) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(text, mode, null, "line-${seq++}")
        }
    }

    fun stop() {
        main.post { tts?.stop() }
    }

    private fun duck(delta: Int) {
        speaking = (speaking + delta).coerceAtLeast(0)
        music.setDucked(speaking > 0)
    }

    private fun textFor(a: Announcement, playerClass: PlayerClass, exercise: ExerciseType): String? {
        val res = appContext.resources
        a.answersLeft?.let { left ->
            return res.getString(R.string.voice_answers_left, times(left))
        }
        a.placement?.let { return placementText(it, exercise) }
        a.cat?.let { return catText(it.line, it.serial, it.arg) }
        a.leg?.let {
            return res.getString(if (it == BodySide.LEFT) R.string.voice_leg_left else R.string.voice_leg_right)
        }
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
            AlertKey.SHALLOW_TWICE -> res.getString(R.string.voice_shallow)
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
}
