package ru.homelab.kidguard.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.data.db.entity.AppScreenTimeEntity

@Dao
interface UsageDao {

    @Query("SELECT seconds FROM screen_time WHERE date = :date")
    fun secondsForDate(date: String): Flow<Int?>

    /** Прибавить секунды к дню (создать запись, если её ещё нет). */
    @Query(
        "INSERT INTO screen_time(date, seconds) VALUES(:date, :seconds) " +
            "ON CONFLICT(date) DO UPDATE SET seconds = seconds + :seconds"
    )
    suspend fun addSeconds(date: String, seconds: Int)

    /** Выставить общий расход за день в АБСОЛЮТ («Заблокировать на сегодня»). */
    @Query(
        "INSERT INTO screen_time(date, seconds) VALUES(:date, :seconds) " +
            "ON CONFLICT(date) DO UPDATE SET seconds = :seconds"
    )
    suspend fun setSeconds(date: String, seconds: Int)

    @Query("SELECT seconds FROM app_screen_time WHERE date = :date AND packageName = :packageName")
    fun appSecondsForDate(date: String, packageName: String): Flow<Int?>

    /** Расход всех приложений за день (для списка настройки лимитов). */
    @Query("SELECT * FROM app_screen_time WHERE date = :date")
    fun appSecondsForDate(date: String): Flow<List<AppScreenTimeEntity>>

    /** Прибавить секунды приложению за день (создать запись, если её ещё нет). */
    @Query(
        "INSERT INTO app_screen_time(date, packageName, seconds) VALUES(:date, :packageName, :seconds) " +
            "ON CONFLICT(date, packageName) DO UPDATE SET seconds = seconds + :seconds"
    )
    suspend fun addAppSeconds(date: String, packageName: String, seconds: Int)

    /** Обнулить общий экранный расход за день (сброс сегодняшнего лимита). */
    @Query("DELETE FROM screen_time WHERE date = :date")
    suspend fun deleteForDate(date: String)

    /** Обнулить пер-app расход всех приложений за день (сброс сегодняшнего лимита). */
    @Query("DELETE FROM app_screen_time WHERE date = :date")
    suspend fun deleteAppSecondsForDate(date: String)
}
