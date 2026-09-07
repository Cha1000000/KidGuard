package ru.homelab.kidguard.data.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

@Serializable
data class GoogleAuthRequest(val idToken: String)

@Serializable
data class AuthResponseDto(val token: String, val user: UserDto)

/**
 * [alertEmail] — адрес для писем-тревог, если родитель попросил слать не на адрес аккаунта
 * (экран «Оповещения»). `null` — значащее состояние («слать на адрес аккаунта»), а не «сервер
 * ничего не знает», поэтому дефолт здесь не про совместимость, а про сам смысл поля.
 */
@Serializable
data class UserDto(
    val id: Int,
    val email: String,
    val displayName: String? = null,
    val alertEmail: String? = null
)

@Serializable
data class DeleteAccountResponse(val ok: Boolean = true)

@Serializable
data class MeResponse(val user: UserDto)

/**
 * В отличие от [ru.homelab.kidguard.data.network.ChildNotificationsRequest], `alertEmail` — БЕЗ
 * дефолтного значения. Это осознанное решение: у setAlertEmail(null) есть настоящий смысл —
 * «сбросить на адрес аккаунта», и это значение обязано уйти на сервер как реальный JSON `null`, а
 * не потеряться. Json в NetworkModule собран с дефолтными настройками kotlinx, где `explicitNulls`
 * не выключен, — но это одно спасает только если у поля нет объявленного значения по умолчанию:
 * тогда `encodeDefaults = false` его не касается и оно кодируется всегда, независимо от значения.
 */
@Serializable
data class UpdateAlertEmailRequest(val alertEmail: String?)

/**
 * [verificationSent] — ушло ли на новый адрес проверочное письмо. Сервер сохраняет адрес даже
 * когда отправка не удалась (см. `alertEmailService`), поэтому это отдельный флаг, а не часть [user].
 */
@Serializable
data class UpdateMeResponse(val user: UserDto, val verificationSent: Boolean)

/** Соответствует контракту `POST /auth/google` из docs/plans/milestone-04-accounts-backend-sync.md. */
interface AuthApi {

    @POST("auth/google")
    suspend fun signInWithGoogle(@Body request: GoogleAuthRequest): AuthResponseDto

    /** Удаляет учётную запись родителя на сервере (каскадно — вместе с его детьми и данными). */
    @DELETE("me")
    suspend fun deleteAccount(): DeleteAccountResponse

    /** Профиль родителя, включая [UserDto.alertEmail] — источник данных для экрана «Оповещения». */
    @GET("me")
    suspend fun getMe(): MeResponse

    /** Меняет адрес для писем-тревог; `alertEmail = null` в теле — сброс на адрес аккаунта. */
    @PATCH("me")
    suspend fun updateMe(@Body request: UpdateAlertEmailRequest): UpdateMeResponse
}
