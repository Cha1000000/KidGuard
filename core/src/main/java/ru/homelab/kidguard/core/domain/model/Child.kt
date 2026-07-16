package ru.homelab.kidguard.core.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Ребёнок в списке у родителя. `avatar` — индекс аватарки из набора; `paired` — привязано ли устройство.
 *
 * [lastSeenAt] и [health] — данные watchdog (веха 6): когда детское устройство последний раз
 * выходило на связь и что доложило о себе. Оба null, пока не пришёл ни один heartbeat (не привязан,
 * или на устройстве старая версия приложения).
 */
data class Child(
    val id: Int,
    val name: String,
    val avatar: Int,
    val paired: Boolean,
    val lastSeenAt: Instant? = null,
    val health: DeviceHealth? = null
) {
    /**
     * Контроль сломан и родителю надо вмешаться.
     *
     * Два разных случая (см. `docs/plans/milestone-06-watchdog.md`):
     * - устройство доложило, что разрешение отвалилось — видно сразу, порог не нужен;
     * - устройство молчит дольше [STALE_AFTER] — значит контроль убит целиком и доложить не может.
     *
     * Молчание не считаем поломкой сразу: телефон ребёнка ночью выключен или без интернета — это
     * норма. Плашка, которая врёт каждое утро, перестаёт работать.
     */
    fun isControlBroken(now: Instant): Boolean = when {
        !paired -> false
        health?.isHealthy == false -> true
        lastSeenAt == null -> false
        else -> Duration.between(lastSeenAt, now) > STALE_AFTER
    }

    companion object {
        /** Порог «молчания», после которого показываем тревогу (решение Володи 16.07.2026). */
        val STALE_AFTER: Duration = Duration.ofHours(12)
    }
}

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
