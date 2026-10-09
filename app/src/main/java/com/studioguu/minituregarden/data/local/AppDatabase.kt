package com.studioguu.minituregarden.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.studioguu.minituregarden.data.local.dao.GridObjectDao
import com.studioguu.minituregarden.data.local.dao.InventorySlotDao
import com.studioguu.minituregarden.data.local.dao.UserProfileDao
import com.studioguu.minituregarden.data.local.entity.GridObjectEntity
import com.studioguu.minituregarden.data.local.entity.InventorySlotEntity
import com.studioguu.minituregarden.data.local.entity.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        GridObjectEntity::class,
        InventorySlotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun gridObjectDao(): GridObjectDao
    abstract fun inventorySlotDao(): InventorySlotDao

    companion object {
        const val NAME = "pocket_garden.db"
    }
}
