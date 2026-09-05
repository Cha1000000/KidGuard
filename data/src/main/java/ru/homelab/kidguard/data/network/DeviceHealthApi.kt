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
    val overlay: Boolean,
    val deviceAdmin: Boolean,
    val vpn: Boolean,
    val batteryOptimization: Boolean,
    // Причина смерти предыдущего процесса. Все три поля с дефолтами: на телефонах стоят сборки без
    // них, и старый отчёт должен читаться как «причина неизвестна», а не валить парсинг. Имя вида
    // TASK_MANAGER_STOP — это ProcessExitKind.name, разбирать его умеет только клиент.
    val lastExitKind: String? = null,
    /** ISO-8601, как и lastSeenAt: сервер хранит health непрозрачным JSON и в поля не вникает. */
    val lastExitAt: String? = null,
    val lastExitDescription: String? = null
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
