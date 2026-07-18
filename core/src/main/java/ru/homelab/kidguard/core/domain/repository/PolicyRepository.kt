package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.core.domain.model.BlockedSite
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.PinProtection
import ru.homelab.kidguard.core.domain.model.SiteBlockRules
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

    /** Сайты (домены), запрещённые родителем, каждый со своим флагом вкл/выкл (веха 4.1.2). */
    val blockedSites: Flow<List<BlockedSite>>

    /** Отдельный тумблер «блокировать google-поиск» (не завязан на список доменов). */
    val blockGoogleSearch: Flow<Boolean>

    /** Готовые правила для DNS-фильтра: только enabled-домены из [blockedSites] + [blockGoogleSearch]. */
    val siteBlockRules: Flow<SiteBlockRules>

    /** Родительский PIN (соль + хеш), защищающий критичные настройки (веха 6.1); null — PIN не задан. */
    val pinProtection: Flow<PinProtection?>

    /** Задать лимит (минут) на день недели; null убирает лимит (в этот день без ограничения). */
    suspend fun setDailyLimit(day: DayOfWeek, minutes: Int?)

    /** Задать личный дневной лимит приложения (минут); null убирает лимит. */
    suspend fun setAppLimit(packageName: String, minutes: Int?)

    /** Добавить/убрать приложение из белого списка. */
    suspend fun setWhitelisted(packageName: String, whitelisted: Boolean)

    /** Добавить/убрать приложение из списка запрещённых. */
    suspend fun setBlocked(packageName: String, blocked: Boolean)

    /** Добавить домен в список запрещённых сайтов (уже нормализован вызывающей стороной; enabled = true; upsert). */
    suspend fun addBlockedSite(domain: String)

    /** Включить/выключить конкретный домен без удаления из списка. */
    suspend fun setSiteEnabled(domain: String, enabled: Boolean)

    /** Убрать домен из списка запрещённых сайтов. */
    suspend fun removeBlockedSite(domain: String)

    /** Задать тумблер «блокировать google-поиск». */
    suspend fun setBlockGoogleSearch(enabled: Boolean)

    /** Задать родительский PIN — хеш и соль уже посчитаны вызывающей стороной ([PinHasher][ru.homelab.kidguard.core.domain.security.PinHasher]). */
    suspend fun setPin(hash: String, salt: String)

    /** Убрать PIN-защиту. */
    suspend fun clearPin()

    /**
     * Транзакционно заменить всю политику разом — применение серверного документа при
     * синхронизации (веха 4.3). Локальные правила полностью перезаписываются.
     */
    suspend fun replaceAll(
        dailyLimits: Map<DayOfWeek, Int>,
        appLimits: Map<String, Int>,
        whitelist: Set<String>,
        blockedApps: Set<String>,
        blockedSites: List<BlockedSite>,
        blockGoogleSearch: Boolean,
        pinHash: String?,
        pinSalt: String?
    )
}
