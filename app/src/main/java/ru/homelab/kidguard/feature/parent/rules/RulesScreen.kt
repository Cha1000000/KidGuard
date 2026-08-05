package ru.homelab.kidguard.feature.parent.rules

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.GlassCard
import ru.homelab.kidguard.core.ui.components.GlassDockBarReservedHeight
import ru.homelab.kidguard.core.ui.components.ScreenTitle
import ru.homelab.kidguard.feature.parent.ChildSelectorChip
import ru.homelab.kidguard.feature.parent.ParentMenu

/** Вкладка «Правила»: карточки-ссылки на дневной лимит, лимиты приложений, запрет и белый список. */
@Composable
fun RulesScreen(
    onOpenDailyLimit: () -> Unit,
    onOpenAppLimits: () -> Unit,
    onOpenBlockedApps: () -> Unit,
    onOpenBlockedSites: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onOpenPinProtection: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTitle(
            stringResource(R.string.parent_tab_rules),
            actions = { ParentMenu(onOpenAbout = onOpenAbout, onOpenAccount = onOpenAccount) }
        )
        ChildSelectorChip()
        // LazyColumn, а не Column: на невысоких экранах 7 карточек (+ заголовки секций) не
        // влезают целиком, и без прокрутки нижние карточки обрезались. Отступ под док-бар — в
        // contentPadding, чтобы нижняя карточка прокручивалась выше навигации, а не пряталась за ней.
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = GlassDockBarReservedHeight),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                RuleSectionHeader(R.string.rules_section_screen_time)
            }
            item {
                RuleCard(
                    icon = R.drawable.ic_clock_solid,
                    title = R.string.rules_daily_limit_title,
                    subtitle = R.string.rules_daily_limit_subtitle,
                    onClick = onOpenDailyLimit
                )
            }
            item {
                RuleCard(
                    icon = R.drawable.ic_timer_solid,
                    title = R.string.rules_app_limits_title,
                    subtitle = R.string.rules_app_limits_subtitle,
                    onClick = onOpenAppLimits
                )
            }
            item {
                RuleCard(
                    icon = R.drawable.ic_schedule_solid,
                    title = R.string.rules_schedule_title,
                    subtitle = R.string.rules_schedule_subtitle,
                    onClick = onOpenSchedule
                )
            }
            item {
                RuleSectionHeader(R.string.rules_section_content_access)
            }
            item {
                RuleCard(
                    icon = R.drawable.ic_check_solid,
                    title = R.string.rules_whitelist_title,
                    subtitle = R.string.rules_whitelist_subtitle,
                    onClick = onOpenWhitelist
                )
            }
            item {
                RuleCard(
                    icon = R.drawable.ic_block_solid,
                    title = R.string.rules_blocked_apps_title,
                    subtitle = R.string.rules_blocked_apps_subtitle,
                    onClick = onOpenBlockedApps,
                )
            }
            item {
                RuleCard(
                    icon = R.drawable.ic_globe_off_solid,
                    title = R.string.rules_blocked_sites_title,
                    subtitle = R.string.rules_blocked_sites_subtitle,
                    onClick = onOpenBlockedSites,
                )
            }
            item {
                RuleSectionHeader(R.string.rules_section_security)
            }
            item {
                RuleCard(
                    icon = R.drawable.ic_lock_solid,
                    title = R.string.rules_pin_title,
                    subtitle = R.string.rules_pin_subtitle,
                    onClick = onOpenPinProtection
                )
            }
        }
    }
}

@Composable
private fun RuleSectionHeader(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
    )
}

/**
 * Строка списка правил. Иконка — объёмная (градиенты и блики), поэтому рисуется через [Image]:
 * у [Icon] есть обязательный `tint`, который перекрасил бы всё содержимое одним цветом и свёл
 * иконку обратно к плоской. По той же причине здесь нет параметра `iconTint` — цвет теперь
 * заложен в саму иконку (запреты красные, остальные бирюзовые).
 *
 * Размер 34dp против прежних 32dp: объёмной иконке нужно чуть больше места, чтобы читались детали.
 */
@Composable
private fun RuleCard(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(34.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

