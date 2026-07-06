package ru.homelab.kidguard.core.domain.model

import java.time.DayOfWeek

/**
 * Дневной лимит экранного времени по дням недели (в минутах).
 * Отсутствие значения для дня трактуется как 0 (в этот день доступа нет).
 */
data class DailyLimits(
    val minutesByDay: Map<DayOfWeek, Int>
) {
    /** Лимит (минут) на конкретный день недели. */
    fun minutesFor(day: DayOfWeek): Int = minutesByDay[day] ?: 0

    companion object {
        val EMPTY = DailyLimits(emptyMap())
    }
}
