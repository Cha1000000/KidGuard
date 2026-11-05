package ru.homelab.kidguard.data.breaks

import ru.homelab.kidguard.core.domain.model.BreakMode
import ru.homelab.kidguard.core.domain.model.BreakSession
import ru.homelab.kidguard.core.domain.model.ContinuousUsageSession
import ru.homelab.kidguard.core.domain.repository.BreakRuntimeRepository
import ru.homelab.kidguard.data.db.dao.BreakRuntimeDao
import ru.homelab.kidguard.data.db.entity.BreakSessionEntity
import ru.homelab.kidguard.data.db.entity.StickinessStateEntity
import javax.inject.Inject

class BreakRuntimeRepositoryImpl @Inject constructor(
    private val dao: BreakRuntimeDao
) : BreakRuntimeRepository {

    override suspend fun loadActiveSession(): BreakSession? = dao.activeSession()?.toDomain()

    override suspend fun saveSession(session: BreakSession) {
        dao.upsertSession(session.toEntity(BreakSessionEntity.STATUS_ACTIVE))
    }

    override suspend fun cancelSession(sessionId: String) {
        dao.cancelSessionIfActive(sessionId)
    }

    override suspend fun completeBreakAndResetUsage(sessionId: String) {
        dao.completeBreakAndResetUsage(sessionId)
    }

    override suspend fun loadUsageSnapshot(): ContinuousUsageSession? = dao.usageSnapshot()?.toDomain()

    override suspend fun saveUsageSnapshot(snapshot: ContinuousUsageSession) {
        dao.upsertUsageSnapshot(snapshot.toEntity())
    }

    private fun BreakSessionEntity.toDomain() = BreakSession(
        id = sessionId,
        mode = BreakMode.valueOf(mode),
        startedAtElapsedMs = startedAtElapsedMs,
        endsAtElapsedMs = endsAtElapsedMs,
        startedAtWallMs = startedAtWallMs,
        endsAtWallMs = endsAtWallMs,
        bootCount = bootCount,
        windowKey = windowKey
    )

    private fun BreakSession.toEntity(status: String) = BreakSessionEntity(
        sessionId = id,
        mode = mode.name,
        startedAtElapsedMs = startedAtElapsedMs,
        endsAtElapsedMs = endsAtElapsedMs,
        startedAtWallMs = startedAtWallMs,
        endsAtWallMs = endsAtWallMs,
        bootCount = bootCount,
        windowKey = windowKey,
        status = status
    )

    private fun StickinessStateEntity.toDomain() = ContinuousUsageSession(
        cycleId = cycleId,
        accumulatedMs = accumulatedMs,
        idleSinceElapsedMs = idleSinceElapsedMs,
        lastObservedElapsedMs = lastObservedElapsedMs
    )

    private fun ContinuousUsageSession.toEntity() = StickinessStateEntity(
        cycleId = cycleId,
        accumulatedMs = accumulatedMs,
        idleSinceElapsedMs = idleSinceElapsedMs,
        lastObservedElapsedMs = lastObservedElapsedMs
    )
}
