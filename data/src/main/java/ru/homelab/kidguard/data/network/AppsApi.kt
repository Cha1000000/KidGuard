package ru.homelab.kidguard.data.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class ChildAppDto(
    val packageName: String,
    val label: String,
    /** Иконка приложения: WebP 96×96 в base64; null — устройство её не прислало. */
    val icon: String? = null,
    /** Системное приложение (FLAG_SYSTEM). Дефолт false — обратная совместимость со старым клиентом. */
    val isSystem: Boolean = false,
    /** Критичное для устройства (сам KidGuard, лаунчер, systemui). Дефолт false. */
    val isRisky: Boolean = false
)

@Serializable
data class PutAppsRequest(val apps: List<ChildAppDto>)

@Serializable
data class PutAppsResponse(val ok: Boolean, val count: Int)

@Serializable
data class ChildAppsResponse(val apps: List<ChildAppDto> = emptyList())

/**
 * Контракт `PUT/GET /apps/:childId` (веха 4.1): детское устройство публикует список своих
 * запускаемых приложений, родитель читает его для экранов выбора (лимиты/белый список/запреты).
 */
interface AppsApi {

    /** Полная замена списка приложений ребёнка. Только device-JWT самого устройства. */
    @PUT("apps/{childId}")
    suspend fun putApps(@Path("childId") childId: Int, @Body request: PutAppsRequest): PutAppsResponse

    /** Список приложений ребёнка (отсортирован по имени). Только родитель. */
    @GET("apps/{childId}")
    suspend fun getApps(@Path("childId") childId: Int): ChildAppsResponse
}
