package com.studioguu.miniaturegarden.domain.model

/** What the player has to do with something for it to count towards the garden encyclopedia. */
enum class CollectionTrigger {
    /** Grow it and harvest it. Merely planting the seed does not count. */
    HARVEST,

    /** Put it on the garden. */
    PLACEMENT,
}

/**
 * One page of the garden encyclopedia.
 *
 * @property objectId the grid object this page is about; also the key it is stored under.
 * @property discoveryCoins paid once, the first time the page is filled in.
 */
enum class CollectionEntry(
    val objectId: String,
    val trigger: CollectionTrigger,
    val discoveryCoins: Int,
) {
    TULIP(PlantSpecies.TULIP.objectId, CollectionTrigger.HARVEST, discoveryCoins = 20),
    SUNFLOWER(PlantSpecies.SUNFLOWER.objectId, CollectionTrigger.HARVEST, discoveryCoins = 40),
    ROSE(PlantSpecies.ROSE.objectId, CollectionTrigger.HARVEST, discoveryCoins = 80),
    GRASS("ground_grass", CollectionTrigger.PLACEMENT, discoveryCoins = 10),
    PEBBLE("ground_pebble", CollectionTrigger.PLACEMENT, discoveryCoins = 10),
    ROCK("deco_rock", CollectionTrigger.PLACEMENT, discoveryCoins = 15),
    WELL(StructureData.WELL.objectId, CollectionTrigger.PLACEMENT, discoveryCoins = 40),
    HUT(StructureData.HUT.objectId, CollectionTrigger.PLACEMENT, discoveryCoins = 80);

    companion object {
        /** Paid once, when the last page is filled in. */
        const val COMPLETION_COINS = 300
        const val COMPLETION_XP = 300

        fun fromObjectId(objectId: String): CollectionEntry? = entries.firstOrNull { it.objectId == objectId }
    }
}

/** What is stored about one page: how often, and since when. Absent means not discovered yet. */
data class CollectionRecord(
    val entryId: String,
    val count: Int,
    /** Epoch millis of the first harvest / placement. */
    val firstObtainedAt: Long,
)

/** The encyclopedia as a whole. */
data class CollectionProgress(private val records: List<CollectionRecord>) {
    private val byId = records.associateBy { it.entryId }

    val total: Int get() = CollectionEntry.entries.size
    val discovered: Int get() = CollectionEntry.entries.count { it.objectId in byId }
    val isComplete: Boolean get() = discovered == total

    /** 0 .. 100, rounded down, so 100 means every page and nothing less. */
    val percent: Int get() = discovered * 100 / total

    fun recordOf(entry: CollectionEntry): CollectionRecord? = byId[entry.objectId]
    fun isDiscovered(entry: CollectionEntry): Boolean = entry.objectId in byId
}

/**
 * What recording one harvest / placement earned.
 *
 * @property discovered the page that was just filled in for the first time, if any.
 * @property completed true when that page was the last one missing.
 */
data class CollectionReward(
    val coins: Int = 0,
    val xp: Int = 0,
    val discovered: CollectionEntry? = null,
    val completed: Boolean = false,
) {
    companion object {
        val NONE = CollectionReward()
    }
}
