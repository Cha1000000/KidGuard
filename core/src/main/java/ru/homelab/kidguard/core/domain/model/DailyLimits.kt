package ru.homelab.kidguard.core.domain.model

import java.time.DayOfWeek

/**
 * Дневной лимит экранного времени по дням недели (в минутах).
 * Отсутствие значения для дня трактуется как 0 (в этот день доступа нет).
 */
data class DailyLimits(
    val minutesByDay: Map<DayOfWeek, Int>
) {
    /**
     * Лимит (минут) на конкретный день недели, либо null, если родитель лимит не задавал
     * (в этот день ограничения нет). Значение 0 означает явный запрет (0 минут доступа).
     */
    fun limitFor(day: DayOfWeek): Int? = minutesByDay[day]

    companion object {
        val EMPTY = DailyLimits(emptyMap())
    }
}
