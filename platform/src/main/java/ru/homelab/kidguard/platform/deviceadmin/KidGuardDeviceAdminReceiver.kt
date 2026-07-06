package ru.homelab.kidguard.platform.deviceadmin

import android.app.admin.DeviceAdminReceiver

/**
 * Device Admin приёмник KidGuard.
 *
 * На шаге 1.4 — скелет: активный Device Admin защищает приложение от удаления и нужен мастеру
 * разрешений для проверки/активации. Это НЕ Device Owner. Логику защиты (запрет деактивации без
 * PIN и т.п.) добавим на вехе 6.
 */
class KidGuardDeviceAdminReceiver : DeviceAdminReceiver()
