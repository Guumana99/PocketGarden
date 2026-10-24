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
 * @property canWither false for a plant that never withers (#58: the tulip): neglect and full
 *   bloom still stress it, but never all the way.
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
    val canWither: Boolean = true,
) {
    /** The first flower: drawn in three stages (#58), and it never withers. */
    TULIP("plant_tulip", growthHours = 48, dryOutHours = 24, harvestCoins = 50, harvestXp = 50, growthStages = 3, canWither = false),
    SUNFLOWER("plant_sunflower", growthHours = 72, dryOutHours = 16, harvestCoins = 90, harvestXp = 80, growthStages = 3),
    ROSE("plant_rose", growthHours = 120, dryOutHours = 36, harvestCoins = 160, harvestXp = 140),

    /** Autumn's own flower; light and quick to grow. */
    COSMOS("plant_cosmos", growthHours = 60, dryOutHours = 20, harvestCoins = 64, harvestXp = 60, growthStages = 3),

    /** The winter flower: slow to dry out in the cold. */
    SNOWDROP("plant_snowdrop", growthHours = 54, dryOutHours = 30, harvestCoins = 58, harvestXp = 55),

    /** A herb that grows in any season; quick, and worth a little less for it. */
    MINT("plant_mint", growthHours = 36, dryOutHours = 18, harvestCoins = 35, harvestXp = 35, growthStages = 3),

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
    DAIKON("plant_daikon", growthHours = 64, dryOutHours = 26, harvestCoins = 80, harvestXp = 70, growthStages = 3),

    /** A root for spring and autumn at Lv.2, a little quicker than the daikon: pulled up whole, harvested once. */
    CARROT("plant_carrot", growthHours = 62, dryOutHours = 22, harvestCoins = 72, harvestXp = 68, growthStages = 3),

    /** Summer's purple fruit at Lv.3: thirsty, and it keeps bearing, like the tomato. */
    EGGPLANT(
        "plant_eggplant", growthHours = 78, dryOutHours = 16, harvestCoins = 102, harvestXp = 88, growthStages = 3,
        harvestType = HarvestType.REPEATABLE_HARVEST, regrowCoins = 30, regrowXp = 42,
    ),

    /** Autumn's grain at Lv.4, a whole bed of it; it stands dry weather well. */
    WHEAT("plant_wheat", growthHours = 98, dryOutHours = 32, harvestCoins = 128, harvestXp = 114, growthStages = 3),

    /** Summer's tall crop at Lv.5: three stalks to a bed, slow to ripen. */
    CORN("plant_corn", growthHours = 126, dryOutHours = 18, harvestCoins = 185, harvestXp = 155, growthStages = 3);

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

    /**
     * The colours this crop blooms in (#58), its plain last stage first (red for the tulip);
     * empty for a crop that blooms in one colour only.
     */
    val bloomColours: List<String> get() = BLOOM_COLOURS[this].orEmpty()

    /**
     * The colour the plant [instanceId] of this crop blooms in, picked by its id once and for
     * good: the same one in the garden, when it is harvested and in the book (#59). Null for a
     * crop of one colour.
     */
    fun bloomColourOf(instanceId: String): String? =
        bloomColours.takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(instanceId.hashCode(), it.size)] }

    companion object {
        private val BLOOM_COLOURS: Map<PlantSpecies, List<String>> = mapOf(
            TULIP to listOf("red", "pink", "yellow", "white", "purple", "blue"),
        )

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
