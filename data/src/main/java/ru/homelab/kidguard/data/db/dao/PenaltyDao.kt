package ru.homelab.kidguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.data.db.entity.PenaltyGrantEntity

/** Зеркало [BonusDao] для штрафов (Room v13). */
@Dao
interface PenaltyDao {

    /** Штраф конкретной цели за день (packageName = "" — штраф телефона). */
    @Query("SELECT * FROM penalty_grants WHERE date = :date AND packageName = :packageName")
    fun penaltyFor(date: String, packageName: String): Flow<PenaltyGrantEntity?>

    /**
     * Добавить минуты к штрафу цели за день (создать запись, если её ещё нет).
     *
     * Минуты суммируются, а комментарий заменяется новым: он поясняет сегодняшнее наказание
     * целиком, а не отдельную выдачу.
     */
    @Query(
        "INSERT INTO penalty_grants(date, packageName, minutes, comment) " +
            "VALUES(:date, :packageName, :minutes, :comment) " +
            "ON CONFLICT(date, packageName) DO UPDATE SET " +
            "minutes = minutes + :minutes, comment = :comment"
    )
    suspend fun addMinutes(date: String, packageName: String, minutes: Int, comment: String)

    /** Переписать комментарий, не трогая минуты. Штрафа ещё нет — запрос ничего не делает. */
    @Query("UPDATE penalty_grants SET comment = :comment WHERE date = :date AND packageName = :packageName")
    suspend fun updateComment(date: String, packageName: String, comment: String)

    /** Отменить штраф цели за день. */
    @Query("DELETE FROM penalty_grants WHERE date = :date AND packageName = :packageName")
    suspend fun clear(date: String, packageName: String)

    /** Все штрафы — для включения в policy-документ при синхронизации. */
    @Query("SELECT * FROM penalty_grants")
    fun observeAll(): Flow<List<PenaltyGrantEntity>>

    @Query("DELETE FROM penalty_grants")
    suspend fun deleteAll()

    /**
     * Удалить штрафы старше указанной даты. Даты хранятся строками ISO (ГГГГ-ММ-ДД), поэтому
     * лексикографическое сравнение совпадает с хронологическим.
     */
    @Query("DELETE FROM penalty_grants WHERE date < :date")
    suspend fun deleteOlderThan(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PenaltyGrantEntity>)

    /** Целиком заменить штрафы содержимым серверного документа (pull). */
    @Transaction
    suspend fun replaceAll(items: List<PenaltyGrantEntity>) {
        deleteAll()
        insertAll(items)
    }
}
