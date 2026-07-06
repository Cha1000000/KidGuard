package ru.homelab.kidguard.feature.parent.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class DailyLimitViewModel @Inject constructor(
    private val policyRepository: PolicyRepository
) : ViewModel() {

    val dailyLimits: StateFlow<DailyLimits> = policyRepository.dailyLimits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyLimits.EMPTY)

    /** Сохранить лимит на день (minutes = null — без лимита). */
    fun setLimit(day: DayOfWeek, minutes: Int?) {
        viewModelScope.launch { policyRepository.setDailyLimit(day, minutes) }
    }
}
