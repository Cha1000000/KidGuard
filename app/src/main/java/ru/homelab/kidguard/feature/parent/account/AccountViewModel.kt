package ru.homelab.kidguard.feature.parent.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.repository.AuthRepository
import ru.homelab.kidguard.core.domain.repository.ChildRepository
import ru.homelab.kidguard.core.domain.usecase.DeleteAccountUseCase
import ru.homelab.kidguard.core.domain.usecase.SignOutUseCase
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "AccountViewModel"

/**
 * Ребёнок в списке последствий удаления аккаунта.
 *
 * [keptByCoParent] — ребёнком управляет ещё один родитель, поэтому при удалении учётной записи он
 * НЕ будет удалён: сервер только разорвёт нашу связь с ним (accountService.deleteAccount).
 * Диалог обязан показать это отдельно — иначе он пообещает удалить то, что на самом деле
 * останется второму родителю.
 */
data class AccountChildUi(val name: String, val keptByCoParent: Boolean)

data class AccountUiState(
    val email: String,
    val displayName: String,
    val children: List<AccountChildUi>,
    val busy: Boolean = false,
    val errorRes: Int? = null
)

/** Экран «Аккаунт»: профиль вошедшего родителя, выход и необратимое удаление аккаунта. */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val childRepository: ChildRepository,
    private val signOutUseCase: SignOutUseCase,
    private val deleteAccountUseCase: DeleteAccountUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountUiState?>(null)
    val uiState: StateFlow<AccountUiState?> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.parentProfile.collect { user ->
                // null — сессии нет; экран «Аккаунт» открывается только у вошедшего родителя,
                // но на случай гонки (сессия истекла прямо во время открытия) просто не обновляем
                // состояние — onSignedOut уже должен увести пользователя с экрана.
                if (user == null) return@collect

                val children = childRepository.listChildren()
                    .onFailure { Timber.tag(TAG).w(it, "list_children_failed") }
                    .getOrDefault(emptyList())

                _uiState.update { previous ->
                    AccountUiState(
                        email = user.email,
                        displayName = user.displayName?.takeIf { it.isNotBlank() } ?: user.email,
                        children = children.map {
                            AccountChildUi(name = it.name, keptByCoParent = it.hasCoParent)
                        },
                        busy = previous?.busy ?: false,
                        errorRes = previous?.errorRes
                    )
                }
            }
        }
    }

    /** Выйти из аккаунта: локальная операция, данные на сервере не трогает. */
    fun signOut(onDone: () -> Unit) {
        _uiState.update { it?.copy(busy = true, errorRes = null) }
        viewModelScope.launch {
            try {
                signOutUseCase()
                onDone()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "sign_out_failed")
                _uiState.update { it?.copy(busy = false, errorRes = R.string.account_signout_error) }
            }
        }
    }

    /**
     * Удалить аккаунт. Сначала — запрос на сервер; только при успехе локальные данные стираются
     * (см. контракт [DeleteAccountUseCase]). При ошибке пользователь остаётся в аккаунте — иначе
     * он решил бы, что аккаунт удалён, а он остался.
     */
    fun deleteAccount(onDone: () -> Unit) {
        _uiState.update { it?.copy(busy = true, errorRes = null) }
        viewModelScope.launch {
            deleteAccountUseCase().fold(
                onSuccess = { onDone() },
                onFailure = { error ->
                    Timber.tag(TAG).w(error, "delete_account_failed")
                    _uiState.update { it?.copy(busy = false, errorRes = R.string.account_delete_error) }
                }
            )
        }
    }

    fun dismissError() {
        _uiState.update { it?.copy(errorRes = null) }
    }
}
