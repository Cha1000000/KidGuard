package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.core.domain.model.AppInfo

/**
 * Источник списка запускаемых приложений ТЕКУЩЕГО устройства (веха 4.1). На детском
 * устройстве список уходит на сервер, чтобы родитель выбирал приложения ребёнка,
 * а не свои. Реализация — в :platform (PackageManager).
 */
interface InstalledAppsSource {

    /** Запускаемые приложения устройства (есть иконка в лаунчере), отсортированы по имени. */
    suspend fun launchableApps(): List<AppInfo>

    /**
     * Список для публикации родителю: запускаемые приложения ∪ переданные `usedPackages`
     * (реально использованные, включая системные без launcher-иконки). На каждом — флаги
     * isSystem (FLAG_SYSTEM) и isRisky (критичные для устройства: сам KidGuard, лаунчер, systemui).
     */
    suspend fun publishableApps(usedPackages: Set<String>): List<AppInfo>

    /** Все установленные пакеты устройства — для pass-through режима VPN (веха 5.4). */
    suspend fun installedPackageNames(): List<String>

    /**
     * Реактивный список всех установленных пакетов: эмитит текущий набор сразу и при каждой
     * установке/удалении приложения (веха 5.4). Нужен, чтобы pass-through VPN сразу подхватывал
     * только что установленное приложение и не оставлял его без интернета до перезапуска сервиса.
     */
    fun observeInstalledPackageNames(): Flow<List<String>>
}
