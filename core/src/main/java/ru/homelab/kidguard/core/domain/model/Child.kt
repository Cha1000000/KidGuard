package ru.homelab.kidguard.core.domain.model

/** Ребёнок в списке у родителя. `avatar` — индекс аватарки из набора; `paired` — привязано ли устройство. */
data class Child(
    val id: Int,
    val name: String,
    val avatar: Int,
    val paired: Boolean
)

/** Результат создания ребёнка: сам ребёнок + сгенерированный сервером pairing-код. */
data class ChildWithCode(
    val child: Child,
    val code: String
)

/** Профиль ребёнка после успешной привязки устройства (веха 4.2). `avatar` — индекс аватарки. */
data class PairedChild(
    val id: Int,
    val name: String,
    val avatar: Int = 0
)
