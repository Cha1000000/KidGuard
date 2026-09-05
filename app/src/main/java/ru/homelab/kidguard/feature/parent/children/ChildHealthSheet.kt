package ru.homelab.kidguard.feature.parent.children

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.domain.model.DevicePermission
import ru.homelab.kidguard.core.domain.model.ProcessExitKind
import ru.homelab.kidguard.core.domain.model.ProcessExitRecord
import ru.homelab.kidguard.core.ui.components.GlassBottomSheet
import ru.homelab.kidguard.core.ui.components.healthImpactRes
import ru.homelab.kidguard.core.ui.components.titleRes
import java.time.Instant

/** Детали поломки контроля устройства (watchdog) — bottom-sheet по тапу на плашку здоровья. */

/**
 * Детали поломки контроля (watchdog, веха 6). Здесь и только здесь показываем «последняя связь» —
 * на карточке при нормальной работе это лишняя строка.
 *
 * Два разных случая с разными текстами:
 * - разрешения отвалились → перечисляем, ЧТО именно и чем грозит;
 * - устройство молчит → флаги показываем с оговоркой, что они могли устареть, а «что делать»
 *   начинается с честного «телефон может быть просто выключен».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HealthSheet(child: Child, onDismiss: () -> Unit) {
    val now = remember(child) { Instant.now() }
    val broken = child.health?.brokenPermissions().orEmpty()
    val isSilent = broken.isEmpty()

    GlassBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(
                    if (isSilent) R.string.child_health_silent_title else R.string.child_health_broken_title
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = child.lastSeenAt
                    ?.let { stringResource(R.string.child_health_last_seen, formatAgo(it, now)) }
                    ?: stringResource(R.string.child_health_last_seen_never),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            if (isSilent) {
                StaleHealthNote()
            } else {
                broken.forEach { HealthIssueRow(it) }
            }

            // Причина прошлой смерти процесса — главный ответ на вопрос «контроль пропал сам или
            // его выключили». Раньше его взять было негде: на HiOS логи вытесняются за минуты.
            child.health?.lastExit
                ?.takeIf { it.kind.worthReporting }
                ?.let { LastExitNote(record = it, now = now) }

            HowToFix(isSilent = isSilent)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Как завершился предыдущий запуск. Показываем только то, что родителю стоит знать
 * ([ProcessExitKind.worthReporting]): обновление приложения в этот список не входит — после него
 * контроль поднимается сам, и уведомлять о каждой установке значит приучить не читать плашку.
 */
@Composable
private fun LastExitNote(record: ProcessExitRecord, now: Instant) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.child_health_last_exit_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(record.kind.textRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = stringResource(R.string.child_health_last_exit_when, formatAgo(record.at, now)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * Текст причины. `PACKAGE_UPDATED`, `OTHER` и `UNKNOWN` сюда не доходят — их отсекает
 * [ProcessExitKind.worthReporting]; на всякий случай отдаём им нейтральную формулировку.
 */
@StringRes
private fun ProcessExitKind.textRes(): Int = when (this) {
    ProcessExitKind.FORCE_STOP -> R.string.child_health_exit_force_stop
    ProcessExitKind.TASK_MANAGER_STOP -> R.string.child_health_exit_task_manager
    ProcessExitKind.REMOVE_TASK -> R.string.child_health_exit_remove_task
    ProcessExitKind.CRASH -> R.string.child_health_exit_crash
    ProcessExitKind.ANR -> R.string.child_health_exit_anr
    ProcessExitKind.LOW_MEMORY -> R.string.child_health_exit_low_memory
    ProcessExitKind.FREEZER -> R.string.child_health_exit_freezer
    ProcessExitKind.PACKAGE_UPDATED,
    ProcessExitKind.OTHER,
    ProcessExitKind.UNKNOWN -> R.string.child_health_exit_force_stop
}

/** Одна отвалившаяся штука: название + чем это грозит. */
@Composable
private fun HealthIssueRow(permission: DevicePermission) {
    Surface(
        color = HealthDangerColor.copy(alpha = 0.09f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(permission.titleRes()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = HealthDangerColor
            )
            Text(
                text = stringResource(permission.healthImpactRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * Устройство молчит: последний известный отчёт был здоровым, но врать «всё работает» на его
 * основании нельзя — оговариваем, что данные могли устареть.
 */
@Composable
private fun StaleHealthNote() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.child_health_stale_ok_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.child_health_stale_ok_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Инструкция текстом, без кнопок: чинить надо на телефоне РЕБЁНКА, а родитель смотрит со своего. */
@Composable
private fun HowToFix(isSilent: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(stringResource(R.string.child_health_howto_label))
                }
                append(" ")
                append(
                    stringResource(
                        if (isSilent) R.string.child_health_howto_silent
                        else R.string.child_health_howto_broken
                    )
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}
