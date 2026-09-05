package ru.homelab.kidguard.platform.permissions

import ru.homelab.kidguard.core.domain.model.DeviceHealth
import ru.homelab.kidguard.core.domain.model.DevicePermission
import ru.homelab.kidguard.core.domain.repository.DeviceHealthSource
import ru.homelab.kidguard.core.domain.repository.ProcessExitReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Снимает состояние контроля через [PermissionsManager] — он уже умеет проверять каждое разрешение,
 * ничего нового для детекта не потребовалось (watchdog, веха 6).
 *
 * Вместе с флагами отчёт несёт причину смерти предыдущего процесса (`ActivityManager`): на HiOS
 * logcat вытесняется за минуты, и без этой записи разобрать инцидент задним числом невозможно.
 *
 * Вендорный автозапуск (HiOS/MIUI) сюда НЕ входит: у вендоров нет API, чтобы узнать его состояние.
 * Его отключение обнаруживается косвенно — сервис умирает и перестаёт слать heartbeat, то есть
 * попадает в «молчание», а не во флаги.
 */
@Singleton
class PlatformDeviceHealthSource @Inject constructor(
    private val permissionsManager: PermissionsManager,
    private val processExitReader: ProcessExitReader
) : DeviceHealthSource {

    override fun current(): DeviceHealth = DeviceHealth(
        accessibility = permissionsManager.isGranted(DevicePermission.ACCESSIBILITY),
        overlay = permissionsManager.isGranted(DevicePermission.OVERLAY),
        deviceAdmin = permissionsManager.isGranted(DevicePermission.DEVICE_ADMIN),
        vpn = permissionsManager.isGranted(DevicePermission.VPN),
        batteryOptimization = permissionsManager.isGranted(DevicePermission.BATTERY_OPTIMIZATION),
        // Берём последнюю запись как есть, а не «последнюю интересную»: она описывает, чем
        // закончился предыдущий запуск. Решать, показывать ли её родителю, будет он сам по
        // ProcessExitKind.worthReporting — иначе картина искажалась бы на нашей стороне.
        lastExit = processExitReader.recent(limit = 1).firstOrNull()
    )
}
