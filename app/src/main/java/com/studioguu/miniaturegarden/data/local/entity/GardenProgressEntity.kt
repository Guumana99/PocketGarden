package com.studioguu.miniaturegarden.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** The garden's season and the player's first steps. Single-row table. Added in schema version 6. */
@Entity(tableName = "garden_progress")
data class GardenProgressEntity(
    /** Always [SINGLETON_ID]. */
    @PrimaryKey val id: Int = SINGLETON_ID,
    /** A Season name. */
    val season: String,
    /** A TutorialStep name. */
    val tutorialStep: String,
    /** Cells across. Added in schema version 7. */
    @ColumnInfo(defaultValue = "9") val gardenSize: Int = 9,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
