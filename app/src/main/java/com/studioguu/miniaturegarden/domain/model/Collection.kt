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
    COSMOS(PlantSpecies.COSMOS.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 30),
    SNOWDROP(PlantSpecies.SNOWDROP.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 30),
    MINT(PlantSpecies.MINT.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 20),
    TOMATO(PlantSpecies.TOMATO.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 30),
    STRAWBERRY(PlantSpecies.STRAWBERRY.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 30),
    WATERMELON(PlantSpecies.WATERMELON.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 50),
    LAVENDER(PlantSpecies.LAVENDER.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 50),
    SHIITAKE(PlantSpecies.SHIITAKE.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 90),
    POINSETTIA(PlantSpecies.POINSETTIA.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 90),
    PAPRIKA(PlantSpecies.PAPRIKA.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 70),
    DAIKON(PlantSpecies.DAIKON.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 40),
    CARROT(PlantSpecies.CARROT.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 30),
    EGGPLANT(PlantSpecies.EGGPLANT.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 50),
    WHEAT(PlantSpecies.WHEAT.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 60),
    CORN(PlantSpecies.CORN.objectId, CollectionCategory.CROPS, CollectionTrigger.HARVEST, discoveryCoins = 80),
    GRASS("ground_grass", CollectionCategory.TERRAIN, CollectionTrigger.PLACEMENT, discoveryCoins = 10),
    PEBBLE("ground_pebble", CollectionCategory.TERRAIN, CollectionTrigger.PLACEMENT, discoveryCoins = 10),
    RIVER(GridObject.RIVER_OBJECT_ID, CollectionCategory.TERRAIN, CollectionTrigger.PLACEMENT, discoveryCoins = 10),
    PATH(GridObject.COBBLESTONE_OBJECT_ID, CollectionCategory.TERRAIN, CollectionTrigger.PLACEMENT, discoveryCoins = 10),
    FLOWERS("ground_flowers", CollectionCategory.TERRAIN, CollectionTrigger.PLACEMENT, discoveryCoins = 10),
    DECK(GridObject.WOOD_DECK_OBJECT_ID, CollectionCategory.TERRAIN, CollectionTrigger.PLACEMENT, discoveryCoins = 15),
    POND(GridObject.POND_OBJECT_ID, CollectionCategory.TERRAIN, CollectionTrigger.PLACEMENT, discoveryCoins = 30),
    ROCK("deco_rock", CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 15),
    ROCK_FLAT("deco_rock_flat", CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 15),
    ROCK_MOSSY("deco_rock_mossy", CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 20),
    ROCK_STANDING("deco_rock_standing", CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 30),
    ROCK_PEBBLES("deco_rock_pebbles", CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 30),
    FENCE(GridObject.FENCE_OBJECT_ID, CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 15),
    BENCH("deco_bench", CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 20),
    LANTERN("deco_lantern", CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 30),
    SPRINKLER(Sprinkler.OBJECT_ID, CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 30),
    WELL(StructureData.WELL.objectId, CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 40),
    HUT(StructureData.HUT.objectId, CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 80),
    COTTAGE(StructureData.COTTAGE.objectId, CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 100),
    WINDMILL(StructureData.WINDMILL.objectId, CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 60),
    GREENHOUSE(GridObject.GREENHOUSE_OBJECT_ID, CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 60),
    BRIDGE(StructureData.BRIDGE.objectId, CollectionCategory.STRUCTURES, CollectionTrigger.PLACEMENT, discoveryCoins = 80);

    companion object {
        fun fromObjectId(objectId: String): CollectionEntry? = entries.firstOrNull { it.objectId == objectId }

        fun inCategory(category: CollectionCategory): List<CollectionEntry> = entries.filter { it.category == category }

        /**
         * The key a colour of a crop that blooms in several ([PlantSpecies.bloomColours]) is
         * noted under, once one in that colour has been harvested (#59): `plant_tulip#pink`. It
         * is no page of its own: it only says which colours the page has seen.
         */
        fun colourRecordId(objectId: String, colour: String): String = "$objectId#$colour"
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

    /**
     * The colours of [entry] the player has harvested, the first one first (#59); empty for a
     * crop of one colour, or for harvests from before colours were noted.
     */
    fun coloursOf(entry: CollectionEntry): List<String> =
        PlantSpecies.fromObjectId(entry.objectId)?.bloomColours.orEmpty()
            .mapNotNull { colour -> byId[CollectionEntry.colourRecordId(entry.objectId, colour)]?.let { colour to it.firstObtainedAt } }
            .sortedBy { it.second }
            .map { it.first }

    fun stateOf(entry: CollectionEntry): PageState {
        val record = byId[entry.objectId] ?: return PageState.LOCKED
        return if (record.revealed) PageState.REVEALED else PageState.READY
    }

    fun readyIn(category: CollectionCategory): Int =
        CollectionEntry.inCategory(category).count { stateOf(it) == PageState.READY }
}
