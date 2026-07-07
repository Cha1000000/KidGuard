package ru.homelab.kidguard.data.usage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.homelab.kidguard.core.domain.repository.UsageRepository
import ru.homelab.kidguard.data.db.dao.UsageDao
import java.time.LocalDate
import javax.inject.Inject

class UsageRepositoryImpl @Inject constructor(
    private val usageDao: UsageDao
) : UsageRepository {

    override fun screenTimeSeconds(date: LocalDate): Flow<Int> =
        usageDao.secondsForDate(date.toString()).map { it ?: 0 }

    override suspend fun addScreenTime(date: LocalDate, seconds: Int) {
        usageDao.addSeconds(date.toString(), seconds)
    }

    override fun appScreenTimeSeconds(date: LocalDate, packageName: String): Flow<Int> =
        usageDao.appSecondsForDate(date.toString(), packageName).map { it ?: 0 }

    override suspend fun addAppScreenTime(date: LocalDate, packageName: String, seconds: Int) {
        usageDao.addAppSeconds(date.toString(), packageName, seconds)
    }
}
