package ru.homelab.kidguard.platform.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.homelab.kidguard.core.domain.model.SiteBlockRules
import ru.homelab.kidguard.core.domain.repository.InstalledAppsSource
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.shouldBlockInternet
import ru.homelab.kidguard.core.domain.usecase.vpnDisallowedPackages
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Связывает лимит, белый список и запрет сайтов с нашим VpnService. VPN активен **всегда**
 * (пока устройство под контролем) — для совместимости с системным always-on/lockdown. Режим
 * выбирается по состоянию (веха 5.4 + запрет сайтов):
 * - лимит исчерпан ([shouldBlockInternet]) → **blackhole**, обходят только KidGuard + белый
 *   список (интернет заблокирован у остальных);
 * - время доступно + активен запрет сайтов ([SiteBlockRules.isActive]) → **DNS-фильтр**
 *   (split-tunnel: в tun только DNS, блокированные домены → NXDOMAIN, остальное напрямую);
 * - время доступно, запрета сайтов нет → **blackhole** с обходом всеми (pass-through, интернет у всех).
 * Смена лимита/белого списка/набора приложений/правил сайтов на лету пересоздаёт tun с новым
 * режимом. Запускается foreground-сервисом, как и [ru.homelab.kidguard.platform.overlay.BlockingController].
 */
@Singleton
class VpnController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val observeLimitStateUseCase: ObserveLimitStateUseCase,
    private val policyRepository: PolicyRepository,
    private val installedAppsSource: InstalledAppsSource
) {

    /** Режим, в котором должен работать VpnService. */
    private sealed interface Mode {
        /** Блокировка/pass-through набором disallowed-пакетов (tun-blackhole). */
        data class Blackhole(val disallowed: Set<String>) : Mode
        /** DNS-фильтр по правилам запрета сайтов. */
        data class DnsFilter(val rules: SiteBlockRules) : Mode
    }

    suspend fun run() {
        Timber.tag(TAG).d("Контроллер VPN запущен")
        // Список установленных пакетов наблюдаем реактивно (эмитит при установке/удалении приложения),
        // чтобы pass-through сразу подхватил только что установленное приложение.
        combine(
            observeLimitStateUseCase(),
            policyRepository.whitelist,
            installedAppsSource.observeInstalledPackageNames(),
            policyRepository.siteBlockRules
        ) { limitState, whitelist, installed, rules ->
            when {
                // Лимит исчерпан — блокируем интернет (обходят только whitelist + сам KidGuard).
                shouldBlockInternet(limitState) ->
                    Mode.Blackhole(vpnDisallowedPackages(whitelist, context.packageName))
                // Время есть и заданы правила запрета сайтов — DNS-фильтр.
                rules.isActive -> Mode.DnsFilter(rules)
                // Время есть, запрета сайтов нет — pass-through (интернет у всех).
                else -> Mode.Blackhole(installed.toSet() + context.packageName)
            }
        }.distinctUntilChanged().collect { mode -> applyMode(mode) }
    }

    private fun applyMode(mode: Mode) {
        // Consent на VPN выдаётся один раз в мастере разрешений (VpnService.prepare == null,
        // когда согласие уже есть). Если его нет — просто не поднимаем VPN и не падаем: оверлей
        // (первый барьер) продолжает работать независимо.
        if (VpnService.prepare(context) != null) {
            Timber.tag(TAG).w("Нет согласия на VPN — tun не поднят")
            return
        }
        val intent = Intent(context, KidGuardVpnService::class.java)
            .setAction(KidGuardVpnService.ACTION_START)
        when (mode) {
            is Mode.Blackhole -> {
                intent
                    .putExtra(KidGuardVpnService.EXTRA_MODE, KidGuardVpnService.MODE_BLACKHOLE)
                    .putStringArrayListExtra(KidGuardVpnService.EXTRA_DISALLOWED, ArrayList(mode.disallowed))
                Timber.tag(TAG).d("VPN blackhole, disallowed=%s", mode.disallowed)
            }
            is Mode.DnsFilter -> {
                intent
                    .putExtra(KidGuardVpnService.EXTRA_MODE, KidGuardVpnService.MODE_DNS_FILTER)
                    .putStringArrayListExtra(
                        KidGuardVpnService.EXTRA_BLOCKED_DOMAINS,
                        ArrayList(mode.rules.blockedDomains)
                    )
                    .putExtra(KidGuardVpnService.EXTRA_BLOCK_GOOGLE, mode.rules.blockGoogleSearch)
                Timber.tag(TAG).d(
                    "VPN DNS-фильтр, доменов=%d, google=%s",
                    mode.rules.blockedDomains.size, mode.rules.blockGoogleSearch
                )
            }
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private companion object {
        const val TAG = "KidGuardVpnCtrl"
    }
}
