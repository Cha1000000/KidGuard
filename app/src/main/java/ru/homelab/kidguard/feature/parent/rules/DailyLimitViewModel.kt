package ru.homelab.kidguard.feature.parent.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.DailyLimits
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.CurrentDateProvider
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class DailyLimitViewModel @Inject constructor(
    private val policyRepository: PolicyRepository,
    private val bonusRepository: BonusRepository,
    private val currentDateProvider: CurrentDateProvider
) : ViewModel() {

    val dailyLimits: StateFlow<DailyLimits> = policyRepository.dailyLimits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyLimits.EMPTY)

    /** Активное «Дополнительное время» телефона на сегодня (минут). */
    val phoneBonusMinutes: StateFlow<Int> = flow {
        emitAll(bonusRepository.phoneBonusMinutes(currentDateProvider.today()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Сохранить лимит на день (minutes = null — без лимита). */
    fun setLimit(day: DayOfWeek, minutes: Int?) {
        viewModelScope.launch { policyRepository.setDailyLimit(day, minutes) }
    }

    /** Сохранить один и тот же лимит на все дни недели. */
    fun setLimitForAllDays(minutes: Int?) {
        viewModelScope.launch {
            DayOfWeek.entries.forEach { policyRepository.setDailyLimit(it, minutes) }
        }
    }

    /** Добавить телефону дополнительное время на сегодня (суммируется). */
    fun addPhoneBonus(minutes: Int) {
        viewModelScope.launch { bonusRepository.addBonus(currentDateProvider.today(), null, minutes) }
    }

    /** Отменить дополнительное время телефона на сегодня. */
    fun clearPhoneBonus() {
        viewModelScope.launch { bonusRepository.clearBonus(currentDateProvider.today(), null) }
    }
}
