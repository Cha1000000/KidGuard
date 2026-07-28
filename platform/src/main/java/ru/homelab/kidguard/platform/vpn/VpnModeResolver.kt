package ru.homelab.kidguard.platform.vpn

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import ru.homelab.kidguard.core.domain.repository.InstalledAppsSource
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.shouldBlockInternet
import ru.homelab.kidguard.core.domain.usecase.vpnDisallowedPackages
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Вычисляет актуальный [VpnMode] по текущему состоянию политики (веха 6.x): нужен, чтобы
 * [KidGuardVpnService] мог сам решить, какой режим поднимать, а не полагаться исключительно на
 * extras от [VpnController] — системный always-on-перезапуск сервиса приходит без них.
 * Правило выбора режима — то же, что раньше жило в [VpnController]:
 * - лимит исчерпан ([shouldBlockInternet]) → blackhole, обходят только whitelist + сам KidGuard;
 * - время есть + активен запрет сайтов → DNS-фильтр;
 * - время есть, запрета сайтов нет → blackhole с обходом всеми (pass-through).
 */
@Singleton
class VpnModeResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val observeLimitStateUseCase: ObserveLimitStateUseCase,
    private val policyRepository: PolicyRepository,
    private val installedAppsSource: InstalledAppsSource
) {

    /** Одноразово читает текущее состояние политики и решает режим (для системного старта без extras). */
    suspend fun resolve(): VpnMode {
        val limitState = observeLimitStateUseCase().first()
        val whitelist = policyRepository.whitelist.first()
        val rules = policyRepository.siteBlockRules.first()
        return when {
            shouldBlockInternet(limitState) ->
                VpnMode.Blackhole(vpnDisallowedPackages(whitelist, context.packageName))
            rules.isActive -> VpnMode.DnsFilter(rules)
            else -> VpnMode.Blackhole(installedAppsSource.installedPackageNames().toSet() + context.packageName)
        }
    }

    /**
     * Реактивный поток режима: эмитит при изменении лимита, белого списка, набора установленных
     * приложений или правил запрета сайтов.
     */
    fun modeFlow(): Flow<VpnMode> = combine(
        observeLimitStateUseCase(),
        policyRepository.whitelist,
        installedAppsSource.observeInstalledPackageNames(),
        policyRepository.siteBlockRules
    ) { limitState, whitelist, installed, rules ->
        when {
            shouldBlockInternet(limitState) ->
                VpnMode.Blackhole(vpnDisallowedPackages(whitelist, context.packageName))
            rules.isActive -> VpnMode.DnsFilter(rules)
            else -> VpnMode.Blackhole(installed.toSet() + context.packageName)
        }
    }.distinctUntilChanged()
}
