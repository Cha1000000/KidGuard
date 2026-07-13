package ru.homelab.kidguard.feature.parent.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.homelab.kidguard.core.domain.model.PinProtection
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.security.PinHasher
import javax.inject.Inject

/**
 * Установка/смена/снятие родительского PIN (веха 6.1). Сам мастер ввода (шаги «придумайте» →
 * «повторите», точки, клавиатура) — локальное UI-состояние экрана; здесь только источник истины
 * «задан ли PIN» и финальные действия хеширования/сохранения.
 */
@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val policyRepository: PolicyRepository
) : ViewModel() {

    /**
     * `null` — PIN не задан. До первого значения из Room StateFlow тоже отдаёт `null`, что
     * совпадает по смыслу с «PIN не задан» — заметного мигания UI на старте нет.
     */
    val pinProtection: StateFlow<PinProtection?> = policyRepository.pinProtection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Хеширует PIN с новой случайной солью (PBKDF2, см. [PinHasher]) и сохраняет в policy. */
    fun setPin(pin: String) {
        viewModelScope.launch {
            val salt = PinHasher.generateSalt()
            val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin, salt) }
            policyRepository.setPin(hash, salt)
        }
    }

    /** Снять PIN-защиту полностью. */
    fun clearPin() {
        viewModelScope.launch { policyRepository.clearPin() }
    }
}
