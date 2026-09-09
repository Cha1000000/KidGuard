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
 * доступные» + приложения с непотраченным дополнительным временем ([bonusPassPackages]).
 *
 * Последнее обязательно: при исчерпанном лимите VPN уходит в blackhole, и без этого приложение,
 * которое родитель только что открыл ребёнку, запустилось бы **без интернета** — то есть
 * бесполезным ровно в том случае, ради которого время и выдавали (позвонить, написать).
 */
fun vpnDisallowedPackages(
    whitelist: Set<String>,
    ownPackageName: String,
    bonusPassPackages: Set<String> = emptySet()
): Set<String> = whitelist + bonusPassPackages + ownPackageName

/**
 * disallowed-набор для VPN (веха 5.4). VPN активен всегда; режим переключается набором:
 * Expired → только whitelist + own (блокировка), иначе → все установленные (pass-through).
 */
fun vpnDisallowedFor(
    limitState: LimitState,
    whitelist: Set<String>,
    allInstalled: Set<String>,
    ownPackageName: String,
    bonusPassPackages: Set<String> = emptySet()
): Set<String> = if (shouldBlockInternet(limitState)) {
    vpnDisallowedPackages(whitelist, ownPackageName, bonusPassPackages)
} else {
    allInstalled + ownPackageName
}
