package ru.homelab.kidguard.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import ru.homelab.kidguard.core.domain.model.AuthUser
import ru.homelab.kidguard.core.domain.repository.AuthRepository
import ru.homelab.kidguard.data.network.AuthApi
import ru.homelab.kidguard.data.network.GoogleAuthRequest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "kidguard_auth")

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authApi: AuthApi
) : AuthRepository {

    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val EXPIRES_AT_MILLIS = longPreferencesKey("expires_at_millis")
        val USER_ID = intPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
    }

    override val hasValidSession: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        val hasToken = !prefs[Keys.TOKEN].isNullOrBlank()
        val expiresAt = prefs[Keys.EXPIRES_AT_MILLIS] ?: 0L
        hasToken && expiresAt > System.currentTimeMillis()
    }

    override suspend fun signInWithGoogleIdToken(googleIdToken: String): Result<AuthUser> = try {
        val response = authApi.signInWithGoogle(GoogleAuthRequest(googleIdToken))
        val expiresAtMillis = decodeJwtExpiryMillis(response.token)

        context.authDataStore.edit { prefs ->
            prefs[Keys.TOKEN] = response.token
            prefs[Keys.EXPIRES_AT_MILLIS] = expiresAtMillis
            prefs[Keys.USER_ID] = response.user.id
            prefs[Keys.USER_EMAIL] = response.user.email
            if (response.user.displayName != null) {
                prefs[Keys.USER_DISPLAY_NAME] = response.user.displayName
            }
        }

        Result.success(AuthUser(response.user.id, response.user.email, response.user.displayName))
    } catch (error: Exception) {
        Result.failure(error)
    }

    /**
     * Читает поле `exp` (Unix-секунды) из тела JWT БЕЗ проверки подписи — нужно только для
     * локальной оценки «сессия ещё жива?» (см. [hasValidSession]). Подпись и реальный срок
     * действия проверяет сервер на каждом аутентифицированном запросе — этот декодер ему не замена.
     */
    private fun decodeJwtExpiryMillis(jwt: String): Long {
        val payloadSegment = jwt.split(".").getOrNull(1) ?: return 0L
        val padded = payloadSegment.padEnd((payloadSegment.length + 3) / 4 * 4, '=')
        val decodedJson = String(Base64.getUrlDecoder().decode(padded))
        val expSeconds = Json.parseToJsonElement(decodedJson).jsonObject["exp"]
            ?.jsonPrimitive?.longOrNull ?: return 0L
        return expSeconds * 1000L
    }
}
