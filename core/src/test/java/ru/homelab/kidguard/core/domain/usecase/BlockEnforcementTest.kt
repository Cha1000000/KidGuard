package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Удержание блокировки: сверка монитора со стеком окон и повтор блокировки.
 *
 * Сценарий, ради которого это написано (эмулятор, 17.09.2026): запрещённое приложение заблокировано,
 * ребёнок мгновенно вернулся в него, опоздавшее событие лаунчера перезаписало монитор — и приложение
 * осталось открытым без блокировки.
 */
class BlockEnforcementTest {

    private val launcher = "com.transsion.hilauncher"
    private val game = "ru.oneme.app"

    // --- nextStackSync ---

    @Test
    fun `одно расхождение со стеком монитор не правит, а запоминает кандидата`() {
        val step = nextStackSync(current = launcher, observed = game, candidate = null)
        assertEquals(StackSyncStep(accept = null, candidate = game), step)
    }

    @Test
    fun `два одинаковых расхождения подряд правят монитор`() {
        val step = nextStackSync(current = launcher, observed = game, candidate = game)
        assertEquals(StackSyncStep(accept = game, candidate = null), step)
    }

    @Test
    fun `два разных расхождения подряд монитор не правят`() {
        val step = nextStackSync(current = launcher, observed = game, candidate = "com.block.juggle")
        assertEquals(StackSyncStep(accept = null, candidate = game), step)
    }

    @Test
    fun `совпадение со стеком сбрасывает кандидата`() {
        val step = nextStackSync(current = game, observed = game, candidate = launcher)
        assertEquals(StackSyncStep(accept = null, candidate = null), step)
    }

    @Test
    fun `пустой стек ничего не меняет и рвёт цепочку подтверждения`() {
        val step = nextStackSync(current = launcher, observed = null, candidate = game)
        assertEquals(StackSyncStep(accept = null, candidate = null), step)
    }

    @Test
    fun `залипший монитор поправляется за две сверки`() {
        // Монитор залип на лаунчере, на экране игра.
        val first = nextStackSync(current = launcher, observed = game, candidate = null)
        val second = nextStackSync(current = launcher, observed = game, candidate = first.candidate)
        assertEquals(game, second.accept)
    }

    @Test
    fun `отставший на одно чтение стек не мигает монитором`() {
        // Ребёнок ушёл домой, событие верно записало лаунчер, а стек ещё секунду показывает игру.
        val first = nextStackSync(current = launcher, observed = game, candidate = null)
        val second = nextStackSync(current = launcher, observed = launcher, candidate = first.candidate)
        assertEquals(null, first.accept)
        assertEquals(StackSyncStep(accept = null, candidate = null), second)
    }

    // --- shouldRepeatBlock ---

    @Test
    fun `ребёнок снова в заблокированном приложении — повторяем`() {
        assertTrue(shouldRepeatBlock(game, currentPackage = game, onScreenPackage = game, blockingUiVisible = false))
    }

    @Test
    fun `оверлей ещё на экране — не повторяем`() {
        assertFalse(shouldRepeatBlock(game, currentPackage = game, onScreenPackage = game, blockingUiVisible = true))
    }

    @Test
    fun `монитор на приложении, а стек показывает рабочий стол — не повторяем`() {
        // HiOS не прислал событие домашнего экрана: монитор врёт, повтор накрыл бы рабочий стол.
        assertFalse(shouldRepeatBlock(game, currentPackage = game, onScreenPackage = launcher, blockingUiVisible = false))
    }

    @Test
    fun `стек не прочитан — не повторяем`() {
        assertFalse(shouldRepeatBlock(game, currentPackage = game, onScreenPackage = null, blockingUiVisible = false))
    }

    @Test
    fun `монитор уже на другом пакете — не повторяем`() {
        assertFalse(shouldRepeatBlock(game, currentPackage = launcher, onScreenPackage = game, blockingUiVisible = false))
    }

    // --- backgroundVisibleCandidates / confirmedAcrossChecks (мини-окна) ---

    @Test
    fun `мини-окно игры поверх рабочего стола — кандидат на проверку`() {
        val candidates = backgroundVisibleCandidates(
            visible = setOf(launcher, game),
            activePackage = launcher,
            alwaysAllowed = setOf(launcher, "ru.homelab.kidguard"),
            bypassedPackage = null
        )
        assertEquals(setOf(game), candidates)
    }

    @Test
    fun `активное приложение и всегда разрешённые — не кандидаты`() {
        val candidates = backgroundVisibleCandidates(
            visible = setOf(launcher, game, "ru.homelab.kidguard"),
            activePackage = game,
            alwaysAllowed = setOf(launcher, "ru.homelab.kidguard"),
            bypassedPackage = null
        )
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `пакет, открытый родителем по PIN, — не кандидат`() {
        val candidates = backgroundVisibleCandidates(
            visible = setOf(launcher, game),
            activePackage = launcher,
            alwaysAllowed = setOf(launcher),
            bypassedPackage = game
        )
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `одна проверка не блокирует — окно могло уже закрыться`() {
        assertTrue(confirmedAcrossChecks(previous = emptySet(), current = setOf(game)).isEmpty())
    }

    @Test
    fun `две проверки подряд блокируют`() {
        assertEquals(setOf(game), confirmedAcrossChecks(previous = setOf(game), current = setOf(game)))
    }

    @Test
    fun `разные окна в двух проверках не подтверждают друг друга`() {
        assertTrue(confirmedAcrossChecks(previous = setOf("com.block.juggle"), current = setOf(game)).isEmpty())
    }
}
