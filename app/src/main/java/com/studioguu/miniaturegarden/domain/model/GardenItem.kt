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
 *                  cosmos       from start      20   64 coins + 60 XP after 60 h (autumn only)
 *                  snowdrop     from start      20   58 coins + 55 XP after 54 h (winter only)
 *                  mint         from start      15   35 coins + 35 XP after 36 h (any season)
 *                  tomato       from start      20   54 coins + 52 XP after 48 h (summer only), dries out fast
 *                  strawberry   from start      20   48 coins + 44 XP after 42 h (spring only)
 *                  watermelon   Lv.3            45   122 coins + 110 XP after 96 h (summer only), dries out fast
 *                  lavender     Lv.3            40   108 coins + 96 XP after 84 h (any season)
 *                  pumpkin      Lv.5            70   190 coins + 160 XP after 132 h (autumn only)
 *                  poinsettia   Lv.5            70   180 coins + 150 XP after 120 h (winter only)
 *     structures   fence        from start      15   joins its neighbours; 20 s to build
 *                  well         Lv.3            80   30 XP; soil round it dries half as fast
 *                  hut          Lv.5           150   60 XP; plants round it grow 10 % faster
 *                  cottage      Lv.6           300   100 XP; symbol building (one per garden, 3x3); lets the garden grow
 *                  greenhouse   Lv.18          300   40 XP; one cell of soil under glass, sown in any season
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
 * Every crop but mint and lavender has its season and is only on sale then ([seasons]); every
 * season has a crop of its own open from the start (summer's is the tomato), and mint grows in all.
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
    ),

    /** Autumn's flower. */
    SEED_COSMOS("seed_cosmos", price = 20, objectId = PlantSpecies.COSMOS.objectId, category = ItemCategory.SEED),

    /** Winter's flower, so that there is always something to sow. */
    SEED_SNOWDROP("seed_snowdrop", price = 20, objectId = PlantSpecies.SNOWDROP.objectId, category = ItemCategory.SEED),

    /** A herb for every season. */
    SEED_MINT("seed_mint", price = 15, objectId = PlantSpecies.MINT.objectId, category = ItemCategory.SEED),

    /** Summer's crop from the start, so that the first summer has something of its own to sow. */
    SEED_TOMATO("seed_tomato", price = 20, objectId = PlantSpecies.TOMATO.objectId, category = ItemCategory.SEED),

    /** Spring's fruit. */
    SEED_STRAWBERRY("seed_strawberry", price = 20, objectId = PlantSpecies.STRAWBERRY.objectId, category = ItemCategory.SEED),

    /** Summer's big fruit. */
    SEED_WATERMELON(
        "seed_watermelon", price = 45, objectId = PlantSpecies.WATERMELON.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(3),
    ),

    /** A herb for every season, a step up from mint. */
    SEED_LAVENDER(
        "seed_lavender", price = 40, objectId = PlantSpecies.LAVENDER.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(3),
    ),

    /** Autumn's big crop. */
    SEED_PUMPKIN(
        "seed_pumpkin", price = 70, objectId = PlantSpecies.PUMPKIN.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(5),
    ),

    /** Winter's red. */
    SEED_POINSETTIA(
        "seed_poinsettia", price = 70, objectId = PlantSpecies.POINSETTIA.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(5),
    ),

    /**
     * A one-cell glass greenhouse over a bed of soil. Seeds go into it as into tilled soil, and
     * whatever the season. Paving-like: it lies in the cell's floor layer.
     */
    FLOOR_GREENHOUSE(
        "floor_greenhouse", price = 300, objectId = GridObject.GREENHOUSE_OBJECT_ID, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(18), placeXp = 40,
    );

    /** A symbol building: one per garden, see [StructureData.isSymbol]. */
    val isSymbol: Boolean get() = StructureData.isSymbol(objectId)

    fun isUnlockedFor(stats: PlayerStats): Boolean = unlock.isMetBy(stats)

    /** Taking it apart again takes half as long as building it did. */
    val demolishSeconds: Int get() = buildSeconds / 2

    /** The player level that opens this item in the shop; 1 when it is never locked. */
    val unlockLevel: Int get() = (unlock as? UnlockCondition.PlayerLevel)?.level ?: 1

    /**
     * The seasons it is at its best in (its "shun"); empty for something good all year round,
     * which is everything but the seasonal crops. The shop only sells a seasonal crop in its
     * season, and outside a greenhouse it can only be sown then.
     */
    val seasons: Set<Season>
        get() = when (this) {
            SEED_TULIP -> setOf(Season.SPRING)
            SEED_SUNFLOWER -> setOf(Season.SUMMER)
            SEED_ROSE -> setOf(Season.SPRING, Season.AUTUMN)
            SEED_COSMOS -> setOf(Season.AUTUMN)
            SEED_SNOWDROP -> setOf(Season.WINTER)
            SEED_TOMATO -> setOf(Season.SUMMER)
            SEED_STRAWBERRY -> setOf(Season.SPRING)
            SEED_WATERMELON -> setOf(Season.SUMMER)
            SEED_PUMPKIN -> setOf(Season.AUTUMN)
            SEED_POINSETTIA -> setOf(Season.WINTER)
            else -> emptySet()
        }

    /** Whether it is good all year round. */
    val isAllSeasons: Boolean get() = seasons.isEmpty()

    /** Whether [season] is a season for it: one of its own, or any for something good all year. */
    fun isInSeason(season: Season): Boolean = isAllSeasons || season in seasons

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

        /**
         * The items of one shelf of the shop, in the order they are shown: soonest first, then
         * cheapest. With a [season], only what is in season is on it (see [isInSeason]).
         */
        fun shelf(category: ItemCategory, season: Season? = null): List<GardenItem> =
            entries.filter { it.category == category && (season == null || it.isInSeason(season)) }
                .sortedWith(compareBy({ it.openingRank }, { it.price }))

        /** Items that are locked at [previousLevel] and for sale at [newLevel]. */
        fun unlockedBetween(previousLevel: Int, newLevel: Int): List<GardenItem> = entries.filter {
            !it.isUnlockedFor(PlayerStats(previousLevel, LevelTable.xpForLevel(previousLevel))) &&
                it.isUnlockedFor(PlayerStats(newLevel, LevelTable.xpForLevel(newLevel)))
        }
    }
}
