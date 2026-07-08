package ru.homelab.kidguard.feature.parent.rules

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.R

/**
 * Блок «Дополнительное время» (бонусы) — переиспользуется на экране дневного лимита (бонус
 * телефона) и в bottom-sheet приложения (бонус приложения). Два способа выдачи (не конфликтуют):
 * быстрые кнопки [+15]/[+30] и слайдер произвольного значения под «Другое…». Показывает активный
 * бонус за сегодня и даёт его отменить.
 */
@Composable
fun BonusSection(
    activeBonusMinutes: Int,
    @StringRes subtitleRes: Int,
    onAdd: (minutes: Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pickerExpanded by remember { mutableStateOf(false) }
    var otherMinutes by remember { mutableIntStateOf(OTHER_DEFAULT_MINUTES) }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_timer),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.bonus_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Text(
            text = stringResource(subtitleRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        if (activeBonusMinutes > 0) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.bonus_active, formatMinutes(activeBonusMinutes)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.bonus_cancel))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onAdd(QUICK_ADD_1) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.bonus_quick_add, QUICK_ADD_1))
            }
            OutlinedButton(onClick = { onAdd(QUICK_ADD_2) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.bonus_quick_add, QUICK_ADD_2))
            }
            OutlinedButton(onClick = { pickerExpanded = !pickerExpanded }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.bonus_other))
            }
        }

        AnimatedVisibility(visible = pickerExpanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = formatMinutes(otherMinutes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                Slider(
                    value = otherMinutes.toFloat(),
                    onValueChange = { otherMinutes = (it / STEP_MINUTES).toInt() * STEP_MINUTES },
                    valueRange = STEP_MINUTES.toFloat()..MAX_MINUTES.toFloat(),
                    steps = MAX_MINUTES / STEP_MINUTES - 2
                )
                Button(
                    onClick = {
                        onAdd(otherMinutes)
                        pickerExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.bonus_add_action, formatMinutes(otherMinutes)),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun formatMinutes(minutes: Int): String = when {
    minutes >= 60 -> stringResource(R.string.limit_value_hm, minutes / 60, minutes % 60)
    else -> stringResource(R.string.limit_value_m, minutes)
}

private const val QUICK_ADD_1 = 15
private const val QUICK_ADD_2 = 30
private const val OTHER_DEFAULT_MINUTES = 45
private const val STEP_MINUTES = 15
private const val MAX_MINUTES = 180
