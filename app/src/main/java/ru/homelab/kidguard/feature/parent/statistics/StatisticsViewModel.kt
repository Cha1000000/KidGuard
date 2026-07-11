package ru.homelab.kidguard.feature.parent.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.domain.repository.ChildRepository
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.SyncRepository
import java.time.LocalDate
import javax.inject.Inject

/** Столбик диаграммы: день + суммарные секунды. */
data class DayUsage(val date: LocalDate, val seconds: Int)

/** Строка «по приложениям»: пакет, секунды и доля от суммарного времени за день. */
data class AppUsage(val packageName: String, val seconds: Int, val share: Float) {
    /** Читаемое имя из package: com.google.android.youtube -> youtube. */
    val label: String get() = packageName.substringAfterLast('.').ifEmpty { packageName }
}

data class StatisticsUiState(
    val loading: Boolean = true,
    val child: Child? = null,
    val todaySeconds: Int = 0,
    /** Лимит на сегодня из локальной политики (минут); null — лимита нет. */
    val todayLimitMinutes: Int? = null,
    val week: List<DayUsage> = emptyList(),
    val apps: List<AppUsage> = emptyList(),
    val noChildren: Boolean = false,
    val error: Boolean = false
) {
    val hasData: Boolean get() = week.any { it.seconds > 0 }
}

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val childRepository: ChildRepository,
    private val policyRepository: PolicyRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        // Переключение активного ребёнка (чип, веха 4.5) — сразу перегружаем статистику.
        viewModelScope.launch {
            syncRepository.activeChildId.drop(1).collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val children = childRepository.listChildren().getOrNull()
            if (children != null && children.isEmpty()) {
                _uiState.value = StatisticsUiState(loading = false, noChildren = true)
                return@launch
            }
            val activeId = syncRepository.activeChildId.first()
            val child = children?.firstOrNull { it.id == activeId } ?: children?.firstOrNull()
            if (child == null) {
                _uiState.value = StatisticsUiState(loading = false, error = true)
                return@launch
            }

            val entries = childRepository.getChildUsage(child.id, days = DAYS).getOrElse {
                _uiState.value = StatisticsUiState(loading = false, child = child, error = true)
                return@launch
            }

            val today = LocalDate.now()
            val totalsByDate = entries.filter { it.isTotal }.associate { it.date to it.seconds }
            val week = (DAYS - 1 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong())
                DayUsage(date, totalsByDate[date] ?: 0)
            }

            val todaySeconds = totalsByDate[today] ?: 0
            val apps = entries
                .filter { !it.isTotal && it.date == today && it.seconds > 0 }
                .sortedByDescending { it.seconds }
                .map { entry ->
                    AppUsage(
                        packageName = entry.packageName,
                        seconds = entry.seconds,
                        share = if (todaySeconds > 0) entry.seconds.toFloat() / todaySeconds else 0f
                    )
                }

            val limit = policyRepository.dailyLimits.first().limitFor(today.dayOfWeek)

            _uiState.value = StatisticsUiState(
                loading = false,
                child = child,
                todaySeconds = todaySeconds,
                todayLimitMinutes = limit,
                week = week,
                apps = apps
            )
        }
    }

    private companion object {
        const val DAYS = 7
    }
}
