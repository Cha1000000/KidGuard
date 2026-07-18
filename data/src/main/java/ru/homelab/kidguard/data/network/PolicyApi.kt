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
    // PIN-защита (веха 6.1): хеш + соль, сырой PIN сюда никогда не попадает. Оба null — PIN не задан.
    // Nullable с дефолтом null — обратная совместимость со старыми документами без PIN.
    val pinHash: String? = null,
    val pinSalt: String? = null,
    // Запрет сайтов (веха 4.1.2, по образцу blockedApps). Дефолты обязательны — обратная
    // совместимость со старыми документами без этих полей.
    val blockedSites: List<BlockedSiteDto> = emptyList(),
    val blockGoogleSearch: Boolean = false
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
