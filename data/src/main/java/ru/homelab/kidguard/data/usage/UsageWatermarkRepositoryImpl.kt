package ru.homelab.kidguard.data.usage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import ru.homelab.kidguard.core.domain.repository.UsageWatermarkRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.usageWatermarkDataStore by preferencesDataStore(name = "kidguard_usage_watermark")

/**
 * Ватерлиния в DataStore. Отдельное хранилище, а не общие настройки: пишется каждые несколько
 * секунд живым учётом, и мешать эту запись с редко меняющимися настройками роли ни к чему.
 */
@Singleton
class UsageWatermarkRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : UsageWatermarkRepository {

    private object Keys {
        val ACCOUNTED_UNTIL = longPreferencesKey("accounted_until_epoch_millis")
    }

    override suspend fun accountedUntil(): Instant? =
        context.usageWatermarkDataStore.data.first()[Keys.ACCOUNTED_UNTIL]
            ?.let(Instant::ofEpochMilli)

    override suspend fun setAccountedUntil(moment: Instant) {
        context.usageWatermarkDataStore.edit { prefs ->
            prefs[Keys.ACCOUNTED_UNTIL] = moment.toEpochMilli()
        }
    }
}
