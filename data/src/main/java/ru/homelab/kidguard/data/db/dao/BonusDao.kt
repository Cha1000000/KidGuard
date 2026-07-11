package ru.homelab.kidguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /** Все бонусы — для включения в policy-документ при синхронизации (веха 4.6). */
    @Query("SELECT * FROM bonus_grants")
    fun observeAll(): Flow<List<BonusGrantEntity>>

    @Query("DELETE FROM bonus_grants")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BonusGrantEntity>)

    /** Целиком заменить бонусы содержимым серверного документа (pull, веха 4.6). */
    @Transaction
    suspend fun replaceAll(items: List<BonusGrantEntity>) {
        deleteAll()
        insertAll(items)
    }
}
