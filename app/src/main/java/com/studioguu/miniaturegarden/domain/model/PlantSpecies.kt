package com.studioguu.miniaturegarden.domain.model

/**
 * Per-plant balance values.
 *
 * @property growthHours healthy hours from seed to fully grown.
 * @property dryOutHours hours for full soil to dry out completely in sunny weather; shorter
 *   means the plant must be watered more often.
 * @property harvestCoins coins paid for harvesting it fully grown.
 * @property harvestXp experience gained for harvesting it.
 * @property growthStages number of growth sprites, named `<objectId>_stage1` .. `_stage<N>`.
 */
enum class PlantSpecies(
    val objectId: String,
    val growthHours: Int,
    val dryOutHours: Int,
    val harvestCoins: Int,
    val harvestXp: Int,
    val growthStages: Int = 4,
) {
    TULIP("plant_tulip", growthHours = 48, dryOutHours = 24, harvestCoins = 50, harvestXp = 50),
    SUNFLOWER("plant_sunflower", growthHours = 72, dryOutHours = 16, harvestCoins = 90, harvestXp = 80),
    ROSE("plant_rose", growthHours = 120, dryOutHours = 36, harvestCoins = 160, harvestXp = 140),

    /** Autumn's own flower; light and quick to grow. */
    COSMOS("plant_cosmos", growthHours = 60, dryOutHours = 20, harvestCoins = 64, harvestXp = 60),

    /** The winter flower: slow to dry out in the cold. */
    SNOWDROP("plant_snowdrop", growthHours = 54, dryOutHours = 30, harvestCoins = 58, harvestXp = 55),

    /** A herb that grows in any season; quick, and worth a little less for it. */
    MINT("plant_mint", growthHours = 36, dryOutHours = 18, harvestCoins = 35, harvestXp = 35);

    /** Growth progress (0..1) gained per healthy hour. */
    val growthPerHour: Float get() = 1f / growthHours

    companion object {
        /** Used for plant ids that have no entry here. */
        val DEFAULT = TULIP

        fun fromObjectId(objectId: String): PlantSpecies? = entries.firstOrNull { it.objectId == objectId }

        fun forObjectId(objectId: String): PlantSpecies = fromObjectId(objectId) ?: DEFAULT
    }
}
