package ru.homelab.kidguard.platform.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.SiteBlockRules
import ru.homelab.kidguard.platform.R
import ru.homelab.kidguard.platform.vpn.dns.DnsProxyLoop
import timber.log.Timber
import javax.inject.Inject

/**
 * VPN-сервис с двумя режимами (веха 5.2 → 6.x):
 * - [MODE_BLACKHOLE] — поднимает tun-интерфейс, заворачивающий весь трафик устройства, и
 *   ничего из него не читает/не пишет — трафик просто дропается ядром. Приложения из
 *   [EXTRA_DISALLOWED] добавлены как `addDisallowedApplication` и обходят VPN, ходя в сеть
 *   напрямую — остальные попадают в tun и теряют интернет.
 * - [MODE_DNS_FILTER] — split-tunnel: в tun завёрнут только DNS (виртуальный сервер
 *   [DNS_VIRTUAL_IP]), остальной трафик идёт мимо VPN как обычно. [DnsProxyLoop] читает
 *   DNS-запросы из tun и либо отвечает NXDOMAIN для заблокированных доменов ([SiteBlockRules]),
 *   либо форвардит запрос на реальный upstream-DNS.
 *
 * С вехи 5.4 blackhole-режим активен всегда (совместимость с always-on/lockdown), а между
 * блокировкой и pass-through (интернет у всех) переключает состав [EXTRA_DISALLOWED], который
 * пересчитывает [VpnController]. Сервис самодостаточен: если он поднят системой напрямую (always-on
 * VPN после перезагрузки/обновления), то есть без наших extras, — сам спрашивает актуальный режим
 * у [VpnModeResolver], не дожидаясь [VpnController].
 */
@AndroidEntryPoint
class KidGuardVpnService : VpnService() {

    @Inject
    lateinit var vpnModeResolver: VpnModeResolver

