package ru.homelab.kidguard.feature.child.today

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.ChildAvatars
import ru.homelab.kidguard.core.ui.components.EmptyState
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.NeonProgress
import ru.homelab.kidguard.core.ui.components.formatDurationMinutes

/**
 * Детский главный экран «Сегодня» (веха 4.1.3, доработан в Фазе 4 UI-аудита): приветствие,
 * крупный остаток времени на сегодня (кольцо / карточка «время вышло» / «без лимита»), статус
 * контроля стеклянной плашкой и сетка 2×2 сводки правил + наигранное сегодня время. Карточки
 * сетки кликабельны — открывают детальные read-only экраны списков правил (Фаза UI-аудита).
 */
@Composable
fun TodayScreen(
    onOpenPermissions: () -> Unit,
    onOpenLimits: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenAllowed: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        TodayScreenState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        TodayScreenState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Filled.Warning,
                    title = stringResource(R.string.child_today_error_title),
                    description = stringResource(R.string.child_today_error_desc)
                )
            }
        }

        is TodayScreenState.Content -> {
            val ui = current.ui
            // Нижний лист выбора локального аватара (веха 4.1.5) — открывается по тапу на аватарку.
            var showAvatarPicker by remember { mutableStateOf(false) }

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                GreetingRow(
                    name = ui.childName,
                    avatar = ui.childAvatar,
                    onAvatarClick = { showAvatarPicker = true },
                    onOpenPermissions = onOpenPermissions
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
                RulesGrid(ui, onOpenLimits, onOpenBlocked, onOpenAllowed, onOpenStats)
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
    }
}

@Composable
private fun GreetingRow(
    name: String,
    avatar: Int,
    onAvatarClick: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
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
        Column(modifier = Modifier.weight(1f)) {
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
        ChildMenu(onOpenPermissions = onOpenPermissions)
    }
}

/** Меню детского режима. Пункты ведут под родительский PIN — сами по себе ничего не открывают. */
@Composable
private fun ChildMenu(onOpenPermissions: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.child_menu_open_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.child_menu_permissions)) },
                onClick = {
                    expanded = false
                    onOpenPermissions()
                }
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
                    text = stringResource(R.string.child_bonus_chip, formatDurationMinutes(bonusMinutes)),
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
        valueText = formatDurationMinutes(minutesLeft),
        subtitleText = stringResource(R.string.child_time_remaining_of, formatDurationMinutes(totalMinutes))
    )
}

@Composable
private fun ExpiredCard(time: TodayTimeState.Expired) {
    StateCard(
        iconTint = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.child_time_expired_title),
        titleColor = MaterialTheme.colorScheme.error,
        subtitle = stringResource(R.string.child_time_expired_sub, formatDurationMinutes(time.totalMinutes)),
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

/**
 * Статус контроля — стеклянная плашка-пилюля (Фаза 4, была голой строкой текста). Переиспользует
 * GlassCard с cornerRadius=999.dp вместо ручного повторения формул цвета — одна точка правды на
 * формулу «стекла» во всём приложении.
 */
@Composable
private fun GuardStatus() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            cornerRadius = 999.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_shield_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(R.string.child_guard_status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Сетка 2×2 (Фаза 4, была вертикальным списком из 3 строк) — по макету
 * docs/ui-concepts/today-screen/. Добавлена четвёртая карточка «Сегодня» (наигранное время) —
 * данные уже читались в TodayViewModel для расчёта кольца, просто не показывались в UI.
 */
@Composable
private fun RulesGrid(
    ui: TodayUiState,
    onOpenLimits: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenAllowed: () -> Unit,
    onOpenStats: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RuleGridCard(
                modifier = Modifier.weight(1f),
                icon = ImageVector.vectorResource(R.drawable.ic_timer),
                iconTint = MaterialTheme.colorScheme.primary,
                iconBackground = MaterialTheme.colorScheme.surfaceContainerHighest,
                label = stringResource(R.string.child_rules_limits_label),
                value = pluralStringResource(R.plurals.child_rules_apps_count, ui.limited.count, ui.limited.count),
                subtitle = limitedGridSubtitle(ui.limited),
                onClick = onOpenLimits
            )
            RuleGridCard(
                modifier = Modifier.weight(1f),
                icon = ImageVector.vectorResource(R.drawable.ic_block),
                iconTint = MaterialTheme.colorScheme.error,
                iconBackground = MaterialTheme.colorScheme.errorContainer,
                label = stringResource(R.string.child_rules_blocked_label),
                value = pluralStringResource(R.plurals.child_rules_apps_count, ui.blocked.count, ui.blocked.count),
                onClick = onOpenBlocked
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RuleGridCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CheckCircle,
                iconTint = MaterialTheme.colorScheme.primary,
                iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                label = stringResource(R.string.child_rules_allowed_label),
                value = pluralStringResource(
                    R.plurals.child_rules_apps_count,
                    ui.alwaysAllowed.count,
                    ui.alwaysAllowed.count
                ),
                onClick = onOpenAllowed
            )
            RuleGridCard(
                modifier = Modifier.weight(1f),
                icon = ImageVector.vectorResource(R.drawable.ic_clock),
                iconTint = MaterialTheme.colorScheme.tertiary,
                iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
                label = stringResource(R.string.child_rules_stats_label),
                value = formatDurationMinutes(ui.usedMinutes),
                onClick = onOpenStats
            )
        }
    }
}

/** Подсказка для карточки «Лимиты»: первое лимитированное приложение и его остаток. `null`, если
 * лимитированных приложений нет — карточка тогда просто без третьей строки, без «—»-заглушки. */
@Composable
private fun limitedGridSubtitle(limited: LimitedGroup): String? {
    val label = limited.firstLabel ?: return null
    val minutesLeft = limited.firstMinutesLeft ?: return null
    return if (minutesLeft <= 0) {
        stringResource(R.string.child_rules_limited_expired, label)
    } else {
        stringResource(R.string.child_rules_limited_preview, label, formatDurationMinutes(minutesLeft))
    }
}

@Composable
private fun RuleGridCard(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    // Фиксированная высота на все 4 карточки: у «Лимиты» есть третья строка-подсказка (первое
    // лимитированное приложение), у остальных — нет, из-за чего карточки без подсказки были
    // короче и сетка 2×2 «съезжала» (не выравнивалась по высоте между соседними карточками).
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        contentPadding = PaddingValues(16.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
