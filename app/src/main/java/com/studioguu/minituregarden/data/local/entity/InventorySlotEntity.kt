package com.studioguu.minituregarden.data.local.entity

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
)
