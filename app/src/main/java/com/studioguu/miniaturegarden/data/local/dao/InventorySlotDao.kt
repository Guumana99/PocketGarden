package com.studioguu.miniaturegarden.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.studioguu.miniaturegarden.data.local.entity.InventorySlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventorySlotDao {

    @Query("SELECT * FROM inventory_slots ORDER BY slotIndex")
    fun observeAll(): Flow<List<InventorySlotEntity>>

    @Query("SELECT * FROM inventory_slots ORDER BY slotIndex")
    suspend fun getAll(): List<InventorySlotEntity>

    @Upsert
    suspend fun upsert(slot: InventorySlotEntity)

    @Upsert
    suspend fun upsertAll(slots: List<InventorySlotEntity>)

    /** Adds missing slots without touching ones that already hold items. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(slots: List<InventorySlotEntity>)
}
