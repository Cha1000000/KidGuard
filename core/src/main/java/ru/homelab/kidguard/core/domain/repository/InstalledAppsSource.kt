package ru.homelab.kidguard.core.domain.repository

import ru.homelab.kidguard.core.domain.model.AppInfo

/**
 * Источник списка запускаемых приложений ТЕКУЩЕГО устройства (веха 4.1). На детском
 * устройстве список уходит на сервер, чтобы родитель выбирал приложения ребёнка,
 * а не свои. Реализация — в :platform (PackageManager).
 */
interface InstalledAppsSource {

    /** Запускаемые приложения устройства (есть иконка в лаунчере), отсортированы по имени. */
    suspend fun launchableApps(): List<AppInfo>
}
