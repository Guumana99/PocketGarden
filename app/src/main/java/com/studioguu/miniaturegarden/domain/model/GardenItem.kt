package com.studioguu.miniaturegarden.domain.model

/** The shop's shelves. */
enum class ItemCategory {
    /** Seeds and seedlings. */
    SEED,

    /** Buildings: things with a footprint larger than one tile. */
    STRUCTURE,

    /** Decorations and terrain tiles. */
    DECORATION,
}

/**
 * The master list of the shop: everything that can be bought, sit in an inventory slot and be
 * put on the garden. A new item is added here and nowhere else; the shop's shelves, the
 * inventory and the placement rules all read from this list.
 *
 * At a glance (coins; a plant's own numbers live in [PlantSpecies]):
 *
 *     shelf        item         unlock        price  what it is for
 *     crops        tulip        from start      20   50 coins + 50 XP after 48 h
 *                  sunflower    Lv.2            35   90 coins + 80 XP after 72 h, dries out fast
 *                  rose         Lv.4            60   160 coins + 140 XP after 120 h
 *     structures   fence        from start      15   joins its neighbours; 20 s to build
 *                  well         Lv.3            80   30 XP; soil round it dries half as fast
 *                  hut          Lv.5           150   60 XP; plants round it grow 10 % faster
 *                  cottage      Lv.6           300   100 XP; symbol building (one per garden, 3x3); lets the garden grow
 *     decorations  grass        from start      10   ground cover
 *                  pebble       from start      10   ground cover
 *                  cobblestone  from start      10   paving; things can stand on it
 *                  river tile   from start      10   terrain; nothing can stand on it
 *                  flowers      from start      15   ground cover
 *                  wooden deck  2 book pages    12   paving; things can stand on it
 *                  rock         Lv.2            30   ornament
 *                  bench        Lv.2            40   ornament
 *                  lantern      4 book pages    60   ornament
 *
 * How the prices hang together:
 * - A new player has 100 coins and one tulip seed, from the starter crate. Everything "from start" costs 20 or less,
 *   so the first session can already shape the garden, and two tulip harvests buy anything on
 *   the first tier several times over.
 * - Each plant earns more per hour than the one before it, which is what makes the next level
 *   worth reaching.
 * - Things that only look nice get dearer as they get later; the two that are opened by the
 *   encyclopedia instead of by level reward the player who places and registers a little of
 *   everything, and are within reach of a first-day garden (the pages of the from-start items
 *   are enough to open both).
 * - The well and the hut are the only purchases that pay for themselves, through the plants
 *   round them; they are the dearest and come last.
 *
 * @property itemId value stored in an inventory slot.
 * @property price shop price in coins.
 * @property objectId the grid object created when the item is placed on a tile. For seeds this
 *   is a [PlantSpecies] id, which is where the plant's own balance values live.
 * @property unlock what the player needs before the shop sells this. Items already in the
 *   inventory can always be placed.
 * @property placeXp experience earned for placing it; seeds pay out at harvest instead.
 * @property buildSeconds how long it takes to build once placed; 0 for things that are simply
 *   put down. Until then it stands on the garden as a work site.
 */
