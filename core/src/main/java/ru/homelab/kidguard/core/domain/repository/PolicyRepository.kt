package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.core.domain.model.DailyLimits
import java.time.DayOfWeek

/**
 * Политика родительского контроля для ребёнка: дневные лимиты по дням недели и белый список
 * «всегда доступных» приложений. Хранится локально (Room); синхронизация с сервером — веха 4.
 */
interface PolicyRepository {

    /** Дневные лимиты экранного времени по дням недели. */
    val dailyLimits: Flow<DailyLimits>

    /** Пакеты приложений из белого списка (доступны всегда, даже после лимита). */
    val whitelist: Flow<Set<String>>

    /** Задать лимит (минут) на день недели; null убирает лимит (в этот день без ограничения). */
    suspend fun setDailyLimit(day: DayOfWeek, minutes: Int?)

    /** Добавить/убрать приложение из белого списка. */
    suspend fun setWhitelisted(packageName: String, whitelisted: Boolean)
}
