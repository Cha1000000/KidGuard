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

    /** Накопленное экранное время приложения (в секундах) за указанный день (веха 3). */
    fun appScreenTimeSeconds(date: LocalDate, packageName: String): Flow<Int>

    /** Расход всех приложений за день: пакет → секунды (для списка настройки лимитов). */
    fun appScreenTimeByPackage(date: LocalDate): Flow<Map<String, Int>>

    /** Прибавить приложению секунды реального экранного времени за указанный день. */
    suspend fun addAppScreenTime(date: LocalDate, packageName: String, seconds: Int)
}
