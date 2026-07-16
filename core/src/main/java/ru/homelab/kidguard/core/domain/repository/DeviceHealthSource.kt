package ru.homelab.kidguard.core.domain.repository

import ru.homelab.kidguard.core.domain.model.DeviceHealth

/**
 * Источник состояния контроля на ТЕКУЩЕМ устройстве (watchdog, веха 6). На детском устройстве
 * отчёт уходит на сервер, чтобы родитель видел, что контроль сломался.
 * Реализация — в :platform (PermissionsManager).
 */
interface DeviceHealthSource {

    /** Снимок «что выдано прямо сейчас». */
    fun current(): DeviceHealth
}
