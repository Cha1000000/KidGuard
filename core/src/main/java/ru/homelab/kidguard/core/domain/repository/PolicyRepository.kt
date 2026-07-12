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

    /** Личные дневные лимиты приложений: пакет → минут/день (веха 3). */
    val appLimits: Flow<Map<String, Int>>

    /** Пакеты приложений, полностью запрещённых родителем (веха 4.1.2) — блокируются всегда. */
    val blockedApps: Flow<Set<String>>

    /** Задать лимит (минут) на день недели; null убирает лимит (в этот день без ограничения). */
    suspend fun setDailyLimit(day: DayOfWeek, minutes: Int?)

    /** Задать личный дневной лимит приложения (минут); null убирает лимит. */
    suspend fun setAppLimit(packageName: String, minutes: Int?)

    /** Добавить/убрать приложение из белого списка. */
    suspend fun setWhitelisted(packageName: String, whitelisted: Boolean)

    /** Добавить/убрать приложение из списка запрещённых. */
    suspend fun setBlocked(packageName: String, blocked: Boolean)

    /**
     * Транзакционно заменить всю политику разом — применение серверного документа при
     * синхронизации (веха 4.3). Локальные правила полностью перезаписываются.
     */
    suspend fun replaceAll(
        dailyLimits: Map<DayOfWeek, Int>,
        appLimits: Map<String, Int>,
        whitelist: Set<String>,
        blockedApps: Set<String>
    )
}
