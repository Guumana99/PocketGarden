package com.studioguu.miniaturegarden.domain.model

/**
 * Per-plant balance values.
 *
 * @property growthHours healthy hours from seed to fully grown.
 * @property dryOutHours hours for full soil to dry out completely in sunny weather; shorter
 *   means the plant must be watered more often. Never under [MIN_DRY_OUT_HOURS].
 * @property harvestCoins coins paid for harvesting it fully grown.
 * @property harvestXp experience gained for harvesting it.
 * @property growthStages number of growth sprites, named `<objectId>_stage1` .. `_stage<N>`.
 * @property harvestType whether harvesting clears the plant or leaves it to bear again.
 * @property regrowCoins what each harvest after the first pays, for a [HarvestType.REPEATABLE_HARVEST]
 *   plant; the first pays [harvestCoins]. 0 for the others.
 * @property regrowXp the same for experience.
 * @property canWither false for a plant that never withers (#58: the tulip): neglect and full
 *   bloom still stress it, but never all the way.
 * @property ground where it can be put down and whether it lives in a bed's soil ([PlantGround]).
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
    val ground: PlantGround = PlantGround.BED,
) {
    /** The first flower: drawn in three stages (#58), and it never withers. */
    TULIP("plant_tulip", growthHours = 6, dryOutHours = 24, harvestCoins = 22, harvestXp = 10, growthStages = 3, canWither = false, ground = PlantGround.FLOWER_BED),
    SUNFLOWER("plant_sunflower", growthHours = 20, dryOutHours = 16, harvestCoins = 50, harvestXp = 22, growthStages = 3, ground = PlantGround.FLOWER_BED),
    ROSE("plant_rose", growthHours = 36, dryOutHours = 36, harvestCoins = 105, harvestXp = 40, ground = PlantGround.FLOWER_BED),

    /** Autumn's own flower; light and quick to grow. */
    COSMOS("plant_cosmos", growthHours = 10, dryOutHours = 20, harvestCoins = 22, harvestXp = 10, growthStages = 3, ground = PlantGround.FLOWER_BED),

    /** The winter flower: slow to dry out in the cold. */
    SNOWDROP("plant_snowdrop", growthHours = 10, dryOutHours = 30, harvestCoins = 22, harvestXp = 10, growthStages = 3, ground = PlantGround.FLOWER_BED),

    /** A herb that grows in any season; the quickest of all, and worth the least for it. */
    MINT("plant_mint", growthHours = 4, dryOutHours = 18, harvestCoins = 13, harvestXp = 7, growthStages = 3),

    /**
     * Summer's crop from the start: thirsty in the heat, like the sunflower. It goes on bearing:
     * after a harvest it is back at its second stage and ripens again in half the time.
     */
    TOMATO(
        "plant_tomato", growthHours = 8, dryOutHours = 16, harvestCoins = 20, harvestXp = 15, growthStages = 3,
        harvestType = HarvestType.REPEATABLE_HARVEST, regrowCoins = 9, regrowXp = 8,
    ),

    /** A small spring fruit; quick to come, worth a little less for it. It goes on bearing, like the tomato. */
    STRAWBERRY(
        "plant_strawberry", growthHours = 6, dryOutHours = 20, harvestCoins = 16, harvestXp = 14, growthStages = 3,
        harvestType = HarvestType.REPEATABLE_HARVEST, regrowCoins = 8, regrowXp = 7,
    ),

    /** A big summer fruit at Lv.9; it drinks a lot. */
    WATERMELON("plant_watermelon", growthHours = 30, dryOutHours = 16, harvestCoins = 100, harvestXp = 42, growthStages = 3),

    /** A herb for every season at Lv.5; it keeps well between waterings. */
    LAVENDER("plant_lavender", growthHours = 24, dryOutHours = 30, harvestCoins = 52, harvestXp = 24, growthStages = 3, ground = PlantGround.FLOWER_BED),

    /**
     * Autumn's crop at Lv.14 (#68): a log laid on the earth or on a bed, on which shiitake come up
     * again and again. Not under glass.
     */
    SHIITAKE(
        "plant_shiitake", growthHours = 48, dryOutHours = 30, harvestCoins = 100, harvestXp = 65, growthStages = 3,
        harvestType = HarvestType.REPEATABLE_HARVEST, regrowCoins = 50, regrowXp = 33, ground = PlantGround.LOG,
    ),

    /** Winter's red leaves at Lv.15; slow to dry out in the cold. */
    POINSETTIA("plant_poinsettia", growthHours = 40, dryOutHours = 36, harvestCoins = 175, harvestXp = 67, growthStages = 3, ground = PlantGround.FLOWER_BED),

    /** Summer's peppers at Lv.11: slow to come, then they keep coming. */
    PAPRIKA(
        "plant_paprika", growthHours = 32, dryOutHours = 18, harvestCoins = 90, harvestXp = 58, growthStages = 3,
        harvestType = HarvestType.REPEATABLE_HARVEST, regrowCoins = 45, regrowXp = 29,
    ),

    /** The cold months' root at Lv.4: pulled up whole, so it is harvested once. */
    DAIKON("plant_daikon", growthHours = 18, dryOutHours = 26, harvestCoins = 50, harvestXp = 23, growthStages = 3),

    /** A root for spring and autumn at Lv.2, a little quicker than the daikon: pulled up whole, harvested once. */
    CARROT("plant_carrot", growthHours = 16, dryOutHours = 22, harvestCoins = 47, harvestXp = 22, growthStages = 3),

    /** Summer's purple fruit at Lv.6: thirsty, and it keeps bearing, like the tomato. */
    EGGPLANT(
        "plant_eggplant", growthHours = 22, dryOutHours = 16, harvestCoins = 34, harvestXp = 25, growthStages = 3,
        harvestType = HarvestType.REPEATABLE_HARVEST, regrowCoins = 17, regrowXp = 12,
    ),

    /** Autumn's grain at Lv.7, a whole bed of it; it stands dry weather well. */
    WHEAT("plant_wheat", growthHours = 30, dryOutHours = 32, harvestCoins = 90, harvestXp = 39, growthStages = 3),

    /** Summer's tall crop at Lv.13: three stalks to a bed, slow to ripen. */
    CORN("plant_corn", growthHours = 42, dryOutHours = 18, harvestCoins = 165, harvestXp = 63, growthStages = 3),

    /**
     * Winter's leafy head at Lv.10: grown and pulled up like the daikon.
     */
    HAKUSAI("plant_hakusai", growthHours = 30, dryOutHours = 26, harvestCoins = 105, harvestXp = 43, growthStages = 3),

    /**
     * Spring's yellow field flower at Lv.12, light like the cosmos.
     */
    NANOHANA("plant_nanohana", growthHours = 32, dryOutHours = 20, harvestCoins = 115, harvestXp = 45, growthStages = 3, ground = PlantGround.FLOWER_BED);

    /** Growth progress (0..1) gained per healthy hour. */
    val growthPerHour: Float get() = 1f / growthHours

    val isRepeatable: Boolean get() = harvestType == HarvestType.REPEATABLE_HARVEST

    /** Whether it is grown for its flowers: what goes on a flower bed. */
    val isFlower: Boolean get() = ground == PlantGround.FLOWER_BED

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
     * The colour the plant [instanceId] of this crop blooms in, going by its id alone: how the
     * colour was picked until [BloomColours] (#79), and still is for what was in bloom by then.
     * Null for a crop of one colour.
     */
    fun bloomColourOf(instanceId: String): String? =
        bloomColours.takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(instanceId.hashCode(), it.size)] }

    companion object {
        private val BLOOM_COLOURS: Map<PlantSpecies, List<String>> = mapOf(
            TULIP to listOf("red", "pink", "yellow", "white", "purple", "blue"),
        )

        /**
         * The shortest a bed may take to dry out: a player who looks in morning and evening finds
         * it still damp overnight.
         */
        const val MIN_DRY_OUT_HOURS = 12

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
