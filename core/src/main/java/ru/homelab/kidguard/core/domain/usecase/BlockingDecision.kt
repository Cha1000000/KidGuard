package ru.homelab.kidguard.core.domain.usecase

import ru.homelab.kidguard.core.domain.model.BlockCause
import ru.homelab.kidguard.core.domain.model.LimitState

/**
 * Какие причины закрывают доступ приложению — по каждому предмету отдельно.
 *
 * Это развёрнутая форма ответа [shouldBlock]: «заблокирован ли пакет» и «почему» решаются в одном
 * месте, поэтому экран-замок и правило его снятия читают одно и то же и не могут разойтись.
 *
 * @property studyTime идёт «Время учёбы»
 * @property blockedByParent пакет запрещён родителем
 * @property dailyLimitExpired исчерпан общий дневной лимит
 * @property personalLimitExpired исчерпан личный лимит этого пакета
 * @property bonusPass у пакета есть нерастраченный пропуск дополнительного времени
 */
data class BlockCauses(
    val studyTime: Boolean = false,
    val blockedByParent: Boolean = false,
    val dailyLimitExpired: Boolean = false,
    val personalLimitExpired: Boolean = false,
    val bonusPass: Boolean = false
) {

    /** Есть ли хоть одна причина, по которой пакет закрыт. */
    val any: Boolean
        get() = studyTime || blockedByParent || dailyLimitExpired || personalLimitExpired

    /**
     * Причины по предметам: окну расписания, общему лимиту, личному лимиту пакета, запрету.
     *
     * Личный лимит относится к конкретному пакету, поэтому несёт его имя — иначе причина
     * «личный лимит» от одного приложения совпала бы с лимитом другого.
     */
    fun causesFor(packageName: String): Set<BlockCause> = buildSet {
        if (studyTime) add(BlockCause.StudyTime)
        if (dailyLimitExpired) add(BlockCause.DailyLimitExpired)
        if (personalLimitExpired) add(BlockCause.PersonalLimitExpired(packageName))
        if (blockedByParent) add(BlockCause.BlockedByParent)
    }

}

/**
 * Чистое правило: нужно ли блокировать активное приложение и по каким причинам.
 *
 * Матрица приоритетов (веха 4.1.2, зафиксирована в плане):
 * 1. [alwaysAllowed] (само KidGuard и лаунчер) — никогда не блокируем.
 * 2. Приложение в списке запрещённых [blockedApps] — блокируем: полный запрет родителя бьёт
 *    всё, кроме [alwaysAllowed] (включая белый список и личные лимиты).
 * 3. Личный лимит приложения исчерпан ([appLimitState] = Expired) — блокируем: точечное
 *    правило бьёт даже белый список.
 * 4. Приложение в белом списке [whitelist] — доступно (общий лимит игнорируется, как в вехе 2).
 * 5. У приложения есть непотраченный пропуск [hasBonusAccessPass] — доступно: родитель выдал
 *    этому приложению дополнительное время поверх исчерпанного общего лимита.
 * 6. Общий дневной лимит исчерпан ([limitState] = Expired) ИЛИ идёт «Время учёбы»
 *    ([studyTimeActive]) — блокируем.
 * 7. Иначе — доступно.
 *
 * **Пропуск стоит ПОСЛЕ личного лимита намеренно.** Если у приложения есть свой лимит, выданный
 * бонус продлевает его обычным путём (`ObserveAppLimitStateUseCase` складывает лимит с бонусом).
 * Пропустить пропуск выше значило бы дать приложению работать мимо учёта собственного времени.
 *
 * **«Время учёбы» пропуск не снимает** (как и «Время сна» — им занят отдельный ночной замок):
 * расписание это осознанное «сейчас телефон нельзя», а не исчерпанный бюджет.
 *
 * «Время учёбы» стоит ровно на месте исчерпанного дневного лимита: по смыслу это то же самое
 * («телефон сейчас нельзя»), поэтому белый список продолжает работать — ребёнок остаётся на
 * связи. «Время сна» сюда НЕ входит: оно блокирует всё, включая лаунчер, и потому не может
 * опираться на [activePackage] — им занимается отдельный контроллер ночного замка.
 */
