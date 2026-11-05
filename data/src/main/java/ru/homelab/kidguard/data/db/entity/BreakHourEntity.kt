package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Час перерыва режима HOURS: минуты от полуночи (15:00 = 900), по одной строке на каждый час.
 *
 * [rulesId] — связь со строкой `break_rules` (single-row, всегда 0): она позволяет читать
 * правила и часы одним @Relation-запросом в одной транзакции, чтобы наблюдатель не видел
 * полуприменённую запись (D18).
 */
@Entity(tableName = "break_hour")
data class BreakHourEntity(
    @PrimaryKey val minuteOfDay: Int,
    val rulesId: Int = 0
)
