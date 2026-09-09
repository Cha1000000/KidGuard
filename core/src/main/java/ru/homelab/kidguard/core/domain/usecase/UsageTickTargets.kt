package ru.homelab.kidguard.core.domain.usecase

import ru.homelab.kidguard.core.domain.model.LimitState

/** Счётчик, в который уходит очередной тик экранного времени. */
enum class UsageBucket {
    /** Расход бюджета: пока лимит не исчерпан. */
    BUDGET,

    /** Время сверх исчерпанного лимита: бюджет им уже не расходуется. */
    OVERRUN
}

/**
 * Куда движку учёта записать очередной тик.
 *
 * @param dailyBucket счётчик дневного лимита; `null` — приложение дневной лимит не расходует
 *   («Всегда доступные», лаунчер, само KidGuard), тик в дневные счётчики не идёт вовсе.
 * @param appBucket счётчик времени самого приложения (ведётся всегда — это статистика).
 * @param spendsBonusWindow тик дополнительно списывается с «пропуска» — выданного этому
 *   приложению дополнительного времени поверх исчерпанного общего лимита. Счётчик параллельный:
 *   тик уходит и сюда, и в [appBucket].
 */
data class UsageTickTargets(
    val dailyBucket: UsageBucket?,
    val appBucket: UsageBucket,
    val spendsBonusWindow: Boolean = false
)

/**
 * Чистое правило: в какие счётчики уходит тик экранного времени.
 *
 * Разделение бюджета и перерасхода нужно, чтобы **выданный бонус не гасил задним числом время,
 * накрученное после блокировки**. Оверлей мягкой блокировки смахивается, поэтому расход рос и
 * после «время вышло»; остаток считается как `бюджет − израсходовано`, и бонус в 30 минут сперва
 * уходил на погашение этого долга — блокировка не снималась. Со счётчиком перерасхода бюджетная
 * часть не может превысить бюджет, и бонус даёт ровно столько времени, сколько выдан, с момента
 * выдачи.
 *
 * Дневной и личный лимиты решаются независимо: приложение с личным лимитом может исчерпать его,
 * когда дневной ещё не исчерпан, и наоборот.
 *
 * **Пропуск на дополнительное время приложения** ([hasBonusAccessPass]) тратится только при
 * исчерпанном общем лимите — до этого приложение доступно и так, и выданное заранее время не
 * должно утекать вхолостую. Пока пропуск тратится, дневной перерасход **не растёт**: это время
 * родитель разрешил сам, и показывать его как нарушение нельзя. Счётчик самого приложения при
 * этом ведётся как обычно — иначе у приложения с личным лимитом тот перестал бы тратиться, и две
 * механики (продление лимита бонусом и окно доступа) разъехались бы.
 *
 * @param countsTowardsDailyLimit результат [countsTowardsDailyLimit] для активного приложения.
 * @param hasBonusAccessPass у активного приложения есть непотраченное дополнительное время
 *   (см. `hasBonusAccessPass`).
 */
fun usageTickTargets(
    countsTowardsDailyLimit: Boolean,
    dailyLimitState: LimitState,
    appLimitState: LimitState,
    hasBonusAccessPass: Boolean = false
): UsageTickTargets {
    val dailyExpired = dailyLimitState is LimitState.Expired
    val spendsWindow = countsTowardsDailyLimit && dailyExpired && hasBonusAccessPass
    return UsageTickTargets(
        dailyBucket = when {
            !countsTowardsDailyLimit -> null
            spendsWindow -> null
            dailyExpired -> UsageBucket.OVERRUN
            else -> UsageBucket.BUDGET
        },
        appBucket = if (appLimitState is LimitState.Expired) UsageBucket.OVERRUN else UsageBucket.BUDGET,
        spendsBonusWindow = spendsWindow
    )
}
