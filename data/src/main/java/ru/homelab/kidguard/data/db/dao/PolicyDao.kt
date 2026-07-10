package ru.homelab.kidguard.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.data.db.entity.AppLimitEntity
import ru.homelab.kidguard.data.db.entity.DayLimitEntity
import ru.homelab.kidguard.data.db.entity.WhitelistedAppEntity

@Dao
interface PolicyDao {

    @Query("SELECT * FROM day_limit")
    fun dayLimits(): Flow<List<DayLimitEntity>>

    @Upsert
    suspend fun upsertDayLimit(entity: DayLimitEntity)

    @Query("DELETE FROM day_limit WHERE dayOfWeek = :dayOfWeek")
    suspend fun deleteDayLimit(dayOfWeek: Int)

    @Query("SELECT * FROM app_limits")
    fun appLimits(): Flow<List<AppLimitEntity>>

    @Upsert
    suspend fun upsertAppLimit(entity: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun deleteAppLimit(packageName: String)

    @Query("SELECT * FROM whitelisted_app")
    fun whitelist(): Flow<List<WhitelistedAppEntity>>

    @Upsert
    suspend fun addToWhitelist(entity: WhitelistedAppEntity)

    @Query("DELETE FROM whitelisted_app WHERE packageName = :packageName")
    suspend fun removeFromWhitelist(packageName: String)

    @Query("DELETE FROM day_limit")
    suspend fun deleteAllDayLimits()

    @Query("DELETE FROM app_limits")
    suspend fun deleteAllAppLimits()

    @Query("DELETE FROM whitelisted_app")
    suspend fun deleteAllWhitelist()

    /**
     * Транзакционно заменяет ВСЮ политику разом (применение серверного документа — веха 4.3):
     * либо применяется целиком, либо не применяется вовсе — исполнители (блокировка/учёт)
     * не увидят промежуточного полупустого состояния.
     */
    @Transaction
    suspend fun replaceAllPolicy(
        dayLimits: List<DayLimitEntity>,
        appLimits: List<AppLimitEntity>,
        whitelist: List<WhitelistedAppEntity>
    ) {
        deleteAllDayLimits()
        deleteAllAppLimits()
        deleteAllWhitelist()
        dayLimits.forEach { upsertDayLimit(it) }
        appLimits.forEach { upsertAppLimit(it) }
        whitelist.forEach { addToWhitelist(it) }
    }
}
