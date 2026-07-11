package ru.homelab.kidguard.feature.parent.children

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.domain.repository.ChildRepository
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.SyncRepository
import timber.log.Timber
import javax.inject.Inject

private const val TAG = "ChildrenViewModel"

data class ChildrenUiState(
    val loading: Boolean = true,
    val children: List<Child> = emptyList(),
    val loadError: Boolean = false
)

/** Результат приглашения второго родителя (для сообщения пользователю). */
enum class CoParentResult { LINKED, PENDING, ERROR }

@HiltViewModel
class ChildrenViewModel @Inject constructor(
    private val childRepository: ChildRepository,
    private val syncRepository: SyncRepository,
    private val policyRepository: PolicyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChildrenUiState())
    val uiState: StateFlow<ChildrenUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(loading = true, loadError = false) }
        viewModelScope.launch {
            childRepository.listChildren().fold(
                onSuccess = { list -> _uiState.update { it.copy(loading = false, children = list) } },
                onFailure = { _uiState.update { it.copy(loading = false, loadError = true) } }
            )
        }
    }

    /** Создать ребёнка; при успехе возвращает pairing-код через [onCode] и обновляет список. */
    fun createChild(name: String, avatar: Int, onCode: (String) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            childRepository.createChild(name.trim(), avatar).fold(
                onSuccess = {
                    refresh()
                    onCode(it.code)
                },
                onFailure = { onError() }
            )
        }
    }

    /** Перевыпустить код ребёнку (для повторного показа/отзыва старого). */
    fun regenerateCode(childId: Int, onCode: (String) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            childRepository.regeneratePairCode(childId).fold(
                onSuccess = { onCode(it) },
                onFailure = { onError() }
            )
        }
    }

    fun inviteCoParent(childId: Int, email: String, onResult: (CoParentResult) -> Unit) {
        viewModelScope.launch {
            childRepository.inviteCoParent(childId, email.trim()).fold(
                onSuccess = { linked -> onResult(if (linked) CoParentResult.LINKED else CoParentResult.PENDING) },
                onFailure = { onResult(CoParentResult.ERROR) }
            )
        }
    }

    /** Редактировать профиль ребёнка (имя + аватар); при успехе обновляет список. */
    fun updateChild(childId: Int, name: String, avatar: Int, onDone: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            childRepository.updateChild(childId, name.trim(), avatar).fold(
                onSuccess = {
                    refresh()
                    onDone()
                },
                onFailure = { onError() }
            )
        }
    }

    /**
     * Удалить ребёнка. Если это был активный ребёнок — переключиться на первого из оставшихся,
     * а если детей не осталось — очистить локальный кэш правил (иначе он останется от удалённого
     * ребёнка). Ошибка переключения/очистки не критична — просто логируем, список всё равно
     * обновится.
     */
    fun deleteChild(childId: Int, onDone: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val wasActive = syncRepository.activeChildId.first() == childId

            childRepository.deleteChild(childId).fold(
                onSuccess = {
                    if (wasActive) {
                        val remaining = _uiState.value.children.filter { it.id != childId }
                        val next = remaining.firstOrNull()
                        if (next != null) {
                            syncRepository.switchActiveChild(next.id)
                                .onFailure { Timber.tag(TAG).w(it, "switch_active_child_after_delete_failed") }
                        } else {
                            policyRepository.replaceAll(emptyMap(), emptyMap(), emptySet())
                        }
                    }
                    refresh()
                    onDone()
                },
                onFailure = { onError() }
            )
        }
    }
}
