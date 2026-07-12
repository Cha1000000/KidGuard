package ru.homelab.kidguard.core.domain.repository

import ru.homelab.kidguard.core.domain.model.AppInfo
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

    /** Обновить имя и/или аватар ребёнка (редактирование профиля). */
    suspend fun updateChild(childId: Int, name: String, avatar: Int): Result<Child>

    /** Удалить ребёнка со всеми его данными на сервере (правила, статистика, приглашения). */
    suspend fun deleteChild(childId: Int): Result<Unit>

    /**
     * Список приложений, установленных на устройстве ребёнка (веха 4.1) — его публикует само
     * детское устройство. Пустой список — устройство ещё не успело отправить свои приложения.
     */
    suspend fun childApps(childId: Int): Result<List<AppInfo>>
}
