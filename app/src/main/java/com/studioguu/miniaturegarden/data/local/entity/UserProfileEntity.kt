package com.studioguu.miniaturegarden.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    /** Single-row table: always [SINGLETON_ID]. */
    @PrimaryKey val id: Int = SINGLETON_ID,
    val coins: Long = 0L,
    val playerLevel: Int = 1,
    /** Total experience earned. Added in schema version 2. */
    @ColumnInfo(defaultValue = "0") val currentXp: Long = 0L,
    val inventoryCapacity: Int = 16,
    /** Epoch millis (System.currentTimeMillis) at the last save. */
    val lastUpdatedTimestamp: Long = 0L,
    /** SystemClock.elapsedRealtime at the last save. */
    val lastMonoClockTime: Long = 0L,
    /** HMAC-SHA256 over the other columns. */
    val signature: String = "",
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
