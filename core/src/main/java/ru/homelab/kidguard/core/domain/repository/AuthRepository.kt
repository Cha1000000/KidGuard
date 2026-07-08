package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.core.domain.model.AuthUser

/**
 * Сессия входа через Google: обмен ID-token на собственный JWT сервера, хранение сессии
 * локально (веха 4). Синхронизация правил/статистики поверх этой сессии — следующие шаги вехи 4.
 */
interface AuthRepository {

    /** Есть ли сохранённая непросроченная сессия (JWT есть и его срок ещё не истёк). */
    val hasValidSession: Flow<Boolean>

    /**
     * Обменивает Google ID-token на JWT сервера, сохраняет сессию локально.
     * `Result.failure` — сеть недоступна, сервер отклонил токен (не тот email/audience) и т.п.
     */
    suspend fun signInWithGoogleIdToken(googleIdToken: String): Result<AuthUser>
}
