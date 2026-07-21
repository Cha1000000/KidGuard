package ru.homelab.kidguard.core.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.homelab.kidguard.core.domain.model.BlockedSite
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.EmergencyContact
import ru.homelab.kidguard.core.domain.model.PinProtection
import ru.homelab.kidguard.core.domain.model.PolicySnapshot
import ru.homelab.kidguard.core.domain.model.ScheduleKind
import ru.homelab.kidguard.core.domain.model.ScheduleRules
import ru.homelab.kidguard.core.domain.model.SiteBlockRules
import ru.homelab.kidguard.core.domain.model.TimeWindow
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import java.time.DayOfWeek

/**
 * Политика-заглушка для use-case тестов: отдаёт заданные значения и молча глотает записи.
 * Общая на все тесты, чтобы правка интерфейса [PolicyRepository] не расходилась по копиям.
 *
 * `PinGuardTest` намеренно использует СВОЙ, строгий фейк: там важно, чтобы обращение к любому
 * члену политики кроме PIN роняло тест.
 */
class FakePolicyRepository(
    dailyLimits: DailyLimits = DailyLimits.EMPTY,
    appLimits: Map<String, Int> = emptyMap(),
    whitelist: Set<String> = emptySet(),
    blockedApps: Set<String> = emptySet(),
    studySchedule: ScheduleRules = ScheduleRules.EMPTY,
    sleepSchedule: ScheduleRules = ScheduleRules.EMPTY,
    pinProtection: PinProtection? = null
) : PolicyRepository {

    override val dailyLimits: Flow<DailyLimits> = flowOf(dailyLimits)
    override val whitelist: Flow<Set<String>> = flowOf(whitelist)
    override val appLimits: Flow<Map<String, Int>> = flowOf(appLimits)
    override val blockedApps: Flow<Set<String>> = flowOf(blockedApps)
    override val blockedSites: Flow<List<BlockedSite>> = flowOf(emptyList())
    override val blockGoogleSearch: Flow<Boolean> = flowOf(false)
    override val siteBlockRules: Flow<SiteBlockRules> = flowOf(SiteBlockRules.NONE)
    override val studySchedule: Flow<ScheduleRules> = flowOf(studySchedule)
    override val sleepSchedule: Flow<ScheduleRules> = flowOf(sleepSchedule)
    override val emergencyContacts: Flow<List<EmergencyContact>> = flowOf(emptyList())
    override val pinProtection: Flow<PinProtection?> = flowOf(pinProtection)

    override suspend fun setDailyLimit(day: DayOfWeek, minutes: Int?) = Unit
    override suspend fun setAppLimit(packageName: String, minutes: Int?) = Unit
    override suspend fun setWhitelisted(packageName: String, whitelisted: Boolean) = Unit
    override suspend fun setBlocked(packageName: String, blocked: Boolean) = Unit
    override suspend fun addBlockedSite(domain: String) = Unit
    override suspend fun setSiteEnabled(domain: String, enabled: Boolean) = Unit
    override suspend fun removeBlockedSite(domain: String) = Unit
    override suspend fun setBlockGoogleSearch(enabled: Boolean) = Unit
    override suspend fun setScheduleWindow(kind: ScheduleKind, day: DayOfWeek, window: TimeWindow?) = Unit
    override suspend fun setScheduleEnabled(kind: ScheduleKind, enabled: Boolean) = Unit
    override suspend fun addEmergencyContact(contact: EmergencyContact) = Unit
    override suspend fun removeEmergencyContact(phone: String) = Unit
    override suspend fun setPin(hash: String, salt: String) = Unit
    override suspend fun clearPin() = Unit
    override suspend fun replaceAll(snapshot: PolicySnapshot) = Unit
}
