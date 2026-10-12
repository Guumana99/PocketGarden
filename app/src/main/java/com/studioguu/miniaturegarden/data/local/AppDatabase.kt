package com.studioguu.miniaturegarden.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.studioguu.miniaturegarden.data.local.dao.CollectionEntryDao
import com.studioguu.miniaturegarden.data.local.dao.GridObjectDao
import com.studioguu.miniaturegarden.data.local.dao.InventorySlotDao
import com.studioguu.miniaturegarden.data.local.dao.UserProfileDao
import com.studioguu.miniaturegarden.data.local.entity.CollectionEntryEntity
import com.studioguu.miniaturegarden.data.local.entity.GridObjectEntity
import com.studioguu.miniaturegarden.data.local.entity.InventorySlotEntity
import com.studioguu.miniaturegarden.data.local.entity.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        GridObjectEntity::class,
        InventorySlotEntity::class,
        CollectionEntryEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun gridObjectDao(): GridObjectDao
    abstract fun inventorySlotDao(): InventorySlotDao
    abstract fun collectionEntryDao(): CollectionEntryDao

    companion object {
        const val NAME = "pocket_garden.db"

        /** v2 adds the player's experience points. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profile ADD COLUMN currentXp INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 adds the garden encyclopedia. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collection_entries` (" +
                        "`entryId` TEXT NOT NULL, `count` INTEGER NOT NULL, " +
                        "`firstObtainedAt` INTEGER NOT NULL, PRIMARY KEY(`entryId`))"
                )
            }
        }

        /**
         * v4: pages are filled in by the player instead of by themselves. Pages that exist
         * already were filled in (and paid for) under the old rule, so they stay filled in.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collection_entries ADD COLUMN revealed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE collection_entries SET revealed = 1")
            }
        }

        /** v5: building and taking apart take time. Everything that exists already is finished. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE grid_objects ADD COLUMN workKind TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE grid_objects ADD COLUMN workStartedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE grid_objects ADD COLUMN workEndsAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
