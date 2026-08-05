package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Накопленное реальное экранное время по дням. Движок учёта (шаг 2.3) прибавляет секунды,
 * а проверка лимита и UI читают накопленное за нужный день.
 *
 * Счётчиков два, и они считают РАЗНОЕ:
 * - [screenTimeSeconds] — время, расходующее дневной лимит (без «Всегда доступных», лаунчера и
 *   самого KidGuard);
 * - [appScreenTimeByPackage] — фактическое время по всем приложениям без исключений; его сумма и
 *   есть «всё экранное время за день».
 */
interface UsageRepository {

    /**
     * Накопленное за день время, которое **расходует дневной лимит** (в секундах). Приложения,
     * которые лимит не закрывает (родительский список «Всегда доступные», домашний лаунчер, само
     * KidGuard), сюда не попадают — см. `ScreenTimeTracker`. Для «всего экранного времени» нужна
     * сумма по [appScreenTimeByPackage].
     */
    fun screenTimeSeconds(date: LocalDate): Flow<Int>

    /** Прибавить секунды к расходу дневного лимита за указанный день. */
    suspend fun addScreenTime(date: LocalDate, seconds: Int)

    /** Выставить общий экранный расход за день в АБСОЛЮТ (для «Заблокировать на сегодня»). */
    suspend fun setScreenTime(date: LocalDate, seconds: Int)

    /** Накопленное экранное время приложения (в секундах) за указанный день (веха 3). */
    fun appScreenTimeSeconds(date: LocalDate, packageName: String): Flow<Int>

    /** Расход всех приложений за день: пакет → секунды (для списка настройки лимитов). */
    fun appScreenTimeByPackage(date: LocalDate): Flow<Map<String, Int>>

    /** Прибавить приложению секунды реального экранного времени за указанный день. */
    suspend fun addAppScreenTime(date: LocalDate, packageName: String, seconds: Int)

    /** Обнулить общий экранный расход за день (сброс сегодняшнего лимита). */
    suspend fun resetScreenTime(date: LocalDate)

    /** Обнулить пер-app расход всех приложений за день (сброс сегодняшнего лимита). */
    suspend fun resetAppScreenTime(date: LocalDate)
}
