package ru.homelab.kidguard.feature.parent.rules

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.BreakMode
import ru.homelab.kidguard.core.domain.model.BreakRules
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.GlassToggle
import ru.homelab.kidguard.ui.theme.BreaksAccentDark
import ru.homelab.kidguard.ui.theme.BreaksAccentLight
import java.time.DayOfWeek

/**
 * Экран «Перерывы» (Дневной лимит → кнопка «Перерывы», макет `docs/ui-concepts/breaks/breaks-mockup.html`).
 *
 * Раз в N минут непрерывного залипания (режим [BreakMode.INTERVAL]) или в назначенные часы
 * (режим [BreakMode.HOURS]) экран ребёнка на несколько минут блокируется замком перерыва.
 * Заготовленных значений нет: интервал, длительность и часы стартуют «не заданы», а тумблер
 * «Включить» недоступен для включения, пока родитель не задаст всё необходимое.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreaksScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BreaksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val rules = uiState.rules
    var showResetConfirm by remember { mutableStateOf(false) }
    var addingHour by remember { mutableStateOf(false) }

    // Тот же зелёный акцент, что и на кнопке дневного лимита — единый цвет фичи «Перерывы».
    val accentColor = if (isSystemInDarkTheme()) BreaksAccentDark else BreaksAccentLight

    Column(modifier = modifier.fillMaxSize()) {
        CompactTopBar(title = stringResource(R.string.breaks_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.breaks_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    BreaksEnableRow(
                        uiState = uiState,
                        accentColor = accentColor,
                        onToggle = { checked ->
                            if (checked) {
                                // Включить можно только когда всё нужное задано — при попытке
                                // включить раньше времени тап просто игнорируется, тумблер и так
                                // визуально притушен (см. BreaksEnableRow).
                                if (uiState.canEnable) viewModel.setEnabled(true)
                            } else {
                                viewModel.setEnabled(false)
                            }
                        }
                    )
                }
            }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BreaksModeRow(
                            labelRes = R.string.breaks_mode_interval,
                            selected = rules.mode == BreakMode.INTERVAL,
                            accentColor = accentColor,
                            onClick = { viewModel.setMode(BreakMode.INTERVAL) }
                        )
                        BreaksModeRow(
                            labelRes = R.string.breaks_mode_hours,
                            selected = rules.mode == BreakMode.HOURS,
                            accentColor = accentColor,
                            onClick = { viewModel.setMode(BreakMode.HOURS) }
                        )

                        if (rules.mode == BreakMode.INTERVAL) {
                            BreaksSliderField(
                                labelRes = R.string.breaks_interval_label,
                                unsetLabelRes = R.string.breaks_interval_unset,
                                value = rules.intervalMinutes,
                                range = INTERVAL_MIN..INTERVAL_MAX,
                                accentColor = accentColor,
                                onValueChange = viewModel::setInterval,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            BreaksHoursField(
                                hours = uiState.sortedHours,
                                accentColor = accentColor,
                                onAddClick = { addingHour = true },
                                onRemove = viewModel::removeHour,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        BreaksSliderField(
                            labelRes = R.string.breaks_duration_label,
                            unsetLabelRes = R.string.breaks_duration_unset,
                            value = rules.durationMinutes,
                            range = DURATION_MIN..DURATION_MAX,
                            accentColor = accentColor,
                            onValueChange = viewModel::setDuration,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }
            }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    BreaksMessageField(
                        message = rules.message,
                        onMessageChange = viewModel::setMessage
                    )
                }
            }
            item {
                val hasAnythingToReset = rules != BreakRules.EMPTY
                TextButton(
                    onClick = { showResetConfirm = true },
                    enabled = hasAnythingToReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.breaks_reset),
                        color = if (hasAnythingToReset) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.breaks_reset_title)) },
            text = { Text(stringResource(R.string.breaks_reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    showResetConfirm = false
                }) {
                    Text(
                        text = stringResource(R.string.breaks_reset_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (addingHour) {
        BreakHourSheet(
            accentColor = accentColor,
            existingHours = rules.hours,
            onDismiss = { addingHour = false },
            onSave = { minuteOfDay ->
                viewModel.addHour(minuteOfDay)
                addingHour = false
            }
        )
    }
}

/** Заголовок с иконкой, подзаголовком-подсказкой и тумблером «Включить перерывы». */
@Composable
private fun BreaksEnableRow(
    uiState: BreaksUiState,
    accentColor: Color,
    onToggle: (Boolean) -> Unit
) {
    val rules = uiState.rules
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_coffee),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.breaks_enable_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = breaksHintText(uiState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        GlassToggle(
            checked = rules.enabled,
            // GlassToggle не поддерживает enabled-параметр (см. аналогичную заметку в
            // ScheduleScreen про ScheduleCard): включение блокируем сами, выключить можно всегда —
            // иначе родитель, донастроивший что-то не так после включения (см. canEnable), не
            // смог бы вернуть тумблер обратно.
            onCheckedChange = onToggle,
            modifier = Modifier
                .alpha(if (rules.enabled || uiState.canEnable) 1f else 0.4f)
                .padding(top = 2.dp)
        )
    }
}

/** Подзаголовок тумблера: чего не хватает, либо в какие дни перерывы реально сработают. */
@Composable
private fun breaksHintText(uiState: BreaksUiState): String = when {
    !uiState.canEnable -> stringResource(R.string.breaks_enable_hint_disabled)
    uiState.activeDays.size == DayOfWeek.entries.size -> stringResource(R.string.breaks_enable_hint_active_all)
    uiState.activeDays.isEmpty() -> stringResource(R.string.breaks_enable_hint_none)
    else -> {
        val daysText = uiState.activeDays.map { stringResource(it.shortNameRes()) }.joinToString(", ")
        stringResource(R.string.breaks_enable_hint_active, daysText)
    }
}

