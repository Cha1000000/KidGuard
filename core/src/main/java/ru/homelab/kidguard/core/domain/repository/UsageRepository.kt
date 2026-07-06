package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Накопленное реальное экранное время по дням. Движок учёта (шаг 2.3) прибавляет секунды,
 * а проверка лимита и UI читают накопленное за нужный день.
 */
interface UsageRepository {

    /** Накопленное экранное время (в секундах) за указанный день. */
    fun screenTimeSeconds(date: LocalDate): Flow<Int>

    /** Прибавить секунды реального экранного времени к указанному дню. */
    suspend fun addScreenTime(date: LocalDate, seconds: Int)
}
