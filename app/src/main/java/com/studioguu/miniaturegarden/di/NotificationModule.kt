package com.studioguu.miniaturegarden.di

import com.studioguu.miniaturegarden.data.notifications.WorkManagerNotificationScheduler
import com.studioguu.miniaturegarden.domain.repository.NotificationScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    abstract fun bindNotificationScheduler(impl: WorkManagerNotificationScheduler): NotificationScheduler
}
