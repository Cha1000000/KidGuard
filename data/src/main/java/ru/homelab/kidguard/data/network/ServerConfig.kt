package ru.homelab.kidguard.data.network

import ru.homelab.kidguard.data.BuildConfig

/**
 * Адрес KidGuard-server. Значение зависит от типа сборки (buildConfigField в build.gradle.kts
 * модуля :data): debug — локальный dev-сервер через 10.0.2.2 (алиас эмулятора на localhost
 * хоста), release — боевой AdminVPS (шаг 4.7; пока по IP:порт, HTTPS/поддомен — позже).
 *
 * Вынесен из NetworkModule: KSP-обработка Hilt-модуля не переваривает поле с типом
 * из генерируемого BuildConfig в release-сборке.
 */
internal object ServerConfig {
    const val BASE_URL = BuildConfig.BASE_URL

    /** Push-канал WebSocket на том же хосте, что HTTP API. */
    val WS_URL = BASE_URL.replaceFirst("http", "ws") + "ws"
}
