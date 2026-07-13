package ru.homelab.kidguard.data.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import ru.homelab.kidguard.core.domain.model.AuthUser
import ru.homelab.kidguard.core.domain.model.PairedChild
import ru.homelab.kidguard.core.domain.repository.AuthRepository
import ru.homelab.kidguard.data.network.AuthApi
import ru.homelab.kidguard.data.network.ChildrenApi
import ru.homelab.kidguard.data.network.DevicePairRequest
import ru.homelab.kidguard.data.network.GoogleAuthRequest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val childrenApi: ChildrenApi,
    private val authLocalStore: AuthLocalStore
) : AuthRepository {

    override val hasValidSession: Flow<Boolean> = authLocalStore.hasValidParentSession

    override val hasPairedDevice: Flow<Boolean> = authLocalStore.hasPairedDevice

    override val childProfile: Flow<PairedChild?> = authLocalStore.childProfile

    override suspend fun signInWithGoogleIdToken(googleIdToken: String): Result<AuthUser> = try {
        val response = authApi.signInWithGoogle(GoogleAuthRequest(googleIdToken))
        authLocalStore.saveParentSession(
            token = response.token,
            expiresAtMillis = decodeJwtExpiryMillis(response.token),
            userId = response.user.id,
            email = response.user.email,
            displayName = response.user.displayName
        )
        Result.success(AuthUser(response.user.id, response.user.email, response.user.displayName))
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun pairDeviceWithCode(code: String): Result<PairedChild> = try {
        val response = childrenApi.pairDevice(DevicePairRequest(code))
        authLocalStore.saveDeviceSession(
            deviceToken = response.token,
            childId = response.child.id,
            childName = response.child.name,
            childAvatar = response.child.avatar
        )
        Result.success(PairedChild(response.child.id, response.child.name, response.child.avatar))
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun setChildLocalAvatar(index: Int) = authLocalStore.setLocalAvatar(index)

    override suspend fun clearChildLocalAvatar() = authLocalStore.clearLocalAvatar()

    /**
     * Читает поле `exp` (Unix-секунды) из тела JWT БЕЗ проверки подписи — нужно только для
     * локальной оценки «сессия ещё жива?» (см. [AuthLocalStore.hasValidParentSession]). Подпись и
     * реальный срок действия проверяет сервер на каждом запросе — этот декодер ему не замена.
     * Для device-токена не используется: он бессрочный (нет `exp`).
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
