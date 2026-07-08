package ru.homelab.kidguard.data.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class GoogleAuthRequest(val idToken: String)

@Serializable
data class AuthResponseDto(val token: String, val user: UserDto)

@Serializable
data class UserDto(val id: Int, val email: String, val displayName: String? = null)

/** Соответствует контракту `POST /auth/google` из docs/plans/milestone-04-accounts-backend-sync.md. */
interface AuthApi {

    @POST("auth/google")
    suspend fun signInWithGoogle(@Body request: GoogleAuthRequest): AuthResponseDto
}
