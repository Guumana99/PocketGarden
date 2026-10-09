package com.studioguu.miniaturegarden.domain.model

/**
 * Everything that can sit in an inventory slot and be bought in the shop.
 *
 * @property itemId value stored in an inventory slot.
 * @property price shop price in coins.
 * @property objectId the grid object created when the item is placed on a tile.
 * @property harvestCoins coins paid out when the grown plant is harvested; 0 for non-plants.
 */
enum class GardenItem(
    val itemId: String,
    val price: Int,
    val objectId: String,
    val harvestCoins: Int = 0,
) {
    SEED_TULIP("seed_tulip", price = 20, objectId = "plant_tulip", harvestCoins = 50),
    DECO_ROCK("deco_rock", price = 30, objectId = "deco_rock"),
    GROUND_GRASS("ground_grass", price = 10, objectId = "ground_grass"),
    GROUND_PEBBLE("ground_pebble", price = 10, objectId = "ground_pebble");

    companion object {
        /** Paid for harvesting a plant that has no catalog entry. */
        const val DEFAULT_HARVEST_COINS = 50

        fun fromItemId(itemId: String): GardenItem? = entries.firstOrNull { it.itemId == itemId }

        fun fromObjectId(objectId: String): GardenItem? = entries.firstOrNull { it.objectId == objectId }

        fun harvestCoinsFor(objectId: String): Int = fromObjectId(objectId)?.harvestCoins ?: DEFAULT_HARVEST_COINS
    }
}
