package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Снимок накопления непрерывного использования (счётчик «залипания») — single-row таблица
 * (`id = 0`). Сохраняется периодически и на системных событиях, чтобы смерть процесса не
 * обнуляла уже накопленное; отсутствие строки — «цикла нет, начинаем с нуля».
 */
@Entity(tableName = "stickiness_state")
data class StickinessStateEntity(
    @PrimaryKey val id: Int = 0,
    val cycleId: String,
    val accumulatedMs: Long,
    /** Начало текущей неактивности (экран погашен/заблокирован); null — экран активен. */
    val idleSinceElapsedMs: Long?,
    val lastObservedElapsedMs: Long
)
