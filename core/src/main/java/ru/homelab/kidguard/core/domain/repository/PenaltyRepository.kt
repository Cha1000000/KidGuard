package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.homelab.kidguard.core.domain.model.PenaltyGrant
import java.time.LocalDate

/**
 * Штрафы («снять время») на день — обратная операция к [BonusRepository]. Штрафы за один день
 * суммируются и действуют только до конца текущего дня. Хранятся в Room и синхронизируются в
 * составе policy-документа рядом с бонусами.
 *
 * Глубину истории отдельной константой не заводим — она общая с бонусами
 * ([BonusRepository.HISTORY_DAYS]): и то, и другое возится в одном документе и чистится одним
 * и тем же отсечением по дате.
 */
interface PenaltyRepository {

    /** Штраф телефона за указанный день; `null` — штрафа не было. */
    fun phonePenalty(date: LocalDate): Flow<PenaltyGrant?>

    /**
     * Добавить штраф (минут) цели за день; `packageName = null` — штраф телефона. Минуты
     * суммируются с уже назначенными, а [comment] **перезаписывает** прежний: он поясняет
     * сегодняшнее наказание целиком, а склеивать фразы родителя автоматически — каша.
     */
    suspend fun addPenalty(date: LocalDate, packageName: String?, minutes: Int, comment: String)

    /** Переписать комментарий уже назначенного штрафа, не трогая минуты. */
    suspend fun setComment(date: LocalDate, packageName: String?, comment: String)

    /** Отменить штраф цели за день; `null` — штраф телефона. */
    suspend fun clearPenalty(date: LocalDate, packageName: String?)

    /** Все штрафы — для включения в policy-документ при push. */
    fun observeAll(): Flow<List<PenaltyGrant>>

    /** Целиком заменить штрафы содержимым серверного документа при pull. */
    suspend fun replaceAll(grants: List<PenaltyGrant>)

    /** Удалить штрафы за дни раньше [date]. */
    suspend fun deleteOlderThan(date: LocalDate)
}
