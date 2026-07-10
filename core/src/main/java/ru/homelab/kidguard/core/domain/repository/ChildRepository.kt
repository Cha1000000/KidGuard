package ru.homelab.kidguard.core.domain.repository

import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.domain.model.ChildWithCode
import ru.homelab.kidguard.core.domain.model.UsageEntry

/**
 * Операции родителя над детьми (веха 4.2): создание ребёнка с pairing-кодом, список,
 * перевыпуск кода, приглашение второго родителя. Все запросы идут с родительским JWT
 * (добавляется сетевым интерсептором).
 */
interface ChildRepository {

    /** Создать ребёнка (имя + индекс аватарки), получить его и pairing-код. */
    suspend fun createChild(name: String, avatar: Int): Result<ChildWithCode>

    /** Список детей текущего родителя. */
    suspend fun listChildren(): Result<List<Child>>

    /** Перевыпустить pairing-код ребёнка (прежний перестаёт действовать). */
    suspend fun regeneratePairCode(childId: Int): Result<String>

    /**
     * Пригласить второго равноправного родителя по email. Возвращает `true`, если тот уже
     * зарегистрирован и связь создана сразу; `false` — приглашение отложено до его первого входа.
     */
    suspend fun inviteCoParent(childId: Int, email: String): Result<Boolean>

    /** Серверная статистика ребёнка за последние [days] дней (включая записи-тоталы). */
    suspend fun getChildUsage(childId: Int, days: Int): Result<List<UsageEntry>>
}
