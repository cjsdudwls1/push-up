package com.pushuprpg.core.progression

import com.pushuprpg.core.detect.Exercises
import com.pushuprpg.core.detect.MovementKind

/** Where on the cat a thing goes. One of each at a time. */
enum class WearSlot { HEAD, FACE, NECK, SCENE }

/** Something 고양이 can wear, or a place it can sit. */
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

    // Sold for 츄르 rather than found: see [Shop]. Never a gift, so nothing found is ever for sale.
    HEADBAND(WearSlot.HEAD),
    BANDANA(WearSlot.NECK),
    HEART_GLASSES(WearSlot.FACE),
    CHEF_HAT(WearSlot.HEAD),
    MEDAL(WearSlot.NECK),
    WITCH_HAT(WearSlot.HEAD),
    BEACH(WearSlot.SCENE),
    GYM(WearSlot.SCENE),
}

/**
 * A gift the cat finds, and the thing it brings.
 *
 * Each one is earned by something that only ever grows — runs banked, the best set, the longest
 * streak, returns, records broken, the calories burned — so a gift is never taken back, and nothing
 * a bad week does can cost one. They are given as the cat's finds, not listed as tasks: a reward
 * promised for doing something is one people come to do it for, and stop when it stops; one that
 * arrives as a surprise adds to why they came. They stay that way beside the [Shop], which the owner
 * added later (2026-10-01): what is bought is bought with 츄르 from records broken, and never a gift.
 */
enum class Gift(
    val item: CatItem,
    val kind: Kind,
    /** The count a [Kind.SET], [Kind.STREAK] or [Kind.RECORDS] gift waits for. */
    val count: Int = 0,
    /** The food a [Kind.BURN] gift waits for: the calories burned in all, as much as it holds. */
    val food: Food? = null,
) {
    /** The first run of any kind, the tutorial's included. */
    FIRST_RUN(CatItem.BELL, Kind.FIRST_RUN),
    SET_10(CatItem.RIBBON, Kind.SET, count = 10),
    STREAK_3(CatItem.FLOWER, Kind.STREAK, count = 3),
    BURN_RICE(CatItem.CITY, Kind.BURN, food = Food.RICE),
    SET_20(CatItem.BEANIE, Kind.SET, count = 20),
    /** Back after [Gifts.COMEBACK_DAYS] days or more away: the return is what is celebrated. */
    COMEBACK(CatItem.STRAW_HAT, Kind.COMEBACK),
    /** A 고양이 session played to its last life. */
    FULL_SESSION(CatItem.SCARF, Kind.FULL_SESSION),
    STREAK_7(CatItem.BOW_TIE, Kind.STREAK, count = 7),
    RECORDS_3(CatItem.GLASSES, Kind.RECORDS, count = 3),
    SET_30(CatItem.SUNGLASSES, Kind.SET, count = 30),
    BURN_CHICKEN(CatItem.MOUNTAIN, Kind.BURN, food = Food.CHICKEN),
    STREAK_30(CatItem.PARTY_HAT, Kind.STREAK, count = 30),
    SET_50(CatItem.CROWN, Kind.SET, count = 50),
    BURN_FIVE_CHICKENS(CatItem.SNOW, Kind.BURN, food = Food.FIVE_CHICKENS),
    BURN_RICE_SACK(CatItem.SPACE, Kind.BURN, food = Food.RICE_SACK),
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
        /** As many calories burned, in all, as [food] holds. */
        BURN,
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
        val kcal = Calories.of(facts)
        val records = Records.broken(facts).size
        return Gift.entries.filterTo(mutableSetOf()) { gift ->
            when (gift.kind) {
                Gift.Kind.FIRST_RUN -> facts.any { it.reps > 0 || it.durationMs >= 1_000L }
                Gift.Kind.SET -> bestSet >= gift.count
                Gift.Kind.STREAK -> bestStreak >= gift.count
                Gift.Kind.COMEBACK -> comebacks(facts) > 0
                Gift.Kind.FULL_SESSION -> facts.any { it.fullSession }
                Gift.Kind.RECORDS -> records >= gift.count
                Gift.Kind.BURN -> gift.food != null && kcal >= gift.food.kcal
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
