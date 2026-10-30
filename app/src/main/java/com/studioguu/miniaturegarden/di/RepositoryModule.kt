package com.studioguu.miniaturegarden.di

import com.studioguu.miniaturegarden.data.repository.SwitchingGardenRepository
import com.studioguu.miniaturegarden.data.security.TrustedClock
import com.studioguu.miniaturegarden.domain.repository.GameClock
import com.studioguu.miniaturegarden.data.repository.SecurityRepositoryImpl
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.repository.PracticeMode
import com.studioguu.miniaturegarden.domain.repository.SecurityRepository
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
    abstract fun bindGardenRepository(impl: SwitchingGardenRepository): GardenRepository

    @Binds
    @Singleton
    abstract fun bindPracticeMode(impl: SwitchingGardenRepository): PracticeMode

    @Binds
    @Singleton
    abstract fun bindSecurityRepository(impl: SecurityRepositoryImpl): SecurityRepository

    @Binds
    @Singleton
    abstract fun bindGameClock(impl: TrustedClock): GameClock
}
