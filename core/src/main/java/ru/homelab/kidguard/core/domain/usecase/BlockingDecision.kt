package ru.homelab.kidguard.core.domain.usecase

import ru.homelab.kidguard.core.domain.model.LimitState

/**
 * Чистое правило: нужно ли блокировать активное приложение.
 *
 * Блокируем, только если лимит исчерпан ([LimitState.Expired]) и приложение не входит ни в
 * белый список родителя, ни в набор всегда разрешённых ([alwaysAllowed] — само KidGuard и
 * лаунчеры, чтобы не блокировать домашний экран и не перекрывать себя).
 */
fun shouldBlock(
    activePackage: String?,
    limitState: LimitState,
    whitelist: Set<String>,
    alwaysAllowed: Set<String>
): Boolean {
    if (activePackage == null) return false
    if (limitState !is LimitState.Expired) return false
    if (activePackage in alwaysAllowed) return false
    if (activePackage in whitelist) return false
    return true
}
