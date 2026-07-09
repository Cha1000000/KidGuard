package ru.homelab.kidguard.data.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class CreateChildRequest(val name: String, val avatar: Int)

@Serializable
data class ChildDto(val id: Int, val name: String, val avatar: Int = 0, val paired: Boolean = false)

@Serializable
data class CreateChildResponse(val child: ChildDto, val code: String)

@Serializable
data class ChildrenListResponse(val children: List<ChildDto>)

@Serializable
data class PairCodeResponse(val code: String)

@Serializable
data class CoParentRequest(val email: String)

@Serializable
data class CoParentResponse(val status: String)

@Serializable
data class DevicePairRequest(val code: String)

@Serializable
data class DevicePairChildDto(val id: Int, val name: String)

@Serializable
data class DevicePairResponse(val token: String, val child: DevicePairChildDto)

/**
 * Дети и pairing (веха 4.2). Запросы `/children*` требуют родительский JWT (добавляет
 * [AuthTokenInterceptor]); `/device/pair` — точка входа детского устройства, токена не требует.
 * Контракт — docs/plans/milestone-04-accounts-backend-sync.md.
 */
interface ChildrenApi {

    @POST("children")
    suspend fun createChild(@Body request: CreateChildRequest): CreateChildResponse

    @GET("children")
    suspend fun listChildren(): ChildrenListResponse

    @POST("children/{childId}/pair-code")
    suspend fun regeneratePairCode(@Path("childId") childId: Int): PairCodeResponse

    @POST("children/{childId}/co-parent")
    suspend fun inviteCoParent(@Path("childId") childId: Int, @Body request: CoParentRequest): CoParentResponse

    @POST("device/pair")
    suspend fun pairDevice(@Body request: DevicePairRequest): DevicePairResponse
}
