package ru.homelab.kidguard.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "kidguard_auth")

/**
 * Единственный владелец локального хранилища сессии (`kidguard_auth`). Держит обе возможные
 * сессии устройства:
 * - **родительскую** (JWT + профиль, от Google-входа — веха 4.1),
 * - **детскую** (device-токен + профиль ребёнка, от pairing — веха 4.2).
 *
 * Устройство работает в одной роли, поэтому одновременно заполнена только одна из сессий.
 * Вынесен из [AuthRepositoryImpl] отдельным классом, чтобы к тому же DataStore мог обращаться и
 * OkHttp-интерсептор ([AuthTokenInterceptor]) — иначе два `preferencesDataStore` на одно имя
 * конфликтуют.
 */
@Singleton
class AuthLocalStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private object Keys {
        // Родительская сессия
        val TOKEN = stringPreferencesKey("token")
        val EXPIRES_AT_MILLIS = longPreferencesKey("expires_at_millis")
        val USER_ID = intPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        // Детская сессия (pairing)
        val DEVICE_TOKEN = stringPreferencesKey("device_token")
        val CHILD_ID = intPreferencesKey("child_id")
        val CHILD_NAME = stringPreferencesKey("child_name")
    }

    val hasValidParentSession: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        val hasToken = !prefs[Keys.TOKEN].isNullOrBlank()
        val expiresAt = prefs[Keys.EXPIRES_AT_MILLIS] ?: 0L
        hasToken && expiresAt > System.currentTimeMillis()
    }

    val hasPairedDevice: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        !prefs[Keys.DEVICE_TOKEN].isNullOrBlank()
    }

    suspend fun saveParentSession(token: String, expiresAtMillis: Long, userId: Int, email: String, displayName: String?) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = token
            prefs[Keys.EXPIRES_AT_MILLIS] = expiresAtMillis
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USER_EMAIL] = email
            if (displayName != null) prefs[Keys.USER_DISPLAY_NAME] = displayName
        }
    }

    suspend fun saveDeviceSession(deviceToken: String, childId: Int, childName: String) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.DEVICE_TOKEN] = deviceToken
            prefs[Keys.CHILD_ID] = childId
            prefs[Keys.CHILD_NAME] = childName
        }
    }

    /**
     * Токен для заголовка `Authorization` исходящих запросов: родительский JWT, если он есть,
     * иначе детский device-токен. Читается интерсептором на фоновом потоке OkHttp.
     */
    suspend fun currentBearerToken(): String? {
        val prefs = context.authDataStore.data.first()
        return prefs[Keys.TOKEN]?.takeIf { it.isNotBlank() }
            ?: prefs[Keys.DEVICE_TOKEN]?.takeIf { it.isNotBlank() }
    }

    /** id привязанного ребёнка (детская сессия), либо null, если устройство не привязано. */
    suspend fun pairedChildId(): Int? = context.authDataStore.data.first()[Keys.CHILD_ID]
}
