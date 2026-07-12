package ru.homelab.kidguard.core.domain.usecase

import ru.homelab.kidguard.core.domain.model.LimitState

/**
 * Чистое правило: нужно ли блокировать активное приложение.
 *
 * Матрица приоритетов (веха 4.1.2, зафиксирована в плане):
 * 1. [alwaysAllowed] (само KidGuard и лаунчер) — никогда не блокируем.
 * 2. Приложение в списке запрещённых [blockedApps] — блокируем: полный запрет родителя бьёт
 *    всё, кроме [alwaysAllowed] (включая белый список и личные лимиты).
 * 3. Личный лимит приложения исчерпан ([appLimitState] = Expired) — блокируем: точечное
 *    правило бьёт даже белый список.
 * 4. Приложение в белом списке [whitelist] — доступно (общий лимит игнорируется, как в вехе 2).
 * 5. Общий дневной лимит исчерпан ([limitState] = Expired) — блокируем.
 * 6. Иначе — доступно.
 */
fun shouldBlock(
    activePackage: String?,
    limitState: LimitState,
    appLimitState: LimitState,
    whitelist: Set<String>,
    alwaysAllowed: Set<String>,
    blockedApps: Set<String>
): Boolean {
    if (activePackage == null) return false
    if (activePackage in alwaysAllowed) return false
    if (activePackage in blockedApps) return true
    if (appLimitState is LimitState.Expired) return true
    if (activePackage in whitelist) return false
    return limitState is LimitState.Expired
}
