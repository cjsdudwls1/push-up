package com.pushuprpg.core.progression

/**
 * What 츄르 buys: things for the cat that no gift brings, by the owner's decision (2026-10-01).
 *
 * 츄르 comes only from sets played against their own best ([SetOutcome.churu]): a first session pays
 * about ten, a set record three. The prices are set against that — the cheapest is under half a first
 * session, the dearest a few weeks of records, and the whole shelf (379) months of them — and ordered
 * cheapest first, which is how the shelf is shown. Twenty things, from eight, since the owner asked for
 * more than twice as much (2026-10-03). Bought is kept: nothing is ever taken back, and 츄르 is never
 * lost but by spending it.
 */
enum class ShopItem(val item: CatItem, val price: Int) {
    BALL(CatItem.BALL, 4),
    HEADBAND(CatItem.HEADBAND, 5),
    MUSTACHE(CatItem.MUSTACHE, 6),
    BANDANA(CatItem.BANDANA, 8),
    MOUSE_TOY(CatItem.MOUSE_TOY, 9),
    SPORTS_TOWEL(CatItem.SPORTS_TOWEL, 10),
    HEART_GLASSES(CatItem.HEART_GLASSES, 12),
    LEI(CatItem.LEI, 12),
    BOX(CatItem.BOX, 14),
    CHEF_HAT(CatItem.CHEF_HAT, 15),
    STAR_GLASSES(CatItem.STAR_GLASSES, 16),
    FROG_HAT(CatItem.FROG_HAT, 18),
    MEDAL(CatItem.MEDAL, 20),
    HEADPHONES(CatItem.HEADPHONES, 22),
    WITCH_HAT(CatItem.WITCH_HAT, 25),
    PARK(CatItem.PARK, 28),
    BEACH(CatItem.BEACH, 30),
    CAT_TOWER(CatItem.CAT_TOWER, 35),
    GYM(CatItem.GYM, 40),
    STAGE(CatItem.STAGE, 50),
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
