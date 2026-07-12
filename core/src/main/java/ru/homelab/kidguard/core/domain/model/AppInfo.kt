package ru.homelab.kidguard.core.domain.model

/**
 * Приложение детского устройства (веха 4.1): пакет + человекочитаемое имя.
 * Без иконки — она есть только локально (UI подставляет её через PackageManager,
 * если приложение установлено и на этом устройстве).
 */
data class AppInfo(
    val packageName: String,
    val label: String
)
