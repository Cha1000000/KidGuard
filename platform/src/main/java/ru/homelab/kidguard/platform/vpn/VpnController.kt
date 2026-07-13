package ru.homelab.kidguard.platform.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.homelab.kidguard.core.domain.repository.InstalledAppsSource
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.vpnDisallowedFor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Связывает состояние общего дневного лимита и белый список с blackhole-VPN (веха 5.4).
 * VPN активен **всегда** (пока устройство под контролем) — это нужно для совместимости
 * с системным always-on VPN + lockdown, который родитель включает вручную: lockdown режет
 * интернет, когда VPN не активен, поэтому снимать tun по лимиту больше нельзя. Вместо этого
 * режим переключается набором disallowed-приложений: `Expired` → только KidGuard и белый
 * список обходят tun (блокировка), иначе → обходят вообще все установленные пакеты
 * (pass-through, интернет у всех). Смена лимита или белого списка на лету пересоздаёт tun
 * с новым disallowed-набором. Запускается foreground-сервисом, как и
 * [ru.homelab.kidguard.platform.overlay.BlockingController].
 */
@Singleton
class VpnController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val observeLimitStateUseCase: ObserveLimitStateUseCase,
    private val policyRepository: PolicyRepository,
    private val installedAppsSource: InstalledAppsSource
) {

    suspend fun run() {
        Timber.tag(TAG).d("Контроллер VPN запущен")
        // Список установленных пакетов меняется редко — читаем один раз при старте контроллера,
        // а не на каждое изменение лимита/whitelist.
        val allInstalled = installedAppsSource.installedPackageNames().toSet()
        combine(
            observeLimitStateUseCase(),
            policyRepository.whitelist
        ) { limitState, whitelist ->
            vpnDisallowedFor(limitState, whitelist, allInstalled, context.packageName)
        }.distinctUntilChanged().collect { disallowed ->
            startVpn(disallowed)
        }
    }

    private fun startVpn(disallowed: Set<String>) {
        // Consent на VPN выдаётся один раз в мастере разрешений (VpnService.prepare == null,
        // когда согласие уже есть). Если его нет — просто не поднимаем VPN и не падаем: оверлей
        // (первый барьер) продолжает работать независимо.
        if (VpnService.prepare(context) != null) {
            Timber.tag(TAG).w("Нет согласия на VPN — blackhole-tun не поднят")
            return
        }
        val intent = Intent(context, KidGuardVpnService::class.java)
            .setAction(KidGuardVpnService.ACTION_START)
            .putStringArrayListExtra(KidGuardVpnService.EXTRA_DISALLOWED, ArrayList(disallowed))
        ContextCompat.startForegroundService(context, intent)
        Timber.tag(TAG).d("VPN активен, disallowed=%s", disallowed)
    }

    private companion object {
        const val TAG = "KidGuardVpnCtrl"
    }
}
