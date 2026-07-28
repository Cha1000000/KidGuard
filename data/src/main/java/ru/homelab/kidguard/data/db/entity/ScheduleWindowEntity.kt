package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity

/**
 * Окно блокировки по расписанию на конкретный день недели.
 *
 * `kind` — имя [ru.homelab.kidguard.core.domain.model.ScheduleKind] ("STUDY"/"SLEEP"): два
 * независимых расписания живут в одной таблице, как строки с разным типом. `dayOfWeek` —
 * значение java.time.DayOfWeek (1–7), день **начала** окна. Границы — минуты от полуночи;
 * `endMinute < startMinute` означает переход через полночь (см. `TimeWindow`).
 */
@Entity(tableName = "schedule_window", primaryKeys = ["kind", "dayOfWeek"])
data class ScheduleWindowEntity(
    val kind: String,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int
)
