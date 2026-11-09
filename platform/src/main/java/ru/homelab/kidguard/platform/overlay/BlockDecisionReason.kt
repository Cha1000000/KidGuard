package ru.homelab.kidguard.platform.overlay

import ru.homelab.kidguard.core.domain.model.ScheduleState
import java.time.format.DateTimeFormatter

/**
 * Причина и время мягкого блока — чистые функции, вынесенные из `BlockingController` для JVM-тестов.
 *
 * Приоритет: запрет родителя бьёт расписание, расписание бьёт обычный лимит. Время окончания
 * показывается только для «Времени учёбы».
 */
fun blockReasonFor(
    packageName: String?,
    alwaysAllowed: Set<String>,
    blockedApps: Set<String>,
    studyTimeActive: Boolean
): BlockReason = when {
    packageName != null && packageName !in alwaysAllowed && packageName in blockedApps ->
        BlockReason.BLOCKED_BY_PARENT
    studyTimeActive -> BlockReason.STUDY_TIME
    else -> BlockReason.LIMIT_EXPIRED
}

/** «14:00» для учёбы; null для остальных причин — у них подзаголовок фиксированный. */
fun studyUntilText(
    scheduleState: ScheduleState,
    reason: BlockReason,
    formatter: DateTimeFormatter
): String? = (scheduleState as? ScheduleState.Study)
    ?.takeIf { reason == BlockReason.STUDY_TIME }
    ?.endsAt
    ?.format(formatter)
