package com.studioguu.miniaturegarden.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "grid_objects")
data class GridObjectEntity(
    /** UUID. */
    @PrimaryKey val instanceId: String,
    /** 0 .. 8 */
    val gridX: Int,
    /** 0 .. 8 */
    val gridY: Int,
    val heightLevel: Int = 0,
    val isFlipped: Boolean = false,
    /** Catalog id, e.g. "plant_tulip". */
    val objectId: String,
    val growthProgress: Float = 0f,
    val moistureLevel: Float = 1f,
    val nutritionLevel: Float = 1f,
    val stressRate: Float = 0f,
    /** PlantStatus name: "HEALTHY", "THIRSTY", "UNNOURISHED" or "WITHERED". */
    val status: String = "HEALTHY",
    /** Epoch millis. */
    val lastUpdatedTimestamp: Long,
    /** WorkKind name: "NONE", "BUILDING", "DEMOLISHING" or "PACKING". Added in schema version 5. */
    val workKind: String = "NONE",
    /** Epoch millis; 0 without a job. Added in schema version 5. */
    val workStartedAt: Long = 0L,
    val workEndsAt: Long = 0L,
)
