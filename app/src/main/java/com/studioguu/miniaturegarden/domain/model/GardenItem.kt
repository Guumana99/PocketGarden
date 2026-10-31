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
 * At a glance (coins; a plant's own numbers live in [PlantSpecies]; "regrows" is each harvest
 * after the first of a crop that bears again):
 *
 *     shelf        item         unlock        price  what it is for
 *     crops        mint         from start       5   13 coins + 7 XP after 4 h (any season)
 *                  tulip        from start      10   22 coins + 10 XP after 6 h (spring)
 *                  cosmos       from start      10   22 coins + 10 XP after 10 h (autumn)
 *                  snowdrop     from start      10   22 coins + 10 XP after 10 h (winter)
 *                  strawberry   from start       5   16 coins + 14 XP after 6 h (spring); regrows 8 + 7 in 3 h
 *                  tomato       from start      10   20 coins + 15 XP after 8 h (summer); regrows 9 + 8 in 4 h
 *                  carrot       Lv.2            20   47 coins + 22 XP after 16 h (spring and autumn)
 *                  sunflower    Lv.3            20   50 coins + 22 XP after 20 h (summer)
 *                  daikon       Lv.4            20   50 coins + 23 XP after 18 h (autumn and winter)
 *                  lavender     Lv.5            20   52 coins + 24 XP after 24 h (any season)
 *                  eggplant     Lv.6            15   34 coins + 25 XP after 22 h (summer); regrows 17 + 12 in 11 h
 *                  wheat        Lv.7            35   90 coins + 39 XP after 30 h (autumn)
 *                  rose         Lv.8            40   105 coins + 40 XP after 36 h (spring and autumn)
 *                  watermelon   Lv.9            40   100 coins + 42 XP after 30 h (summer)
 *                  hakusai      Lv.10           40   105 coins + 43 XP after 30 h (winter)      not on sale yet
 *                  paprika      Lv.11           40   90 coins + 58 XP after 32 h (summer); regrows 45 + 29 in 16 h
 *                  nanohana     Lv.12           45   115 coins + 45 XP after 32 h (spring)      not on sale yet
 *                  corn         Lv.13           65   165 coins + 63 XP after 42 h (summer)
 *                  shiitake     Lv.14           45   100 coins + 65 XP after 48 h (autumn), on a log; regrows 50 + 33 in 24 h
 *                  poinsettia   Lv.15           70   175 coins + 67 XP after 40 h (winter)
 *     structures   fence        from start      15   joins its neighbours of its kind; 20 s to build
 *                  white fence  Lv.5            25   as the fence
 *                  bamboo fence Lv.8            35   as the fence
 *                  brick edging Lv.16           60   joins its neighbours; no fence, but nothing walks through it; 20 s  withdrawn
 *                  cottage      Lv.3           800   100 XP; symbol building (one per garden, 3x3); 2 h
 *                  well         Lv.5           400   30 XP; soil round it dries half as fast; 30 min to build
 *                  windmill     Lv.9         1,200   45 XP; 2x2, its sails turn; soil within two cells dries a little slower; 45 min
 *                  hut          Lv.11        2,000   60 XP; plants round it grow 10 % faster; 1 h
 *                  greenhouse   Lv.18        3,000   40 XP; one cell of soil under glass, sown in any season
 *     decorations  grass        from start      10   ground cover
 *                  pebble       from start      10   ground cover
 *                  cobblestone  from start      10   paving; things can stand on it
 *                  river tile   from start      10   terrain; nothing can stand on it
 *                  flowers      from start      15   ground cover
 *                  wooden deck  2 book pages    12   paving; things can stand on it
 *                  rock         Lv.2            60   ornament
 *                  bench        Lv.3           100   ornament
 *                  pond         Lv.4           120   water; ponds side by side are one pool
 *                  lantern      4 book pages    60   ornament
 *                  weathervane  Lv.6           500   ornament; tomorrow's weather can be read      not on sale yet
 *                  sprinkler    Lv.7           450   while on, the soil round it hardly dries
 *
 * Which of these have finished pictures and which are drawn in code for now: docs/ASSETS.md,
 * "ショップの取扱一覧". Keep the two in step.
 *
 * Every crop but mint and lavender has its season and is only on sale then ([seasons]); every
 * season has a crop of its own open from the start (summer's is the tomato), and mint grows in all.
 *
 * How the prices hang together:
 * - A new player has 100 coins and one tulip seed, from the starter crate. Everything "from start" costs 15 or less,
 *   so the first session can already shape the garden.
 * - A crop opened later earns at least as much for a bed and a day as anything sown in the same
 *   season before it, for a player who looks in three times a day (a crop that bears again is
 *   counted by its later harvests): that is what makes the next level worth reaching.
 * - Things that only look nice get dearer as they get later; the two that are opened by the
 *   encyclopedia instead of by level reward the player who places and registers a little of
 *   everything, and are within reach of a first-day garden (the pages of the from-start items
 *   are enough to open both).
 * - The well, the windmill and the hut pay for themselves, through the plants round them; they
 *   are the dearest of what stands in the garden and come late.
 *
 * How long the buildings take (#43): long enough to be something to come back to, the way a
 * crop is, and short enough to be ready within one sitting's day. A fence goes up while you watch
 * (it is laid a piece at a time); the well takes half an hour, the windmill three quarters, the hut
 * an hour, and the cottage, which opens up the garden, two hours: about a tea break to an
 * afternoon, against a crop's two days and more. Taking a building down is a chore rather than
 * something to look forward to (PHILOSOPHY 4): half its building time, never more than five minutes.
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
 * @property inShop false for something registered ahead of its pictures: it is never on a shelf
 *   or in a level-up's news until it has them (docs/ASSET_LEDGER.md).
 */
/** A minute and an hour, in seconds: for [GardenItem.buildSeconds]. */
private const val MINUTE = 60
private const val HOUR = 60 * MINUTE

/** The longest any building takes to come down: a chore, not something to wait for. */
const val MAX_DEMOLISH_SECONDS = 5 * MINUTE

enum class GardenItem(
    val itemId: String,
    val price: Int,
    val objectId: String,
    val category: ItemCategory,
    val unlock: UnlockCondition = UnlockCondition.None,
    val placeXp: Int = 0,
    val buildSeconds: Int = 0,
    val inShop: Boolean = true,
) {
    SEED_TULIP("seed_tulip", price = 10, objectId = PlantSpecies.TULIP.objectId, category = ItemCategory.SEED),
    SEED_SUNFLOWER(
        "seed_sunflower", price = 20, objectId = PlantSpecies.SUNFLOWER.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(3),
    ),
    SEED_ROSE(
        "seed_rose", price = 40, objectId = PlantSpecies.ROSE.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(8),
    ),
    DECO_ROCK(
        "deco_rock", price = 60, objectId = "deco_rock", category = ItemCategory.DECORATION,
        unlock = UnlockCondition.PlayerLevel(2),
    ),
    STRUCT_HUT(
        "struct_hut", price = 2000, objectId = StructureData.HUT.objectId, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(11), placeXp = 60, buildSeconds = HOUR,
    ),
    STRUCT_WELL(
        "struct_well", price = 400, objectId = StructureData.WELL.objectId, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(5), placeXp = 30, buildSeconds = 30 * MINUTE,
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
        "deco_bench", price = 100, objectId = "deco_bench", category = ItemCategory.DECORATION,
        unlock = UnlockCondition.PlayerLevel(3),
    ),

    /** A stone lantern. */
    DECO_LANTERN(
        "deco_lantern", price = 60, objectId = "deco_lantern", category = ItemCategory.DECORATION,
        unlock = UnlockCondition.CollectionPages(4),
    ),

    /**
     * A pond in a ring of stones and logs: a cell of still water with water lilies. Like the
     * river it takes its cell to itself ([PlacementKind.TERRAIN]); unlike the river it is one
     * finished thing, bought whole, and pays a little experience for being put down.
     */
    DECO_POND(
        "deco_pond", price = 120, objectId = GridObject.POND_OBJECT_ID, category = ItemCategory.DECORATION,
        unlock = UnlockCondition.PlayerLevel(4), placeXp = 20,
    ),

    /**
     * A sprinkler, put down among the beds: while it is on, the soil round it hardly dries
     * ([Sprinkler]). A thing in the garden, not a tool in the hand.
     */
    DECO_SPRINKLER(
        "deco_sprinkler", price = 450, objectId = Sprinkler.OBJECT_ID, category = ItemCategory.DECORATION,
        unlock = UnlockCondition.PlayerLevel(7),
    ),

    /** The symbol building: a cottage. One per garden. */
    STRUCT_COTTAGE(
        "struct_cottage", price = 800, objectId = StructureData.COTTAGE.objectId, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(3), placeXp = 100, buildSeconds = 2 * HOUR,
    ),

    /** A windmill with turning sails, between the well and the hut. */
    STRUCT_WINDMILL(
        "struct_windmill", price = 1200, objectId = StructureData.WINDMILL.objectId, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(9), placeXp = 45, buildSeconds = 45 * MINUTE,
    ),

    /** Autumn's flower. */
    SEED_COSMOS("seed_cosmos", price = 10, objectId = PlantSpecies.COSMOS.objectId, category = ItemCategory.SEED),

    /** Winter's flower, so that there is always something to sow. */
    SEED_SNOWDROP("seed_snowdrop", price = 10, objectId = PlantSpecies.SNOWDROP.objectId, category = ItemCategory.SEED),

    /** A herb for every season. */
    SEED_MINT("seed_mint", price = 5, objectId = PlantSpecies.MINT.objectId, category = ItemCategory.SEED),

    /** Summer's crop from the start, so that the first summer has something of its own to sow. */
    SEED_TOMATO("seed_tomato", price = 10, objectId = PlantSpecies.TOMATO.objectId, category = ItemCategory.SEED),

    /** Spring's fruit. */
    SEED_STRAWBERRY("seed_strawberry", price = 5, objectId = PlantSpecies.STRAWBERRY.objectId, category = ItemCategory.SEED),

    /** Summer's big fruit. */
    SEED_WATERMELON(
        "seed_watermelon", price = 40, objectId = PlantSpecies.WATERMELON.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(9),
    ),

    /** A herb for every season, a step up from mint. */
    SEED_LAVENDER(
        "seed_lavender", price = 20, objectId = PlantSpecies.LAVENDER.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(5),
    ),

    /** Autumn's log of shiitake (#68): laid on open earth or a bed, not under glass. */
    SEED_SHIITAKE(
        "seed_shiitake", price = 45, objectId = PlantSpecies.SHIITAKE.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(14),
    ),

    /** Winter's red. */
    SEED_POINSETTIA(
        "seed_poinsettia", price = 70, objectId = PlantSpecies.POINSETTIA.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(15),
    ),

    /** Summer's peppers, a step up from the tomato; they keep bearing. */
    SEED_PAPRIKA(
        "seed_paprika", price = 40, objectId = PlantSpecies.PAPRIKA.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(11),
    ),

    /** The cold months' root. */
    SEED_DAIKON(
        "seed_daikon", price = 20, objectId = PlantSpecies.DAIKON.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(4),
    ),

    /** Spring's and autumn's root, a quicker one than the daikon. */
    SEED_CARROT(
        "seed_carrot", price = 20, objectId = PlantSpecies.CARROT.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(2),
    ),

    /** Summer's eggplant; it keeps bearing. */
    SEED_EGGPLANT(
        "seed_eggplant", price = 15, objectId = PlantSpecies.EGGPLANT.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(6),
    ),

    /** Autumn's wheat. */
    SEED_WHEAT(
        "seed_wheat", price = 35, objectId = PlantSpecies.WHEAT.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(7),
    ),

    /** Summer's corn. */
    SEED_CORN(
        "seed_corn", price = 65, objectId = PlantSpecies.CORN.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(13),
    ),

    /** Winter's leafy head. No pictures yet: registered, but not on sale ([inShop]). */
    SEED_HAKUSAI(
        "seed_hakusai", price = 40, objectId = PlantSpecies.HAKUSAI.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(10), inShop = false,
    ),

    /** Spring's field of yellow. No pictures yet: registered, but not on sale ([inShop]). */
    SEED_NANOHANA(
        "seed_nanohana", price = 45, objectId = PlantSpecies.NANOHANA.objectId, category = ItemCategory.SEED,
        unlock = UnlockCondition.PlayerLevel(12), inShop = false,
    ),

    /**
     * A weathervane: a one-cell ornament. While a finished one stands in the garden, tomorrow's
     * weather can be read as well as today's ([WeatherOutlook.isReadable]). No picture yet:
     * registered, but not on sale ([inShop]).
     */
    DECO_WEATHERVANE(
        "deco_weathervane", price = 500, objectId = WeatherOutlook.WEATHERVANE_OBJECT_ID, category = ItemCategory.DECORATION,
        unlock = UnlockCondition.PlayerLevel(6), inShop = false,
    ),

    /**
     * A one-cell glass greenhouse over a bed of soil. Seeds go into it as into tilled soil, and
     * whatever the season. Paving-like: it lies in the cell's floor layer.
     */
    FLOOR_GREENHOUSE(
        "floor_greenhouse", price = 3000, objectId = GridObject.GREENHOUSE_OBJECT_ID, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(18), placeXp = 40,
    ),

    /** The other kinds of rock: the shop keeps them with the first ([ROCK_KINDS]), under "石". */
    DECO_ROCK_FLAT(
        "deco_rock_flat", price = 80, objectId = "deco_rock_flat", category = ItemCategory.DECORATION,
        unlock = UnlockCondition.PlayerLevel(4),
    ),
    DECO_ROCK_MOSSY(
        "deco_rock_mossy", price = 150, objectId = "deco_rock_mossy", category = ItemCategory.DECORATION,
        unlock = UnlockCondition.PlayerLevel(7),
    ),
    DECO_ROCK_STANDING(
        "deco_rock_standing", price = 250, objectId = "deco_rock_standing", category = ItemCategory.DECORATION,
        unlock = UnlockCondition.PlayerLevel(10),
    ),
    DECO_ROCK_PEBBLES(
        "deco_rock_pebbles", price = 300, objectId = "deco_rock_pebbles", category = ItemCategory.DECORATION,
        unlock = UnlockCondition.PlayerLevel(13),
    ),

    /** The other kinds of fence: the shop keeps them with the first ([FENCE_KINDS]), under "柵". */
    STRUCT_FENCE_WHITE(
        "struct_fence_white", price = 25, objectId = RunKind.WHITE_FENCE.objectId, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(5), buildSeconds = 20,
    ),
    STRUCT_FENCE_BAMBOO(
        "struct_fence_bamboo", price = 35, objectId = RunKind.BAMBOO_FENCE.objectId, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(8), buildSeconds = 20,
    ),

    /**
     * Bricks set on the slant round a bed. It joins up like a fence, and is not one. Withdrawn for
     * now (#86): not on sale, no page in the book, and a saved garden's are taken up and paid back
     * ([com.studioguu.miniaturegarden.domain.usecase.RetiredCropsUseCase]). Its pictures and its
     * [RunKind] are kept, to bring it back.
     */
    STRUCT_EDGING_BRICK(
        "struct_edging_brick", price = 60, objectId = RunKind.BRICK_EDGING.objectId, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(16), buildSeconds = 20, inShop = false,
    ),

    /** An arched bridge from bank to bank, one price whatever the river ([GardenGrid.bridgeAcross]). */
    STRUCT_BRIDGE(
        "struct_bridge_taiko", price = 3500, objectId = StructureData.BRIDGE.objectId, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(17),
    );

    /** A symbol building: one per garden, see [StructureData.isSymbol]. */
    val isSymbol: Boolean get() = StructureData.isSymbol(objectId)

    fun isUnlockedFor(stats: PlayerStats): Boolean = unlock.isMetBy(stats)

    /** Taking it apart again takes half as long as building it did, and never more than [MAX_DEMOLISH_SECONDS]. */
    val demolishSeconds: Int get() = minOf(buildSeconds / 2, MAX_DEMOLISH_SECONDS)

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
            SEED_SHIITAKE -> setOf(Season.AUTUMN)
            SEED_POINSETTIA -> setOf(Season.WINTER)
            SEED_PAPRIKA -> setOf(Season.SUMMER)
            SEED_DAIKON -> setOf(Season.AUTUMN, Season.WINTER)
            SEED_CARROT -> setOf(Season.SPRING, Season.AUTUMN)
            SEED_EGGPLANT -> setOf(Season.SUMMER)
            SEED_WHEAT -> setOf(Season.AUTUMN)
            SEED_CORN -> setOf(Season.SUMMER)
            SEED_HAKUSAI -> setOf(Season.WINTER)
            SEED_NANOHANA -> setOf(Season.SPRING)
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
         * cheapest. With a [season], only what is in season is on it (see [isInSeason]). What is
         * not on sale yet ([inShop]) is never on it.
         */
        fun shelf(category: ItemCategory, season: Season? = null): List<GardenItem> =
            entries.filter { it.inShop && it.category == category && (season == null || it.isInSeason(season)) && it !in OFF_THE_SHELF }
                .sortedWith(compareBy({ it.openingRank }, { it.price }))

        /**
         * The kinds of rock, in the order they open. One stands on the shelf for all of them, the
         * first: tapping it shows the lot, those not open yet as shadows.
         */
        val ROCK_KINDS: List<GardenItem> get() = listOf(DECO_ROCK, DECO_ROCK_FLAT, DECO_ROCK_MOSSY, DECO_ROCK_STANDING, DECO_ROCK_PEBBLES)

        /** The kinds of fence, shown the way the rocks are. */
        val FENCE_KINDS: List<GardenItem> get() = listOf(STRUCT_FENCE, STRUCT_FENCE_WHITE, STRUCT_FENCE_BAMBOO)

        /** What is bought from the list under another item, and so has no place of its own on a shelf. */
        val OFF_THE_SHELF: List<GardenItem> get() = ROCK_KINDS.drop(1) + FENCE_KINDS.drop(1)

        /** The kinds that open from [item] when it is tapped on the shelf; null for a thing with none. */
        fun kindsUnder(item: GardenItem): List<GardenItem>? = listOf(ROCK_KINDS, FENCE_KINDS).firstOrNull { it.first() == item }

        /** Items that are locked at [previousLevel] and for sale at [newLevel]. */
        fun unlockedBetween(previousLevel: Int, newLevel: Int): List<GardenItem> = entries.filter {
            it.inShop &&
                !it.isUnlockedFor(PlayerStats(previousLevel, LevelTable.xpForLevel(previousLevel))) &&
                it.isUnlockedFor(PlayerStats(newLevel, LevelTable.xpForLevel(newLevel)))
        }
    }
}