/** Строка выбора режима (галочка): «Через интервал залипания» / «В определённые часы». */
@Composable
private fun BreaksModeRow(
    @StringRes labelRes: Int,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val boxShape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(19.dp)
                .clip(boxShape)
                .background(if (selected) accentColor else Color.Transparent)
                .border(
                    width = 1.5.dp,
                    color = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant,
                    shape = boxShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Слайдер с подписью значения. 0 = «не задано» — слайдер в этом случае визуально стоит на
 * минимуме диапазона (мутный цвет), а подпись показывает текст [unsetLabelRes] вместо числа: сам
 * диапазон слайдера не может опуститься до нуля (минимум — [range] first), это и не нужно,
 * состояние «не задано» отражает только подпись и цвет.
 */
@Composable
private fun BreaksSliderField(
    @StringRes labelRes: Int,
    @StringRes unsetLabelRes: Int,
    value: Int,
    range: IntRange,
    accentColor: Color,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSet = value > 0
    val fieldColor = if (isSet) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isSet) stringResource(R.string.duration_minutes, value) else stringResource(unsetLabelRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSet) FontWeight.Bold else FontWeight.Normal,
                color = fieldColor
            )
        }
        Slider(
            value = value.coerceAtLeast(range.first).toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / SLIDER_STEP - 1,
            colors = SliderDefaults.colors(
                thumbColor = fieldColor,
                activeTrackColor = fieldColor
            ),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** Список часов перерыва (чипы «12:00 ✕») + чип «Добавить час» — режим HOURS. */
@Composable
private fun BreaksHoursField(
    hours: List<Int>,
    accentColor: Color,
    onAddClick: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.breaks_hours_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            hours.forEach { minuteOfDay ->
                BreakHourChip(
                    minuteOfDay = minuteOfDay,
                    accentColor = accentColor,
                    onRemove = { onRemove(minuteOfDay) }
                )
            }
            AddHourChip(onClick = onAddClick)
        }
    }
}

@Composable
private fun BreakHourChip(minuteOfDay: Int, accentColor: Color, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(accentColor.copy(alpha = 0.16f))
            .border(1.dp, accentColor.copy(alpha = 0.3f), shape)
            .padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.schedule_time_hm, minuteOfDay / 60, minuteOfDay % 60),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.breaks_hour_remove),
            tint = accentColor,
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onRemove)
        )
    }
}

@Composable
private fun AddHourChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.breaks_hours_add),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Текст, который увидит ребёнок на замке перерыва. Пусто → подсказка поля и есть фраза-шаблон
 * ([R.string.breaks_default_message]), которую замок покажет вместо пустого текста.
 *
 * Правку дебаунсим на [MESSAGE_DEBOUNCE_MS]: без этого каждая напечатанная буква писала бы в Room
 * и — так как `breakRules` участвует в петле синхронизации (веха 4.3) — гоняла бы push на сервер
 * на каждое нажатие клавиши.
 */
@Composable
private fun BreaksMessageField(message: String, onMessageChange: (String) -> Unit) {
    var input by remember(message) { mutableStateOf(message) }

    LaunchedEffect(input) {
        if (input != message) {
            delay(MESSAGE_DEBOUNCE_MS)
            onMessageChange(input)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.breaks_message_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text(stringResource(R.string.breaks_default_message)) },
            // Многострочное: фраза родителя может быть длинной, и в одну строку она обрезалась бы
            // многоточием — родитель не видел бы, что именно прочитает ребёнок.
            singleLine = false,
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
        // Пустое поле объясняем подсказкой про шаблон, заполненное — где текст появится.
        Text(
            text = stringResource(
                if (input.isBlank()) R.string.breaks_message_hint else R.string.breaks_message_filled_hint
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// --- Шторка добавления часа перерыва (переиспользует барабаны TimeBlock/WheelColumn из ScheduleScreen) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreakHourSheet(
    accentColor: Color,
    existingHours: Set<Int>,
    onDismiss: () -> Unit,
    onSave: (minuteOfDay: Int) -> Unit
) {
    var hour by remember { mutableIntStateOf(DEFAULT_BREAK_HOUR) }
    var minute by remember { mutableIntStateOf(0) }
    val minuteOfDay = hour * 60 + minute
    val alreadyAdded = minuteOfDay in existingHours

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.breaks_hour_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.breaks_hour_sheet_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
            )
            TimeBlock(
                labelRes = R.string.breaks_hour_sheet_time_label,
                hour = hour,
                minute = minute,
                onHourChange = { hour = it },
                onMinuteChange = { minute = it },
                accentColor = accentColor
            )
            if (alreadyAdded) {
                Text(
                    text = stringResource(R.string.breaks_hour_already_added),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(
                    enabled = !alreadyAdded,
                    onClick = { onSave(minuteOfDay) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.schedule_save))
                }
            }
        }
    }
}

@StringRes
private fun DayOfWeek.shortNameRes(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.day_monday_short
    DayOfWeek.TUESDAY -> R.string.day_tuesday_short
    DayOfWeek.WEDNESDAY -> R.string.day_wednesday_short
    DayOfWeek.THURSDAY -> R.string.day_thursday_short
    DayOfWeek.FRIDAY -> R.string.day_friday_short
    DayOfWeek.SATURDAY -> R.string.day_saturday_short
    DayOfWeek.SUNDAY -> R.string.day_sunday_short
}

private const val INTERVAL_MIN = 20
private const val INTERVAL_MAX = 180
private const val DURATION_MIN = 5
private const val DURATION_MAX = 30
private const val SLIDER_STEP = 5
private const val DEFAULT_BREAK_HOUR = 15
private const val MESSAGE_DEBOUNCE_MS = 500L
