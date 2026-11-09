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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.PinProtection
import ru.homelab.kidguard.core.domain.model.ScheduleKind

/**
 * Контракт экрана «Расписание»: без родительского PIN экраны-замки (учёба/сон) не включаются —
 * ни в UI (тумблер), ни в ViewModel (defense-in-depth). Выключение разрешено всегда.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    private val pin = PinProtection(hash = "hash", salt = "salt")

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `без PIN включение STUDY не пишет расписание`() = runTest {
        val repository = FakePolicyRepository(initialPinProtection = null)
        val viewModel = ScheduleViewModel(repository)

        viewModel.setEnabled(ScheduleKind.STUDY, true)
        advanceUntilIdle()

        assertTrue(repository.enabledWrites.isEmpty())
    }

    @Test
    fun `без PIN включение SLEEP не пишет расписание`() = runTest {
        val repository = FakePolicyRepository(initialPinProtection = null)
        val viewModel = ScheduleViewModel(repository)

        viewModel.setEnabled(ScheduleKind.SLEEP, true)
        advanceUntilIdle()

        assertTrue(repository.enabledWrites.isEmpty())
    }

    @Test
    fun `с PIN включение STUDY пишет расписание`() = runTest {
        val repository = FakePolicyRepository(initialPinProtection = pin)
        val viewModel = ScheduleViewModel(repository)

        viewModel.setEnabled(ScheduleKind.STUDY, true)
        advanceUntilIdle()

        assertEquals(listOf(ScheduleKind.STUDY to true), repository.enabledWrites)
    }

    @Test
    fun `без PIN выключение разрешено`() = runTest {
        val repository = FakePolicyRepository(initialPinProtection = null)
        val viewModel = ScheduleViewModel(repository)

        viewModel.setEnabled(ScheduleKind.STUDY, false)
        advanceUntilIdle()

        assertEquals(listOf(ScheduleKind.STUDY to false), repository.enabledWrites)
    }
}
