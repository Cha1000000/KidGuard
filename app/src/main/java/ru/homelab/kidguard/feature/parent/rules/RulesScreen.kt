package ru.homelab.kidguard.feature.parent.rules

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.vectorResource
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.ScreenTitle
import ru.homelab.kidguard.feature.parent.ChildSelectorChip

/** Вкладка «Правила»: карточки-ссылки на дневной лимит, лимиты приложений, запрет и белый список. */
@Composable
fun RulesScreen(
    onOpenDailyLimit: () -> Unit,
    onOpenAppLimits: () -> Unit,
    onOpenBlockedApps: () -> Unit,
    onOpenWhitelist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.parent_tab_rules))
        ChildSelectorChip()
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RuleCard(
                icon = Icons.Filled.DateRange,
                title = R.string.rules_daily_limit_title,
                subtitle = R.string.rules_daily_limit_subtitle,
                onClick = onOpenDailyLimit
            )
            RuleCard(
                icon = ImageVector.vectorResource(R.drawable.ic_timer),
                title = R.string.rules_app_limits_title,
                subtitle = R.string.rules_app_limits_subtitle,
                onClick = onOpenAppLimits
            )
            RuleCard(
                icon = ImageVector.vectorResource(R.drawable.ic_block),
                title = R.string.rules_blocked_apps_title,
                subtitle = R.string.rules_blocked_apps_subtitle,
                onClick = onOpenBlockedApps,
                iconTint = MaterialTheme.colorScheme.error
            )
            RuleCard(
                icon = Icons.Filled.CheckCircle,
                title = R.string.rules_whitelist_title,
                subtitle = R.string.rules_whitelist_subtitle,
                onClick = onOpenWhitelist
            )
        }
    }
}

@Composable
private fun RuleCard(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
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
