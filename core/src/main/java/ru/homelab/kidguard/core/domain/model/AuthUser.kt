package ru.homelab.kidguard.core.domain.model

/** Профиль вошедшего пользователя (без служебных полей вроде google_sub — тех данных клиенту не нужно). */
data class AuthUser(
    val id: Int,
    val email: String,
    val displayName: String?
)
