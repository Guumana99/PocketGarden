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
 * Everything that can sit in an inventory slot and be bought in the shop.
 *
 * Economy at a glance (coins; a plant's own numbers live in [PlantSpecies]):
 *
 *     item        unlock  price  returns
 *     tulip       Lv.1      20   50 coins + 50 XP after 48 h
 *     sunflower   Lv.2      35   90 coins + 80 XP after 72 h, dries out fast
 *     rose        Lv.4      60   160 coins + 140 XP after 120 h
 *     grass       Lv.1      10   decoration
 *     pebble      Lv.1      10   decoration
 *     river tile  Lv.1      10   terrain; nothing can stand on it
 *     cobblestone Lv.1      10   paving; things can stand on it
 *     fence       Lv.1      15   structure, one tile
 *     rock        Lv.2      30   decoration
 *     well        Lv.3      80   30 XP when built
 *     hut         Lv.5     150   60 XP when built
 *
 * Each plant earns more per hour than the one before it, which is what makes the next unlock
 * worth reaching.
 *
 * @property itemId value stored in an inventory slot.
 * @property price shop price in coins.
 * @property objectId the grid object created when the item is placed on a tile. For seeds this
 *   is a [PlantSpecies] id, which is where the plant's own balance values live.
 * @property unlock what the player needs before the shop sells this. Items already in the
 *   inventory can always be placed.
 * @property placeXp experience earned for placing it; seeds pay out at harvest instead.
 */
enum class GardenItem(
    val itemId: String,
    val price: Int,
    val objectId: String,
    val category: ItemCategory,
    val unlock: UnlockCondition = UnlockCondition.None,
    val placeXp: Int = 0,
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
        unlock = UnlockCondition.PlayerLevel(5), placeXp = 60,
    ),
    STRUCT_WELL(
        "struct_well", price = 80, objectId = StructureData.WELL.objectId, category = ItemCategory.STRUCTURE,
        unlock = UnlockCondition.PlayerLevel(3), placeXp = 30,
    ),
    GROUND_GRASS("ground_grass", price = 10, objectId = "ground_grass", category = ItemCategory.DECORATION),
    GROUND_PEBBLE("ground_pebble", price = 10, objectId = "ground_pebble", category = ItemCategory.DECORATION),

    /** Paving for paths. Goes under things rather than in their place. */
    FLOOR_COBBLESTONE(
        "floor_cobblestone", price = 10, objectId = GridObject.COBBLESTONE_OBJECT_ID, category = ItemCategory.DECORATION,
    ),

    /** A one-tile section of fence, for edging beds and paths. */
    STRUCT_FENCE("struct_fence", price = 15, objectId = GridObject.FENCE_OBJECT_ID, category = ItemCategory.STRUCTURE),

    /** Turns the tile it is placed on into river; removing it turns the tile back into lawn. */
    TILE_RIVER("tile_river", price = 10, objectId = GridObject.RIVER_OBJECT_ID, category = ItemCategory.DECORATION);

    fun isUnlockedFor(stats: PlayerStats): Boolean = unlock.isMetBy(stats)

    /** The player level that opens this item in the shop; 1 when it is never locked. */
    val unlockLevel: Int get() = (unlock as? UnlockCondition.PlayerLevel)?.level ?: 1

    companion object {
        fun fromItemId(itemId: String): GardenItem? = entries.firstOrNull { it.itemId == itemId }

        /** Items that are locked at [previousLevel] and for sale at [newLevel]. */
        fun unlockedBetween(previousLevel: Int, newLevel: Int): List<GardenItem> = entries.filter {
            !it.isUnlockedFor(PlayerStats(previousLevel, LevelTable.xpForLevel(previousLevel))) &&
                it.isUnlockedFor(PlayerStats(newLevel, LevelTable.xpForLevel(newLevel)))
        }
    }
}
