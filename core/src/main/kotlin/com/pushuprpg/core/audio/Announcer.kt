package com.pushuprpg.core.audio

import com.pushuprpg.core.detect.PlacementAdvice
import com.pushuprpg.core.survival.CatSpeech

/** How a line is spoken: [COACH] plain, [CAT] as 고양이. */
enum class VoiceStyle { COACH, CAT }

/** One thing to say out loud. Exactly one of [placement] or [cat] is set; the app turns it into words. */
data class Announcement(
    val style: VoiceStyle,
    val placement: PlacementAdvice? = null,
    val cat: CatSpeech? = null,
)

/**
 * Decides what the game says aloud, and when.
 *
 * The phone is on the floor two metres away during a set, and a line on the screen is not
 * something anyone reads from there. So the cat's lines are spoken, and so is where to move when
 * the camera loses the user. Everything else stays on screen.
 *
 * Rules, because a voice that talks over itself is worse than none:
 * - A line waits for a gap of [MIN_GAP_MS] since the last one, or is dropped — a stale instruction
 *   is noise.
 * - The same placement advice is not repeated within [PLACEMENT_REPEAT_MS]: said once, the banner
 *   carries it.
 *
 * Pure like the rest of `:core`: time is a parameter.
 */
class Announcer {

    private var lastSpokenMs = Long.MIN_VALUE
    private var lastPlacement: PlacementAdvice? = null
    private val placementSaidAt = HashMap<PlacementAdvice, Long>()
    private var lastCat: CatSpeech? = null

    /** Folds in one frame: the cat's newest line, and where to move. [nowMs] is the frame's timestamp. */
    fun survival(
        cat: CatSpeech?,
        placement: PlacementAdvice?,
        nowMs: Long,
    ): List<Announcement> {
        val out = ArrayList<Announcement>()
        if (cat != null && cat != lastCat) out += Announcement(VoiceStyle.CAT, cat = cat)
        lastCat = cat
        placementLine(placement, nowMs)?.let { out += it }
        return gate(out, nowMs)
    }

    fun reset() {
        lastSpokenMs = Long.MIN_VALUE
        lastPlacement = null
        placementSaidAt.clear()
        lastCat = null
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
        val kept = if (lastSpokenMs == Long.MIN_VALUE || nowMs - lastSpokenMs >= MIN_GAP_MS) {
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
