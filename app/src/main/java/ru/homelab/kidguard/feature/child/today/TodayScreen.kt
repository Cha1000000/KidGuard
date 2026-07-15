package ru.homelab.kidguard.feature.child.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.ChildAvatars
import ru.homelab.kidguard.core.ui.components.GlassBackground
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.NeonProgress

/**
 * Детский главный экран «Сегодня» (веха 4.1.3): приветствие, крупный остаток времени на сегодня
 * (кольцо / карточка «время вышло» / «без лимита»), статус контроля и прозрачная для ребёнка
 * сводка правил. Один экран без навигации — списки правил пока read-only (раскрытие — позже).
 */
@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ui = state
    if (ui == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Нижний лист выбора локального аватара (веха 4.1.5) — открывается по тапу на аватарку.
    var showAvatarPicker by remember { mutableStateOf(false) }

    GlassBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            GreetingRow(
                name = ui.childName,
                avatar = ui.childAvatar,
                onAvatarClick = { showAvatarPicker = true }
            )

            when (val time = ui.time) {
                is TodayTimeState.Remaining -> RemainingSection(time, ui.bonusMinutes)
                is TodayTimeState.Expired -> ExpiredCard(time)
                TodayTimeState.NoLimit -> NoLimitCard()
            }

            GuardStatus()

            Text(
                text = stringResource(R.string.child_rules_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
            )
            RulesSection(ui)
        }
    }

    if (showAvatarPicker) {
        AvatarPickerSheet(
            selected = ui.childAvatar,
            onSelect = { viewModel.chooseAvatar(it) },
            onReset = { viewModel.resetAvatar() },
            onDismiss = { showAvatarPicker = false }
        )
    }
}

@Composable
private fun GreetingRow(name: String, avatar: Int, onAvatarClick: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Аватар кликабелен (тап → выбор своего аватара, веха 4.1.5); визуального бейджа нет.
        Image(
            painter = painterResource(ChildAvatars.resFor(avatar)),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = stringResource(R.string.child_avatar_edit_cd), onClick = onAvatarClick)
        )
        Column {
            Text(
                text = stringResource(R.string.child_greeting_hello),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.child_greeting_name,
                    name.ifBlank { stringResource(R.string.child_greeting_name_fallback) }
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RemainingSection(time: TodayTimeState.Remaining, bonusMinutes: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RingIndicator(minutesLeft = time.minutesLeft, totalMinutes = time.totalMinutes)
        if (bonusMinutes > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.padding(top = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.child_bonus_chip, formatDuration(bonusMinutes)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun RingIndicator(minutesLeft: Int, totalMinutes: Int) {
    val fraction = if (totalMinutes > 0) {
        (minutesLeft.toFloat() / totalMinutes).coerceIn(0f, 1f)
    } else 0f

    NeonProgress(
        progress = fraction,
        size = 220.dp,
        strokeWidth = 12.dp,
        glowRadius = 4.dp,
        valueText = formatDuration(minutesLeft),
        subtitleText = stringResource(R.string.child_time_remaining_of, formatDuration(totalMinutes))
    )
}

@Composable
private fun ExpiredCard(time: TodayTimeState.Expired) {
    StateCard(
        iconTint = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.child_time_expired_title),
        titleColor = MaterialTheme.colorScheme.error,
        subtitle = stringResource(R.string.child_time_expired_sub, formatDuration(time.totalMinutes)),
        icon = ImageVector.vectorResource(R.drawable.ic_timer)
    )
}

@Composable
private fun NoLimitCard() {
    StateCard(
        iconTint = MaterialTheme.colorScheme.primary,
        title = stringResource(R.string.child_time_nolimit_title),
        titleColor = MaterialTheme.colorScheme.primary,
        subtitle = stringResource(R.string.child_time_nolimit_sub),
        icon = Icons.Filled.CheckCircle
    )
}

@Composable
private fun StateCard(
    iconTint: Color,
    icon: ImageVector,
    title: String,
    titleColor: Color,
    subtitle: String
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = titleColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GuardStatus() {
    Text(
        text = stringResource(R.string.child_guard_status),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun RulesSection(ui: TodayUiState) {
    val noneText = stringResource(R.string.child_rules_none)

    RuleRow(
        icon = Icons.Filled.CheckCircle,
        iconTint = Color(0xFF2E7D32),
        iconBackground = MaterialTheme.colorScheme.secondaryContainer,
        name = stringResource(R.string.child_rules_allowed_name),
        subtitle = ui.alwaysAllowed.previewLabels.joinToString(", ").ifEmpty { noneText },
        count = ui.alwaysAllowed.count
    )
    RuleRow(
        icon = ImageVector.vectorResource(R.drawable.ic_timer),
        iconTint = MaterialTheme.colorScheme.primary,
        iconBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
        name = stringResource(R.string.child_rules_limited_name),
        subtitle = limitedSubtitle(ui.limited, noneText),
        count = ui.limited.count
    )
    RuleRow(
        icon = ImageVector.vectorResource(R.drawable.ic_block),
        iconTint = MaterialTheme.colorScheme.error,
        iconBackground = MaterialTheme.colorScheme.errorContainer,
        name = stringResource(R.string.child_rules_blocked_name),
        subtitle = ui.blocked.previewLabels.joinToString(", ").ifEmpty { noneText },
        count = ui.blocked.count
    )
}

@Composable
private fun limitedSubtitle(limited: LimitedGroup, noneText: String): String {
    val label = limited.firstLabel ?: return noneText
    val minutesLeft = limited.firstMinutesLeft ?: return noneText
    return if (minutesLeft <= 0) {
        stringResource(R.string.child_rules_limited_expired, label)
    } else {
        stringResource(R.string.child_rules_limited_preview, label, formatDuration(minutesLeft))
    }
}

@Composable
private fun RuleRow(
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceContainer,
    name: String,
    subtitle: String,
    count: Int
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun formatDuration(minutes: Int): String {
    val safe = minutes.coerceAtLeast(0)
    return when {
        // Целые часы показываем без «00 мин» — «2 ч» вместо «2 ч 00 мин» (как на макете).
        safe >= 60 && safe % 60 == 0 -> stringResource(R.string.limit_value_h, safe / 60)
        safe >= 60 -> stringResource(R.string.limit_value_hm, safe / 60, safe % 60)
        else -> stringResource(R.string.limit_value_m, safe)
    }
}
