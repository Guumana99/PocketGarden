package com.studioguu.minituregarden.di

import com.studioguu.minituregarden.data.repository.GardenRepositoryImpl
import com.studioguu.minituregarden.data.repository.SecurityRepositoryImpl
import com.studioguu.minituregarden.domain.repository.GardenRepository
import com.studioguu.minituregarden.domain.repository.SecurityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGardenRepository(impl: GardenRepositoryImpl): GardenRepository

    @Binds
    @Singleton
    abstract fun bindSecurityRepository(impl: SecurityRepositoryImpl): SecurityRepository
}
