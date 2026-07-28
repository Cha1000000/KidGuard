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
 * 5. Общий дневной лимит исчерпан ([limitState] = Expired) ИЛИ идёт «Время учёбы»
 *    ([studyTimeActive]) — блокируем.
 * 6. Иначе — доступно.
 *
 * «Время учёбы» стоит ровно на месте исчерпанного дневного лимита: по смыслу это то же самое
 * («телефон сейчас нельзя»), поэтому белый список продолжает работать — ребёнок остаётся на
 * связи. «Время сна» сюда НЕ входит: оно блокирует всё, включая лаунчер, и потому не может
 * опираться на [activePackage] — им занимается отдельный контроллер ночного замка.
 */
fun shouldBlock(
    activePackage: String?,
    limitState: LimitState,
    appLimitState: LimitState,
    whitelist: Set<String>,
    alwaysAllowed: Set<String>,
    blockedApps: Set<String>,
    studyTimeActive: Boolean = false
): Boolean {
    if (activePackage == null) return false
    if (activePackage in alwaysAllowed) return false
    if (activePackage in blockedApps) return true
    if (appLimitState is LimitState.Expired) return true
    if (activePackage in whitelist) return false
    return limitState is LimitState.Expired || studyTimeActive
}
