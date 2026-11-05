package ru.homelab.kidguard.core.domain.repository

import ru.homelab.kidguard.core.domain.model.BreakSession
import ru.homelab.kidguard.core.domain.model.ContinuousUsageSession

/**
 * Локальное runtime-хранилище перерывов (план «исправление перерывов», этап 2). Реализация —
 * Room в `:data`; сознательно НЕ входит в `PolicySnapshot`/policy DTO/серверную синхронизацию:
 * сессия перерыва принадлежит устройству ребёнка и никуда не реплицируется.
 *
 * Порядок, который обязан соблюдать вызывающий: сначала [saveSession], только потом эмит
 * `BreakState.Active` и показ замка — иначе смерть процесса между показом и записью потеряла бы
 * активный перерыв.
 */
interface BreakRuntimeRepository {

    /** Текущая активная сессия перерыва (или null). */
    suspend fun loadActiveSession(): BreakSession?

    /** Создать/обновить сессию перерыва. */
    suspend fun saveSession(session: BreakSession)

    /**
     * Отменить сессию (смена правил, выключение перерывов, неприменимый день): замок снимается,
     * usage-накопление не трогается — отмена это не «отдых».
     */
    suspend fun cancelSession(sessionId: String)

    /**
     * Атомарно: завершить сессию И сбросить usage-счётчик одной транзакцией — между ними не может
     * вклиниться сбой, оставляющий активную сессию с обнулённым счётчиком.
     *
     * Идемпотентно по [sessionId]: повторный вызов для уже завершённой сессии — no-op, поэтому
     * запоздалое завершение старой сессии не сбрасывает уже начавшийся следующий usage-цикл.
     */
    suspend fun completeBreakAndResetUsage(sessionId: String)

    /** Сохранённый снимок накопления непрерывного использования (или null — цикла нет). */
    suspend fun loadUsageSnapshot(): ContinuousUsageSession?

    /** Сохранить снимок накопления (периодически и на системных событиях). */
    suspend fun saveUsageSnapshot(snapshot: ContinuousUsageSession)

    /**
     * Удалить снимок: цикл очищен (перерывы выключены/неприменимы) или завершён перерыв.
     * После рестарта накопление начнётся с чистого цикла.
     */
    suspend fun clearUsageSnapshot()
}
