package ru.homelab.kidguard.platform.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.usecase.ObserveLimitStateUseCase
import ru.homelab.kidguard.core.domain.usecase.shouldBlockInternet
import ru.homelab.kidguard.core.domain.usecase.vpnDisallowedPackages
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Связывает состояние общего дневного лимита и белый список с blackhole-VPN (веха 5.3):
 * `Expired` → поднять tun (интернет только у KidGuard и белого списка), иначе → снять.
 * Смена белого списка на лету пересоздаёт tun с новым disallowed-набором. Запускается
 * foreground-сервисом, как и [ru.homelab.kidguard.platform.overlay.BlockingController].
 */
@Singleton
class VpnController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val observeLimitStateUseCase: ObserveLimitStateUseCase,
    private val policyRepository: PolicyRepository
) {

    suspend fun run() {
        Timber.tag(TAG).d("Контроллер VPN запущен")
        combine(
            observeLimitStateUseCase(),
            policyRepository.whitelist
        ) { limitState, whitelist ->
            shouldBlockInternet(limitState) to vpnDisallowedPackages(whitelist, context.packageName)
        }.distinctUntilChanged().collect { (blockInternet, disallowed) ->
            if (blockInternet) {
                startVpn(disallowed)
            } else {
                stopVpn()
            }
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
        Timber.tag(TAG).d("Интернет заблокирован, disallowed=%s", disallowed)
    }

    private fun stopVpn() {
        // stopService, а не startService+ACTION_STOP: если сервис ещё не поднят (обычный случай
        // до истечения лимита), это no-op и не будит сервис попусту. Если поднят — Android сам
        // вызовет onDestroy(), где tun закрывается.
        val intent = Intent(context, KidGuardVpnService::class.java)
        context.stopService(intent)
        Timber.tag(TAG).d("Интернет разблокирован")
    }

    private companion object {
        const val TAG = "KidGuardVpnCtrl"
    }
}
