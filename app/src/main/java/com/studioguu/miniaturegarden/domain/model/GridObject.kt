package com.studioguu.miniaturegarden.domain.model

data class GridObject(
    val instanceId: String,
    val gridX: Int,
    val gridY: Int,
    val heightLevel: Int = 0,
    val isFlipped: Boolean = false,
    val objectId: String,
    val growthProgress: Float = 0f,
    val moistureLevel: Float = 1f,
    val nutritionLevel: Float = 1f,
    val stressRate: Float = 0f,
    val status: PlantStatus = PlantStatus.HEALTHY,
    val lastUpdatedTimestamp: Long,
    /** The job being done on this object, if any. See [WorkKind]. */
    val workKind: WorkKind = WorkKind.NONE,
    /** Epoch millis at which that job began and at which it will be finished; 0 without a job. */
    val workStartedAt: Long = 0L,
    val workEndsAt: Long = 0L,
    /** How many times this plant has been harvested; only a repeatable crop is ever above 0. */
    val harvestCount: Int = 0,
    /** The grade of a cell of soil ([SoilGrade]): 0 plain, 1 good, 2 fluffy. 0 for anything else. */
    val soilQuality: Int = 0,
    /** Whether a thing that can be switched (the sprinkler) is running. True for everything else. */
    val isOn: Boolean = true,
    /** Of a cell of soil: the points towards its next grade ([Soil.POINTS_PER_GRADE]). */
    val soilProgress: Int = 0,
    /** Of a cell of soil: the crop last harvested from it, to tell when crops change. */
    val lastCropId: String? = null,
    /** Of a cell of soil: how long it has lain with nothing growing, as [Soil.afterTime] counts it. */
    val idleMillis: Long = 0L,
    /** Of a crop of several colours: the one it opened in ([BloomColours]); null until it has. */
    val bloomColour: String? = null,
    /** Of a gift box: what is inside ([Gift.encoded]); null for a box from before there were gifts. */
    val gift: String? = null,
) {
    /** Whether this is a work site: being built (or built and not yet unveiled), taken apart or packed up. */
    val isWorking: Boolean get() = workKind != WorkKind.NONE

    /** How far along the job is at [now], 0 (just begun) to 1 (done); 1 when there is none. */
    fun workProgress(now: Long): Float {
        if (!isWorking || workKind == WorkKind.READY) return 1f
        val total = workEndsAt - workStartedAt
        if (total <= 0L) return 1f
        return ((now - workStartedAt).toFloat() / total).coerceIn(0f, 1f)
    }

    /** Milliseconds of the job still to go at [now]; 0 when it is done or there is none. */
    fun workRemainingMillis(now: Long): Long =
        if (isWorking && workKind != WorkKind.READY) (workEndsAt - now).coerceAtLeast(0L) else 0L

    /** The same object with no job on it. */
    fun withoutWork(): GridObject = copy(workKind = WorkKind.NONE, workStartedAt = 0L, workEndsAt = 0L)

    val isPlant: Boolean get() = objectId.startsWith(PLANT_ID_PREFIX)

    /** Whether it can be turned to face the other way (see [canFaceEitherWay]). */
    val canFlip: Boolean get() = canFaceEitherWay(objectId)

    /** Terrain laid by the player (river). It is part of the ground: nothing can stand on it. */
    val isTerrain: Boolean get() = objectId.startsWith(TERRAIN_ID_PREFIX)

    /** Paving. It lies under whatever stands on the cell and does not take the cell up. */
    val isFloor: Boolean get() = objectId.startsWith(FLOOR_ID_PREFIX)

    /** Laid on the earth as cover: pebbles, wild flowers, a lawn ([GROUND_COVER_ID_PREFIX]). */
    val isGroundCover: Boolean get() = objectId.startsWith(GROUND_COVER_ID_PREFIX)

    /**
     * Ridged soil (畝): ground the hoe has been over twice, where any seed can go ([PlantGround]).
     * Its id is the one every bed had before there were two kinds, which is why a bed in an older
     * save is ridged, as all of them could be sown with anything.
     */
    val isTilledSoil: Boolean get() = objectId == TILLED_SOIL_OBJECT_ID

    /** Tilled earth (耕した土): ground the hoe has been over once, flat, where flowers can go. */
    val isLooseSoil: Boolean get() = objectId == LOOSE_SOIL_OBJECT_ID

    /** Ground the hoe has made, tilled or ridged. It can be turned back ([Soil.groundBelow]). */
    val isHoedSoil: Boolean get() = isLooseSoil || isTilledSoil

    /** A cell of soil, with its food and grade ([Soil]): what the hoe made, or a greenhouse bed. */
    val isSoil: Boolean get() = isHoedSoil || isGreenhouse

    /**
     * A glass greenhouse bed: tilled soil under glass. Seeds can be sown in it like in tilled
     * soil, and in any season.
     */
    val isGreenhouse: Boolean get() = objectId == GREENHOUSE_OBJECT_ID

    /** A fence, under whichever of its ids. Fences join up with the fences next to them. */
    val isFence: Boolean get() = FencePart.fromObjectId(objectId) != null

    /** The pond: water in a ring of stones, which takes its whole cell like the river does. */
    val isPond: Boolean get() = objectId == POND_OBJECT_ID

    /** The sprinkler, which waters the beds round it while it is switched on. */
    val isSprinkler: Boolean get() = objectId == Sprinkler.OBJECT_ID

    /** The crate a new player opens for their first seed. */
    val isGiftBox: Boolean get() = objectId == GIFT_BOX_OBJECT_ID

    /** The garden's landmark building, of which there is one at most. */
    val isSymbol: Boolean get() = StructureData.isSymbol(objectId)

    /** Cells covered, starting at (gridX, gridY); 1x1 for everything but structures. */
    val footprint: Footprint get() = Footprint.forObjectId(objectId)

    fun occupiedCells(): List<GridCell> = footprint.cells(gridX, gridY)

    companion object {
        /** The size a new garden starts at; it can grow from there, see [GardenExpansion]. */
        const val GRID_SIZE = 11

        /**
         * The size gardens started at until 11 x 11 became the start: a garden saved before then
         * may still be this size, and grows from it through every stage of [GardenExpansion].
         */
        const val LEGACY_GRID_SIZE = 9
        const val PLANT_ID_PREFIX = "plant_"
        const val TERRAIN_ID_PREFIX = "terrain_"
        const val RIVER_OBJECT_ID = "terrain_river"
        const val FLOOR_ID_PREFIX = "floor_"
        const val GROUND_COVER_ID_PREFIX = "ground_"
        const val COBBLESTONE_OBJECT_ID = "floor_cobblestone"
        const val WOOD_DECK_OBJECT_ID = "floor_wood"

        /**
         * Tilled soil. It is stored like paving, in the floor layer of its cell: the plant grown
         * on it stands on top, and it and paving cannot share a cell.
         */
        /**
         * The buildings that can be turned to face the other way: drawn mirrored, which on the
         * isometric grid shows the side that faced away. Their footprints are square, so turning
         * one does not change the cells it stands on. The order is the one they are listed in.
         */
        val FLIPPABLE_OBJECT_IDS: List<String> = listOf("struct_cottage", "floor_greenhouse", "struct_windmill")

        fun canFaceEitherWay(objectId: String): Boolean = objectId in FLIPPABLE_OBJECT_IDS

        const val TILLED_SOIL_OBJECT_ID = "floor_tilled"

        /** A bed of soil under a small glass house; see [isGreenhouse]. */
        const val LOOSE_SOIL_OBJECT_ID = "floor_loose"
        const val GREENHOUSE_OBJECT_ID = "floor_greenhouse"

        const val FENCE_OBJECT_ID = "struct_fence"

        /**
         * The small wooden crate a new garden starts with (see [StarterGarden]). It is not an item:
         * it cannot be bought, moved or put away, and tapping it opens it, which uses it up.
         */
        const val GIFT_BOX_OBJECT_ID = "deco_starter_crate"

        /** The pond, bought in the shop and put down on a cell of its own. */
        const val POND_OBJECT_ID = "deco_pond"
    }
}

