package ru.homelab.kidguard.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.homelab.kidguard.core.domain.model.Role
import ru.homelab.kidguard.core.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "kidguard_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object Keys {
        val ROLE = stringPreferencesKey("role")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
    }

    override val role: Flow<Role?> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.ROLE]?.let { stored -> runCatching { Role.valueOf(stored) }.getOrNull() }
    }

    override val setupCompleted: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.SETUP_COMPLETED] ?: false
    }

    override suspend fun setRole(role: Role) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.ROLE] = role.name }
    }

    override suspend fun setSetupCompleted(completed: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.SETUP_COMPLETED] = completed }
    }
}
