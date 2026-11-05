package ru.homelab.kidguard.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ru.homelab.kidguard.data.db.entity.BreakSessionEntity
import ru.homelab.kidguard.data.db.entity.StickinessStateEntity

/**
 * Runtime-состояние перерывов (план «исправление перерывов», этап 2). Таблицы не участвуют в
 * `replaceAllPolicy`: применение удалённой политики не должно затирать идущий перерыв.
 */
@Dao
interface BreakRuntimeDao {

    @Query("SELECT * FROM break_session WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun activeSession(): BreakSessionEntity?

    @Upsert
    suspend fun upsertSession(entity: BreakSessionEntity)

    /**
     * Завершает сессию, только если она ещё ACTIVE; возвращает число изменённых строк.
     * Условие в WHERE и есть идемпотентность: повторное завершение по тому же `sessionId`
     * заденет 0 строк, и вызывающий не станет сбрасывать usage второй раз.
     */
    @Query("UPDATE break_session SET status = 'COMPLETED' WHERE sessionId = :sessionId AND status = 'ACTIVE'")
    suspend fun completeSessionIfActive(sessionId: String): Int

    @Query("UPDATE break_session SET status = 'CANCELLED' WHERE sessionId = :sessionId AND status = 'ACTIVE'")
    suspend fun cancelSessionIfActive(sessionId: String): Int

    @Query("SELECT * FROM stickiness_state WHERE id = 0")
    suspend fun usageSnapshot(): StickinessStateEntity?

    @Upsert
    suspend fun upsertUsageSnapshot(entity: StickinessStateEntity)

    @Query("DELETE FROM stickiness_state")
    suspend fun deleteUsageSnapshot()

    /**
     * Атомарное «перерыв доигран»: завершение сессии и сброс usage-счётчика в одной транзакции.
     * Сброс выполняется, только если сессия реально перешла в COMPLETED этим вызовом — повторный
     * вызов (гонка тика и события) не тронет уже начавшийся следующий цикл использования.
     */
    @Transaction
    suspend fun completeBreakAndResetUsage(sessionId: String) {
        if (completeSessionIfActive(sessionId) > 0) {
            deleteUsageSnapshot()
        }
    }
}
