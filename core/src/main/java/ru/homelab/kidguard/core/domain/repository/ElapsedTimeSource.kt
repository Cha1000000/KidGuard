package ru.homelab.kidguard.core.domain.repository

/**
 * Монотонное время с момента загрузки устройства (Android: `SystemClock.elapsedRealtime`).
 *
 * Зачем отдельный интерфейс: системным часам верить нельзя — ребёнок переведёт время вперёд и
 * снимет блокировку PIN. Это время часам не подчиняется. Плюс `SystemClock` недоступен в
 * юнит-тестах `:core` (чистый JUnit, без Robolectric), а через интерфейс он подменяется фейком.
 */
interface ElapsedTimeSource {

    /** Миллисекунды с загрузки устройства. Только для измерения интервалов, не для дат. */
    fun elapsedRealtimeMs(): Long
}
