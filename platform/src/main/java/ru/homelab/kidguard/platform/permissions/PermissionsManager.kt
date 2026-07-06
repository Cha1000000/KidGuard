package ru.homelab.kidguard.platform.permissions

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.homelab.kidguard.core.domain.model.DevicePermission
import ru.homelab.kidguard.platform.accessibility.KidGuardAccessibilityService
import ru.homelab.kidguard.platform.deviceadmin.KidGuardDeviceAdminReceiver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Проверка статуса специальных разрешений и построение интентов для их выдачи через системные
 * экраны. Живёт в platform-слое, инкапсулирует Android-специфику от UI.
 */
@Singleton
class PermissionsManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Выдано ли разрешение сейчас. */
    fun isGranted(permission: DevicePermission): Boolean = when (permission) {
        DevicePermission.USAGE_ACCESS -> isUsageAccessGranted()
        DevicePermission.ACCESSIBILITY -> isAccessibilityEnabled()
        DevicePermission.OVERLAY -> Settings.canDrawOverlays(context)
        DevicePermission.DEVICE_ADMIN -> isDeviceAdminActive()
        DevicePermission.BATTERY_OPTIMIZATION -> isIgnoringBatteryOptimizations()
        DevicePermission.VPN -> VpnService.prepare(context) == null
    }

    /**
     * Интент, ведущий в системный экран выдачи разрешения (или null, если не требуется).
     *
     * BatteryLife подавлен осознанно: `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` нарушает
     * политику Play Store, но KidGuard распространяется sideload'ом (не через Play), а исключение
     * из оптимизации батареи критично, чтобы система не выгружала фоновый контроль.
     */
    @SuppressLint("BatteryLife")
    fun grantIntent(permission: DevicePermission): Intent? = when (permission) {
        DevicePermission.USAGE_ACCESS ->
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

        DevicePermission.ACCESSIBILITY ->
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        DevicePermission.OVERLAY ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri())

        DevicePermission.DEVICE_ADMIN ->
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent())

        DevicePermission.BATTERY_OPTIMIZATION ->
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri())

        DevicePermission.VPN ->
            VpnService.prepare(context)
    }

    // unsafeCheckOpNoThrow помечен deprecated, но остаётся штатным способом проверки Usage
    // Access — не-deprecated альтернативы для этой проверки нет.
    @Suppress("DEPRECATION")
    private fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(context, KidGuardAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    private fun isDeviceAdminActive(): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.isAdminActive(deviceAdminComponent())
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun deviceAdminComponent() =
        ComponentName(context, KidGuardDeviceAdminReceiver::class.java)

    private fun packageUri(): Uri = Uri.fromParts("package", context.packageName, null)
}
