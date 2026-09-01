package ru.homelab.kidguard.feature.parent.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.PenaltyGrant
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SliderDefaults
import ru.homelab.kidguard.core.ui.components.GlassDangerButton
import ru.homelab.kidguard.core.ui.components.ShimmerBox
import ru.homelab.kidguard.ui.theme.DangerAccentDark
import ru.homelab.kidguard.ui.theme.DangerAccentLight

/**
 * Блок «Штраф» на экране дневного лимита — обратная операция к [BonusSection]: снимает время
 * с того, что у ребёнка осталось на сегодня.
 *
 * Отдельный composable, а не флаг в [BonusSection]: та секция переиспользуется в шторке
 * приложения, где штрафа нет, и раздваивать её ветвлениями значило бы усложнить оба сценария.
 *
 * Верхняя граница штрафа — [remainingMinutes]: снять больше, чем осталось, нельзя (это уже
 * означало бы «заблокировать день», для чего на экране есть отдельная кнопка).
 */
@Composable
fun PenaltySection(
    remainingMinutes: Int,
    penalty: PenaltyGrant?,
    onApply: (minutes: Int, comment: String) -> Unit,
    onCommentChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pickerExpanded by remember { mutableStateOf(false) }
    // Ползунок «Другое…» не должен предлагать больше остатка — и стартовое значение тоже.
    val maxMinutes = remainingMinutes.coerceAtLeast(STEP_MINUTES)
    var otherMinutes by remember(maxMinutes) {
        mutableIntStateOf(OTHER_DEFAULT_MINUTES.coerceAtMost(maxMinutes))
    }
    var comment by remember(penalty?.comment) { mutableStateOf(penalty?.comment.orEmpty()) }
    // Тот же «опасный» акцент, что у кнопки «Заблокировать на сегодня» ниже на экране:
    // colorScheme.error в тёмной теме даёт кричаще-розовую заливку и выбивается из палитры.
    val danger = if (isSystemInDarkTheme()) DangerAccentDark else DangerAccentLight

    // Пока штрафа нет, текст живёт в поле и уедет в БД вместе с нажатием кнопки. Когда штраф
    // уже назначен — правка обновляет его комментарий, но с задержкой: иначе каждая буква
    // писала бы в Room и гнала push на сервер (та же причина, что в BreaksMessageField).
    LaunchedEffect(comment, penalty != null) {
        if (penalty != null && comment != penalty.comment) {
            delay(COMMENT_DEBOUNCE_MS)
            onCommentChange(comment)
        }
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_timer),
                contentDescription = null,
                tint = danger
            )
            Text(
                text = stringResource(R.string.penalty_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Text(
            text = stringResource(R.string.penalty_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        if (penalty != null && penalty.minutes > 0) {
            Surface(
                color = danger.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.penalty_active,
                            formatPenaltyMinutes(penalty.minutes)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = danger,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.penalty_cancel))
                    }
                }
            }
        }

        // Снимать больше нечего — остаются только карточка с «Отменить» и пояснение.
        if (remainingMinutes > 0) {
            // Ряд кнопок с ужатыми отступами — по тем же соображениям, что в BonusSection
            // (на 360dp «Другое…» иначе переносится и ломает высоту ряда).
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickPenaltyButton(QUICK_1, remainingMinutes, onApply = { onApply(it, comment) })
                // Вторую быструю кнопку показываем, только если она даст не то же самое, что
                // первая: при остатке 15 обе обрезались бы до «−15 мин» и стояли бы рядом
                // одинаковыми.
                if (remainingMinutes > QUICK_1) {
                    QuickPenaltyButton(QUICK_2, remainingMinutes, onApply = { onApply(it, comment) })
                }
                OutlinedButton(
                    onClick = { pickerExpanded = !pickerExpanded },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = danger),
                    border = BorderStroke(1.dp, danger.copy(alpha = 0.5f)),
                    contentPadding = CompactButtonPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.penalty_other), maxLines = 1)
                }
            }
        }

        AnimatedVisibility(visible = pickerExpanded && remainingMinutes > 0) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = formatPenaltyMinutes(otherMinutes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = danger,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                Slider(
                    value = otherMinutes.toFloat(),
                    onValueChange = {
                        otherMinutes = ((it / STEP_MINUTES).toInt() * STEP_MINUTES)
                            .coerceIn(STEP_MINUTES, maxMinutes)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = danger,
                        activeTrackColor = danger,
                        activeTickColor = danger.copy(alpha = 0.4f)
                    ),
                    valueRange = STEP_MINUTES.toFloat()..maxMinutes.toFloat(),
                    // Шагов ровно столько, сколько влезает в остаток; при остатке меньше шага
                    // ползунок вырождается в одну позицию, и делений быть не должно.
                    steps = (maxMinutes / STEP_MINUTES - 2).coerceAtLeast(0)
                )
                GlassDangerButton(
                    onClick = {
                        onApply(otherMinutes, comment)
                        pickerExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.penalty_apply_action,
                            formatPenaltyMinutes(otherMinutes)
                        )
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.penalty_comment_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            placeholder = { Text(stringResource(R.string.penalty_comment_placeholder)) },
            singleLine = false,
            minLines = 2,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
    }
}

/**
 * Быстрая кнопка на фиксированное число минут. Если остатка на неё не хватает, снимаем ровно
 * остаток: обещать «−30» и снять 30 при остатке 20 нельзя, а прятать кнопку — значит менять
 * состав ряда на глазах у родителя.
 */
@Composable
private fun RowScope.QuickPenaltyButton(
    minutes: Int,
    remainingMinutes: Int,
    onApply: (Int) -> Unit
) {
    val applied = minutes.coerceAtMost(remainingMinutes)
    val danger = if (isSystemInDarkTheme()) DangerAccentDark else DangerAccentLight
    OutlinedButton(
        onClick = { onApply(applied) },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = danger),
        border = BorderStroke(1.dp, danger.copy(alpha = 0.5f)),
        contentPadding = CompactButtonPadding,
        modifier = Modifier.weight(1f)
    ) {
        Text(stringResource(R.string.penalty_quick, applied), maxLines = 1)
    }
}

/** Заглушка в габаритах готовой секции: после загрузки экран не должен сдвинуться. */
@Composable
fun PenaltySectionSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(24.dp))
        ShimmerBox(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(32.dp)
        )
        ShimmerBox(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(40.dp)
        )
        ShimmerBox(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(72.dp)
        )
    }
}

@Composable
private fun formatPenaltyMinutes(minutes: Int): String = when {
    minutes >= 60 -> stringResource(R.string.limit_value_hm, minutes / 60, minutes % 60)
    else -> stringResource(R.string.limit_value_m, minutes)
}

/** Те же ужатые отступы, что в [BonusSection]: три кнопки делят ряд поровну. */
private val CompactButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

private const val QUICK_1 = 15
private const val QUICK_2 = 30
private const val OTHER_DEFAULT_MINUTES = 45
private const val STEP_MINUTES = 15
private const val COMMENT_DEBOUNCE_MS = 700L
