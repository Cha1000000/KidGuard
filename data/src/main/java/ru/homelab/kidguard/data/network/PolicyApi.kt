package ru.homelab.kidguard.data.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * JSON-документ единой политики (формат зафиксирован в плане вехи 4, шаги 4.3 и 4.6).
 * Сервер policy-agnostic — структуру понимает только клиент. Ключи dailyLimits — имена
 * java.time.DayOfWeek ("MONDAY"…). Бонусы датированы и включаются только за текущий день.
 */
@Serializable
data class PolicyDocumentDto(
    val dailyLimits: Map<String, Int> = emptyMap(),
    val appLimits: Map<String, Int> = emptyMap(),
    val whitelist: List<String> = emptyList(),
    val blockedApps: List<String> = emptyList(),
    val bonuses: List<BonusEntryDto> = emptyList(),
    // Штрафы (снятое родителем время). Дефолт обязателен: на детских устройствах ещё стоят
    // сборки без этого поля, и их документ должен читаться как «штрафов нет», а не падать.
    val penalties: List<PenaltyEntryDto> = emptyList(),
    // PIN-защита (веха 6.1): хеш + соль, сырой PIN сюда никогда не попадает. Оба null — PIN не задан.
    // Nullable с дефолтом null — обратная совместимость со старыми документами без PIN.
    val pinHash: String? = null,
    val pinSalt: String? = null,
    // Запрет сайтов (веха 4.1.2, по образцу blockedApps). Дефолты обязательны — обратная
    // совместимость со старыми документами без этих полей.
    val blockedSites: List<BlockedSiteDto> = emptyList(),
    val blockGoogleSearch: Boolean = false,
    // Расписания «Время учёбы» и «Время сна». Ключи карт — имена java.time.DayOfWeek, как в
    // dailyLimits. Дефолты обязательны: у Олега сейчас стоит версия без этих полей.
    val studySchedule: Map<String, TimeWindowDto> = emptyMap(),
    val sleepSchedule: Map<String, TimeWindowDto> = emptyMap(),
    val studyScheduleEnabled: Boolean = false,
    val sleepScheduleEnabled: Boolean = false,
    /** Контакты для экстренного звонка с ночного замка. */
    val emergencyContacts: List<EmergencyContactDto> = emptyList(),
    // Настройки принудительных перерывов (план forced-breaks, задача 3). Дефолт — BreakRulesDto()
    // с enabled=false: старые документы без поля breaks должны применяться как раньше, без
    // перерывов, а не падать при парсинге на уже работающих устройствах.
    val breaks: BreakRulesDto = BreakRulesDto(),
    // Маркер сброса дневного лимита (кнопка «Сбросить сегодняшний лимит»). Nullable с дефолтом
    // null — обратная совместимость: старый документ без поля читается как «сброса нет».
    val dailyUsageReset: DailyUsageResetDto? = null,
    // null — обратная совместимость: старый документ без поля читается как «блокировки нет».
    val dailyUsageBlock: DailyUsageBlockDto? = null
)

/** Маркер сброса дневного лимита: родитель обнуляет израсходованное сегодня время ребёнку. */
@Serializable
data class DailyUsageResetDto(val date: String, val issuedAt: Long)

/** Маркер блокировки на сегодня: родитель обнуляет доступное ребёнку время. */
@Serializable
data class DailyUsageBlockDto(val date: String, val issuedAt: Long)

/** Окно блокировки в минутах от полуночи; `end < start` — переход через полночь. */
@Serializable
data class TimeWindowDto(
    val startMinute: Int,
    val endMinute: Int
)

/** Контакт для экстренного звонка с ночного замка. */
@Serializable
data class EmergencyContactDto(
    val name: String,
    val phone: String
)

/** Запрещённый сайт (домен) в policy-документе; `enabled = true` по умолчанию. */
@Serializable
data class BlockedSiteDto(
    val domain: String,
    val enabled: Boolean = true
)

/** Бонус «Дополнительное время» за день; `packageName = ""` — бонус на весь телефон. */
@Serializable
data class BonusEntryDto(
    val date: String,
    val packageName: String,
    val minutes: Int
)

/**
 * Штраф за день; `packageName = ""` — штраф на весь телефон. `minutes` — положительное число
 * снятых минут, `comment` — пояснение родителя «за что» (пустое допустимо).
 */
@Serializable
data class PenaltyEntryDto(
    val date: String,
    val packageName: String,
    val minutes: Int,
    val comment: String = ""
)

/**
 * Настройки принудительных перерывов в policy-документе. Все поля с дефолтами «не задано» —
 * ровно так же, как у BreakRules.EMPTY в домене: старый документ без блока breaks должен
 * читаться как «перерывы выключены», а не валить парсинг.
 */
@Serializable
data class BreakRulesDto(
    val enabled: Boolean = false,
    val mode: String = "INTERVAL",
    val intervalMinutes: Int = 0,
    val hours: List<Int> = emptyList(),
    val durationMinutes: Int = 0,
    val message: String = ""
)

@Serializable
data class PolicyResponseDto(val data: PolicyDocumentDto? = null, val updatedAt: String? = null)

@Serializable
data class PutPolicyRequest(val data: PolicyDocumentDto)

@Serializable
data class PutPolicyResponse(val updatedAt: String)

/** Контракт `GET/PUT /policy/:childId` (веха 4.3). GET доступен родителю и детскому устройству, PUT — только родителю. */
interface PolicyApi {

    @GET("policy/{childId}")
    suspend fun getPolicy(@Path("childId") childId: Int): PolicyResponseDto

    @PUT("policy/{childId}")
    suspend fun putPolicy(@Path("childId") childId: Int, @Body request: PutPolicyRequest): PutPolicyResponse
}