    private var tunFd: ParcelFileDescriptor? = null
    private var dnsLoop: DnsProxyLoop? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                closeTun()
                stopSelf()
            }
            else -> {
                // ACTION_START (явный запуск от VpnController) либо системный always-on-рестарт
                // без action/extras вовсе — в обоих случаях поднимаем tun.
                startVpnForeground()
                if (intent?.hasExtra(EXTRA_MODE) == true) {
                    // Явный путь: режим уже посчитан VpnController и приехал в extras.
                    establishForMode(modeFromIntent(intent))
                } else {
                    // Системный старт без extras — сами читаем актуальный режим из политики.
                    Timber.tag(TAG).d("Старт без extras (%s) — читаем режим сами", intent?.action)
                    scope.launch { establishForMode(vpnModeResolver.resolve()) }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        closeTun()
        scope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        // Пользователь отозвал VPN-согласие в системных настройках — снимаем tun и себя.
        Timber.tag(TAG).w("VPN-согласие отозвано системой")
        closeTun()
        stopSelf()
        super.onRevoke()
    }

    /** Диспетчеризует режим на существующие establish-функции для tun. */
    private fun establishForMode(mode: VpnMode) {
        when (mode) {
            is VpnMode.Blackhole -> establishTun(mode.disallowed.toList())
            is VpnMode.DnsFilter -> establishDnsFilterTun(mode.rules)
        }
    }

    /** Собирает [VpnMode] из extras явного запуска (см. [VpnController.applyMode]). */
    private fun modeFromIntent(intent: Intent): VpnMode = when (intent.getStringExtra(EXTRA_MODE)) {
        MODE_DNS_FILTER -> {
            val blockedDomains = intent.getStringArrayListExtra(EXTRA_BLOCKED_DOMAINS).orEmpty().toSet()
            val blockGoogle = intent.getBooleanExtra(EXTRA_BLOCK_GOOGLE, false)
            VpnMode.DnsFilter(SiteBlockRules(blockedDomains, blockGoogle))
        }
        else -> {
            val disallowed = intent.getStringArrayListExtra(EXTRA_DISALLOWED).orEmpty().toSet()
            VpnMode.Blackhole(disallowed)
        }
    }

    /**
     * Поднимает blackhole-tun заново с актуальным disallowed-набором. При повторном вызове
     * (сменился whitelist, либо был поднят DNS-режим) сначала закрывает старый fd/loop —
     * `establish()` создаёт новый интерфейс.
     */
    private fun establishTun(disallowed: List<String>) {
        closeTun()
        val builder = Builder()
            .setSession("KidGuard")
            .addAddress(TUN_ADDRESS, 32)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
        disallowed.forEach { packageName ->
            runCatching { builder.addDisallowedApplication(packageName) }
                .onFailure { Timber.tag(TAG).w(it, "Не удалось добавить в disallowed: %s", packageName) }
        }
        tunFd = runCatching { builder.establish() }
            .onFailure { Timber.tag(TAG).e(it, "Не удалось поднять blackhole-tun") }
            .getOrNull()
        if (tunFd != null) {
            Timber.tag(TAG).d("Blackhole-tun поднят, disallowed=%s", disallowed)
        }
        // tunFd намеренно не читаем и не пишем — это и есть blackhole: весь трафик, попавший в
        // tun, ядро просто отбрасывает.
    }

    /**
     * Поднимает split-tunnel tun: в него завёрнут только DNS-трафик (адрес [DNS_VIRTUAL_IP]
     * прописан как единственный DNS-сервер и единственный маршрут) — остальной трафик устройства
     * идёт мимо VPN как обычно. Сам KidGuard тоже обходит VPN (`addDisallowedApplication`),
     * чтобы форвардинг DNS через собственный сокет (`protect()`) не зациклился на себя.
     * [DnsProxyLoop] запускается сразу после успешного `establish()`.
     */
    private fun establishDnsFilterTun(rules: SiteBlockRules) {
        closeTun()
        val builder = Builder()
            .setSession("KidGuard")
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(DNS_VIRTUAL_IP)
            .addRoute(DNS_VIRTUAL_IP, 32)
        runCatching { builder.addDisallowedApplication(packageName) }
            .onFailure { Timber.tag(TAG).w(it, "Не удалось добавить себя в disallowed") }
        val fd = runCatching { builder.establish() }
            .onFailure { Timber.tag(TAG).e(it, "Не удалось поднять dns-filter tun") }
            .getOrNull()
        tunFd = fd
        if (fd != null) {
            Timber.tag(TAG).d("DNS-filter tun поднят, rules.isActive=%s", rules.isActive)
            dnsLoop = DnsProxyLoop(
                tunFd = fd,
                rules = rules,
                protect = { socket -> protect(socket) }
            ).also { it.start() }
        }
    }

    private fun closeTun() {
        dnsLoop?.stop()
        dnsLoop = null
        tunFd?.let { fd -> runCatching { fd.close() } }
        tunFd = null
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun startVpnForeground() {
        // minSdk = 33, поэтому трёхаргументный startForeground (с типом FGS) доступен всегда.
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    companion object {
        private const val TAG = "KidGuardVpnSvc"
        private const val CHANNEL_ID = "kidguard_vpn"
        private const val NOTIFICATION_ID = 3
        private const val TUN_ADDRESS = "10.111.222.1"

        /** Виртуальный DNS-сервер, который видит система в режиме [MODE_DNS_FILTER]. */
        private const val DNS_VIRTUAL_IP = "10.111.222.2"

        const val ACTION_START = "ru.homelab.kidguard.platform.vpn.action.START"
        const val ACTION_STOP = "ru.homelab.kidguard.platform.vpn.action.STOP"

        /** `ArrayList<String>` пакетов, которые должны обходить VPN (режим [MODE_BLACKHOLE]). */
        const val EXTRA_DISALLOWED = "ru.homelab.kidguard.platform.vpn.extra.DISALLOWED"

        /** Режим VPN: [MODE_BLACKHOLE] (по умолчанию, если extra отсутствует) или [MODE_DNS_FILTER]. */
        const val EXTRA_MODE = "ru.homelab.kidguard.platform.vpn.extra.MODE"

        /** `ArrayList<String>` заблокированных доменов (режим [MODE_DNS_FILTER]). */
        const val EXTRA_BLOCKED_DOMAINS = "ru.homelab.kidguard.platform.vpn.extra.BLOCKED_DOMAINS"

        /** `Boolean` — блокировать ли google.com/www.google.com (режим [MODE_DNS_FILTER]). */
        const val EXTRA_BLOCK_GOOGLE = "ru.homelab.kidguard.platform.vpn.extra.BLOCK_GOOGLE"

        /** Прежнее поведение: весь трафик в tun дропается, [EXTRA_DISALLOWED] обходит VPN. */
        const val MODE_BLACKHOLE = "blackhole"

        /** Split-tunnel DNS-фильтр: в tun завёрнут только DNS, см. [DnsProxyLoop]. */
        const val MODE_DNS_FILTER = "dns_filter"
    }
}
