package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity

/**
 * Назначенный штраф (снятое время) на день. `packageName` = "" — штраф на весь телефон;
 * поле заведено про запас, штрафов для отдельных приложений пока нет. Штрафы за один день
 * суммируются, комментарий перезаписывается последним (Room v13).
 *
 * `minutes` — положительное число снятых минут: знак живёт в формуле бюджета, а не в данных.
 */
@Entity(tableName = "penalty_grants", primaryKeys = ["date", "packageName"])
data class PenaltyGrantEntity(
    val date: String,
    val packageName: String,
    val minutes: Int,
    val comment: String
)
