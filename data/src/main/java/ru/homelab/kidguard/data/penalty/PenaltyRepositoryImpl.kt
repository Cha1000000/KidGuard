package ru.homelab.kidguard.data.penalty

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.homelab.kidguard.core.domain.model.PenaltyGrant
import ru.homelab.kidguard.core.domain.repository.PenaltyRepository
import ru.homelab.kidguard.data.db.dao.PenaltyDao
import ru.homelab.kidguard.data.db.entity.PenaltyGrantEntity
import java.time.LocalDate
import javax.inject.Inject

/** Пустая строка в БД — маркер штрафа телефона (в домене это `null`), как и у бонусов. */
private const val PHONE_PENALTY_MARKER = ""

class PenaltyRepositoryImpl @Inject constructor(
    private val penaltyDao: PenaltyDao
) : PenaltyRepository {

    override fun phonePenalty(date: LocalDate): Flow<PenaltyGrant?> =
        penaltyDao.penaltyFor(date.toString(), PHONE_PENALTY_MARKER).map { it?.toDomain() }

    override suspend fun addPenalty(
        date: LocalDate,
        packageName: String?,
        minutes: Int,
        comment: String
    ) {
        penaltyDao.addMinutes(
            date.toString(),
            packageName ?: PHONE_PENALTY_MARKER,
            minutes,
            comment
        )
    }

    override suspend fun setComment(date: LocalDate, packageName: String?, comment: String) {
        penaltyDao.updateComment(date.toString(), packageName ?: PHONE_PENALTY_MARKER, comment)
    }

    override suspend fun clearPenalty(date: LocalDate, packageName: String?) {
        penaltyDao.clear(date.toString(), packageName ?: PHONE_PENALTY_MARKER)
    }

    override fun observeAll(): Flow<List<PenaltyGrant>> =
        penaltyDao.observeAll().map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun replaceAll(grants: List<PenaltyGrant>) {
        penaltyDao.replaceAll(
            grants.map {
                PenaltyGrantEntity(it.date.toString(), it.packageName, it.minutes, it.comment)
            }
        )
    }

    override suspend fun deleteOlderThan(date: LocalDate) {
        penaltyDao.deleteOlderThan(date.toString())
    }
}

/** Битую дату пропускаем: лучше потерять запись, чем уронить синхронизацию (как у бонусов). */
private fun PenaltyGrantEntity.toDomain(): PenaltyGrant? = runCatching {
    PenaltyGrant(LocalDate.parse(date), packageName, minutes, comment)
}.getOrNull()
