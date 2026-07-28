package ru.homelab.kidguard.data.policy

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.homelab.kidguard.core.domain.model.BlockedSite
import ru.homelab.kidguard.core.domain.model.BreakMode
import ru.homelab.kidguard.core.domain.model.BreakRules
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.EmergencyContact
import ru.homelab.kidguard.core.domain.model.PinProtection
import ru.homelab.kidguard.core.domain.model.PolicySnapshot
import ru.homelab.kidguard.core.domain.model.ScheduleKind
import ru.homelab.kidguard.core.domain.model.ScheduleRules
import ru.homelab.kidguard.core.domain.model.SiteBlockRules
import ru.homelab.kidguard.core.domain.model.TimeWindow
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.data.db.dao.PolicyDao
import ru.homelab.kidguard.data.db.dao.PolicyEntities
import ru.homelab.kidguard.data.db.entity.AppLimitEntity
import ru.homelab.kidguard.data.db.entity.BlockedAppEntity
import ru.homelab.kidguard.data.db.entity.BlockedSiteEntity
import ru.homelab.kidguard.data.db.entity.BreakRulesEntity
import ru.homelab.kidguard.data.db.entity.DayLimitEntity
import ru.homelab.kidguard.data.db.entity.EmergencyContactEntity
import ru.homelab.kidguard.data.db.entity.PinEntity
import ru.homelab.kidguard.data.db.entity.PolicyFlagsEntity
import ru.homelab.kidguard.data.db.entity.ScheduleWindowEntity
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

    override val blockedSites: Flow<List<BlockedSite>> = policyDao.blockedSites().map { rows ->
        rows.map { BlockedSite(it.domain, it.enabled) }
    }

    override val blockGoogleSearch: Flow<Boolean> = policyDao.policyFlags().map { it?.blockGoogleSearch ?: false }

    override val siteBlockRules: Flow<SiteBlockRules> = combine(blockedSites, blockGoogleSearch) { sites, google ->
        SiteBlockRules(sites.filter { it.enabled }.map { it.domain }.toSet(), google)
    }

    // Окна обоих расписаний лежат в одной таблице — делим по kind и склеиваем с тумблером.
    private val scheduleWindows: Flow<Map<String, Map<DayOfWeek, TimeWindow>>> =
        policyDao.scheduleWindows().map { rows ->
            rows.groupBy { it.kind }.mapValues { (_, windows) ->
                windows.associate { DayOfWeek.of(it.dayOfWeek) to TimeWindow(it.startMinute, it.endMinute) }
            }
        }

    override val studySchedule: Flow<ScheduleRules> = scheduleRules(ScheduleKind.STUDY)

    override val sleepSchedule: Flow<ScheduleRules> = scheduleRules(ScheduleKind.SLEEP)

    override val emergencyContacts: Flow<List<EmergencyContact>> =
        policyDao.emergencyContacts().map { rows -> rows.map { EmergencyContact(it.name, it.phone) } }

    // Строка настроек + отдельная таблица часов режима HOURS склеиваются в одну модель домена.
    override val breakRules: Flow<BreakRules> =
        combine(policyDao.breakRules(), policyDao.breakHours()) { row, hours ->
            if (row == null) {
                BreakRules.EMPTY
            } else {
                BreakRules(
                    enabled = row.enabled,
                    mode = BreakMode.valueOf(row.mode),
                    intervalMinutes = row.intervalMinutes,
                    hours = hours.toSet(),
                    durationMinutes = row.durationMinutes,
                    message = row.message
                )
            }
        }

    override val pinProtection: Flow<PinProtection?> = policyDao.pin().map { entity ->
        val hash = entity?.pinHash
        val salt = entity?.pinSalt
        if (hash != null && salt != null) PinProtection(hash, salt) else null
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

    override suspend fun addBlockedSite(domain: String) {
        policyDao.upsertBlockedSite(BlockedSiteEntity(domain, enabled = true))
    }

    override suspend fun setSiteEnabled(domain: String, enabled: Boolean) {
        policyDao.setSiteEnabled(domain, enabled)
    }

    override suspend fun removeBlockedSite(domain: String) {
        policyDao.removeBlockedSite(domain)
    }

    override suspend fun setBlockGoogleSearch(enabled: Boolean) {
        policyDao.setBlockGoogleSearchFlag(enabled)
    }

    override suspend fun setScheduleWindow(kind: ScheduleKind, day: DayOfWeek, window: TimeWindow?) {
        if (window == null) {
            policyDao.deleteScheduleWindow(kind.name, day.value)
        } else {
            policyDao.upsertScheduleWindow(
                ScheduleWindowEntity(kind.name, day.value, window.startMinute, window.endMinute)
            )
        }
    }

    override suspend fun setScheduleEnabled(kind: ScheduleKind, enabled: Boolean) {
        when (kind) {
            ScheduleKind.STUDY -> policyDao.setStudyScheduleEnabledFlag(enabled)
            ScheduleKind.SLEEP -> policyDao.setSleepScheduleEnabledFlag(enabled)
        }
    }

    override suspend fun addEmergencyContact(contact: EmergencyContact) {
        policyDao.upsertEmergencyContact(EmergencyContactEntity(contact.phone, contact.name))
    }

    override suspend fun updateEmergencyContact(oldPhone: String, contact: EmergencyContact) {
        policyDao.updateEmergencyContact(
            oldPhone,
            EmergencyContactEntity(contact.phone, contact.name)
        )
    }

    override suspend fun removeEmergencyContact(phone: String) {
        policyDao.removeEmergencyContact(phone)
    }

    override suspend fun setPin(hash: String, salt: String) {
        policyDao.upsertPin(PinEntity(pinHash = hash, pinSalt = salt))
    }

    override suspend fun clearPin() {
        policyDao.deletePin()
    }

    override suspend fun setBreakRules(rules: BreakRules) {
        policyDao.upsertBreakRules(rules.toEntity())
        policyDao.replaceBreakHours(rules.hours)
    }

    override suspend fun resetBreaks() = setBreakRules(BreakRules.EMPTY)

    override suspend fun replaceAll(snapshot: PolicySnapshot) {
        val hash = snapshot.pinHash
        val salt = snapshot.pinSalt
        policyDao.replaceAllPolicy(
            PolicyEntities(
                dayLimits = snapshot.dailyLimits.map { (day, minutes) -> DayLimitEntity(day.value, minutes) },
                appLimits = snapshot.appLimits.map { (pkg, minutes) -> AppLimitEntity(pkg, minutes) },
                whitelist = snapshot.whitelist.map { WhitelistedAppEntity(it) },
                blockedApps = snapshot.blockedApps.map { BlockedAppEntity(it) },
                blockedSites = snapshot.blockedSites.map { BlockedSiteEntity(it.domain, it.enabled) },
                scheduleWindows = snapshot.studySchedule.toEntities(ScheduleKind.STUDY) +
                    snapshot.sleepSchedule.toEntities(ScheduleKind.SLEEP),
                emergencyContacts = snapshot.emergencyContacts.map { EmergencyContactEntity(it.phone, it.name) },
                flags = PolicyFlagsEntity(
                    blockGoogleSearch = snapshot.blockGoogleSearch,
                    studyScheduleEnabled = snapshot.studySchedule.enabled,
                    sleepScheduleEnabled = snapshot.sleepSchedule.enabled
                ),
                pin = if (hash != null && salt != null) PinEntity(pinHash = hash, pinSalt = salt) else null,
                breakRules = snapshot.breakRules.toEntity(),
                breakHours = snapshot.breakRules.hours.toList()
            )
        )
    }

    /** Окна расписания [kind] + его тумблер из общей строки флагов. */
    private fun scheduleRules(kind: ScheduleKind): Flow<ScheduleRules> =
        combine(scheduleWindows, policyDao.policyFlags()) { windowsByKind, flags ->
            val enabled = when (kind) {
                ScheduleKind.STUDY -> flags?.studyScheduleEnabled
                ScheduleKind.SLEEP -> flags?.sleepScheduleEnabled
            } ?: false
            ScheduleRules(windowsByKind[kind.name].orEmpty(), enabled)
        }

    private fun ScheduleRules.toEntities(kind: ScheduleKind): List<ScheduleWindowEntity> =
        windowsByDay.map { (day, window) ->
            ScheduleWindowEntity(kind.name, day.value, window.startMinute, window.endMinute)
        }

    private fun BreakRules.toEntity(): BreakRulesEntity = BreakRulesEntity(
        enabled = enabled,
        mode = mode.name,
        intervalMinutes = intervalMinutes,
        durationMinutes = durationMinutes,
        message = message
    )
}
