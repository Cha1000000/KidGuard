package ru.homelab.kidguard.feature.parent.rules

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import ru.homelab.kidguard.core.domain.repository.ChildRepository
import ru.homelab.kidguard.core.domain.repository.SyncRepository
import javax.inject.Inject

/**
 * Приложение с устройства ребёнка: пакет, название и иконка (для экранов белого списка и лимитов).
 * Иконки нет, если такое приложение не установлено на родительском телефоне — тогда экран рисует
 * буквенный кружок.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/**
 * Список приложений АКТИВНОГО РЕБЁНКА с сервера (веха 4.1): детское устройство публикует свои
 * запускаемые приложения, родитель выбирает из них лимиты/белый список/запреты. Иконок сервер
 * не хранит — подставляем локальную, если тот же пакет установлен у родителя (частый случай для
 * популярных приложений). Единая точка для экранов «Всегда доступные» и «Лимиты приложений».
 */
class ChildAppsProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val childRepository: ChildRepository,
    private val syncRepository: SyncRepository
) {

    /** Пустой список — ребёнок не выбран, устройство ещё не прислало приложения или сеть недоступна. */
    suspend fun loadActiveChildApps(): List<InstalledApp> {
        val childId = syncRepository.activeChildId.first() ?: return emptyList()
        val apps = childRepository.childApps(childId).getOrDefault(emptyList())
        return apps
            .map { InstalledApp(it.packageName, it.label, localIcon(it.packageName)) }
            .sortedBy { it.label.lowercase() }
    }

    private fun localIcon(packageName: String): ImageBitmap? = runCatching {
        context.packageManager.getApplicationIcon(packageName)
            .toBitmap(width = ICON_PX, height = ICON_PX)
            .asImageBitmap()
    }.getOrNull()

    private companion object {
        const val ICON_PX = 96
    }
}
