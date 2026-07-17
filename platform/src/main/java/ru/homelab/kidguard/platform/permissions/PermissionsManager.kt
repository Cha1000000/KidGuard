package ru.homelab.kidguard.platform.permissions

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
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
        DevicePermission.ACCESSIBILITY -> isAccessibilityEnabled()
        DevicePermission.OVERLAY -> Settings.canDrawOverlays(context)
        DevicePermission.DEVICE_ADMIN -> isDeviceAdminActive()
        DevicePermission.BATTERY_OPTIMIZATION -> isIgnoringBatteryOptimizations()
        DevicePermission.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
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
        DevicePermission.ACCESSIBILITY ->
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        DevicePermission.OVERLAY ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri())

        DevicePermission.DEVICE_ADMIN ->
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent())

        DevicePermission.BATTERY_OPTIMIZATION ->
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri())

        DevicePermission.NOTIFICATIONS ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        DevicePermission.VPN ->
            VpnService.prepare(context)
    }

    /**
     * Интент в **вендорный менеджер автозапуска** (HiOS/Transsion, MIUI, EMUI, ColorOS, FuntouchOS).
     *
     * Зачем: стандартного `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` на этих оболочках НЕ достаточно —
     * поверх AOSP-механизма у них свой список «кого не запускать в фоне», и он убивает
     * foreground-сервисы (у нас — весь контроль). Программного API для проверки/выдачи не
     * существует ни у одного вендора, поэтому это не [DevicePermission] (его нельзя `isGranted`),
     * а карточка-инструкция в мастере: сами открыть нужный экран мы можем лишь best-effort.
     *
     * Компоненты ниже — известные точки входа, их набор заведомо неполон и меняется от версии к
     * версии прошивки. Поэтому: берём ПЕРВЫЙ реально существующий на устройстве, а если ни одного
     * нет (или вендор незнакомый) — уводим в системное «О приложении», откуда на любой оболочке
     * можно дойти до энергонастроек. Точный путь на реальном Tecno снимаем на обкатке
     * (см. `milestone-06v-field-test-checklist.md`, этап 0.3) и дополняем список по факту.
     */
    fun autostartIntent(): Intent =
        VENDOR_AUTOSTART_COMPONENTS
            .asSequence()
            .map { Intent().setComponent(it) }
            .firstOrNull { context.packageManager.resolveActivity(it, 0) != null }
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())

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

    private companion object {
        /**
         * Точки входа в вендорные менеджеры автозапуска. Проверяются по порядку, берётся первая
         * существующая на устройстве (см. [autostartIntent]). Видимость чужих пакетов на
         * Android 11+ уже обеспечена `QUERY_ALL_PACKAGES` в манифесте (объявлено ради списка
         * приложений ребёнка), поэтому отдельные `<queries>` не нужны.
         */
        val VENDOR_AUTOSTART_COMPONENTS = listOf(
            // Transsion (HiOS — наш Tecno; а также Infinix, itel): менеджер энергопотребления
            // PhoneMaster. ПРОВЕРЕНО 2026-07-16 на TECNO CM7 (Android 16, PhoneMaster 6.2.2):
            // активность существует, экспортирована, открывает экран «Управление автозапуском».
            // На детском Spark 30 Pro (Android 14) перепроверить — версия PhoneMaster другая.
            ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
            // Xiaomi MIUI / HyperOS
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            // Huawei EMUI
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            // Oppo ColorOS
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            // Vivo FuntouchOS
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
        )
    }
}
