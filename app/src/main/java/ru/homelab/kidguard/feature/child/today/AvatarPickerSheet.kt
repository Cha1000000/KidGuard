package ru.homelab.kidguard.feature.child.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.homelab.kidguard.R
import ru.homelab.kidguard.core.ui.components.AvatarGrid

/**
 * Нижний лист выбора локального аватара ребёнка (веха 4.1.5). Выбор хранится только на этом
 * устройстве (см. [TodayViewModel.chooseAvatar]) — родителям ребёнок виден с прежним, серверным
 * аватаром. Сброс сверху сетки возвращает «как выбрал родитель».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerSheet(
    selected: Int,
    onSelect: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.child_avatar_picker_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.child_avatar_picker_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            OutlinedButton(
                onClick = {
                    onReset()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(stringResource(R.string.child_avatar_reset))
            }
            AvatarGrid(
                selected = selected,
                onSelect = {
                    onSelect(it)
                    onDismiss()
                },
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
