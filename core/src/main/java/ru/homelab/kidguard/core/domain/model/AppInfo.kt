package ru.homelab.kidguard.core.domain.model

/**
 * Приложение детского устройства (веха 4.1): пакет, человекочитаемое имя и иконка.
 * Иконка — сжатое изображение (WebP) в base64: детское устройство публикует её на сервер
 * вместе со списком, родитель показывает её на экранах выбора приложений.
 * isSystem — системное приложение (FLAG_SYSTEM); isRisky — критичное для устройства
 * (сам KidGuard, дефолтный лаунчер, systemui) — родитель видит предупреждение.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val iconBase64: String? = null,
    val isSystem: Boolean = false,
    val isRisky: Boolean = false
)
