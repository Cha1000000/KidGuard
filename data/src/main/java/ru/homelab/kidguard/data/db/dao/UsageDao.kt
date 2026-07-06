package ru.homelab.kidguard.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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
}
