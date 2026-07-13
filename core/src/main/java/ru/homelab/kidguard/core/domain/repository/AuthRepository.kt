package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.core.domain.model.AuthUser
import ru.homelab.kidguard.core.domain.model.PairedChild

/**
 * Сессия устройства (веха 4). Родитель входит через Google (JWT); детское устройство
 * привязывается pairing-кодом (device-токен). Устройство работает в одной роли, поэтому
 * одновременно активна только одна сессия.
 */
interface AuthRepository {

    /** Есть ли непросроченная РОДИТЕЛЬСКАЯ сессия (JWT есть и срок не истёк). */
    val hasValidSession: Flow<Boolean>

    /** Привязано ли ДЕТСКОЕ устройство (есть сохранённый device-токен). */
    val hasPairedDevice: Flow<Boolean>

    /**
     * Профиль привязанного ребёнка (имя, аватар) — для детского приветствия на экране «Сегодня».
     * `null`, если устройство не привязано.
     */
    val childProfile: Flow<PairedChild?>

    /**
     * Обменивает Google ID-token на JWT сервера, сохраняет родительскую сессию локально.
     * `Result.failure` — сеть недоступна, сервер отклонил токен и т.п.
     */
    suspend fun signInWithGoogleIdToken(googleIdToken: String): Result<AuthUser>

    /**
     * Привязывает детское устройство по pairing-коду: обменивает код на device-токен и сохраняет
     * детскую сессию. `Result.failure` — код неверный/использован или сеть недоступна.
     */
    suspend fun pairDeviceWithCode(code: String): Result<PairedChild>
}
