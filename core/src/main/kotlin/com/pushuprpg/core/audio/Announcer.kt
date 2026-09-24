package com.pushuprpg.core.audio

import com.pushuprpg.core.detect.BodySide
import com.pushuprpg.core.detect.PlacementAdvice
import com.pushuprpg.core.game.Encounter
import com.pushuprpg.core.run.AlertKey
import com.pushuprpg.core.run.BattleState
import com.pushuprpg.core.survival.CatSpeech

/** How a line is spoken: [URGENT] fast and high, [COACH] plain, [CAT] as 고냥이. */
enum class VoiceStyle { URGENT, COACH, CAT }

/**
 * One thing to say out loud. Exactly one of [alert], [answersLeft], [placement] or [cat] is set;
 * the app turns it into words. [arg] is the alert's number.
 */
data class Announcement(
    val style: VoiceStyle,
    val alert: AlertKey? = null,
    val arg: Int = 0,
    /** Answers still needed to block the ultimate that is winding up. */
    val answersLeft: Int? = null,
    val placement: PlacementAdvice? = null,
    val cat: CatSpeech? = null,
    /** The leg to put forward next, for a lunge. */
    val leg: BodySide? = null,
)

/**
 * Decides what the game says aloud, and when.
 *
 * The phone is on the floor two metres away during a set, and a red banner saying 필살기 준비 is
 * not something anyone reads from there. So the lines that ask for action are spoken: the
 * ultimate winding up and what answers it, how many answers are still needed as they land, and
 * where to move when the camera loses the user. Everything else stays on screen.
 *
 * Rules, because a voice that talks over itself is worse than none:
 * - Urgent lines always go, and cut off whatever is being said.
 * - Anything else waits for a gap of [MIN_GAP_MS] since the last line, or is dropped — a stale
 *   instruction is noise.
 * - The same placement advice is not repeated within [PLACEMENT_REPEAT_MS]: said once, the banner
 *   carries it.
 *
 * Pure like the rest of `:core`: time is a parameter.
 */
class Announcer {

    private var lastAlertAtMs = Long.MIN_VALUE
    private var lastSpokenMs = Long.MIN_VALUE
    private var wasIncoming = false
    private var lastAnswers = 0
    private var lastPlacement: PlacementAdvice? = null
    private val placementSaidAt = HashMap<PlacementAdvice, Long>()
    private var lastCat: CatSpeech? = null
    private var lastLeg: BodySide? = null

    /** Folds in one battle frame. [nowMs] is the frame's timestamp. */
    fun battle(state: BattleState, nowMs: Long): List<Announcement> {
        val out = ArrayList<Announcement>()

        // The ultimate: once as it starts winding up, with what answers it, then the count down.
        if (state.ultimateIncoming && !wasIncoming) {
            lastAnswers = 0
            out += Announcement(VoiceStyle.URGENT, alert = AlertKey.ULTIMATE_INCOMING, arg = Encounter.ANSWERS_TO_BLOCK)
        } else if (state.ultimateIncoming && state.ultimateAnswers > lastAnswers) {
            val left = Encounter.ANSWERS_TO_BLOCK - state.ultimateAnswers
            if (left > 0) out += Announcement(VoiceStyle.URGENT, answersLeft = left)
        }
        wasIncoming = state.ultimateIncoming
        lastAnswers = if (state.ultimateIncoming) state.ultimateAnswers else 0

        state.alert?.let { toast ->
            if (toast.atMs != lastAlertAtMs) {
                lastAlertAtMs = toast.atMs
                when (toast.textKey) {
                    AlertKey.ULTIMATE_BLOCKED, AlertKey.ULTIMATE_HIT ->
                        out += Announcement(VoiceStyle.URGENT, alert = toast.textKey, arg = toast.arg)
                    AlertKey.BOSS_LOW_HP, AlertKey.COMBO_MILESTONE, AlertKey.SHALLOW_TWICE, AlertKey.NOT_SPLIT ->
                        out += Announcement(VoiceStyle.COACH, alert = toast.textKey, arg = toast.arg)
                    // The rest are either the placement line's business (tracking lost and
                    // found) or too frequent to be worth a voice.
                    else -> Unit
                }
            }
        }

        placementLine(state.placement.advice, nowMs)?.let { out += it }
        legLine(state.nextFront)?.let { out += it }
        return gate(out, nowMs)
    }

    /** Folds in one survival frame: the cat's newest line, and where to move. */
    fun survival(
        cat: CatSpeech?,
        placement: PlacementAdvice?,
        nowMs: Long,
        nextFront: BodySide? = null,
    ): List<Announcement> {
        val out = ArrayList<Announcement>()
        // The leg first: it is the instruction for the rep about to start, the cat can wait.
        legLine(nextFront)?.let { out += it }
        if (cat != null && cat != lastCat) out += Announcement(VoiceStyle.CAT, cat = cat)
        lastCat = cat
        placementLine(placement, nowMs)?.let { out += it }
        return gate(out, nowMs)
    }

    /**
     * "왼발!" as the next rep's leg changes — every rep of a lunge set, which is the point: the
     * user is looking at the floor, not at the phone.
     */
    private fun legLine(next: BodySide?): Announcement? {
        if (next == lastLeg) return null
        lastLeg = next
        return next?.let { Announcement(VoiceStyle.COACH, leg = it) }
    }

    fun reset() {
        lastAlertAtMs = Long.MIN_VALUE
        lastSpokenMs = Long.MIN_VALUE
        wasIncoming = false
        lastAnswers = 0
        lastPlacement = null
        placementSaidAt.clear()
        lastCat = null
        lastLeg = null
    }

    private fun placementLine(advice: PlacementAdvice?, nowMs: Long): Announcement? {
        if (advice == lastPlacement) return null
        lastPlacement = advice
        if (advice == null) return null
        val said = placementSaidAt[advice]
        if (said != null && nowMs - said < PLACEMENT_REPEAT_MS) return null
        placementSaidAt[advice] = nowMs
        return Announcement(VoiceStyle.COACH, placement = advice)
    }

    private fun gate(lines: List<Announcement>, nowMs: Long): List<Announcement> {
        if (lines.isEmpty()) return lines
        val urgent = lines.filter { it.style == VoiceStyle.URGENT }
        val kept = if (urgent.isNotEmpty()) {
            // An urgent line cuts in; anything calmer said in the same breath would only be cut off.
            listOf(urgent.last())
        } else if (lastSpokenMs == Long.MIN_VALUE || nowMs - lastSpokenMs >= MIN_GAP_MS || lines.first().leg != null) {
            // The leg call is never dropped for being close behind another line: it is the one
            // instruction that is wrong a rep later.
            listOf(lines.first())
        } else {
            emptyList()
        }
        if (kept.isNotEmpty()) lastSpokenMs = nowMs
        return kept
    }

    companion object {
        const val MIN_GAP_MS = 1_500L
        const val PLACEMENT_REPEAT_MS = 12_000L
    }
}
