package ru.homelab.kidguard.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.homelab.kidguard.core.domain.repository.AuthRepository
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.ChildRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.PolicySyncRepository
import ru.homelab.kidguard.core.domain.repository.SettingsRepository
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import ru.homelab.kidguard.data.auth.AuthRepositoryImpl
import ru.homelab.kidguard.data.bonus.BonusRepositoryImpl
import ru.homelab.kidguard.data.children.ChildRepositoryImpl
import ru.homelab.kidguard.data.date.CurrentDateProviderImpl
import ru.homelab.kidguard.data.policy.PolicyRepositoryImpl
import ru.homelab.kidguard.data.settings.SettingsRepositoryImpl
import ru.homelab.kidguard.data.sync.PolicySyncRepositoryImpl
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

    @Binds
    @Singleton
    abstract fun bindCurrentDateProvider(impl: CurrentDateProviderImpl): CurrentDateProvider

    @Binds
    @Singleton
    abstract fun bindBonusRepository(impl: BonusRepositoryImpl): BonusRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindChildRepository(impl: ChildRepositoryImpl): ChildRepository

    @Binds
    @Singleton
    abstract fun bindPolicySyncRepository(impl: PolicySyncRepositoryImpl): PolicySyncRepository
}
