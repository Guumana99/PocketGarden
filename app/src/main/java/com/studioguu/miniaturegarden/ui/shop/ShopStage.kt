package com.studioguu.miniaturegarden.ui.shop

import com.studioguu.miniaturegarden.domain.model.ExpansionCheck
import com.studioguu.miniaturegarden.domain.model.GardenExpansion
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.ItemCategory
import com.studioguu.miniaturegarden.domain.model.PlayerStats
import com.studioguu.miniaturegarden.domain.model.PracticeGarden
import com.studioguu.miniaturegarden.domain.model.Season

/**
 * The shops down the lane from the garden. Each sells one shelf of the item master
 * ([GardenItem.shelf]) and has a look of its own; the player walks between them on the
 * signboards at the top of the shop.
 *
 * The seed shop is where the lane starts: the garden's shop button opens it.
 */
enum class ShopStage(val category: ItemCategory) {
    /** Seeds and seedlings, what is in season. */
    SEEDS(ItemCategory.SEED),

    /**
     * The carpenter's workshop: fences, the well, the windmill, the hut, the cottage, the
     * greenhouse; and where the garden is grown ([ExpansionOffer]).
     */
    WORKSHOP(ItemCategory.STRUCTURE),

    /** The garden sundries: lawn, pebbles, paving, the river, benches and lanterns. */
    SUNDRIES(ItemCategory.DECORATION),
    ;

    companion object {
        /** Where the garden's shop button leads. */
        val ENTRANCE = SEEDS
    }
}

/** How a thing on the shelf stands for the player right now. */
enum class ShelfTag {
    /** On sale, and the player has the coins. */
    FOR_SALE,

    /** On sale, but the player is short of coins; tapping "buy" says so and offers the bonus. */
    SHORT_OF_COINS,

    /** Not opened yet (level or encyclopedia pages); on show so the player sees what is coming. */
    LOCKED,

    /** One of a kind, and already the player's (the symbol building): on show, not for sale. */
    OWNED,
}

/** One thing standing on a shelf, and how many of it the player has in the bag ([held]). */
data class ShelfItem(val item: GardenItem, val tag: ShelfTag, val held: Int = 0, val free: Boolean = false)

/**
 * What stands on a shop's shelves, and how it is set out: a pure function of the shop, the
 * season, the player and their coins, so the stage can be drawn from it and tested without it.
 */
object ShopShelves {

    /** Things side by side on one shelf: few enough that each is a good finger's width. */
    const val PER_SHELF = 3

    /**
     * Everything on show in [stage], in shelf order, each tagged for the player and with how many
     * of it they have in the bag ([held], see [heldIn]).
     */
    fun stock(
        stage: ShopStage,
        season: Season,
        stats: PlayerStats,
        coins: Long,
        ownsSymbol: Boolean,
        held: Map<GardenItem, Int> = emptyMap(),
    ): List<ShelfItem> =
        GardenItem.shelf(stage.category, season).map { item -> shelfItem(item, stats, coins, ownsSymbol, held) }

    /** The kinds of rock ([GardenItem.ROCK_KINDS]), each tagged like anything on a shelf. */
    fun rockKinds(stats: PlayerStats, coins: Long, held: Map<GardenItem, Int> = emptyMap()): List<ShelfItem> =
        GardenItem.ROCK_KINDS.map { shelfItem(it, stats, coins, ownsSymbol = false, held) }

    /** The kinds of fence ([GardenItem.FENCE_KINDS]), tagged the same way. */
    fun fenceKinds(stats: PlayerStats, coins: Long, held: Map<GardenItem, Int> = emptyMap()): List<ShelfItem> =
        GardenItem.FENCE_KINDS.map { shelfItem(it, stats, coins, ownsSymbol = false, held) }

    private fun shelfItem(item: GardenItem, stats: PlayerStats, coins: Long, ownsSymbol: Boolean, held: Map<GardenItem, Int>): ShelfItem {
        val tag = when {
            !item.isUnlockedFor(stats) -> ShelfTag.LOCKED
            item.isSymbol && ownsSymbol -> ShelfTag.OWNED
            coins < item.price -> ShelfTag.SHORT_OF_COINS
            else -> ShelfTag.FOR_SALE
        }
        return ShelfItem(item, tag, held[item] ?: 0)
    }

    /** What is on show during the first steps: the seed they give away, and nothing else. */
    fun firstSteps(shelves: List<List<ShelfItem>>): List<List<ShelfItem>> =
        shelves.flatten().filter { it.item == PracticeGarden.SEED }.map { it.copy(tag = ShelfTag.FOR_SALE, free = true) }
            .let { if (it.isEmpty()) emptyList() else listOf(it) }

    /** How many of each item are in the bag, over all its slots. */
    fun heldIn(slots: List<InventorySlot>): Map<GardenItem, Int> =
        slots.filter { !it.isEmpty }
            .mapNotNull { slot -> GardenItem.fromItemId(slot.itemId)?.let { it to slot.amount } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.sum() }

    /** [stock] laid out shelf by shelf, top to bottom. */
    fun shelves(stock: List<ShelfItem>): List<List<ShelfItem>> = stock.chunked(PER_SHELF)

    /** The shop that sells [item]. */
    fun stageOf(item: GardenItem): ShopStage = ShopStage.entries.first { it.category == item.category }
}

/**
 * Growing the garden, as the workshop offers it: [check] says whether it can be done now, and
 * [level] and [price] are those of the next size (0 when there is none). The size itself is
 * never shown to the player.
 */
data class ExpansionOffer(val check: ExpansionCheck, val level: Int = 0, val price: Int = 0) {
    companion object {
        /** The offer for a garden of [gardenSize] kept by a player at [playerLevel] with [coins]. */
        fun of(gardenSize: Int, playerLevel: Int, coins: Long): ExpansionOffer {
            val stage = GardenExpansion.nextStage(gardenSize) ?: return ExpansionOffer(ExpansionCheck.AT_MAX_SIZE)
            return ExpansionOffer(GardenExpansion.check(gardenSize, playerLevel, coins), stage.playerLevel, stage.price)
        }
    }
}
