package ru.homelab.kidguard.feature.parent.children

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.domain.repository.ChildRepository
import javax.inject.Inject

data class ChildrenUiState(
    val loading: Boolean = true,
    val children: List<Child> = emptyList(),
    val loadError: Boolean = false
)

/** Результат приглашения второго родителя (для сообщения пользователю). */
enum class CoParentResult { LINKED, PENDING, ERROR }

@HiltViewModel
class ChildrenViewModel @Inject constructor(
    private val childRepository: ChildRepository
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
}
