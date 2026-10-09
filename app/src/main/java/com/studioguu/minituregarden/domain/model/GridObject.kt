package com.studioguu.minituregarden.domain.model

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

    companion object {
        const val GRID_SIZE = 9
        const val PLANT_ID_PREFIX = "plant_"
    }
}
