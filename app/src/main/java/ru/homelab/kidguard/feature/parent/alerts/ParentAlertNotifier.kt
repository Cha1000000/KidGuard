package ru.homelab.kidguard.feature.parent.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.homelab.kidguard.MainActivity
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.DevicePermission
import ru.homelab.kidguard.core.domain.usecase.ChildAlert
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Уведомления родителю о том, что контроль на телефоне ребёнка сломан.
 *
 * Это локальные уведомления, а не push с сервера: FCM в проекте нет, поэтому приложение родителя
 * само просыпается по расписанию (`ChildHealthWorker`) и, если контроль упал, показывает
 * уведомление. Для родителя разница незаметна — кроме задержки до 15 минут, когда приложение
 * закрыто.
 *
 * Важность канала HIGH: смысл фичи в том, чтобы родитель узнал СЕЙЧАС, а не открыв приложение
 * через день.
 */
@Singleton
class ParentAlertNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val notificationManager = context.getSystemService<NotificationManager>()

    fun show(alert: ChildAlert) {
        val manager = notificationManager ?: return
        ensureChannel(manager)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.parent_alert_title, alert.childName))
            .setContentText(textFor(alert))
            .setStyle(NotificationCompat.BigTextStyle().bigText(textFor(alert)))
            .setSmallIcon(ru.homelab.kidguard.platform.R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        // Свой id на каждого ребёнка: тревога о втором ребёнке не должна затирать первую.
        manager.notify(NOTIFICATION_ID_BASE + alert.childId, notification)
    }

    private fun textFor(alert: ChildAlert): String = if (alert.silent) {
        context.getString(R.string.parent_alert_silent)
    } else {
        context.getString(R.string.parent_alert_broken, alert.brokenPermissions.joinToString(", ") { labelOf(it) })
    }

    private fun labelOf(permission: DevicePermission): String = context.getString(
        when (permission) {
            DevicePermission.ACCESSIBILITY -> R.string.permission_accessibility_title
            DevicePermission.OVERLAY -> R.string.permission_overlay_title
            DevicePermission.DEVICE_ADMIN -> R.string.permission_device_admin_title
            DevicePermission.BATTERY_OPTIMIZATION -> R.string.permission_battery_title
            DevicePermission.VPN -> R.string.permission_vpn_title
            DevicePermission.NOTIFICATIONS -> R.string.permission_notifications_title
            DevicePermission.EMERGENCY_CALL -> R.string.permission_emergency_call_title
        }
    )

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.parent_alert_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "kidguard_parent_alerts"

        /** К базе прибавляется id ребёнка — уведомления о разных детях не затирают друг друга. */
        const val NOTIFICATION_ID_BASE = 1000
    }
}
