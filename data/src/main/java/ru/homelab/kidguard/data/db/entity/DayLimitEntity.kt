package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Лимит экранного времени на день недели. dayOfWeek — значение java.time.DayOfWeek (1–7). */
@Entity(tableName = "day_limit")
data class DayLimitEntity(
    @PrimaryKey val dayOfWeek: Int,
    val minutes: Int
)
