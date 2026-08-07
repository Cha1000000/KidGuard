package ru.homelab.kidguard.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.LimitState

/**
 * Таблица «куда уходит тик». Комбинаций восемь, и перепутать их легко — а цена ошибки заметная:
 * тик, ушедший не в тот счётчик, либо снова даёт бонусу гаситься перерасходом, либо (наоборот)
 * позволяет расходовать бюджет после того, как он исчерпан.
 */
class UsageTickTargetsTest {

    private val remaining = LimitState.Remaining(minutesLeft = 10)

    private fun targets(
        counts: Boolean = true,
        daily: LimitState = remaining,
        app: LimitState = LimitState.NoLimit
    ) = usageTickTargets(counts, daily, app)

    @Test
    fun `обычное приложение при живом лимите расходует бюджет`() {
        val result = targets()
        assertEquals(UsageBucket.BUDGET, result.dailyBucket)
        assertEquals(UsageBucket.BUDGET, result.appBucket)
    }

    @Test
    fun `после исчерпания дневного лимита время идёт в перерасход`() {
        // Ровно тот случай, ради которого счётчики и разведены: ребёнок смахнул оверлей и
        // продолжил пользоваться телефоном. Бюджетный счётчик расти уже не должен.
        assertEquals(UsageBucket.OVERRUN, targets(daily = LimitState.Expired).dailyBucket)
    }

    @Test
    fun `Всегда доступные не пишут в дневные счётчики вовсе`() {
        assertNull(targets(counts = false).dailyBucket)
        assertNull(targets(counts = false, daily = LimitState.Expired).dailyBucket)
    }

    @Test
    fun `время самого приложения учитывается и у Всегда доступных`() {
        // Пер-app счётчик — это статистика: «сколько ребёнок пробыл в приложении» нужно знать и
        // для тех приложений, которые дневной лимит не расходуют.
        assertEquals(UsageBucket.BUDGET, targets(counts = false).appBucket)
    }

    @Test
    fun `исчерпанный личный лимит уводит время приложения в перерасход`() {
        val result = targets(app = LimitState.Expired)
        assertEquals(UsageBucket.OVERRUN, result.appBucket)
        // Дневной лимит ещё живой — его бюджет продолжает расходоваться.
        assertEquals(UsageBucket.BUDGET, result.dailyBucket)
    }

    @Test
    fun `лимиты решаются независимо`() {
        val result = targets(daily = LimitState.Expired, app = remaining)
        assertEquals(UsageBucket.OVERRUN, result.dailyBucket)
        assertEquals(UsageBucket.BUDGET, result.appBucket)
    }

    @Test
    fun `оба лимита исчерпаны - оба счётчика перерасходные`() {
        val result = targets(daily = LimitState.Expired, app = LimitState.Expired)
        assertEquals(UsageBucket.OVERRUN, result.dailyBucket)
        assertEquals(UsageBucket.OVERRUN, result.appBucket)
    }

    @Test
    fun `без лимитов всё идёт в обычные счётчики`() {
        val result = targets(daily = LimitState.NoLimit, app = LimitState.NoLimit)
        assertEquals(UsageBucket.BUDGET, result.dailyBucket)
        assertEquals(UsageBucket.BUDGET, result.appBucket)
    }
}
