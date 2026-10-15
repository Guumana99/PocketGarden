package com.studioguu.miniaturegarden.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_slots")
data class InventorySlotEntity(
    /** 0 .. inventoryCapacity - 1 */
    @PrimaryKey val slotIndex: Int,
    /** "" for an empty slot. */
    val itemId: String = "",
    /** Max stack is 64. */
    val amount: Int = 0,
    /** Running number of when it last came into the bag; higher is more recent (v9). */
    @ColumnInfo(defaultValue = "0") val acquiredOrder: Long = 0L,
)
