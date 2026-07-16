package ru.homelab.kidguard.core.domain.model

/**
 * Родительский PIN, защищающий критичные настройки на детском устройстве (веха 6, шаг 6.1).
 * Хранится и синхронизируется только в виде соли и хеша — сырой PIN нигде не оседает.
 */
data class PinProtection(
    val hash: String,
    val salt: String
)
