package ru.homelab.kidguard.data.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Отчёт детского устройства о состоянии контроля (watchdog, веха 6). Имена полей должны совпадать
 * с тем, что читает родительский клиент: сервер хранит этот объект как непрозрачный JSON и в его
 * содержимое не вникает.
 */
@Serializable
data class DeviceHealthDto(
    val accessibility: Boolean,
    val usageAccess: Boolean,
    val overlay: Boolean,
    val deviceAdmin: Boolean,
    val vpn: Boolean,
    val batteryOptimization: Boolean
)

@Serializable
data class DeviceHealthRequest(val health: DeviceHealthDto)

@Serializable
data class DeviceHealthResponse(val ok: Boolean = false)

/**
 * Контракт `POST /device/health` (веха 6). Шлёт ТОЛЬКО детское устройство своим device-токеном:
 * childId сервер берёт из токена, поэтому в пути его нет.
 */
interface DeviceHealthApi {

    @POST("device/health")
    suspend fun sendHealth(@Body request: DeviceHealthRequest): DeviceHealthResponse
}
