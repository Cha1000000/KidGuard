package ru.homelab.kidguard.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.homelab.kidguard.data.db.dao.PolicyDao
import ru.homelab.kidguard.data.db.dao.UsageDao
import ru.homelab.kidguard.data.db.entity.AppLimitEntity
import ru.homelab.kidguard.data.db.entity.DayLimitEntity
import ru.homelab.kidguard.data.db.entity.ScreenTimeEntity
import ru.homelab.kidguard.data.db.entity.WhitelistedAppEntity

@Database(
    entities = [
        DayLimitEntity::class,
        WhitelistedAppEntity::class,
        ScreenTimeEntity::class,
        AppLimitEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class KidGuardDatabase : RoomDatabase() {
    abstract fun policyDao(): PolicyDao
    abstract fun usageDao(): UsageDao
}
