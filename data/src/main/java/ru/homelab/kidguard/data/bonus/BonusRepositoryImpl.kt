package ru.homelab.kidguard.data.bonus

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.data.db.dao.BonusDao
import java.time.LocalDate
import javax.inject.Inject

/** Пустая строка в БД — маркер бонуса телефона (в домене это `null`). */
private const val PHONE_BONUS_MARKER = ""

class BonusRepositoryImpl @Inject constructor(
    private val bonusDao: BonusDao
) : BonusRepository {

    override fun phoneBonusMinutes(date: LocalDate): Flow<Int> =
        bonusDao.minutesFor(date.toString(), PHONE_BONUS_MARKER).map { it ?: 0 }

    override fun appBonusMinutes(date: LocalDate): Flow<Map<String, Int>> =
        bonusDao.appBonusesForDate(date.toString()).map { rows ->
            rows.associate { it.packageName to it.minutes }
        }

    override suspend fun addBonus(date: LocalDate, packageName: String?, minutes: Int) {
        bonusDao.addMinutes(date.toString(), packageName ?: PHONE_BONUS_MARKER, minutes)
    }

    override suspend fun clearBonus(date: LocalDate, packageName: String?) {
        bonusDao.clear(date.toString(), packageName ?: PHONE_BONUS_MARKER)
    }
}
