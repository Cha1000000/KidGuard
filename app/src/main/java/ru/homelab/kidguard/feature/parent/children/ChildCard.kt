package ru.homelab.kidguard.feature.parent.children

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.domain.model.Child
import ru.homelab.kidguard.core.ui.components.ChildAvatars
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.titleRes
import java.time.Instant

/** Карточка ребёнка в списке и кнопка добавления нового ребёнка. */

/**
 * Красный для поломки контроля. Фиксированный, а не `colorScheme.error`: в тёмной теме M3-токен
 * ошибки — светлый розовый (#F2B8B5), на тёмном фоне читается блёкло, а это тревога. Тот же
 * приём и по той же причине, что у кнопки «Удалить ребёнка».
 */
internal val HealthDangerColor = Color(0xFFE5534B)

@Composable
internal fun ChildCard(child: Child, onClick: () -> Unit, onHealthClick: () -> Unit) {
    // Считаем «сейчас» на каждой рекомпозиции: экран живёт недолго и обновляется при входе
    // (LaunchedEffect → refresh), точности до минуты для порога в 12 ч заведомо хватает.
    val now = remember(child) { Instant.now() }

    GlassCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(ChildAvatars.resFor(child.avatar)),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(child.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        if (child.paired) R.string.children_status_paired else R.string.children_status_waiting
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (child.paired) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary
                )
                // Плашка ТОЛЬКО при поломке: когда всё работает, карточка не меняется вовсе
                // (решение Володи — лишняя строка при норме превращается в шум).
                if (child.isControlBroken(now)) {
                    HealthWarningBadge(child = child, now = now, onClick = onHealthClick)
                }
            }
        }
    }
}

/** Красная плашка «контроль сломан» на карточке ребёнка. Тап — детали. */
@Composable
private fun HealthWarningBadge(child: Child, now: Instant, onClick: () -> Unit) {
    val broken = child.health?.brokenPermissions().orEmpty()
    val text = when {
        // Сломано несколько — говорим, что есть ещё, иначе родитель починит одно и решит, что всё.
        broken.size > 1 -> stringResource(
            R.string.child_health_broken_more,
            stringResource(broken.first().titleRes()),
            broken.size - 1
        )

        broken.size == 1 ->
            stringResource(R.string.child_health_broken, stringResource(broken.first().titleRes()))

        // Поломка без флагов = устройство молчит (сервис убит и доложить не может).
        else -> stringResource(R.string.child_health_silent, formatAgo(child.lastSeenAt ?: now, now))
    }

    Surface(
        onClick = onClick,
        color = HealthDangerColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = HealthDangerColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun AddChildButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.children_add),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
