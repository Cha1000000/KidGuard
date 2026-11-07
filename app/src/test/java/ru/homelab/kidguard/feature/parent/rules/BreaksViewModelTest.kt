package ru.homelab.kidguard.feature.parent.rules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.BreakMode
import ru.homelab.kidguard.core.domain.model.BreakRules

/**
 * Контракт экрана «Перерывы» против гонок быстрых правок (план «исправление перерывов», этап 7):
 * каждое действие пользователя применяется к АКТУАЛЬНОМУ сохранённому состоянию, а не к
 * отстающему снимку UI; порядок действий сохраняется; сброс не отменяется более старой записью.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BreaksViewModelTest {

    private val savedRules = BreakRules(
        enabled = true,
        mode = BreakMode.INTERVAL,
        intervalMinutes = 15,
        hours = emptySet(),
        durationMinutes = 10,
        message = "Разомнись"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `серия быстрых slider updates сохраняет итоговое значение и не затирает остальные поля`() = runTest {
        val repository = FakePolicyRepository(initialRules = savedRules)
        val viewModel = BreaksViewModel(repository)
        viewModel.setInterval(20)
        viewModel.setDuration(5)
        advanceUntilIdle()

        val saved = repository.saved
        assertEquals(20, saved.intervalMinutes)
        assertEquals(5, saved.durationMinutes)
        assertEquals(BreakMode.INTERVAL, saved.mode)
        assertEquals("Разомнись", saved.message)
    }

    @Test
    fun `быстрый ввод сообщения не откатывается на старые snapshots`() = runTest {
        val repository = FakePolicyRepository(initialRules = savedRules)
        val viewModel = BreaksViewModel(repository)
        viewModel.setInterval(30)
        viewModel.setMessage("Разомнись, Алёна!")
        viewModel.setMessage("Разомнись, Алёна! И выпей воды.")
        advanceUntilIdle()

        val saved = repository.saved
        assertEquals("Разомнись, Алёна! И выпей воды.", saved.message)
        assertEquals(30, saved.intervalMinutes)
        assertEquals(10, saved.durationMinutes)
    }

    @Test
    fun `mode + interval + duration применяются без потери полей`() = runTest {
        val repository = FakePolicyRepository(initialRules = BreakRules.EMPTY)
        val viewModel = BreaksViewModel(repository)
        viewModel.setMode(BreakMode.INTERVAL)
        viewModel.setInterval(45)
        viewModel.setDuration(7)
        advanceUntilIdle()

        val saved = repository.saved
        assertEquals(BreakMode.INTERVAL, saved.mode)
        assertEquals(45, saved.intervalMinutes)
        assertEquals(7, saved.durationMinutes)
    }

    @Test
    fun `add и remove нескольких часов подряд не теряет элементы`() = runTest {
        val repository = FakePolicyRepository(initialRules = BreakRules.EMPTY)
        val viewModel = BreaksViewModel(repository)
        viewModel.addHour(900)
        viewModel.addHour(1_020)
        viewModel.removeHour(900)
        advanceUntilIdle()

        assertEquals(setOf(1_020), repository.saved.hours)
    }

    @Test
    fun `reset в очереди изменений не отменяется более старой записью`() = runTest {
        // Запись интервала приезжает медленно: если она ляжет ПОСЛЕ сброса, сброс будет затёрт.
        val repository = FakePolicyRepository(initialRules = savedRules, rulesWriteDelayMs = 50)
        val viewModel = BreaksViewModel(repository)
        viewModel.setInterval(45)
        viewModel.reset()
        advanceUntilIdle()

        assertEquals(BreakRules.EMPTY, repository.saved)
    }

    @Test
    fun `действие сразу после создания ViewModel использует сохранённые правила, а не EMPTY`() = runTest {
        // Репозититорий отдаёт первое значение не мгновенно: до загрузки UI-снимок пуст, и
        // быстрый тап обязан подождать фактическое состояние, а не затереть настройки EMPTY.
        val repository = FakePolicyRepository(initialRules = savedRules, rulesEmitDelayMs = 100)
        val viewModel = BreaksViewModel(repository)
        viewModel.setInterval(25)
        advanceUntilIdle()

        val saved = repository.saved
        assertEquals(25, saved.intervalMinutes)
        assertEquals("Разомнись", saved.message)
        assertEquals(10, saved.durationMinutes)
    }
}
