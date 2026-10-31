package com.studioguu.miniaturegarden.data.local.entity

import androidx.room.ColumnInfo
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
    /** Times a repeatable crop has been harvested. Added in schema version 13. */
    @ColumnInfo(defaultValue = "0")
    val harvestCount: Int = 0,
    /** The grade of a cell of soil, 0 to 2. Added in schema version 14 (as 0 to 4). */
    @ColumnInfo(defaultValue = "0")
    val soilQuality: Int = 0,
    /** Whether a switchable thing (the sprinkler) is running. Added in schema version 14. */
    @ColumnInfo(defaultValue = "1")
    val isOn: Boolean = true,
    /** Of a cell of soil: points towards its next grade. Added in schema version 17. */
    @ColumnInfo(defaultValue = "0")
    val soilProgress: Int = 0,
    /** Of a cell of soil: the crop last harvested from it. Added in schema version 17. */
    val lastCropId: String? = null,
    /** Of a cell of soil: how long it has lain idle. Added in schema version 17. */
    @ColumnInfo(defaultValue = "0")
    val idleMillis: Long = 0L,
    /** Of a crop of several colours: the one it opened in. Added in schema version 18. */
    val bloomColour: String? = null,
    /** Of a gift box: what is inside. Added in schema version 18. */
    val gift: String? = null,
    /** Of something with a back: whether it is turned to show it. Added in schema version 19. */
    @ColumnInfo(defaultValue = "0")
    val turned: Boolean = false,
    /** Of a painted building: its colours; null for the picture's own. Added in schema version 19. */
    val roofColour: String? = null,
    val wallColour: String? = null,
)
