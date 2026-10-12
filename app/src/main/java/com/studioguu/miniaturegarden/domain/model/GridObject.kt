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
) {
    val isPlant: Boolean get() = objectId.startsWith(PLANT_ID_PREFIX)

    /** Terrain laid by the player (river). It is part of the ground: nothing can stand on it. */
    val isTerrain: Boolean get() = objectId.startsWith(TERRAIN_ID_PREFIX)

    /** Paving. It lies under whatever stands on the cell and does not take the cell up. */
    val isFloor: Boolean get() = objectId.startsWith(FLOOR_ID_PREFIX)

    /** Ground the player has broken up with the hoe. The only place a seed can go. */
    val isTilledSoil: Boolean get() = objectId == TILLED_SOIL_OBJECT_ID

    /**
     * Tilled soil that a withered plant was dug into. The next seed planted here starts off
     * ahead (see [COMPOST_GROWTH_BONUS]), which uses the compost up. Soil has no growth of its
     * own, so its otherwise unused [growthProgress] holds this.
     */
    val isComposted: Boolean get() = isTilledSoil && growthProgress > 0f

    /** A fence, whichever of its parts. */
    val isFence: Boolean get() = FencePart.fromObjectId(objectId) != null

    /** Cells covered, starting at (gridX, gridY); 1x1 for everything but structures. */
    val footprint: Footprint get() = Footprint.forObjectId(objectId)

    fun occupiedCells(): List<GridCell> = footprint.cells(gridX, gridY)

    companion object {
        const val GRID_SIZE = 9
        const val PLANT_ID_PREFIX = "plant_"
        const val TERRAIN_ID_PREFIX = "terrain_"
        const val RIVER_OBJECT_ID = "terrain_river"
        const val FLOOR_ID_PREFIX = "floor_"
        const val COBBLESTONE_OBJECT_ID = "floor_cobblestone"

        /**
         * Tilled soil. It is stored like paving, in the floor layer of its cell: the plant grown
         * on it stands on top, and it and paving cannot share a cell.
         */
        const val TILLED_SOIL_OBJECT_ID = "floor_tilled"

        /** Growth a seed starts with when it is planted in composted soil. */
        const val COMPOST_GROWTH_BONUS = 0.2f
        const val FENCE_OBJECT_ID = "struct_fence"
    }
}

/**
 * The pieces a fence can be shown as. Every piece stands upright on its one cell, facing the
 * viewer; none of them looks at its neighbours or changes shape to join them. A newly placed
 * fence is a [SECTION], and the player can switch a standing fence to another piece.
 */
enum class FencePart(val objectId: String) {
    /** Five pickets on one rail. */
    SECTION(GridObject.FENCE_OBJECT_ID),

    /** Two posts joined by two rails. */
    RAIL("struct_fence_rail"),

    /** A single post, for corners and ends. */
    POST("struct_fence_post");

    fun next(): FencePart = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromObjectId(objectId: String): FencePart? = entries.firstOrNull { it.objectId == objectId }
    }
}
