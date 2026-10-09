package com.studioguu.miniaturegarden.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.studioguu.miniaturegarden.data.local.dao.GridObjectDao
import com.studioguu.miniaturegarden.data.local.dao.InventorySlotDao
import com.studioguu.miniaturegarden.data.local.dao.UserProfileDao
import com.studioguu.miniaturegarden.data.local.entity.GridObjectEntity
import com.studioguu.miniaturegarden.data.local.entity.InventorySlotEntity
import com.studioguu.miniaturegarden.data.local.entity.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        GridObjectEntity::class,
        InventorySlotEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun gridObjectDao(): GridObjectDao
    abstract fun inventorySlotDao(): InventorySlotDao

    companion object {
        const val NAME = "pocket_garden.db"

        /** v2 adds the player's experience points. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profile ADD COLUMN currentXp INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
