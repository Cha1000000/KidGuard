package ru.homelab.kidguard.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
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

    @Query("SELECT * FROM whitelisted_app")
    fun whitelist(): Flow<List<WhitelistedAppEntity>>

    @Upsert
    suspend fun addToWhitelist(entity: WhitelistedAppEntity)

    @Query("DELETE FROM whitelisted_app WHERE packageName = :packageName")
    suspend fun removeFromWhitelist(packageName: String)
}
