package com.studioguu.minituregarden.di

import android.content.Context
import androidx.room.Room
import com.studioguu.minituregarden.data.local.AppDatabase
import com.studioguu.minituregarden.data.local.dao.GridObjectDao
import com.studioguu.minituregarden.data.local.dao.InventorySlotDao
import com.studioguu.minituregarden.data.local.dao.UserProfileDao
import com.studioguu.minituregarden.data.security.SecurityManager
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
            .build()
    }

    @Provides
    fun provideUserProfileDao(database: AppDatabase): UserProfileDao = database.userProfileDao()

    @Provides
    fun provideGridObjectDao(database: AppDatabase): GridObjectDao = database.gridObjectDao()

    @Provides
    fun provideInventorySlotDao(database: AppDatabase): InventorySlotDao = database.inventorySlotDao()
}
