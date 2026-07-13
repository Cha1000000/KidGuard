package ru.homelab.kidguard.core.domain.usecase

import ru.homelab.kidguard.core.domain.model.LimitState

/**
 * Чистое правило (веха 5.3): нужно ли поднимать blackhole-VPN и блокировать интернет.
 *
 * Блокируем ровно тогда, когда исчерпан **общий** дневной лимит ([LimitState.Expired]).
 * Пер-app лимиты и запрещённые приложения сюда не относятся — они по-прежнему обрабатываются
 * оверлеем ([shouldBlock]), VPN их не касается.
 */
fun shouldBlockInternet(limitState: LimitState): Boolean = limitState is LimitState.Expired

/**
 * Набор пакетов, которые должны обходить VPN (`addDisallowedApplication`) и ходить в сеть
 * напрямую: само KidGuard (нужна синхронизация с сервером) + всё из белого списка «всегда
 * доступные».
 */
fun vpnDisallowedPackages(whitelist: Set<String>, ownPackageName: String): Set<String> =
    whitelist + ownPackageName
