package ru.homelab.kidguard.data.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class CreateChildRequest(val name: String, val avatar: Int)

@Serializable
data class UpdateChildRequest(val name: String? = null, val avatar: Int? = null)

@Serializable
data class UpdateChildResponse(val child: ChildDto)

@Serializable
data class DeleteChildResponse(val ok: Boolean = true)

/**
 * `lastSeenAt`/`health` — watchdog (веха 6). Оба с дефолтом null: приходят только от сервера с
 * поддержкой heartbeat, и остаются null, пока детское устройство не прислало ни одного отчёта.
 */
@Serializable
data class ChildDto(
    val id: Int,
    val name: String,
    val avatar: Int = 0,
    val paired: Boolean = false,
    val lastSeenAt: String? = null,
    val health: DeviceHealthDto? = null,
    // Дефолт false нужен для совместимости со старым сервером, который поля ещё не отдаёт:
    // худшее, что случится, — диалог удаления не покажет подсказку про второго родителя.
    val hasCoParent: Boolean = false,
    // Дефолт true у обоих (веха «Оповещения»): совместимость со старым сервером, который эти поля
    // ещё не отдаёт — до фичи уведомления были включены всегда, и молчание сервера не должно тихо
    // менять это поведение на «выключено».
    val notifyPush: Boolean = true,
    val notifyEmail: Boolean = true
)

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
data class DevicePairChildDto(val id: Int, val name: String, val avatar: Int = 0)

@Serializable
data class DevicePairResponse(val token: String, val child: DevicePairChildDto)

/**
 * Патч подписки родителя на тревоги по одному ребёнку (экран «Оповещения»). Оба поля — с дефолтом
 * `null` и НЕ обязательны специально: Json в [NetworkModule] собран с настройками kotlinx по
 * умолчанию (`encodeDefaults = false`), поэтому поле, оставшееся равным своему дефолту, в тело
 * запроса вообще не попадает — незаданный канал просто не уедет на сервер, и тот его не тронет.
 * Если когда-нибудь понадобится реально отправлять `null` (не «не менять», а «сбросить») — это
 * НЕ такой случай, смотри [UpdateAlertEmailRequest] в AuthApi, где решение обратное.
 */
@Serializable
data class ChildNotificationsRequest(val notifyPush: Boolean? = null, val notifyEmail: Boolean? = null)

@Serializable
data class ChildNotificationsResponse(val ok: Boolean = true)

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

    @PATCH("children/{childId}")
    suspend fun updateChild(@Path("childId") childId: Int, @Body request: UpdateChildRequest): UpdateChildResponse

    @DELETE("children/{childId}")
    suspend fun deleteChild(@Path("childId") childId: Int): DeleteChildResponse

    @PATCH("children/{childId}/notifications")
    suspend fun updateNotifications(
        @Path("childId") childId: Int,
        @Body request: ChildNotificationsRequest
    ): ChildNotificationsResponse

    @POST("device/pair")
    suspend fun pairDevice(@Body request: DevicePairRequest): DevicePairResponse
}
