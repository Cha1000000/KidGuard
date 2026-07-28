package ru.homelab.kidguard.core.domain.model

import java.time.LocalDate

/**
 * Маркер сброса дневного лимита: родитель обнуляет израсходованное сегодня время ребёнку.
 * Едет в policy-документе (по образцу бонусов). [issuedAt] — метка времени нажатия (epoch-ms),
 * идемпотентный ключ: ребёнок применяет только маркер новее уже применённого.
 */
data class DailyUsageReset(val date: LocalDate, val issuedAt: Long)

/**
 * Пора ли применить сброс: маркер есть, он на сегодня и новее последнего применённого.
 * Вчерашний маркер после полуночи игнорируется; повторный тот же — тоже (idempotent).
 */
fun shouldApplyReset(marker: DailyUsageReset?, today: LocalDate, lastAppliedAt: Long): Boolean =
    marker != null && marker.date == today && marker.issuedAt > lastAppliedAt
