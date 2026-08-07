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

    override suspend fun setScreenTime(date: LocalDate, seconds: Int) {
        usageDao.setSeconds(date.toString(), seconds)
    }

    override fun overrunSeconds(date: LocalDate): Flow<Int> =
        usageDao.overrunForDate(date.toString()).map { it ?: 0 }

    override suspend fun addOverrunTime(date: LocalDate, seconds: Int) {
        usageDao.addOverrunSeconds(date.toString(), seconds)
    }

    override fun appScreenTimeSeconds(date: LocalDate, packageName: String): Flow<Int> =
        usageDao.appSecondsForDate(date.toString(), packageName).map { it ?: 0 }

    override fun appScreenTimeByPackage(date: LocalDate): Flow<Map<String, Int>> =
        usageDao.appSecondsForDate(date.toString()).map { rows ->
            rows.associate { it.packageName to it.seconds }
        }

    override fun appOverrunByPackage(date: LocalDate): Flow<Map<String, Int>> =
        usageDao.appSecondsForDate(date.toString()).map { rows ->
            rows.associate { it.packageName to it.overrunSeconds }
        }

    override fun appTotalScreenTimeByPackage(date: LocalDate): Flow<Map<String, Int>> =
        usageDao.appSecondsForDate(date.toString()).map { rows ->
            rows.associate { it.packageName to (it.seconds + it.overrunSeconds) }
        }

    override suspend fun addAppScreenTime(date: LocalDate, packageName: String, seconds: Int) {
        usageDao.addAppSeconds(date.toString(), packageName, seconds)
    }

    override suspend fun addAppOverrunTime(date: LocalDate, packageName: String, seconds: Int) {
        usageDao.addAppOverrunSeconds(date.toString(), packageName, seconds)
    }

    override suspend fun resetScreenTime(date: LocalDate) {
        usageDao.deleteForDate(date.toString())
    }

    override suspend fun resetAppScreenTime(date: LocalDate) {
        usageDao.deleteAppSecondsForDate(date.toString())
    }
}
