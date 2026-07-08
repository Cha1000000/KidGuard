package ru.homelab.kidguard.core.domain.usecase

import ru.homelab.kidguard.core.domain.model.LimitState

/**
 * Чистое правило: нужно ли блокировать активное приложение.
 *
 * Матрица приоритетов (веха 3, зафиксирована в плане):
 * 1. [alwaysAllowed] (само KidGuard и лаунчер) — никогда не блокируем.
 * 2. Личный лимит приложения исчерпан ([appLimitState] = Expired) — блокируем: точечное
 *    правило бьёт даже белый список.
 * 3. Приложение в белом списке [whitelist] — доступно (общий лимит игнорируется, как в вехе 2).
 * 4. Общий дневной лимит исчерпан ([limitState] = Expired) — блокируем.
 * 5. Иначе — доступно.
 */
fun shouldBlock(
    activePackage: String?,
    limitState: LimitState,
    appLimitState: LimitState,
    whitelist: Set<String>,
    alwaysAllowed: Set<String>
): Boolean {
    if (activePackage == null) return false
    if (activePackage in alwaysAllowed) return false
    if (appLimitState is LimitState.Expired) return true
    if (activePackage in whitelist) return false
    return limitState is LimitState.Expired
}
