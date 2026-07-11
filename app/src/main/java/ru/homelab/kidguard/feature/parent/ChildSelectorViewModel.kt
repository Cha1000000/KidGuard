package ru.homelab.kidguard.feature.parent

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
import ru.homelab.kidguard.core.domain.repository.SyncRepository
import javax.inject.Inject

data class ChildSelectorUiState(
    val children: List<Child> = emptyList(),
    val active: Child? = null,
    /** Идёт переключение (pull политики нового ребёнка) — чип временно не активен. */
    val switching: Boolean = false,
    /** Переключить не удалось (нет сети) — показать сообщение и остаться на текущем. */
    val switchError: Boolean = false
) {
    /** Меню выбора имеет смысл только при двух и более детях. */
    val selectable: Boolean get() = children.size > 1 && !switching
}

/**
 * Селектор активного ребёнка (веха 4.5): чип на вкладках «Правила» и «Статистика».
 * Сам выбор глобальный — хранится в SyncRepository, поэтому инстансы VM на разных
 * вкладках всегда показывают одного и того же ребёнка.
 */
@HiltViewModel
class ChildSelectorViewModel @Inject constructor(
    private val childRepository: ChildRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChildSelectorUiState())
    val uiState: StateFlow<ChildSelectorUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val children = childRepository.listChildren().getOrNull() ?: return@launch
            val activeId = syncRepository.activeChildId.first()
            _uiState.update {
                it.copy(
                    children = children,
                    active = children.firstOrNull { child -> child.id == activeId }
                        ?: children.firstOrNull()
                )
            }
        }
    }

    fun select(child: Child) {
        if (child.id == _uiState.value.active?.id) return
        viewModelScope.launch {
            _uiState.update { it.copy(switching = true, switchError = false) }
            syncRepository.switchActiveChild(child.id)
                .onSuccess { _uiState.update { it.copy(active = child, switching = false) } }
                .onFailure { _uiState.update { it.copy(switching = false, switchError = true) } }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(switchError = false) }
    }
}
