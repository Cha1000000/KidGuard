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
import ru.homelab.kidguard.platform.R
import timber.log.Timber

/**
 * Постоянный foreground-сервис детского режима. На шаге 2.1 держит устройство «под контролем»:
 * висит в фоне с уведомлением и переживает сворачивание/перезагрузку. Движок учёта экранного
 * времени будет добавлен на шаге 2.3.
 */
@AndroidEntryPoint
class KidGuardForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        Timber.tag(TAG).d("Foreground-сервис запущен")
        // START_STICKY — система перезапустит сервис, если он будет убит.
        return START_STICKY
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
