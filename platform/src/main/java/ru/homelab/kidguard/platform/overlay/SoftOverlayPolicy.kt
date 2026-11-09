package ru.homelab.kidguard.platform.overlay

/**
 * Скрывать ли уже показанную мягкую плашку, когда решение стало «не блокировать».
 *
 * Наивное «скрывать при `block = false`» ломает показ: `enforce` уводит ребёнка «Домой», лаунчер
 * всегда разрешён, решение тут же становится false — и плашка исчезла бы, не дав прочитать текст.
 * Поэтому скрываем только когда исчезла сама причина (расписание кончилось, лимит восстановлен,
 * пакет разблокирован) или ребёнок ушёл в другое приложение. Лаунчер/KidGuard с живой причиной
 * плашку не снимают — ребёнок как раз читает её.
 */
fun shouldHideSoftOverlay(
    lastReason: BlockReason,
    lastPackage: String?,
    activePackage: String?,
    studyTimeActive: Boolean,
    dailyLimitExpired: Boolean,
    personalLimitExpired: Boolean,
    blockedApps: Set<String>,
    alwaysAllowed: Set<String>
): Boolean = when {
    lastReason == BlockReason.STUDY_TIME && !studyTimeActive -> true
    // Причина LIMIT_EXPIRED значит «исчерпан какой-то лимит». Дневной и личный — разные лимиты,
    // и после sendHome активным пакетом становится лаунчер, у которого личных лимитов нет:
    // смотреть на лимиты ТЕКУЩЕГО пакета нельзя, плашка снялась бы сразу после показа.
    // Поэтому «причина жива», пока жив хотя бы один из лимитов, заданных для показа.
    lastReason == BlockReason.LIMIT_EXPIRED && !dailyLimitExpired && !personalLimitExpired -> true
    lastReason == BlockReason.BLOCKED_BY_PARENT &&
        lastPackage != null &&
        lastPackage !in blockedApps -> true
    // Переход в другое приложение (не лаунчер и не сам KidGuard): плашка не должна накрывать
    // экран, который ребёнок только что открыл; таймер всё равно снял бы её через 6 с.
    activePackage != null &&
        activePackage !in alwaysAllowed &&
        activePackage != lastPackage -> true
    else -> false
}
