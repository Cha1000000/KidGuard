package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.core.domain.model.Role

/**
 * Локальные настройки приложения (роль устройства, факт завершения первичной настройки).
 * Реализация хранит их в DataStore. Роль задаётся один раз и далее не меняется.
 */
interface SettingsRepository {

    /** Выбранная роль устройства, либо null, если первичная настройка ещё не пройдена. */
    val role: Flow<Role?>

    /** Завершён ли мастер первичной настройки. */
    val setupCompleted: Flow<Boolean>

    /** Зафиксировать роль устройства (единоразово, при первичной настройке). */
    suspend fun setRole(role: Role)

    /** Отметить первичную настройку завершённой. */
    suspend fun setSetupCompleted(completed: Boolean)
}
