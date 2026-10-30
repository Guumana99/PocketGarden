package com.studioguu.miniaturegarden.di

import android.content.Context
import androidx.room.Room
import com.studioguu.miniaturegarden.data.local.AppDatabase
import com.studioguu.miniaturegarden.data.local.dao.CollectionEntryDao
import com.studioguu.miniaturegarden.data.local.dao.GardenProgressDao
import com.studioguu.miniaturegarden.data.local.dao.GridObjectDao
import com.studioguu.miniaturegarden.data.local.dao.InventorySlotDao
import com.studioguu.miniaturegarden.data.local.dao.LetterDao
import com.studioguu.miniaturegarden.data.local.dao.WeatherDayDao
import com.studioguu.miniaturegarden.data.local.dao.NatureNoteDao
import com.studioguu.miniaturegarden.data.local.dao.UserProfileDao
import com.studioguu.miniaturegarden.data.security.SecurityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        securityManager: SecurityManager,
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(securityManager.getDatabasePassphrase()))
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15, AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18)
            .build()
    }

    @Provides
    fun provideUserProfileDao(database: AppDatabase): UserProfileDao = database.userProfileDao()

    @Provides
    fun provideGridObjectDao(database: AppDatabase): GridObjectDao = database.gridObjectDao()

    @Provides
    fun provideInventorySlotDao(database: AppDatabase): InventorySlotDao = database.inventorySlotDao()

    @Provides
    fun provideCollectionEntryDao(database: AppDatabase): CollectionEntryDao = database.collectionEntryDao()

    @Provides
    fun provideGardenProgressDao(database: AppDatabase): GardenProgressDao = database.gardenProgressDao()

    @Provides
    fun provideNatureNoteDao(database: AppDatabase): NatureNoteDao = database.natureNoteDao()

    @Provides
    fun provideLetterDao(database: AppDatabase): LetterDao = database.letterDao()

    @Provides
    fun provideWeatherDayDao(database: AppDatabase): WeatherDayDao = database.weatherDayDao()
}
