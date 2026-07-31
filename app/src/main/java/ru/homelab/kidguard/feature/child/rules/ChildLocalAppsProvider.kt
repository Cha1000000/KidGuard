package ru.homelab.kidguard.feature.child.rules

import androidx.compose.ui.graphics.ImageBitmap
import ru.homelab.kidguard.core.domain.repository.InstalledAppsSource
import ru.homelab.kidguard.core.ui.components.decodeAppIconBase64
import javax.inject.Inject

/** Приложение локального (детского) устройства для списков правил: пакет, имя, готовая иконка. */
data class ChildLocalApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/**
 * Приложения ДЕТСКОГО устройства для его собственных экранов правил (лимиты/запрещено/доступно/
 * статистика). В отличие от [ru.homelab.kidguard.feature.parent.rules.ChildAppsProvider] на
 * родительском устройстве, здесь всё берётся локально через [InstalledAppsSource] — ребёнок
 * читает свои же приложения, поэтому ни сеть, ни childId не нужны (см. TodayViewModel).
 */
class ChildLocalAppsProvider @Inject constructor(
    private val installedAppsSource: InstalledAppsSource
) {

    /**
     * Пакет → приложение. [extraPackages] — пакеты, которые надо показать, даже если у них нет
     * launcher-иконки (реально использованные системные приложения в статистике). Ошибка
     * PackageManager → пустая карта: экран тогда покажет имя пакета вместо названия.
     */
    suspend fun loadByPackage(extraPackages: Set<String> = emptySet()): Map<String, ChildLocalApp> =
        runCatching {
            installedAppsSource.publishableApps(extraPackages)
                .map {
                    ChildLocalApp(
                        packageName = it.packageName,
                        label = it.label,
                        icon = decodeAppIconBase64(it.iconBase64)
                    )
                }
                .associateBy { it.packageName }
        }.getOrDefault(emptyMap())
}
