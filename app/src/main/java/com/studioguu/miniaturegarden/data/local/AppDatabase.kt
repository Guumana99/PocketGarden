package com.studioguu.miniaturegarden.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.studioguu.miniaturegarden.data.local.dao.CollectionEntryDao
import com.studioguu.miniaturegarden.data.local.dao.GardenProgressDao
import com.studioguu.miniaturegarden.data.local.dao.GridObjectDao
import com.studioguu.miniaturegarden.data.local.dao.InventorySlotDao
import com.studioguu.miniaturegarden.data.local.dao.LetterDao
import com.studioguu.miniaturegarden.data.local.dao.NatureNoteDao
import com.studioguu.miniaturegarden.data.local.dao.UserProfileDao
import com.studioguu.miniaturegarden.data.local.entity.CollectionEntryEntity
import com.studioguu.miniaturegarden.data.local.entity.GardenProgressEntity
import com.studioguu.miniaturegarden.data.local.entity.GridObjectEntity
import com.studioguu.miniaturegarden.data.local.entity.InventorySlotEntity
import com.studioguu.miniaturegarden.data.local.entity.LetterEntity
import com.studioguu.miniaturegarden.data.local.entity.NatureNoteEntity
import com.studioguu.miniaturegarden.data.local.entity.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        GridObjectEntity::class,
        InventorySlotEntity::class,
        CollectionEntryEntity::class,
        GardenProgressEntity::class,
        NatureNoteEntity::class,
        LetterEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun gridObjectDao(): GridObjectDao
    abstract fun inventorySlotDao(): InventorySlotDao
    abstract fun collectionEntryDao(): CollectionEntryDao
    abstract fun gardenProgressDao(): GardenProgressDao
    abstract fun natureNoteDao(): NatureNoteDao
    abstract fun letterDao(): LetterDao

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

        /**
         * v6 adds the garden's progress: its season and the player's first steps. The row itself
         * is written by the repository at start-up, where a garden that already exists is given
         * one with the first steps done.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `garden_progress` (" +
                        "`id` INTEGER NOT NULL, `season` TEXT NOT NULL, `tutorialStep` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        /** v7: the garden can grow. Every garden so far is the starting 9 x 9. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE garden_progress ADD COLUMN gardenSize INTEGER NOT NULL DEFAULT 9")
            }
        }

        /** The table v8 adds, exactly as Room expects it (checked against the exported schema by a test). */
        const val CREATE_NATURE_NOTES =
            "CREATE TABLE IF NOT EXISTS `nature_notes` (`noteId` TEXT NOT NULL, `firstSeenAt` INTEGER NOT NULL, " +
                "`count` INTEGER NOT NULL, PRIMARY KEY(`noteId`))"

        /** v8 adds the nature notes. Nobody has met anything yet. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_NATURE_NOTES)
            }
        }

        /** v9 remembers when each slot was last filled, for sorting the bag. What is there already counts as oldest. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_slots ADD COLUMN acquiredOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** The table v10 adds, exactly as Room expects it (checked against the exported schema by a test). */
        const val CREATE_LETTERS =
            "CREATE TABLE IF NOT EXISTS `letters` (`letterId` TEXT NOT NULL, `kind` TEXT NOT NULL, `season` TEXT NOT NULL, " +
                "`sentAt` INTEGER NOT NULL, `read` INTEGER NOT NULL, PRIMARY KEY(`letterId`))"

        /**
         * v10 brings the seasons: the post, and when the current season began. The season clock of
         * a garden saved before starts at its next sync (0 means "not started").
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_LETTERS)
                db.execSQL("ALTER TABLE garden_progress ADD COLUMN seasonStartedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Nature notes remember whether their page has been looked at. Creatures already met are not new. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE nature_notes ADD COLUMN opened INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** v12: letters can carry a topic (a tip, new stock). The season letters already there have none. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE letters ADD COLUMN topic TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v13: plants count their harvests, for the crops that bear again. Nothing has been harvested yet. */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE grid_objects ADD COLUMN harvestCount INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
