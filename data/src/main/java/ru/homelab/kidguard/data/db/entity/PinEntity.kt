package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Родительский PIN (веха 6.1) — single-row таблица (всегда `id = 0`, как скаляр-настройка).
 * `pinHash`/`pinSalt` оба null, пока PIN не задан.
 */
@Entity(tableName = "pin_protection")
data class PinEntity(
    @PrimaryKey val id: Int = 0,
    val pinHash: String?,
    val pinSalt: String?
)
