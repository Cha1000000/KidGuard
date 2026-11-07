package ru.homelab.kidguard.platform.overlay

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.BreakState

/**
 * Контракт плашки предупреждения (план, этап 6, дефект D15):
 * - одна плашка на ключ предупреждения, без повтора на каждом тике и после транзиторного
 *   скрытия (SCREEN_ON, конец звонка) — «однократность»;
 * - ключ запоминается только после успешного addView, неудача не блокирует ретрай;
 * - не показываем при неинтерактивном экране, расписании и звонке;
 * - явно убираем при старте перерыва, отмене правил и смене ключа.
 */
class BreakWarningPolicyTest {

    private val nothingShown = BreakWarningPolicyState()
    private val warning = BreakState.Warning("interval:cycle-1")
    private val nextWarning = BreakState.Warning("interval:cycle-2")
    private val activeBreak = BreakState.Active(
        secondsLeft = 300,
        message = "Разомнись",
        sessionId = "session-1",
        endsAtElapsedMs = 900_000L
    )

    private fun decide(
        state: BreakWarningPolicyState = nothingShown,
        breakState: BreakState = warning,
        screenInteractive: Boolean = true,
        scheduleActive: Boolean = false,
        callActive: Boolean = false
    ) = BreakWarningPolicy.decide(
        state = state,
        breakState = breakState,
        screenInteractive = screenInteractive,
        scheduleActive = scheduleActive,
        callActive = callActive
    )

    @Test
    fun `активное предупреждение на интерактивном экране — показываем`() {
        assertEquals(BreakWarningAction.Show("interval:cycle-1"), decide())
    }

    @Test
    fun `тот же ключ уже показан — повторно не показываем`() {
        val shown = BreakWarningPolicyState("interval:cycle-1")
        assertEquals(BreakWarningAction.Keep, decide(state = shown))
    }

    @Test
    fun `неудачный addView не запоминает ключ — следующий проход пробует снова`() {
        val afterFailure = BreakWarningPolicy.onShown(nothingShown, "interval:cycle-1", success = false)
        assertEquals(nothingShown, afterFailure)
        assertEquals(BreakWarningAction.Show("interval:cycle-1"), decide(state = afterFailure))
    }

    @Test
    fun `неудачный addView не трогает уже запомненный другой ключ`() {
        val shown = BreakWarningPolicyState("interval:cycle-1")
        assertEquals(shown, BreakWarningPolicy.onShown(shown, "interval:cycle-2", success = false))
    }

    @Test
    fun `успешный addView запоминает ключ`() {
        val afterSuccess = BreakWarningPolicy.onShown(nothingShown, "interval:cycle-1", success = true)
        assertEquals("interval:cycle-1", afterSuccess.shownKey)
        assertEquals(BreakWarningAction.Keep, decide(state = afterSuccess))
    }

    @Test
    fun `неинтерактивный экран — плашку не показываем`() {
        assertEquals(BreakWarningAction.Keep, decide(screenInteractive = false))
    }

    @Test
    fun `активное расписание — плашку не показываем`() {
        assertEquals(BreakWarningAction.Keep, decide(scheduleActive = true))
    }

    @Test
    fun `идёт звонок — плашку не показываем`() {
        assertEquals(BreakWarningAction.Keep, decide(callActive = true))
    }

    @Test
    fun `начался перерыв — показанную плашку убираем и забываем ключ`() {
        val shown = BreakWarningPolicyState("interval:cycle-1")
        assertEquals(
            BreakWarningAction.Hide(forgetShownKey = true),
            decide(state = shown, breakState = activeBreak)
        )
    }

    @Test
    fun `перерыв без показанной плашки — скрывать нечего`() {
        assertEquals(BreakWarningAction.Keep, decide(breakState = activeBreak))
    }

    @Test
    fun `после терминального скрытия ключ сбрасывается и плашка может показаться снова`() {
        val shown = BreakWarningPolicyState("interval:cycle-1")
        val hidden = BreakWarningPolicy.onHidden(shown, forgetShownKey = true)
        assertEquals(nothingShown, hidden)
        assertEquals(BreakWarningAction.Show("interval:cycle-1"), decide(state = hidden))
    }

    @Test
    fun `транзиторное скрытие не даёт повторного показа той же плашки`() {
        // Плашка показана, экран погашен (неинтерактивно) — контроллер убирает её. «Однократность»:
        // после SCREEN_ON та же плашка того же ключа НЕ показывается снова, но и не показывается
        // ложью — ключ остаётся запомненным.
        val shown = BreakWarningPolicyState("interval:cycle-1")
        val action = decide(state = shown, screenInteractive = false)
        assertEquals(BreakWarningAction.Hide(forgetShownKey = false), action)
        val hidden = BreakWarningPolicy.onHidden(shown, forgetShownKey = false)
        assertEquals(shown, hidden)
        assertEquals(BreakWarningAction.Keep, decide(state = hidden))
    }

    @Test
    fun `после звонка та же плашка не показывается повторно`() {
        val shown = BreakWarningPolicyState("interval:cycle-1")
        val action = decide(state = shown, callActive = true)
        assertEquals(BreakWarningAction.Hide(forgetShownKey = false), action)
        val hidden = BreakWarningPolicy.onHidden(shown, forgetShownKey = false)
        assertEquals(BreakWarningAction.Keep, decide(state = hidden))
    }

    @Test
    fun `смена ключа — старую плашку убираем, новый покажет следующий проход`() {
        val shown = BreakWarningPolicyState("interval:cycle-1")
        assertEquals(
            BreakWarningAction.Hide(forgetShownKey = true),
            decide(state = shown, breakState = nextWarning)
        )
        val hidden = BreakWarningPolicy.onHidden(shown, forgetShownKey = true)
        assertEquals(
            BreakWarningAction.Show("interval:cycle-2"),
            decide(state = hidden, breakState = nextWarning)
        )
    }

    @Test
    fun `транзиторное скрытие при смене ключа — старый ключ всё равно забываем`() {
        // Звонок накрыл момент смены ключа: старый ключ уже неактуален, его память не должна
        // блокировать показ новой плашки после звонка.
        val shown = BreakWarningPolicyState("interval:cycle-1")
        val action = decide(state = shown, breakState = nextWarning, callActive = true)
        assertEquals(BreakWarningAction.Hide(forgetShownKey = true), action)
        val hidden = BreakWarningPolicy.onHidden(shown, forgetShownKey = true)
        assertEquals(
            BreakWarningAction.Show("interval:cycle-2"),
            decide(state = hidden, breakState = nextWarning)
        )
    }

    @Test
    fun `расписание при показанной плашке — убираем, но ключ не забываем`() {
        val shown = BreakWarningPolicyState("interval:cycle-1")
        assertEquals(
            BreakWarningAction.Hide(forgetShownKey = false),
            decide(state = shown, scheduleActive = true)
        )
    }

    @Test
    fun `звонок при показанной плашке — убираем, но ключ не забываем`() {
        val shown = BreakWarningPolicyState("interval:cycle-1")
        assertEquals(
            BreakWarningAction.Hide(forgetShownKey = false),
            decide(state = shown, callActive = true)
        )
    }

    @Test
    fun `состояние Idle без плашки — ничего не делаем`() {
        assertEquals(BreakWarningAction.Keep, decide(breakState = BreakState.Idle))
    }

    @Test
    fun `отмена правил при показанной плашке — убираем и забываем ключ`() {
        val shown = BreakWarningPolicyState("interval:cycle-1")
        assertEquals(
            BreakWarningAction.Hide(forgetShownKey = true),
            decide(state = shown, breakState = BreakState.Idle)
        )
    }
}
