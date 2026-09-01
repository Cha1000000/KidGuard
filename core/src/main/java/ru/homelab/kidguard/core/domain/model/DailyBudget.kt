package ru.homelab.kidguard.core.domain.model

/**
 * Итог одного дня по экранному времени: сколько времени было доступно ребёнку и как он его
 * израсходовал.
 *
 * «Бюджет дня» = дневной лимит **плюс** выданное на этот день «Дополнительное время» (бонус)
 * **минус** назначенный на этот день штраф (см. [dayBudgetMinutes]).
 * Именно эту величину сравнивает с расходом сам enforcement ([LimitState] через
 * `ObserveLimitStateUseCase`), поэтому и родительская статистика обязана показывать её же —
 * иначе экран расходится с реальным поведением приложения.
 *
 * Расход может превысить бюджет, и это НЕ ошибка учёта: оверлей блокировки смахиваемый
 * (намеренно — чтобы ребёнок мог позвонить), а счётчик экранного времени продолжает капать на
 * «Всегда доступных» приложениях и лаунчере. Поэтому перерасход — отдельное состояние
 * [Overrun], а не отрицательный остаток.
 */
sealed interface DailyBudgetState {

    /** На этот день лимит не задан — ограничения нет, бюджета тоже. */
    data object NoLimit : DailyBudgetState

    /** Время ещё осталось: израсходовано [usedMinutes] из [budgetMinutes]. */
    data class Remaining(
        val budgetMinutes: Int,
        val usedMinutes: Int,
        val leftMinutes: Int
    ) : DailyBudgetState

    /**
     * Бюджет выбран полностью. [overMinutes] — сколько ребёнок пользовался телефоном СВЕРХ
     * бюджета; ноль означает «ровно исчерпан, перерасхода пока нет».
     */
    data class Overrun(
        val budgetMinutes: Int,
        val usedMinutes: Int,
        val overMinutes: Int
    ) : DailyBudgetState
}

/**
 * Бюджет дня в минутах: дневной лимит **плюс** выданный бонус **минус** назначенный штраф, но
 * не меньше нуля.
 *
 * Единая формула для всего приложения: и enforcement, и оба детских экрана, и родительская
 * статистика обязаны считать бюджет одинаково — иначе экран расходится с реальным поведением.
 *
 * **Штраф не может отнять больше самого лимита** — отсюда `coerceAtMost(limitMinutes)`.
 * Родительский экран и так не даёт назначить штраф больше остатка, но он может прийти от
 * второго родителя или разъехаться при рассинхроне. Обрезать нужно именно штраф, а не итоговую
 * сумму: при обрезке суммы «лишние» минусы съедали бы выданный следом бонус, и родитель,
 * вернувший ребёнку время, видел бы всё тот же экран блокировки. После обрезки бонус всегда
 * прибавляется целиком и всегда даёт время.
 */
fun dayBudgetMinutes(limitMinutes: Int, bonusMinutes: Int, penaltyMinutes: Int): Int =
    limitMinutes - penaltyMinutes.coerceIn(0, limitMinutes) + bonusMinutes

/**
 * Считает состояние дня по лимиту (null — лимита нет), выданному бонусу, назначенному штрафу и
 * израсходованным минутам.
 *
 * Граница «израсходовано ровно по бюджету» относится к [DailyBudgetState.Overrun] — так же, как в
 * `ObserveLimitStateUseCase`, где `minutesLeft <= 0` даёт `Expired` и включает блокировку.
 */
fun dailyBudgetState(
    limitMinutes: Int?,
    bonusMinutes: Int,
    penaltyMinutes: Int,
    usedMinutes: Int
): DailyBudgetState {
    if (limitMinutes == null) return DailyBudgetState.NoLimit
    val budgetMinutes = dayBudgetMinutes(limitMinutes, bonusMinutes, penaltyMinutes)
    val leftMinutes = budgetMinutes - usedMinutes
    return if (leftMinutes > 0) {
        DailyBudgetState.Remaining(budgetMinutes, usedMinutes, leftMinutes)
    } else {
        DailyBudgetState.Overrun(budgetMinutes, usedMinutes, overMinutes = -leftMinutes)
    }
}
