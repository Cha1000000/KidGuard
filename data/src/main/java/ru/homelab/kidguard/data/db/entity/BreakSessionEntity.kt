package ru.homelab.kidguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сессия уже начавшегося перерыва (план «исправление перерывов», этап 2). Runtime-состояние
 * устройства ребёнка: в `PolicySnapshot` и серверную синхронизацию НЕ входит — `replaceAllPolicy`
 * эту таблицу не трогает.
 *
 * Статус хранится строкой, чтобы история сессий сохранялась: идемпотентность завершения по
 * `sessionId` проверяется переходом ACTIVE → COMPLETED, а не удалением строки.
 */
@Entity(tableName = "break_session")
data class BreakSessionEntity(
    @PrimaryKey val sessionId: String,
    val mode: String,
    val startedAtElapsedMs: Long,
    val endsAtElapsedMs: Long,
    val startedAtWallMs: Long,
    val endsAtWallMs: Long,
    /** Системный BOOT_COUNT на момент старта; null — прошивка его не отдаёт. */
    val bootCount: Int?,
    /** Стабильный ключ объединённого окна HOURS; у INTERVAL — null. */
    val windowKey: String?,
    /** ACTIVE / COMPLETED / CANCELLED. */
    val status: String
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
