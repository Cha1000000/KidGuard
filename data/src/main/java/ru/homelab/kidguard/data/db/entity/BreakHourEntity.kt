package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Час перерыва режима HOURS: минуты от полуночи (15:00 = 900), по одной строке на каждый час. */
@Entity(tableName = "break_hour")
data class BreakHourEntity(
    @PrimaryKey val minuteOfDay: Int
)
