package com.studioguu.miniaturegarden.domain.model

/** What the player has to do with something for its page of the garden encyclopedia to open up. */
enum class CollectionTrigger {
    /** Grow it and harvest it. Merely planting the seed does not count. */
    HARVEST,

    /** Put it on the garden. */
    PLACEMENT,
}

/** The chapters of the encyclopedia. */
enum class CollectionCategory {
    /** Things that are grown and harvested. */
    CROPS,

    /** The ground itself and what is laid on it: lawn, pebbles, paths, river. */
    TERRAIN,

    /** Things that are built or stood on the ground: rocks, fences, buildings. */
    STRUCTURES,
}

/**
 * One page of the garden encyclopedia.
 *
 * @property objectId the grid object this page is about; also the key it is stored under.
 * @property discoveryCoins paid once, when the player fills the page in.
 */
enum class CollectionEntry(
    val objectId: String,
    val category: CollectionCategory,
    val trigger: CollectionTrigger,
    val discoveryCoins: Int,
) {
    TULIP(PlantSpecies.TULIP.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 20),
    SUNFLOWER(PlantSpecies.SUNFLOWER.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 40),
    ROSE(PlantSpecies.ROSE.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 80),
    GRASS("ground_grass", CollectionCategory.TERRAIN, CollectionTrigger.PLACEMENT, discoveryCoins = 10),
    PEBBLE("ground_pebble", CollectionCategory.TERRAIN, CollectionTrigger.PLACEMENT, discoveryCoins = 10),
    RIVER(GridObject.RIVER_OBJECT_ID, CollectionCategory.TERRAIN, CollectionTrigger.PLACEMENT, discoveryCoins = 10),
    PATH(GridObject.COBBLESTONE_OBJECT_ID, CollectionCategory.TERRAIN, CollectionTrigger.PLACEMENT, discoveryCoins = 10),
    ROCK("deco_rock", CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 15),
    FENCE(GridObject.FENCE_OBJECT_ID, CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 15),
    WELL(StructureData.WELL.objectId, CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 40),
    HUT(StructureData.HUT.objectId, CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 80);

    companion object {
        fun fromObjectId(objectId: String): CollectionEntry? = entries.firstOrNull { it.objectId == objectId }

        fun inCategory(category: CollectionCategory): List<CollectionEntry> = entries.filter { it.category == category }
    }
}

/** Where a page stands. */
enum class PageState {
    /** The player has not harvested / placed it yet: the page shows a question mark. */
    LOCKED,

    /** The condition is met, but the page still shows a question mark until the player fills it in. */
    READY,

    /** The player filled the page in: it shows the picture and the text. */
    REVEALED,
}

/**
 * What is stored about one page. Absent means the condition has not been met yet.
 *
 * @property revealed whether the player has filled the page in. Meeting the condition does not
 *   do that by itself; it takes a tap in the book.
 */
data class CollectionRecord(
    val entryId: String,
    val count: Int,
    /** Epoch millis of the first harvest / placement. */
    val firstObtainedAt: Long,
    val revealed: Boolean = false,
)

/** The encyclopedia as a whole. */
data class CollectionProgress(private val records: List<CollectionRecord>) {
    private val byId = records.associateBy { it.entryId }

    val total: Int get() = CollectionEntry.entries.size

    /** Pages the player has filled in. */
    val revealed: Int get() = CollectionEntry.entries.count { stateOf(it) == PageState.REVEALED }

    /** Pages waiting to be filled in. */
    val ready: Int get() = CollectionEntry.entries.count { stateOf(it) == PageState.READY }

    /** 0 .. 100, rounded down, so 100 means every page and nothing less. */
    val percent: Int get() = revealed * 100 / total

    fun recordOf(entry: CollectionEntry): CollectionRecord? = byId[entry.objectId]

    fun stateOf(entry: CollectionEntry): PageState {
        val record = byId[entry.objectId] ?: return PageState.LOCKED
        return if (record.revealed) PageState.REVEALED else PageState.READY
    }

    fun readyIn(category: CollectionCategory): Int =
        CollectionEntry.inCategory(category).count { stateOf(it) == PageState.READY }
}
