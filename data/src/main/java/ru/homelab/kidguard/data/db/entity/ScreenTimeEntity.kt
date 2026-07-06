package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Накопленное реальное экранное время за день. date — ISO-строка LocalDate (YYYY-MM-DD). */
@Entity(tableName = "screen_time")
data class ScreenTimeEntity(
    @PrimaryKey val date: String,
    val seconds: Int
)
