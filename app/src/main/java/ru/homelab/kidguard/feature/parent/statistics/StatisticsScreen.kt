package ru.homelab.kidguard.feature.parent.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.CenteredMessage
import ru.homelab.kidguard.core.ui.components.GlassBackground
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.ScreenTitle
import ru.homelab.kidguard.feature.parent.ChildSelectorChip
import java.time.format.TextStyle
import java.util.Locale

/** Вкладка «Статистика» родителя (веха 4.4): экранное время ребёнка с сервера. */
@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Обновляем при каждом входе на вкладку: VM переживает переключение вкладок
    // и без этого показывала бы устаревшие данные.
    LaunchedEffect(Unit) { viewModel.refresh() }

    GlassBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTitle(stringResource(R.string.parent_tab_statistics))
            if (!uiState.noChildren) ChildSelectorChip()

            when {
                uiState.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                uiState.noChildren -> CenteredMessage(
                    text = stringResource(R.string.statistics_no_children),
                    modifier = Modifier.weight(1f)
                )

                uiState.error -> CenteredMessage(
                    text = stringResource(R.string.statistics_load_error),
                    modifier = Modifier.weight(1f)
                )

                else -> StatisticsContent(uiState)
            }
        }
    }
}

@Composable
private fun StatisticsContent(state: StatisticsUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        if (!state.hasData) {
            CenteredMessage(
                text = stringResource(R.string.statistics_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp)
            )
            return@Column
        }

        TodayCard(state)
        WeekChartCard(state.week)

        if (state.apps.isNotEmpty()) {
            Text(
                text = stringResource(R.string.statistics_apps_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            state.apps.forEach { app -> AppUsageRow(app) }
        }
    }
}

@Composable
private fun TodayCard(state: StatisticsUiState) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.statistics_today_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatMinutes(state.todaySeconds / 60),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                state.todayLimitMinutes?.let { limit ->
                val leftMinutes = limit - state.todaySeconds / 60
                Text(
                    text = if (leftMinutes > 0) {
                        stringResource(
                            R.string.statistics_today_limit,
                            formatMinutes(limit),
                            formatMinutes(leftMinutes)
                        )
                    } else {
                        stringResource(R.string.statistics_today_limit_exceeded, formatMinutes(limit))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            }
            // Иконка часов справа (как на мокапе)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = Color(0xFF2D4B42),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_timer),
                    contentDescription = null,
                    tint = Color(0xFFE0E0E0),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun WeekChartCard(week: List<DayUsage>) {
    val maxSeconds = (week.maxOfOrNull { it.seconds } ?: 0).coerceAtLeast(1)
    val lastIndex = week.lastIndex

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.statistics_week_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                week.forEachIndexed { index, day ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (day.seconds > 0) {
                            Text(
                                text = formatChartValue(day.seconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                                // Минимум 2dp, чтобы нулевые дни были видны линией у основания.
                                .height((110f * day.seconds / maxSeconds).dp.coerceAtLeast(2.dp))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (index == lastIndex) 1f else 0.45f
                                    )
                                )
                        )
                        Text(
                            text = day.date.dayOfWeek
                                .getDisplayName(TextStyle.SHORT, Locale("ru"))
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(app: AppUsage) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Text(
                    text = app.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatMinutes(app.seconds / 60),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        // Полоска — доля приложения в суммарном времени за день.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(app.share)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun formatMinutes(minutes: Int): String = when {
    minutes >= 60 -> stringResource(R.string.limit_value_hm, minutes / 60, minutes % 60)
    else -> stringResource(R.string.limit_value_m, minutes)
}

/** Компактное «1:05» (ч:мм) для подписи над столбиком диаграммы. */
private fun formatChartValue(seconds: Int): String {
    val minutes = seconds / 60
    return "%d:%02d".format(minutes / 60, minutes % 60)
}
