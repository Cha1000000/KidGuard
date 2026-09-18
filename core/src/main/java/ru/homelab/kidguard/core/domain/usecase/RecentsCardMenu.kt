package ru.homelab.kidguard.core.domain.usecase

/** Подписи пункта «закрепить/заблокировать карточку» в меню карточки списка последних. */
private val LOCK_LABELS = listOf("заблокирова", "закрепи", "lock", "pin app", "закріпи")

/** Подписи обратного пункта: он показывается, когда карточка УЖЕ закреплена. */
private val UNLOCK_LABELS = listOf("разблокирова", "открепи", "unlock", "unpin")

/**
 * Пункт меню закрепляет карточку.
 *
 * Отдельная функция с тестами, потому что действие «нажать пункт чужого меню» опасно ошибиться:
 * промах по соседнему пункту закроет приложение или разделит экран. Сначала проверяем обратный
 * пункт ([isRecentsUnlockMenuItem]) — «Разблокировать» содержит в себе «блокирова» и иначе
 * распозналось бы как закрепление.
 */
fun isRecentsLockMenuItem(label: String?): Boolean {
    val text = label?.lowercase()?.trim().orEmpty()
    if (text.isEmpty() || isRecentsUnlockMenuItem(text)) return false
    return LOCK_LABELS.any { text.contains(it) }
}

/** Пункт меню открепляет карточку — значит, она уже закреплена и делать ничего не нужно. */
fun isRecentsUnlockMenuItem(label: String?): Boolean {
    val text = label?.lowercase()?.trim().orEmpty()
    return text.isNotEmpty() && UNLOCK_LABELS.any { text.contains(it) }
}
