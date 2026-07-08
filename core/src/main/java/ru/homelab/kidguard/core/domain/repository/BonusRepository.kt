package ru.homelab.kidguard.core.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Разовые бонусы («Дополнительное время») на день — для телефона в целом или для конкретного
 * приложения. Выдачи за один день суммируются; действуют только до конца текущего дня
 * (веха 3Б). Хранится локально (Room); синхронизация с сервером — веха 4.
 */
interface BonusRepository {

    /** Бонус телефона (минут) за указанный день. */
    fun phoneBonusMinutes(date: LocalDate): Flow<Int>

    /** Бонусы приложений за указанный день: пакет → минут. */
    fun appBonusMinutes(date: LocalDate): Flow<Map<String, Int>>

    /** Добавить бонус (минут) цели за день; null — бонус телефона. Суммируется с уже выданным. */
    suspend fun addBonus(date: LocalDate, packageName: String?, minutes: Int)

    /** Досрочно обнулить бонус цели за день; null — бонус телефона. */
    suspend fun clearBonus(date: LocalDate, packageName: String?)
}
