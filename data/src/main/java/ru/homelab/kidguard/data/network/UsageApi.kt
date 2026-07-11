package ru.homelab.kidguard.data.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Запись статистики: АБСОЛЮТНЫЕ накопленные секунды за день (не дельта). `packageName = ""` —
 * суммарное экранное время за день (тот же маркер-приём, что «весь телефон» у бонусов).
 */
@Serializable
data class UsageEntryDto(val date: String, val packageName: String = "", val seconds: Int)

@Serializable
data class UsageBatchRequest(val entries: List<UsageEntryDto>)

@Serializable
data class UsageBatchResponse(val saved: Int)

@Serializable
data class UsageResponseDto(val entries: List<UsageEntryDto> = emptyList())

/** Контракт `POST/GET /usage/:childId` (веха 4.4). POST — только детское устройство, GET — родитель/устройство. */
interface UsageApi {

    @POST("usage/{childId}")
    suspend fun sendUsage(@Path("childId") childId: Int, @Body request: UsageBatchRequest): UsageBatchResponse

    @GET("usage/{childId}")
    suspend fun getUsage(@Path("childId") childId: Int, @Query("days") days: Int): UsageResponseDto
}
