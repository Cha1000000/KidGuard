package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity

/**
 * Накопленное реальное экранное время приложения за день (веха 3, Room v3).
 * date — ISO-строка LocalDate (YYYY-MM-DD), ключ составной: день + пакет.
 */
@Entity(tableName = "app_screen_time", primaryKeys = ["date", "packageName"])
data class AppScreenTimeEntity(
    val date: String,
    val packageName: String,
    val seconds: Int
)
