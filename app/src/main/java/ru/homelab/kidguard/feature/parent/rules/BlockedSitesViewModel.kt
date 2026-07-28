package ru.homelab.kidguard.feature.parent.rules

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ru.homelab.kidguard.core.domain.model.BlockedSite
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.usecase.DomainNormalizer
import javax.inject.Inject

/** Экран «Запрет сайтов» (веха 4.1.2): DNS-чёрный список доменов + отдельный тумблер google-поиска. */
@HiltViewModel
class BlockedSitesViewModel @Inject constructor(
    private val policyRepository: PolicyRepository
) : ViewModel() {

    val blockGoogleSearch: StateFlow<Boolean> = policyRepository.blockGoogleSearch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val sites: StateFlow<List<BlockedSite>> = policyRepository.blockedSites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Разовый флаг: последняя попытка добавить домен была невалидной (сброс на новый ввод/успех). */
    private val _inputError = MutableStateFlow(false)
    val inputError: StateFlow<Boolean> = _inputError.asStateFlow()

    fun setBlockGoogleSearch(enabled: Boolean) {
        viewModelScope.launch { policyRepository.setBlockGoogleSearch(enabled) }
    }

    /** Нормализует ввод и добавляет домен в список; при невалидном вводе выставляет [inputError]. */
    fun addSite(rawInput: String) {
        val domain = DomainNormalizer.normalize(rawInput)
        if (domain == null) {
            _inputError.value = true
            return
        }
        _inputError.value = false
        viewModelScope.launch { policyRepository.addBlockedSite(domain) }
    }

    /** Сбрасывает состояние ошибки ввода (например, при изменении текста в поле). */
    fun clearInputError() {
        _inputError.value = false
    }

    fun setSiteEnabled(domain: String, enabled: Boolean) {
        viewModelScope.launch { policyRepository.setSiteEnabled(domain, enabled) }
    }

    fun removeSite(domain: String) {
        viewModelScope.launch { policyRepository.removeBlockedSite(domain) }
    }
}
