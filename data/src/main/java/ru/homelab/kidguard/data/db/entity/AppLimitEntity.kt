package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Личный дневной лимит приложения (минут/день). Добавлено в вехе 3 (Room v2). */
@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val minutes: Int
)
