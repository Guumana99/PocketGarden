package com.studioguu.miniaturegarden.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.studioguu.miniaturegarden.data.reward.RewardLimitRepositoryImpl
import com.studioguu.miniaturegarden.domain.repository.RewardLimitRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RewardModule {

    @Binds
    @Singleton
    abstract fun bindRewardLimitRepository(impl: RewardLimitRepositoryImpl): RewardLimitRepository

    companion object {
        // Named, to keep it apart from the settings store; one DataStore per file.
        @Provides
        @Singleton
        @Named(RewardLimitRepositoryImpl.DATA_STORE_NAME)
        fun provideRewardDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create {
                context.preferencesDataStoreFile(RewardLimitRepositoryImpl.DATA_STORE_NAME)
            }
    }
}
