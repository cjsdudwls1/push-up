package com.pushuprpg.core.survival

import com.pushuprpg.core.audio.BattleAudio
import com.pushuprpg.core.audio.SoundCue
import com.pushuprpg.core.audio.SoundRequest

/** How frightened the cat is. Read from the room it has left, never from anything the user did. */
enum class CatMood { CALM, UNEASY, SCARED, PANIC }

/**
 * Everything the cat can say. The app owns the words and rotates between wordings by
 * [CatSpeech.serial]; this owns *when*.
 *
 * A line still on screen is only replaced by one of at least its [priority], so "살았다냥" is never
 * cut off by a combo count. [repeatAfterMs] keeps a ceiling hovering on a threshold from making the
 * cat say the same thing every few frames.
 */
enum class CatLine(val priority: Int, val repeatAfterMs: Long) {
    /** Before the run: the ceiling is waiting for the user, and so is the cat. */
    WAITING(0, 0),
    HELLO(1, 0),
    /** Praise while the ceiling is high — rationed, because praise that never stops stops meaning anything. */
    CALM(1, 15_000),
    NEAR_MISS(2, 10_000),
    UNEASY(3, 8_000),
    MILESTONE(4, 0),
    COMBO(4, 0),
    SCARED(5, 6_000),
    PANIC(6, 5_000),
    /** A push that lifted the ceiling out of the frightening part. The line the mode is built for. */
    SAVED(7, 4_000),
}

data class CatSpeech(
    val line: CatLine,
    /** How many times this line was said before, so the wording can rotate rather than repeat. */
    val serial: Int,
    /** The number the line quotes — the combo, the seconds — or 0. */
    val arg: Int = 0,
    val atMs: Long,
)

/** What to draw. Everything the cat shows is in here, so the screen decides nothing about it. */
data class CatView(
    val mood: CatMood = CatMood.CALM,
    /** 1 the moment a push lands, falling to 0 over [CatCompanion.CHEER_MS]: the smile and the hearts. */
    val cheer: Float = 0f,
    /** Hearts the last push earned — more for a deeper one. */
    val hearts: Int = 0,
    /** What the cat is saying, or null when there is no bubble. */
    val speech: CatSpeech? = null,
)

/**
 * 고냥이 itself: its face, its lines and its voice, derived from the run.
 *
 * The mode works because the user is protecting something, and something that does not react is
 * not something anyone protects. So the cat has four stages of fear read from the ceiling's height,
 * brightens for a moment at every push, says so in words, and is heard — purring while the ceiling
 * is high, crying as it comes down, with a heartbeat that quickens near the end and a creak from the
 * ceiling that gets louder as it closes. None of it changes the game: [CeilingSurvival] decides what
 * happens, and this only decides how the cat feels about it.
 *
 * Pure and clock-free like the rest of `:core`: time is the frame's timestamp, so the same trace
 * gives the same cat.
 */
class CatCompanion {

    private var mood = CatMood.CALM
    private var started = false
    private var over = false
    private var lastPushMs = NEVER
    private var hearts = 0
    private var speech: CatSpeech? = null
    private var nowMs = 0L

    private val said = IntArray(CatLine.entries.size)
    private val lastSaidMs = LongArray(CatLine.entries.size) { NEVER }

    private var nextPurrMs = NEVER
    private var nextCreakMs = NEVER
    private var nextHeartbeatMs = NEVER
    private var lastHoldSoundMs = NEVER

    fun view(): CatView = CatView(
        mood = mood,
        cheer = cheer(),
        hearts = if (cheer() > 0f) hearts else 0,
        speech = speech?.takeIf { showing(it, nowMs) },
    )

