package ru.homelab.kidguard.core.domain.model

/**
 * Приложение детского устройства (веха 4.1): пакет, человекочитаемое имя и иконка.
 * Иконка — сжатое изображение (WebP) в base64: детское устройство публикует её на сервер
 * вместе со списком, родитель показывает её на экранах выбора приложений.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val iconBase64: String? = null
)
