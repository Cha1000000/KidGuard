package ru.homelab.kidguard.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.SettingsRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import ru.homelab.kidguard.data.policy.PolicyRepositoryImpl
import ru.homelab.kidguard.data.settings.SettingsRepositoryImpl
import ru.homelab.kidguard.data.usage.UsageRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPolicyRepository(impl: PolicyRepositoryImpl): PolicyRepository

    @Binds
    @Singleton
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository
}
