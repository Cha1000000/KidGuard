package ru.homelab.kidguard.feature.parent.statistics

import androidx.compose.ui.graphics.ImageBitmap
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
import ru.homelab.kidguard.core.domain.repository.BonusRepository
import ru.homelab.kidguard.core.domain.repository.ChildRepository
import ru.homelab.kidguard.core.domain.repository.PolicyRepository
import ru.homelab.kidguard.core.domain.repository.SyncRepository
import ru.homelab.kidguard.feature.parent.rules.ChildAppsProvider
import java.time.LocalDate
import javax.inject.Inject

/**
 * Столбик диаграммы: день, суммарные секунды и бюджет этого дня (лимит дня недели + выданный
 * в тот день бонус; null — лимита на день не было). Бюджет хранится по дням, а не один на график:
 * лимиты задаются по дням недели, а бонусы привязаны к конкретной дате.
 */
data class DayUsage(val date: LocalDate, val seconds: Int, val budgetMinutes: Int? = null)

/** Строка «по приложениям»: пакет, секунды, доля от суммарного времени за день и иконка. */
data class AppUsage(val packageName: String, val seconds: Int, val share: Float, val icon: ImageBitmap? = null) {
    /** Читаемое имя из package: com.google.android.youtube -> youtube. */
    val label: String get() = packageName.substringAfterLast('.').ifEmpty { packageName }
}

data class StatisticsUiState(
    val loading: Boolean = true,
    val child: Child? = null,
    /**
     * Всё экранное время за сегодня (сумма по приложениям). Именно оно показывается крупной
     * цифрой и от него считаются доли в списке «По приложениям».
     */
    val todayTotalSeconds: Int = 0,
    /**
     * Часть [todayTotalSeconds], расходующая дневной лимит: без «Всегда доступных», лаунчера и
     * самого KidGuard. С бюджетом сравнивается именно она — как и в enforcement.
     */
    val todaySeconds: Int = 0,
    /** Лимит на сегодня из локальной политики (минут); null — лимита нет. */
    val todayLimitMinutes: Int? = null,
    /** Выданное на сегодня «Дополнительное время» (минут); 0 — бонуса не было. */
    val todayBonusMinutes: Int = 0,
    val week: List<DayUsage> = emptyList(),
    val apps: List<AppUsage> = emptyList(),
    val noChildren: Boolean = false,
    val error: Boolean = false
) {
    val hasData: Boolean get() = week.any { it.seconds > 0 } || todayTotalSeconds > 0

    /** Время в приложениях, которые лимит не закрывает. Ноль — таких приложений сегодня не было. */
    val outsideLimitSeconds: Int get() = (todayTotalSeconds - todaySeconds).coerceAtLeast(0)
}

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val childRepository: ChildRepository,
    private val policyRepository: PolicyRepository,
    private val bonusRepository: BonusRepository,
    private val syncRepository: SyncRepository,
    private val childAppsProvider: ChildAppsProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        // Стартовую загрузку делает сам экран (LaunchedEffect при входе на вкладку) — он же
        // обновляет данные при каждом возврате. Дублировать её здесь значило бы слать два
        // одинаковых запроса подряд при первом открытии.
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
            val limits = policyRepository.dailyLimits.first()
            // Бонусы телефона за все дни разом: отдельного метода «бонусы за период» в репозитории
            // нет, а observeAll() уже отдаёт всё с датами — по дню тянуть 7 потоков избыточно.
            val phoneBonusByDate = bonusRepository.observeAll().first()
                .filter { it.packageName.isEmpty() }
                .associate { it.date to it.minutes }
            val week = (DAYS - 1 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong())
                DayUsage(
                    date = date,
                    seconds = totalsByDate[date] ?: 0,
                    // Бюджет дня = лимит этого дня недели + бонус, выданный именно в этот день.
                    budgetMinutes = limits.limitFor(date.dayOfWeek)?.plus(phoneBonusByDate[date] ?: 0)
                )
            }

            val todaySeconds = totalsByDate[today] ?: 0
            val todayAppEntries = entries.filter { !it.isTotal && it.date == today && it.seconds > 0 }
            // Всё экранное время = сумма по приложениям: общий счётчик с приходом «вне лимита»
            // («Всегда доступные», лаунчер, само KidGuard) больше не совпадает с фактическим
            // временем на экране, а пер-app счётчик по-прежнему учитывает всё.
            val todayTotalSeconds = todayAppEntries.sumOf { it.seconds }
            // Иконки — те же, что видит родитель на экранах Правил (иконка с детского устройства,
            // не с родительского): ChildAppsProvider уже решает приоритет серверная/локальная/нет.
            val iconsByPackage = childAppsProvider.loadActiveChildApps().associate { it.packageName to it.icon }
            val apps = todayAppEntries
                .sortedByDescending { it.seconds }
                .map { entry ->
                    AppUsage(
                        packageName = entry.packageName,
                        seconds = entry.seconds,
                        share = if (todayTotalSeconds > 0) {
                            entry.seconds.toFloat() / todayTotalSeconds
                        } else {
                            0f
                        },
                        icon = iconsByPackage[entry.packageName]
                    )
                }

            _uiState.value = StatisticsUiState(
                loading = false,
                child = child,
                todayTotalSeconds = todayTotalSeconds,
                todaySeconds = todaySeconds,
                todayLimitMinutes = limits.limitFor(today.dayOfWeek),
                todayBonusMinutes = phoneBonusByDate[today] ?: 0,
                week = week,
                apps = apps
            )
        }
    }

    private companion object {
        const val DAYS = 7
    }
}
