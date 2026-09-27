package com.pushuprpg.core.progression

import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind

/** Where on the cat a thing goes. One of each at a time. */
enum class WearSlot { HEAD, FACE, NECK, SCENE }

/** Something 고냥이 can wear, or a place it can sit. */
enum class CatItem(val slot: WearSlot) {
    BELL(WearSlot.NECK),
    RIBBON(WearSlot.HEAD),
    FLOWER(WearSlot.HEAD),
    CITY(WearSlot.SCENE),
    BEANIE(WearSlot.HEAD),
    STRAW_HAT(WearSlot.HEAD),
    SCARF(WearSlot.NECK),
    BOW_TIE(WearSlot.NECK),
    GLASSES(WearSlot.FACE),
    SUNGLASSES(WearSlot.FACE),
    MOUNTAIN(WearSlot.SCENE),
    PARTY_HAT(WearSlot.HEAD),
    CROWN(WearSlot.HEAD),
    SNOW(WearSlot.SCENE),
    SPACE(WearSlot.SCENE),
}

/**
 * A gift the cat finds, and the thing it brings.
 *
 * Each one is earned by something that only ever grows — runs banked, the best set, the longest
 * streak, returns, records broken, the height climbed — so a gift is never taken back, and nothing
 * a bad week does can cost one. They are given as the cat's finds, not listed as tasks: a reward
 * promised for doing something is one people come to do it for, and stop when it stops; one that
 * arrives as a surprise adds to why they came. The owner chose this over coins and a shop.
 */
enum class Gift(
    val item: CatItem,
    val kind: Kind,
    /** The count a [Kind.SET], [Kind.STREAK] or [Kind.RECORDS] gift waits for. */
    val count: Int = 0,
    /** The place a [Kind.CLIMB] gift waits for. */
    val place: Landmark? = null,
) {
    /** The first run of any kind, the tutorial's included. */
    FIRST_RUN(CatItem.BELL, Kind.FIRST_RUN),
    SET_10(CatItem.RIBBON, Kind.SET, count = 10),
    STREAK_3(CatItem.FLOWER, Kind.STREAK, count = 3),
    CLIMB_SIXTY_THREE(CatItem.CITY, Kind.CLIMB, place = Landmark.SIXTY_THREE),
    SET_20(CatItem.BEANIE, Kind.SET, count = 20),
    /** Back after [Gifts.COMEBACK_DAYS] days or more away: the return is what is celebrated. */
    COMEBACK(CatItem.STRAW_HAT, Kind.COMEBACK),
    /** A 고냥이 session played to its last life. */
    FULL_SESSION(CatItem.SCARF, Kind.FULL_SESSION),
    STREAK_7(CatItem.BOW_TIE, Kind.STREAK, count = 7),
    RECORDS_3(CatItem.GLASSES, Kind.RECORDS, count = 3),
    SET_30(CatItem.SUNGLASSES, Kind.SET, count = 30),
    CLIMB_HALLASAN(CatItem.MOUNTAIN, Kind.CLIMB, place = Landmark.HALLASAN),
    STREAK_30(CatItem.PARTY_HAT, Kind.STREAK, count = 30),
    SET_50(CatItem.CROWN, Kind.SET, count = 50),
    CLIMB_EVEREST(CatItem.SNOW, Kind.CLIMB, place = Landmark.EVEREST),
    CLIMB_SPACE(CatItem.SPACE, Kind.CLIMB, place = Landmark.SPACE),
    ;

    /** What a gift is found for. */
    enum class Kind {
        FIRST_RUN,
        /** A best set of [count] of a counted movement. */
        SET,
        /** A longest streak of [count] days. */
        STREAK,
        COMEBACK,
        FULL_SESSION,
        /** [count] records broken, all told. */
        RECORDS,
        /** The climb past [place]. */
        CLIMB,
    }
}

object Gifts {

    /** Days away that make coming back a comeback. The window where a nudge helps most is three or four. */
    const val COMEBACK_DAYS = 3

    /** Every gift earned by [facts] and the longest streak ever, [bestStreak]. */
    fun earned(facts: List<SessionFacts>, bestStreak: Int): Set<Gift> {
        // The best set of any counted movement. A hold has no set: what its row keeps there is not
        // reps, and ten seconds of plank is not ten pushups.
        val bestSet = facts.filter { Exercises.of(it.exercise).kind != MovementKind.HOLD }.maxOfOrNull { it.bestSet } ?: 0
        val meters = Climb.meters(facts)
        val records = Records.broken(facts).size
        return Gift.entries.filterTo(mutableSetOf()) { gift ->
            when (gift.kind) {
                Gift.Kind.FIRST_RUN -> facts.any { it.reps > 0 || it.durationMs >= 1_000L }
                Gift.Kind.SET -> bestSet >= gift.count
                Gift.Kind.STREAK -> bestStreak >= gift.count
                Gift.Kind.COMEBACK -> comebacks(facts) > 0
                Gift.Kind.FULL_SESSION -> facts.any { it.fullSession }
                Gift.Kind.RECORDS -> records >= gift.count
                Gift.Kind.CLIMB -> gift.place != null && meters >= gift.place.meters
            }
        }
    }

    /** The things [gifts] brought. */
    fun items(gifts: Set<Gift>): Set<CatItem> = gifts.mapTo(mutableSetOf()) { it.item }

    /** How many times someone came back after [COMEBACK_DAYS] days or more without a run. */
    fun comebacks(facts: List<SessionFacts>): Int =
        facts.map { it.epochDay }.distinct().sorted().zipWithNext().count { (a, b) -> b - a >= COMEBACK_DAYS }

    /**
     * What to wear after [found] arrive: each goes on in its slot if the slot is empty, so the cat
     * shows up wearing what it found. Something the user chose to wear is never taken off for it.
     */
    fun wearNew(wearing: Set<CatItem>, found: List<CatItem>): Set<CatItem> {
        val out = wearing.toMutableSet()
        for (item in found) {
            if (out.none { it.slot == item.slot }) out += item
        }
        return out
    }

    /** [wearing] with [item] put on, and whatever was in its slot taken off. */
    fun wear(wearing: Set<CatItem>, item: CatItem): Set<CatItem> =
        wearing.filterTo(mutableSetOf()) { it.slot != item.slot } + item

    /** [wearing] with nothing in [slot]. */
    fun takeOff(wearing: Set<CatItem>, slot: WearSlot): Set<CatItem> =
        wearing.filterTo(mutableSetOf()) { it.slot != slot }
}
