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

/** Вкладка «Правила»: две карточки-ссылки — на дневной лимит и на белый список приложений. */
@Composable
fun RulesScreen(
    onOpenDailyLimit: () -> Unit,
    onOpenWhitelist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.parent_tab_rules))
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
    modifier: Modifier = Modifier
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
                tint = MaterialTheme.colorScheme.primary,
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
