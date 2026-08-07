package ru.homelab.kidguard.platform.warning

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.homelab.kidguard.platform.R
import ru.homelab.kidguard.platform.notification.NotificationIds
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Показывает уведомления-предупреждения, чтобы блокировка не была для ребёнка внезапной:
 * «осталось N минут» перед истечением дневного лимита и отдельно — «через N минут начнётся
 * Время сна». Требует POST_NOTIFICATIONS (из мастера разрешений).
 *
 * У двух предупреждений разные id — иначе показ одного затирал бы другое в системной шторке
 * (notify() с одним id перезаписывает уведомление). Берём их из общего реестра
 * [NotificationIds]: раньше id выбирались по месту, и «Время сна» столкнулось с уведомлением
 * VPN-сервиса. Канал общий: оба предупреждения по смыслу и важности одинаковы, отдельный канал
 * не даёт пользователю ничего нового, а только плодит пункты в системных настройках.
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
        manager.notify(NotificationIds.LIMIT_WARNING, notification)
    }

    fun clearLimitWarning() {
        notificationManager?.cancel(NotificationIds.LIMIT_WARNING)
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
        manager.notify(NotificationIds.SLEEP_WARNING, notification)
    }

    fun clearSleepWarning() {
        notificationManager?.cancel(NotificationIds.SLEEP_WARNING)
    }

    /**
     * Контроль потерял разрешение «Специальные возможности» — предупреждение перед замком
     * (см. `AccessibilityGuardController`). Уведомление настойчивое: `ongoing` — чтобы его нельзя
     * было просто смахнуть и забыть, пока разрешение не вернули.
     */
    fun showControlLostWarning(minutesLeft: Int) {
        val manager = notificationManager ?: return
        ensureChannel(manager)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.warning_control_lost_title))
            .setContentText(context.getString(R.string.warning_control_lost_text, minutesLeft))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NotificationIds.CONTROL_LOST, notification)
    }

    fun clearControlLostWarning() {
        notificationManager?.cancel(NotificationIds.CONTROL_LOST)
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
    }
}
