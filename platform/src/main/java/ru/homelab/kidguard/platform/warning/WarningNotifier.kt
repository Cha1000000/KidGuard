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
 * Показывает уведомления-предупреждения, чтобы блокировка не была для ребёнка внезапной:
 * «осталось N минут» перед истечением дневного лимита и отдельно — «через N минут начнётся
 * Время сна». Требует POST_NOTIFICATIONS (из мастера разрешений).
 *
 * У двух предупреждений разные [id][NOTIFICATION_ID]/[SLEEP_NOTIFICATION_ID] — иначе показ
 * одного затирал бы другое в системном шторке (notify() с одним id перезаписывает уведомление).
 * Канал общий: оба предупреждения по смыслу и важности одинаковы, отдельный канал не даёт
 * пользователю ничего нового, а только плодит пункты в системных настройках уведомлений.
 */
@Singleton
class WarningNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val notificationManager = context.getSystemService<NotificationManager>()

    /** Предупреждение об истечении дневного лимита. */
    fun showLimitWarning(minutesLeft: Int) {
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

    fun clearLimitWarning() {
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    /** Предупреждение о скором начале «Времени сна». */
    fun showSleepWarning(minutesLeft: Int) {
        val manager = notificationManager ?: return
        ensureChannel(manager)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.warning_sleep_title))
            .setContentText(context.getString(R.string.warning_sleep_text, minutesLeft))
            .setSmallIcon(R.drawable.ic_notification)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(SLEEP_NOTIFICATION_ID, notification)
    }

    fun clearSleepWarning() {
        notificationManager?.cancel(SLEEP_NOTIFICATION_ID)
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
        const val SLEEP_NOTIFICATION_ID = 3
    }
}
