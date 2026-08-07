package ru.homelab.kidguard.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.homelab.kidguard.core.domain.FakePolicyRepository
import ru.homelab.kidguard.core.domain.model.AuthUser
import ru.homelab.kidguard.core.domain.model.PairedChild
import ru.homelab.kidguard.core.domain.model.Role
import ru.homelab.kidguard.core.domain.repository.AuthRepository
import ru.homelab.kidguard.core.domain.repository.SettingsRepository
import ru.homelab.kidguard.core.domain.repository.SyncRepository

/**
 * Тесты удаления аккаунта. Проверяются вживую на устройстве они плохо: удаление необратимо и
 * сносит реальную связку родителя с детьми, поэтому поведение закрыто здесь.
 *
 * Главное, что защищают эти тесты, — порядок «сначала сервер, потом локальная очистка». Если
 * очистить локальное состояние при упавшем запросе, человек увидит экран выбора роли и решит,
 * что аккаунт удалён, хотя тот жив на сервере вместе со всеми детьми.
 */
class DeleteAccountUseCaseTest {

    @Test
    fun `сервер удалил аккаунт - локальное состояние очищено`() = runTest {
        val auth = FakeAuthRepository(deleteResult = Result.success(Unit))
        val sync = FakeSyncRepository()
        val settings = FakeSettingsRepository()
        val policy = FakePolicyRepository()

        val result = useCase(auth, sync, settings, policy).invoke()

        assertTrue(result.isSuccess)
        assertTrue("сессия должна быть стёрта", auth.sessionCleared)
        assertTrue("состояние синхронизации должно быть стёрто", sync.cleared)
        assertTrue("первичная настройка должна быть сброшена", settings.setupReset)
    }

    @Test
    fun `сервер недоступен - локальное состояние НЕ трогаем`() = runTest {
        val error = IllegalStateException("нет сети")
        val auth = FakeAuthRepository(deleteResult = Result.failure(error))
        val sync = FakeSyncRepository()
        val settings = FakeSettingsRepository()
        val policy = FakePolicyRepository()

        val result = useCase(auth, sync, settings, policy).invoke()

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
        // Ключевая проверка: пользователь остаётся в аккаунте и может повторить попытку.
        assertFalse("сессия НЕ должна быть стёрта", auth.sessionCleared)
        assertFalse("состояние синхронизации НЕ должно быть стёрто", sync.cleared)
        assertFalse("первичная настройка НЕ должна сбрасываться", settings.setupReset)
    }

    @Test
    fun `обычный выход не обращается к серверу и чистит всё локальное`() = runTest {
        val auth = FakeAuthRepository(deleteResult = Result.success(Unit))
        val sync = FakeSyncRepository()
        val settings = FakeSettingsRepository()

        SignOutUseCase(auth, sync, FakePolicyRepository(), settings).invoke()

        assertFalse("выход не должен удалять аккаунт на сервере", auth.deleteCalled)
        assertTrue(auth.sessionCleared)
        assertTrue(sync.cleared)
        assertTrue(settings.setupReset)
    }

    private fun useCase(
        auth: AuthRepository,
        sync: SyncRepository,
        settings: SettingsRepository,
        policy: FakePolicyRepository
    ) = DeleteAccountUseCase(auth, SignOutUseCase(auth, sync, policy, settings))

    private class FakeAuthRepository(
        private val deleteResult: Result<Unit>
    ) : AuthRepository {

        var sessionCleared = false
            private set
        var deleteCalled = false
            private set

        override val hasValidSession: Flow<Boolean> = flowOf(true)
        override val hasPairedDevice: Flow<Boolean> = flowOf(false)
        override val childProfile: Flow<PairedChild?> = flowOf(null)
        override val parentProfile: Flow<AuthUser?> = flowOf(null)

        override suspend fun signInWithGoogleIdToken(googleIdToken: String): Result<AuthUser> =
            error("не используется в этих тестах")

        override suspend fun pairDeviceWithCode(code: String): Result<PairedChild> =
            error("не используется в этих тестах")

        override suspend fun setChildLocalAvatar(index: Int) = Unit
        override suspend fun clearChildLocalAvatar() = Unit

        override suspend fun clearParentSession() {
            sessionCleared = true
        }

        override suspend fun deleteAccount(): Result<Unit> {
            deleteCalled = true
            return deleteResult
        }
    }

    private class FakeSyncRepository : SyncRepository {

        var cleared = false
            private set

        override val activeChildId: Flow<Int?> = flowOf(null)
        override val childPaired: Flow<Int> = flowOf(0)

        override suspend fun parentSyncLoop() = Unit
        override suspend fun childSyncLoop() = Unit
        override suspend fun switchActiveChild(childId: Int): Result<Unit> = Result.success(Unit)

        override suspend fun clearLocalSyncState() {
            cleared = true
        }
    }

    private class FakeSettingsRepository : SettingsRepository {

        var setupReset = false
            private set

        override val role: Flow<Role?> = flowOf(Role.PARENT)
        override val setupCompleted: Flow<Boolean> = flowOf(true)
        override val controlEverConfigured: Flow<Boolean> = flowOf(false)

        override suspend fun setRole(role: Role) = Unit
        override suspend fun setSetupCompleted(completed: Boolean) = Unit
        override suspend fun markControlConfigured() = Unit

        override suspend fun resetSetup() {
            setupReset = true
        }
    }
}
