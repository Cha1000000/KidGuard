package ru.homelab.kidguard.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.Role
import ru.homelab.kidguard.core.domain.repository.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /**
     * Зафиксировать выбранную роль (единоразово) и отметить настройку завершённой.
     * [onSaved] вызывается после сохранения — для перехода в граф выбранной роли.
     */
    fun chooseRole(role: Role, onSaved: (Role) -> Unit) {
        viewModelScope.launch {
            settingsRepository.setRole(role)
            settingsRepository.setSetupCompleted(true)
            onSaved(role)
        }
    }
}
