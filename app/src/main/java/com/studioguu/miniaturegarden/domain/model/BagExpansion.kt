package com.studioguu.miniaturegarden.domain.model

/**
 * How the bag grows (FB1125 4). It starts with [UserProfile.DEFAULT_INVENTORY_CAPACITY] slots, one
 * kind to a slot and up to [InventorySlot.MAX_STACK] of it. In the carpenter workshop the player
 * can buy [STEP] more slots, [PURCHASES] times at most, each dearer than the last
 * (16 -> 24 -> 32 -> 40 -> 48 -> 56). The number of purchases is not stored on its own: it is the
 * capacity itself ([UserProfile.inventoryCapacity], which the security signature already covers).
 *
 *     purchase   price   what it is for the standard player (ProgressionPaceTest, no ads)
 *     1st        1,200   Lv.6 (day ~10): 490 coins a day, 2.4 days
 *     2nd        2,000   Lv.9 (day ~23): 990 a day, 2.0 days
 *     3rd        3,000   Lv.11 (day ~32): 1,000 a day, 3.0 days
 *     4th        4,000   Lv.14 (day ~50): 1,570 a day, 2.5 days
 *     5th        5,500   Lv.17 (day ~67): 2,320 a day, 2.4 days
 *
 * They are open from [UNLOCK_LEVEL] on, the level at which the first sixteen kinds begin to run
 * short (some twenty kinds of seed, and the decorations bought and not yet put out); the price
 * paces the rest. All together 15,700 coins, less than the garden growth itself (33,000).
 */
object BagExpansion {
    /** Slots a purchase adds. */
    const val STEP = 8

    /** The level the first purchase opens at. */
    const val UNLOCK_LEVEL = 6

    /** The price of each purchase, in order. */
    val PRICES: List<Int> = listOf(1_200, 2_000, 3_000, 4_000, 5_500)

    val PURCHASES: Int = PRICES.size

    /** The largest the bag grows. */
    val MAX_CAPACITY: Int = UserProfile.DEFAULT_INVENTORY_CAPACITY + STEP * PURCHASES

    /** How many times the bag has been enlarged, going by its [capacity]. */
    fun purchasesMade(capacity: Int): Int =
        ((capacity - UserProfile.DEFAULT_INVENTORY_CAPACITY) / STEP).coerceIn(0, PURCHASES)

    /** How many purchases are left. */
    fun remaining(capacity: Int): Int = PURCHASES - purchasesMade(capacity)

    /** The price of the next purchase; null when the bag is as large as it gets. */
    fun nextPrice(capacity: Int): Int? = PRICES.getOrNull(purchasesMade(capacity))

    /** Whether a player at [playerLevel] with [coins] can enlarge a bag of [capacity] now. */
    fun check(capacity: Int, playerLevel: Int, coins: Long): ExpansionCheck {
        val price = nextPrice(capacity) ?: return ExpansionCheck.AT_MAX_SIZE
        return when {
            playerLevel < UNLOCK_LEVEL -> ExpansionCheck.NEEDS_LEVEL
            coins < price -> ExpansionCheck.NEEDS_COINS
            else -> ExpansionCheck.READY
        }
    }
}

/** The workshop offer of more bag: [check], the [price] of the next purchase and how many are [remaining]. */
data class BagOffer(val check: ExpansionCheck, val level: Int = BagExpansion.UNLOCK_LEVEL, val price: Int = 0, val remaining: Int = 0) {
    companion object {
        fun of(capacity: Int, playerLevel: Int, coins: Long) = BagOffer(
            check = BagExpansion.check(capacity, playerLevel, coins),
            price = BagExpansion.nextPrice(capacity) ?: 0,
            remaining = BagExpansion.remaining(capacity),
        )
    }
}
