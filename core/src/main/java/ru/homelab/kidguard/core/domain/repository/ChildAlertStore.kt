package ru.homelab.kidguard.core.domain.repository

import ru.homelab.kidguard.core.domain.model.Child

/**
 * Помнит, в каком состоянии были детские устройства на прошлой проверке здоровья.
 *
 * Нужен, чтобы уведомлять родителя на ПЕРЕХОДЕ «было в порядке → сломалось»
 * (см. `childAlert`), а не повторять одно и то же каждые 15 минут: от таких уведомлений
 * отключаются, и тогда родитель не узнает и о настоящей поломке.
 */
interface ChildAlertStore {

    /** Состояние с прошлой проверки: id ребёнка → ребёнок. Пусто — проверяем впервые. */
    suspend fun previous(): Map<Int, Child>

    suspend fun save(children: List<Child>)

    /** Выход из аккаунта: чужие дети не должны участвовать в сравнении после нового входа. */
    suspend fun clear()
}
