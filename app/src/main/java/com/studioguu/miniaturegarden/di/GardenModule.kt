package com.studioguu.miniaturegarden.di

import com.studioguu.miniaturegarden.domain.model.GardenMapSource
import com.studioguu.miniaturegarden.domain.usecase.StoredGardenMap
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GardenModule {

    /** The terrain everything is placed on: lawn, as large as the garden has grown. */
    @Binds
    abstract fun bindGardenMapSource(impl: StoredGardenMap): GardenMapSource
}
