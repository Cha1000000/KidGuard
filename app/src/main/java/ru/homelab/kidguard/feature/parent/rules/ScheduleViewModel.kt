package ru.homelab.kidguard.feature.parent.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.EmergencyContact
import ru.homelab.kidguard.core.domain.model.ScheduleKind
import ru.homelab.kidguard.core.domain.model.ScheduleRules
import ru.homelab.kidguard.core.domain.model.TimeWindow
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import java.time.DayOfWeek
import javax.inject.Inject

/** Экран «Расписание»: два независимых расписания блокировки («Время учёбы», «Время сна») + список
 *  контактов для экстренного звонка с ночного замка. */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val policyRepository: PolicyRepository
) : ViewModel() {

    val studySchedule: StateFlow<ScheduleRules> = policyRepository.studySchedule
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleRules.EMPTY)

    val sleepSchedule: StateFlow<ScheduleRules> = policyRepository.sleepSchedule
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleRules.EMPTY)

    val emergencyContacts: StateFlow<List<EmergencyContact>> = policyRepository.emergencyContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** PIN задан у родителя — без него включать «Время сна» нельзя: замок будет нечем снять. */
    val pinIsSet: StateFlow<Boolean> = policyRepository.pinProtection
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Сохранить окно блокировки на конкретный день (window = null убирает окно). */
    fun setWindow(kind: ScheduleKind, day: DayOfWeek, window: TimeWindow?) {
        viewModelScope.launch { policyRepository.setScheduleWindow(kind, day, window) }
    }

    /** Сохранить одно и то же окно на все 7 дней недели («Применить ко всем дням»). */
    fun setWindowForAllDays(kind: ScheduleKind, window: TimeWindow?) {
        viewModelScope.launch {
            DayOfWeek.entries.forEach { policyRepository.setScheduleWindow(kind, it, window) }
        }
    }

    /** Включить/выключить расписание целиком, не трогая уже заданные часы дней. */
    fun setEnabled(kind: ScheduleKind, enabled: Boolean) {
        viewModelScope.launch { policyRepository.setScheduleEnabled(kind, enabled) }
    }

    /** Стереть окна всех 7 дней расписания [kind] («Сбросить время учёбы/сна»). Тумблер не трогаем. */
    fun resetSchedule(kind: ScheduleKind) {
        viewModelScope.launch {
            DayOfWeek.entries.forEach { policyRepository.setScheduleWindow(kind, it, null) }
        }
    }

    /** Добавить/переписать контакт для ночного звонка (upsert по номеру — см. репозиторий). */
    fun addEmergencyContact(contact: EmergencyContact) {
        viewModelScope.launch { policyRepository.addEmergencyContact(contact) }
    }

    /** Исправить контакт: [oldPhone] — номер до правки (он же ключ записи). */
    fun updateEmergencyContact(oldPhone: String, contact: EmergencyContact) {
        viewModelScope.launch { policyRepository.updateEmergencyContact(oldPhone, contact) }
    }

    fun removeEmergencyContact(phone: String) {
        viewModelScope.launch { policyRepository.removeEmergencyContact(phone) }
    }
}
