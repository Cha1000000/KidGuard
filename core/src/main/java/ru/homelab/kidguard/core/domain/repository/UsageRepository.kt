package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Накопленное реальное экранное время по дням. Движок учёта (шаг 2.3) прибавляет секунды,
 * а проверка лимита и UI читают накопленное за нужный день.
 *
 * Счётчики считают РАЗНОЕ, и путать их нельзя:
 * - [screenTimeSeconds] — время, **израсходовавшее дневной бюджет** (без «Всегда доступных»,
 *   лаунчера и самого KidGuard). Бюджет не превышает никогда;
 * - [overrunSeconds] — время сверх исчерпанного дневного бюджета. Отдельный счётчик, потому что
 *   иначе выданный бонус сперва гасил бы накопленный перерасход, а не давал время с момента
 *   выдачи;
 * - [appScreenTimeByPackage] / [appScreenTimeSeconds] — время приложения в счёт его ЛИЧНОГО
 *   лимита, и [appOverrunByPackage] — сверх него;
 * - [appTotalScreenTimeByPackage] — фактическое время по приложениям (сумма двух предыдущих);
 *   его сумма и есть «всё экранное время за день», именно она нужна статистике.
 */
interface UsageRepository {

    /**
     * Накопленное за день время, которое **израсходовало дневной бюджет** (в секундах).
     * Приложения, которые лимит не закрывает (родительский список «Всегда доступные», домашний
     * лаунчер, само KidGuard), сюда не попадают — см. `ScreenTimeTracker`. Время сверх бюджета
     * тоже не попадает: оно в [overrunSeconds]. Для «всего экранного времени» нужна сумма по
     * [appTotalScreenTimeByPackage].
     */
    fun screenTimeSeconds(date: LocalDate): Flow<Int>

    /** Прибавить секунды к расходу дневного лимита за указанный день. */
    suspend fun addScreenTime(date: LocalDate, seconds: Int)

    /**
     * Накопленное за день время **сверх исчерпанного дневного бюджета** (в секундах). Копится,
     * когда ребёнок смахнул оверлей мягкой блокировки и продолжил пользоваться телефоном.
     */
    fun overrunSeconds(date: LocalDate): Flow<Int>

    /** Прибавить секунды к перерасходу дневного бюджета за указанный день. */
    suspend fun addOverrunTime(date: LocalDate, seconds: Int)

    /** Выставить общий экранный расход за день в АБСОЛЮТ (для «Заблокировать на сегодня»). */
    suspend fun setScreenTime(date: LocalDate, seconds: Int)

    /**
     * Время приложения за день в счёт его ЛИЧНОГО лимита (в секундах, веха 3). Личный лимит не
     * превышает: время сверх него — в [appOverrunByPackage].
     */
    fun appScreenTimeSeconds(date: LocalDate, packageName: String): Flow<Int>

    /** Расход личных лимитов приложений за день: пакет → секунды (остаток лимита считается по нему). */
    fun appScreenTimeByPackage(date: LocalDate): Flow<Map<String, Int>>

    /** Время сверх исчерпанных личных лимитов за день: пакет → секунды. */
    fun appOverrunByPackage(date: LocalDate): Flow<Map<String, Int>>

    /**
     * Фактическое время в приложениях за день: пакет → секунды, расход личного лимита плюс
     * перерасход. Это и есть «сколько ребёнок пробыл в приложении» — для статистики нужен именно
     * этот метод, а не [appScreenTimeByPackage].
     */
    fun appTotalScreenTimeByPackage(date: LocalDate): Flow<Map<String, Int>>

    /** Прибавить приложению секунды в счёт его личного лимита за указанный день. */
    suspend fun addAppScreenTime(date: LocalDate, packageName: String, seconds: Int)

    /** Прибавить приложению секунды сверх его исчерпанного личного лимита за указанный день. */
    suspend fun addAppOverrunTime(date: LocalDate, packageName: String, seconds: Int)

    /** Обнулить общий экранный расход за день (сброс сегодняшнего лимита). */
    suspend fun resetScreenTime(date: LocalDate)

    /** Обнулить пер-app расход всех приложений за день (сброс сегодняшнего лимита). */
    suspend fun resetAppScreenTime(date: LocalDate)
}
