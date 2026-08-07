package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.core.domain.model.Role

/**
 * Локальные настройки приложения (роль устройства, факт завершения первичной настройки).
 * Реализация хранит их в DataStore. Роль обычно задаётся один раз при первичной настройке,
 * но сбрасывается при выходе из аккаунта ([resetSetup]) — тогда устройство снова проходит выбор.
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

    /**
     * Видели ли на этом устройстве выданное accessibility-разрешение хотя бы раз, то есть
     * настроен ли контроль вообще. Отличает «ребёнок выключил разрешение» от «мастер разрешений
     * ещё идёт»: `setupCompleted` для этого не годится — он ставится уже при выборе роли, задолго
     * до выдачи разрешений.
     */
    val controlEverConfigured: Flow<Boolean>

    /** Запомнить, что контроль был настроен (accessibility увидели включённым). */
    suspend fun markControlConfigured()

    /** Сбросить первичную настройку: роль и признак завершённости. Приложение вернётся к выбору роли. */
    suspend fun resetSetup()
}