/**
 * The object ids a fence can be stored under. A fence is placed as a [SECTION] and how it looks
 * is worked out from the fences next to it, so the id says nothing about its shape. The other two
 * ids date from when the player picked a piece by hand; gardens saved then still hold them, and
 * they are fences like any other.
 */
enum class FencePart(val objectId: String) {
    /** What every new fence is stored as. Its sprite, a run of pales, is the shop's picture of a fence. */
    SECTION(GridObject.FENCE_OBJECT_ID),

    /** Stored by older versions only. Its sprite, one panel between two posts, is a fence standing alone (#55). */
    RAIL("struct_fence_rail"),

    /** Stored by older versions only. Its sprite is the post that joined fences are built round. */
    POST("struct_fence_post");

    companion object {
        fun fromObjectId(objectId: String): FencePart? = entries.firstOrNull { it.objectId == objectId }
    }
}

/** A job that takes time, done on something standing on the garden. */
enum class WorkKind {
    /** Nothing is being done: the object simply stands there. */
    NONE,

    /** Being built. It takes up its place already, but is not finished. */
    BUILDING,

    /**
     * Built, but still under wraps: the building time is over and it waits for the player to
     * tap it, which is what unveils it. Nothing about it depends on the clock any more.
     */
    READY,

    /** Being taken apart to be thrown away. When the job is done the object is gone. */
    DEMOLISHING,

    /** Being taken apart to go back into the bag. When the job is done it is an item again. */
    PACKING;

    companion object {
        /** Unknown values (e.g. written by a newer app version) count as no job. */
        fun fromName(name: String): WorkKind = entries.firstOrNull { it.name == name } ?: NONE
    }
}
