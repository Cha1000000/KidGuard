package ru.homelab.kidguard.feature.parent.rules

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardOptions
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.EmergencyContact
import ru.homelab.kidguard.core.domain.model.ScheduleKind
import ru.homelab.kidguard.core.domain.model.ScheduleRules
import ru.homelab.kidguard.core.domain.model.TimeWindow
import ru.homelab.kidguard.core.domain.text.RussianDative
import ru.homelab.kidguard.core.ui.components.CompactTopBar
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.GlassToggle
import ru.homelab.kidguard.ui.theme.ScheduleSleepDark
import ru.homelab.kidguard.ui.theme.ScheduleSleepLight
import ru.homelab.kidguard.ui.theme.ScheduleStudyDark
import ru.homelab.kidguard.ui.theme.ScheduleStudyLight
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/** Тот же жёлтый предупреждающий акцент, что и в BlockedSitesScreen — единый цвет warning-плашек. */
private val WarningColor = Color(0xFFF5B301)

/** Высота, к которой выравниваются поле ввода и кнопка «Добавить» контакта. */
private val InputRowHeight = 56.dp

/**
 * Экран «Расписание» (Правила → Расписание, макет `docs/ui-concepts/schedule/schedule-mockup.html`).
 *
 * Два независимых расписания на 7 дней недели: «Время учёбы» (мягкая блокировка — «Всегда
 * доступные» продолжают работать) и «Время сна» (полная блокировка несмахиваемым замком,
 * снимается только PIN-ом родителя). Ниже — общий для обоих родителей список контактов, по
 * которым ребёнок может позвонить с ночного замка.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onBack: () -> Unit,
    onOpenPinSetup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val studySchedule by viewModel.studySchedule.collectAsStateWithLifecycle()
    val sleepSchedule by viewModel.sleepSchedule.collectAsStateWithLifecycle()
    val contacts by viewModel.emergencyContacts.collectAsStateWithLifecycle()
    val pinIsSet by viewModel.pinIsSet.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now().dayOfWeek }

    var editing by remember { mutableStateOf<Pair<ScheduleKind, DayOfWeek>?>(null) }
    var resetConfirmKind by remember { mutableStateOf<ScheduleKind?>(null) }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }

    // Затемнённые варианты в светлой теме — на светлом фоне исходные (тёмная тема) значения
    // дают контраст ~2:1, почти не читаются (см. заметку в макете).
    val studyColor = if (isSystemInDarkTheme()) ScheduleStudyDark else ScheduleStudyLight
    val sleepColor = if (isSystemInDarkTheme()) ScheduleSleepDark else ScheduleSleepLight

    Column(modifier = modifier.fillMaxSize()) {
        CompactTopBar(
            title = stringResource(R.string.rules_schedule_title),
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.schedule_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            item {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    ScheduleCard(
                        titleRes = R.string.schedule_study_title,
                        hintRes = R.string.schedule_study_hint,
                        resetLabelRes = R.string.schedule_reset_study,
                        icon = "🎒",
                        accentColor = studyColor,
                        schedule = studySchedule,
                        today = today,
                        toggleEnabled = true,
                        onToggle = { viewModel.setEnabled(ScheduleKind.STUDY, it) },
                        onDayClick = { day -> editing = ScheduleKind.STUDY to day },
                        onReset = { resetConfirmKind = ScheduleKind.STUDY }
                    )
                }
            }
            item {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        ScheduleCard(
                            titleRes = R.string.schedule_sleep_title,
                            hintRes = R.string.schedule_sleep_hint,
                            resetLabelRes = R.string.schedule_reset_sleep,
                            icon = "🌙",
                            accentColor = sleepColor,
                            schedule = sleepSchedule,
                            today = today,
                            // Без PIN включать ночной замок нельзя — снять его будет нечем.
                            toggleEnabled = pinIsSet,
                            onToggle = { viewModel.setEnabled(ScheduleKind.SLEEP, it) },
                            onDayClick = { day -> editing = ScheduleKind.SLEEP to day },
                            onReset = { resetConfirmKind = ScheduleKind.SLEEP }
                        )
                    }
                    if (!pinIsSet) {
                        PinWarningCard(
                            onOpenPinSetup = onOpenPinSetup,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.schedule_contacts_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            item {
                EmergencyContactInputRow(
                    name = contactName,
                    onNameChange = { contactName = it },
                    phone = contactPhone,
                    onPhoneChange = { contactPhone = it },
                    onAdd = {
                        if (contactName.isNotBlank() && contactPhone.isNotBlank()) {
                            viewModel.addEmergencyContact(
                                EmergencyContact(contactName.trim(), contactPhone.trim())
                            )
                            contactName = ""
                            contactPhone = ""
                        }
                    }
                )
            }
            if (contactName.isNotBlank()) {
                item {
                    Text(
                        text = buildContactPreview(contactName, sleepColor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp, start = 4.dp)
                    )
                }
            }
            if (contacts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.schedule_contacts_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                itemsIndexed(contacts, key = { _, contact -> contact.phone }) { index, contact ->
                    EmergencyContactRow(
                        contact = contact,
                        onRemove = { viewModel.removeEmergencyContact(contact.phone) },
                        modifier = Modifier.padding(
                            top = if (index == 0) 0.dp else 6.dp
                        )
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.schedule_contacts_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }

    resetConfirmKind?.let { kind ->
        val titleRes = if (kind == ScheduleKind.STUDY) {
            R.string.schedule_reset_study_title
        } else {
            R.string.schedule_reset_sleep_title
        }
        AlertDialog(
            onDismissRequest = { resetConfirmKind = null },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(R.string.schedule_reset_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetSchedule(kind)
                    resetConfirmKind = null
                }) {
                    Text(
                        text = stringResource(R.string.schedule_reset_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirmKind = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    editing?.let { (kind, day) ->
        val schedule = if (kind == ScheduleKind.STUDY) studySchedule else sleepSchedule
        val accentColor = if (kind == ScheduleKind.STUDY) studyColor else sleepColor
        ScheduleTimeSheet(
            kind = kind,
            day = day,
            accentColor = accentColor,
            currentWindow = schedule.windowFor(day),
            onDismiss = { editing = null },
            onSave = { window, applyToAll ->
                if (applyToAll) {
                    viewModel.setWindowForAllDays(kind, window)
                } else {
                    viewModel.setWindow(kind, day, window)
                }
                editing = null
            }
        )
    }
}

/** Карточка одного расписания: заголовок с тумблером, 7 строк дней, кнопка сброса. */
@Composable
private fun ScheduleCard(
    @StringRes titleRes: Int,
    @StringRes hintRes: Int,
    @StringRes resetLabelRes: Int,
    icon: String,
    accentColor: Color,
    schedule: ScheduleRules,
    today: DayOfWeek,
    toggleEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onDayClick: (DayOfWeek) -> Unit,
    onReset: () -> Unit
) {
    // Сбрасывать нечего, если ни на один день окно не задано — тогда кнопка неактивна.
    val hasAnyWindow = DayOfWeek.entries.any { day ->
        schedule.windowFor(day)?.isEmpty == false
    }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                Text(text = icon, style = MaterialTheme.typography.bodyMedium)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            GlassToggle(
                checked = schedule.enabled,
                // Тумблер «отключен» без true-disabled семантики: тап просто ничего не делает —
                // GlassToggle не поддерживает enabled-параметр, трогать общий компонент не хотим.
                onCheckedChange = { if (toggleEnabled) onToggle(it) },
                modifier = Modifier
                    .alpha(if (toggleEnabled) 1f else 0.4f)
                    .padding(top = 2.dp)
            )
        }
        Column(modifier = Modifier.padding(top = 8.dp)) {
            val days = DayOfWeek.entries
            days.forEachIndexed { index, day ->
                ScheduleDayRow(
                    day = day,
                    window = schedule.windowFor(day),
                    isToday = day == today,
                    accentColor = accentColor,
                    onClick = { onDayClick(day) }
                )
                if (index < days.lastIndex) HorizontalDivider()
            }
        }
        TextButton(
            onClick = onReset,
            enabled = hasAnyWindow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(resetLabelRes),
                color = if (hasAnyWindow) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScheduleDayRow(
    day: DayOfWeek,
    window: TimeWindow?,
    isToday: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val hasWindow = window != null && !window.isEmpty
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
            text = if (hasWindow) windowRangeText(window!!) else stringResource(R.string.schedule_not_set),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (hasWindow) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun windowRangeText(window: TimeWindow): String {
    val startTime = LocalTime.of(window.startMinute / 60, window.startMinute % 60)
    val start = stringResource(R.string.schedule_time_hm, startTime.hour, startTime.minute)
    val end = stringResource(R.string.schedule_time_hm, window.endsAt.hour, window.endsAt.minute)
    return stringResource(R.string.schedule_window_range, start, end)
}

/** Плашка-предупреждение: «Время сна» нельзя включить без родительского PIN. */
@Composable
private fun PinWarningCard(onOpenPinSetup: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(WarningColor.copy(alpha = 0.12f))
            .border(1.dp, WarningColor.copy(alpha = 0.4f), shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = WarningColor,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = stringResource(R.string.schedule_pin_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(
                onClick = onOpenPinSetup,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.schedule_pin_warning_action),
                    color = WarningColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmergencyContactInputRow(
    name: String,
    onNameChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text(stringResource(R.string.schedule_contact_name_placeholder)) },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .height(InputRowHeight)
        )
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            placeholder = { Text(stringResource(R.string.schedule_contact_phone_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .weight(1f)
                .height(InputRowHeight)
        )
        IconButton(
            onClick = onAdd,
            modifier = Modifier.height(InputRowHeight)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.schedule_contact_add),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Живой предпросмотр: «На замке ребёнок увидит: Позвонить маме» — склонение через [RussianDative]. */
@Composable
private fun buildContactPreview(name: String, accentColor: Color) = buildAnnotatedString {
    append(stringResource(R.string.schedule_contact_preview_prefix))
    append(" ")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) {
        append(stringResource(R.string.schedule_contact_preview_call, RussianDative.of(name)))
    }
}

@Composable
private fun EmergencyContactRow(
    contact: EmergencyContact,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = contact.phone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.schedule_contact_remove),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// --- Шторка выбора времени окна (барабаны часов/минут) ---

private val DEFAULT_STUDY_WINDOW = TimeWindow(8 * 60, 14 * 60)
private val DEFAULT_SLEEP_WINDOW = TimeWindow(21 * 60, 7 * 60)
private const val MINUTE_STEP = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimeSheet(
    kind: ScheduleKind,
    day: DayOfWeek,
    accentColor: Color,
    currentWindow: TimeWindow?,
    onDismiss: () -> Unit,
    onSave: (window: TimeWindow?, applyToAll: Boolean) -> Unit
) {
    val default = if (kind == ScheduleKind.SLEEP) DEFAULT_SLEEP_WINDOW else DEFAULT_STUDY_WINDOW
    val initial = currentWindow?.takeUnless { it.isEmpty } ?: default

    var startHour by remember { mutableIntStateOf(initial.startMinute / 60) }
    var startMinute by remember { mutableIntStateOf(roundToStep(initial.startMinute % 60)) }
    var endHour by remember { mutableIntStateOf(initial.endsAt.hour) }
    var endMinute by remember { mutableIntStateOf(roundToStep(initial.endsAt.minute)) }
    var applyToAll by remember { mutableStateOf(false) }

    val window = TimeWindow(startHour * 60 + startMinute, endHour * 60 + endMinute)

    // Сразу на всю высоту: в наполовину раскрытой шторке барабаны занимают весь экран, а
    // «Сохранить» и галку «применить ко всем» приходится доставать свайпом — их просто не видно.
    //
    // confirmValueChange запрещает закрытие свайпом: вертикальный жест по барабану, сделанный с
    // размаху, шторка перехватывала на себя и закрывалась, теряя выставленное время. Закрыть
    // по-прежнему можно кнопками и тапом по затемнению — их onDismissRequest не проходит через
    // это состояние.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden }
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(
                    R.string.schedule_sheet_title,
                    stringResource(day.nameRes()),
                    stringResource(kind.labelRes())
                ),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(
                    if (kind == ScheduleKind.SLEEP) R.string.schedule_sheet_hint_sleep
                    else R.string.schedule_sheet_hint_study
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
            )

            TimeBlock(
                labelRes = R.string.schedule_sheet_start,
                hour = startHour,
                minute = startMinute,
                onHourChange = { startHour = it },
                onMinuteChange = { startMinute = it },
                accentColor = accentColor
            )
            Spacer(modifier = Modifier.height(10.dp))
            TimeBlock(
                labelRes = R.string.schedule_sheet_end,
                hour = endHour,
                minute = endMinute,
                onHourChange = { endHour = it },
                onMinuteChange = { endMinute = it },
                accentColor = accentColor
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { applyToAll = !applyToAll }
                    .padding(top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                Text(
                    text = stringResource(R.string.schedule_apply_all),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = { onSave(null, applyToAll) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.schedule_remove))
                }
                Button(
                    // Равные границы = пустое окно (ничего не блокирует) — сохранять его бессмысленно.
                    enabled = !window.isEmpty,
                    onClick = { onSave(window, applyToAll) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.schedule_save))
                }
            }
        }
    }
}

private fun roundToStep(minute: Int): Int = (minute / MINUTE_STEP) * MINUTE_STEP

@StringRes
private fun ScheduleKind.labelRes(): Int = when (this) {
    ScheduleKind.STUDY -> R.string.schedule_kind_study_label
    ScheduleKind.SLEEP -> R.string.schedule_kind_sleep_label
}

/** Блок «Начало»/«Конец»: подпись + пара барабанов (часы, минуты). */
@Composable
private fun TimeBlock(
    @StringRes labelRes: Int,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    accentColor: Color
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 15.dp,
        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelColumn(
                    values = HOURS,
                    selectedValue = hour,
                    onValueChange = onHourChange,
                    accentColor = accentColor
                )
                Text(
                    text = stringResource(R.string.schedule_wheel_separator),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                WheelColumn(
                    values = MINUTES,
                    selectedValue = minute,
                    onValueChange = onMinuteChange,
                    accentColor = accentColor
                )
            }
        }
    }
}

