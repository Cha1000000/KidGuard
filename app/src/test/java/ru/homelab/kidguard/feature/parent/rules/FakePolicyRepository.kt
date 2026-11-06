package ru.homelab.kidguard.feature.parent.rules

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import ru.homelab.kidguard.core.domain.model.BlockedSite
import ru.homelab.kidguard.core.domain.model.BreakRules
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.model.DailyUsageBlock
import ru.homelab.kidguard.core.domain.model.DailyUsageReset
import ru.homelab.kidguard.core.domain.model.DailyUsageUnblock
import ru.homelab.kidguard.core.domain.model.EmergencyContact
import ru.homelab.kidguard.core.domain.model.PinProtection
import ru.homelab.kidguard.core.domain.model.PolicySnapshot
import ru.homelab.kidguard.core.domain.model.ScheduleKind
import ru.homelab.kidguard.core.domain.model.ScheduleRules
import ru.homelab.kidguard.core.domain.model.SiteBlockRules
import ru.homelab.kidguard.core.domain.model.TimeWindow
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Фейк [PolicyRepository] для тестов `BreaksViewModel`: функциональны только перерывы и дневные
 * лимиты (единственное, что экран «Перерывы» читает и пишет); остальное падает явной ошибкой,
 * чтобы случайное использование не прошло незамеченным.
 *
 * Задержки намеренны и моделируют реальные гонки этапа 7:
 * - [rulesEmitDelayMs] — Room-флоу выдаёт первое значение не мгновенно: действие до загрузки UI
 *   обязано ждать фактическое состояние репозитория, а не писать `BreakRules.EMPTY`;
 * - [rulesWriteDelayMs] — запись приезжает не сразу: более старая запись не должна завершиться
 *   позже более новой (например, сброса) и затереть её.
 */
class FakePolicyRepository(
    initialRules: BreakRules = BreakRules.EMPTY,
    private val rulesEmitDelayMs: Long = 0L,
    private val rulesWriteDelayMs: Long = 0L
) : PolicyRepository {

    private val savedRules = MutableStateFlow(initialRules)

    /** Все записи по порядку — чтобы проверять, что реально уехало в репозиторий и в каком виде. */
    val writtenRules = mutableListOf<BreakRules>()

    /** Текущее сохранённое состояние (то, что видит ребёнок после синка). */
    val saved: BreakRules get() = savedRules.value

    override val dailyLimits: Flow<DailyLimits> = MutableStateFlow(DailyLimits.EMPTY)

    override val breakRules: Flow<BreakRules>
        get() = flow {
            if (rulesEmitDelayMs > 0) delay(rulesEmitDelayMs)
            emitAll(savedRules)
        }

    override suspend fun setBreakRules(rules: BreakRules) {
        if (rulesWriteDelayMs > 0) delay(rulesWriteDelayMs)
        writtenRules += rules
        savedRules.value = rules
    }

    override suspend fun resetBreaks() {
        writtenRules += BreakRules.EMPTY
        savedRules.value = BreakRules.EMPTY
    }

    // Ниже — неиспользуемые экраном «Перерывы» контракты: падаем явно, а не подсовываем нули.

    override val whitelist: Flow<Set<String>> get() = error("не используется в тестах BreaksViewModel")
    override val appLimits: Flow<Map<String, Int>> get() = error("не используется в тестах BreaksViewModel")
    override val blockedApps: Flow<Set<String>> get() = error("не используется в тестах BreaksViewModel")
    override val blockedSites: Flow<List<BlockedSite>> get() = error("не используется в тестах BreaksViewModel")
    override val blockGoogleSearch: Flow<Boolean> get() = error("не используется в тестах BreaksViewModel")
    override val siteBlockRules: Flow<SiteBlockRules> get() = error("не используется в тестах BreaksViewModel")
    override val studySchedule: Flow<ScheduleRules> get() = error("не используется в тестах BreaksViewModel")
    override val sleepSchedule: Flow<ScheduleRules> get() = error("не используется в тестах BreaksViewModel")
    override val emergencyContacts: Flow<List<EmergencyContact>> get() = error("не используется в тестах BreaksViewModel")
    override val pinProtection: Flow<PinProtection?> get() = error("не используется в тестах BreaksViewModel")
    override val dailyUsageReset: Flow<DailyUsageReset?> get() = error("не используется в тестах BreaksViewModel")
    override val dailyUsageBlock: Flow<DailyUsageBlock?> get() = error("не используется в тестах BreaksViewModel")
    override val dailyUsageUnblock: Flow<DailyUsageUnblock?> get() = error("не используется в тестах BreaksViewModel")

    override suspend fun setDailyLimit(day: DayOfWeek, minutes: Int?): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun setAppLimit(packageName: String, minutes: Int?): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun setWhitelisted(packageName: String, whitelisted: Boolean): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun setBlocked(packageName: String, blocked: Boolean): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun addBlockedSite(domain: String): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun setSiteEnabled(domain: String, enabled: Boolean): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun removeBlockedSite(domain: String): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun setBlockGoogleSearch(enabled: Boolean): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun setScheduleWindow(kind: ScheduleKind, day: DayOfWeek, window: TimeWindow?): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun setScheduleEnabled(kind: ScheduleKind, enabled: Boolean): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun addEmergencyContact(contact: EmergencyContact): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun removeEmergencyContact(phone: String): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun updateEmergencyContact(oldPhone: String, contact: EmergencyContact): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun setPin(hash: String, salt: String): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun clearPin(): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun setDailyUsageReset(date: LocalDate, issuedAt: Long): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun setDailyUsageBlock(date: LocalDate, issuedAt: Long): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun setDailyUsageUnblock(date: LocalDate, issuedAt: Long, restoreRemaining: Boolean): Unit = error("не используется в тестах BreaksViewModel")
    override suspend fun replaceAll(snapshot: PolicySnapshot): Unit = error("не используется в тестах BreaksViewModel")
}
