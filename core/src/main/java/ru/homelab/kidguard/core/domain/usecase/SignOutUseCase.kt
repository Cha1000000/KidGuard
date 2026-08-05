package ru.homelab.kidguard.core.domain.usecase

import ru.homelab.kidguard.core.domain.model.PolicySnapshot
import ru.homelab.kidguard.core.domain.repository.AuthRepository
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.SettingsRepository
import ru.homelab.kidguard.core.domain.repository.SyncRepository
import javax.inject.Inject

/**
 * Единая точка выхода родителя из аккаунта. Затрагивает четыре независимых репозитория, поэтому
 * не может жить методом одного из них — координация вынесена в use case.
 *
 * Порядок шагов важен: [SettingsRepository.resetSetup] — последний, потому что именно он
 * возвращает приложение к экрану выбора роли. Если сбросить его раньше остальных, UI успеет
 * перерисоваться на выбор роли, пока сессия/кэш/синк ещё не очищены.
 */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val policyRepository: PolicyRepository,
    private val settingsRepository: SettingsRepository
) {

    suspend operator fun invoke() {
        authRepository.clearParentSession()
        syncRepository.clearLocalSyncState()
        // Стереть локальный кэш правил — приём уже применяется при удалении последнего ребёнка
        // (см. ChildrenViewModel.deleteChild), здесь по той же причине: кэш не должен пережить
        // выход и достаться следующему вошедшему на этом устройстве родителю.
        policyRepository.replaceAll(PolicySnapshot())
        settingsRepository.resetSetup()
    }
}
