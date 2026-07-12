package ru.homelab.kidguard.data.policy

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.data.db.dao.PolicyDao
import ru.homelab.kidguard.data.db.entity.AppLimitEntity
import ru.homelab.kidguard.data.db.entity.BlockedAppEntity
import ru.homelab.kidguard.data.db.entity.DayLimitEntity
import ru.homelab.kidguard.data.db.entity.WhitelistedAppEntity
import java.time.DayOfWeek
import javax.inject.Inject

class PolicyRepositoryImpl @Inject constructor(
    private val policyDao: PolicyDao
) : PolicyRepository {

    override val dailyLimits: Flow<DailyLimits> = policyDao.dayLimits().map { rows ->
        DailyLimits(rows.associate { DayOfWeek.of(it.dayOfWeek) to it.minutes })
    }

    override val whitelist: Flow<Set<String>> = policyDao.whitelist().map { rows ->
        rows.map { it.packageName }.toSet()
    }

    override val appLimits: Flow<Map<String, Int>> = policyDao.appLimits().map { rows ->
        rows.associate { it.packageName to it.minutes }
    }

    override val blockedApps: Flow<Set<String>> = policyDao.blockedApps().map { rows ->
        rows.map { it.packageName }.toSet()
    }

    override suspend fun setDailyLimit(day: DayOfWeek, minutes: Int?) {
        if (minutes == null) {
            policyDao.deleteDayLimit(day.value)
        } else {
            policyDao.upsertDayLimit(DayLimitEntity(day.value, minutes))
        }
    }

    override suspend fun setAppLimit(packageName: String, minutes: Int?) {
        if (minutes == null) {
            policyDao.deleteAppLimit(packageName)
        } else {
            policyDao.upsertAppLimit(AppLimitEntity(packageName, minutes))
        }
    }

    override suspend fun setWhitelisted(packageName: String, whitelisted: Boolean) {
        if (whitelisted) {
            policyDao.addToWhitelist(WhitelistedAppEntity(packageName))
        } else {
            policyDao.removeFromWhitelist(packageName)
        }
    }

    override suspend fun setBlocked(packageName: String, blocked: Boolean) {
        if (blocked) {
            policyDao.addToBlocked(BlockedAppEntity(packageName))
        } else {
            policyDao.removeFromBlocked(packageName)
        }
    }

    override suspend fun replaceAll(
        dailyLimits: Map<DayOfWeek, Int>,
        appLimits: Map<String, Int>,
        whitelist: Set<String>,
        blockedApps: Set<String>
    ) {
        policyDao.replaceAllPolicy(
            dayLimits = dailyLimits.map { (day, minutes) -> DayLimitEntity(day.value, minutes) },
            appLimits = appLimits.map { (pkg, minutes) -> AppLimitEntity(pkg, minutes) },
            whitelist = whitelist.map { WhitelistedAppEntity(it) },
            blockedApps = blockedApps.map { BlockedAppEntity(it) }
        )
    }
}
