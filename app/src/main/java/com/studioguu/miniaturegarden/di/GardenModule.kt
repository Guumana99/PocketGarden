package com.studioguu.miniaturegarden.di

import com.studioguu.miniaturegarden.domain.model.GardenMap
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object GardenModule {

    /** The terrain everything is placed on and drawn over. */
    @Provides
    fun provideGardenMap(): GardenMap = GardenMap.DEFAULT
}