    /**
     * Folds in one frame: the state after it and the events it produced. Returns what to play.
     * Call once per frame, after [CeilingSurvival.update], with the frame's timestamp.
     */
    fun update(state: SurvivalState, events: List<SurvivalEvent>, nowMs: Long): List<SoundRequest> {
        this.nowMs = nowMs
        val sounds = mutableListOf<SoundRequest>()

        if (!state.started) {
            say(CatLine.WAITING, nowMs)
            // A soft purr while the user sets up: the cat is there before the game is.
            if (nowMs >= nextPurrMs) {
                sounds += SoundRequest(SoundCue.CAT_PURR, volume = 0.35f)
                nextPurrMs = nowMs + PURR_EVERY_MS
            }
            return sounds
        }
        if (!started) {
            started = true
            say(CatLine.HELLO, nowMs)
            sounds += SoundRequest(SoundCue.GO)
            nextCreakMs = nowMs + CREAK_FIRST_MS
        }
        if (over) return sounds

        val before = mood
        var pushed = false

        for (event in events) {
            when (event) {
                is SurvivalEvent.Pushed -> {
                    pushed = true
                    lastPushMs = event.atMs
                    hearts = when {
                        event.deep -> 3
                        event.combo > 0 -> 2
                        else -> 1
                    }
                    sounds += pushSounds(event)
                    if (!event.hold && event.combo > 0 && event.combo % COMBO_LINE_EVERY == 0) {
                        if (say(CatLine.COMBO, event.atMs, arg = event.combo)) {
                            sounds += SoundRequest(SoundCue.COMBO_UP)
                        }
                    }
                }
                is SurvivalEvent.NearMiss -> say(CatLine.NEAR_MISS, event.atMs)
                is SurvivalEvent.Milestone -> {
                    if (say(CatLine.MILESTONE, event.atMs, arg = event.seconds)) {
                        sounds += SoundRequest(SoundCue.COMBO_UP, volume = 0.8f)
                    }
                }
                is SurvivalEvent.GameOver -> {
                    over = true
                    // The result card says it; a bubble on top would be two things talking at once.
                    speech = null
                    sounds += SoundRequest(SoundCue.DEFEAT)
                    return sounds
                }
            }
        }

        mood = moodFor(state.height, before)
        when {
            mood.ordinal > before.ordinal -> sounds += onWorse(mood)
            // Out of the frightening part altogether, by a push. Panic easing into mere fear is
            // not being saved, and the cat does not say it is.
            pushed && before.ordinal >= CatMood.SCARED.ordinal && mood.ordinal < CatMood.SCARED.ordinal -> {
                if (say(CatLine.SAVED, nowMs)) sounds += SoundRequest(SoundCue.CAT_HAPPY)
            }
            pushed && mood == CatMood.CALM -> say(CatLine.CALM, nowMs)
        }

        sounds += ambience(state)
        return sounds
    }

    fun reset() {
        mood = CatMood.CALM
        started = false
        over = false
        lastPushMs = NEVER
        hearts = 0
        speech = null
        // [said] is kept: a retry should not open with the exact wording the last run did.
        lastSaidMs.fill(NEVER)
        nextPurrMs = NEVER
        nextCreakMs = NEVER
        nextHeartbeatMs = NEVER
        lastHoldSoundMs = NEVER
    }

    private fun pushSounds(event: SurvivalEvent.Pushed): List<SoundRequest> {
        if (event.hold) {
            // Twice a second is a plank's tick rate, and a thump at that rate is a drum machine;
            // every other tick is about once a second.
            if (lastHoldSoundMs != NEVER && event.atMs - lastHoldSoundMs < HOLD_SOUND_EVERY_MS) return emptyList()
            lastHoldSoundMs = event.atMs
            return listOf(SoundRequest(SoundCue.CEILING_PUSH, volume = 0.45f))
        }
        val push = SoundRequest(SoundCue.CEILING_PUSH, rate = BattleAudio.pitchForCombo(event.combo))
        return when {
            event.deep -> listOf(push, SoundRequest(SoundCue.REP_DEEP))
            event.combo > 0 -> listOf(push, SoundRequest(SoundCue.REP_ACCEPT))
            // Short of the line: it still moved the ceiling, so it still makes the sound — softer.
            else -> listOf(push.copy(volume = 0.5f))
        }
    }

    private fun onWorse(now: CatMood): List<SoundRequest> = when (now) {
        CatMood.CALM -> emptyList()
        CatMood.UNEASY ->
            if (say(CatLine.UNEASY, nowMs)) listOf(SoundRequest(SoundCue.CAT_MEOW, volume = 0.8f)) else emptyList()
        CatMood.SCARED ->
            if (say(CatLine.SCARED, nowMs)) listOf(SoundRequest(SoundCue.CAT_CRY, volume = 0.85f)) else emptyList()
        CatMood.PANIC ->
            if (say(CatLine.PANIC, nowMs)) listOf(SoundRequest(SoundCue.CAT_CRY, rate = 1.2f)) else emptyList()
    }

