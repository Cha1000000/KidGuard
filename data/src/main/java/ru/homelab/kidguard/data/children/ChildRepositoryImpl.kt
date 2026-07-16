package ru.homelab.kidguard.data.children

import ru.homelab.kidguard.core.domain.model.AppInfo
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.domain.model.ChildWithCode
import ru.homelab.kidguard.core.domain.model.DeviceHealth
import ru.homelab.kidguard.core.domain.model.UsageEntry
import ru.homelab.kidguard.core.domain.repository.ChildRepository
import ru.homelab.kidguard.data.network.AppsApi
import ru.homelab.kidguard.data.network.ChildDto
import ru.homelab.kidguard.data.network.ChildrenApi
import ru.homelab.kidguard.data.network.CoParentRequest
import ru.homelab.kidguard.data.network.CreateChildRequest
import ru.homelab.kidguard.data.network.DeviceHealthDto
import ru.homelab.kidguard.data.network.UpdateChildRequest
import ru.homelab.kidguard.data.network.UsageApi
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChildRepositoryImpl @Inject constructor(
    private val childrenApi: ChildrenApi,
    private val usageApi: UsageApi,
    private val appsApi: AppsApi
) : ChildRepository {

    override suspend fun createChild(name: String, avatar: Int): Result<ChildWithCode> = try {
        val response = childrenApi.createChild(CreateChildRequest(name, avatar))
        Result.success(ChildWithCode(response.child.toDomain(), response.code))
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun listChildren(): Result<List<Child>> = try {
        val response = childrenApi.listChildren()
        Result.success(response.children.map { it.toDomain() })
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun regeneratePairCode(childId: Int): Result<String> = try {
        Result.success(childrenApi.regeneratePairCode(childId).code)
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun inviteCoParent(childId: Int, email: String): Result<Boolean> = try {
        val response = childrenApi.inviteCoParent(childId, CoParentRequest(email))
        Result.success(response.status == "linked")
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun getChildUsage(childId: Int, days: Int): Result<List<UsageEntry>> = try {
        val response = usageApi.getUsage(childId, days)
        Result.success(
            response.entries.mapNotNull { dto ->
                runCatching {
                    UsageEntry(LocalDate.parse(dto.date), dto.packageName, dto.seconds)
                }.getOrNull()
            }
        )
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun updateChild(childId: Int, name: String, avatar: Int): Result<Child> = try {
        val response = childrenApi.updateChild(childId, UpdateChildRequest(name = name, avatar = avatar))
        Result.success(response.child.toDomain())
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun deleteChild(childId: Int): Result<Unit> = try {
        childrenApi.deleteChild(childId)
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun childApps(childId: Int): Result<List<AppInfo>> = try {
        val response = appsApi.getApps(childId)
        Result.success(response.apps.map { AppInfo(it.packageName, it.label, it.icon) })
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun ChildDto.toDomain() = Child(
        id = id,
        name = name,
        avatar = avatar,
        paired = paired,
        lastSeenAt = parseLastSeen(lastSeenAt),
        health = health?.toDomain()
    )

    /**
     * Кривую метку времени от сервера глотаем: список детей — основной экран родителя, ронять его
     * из-за необязательного watchdog-поля нельзя. Родитель увидит «нет данных», а не ошибку.
     */
    private fun parseLastSeen(raw: String?): Instant? = raw?.let {
        runCatching { Instant.parse(it) }
            .onFailure { e -> Timber.w(e, "Не разобрал lastSeenAt: %s", raw) }
            .getOrNull()
    }

    private fun DeviceHealthDto.toDomain() = DeviceHealth(
        accessibility = accessibility,
        usageAccess = usageAccess,
        overlay = overlay,
        deviceAdmin = deviceAdmin,
        vpn = vpn,
        batteryOptimization = batteryOptimization
    )
}
