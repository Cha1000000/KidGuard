package ru.homelab.kidguard.platform.foreground

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.repository.PolicySyncRepository
import ru.homelab.kidguard.platform.R
import ru.homelab.kidguard.platform.overlay.BlockingController
import ru.homelab.kidguard.platform.tracking.ScreenTimeTracker
import ru.homelab.kidguard.platform.warning.WarningController
import timber.log.Timber
import javax.inject.Inject

/**
 * Постоянный foreground-сервис детского режима. На шаге 2.1 держит устройство «под контролем»:
 * висит в фоне с уведомлением и переживает сворачивание/перезагрузку. Движок учёта экранного
 * времени будет добавлен на шаге 2.3.
 */
@AndroidEntryPoint
class KidGuardForegroundService : Service() {

    @Inject
    lateinit var screenTimeTracker: ScreenTimeTracker

    @Inject
    lateinit var blockingController: BlockingController

    @Inject
    lateinit var warningController: WarningController

    @Inject
    lateinit var policySyncRepository: PolicySyncRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackingJob: Job? = null
    private var blockingJob: Job? = null
    private var warningJob: Job? = null
    private var policySyncJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        Timber.tag(TAG).d("Foreground-сервис запущен")
        // Запускаем движок учёта и контроллер блокировки по одному разу
        // (onStartCommand может вызываться повторно).
        if (trackingJob == null) {
            trackingJob = scope.launch { screenTimeTracker.run() }
        }
        if (blockingJob == null) {
            blockingJob = scope.launch { blockingController.run() }
        }
        if (warningJob == null) {
            warningJob = scope.launch { warningController.run() }
        }
        if (policySyncJob == null) {
            // Периодический pull единой политики с сервера (веха 4.3) — правила родителя
            // доезжают до устройства и применяются в Room офлайн-движком.
            policySyncJob = scope.launch { policySyncRepository.childSyncLoop() }
        }
        // START_STICKY — система перезапустит сервис, если он будет убит.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(getString(R.string.foreground_notification_text))
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
                getString(R.string.foreground_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "KidGuardFgs"
        private const val CHANNEL_ID = "kidguard_control"
        private const val NOTIFICATION_ID = 1

        /** Запустить сервис контроля (idempotent — повторный вызов безопасен). */
        fun start(context: Context) {
            val intent = Intent(context, KidGuardForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
