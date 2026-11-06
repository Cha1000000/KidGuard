package ru.homelab.kidguard.platform.overlay

import ru.homelab.kidguard.core.domain.model.BreakState

/**
 * Состояние показа плашки «скоро перерыв».
 *
 * @param shownKey ключ предупреждения ([BreakState.Warning.warningKey]), для которого плашка уже
 *   успешно показана. `null` — плашка сейчас показывать нечего либо её не удалось показать.
 */
data class BreakWarningPolicyState(val shownKey: String? = null)

/** Что сделать с плашкой на текущем проходе. */
sealed interface BreakWarningAction {

    /** Показать плашку для ключа [warningKey]. */
    data class Show(val warningKey: String) : BreakWarningAction

    /**
     * Убрать плашку с экрана. [forgetShownKey] — терминальное скрытие (перерыв начался, отмена
     * правил, смена ключа): память «плашка показана» сбрасывается. Транзиторное скрытие (звонок,
     * расписание, неинтерактивный экран) память сохраняет — иначе после SCREEN_ON или конца звонка
     * та же плашка показалась бы второй раз (требование однократности).
     */
    data class Hide(val forgetShownKey: Boolean) : BreakWarningAction

    /** Ничего не делать. */
    data object Keep : BreakWarningAction
}

/**
 * Чистая политика плашки предупреждения: когда показывать, когда убирать и когда не повторять.
 *
 * Контракт (план, этап 6):
 * 1. плашка нужна только в состоянии [BreakState.Warning];
 * 2а. не показываем при неинтерактивном экране, активном расписании и звонке — если плашка ещё
 *    НЕ была показана, попытка повторится при следующем событии (в т.ч. SCREEN_ON);
 * 2б. показанную плашку при тех же условиях убираем, но ключ НЕ забываем: одна плашка на ключ
 *    показывается ровно один раз за цикл;
 * 2в. ключ запоминается только после УСПЕШНОГО addView ([onShown] с `success = true`), неудача
 *    оставляет состояние нетронутым и не блокирует повтор;
 * 3. при смене ключа, активном перерыве, отмене правил и любом не-Warning состоянии показанную
 *    плашку убираем и сбрасываем ключ.
 *
 * Android-часть (WindowManager) сюда не входит — только решения.
 */
object BreakWarningPolicy {

    fun decide(
        state: BreakWarningPolicyState,
        breakState: BreakState,
        screenInteractive: Boolean,
        scheduleActive: Boolean,
        callActive: Boolean
    ): BreakWarningAction {
        val shownKey = state.shownKey
        // Плашка живёт только в состоянии предупреждения. Любое другое состояние — явно убираем
        // показанное (старт перерыва, отмена правил, смена дня) и забываем ключ.
        if (breakState !is BreakState.Warning) {
            return if (shownKey != null) BreakWarningAction.Hide(forgetShownKey = true)
            else BreakWarningAction.Keep
        }
        // Транзиторное скрытие: плашку снимаем, но ключ забываем только если он уже неактуален.
        if (callActive || scheduleActive || !screenInteractive) {
            val staleKey = shownKey != null && shownKey != breakState.warningKey
            return if (shownKey != null) BreakWarningAction.Hide(forgetShownKey = staleKey)
            else BreakWarningAction.Keep
        }
        return when (shownKey) {
            breakState.warningKey -> BreakWarningAction.Keep
            // Ключ сменился (новый usage-цикл или другое окно часов): старую плашку убираем и
            // забываем ключ, новую покажет следующий проход — так на экране не окажется два текста.
            null -> BreakWarningAction.Show(breakState.warningKey)
            else -> BreakWarningAction.Hide(forgetShownKey = true)
        }
    }

    /** Учесть результат `addView`: ключ запоминается только при успехе. */
    fun onShown(
        state: BreakWarningPolicyState,
        warningKey: String,
        success: Boolean
    ): BreakWarningPolicyState =
        if (success) BreakWarningPolicyState(warningKey) else state

    /** Учесть скрытие плашки: при [forgetShownKey] ключ перестаёт считаться показанным. */
    fun onHidden(
        state: BreakWarningPolicyState,
        forgetShownKey: Boolean
    ): BreakWarningPolicyState =
        if (forgetShownKey) BreakWarningPolicyState() else state
}
