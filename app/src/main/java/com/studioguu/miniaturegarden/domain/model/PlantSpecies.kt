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
 * @property harvestType whether harvesting clears the plant or leaves it to bear again.
 * @property regrowCoins what each harvest after the first pays, for a [HarvestType.REPEATABLE_HARVEST]
 *   plant; the first pays [harvestCoins]. 0 for the others.
 * @property regrowXp the same for experience.
 */
enum class PlantSpecies(
    val objectId: String,
    val growthHours: Int,
    val dryOutHours: Int,
    val harvestCoins: Int,
    val harvestXp: Int,
    val growthStages: Int = 4,
    val harvestType: HarvestType = HarvestType.SINGLE_HARVEST,
    val regrowCoins: Int = 0,
    val regrowXp: Int = 0,
) {
    TULIP("plant_tulip", growthHours = 48, dryOutHours = 24, harvestCoins = 50, harvestXp = 50),
    SUNFLOWER("plant_sunflower", growthHours = 72, dryOutHours = 16, harvestCoins = 90, harvestXp = 80),
    ROSE("plant_rose", growthHours = 120, dryOutHours = 36, harvestCoins = 160, harvestXp = 140),

    /** Autumn's own flower; light and quick to grow. */
    COSMOS("plant_cosmos", growthHours = 60, dryOutHours = 20, harvestCoins = 64, harvestXp = 60),

    /** The winter flower: slow to dry out in the cold. */
    SNOWDROP("plant_snowdrop", growthHours = 54, dryOutHours = 30, harvestCoins = 58, harvestXp = 55),

    /** A herb that grows in any season; quick, and worth a little less for it. */
    MINT("plant_mint", growthHours = 36, dryOutHours = 18, harvestCoins = 35, harvestXp = 35),

    /**
     * Summer's crop from the start: thirsty in the heat, like the sunflower. It goes on bearing:
     * after a harvest it is back at its second stage and ripens again in half the time.
     */
    TOMATO(
        "plant_tomato", growthHours = 48, dryOutHours = 16, harvestCoins = 54, harvestXp = 52, growthStages = 3,
        harvestType = HarvestType.REPEATABLE_HARVEST, regrowCoins = 17, regrowXp = 26,
    ),

    /** A small spring fruit; quick to come, worth a little less for it. It goes on bearing, like the tomato. */
    STRAWBERRY(
        "plant_strawberry", growthHours = 42, dryOutHours = 20, harvestCoins = 48, harvestXp = 44, growthStages = 3,
        harvestType = HarvestType.REPEATABLE_HARVEST, regrowCoins = 14, regrowXp = 22,
    ),

    /** A big summer fruit at Lv.3; it drinks a lot. */
    WATERMELON("plant_watermelon", growthHours = 96, dryOutHours = 16, harvestCoins = 122, harvestXp = 110),

    /** A herb for every season at Lv.3; it keeps well between waterings. */
    LAVENDER("plant_lavender", growthHours = 84, dryOutHours = 30, harvestCoins = 108, harvestXp = 96),

    /** Autumn's big crop at Lv.5. */
    PUMPKIN("plant_pumpkin", growthHours = 132, dryOutHours = 30, harvestCoins = 190, harvestXp = 160),

    /** Winter's red leaves at Lv.5; slow to dry out in the cold. */
    POINSETTIA("plant_poinsettia", growthHours = 120, dryOutHours = 36, harvestCoins = 180, harvestXp = 150),

    /** Summer's peppers at Lv.4: slow to come, then they keep coming. */
    PAPRIKA(
        "plant_paprika", growthHours = 100, dryOutHours = 18, harvestCoins = 140, harvestXp = 118, growthStages = 3,
        harvestType = HarvestType.REPEATABLE_HARVEST, regrowCoins = 42, regrowXp = 59,
    ),

    /** The cold months' root at Lv.2: pulled up whole, so it is harvested once. */
    DAIKON("plant_daikon", growthHours = 64, dryOutHours = 26, harvestCoins = 80, harvestXp = 70, growthStages = 3);

    /** Growth progress (0..1) gained per healthy hour. */
    val growthPerHour: Float get() = 1f / growthHours

    val isRepeatable: Boolean get() = harvestType == HarvestType.REPEATABLE_HARVEST

    /**
     * Where a [HarvestType.REPEATABLE_HARVEST] plant's growth goes back to after a harvest: the
     * start of its second stage (0.5 for a plant drawn in three stages).
     */
    val regrowFrom: Float get() = if (growthStages <= 2) 0f else 1f / (growthStages - 1)

    /** Healthy hours from one harvest to the next for a repeatable plant. */
    val regrowHours: Float get() = growthHours * (1f - regrowFrom)

    /**
     * What a harvest pays: the full [harvestCoins] / [harvestXp] the first time, and the smaller
     * [regrowCoins] / [regrowXp] for each harvest of the same repeatable plant after that.
     *
     * @param harvestsBefore how many times this plant has been harvested already.
     */
    fun harvestReward(harvestsBefore: Int): HarvestReward =
        if (isRepeatable && harvestsBefore > 0) HarvestReward(regrowCoins, regrowXp) else HarvestReward(harvestCoins, harvestXp)

    companion object {
        /** Used for plant ids that have no entry here. */
        val DEFAULT = TULIP

        fun fromObjectId(objectId: String): PlantSpecies? = entries.firstOrNull { it.objectId == objectId }

        fun forObjectId(objectId: String): PlantSpecies = fromObjectId(objectId) ?: DEFAULT
    }
}

/** What harvesting does to a plant. */
enum class HarvestType {
    /** Harvested once: the plant goes, and the tilled soil it stood on is free again. */
    SINGLE_HARVEST,

    /**
     * Harvested again and again: the plant stays, back at the start of its second stage
     * ([PlantSpecies.regrowFrom]), and ripens once more with time and care.
     */
    REPEATABLE_HARVEST,
}

/** The coins and experience one harvest pays. */
data class HarvestReward(val coins: Int, val xp: Int)
