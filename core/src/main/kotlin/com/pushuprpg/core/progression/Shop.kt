package com.pushuprpg.core.progression

/**
 * What 츄르 buys: things for the cat that no gift brings, by the owner's decision (2026-10-01).
 *
 * 츄르 comes only from sets played against their own best ([SetOutcome.churu]): a first session pays
 * about ten, a set record three. The prices are set against that — the cheapest is a first session,
 * the dearest a few weeks of records — and ordered cheapest first, which is how the shelf is shown.
 * Bought is kept: nothing is ever taken back, and 츄르 is never lost but by spending it.
 */
enum class ShopItem(val item: CatItem, val price: Int) {
    HEADBAND(CatItem.HEADBAND, 5),
    BANDANA(CatItem.BANDANA, 8),
    HEART_GLASSES(CatItem.HEART_GLASSES, 12),
    CHEF_HAT(CatItem.CHEF_HAT, 15),
    MEDAL(CatItem.MEDAL, 20),
    WITCH_HAT(CatItem.WITCH_HAT, 25),
    BEACH(CatItem.BEACH, 30),
    GYM(CatItem.GYM, 40),
}

/** A purse after a purchase. */
data class Purse(val churu: Int, val owned: Set<CatItem>)

object Shop {

    fun of(item: CatItem): ShopItem? = ShopItem.entries.firstOrNull { it.item == item }

    /** Whether [item] can be bought from [purse]: not owned already, and paid for in full. */
    fun canBuy(purse: Purse, item: ShopItem): Boolean = item.item !in purse.owned && purse.churu >= item.price

    /** [purse] after buying [item], or null when it cannot be bought. */
    fun buy(purse: Purse, item: ShopItem): Purse? =
        if (!canBuy(purse, item)) null else Purse(purse.churu - item.price, purse.owned + item.item)
}