private val HOURS = (0..23).toList()
private val MINUTES = (0..55 step MINUTE_STEP).toList()
private val WheelItemHeight = 36.dp
private const val WHEEL_VISIBLE_COUNT = 3

/**
 * Барабан значений на LazyColumn: три видимых строки, выбранное значение — центральное, крупнее и
 * акцентным цветом. Снаппинг к центру строки — [SnapPosition.Center]; contentPadding сверху/снизу
 * в половину видимой высоты минус пол-элемента даёт докрутить до центра даже крайние значения
 * (0 час, 55 минут).
 */
@Composable
private fun WheelColumn(
    values: List<Int>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    accentColor: Color
) {
    val initialIndex = remember(values, selectedValue) {
        values.indexOf(selectedValue).coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center)

    val verticalPadding = WheelItemHeight * (WHEEL_VISIBLE_COUNT - 1) / 2
    val fadeColor = MaterialTheme.colorScheme.surfaceContainerLow

    // LazyListItemInfo.offset уже отсчитан от начала СОДЕРЖИМОГО (contentPadding вычтен), поэтому
    // и целимся в половину высоты строки — без прибавления padding'а (иначе центр «уезжает» на
    // одну строку вперёд, как было при первой версии этого расчёта).
    val density = LocalDensity.current
    val targetCenterPx = with(density) { (WheelItemHeight / 2).toPx() }

    val centerIndex by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2) - targetCenterPx) }
                ?.index
                ?: initialIndex
        }
    }
    LaunchedEffect(centerIndex) {
        values.getOrNull(centerIndex)?.let(onValueChange)
    }

    Box(
        modifier = Modifier
            .width(60.dp)
            .height(WheelItemHeight * WHEEL_VISIBLE_COUNT)
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(values) { index, value ->
                val selected = index == centerIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.schedule_wheel_value, value),
                        style = if (selected) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // Затенение сверху/снизу — соседние значения уходят под градиент, как в макете.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(WheelItemHeight)
                .background(Brush.verticalGradient(listOf(fadeColor, fadeColor.copy(alpha = 0f))))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(WheelItemHeight)
                .background(Brush.verticalGradient(listOf(fadeColor.copy(alpha = 0f), fadeColor)))
        )
    }
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
