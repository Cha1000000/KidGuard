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
 * Запись серверной статистики ребёнка (веха 4.4): накопленные секунды за день.
 * `packageName = ""` — время, израсходовавшее дневной бюджет (маркер-тотал);
 * `packageName = "__overrun__"` — время сверх исчерпанного бюджета; остальное — по приложениям.
 */
data class UsageEntry(
    val date: LocalDate,
    val packageName: String,
    val seconds: Int
) {
    val isTotal: Boolean get() = packageName.isEmpty()

    val isOverrun: Boolean get() = packageName == OVERRUN_PACKAGE

    /** Запись про конкретное приложение — а не служебный маркер дня. */
    val isApp: Boolean get() = !isTotal && !isOverrun
}
