package ru.homelab.kidguard.feature.parent.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.R
import ru.homelab.kidguard.ui.theme.WarningAccent

/**
 * Общая подсказка экранов «Расписание» и «Перерывы»: без родительского PIN экраны-замки
 * включать нельзя — снять их с телефона ребёнка будет нечем, кроме приложения родителя.
 */
@Composable
fun PinWarningCard(onOpenPinSetup: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(WarningAccent.copy(alpha = 0.12f))
            .border(1.dp, WarningAccent.copy(alpha = 0.4f), shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = WarningAccent,
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
                    color = WarningAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