enum class GardenItem(
    val itemId: String,
    val price: Int,
    val objectId: String,
    val category: ItemCategory,
    val unlock: UnlockCondition = UnlockCondition.None,
    val placeXp: Int = 0,
    val buildSeconds: Int = 0,
) {
    SEED_TULIP("seed_tulip", price = 20, objectId = PlantSpecies.TULIP.objectId, category = ItemCategory.SEED),
    SEED_SUNFLOWER(
        "seed_sunflower", price = 35, objectId = PlantSpecies.SUNFLOWER.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(2),
    ),
    SEED_ROSE(
        "seed_rose", price = 60, objectId = PlantSpecies.ROSE.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(4),
    ),
    DECO_ROCK(
        "deco_rock", price = 30, objectId = "deco_rock", category = ItemCategory.DECORATION,
        unlock = UnlockCondition.PlayerLevel(2),
    ),
    STRUCT_HUT(
        "struct_hut", price = 150, objectId = StructureData.HUT.objectId, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(5), placeXp = 60, buildSeconds = 300,
    ),
    STRUCT_WELL(
        "struct_well", price = 80, objectId = StructureData.WELL.objectId, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(3), placeXp = 30, buildSeconds = 120,
    ),
    GROUND_GRASS("ground_grass", price = 10, objectId = "ground_grass", category = ItemCategory.DECORATION),
    GROUND_PEBBLE("ground_pebble", price = 10, objectId = "ground_pebble", category = ItemCategory.DECORATION),

    /** Paving for paths. Goes under things rather than in their place. */
    FLOOR_COBBLESTONE(
        "floor_cobblestone", price = 10, objectId = GridObject.COBBLESTONE_OBJECT_ID, category = ItemCategory.DECORATION,
    ),

    /** A one-tile section of fence, for edging beds and paths. */
    STRUCT_FENCE(
        "struct_fence", price = 15, objectId = GridObject.FENCE_OBJECT_ID, category = ItemCategory.STRUCTURE,
        buildSeconds = 20,
    ),

    /** Turns the tile it is placed on into river; removing it turns the tile back into lawn. */
    TILE_RIVER("tile_river", price = 10, objectId = GridObject.RIVER_OBJECT_ID, category = ItemCategory.DECORATION),

    /** A scatter of small wild flowers. Ground cover, like the grass and the pebbles. */
    GROUND_FLOWERS("ground_flowers", price = 15, objectId = "ground_flowers", category = ItemCategory.DECORATION),

    /** Wooden decking. Paving, like the cobblestones: it goes under things. */
    FLOOR_WOOD(
        "floor_wood", price = 12, objectId = GridObject.WOOD_DECK_OBJECT_ID, category = ItemCategory.DECORATION,
        unlock = UnlockCondition.CollectionPages(2),
    ),

    /** A garden bench. */
    DECO_BENCH(
        "deco_bench", price = 40, objectId = "deco_bench", category = ItemCategory.DECORATION,
        unlock = UnlockCondition.PlayerLevel(2),
    ),

    /** A stone lantern. */
    DECO_LANTERN(
        "deco_lantern", price = 60, objectId = "deco_lantern", category = ItemCategory.DECORATION,
        unlock = UnlockCondition.CollectionPages(4),
    ),

    /** The symbol building: a cottage. One per garden; with it standing, the garden can grow. */
    STRUCT_COTTAGE(
        "struct_cottage", price = 300, objectId = StructureData.COTTAGE.objectId, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(6), placeXp = 100, buildSeconds = 600,
    );

    /** A symbol building: one per garden, see [StructureData.isSymbol]. */
    val isSymbol: Boolean get() = StructureData.isSymbol(objectId)

    fun isUnlockedFor(stats: PlayerStats): Boolean = unlock.isMetBy(stats)

    /** Taking it apart again takes half as long as building it did. */
    val demolishSeconds: Int get() = buildSeconds / 2

    /** The player level that opens this item in the shop; 1 when it is never locked. */
    val unlockLevel: Int get() = (unlock as? UnlockCondition.PlayerLevel)?.level ?: 1

    /**
     * The seasons it is at its best in (its "shun"); empty for something good all year round.
     * Only the bag's sorting reads this so far: the shop and planting do not follow the seasons
     * yet (see "四季" in docs/ARCHITECTURE.md).
     */
    val seasons: Set<Season>
        get() = when (this) {
            SEED_TULIP -> setOf(Season.SPRING)
            SEED_SUNFLOWER -> setOf(Season.SUMMER)
            SEED_ROSE -> setOf(Season.SPRING, Season.AUTUMN)
            else -> emptySet()
        }

    /**
     * How early it opens: what is for sale from the start comes first, then what a level opens
     * (lower levels first), then what the encyclopedia opens. The shop's shelves and the bag's
     * level order both go by this.
     */
    val openingRank: Int
        get() = when (val condition = unlock) {
            UnlockCondition.None -> 0
            is UnlockCondition.PlayerLevel -> condition.level
            is UnlockCondition.CollectionPages -> 100 + condition.pages
        }

    companion object {
        fun fromItemId(itemId: String): GardenItem? = entries.firstOrNull { it.itemId == itemId }

        /** The items of one shelf of the shop, in the order they are shown: soonest first, then cheapest. */
        fun shelf(category: ItemCategory): List<GardenItem> =
            entries.filter { it.category == category }.sortedWith(compareBy({ it.openingRank }, { it.price }))

        /** Items that are locked at [previousLevel] and for sale at [newLevel]. */
        fun unlockedBetween(previousLevel: Int, newLevel: Int): List<GardenItem> = entries.filter {
            !it.isUnlockedFor(PlayerStats(previousLevel, LevelTable.xpForLevel(previousLevel))) &&
                it.isUnlockedFor(PlayerStats(newLevel, LevelTable.xpForLevel(newLevel)))
        }
    }
}
