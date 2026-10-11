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
        const val FENCE_OBJECT_ID = "struct_fence"
    }
}
