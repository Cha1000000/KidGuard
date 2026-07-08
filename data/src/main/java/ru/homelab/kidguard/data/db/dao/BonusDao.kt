package ru.homelab.kidguard.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.data.db.entity.BonusGrantEntity

@Dao
interface BonusDao {

    /** Бонус конкретной цели за день (packageName = "" — бонус телефона). */
    @Query("SELECT minutes FROM bonus_grants WHERE date = :date AND packageName = :packageName")
    fun minutesFor(date: String, packageName: String): Flow<Int?>

    /** Бонусы приложений за день (маркер телефона "" исключён). */
    @Query("SELECT * FROM bonus_grants WHERE date = :date AND packageName != ''")
    fun appBonusesForDate(date: String): Flow<List<BonusGrantEntity>>

    /** Прибавить минуты к бонусу цели за день (создать запись, если её ещё нет). Суммирование. */
    @Query(
        "INSERT INTO bonus_grants(date, packageName, minutes) VALUES(:date, :packageName, :minutes) " +
            "ON CONFLICT(date, packageName) DO UPDATE SET minutes = minutes + :minutes"
    )
    suspend fun addMinutes(date: String, packageName: String, minutes: Int)

    /** Обнулить бонус цели за день (досрочная отмена). */
    @Query("DELETE FROM bonus_grants WHERE date = :date AND packageName = :packageName")
    suspend fun clear(date: String, packageName: String)
}
