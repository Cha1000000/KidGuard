package ru.homelab.kidguard.data.date

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dateDataStore by preferencesDataStore(name = "kidguard_date")

/**
 * Реализация с анти-отмоткой: хранит последнюю виденную дату и не даёт «дню учёта» откатиться
 * назад при переводе системного времени назад.
 */
@Singleton
class CurrentDateProviderImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : CurrentDateProvider {

    private val lastSeenKey = stringPreferencesKey("last_seen_date")

    override suspend fun today(): LocalDate {
        val systemToday = LocalDate.now()
        val lastSeen = context.dateDataStore.data
            .map { prefs -> prefs[lastSeenKey]?.let(LocalDate::parse) }
            .first()

        // Не откатываемся назад: если системная дата раньше виденной — берём виденную.
        val effective = if (lastSeen != null && systemToday.isBefore(lastSeen)) lastSeen else systemToday

        if (effective != lastSeen) {
            context.dateDataStore.edit { prefs -> prefs[lastSeenKey] = effective.toString() }
        }
        return effective
    }
}
