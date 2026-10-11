package com.studioguu.miniaturegarden.domain.model

/**
 * Everything that can sit in an inventory slot and be bought in the shop.
 *
 * @property itemId value stored in an inventory slot.
 * @property price shop price in coins.
 * @property objectId the grid object created when the item is placed on a tile. For seeds this
 *   is a [PlantSpecies] id, which is where the plant's own balance values live.
 */
enum class GardenItem(
    val itemId: String,
    val price: Int,
    val objectId: String,
) {
    SEED_TULIP("seed_tulip", price = 20, objectId = PlantSpecies.TULIP.objectId),
    SEED_SUNFLOWER("seed_sunflower", price = 35, objectId = PlantSpecies.SUNFLOWER.objectId),
    SEED_ROSE("seed_rose", price = 60, objectId = PlantSpecies.ROSE.objectId),
    DECO_ROCK("deco_rock", price = 30, objectId = "deco_rock"),
    STRUCT_HUT("struct_hut", price = 150, objectId = StructureData.HUT.objectId),
    STRUCT_WELL("struct_well", price = 80, objectId = StructureData.WELL.objectId),
    GROUND_GRASS("ground_grass", price = 10, objectId = "ground_grass"),
    GROUND_PEBBLE("ground_pebble", price = 10, objectId = "ground_pebble");

    companion object {
        fun fromItemId(itemId: String): GardenItem? = entries.firstOrNull { it.itemId == itemId }
    }
}
