package ru.homelab.kidguard.core.domain.model

import java.time.LocalDate

/**
 * Маркер блокировки на сегодня: родитель обнуляет ДОСТУПНОЕ ребёнку время (противоположность
 * [DailyUsageReset], который время возвращает). Едет в policy-документе по тому же образцу.
 * [issuedAt] — метка времени нажатия (epoch-ms), идемпотентный ключ: ребёнок применяет только
 * маркер новее уже применённого.
 */
data class DailyUsageBlock(val date: LocalDate, val issuedAt: Long)

/**
 * Пора ли применить блокировку: маркер есть, он на сегодня и новее последнего применённого.
 * Вчерашний маркер после полуночи игнорируется; повторный тот же — тоже (idempotent).
 */
fun shouldApplyBlock(marker: DailyUsageBlock?, today: LocalDate, lastAppliedAt: Long): Boolean =
    marker != null && marker.date == today && marker.issuedAt > lastAppliedAt
