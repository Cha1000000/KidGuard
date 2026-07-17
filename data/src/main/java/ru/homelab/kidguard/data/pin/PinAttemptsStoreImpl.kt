package ru.homelab.kidguard.data.pin

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.homelab.kidguard.core.domain.repository.PinAttemptsStore
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pinAttemptsDataStore by preferencesDataStore(name = "kidguard_pin_attempts")

/**
 * Счётчик неудачных вводов PIN на диске: должен переживать перезапуск процесса и перезагрузку,
 * иначе перебор сбрасывался бы убийством приложения.
 */
@Singleton
class PinAttemptsStoreImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PinAttemptsStore {

    private object Keys {
        val TOTAL_FAILURES = intPreferencesKey("total_failures")
    }

    override suspend fun totalFailures(): Int =
        context.pinAttemptsDataStore.data.map { prefs -> prefs[Keys.TOTAL_FAILURES] ?: 0 }.first()

    override suspend fun increment(): Int {
        var updated = 0
        context.pinAttemptsDataStore.edit { prefs ->
            updated = (prefs[Keys.TOTAL_FAILURES] ?: 0) + 1
            prefs[Keys.TOTAL_FAILURES] = updated
        }
        return updated
    }

    override suspend fun reset() {
        context.pinAttemptsDataStore.edit { prefs -> prefs[Keys.TOTAL_FAILURES] = 0 }
    }
}
