package ru.homelab.kidguard.platform.permissions

import ru.homelab.kidguard.core.domain.model.DeviceHealth
import ru.homelab.kidguard.core.domain.model.DevicePermission
import ru.homelab.kidguard.core.domain.repository.DeviceHealthSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Снимает состояние контроля через [PermissionsManager] — он уже умеет проверять каждое разрешение,
 * ничего нового для детекта не потребовалось (watchdog, веха 6).
 *
 * Вендорный автозапуск (HiOS/MIUI) сюда НЕ входит: у вендоров нет API, чтобы узнать его состояние.
 * Его отключение обнаруживается косвенно — сервис умирает и перестаёт слать heartbeat, то есть
 * попадает в «молчание», а не во флаги.
 */
@Singleton
class PlatformDeviceHealthSource @Inject constructor(
    private val permissionsManager: PermissionsManager
) : DeviceHealthSource {

    override fun current(): DeviceHealth = DeviceHealth(
        accessibility = permissionsManager.isGranted(DevicePermission.ACCESSIBILITY),
        overlay = permissionsManager.isGranted(DevicePermission.OVERLAY),
        deviceAdmin = permissionsManager.isGranted(DevicePermission.DEVICE_ADMIN),
        vpn = permissionsManager.isGranted(DevicePermission.VPN),
        batteryOptimization = permissionsManager.isGranted(DevicePermission.BATTERY_OPTIMIZATION)
    )
}
