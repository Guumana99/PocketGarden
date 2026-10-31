package com.studioguu.miniaturegarden.domain.model

/**
 * The groups the bag sorts its items into, in the order they are shown when sorted by kind.
 * Finer than the shop's shelves ([ItemCategory]): the decorations are split into furniture,
 * greenery and ground.
 */
enum class InventoryGroup {
    /** Seeds and seedlings: flowers now, vegetables and fruit later. */
    CROPS,

    /** Furniture and buildings: things that stand on the garden. */
    FURNITURE,

    /** Herbs and greenery: living ground cover, and herbs grown from seed. */
    GREENERY,

    /** Soil and tiles: paving, stones and water laid on the ground. */
    GROUND;

    companion object {
        fun of(item: GardenItem): InventoryGroup = when (item) {
            GardenItem.SEED_TULIP, GardenItem.SEED_SUNFLOWER, GardenItem.SEED_ROSE,
            GardenItem.SEED_COSMOS, GardenItem.SEED_SNOWDROP, GardenItem.SEED_TOMATO, GardenItem.SEED_STRAWBERRY,
            GardenItem.SEED_WATERMELON, GardenItem.SEED_SHIITAKE, GardenItem.SEED_POINSETTIA,
            GardenItem.SEED_PAPRIKA, GardenItem.SEED_DAIKON, GardenItem.SEED_CARROT, GardenItem.SEED_EGGPLANT,
            GardenItem.SEED_WHEAT, GardenItem.SEED_CORN, GardenItem.SEED_HAKUSAI, GardenItem.SEED_NANOHANA -> CROPS
            GardenItem.STRUCT_HUT, GardenItem.STRUCT_WELL, GardenItem.STRUCT_FENCE, GardenItem.STRUCT_COTTAGE, GardenItem.STRUCT_WINDMILL,
            GardenItem.DECO_ROCK, GardenItem.DECO_BENCH, GardenItem.DECO_LANTERN, GardenItem.DECO_SPRINKLER, GardenItem.FLOOR_GREENHOUSE,
            GardenItem.DECO_WEATHERVANE -> FURNITURE
            GardenItem.GROUND_GRASS, GardenItem.GROUND_FLOWERS, GardenItem.SEED_MINT, GardenItem.SEED_LAVENDER -> GREENERY
            GardenItem.GROUND_PEBBLE, GardenItem.FLOOR_COBBLESTONE, GardenItem.FLOOR_WOOD, GardenItem.TILE_RIVER, GardenItem.DECO_POND -> GROUND
        }
    }
}

/** The tabs along the top of the bag: which items it shows. */
enum class InventoryFilter {
    ALL,

    /** Seeds and seedlings, herbs included. */
    SEEDS,

    /** Furniture and buildings. */
    FURNITURE,

    /** Everything else: greenery and ground. */
    OTHER;

    fun matches(item: GardenItem): Boolean = when (this) {
        ALL -> true
        SEEDS -> item.category == ItemCategory.SEED
        FURNITURE -> InventoryGroup.of(item) == InventoryGroup.FURNITURE
        OTHER -> item.category != ItemCategory.SEED && InventoryGroup.of(item).let { it == InventoryGroup.GREENERY || it == InventoryGroup.GROUND }
    }
}

/** The orders the bag can be sorted in. Stored by name in the settings. */
enum class InventorySort {
    /** What came into the bag last comes first. The default. */
    ACQUIRED,

    /** By [InventoryGroup]: crops, furniture, greenery, ground. */
    KIND,

    /** The biggest stacks first. */
    AMOUNT,

    /** What is in season now first, then what grows all year, then the rest by how soon their season comes. */
    SEASON,

    /** What opens earliest first: from the start, then by player level, then what the encyclopedia opens. */
    LEVEL;

    companion object {
        val DEFAULT = ACQUIRED

        /** Unknown values (e.g. written by a newer app version) fall back to [DEFAULT]. */
        fun fromName(name: String?): InventorySort = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Puts the bag in order. Pure: the screen hands in the slots, the tab, the sort and the season,
 * and shows what comes back.
 */
object InventoryOrder {

    /**
     * The filled slots that [filter] lets through, sorted by [sort], with the empty slots after
     * them. Under a tab other than [InventoryFilter.ALL] the slots holding other items are left
     * out but the empty ones are kept, so that the bag keeps its size whichever tab is open.
     * Ties are settled by what came last, then by slot, so the order never jumps about. Whatever
     * the sort, seeds out of their season wait at the back ([isOutOfSeason]).
     */
    fun arrange(
        slots: List<InventorySlot>,
        sort: InventorySort,
        filter: InventoryFilter = InventoryFilter.ALL,
        season: Season = Season.SPRING,
    ): List<InventorySlot> {
        val filled = slots.filter { !it.isEmpty }
            .mapNotNull { slot -> GardenItem.fromItemId(slot.itemId)?.let { slot to it } }
            .filter { (_, item) -> filter.matches(item) }
        val newestFirst = compareByDescending<Pair<InventorySlot, GardenItem>> { it.first.acquiredOrder }
            .thenBy { it.first.slotIndex }
        val order: Comparator<Pair<InventorySlot, GardenItem>> = when (sort) {
            InventorySort.ACQUIRED -> newestFirst
            InventorySort.KIND -> compareBy<Pair<InventorySlot, GardenItem>> { InventoryGroup.of(it.second) }.then(newestFirst)
            InventorySort.AMOUNT -> compareByDescending<Pair<InventorySlot, GardenItem>> { it.first.amount }.then(newestFirst)
            InventorySort.SEASON -> compareBy<Pair<InventorySlot, GardenItem>> { seasonRank(it.second, season) }.then(newestFirst)
            InventorySort.LEVEL -> compareBy<Pair<InventorySlot, GardenItem>> { it.second.openingRank }.then(newestFirst)
        }
        val resting = compareBy<Pair<InventorySlot, GardenItem>> { isOutOfSeason(it.second, season) }
        return filled.sortedWith(resting.then(order)).map { it.first } + slots.filter { it.isEmpty }.sortedBy { it.slotIndex }
    }

    /** Whether [item] is a seed that cannot be sown in [season], other than under glass. */
    fun isOutOfSeason(item: GardenItem, season: Season): Boolean =
        item.category == ItemCategory.SEED && !item.isInSeason(season)

    /**
     * The season [item] has to wait for in the bag, or null if it can be taken out now: a seed out
     * of its [season] can only go in under glass, so it waits unless the garden has a greenhouse.
     */
    fun waitsFor(item: GardenItem, season: Season, greenhouse: Boolean): Season? =
        if (isOutOfSeason(item, season) && !greenhouse) nextSeason(item, season) else null

    /** The soonest season after [now] that [item] can be sown in. */
    fun nextSeason(item: GardenItem, now: Season): Season =
        item.seasons.minByOrNull { Math.floorMod(it.ordinal - now.ordinal, Season.entries.size) } ?: now

    /**
     * 0 for an item in season, 1 for one that grows all year, then 2 and up for the other seasons:
     * the sooner its season comes round, the lower.
     */
    fun seasonRank(item: GardenItem, now: Season): Int {
        val seasons = item.seasons
        return when {
            now in seasons -> 0
            seasons.isEmpty() -> 1
            else -> 1 + seasons.minOf { Math.floorMod(it.ordinal - now.ordinal, Season.entries.size) }
        }
    }
}

/** The number for the next thing to come into the bag: higher than any so far. */
fun List<InventorySlot>.nextAcquisition(): Long = (maxOfOrNull { it.acquiredOrder } ?: 0L) + 1L