fun blockCausesFor(
    activePackage: String?,
    limitState: LimitState,
    appLimitState: LimitState,
    whitelist: Set<String>,
    alwaysAllowed: Set<String>,
    blockedApps: Set<String>,
    studyTimeActive: Boolean = false,
    hasBonusAccessPass: Boolean = false
): BlockCauses {
    if (activePackage == null) return BlockCauses()
    if (activePackage in alwaysAllowed) return BlockCauses()

    // Порядок как в матрице: запрет и личный лимит бьют белый список, белый список снимает
    // общий лимит и расписание, пропуск — только общий лимит.
    val blockedByParent = activePackage in blockedApps
    val personalLimitExpired = appLimitState is LimitState.Expired
    val whitelisted = activePackage in whitelist
    // Расписание — осознанное «сейчас телефон нельзя»: пропуск его НЕ снимает (пункт 5),
    // а «Всегда доступные» — снимают: ребёнок должен иметь возможность позвонить.
    val studyTime = studyTimeActive && !whitelisted
    // Общий лимит снимают «Всегда доступные» и нерастраченный пропуск (но не во время учёбы:
    // там «телефон нельзя» независимо от потраченного бюджета).
    val dailyLimitBlocked = limitState is LimitState.Expired &&
        !whitelisted &&
        !(hasBonusAccessPass && !studyTimeActive)

    return BlockCauses(
        studyTime = studyTime,
        blockedByParent = blockedByParent,
        dailyLimitExpired = dailyLimitBlocked,
        personalLimitExpired = personalLimitExpired,
        bonusPass = hasBonusAccessPass
    )
}

/**
 * Нужно ли блокировать активное приложение.
 *
 * Тонкая обёртка над [blockCausesFor]: «есть ли хоть одна причина». Всё, что нужно знать
 * экрану-замку, берётся из причин, а не из этого флага.
 */
fun shouldBlock(
    activePackage: String?,
    limitState: LimitState,
    appLimitState: LimitState,
    whitelist: Set<String>,
    alwaysAllowed: Set<String>,
    blockedApps: Set<String>,
    studyTimeActive: Boolean = false,
    hasBonusAccessPass: Boolean = false
): Boolean = blockCausesFor(
    activePackage = activePackage,
    limitState = limitState,
    appLimitState = appLimitState,
    whitelist = whitelist,
    alwaysAllowed = alwaysAllowed,
    blockedApps = blockedApps,
    studyTimeActive = studyTimeActive,
    hasBonusAccessPass = hasBonusAccessPass
).any

/**
 * Можно ли предлагать на мягком блоке ссылку «Открыть с PIN родителя».
 *
 * Без заданного PIN ссылка — дыра: `PinGuard` вернёт `NoPinSet`, а `PinOverlayManager` трактует
 * это как успех (осознанный аварийный выход для замка «контроль отключён»), и ребёнок откроет
 * приложение любыми четырьмя цифрами. Поэтому ссылка только при реально заданном PIN и известном
 * пакете, который нужно перезапустить после обхода.
 */
fun pinBypassAllowed(pinConfigured: Boolean, activePackage: String?): Boolean =
    pinConfigured && activePackage != null

/**
 * Расходует ли приложение **общий дневной лимит**. Зеркальная сторона [shouldBlock]: что дневной
 * лимит не закрывает, то он и не тратит.
 *
 * Не расходуют лимит [alwaysAllowed] (лаунчер и само KidGuard — пункт 1 матрицы) и [whitelist]
 * (родительский список «Всегда доступные» — пункт 4). Раньше расходовали все подряд, и час
 * разговора по телефону съедал час игрового времени, хотя телефон при исчерпанном лимите
 * оставался доступен.
 *
 * Запрещённые приложения и приложения с личным лимитом сюда НЕ входят: их время учитывается
 * обычным порядком, а закрывают их отдельные правила (пункты 2 и 3), не общий лимит.
 */
fun countsTowardsDailyLimit(
    packageName: String,
    whitelist: Set<String>,
    alwaysAllowed: Set<String>
): Boolean = packageName !in alwaysAllowed && packageName !in whitelist
