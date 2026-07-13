package ru.homelab.kidguard.platform.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import ru.homelab.kidguard.platform.R
import timber.log.Timber

/**
 * Blackhole-VPN (веха 5.2): поднимает tun-интерфейс, заворачивающий весь трафик устройства, и
 * ничего из него не читает/не пишет — трафик просто дропается ядром. Приложения из
 * [EXTRA_DISALLOWED] (само KidGuard + белый список) добавлены как `addDisallowedApplication` и
 * обходят VPN, ходя в сеть напрямую. Поднимается/снимается контроллером [VpnController] в
 * зависимости от состояния дневного лимита.
 */
class KidGuardVpnService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startVpnForeground()
                val disallowed = intent.getStringArrayListExtra(EXTRA_DISALLOWED).orEmpty()
                establishTun(disallowed)
            }
            ACTION_STOP -> {
                closeTun()
                stopSelf()
            }
            else -> Timber.tag(TAG).w("Неизвестный action: %s", intent?.action)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        closeTun()
        super.onDestroy()
    }

    override fun onRevoke() {
        // Пользователь отозвал VPN-согласие в системных настройках — снимаем tun и себя.
        Timber.tag(TAG).w("VPN-согласие отозвано системой")
        closeTun()
        stopSelf()
        super.onRevoke()
    }

    /**
     * Поднимает blackhole-tun заново с актуальным disallowed-набором. При повторном вызове
     * (сменился whitelist) сначала закрывает старый fd — `establish()` создаёт новый интерфейс.
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

    private fun closeTun() {
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

        const val ACTION_START = "ru.homelab.kidguard.platform.vpn.action.START"
        const val ACTION_STOP = "ru.homelab.kidguard.platform.vpn.action.STOP"

        /** `ArrayList<String>` пакетов, которые должны обходить VPN. */
        const val EXTRA_DISALLOWED = "ru.homelab.kidguard.platform.vpn.extra.DISALLOWED"
    }
}
