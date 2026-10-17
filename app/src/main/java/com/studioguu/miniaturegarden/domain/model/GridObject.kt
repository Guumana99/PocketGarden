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

    /** Terrain laid by the player (river). It is part of the ground: nothing can stand on it. */
    val isTerrain: Boolean get() = objectId.startsWith(TERRAIN_ID_PREFIX)

    /** Paving. It lies under whatever stands on the cell and does not take the cell up. */
    val isFloor: Boolean get() = objectId.startsWith(FLOOR_ID_PREFIX)

    /** Ground the player has broken up with the hoe. The only place a seed can go. */
    val isTilledSoil: Boolean get() = objectId == TILLED_SOIL_OBJECT_ID

    /**
     * A glass greenhouse bed: tilled soil under glass. Seeds can be sown in it like in tilled
     * soil, and in any season.
     */
    val isGreenhouse: Boolean get() = objectId == GREENHOUSE_OBJECT_ID

    /**
     * Tilled soil that a withered plant was dug into. The next seed planted here starts off
     * ahead (see [COMPOST_GROWTH_BONUS]), which uses the compost up. Soil has no growth of its
     * own, so its otherwise unused [growthProgress] holds this.
     */
    val isComposted: Boolean get() = isTilledSoil && growthProgress > 0f

    /** A fence, under whichever of its ids. Fences join up with the fences next to them. */
    val isFence: Boolean get() = FencePart.fromObjectId(objectId) != null

    /** The crate a new player opens for their first seed. */
    val isStarterCrate: Boolean get() = objectId == STARTER_CRATE_OBJECT_ID

    /** The garden's landmark building, of which there is one at most. */
    val isSymbol: Boolean get() = StructureData.isSymbol(objectId)

    /** Cells covered, starting at (gridX, gridY); 1x1 for everything but structures. */
    val footprint: Footprint get() = Footprint.forObjectId(objectId)

    fun occupiedCells(): List<GridCell> = footprint.cells(gridX, gridY)

    companion object {
        /** The size a garden starts at; it can grow from there, see [GardenExpansion]. */
        const val GRID_SIZE = 9
        const val PLANT_ID_PREFIX = "plant_"
        const val TERRAIN_ID_PREFIX = "terrain_"
        const val RIVER_OBJECT_ID = "terrain_river"
        const val FLOOR_ID_PREFIX = "floor_"
        const val COBBLESTONE_OBJECT_ID = "floor_cobblestone"
        const val WOOD_DECK_OBJECT_ID = "floor_wood"

        /**
         * Tilled soil. It is stored like paving, in the floor layer of its cell: the plant grown
         * on it stands on top, and it and paving cannot share a cell.
         */
        const val TILLED_SOIL_OBJECT_ID = "floor_tilled"

        /** A bed of soil under a small glass house; see [isGreenhouse]. */
        const val GREENHOUSE_OBJECT_ID = "floor_greenhouse"

        /** Growth a seed starts with when it is planted in composted soil. */
        const val COMPOST_GROWTH_BONUS = 0.2f
        const val FENCE_OBJECT_ID = "struct_fence"

        /**
         * The small wooden crate a new garden starts with (see [StarterGarden]). It is not an item:
         * it cannot be bought, moved or put away, and tapping it opens it, which uses it up.
         */
        const val STARTER_CRATE_OBJECT_ID = "deco_starter_crate"
    }
}

/**
 * The object ids a fence can be stored under. A fence is placed as a [SECTION] and how it looks
 * is worked out from the fences next to it, so the id says nothing about its shape. The other two
 * ids date from when the player picked a piece by hand; gardens saved then still hold them, and
 * they are fences like any other.
 */
enum class FencePart(val objectId: String) {
    /** What every new fence is stored as. Its sprite is the stand-alone section. */
    SECTION(GridObject.FENCE_OBJECT_ID),

    /** Stored by older versions only. Its sprite is not used any more. */
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
