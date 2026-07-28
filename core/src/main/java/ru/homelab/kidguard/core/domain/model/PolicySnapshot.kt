package ru.homelab.kidguard.core.domain.model

import java.time.DayOfWeek

/**
 * Вся политика ребёнка одним значением — то, что приезжает с сервера и целиком заменяет
 * локальные правила ([ru.homelab.kidguard.core.domain.repository.PolicyRepository.replaceAll]).
 *
 * Отдельный тип вместо длинного списка параметров: правил становится больше с каждой фичей, а
 * перепутанные местами аргументы одного типа (два `Set<String>`, две карты дней недели)
 * компилятор бы не поймал. Дефолты позволяют собрать «пустую политику» одной строкой.
 */
data class PolicySnapshot(
    val dailyLimits: Map<DayOfWeek, Int> = emptyMap(),
    val appLimits: Map<String, Int> = emptyMap(),
    val whitelist: Set<String> = emptySet(),
    val blockedApps: Set<String> = emptySet(),
    val blockedSites: List<BlockedSite> = emptyList(),
    val blockGoogleSearch: Boolean = false,
    val studySchedule: ScheduleRules = ScheduleRules.EMPTY,
    val sleepSchedule: ScheduleRules = ScheduleRules.EMPTY,
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val breakRules: BreakRules = BreakRules.EMPTY,
    val dailyUsageReset: DailyUsageReset? = null
)
