package ru.homelab.kidguard.core.domain.model

import java.time.LocalDate

/**
 * Маркер записи «время сверх исчерпанного дневного бюджета» в статистике (см. [UsageEntry]).
 *
 * Отдельная запись, а не новое поле: протокол статистики уже опознаёт итог дня по маркеру в
 * `packageName`, имя пакета сервер не валидирует — значит перерасход доезжает до родителя, не
 * требуя ни правки схемы, ни деплоя. Имя намеренно непохоже на настоящий пакет.
 */
const val OVERRUN_PACKAGE = "__overrun__"

/**
 * Префикс записи «сколько выданного приложению дополнительного времени израсходовано».
 *
 * Тот же приём, что и с [OVERRUN_PACKAGE]: имя пакета сервер не валидирует, поэтому счётчик
 * доезжает до родителя без правки схемы и деплоя. Префикс, а не отдельный маркер, потому что
 * величина своя у каждого приложения — имя пакета дописывается следом.
 */
const val BONUS_SPENT_PREFIX = "__bonus__:"

/**
 * Запись серверной статистики ребёнка (веха 4.4): накопленные секунды за день.
 * `packageName = ""` — время, израсходовавшее дневной бюджет (маркер-тотал);
 * `packageName = "__overrun__"` — время сверх исчерпанного бюджета;
 * `packageName = "__bonus__:<пакет>"` — израсходованное дополнительное время этого приложения;
 * остальное — по приложениям.
 */
data class UsageEntry(
    val date: LocalDate,
    val packageName: String,
    val seconds: Int
) {
    val isTotal: Boolean get() = packageName.isEmpty()

    val isOverrun: Boolean get() = packageName == OVERRUN_PACKAGE

    /**
     * Пакет, чьё выданное дополнительное время израсходовано на [seconds] секунд; `null` — запись
     * не про это.
     */
    val bonusSpentPackage: String?
        get() = packageName.removePrefix(BONUS_SPENT_PREFIX).takeIf {
            packageName.startsWith(BONUS_SPENT_PREFIX) && it.isNotEmpty()
        }

    /** Запись про конкретное приложение — а не служебный маркер дня. */
    val isApp: Boolean get() = !isTotal && !isOverrun && bonusSpentPackage == null
}
