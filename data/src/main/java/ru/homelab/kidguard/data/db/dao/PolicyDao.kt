package ru.homelab.kidguard.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.data.db.entity.AppLimitEntity
import ru.homelab.kidguard.data.db.entity.BlockedAppEntity
import ru.homelab.kidguard.data.db.entity.BlockedSiteEntity
import ru.homelab.kidguard.data.db.entity.DayLimitEntity
import ru.homelab.kidguard.data.db.entity.PinEntity
import ru.homelab.kidguard.data.db.entity.PolicyFlagsEntity
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

    @Query("SELECT * FROM blocked_app")
    fun blockedApps(): Flow<List<BlockedAppEntity>>

    @Upsert
    suspend fun addToBlocked(entity: BlockedAppEntity)

    @Query("DELETE FROM blocked_app WHERE packageName = :packageName")
    suspend fun removeFromBlocked(packageName: String)

    @Query("SELECT * FROM blocked_site")
    fun blockedSites(): Flow<List<BlockedSiteEntity>>

    @Upsert
    suspend fun upsertBlockedSite(entity: BlockedSiteEntity)

    @Query("DELETE FROM blocked_site WHERE domain = :domain")
    suspend fun removeBlockedSite(domain: String)

    @Query("UPDATE blocked_site SET enabled = :enabled WHERE domain = :domain")
    suspend fun setSiteEnabled(domain: String, enabled: Boolean)

    /** Скалярные флаги политики (веха 4.1.2) — single-row таблица, `id = 0`; отсутствие строки = дефолты. */
    @Query("SELECT * FROM policy_flags WHERE id = 0")
    fun policyFlags(): Flow<PolicyFlagsEntity?>

    @Upsert
    suspend fun upsertPolicyFlags(entity: PolicyFlagsEntity)

    @Query("DELETE FROM day_limit")
    suspend fun deleteAllDayLimits()

    @Query("DELETE FROM app_limits")
    suspend fun deleteAllAppLimits()

    @Query("DELETE FROM whitelisted_app")
    suspend fun deleteAllWhitelist()

    @Query("DELETE FROM blocked_app")
    suspend fun deleteAllBlocked()

    @Query("DELETE FROM blocked_site")
    suspend fun deleteAllBlockedSites()

    /** Родительский PIN (веха 6.1) — single-row таблица, `id = 0`; null-строка означает «PIN не задан». */
    @Query("SELECT * FROM pin_protection WHERE id = 0")
    fun pin(): Flow<PinEntity?>

    @Upsert
    suspend fun upsertPin(entity: PinEntity)

    @Query("DELETE FROM pin_protection")
    suspend fun deletePin()

    /**
     * Транзакционно заменяет ВСЮ политику разом (применение серверного документа — веха 4.3):
     * либо применяется целиком, либо не применяется вовсе — исполнители (блокировка/учёт)
     * не увидят промежуточного полупустого состояния.
     */
    @Transaction
    suspend fun replaceAllPolicy(
        dayLimits: List<DayLimitEntity>,
        appLimits: List<AppLimitEntity>,
        whitelist: List<WhitelistedAppEntity>,
        blockedApps: List<BlockedAppEntity>,
        blockedSites: List<BlockedSiteEntity>,
        blockGoogleSearch: Boolean,
        pin: PinEntity?
    ) {
        deleteAllDayLimits()
        deleteAllAppLimits()
        deleteAllWhitelist()
        deleteAllBlocked()
        deleteAllBlockedSites()
        dayLimits.forEach { upsertDayLimit(it) }
        appLimits.forEach { upsertAppLimit(it) }
        whitelist.forEach { addToWhitelist(it) }
        blockedApps.forEach { addToBlocked(it) }
        blockedSites.forEach { upsertBlockedSite(it) }
        upsertPolicyFlags(PolicyFlagsEntity(blockGoogleSearch = blockGoogleSearch))
        if (pin != null) upsertPin(pin) else deletePin()
    }
}
