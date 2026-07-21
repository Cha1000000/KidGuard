package ru.homelab.kidguard.feature.parent.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.homelab.kidguard.core.domain.model.BreakMode
import ru.homelab.kidguard.core.domain.model.BreakRules
import ru.homelab.kidguard.core.domain.model.breaksApplyToday
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import java.time.DayOfWeek
import javax.inject.Inject

/**
 * Состояние экрана «Перерывы»: сами настройки [BreakRules] плюс [activeDays] — дни недели, где
 * перерывы реально сработают (лимит на день не задан или больше 3 часов, см. [breaksApplyToday]).
 */
data class BreaksUiState(
    val rules: BreakRules = BreakRules.EMPTY,
    val activeDays: List<DayOfWeek> = emptyList()
) {

    /** Часы перерывов по возрастанию — для стабильного порядка чипов в списке. */
    val sortedHours: List<Int> get() = rules.hours.sorted()

    /**
     * Тумблер «Включить перерывы» можно включить, только если заданы длительность и (в
     * зависимости от режима) интервал или хотя бы один час — то же правило, что
     * [BreakRules.isConfigured], но без самого enabled: иначе тумблер нельзя было бы включить
     * в первый раз.
     */
    val canEnable: Boolean
        get() = rules.durationMinutes > 0 && when (rules.mode) {
            BreakMode.INTERVAL -> rules.intervalMinutes > 0
            BreakMode.HOURS -> rules.hours.isNotEmpty()
        }
}

/** Экран «Перерывы»: настройка принудительных перерывов (интервал/часы, длительность, текст). */
@HiltViewModel
class BreaksViewModel @Inject constructor(
    private val policyRepository: PolicyRepository
) : ViewModel() {

    val uiState: StateFlow<BreaksUiState> = combine(
        policyRepository.breakRules,
        policyRepository.dailyLimits
    ) { rules, dailyLimits ->
        BreaksUiState(
            rules = rules,
            activeDays = DayOfWeek.entries.filter { day ->
                breaksApplyToday(dailyLimits.limitFor(day))
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BreaksUiState())

    /**
     * Включить/выключить перерывы. Родитель всегда может выключить — включить получится только
     * когда [BreaksUiState.canEnable] истинно (проверяет вызывающая сторона, экран блокирует тап).
     */
    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    /** Переключить режим — интервал залипания или назначенные часы (взаимоисключающе). */
    fun setMode(mode: BreakMode) = update { it.copy(mode = mode) }

    /** Интервал залипания в минутах (режим INTERVAL); 0 = не задан. */
    fun setInterval(minutes: Int) = update { it.copy(intervalMinutes = minutes) }

    /** Длительность самого перерыва в минутах; 0 = не задана. */
    fun setDuration(minutes: Int) = update { it.copy(durationMinutes = minutes) }

    /** Добавить час перерыва (режим HOURS) — минута от полуночи, напр. 15:00 = 900. */
    fun addHour(minuteOfDay: Int) = update { it.copy(hours = it.hours + minuteOfDay) }

    fun removeHour(minuteOfDay: Int) = update { it.copy(hours = it.hours - minuteOfDay) }

    /** Текст, который увидит ребёнок на замке перерыва; пусто — покажется фраза-шаблон. */
    fun setMessage(message: String) = update { it.copy(message = message) }

    /** Общий сброс: выключает перерывы, обнуляет интервал, часы, длительность и текст. */
    fun reset() {
        viewModelScope.launch { policyRepository.resetBreaks() }
    }

    private fun update(transform: (BreakRules) -> BreakRules) {
        viewModelScope.launch {
            policyRepository.setBreakRules(transform(uiState.value.rules))
        }
    }
}
