package ru.homelab.kidguard.platform.warning

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.homelab.kidguard.platform.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Показывает уведомление-предупреждение «осталось N минут» перед истечением дневного лимита,
 * чтобы блокировка не была для ребёнка внезапной. Требует POST_NOTIFICATIONS (из мастера разрешений).
 */
@Singleton
class WarningNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val notificationManager = context.getSystemService<NotificationManager>()

    fun show(minutesLeft: Int) {
        val manager = notificationManager ?: return
        ensureChannel(manager)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.warning_title))
            .setContentText(context.getString(R.string.warning_text, minutesLeft))
            .setSmallIcon(R.drawable.ic_notification)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun clear() {
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.warning_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "kidguard_warning"
        const val NOTIFICATION_ID = 2
    }
}
