package ru.homelab.kidguard.feature.parent.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.homelab.kidguard.R

/** Заголовок-разделитель группы системных приложений в пикерах. */
@Composable
fun SystemAppsSectionHeader() {
    Text(
        text = stringResource(R.string.system_apps_group_header),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 4.dp)
    )
}

/**
 * Значок-предупреждение у критичных для устройства приложений (systemui/лаунчер/сам KidGuard):
 * жёлтый треугольник; тап показывает короткое пояснение.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskyAppWarning(modifier: Modifier = Modifier) {
    val tooltipState = rememberTooltipState(isPersistent = false)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(stringResource(R.string.system_apps_risky_tooltip)) } },
        state = tooltipState
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = stringResource(R.string.system_apps_risky_cd),
            tint = Color(0xFFF5B301),
            modifier = modifier
                .size(18.dp)
                .clickable { scope.launch { tooltipState.show() } }
        )
    }
}
