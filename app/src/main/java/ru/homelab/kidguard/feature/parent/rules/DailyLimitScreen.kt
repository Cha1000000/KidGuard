package ru.homelab.kidguard.feature.parent.rules

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassBackground
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.domain.model.DailyLimits
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLimitScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DailyLimitViewModel = hiltViewModel()
) {
    val limits by viewModel.dailyLimits.collectAsStateWithLifecycle()
    val phoneBonus by viewModel.phoneBonusMinutes.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now().dayOfWeek }
    var editingDay by remember { mutableStateOf<DayOfWeek?>(null) }

    GlassBackground(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            CompactTopBar(
                title = stringResource(R.string.rules_daily_limit_title),
                onBack = onBack
            )
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.daily_limit_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    BonusSection(
                        activeBonusMinutes = phoneBonus,
                        subtitleRes = R.string.bonus_subtitle_phone,
                        onAdd = { viewModel.addPhoneBonus(it) },
                        onClear = { viewModel.clearPhoneBonus() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val days = DayOfWeek.entries
                        days.forEachIndexed { index, day ->
                            DayRow(
                                day = day,
                                minutes = limits.limitFor(day),
                                isToday = day == today,
                                onClick = { editingDay = day }
                            )
                            if (index < days.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    editingDay?.let { day ->
        LimitEditorSheet(
            day = day,
            currentMinutes = limits.limitFor(day),
            onDismiss = { editingDay = null },
            onSave = { minutes, applyToAll ->
                if (applyToAll) {
                    viewModel.setLimitForAllDays(minutes)
                } else {
                    viewModel.setLimit(day, minutes)
                }
                editingDay = null
            }
        )
    }
}

@Composable
private fun DayRow(day: DayOfWeek, minutes: Int?, isToday: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(day.nameRes()),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (isToday) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.daily_limit_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = formatLimit(minutes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (minutes == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitEditorSheet(
    day: DayOfWeek,
    currentMinutes: Int?,
    onDismiss: () -> Unit,
    onSave: (minutes: Int?, applyToAll: Boolean) -> Unit
) {
    var minutes by remember { mutableIntStateOf(currentMinutes ?: DEFAULT_MINUTES) }
    var applyToAll by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(stringResource(day.nameRes()), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.daily_limit_sheet_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
            )
            Text(
                text = formatLimit(minutes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            Slider(
                value = minutes.toFloat(),
                onValueChange = { minutes = (it / STEP_MINUTES).toInt() * STEP_MINUTES },
                valueRange = 0f..MAX_MINUTES.toFloat(),
                steps = MAX_MINUTES / STEP_MINUTES - 1
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { applyToAll = !applyToAll }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                Text(
                    text = stringResource(R.string.daily_limit_apply_all),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = { onSave(null, applyToAll) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.daily_limit_no_limit))
                }
                Button(
                    onClick = { onSave(minutes, applyToAll) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.daily_limit_save))
                }
            }
        }
    }
}

@Composable
private fun formatLimit(minutes: Int?): String = when {
    minutes == null -> stringResource(R.string.daily_limit_no_limit)
    minutes >= 60 -> stringResource(R.string.limit_value_hm, minutes / 60, minutes % 60)
    else -> stringResource(R.string.limit_value_m, minutes)
}

@StringRes
private fun DayOfWeek.nameRes(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.day_monday
    DayOfWeek.TUESDAY -> R.string.day_tuesday
    DayOfWeek.WEDNESDAY -> R.string.day_wednesday
    DayOfWeek.THURSDAY -> R.string.day_thursday
    DayOfWeek.FRIDAY -> R.string.day_friday
    DayOfWeek.SATURDAY -> R.string.day_saturday
    DayOfWeek.SUNDAY -> R.string.day_sunday
}

private const val DEFAULT_MINUTES = 120
private const val MAX_MINUTES = 300
private const val STEP_MINUTES = 15
