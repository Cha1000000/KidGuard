package ru.homelab.kidguard.core.domain.usecase

import ru.homelab.kidguard.core.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Удаление аккаунта родителя. Сначала бьём на сервер, и только при успехе чистим локальное
 * состояние (переиспользуя [SignOutUseCase] — тот же набор шагов, что и обычный выход).
 *
 * Локальный сброс НЕЛЬЗЯ выполнять при ошибке сервера: если стереть сессию, а `DELETE /me`
 * упал (нет сети, сервер недоступен), человек увидит экран выбора роли и решит, что аккаунт
 * удалён — а он всё ещё существует на сервере со всеми детьми и данными. Поэтому при ошибке
 * ничего локально не трогаем и возвращаем её наружу, чтобы UI показал повтор.
 */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val signOutUseCase: SignOutUseCase
) {

    suspend operator fun invoke(): Result<Unit> {
        val deleteResult = authRepository.deleteAccount()
        if (deleteResult.isFailure) return deleteResult

        signOutUseCase()
        return Result.success(Unit)
    }
}
