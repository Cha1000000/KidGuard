package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity

/**
 * Разовый бонус (дополнительное время) на день. `packageName` = "" — бонус на весь телефон;
 * иначе — бонус для конкретного приложения. Выдачи за один день суммируются (веха 3Б, Room v4).
 */
@Entity(tableName = "bonus_grants", primaryKeys = ["date", "packageName"])
data class BonusGrantEntity(
    val date: String,
    val packageName: String,
    val minutes: Int
)
