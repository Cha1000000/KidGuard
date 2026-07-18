package ru.homelab.kidguard.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.homelab.kidguard.data.db.dao.BonusDao
import ru.homelab.kidguard.data.db.dao.PolicyDao
import ru.homelab.kidguard.data.db.dao.UsageDao
import ru.homelab.kidguard.data.db.entity.AppLimitEntity
import ru.homelab.kidguard.data.db.entity.AppScreenTimeEntity
import ru.homelab.kidguard.data.db.entity.BlockedAppEntity
import ru.homelab.kidguard.data.db.entity.BlockedSiteEntity
import ru.homelab.kidguard.data.db.entity.BonusGrantEntity
import ru.homelab.kidguard.data.db.entity.DayLimitEntity
import ru.homelab.kidguard.data.db.entity.PinEntity
import ru.homelab.kidguard.data.db.entity.PolicyFlagsEntity
import ru.homelab.kidguard.data.db.entity.ScreenTimeEntity
import ru.homelab.kidguard.data.db.entity.WhitelistedAppEntity

@Database(
    entities = [
        DayLimitEntity::class,
        WhitelistedAppEntity::class,
        ScreenTimeEntity::class,
        AppLimitEntity::class,
        AppScreenTimeEntity::class,
        BonusGrantEntity::class,
        BlockedAppEntity::class,
        PinEntity::class,
        BlockedSiteEntity::class,
        PolicyFlagsEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class KidGuardDatabase : RoomDatabase() {
    abstract fun policyDao(): PolicyDao
    abstract fun usageDao(): UsageDao
    abstract fun bonusDao(): BonusDao
}
