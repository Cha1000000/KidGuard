package ru.homelab.kidguard.platform.permissions

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.homelab.kidguard.core.domain.model.DevicePermission
import ru.homelab.kidguard.platform.accessibility.AccessibilityLiveness
import ru.homelab.kidguard.platform.accessibility.KidGuardAccessibilityService
import ru.homelab.kidguard.platform.deviceadmin.KidGuardDeviceAdminReceiver
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Проверка статуса специальных разрешений и построение интентов для их выдачи через системные
 * экраны. Живёт в platform-слое, инкапсулирует Android-специфику от UI.
 */
@Singleton
class PermissionsManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val accessibilityLiveness: AccessibilityLiveness
) {

    /** Выдано ли разрешение сейчас. */
    fun isGranted(permission: DevicePermission): Boolean = when (permission) {
        DevicePermission.ACCESSIBILITY -> isAccessibilityEnabled()
        DevicePermission.OVERLAY -> Settings.canDrawOverlays(context)
        DevicePermission.DEVICE_ADMIN -> isDeviceAdminActive()
        DevicePermission.BATTERY_OPTIMIZATION -> isIgnoringBatteryOptimizations()
        DevicePermission.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
        DevicePermission.VPN -> VpnService.prepare(context) == null
        DevicePermission.EMERGENCY_CALL -> ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
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

        // Runtime-разрешение: выдаётся системным диалогом, а не экраном настроек — интента нет,
        // мастер запрашивает его своим launcher'ом.
        DevicePermission.EMERGENCY_CALL -> null
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

    /**
     * Можем ли вернуть accessibility сами. Обычному приложению система `WRITE_SECURE_SETTINGS` не
     * выдаёт — оно появляется, только если разрешение выдали с компьютера:
     *
     * ```
     * adb shell pm grant ru.homelab.kidguard android.permission.WRITE_SECURE_SETTINGS
     * ```
     *
     * Разрешение переживает обновления приложения (и force-stop), слетает при полной переустановке.
     * Если его нет — работает обычный путь: предупреждение, а затем замок.
     */
    fun canRestoreAccessibility(): Boolean = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.WRITE_SECURE_SETTINGS
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Возвращает accessibility-разрешение себе. Наш компонент ДОПИСЫВАЕТСЯ к списку, а не заменяет
     * его: в списке могут быть чужие сервисы (тот же TalkBack), и затирать их нельзя.
     *
     * Пишем ДВАЖДЫ — сначала список без нас, потом с нами. Одной записи хватает, только когда
     * компонент из списка пропал (обычное «разрешение слетело»). Во втором сценарии — сервис
     * числится включённым, но система держит его в `Crashed services` и не перепривязывает
     * (см. [isAccessibilityEnabled]) — итоговая строка совпала бы с текущей, система не увидела
     * бы изменения и биндить заново не стала. Пара «убрать → вернуть» равносильна тому, чтобы
     * руками выключить и включить тумблер; проверено на телефоне Олега 31.08.2026.
     *
     * @return удалось ли; `false` — разрешения на запись нет либо система запись отклонила.
     */
    fun restoreAccessibility(): Boolean {
        if (!canRestoreAccessibility()) return false
        val expected = ComponentName(context, KidGuardAccessibilityService::class.java)
        val current = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val others = current.split(':').filter { it.isNotBlank() && !it.matchesService(expected) }
        return runCatching {
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                others.joinToString(":")
            )
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                (others + expected.flattenToString()).joinToString(":")
            )
            // Без общего тумблера список игнорируется — система не забиндит ни один сервис.
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }.isSuccess
    }

    /**
     * Контроль жив, только если выполнено ВСЁ: поднят общий тумблер специальных возможностей, наш
     * сервис в списке включённых И система его действительно ПРИВЯЗАЛА.
     *
     * Последнее — не паранойя, а разбор боевого случая (телефон Олега, 31.08.2026): процесс убил
     * вендорский «оптимизатор», система подняла обратно foreground- и VPN-сервисы, а
     * accessibility-сервис пометила `Crashed services` и биндить заново не стала. Оба ключа
     * `Settings.Secure` при этом остались нетронутыми — то есть по ним разрешение «выдано», а
     * событий не приходит ни одного, и не блокируется вообще ничего. Телефон так прожил ~9 часов:
     * ни замка ребёнку, ни уведомления родителю, потому что и сторож, и heartbeat ходят сюда.
     * Force-stop тут ни при чём — он бы вычистил список (см. [restoreAccessibility]).
     *
     * Признак привязки берём у самой системы: `getEnabledAccessibilityServiceList` отдаёт
     * привязанные сервисы (в AOSP — `mBoundServices`), а не строку настроек, поэтому упавший
     * сервис из неё исчезает.
     */
    private fun isAccessibilityEnabled(): Boolean {
        val masterSwitchOn = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!masterSwitchOn) return false
        val expected = ComponentName(context, KidGuardAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        if (enabled.split(':').none { it.matchesService(expected) }) return false
        // Два независимых признака живости, потому что ошибиться в эту сторону дорого:
        // 1. слово самого сервиса — факт, а не догадка, но слепо к «система отвязала молча»;
        // 2. системный список привязанных — закрывает как раз этот угол.
        if (!accessibilityLiveness.connected) {
            Timber.w("Сервис числится включённым, но не подключён в этом процессе — контроль мёртв")
            return false
        }
        return isAccessibilityServiceBound(expected)
    }

    /**
     * Привязан ли наш сервис системой прямо сейчас.
     *
     * Ошибку опроса трактуем как «привязан» (`true`): ошибиться здесь можно только в одну
     * безопасную сторону — ложное «не привязан» запустит ребёнку замок на ровном месте, а ложное
     * «всё хорошо» лишь оставит поведение прежним, каким оно было до этой правки.
     */
    private fun isAccessibilityServiceBound(expected: ComponentName): Boolean = runCatching {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return true
        val bound = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.matchesService(expected) }
        if (!bound) {
            // Улика для будущих разборов «почему контроль молчал»: снаружи всё выглядит выданным.
            Timber.w("Сервис есть в списке включённых, но система его не привязала — контроль мёртв")
        }
        bound
    }.getOrDefault(true)

    /**
     * Сверяем и по `id`, и по `resolveInfo` — заполненность обоих полей зависит от прошивки, а
     * «не нашли» тут означает замок ребёнку, поэтому лучше перестраховаться двумя источниками.
     */
    private fun AccessibilityServiceInfo.matchesService(expected: ComponentName): Boolean =
        ComponentName.unflattenFromString(id) == expected ||
            resolveInfo?.serviceInfo?.let { ComponentName(it.packageName, it.name) } == expected

    /** Строка списка описывает наш сервис (сравниваем компонентами, а не текстом). */
    private fun String.matchesService(expected: ComponentName): Boolean =
        ComponentName.unflattenFromString(this) == expected

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