    /**
     * The room: a creak from the ceiling that comes faster and louder as it closes, a heartbeat once
     * the cat is frightened, and a purr while it is content and being looked after.
     */
    private fun ambience(state: SurvivalState): List<SoundRequest> {
        val out = mutableListOf<SoundRequest>()
        val closeness = state.intensity.coerceIn(0f, 1f)

        if (nowMs >= nextCreakMs) {
            out += SoundRequest(SoundCue.CEILING_CREAK, volume = 0.2f + 0.6f * closeness)
            nextCreakMs = nowMs + lerp(CREAK_SLOW_MS, CREAK_FAST_MS, closeness)
        }

        if (mood.ordinal >= CatMood.SCARED.ordinal) {
            val fear = ((SCARED_BELOW - state.height) / SCARED_BELOW).coerceIn(0f, 1f)
            if (nextHeartbeatMs == NEVER || nowMs >= nextHeartbeatMs) {
                out += SoundRequest(SoundCue.HEARTBEAT, rate = 1f + 0.15f * fear, volume = 0.45f + 0.55f * fear)
                nextHeartbeatMs = nowMs + lerp(HEARTBEAT_SLOW_MS, HEARTBEAT_FAST_MS, fear)
            }
        } else {
            // Restart the beat from the top next time rather than from a stale schedule.
            nextHeartbeatMs = NEVER
        }

        val content = mood == CatMood.CALM && lastPushMs != NEVER && nowMs - lastPushMs < PURR_WHILE_PUSHED_MS
        if (content && nowMs >= nextPurrMs) {
            out += SoundRequest(SoundCue.CAT_PURR, volume = 0.45f)
            nextPurrMs = nowMs + PURR_EVERY_MS
        }
        return out
    }

    /** Says [line] unless it was said too recently or a more important line is still up. */
    private fun say(line: CatLine, atMs: Long, arg: Int = 0): Boolean {
        val i = line.ordinal
        if (line == CatLine.WAITING && speech?.line == CatLine.WAITING) return false
        if (lastSaidMs[i] != NEVER && atMs - lastSaidMs[i] < line.repeatAfterMs) return false
        val current = speech?.takeIf { showing(it, atMs) }
        if (current != null && current.line.priority > line.priority) return false
        speech = CatSpeech(line, said[i]++, arg, atMs)
        lastSaidMs[i] = atMs
        return true
    }

    // Waiting stays up until the run starts; everything else is a moment.
    private fun showing(s: CatSpeech, atMs: Long): Boolean =
        (s.line == CatLine.WAITING && !started) || (s.line != CatLine.WAITING && atMs - s.atMs < LINE_MS)

    private fun cheer(): Float =
        if (lastPushMs == NEVER) 0f else (1f - (nowMs - lastPushMs).toFloat() / CHEER_MS).coerceIn(0f, 1f)

    companion object {
        private const val NEVER = Long.MIN_VALUE

        /** Heights at which the cat gets more frightened, going down. */
        const val UNEASY_BELOW = 0.62f
        const val SCARED_BELOW = 0.38f
        const val PANIC_BELOW = 0.18f

        /** How far back past a threshold the ceiling must go before the cat calms a stage. */
        const val CALM_MARGIN = 0.05f

        const val LINE_MS = 2_400L
        const val CHEER_MS = 900L
        const val COMBO_LINE_EVERY = 10

        const val PURR_EVERY_MS = 3_500L
        const val PURR_WHILE_PUSHED_MS = 3_000L
        const val CREAK_FIRST_MS = 2_500L
        const val CREAK_SLOW_MS = 4_200L
        const val CREAK_FAST_MS = 1_600L
        const val HEARTBEAT_SLOW_MS = 850L
        const val HEARTBEAT_FAST_MS = 420L
        const val HOLD_SOUND_EVERY_MS = 900L

        /**
         * The mood for [height], given the mood the cat is already in.
         *
         * It gets worse at the thresholds and better only [CALM_MARGIN] past them, so a ceiling
         * sitting on a line does not flick the face between two stages every frame.
         */
        fun moodFor(height: Float, current: CatMood): CatMood {
            val worse = stageAt(height, 0f)
            if (worse.ordinal >= current.ordinal) return worse
            val better = stageAt(height, CALM_MARGIN)
            return if (better.ordinal < current.ordinal) better else current
        }

        private fun stageAt(height: Float, margin: Float): CatMood = when {
            height < PANIC_BELOW + margin -> CatMood.PANIC
            height < SCARED_BELOW + margin -> CatMood.SCARED
            height < UNEASY_BELOW + margin -> CatMood.UNEASY
            else -> CatMood.CALM
        }

        private fun lerp(from: Long, to: Long, t: Float): Long = (from + (to - from) * t).toLong()
    }
}
