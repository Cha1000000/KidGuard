package ru.homelab.kidguard.platform.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.homelab.kidguard.core.domain.repository.DeviceHealthSource
import ru.homelab.kidguard.core.domain.repository.InstalledAppsSource
import ru.homelab.kidguard.platform.apps.PlatformInstalledAppsSource
import ru.homelab.kidguard.platform.permissions.PlatformDeviceHealthSource
import javax.inject.Singleton

/** Бинды platform-реализаций доменных интерфейсов (системные интеграции). */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds
    @Singleton
    abstract fun bindInstalledAppsSource(impl: PlatformInstalledAppsSource): InstalledAppsSource

    @Binds
    @Singleton
    abstract fun bindDeviceHealthSource(impl: PlatformDeviceHealthSource): DeviceHealthSource
}
