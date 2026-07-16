package ru.homelab.kidguard.core.ui.components

import androidx.annotation.StringRes
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.DevicePermission

/**
 * Подписи разрешений — общие для мастера разрешений (детский флоу) и листа здоровья watchdog
 * (родительский). Вынесены сюда, чтобы название разрешения не разъезжалось между экранами.
 */

/** Название разрешения. */
@StringRes
fun DevicePermission.titleRes(): Int = when (this) {
    DevicePermission.USAGE_ACCESS -> R.string.permission_usage_title
    DevicePermission.ACCESSIBILITY -> R.string.permission_accessibility_title
    DevicePermission.OVERLAY -> R.string.permission_overlay_title
    DevicePermission.DEVICE_ADMIN -> R.string.permission_device_admin_title
    DevicePermission.BATTERY_OPTIMIZATION -> R.string.permission_battery_title
    DevicePermission.NOTIFICATIONS -> R.string.permission_notifications_title
    DevicePermission.VPN -> R.string.permission_vpn_title
}

/** Зачем разрешение нужно — формулировка для мастера, ДО выдачи. */
@StringRes
fun DevicePermission.descRes(): Int = when (this) {
    DevicePermission.USAGE_ACCESS -> R.string.permission_usage_desc
    DevicePermission.ACCESSIBILITY -> R.string.permission_accessibility_desc
    DevicePermission.OVERLAY -> R.string.permission_overlay_desc
    DevicePermission.DEVICE_ADMIN -> R.string.permission_device_admin_desc
    DevicePermission.BATTERY_OPTIMIZATION -> R.string.permission_battery_desc
    DevicePermission.NOTIFICATIONS -> R.string.permission_notifications_desc
    DevicePermission.VPN -> R.string.permission_vpn_desc
}

/**
 * Что сломается без разрешения — формулировка для watchdog, ПОСЛЕ поломки. Отличается от
 * [descRes] намеренно: родитель смотрит на уже случившуюся проблему, ему нужны последствия,
 * а не назначение.
 */
@StringRes
fun DevicePermission.healthImpactRes(): Int = when (this) {
    DevicePermission.USAGE_ACCESS -> R.string.health_impact_usage
    DevicePermission.ACCESSIBILITY -> R.string.health_impact_accessibility
    DevicePermission.OVERLAY -> R.string.health_impact_overlay
    DevicePermission.DEVICE_ADMIN -> R.string.health_impact_device_admin
    DevicePermission.BATTERY_OPTIMIZATION -> R.string.health_impact_battery
    // Уведомления в DeviceHealth не входят (контроль без них работает) — сюда попасть не должны.
    DevicePermission.NOTIFICATIONS -> R.string.permission_notifications_desc
    DevicePermission.VPN -> R.string.health_impact_vpn
}
